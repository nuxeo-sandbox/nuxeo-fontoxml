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

    /**
     * Returns the mime-type of the blob.
     * <ul>
     * <li>If the blob is <code>null</code>, returns <code>null</code></li>
     * <li>If the blob already has a mime-type, returns it</li>
     * <li>Else, uses the <code>MimetypeRegistry</code> service to get the mime-type</li>
     * </ul>
     * 
     * @param blob
     * @return the mime type
     * @since 10.10
     */
    public static String getBlobMimeType(Blob blob, boolean setIfEmpty) {

        if (blob == null) {
            return null;
        }

        String mimeType = blob.getMimeType();
        if (StringUtils.isNotBlank(mimeType)) {
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
        
        if(setIfEmpty) {
            blob.setMimeType(mimeType);
        }

        return mimeType;
    }

    /**
     * This is based on the mime type. text/xml, application/xml, application/xhtml+xml, ...
     * 
     * @param blob
     * @return true if the blob contains xml
     * @since 10.10
     */
    public static boolean looksLikeXml(Blob blob) {

        if (blob == null) {
            return false;
        }

        String mimeType = Utilities.getBlobMimeType(blob, true);
        if (mimeType.startsWith("application/xml") || mimeType.endsWith("xml")) {
            return true;
        }

        return false;

    }

    /**
     * Checks if calling <code>blob.getString()</code> will return a "regular" text String.
     * This is based on the mimetype.
     * 
     * @param blob
     * @return boolean <code>true</code> if the blob contains text-based content
     * @since 10.10
     */
    public static boolean canGetString(Blob blob) {

        if (blob == null) {
            return false;
        }

        String mimeType = Utilities.getBlobMimeType(blob, true);
        /*
         * text/xml, text/plain, text/xml, text/css, text/sgml, ...
         * application/xml, application/xhtml+xml, ...
         */
        if (mimeType.startsWith("text/") || Utilities.looksLikeXml(blob)) {
            return true;
        }

        return false;

    }

    /**
     * Checks if the blob can be fetched by fonto later, whenj calling GET /document
     * This depends on the fonto distribution used
     * TODO improve the service to adapt to the Fonto disctibution (F4B, DITA, ...)
     * 
     * @param blob
     * @return true is the blob can be fetched by Fonto using GET /document
     * @since 10.10
     */
    public static boolean isOkForFontoGETDocument(Blob blob) {

        if (!Utilities.canGetString(blob)) {
            return false;
        }

        String fileName = blob.getFilename();
        for (String fileExt : Constants.FONTO_DOCUMENT_FILE_EXTENSIONS) {
            if (fileName.endsWith(fileExt)) {
                return true;
            }
        }

        // Last, if it's XML, then return true
        return Utilities.looksLikeXml(blob);
    }

}
