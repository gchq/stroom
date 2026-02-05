/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.datagen.impl;

import stroom.datagen.shared.DataGenDoc;
import stroom.datagen.shared.DataGenDoc.Builder;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.language.SearchRequestFactory;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Message;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
class DataGenStoreImpl implements DataGenStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataGenStoreImpl.class);

    private final Store<DataGenDoc> store;
    private final SecurityContext securityContext;
    private final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider;
    private final SearchRequestFactory searchRequestFactory;
    private final Provider<DataGenProcessors> dataGenProcessorsProvider;

    @Inject
    DataGenStoreImpl(final StoreFactory storeFactory,
                     final DataGenSerialiser serialiser,
                     final SecurityContext securityContext,
                     final Provider<DataGenProcessors> dataGenProcessorsProvider,
                     final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider,
                     final SearchRequestFactory searchRequestFactory) {
        this.store = storeFactory.createStore(serialiser, DataGenDoc.TYPE, DataGenDoc::builder);
        this.securityContext = securityContext;
        this.dataSourceProviderRegistryProvider = dataSourceProviderRegistryProvider;
        this.searchRequestFactory = searchRequestFactory;
        this.dataGenProcessorsProvider = dataGenProcessorsProvider;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler

    /// /////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name) {
        final DocRef docRef = store.createDocument(name);

        // Read and write as a processing user to ensure we are allowed as documents do not have permissions added to
        // them until after they are created in the store.
        securityContext.asProcessingUser(() -> {
            final DataGenDoc dataGenDoc = store.readDocument(docRef);
            store.writeDocument(dataGenDoc);
        });
        return docRef;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        final DataGenDoc document = store.readDocument(docRef);
        return store.createDocument(newName,
                (uuid, docName, version, createTime, updateTime, createUser, updateUser) -> {
                    final Builder builder = document
                            .copy()
                            .uuid(uuid)
                            .name(docName)
                            .version(version)
                            .createTimeMs(createTime)
                            .updateTimeMs(updateTime)
                            .createUser(createUser)
                            .updateUser(updateUser);

                    return builder.build();
                });
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
        deleteProcessorFilter(docRef);
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
    // START OF HasDependencies

    /// /////////////////////////////////////////////////////////////////////

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
    // START OF DocumentActionHandler

    /// /////////////////////////////////////////////////////////////////////

    @Override
    public DataGenDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public DataGenDoc writeDocument(final DataGenDoc document) {
        return store.writeDocument(document);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler

    /// /////////////////////////////////////////////////////////////////////

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

    /// /////////////////////////////////////////////////////////////////////

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

    private void deleteProcessorFilter(final DocRef docRef) {
        try {
            final DataGenDoc dataGenDoc = readDocument(docRef);
            dataGenProcessorsProvider.get().deleteProcessorFilters(dataGenDoc);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
    }
}
