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

import java.util.Arrays;
import java.util.List;

/**
 * Centralize misc. constants, including the names of parameters/properties/query parameters as provided/expected by
 * Fonto
 * 
 * @since 10.10
 */
public class Constants {

    // ========================================> Misc. constants
    // If you change this, also change in the the misc. resources
    public static final String FONTO_PATH_NAME = "fontoxml";

    public static final String MIME_TYPE_XML = "text/xml";

    public static final String EVENT_DOC_MODIFIED_BY_FONTOWML = "documentModifiedByFontoXML";

    public static final String EVENT_CONTEXT_IS_AUTOSAVE = "isFontoAutoSave";

    // ========================================> FONTO END POINTS
    public static final String PATH_DOCUMENT = "/document";

    public static final String PATH_DOCUMENT_STATE = "/document/state";

    public static final String PATH_DOCUMENT_LOCK = "/document/lock";

    public static final String PATH_HEARTBEAT = "/heartbeat";

    public static final String PATH_BROWSE = "/browse";

    public static final String PATH_ASSET = "/asset";

    public static final String PATH_ASSET_PREVIEW = "/asset/preview";

    // ========================================> FONTO PROPERTIES/PARAMETERS/...
    public static final String PARAM_CONTEXT = "context";

    public static final String PARAM_EDIT_SESSION_TOKEN = "editSessionToken";

    public static final String PARAM_DOC_ID = "documentId";

    public static final String PARAM_ID = "id";

    public static final String PARAM_BODY = "body";

    public static final String PARAM_VARIANT = "variant";

    public static final String PARAM_CONTENT = "content";

    public static final String PARAM_LOCK = "lock";

    public static final String PARAM_LOCK_ACQUIRED = "isLockAcquired";

    public static final String PARAM_LOCK_AVAILABLE = "isLockAvailable";

    public static final String PARAM_LOCK_REASON = "reason";

    public static final String PARAM_DOCUMENTS = "documents";

    public static final String PARAM_DOCUMENT_CONTEXT = "documentContext";

    public static final String PARAM_AUTOSAVE = "autosave";

    public static final String PARAM_REVISION_ID = "revisionId";

    public static final String PARAM_METADATA = "metadata";

    public static final String PARAM_PROPERTIES = "properties";

    public static final String PARAM_REQUEST = "request";

    public static final String PARAM_FILE = "file";

    public static final String PARAM_TYPE = "type";

    public static final String PARAM_FOLDER_ID = "folderId";

    public static final String PARAM_LABEL = "label";

    public static final String VARIANT_THUMBNAIL = "thumbnail";

    public static final String VARIANT_WEB = "web";

    // ========================================> CREATING/GETING/BROWSING DOCUMENTS FROM FONTO
    // In the PARAM_METADATA object
    public static final String PARAM_FILE_NAME = "fileName";

    public static final String PARAM_FILE_EXTENSION = "fileExtension";

    public static final String PARAM_HIERARCHY = "hierarchy";
    

    // ========================================> FONTO TYPES
    public static final String FONTO_TYPE_DOCUMENT = "document";

    public static final String FONTO_TYPE_FOLDER = "folder";

    public static final String FONTO_TYPE_TEMPLATE = "document-template";

    public static final String FONTO_TYPE_FILE = "file";

    public static final String FONTO_TYPE_IMAGE = "image";

    public static final String FONTO_TYPE_AUDIO = "audio";

    public static final String FONTO_TYPE_VIDEO = "video";

    public static final String FONTO_TYPE_LINK = "link";

    public static final String FONTO_TYPE_UNKNOWN = "unknown";

    public static final String FONTO_TYPE_OUTPUT_SUPPORT = "output-support";

    // ========================================> FONTO FILE EXTENSIONS AND FILTERS DURING BROWSE
    public static final String FONTO_FILE_EXT_SETTINGS = ".settings.html";

    public static final String FONTO_FILE_EXT_TEMPLATE = ".template.html";

    public static final String FONTO_FILE_EXT_FRAGMENT = ".fragment.html";

    public static final String FONTO_FILE_EXT_DOCUMENT = ".document.html";

    public static final List<String> FONTO_DOCUMENT_FILE_EXTENSIONS = Arrays.asList(FONTO_FILE_EXT_SETTINGS,
            FONTO_FILE_EXT_TEMPLATE, FONTO_FILE_EXT_FRAGMENT, FONTO_FILE_EXT_DOCUMENT);

    public static final List<String> FONTO_DOCUMENT_FILE_EXTENSIONS_ARE_XML = Arrays.asList(FONTO_FILE_EXT_SETTINGS,
            FONTO_FILE_EXT_TEMPLATE, FONTO_FILE_EXT_FRAGMENT);

    public static final String FONTO_FILE_EXT_CSS = ".css";

    public static final String FONTO_FILE_EXT_HEADERFOOTER = ".hf.html";

    public static final List<String> FONTO_OUTPUTSUPPORT_FILE_EXTENSIONS = Arrays.asList(FONTO_FILE_EXT_CSS,
            FONTO_FILE_EXT_HEADERFOOTER);

    // ========================================> CUSTOM INFO SEND (in context, mainly)
    public static final String DOC_UUID = "uuid";

    public static final String DOC_TYPE = "type";

    public static final String DOC_STATE = "state";

}
