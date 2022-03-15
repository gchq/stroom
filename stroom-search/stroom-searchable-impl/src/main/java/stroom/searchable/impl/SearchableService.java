package stroom.searchable.impl;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docref.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.searchable.api.Searchable;
import stroom.searchable.api.SearchableProvider;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;

@SuppressWarnings("unused")
// Used by DI
class SearchableService implements DataSourceProvider {

    public static final long PROCESS_PAYLOAD_INTERVAL_SECS = 1L;
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchableService.class);
    private final SearchableProvider searchableProvider;
    private final SearchResponseCreatorManager searchResponseCreatorManager;
    private final SearchableStoreFactory storeFactory;
    private final SecurityContext securityContext;

    @Inject
    SearchableService(final SearchableProvider searchableProvider,
                      final SearchResponseCreatorManager searchResponseCreatorManager,
                      final SearchableStoreFactory storeFactory,
                      final SecurityContext securityContext) {
        this.searchableProvider = searchableProvider;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.storeFactory = storeFactory;
        this.securityContext = securityContext;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            LOGGER.debug(() -> "getDataSource called for docRef " + docRef);
            final Searchable searchable = searchableProvider.get(docRef);
            if (searchable == null) {
                return null;
            }
            return searchable.getDataSource();
        });
    }

    @Override
    public SearchResponse search(final SearchRequest searchRequest) {
        return searchResponseCreatorManager.search(storeFactory, searchRequest);
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
        return "Searchable";
    }
}
