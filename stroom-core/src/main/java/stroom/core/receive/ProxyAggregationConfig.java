package stroom.core.receive;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.IsConfig;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Singleton;

@Singleton
public class ProxyAggregationConfig implements IsConfig {
    private String proxyDir = "${stroom.temp}/proxy";
    private volatile int proxyThreads = 10;
    private volatile int maxFilesPerAggregate = 10000;
    private volatile int maxConcurrentMappedFiles = 100000;
    private String maxUncompressedFileSize = "1G";

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

    @JsonPropertyDescription("The maximum number of files that can be aggregated together")
    public int getMaxFilesPerAggregate() {
        return maxFilesPerAggregate;
    }

    public void setMaxFilesPerAggregate(final int maxFilesPerAggregate) {
        this.maxFilesPerAggregate = maxFilesPerAggregate;
    }

    @JsonPropertyDescription("The maximum number of file references in aggregation file sets to hold in memory prior to aggregation")
    public int getMaxConcurrentMappedFiles() {
        return maxConcurrentMappedFiles;
    }

    public void setMaxConcurrentMappedFiles(final int maxConcurrentMappedFiles) {
        this.maxConcurrentMappedFiles = maxConcurrentMappedFiles;
    }

    @JsonPropertyDescription("The maximum total size of the uncompressed contents that will be held in an aggregate unless the first and only aggregated file exceeds this limit")
    public String getMaxUncompressedFileSize() {
        return maxUncompressedFileSize;
    }

    public void setMaxUncompressedFileSize(final String maxUncompressedFileSize) {
        this.maxUncompressedFileSize = maxUncompressedFileSize;
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
