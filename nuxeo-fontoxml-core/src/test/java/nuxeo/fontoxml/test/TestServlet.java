/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package nuxeo.fontoxml.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.io.download.DownloadHelper;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.picture.api.ImageInfo;
import org.nuxeo.ecm.platform.picture.api.ImagingService;
import org.nuxeo.ecm.platform.picture.api.PictureView;
import org.nuxeo.ecm.platform.picture.api.adapters.MultiviewPicture;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.common.collect.ImmutableMap;
import com.nuxeo.fontoxml.servlet.Constants;

import nuxeo.fontoxml.test.utils.MockedServlet;
import nuxeo.fontoxml.test.utils.TestMockersAndFakers;
import nuxeo.fontoxml.test.utils.Utilities;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.platform.types.api")
@Deploy("org.nuxeo.ecm.platform.types.core")
@Deploy("org.nuxeo.ecm.platform.picture.api")
@Deploy("org.nuxeo.ecm.platform.picture.core")
@Deploy("org.nuxeo.ecm.platform.picture.convert")
@Deploy("org.nuxeo.ecm.platform.tag")
@Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core")
public class TestServlet extends MockedServlet {

    public static final String BASE_URL = "http://localhost";

    public static final String PSEUDO_XML_CONTENT = "This should be XML";

    @Inject
    protected CoreSession session;

    @Inject
    protected ImagingService imagingService;

    @Test
    public void testHeartbeat() throws Exception {

        run("GET", Constants.PATH_HEARTBEAT);

        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    public void shouldGetDocument() throws Exception {

        DocumentModel doc = Utilities.createTestDoc(session, true, Constants.MIME_TYPE_XML);

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

        DocumentModel doc = Utilities.createTestDoc(session, false);

        Map<String, String> params = ImmutableMap.of(Constants.PARAM_DOC_ID, doc.getId());
        run("GET", Constants.PATH_DOCUMENT, params);

        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "This document has no blob");

    }

    @Test
    public void testGetDocumentFailsWhenNoText() throws Exception {

        DocumentModel doc = Utilities.createDocWithEmptyStringBlob(session, "image/jpeg");

        Map<String, String> params = ImmutableMap.of(Constants.PARAM_DOC_ID, doc.getId());
        run("GET", Constants.PATH_DOCUMENT, params);

        verify(mockResponse).sendError(HttpServletResponse.SC_NOT_FOUND, "Not a text-based blob");

    }

    @Test
    @Deploy("org.nuxeo.ecm.platform.thumbnail")
    @Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core:thumbnail-factory.xml")
    public void shouldGetAssetPreview() throws Exception {

        // We test with the xml file. The code gets the thumbnail and the test
        // provides one so it's fine
        DocumentModel doc = Utilities.createTestDoc(session, true, "text/plain");

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

    /*
     * We don't test misc. configurations with automation chain, defaultRendition or xpath => this is tested with the
     * service (TestFontoXMLService)
     */
    @Test
    public void shouldGetAssetUsingDefaultConfig() throws Exception {

        DocumentModel doc = Utilities.createDocumentFromFile(session, "/", "Picture", "home_bg.jpg", "image/jpeg");

        // Wait for picture:views to be calculated
        Utilities.waitForAsyncWorkAndStartTransaction(session);

        // Store info to compare with
        Blob originalBlob = (Blob) doc.getPropertyValue("file:content");
        String originalMimeType = originalBlob.getMimeType();
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
        MultiviewPicture mvp = doc.getAdapter(MultiviewPicture.class);
        PictureView pv = mvp.getView("OriginalJpeg");
        Blob testBlob = pv.getBlob();
        String fileName = testBlob.getFilename();
        String contentDisposition = DownloadHelper.getRFC2231ContentDisposition(mockRequest, fileName, null);
        verify(mockResponse).setHeader("Content-Disposition", contentDisposition);

        Blob image = Blobs.createBlob(responseOutputStream.toByteArray());
        assertEquals(testBlob.getLength(), image.getLength());
        ImageInfo imageInfo = imagingService.getImageInfo(image);
        assertEquals(originalImageInfo.getWidth(), originalImageInfo.getWidth());
        assertEquals(originalImageInfo.getHeight(), imageInfo.getHeight());

    }

    @Test
    @Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core:listener-docModifiedByFonto.xml")
    public void shouldPutDocumentAndCallListener() throws Exception {

        DocumentModel doc = Utilities.createTestDoc(session, true, Constants.MIME_TYPE_XML);

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
    
    @Ignore
    @Test
    public void shouldPostAsset() throws Exception {
        
        // Will be created at root$
        DocumentModel root = session.getRootDocument();
        
        // Build the PARAM_REQUEST Part
        JSONObject requestJson = new JSONObject();
        requestJson.put(Constants.PARAM_TYPE, "not used but expected, go figure");
        requestJson.put(Constants.PARAM_FOLDER_ID, root.getId());
        JSONObject context = new JSONObject();
        context.put(Constants.PARAM_DOC_ID, ""); // we don't use this because we use PARAM_FOLDER_ID
        requestJson.put(Constants.PARAM_CONTEXT, context);
        Part partRequest = null;
        when(mockRequest.getPart(Constants.PARAM_REQUEST)).thenReturn(partRequest);
        
        // Build the PARAM_FILE Part
        Part partFile = null;
        when(mockRequest.getPart(Constants.PARAM_REQUEST)).thenReturn(partFile);
    }

}
