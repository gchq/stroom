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

package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class AggregatorConfig extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    public static final String PROP_NAME_ENABLED = "enabled";
    public static final String PROP_NAME_MAX_ITEMS_PER_AGGREGATE = "maxItemsPerAggregate";
    public static final String PROP_NAME_SPLIT_SOURCES = "splitSources";

    protected static final boolean DEFAULT_ENABLED = true;
    protected static final int DEFAULT_MAX_ITEMS_PER_AGGREGATE = 1_000;
    protected static final long DEFAULT_MAX_UNCOMPRESSED_BYTES_SIZE = ByteSize.ofGibibytes(1).getBytes();
    protected static final StroomDuration DEFAULT_AGGREGATION_FREQUENCY = StroomDuration.ofMinutes(10);
    protected static final boolean DEFAULT_SPLIT_SOURCES = true;

    private final boolean enabled;
    private final int maxItemsPerAggregate;
    private final long maxUncompressedByteSize;
    private final StroomDuration aggregationFrequency;
    private final boolean splitSources;

    public AggregatorConfig() {
        enabled = DEFAULT_ENABLED;
        maxItemsPerAggregate = DEFAULT_MAX_ITEMS_PER_AGGREGATE;
        maxUncompressedByteSize = DEFAULT_MAX_UNCOMPRESSED_BYTES_SIZE;
        aggregationFrequency = DEFAULT_AGGREGATION_FREQUENCY;
        splitSources = DEFAULT_SPLIT_SOURCES;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public AggregatorConfig(@JsonProperty(PROP_NAME_ENABLED) final Boolean enabled,
                            @JsonProperty(PROP_NAME_MAX_ITEMS_PER_AGGREGATE) final Integer maxItemsPerAggregate,
                            @JsonProperty("maxUncompressedByteSize") final String maxUncompressedByteSizeString,
                            @JsonProperty("aggregationFrequency") final StroomDuration aggregationFrequency,
                            @JsonProperty(PROP_NAME_SPLIT_SOURCES) final Boolean splitSources) {

        this.enabled = Objects.requireNonNullElse(enabled, DEFAULT_ENABLED);
        this.maxItemsPerAggregate = Objects.requireNonNullElse(maxItemsPerAggregate, DEFAULT_MAX_ITEMS_PER_AGGREGATE);
        this.maxUncompressedByteSize = NullSafe.getOrElse(
                maxUncompressedByteSizeString,
                ModelStringUtil::parseIECByteSizeString,
                DEFAULT_MAX_UNCOMPRESSED_BYTES_SIZE);
        this.aggregationFrequency = Objects.requireNonNullElse(aggregationFrequency, DEFAULT_AGGREGATION_FREQUENCY);
        this.splitSources = Objects.requireNonNullElse(splitSources, DEFAULT_SPLIT_SOURCES);
    }

    private AggregatorConfig(final boolean enabled,
                             final int maxItemsPerAggregate,
                             final long maxUncompressedByteSize,
                             final StroomDuration aggregationFrequency,
                             final boolean splitSources) {
        this.enabled = enabled;
        this.maxItemsPerAggregate = maxItemsPerAggregate;
        this.maxUncompressedByteSize = maxUncompressedByteSize;
        this.aggregationFrequency = aggregationFrequency;
        this.splitSources = splitSources;
    }

    @RequiresProxyRestart
    @JsonPropertyDescription("If we are actually going to aggregate stored data or use it as is")
    @JsonProperty
    public boolean isEnabled() {
        return enabled;
    }

    @Min(0)
    @JsonPropertyDescription("Maximum number of data items to add to an aggregate before a new one is created")
    @JsonProperty
    public int getMaxItemsPerAggregate() {
        return maxItemsPerAggregate;
    }

    @Min(0)
    @JsonIgnore
    public long getMaxUncompressedByteSize() {
        return maxUncompressedByteSize;
    }

    @JsonPropertyDescription(
            "Maximum total uncompressed size of all data within unless a single item is present in " +
            "which case it's total size might exceed this in order for us to be able to add it to an aggregate")
    @JsonProperty("maxUncompressedByteSize")
    public String getMaxUncompressedByteSizeString() {
        return ModelStringUtil.formatIECByteSizeString(maxUncompressedByteSize);
    }

    @NotNull
    @JsonPropertyDescription("The the length of time that data is added to an aggregate for before the " +
                             "aggregate is closed")
    @JsonProperty("aggregationFrequency")
    public StroomDuration getAggregationFrequency() {
        return aggregationFrequency;
    }

    @NotNull
    @JsonPropertyDescription(
            "In order to form aggregates that do not exceed the max item count or max uncompressed" +
            " byte size we can split sources into parts." +
            " Note that if a single zip entry exceeds the max uncompressed size then an aggregate will still be" +
            " produced containing the single entry but will obviously exceed the max uncompressed size." +
            " If we do not split sources then all aggregates produced will exceed or equal the max uncompressed byte" +
            " size unless they reach an item limit first." +
            " Splitting sources only occurs when needed, but as it requires additional processing and IO it may be" +
            " preferable to turn it off in some environments.")
    @JsonProperty(PROP_NAME_SPLIT_SOURCES)
    public boolean isSplitSources() {
        return splitSources;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this.enabled,
                this.maxItemsPerAggregate,
                this.maxUncompressedByteSize,
                this.aggregationFrequency,
                splitSources);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AggregatorConfig that = (AggregatorConfig) o;
        return enabled == that.enabled
               && maxItemsPerAggregate == that.maxItemsPerAggregate
               && maxUncompressedByteSize == that.maxUncompressedByteSize
               && Objects.equals(aggregationFrequency, that.aggregationFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled,
                maxItemsPerAggregate,
                maxUncompressedByteSize,
                aggregationFrequency);
    }

    @Override
    public String toString() {
        return "AggregatorConfig{" +
               "enabled=" + enabled +
               ", maxItemsPerAggregate=" + maxItemsPerAggregate +
               ", maxUncompressedByteSize=" + maxUncompressedByteSize +
               ", aggregationFrequency=" + aggregationFrequency +
               '}';
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class Builder {

        private boolean enabled = DEFAULT_ENABLED;
        private int maxItemsPerAggregate = DEFAULT_MAX_ITEMS_PER_AGGREGATE;
        private Long maxUncompressedByteSize = DEFAULT_MAX_UNCOMPRESSED_BYTES_SIZE;
        private StroomDuration aggregationFrequency = DEFAULT_AGGREGATION_FREQUENCY;
        private boolean splitSources = DEFAULT_SPLIT_SOURCES;

        private Builder() {
        }

        private Builder(final boolean enabled,
                        final int maxItemsPerAggregate,
                        final Long maxUncompressedByteSize,
                        final StroomDuration aggregationFrequency,
                        final boolean splitSources) {
            this.enabled = enabled;
            this.maxItemsPerAggregate = maxItemsPerAggregate;
            this.maxUncompressedByteSize = maxUncompressedByteSize;
            this.aggregationFrequency = aggregationFrequency;
            this.splitSources = splitSources;
        }

        public Builder withEnabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxItemsPerAggregate(final int maxItemsPerAggregate) {
            this.maxItemsPerAggregate = maxItemsPerAggregate;
            return this;
        }

        public Builder maxUncompressedByteSize(final long maxUncompressedByteSize) {
            this.maxUncompressedByteSize = maxUncompressedByteSize;
            return this;
        }

        public Builder maxUncompressedByteSizeString(final String maxUncompressedByteSizeString) {
            this.maxUncompressedByteSize = ModelStringUtil.parseIECByteSizeString(maxUncompressedByteSizeString);
            return this;
        }

        public Builder aggregationFrequency(final StroomDuration aggregationFrequency) {
            this.aggregationFrequency = aggregationFrequency;
            return this;
        }

        public Builder splitSources(final boolean splitSources) {
            this.splitSources = splitSources;
            return this;
        }

        public AggregatorConfig build() {
            return new AggregatorConfig(
                    enabled,
                    maxItemsPerAggregate,
                    maxUncompressedByteSize,
                    aggregationFrequency,
                    splitSources);
        }
    }
}
