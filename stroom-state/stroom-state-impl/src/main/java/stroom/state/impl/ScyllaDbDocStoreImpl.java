/*
 * Copyright 2016-2025 Crown Copyright
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
 */

package stroom.state.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.state.shared.ScyllaDbDoc;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
public class ScyllaDbDocStoreImpl implements ScyllaDbDocStore {

    private final Store<ScyllaDbDoc> store;

    @Inject
    public ScyllaDbDocStoreImpl(
            final StoreFactory storeFactory,
            final ScyllaDbSerialiser serialiser) {
        this.store = storeFactory.createStore(serialiser, ScyllaDbDoc.TYPE, ScyllaDbDoc::builder);
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name) {
        final DocRef docRef = store.createDocument(name);
        final ScyllaDbDoc doc = store.readDocument(docRef);
        doc.setConnection(ScyllaDbUtil.getDefaultConnection());
        doc.setKeyspace(ScyllaDbUtil.getDefaultKeyspace());
        doc.setKeyspaceCql(ScyllaDbUtil.getDefaultKeyspaceCql());
        store.writeDocument(doc);
        return docRef;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        return store.copyDocument(docRef.getUuid(), newName);
    }

    @Override
    public DocRef moveDocument(final DocRef docRef) {
        return store.moveDocument(docRef);
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        return store.renameDocument(docRef, name);
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        store.deleteDocument(docRef);
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        return store.info(docRef);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public ScyllaDbDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public ScyllaDbDoc writeDocument(final ScyllaDbDoc document) {
        validateKeyspace(document);
        return store.writeDocument(document);
    }

    private void validateKeyspace(final ScyllaDbDoc document) {
        if (NullSafe.isBlankString(document.getKeyspace())) {
            throw new EntityServiceException("No keyspace name has been defined for '" +
                                             document.getName() +
                                             "'");
        }

        if (!ScyllaDbNameValidator.isValidName(document.getKeyspace())) {
            throw new EntityServiceException("The keyspace name must match the pattern '" +
                                             ScyllaDbNameValidator.getPattern() +
                                             "'");
        }

        // Validate that the keyspace CQL has the correct keyspace name else bad things could happen.
        if (NullSafe.isBlankString(document.getKeyspaceCql())) {
            throw new EntityServiceException("No keyspace CQL has been defined for '" +
                                             document.getName() +
                                             "'");
        }

        final Optional<String> keyspace = ScyllaDbUtil.extractKeyspaceNameFromCql(document.getKeyspaceCql());
        if (keyspace.isEmpty()) {
            throw new EntityServiceException("Unable to determine keyspace name from keyspace CQL in '" +
                                             document.getName() +
                                             "'");
        }

        if (!keyspace.get().equals(document.getKeyspace())) {
            throw new EntityServiceException("Keyspace name '" +
                                             keyspace.get() +
                                             "' in CQL does not match keyspace name '" +
                                             document.getKeyspace() +
                                             "'");
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return store.getDependencies(null);
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return store.getDependencies(docRef, null);
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        store.remapDependencies(docRef, remappings, null);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Set<DocRef> listDocuments() {
        return store.listDocuments();
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        return store.importDocument(docRef, dataMap, importState, importSettings);
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {
        if (omitAuditFields) {
            return store.exportDocument(docRef, messageList, new AuditFieldFilter<>());
        }
        return store.exportDocument(docRef, messageList, d -> d);
    }

    @Override
    public String getType() {
        return store.getType();
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public List<DocRef> list() {
        return store.list();
    }

    @Override
    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
        return store.findByNames(name, allowWildCards);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }
}
