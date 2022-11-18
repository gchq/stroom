package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.ModelStringUtil;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Duration;
import java.util.Objects;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class AggregatorConfig extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    public static final String PROP_NAME_ENABLED = "enabled";
    public static final String PROP_NAME_MAX_ITEMS_PER_AGGREGATE = "maxItemsPerAggregate";

    protected static final boolean DEFAULT_ENABLED = true;
    protected static final int DEFAULT_MAX_ITEMS_PER_AGGREGATE = 1_000;
    protected static final String DEFAULT_MAX_UNCOMPRESSED_SIZE_STR = "1G";
    protected static final Long DEFAULT_MAX_UNCOMPRESSED_SIZE = ModelStringUtil.parseIECByteSizeString(
            DEFAULT_MAX_UNCOMPRESSED_SIZE_STR);
    protected static final StroomDuration DEFAULT_MAX_AGGREGATE_AGE = StroomDuration.of(Duration.ofMinutes(10));
    protected static final StroomDuration DEFAULT_MAX_AGGREGATION_FREQUENCY = StroomDuration.of(Duration.ofMinutes(1));

    private final boolean enabled;
    private final int maxItemsPerAggregate;
    private final long maxUncompressedByteSize;
    private final StroomDuration maxAggregateAge;
    private final StroomDuration aggregationFrequency;

    public AggregatorConfig() {
        enabled = DEFAULT_ENABLED;
        maxItemsPerAggregate = DEFAULT_MAX_ITEMS_PER_AGGREGATE;
        maxUncompressedByteSize = DEFAULT_MAX_UNCOMPRESSED_SIZE;
        maxAggregateAge = DEFAULT_MAX_AGGREGATE_AGE;
        aggregationFrequency = DEFAULT_MAX_AGGREGATION_FREQUENCY;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public AggregatorConfig(@JsonProperty(PROP_NAME_ENABLED) final boolean enabled,
                            @JsonProperty(PROP_NAME_MAX_ITEMS_PER_AGGREGATE) final int maxItemsPerAggregate,
                            @JsonProperty("maxUncompressedByteSize") final String maxUncompressedByteSizeString,
                            @JsonProperty("maxAggregateAge") final StroomDuration maxAggregateAge,
                            @JsonProperty("aggregationFrequency") final StroomDuration aggregationFrequency) {
        this.enabled = enabled;
        this.maxItemsPerAggregate = maxItemsPerAggregate;
        this.maxUncompressedByteSize = ModelStringUtil.parseIECByteSizeString(maxUncompressedByteSizeString);
        this.maxAggregateAge = maxAggregateAge;
        this.aggregationFrequency = aggregationFrequency;
    }

    private AggregatorConfig(final boolean enabled,
                             final int maxItemsPerAggregate,
                             final long maxUncompressedByteSize,
                             final StroomDuration maxAggregateAge,
                             final StroomDuration aggregationFrequency) {
        this.enabled = enabled;
        this.maxItemsPerAggregate = maxItemsPerAggregate;
        this.maxUncompressedByteSize = maxUncompressedByteSize;
        this.maxAggregateAge = maxAggregateAge;
        this.aggregationFrequency = aggregationFrequency;
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

    @JsonPropertyDescription("Maximum total uncompressed size of all data within unless a single item is present in " +
            "which case it's total size might exceed this in order for us to be able to add it to an aggregate")
    @JsonProperty("maxUncompressedByteSize")
    public String getMaxUncompressedByteSizeString() {
        return ModelStringUtil.formatIECByteSizeString(maxUncompressedByteSize);
    }

    @NotNull
    @JsonPropertyDescription("What is the maximum age of an aggregate before it no longer accepts new items")
    @JsonProperty("maxAggregateAge")
    public StroomDuration getMaxAggregateAge() {
        return maxAggregateAge;
    }

    @RequiresProxyRestart
    @NotNull
    @JsonPropertyDescription("The the length of time that data is added to an aggregate for before the " +
            "aggregate is closed")
    @JsonProperty("aggregationFrequency")
    public StroomDuration getAggregationFrequency() {
        return aggregationFrequency;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this.enabled,
                this.maxItemsPerAggregate,
                this.maxUncompressedByteSize,
                this.maxAggregateAge,
                this.aggregationFrequency);
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
                && Objects.equals(maxAggregateAge, that.maxAggregateAge)
                && Objects.equals(aggregationFrequency, that.aggregationFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled,
                maxItemsPerAggregate,
                maxUncompressedByteSize,
                maxAggregateAge,
                aggregationFrequency);
    }

    @Override
    public String toString() {
        return "AggregatorConfig{" +
                "enabled=" + enabled +
                ", maxItemsPerAggregate=" + maxItemsPerAggregate +
                ", maxUncompressedByteSize=" + maxUncompressedByteSize +
                ", maxAggregateAge=" + maxAggregateAge +
                ", aggregationFrequency=" + aggregationFrequency +
                '}';
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public static class Builder {

        private boolean enabled = DEFAULT_ENABLED;
        private int maxItemsPerAggregate = DEFAULT_MAX_ITEMS_PER_AGGREGATE;
        private Long maxUncompressedByteSize = DEFAULT_MAX_UNCOMPRESSED_SIZE;
        private StroomDuration maxAggregateAge = DEFAULT_MAX_AGGREGATE_AGE;
        private StroomDuration aggregationFrequency = DEFAULT_MAX_AGGREGATION_FREQUENCY;

        private Builder() {
        }

        private Builder(final boolean enabled,
                        final int maxItemsPerAggregate,
                        final Long maxUncompressedByteSize,
                        final StroomDuration maxAggregateAge, final StroomDuration aggregationFrequency) {
            this.enabled = enabled;
            this.maxItemsPerAggregate = maxItemsPerAggregate;
            this.maxUncompressedByteSize = maxUncompressedByteSize;
            this.maxAggregateAge = maxAggregateAge;
            this.aggregationFrequency = aggregationFrequency;
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

        public Builder maxAggregateAge(final StroomDuration maxAggregateAge) {
            this.maxAggregateAge = maxAggregateAge;
            return this;
        }

        public Builder aggregationFrequency(final StroomDuration aggregationFrequency) {
            this.aggregationFrequency = aggregationFrequency;
            return this;
        }

        public AggregatorConfig build() {
            return new AggregatorConfig(
                    enabled,
                    maxItemsPerAggregate,
                    maxUncompressedByteSize,
                    maxAggregateAge,
                    aggregationFrequency);
        }
    }
}
