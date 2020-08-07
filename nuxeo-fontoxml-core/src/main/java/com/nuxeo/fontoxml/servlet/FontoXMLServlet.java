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

import static com.nuxeo.fontoxml.servlet.Constants.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

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
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.filemanager.api.FileImporterContext;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
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
 * Please, read the README for a list of features/implementation/"not implemented"/shortcuts used for this POC/ etc.
 */
public class FontoXMLServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(FontoXMLServlet.class);

    /**
     * As it is a GET, we don't do anything at repository level, no change in the document,
     * not even a lock (which does modify data at database level)
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // String uri = req.getRequestURI();

        String path = req.getPathInfo();
        log.info("GET " + path);

        switch (path) {
        case PATH_DOCUMENT:
            handleGetDocument(req, resp);
            break;

        case PATH_ASSET_PREVIEW:
            handleGetAssetPreview(req, resp);
            break;

        case PATH_HEARTBEAT:
            // POC CONTEXT: We don't implement all logic and always return OK
            ServletUtils.sendOKStringResponse(resp, null);
            break;

        default:
            log.warn("GET " + path + ", not handled");
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            break;
        }
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String path = req.getPathInfo();
        log.info("POST " + path);

        switch (path) {
        case PATH_BROWSE:
            try {
                DocumentBrowser browser = new DocumentBrowser(req, resp);
                browser.browse();
            } catch (JSONException e) {
                throw new NuxeoException("Failed to json-parse a string", e);
            }
            break;

        case PATH_ASSET:
            handlePostAsset(req, resp);
            break;

        case PATH_DOCUMENT_STATE:
            handlePostDocumentState(req, resp);
            break;

        default:
            log.warn("POST " + path + ", not handled");
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            break;
        }
    }

    @Override
    public void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String path = req.getPathInfo();
        log.info("PUT " + path);

        switch (path) {
        case PATH_DOCUMENT:
            handlePutDocument(req, resp);
            break;

        case PATH_DOCUMENT_LOCK:
            handlePutDocumentLock(req, resp);
            break;

        default:
            log.warn("PUT " + path + ", not handled");
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            break;
        }
    }

    /*
     * Return the objects as expected by Fonto
     * This method must not modify the document (could be called in a GET)
     */
    public JSONObject getLockInfoForFonto(DocumentModel doc) throws JSONException {

        JSONObject lock = new JSONObject();

        if (doc.isLocked()) {
            Lock docLockInfo = doc.getLockInfo();
            if (docLockInfo.getOwner().equals(doc.getCoreSession().getPrincipal().getName())) {
                // Locked by me, all good
                lock.put(PARAM_LOCK_ACQUIRED, true);
                lock.put(PARAM_LOCK_AVAILABLE, true);
            } else {
                lock.put(PARAM_LOCK_ACQUIRED, false);
                lock.put(PARAM_LOCK_AVAILABLE, false);
                lock.put(PARAM_LOCK_REASON, "The document is locked by another user.");
            }
        } else {
            lock.put(PARAM_LOCK_ACQUIRED, false);
            if (doc.getCoreSession().hasPermission(doc.getRef(), "Write")) {
                lock.put(PARAM_LOCK_AVAILABLE, true);
            } else {
                lock.put(PARAM_LOCK_AVAILABLE, false);
                lock.put(PARAM_LOCK_REASON, "The document cannot be modified by this user");
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
        // String context = req.getParameter(PARAM_CONTEXT);
        String docId = req.getParameter(PARAM_DOC_ID);

        String includeAdditionalDocuments = req.getParameter("includeAdditionalDocuments");
        if (includeAdditionalDocuments != null) {
            log.warn("includeAdditionalDocuments set to " + includeAdditionalDocuments
                    + ", we do not support this parameter in this version");
        }

        // We assume these parameters were passed and are correctly formated
        try {
            // JSONObject contextJson = new JSONObject(context);
            // context contains editSessionToken and referrerDocumentId (optionals but context is always passed)
            try (CloseableCoreSession session = CoreInstance.openCoreSession(null)) {
                DocumentRef docRef = new IdRef(docId);
                if (!session.exists(docRef)) {
                    log.warn("docId <" + docId + "> not found");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Document not found");
                    return;
                }

                DocumentModel doc = session.getDocument(docRef);
                Blob blob = (Blob) doc.getPropertyValue("file:content");
                if (blob == null) {
                    log.warn("No blob");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "This document has no blob");
                    return;
                }

                if (!StringUtils.equals(MIME_TYPE_XML, blob.getMimeType())) {
                    log.warn("Not an XML blob");
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
                documentContext.put(DOC_TYPE, doc.getType());
                documentContext.put(DOC_STATE, doc.getCurrentLifeCycleState());
                responseJson.put(PARAM_DOCUMENT_CONTEXT, documentContext);
                // - Optional, revisionId
                //   (unused in this POC)
                // - Optional, metadata
                //   (unused in this POC)

                String response = responseJson.toString();
                ServletUtils.sendOKStringResponse(resp, response);
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

        log.info("variant: " + variant);
        try {
            JSONObject contextJson = new JSONObject(context);
            String mainDocId = contextJson.getString(PARAM_DOC_ID);
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
                    log.warn("Asset ID " + assetId + " (" + asset.getTitle() + ") has no blob");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Asset has no file");
                    return;
                }

                Blob thumbnail = Framework.getService(ThumbnailService.class).getThumbnail(asset, session);
                if (thumbnail == null) {
                    // We are screwed... Calculate a default one?
                    log.warn("Asset ID " + assetId + " (" + asset.getTitle() + ") => cannot get a thumbnail");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Cannot get a thumbnail");
                    return;
                }

                // . . . RESIZE . . .
                ImagingService imagingService = Framework.getService(ImagingService.class);
                ImageInfo imageInfo = imagingService.getImageInfo(thumbnail);
                log.info("Thumbnail size: " + imageInfo.getWidth() + "x" + imageInfo.getHeight());
                switch (variant) {
                case VARIANT_THUMBNAIL:
                    if (imageInfo.getWidth() != 128 || imageInfo.getHeight() != 128) {
                        log.info("RESIZING TO 128x128");
                        thumbnail = imagingService.resize(thumbnail, imageInfo.getFormat(), 128, 128, -1);
                    }
                    break;

                case VARIANT_WEB:
                    if (imageInfo.getWidth() > 1024 || imageInfo.getHeight() > 1024) {
                        log.info("RESIZING TO max 1024x1024");
                        thumbnail = imagingService.resize(thumbnail, imageInfo.getFormat(), 1024, 1024, -1);
                    }
                    break;

                default:
                    log.warn("Unhandled variant: " + variant);
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
     * POST /asset
     * FontoXML will issue a multipart/form-data request containing the uploaded file and the metadata to associate with
     * the file.
     * The binary data of the file is always stored in the file field.
     * -
     * We use the FileManager importer to create the document.
     * This is where a specific doc. type could be create instead.
     */
    protected void handlePostAsset(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
        String body = IOUtils.toString(req.getReader());

        Part request = req.getPart(PARAM_REQUEST);
        Part file = req.getPart(PARAM_FILE);

        String requestStr = IOUtils.toString(request.getInputStream(), Charset.forName("UTF-8"));
        try {
            JSONObject requestJson = new JSONObject(requestStr);
            // Required
            JSONObject context = requestJson.getJSONObject(PARAM_CONTEXT);
            String type = requestJson.getString(PARAM_TYPE);
            // Optional
            String folderId = requestJson.optString(PARAM_FOLDER_ID, null);
            // JSONObject metadata = requestJson.optJSONObject(PARAM_METADATA);

            String docId = context.getString(PARAM_DOC_ID);

            try (CloseableCoreSession session = CoreInstance.openCoreSession(null)) {
                DocumentModel destContainer;
                // If folderId is empty, it means "root". but we don't want to create at root level, do we? So, instead,
                // we create a same level as current document
                if (StringUtils.isBlank(folderId)) {
                    // We assume the docId exists, the user is currently editing it and they have access to the parent
                    destContainer = session.getParentDocument(new IdRef(docId));
                } else {
                    DocumentRef containerRef = new IdRef(folderId);
                    if (!session.exists(containerRef)) {
                        log.warn("Dest folder " + folderId + " not found");
                        ServletUtils.sendStringResponse(resp, HttpServletResponse.SC_NOT_FOUND, null);
                        return;
                    }
                    destContainer = session.getDocument(containerRef);
                }

                Blob blob = ServletUtils.createBlobFromPart(file);
                // TODO create a custom doc type if needed. (configuration/etc.)
                FileImporterContext fileCreationContext = FileImporterContext.builder(session, blob,
                        destContainer.getPathAsString()).build();

                DocumentModel asset = Framework.getService(FileManager.class)
                                               .createOrUpdateDocument(fileCreationContext);

                JSONObject result = new JSONObject();
                result.put(PARAM_ID, asset.getId());
                result.put(PARAM_LABEL, asset.getTitle());
                // For "type",; see Fonto API doc: "The type of document, image, file or unknown."
                switch (asset.getType()) {
                case "File":
                    break;

                case "Picture":
                    result.put(PARAM_TYPE, FONTO_TYPE_IMAGE);
                    break;

                case "Audio":
                    result.put(PARAM_TYPE, FONTO_TYPE_AUDIO);
                    break;

                case "Video":
                    result.put(PARAM_TYPE, FONTO_TYPE_VIDEO);
                    break;

                default:
                    result.put(PARAM_TYPE, FONTO_TYPE_UNKNOWN);
                    break;
                }

                String response = result.toString();
                ServletUtils.sendOKStringResponse(resp, response);

            } // CloseableCoreSession

        } catch (JSONException e) {
            throw new NuxeoException("Failed to json-parse a string", e);
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
                bodyResult.put(PARAM_LOCK, lockInfo);
                // bodyResult.put(PARAM_REVISION_ID, "some id");

                JSONObject oneResult = new JSONObject();
                oneResult.put("status", HttpServletResponse.SC_OK);
                oneResult.put(PARAM_BODY, bodyResult);
                results.put(i, oneResult);

                // log.warn("isLockAcquired: " + lockInfo.getBoolean(PARAM_LOCK_ACQUIRED));
            }

            JSONObject responseJson = new JSONObject();
            responseJson.put("results", results);
            String response = responseJson.toString();
            ServletUtils.sendOKStringResponse(resp, response);

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
                boolean autosave = bodyJson.optBoolean(PARAM_AUTOSAVE);
                JSONObject documentContext = bodyJson.getJSONObject(PARAM_DOCUMENT_CONTEXT);
                String revisionId = bodyJson.optString(PARAM_REVISION_ID, null);
                JSONObject metadata = bodyJson.optJSONObject(PARAM_METADATA);

                // log.warn("PUT " + PATH_DOCUMENT + ", body:\n" + body);

                DocumentRef docRef = new IdRef(docId);
                if (!session.exists(docRef)) {
                    log.warn("docId " + docId + " not found");
                    ServletUtils.sendStringResponse(resp, HttpServletResponse.SC_NOT_FOUND, null);
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

                    // Raise an event so configuration can add some logic
                    DocumentEventContext eventCtx = new DocumentEventContext(session, session.getPrincipal(), doc);
                    Event eventToSend = eventCtx.newEvent(EVENT_DOC_MODIFIED_BY_FONTOWML);
                    //eventCtx.setProperty(EVENT_CONTEXT_IS_AUTOSAVE, autosave);
                    doc.putContextData(EVENT_CONTEXT_IS_AUTOSAVE, autosave);
                    Framework.getService(EventService.class).fireEvent(eventToSend);

                    // We should return 200 only if the documentContext has changed,
                    // 204 if not.
                    // In the context of the POC we do it quickly and always return it if we had one
                    if (documentContext != null || StringUtils.isNotBlank(revisionId)) {
                        JSONObject bodyResult = new JSONObject();
                        if (documentContext != null) {
                            bodyResult.put(PARAM_DOCUMENT_CONTEXT, documentContext);
                        }
                        if (StringUtils.isNotBlank(revisionId)) {
                            bodyResult.put(PARAM_REVISION_ID, revisionId);
                        }
                        ServletUtils.sendStringResponse(resp, HttpServletResponse.SC_OK, bodyResult.toString());
                    } else {
                        ServletUtils.sendStringResponse(resp, 204, null);
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
                JSONObject context = bodyJson.getJSONObject(PARAM_CONTEXT);
                String docId = bodyJson.getString("documentId");
                JSONObject lock = bodyJson.getJSONObject(PARAM_LOCK);
                boolean acquireLock = lock.getBoolean(PARAM_LOCK_ACQUIRED);
                String revisionId = bodyJson.optString(PARAM_REVISION_ID, null);
                JSONObject documentContext = bodyJson.getJSONObject(PARAM_DOCUMENT_CONTEXT);

                DocumentRef docRef = new IdRef(docId);
                if (!session.exists(docRef)) {
                    log.warn("docId " + docId + " not found");
                    ServletUtils.sendStringResponse(resp, HttpServletResponse.SC_NOT_FOUND, null);
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
                            bodyResult.put(PARAM_REVISION_ID, revisionId);
                        }
                        // We return 200, "The lock has been successfully acquired/released. The body of the response
                        // may contain an updated documentContext."
                        // We don't return 204, "he lock has been successfully acquired/released. The documentContext is
                        // not updated."
                        ServletUtils.sendStringResponse(resp, HttpServletResponse.SC_OK, bodyResult.toString());
                    } else {
                        // 403
                        ServletUtils.sendStringResponse(resp, HttpServletResponse.SC_FORBIDDEN, null);
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

    /**
     * Returns the Fonto document type (document, image, file, ... for the input document
     * For this POC, this is based on the document type. Final product should be smarter (using facet, maybe?)
     * (it actually returns "folder" if the document has the "Folderish" facet)
     * 
     * @param doc
     * @return the type of document for Fonto
     * @since 10.10
     */
    public static String getFontoType(DocumentModel doc) {

        if (doc.hasFacet("Folderish")) {
            return FONTO_TYPE_FOLDER;
        }

        if (!doc.hasSchema("file")) {
            return FONTO_TYPE_UNKNOWN;
        }

        Blob blob = (Blob) doc.getPropertyValue("file:content");
        if (blob == null) {
            return FONTO_TYPE_UNKNOWN;
        }

        // TODO: Handle document-template...

        switch (doc.getType()) {
        case "File":
            String mimeType = blob.getMimeType();
            if (mimeType != null && mimeType.equals(MIME_TYPE_XML)) {
                return FONTO_TYPE_DOCUMENT;
            } else {
                return FONTO_TYPE_FILE;
            }

        case "Picture":
            return FONTO_TYPE_IMAGE;

        case "Audio":
            return FONTO_TYPE_AUDIO;

        case "Video":
            return FONTO_TYPE_VIDEO;

        default:
            return FONTO_TYPE_UNKNOWN;

        }

    }
}
