package stroom.statistics.sql.search;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import stroom.query.common.v2.AbstractInMemorySearchResponseCreatorCacheFactory;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused") //Used by DI
public class SqlStatisticsInMemorySearchResponseCreatorCacheFactory extends AbstractInMemorySearchResponseCreatorCacheFactory {

    private static final long DEFAULT_MAX_ACTIVE_QUERIES = 10000;
    private static final Duration DEFAULT_EXPIRE_AFTER_ACCESS_DURATION = Duration.ofMinutes(10);

    private final CacheManager cacheManager;
    private final long maxActiveQueries;
    private final Duration expireAfterAccessDuration;

    @Inject
    public SqlStatisticsInMemorySearchResponseCreatorCacheFactory(final CacheManager cacheManager) {
        this.cacheManager = cacheManager;

        //TODO replace these with props
        this.expireAfterAccessDuration = DEFAULT_EXPIRE_AFTER_ACCESS_DURATION;
        this.maxActiveQueries = DEFAULT_MAX_ACTIVE_QUERIES;
    }

    @Override
    protected void addAdditionalBuildOptions(
            final CacheBuilder<SearchResponseCreatorCache.Key, SearchResponseCreator> cacheBuilder) {
       cacheBuilder
                .maximumSize(maxActiveQueries)
                .expireAfterAccess(expireAfterAccessDuration.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    protected void registerCache(final CacheBuilder<SearchResponseCreatorCache.Key, SearchResponseCreator> cacheBuilder,
                       final Cache<SearchResponseCreatorCache.Key, SearchResponseCreator> cache) {
        cacheManager.registerCache("SQL Statistics Search Result Creators", cacheBuilder, cache);
    }
}
