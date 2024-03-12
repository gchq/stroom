package stroom.view.impl;

import stroom.datasource.api.v2.DataSourceProvider;
import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.docstore.shared.DocRefUtil;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldProvider;
import stroom.index.shared.LuceneIndexField;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.SearchProvider;
import stroom.query.common.v2.StoreFactoryRegistry;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
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
    private final Provider<StoreFactoryRegistry> storeFactoryRegistryProvider;
    private final SecurityContext securityContext;
    private final Provider<DataSourceProviderRegistry> dataSourceProviderRegistry;

    @Inject
    public ViewSearchProvider(final ViewStore viewStore,
                              final Provider<StoreFactoryRegistry> storeFactoryRegistryProvider,
                              final SecurityContext securityContext,
                              final Provider<DataSourceProviderRegistry> dataSourceProviderRegistry) {
        this.viewStore = viewStore;
        this.storeFactoryRegistryProvider = storeFactoryRegistryProvider;
        this.securityContext = securityContext;
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
    }

    @Override
    public ResultPage<FieldInfo> getFieldInfo(final FindFieldInfoCriteria criteria) {
        final Optional<ResultPage<FieldInfo>> optional = securityContext.useAsReadResult(() -> {
            final ViewDoc viewDoc = viewStore.readDocument(criteria.getDataSourceRef());
            if (viewDoc != null) {
                // Find the referenced data source.
                final DocRef docRef = viewDoc.getDataSource();
                if (docRef != null) {
                    final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                            criteria.getPageRequest(),
                            criteria.getSortList(),
                            docRef,
                            criteria.getStringMatch());
                    final Optional<DataSourceProvider> delegate =
                            dataSourceProviderRegistry.get().getDataSourceProvider(docRef.getType());
                    return delegate.map(dataSourceProvider -> dataSourceProvider.getFieldInfo(findFieldInfoCriteria));
                }
            }
            return Optional.empty();
        });
        return optional.orElseGet(() -> {
            final List<FieldInfo> list = Collections.emptyList();
            return ResultPage.createCriterialBasedList(list, criteria);
        });
    }

    @Override
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {
        final FindFieldInfoCriteria criteria = new FindFieldInfoCriteria(
                new PageRequest(0, 1),
                null,
                docRef,
                StringMatch.equals(fieldName, true));
        final ResultPage<FieldInfo> resultPage = getFieldInfo(criteria);
        if (resultPage.size() > 0) {
            final FieldInfo fieldInfo = resultPage.getFirst();
            return LuceneIndexField
                    .builder()
                    .name(fieldInfo.getName())
                    .type(fieldInfo.getType())
                    .indexed(true)
                    .build();
        }
        return null;
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(viewStore.readDocument(docRef)).map(ViewDoc::getDescription);
    }

    @Override
    public DocRef fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return securityContext.useAsReadResult(() -> {
            final ViewDoc viewDoc = viewStore.readDocument(dataSourceRef);
            if (viewDoc != null) {
                return viewDoc.getPipeline();
            }
            return null;
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
                storeFactoryRegistryProvider.get().getStoreFactory(dataSource);
        if (delegate.isEmpty()) {
            throw new RuntimeException("No data source provider found for " + dataSource);
        }

        return delegate.get();
    }

    @Override
    public QueryField getTimeField(final DocRef docRef) {
        final ViewDoc viewDoc = getView(docRef);
        return getDelegateStoreFactory(viewDoc).getTimeField(viewDoc.getDataSource());
    }

    @Override
    public List<DocRef> list() {
        return viewStore.list();
    }

    @Override
    public String getType() {
        return ViewDoc.DOCUMENT_TYPE;
    }
}
