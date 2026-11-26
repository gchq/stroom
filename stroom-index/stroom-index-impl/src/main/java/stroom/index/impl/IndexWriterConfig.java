/*
 * Copyright 2018 Crown Copyright
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

package stroom.index.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;


@JsonPropertyOrder(alphabetic = true)
public class IndexWriterConfig extends AbstractConfig implements IsStroomConfig {

    private static final StroomDuration DEFAULT_REPLACED_COMMIT_RETENTION = StroomDuration.ofMinutes(30);
    private static final StroomDuration DEFAULT_SHARD_LEASE_DURATION = StroomDuration.ofMinutes(1);
    private static final StroomDuration DEFAULT_SHARD_LEASE_RENEW_INTERVAL = StroomDuration.ofSeconds(20);
    private static final StroomDuration DEFAULT_SHARD_LEASE_TAKEOVER_GRACE = StroomDuration.ofMinutes(5);

    private final CacheConfig activeShardCache;
    private final CacheConfig indexShardWriterCache;
    @Deprecated
    private final IndexShardWriterCacheConfig indexShardWriterCacheConfig;
    private final StroomDuration slowIndexWriteWarningThreshold;
    private final StroomDuration replacedCommitRetention;
    private final StroomDuration shardLeaseDuration;
    private final StroomDuration shardLeaseRenewInterval;
    private final StroomDuration shardLeaseTakeoverGrace;

    public IndexWriterConfig() {
        activeShardCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofHours(1))
                .build();
        indexShardWriterCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterAccess(StroomDuration.ofHours(1))
                .build();
        indexShardWriterCacheConfig = IndexShardWriterCacheConfig.builder()
                .withCoreItems(50)
                .withMaxItems(100)
                .build();
        slowIndexWriteWarningThreshold = StroomDuration.ofSeconds(1);
        replacedCommitRetention = DEFAULT_REPLACED_COMMIT_RETENTION;
        shardLeaseDuration = DEFAULT_SHARD_LEASE_DURATION;
        shardLeaseRenewInterval = DEFAULT_SHARD_LEASE_RENEW_INTERVAL;
        shardLeaseTakeoverGrace = DEFAULT_SHARD_LEASE_TAKEOVER_GRACE;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public IndexWriterConfig(
            @JsonProperty("activeShardCache") final CacheConfig activeShardCache,
            @JsonProperty("indexShardWriterCache") final CacheConfig indexShardWriterCache,
            @JsonProperty("cache") final IndexShardWriterCacheConfig indexShardWriterCacheConfig,
            @JsonProperty("slowIndexWriteWarningThreshold") final StroomDuration slowIndexWriteWarningThreshold,
            @JsonProperty("replacedCommitRetention") final StroomDuration replacedCommitRetention,
            @JsonProperty("shardLeaseDuration") final StroomDuration shardLeaseDuration,
            @JsonProperty("shardLeaseRenewInterval") final StroomDuration shardLeaseRenewInterval,
            @JsonProperty("shardLeaseTakeoverGrace") final StroomDuration shardLeaseTakeoverGrace) {
        this.activeShardCache = activeShardCache;
        this.indexShardWriterCache = indexShardWriterCache;
        this.indexShardWriterCacheConfig = indexShardWriterCacheConfig;
        this.slowIndexWriteWarningThreshold = slowIndexWriteWarningThreshold;
        this.replacedCommitRetention = Objects.requireNonNullElse(
                replacedCommitRetention,
                DEFAULT_REPLACED_COMMIT_RETENTION);
        this.shardLeaseDuration = Objects.requireNonNullElse(
                shardLeaseDuration,
                DEFAULT_SHARD_LEASE_DURATION);
        this.shardLeaseRenewInterval = Objects.requireNonNullElse(
                shardLeaseRenewInterval,
                DEFAULT_SHARD_LEASE_RENEW_INTERVAL);
        this.shardLeaseTakeoverGrace = Objects.requireNonNullElse(
                shardLeaseTakeoverGrace,
                DEFAULT_SHARD_LEASE_TAKEOVER_GRACE);
    }

    public CacheConfig getActiveShardCache() {
        return activeShardCache;
    }

    public CacheConfig getIndexShardWriterCache() {
        return indexShardWriterCache;
    }

    @Deprecated
    @JsonProperty("cache")
    public IndexShardWriterCacheConfig getIndexShardWriterCacheConfig() {
        return indexShardWriterCacheConfig;
    }

    @NotNull
    @JsonProperty
    @JsonPropertyDescription("A warning will be logged for any index shard writes that take longer than " +
            "this threshold to complete. A value of '0' or 'PT0' means no warnings will be logged at all.")
    public StroomDuration getSlowIndexWriteWarningThreshold() {
        return slowIndexWriteWarningThreshold;
    }

    /// Gets how long a writer keeps the commits it has replaced in a shard on a shared volume.
    ///
    /// @return The retention period, never null.
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("How long the writer of an index shard on a shared index volume keeps the " +
            "commits it has replaced before deleting their files. Other nodes may be reading the shard, and a " +
            "search fails if a file it is reading is deleted, so this must be longer than the longest time a " +
            "search holds a shard open. Longer periods use more disk space. A value of '0' or 'PT0' deletes " +
            "replaced commits at once. Shards on local index volumes are not affected.")
    public StroomDuration getReplacedCommitRetention() {
        return replacedCommitRetention;
    }

    /// Gets how long a node's lease on a shard on a shared volume lasts without being renewed.
    ///
    /// @return The lease duration, never null.
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("How long a node's lease on an index shard on a shared index volume lasts if it " +
            "is not renewed. A node must hold the lease to write to the shard. A writer stops adding to a " +
            "shard and discards what it has not committed if it has been unable to renew the lease for three " +
            "quarters of this time. Shards on local index volumes have no lease.")
    public StroomDuration getShardLeaseDuration() {
        return shardLeaseDuration;
    }

    /// Gets how often a node renews the leases it holds on shards on shared volumes.
    ///
    /// @return The renewal interval, never null.
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("How often a node renews the leases it holds on index shards on shared index " +
            "volumes. This should be about a third of shardLeaseDuration, so that one failed renewal does " +
            "not cost the node its leases.")
    public StroomDuration getShardLeaseRenewInterval() {
        return shardLeaseRenewInterval;
    }

    /// Gets how long after a lease on an index shard has expired before the shard is taken from its holder.
    ///
    /// @return The grace period.
    @JsonPropertyDescription("How long after the lease on an index shard on a shared index volume has expired " +
            "before the shard is taken from the node that held it, so that other nodes can write to it. A node " +
            "that cannot renew its leases stops writing well before they expire. This period is the margin " +
            "for a node that was frozen at the moment of a commit and wakes later, so it should be much " +
            "longer than any pause a node could suffer. Until it has passed, new documents go to other shards.")
    public StroomDuration getShardLeaseTakeoverGrace() {
        return shardLeaseTakeoverGrace;
    }

    @Override
    public String toString() {
        return "IndexWriterConfig{" +
                "activeShardCache=" + activeShardCache +
                ", indexShardWriterCache=" + indexShardWriterCache +
                ", indexShardWriterCacheConfig=" + indexShardWriterCacheConfig +
                ", slowIndexWriteWarningThreshold=" + slowIndexWriteWarningThreshold +
                ", replacedCommitRetention=" + replacedCommitRetention +
                ", shardLeaseDuration=" + shardLeaseDuration +
                ", shardLeaseRenewInterval=" + shardLeaseRenewInterval +
                ", shardLeaseTakeoverGrace=" + shardLeaseTakeoverGrace +
                '}';
    }
}
