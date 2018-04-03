package stroom.search.server;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.Store;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.function.Function;

public abstract class AbstractInMemorySearchResponseCreatorCacheFactory implements SearchResponseCreatorCacheFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneInMemorySearchResponseCreatorCacheFactory.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(LuceneInMemorySearchResponseCreatorCacheFactory.class);

    @Override
    public SearchResponseCreatorCache create(final StoreFactory storeFactory) {

        final CacheLoader<SearchResponseCreatorCache.Key, SearchResponseCreator> cacheLoader = buildLoaderFunc(storeFactory);

        final CacheBuilder<SearchResponseCreatorCache.Key, SearchResponseCreator> cacheBuilder = CacheBuilder.newBuilder()
                .removalListener(AbstractInMemorySearchResponseCreatorCacheFactory::onRemove);

        buildCache(cacheBuilder);

        final LoadingCache<SearchResponseCreatorCache.Key, SearchResponseCreator> cache = cacheBuilder.build(cacheLoader);

        registerCache(cacheBuilder, cache);

        return new InMemorySearchResponseCreatorCache(cache);
    }

    private static void onRemove(final RemovalNotification<SearchResponseCreatorCache.Key, SearchResponseCreator> notification) {
        LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("Removal notification for key {}, value {}, cause {}",
                notification.getKey(),
                notification.getValue(),
                notification.getCause()));

        if (notification != null && notification.getValue() != null) {
            notification.getValue().destroy();
        }
    }

    private CacheLoader<SearchResponseCreatorCache.Key, SearchResponseCreator> buildLoaderFunc(final StoreFactory storeFactory) {
        final Function<SearchResponseCreatorCache.Key, SearchResponseCreator> loaderFunc = (SearchResponseCreatorCache.Key key) -> {
            LOGGER.debug("Loading new store for key {}", key);
            final Store store = storeFactory.create(key.getSearchRequest());
            return new SearchResponseCreator(store);
        };
        return CacheLoader.from(loaderFunc::apply);
    }

    /**
     * Allows for additional implementation specific build options to be added to cacheBuilder
     */
    abstract void buildCache(final CacheBuilder<SearchResponseCreatorCache.Key, SearchResponseCreator> cacheBuilder);

    /**
     * Allows for the cache and its builder to be registered with a cache manager
     */
    abstract void registerCache(final CacheBuilder<SearchResponseCreatorCache.Key, SearchResponseCreator> cacheBuilder,
                                final Cache<SearchResponseCreatorCache.Key, SearchResponseCreator> cache);

}
