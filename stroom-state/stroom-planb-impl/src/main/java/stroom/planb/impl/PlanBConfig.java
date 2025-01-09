package stroom.planb.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class PlanBConfig extends AbstractConfig implements IsStroomConfig {

    private final CacheConfig stateDocCache;
    private final CacheConfig snapshotCache;
    private final CacheConfig readerCache;
    private final List<String> nodeList;
    private final String path;

    public PlanBConfig() {
        stateDocCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
        snapshotCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
        readerCache = CacheConfig.builder()
                .maximumSize(10L)
                .expireAfterWrite(StroomDuration.ofMinutes(5))
                .build();
        nodeList = new ArrayList<>();
        path = "${stroom.home}/planb";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public PlanBConfig(@JsonProperty("stateDocCache") final CacheConfig stateDocCache,
                       @JsonProperty("snapshotCache") final CacheConfig snapshotCache,
                       @JsonProperty("readerCache") final CacheConfig readerCache,
                       @JsonProperty("nodeList") final List<String> nodeList,
                       @JsonProperty("path") final String path) {
        this.stateDocCache = stateDocCache;
        this.snapshotCache = snapshotCache;
        this.readerCache = readerCache;
        this.nodeList = nodeList;
        this.path = path;
    }

    public CacheConfig getStateDocCache() {
        return stateDocCache;
    }

    public CacheConfig getSnapshotCache() {
        return snapshotCache;
    }

    public CacheConfig getReaderCache() {
        return readerCache;
    }

    public List<String> getNodeList() {
        return nodeList;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "PlanBConfig{" +
               "stateDocCache=" + stateDocCache +
               ", snapshotCache=" + snapshotCache +
               ", readerCache=" + readerCache +
               ", nodeList=" + nodeList +
               ", path='" + path + '\'' +
               '}';
    }
}
