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

package stroom.view.impl;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.query.api.Query;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.TableSettings;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.IndexFieldProviders;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.SearchProvider;
import stroom.query.common.v2.SearchProviderRegistry;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;
import stroom.view.api.ViewStore;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("unused")
public class ViewSearchProvider implements SearchProvider, IndexFieldProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ViewSearchProvider.class);

    private final ViewStore viewStore;
    private final Provider<SearchProviderRegistry> storeFactoryRegistryProvider;
    private final SecurityContext securityContext;
    private final Provider<DataSourceProviderRegistry> dataSourceProviderRegistry;
    private final IndexFieldProviders indexFieldProviders;

    @Inject
    public ViewSearchProvider(final ViewStore viewStore,
                              final Provider<SearchProviderRegistry> storeFactoryRegistryProvider,
                              final SecurityContext securityContext,
                              final Provider<DataSourceProviderRegistry> dataSourceProviderRegistry,
                              final IndexFieldProviders indexFieldProviders) {
        this.viewStore = viewStore;
        this.storeFactoryRegistryProvider = storeFactoryRegistryProvider;
        this.securityContext = securityContext;
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
        this.indexFieldProviders = indexFieldProviders;
    }

    private DocRef getReferencedDataSource(final DocRef viewDocRef) {
        final ViewDoc viewDoc = viewStore.readDocument(viewDocRef);
        if (viewDoc != null) {
            // Find the referenced data source.
            return viewDoc.getDataSource();
        }
        return null;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        final Optional<ResultPage<QueryField>> optional = securityContext.useAsReadResult(() -> {
            // Find the referenced data source.
            final DocRef docRef = getReferencedDataSource(criteria.getDataSourceRef());
            if (docRef != null) {
                final FindFieldCriteria findFieldInfoCriteria = new FindFieldCriteria(
                        criteria.getPageRequest(),
                        criteria.getSortList(),
                        docRef,
                        criteria.getFilter(),
                        criteria.getQueryable());
                final Optional<DataSourceProvider> delegate =
                        dataSourceProviderRegistry.get().getDataSourceProvider(docRef.getType());
                return delegate.map(dataSourceProvider -> dataSourceProvider.getFieldInfo(findFieldInfoCriteria));
            }
            return Optional.empty();
        });
        return optional.orElseGet(() -> {
            final List<QueryField> list = Collections.emptyList();
            return ResultPage.createCriterialBasedList(list, criteria);
        });
    }

    @Override
    public int getFieldCount(final DocRef viewDocRef) {
        return securityContext.useAsReadResult(() -> {
            final DocRef dataSourceRef = getReferencedDataSource(viewDocRef);
            if (dataSourceRef != null) {
                final Optional<DataSourceProvider> optDelegate =
                        dataSourceProviderRegistry.get()
                                .getDataSourceProvider(dataSourceRef.getType());
                return optDelegate.map(dataSourceProvider -> dataSourceProvider.getFieldCount(dataSourceRef))
                        .orElse(0);
            } else {
                return 0;
            }
        });
    }

    @Override
    public IndexField getIndexField(final DocRef viewDocRef, final String fieldName) {
        final DocRef docRef = getReferencedDataSource(viewDocRef);
        if (docRef != null) {
            return indexFieldProviders.getIndexField(docRef, fieldName);
        }
        return null;
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(viewStore.readDocument(docRef)).map(ViewDoc::getDescription);
    }

    @Override
    public Optional<DocRef> fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return securityContext.useAsReadResult(() -> {
            final ViewDoc viewDoc = viewStore.readDocument(dataSourceRef);
            return Optional.ofNullable(viewDoc).map(ViewDoc::getPipeline);
        });
    }

    @Override
    public ResultStore createResultStore(final SearchRequest request) {
        final ViewDoc viewDoc = getView(request.getQuery().getDataSource());

        final List<ResultRequest> resultRequests = request.getResultRequests();
        List<ResultRequest> modifiedResultRequests = null;
        if (resultRequests != null) {
            modifiedResultRequests = new ArrayList<>(resultRequests.size());
            for (final ResultRequest resultRequest : resultRequests) {
                final List<TableSettings> mappings = resultRequest.getMappings();
                if (mappings != null) {
                    final List<TableSettings> modifiedMappings = new ArrayList<>(mappings.size());
                    // Only modify first.
                    boolean first = true;
                    for (final TableSettings mapping : mappings) {
                        if (first) {
                            modifiedMappings.add(mapping.copy().extractionPipeline(viewDoc.getPipeline()).build());
                        } else {
                            modifiedMappings.add(mapping);
                        }
                        first = false;
                    }
                    modifiedResultRequests.add(resultRequest
                            .copy()
                            .mappings(modifiedMappings)
                            .build());
                } else {
                    modifiedResultRequests.add(resultRequest);
                }
            }
        }

        final DocRef dataSource = viewDoc.getDataSource();
        final Query modifiedQuery = request.getQuery().copy().dataSource(dataSource).build();

        final SearchRequest modifiedSearchRequest = request
                .copy()
                .query(modifiedQuery)
                .resultRequests(modifiedResultRequests)
                .build();

        // TODO : ADD SOMETHING FOR VIEWDOC FILTER.

        // Find the referenced data source.
        return getDelegateStoreFactory(viewDoc).createResultStore(modifiedSearchRequest);
    }

    private ViewDoc getView(final DocRef docRef) {
        final ViewDoc viewDoc = viewStore.readDocument(docRef);
        if (viewDoc == null) {
            throw new RuntimeException("Unable to load view " + docRef);
        }
        return viewDoc;
    }

    private SearchProvider getDelegateStoreFactory(final ViewDoc viewDoc) {
        // Find the referenced data source.
        final DocRef dataSource = viewDoc.getDataSource();
        if (dataSource == null) {
            throw new RuntimeException("Null datasource in view " + DocRefUtil.create(viewDoc));
        }

        final Optional<SearchProvider> delegate =
                storeFactoryRegistryProvider.get().getSearchProvider(dataSource);
        if (delegate.isEmpty()) {
            throw new RuntimeException("No data source provider found for " + dataSource);
        }

        return delegate.get();
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        final ViewDoc viewDoc = getView(docRef);
        return getDelegateStoreFactory(viewDoc).getTimeField(viewDoc.getDataSource());
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return viewStore.list();
    }

    @Override
    public String getDataSourceType() {
        return ViewDoc.TYPE;
    }
}
