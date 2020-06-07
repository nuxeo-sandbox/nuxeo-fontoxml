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
/*
 * ************************************************************************
 * ************************************************************************
 * WARNING - THIS IS WORK IN PROGRESS (June 2020)
 * Needs refactoring (lots of copy/paste of same codeà, cleanup, etc.
 * AND optimizations.
 * ************************************************************************
 * ************************************************************************
 * 
 */
package nuxeo.fontoxml.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.Lock;
import org.nuxeo.ecm.core.api.LockException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.api.thumbnail.ThumbnailService;
import org.nuxeo.ecm.platform.picture.api.ImageInfo;
import org.nuxeo.ecm.platform.picture.api.ImagingService;
import org.nuxeo.runtime.api.Framework;

/**
 * Servlet answering requests sent to /fontoxml
 * See https://documentation.fontoxml.com/editor/latest/cms-connectors-api-7274761.html
 *
 * @since 10.10
 */
/*
 * @TODO Write quick doc/README about the scope of this POC
 * Here is a quick, unordered list of done/not done
 * - done: . . .
 * - Lock is handled once. So we don't recheck the document all the time, don't release all the time either etc.
 * This will have to be improved in the product, of course, we are just optimizing for the POC
 * - When browsing Nuxeo, we only handle default document types ("File", "Picture", ...)
 *   - => Room for improvement and configuration in a final product to handle custom document types, if any
 * - We also don't handle specific Picture renditions, etc.
 * - Also, we don't handle document-template in browse
 * - ASSUMES THE USER CAN READ root, root/aDomain etc.
 * - We use heartbeat but always return OK +> >TO BE TUNED
 * In out test, it is set tio "every 30s"
 * - We don't use the editSession token
 * - unimplemented endpoints.features:
 * Multi-load documents (this POC always loads one by one)
 * . . .
 * Idea of usage for type documentContext: Add a boolean that will give the lock state of the
 * document at first load. So if it was already locked, we don't release the lock when asked
 * byt Fonto (but tell it it was released)
 */
