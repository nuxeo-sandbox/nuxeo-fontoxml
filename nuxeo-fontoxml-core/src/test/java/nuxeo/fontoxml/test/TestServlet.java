package nuxeo.fontoxml.test;

import com.nuxeo.fontoxml.servlet.Constants;

import nuxeo.fontoxml.test.utils.MockedServlet;
import nuxeo.fontoxml.test.utils.TestMockersAndFakers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.filemanager.api.FileImporterContext;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.ecm.platform.picture.api.ImageInfo;
import org.nuxeo.ecm.platform.picture.api.ImagingService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.google.common.collect.ImmutableMap;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.platform.types.api")
@Deploy("org.nuxeo.ecm.platform.types.core")
@Deploy("org.nuxeo.ecm.platform.filemanager.core")
@Deploy("org.nuxeo.ecm.platform.picture.api")
@Deploy("org.nuxeo.ecm.platform.picture.core")
@Deploy("org.nuxeo.ecm.platform.picture.convert")
@Deploy("org.nuxeo.ecm.platform.tag")
@Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core")
public class TestServlet extends MockedServlet {

    public static final String BASE_URL = "http://localhost";

    public static final String PSEUDO_XML_CONTENT = "This should be XML";

    @Inject
    protected CoreSession coreSession;
    
    @Inject
    protected ImagingService imagingService;
    
    @Inject
    protected FileManager fileManager;

    @Inject
    protected EventService eventService;

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
    
    protected void waitForEvents() {
        
        TransactionHelper.commitOrRollbackTransaction();
        eventService.waitForAsyncCompletion();
        TransactionHelper.startTransaction();
    }

    @Test
    public void testHeartbeat() throws Exception {

        run("GET", Constants.PATH_HEARTBEAT);

        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    public void shouldGetDocument() throws Exception {

        DocumentModel doc = createTestDoc(true, Constants.MIME_TYPE_XML);

        Map<String, String> params = ImmutableMap.of(Constants.PARAM_DOC_ID, doc.getId());
        run("GET", Constants.PATH_DOCUMENT, params, null, true);

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
        String docId = json.getString(Constants.PARAM_DOC_ID);
        assertEquals(doc.getId(), docId);

        // We have the xml
        String xml = json.getString(Constants.PARAM_CONTENT);
        assertEquals(PSEUDO_XML_CONTENT, xml);

        // More info we should have
        // Document is not locked and can be locked
        JSONObject lock = json.getJSONObject(Constants.PARAM_LOCK);
        assertFalse(lock.getBoolean(Constants.PARAM_LOCK_ACQUIRED));
        assertTrue(lock.getBoolean(Constants.PARAM_LOCK_AVAILABLE));
        // The context we always send to the frontend
        JSONObject context = json.getJSONObject(Constants.PARAM_DOCUMENT_CONTEXT);
        assertEquals("File", context.getString(Constants.DOC_TYPE));

    }

    @Test
    public void testGetDocumentFailsWithDocNotFound() throws Exception {

        Map<String, String> params = ImmutableMap.of(Constants.PARAM_DOC_ID, "not a valid UUID");
        run("GET", Constants.PATH_DOCUMENT, params);

        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "Document not found");

    }

