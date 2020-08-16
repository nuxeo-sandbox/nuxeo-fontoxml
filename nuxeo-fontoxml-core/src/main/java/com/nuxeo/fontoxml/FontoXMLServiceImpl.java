package com.nuxeo.fontoxml;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.features.PlatformFunctions;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.platform.filemanager.api.FileImporterContext;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.ecm.platform.mimetype.MimetypeDetectionException;
import org.nuxeo.ecm.platform.mimetype.MimetypeNotFoundException;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.platform.picture.api.PictureView;
import org.nuxeo.ecm.platform.picture.api.adapters.MultiviewPicture;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

import com.nuxeo.fontoxml.servlet.Constants;

public class FontoXMLServiceImpl extends DefaultComponent implements FontoXMLService {

    public static final String EXT_POINT = "configuration";

    protected FontoXMLConfigDescriptor config = null;

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {

        if (EXT_POINT.equals(extensionPoint)) {
            config = (FontoXMLConfigDescriptor) contribution;
        }
    }

    public FontoXMLConfigDescriptor getConfiguration() {
        return config;
    }

    @Override
    public Blob getRendition(CoreSession session, DocumentModel doc) {

        Blob blob = null;

        if (config == null) {
            // That's not normal
            throw new NuxeoException("No XML configuration loaded");
        }

        // 1. Use a chain
        String chainId = config.getRenditionCallbackChain();
        if (StringUtils.isNotBlank(chainId)) {
            AutomationService as = Framework.getService(AutomationService.class);
            OperationContext octx = new OperationContext(session);
            octx.setInput(doc);
            try {
                blob = (Blob) as.run(octx, chainId);
            } catch (OperationException e) {
                throw new NuxeoException("Failed to run the callback chain", e);
            }
        }

        // 2. No blob? Use rendition
        if (blob == null) {
            String defaultRendition = config.getDefaultRendition();
            if (StringUtils.isNotBlank(defaultRendition)) {
                MultiviewPicture mvp = doc.getAdapter(MultiviewPicture.class);
                if (mvp != null) {
                    PictureView pv = mvp.getView(defaultRendition);
                    if (pv != null) {
                        blob = pv.getBlob();
                    }
                }
            }

        }

        // 3. No blob? Use xpath
        if (blob == null) {
            String xpath = config.getRenditionXPath();
            if (StringUtils.isNotBlank(xpath)) {
                blob = (Blob) doc.getPropertyValue(xpath);
            }

        }

        // 4. Last resort, return file:content
        if (blob == null) {
            if (doc.hasSchema("file")) {
                blob = (Blob) doc.getPropertyValue("file:content");
            }
        }

        // Ultimate check, in case someone did not set the mimetype
        if (blob != null && blob.getMimeType() == null) {
            String mimeType = null;

            MimetypeRegistry mimeRegistry = Framework.getService(MimetypeRegistry.class);
            try {
                mimeType = mimeRegistry.getMimetypeFromBlob(blob);
            } catch (MimetypeNotFoundException | MimetypeDetectionException e1) {
                try {
                    mimeType = mimeRegistry.getMimetypeFromFile(blob.getFile());
                } catch (MimetypeNotFoundException | MimetypeDetectionException e2) {
                    throw new NuxeoException("Cannot get a Mime Type from the blob or the file", e2);
                }
            }
            blob.setMimeType(mimeType);
        }

        return blob;
    }

    protected DocumentModel createDocument(CoreSession session, Blob content, DocumentModel mainDoc,
            DocumentModel folder, boolean isAsset) throws IOException {

        DocumentModel doc = null;

        if (config == null) {
            // That's not normal
            throw new NuxeoException("No XML configuration loaded");
        }
        
        String xmlDocType = config.getTypeForNewXMLDocument();

        // 1. Use a chain
        String chainId = config.getDocumentCreationCallbackChain();
        if (StringUtils.isNotBlank(chainId)) {
            AutomationService as = Framework.getService(AutomationService.class);
            OperationContext octx = new OperationContext(session);
            octx.setInput(content);
            HashMap<String, Serializable> parameters = new HashMap<String, Serializable>();
            parameters.put(CHAIN_PARAM_MAINDOC, mainDoc == null ? null : mainDoc.getId());
            parameters.put(CHAIN_PARAM_FOLDER, folder == null ? null : folder.getId());
            parameters.put(CHAIN_PARAM_IS_ASSET, false);
            if (!isAsset) {
                parameters.put(CHAIN_PARAM_DOCTYPE_FOR_XML, xmlDocType);
            }

            try {
                doc = (DocumentModel) as.run(octx, chainId, parameters);
            } catch (OperationException e) {
                throw new NuxeoException("Failed to run the " + chainId + " callback chain", e);
            }
        }

        // 2. Use the suggested folder
        DocumentModel container = null;
        if (doc == null && folder != null) {
            container = folder;
        }
        if (container == null && mainDoc != null) {
            if (mainDoc.isFolder()) {
                container = mainDoc;
            } else {
                container = session.getParentDocument(doc.getParentRef());
            }
        }
        if (container == null) {
            // We must giveup...
            throw new NuxeoException("Cannot find a container for the new document");
        }

        // Use the filemanager so a plugin can decide which type of document to create
        FileManager fileManager = Framework.getService(FileManager.class);

        if (content.getFilename() == null) {
            if (Constants.MIME_TYPE_XML.equals(content.getMimeType())) {

                PlatformFunctions pf = new PlatformFunctions();
                String fileName = "XML-Doc-" + pf.getNextId("FontoPostDocument") + ".xml";
                content.setFilename(fileName);
            } else {
                // It is not normal that we don'gt have a file name for other mime type (creating "asset")
                throw new NuxeoException("Blob has no filename???");
            }
        }

        FileImporterContext context = FileImporterContext.builder(session, content, container.getPathAsString())
                                                         .overwrite(true)
                                                         .fileName(content.getFilename())
                                                         .build();
        doc = fileManager.createOrUpdateDocument(context);

        return doc;
    }

    /*
     * If there is no callback chain defined, we create in the folder.
     * If the folder is null, we create in the container of mainDoc, or in mainDoc if it is a container
     */
    @Override
    public DocumentModel createDocument(CoreSession session, Blob content, DocumentModel mainDoc, DocumentModel folder)
            throws IOException {

        return createDocument(session, content, mainDoc, folder, false);
    }

    @Override
    public DocumentModel createAsset(CoreSession session, Blob content, DocumentModel mainDoc, DocumentModel folder)
            throws IOException {

        return createDocument(session, content, mainDoc, folder, true);
    }
}
