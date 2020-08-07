package nuxeo.fontoxml.test.utils;

import java.io.File;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.thumbnail.ThumbnailFactory;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

import com.nuxeo.fontoxml.servlet.Constants;

/**
 * This class centralizes misc helpers, typically declared in XML extensions
 * to fake/mock a behavior. (instead of having several small classes)
 * 
 */
public class TestMockersAndFakers implements EventListener, ThumbnailFactory {

    /*
     * ==================================================
     * EventListener: fonto-specific event (PUT /document)
     * ==================================================
     */
    @Override
    public void handleEvent(Event event) {

        if (!event.getName().equals(Constants.EVENT_DOC_MODIFIED_BY_FONTOWML)) {
            return;
        }

        DocumentEventContext docCtx = (DocumentEventContext) event.getContext();
        DocumentModel doc = docCtx.getSourceDocument();
        doc.setPropertyValue("dc:description", "OK");
        doc.getCoreSession().saveDocument(doc);

    }

    /*
     * ==================================================
     * ThumbnailFactory: Asset preview
     * ==================================================
     */
    public static final String THUMBNAIL_FILE_NAME = "file_100.png";
    
    public static final String THUMBNAIL_MIMETYPE = "image/png";
    @Override
    public Blob getThumbnail(DocumentModel doc, CoreSession session) {
        
        return computeThumbnail(doc, session);
    }

    @Override
    public Blob computeThumbnail(DocumentModel doc, CoreSession session) {
        
        File f = FileUtils.getResourceFileFromContext(THUMBNAIL_FILE_NAME);
        FileBlob thumbnail = new FileBlob(f);
        thumbnail.setMimeType(THUMBNAIL_MIMETYPE);
        
        return thumbnail;
    }
    
    

}
