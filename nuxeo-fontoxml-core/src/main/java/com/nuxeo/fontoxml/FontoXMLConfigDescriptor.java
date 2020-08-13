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
 * 
 * @since 10.10
 */
@XObject("configuration")
public class FontoXMLConfigDescriptor {

    @XNode("renditionCallbackChain")
    protected String renditionCallbackChain;

    @XNode("defaultRendition")
    protected String defaultRendition;

    @XNode("renditionXPath")
    protected String renditionXPath;

    public String getRenditionCallbackChain() {
        return renditionCallbackChain;
    }
    
    public String getDefaultRendition() {
        return defaultRendition;
    }
    
    public String getRenditionXPath() {
        return renditionXPath;
    }

}
