package nuxeo.fontoxml.test;

import static com.nuxeo.fontoxml.servlet.Constants.DOC_TYPE;
import static com.nuxeo.fontoxml.servlet.Constants.MIME_TYPE_XML;
import static com.nuxeo.fontoxml.servlet.Constants.PARAM_CONTENT;
import static com.nuxeo.fontoxml.servlet.Constants.PARAM_CONTEXT;
import static com.nuxeo.fontoxml.servlet.Constants.PARAM_DOCUMENT_CONTEXT;
import static com.nuxeo.fontoxml.servlet.Constants.PARAM_DOC_ID;
import static com.nuxeo.fontoxml.servlet.Constants.PARAM_LOCK;
import static com.nuxeo.fontoxml.servlet.Constants.PARAM_LOCK_ACQUIRED;
import static com.nuxeo.fontoxml.servlet.Constants.PARAM_LOCK_AVAILABLE;
import static com.nuxeo.fontoxml.servlet.Constants.PARAM_METADATA;
import static com.nuxeo.fontoxml.servlet.Constants.PATH_DOCUMENT;
import static com.nuxeo.fontoxml.servlet.Constants.PATH_HEARTBEAT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import java.io.Serializable;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.google.common.collect.ImmutableMap;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core")
public class TestServlet extends MockedServlet {

    public static final String BASE_URL = "http://localhost";

    public static final String PSEUDO_XML_CONTENT = "This should be XML";

    @Inject
    protected CoreSession coreSession;

    protected DocumentModel createTestDoc(boolean withBlob, String blobMimeType) {

        DocumentModel doc = coreSession.createDocumentModel("/", "test", "File");
        doc.setPropertyValue("dc:title", "Test Doc");
        if (withBlob) {
            Blob dummyBlob = new StringBlob(PSEUDO_XML_CONTENT, blobMimeType);
            doc.setPropertyValue("file:content", (Serializable) dummyBlob);
        }

        doc = coreSession.createDocument(doc);
        coreSession.save();
        // When testing a real running server => flush transaction too so the server's thread can find the document
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        return doc;
    }

    protected DocumentModel createTestDoc(boolean withBlob) {
        return createTestDoc(withBlob, null);
    }

    @Test
    public void testHeartbeat() throws Exception {

        run("GET", PATH_HEARTBEAT);

        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    public void shouldGetDocument() throws Exception {

        DocumentModel doc = createTestDoc(true, MIME_TYPE_XML);

        Map<String, String> params = ImmutableMap.of(PARAM_DOC_ID, doc.getId());
        run("GET", PATH_DOCUMENT, params, null, true);

        // Should test we have the correct blob...
        // Not very useful to build a JSON string and test it equals what was returned,
        // a different order of properties is ok, but the test will fail...
        // So far we only test that all went well...
        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);

        verify(mockResponse).setContentType("application/json");

        // Check the result
        String resultStr = responseOutputStream.toString();
        JSONObject json = new JSONObject(resultStr);
        // We have the doc id
        String docId = json.getString(PARAM_DOC_ID);
        assertEquals(doc.getId(), docId);

        // We have the xml
        String xml = json.getString(PARAM_CONTENT);
        assertEquals(PSEUDO_XML_CONTENT, xml);

        // More info we should have
        // Document is not locked and can be locked
        JSONObject lock = json.getJSONObject(PARAM_LOCK);
        assertFalse(lock.getBoolean(PARAM_LOCK_ACQUIRED));
        assertTrue(lock.getBoolean(PARAM_LOCK_AVAILABLE));
        // The context we always send to the frontend
        JSONObject context = json.getJSONObject(PARAM_DOCUMENT_CONTEXT);
        assertEquals("File", context.getString(DOC_TYPE));

    }

    @Test
    public void testGetDocumentFailsWithDocNotFound() throws Exception {

        Map<String, String> params = ImmutableMap.of(PARAM_DOC_ID, "not a valid UUID");
        run("GET", PATH_DOCUMENT, params);

        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "Document not found");

    }

    @Test
    public void testGetDocumentFailsWithNoBlob() throws Exception {

        DocumentModel doc = createTestDoc(false);

        Map<String, String> params = ImmutableMap.of(PARAM_DOC_ID, doc.getId());
        run("GET", PATH_DOCUMENT, params);

        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "This document has no blob");

    }

    @Test
    public void testGetDocumentFailsWithNoXml() throws Exception {

        DocumentModel doc = createTestDoc(true, "text/plain");

        Map<String, String> params = ImmutableMap.of(PARAM_DOC_ID, doc.getId());
        run("GET", PATH_DOCUMENT, params);

        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "This document contains no XML");

    }

    @Test
    @Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core:postDocumentListener-contrib.xml")
    public void shouldPutDocumentAndCallListener() throws Exception {

        DocumentModel doc = createTestDoc(true, MIME_TYPE_XML);

        // See PUT /document. Several parameters are expected in the body, even if not handled
        JSONObject body = new JSONObject();
        body.put(PARAM_CONTEXT, new JSONObject());
        body.put(PARAM_DOC_ID, doc.getId());
        body.put(PARAM_DOCUMENT_CONTEXT, new JSONObject());
        body.put(PARAM_METADATA, new JSONObject());
        // What we test:
        body.put(PARAM_CONTENT, "NEW XML CONTENT");
        run("PUT", PATH_DOCUMENT, null, body.toString(), true);

        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);

        // Check the doc
        doc.refresh();
        Blob blob = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(blob);
        String xml = blob.getString();
        assertEquals("NEW XML CONTENT", xml);

        // Check the listener was called
        String desc = (String) doc.getPropertyValue("dc:description");
        assertEquals("OK", desc);

    }

}
