package stroom.streamtask;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Singleton;

@Singleton
public class ProxyAggregationConfig {
    private String proxyDir = "${stroom.temp}/proxy";
    private volatile int proxyThreads = 10;
    private volatile int maxAggregation = 10000;
    private volatile int maxAggregationScan = 100000;
    private String maxStreamSize = "1G";

    @JsonPropertyDescription("Folder to look for Stroom Proxy Content to aggregate")
    public String getProxyDir() {
        return proxyDir;
    }

    public void setProxyDir(final String proxyDir) {
        this.proxyDir = proxyDir;
    }

    @JsonPropertyDescription("Number of threads used in aggregation")
    public int getProxyThreads() {
        return proxyThreads;
    }

    public void setProxyThreads(final int proxyThreads) {
        this.proxyThreads = proxyThreads;
    }

    @JsonPropertyDescription("This stops the aggregation after a certain size / nested streams")
    public int getMaxAggregation() {
        return maxAggregation;
    }

    public void setMaxAggregation(final int maxAggregation) {
        this.maxAggregation = maxAggregation;
    }

    @JsonPropertyDescription("The limit of files to inspect before aggregation begins (should be bigger than maxAggregation)")
    public int getMaxAggregationScan() {
        return maxAggregationScan;
    }

    public void setMaxAggregationScan(final int maxAggregationScan) {
        this.maxAggregationScan = maxAggregationScan;
    }

    @JsonPropertyDescription("This stops the aggregation after a certain size / nested streams")
    public String getMaxStreamSize() {
        return maxStreamSize;
    }

    public void setMaxStreamSize(final String maxStreamSize) {
        this.maxStreamSize = maxStreamSize;
    }

    @JsonIgnore
    public long getMaxStreamSizeBytes() {
        return ModelStringUtil.parseIECByteSizeString(maxStreamSize);
    }
}
