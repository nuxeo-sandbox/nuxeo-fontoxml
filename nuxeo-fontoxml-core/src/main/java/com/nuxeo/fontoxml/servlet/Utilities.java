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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
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
     * <li>If the file name ends with some specificfonto extension,w e hard code the mime-type</li
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

        String fileName = blob.getFilename();
        if (fileName != null) {
            for (String ext : Constants.FONTO_DOCUMENT_FILE_EXTENSIONS_ARE_XML) {
                if (fileName.endsWith(ext)) {
                    mimeType = "text/xml";
                    break;
                }
            }
        }

        if (StringUtils.isBlank(mimeType)) {
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
        }

        if (setIfEmpty) {
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
     * Checks if the blob can be fetched by fonto later, when calling GET /document
     * This depends on the fonto distribution used
     * TODO improve the service to adapt to the Fonto distribution (F4B, DITA, ...)
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
        if (fileName != null) {
            for (String fileExt : Constants.FONTO_DOCUMENT_FILE_EXTENSIONS) {
                if (fileName.endsWith(fileExt)) {
                    return true;
                }
            }
        }

        // Last, if it's XML, then return true
        return Utilities.looksLikeXml(blob);
    }

    /**
     * Only for F4D.
     * 
     * @param blob
     * @return true is the blob can be fetched by Fonto using PÖST /browse and assetType "output-support"
     * @since 10.10
     */
    public static boolean isOkForFontoPOSTBrowseOutputSupport(Blob blob) {

        if (!Utilities.canGetString(blob)) {
            return false;
        }

        String fileName = blob.getFilename();
        if (fileName != null) {
            for (String fileExt : Constants.FONTO_OUTPUTSUPPORT_FILE_EXTENSIONS) {
                if (fileName.endsWith(fileExt)) {
                    return true;
                }
            }
        }

        return false;
    }
    
    /**
     * Returns a JSON array of the path to the doc, with properties expected by Fonto (id, label, type)
     * 
     * @param doc
     * @return the JSONArray containing the hierarchy (the path + details)
     * @throws JSONException 
     * @since 10.10
     */
    public static JSONArray buildHierarchy(DocumentModel doc) throws JSONException {
        
        JSONArray array = new JSONArray();
        
        // getParentDocuments also returns the document itseld
        List<DocumentModel> parents = doc.getCoreSession().getParentDocuments(doc.getRef());
        for(DocumentModel aParent : parents) {
            JSONObject obj = new JSONObject();
            obj.put("id", aParent.getId());
            obj.put("label", aParent.getTitle());
            if(aParent.isFolder()) {
                obj.put("type", Constants.FONTO_TYPE_FOLDER);
            } else {
                obj.put("type", Constants.FONTO_TYPE_FILE);
            }
            
            array.put(obj);
        }
        
        return array;
    }

}
