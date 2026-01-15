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

package stroom.proxy.app.handler;

import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class ThreadConfig extends AbstractConfig implements IsProxyConfig {

    public static final int DEFAULT_ZIP_SPLITTING_INPUT_QUEUE_THREAD_COUNT = 1;
    public static final int DEFAULT_AGGREGATE_INPUT_QUEUE_THREAD_COUNT = 1;
    public static final int DEFAULT_PRE_AGGREGATE_INPUT_QUEUE_THREAD_COUNT = 1;
    public static final int DEFAULT_FORWARDING_INPUT_QUEUE_THREAD_COUNT = 1;

    private final int zipSplittingInputQueueThreadCount;
    private final int aggregateInputQueueThreadCount;
    private final int preAggregateInputQueueThreadCount;
    private final int forwardingInputQueueThreadCount;

    public ThreadConfig() {
        zipSplittingInputQueueThreadCount = DEFAULT_ZIP_SPLITTING_INPUT_QUEUE_THREAD_COUNT;
        aggregateInputQueueThreadCount = DEFAULT_AGGREGATE_INPUT_QUEUE_THREAD_COUNT;
        preAggregateInputQueueThreadCount = DEFAULT_PRE_AGGREGATE_INPUT_QUEUE_THREAD_COUNT;
        forwardingInputQueueThreadCount = DEFAULT_FORWARDING_INPUT_QUEUE_THREAD_COUNT;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ThreadConfig(
            @JsonProperty("zipSplittingInputQueueThreadCount") final Integer zipSplittingInputQueueThreadCount,
            @JsonProperty("aggregateInputQueueThreadCount") final Integer aggregateInputQueueThreadCount,
            @JsonProperty("preAggregateInputQueueThreadCount") final Integer preAggregateInputQueueThreadCount,
            @JsonProperty("forwardingInputQueueThreadCount") final Integer forwardingInputQueueThreadCount) {

        this.zipSplittingInputQueueThreadCount = Objects.requireNonNullElse(
                zipSplittingInputQueueThreadCount, DEFAULT_ZIP_SPLITTING_INPUT_QUEUE_THREAD_COUNT);
        this.aggregateInputQueueThreadCount = Objects.requireNonNullElse(
                aggregateInputQueueThreadCount, DEFAULT_AGGREGATE_INPUT_QUEUE_THREAD_COUNT);
        this.preAggregateInputQueueThreadCount = Objects.requireNonNullElse(
                preAggregateInputQueueThreadCount, DEFAULT_PRE_AGGREGATE_INPUT_QUEUE_THREAD_COUNT);
        this.forwardingInputQueueThreadCount = Objects.requireNonNullElse(
                forwardingInputQueueThreadCount, DEFAULT_FORWARDING_INPUT_QUEUE_THREAD_COUNT);
    }

    @JsonPropertyDescription("Number of threads to consume from the zip splitting input queue.")
    @RequiresProxyRestart
    @Min(1)
    @JsonProperty
    public int getZipSplittingInputQueueThreadCount() {
        return zipSplittingInputQueueThreadCount;
    }

    @JsonPropertyDescription("Number of threads to consume from the aggregate input queue.")
    @RequiresProxyRestart
    @Min(1)
    @JsonProperty
    public int getAggregateInputQueueThreadCount() {
        return aggregateInputQueueThreadCount;
    }

    @JsonPropertyDescription("Number of threads to consume from the pre-aggregate input queue.")
    @RequiresProxyRestart
    @Min(1)
    @JsonProperty
    public int getPreAggregateInputQueueThreadCount() {
        return preAggregateInputQueueThreadCount;
    }

    @JsonPropertyDescription("Number of threads to consume from the forwarding input queue.")
    @RequiresProxyRestart
    @Min(1)
    @JsonProperty
    public int getForwardingInputQueueThreadCount() {
        return forwardingInputQueueThreadCount;
    }

    @Override
    public String toString() {
        return "ThreadConfig{" +
               "zipSplittingInputQueueThreadCount=" + zipSplittingInputQueueThreadCount +
               ", aggregateInputQueueThreadCount=" + aggregateInputQueueThreadCount +
               ", preAggregateInputQueueThreadCount=" + preAggregateInputQueueThreadCount +
               ", forwardingInputQueueThreadCount=" + forwardingInputQueueThreadCount +
               '}';
    }
}
