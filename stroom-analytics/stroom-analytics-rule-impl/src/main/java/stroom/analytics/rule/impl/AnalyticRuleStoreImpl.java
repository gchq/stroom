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

package stroom.analytics.rule.impl;

import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleDoc.Builder;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.DependencyRemapper;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

@Singleton
class AnalyticRuleStoreImpl implements AnalyticRuleStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticRuleStoreImpl.class);

    private final Store<AnalyticRuleDoc> store;
    private final SecurityContext securityContext;
    private final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider;
    private final SearchRequestFactory searchRequestFactory;
    private final Provider<AnalyticRuleProcessors> analyticRuleProcessorsProvider;

    @Inject
    AnalyticRuleStoreImpl(final StoreFactory storeFactory,
                          final AnalyticRuleSerialiser serialiser,
                          final SecurityContext securityContext,
                          final Provider<AnalyticRuleProcessors> analyticRuleProcessorsProvider,
                          final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider,
                          final SearchRequestFactory searchRequestFactory) {
        this.store = storeFactory.createStore(serialiser, AnalyticRuleDoc.TYPE, AnalyticRuleDoc::builder);
        this.securityContext = securityContext;
        this.dataSourceProviderRegistryProvider = dataSourceProviderRegistryProvider;
        this.searchRequestFactory = searchRequestFactory;
        this.analyticRuleProcessorsProvider = analyticRuleProcessorsProvider;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name) {
        final DocRef docRef = store.createDocument(name);

        // Read and write as a processing user to ensure we are allowed as documents do not have permissions added to
        // them until after they are created in the store.
        securityContext.asProcessingUser(() -> {
            final AnalyticRuleDoc analyticRuleDoc = store.readDocument(docRef);
            store.writeDocument(analyticRuleDoc);
        });
        return docRef;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        final AnalyticRuleDoc document = store.readDocument(docRef);
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

                    final AnalyticProcessConfig analyticProcessConfig = document.getAnalyticProcessConfig();
                    if (analyticProcessConfig != null) {
//                        if (analyticProcessConfig instanceof
//                                final ScheduledQueryAnalyticProcessConfig scheduledQueryAnalyticProcessConfig) {
//                            builder.analyticProcessConfig(
//                                    scheduledQueryAnalyticProcessConfig.copy().enabled(false).build());
//                        } else
                        if (analyticProcessConfig instanceof
                                final TableBuilderAnalyticProcessConfig tableBuilderAnalyticProcessConfig) {
                            builder.analyticProcessConfig(
                                    tableBuilderAnalyticProcessConfig.copy().enabled(false).build());
                        }
//                        } else if (analyticProcessConfig instanceof
//                                final StreamingAnalyticProcessConfig streamingAnalyticProcessConfig) {
////                            builder.analyticProcessConfig(
////                                    streamingAnalyticProcessConfig.copy().enabled(false).build());
//                        }

                        builder.analyticProcessConfig(analyticProcessConfig);
                    }

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
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return store.getDependencies(createMapper());
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return store.getDependencies(docRef, createMapper());
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        store.remapDependencies(docRef, remappings, createMapper());
    }

    private BiConsumer<AnalyticRuleDoc, DependencyRemapper> createMapper() {
        return (doc, dependencyRemapper) -> {
            try {
                if (doc.getQuery() != null) {
                    searchRequestFactory.extractDataSourceOnly(doc.getQuery(), docRef -> {
                        try {
                            if (docRef != null) {
                                final DataSourceProviderRegistry dataSourceProviderRegistry =
                                        dataSourceProviderRegistryProvider.get();
                                final Optional<DocRef> optional = dataSourceProviderRegistry
                                        .getDataSourceDocRefs()
                                        .stream()
                                        .filter(dr -> dr.equals(docRef))
                                        .findAny();
                                optional.ifPresent(dataSourceRef -> {
                                    final DocRef remapped = dependencyRemapper.remap(dataSourceRef);
                                    if (remapped != null) {
                                        String query = doc.getQuery();
                                        if (remapped.getName() != null &&
                                            !remapped.getName().isBlank() &&
                                            !Objects.equals(remapped.getName(), docRef.getName())) {
                                            query = query.replaceFirst(docRef.getName(), remapped.getName());
                                        }
                                        if (remapped.getUuid() != null &&
                                            !remapped.getUuid().isBlank() &&
                                            !Objects.equals(remapped.getUuid(), docRef.getUuid())) {
                                            query = query.replaceFirst(docRef.getUuid(), remapped.getUuid());
                                        }
                                        doc.setQuery(query);
                                    }
                                });
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.debug(e::getMessage, e);
                        }
                    });
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
        };
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public AnalyticRuleDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public AnalyticRuleDoc writeDocument(final AnalyticRuleDoc document) {
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

    private void deleteProcessorFilter(final DocRef docRef) {
        try {
            final AnalyticRuleDoc analyticRuleDoc = readDocument(docRef);
            analyticRuleProcessorsProvider.get().deleteProcessorFilters(analyticRuleDoc);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
    }
}
