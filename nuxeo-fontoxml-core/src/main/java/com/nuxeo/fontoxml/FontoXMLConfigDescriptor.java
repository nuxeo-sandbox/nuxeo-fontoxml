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
package com.nuxeo.fontoxml;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * @since 10.10
 */
@XObject("configuration")
public class FontoXMLConfigDescriptor {
    
    @XObject("creation")
    protected static class CreationDescriptor{

        @XNode("callbackChain")
        protected String callbackChain;

        @XNode("typeForNewXMLDocument")
        protected String typeForNewXMLDocument;
        
    }
    
    @XObject("rendition")
    protected static class RenditionDescriptor{

        @XNode("callbackChain")
        protected String callbackChain;

        @XNode("defaultRendition")
        protected String defaultRendition;

        @XNode("xpath")
        protected String xpath;
        
    }
    
    @XNode(value="creation")
    protected CreationDescriptor creationDescriptor = new CreationDescriptor();
    
    @XNode(value="rendition")
    protected RenditionDescriptor renditionDescriptor = new RenditionDescriptor();
    
    public String getTypeForNewXMLDocument() {
        return creationDescriptor.typeForNewXMLDocument;
    }

    public String getDocumentCreationCallbackChain() {
        return creationDescriptor.callbackChain;
    }

    public String getRenditionCallbackChain() {
        return renditionDescriptor.callbackChain;
    }

    public String getDefaultRendition() {
        return renditionDescriptor.defaultRendition;
    }

    public String getRenditionXPath() {
        return renditionDescriptor.xpath;
    }

}
