package stroom.core.receive;

import stroom.data.zip.BufferSizeUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.ModelStringUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


public class ProxyAggregationConfig extends AbstractConfig {

    private final String proxyDir;
    private final int proxyThreads;

    private final int maxFileScan;
    private final int maxConcurrentMappedFiles;
    private final int maxFilesPerAggregate;
    private final String maxUncompressedFileSize;

    public ProxyAggregationConfig() {
        proxyDir = "proxy_repo";
        proxyThreads = 10;
        maxFileScan = 100000;
        maxConcurrentMappedFiles = 100000;
        maxFilesPerAggregate = 10000;
        maxUncompressedFileSize = "1G";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ProxyAggregationConfig(@JsonProperty("proxyDir") final String proxyDir,
                                  @JsonProperty("bufferSize") final int bufferSize,
                                  @JsonProperty("proxyThreads") final int proxyThreads,
                                  @JsonProperty("maxFileScan") final int maxFileScan,
                                  @JsonProperty("maxConcurrentMappedFiles") final int maxConcurrentMappedFiles,
                                  @JsonProperty("maxFilesPerAggregate") final int maxFilesPerAggregate,
                                  @JsonProperty("maxUncompressedFileSize") final String maxUncompressedFileSize) {
        this.proxyDir = proxyDir;
        BufferSizeUtil.setValue(bufferSize);
        this.proxyThreads = proxyThreads;
        this.maxFileScan = maxFileScan;
        this.maxConcurrentMappedFiles = maxConcurrentMappedFiles;
        this.maxFilesPerAggregate = maxFilesPerAggregate;
        this.maxUncompressedFileSize = maxUncompressedFileSize;
    }

    @JsonPropertyDescription("Directory to look for Stroom Proxy Content to aggregate. Typically this directory " +
            "will belong to the stroom-proxy that is populating the repository in it. If the value is a " +
            "relative path then it will be treated as being relative to stroom.path.home.")
    public String getProxyDir() {
        return proxyDir;
    }

    @JsonPropertyDescription("The amount of memory to use for buffering reads/writes")
    public int getBufferSize() {
        return BufferSizeUtil.get();
    }

    @JsonPropertyDescription("Number of threads used in aggregation")
    public int getProxyThreads() {
        return proxyThreads;
    }

    @JsonPropertyDescription("The limit of files to inspect before aggregation begins (should be bigger than " +
            "maxAggregation)")
    public int getMaxFileScan() {
        return maxFileScan;
    }

    @JsonPropertyDescription("The maximum number of file references in aggregation file sets to hold in memory " +
            "prior to aggregation")
    public int getMaxConcurrentMappedFiles() {
        return maxConcurrentMappedFiles;
    }

    @JsonPropertyDescription("The maximum number of files that can be aggregated together")
    public int getMaxFilesPerAggregate() {
        return maxFilesPerAggregate;
    }

    @JsonPropertyDescription("The maximum total size of the uncompressed contents that will be held in an " +
            "aggregate unless the first and only aggregated file exceeds this limit")
    public String getMaxUncompressedFileSize() {
        return maxUncompressedFileSize;
    }

    @JsonIgnore
    public long getMaxUncompressedFileSizeBytes() {
        return ModelStringUtil.parseIECByteSizeString(maxUncompressedFileSize);
    }

    @Override
    public String toString() {
        return "ProxyAggregationConfig{" +
                "proxyDir='" + proxyDir + '\'' +
                ", proxyThreads=" + proxyThreads +
                ", maxFilesPerAggregate=" + maxFilesPerAggregate +
                ", maxConcurrentMappedFiles=" + maxConcurrentMappedFiles +
                ", maxUncompressedFileSize='" + maxUncompressedFileSize + '\'' +
                '}';
    }
}