    @Test
    public void testGetDocumentFailsWithNoBlob() throws Exception {

        DocumentModel doc = createTestDoc(false);

        Map<String, String> params = ImmutableMap.of(Constants.PARAM_DOC_ID, doc.getId());
        run("GET", Constants.PATH_DOCUMENT, params);

        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "This document has no blob");

    }

    @Test
    public void testGetDocumentFailsWithNoXml() throws Exception {

        DocumentModel doc = createTestDoc(true, "text/plain");

        Map<String, String> params = ImmutableMap.of(Constants.PARAM_DOC_ID, doc.getId());
        run("GET", Constants.PATH_DOCUMENT, params);

        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "This document contains no XML");

    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.thumbnail")
    @Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core:thumbnail-factory.xml")
    public void shouldGetAssetPreview() throws Exception {

        // We test with the xml file. The code gets the thumbnail and the test
        // provides one so it's fine
        DocumentModel doc = createTestDoc(true, "text/plain");

        // See GET /preview. Several parameters are expected in the body, even if not handled
        JSONObject context = new JSONObject();
        context.put(Constants.PARAM_DOC_ID, doc.getId());

        Map<String, String> params = new HashMap<String, String>();
        params.put(Constants.PARAM_CONTEXT, context.toString());
        params.put(Constants.PARAM_ID, doc.getId());// Using same doc to get the blob
        params.put(Constants.PARAM_VARIANT, Constants.VARIANT_THUMBNAIL);

        run("GET", Constants.PATH_ASSET_PREVIEW, params, null, true);

        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);
        verify(mockResponse).setContentType(TestMockersAndFakers.THUMBNAIL_MIMETYPE);
        // We asked for a thumbnail, size must be exactly 128 max
        Blob image = Blobs.createBlob(responseOutputStream.toByteArray(), TestMockersAndFakers.THUMBNAIL_MIMETYPE);
        ImageInfo imageInfo = imagingService.getImageInfo(image);
        assertTrue(imageInfo.getWidth() <= 128);
        assertTrue(imageInfo.getHeight() <= 128);
        
    }

    @Test
    public void shouldGetAssetUsingDefaultConfig() throws Exception {

        // Create an asset. Using the file manager so we have the mimetype set etc.
        File f = FileUtils.getResourceFileFromContext("home_bg.jpg");
        Blob blob = Blobs.createBlob(f);

        FileImporterContext fmContext = FileImporterContext.builder(coreSession, blob, "/")
                                                         .overwrite(true)
                                                         .mimeTypeCheck(true)
                                                         .build();
        DocumentModel doc = fileManager.createOrUpdateDocument(fmContext);
        coreSession.save();
        waitForEvents();
        
        // Check all is good before running the real test
        doc.refresh();
        assertEquals("Picture", doc.getType());
        Blob originalBlob = (Blob) doc.getPropertyValue("file:content");
        assertNotNull(originalBlob);
        String originalMimeType = originalBlob.getMimeType();
        assertNotNull(originalMimeType);
        ImageInfo originalImageInfo = imagingService.getImageInfo(originalBlob);
        
        // Now, test the servlet
        JSONObject context = new JSONObject();
        context.put(Constants.PARAM_DOC_ID, doc.getId());

        Map<String, String> params = new HashMap<String, String>();
        params.put(Constants.PARAM_CONTEXT, context.toString());
        params.put(Constants.PARAM_ID, doc.getId());// Using same doc to get the blob

        run("GET", Constants.PATH_ASSET, params, null, true);

        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);
        verify(mockResponse).setContentType(originalMimeType);

        // Default config uses the "original jpeg" rendition (no callback chain, no xpath)
        // => filenampe and lenght will not be the same, do not test on these
        Blob image = Blobs.createBlob(responseOutputStream.toByteArray());
        // But the dimensions should be the same
        ImageInfo imageInfo = imagingService.getImageInfo(image);
        assertEquals(imageInfo.getWidth(), originalImageInfo.getWidth());
        assertEquals(imageInfo.getHeight(), originalImageInfo.getHeight());
        
    }

    @Test
    @Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core:listener-docModifiedByFonto.xml")
    public void shouldPutDocumentAndCallListener() throws Exception {

        DocumentModel doc = createTestDoc(true, Constants.MIME_TYPE_XML);

        // See PUT /document. Several parameters are expected in the body, even if not handled
        JSONObject body = new JSONObject();
        body.put(Constants.PARAM_CONTEXT, new JSONObject());
        body.put(Constants.PARAM_DOC_ID, doc.getId());
        body.put(Constants.PARAM_DOCUMENT_CONTEXT, new JSONObject());
        body.put(Constants.PARAM_METADATA, new JSONObject());
        // What we test:
        body.put(Constants.PARAM_CONTENT, "NEW XML CONTENT");
        run("PUT", Constants.PATH_DOCUMENT, null, body.toString(), true);

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
