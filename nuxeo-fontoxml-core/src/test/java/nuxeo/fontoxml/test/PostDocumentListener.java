package nuxeo.fontoxml.test;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

import com.nuxeo.fontoxml.servlet.Constants;

// This is a sync. listener
public class PostDocumentListener implements EventListener {

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

}
