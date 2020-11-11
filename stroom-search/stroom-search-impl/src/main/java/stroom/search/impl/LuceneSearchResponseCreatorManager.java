package stroom.search.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorFactory;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.query.common.v2.Store;
import stroom.search.impl.shard.IndexShardSearchConfig;
import stroom.util.shared.Clearable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@SuppressWarnings("unused") //Used by DI
public class LuceneSearchResponseCreatorManager implements SearchResponseCreatorManager, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneSearchResponseCreatorManager.class);

    private static final String CACHE_NAME = "Lucene Search Result Creators";

    private final LuceneSearchStoreFactory storeFactory;
    private final SearchResponseCreatorFactory searchResponseCreatorFactory;
    private final ICache<SearchResponseCreatorCache.Key, SearchResponseCreator> cache;

    @Inject
    public LuceneSearchResponseCreatorManager(final CacheManager cacheManager,
                                              final IndexShardSearchConfig indexShardSearchConfig,
                                              final LuceneSearchStoreFactory storeFactory,
                                              final SearchResponseCreatorFactory searchResponseCreatorFactory) {
        this.storeFactory = storeFactory;
        this.searchResponseCreatorFactory = searchResponseCreatorFactory;
        cache = cacheManager.create(CACHE_NAME, indexShardSearchConfig::getSearchResultCache, this::create, this::destroy);
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

    private void destroy(final SearchResponseCreatorCache.Key key, final SearchResponseCreator value) {
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
