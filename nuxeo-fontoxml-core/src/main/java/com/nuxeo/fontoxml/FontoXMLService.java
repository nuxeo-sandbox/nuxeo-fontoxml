package com.nuxeo.fontoxml;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

public interface FontoXMLService {

    /**
     * Returns the blob to send to Fonto (when it is sending a GET /asset request).
     * Typical usage could be:
     * - If the configuration has a renditionCallbackChain => calls it to get the blob
     * - Else (or if the chain returns null), use the defaultRendition to get the blob
     * - If no blobs returned by these 2 then return file:content
     * 
     * @param session
     * @param doc
     * @return the blob to send back to FontoXML
     * @since 10.10
     */
    public Blob getRendition(CoreSession session, DocumentModel doc);
}
