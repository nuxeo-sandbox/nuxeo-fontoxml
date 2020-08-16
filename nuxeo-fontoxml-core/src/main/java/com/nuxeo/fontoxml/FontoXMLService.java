package com.nuxeo.fontoxml;

import java.io.IOException;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

public interface FontoXMLService {

    public static final String CHAIN_PARAM_DOCTYPE_FOR_XML = "docTypeForNewXML";

    public static final String CHAIN_PARAM_MAINDOC = "mainDocId";

    public static final String CHAIN_PARAM_FOLDER = "folderId";

    public static final String CHAIN_PARAM_IS_ASSET = "isAsset";

    /**
     * Return a DocumentModel created from the content, which is XML.
     * <br />
     * To decide where to create the document, the method can use either the main XML document or a suggested folder.
     * Important notes, because these are basically the wrapping around info sent by fonto:
     * - mainDoc is either the XML document originally opened in Fonto _or_ the ID of a Folderish
     * - folder can be null
     * <br />
     * IMPORTANT : The blob is assumed to have a correct mime-type but can have no filename
     * <br />
     * The service should handle the documentCreationCallbackChain configuration if any and call the chain, passing it 3
     * parameters:
     * <ul>
     * <li>mainDocId: string, the UUID of the main document (can actually be a folder)</li>
     * <li>folderId: string, where to create. Can be null</li>
     * <li>isAsset: boolean, must be set to <code>false</code> in this createDocument()</li>
     * <li>docTypeForNewXML: string, the type of document to create when isAsset is false. Optional, can be null</li>
     * <ul>
     * The chain can return null. In this case default behavior using folder and/or mainDoc should be used.
     * 
     * @param session
     * @param content
     * @param mainDoc
     * @param folder
     * @return the created document
     * @since 10.10
     */
    public DocumentModel createDocument(CoreSession session, Blob content, DocumentModel mainDoc, DocumentModel folder)
            throws IOException;

    /**
     * Return a DocumentModel created from the content, which is an asset (mainly, an image => check the mime-type).
     * <br />
     * To decide where to create the asset, the method can use either the main XML document or a suggested folder.
     * Important notes, because these are basically the wrapping around info sent by fonto:
     * - mainDoc is either the XML document originally opened in Fonto _or_ the ID of a Folderish
     * - folder can be null
     * <br />
     * IMPORTANT : The blob is assumed to have a correct mime-type and a filename
     * <br />
     * The service should handle the documentCreationCallbackChain configuration if any and call the chain, passing it 3
     * parameters:
     * <ul>
     * <li>mainDocId, string, the UUID of the main document (can actually be a folder)</li>
     * <li>folderId: string, where to create. Can be null</li>
     * <li>isAsset: boolean, must be set to <code>true</code> in this createDocument()</li>
     * <ul>
     * The chain can return null. In this case default behavior using folder and/or mainDoc should be used.
     * 
     * @param session
     * @param content
     * @param mainDoc
     * @param folder
     * @return the created document
     * @since 10.10
     */
    public DocumentModel createAsset(CoreSession session, Blob content, DocumentModel mainDoc, DocumentModel folder)
            throws IOException;

    /**
     * Returns the blob to send to Fonto (when it is sending a GET /asset request).
     * Typical usage could be (this is not required, you implement the service as you want):
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
