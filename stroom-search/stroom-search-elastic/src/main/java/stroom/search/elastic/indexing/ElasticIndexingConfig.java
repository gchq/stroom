package stroom.search.elastic.indexing;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ElasticIndexingConfig extends AbstractConfig implements IsStroomConfig {

    private final int maxNestedElementDepth;
    private final int initialRetryBackoffPeriodMs;
    private final int retryCount;

    public ElasticIndexingConfig() {
        maxNestedElementDepth = 10;
        initialRetryBackoffPeriodMs = 1000;
        retryCount = 10;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticIndexingConfig(
            @JsonProperty("maxNestedElementDepth") final int maxNestedElementDepth,
            @JsonProperty("initialRetryBackoffPeriodMs") final int initialRetryBackoffPeriodMs,
            @JsonProperty("retryCount") final int retryCount
    ) {
        this.maxNestedElementDepth = maxNestedElementDepth;
        this.initialRetryBackoffPeriodMs = initialRetryBackoffPeriodMs;
        this.retryCount = retryCount;
    }

    @JsonPropertyDescription("Maximum allowed depth of JSON XML `array`/`map` elements, that a JSON document " +
            "may have when being sent to Elasticsearch for indexing.")
    public int getMaxNestedElementDepth() {
        return maxNestedElementDepth;
    }

    @JsonPropertyDescription("Delay in milliseconds, before the indexing request is initially retried. " +
            "Subsequent retries occur after a multiple of this initial delay.")
    public int getInitialRetryBackoffPeriodMs() {
        return initialRetryBackoffPeriodMs;
    }

    @JsonPropertyDescription("Number of times to retry the indexing request, before failing and generating an Error")
    public int getRetryCount() {
        return retryCount;
    }

    @Override
    public String toString() {
        return "ElasticSearchConfig{" +
                "maxNestedElementDepth=" + maxNestedElementDepth +
                ", initialRetryBackoffPeriodMs=" + initialRetryBackoffPeriodMs +
                ", retryCount=" + retryCount +
                '}';
    }
}
