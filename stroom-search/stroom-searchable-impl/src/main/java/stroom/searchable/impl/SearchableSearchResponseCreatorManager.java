package stroom.searchable.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorCache.Key;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.query.common.v2.Store;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;

@SuppressWarnings("unused") //Used by DI
@Singleton
class SearchableSearchResponseCreatorManager implements SearchResponseCreatorManager, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchableSearchResponseCreatorManager.class);

    private static final String CACHE_NAME = "DB Search Result Creators";

    private final SearchableStoreFactory storeFactory;
    private final ICache<Key, SearchResponseCreator> cache;

    @Inject
    public SearchableSearchResponseCreatorManager(final CacheManager cacheManager,
                                                  final SearchableConfig searchableConfig,
                                                  final SearchableStoreFactory storeFactory) {
        this.storeFactory = storeFactory;
        cache = cacheManager.create(CACHE_NAME, searchableConfig::getSearchResultCache, this::create, this::destroy);
    }

    private SearchResponseCreator create(SearchResponseCreatorCache.Key key) {
        try {
            LOGGER.debug("Creating new store for key {}", key);
            final Store store = storeFactory.create(key.getSearchRequest());
            return new SearchResponseCreator(store);
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }

    private void destroy(final Key key, final SearchResponseCreator value) {
        value.destroy();
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
