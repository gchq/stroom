package stroom.proxy.repo;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.ModelStringUtil;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.Duration;
import javax.inject.Singleton;

@Singleton
public class AggregatorConfig extends AbstractConfig {

    private int maxItemsPerAggregate = 1000;
    private StroomDuration maxAggregateAge = StroomDuration.of(Duration.ofMinutes(10));
    private StroomDuration aggregationFrequency = StroomDuration.of(Duration.ofMinutes(1));
    private long maxUncompressedByteSize = ModelStringUtil.parseIECByteSizeString("1G");

    @JsonPropertyDescription("Maximum number of data items to add to an aggregate before a new one is created")
    @JsonProperty
    public int getMaxItemsPerAggregate() {
        return maxItemsPerAggregate;
    }

    @JsonProperty
    public void setMaxItemsPerAggregate(final int maxItemsPerAggregate) {
        this.maxItemsPerAggregate = maxItemsPerAggregate;
    }

    @JsonPropertyDescription("Maximum total uncompressed size of all data within unless a single item is present in " +
            "which case it's total size might exceed this in order for us to be able to add it to an aggregate")
    @JsonProperty("maxUncompressedByteSize")
    public String getMaxUncompressedByteSizeString() {
        return ModelStringUtil.formatIECByteSizeString(maxUncompressedByteSize);
    }

    @JsonProperty("maxUncompressedByteSize")
    public void setMaxUncompressedByteSizeString(final String maxStreamSize) {
        this.maxUncompressedByteSize = ModelStringUtil.parseIECByteSizeString(maxStreamSize);
    }

    @JsonIgnore
    public long getMaxUncompressedByteSize() {
        return maxUncompressedByteSize;
    }

    @JsonIgnore
    public void setMaxUncompressedByteSize(final long maxUncompressedByteSize) {
        this.maxUncompressedByteSize = maxUncompressedByteSize;
    }

    @JsonPropertyDescription("What is the maximum age of an aggregate before it no longer accepts new items")
    @JsonProperty("maxAggregateAge")
    public StroomDuration getMaxAggregateAge() {
        return maxAggregateAge;
    }

    public void setMaxAggregateAge(final StroomDuration maxAggregateAge) {
        this.maxAggregateAge = maxAggregateAge;
    }

    @JsonPropertyDescription("How frequently do we want to produce aggregates")
    @JsonProperty("aggregationFrequency")
    public StroomDuration getAggregationFrequency() {
        return aggregationFrequency;
    }

    public void setAggregationFrequency(final StroomDuration aggregationFrequency) {
        this.aggregationFrequency = aggregationFrequency;
    }
}
