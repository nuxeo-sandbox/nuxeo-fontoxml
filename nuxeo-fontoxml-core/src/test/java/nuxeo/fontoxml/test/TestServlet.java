package nuxeo.fontoxml.test;

import static org.junit.Assert.*;

import javax.inject.Inject;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

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

import com.nuxeo.fontoxml.servlet.Constants;
import com.nuxeo.fontoxml.servlet.FontoXMLServlet;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core")
public class TestServlet {

    public static final String BASE_URL = "http://localhost";

    // Administrator/Administrator
    public static final String BASIC_AUTH_DEFAULT = "Basic QWRtaW5pc3RyYXRvcjpBZG1pbmlzdHJhdG9y";

    @Inject
    protected CoreSession coreSession;

    @Inject
    protected EventService eventService;

    protected DocumentModel createTestXMLDoc() {

        Blob dummyBlob = new StringBlob("this should be xml", Constants.MIME_TYPE_XML);

        DocumentModel doc = coreSession.createDocumentModel("/", "test", "File");
        doc.setPropertyValue("dc:title", "Test Doc");
        doc.setPropertyValue("file:content", (Serializable) dummyBlob);

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

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);

        when(mockRequest.getPathInfo()).thenReturn(Constants.PATH_HEARTBEAT);

        (new FontoXMLServlet()).doGet(mockRequest, mockResponse);

        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    public void testGetDocument() throws Exception {

        DocumentModel doc = createTestXMLDoc();

        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getPathInfo()).thenReturn(Constants.PATH_DOCUMENT);
        when(mockRequest.getParameter(Constants.PARAM_DOC_ID)).thenReturn(doc.getId());

        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        ServletOutputStream mockOutput = mock(ServletOutputStream.class);
        when(mockResponse.getOutputStream()).thenReturn(mockOutput);
        
        (new FontoXMLServlet()).doGet(mockRequest, mockResponse);
        
        // Should test we have the correct blob...
        // Not very useful to build a JSON string and test it equals what was returned,
        // a different order of properties is ok, but the test will fail...
        // So far we only test that all went well...
        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);

    }

}
