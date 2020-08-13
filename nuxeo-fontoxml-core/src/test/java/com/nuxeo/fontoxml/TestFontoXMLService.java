package com.nuxeo.fontoxml;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("nuxeo.fontoxml.nuxeo-fontoxml-core")
public class TestFontoXMLService {

    @Inject
    protected FontoXMLService fontoxmlservice;

    @Test
    public void testService() {
        assertNotNull(fontoxmlservice);
    }
}
