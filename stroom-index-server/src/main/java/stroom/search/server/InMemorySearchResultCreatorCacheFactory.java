package stroom.search.server;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.RemovalListener;
import org.springframework.stereotype.Component;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.Store;
import stroom.util.cache.CacheManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class InMemorySearchResultCreatorCacheFactory implements SearchResultCreatorCacheFactory {

    private static final long DEFAULT_MAX_ACTIVE_QUERIES = 10000;
    private static final Duration DEFAULT_EXPIRE_AFTER_ACCESS_DURATION = Duration.ofMinutes(10);


    private final CacheManager cacheManager;
    private final long maxActiveQueries;
    private final Duration expireAfterAccessDuration;


    public InMemorySearchResultCreatorCacheFactory(final CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.expireAfterAccessDuration = DEFAULT_EXPIRE_AFTER_ACCESS_DURATION;
        this.maxActiveQueries = DEFAULT_MAX_ACTIVE_QUERIES;
    }

//    public InMemorySearchResultCreatorCacheFactory(final long maxActiveQueries, final Duration expireAfterAccessDuration) {
//        this.maxActiveQueries = maxActiveQueries;
//        this.expireAfterAccessDuration = expireAfterAccessDuration;
//    }

    @Override
    public SearchResultCreatorCache create(final StoreFactory storeFactory) {

        final RemovalListener<SearchResultCreatorCache.Key, SearchResponseCreator> removalListener = notification ->
                onRemove(notification.getKey(), notification.getValue());
        final com.google.common.base.Function<SearchResultCreatorCache.Key, SearchResponseCreator> loaderFunc = buildLoaderFunc(storeFactory);
        final CacheLoader<SearchResultCreatorCache.Key, SearchResponseCreator> cacheLoader = CacheLoader.from(loaderFunc);

        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(maxActiveQueries)
                .expireAfterAccess(expireAfterAccessDuration.toMillis(), TimeUnit.MILLISECONDS)
                .removalListener(removalListener);
        final Cache<SearchResultCreatorCache.Key, SearchResponseCreator> cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Search Result Creators", cacheBuilder, cache);

        return new InMemorySearchResultCreatorCache(cache);
    }

    private com.google.common.base.Function<SearchResultCreatorCache.Key, SearchResponseCreator> buildLoaderFunc(final StoreFactory storeFactory) {
            return (SearchResultCreatorCache.Key key) -> {
                final Store store = storeFactory.create(key.getSearchRequest());
                return new SearchResponseCreator(store);
            };
    }

    private void onRemove(final SearchResultCreatorCache.Key key, final SearchResponseCreator value) {
        if (value != null) {
            value.destroy();
        }
    }
}
