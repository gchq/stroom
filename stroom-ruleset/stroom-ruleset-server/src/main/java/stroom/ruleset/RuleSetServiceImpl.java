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

package stroom.ruleset;

import stroom.docstore.JsonSerialiser2;
import stroom.docstore.Store;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.docref.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.ruleset.shared.RuleSet;
import stroom.util.shared.Message;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class RuleSetServiceImpl implements RuleSetService {
    private final Store<RuleSet> store;

    @Inject
    public RuleSetServiceImpl(final Store<RuleSet> store) {
        this.store = store;
        store.setType(RuleSet.DOCUMENT_TYPE, RuleSet.class);
        store.setSerialiser(new JsonSerialiser2<>(RuleSet.class));
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name) {
        return store.createDocument(name);
    }

    @Override
    public DocRef copyDocument(final String originalUuid,
                               final String copyUuid,
                               final Map<String, String> otherCopiesByOriginalUuid) {
        return store.copyDocument(originalUuid, copyUuid, otherCopiesByOriginalUuid);
    }

    @Override
    public DocRef moveDocument(final String uuid) {
        return store.moveDocument(uuid);
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        return store.renameDocument(uuid, name);
    }

    @Override
    public void deleteDocument(final String uuid) {
        store.deleteDocument(uuid);
    }

    @Override
    public DocRefInfo info(final String uuid) {
        return store.info(uuid);
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(100, RuleSet.DOCUMENT_TYPE, "Rule Set");
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public RuleSet readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public RuleSet writeDocument(final RuleSet document) {
        return store.writeDocument(document);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Set<DocRef> listDocuments() {
        return store.listDocuments();
    }

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return store.getDependencies();
    }

    @Override
    public DocRef importDocument(final DocRef docRef, final Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode) {
        return store.importDocument(docRef, dataMap, importState, importMode);
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        return store.exportDocument(docRef, omitAuditFields, messageList);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////
}
