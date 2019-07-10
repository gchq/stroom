package stroom.proxy.repo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.ModelStringUtil;

public class ProxyRepositoryReaderConfig {
    private String readCron;
    private int forwardThreadCount = 3;
    private int maxFileScan = 100000;
    private int maxConcurrentMappedFiles = 100000;
    private int maxAggregation = 1000;
    private long maxStreamSize = ModelStringUtil.parseIECByteSizeString("1G");

    @JsonPropertyDescription("Cron style interval (e.g. every hour '0 * *', every half hour '0,30 * *') to read any ready repositories (if not defined we read all the time)")
    @JsonProperty
    public String getReadCron() {
        return readCron;
    }

    @JsonProperty
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


    @JsonPropertyDescription("Max number of files to scan over during forwarding. Once this limit is reached it will wait until next read interval")
    @JsonProperty
    public int getMaxFileScan() {
        return maxFileScan;
    }

    @JsonProperty
    public void setMaxFileScan(final int maxFileScan) {
        this.maxFileScan = maxFileScan;
    }

    @JsonPropertyDescription("The maximum number of concurrent mapped files we can hold before we send the largest set for aggregation")
    @JsonProperty
    public int getMaxConcurrentMappedFiles() {
        return maxConcurrentMappedFiles;
    }

    @JsonProperty
    public void setMaxConcurrentMappedFiles(final int maxConcurrentMappedFiles) {
        this.maxConcurrentMappedFiles = maxConcurrentMappedFiles;
    }

    @JsonPropertyDescription("Aggregate size to break at when building an aggregate. 1G stream maybe a file around 100MB in size (with 90% compression)")
    @JsonProperty
    public int getMaxAggregation() {
        return maxAggregation;
    }

    @JsonProperty
    public void setMaxAggregation(final int maxAggregation) {
        this.maxAggregation = maxAggregation;
    }

    @JsonPropertyDescription("Stream size to break at when building an aggregate")
    @JsonProperty("maxStreamSize")
    public String getMaxStreamSizeString() {
        return ModelStringUtil.formatIECByteSizeString(maxStreamSize);
    }

    @JsonProperty("maxStreamSize")
    public void setMaxStreamSizeString(final String maxStreamSize) {
        this.maxStreamSize = ModelStringUtil.parseIECByteSizeString(maxStreamSize);
    }

    @JsonIgnore
    public long getMaxStreamSize() {
        return maxStreamSize;
    }

    @JsonIgnore
    public void setMaxStreamSize(final long maxStreamSize) {
        this.maxStreamSize = maxStreamSize;
    }
}
