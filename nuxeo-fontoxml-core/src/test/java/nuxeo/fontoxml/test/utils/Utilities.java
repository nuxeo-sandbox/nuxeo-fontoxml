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

import java.io.File;
import java.io.Serializable;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.platform.filemanager.api.FileImporterContext;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

public class Utilities {

    public static final String PSEUDO_XML_CONTENT = "This should be XML";
    
    public static DocumentModel createDocumentFromFile(CoreSession session, String parentPath, String inType,
            String inResourceFilePath, String mimeType) {

        File f = FileUtils.getResourceFileFromContext(inResourceFilePath);
        Blob blob = new FileBlob(f);
        if(mimeType != null) {
            blob.setMimeType(mimeType);
        }

        DocumentModel doc = session.createDocumentModel(parentPath, f.getName(), inType);
        doc.setPropertyValue("dc:title", f.getName());
        doc.setPropertyValue("file:content", (Serializable) blob);
        return session.createDocument(doc);

    }

    public static DocumentModel createDocumentFromFile(CoreSession session, DocumentModel inParent, String inType,
            String inResourceFilePath, String mimeType) {

        return createDocumentFromFile(session, inParent.getPathAsString(), inType, inResourceFilePath, mimeType);

    }

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

    public static void waitForAsyncWorkAndStartTransaction(CoreSession session) {


        session.save();

        EventService eventService = Framework.getService(EventService.class);
        TransactionHelper.commitOrRollbackTransaction();
        eventService.waitForAsyncCompletion();
        TransactionHelper.startTransaction();
    }
}
