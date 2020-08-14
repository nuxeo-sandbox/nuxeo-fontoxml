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

import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;

/**
 * This class encapsulates the value stored in the editSessionToken that is passed back by
 * FontoXML front-end in several calls.
 * It stores the doc. UID of the "main" document and a unicityToken (number) that makes the whole thing unique for Fonto
 * in the front end.
 * The UUID of the document is used in some context, when creating a new doc/asset etc.
 * <br/>
 * It is initialized
 * <a href="https://documentation.fontoxml.com/editor/latest/invocation-of-the-fonto-editor-30015537.html">in the
 * frontend</a> when invoking the Fonto UI. initSessionToken must be a JSON string with 2 properties, mainDocId and
 * unicityToken
 * <code>
 * ...
 * initSessionToken="{\"mainDocId": this.document.uuid, \"unicityToken\":Date.now()}"
 * ...
 * </code>
 * 
 * @since 10.10
 */
public class EditSessionToken {

    protected JSONObject editSessionToken;

    protected String mainDocId = null;

    protected double unicityToken;

    public EditSessionToken(String jsonString) throws JSONException {
        
        init(new JSONObject(jsonString));

    }

    public EditSessionToken(JSONObject json) throws JSONException {
        
        init(json);

    }
    
    protected void init(JSONObject json) throws JSONException {
        
        editSessionToken = json;
        mainDocId = editSessionToken.getString("mainDocId");
        unicityToken = editSessionToken.getDouble("unicityToken");
        
    }

    public String getMainDocId() {
        return mainDocId;
    }
    
    public DocumentModel getMainDocument(CoreSession session) {
        
        return session.getDocument(new IdRef(mainDocId));
        
    }
    
    public DocumentModel getContainer(CoreSession session) {
        
        DocumentModel doc = getMainDocument(session);
        if(doc.isFolder()) {
            return doc;
        }
                
        return session.getParentDocument(new IdRef(mainDocId));
    }
}
