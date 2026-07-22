package stroom.planb.impl;

import stroom.planb.shared.StateType;
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
    private final StroomDuration minTimeToKeepSnapshotEnv;
    private final StroomDuration snapshotRetryFetchInterval;
    private final java.util.Map<StateType, Integer> defaultShardCounts;
    private final int shardMergeThreadCount;
    private final StroomDuration minTimeToKeepStoreShardEnv;
    private final StroomDuration mergedCheckpointCacheTtl;

    public PlanBConfig() {
        this("planb");
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
                StroomDuration.ofMinutes(20),
                StroomDuration.ofMinutes(1),
                java.util.Map.of(
                        StateType.STATE, 16,
                        StateType.TEMPORAL_STATE, 32,
                        StateType.RANGED_STATE, 16,
                        StateType.TEMPORAL_RANGED_STATE, 32,
                        StateType.SESSION, 32,
                        StateType.HISTOGRAM, 16,
                        StateType.METRIC, 16,
                        StateType.TRACE, 64
                ),
                4,
                StroomDuration.ofMinutes(60),
                StroomDuration.ofMinutes(10));
    }

    @Deprecated
    public PlanBConfig(final CacheConfig stateDocCache,
                       final List<String> nodeList,
                       final String path,
                       final StroomDuration minTimeToKeepSnapshots,
                       final StroomDuration minTimeToKeepSnapshotEnv,
                       final StroomDuration snapshotRetryFetchInterval) {
        this(stateDocCache,
             nodeList,
             path,
             minTimeToKeepSnapshots,
             minTimeToKeepSnapshotEnv,
             snapshotRetryFetchInterval,
             null,
             4,
             null,
             null);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public PlanBConfig(@JsonProperty("stateDocCache") final CacheConfig stateDocCache,
                       @JsonProperty("nodeList") final List<String> nodeList,
                       @JsonProperty("path") final String path,
                       @JsonProperty("minTimeToKeepSnapshots") final StroomDuration minTimeToKeepSnapshots,
                       @JsonProperty("minTimeToKeepSnapshotEnv") final StroomDuration minTimeToKeepSnapshotEnv,
                       @JsonProperty("snapshotRetryFetchInterval") final StroomDuration snapshotRetryFetchInterval,
                       @JsonProperty("defaultShardCounts") final java.util.Map<StateType, Integer> defaultShardCounts,
                       @JsonProperty("shardMergeThreadCount") final Integer shardMergeThreadCount,
                       @JsonProperty("minTimeToKeepStoreShardEnv")
                       final StroomDuration minTimeToKeepStoreShardEnv,
                       @JsonProperty("mergedCheckpointCacheTtl")
                       final StroomDuration mergedCheckpointCacheTtl) {
        this.stateDocCache = stateDocCache;
        this.nodeList = nodeList;
        this.path = path;
        this.minTimeToKeepSnapshots = minTimeToKeepSnapshots;
        this.minTimeToKeepSnapshotEnv = minTimeToKeepSnapshotEnv;
        this.snapshotRetryFetchInterval = snapshotRetryFetchInterval;
        this.defaultShardCounts = defaultShardCounts != null ? defaultShardCounts : java.util.Map.of(
                StateType.STATE, 16,
                StateType.TEMPORAL_STATE, 32,
                StateType.RANGED_STATE, 16,
                StateType.TEMPORAL_RANGED_STATE, 32,
                StateType.SESSION, 32,
                StateType.HISTOGRAM, 16,
                StateType.METRIC, 16,
                StateType.TRACE, 64
        );
        this.shardMergeThreadCount = shardMergeThreadCount != null && shardMergeThreadCount > 0
                ? shardMergeThreadCount
                : 4;
        this.minTimeToKeepStoreShardEnv = minTimeToKeepStoreShardEnv != null
                ? minTimeToKeepStoreShardEnv
                : StroomDuration.ofMinutes(60);
        this.mergedCheckpointCacheTtl = mergedCheckpointCacheTtl != null
                ? mergedCheckpointCacheTtl
                : StroomDuration.ofMinutes(10);
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
    @JsonPropertyDescription("How long should we keep a snapshot shard before cleaning it up " +
                             "due to inactivity. Should be at least twice minTimeToKeepSnapshots.")
    public StroomDuration getMinTimeToKeepSnapshotEnv() {
        return minTimeToKeepSnapshotEnv;
    }

    @JsonProperty
    @JsonPropertyDescription("How often should we retry to fetch snapshots when we fail to get a snapshot.")
    public StroomDuration getSnapshotRetryFetchInterval() {
        return snapshotRetryFetchInterval;
    }

    @JsonProperty
    @JsonPropertyDescription("Default shard counts by state type.")
    public java.util.Map<StateType, Integer> getDefaultShardCounts() {
        return defaultShardCounts;
    }

    @JsonProperty
    @JsonPropertyDescription("Maximum number of shard merges to run concurrently per merge cycle. Defaults to 4.")
    public int getShardMergeThreadCount() {
        return shardMergeThreadCount;
    }

    @JsonProperty
    @JsonPropertyDescription("How long to keep a shared-file-store shard's local copy after last use " +
                             "before evicting it due to inactivity. The copy is re-synced from the " +
                             "shared store on next access, so this trades local disk usage against " +
                             "re-sync cost. Defaults to 1 hour.")
    public StroomDuration getMinTimeToKeepStoreShardEnv() {
        return minTimeToKeepStoreShardEnv;
    }

    @JsonProperty
    @JsonPropertyDescription("How long to keep a large split-trace's in-memory merged DFS checkpoint " +
                             "index (used for random-access/last-page paging across the live shard and " +
                             "archive buckets) after last use before evicting it. Defaults to 10 minutes.")
    public StroomDuration getMergedCheckpointCacheTtl() {
        return mergedCheckpointCacheTtl;
    }

    @Override
    public String toString() {
        return "PlanBConfig{" +
               "stateDocCache=" + stateDocCache +
               ", nodeList=" + nodeList +
               ", path='" + path + '\'' +
               ", minTimeToKeepSnapshots=" + minTimeToKeepSnapshots +
               ", minTimeToKeepSnapshotEnv=" + minTimeToKeepSnapshotEnv +
               ", snapshotRetryFetchInterval=" + snapshotRetryFetchInterval +
               ", defaultShardCounts=" + defaultShardCounts +
               ", shardMergeThreadCount=" + shardMergeThreadCount +
               ", minTimeToKeepStoreShardEnv=" + minTimeToKeepStoreShardEnv +
               ", mergedCheckpointCacheTtl=" + mergedCheckpointCacheTtl +
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
               Objects.equals(minTimeToKeepSnapshotEnv, that.minTimeToKeepSnapshotEnv) &&
               Objects.equals(snapshotRetryFetchInterval, that.snapshotRetryFetchInterval) &&
               Objects.equals(defaultShardCounts, that.defaultShardCounts) &&
               shardMergeThreadCount == that.shardMergeThreadCount &&
               Objects.equals(minTimeToKeepStoreShardEnv, that.minTimeToKeepStoreShardEnv) &&
               Objects.equals(mergedCheckpointCacheTtl, that.mergedCheckpointCacheTtl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                stateDocCache,
                nodeList,
                path,
                minTimeToKeepSnapshots,
                minTimeToKeepSnapshotEnv,
                snapshotRetryFetchInterval,
                defaultShardCounts,
                shardMergeThreadCount,
                minTimeToKeepStoreShardEnv,
                mergedCheckpointCacheTtl);
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
        private StroomDuration minTimeToKeepSnapshotEnv;
        private StroomDuration snapshotRetryFetchInterval;
        private java.util.Map<StateType, Integer> defaultShardCounts;
        private int shardMergeThreadCount;
        private StroomDuration minTimeToKeepStoreShardEnv;
        private StroomDuration mergedCheckpointCacheTtl;

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
            this.minTimeToKeepSnapshotEnv = StroomDuration.ofMinutes(20);
            this.snapshotRetryFetchInterval = StroomDuration.ofMinutes(1);
            this.defaultShardCounts = java.util.Map.of(
                    StateType.STATE, 16,
                    StateType.TEMPORAL_STATE, 32,
                    StateType.RANGED_STATE, 16,
                    StateType.TEMPORAL_RANGED_STATE, 32,
                    StateType.SESSION, 32,
                    StateType.HISTOGRAM, 16,
                    StateType.METRIC, 16,
                    StateType.TRACE, 64
            );
            this.shardMergeThreadCount = 4;
            this.minTimeToKeepStoreShardEnv = StroomDuration.ofMinutes(60);
            this.mergedCheckpointCacheTtl = StroomDuration.ofMinutes(10);
        }

        public Builder(final PlanBConfig config) {
            this.stateDocCache = config.stateDocCache;
            this.nodeList = config.nodeList;
            this.path = config.path;
            this.minTimeToKeepSnapshots = config.minTimeToKeepSnapshots;
            this.minTimeToKeepSnapshotEnv = config.minTimeToKeepSnapshotEnv;
            this.snapshotRetryFetchInterval = config.snapshotRetryFetchInterval;
            this.defaultShardCounts = config.defaultShardCounts;
            this.shardMergeThreadCount = config.shardMergeThreadCount;
            this.minTimeToKeepStoreShardEnv = config.minTimeToKeepStoreShardEnv;
            this.mergedCheckpointCacheTtl = config.mergedCheckpointCacheTtl;
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

        public Builder minTimeToKeepSnapshotEnv(final StroomDuration minTimeToKeepSnapshotEnv) {
            this.minTimeToKeepSnapshotEnv = minTimeToKeepSnapshotEnv;
            return this;
        }

        public Builder snapshotRetryFetchInterval(final StroomDuration snapshotRetryFetchInterval) {
            this.snapshotRetryFetchInterval = snapshotRetryFetchInterval;
            return this;
        }

        public Builder defaultShardCounts(final java.util.Map<StateType, Integer> defaultShardCounts) {
            this.defaultShardCounts = defaultShardCounts;
            return this;
        }

        public Builder shardMergeThreadCount(final int shardMergeThreadCount) {
            this.shardMergeThreadCount = shardMergeThreadCount;
            return this;
        }

        public Builder minTimeToKeepStoreShardEnv(final StroomDuration minTimeToKeepStoreShardEnv) {
            this.minTimeToKeepStoreShardEnv = minTimeToKeepStoreShardEnv;
            return this;
        }

        public Builder mergedCheckpointCacheTtl(final StroomDuration mergedCheckpointCacheTtl) {
            this.mergedCheckpointCacheTtl = mergedCheckpointCacheTtl;
            return this;
        }

        public PlanBConfig build() {
            return new PlanBConfig(
                    stateDocCache,
                    nodeList,
                    path,
                    minTimeToKeepSnapshots,
                    minTimeToKeepSnapshotEnv,
                    snapshotRetryFetchInterval,
                    defaultShardCounts,
                    shardMergeThreadCount,
                    minTimeToKeepStoreShardEnv,
                    mergedCheckpointCacheTtl);
        }
    }
}
