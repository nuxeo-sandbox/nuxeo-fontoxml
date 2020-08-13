package com.nuxeo.fontoxml;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.mimetype.MimetypeDetectionException;
import org.nuxeo.ecm.platform.mimetype.MimetypeNotFoundException;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.platform.picture.api.PictureView;
import org.nuxeo.ecm.platform.picture.api.adapters.MultiviewPicture;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

public class FontoXMLServiceImpl extends DefaultComponent implements FontoXMLService {

    public static final String EXT_POINT = "configuration";

    protected FontoXMLConfigDescriptor config = null;

    /**
     * Component activated notification.
     * Called when the component is activated. All component dependencies are resolved at that moment.
     * Use this method to initialize the component.
     *
     * @param context the component context.
     */
    @Override
    public void activate(ComponentContext context) {
        super.activate(context);
    }

    /**
     * Component deactivated notification.
     * Called before a component is unregistered.
     * Use this method to do cleanup if any and free any resources held by the component.
     *
     * @param context the component context.
     */
    @Override
    public void deactivate(ComponentContext context) {
        super.deactivate(context);
    }

    /**
     * Application started notification.
     * Called after the application started.
     * You can do here any initialization that requires a working application
     * (all resolved bundles and components are active at that moment)
     *
     * @param context the component context. Use it to get the current bundle context
     * @throws Exception
     */
    @Override
    public void applicationStarted(ComponentContext context) {
        // do nothing by default. You can remove this method if not used.
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {

        if (EXT_POINT.equals(extensionPoint)) {
            config = (FontoXMLConfigDescriptor) contribution;
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        // Logic to do when unregistering any contribution
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
                String schema = StringUtils.substringBefore(xpath, ":");
                if (doc.hasSchema(schema)) {
                    blob = (Blob) doc.getPropertyValue(xpath);
                }
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
