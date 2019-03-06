package stroom.proxy.repo;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.ModelStringUtil;

public class ProxyRepositoryReaderConfig {
    private String readCron;
    private int forwardThreadCount = 3;
    private int maxAggregation = 1000;
    private long maxStreamSize = ModelStringUtil.parseIECByteSizeString("1G");
    private int maxFileScan = 10000;

    /**
     * Cron style interval (e.g. every hour '0 * *', every half hour '0,30 * *') to read any ready repositories (if not defined we read all the time).
     */
    @JsonProperty
    public String getReadCron() {
        return readCron;
    }

    @JsonProperty
    public void setReadCron(final String readCron) {
        this.readCron = readCron;
    }

    /**
     * Number of threads to forward with
     */
    @JsonProperty
    public int getForwardThreadCount() {
        return forwardThreadCount;
    }

    @JsonProperty
    public void setForwardThreadCount(final int forwardThreadCount) {
        this.forwardThreadCount = forwardThreadCount;
    }

    /**
     * Aggregate size to break at when building an aggregate. 1G stream maybe a file around 100MB in size (with 90% compression)
     *
     * @return
     */
    @JsonProperty
    public int getMaxAggregation() {
        return maxAggregation;
    }

    @JsonProperty
    public void setMaxAggregation(final int maxAggregation) {
        this.maxAggregation = maxAggregation;
    }

    /**
     * Stream size to break at when building an aggregate.
     */
    @JsonProperty("maxStreamSize")
    public String getMaxStreamSizeString() {
        return ModelStringUtil.formatIECByteSizeString(maxStreamSize);
    }

    @JsonProperty("maxStreamSize")
    public void setMaxStreamSizeString(final String maxStreamSize) {
        this.maxStreamSize = ModelStringUtil.parseIECByteSizeString(maxStreamSize);
    }

    public long getMaxStreamSize() {
        return maxStreamSize;
    }

    public void setMaxStreamSize(final long maxStreamSize) {
        this.maxStreamSize = maxStreamSize;
    }

    /**
     * Max number of files to scan over during forwarding. Once this limit is reached it will wait until next read interval.
     */
    @JsonProperty
    public int getMaxFileScan() {
        return maxFileScan;
    }

    @JsonProperty
    public void setMaxFileScan(final int maxFileScan) {
        this.maxFileScan = maxFileScan;
    }
}
