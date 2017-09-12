/*
 * Copyright 2017 Crown Copyright
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
 */

package stroom.ruleset.server;

import org.springframework.stereotype.Component;
import stroom.document.server.fs.FSDocumentStore;
import stroom.entity.shared.ImportState;
import stroom.entity.shared.ImportState.ImportMode;
import stroom.query.api.v1.DocRef;
import stroom.ruleset.shared.RuleSet;
import stroom.security.SecurityContext;
import stroom.util.shared.Message;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Component
public class RuleSetServiceImpl implements RuleSetService {
    private final FSDocumentStore<RuleSet> documentStore;

    @Inject
    public RuleSetServiceImpl(final SecurityContext securityContext) throws IOException {
        documentStore = new FSDocumentStore<>(Paths.get("/Users/stroomdev66/tmp/config/" + RuleSet.DOCUMENT_TYPE), RuleSet.DOCUMENT_TYPE, RuleSet.class, securityContext);
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name, final String parentFolderUUID) {
        return documentStore.createDocument(parentFolderUUID, name);
    }

    @Override
    public DocRef copyDocument(final String uuid, final String parentFolderUUID) {
        return documentStore.copyDocument(uuid, parentFolderUUID);
    }

    @Override
    public DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        return documentStore.moveDocument(uuid, parentFolderUUID);
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        return documentStore.renameDocument(uuid, name);
    }

    @Override
    public void deleteDocument(final String uuid) {
        documentStore.deleteDocument(uuid);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public RuleSet readDocument(final DocRef docRef) {
        return documentStore.readDocument(docRef);
    }

    @Override
    public RuleSet writeDocument(final RuleSet document) {
        return documentStore.writeDocument(document);
    }

    @Override
    public RuleSet forkDocument(final RuleSet document, String name, DocRef destinationFolderRef) {
        return documentStore.forkDocument(document, name, destinationFolderRef);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef importDocument(final DocRef docRef, final Map<String, String> dataMap, final ImportState importState, final ImportMode importMode) {
        return documentStore.importDocument(docRef, dataMap, importState, importMode);
    }

    @Override
    public Map<String, String> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        return documentStore.exportDocument(docRef, omitAuditFields, messageList);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public String getDocType() {
        return RuleSet.DOCUMENT_TYPE;
    }

    @Override
    public RuleSet read(final String uuid) {
        return documentStore.read(uuid);
    }

    @Override
    public RuleSet update(final RuleSet dataReceiptPolicy) {
        return documentStore.update(dataReceiptPolicy);
    }
}
