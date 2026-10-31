/*
 * Copyright 2022 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.search.elastic.indexing;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class ElasticIndexingConfig extends AbstractConfig implements IsStroomConfig {

    private static final int DEFAULT_MAX_NESTED_ELEMENT_DEPTH = 10;
    private static final int DEFAULT_INITIAL_RETRY_BACKOFF_PERIOD_MS = 1000;
    private static final int DEFAULT_RETRY_COUNT = 5;

    private final int maxNestedElementDepth;
    private final int initialRetryBackoffPeriodMs;
    private final int retryCount;

    public ElasticIndexingConfig() {
        maxNestedElementDepth = DEFAULT_MAX_NESTED_ELEMENT_DEPTH;
        initialRetryBackoffPeriodMs = DEFAULT_INITIAL_RETRY_BACKOFF_PERIOD_MS;
        retryCount = DEFAULT_RETRY_COUNT;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticIndexingConfig(
            @JsonProperty("maxNestedElementDepth") final Integer maxNestedElementDepth,
            @JsonProperty("initialRetryBackoffPeriodMs") final Integer initialRetryBackoffPeriodMs,
            @JsonProperty("retryCount") final Integer retryCount) {
        this.maxNestedElementDepth =
                Objects.requireNonNullElse(maxNestedElementDepth, DEFAULT_MAX_NESTED_ELEMENT_DEPTH);
        this.initialRetryBackoffPeriodMs =
                Objects.requireNonNullElse(initialRetryBackoffPeriodMs, DEFAULT_INITIAL_RETRY_BACKOFF_PERIOD_MS);
        this.retryCount = Objects.requireNonNullElse(retryCount, DEFAULT_RETRY_COUNT);
    }

    @JsonPropertyDescription("Maximum allowed depth of JSON XML `array`/`map` elements, that a JSON document " +
            "may have when being sent to Elasticsearch for indexing.")
    public int getMaxNestedElementDepth() {
        return maxNestedElementDepth;
    }

    @JsonPropertyDescription("Delay in milliseconds, before an indexing request that Elasticsearch rejected " +
            "because it was overloaded is first retried. Each later retry waits twice as long as the one before, up " +
            "to 60 seconds. Each wait is randomised to between half and all of this, so that indexing tasks " +
            "rejected together don't all retry together.")
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
