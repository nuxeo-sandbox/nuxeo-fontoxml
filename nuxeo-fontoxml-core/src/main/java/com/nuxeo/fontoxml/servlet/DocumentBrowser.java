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
package com.nuxeo.fontoxml.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.nuxeo.fontoxml.servlet.Constants.*;
import static com.nuxeo.fontoxml.servlet.FontoXMLServlet.*;

import java.io.IOException;
import java.io.Serializable;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
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
 * - WE ONLY HANDLE File, Picture, Audio and Video (and "Folderish")
 * - FROM FONT STANDPOINT, WE DON'T HANDLE ALL ASSET TYPES (not "document-template", or "link" for example)
 * **************************************************
 * Also, we do not handle the "sort" optional parameter, always order by title.
 * 
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

    public static final FastDateFormat FORMATTER = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

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

    /*
     * IMPORTANT
     * The query should have pre-filter Folder/Picture/...
     * We do it here in this POC.
     */
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

            if (log.isInfoEnabled()) {
                String msg = "AssetTypes: " + assetTypes.toString();
                msg += "\nresultTypes: " + resultTypes.toString();
                msg += "NXQL:\n" + nxql;
                log.info(msg);
            }

            // For quick search of the type that is wanted
            // The query should have already return only the types of documents we want
            ArrayList<String> assetTypesList = new ArrayList<String>();
            for (int i = 0; i < assetTypes.length(); i++) {
                assetTypesList.add(assetTypes.getString(i));
            }
            boolean hasFontoFileAssetType = assetTypesList.contains(FONTO_TYPE_FILE);
            // ======================================== <Just for the POC context>
            String infoMsg = "";
            for (String assetType : assetTypesList) {
                switch (assetType) {
                case FONTO_TYPE_DOCUMENT:
                case FONTO_TYPE_FOLDER:
                case FONTO_TYPE_FILE:
                case FONTO_TYPE_IMAGE:
                case FONTO_TYPE_AUDIO:
                case FONTO_TYPE_VIDEO:
                    // OK. We handle these (see below)
                    break;

                default:
                    infoMsg += assetType + " ";
                    break;
                }
            }
            if (infoMsg.length() > 0) {
                log.warn("This POC does not handle browsing the following: " + infoMsg);
            }
            // ======================================== </Just for the POC context>

            DocumentModelList docs = session.query(nxql);

            JSONObject results = new JSONObject();
            int totalCount = docs.size();
            JSONArray items = new JSONArray();
            for (DocumentModel oneDoc : docs) {
                // See above. Once the search will have secure the result, this will not not be required.
                boolean gotOne = false;
                JSONObject item = new JSONObject();
                // Assumes that if we have a Folderish, it was OK to query it.
                if (oneDoc.hasFacet("Folderish")) {
                    gotOne = true;

                    item.put(PARAM_ID, oneDoc.getId());
                    item.put(PARAM_LABEL, oneDoc.getTitle());
                    item.put(PARAM_TYPE, FONTO_TYPE_FOLDER);
                    // No metadata in there
                    // Could be icon, isDisabled, description (only for document-template) and properties (object, only
                    // for images)
                } else if (oneDoc.hasSchema("file")) {// We don't add it to the result if it has no blob

                    // Something more efficient should be implemented, like a specific doctype or a field that quickly
                    // identify the oneDoc as XML, etc.
                    Blob blob = (Blob) oneDoc.getPropertyValue("file:content");
                    if (blob != null) {
                        JSONObject metadata = null;
                        JSONObject properties = null;
                        // This is where configuration would allow for handling different type of document (and custom
                        // document types)

                        // Logic per document type
                        switch (oneDoc.getType()) {
                        case "File":
                            if (assetTypesList.contains(FONTO_TYPE_FILE)) {
                                gotOne = true;
                            } else if (assetTypesList.contains(FONTO_TYPE_DOCUMENT)) {
                                // A "document" is an XML that Fonto can display
                                if (blob.getMimeType().equals(MIME_TYPE_XML)) {
                                    gotOne = true;
                                }
                            }
                            break;

                        case "Picture":
                            if (assetTypesList.contains(FONTO_TYPE_IMAGE) || hasFontoFileAssetType) {
                                gotOne = true;

                                properties = new JSONObject();
                                @SuppressWarnings("unchecked")
                                Map<String, Serializable> pictInfo = (Map<String, Serializable>) oneDoc.getPropertyValue(
                                        "picture:info");
                                // properties.put("width", pictInfo.get("width"));
                                // properties.put("height", pictInfo.get("height"));
                                properties.put("dimension", pictInfo.get("width") + "x" + pictInfo.get("height"));
                            }
                            break;

                        // . . . other logic for other types of documents . . .
                        case "Video":
                            if (assetTypesList.contains(FONTO_TYPE_VIDEO) || hasFontoFileAssetType) {
                                gotOne = true;

                                properties = new JSONObject();
                                @SuppressWarnings("unchecked")
                                Map<String, Serializable> videoInfo = (Map<String, Serializable>) oneDoc.getPropertyValue(
                                        "vid:info");
                                // properties.put("width", videoInfo.get("width"));
                                // properties.put("height", videoInfo.get("height"));
                                properties.put("dimension", videoInfo.get("width") + "x" + videoInfo.get("height"));
                                Double duration = (Double) videoInfo.get("duration");
                                // Hopping the duration is less than 24 hours :-)
                                if (duration != null) {
                                    LocalTime d = LocalTime.ofSecondOfDay(duration.longValue());
                                    StringBuilder buf = new StringBuilder(9);
                                    int h = d.getHour();
                                    int m = d.getMinute();
                                    int s = d.getSecond();
                                    buf.append(h < 10 ? "0h" : "h")
                                       .append(h)
                                       .append(m < 10 ? "0m" : "m")
                                       .append(m)
                                       .append(s < 10 ? "0s" : "s")
                                       .append(s);
                                    properties.put("duration", buf.toString());
                                }
                            }

                            break;

                        case "Audio":
                            if (assetTypesList.contains(FONTO_TYPE_AUDIO) || hasFontoFileAssetType) {
                                gotOne = true;
                                // . . .
                            }
                            break;
                        }

                        if (gotOne) {
                            // Common to all documents
                            item.put(PARAM_ID, oneDoc.getId());
                            item.put(PARAM_LABEL, oneDoc.getTitle());
                            item.put(PARAM_TYPE, FontoXMLServlet.getFontoType(oneDoc));

                            // More info to the metadata/properties field
                            metadata = new JSONObject();
                            if (properties == null) {
                                properties = new JSONObject();
                            }
                            properties.put("version", oneDoc.getVersionLabel());
                            properties.put("state", oneDoc.getCurrentLifeCycleState());
                            Calendar aDate = (Calendar) oneDoc.getPropertyValue("created");
                            properties.put("created", FORMATTER.format(aDate));
                            aDate = (Calendar) oneDoc.getPropertyValue("dc:modified");
                            properties.put("modified", FORMATTER.format(aDate));
                            Set<String> tags = tagService.getTags(session, oneDoc.getId());
                            if (tags != null && tags.size() > 0) {
                                properties.put("tags", String.join(",", tags));
                            }
                            properties.put("fileSize", FileUtils.byteCountToDisplaySize(blob.getLength()));
                            String description = (String) oneDoc.getPropertyValue("dc:description");
                            if (StringUtils.isNotBlank(description)) {
                                properties.put("description", description);
                            }

                            metadata.put(PARAM_PROPERTIES, properties);
                            item.put(PARAM_METADATA, metadata);

                        } else {
                            totalCount -= 1;
                        }
                    } else {
                        totalCount -= 1;
                    }
                } // if (Folderish)...else

                if (gotOne) {
                    items.put(item);
                }
            }

            results.put("totalItemCount", totalCount);
            results.put("items", items);
            log.info("Results sent to Fonto:\n" + results.toString(2));

            ServletUtils.sendStringResponse(response, HttpServletResponse.SC_OK, results.toString());
        } // CloseableCoreSession
    }

    /*
     * Maybe we should be a "real" query, not using String I mean.
     * -
     * We don't really handle assetTypes. But the final product should handle
     * both assetTypes and resultTypes.
     * -
     * TODO Tune the way the expected doc types are handled.
     * Here, we ignore whatever is not File, Picture, Audio, Video (or Folderish)...
     */
    protected String buildNXQL(CoreSession session) throws JSONException {

        String nxql = "SELECT * FROM DOCUMENT WHERE ecm:isTrashed = 0 AND ecm:isVersion = 0 AND ecm:isProxy = 0 AND ecm:mixinType != 'HiddenInNavigation'";

        // Cf. Fonto API doc. resultTypes can be file, folder or both
        // So, either we want only Folderish, or we want anything
        if (resultTypes.length() == 1) {
            String type = resultTypes.getString(0);
            switch (type) {
            case FONTO_TYPE_FOLDER:
                nxql += " AND ecm:mixinType = 'Folderish'";
                break;

            case FONTO_TYPE_FILE:
                nxql += " AND ecm:mixinType != 'Folderish'";
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
