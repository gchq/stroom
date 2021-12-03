package stroom.proxy.repo;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.validation.ValidSimpleCron;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ProxyRepositoryReaderConfig extends AbstractConfig implements IsProxyConfig {

    private final String readCron;
    private final int forwardThreadCount;
    private final int maxFileScan;
    private final int maxConcurrentMappedFiles;
    private final int maxAggregation;
    private final long maxStreamSize;

    public ProxyRepositoryReaderConfig() {
        readCron = null;
        forwardThreadCount = 3;
        maxFileScan = 100000;
        maxConcurrentMappedFiles = 100000;
        maxAggregation = 1000;
        maxStreamSize = ModelStringUtil.parseIECByteSizeString("1G");
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ProxyRepositoryReaderConfig(@JsonProperty("readCron") final String readCron,
                                       @JsonProperty("forwardThreadCount") final int forwardThreadCount,
                                       @JsonProperty("maxFileScan") final int maxFileScan,
                                       @JsonProperty("maxConcurrentMappedFiles") final int maxConcurrentMappedFiles,
                                       @JsonProperty("maxAggregation") final int maxAggregation,
                                       @JsonProperty("maxStreamSize") final long maxStreamSize) {
        this.readCron = readCron;
        this.forwardThreadCount = forwardThreadCount;
        this.maxFileScan = maxFileScan;
        this.maxConcurrentMappedFiles = maxConcurrentMappedFiles;
        this.maxAggregation = maxAggregation;
        this.maxStreamSize = maxStreamSize;
    }

    @JsonPropertyDescription("Cron style interval (e.g. every hour '0 * *', every half hour '0,30 * *') to read " +
            "any ready repositories (if not defined we read all the time)")
    @ValidSimpleCron
    @JsonProperty
    public String getReadCron() {
        return readCron;
    }

    @JsonPropertyDescription("Number of threads to forward with")
    @JsonProperty
    public int getForwardThreadCount() {
        return forwardThreadCount;
    }


    @JsonPropertyDescription("Max number of files to scan over during forwarding. Once this limit is reached it " +
            "will wait until next read interval")
    @JsonProperty
    public int getMaxFileScan() {
        return maxFileScan;
    }

    @JsonPropertyDescription("The maximum number of concurrent mapped files we can hold before we send the " +
            "largest set for aggregation")
    @JsonProperty
    public int getMaxConcurrentMappedFiles() {
        return maxConcurrentMappedFiles;
    }

    @JsonPropertyDescription("Aggregate size to break at when building an aggregate. 1G stream maybe a file " +
            "around 100MB in size (with 90% compression)")
    @JsonProperty
    public int getMaxAggregation() {
        return maxAggregation;
    }

    @JsonPropertyDescription("Stream size to break at when building an aggregate")
    @JsonProperty("maxStreamSize")
    public String getMaxStreamSizeString() {
        return ModelStringUtil.formatIECByteSizeString(maxStreamSize);
    }

    @JsonIgnore
    public long getMaxStreamSize() {
        return maxStreamSize;
    }
}
