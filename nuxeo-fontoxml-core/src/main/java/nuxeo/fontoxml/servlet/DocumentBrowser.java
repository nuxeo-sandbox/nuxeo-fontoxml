/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package nuxeo.fontoxml.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static nuxeo.fontoxml.servlet.FontoXMLServlet.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.platform.tag.TagService;
import org.nuxeo.runtime.api.Framework;

/**
 * A class that encapsulates the handling of a POST /browse from Fonto XML
 * (https://documentation.fontoxml.com/editor/latest/browse-for-documents-and-assets-30015560.html)
 * <br/>
 * ******************** WARNINGS ********************
 * - WE DO NOT HANDLE PAGINATION IN THIS POC
 * - WE ASSUME THE CURRENT USER CAN READ ROOT AND ALL
 * - WE ASSUME AN XML ALWAYS HAS THE "text/xml" mime-type
 * **************************************************
 * Also, we do not handle the "sort" optional parameter, always order by title.
 * @since 10.10
 */
/*
 * POST /browse
 * "Browse for documents and assets. This describes the endpoint for browsing for items in the CMS.
 * This is used for browsing documents, for example for cross-references, for document templates for
 * new documents, and for browsing assets like images"
 * -
 * REMINDER: For Fonto, "document" means XML. Main file being edited, or a related one.
 */
public class DocumentBrowser {

    private static final Log log = LogFactory.getLog(DocumentBrowser.class);

    protected JSONObject body;

    protected HttpServletResponse response;

    protected JSONObject context;

    protected JSONArray assetTypes;

    protected JSONArray resultTypes;

    protected String folderId;

    protected JSONObject query;

    protected int limit;

    protected int offset;

    protected String currentDocId;

    protected static TagService tagService = null;

    public static final int ALL_RESULTS = -1;

    // See the Fonto XML API doc for these values:
    public static final String ASSET_TYPE_DOCUMENT = "document";

    public static final String ASSET_TYPE_TEMPLATE = "document-template";// We don't handle this in the POC

    public static final String ASSET_TYPE_IMAGE = "image";

    public static final String RESULT_TYPE_FOLDER = "folder";

    public static final String RESULT_TYPE_DOCUMENT = "document";

    public static final String RESULT_TYPE_TEMPLATE = "document-template";

    public static final String RESULT_TYPE_LINK = "link";

    public static final String RESULT_TYPE_IMAGE = "image";

    public static final String RESULT_TYPE_FILE = "file";

    public static final String RESULT_TYPE_UNKNOWN = "unknown";

    public DocumentBrowser(HttpServletRequest req, HttpServletResponse response) throws JSONException, IOException {

        String bodyStr = IOUtils.toString(req.getReader());
        this.body = new JSONObject(bodyStr);
        
        this.response = response;

        // Required:
        context = body.getJSONObject(PARAM_CONTEXT);
        assetTypes = body.getJSONArray("assetTypes");
        resultTypes = body.getJSONArray("resultTypes");
        // Optional:
        folderId = body.optString("folderId", null); // null => root
        query = body.optJSONObject("query");
        limit = body.optInt("limit", ALL_RESULTS);
        offset = body.optInt("offset", 0);

        // Doc ID is optional in this call. From Fonto doc, it could be used when folderId to start the search from the
        // current document's container.
        currentDocId = context.optString(PARAM_DOC_ID, null);

        if (tagService == null) {
            tagService = Framework.getService(TagService.class);
        }
    }

