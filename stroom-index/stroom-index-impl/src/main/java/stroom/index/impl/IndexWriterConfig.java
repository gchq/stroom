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

    private final CacheConfig activeShardCache;
    private final CacheConfig indexShardWriterCache;
    @Deprecated
    private final IndexShardWriterCacheConfig indexShardWriterCacheConfig;
    private final StroomDuration slowIndexWriteWarningThreshold;
    private final StroomDuration replacedCommitRetention;

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
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public IndexWriterConfig(
            @JsonProperty("activeShardCache") final CacheConfig activeShardCache,
            @JsonProperty("indexShardWriterCache") final CacheConfig indexShardWriterCache,
            @JsonProperty("cache") final IndexShardWriterCacheConfig indexShardWriterCacheConfig,
            @JsonProperty("slowIndexWriteWarningThreshold") final StroomDuration slowIndexWriteWarningThreshold,
            @JsonProperty("replacedCommitRetention") final StroomDuration replacedCommitRetention) {
        this.activeShardCache = activeShardCache;
        this.indexShardWriterCache = indexShardWriterCache;
        this.indexShardWriterCacheConfig = indexShardWriterCacheConfig;
        this.slowIndexWriteWarningThreshold = slowIndexWriteWarningThreshold;
        this.replacedCommitRetention = Objects.requireNonNullElse(
                replacedCommitRetention,
                DEFAULT_REPLACED_COMMIT_RETENTION);
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

    @Override
    public String toString() {
        return "IndexWriterConfig{" +
                "activeShardCache=" + activeShardCache +
                ", indexShardWriterCache=" + indexShardWriterCache +
                ", indexShardWriterCacheConfig=" + indexShardWriterCacheConfig +
                ", slowIndexWriteWarningThreshold=" + slowIndexWriteWarningThreshold +
                ", replacedCommitRetention=" + replacedCommitRetention +
                '}';
    }
}
