package com.nuxeo.fontoxml;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.platform.mimetype.MimetypeDetectionException;
import org.nuxeo.ecm.platform.mimetype.MimetypeNotFoundException;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.platform.picture.api.PictureView;
import org.nuxeo.ecm.platform.picture.api.adapters.MultiviewPicture;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

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
        if(blob != null && blob.getMimeType() == null) {
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
}
