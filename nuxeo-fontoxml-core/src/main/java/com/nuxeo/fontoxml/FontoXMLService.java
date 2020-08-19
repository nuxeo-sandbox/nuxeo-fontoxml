package com.nuxeo.fontoxml;

import java.io.IOException;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;

/**
 * This service uses the <code>FontoXMLConfigDescriptor</code> to make decisions when creating a new document or
 * rendering an asset, etc. See fontoxmlservice-service.xml
 * <br />
 * When <b>creating a new document</b> (XML or media - usually an image), we use the "creation" node of the
 * configuration.
 * <ul>
 * <li>A chain can be called (callbackChain). It receives some parameters and can create the document.<br />
 * The parameters are:
 * <ul>
 * <li>mainDocId: string, the UUID of the main document (could actually be a folder) opened in FontoXML UI.</li>
 * <li>folderId: string, where to create. Sent by Fonto. Can be null</li>
 * <li>isAsset: boolean, tells the chain is we are creating an XML document or an asset (Picture, typically)</li>
 * <li>docTypeForNewXML: string, the type of document to create when isAsset is false. Optional, can be null</li>
 * </ul>
 * </li>
 * <li>If there is no callbackChain or it returned null, we create the document:
 * <ul>
 * <li>If it is XML, we use the typeForNewXMLDocument config to create this doc type. If not set, we use the
 * FileManager</li>
 * <li>Else, we use the FileManager to create the document from the blob</li>
 * </ul>
 * </li>
 * </ul>
 * <br />
 * When <b>rendering a document</b> (an image), we use the "rendition" node of the configuration:
 * <ul>
 * <li>If there is callbackChain => we call it => returns the rendition as blob, or null</li>
 * <li>If there is no callbackChain or it returned null, then we use the defaultRendition</li>
 * <li>If it is still null at this step, then we use the xpath configuration parameter</li>
 * <li>If it is still null at this step, we return file:content</li>
 * <li></li>
 * </ul>
 * 
 * @since 10.10
 */
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
     * The service should handle the documentCreationCallbackChain configuration if any and pass it the expected
     * parameters defined above (it will pas the <code>isAsset</code> parameter to <code>false</code>)
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
     * The service should handle the documentCreationCallbackChain configuration if any and pass it the expected
     * parameters defined above (it will pas the <code>isAsset</code> parameter to <code>true</code>)
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
