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
 * - We use heartbeat but always return OK +> >TO BE TUNED
 * In out test, it is set tio "every 30s"
 * - We don't use the editSession tocken
 * - unimplemented endpoints.features:
 * Multi-load documents (this POC always loads one by one)
 * . . .
 * Idea of usage for tyhe documentContext: Add a boolean that will give the lock state of the
 * document at first load. So if it was already locked, we don't release the lock when asked
 * byt Fonto (but tell it it was released)
 */
public class FontoXMLServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(FontoXMLServlet.class);

    public static final String PATH_DOCUMENT = "/document";

    public static final String PATH_DOCUMENT_STATE = "/document/state";

    public static final String PATH_DOCUMENT_LOCK = "/document/lock";

    public static final String PATH_HEARTBEAT = "/heartbeat";

    public static final String PARAM_CONTEXT = "context";

    public static final String PARAM_DOC_ID = "documentId";

    public static final String PARAM_DOCUMENTS = "documents";

    /**
     * As it is a GET, we don't do anything at repository level, no change in the document,
     * not even a lock (which does modify data at database level)
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // String uri = req.getRequestURI();

        String path = req.getPathInfo();
        log.warn("GET " + path);

        if (path.equals(PATH_DOCUMENT)) {
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

                    String xml = blob.getString();
                    // log.warn("blob.getString => \n" + xml);

                    // Build the response
                    JSONObject responseJson = new JSONObject();
                    // - Required: Doc Id
                    responseJson.put(PARAM_DOC_ID, docId);
                    // - Required: XML content
                    responseJson.put("content", xml);
                    // - Required: Lock info
                    JSONObject lock = getLockInfoForFonto(doc);
                    responseJson.put("lock", lock);
                    // - Optional, documentContext
                    //   ("CMS-specific data associated with the document. Will be included in related requests.")
                    //   . . . maybe do optimization and put in there data that will then not need to be recalculated or
                    //   re-fetched.. . .
                    JSONObject documentContext = new JSONObject();
                    // We save in our private context the same object
                    documentContext.put("lockInfo", lock);
                    /*
                     * if(session.hasPermission(doc.getRef(), "Write")) {
                     * Lock lockInfo = doc.getLockInfo();
                     * if (lockInfo == null) {
                     * documentContext.put("canBeLocked", true);
                     * documentContext.put("isLockedByMe", false);
                     * } else {
                     * documentContext.put("canBeLocked", false); // already locked
                     * if(lockInfo.getOwner().equals(session.getPrincipal().getName())) {
                     * documentContext.put("isLockedByMe", true);
                     * } else {
                     * documentContext.put("isLockedByMe", false);
                     * documentContext.put("lockReason", "The document is locked by another user.");
                     * }
                     * }
                     * } else {
                     * documentContext.put("canBeLocked", false);
                     * documentContext.put("isLockedByMe", false);
                     * documentContext.put("lockReason", "The document cannot be modified by this user");
                     * }
                     */
                    responseJson.put("documentContext", documentContext);
                    // - Optional, revisionId
                    //   (unused)
                    // - Optional, metadata
                    //   (unused)

                    String response = responseJson.toString();
                    sendOKResponse(resp, response);
                }

            } catch (JSONException e) {
                throw new NuxeoException("Failed to jaon-parse the <context> parameter", e);
            }

        } else if (path.endsWith(PATH_HEARTBEAT)) { // if (path.equals(GET_DOCUMENT)) . . . to be continued . . .
            // We don't implement all logic and always return OK
            sendOKResponse(resp, null);
        }

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String path = req.getPathInfo();
        log.warn("POST " + path);

        String body = IOUtils.toString(req.getReader());
        // log.warn("POST, body:\n" + body);
        try {
            JSONObject bodyJson = new JSONObject(body);
            try (CloseableCoreSession session = CoreInstance.openCoreSession(null)) {
                switch (path) {
                // "Can be used to periodically retrieve the most recent lock state and revisionId for a set of
                // documents"
                // IMPLEMENTATION - POC WARNING
                // We use the documentContext to cache the lock info when it is modified (GET /document and
                // /PUT /document/lock)
                // This means, we don't check if the lock or the permissions have changed.
                // => OK in the context of a POC
                // TODO Change the /document/state call frequency (I think it requires a different build of Fonto) and
                // recalculate all if needed
                case PATH_DOCUMENT_STATE:
                    JSONObject context = bodyJson.getJSONObject(PARAM_CONTEXT);
                    JSONArray documents = bodyJson.getJSONArray(PARAM_DOCUMENTS);
                    JSONArray results = new JSONArray();

                    // Received an array of document info, must return lock state values in the same order
                    int max = documents.length();
                    for (int i = 0; i < max; i++) {
                        JSONObject oneDocInfo = documents.getJSONObject(i);
                        JSONObject documentContext = oneDocInfo.getJSONObject("documentContext");
                        JSONObject lockInfo = documentContext.getJSONObject("lockInfo");

                        // So. We just return the lock info as we have it, not recalculating it.
                        JSONObject bodyResult = new JSONObject();
                        bodyResult.put("documentContext", documentContext);
                        bodyResult.put("lock", lockInfo);
                        // bodyResult.put("revisionId", "some id");

                        JSONObject oneResult = new JSONObject();
                        oneResult.put("status", HttpServletResponse.SC_OK);
                        oneResult.put("body", bodyResult);
                        results.put(i, oneResult);

                        log.warn("isLockAcquired: " + lockInfo.getBoolean("isLockAcquired"));
                    }

                    JSONObject responseJson = new JSONObject();
                    responseJson.put("results", results);
                    String response = responseJson.toString();
                    sendOKResponse(resp, response);

                    break;

                }
            } // CoreInstance.openCoreSession
        } catch (JSONException e) {
            throw new NuxeoException("Failed to json-parse a string", e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        String path = req.getPathInfo();
        log.warn("PUT " + path);

        String body = IOUtils.toString(req.getReader());
        try {
            JSONObject bodyJson = new JSONObject(body);
            try (CloseableCoreSession session = CoreInstance.openCoreSession(null)) {
                switch (path) {
                // ========================================
                // PUT /document
                // This service is used by FontoXML to save a modified XML document and/or its metadata. The CMS must
                // permanently store the new content and metadata.
                // ========================================
                case PATH_DOCUMENT: {
                    // Required:
                    JSONObject context = bodyJson.getJSONObject("context");
                    String docId = bodyJson.getString("documentId");
                    String xmlContent = bodyJson.getString("content");
                    // Optional:
                    boolean autosave = bodyJson.optBoolean("autosave");
                    JSONObject documentContext = bodyJson.optJSONObject("documentContext");
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
                        Blob newBlob = new StringBlob(xmlContent, "text/xml");
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
                                bodyResult.put("documentContext", documentContext);
                            }
                            if (StringUtils.isNotBlank(revisionId)) {
                                bodyResult.put("revisionId", revisionId);
                            }
                            sendResponse(resp, HttpServletResponse.SC_OK, bodyResult.toString());
                        } else {
                            sendResponse(resp, 204, null);
                        }
                    }

                }
                    break;

                // ========================================
                // PUT /document/lock
                // This service is used by FontoXML to acquire or release a lock on a given document.
                // See GET /document and POST /document/state. We use the documentContext to handle the lock
                // ========================================
                case PATH_DOCUMENT_LOCK: {
                    JSONObject context = bodyJson.getJSONObject("context");
                    String docId = bodyJson.getString("documentId");
                    JSONObject lock = bodyJson.getJSONObject("lock");
                    boolean acquireLock = lock.getBoolean("isLockAcquired");
                    String revisionId = bodyJson.optString("revisionId", null);
                    JSONObject documentContext = bodyJson.getJSONObject("documentContext");

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
                        // Little tweak. If it was originally locked, then pretend we succesfuly unlocked it
                        documentContext.put("lockInfo", lockInfoForFonto);

                        /*
                         * String msg = "acquireLock: " + acquireLock;
                         * msg += "\nisLockedByMe: " + isLockedByMe;
                         * msg += "\nlockRemoved: " + lockRemoved;
                         * msg += "\nlockInfoForFonto:\n" + lockInfoForFonto.toString(2);
                         * log.warn(msg);
                         */

                        // Cf. FontoXML API doc:
                        // Return 200 if the lock was set/unset _and_ the body of the response may contain an updated
                        // documentContext.
                        // Return 204 is lock was set/unset and the documentContext is not updated.
                        if (isLockedByMe || lockRemoved) {
                            if (documentContext != null || StringUtils.isNotBlank(revisionId)) {
                                JSONObject bodyResult = null;
                                bodyResult = new JSONObject();
                                if (documentContext != null) {
                                    bodyResult.put("documentContext", documentContext);
                                }
                                if (StringUtils.isNotBlank(revisionId)) {
                                    bodyResult.put("revisionId", revisionId);
                                }
                                sendResponse(resp, HttpServletResponse.SC_OK, bodyResult.toString());
                            } else {
                                sendResponse(resp, 204, null);
                            }
                        } else {
                            // 403
                            sendResponse(resp, HttpServletResponse.SC_FORBIDDEN, null);
                        }
                    }
                }
                    break;

                }
            } // CoreInstance.openCoreSession
        } catch (JSONException e) {
            throw new NuxeoException("Failed to json-parse a string", e);
        }

    }

    protected void sendResponse(HttpServletResponse resp, int status, String response) throws IOException {

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

    @Override
    public void init() throws ServletException {
        log.info("Ready.");
    }
}
