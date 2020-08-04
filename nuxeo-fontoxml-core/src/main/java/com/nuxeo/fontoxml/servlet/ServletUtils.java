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

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;

/**
 * @since 10.10
 */
public class ServletUtils {

    public static void sendStringResponse(HttpServletResponse resp, int status, String response) throws IOException {

        resp.setStatus(status);

        if (StringUtils.isNotBlank(response)) {
            resp.setContentType("application/json");
            resp.setContentLength(response.getBytes().length);
            OutputStream out = resp.getOutputStream();
            out.write(response.getBytes());
            out.close();
        }
    }

    public static void sendOKStringResponse(HttpServletResponse resp, String response) throws IOException {

        sendStringResponse(resp, HttpServletResponse.SC_OK, response);
    }
    
    public static Blob createBlobFromPart(Part part) throws IOException {
        
        Blob b = null;
        
        b = Blobs.createBlob(part.getInputStream());
        b.setFilename(retrieveFilename(part));
        b.setMimeType(part.getContentType());
        
        return b;
    }

    /**
     * INFO: This is a copy-paste from org.nuxeo.ecm.platform.ui.web.util.files.FileUtils, but this one may be
     * deprecated since it is mainly used by JSF
     * =============================================
     * Helper method to retrieve filename from part, waiting for servlet-api related improvements.
     * <p>
     * Filename is cleaned before being returned.
     *
     * @see #getCleanFileName(String)
     * @since 7.1
     */
    public static String retrieveFilename(Part part) {
        for (String cd : part.getHeader("content-disposition").split(";")) {
            if (cd.trim().startsWith("filename")) {
                String filename = cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
                return getCleanFileName(filename);
            }
        }
        return null;
    }

    /**
     * INFO: This is a copy-paste from org.nuxeo.ecm.platform.ui.web.util.files.FileUtils, but this one may be
     * deprecated since it is mainly used by JSF
     * =============================================
     * Returns a clean filename, stripping upload path on client side.
     * For instance, it turns "/tmp/2349876398/foo.pdf" into "foo.pdf"
     * <p>
     * Fixes NXP-544
     *
     * @param filename the filename
     * @return the stripped filename
     */
    public static String getCleanFileName(String filename) {
        String res = null;
        int lastWinSeparator = filename.lastIndexOf('\\');
        int lastUnixSeparator = filename.lastIndexOf('/');
        int lastSeparator = Math.max(lastWinSeparator, lastUnixSeparator);
        if (lastSeparator != -1) {
            res = filename.substring(lastSeparator + 1, filename.length());
        } else {
            res = filename;
        }
        return res;
    }

}
