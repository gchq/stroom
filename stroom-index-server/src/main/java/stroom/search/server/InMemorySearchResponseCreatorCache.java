package stroom.search.server;

import com.google.common.cache.LoadingCache;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.util.spring.StroomFrequencySchedule;

class InMemorySearchResponseCreatorCache implements SearchResponseCreatorCache {

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
    @StroomFrequencySchedule("10s")
    public void evictExpiredElements() {
        cache.cleanUp();
    }
}
