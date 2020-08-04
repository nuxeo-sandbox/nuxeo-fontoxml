package nuxeo.fontoxml.test;

import static org.junit.Assert.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;

import javax.inject.Inject;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

import static com.nuxeo.fontoxml.servlet.Constants.*;
import com.nuxeo.fontoxml.servlet.FontoXMLServlet;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core")
public class TestServlet {

    public static final String BASE_URL = "http://localhost";

    public static final String PSEUDO_XML_CONTENT = "This should be XML";

    protected static abstract class DummyServletOutputStream extends ServletOutputStream {
        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    }

    // Administrator/Administrator
    public static final String BASIC_AUTH_DEFAULT = "Basic QWRtaW5pc3RyYXRvcjpBZG1pbmlzdHJhdG9y";

    @Inject
    protected CoreSession coreSession;

    @Inject
    protected EventService eventService;

    protected DocumentModel createTestDoc(String mimeType, boolean withBlob) {

        DocumentModel doc = coreSession.createDocumentModel("/", "test", "File");
        doc.setPropertyValue("dc:title", "Test Doc");
        if (withBlob) {
            Blob dummyBlob = new StringBlob(PSEUDO_XML_CONTENT, mimeType);
            doc.setPropertyValue("file:content", (Serializable) dummyBlob);
        }

        doc = coreSession.createDocument(doc);
        coreSession.save();
        // When testing a real running server => flush transaction too so the server's thread can find the document
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        return doc;
    }

    @Test
    // (does not work with a mocked response, status code cannot be set by FontoXMLServlet)
    public void testHeartbeat() throws Exception {

        // Prepare mock request
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getPathInfo()).thenReturn(PATH_HEARTBEAT);

        // Prepare mock response
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);

        (new FontoXMLServlet()).doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    public void shouldGetDocument() throws Exception {

        DocumentModel doc = createTestDoc(MIME_TYPE_XML, true);

        // Prepare mock request
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getPathInfo()).thenReturn(PATH_DOCUMENT);
        when(mockRequest.getParameter(PARAM_DOC_ID)).thenReturn(doc.getId());

        // Prepare mock response
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ServletOutputStream sos = new DummyServletOutputStream() {
            @Override
            public void write(int b) {
                out.write(b);
            }
        };
        PrintWriter printWriter = new PrintWriter(sos);
        when(mockResponse.getOutputStream()).thenReturn(sos);
        when(mockResponse.getWriter()).thenReturn(printWriter);

        (new FontoXMLServlet()).doGet(mockRequest, mockResponse);

        // Should test we have the correct blob...
        // Not very useful to build a JSON string and test it equals what was returned,
        // a different order of properties is ok, but the test will fail...
        // So far we only test that all went well...
        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);

        verify(mockResponse).setContentType("application/json");

        // Check the result
        String resultStr = out.toString();
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

        // Prepare mock request
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getPathInfo()).thenReturn(PATH_DOCUMENT);
        when(mockRequest.getParameter(PARAM_DOC_ID)).thenReturn("not a valid UUID");

        // Prepare mock response
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);

        (new FontoXMLServlet()).doGet(mockRequest, mockResponse);

        // Reminder: Using mockito, you must get exactly what is sent in the servlet.
        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "Document not found");

    }

    @Test
    public void testGetDocumentFailsWithNoBlob() throws Exception {

        DocumentModel doc = createTestDoc(MIME_TYPE_XML, false);

        // Prepare mock request
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getPathInfo()).thenReturn(PATH_DOCUMENT);
        when(mockRequest.getParameter(PARAM_DOC_ID)).thenReturn(doc.getId());

        // Prepare mock response
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);

        (new FontoXMLServlet()).doGet(mockRequest, mockResponse);

        // Reminder: Using mockito, you must get exactly what is sent in the servlet.
        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "This document has no blob");

    }

    @Test
    public void testGetDocumentFailsWithNoXml() throws Exception {

        DocumentModel doc = createTestDoc("text/plain", true);

        // Prepare mock request
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getPathInfo()).thenReturn(PATH_DOCUMENT);
        when(mockRequest.getParameter(PARAM_DOC_ID)).thenReturn(doc.getId());

        // Prepare mock response
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);

        (new FontoXMLServlet()).doGet(mockRequest, mockResponse);

        // Reminder: Using mockito, you must get exactly what is sent in the servlet.
        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "This document contains no XML");

    }

}
