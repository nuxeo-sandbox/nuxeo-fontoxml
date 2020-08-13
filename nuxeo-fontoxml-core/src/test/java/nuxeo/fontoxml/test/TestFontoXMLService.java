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

import static org.junit.Assert.*;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.picture.api.ImageInfo;
import org.nuxeo.ecm.platform.picture.api.ImagingService;
import org.nuxeo.ecm.platform.picture.api.PictureView;
import org.nuxeo.ecm.platform.picture.api.adapters.MultiviewPicture;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.nuxeo.fontoxml.FontoXMLConfigDescriptor;
import com.nuxeo.fontoxml.FontoXMLService;
import com.nuxeo.fontoxml.FontoXMLServiceImpl;

import nuxeo.fontoxml.test.utils.Utilities;

import javax.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.platform.picture.api")
@Deploy("org.nuxeo.ecm.platform.picture.core")
@Deploy("org.nuxeo.ecm.platform.picture.convert")
@Deploy("org.nuxeo.ecm.platform.tag")
@Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core")
public class TestFontoXMLService {

    @Inject
    protected CoreSession session;

    @Inject
    protected ImagingService imagingService;

    @Inject
    protected FontoXMLService fontoxmlservice;

    @Test
    public void testServiceIsDeployed() {
        assertNotNull(fontoxmlservice);
    }

    @Test
    @Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core:get-asset-rendition-from-automation.xml")
    public void shouldLoadCustomContribution() {

        FontoXMLConfigDescriptor config = ((FontoXMLServiceImpl) fontoxmlservice).getConfiguration();
        assertNotNull(config);

        // See values in get-asset-rendition-from-automation.xml
        assertEquals("javascript.testGetRendition", config.getRenditionCallbackChain());
        assertEquals("Small", config.getDefaultRendition());
        assertTrue(StringUtils.isBlank(config.getRenditionXPath()));
    }

    @Test
    @Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core:get-asset-rendition-from-automation.xml")
    public void shouldUseAutomation() {

        DocumentModel doc = Utilities.createDocumentFromFile(session, "/", "Picture", "home_bg.jpg", "image/jpeg");
        // See the get-asset-rendition-from-automation.xml script
        // => here we ask to get the "Thumbnail" rendition
        doc.setPropertyValue("dc:description", "Thumbnail");
        doc = session.saveDocument(doc);

        // Wait for picture:views to be calculated
        Utilities.waitForAsyncWorkAndStartTransaction(session);

        // Get values for the test below
        MultiviewPicture mvp = doc.getAdapter(MultiviewPicture.class);
        PictureView pv = mvp.getView("Thumbnail");
        Blob testBlob = pv.getBlob();

        // Call the service
        Blob result = fontoxmlservice.getRendition(session, doc);

        // Check we basically have the same blob as testBlob
        assertEquals(testBlob.getLength(), result.getLength());
        assertEquals(testBlob.getFilename(), result.getFilename());
        assertEquals(testBlob.getMimeType(), result.getMimeType());
        // If they have the same length, do we _really_ need to check they have the same dimensions? ;-)
        ImageInfo testImageInfo = imagingService.getImageInfo(testBlob);
        ImageInfo imageInfo = imagingService.getImageInfo(result);
        assertEquals(testImageInfo.getWidth(), imageInfo.getWidth());
        assertEquals(testImageInfo.getHeight(), testImageInfo.getHeight());

    }

    @Test
    @Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core:get-asset-rendition-from-automation.xml")
    public void shouldUseAutomationButReturnARendition() {

        DocumentModel doc = Utilities.createDocumentFromFile(session, "/", "Picture", "home_bg.jpg", "image/jpeg");
        // See the get-asset-rendition-from-automation.xml script
        // => here let dc:description null => service should return the defaultRendition ("Small" in our config.)

        // Wait for picture:views to be calculated
        Utilities.waitForAsyncWorkAndStartTransaction(session);

        // Get values for the test below
        MultiviewPicture mvp = doc.getAdapter(MultiviewPicture.class);
        PictureView pv = mvp.getView("Small");
        Blob testBlob = pv.getBlob();

        // Call the service
        Blob result = fontoxmlservice.getRendition(session, doc);

        // Check we basically have the same blob as testBlob
        assertEquals(testBlob.getLength(), result.getLength());
        assertEquals(testBlob.getFilename(), result.getFilename());
        assertEquals(testBlob.getMimeType(), result.getMimeType());
        // If they have the same length, do we _really_ need to check they have the same dimensions? ;-)
        ImageInfo testImageInfo = imagingService.getImageInfo(testBlob);
        ImageInfo imageInfo = imagingService.getImageInfo(result);
        assertEquals(testImageInfo.getWidth(), imageInfo.getWidth());
        assertEquals(testImageInfo.getHeight(), testImageInfo.getHeight());

    }
}
