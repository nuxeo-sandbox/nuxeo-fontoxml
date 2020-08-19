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
package com.nuxeo.fontoxml.servlet;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.mimetype.MimetypeDetectionException;
import org.nuxeo.ecm.platform.mimetype.MimetypeNotFoundException;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 10.10
 */
public class Utilities {

    public static String getBlobMimeType(Blob blob) {

        if (blob == null) {
            return null;
        }

        String mimeType = blob.getMimeType();
        if(StringUtils.isNotBlank(mimeType)) {
            return mimeType;
        }
        
        MimetypeRegistry mimeRegistry = Framework.getService(MimetypeRegistry.class);
        try {
            mimeType = mimeRegistry.getMimetypeFromBlob(blob);
        } catch (MimetypeNotFoundException | MimetypeDetectionException e1) {
            try {
                mimeType = mimeRegistry.getMimetypeFromFile(blob.getFile());
            } catch (MimetypeNotFoundException | MimetypeDetectionException e2) {
                throw new NuxeoException("Cannot get a Mime Type from the blob or the file", e2);
            }
        }

        return mimeType;
    }

    public static boolean canGetString(Blob blob) {

        if (blob == null) {
            return false;
        }

        String mimeType = Utilities.getBlobMimeType(blob);
        switch (mimeType) {
        case "text/xml":
        case "application/xml":
        case "application/xhtml+xml":
        case "text/sgml":
            // ----------
        case "text/html":
            // ----------
        case "text/plain":
            return true;
        }

        if (mimeType.endsWith("/xml") || mimeType.endsWith("+xml") || mimeType.startsWith("text/")) {
            return true;
        }

        return false;

    }

}
