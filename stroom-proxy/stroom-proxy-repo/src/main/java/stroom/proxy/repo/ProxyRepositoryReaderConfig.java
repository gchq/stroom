package stroom.proxy.repo;

import stroom.util.shared.ModelStringUtil;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class ProxyRepositoryReaderConfig {

    @Deprecated
    private String readCron;
    private int forwardThreadCount = 3;
    @Deprecated
    private int maxFileScan = 100000;
    @Deprecated
    private int maxConcurrentMappedFiles = 100000;
    private int maxItemsPerAggregate = 1000;
    private StroomDuration maxAggregateAge;
    private StroomDuration aggregationFrequency;
    private long maxUncompressedByteSize = ModelStringUtil.parseIECByteSizeString("1G");

    @JsonPropertyDescription("Cron style interval (e.g. every hour '0 * *', every half hour '0,30 * *') to read " +
            "any ready repositories (if not defined we read all the time)")
    @JsonProperty
    @Deprecated
    public String getReadCron() {
        return readCron;
    }

    @JsonProperty
    @Deprecated
    public void setReadCron(final String readCron) {
        this.readCron = readCron;
    }

    @JsonPropertyDescription("Number of threads to forward with")
    @JsonProperty
    public int getForwardThreadCount() {
        return forwardThreadCount;
    }

    @JsonProperty
    public void setForwardThreadCount(final int forwardThreadCount) {
        this.forwardThreadCount = forwardThreadCount;
    }


    @JsonPropertyDescription("Max number of files to scan over during forwarding. Once this limit is reached it " +
            "will wait until next read interval")
    @JsonProperty
    @Deprecated
    public int getMaxFileScan() {
        return maxFileScan;
    }

    @JsonProperty
    @Deprecated
    public void setMaxFileScan(final int maxFileScan) {
        this.maxFileScan = maxFileScan;
    }

    @JsonPropertyDescription("The maximum number of concurrent mapped files we can hold before we send the " +
            "largest set for aggregation")
    @JsonProperty
    @Deprecated
    public int getMaxConcurrentMappedFiles() {
        return maxConcurrentMappedFiles;
    }

    @JsonProperty
    @Deprecated
    public void setMaxConcurrentMappedFiles(final int maxConcurrentMappedFiles) {
        this.maxConcurrentMappedFiles = maxConcurrentMappedFiles;
    }

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