    public void browse() throws JSONException, IOException {

        // This POC does not handle pagination
        if (limit != ALL_RESULTS || offset > 0) {
            log.warn(
                    "This Nuxeo-FontoXML POC does not handle paginaiton when browsing the repository from Fonto (limit: "
                            + limit + ", offset: " + offset + ")");
        }

        // Query
        try (CloseableCoreSession session = CoreInstance.openCoreSession(null)) {

            String nxql = buildNXQL(session);
            //log.warn("NXQL:\n" + nxql);

            DocumentModelList docs = session.query(nxql);

            JSONObject results = new JSONObject();
            results.put("totalItemCount", docs.size());
            JSONArray items = new JSONArray();
            for (DocumentModel oneDoc : docs) {
                JSONObject item = new JSONObject();
                if (oneDoc.hasFacet("Folderish")) {
                    item.put("id", oneDoc.getId());
                    item.put("label", oneDoc.getTitle());
                    item.put("type", RESULT_TYPE_FOLDER);
                    // No metadata in there
                    // Could be icon, isDisabled, description (only for document-template) and properties (object, only
                    // fopr
                    // images)
                } else if (oneDoc.hasSchema("file")) {// We don't add it to the result if it has no blob
                    // Something more efficient should be implemented, like a specific doctype or a field that quickly
                    // identify the oneDoc as XML, etc.
                    Blob blob = (Blob) oneDoc.getPropertyValue("file:content");
                    if (blob != null) {
                        JSONObject metadata;
                        JSONObject properties;
                        // This is where configuration would allow for handling different type of document (and custom
                        // document types)
                        switch (oneDoc.getType()) {
                        case "File":
                            String mimeType = blob.getMimeType();
                            if (mimeType != null && mimeType.equals(MIME_TYPE_XML)) {
                                item.put("id", oneDoc.getId());
                                item.put("label", oneDoc.getTitle());
                                item.put("type", RESULT_TYPE_DOCUMENT);
                            } else {
                                // id must be a link to a rendition.
                                // No idea how this will work with an Office doc or a pdf or...
                                item.put("id", oneDoc.getId());
                                item.put("label", oneDoc.getTitle());
                                item.put("type", RESULT_TYPE_FILE);
                            }
                            break;

                        case "Picture":
                            // item.put("id", oneDoc.getId());
                            item.put("id", oneDoc.getId());
                            item.put("label", oneDoc.getTitle());
                            item.put("type", RESULT_TYPE_IMAGE);

                            metadata = new JSONObject();
                            properties = new JSONObject();
                            @SuppressWarnings("unchecked")
                            Map<String, Serializable> pictInfo = (Map<String, Serializable>) oneDoc.getPropertyValue(
                                    "picture:info");
                            properties.put("width", pictInfo.get("width"));
                            properties.put("height", pictInfo.get("height"));
                            properties.put("fileSize", blob.getLength());
                            // properties.put("created", 1);
                            Set<String> tags = tagService.getTags(session, oneDoc.getId());
                            if (tags != null && tags.size() > 0) {
                                properties.put("tags", String.join(",", tags));
                            }
                            metadata.put("properties", properties);
                            item.put("metadata", metadata);
                            break;

                        case "Video":
                            item.put("id", oneDoc.getId());
                            item.put("label", oneDoc.getTitle());
                            item.put("type", "video");
                            break;

                        case "Audio":
                            item.put("id", oneDoc.getId());
                            item.put("label", oneDoc.getTitle());
                            item.put("type", "audio");
                            break;

                        default:
                            item.put("id", oneDoc.getId());
                            item.put("label", oneDoc.getTitle());
                            item.put("type", RESULT_TYPE_UNKNOWN);
                            break;
                        }
                    }
                }

                items.put(item);
            }

            results.put("items", items);
            // WE DO NOT USE "metadat" IN THIS POC
            //log.warn("RESULTS SENT TO FONTO:\n" + results.toString(2));

            FontoXMLServlet.sendResponse(response, HttpServletResponse.SC_OK, results.toString());
        } // CloseableCoreSession
    }

    /*
     * Maybe we should be a "real" query, not using String I mean.
     */
    protected String buildNXQL(CoreSession session) throws JSONException {

        String nxql = "SELECT * FROM DOCUMENT WHERE ecm:isTrashed = 0 AND ecm:isVersion = 0 AND ecm:isProxy = 0 AND ecm:mixinType != 'HiddenInNavigation'";

        // Requesting only "folder" or only "file"?
        if (assetTypes.length() == 1) {
            switch (assetTypes.getString(0)) {
            case RESULT_TYPE_FOLDER:
                nxql += " AND ecm:mixinType = 'Folderish'";
                break;

            case RESULT_TYPE_FILE:
                nxql += " AND ecm:mixinType != 'Folderish'";
                break;

            default:
                log.warn("Requested asset types: " + assetTypes.getString(0) + "(Not " + RESULT_TYPE_FOLDER + ", nor "
                        + RESULT_TYPE_FILE + ")");
                break;
            }
        }

        // Where to start?
        if (StringUtils.isNotBlank(folderId)) {
            nxql += " AND ecm:parentId = '" + folderId + "'";
        } else {
            // . . .
            // We could start at the current document container's level
            // . . .
            // But here, we start at the root
            // ********** WARNING WE ASSUME THE CURRENT USER CAN READ ROOT AND ALL **********
            DocumentModel root = session.getDocument(new PathRef("/"));
            nxql += " AND ecm:parentId = '" + root.getId() + "'";
        }
        
        nxql += " ORDER BY dc:title ASC";

        return nxql;

    }

}
