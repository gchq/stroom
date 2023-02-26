package stroom.search.solr.search;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class SolrSearchConfig extends AbstractConfig implements IsStroomConfig {

    /**
     * We don't want to collect more than 10k doc's data into the queue by
     * default. When the queue is full the index shard data tasks will pause
     * until the docs are drained from the queue.
     */
    private static final int DEFAULT_MAX_STORED_DATA_QUEUE_SIZE = 1000;
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private final int maxStoredDataQueueSize;
    private final int maxBooleanClauseCount;

    public SolrSearchConfig() {

        maxStoredDataQueueSize = DEFAULT_MAX_STORED_DATA_QUEUE_SIZE;
        maxBooleanClauseCount = DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SolrSearchConfig(@JsonProperty("maxStoredDataQueueSize") final int maxStoredDataQueueSize,
                            @JsonProperty("maxBooleanClauseCount") final int maxBooleanClauseCount) {
        this.maxStoredDataQueueSize = maxStoredDataQueueSize;
        this.maxBooleanClauseCount = maxBooleanClauseCount;
    }

    @JsonPropertyDescription("The maximum number documents that will have stored data retrieved from the index " +
            "shard and queued prior to further processing")
    public int getMaxStoredDataQueueSize() {
        return maxStoredDataQueueSize;
    }

    @JsonPropertyDescription("The maximum number of clauses that a boolean search can contain.")
    public int getMaxBooleanClauseCount() {
        return maxBooleanClauseCount;
    }

    @Override
    public String toString() {
        return "SolrSearchConfig{" +
                "maxStoredDataQueueSize=" + maxStoredDataQueueSize +
                ", maxBooleanClauseCount=" + maxBooleanClauseCount +
                '}';
    }
}
