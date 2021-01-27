package stroom.query.common.v2;

import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class InMemorySearchResponseCreatorCache implements SearchResponseCreatorCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemorySearchResponseCreatorCache.class);

    private final LoadingCache<Key, SearchResponseCreator> cache;

    InMemorySearchResponseCreatorCache(final LoadingCache<Key, SearchResponseCreator> cache) {
        this.cache = cache;
    }

    @Override
    public SearchResponseCreator get(final SearchResponseCreatorCache.Key key) {
        return cache.getUnchecked(key);
    }

    @Override
    public void remove(final SearchResponseCreatorCache.Key key) {
        cache.invalidate(key);
        cache.cleanUp();
    }

    @Override
    public void evictExpiredElements() {
        cache.cleanUp();
    }

    @Override
    public void clear() {
        LOGGER.debug("Removing all items from cache {}", cache);

        try {
            cache.invalidateAll();
            cache.cleanUp();
        } catch (final RuntimeException e) {
            LOGGER.error("Error clearing cache: " + e.getMessage(), e);
        }
    }
}