public class FontoXMLServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(FontoXMLServlet.class);

    public static final String MIME_TYPE_XML = "text/xml";

    public static final String PATH_DOCUMENT = "/document";

    public static final String PATH_DOCUMENT_STATE = "/document/state";

    public static final String PATH_DOCUMENT_LOCK = "/document/lock";

    public static final String PATH_HEARTBEAT = "/heartbeat";

    public static final String PATH_BROWSE = "/browse";

    public static final String PATH_ASSET_PREVIEW = "/asset/preview";

    // The following are the names of the properties in the JSON body (or the query string)
    public static final String PARAM_CONTEXT = "context";

    public static final String PARAM_DOC_ID = "documentId";

    public static final String PARAM_ID = "id";

    public static final String PARAM_VARIANT = "variant";

    public static final String PARAM_CONTENT = "content";

    public static final String PARAM_LOCK = "lock";

    public static final String PARAM_DOCUMENTS = "documents";

    public static final String PARAM_DOCUMENT_CONTEXT = "documentContext";

    // Variables/members
    protected static ThumbnailService thumbnailService = null;

    protected static ImagingService imagingService = null;

    /**
     * As it is a GET, we don't do anything at repository level, no change in the document,
     * not even a lock (which does modify data at database level)
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // String uri = req.getRequestURI();

        String path = req.getPathInfo();
        log.warn("GET " + path);

        switch (path) {
        case PATH_DOCUMENT:
            handleGetDocument(req, resp);
            break;

        case PATH_ASSET_PREVIEW:
            handleGetAssetPreview(req, resp);
            break;

        case PATH_HEARTBEAT:
            // POC CONTEXT: We don't implement all logic and always return OK
            sendOKResponse(resp, null);
            break;

        default:
            log.warn("GET " + path + ", not handled");
            break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String path = req.getPathInfo();
        log.warn("POST " + path);

        try {
            switch (path) {
            case PATH_BROWSE:
                DocumentBrowser browser = new DocumentBrowser(req, resp);
                browser.browse();
                break;

            case PATH_DOCUMENT_STATE:
                handlePostDocumentState(req, resp);
                break;
            }
        } catch (JSONException e) {
            throw new NuxeoException("Failed to json-parse a string", e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String path = req.getPathInfo();
        log.warn("PUT " + path);

        switch (path) {
        case PATH_DOCUMENT:
            handlePutDocument(req, resp);
            break;

        case PATH_DOCUMENT_LOCK:
            handlePutDocumentLock(req, resp);
            break;

        }
    }

    public static void sendResponse(HttpServletResponse resp, int status, String response) throws IOException {

        resp.setStatus(status);

        if (StringUtils.isNotBlank(response)) {
            resp.setContentType("application/json");
            resp.setContentLength(response.getBytes().length);
            OutputStream out = resp.getOutputStream();
            out.write(response.getBytes());
            out.close();
        }
    }

    protected void sendOKResponse(HttpServletResponse resp, String response) throws IOException {

        sendResponse(resp, HttpServletResponse.SC_OK, response);
    }

    /*
     * Return the objects as expected by Fonto
     * This method must not modify the document (could be called in a GET)
     */
    protected JSONObject getLockInfoForFonto(DocumentModel doc) throws JSONException {

        JSONObject lock = new JSONObject();

        if (doc.isLocked()) {
            Lock docLockInfo = doc.getLockInfo();
            if (docLockInfo.getOwner().equals(doc.getCoreSession().getPrincipal().getName())) {
                // Locked by me, all good
                lock.put("isLockAcquired", true);
                lock.put("isLockAvailable", true);
            } else {
                lock.put("isLockAcquired", false);
                lock.put("isLockAvailable", false);
                lock.put("reason", "The document is locked by another user.");
            }
        } else {
            lock.put("isLockAcquired", false);
            if (doc.getCoreSession().hasPermission(doc.getRef(), "Write")) {
                lock.put("isLockAvailable", true);
            } else {
                lock.put("isLockAvailable", false);
                lock.put("reason", "The document cannot be modified by this user");
            }
        }

        return lock;
    }

    /*
     * GET /document
     * "This service is used by FontoXML to load all XML documents it needs during an edit session.
     * This includes documents initially loaded, templates and documents for preview."
     */
    protected void handleGetDocument(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String context = req.getParameter(PARAM_CONTEXT);
        String docId = req.getParameter(PARAM_DOC_ID);

        String includeAdditionalDocuments = req.getParameter("includeAdditionalDocuments");
        if (includeAdditionalDocuments != null) {
            log.warn("includeAdditionalDocuments set to " + includeAdditionalDocuments
                    + ", we do not support this parameter in this version");
        }

        // We assume these parameters were passed and are correctly formated
        try {
            JSONObject contextJson = new JSONObject(context);
            // context contains editSessionToken and referrerDocumentId (optionals but context is always passed)
            try (CloseableCoreSession session = CoreInstance.openCoreSession(null)) {
                DocumentRef docRef = new IdRef(docId);
                if (!session.exists(docRef)) {
                    log.warn("docId " + docId + " not found");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Document not found");
                    return;
                }

                DocumentModel doc = session.getDocument(docRef);
                Blob blob = (Blob) doc.getPropertyValue("file:content");
                if (blob == null) {
                    log.warn("No blob...");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "This document has no blob");
                    return;
                }

                if (!StringUtils.equals(MIME_TYPE_XML, blob.getMimeType())) {
                    log.warn("Not an XML blob...");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "This document contains no XML");
                    return;
                }

                String xml = blob.getString();
                // log.warn("blob.getString => \n" + xml);

                // Build the response
                JSONObject responseJson = new JSONObject();
                // - Required: Doc Id
                responseJson.put(PARAM_DOC_ID, docId);
                // - Required: XML content
                responseJson.put(PARAM_CONTENT, xml);
                // - Required: Lock info
                JSONObject lock = getLockInfoForFonto(doc);
                responseJson.put(PARAM_LOCK, lock);
                // - Optional, documentContext
                //   ("CMS-specific data associated with the document. Will be included in related requests.")
                //   . . . maybe do optimization and put in there data that will then not need to be recalculated or
                //   re-fetched.. . .
                JSONObject documentContext = new JSONObject();
                // We save in our private context the same object
                documentContext.put("lockInfo", lock);
                // Let's add stuff "just in case" (to be removed from the final product, only put what's
                // interesting)
                documentContext.put("type", doc.getType());
                documentContext.put("state", doc.getCurrentLifeCycleState());
                responseJson.put(PARAM_DOCUMENT_CONTEXT, documentContext);
                // - Optional, revisionId
                //   (unused in this POC)
                // - Optional, metadata
                //   (unused in this POC)

                String response = responseJson.toString();
                sendOKResponse(resp, response);
            }

        } catch (JSONException e) {
            throw new NuxeoException("Failed to json-parse the <context> parameter", e);
        }
    }

    /*
     * GET /asset/preview
     * "Retrieve a preview of an asset from the CMS. The CMS must return the binary result of the asset, the image for
     * example, with the correct MIME type for the asset."
     * -
     * Also:
     * "The preferred preview variant to retrieve. Thumbnail dimension being 128x128 pixels fixed size and web being
     * 1024x1024 pixels maximum."
     * -
     * In this context, the user should have at least READ access to the asset, since it was retrieved after a search
     * (see DocumentBrowser)
     * -
     * We also should handle what FontoXML is expected if the image was not modified (a 304 response status). We don't
     * handle this in this POC.
     */
    protected void handleGetAssetPreview(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Parameters are context, id and variant
        String context = req.getParameter(PARAM_CONTEXT);
        String assetId = req.getParameter(PARAM_ID);
        String variant = req.getParameter(PARAM_VARIANT);

        /*
         * log.warn("Context: " + context);
         * log.warn("id: " + assetId);
         * log.warn("variant: " + variant);
         */

        log.warn("GET /asset/preview - variant: " + variant);
        try {
            JSONObject contextJson = new JSONObject(context);
            String mainDocId = contextJson.getString("documentId");
            try (CloseableCoreSession session = CoreInstance.openCoreSession(null)) {
                DocumentRef assetDocRef = new IdRef(assetId);
                if (!session.exists(assetDocRef)) {
                    log.warn("Asset Id " + assetId + " not found");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Document not found");
                    return;
                }
                DocumentModel asset = session.getDocument(assetDocRef);
                // Should assume we have a blob (DocumentBrowser filtered that), but we still check
                if (!asset.hasSchema("file")) {
                    log.warn("Asset ID " + assetId + " (" + asset.getTitle() + ") does not have the <file> schema???");
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            "Asset does not have the 'file' schema");
                    return;
                }
                Blob blob = (Blob) asset.getPropertyValue("file:content");
                if (blob == null) {
                    log.warn("Asset ID " + assetId + " (" + asset.getTitle() + ") has no blob...");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Asset has no file");
                    return;
                }

                Blob thumbnail = getThumbnailService().getThumbnail(asset, session);
                if (thumbnail == null) {
                    // We are screwed... Calculate a default one?
                    log.warn("Asset ID " + assetId + " (" + asset.getTitle() + ") => cannot get a thumbnail...");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Cannot get a thumbnail");
                    return;
                }
                
                // . . . RESIZE . . .
                ImageInfo imageInfo = getImagingService().getImageInfo(thumbnail);
                log.warn("Thumbnail size: " + imageInfo.getWidth() + "x" + imageInfo.getHeight());
                switch (variant) {
                case "thumbnail":
                    if(imageInfo.getWidth() != 128 || imageInfo.getHeight() != 128) {
                        log.warn("RESIZING TO 128x128");
                        thumbnail = getImagingService().resize(thumbnail, imageInfo.getFormat(), 128, 128, -1);
                    }
                    break;

                case "web":
                    if(imageInfo.getWidth() > 1024 || imageInfo.getHeight() > 1024) {
                        log.warn("RESIZING TO max 1024x1024");
                        thumbnail = getImagingService().resize(thumbnail, imageInfo.getFormat(), 1024, 1024, -1);
                    }
                    break;

                default:
                    log.warn("Unknow variant: " + variant);
                    break;
                }

                resp.setContentType(thumbnail.getMimeType());
                resp.setContentLengthLong(thumbnail.getLength());
                OutputStream out = resp.getOutputStream();
                IOUtils.copy(thumbnail.getStream(), out);
                out.close();

                resp.setStatus(HttpServletResponse.SC_OK);

            } // CloseableCoreSession

        } catch (JSONException e) {
            throw new NuxeoException("Failed to json-parse the <context> parameter", e);
        }
    }

    /*
     * POST /document/state
     * "Can be used to periodically retrieve the most recent lock state and revisionId for a set of documents"
     * --------------------------------
     * IMPLEMENTATION - POC WARNING
     * --------------------------------
     * We use the documentContext to cache the lock info when it is modified (GET /document and /PUT /document/lock).
     * This means, we don't check if the lock or the permissions have changed.
     * => OK in the context of a POC
     * TODO Change the /document/state call frequency (I think it requires a different build of Fonto) and recalculate
     * all if needed
     * NOTICE THAT doing so, we actually don't need a CoreSession. But well.
     */
    protected void handlePostDocumentState(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = IOUtils.toString(req.getReader());
        try {
            JSONObject bodyJson = new JSONObject(body);
            JSONArray documents = bodyJson.getJSONArray(PARAM_DOCUMENTS);
            JSONArray results = new JSONArray();

            // Received an array of document info, must return lock state values in the same order
            int max = documents.length();
            for (int i = 0; i < max; i++) {
                JSONObject oneDocInfo = documents.getJSONObject(i);
                JSONObject documentContext = oneDocInfo.getJSONObject(PARAM_DOCUMENT_CONTEXT);
                JSONObject lockInfo = documentContext.getJSONObject("lockInfo");

                // So. We just return the lock info as we have it, not recalculating it.
                JSONObject bodyResult = new JSONObject();
                bodyResult.put(PARAM_DOCUMENT_CONTEXT, documentContext);
                bodyResult.put("lock", lockInfo);
                // bodyResult.put("revisionId", "some id");

                JSONObject oneResult = new JSONObject();
                oneResult.put("status", HttpServletResponse.SC_OK);
                oneResult.put("body", bodyResult);
                results.put(i, oneResult);

                // log.warn("isLockAcquired: " + lockInfo.getBoolean("isLockAcquired"));
            }

            JSONObject responseJson = new JSONObject();
            responseJson.put("results", results);
            String response = responseJson.toString();
            sendOKResponse(resp, response);

        } catch (JSONException e) {
            throw new NuxeoException("Failed to json-parse a string", e);
        }

    }

    /*
     * PUT /document
     * "This service is used by FontoXML to save a modified XML document and/or its metadata.
     * The CMS must permanently store the new content and metadata."
     */
    protected void handlePutDocument(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String body = IOUtils.toString(req.getReader());
        try {
            JSONObject bodyJson = new JSONObject(body);
            try (CloseableCoreSession session = CoreInstance.openCoreSession(null)) {
                // Required:
                JSONObject context = bodyJson.getJSONObject(PARAM_CONTEXT);
                String docId = bodyJson.getString(PARAM_DOC_ID);
                String xmlContent = bodyJson.getString(PARAM_CONTENT);
                // Optional:
                boolean autosave = bodyJson.optBoolean("autosave");
                JSONObject documentContext = bodyJson.getJSONObject(PARAM_DOCUMENT_CONTEXT);
                String revisionId = bodyJson.optString("revisionId", null);
                JSONObject metadata = bodyJson.optJSONObject("metadata");

                // log.warn("PUT " + PATH_DOCUMENT + ", body:\n" + body);

                DocumentRef docRef = new IdRef(docId);
                if (!session.exists(docRef)) {
                    log.warn("docId " + docId + " not found");
                    sendResponse(resp, HttpServletResponse.SC_NOT_FOUND, null);
                } else {
                    DocumentModel doc = session.getDocument(docRef);
                    // . . . create a version . . .
                    // Not really, because Fonto can sends PUT very often during modification when autoSave is true
                    Blob blob = (Blob) doc.getPropertyValue("file:content");
                    Blob newBlob = new StringBlob(xmlContent, MIME_TYPE_XML);
                    newBlob.setFilename(blob.getFilename());
                    doc.setPropertyValue("file:content", (Serializable) newBlob);
                    doc = session.saveDocument(doc);
                    session.save();
                    // We should return 200 only if the documentContext has changed,
                    // 204 if not.
                    // In the context of the POC we do it quickly and always return it if we had one
                    if (documentContext != null || StringUtils.isNotBlank(revisionId)) {
                        JSONObject bodyResult = new JSONObject();
                        if (documentContext != null) {
                            bodyResult.put(PARAM_DOCUMENT_CONTEXT, documentContext);
                        }
                        if (StringUtils.isNotBlank(revisionId)) {
                            bodyResult.put("revisionId", revisionId);
                        }
                        sendResponse(resp, HttpServletResponse.SC_OK, bodyResult.toString());
                    } else {
                        sendResponse(resp, 204, null);
                    }
                }
            } // CloseableCoreSession
        } catch (JSONException e) {
            throw new NuxeoException("Failed to json-parse a string", e);
        }
    }

    /*
     * PUT /document/lock
     * "This service is used by FontoXML to acquire or release a lock on a given document."
     * See GET /document and POST /document/state. We use the documentContext to handle the lock
     */
    protected void handlePutDocumentLock(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String body = IOUtils.toString(req.getReader());
        try {
            JSONObject bodyJson = new JSONObject(body);
            try (CloseableCoreSession session = CoreInstance.openCoreSession(null)) {
                JSONObject context = bodyJson.getJSONObject("context");
                String docId = bodyJson.getString("documentId");
                JSONObject lock = bodyJson.getJSONObject(PARAM_LOCK);
                boolean acquireLock = lock.getBoolean("isLockAcquired");
                String revisionId = bodyJson.optString("revisionId", null);
                JSONObject documentContext = bodyJson.getJSONObject(PARAM_DOCUMENT_CONTEXT);

                DocumentRef docRef = new IdRef(docId);
                if (!session.exists(docRef)) {
                    log.warn("docId " + docId + " not found");
                    sendResponse(resp, HttpServletResponse.SC_NOT_FOUND, null);
                } else {
                    boolean isLockedByMe = false;
                    boolean lockRemoved = false;

                    DocumentModel doc = session.getDocument(docRef);
                    if (acquireLock) {
                        try {
                            if (!doc.isLocked()) {
                                @SuppressWarnings("unused")
                                Lock docLock = session.setLock(docRef);
                                isLockedByMe = true;
                            } else {
                                Lock lockInfo = doc.getLockInfo();
                                isLockedByMe = lockInfo.getOwner().equals(session.getPrincipal().getName());
                            }
                        } catch (LockException e) {
                            // Ignore, we just don't lock the document
                        }
                    } else {
                        // If acquireLock is false it means we must release it
                        try {
                            doc.removeLock();
                            lockRemoved = true;
                        } catch (LockException e) {
                            // Ignore, we just don't unlock the document
                        }
                    }

                    // Update the private data lock info
                    JSONObject lockInfoForFonto = getLockInfoForFonto(doc);
                    // Little tweak. If it was originally locked, then pretend we successfully unlocked it
                    documentContext.put("lockInfo", lockInfoForFonto);
                    if (isLockedByMe || lockRemoved) {
                        JSONObject bodyResult = null;
                        bodyResult = new JSONObject();
                        if (documentContext != null) {
                            bodyResult.put(PARAM_DOCUMENT_CONTEXT, documentContext);
                        }
                        if (StringUtils.isNotBlank(revisionId)) {
                            bodyResult.put("revisionId", revisionId);
                        }
                        // We return 200, "The lock has been successfully acquired/released. The body of the response
                        // may contain an updated documentContext."
                        // We don't return 204, "he lock has been successfully acquired/released. The documentContext is
                        // not updated."
                        sendResponse(resp, HttpServletResponse.SC_OK, bodyResult.toString());
                    } else {
                        // 403
                        sendResponse(resp, HttpServletResponse.SC_FORBIDDEN, null);
                    }

                }

            } // CloseableCoreSession
        } catch (JSONException e) {
            throw new NuxeoException("Failed to json-parse a string", e);
        }
    }

    @Override
    public void init() throws ServletException {
        log.info("Ready.");
    }

    protected ThumbnailService getThumbnailService() {
        // In this context we don't care about concurrent request
        if (thumbnailService == null) {
            thumbnailService = Framework.getService(ThumbnailService.class);
        }
        return thumbnailService;
    }

    protected ImagingService getImagingService() {
        // In this context we don't care about concurrent request
        if (imagingService == null) {
            imagingService = Framework.getService(ImagingService.class);
        }
        return imagingService;
    }
}
