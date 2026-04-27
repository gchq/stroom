/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.proxy.app.pipeline;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/**
 * Per-stage processing thread settings for the reference-message pipeline.
 */
@JsonPropertyOrder(alphabetic = true)
public class PipelineStageThreadsConfig extends AbstractConfig implements IsProxyConfig {

    public static final int DEFAULT_MAX_CONCURRENT_RECEIVES = 5;
    public static final int DEFAULT_CONSUMER_THREADS = 1;
    public static final int DEFAULT_CLOSE_OLD_AGGREGATES_THREADS = 1;

    private final int maxConcurrentReceives;
    private final int consumerThreads;
    private final int closeOldAggregatesThreads;

    public PipelineStageThreadsConfig() {
        this(
                DEFAULT_MAX_CONCURRENT_RECEIVES,
                DEFAULT_CONSUMER_THREADS,
                DEFAULT_CLOSE_OLD_AGGREGATES_THREADS);
    }

    @JsonCreator
    public PipelineStageThreadsConfig(
            @JsonProperty("maxConcurrentReceives") final Integer maxConcurrentReceives,
            @JsonProperty("consumerThreads") final Integer consumerThreads,
            @JsonProperty("closeOldAggregatesThreads") final Integer closeOldAggregatesThreads) {

        this.maxConcurrentReceives = Objects.requireNonNullElse(
                maxConcurrentReceives, DEFAULT_MAX_CONCURRENT_RECEIVES);
        this.consumerThreads = Objects.requireNonNullElse(consumerThreads, DEFAULT_CONSUMER_THREADS);
        this.closeOldAggregatesThreads = Objects.requireNonNullElse(
                closeOldAggregatesThreads, DEFAULT_CLOSE_OLD_AGGREGATES_THREADS);
    }

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("Maximum concurrent receive requests for the receive stage.")
    public int getMaxConcurrentReceives() {
        return maxConcurrentReceives;
    }

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("Number of worker threads consuming this stage input queue.")
    public int getConsumerThreads() {
        return consumerThreads;
    }

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("Number of threads used by pre-aggregation to close old aggregates.")
    public int getCloseOldAggregatesThreads() {
        return closeOldAggregatesThreads;
    }
}
