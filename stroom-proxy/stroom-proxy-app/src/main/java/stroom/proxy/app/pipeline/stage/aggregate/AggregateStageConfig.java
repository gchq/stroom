/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.stage.aggregate;

import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * The aggregate stage's configuration: where it reads and writes, when an aggregate closes, and
 * its threads ({@code designs/stages/aggregate.md §4.7}).
 * <p>
 * Read from YAML, every bound must be stated; the no-argument constructor is for programmatic use
 * and substitutes the standard values.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class AggregateStageConfig extends AbstractConfig implements IsProxyConfig {

    public static final int DEFAULT_MAX_ITEMS_PER_AGGREGATE = 1_000;
    public static final long DEFAULT_MAX_UNCOMPRESSED_BYTE_SIZE = ByteSize.ofGibibytes(1).getBytes();
    public static final StroomDuration DEFAULT_AGGREGATION_FREQUENCY = StroomDuration.ofMinutes(10);

    private final boolean enabled;
    @JsonIgnore
    private final boolean enabledSpecified;
    private final String inputQueue;
    private final String outputQueue;
    private final String fileStore;
    private final Integer maxItemsPerAggregate;
    private final Long maxUncompressedByteSize;
    private final StroomDuration aggregationFrequency;
    private final AggregateStageThreadsConfig threads;

    public AggregateStageConfig() {
        this(true, null, null, null, new AggregateStageThreadsConfig());
    }

    /**
     * Programmatic: the standard bounds.
     */
    public AggregateStageConfig(final Boolean enabled,
                                final String inputQueue,
                                final String outputQueue,
                                final String fileStore,
                                final AggregateStageThreadsConfig threads) {
        this(enabled,
                inputQueue,
                outputQueue,
                fileStore,
                DEFAULT_MAX_ITEMS_PER_AGGREGATE,
                ModelStringUtil.formatIECByteSizeString(DEFAULT_MAX_UNCOMPRESSED_BYTE_SIZE),
                DEFAULT_AGGREGATION_FREQUENCY,
                threads);
    }

    @JsonCreator
    public AggregateStageConfig(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty("inputQueue") final String inputQueue,
            @JsonProperty("outputQueue") final String outputQueue,
            @JsonProperty("fileStore") final String fileStore,
            @JsonProperty("maxItemsPerAggregate") final Integer maxItemsPerAggregate,
            @JsonProperty("maxUncompressedByteSize") final String maxUncompressedByteSize,
            @JsonProperty("aggregationFrequency") final StroomDuration aggregationFrequency,
            @JsonProperty("threads") final AggregateStageThreadsConfig threads) {
        this.enabled = Objects.requireNonNullElse(enabled, false);
        this.enabledSpecified = enabled != null;
        this.inputQueue = normaliseOptional(inputQueue);
        this.outputQueue = normaliseOptional(outputQueue);
        this.fileStore = normaliseOptional(fileStore);
        this.maxItemsPerAggregate = maxItemsPerAggregate;
        this.maxUncompressedByteSize = maxUncompressedByteSize == null || maxUncompressedByteSize.isBlank()
                ? null
                : ModelStringUtil.parseIECByteSizeString(maxUncompressedByteSize);
        this.aggregationFrequency = aggregationFrequency;
        this.threads = Objects.requireNonNullElseGet(threads, AggregateStageThreadsConfig::new);
    }

    @JsonProperty
    @JsonPropertyDescription("Whether the aggregate stage is enabled on this proxy process.")
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return true if {@code enabled} was present in configuration. Absence is a validation
     * error rather than a default, so that a stage block never means something the operator
     * did not write.
     */
    @JsonIgnore
    public boolean isEnabledSpecified() {
        return enabledSpecified;
    }

    @JsonProperty
    @JsonPropertyDescription("Logical input queue name (e.g. aggregateInput).")
    public String getInputQueue() {
        return inputQueue;
    }

    @JsonProperty
    @JsonPropertyDescription("Logical output queue name (e.g. forwardingInput).")
    public String getOutputQueue() {
        return outputQueue;
    }

    @JsonProperty
    @JsonPropertyDescription("Named file store the aggregates are written to.")
    public String getFileStore() {
        return fileStore;
    }

    @JsonProperty
    @JsonPropertyDescription("The most items an aggregate holds before it closes. An item is one data entry and "
                             + "its sidecars. A target, not a guarantee: an aggregate may close smaller on age.")
    public Integer getMaxItemsPerAggregate() {
        return maxItemsPerAggregate;
    }

    @JsonProperty("maxUncompressedByteSize")
    @JsonPropertyDescription("The most uncompressed bytes, as the inputs declare them, an aggregate holds before "
                             + "it closes. A single item larger than this ships alone in an aggregate of one.")
    public String getMaxUncompressedByteSizeString() {
        return maxUncompressedByteSize == null
                ? null
                : ModelStringUtil.formatIECByteSizeString(maxUncompressedByteSize);
    }

    @JsonIgnore
    public Long getMaxUncompressedByteSize() {
        return maxUncompressedByteSize;
    }

    @JsonProperty
    @JsonPropertyDescription("How long an aggregate stays open waiting for more of its feed before it closes "
                             + "with what it has. On SQS this also bounds the messages held in flight.")
    public StroomDuration getAggregationFrequency() {
        return aggregationFrequency;
    }

    @JsonProperty
    @JsonPropertyDescription("consumerThreads claim inputs from the input queue and hold the open aggregates; " +
                             "more than one is only allowed on a Kafka input queue, where a feed's inputs reach " +
                             "one consumer. mergeThreads write the closed aggregates.")
    public AggregateStageThreadsConfig getThreads() {
        return threads;
    }

    /**
     * @return the bounds, once validation has established every one is stated and positive.
     */
    @JsonIgnore
    public AggregateBounds getBounds() {
        return new AggregateBounds(
                Objects.requireNonNull(maxItemsPerAggregate, "maxItemsPerAggregate"),
                Objects.requireNonNull(maxUncompressedByteSize, "maxUncompressedByteSize"),
                Objects.requireNonNull(aggregationFrequency, "aggregationFrequency").getDuration());
    }

    private static String normaliseOptional(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
