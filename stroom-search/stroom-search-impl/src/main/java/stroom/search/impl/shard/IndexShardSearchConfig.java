package stroom.search.impl.shard;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


public class IndexShardSearchConfig extends AbstractConfig {

    private static final int DEFAULT_MAX_THREADS = 4;
    private static final int DEFAULT_MAX_THREADS_PER_TASK = 2;

    private final int maxDocIdQueueSize;
    // TODO 01/12/2021 AT: Make final
    private int maxThreads;
    private final int maxThreadsPerTask;
    private final CacheConfig searchResultCache;
    private final CacheConfig indexShardSearcherCache;

    public IndexShardSearchConfig() {
        maxDocIdQueueSize = 1000000;
        maxThreads = DEFAULT_MAX_THREADS;
        maxThreadsPerTask = DEFAULT_MAX_THREADS_PER_TASK;
        searchResultCache = CacheConfig.builder()
                .maximumSize(10000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        indexShardSearcherCache = CacheConfig.builder()
                .maximumSize(2L)
                .expireAfterAccess(StroomDuration.ofMinutes(1))
                .build();
    }

    @JsonCreator
    public IndexShardSearchConfig(@JsonProperty("maxDocIdQueueSize") final int maxDocIdQueueSize,
                                  @JsonProperty("maxThreads") final int maxThreads,
                                  @JsonProperty("maxThreadsPerTask") final int maxThreadsPerTask,
                                  @JsonProperty("searchResultCache") final CacheConfig searchResultCache,
                                  @JsonProperty("indexShardSearcherCache") final CacheConfig indexShardSearcherCache) {
        this.maxDocIdQueueSize = maxDocIdQueueSize;
        this.maxThreads = maxThreads;
        this.maxThreadsPerTask = maxThreadsPerTask;
        this.searchResultCache = searchResultCache;
        this.indexShardSearcherCache = indexShardSearcherCache;
    }

    @JsonPropertyDescription("The maximum number of doc ids that will be queued ready for stored data to be " +
            "retrieved from the index shard")
    public int getMaxDocIdQueueSize() {
        return maxDocIdQueueSize;
    }

    @JsonPropertyDescription("The absolute maximum number of threads per node, used to search Lucene index " +
            "shards across all searches")
    public int getMaxThreads() {
        return maxThreads;
    }

    @Deprecated(forRemoval = true)
    public void setMaxThreads(final int maxThreads) {
        this.maxThreads = maxThreads;
    }

    @JsonPropertyDescription("The maximum number of threads per search, per node, used to search Lucene index shards")
    public int getMaxThreadsPerTask() {
        return maxThreadsPerTask;
    }

    public CacheConfig getSearchResultCache() {
        return searchResultCache;
    }

    public CacheConfig getIndexShardSearcherCache() {
        return indexShardSearcherCache;
    }

    @Override
    public String toString() {
        return "IndexShardSearchConfig{" +
                "maxDocIdQueueSize=" + maxDocIdQueueSize +
                ", maxThreads=" + maxThreads +
                ", maxThreadsPerTask=" + maxThreadsPerTask +
                ", searchResultCache=" + searchResultCache +
                ", indexShardSearcherCache=" + indexShardSearcherCache +
                '}';
    }
}
