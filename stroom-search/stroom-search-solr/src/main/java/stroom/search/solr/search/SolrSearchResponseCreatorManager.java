package stroom.search.solr.search;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorCache.Key;
import stroom.query.common.v2.SearchResponseCreatorFactory;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.query.common.v2.Store;
import stroom.util.shared.Clearable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@SuppressWarnings("unused") //Used by DI
@Singleton
public class SolrSearchResponseCreatorManager implements SearchResponseCreatorManager, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolrSearchResponseCreatorManager.class);

    private static final String CACHE_NAME = "Solr Search Result Creators";

    private final SolrSearchStoreFactory storeFactory;
    private final SearchResponseCreatorFactory searchResponseCreatorFactory;
    private final ICache<Key, SearchResponseCreator> cache;

    @Inject
    public SolrSearchResponseCreatorManager(final CacheManager cacheManager,
                                            final SolrSearchConfig solrSearchConfig,
                                            final SolrSearchStoreFactory storeFactory,
                                            final SearchResponseCreatorFactory searchResponseCreatorFactory) {
        this.storeFactory = storeFactory;
        this.searchResponseCreatorFactory = searchResponseCreatorFactory;
        cache = cacheManager.create(CACHE_NAME, solrSearchConfig::getSearchResultCache, this::create, this::destroy);
    }

    private SearchResponseCreator create(SearchResponseCreatorCache.Key key) {
        try {
            LOGGER.debug("Creating new store for key {}", key);
            final Store store = storeFactory.create(key.getSearchRequest());
            return searchResponseCreatorFactory.create(store);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    private void destroy(final Key key, final SearchResponseCreator value) {
        if (value != null) {
            value.destroy();
        }
    }

    @Override
    public SearchResponseCreator get(final SearchResponseCreatorCache.Key key) {
        return cache.get(key);
    }

    @Override
    public void remove(final SearchResponseCreatorCache.Key key) {
        cache.remove(key);
    }

    @Override
    public void evictExpiredElements() {
        cache.evictExpiredElements();
    }

    @Override
    public void clear() {
        cache.clear();
    }
}