package nuxeo.fontoxml.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.nuxeo.fontoxml.servlet.Utilities;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core")
public class TestServletUtilities {
    
    @Test
    public void shouldGetTheCorrectMimeTypes() throws Exception {
        
        File f = FileUtils.getResourceFileFromContext("home_bg.jpg");
        Blob blob = Blobs.createBlob(f);
        
        String mimeType = Utilities.getBlobMimeType(blob);
        assertEquals("image/jpeg", mimeType);
        
        // ------------------------------
        
        f = FileUtils.getResourceFileFromContext("listener-docModifiedByFonto.xml");
        blob = Blobs.createBlob(f);
        
        mimeType = Utilities.getBlobMimeType(blob);
        assertEquals("text/xml", mimeType);
        
    }

    @Test
    public void shouldBeAbleToGetStringFromBlob() throws Exception {
        
        File f = FileUtils.getResourceFileFromContext("listener-docModifiedByFonto.xml");
        Blob blob = Blobs.createBlob(f, "text/xml");
        
        boolean canGetString = Utilities.canGetString(blob);
        assertTrue(canGetString);
        
    }

    @Test
    public void shouldNotBeAbleToGetStringFromBlob() throws Exception {
        
        File f = FileUtils.getResourceFileFromContext("home_bg.jpg");
        Blob blob = Blobs.createBlob(f);
        
        boolean canGetString = Utilities.canGetString(blob);
        assertFalse(canGetString);
        
    }
}
