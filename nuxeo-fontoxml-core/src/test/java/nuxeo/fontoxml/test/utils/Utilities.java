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
package nuxeo.fontoxml.test.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.platform.filemanager.api.FileImporterContext;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.ecm.platform.picture.api.ImageInfo;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

public class Utilities {

    public static final String PSEUDO_XML_CONTENT = "This should be XML";

    public static DocumentModel createTestDoc(CoreSession session, boolean withBlob, String blobMimeType) {

        DocumentModel doc = session.createDocumentModel("/", "test", "File");
        doc.setPropertyValue("dc:title", "Test Doc");
        if (withBlob) {
            Blob dummyBlob = new StringBlob(PSEUDO_XML_CONTENT, blobMimeType);
            doc.setPropertyValue("file:content", (Serializable) dummyBlob);
        }

        doc = session.createDocument(doc);
        session.save();
        // When testing a real running server => flush transaction too so the server's thread can find the document
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        return doc;
    }

    public static DocumentModel createTestDoc(CoreSession session, boolean withBlob) {
        return Utilities.createTestDoc(session, withBlob, null);
    }

    public static DocumentModel createDocFromBlob(CoreSession session, String resourceFile) throws Exception {

        FileManager fileManager = Framework.getService(FileManager.class);

        // Create an asset. Using the file manager so we have the mimetype set etc.
        File f = FileUtils.getResourceFileFromContext(resourceFile);
        Blob blob = Blobs.createBlob(f);

        FileImporterContext fmContext = FileImporterContext.builder(session, blob, "/")
                                                           .overwrite(true)
                                                           .mimeTypeCheck(true)
                                                           .build();
        DocumentModel doc = fileManager.createOrUpdateDocument(fmContext);
        
        session.save();
        Utilities.waitForAsyncWorkAndStartTransaction();

        doc.refresh();
        return doc;
    }

    public static void waitForAsyncWorkAndStartTransaction() {

        EventService eventService = Framework.getService(EventService.class);

        TransactionHelper.commitOrRollbackTransaction();
        eventService.waitForAsyncCompletion();
        TransactionHelper.startTransaction();
    }
}
