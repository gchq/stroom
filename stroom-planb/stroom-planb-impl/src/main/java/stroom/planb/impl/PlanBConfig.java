/*
 * Copyright 2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import jakarta.validation.constraints.Min;

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
    private final StroomDuration mergeStatusRetention;
    private final int sendPartAttempts;
    private final StroomDuration sendPartRetryDelay;

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
                StroomDuration.ofMinutes(10),
                StroomDuration.ofDays(30),
                3,
                StroomDuration.ofSeconds(10));
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
             null,
             StroomDuration.ofDays(30),
             3,
             StroomDuration.ofSeconds(10));
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
                       final StroomDuration mergedCheckpointCacheTtl,
                       @JsonProperty("mergeStatusRetention") final StroomDuration mergeStatusRetention,
                       @JsonProperty("sendPartAttempts") final int sendPartAttempts,
                       @JsonProperty("sendPartRetryDelay") final StroomDuration sendPartRetryDelay) {
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
        this.mergeStatusRetention = mergeStatusRetention;
        this.sendPartAttempts = sendPartAttempts;
        this.sendPartRetryDelay = sendPartRetryDelay;
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
    @JsonPropertyDescription("How long snapshot data remains useful. This bounds both how stale a snapshot " +
                             "may be and still be served, measured from when the store node last confirmed " +
                             "it was current, and how long an inactive snapshot shard is kept before being " +
                             "cleaned up. Should be at least twice minTimeToKeepSnapshots.")
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

    @JsonProperty
    @JsonPropertyDescription("How long to keep the per source merge status records that stop additive " +
                             "stores (histogram and metric) double counting when a merge is rerun after " +
                             "interruption. Records are only pruned once no replayable copy of the source " +
                             "data remains, so this only needs to exceed any realistic replay delay.")
    public StroomDuration getMergeStatusRetention() {
        return mergeStatusRetention;
    }

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("How many times to attempt sending a part of Plan B data to a node, including the " +
                             "first attempt. Only transport level failures, e.g. a DNS lookup failure during a " +
                             "network blip, are retried. A node that answers, even with an error, is not asked " +
                             "again. Set to 1 for no retries.")
    public int getSendPartAttempts() {
        return sendPartAttempts;
    }

    @JsonProperty
    @JsonPropertyDescription("How long to wait between attempts to send a part of Plan B data to a node. " +
                             "Note that the sending processing task is held for the duration of any retries.")
    public StroomDuration getSendPartRetryDelay() {
        return sendPartRetryDelay;
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
               ", mergeStatusRetention=" + mergeStatusRetention +
               ", sendPartAttempts=" + sendPartAttempts +
               ", sendPartRetryDelay=" + sendPartRetryDelay +
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
               Objects.equals(mergedCheckpointCacheTtl, that.mergedCheckpointCacheTtl) &&
               Objects.equals(mergeStatusRetention, that.mergeStatusRetention) &&
               sendPartAttempts == that.sendPartAttempts &&
               Objects.equals(sendPartRetryDelay, that.sendPartRetryDelay);
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
                mergedCheckpointCacheTtl,
                mergeStatusRetention,
                sendPartAttempts,
                sendPartRetryDelay);
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
        private StroomDuration mergeStatusRetention;
        private int sendPartAttempts;
        private StroomDuration sendPartRetryDelay;

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
            this.mergeStatusRetention = StroomDuration.ofDays(30);
            this.sendPartAttempts = 3;
            this.sendPartRetryDelay = StroomDuration.ofSeconds(10);
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
            this.mergeStatusRetention = config.mergeStatusRetention;
            this.sendPartAttempts = config.sendPartAttempts;
            this.sendPartRetryDelay = config.sendPartRetryDelay;
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

        public Builder mergeStatusRetention(final StroomDuration mergeStatusRetention) {
            this.mergeStatusRetention = mergeStatusRetention;
            return this;
        }

        public Builder sendPartAttempts(final int sendPartAttempts) {
            this.sendPartAttempts = sendPartAttempts;
            return this;
        }

        public Builder sendPartRetryDelay(final StroomDuration sendPartRetryDelay) {
            this.sendPartRetryDelay = sendPartRetryDelay;
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
                    mergedCheckpointCacheTtl,
                    mergeStatusRetention,
                    sendPartAttempts,
                    sendPartRetryDelay);
        }
    }
}
