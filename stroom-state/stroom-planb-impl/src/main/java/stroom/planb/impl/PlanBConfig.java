package stroom.planb.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class PlanBConfig extends AbstractConfig implements IsStroomConfig {

    private final CacheConfig stateDocCache;
    private final CacheConfig readerCache;
    private final List<String> nodeList;
    private final String path;

    public PlanBConfig() {
        stateDocCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
        readerCache = CacheConfig.builder()
                .maximumSize(10L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
        nodeList = new ArrayList<>();
        path = "${stroom.home}/planb";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public PlanBConfig(@JsonProperty("stateDocCache") final CacheConfig stateDocCache,
                       @JsonProperty("readerCache") final CacheConfig readerCache,
                       @JsonProperty("nodeList") final List<String> nodeList,
                       @JsonProperty("path") final String path) {
        this.stateDocCache = stateDocCache;
        this.readerCache = readerCache;
        this.nodeList = nodeList;
        this.path = path;
    }

    @JsonProperty
    @JsonPropertyDescription("Cache for Plan B state docs.")
    public CacheConfig getStateDocCache() {
        return stateDocCache;
    }

    @JsonProperty
    @JsonPropertyDescription("Cache for Plan B shard readers.")
    public CacheConfig getReaderCache() {
        return readerCache;
    }

    @JsonProperty
    @JsonPropertyDescription("Nodes to use to store Plan B shards. " +
                             "If none are specified only the local node is used. " +
                             "This is only appropriate in a single node setup.")
    public List<String> getNodeList() {
        return nodeList;
    }

    @JsonProperty
    @JsonPropertyDescription("The root path to store shards and snapshots.")
    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "PlanBConfig{" +
               "stateDocCache=" + stateDocCache +
               ", readerCache=" + readerCache +
               ", nodeList=" + nodeList +
               ", path='" + path + '\'' +
               '}';
    }
}
