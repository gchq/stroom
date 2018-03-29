package stroom.search.server;

import com.google.common.cache.LoadingCache;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.util.spring.StroomFrequencySchedule;

class InMemorySearchResultCreatorCache implements SearchResultCreatorCache {

    private final LoadingCache<Key, SearchResponseCreator> cache;

    InMemorySearchResultCreatorCache(final LoadingCache<Key, SearchResponseCreator> cache) {
        this.cache = cache;
    }

    @Override
    public SearchResponseCreator get(final SearchResultCreatorCache.Key key) {
        return cache.getUnchecked(key);
    }

    @Override
    public void remove(final SearchResultCreatorCache.Key key) {
        cache.invalidate(key);
        cache.cleanUp();
    }

    @Override
    @StroomFrequencySchedule("10s")
    public void evictExpiredElements() {
        cache.cleanUp();
    }
}
