package stroom.view.impl;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.security.api.SecurityContext;
import stroom.view.shared.ViewDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;

public class ViewQueryService implements DataSourceProvider {

    private final ViewStore viewStore;
    private final SecurityContext securityContext;
    private final Provider<DataSourceProviderRegistry> dataSourceProviderRegistry;
    private final SearchResponseCreatorManager searchResponseCreatorManager;

    @Inject
    public ViewQueryService(final ViewStore viewStore,
                            final SecurityContext securityContext,
                            final Provider<DataSourceProviderRegistry> dataSourceProviderRegistry,
                            final SearchResponseCreatorManager searchResponseCreatorManager) {
        this.viewStore = viewStore;
        this.securityContext = securityContext;
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final ViewDoc viewDoc = getView(docRef);
            return getDelegateDataSourceProvider(viewDoc).getDataSource(viewDoc.getDataSource());
        });
    }

    private ViewDoc getView(final DocRef docRef) {
        final ViewDoc viewDoc = viewStore.readDocument(docRef);
        if (viewDoc == null) {
            throw new RuntimeException("Unable to load view " + docRef);
        }
        return viewDoc;
    }

    @Override
    public SearchResponse search(final SearchRequest request) {
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
        return getDelegateDataSourceProvider(viewDoc).search(modifiedSearchRequest);
    }

    private DataSourceProvider getDelegateDataSourceProvider(final ViewDoc viewDoc) {
        // Find the referenced data source.
        final DocRef dataSource = viewDoc.getDataSource();
        if (dataSource == null) {
            throw new RuntimeException("Null datasource in view " + DocRefUtil.create(viewDoc));
        }

        final Optional<DataSourceProvider> delegate =
                dataSourceProviderRegistry.get().getDataSourceProvider(dataSource);
        if (delegate.isEmpty()) {
            throw new RuntimeException("No data source provider found for " + dataSource);
        }

        return delegate.get();
    }

    @Override
    public Boolean keepAlive(final QueryKey queryKey) {
        return searchResponseCreatorManager.keepAlive(queryKey);
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        return searchResponseCreatorManager.remove(queryKey);
    }

    @Override
    public String getType() {
        return ViewDoc.DOCUMENT_TYPE;
    }
}
