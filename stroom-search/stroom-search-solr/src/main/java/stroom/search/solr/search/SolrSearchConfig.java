package stroom.search.solr.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.cache.CacheConfig;
import stroom.search.extraction.ExtractionConfig;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class SolrSearchConfig extends AbstractConfig {
    /**
     * We don't want to collect more than 10k doc's data into the queue by
     * default. When the queue is full the index shard data tasks will pause
     * until the docs are drained from the queue.
     */
    private static final int DEFAULT_MAX_STORED_DATA_QUEUE_SIZE = 1000;
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private int maxStoredDataQueueSize = DEFAULT_MAX_STORED_DATA_QUEUE_SIZE;
    private int maxBooleanClauseCount = DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT;
    private String storeSize = "1000000,100,10,1";
    private ExtractionConfig extractionConfig = new ExtractionConfig();
    private CacheConfig searchResultCache = new CacheConfig.Builder()
            .maximumSize(10000L)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    @JsonPropertyDescription("The maximum number documents that will have stored data retrieved from the index shard and queued prior to further processing")
    public int getMaxStoredDataQueueSize() {
        return maxStoredDataQueueSize;
    }

    public void setMaxStoredDataQueueSize(final int maxStoredDataQueueSize) {
        this.maxStoredDataQueueSize = maxStoredDataQueueSize;
    }

    @JsonPropertyDescription("The maximum number of clauses that a boolean search can contain.")
    public int getMaxBooleanClauseCount() {
        return maxBooleanClauseCount;
    }

    public void setMaxBooleanClauseCount(final int maxBooleanClauseCount) {
        this.maxBooleanClauseCount = maxBooleanClauseCount;
    }

    @JsonPropertyDescription("The maximum number of search results to keep in memory at each level.")
    public String getStoreSize() {
        return storeSize;
    }

    public void setStoreSize(final String storeSize) {
        this.storeSize = storeSize;
    }

    @JsonProperty("extraction")
    public ExtractionConfig getExtractionConfig() {
        return extractionConfig;
    }

    public void setExtractionConfig(final ExtractionConfig extractionConfig) {
        this.extractionConfig = extractionConfig;
    }

    public CacheConfig getSearchResultCache() {
        return searchResultCache;
    }

    public void setSearchResultCache(final CacheConfig searchResultCache) {
        this.searchResultCache = searchResultCache;
    }

    @Override
    public String toString() {
        return "SolrSearchConfig{" +
                "maxStoredDataQueueSize=" + maxStoredDataQueueSize +
                ", maxBooleanClauseCount=" + maxBooleanClauseCount +
                ", storeSize='" + storeSize + '\'' +
                ", extractionConfig=" + extractionConfig +
                ", searchResultCache=" + searchResultCache +
                '}';
    }
}
