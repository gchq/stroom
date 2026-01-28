package stroom.planb.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class PlanBConfig extends AbstractConfig implements IsStroomConfig {

    private final CacheConfig stateDocCache;
    private final List<String> nodeList;
    private final String path;
    private final StroomDuration minTimeToKeepSnapshots;
    private final StroomDuration minTimeToKeepEnvOpen;
    private final StroomDuration snapshotRetryFetchInterval;

    public PlanBConfig() {
        this("${stroom.home}/planb");
    }

    public PlanBConfig(final String path) {
        this(CacheConfig
                        .builder()
                        .maximumSize(1000L)
                        .expireAfterWrite(StroomDuration.ofMinutes(10))
                        .build(),
                Collections.emptyList(),
                path,
                StroomDuration.ofMinutes(10),
                StroomDuration.ofMinutes(1),
                StroomDuration.ofMinutes(1));
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public PlanBConfig(@JsonProperty("stateDocCache") final CacheConfig stateDocCache,
                       @JsonProperty("nodeList") final List<String> nodeList,
                       @JsonProperty("path") final String path,
                       @JsonProperty("minTimeToKeepSnapshots") final StroomDuration minTimeToKeepSnapshots,
                       @JsonProperty("minTimeToKeepEnvOpen") final StroomDuration minTimeToKeepEnvOpen,
                       @JsonProperty("snapshotRetryFetchInterval") final StroomDuration snapshotRetryFetchInterval) {
        this.stateDocCache = stateDocCache;
        this.nodeList = nodeList;
        this.path = path;
        this.minTimeToKeepSnapshots = minTimeToKeepSnapshots;
        this.minTimeToKeepEnvOpen = minTimeToKeepEnvOpen;
        this.snapshotRetryFetchInterval = snapshotRetryFetchInterval;
    }

    @JsonProperty
    @JsonPropertyDescription("Cache for Plan B state docs.")
    public CacheConfig getStateDocCache() {
        return stateDocCache;
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

    @JsonProperty
    @JsonPropertyDescription("How long should we keep snapshots before we fetch new ones.")
    public StroomDuration getMinTimeToKeepSnapshots() {
        return minTimeToKeepSnapshots;
    }

    @JsonProperty
    @JsonPropertyDescription("How long should we keep an environment open but inactive.")
    public StroomDuration getMinTimeToKeepEnvOpen() {
        return minTimeToKeepEnvOpen;
    }

    @JsonProperty
    @JsonPropertyDescription("How often should we retry to fetch snapshots when we fail to get a snapshot.")
    public StroomDuration getSnapshotRetryFetchInterval() {
        return snapshotRetryFetchInterval;
    }

    @Override
    public String toString() {
        return "PlanBConfig{" +
               "stateDocCache=" + stateDocCache +
               ", nodeList=" + nodeList +
               ", path='" + path + '\'' +
               ", minTimeToKeepSnapshots=" + minTimeToKeepSnapshots +
               ", minTimeToKeepEnvOpen=" + minTimeToKeepEnvOpen +
               ", snapshotRetryFetchInterval=" + snapshotRetryFetchInterval +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PlanBConfig that = (PlanBConfig) o;
        return Objects.equals(stateDocCache, that.stateDocCache) &&
               Objects.equals(nodeList, that.nodeList) &&
               Objects.equals(path, that.path) &&
               Objects.equals(minTimeToKeepSnapshots, that.minTimeToKeepSnapshots) &&
               Objects.equals(minTimeToKeepEnvOpen, that.minTimeToKeepEnvOpen) &&
               Objects.equals(snapshotRetryFetchInterval, that.snapshotRetryFetchInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                stateDocCache,
                nodeList,
                path,
                minTimeToKeepSnapshots,
                minTimeToKeepEnvOpen,
                snapshotRetryFetchInterval);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder {

        private CacheConfig stateDocCache;
        private List<String> nodeList;
        private String path;
        private StroomDuration minTimeToKeepSnapshots;
        private StroomDuration minTimeToKeepEnvOpen;
        private StroomDuration snapshotRetryFetchInterval;

        public Builder() {
            // Set defaults
            this.stateDocCache = CacheConfig
                    .builder()
                    .maximumSize(1000L)
                    .expireAfterWrite(StroomDuration.ofMinutes(10))
                    .build();
            this.nodeList = Collections.emptyList();
            this.path = "${stroom.home}/planb";
            this.minTimeToKeepSnapshots = StroomDuration.ofMinutes(10);
            this.minTimeToKeepEnvOpen = StroomDuration.ofMinutes(1);
            this.snapshotRetryFetchInterval = StroomDuration.ofMinutes(1);
        }

        public Builder(final PlanBConfig config) {
            this.stateDocCache = config.stateDocCache;
            this.nodeList = config.nodeList;
            this.path = config.path;
            this.minTimeToKeepSnapshots = config.minTimeToKeepSnapshots;
            this.minTimeToKeepEnvOpen = config.minTimeToKeepEnvOpen;
            this.snapshotRetryFetchInterval = config.snapshotRetryFetchInterval;
        }

        public Builder stateDocCache(final CacheConfig stateDocCache) {
            this.stateDocCache = stateDocCache;
            return this;
        }

        public Builder nodeList(final List<String> nodeList) {
            this.nodeList = nodeList;
            return this;
        }

        public Builder path(final String path) {
            this.path = path;
            return this;
        }

        public Builder minTimeToKeepSnapshots(final StroomDuration minTimeToKeepSnapshots) {
            this.minTimeToKeepSnapshots = minTimeToKeepSnapshots;
            return this;
        }

        public Builder minTimeToKeepEnvOpen(final StroomDuration minTimeToKeepEnvOpen) {
            this.minTimeToKeepEnvOpen = minTimeToKeepEnvOpen;
            return this;
        }

        public Builder snapshotRetryFetchInterval(final StroomDuration snapshotRetryFetchInterval) {
            this.snapshotRetryFetchInterval = snapshotRetryFetchInterval;
            return this;
        }

        public PlanBConfig build() {
            return new PlanBConfig(
                    stateDocCache,
                    nodeList,
                    path,
                    minTimeToKeepSnapshots,
                    minTimeToKeepEnvOpen,
                    snapshotRetryFetchInterval);
        }
    }
}
