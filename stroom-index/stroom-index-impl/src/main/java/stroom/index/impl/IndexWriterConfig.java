/*
 * Copyright 2016-2025 Crown Copyright
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


@JsonPropertyOrder(alphabetic = true)
public class IndexWriterConfig extends AbstractConfig implements IsStroomConfig {

    private final CacheConfig activeShardCache;
    private final CacheConfig indexShardWriterCache;
    @Deprecated
    private final IndexShardWriterCacheConfig indexShardWriterCacheConfig;
    private final StroomDuration slowIndexWriteWarningThreshold;

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
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public IndexWriterConfig(
            @JsonProperty("activeShardCache") final CacheConfig activeShardCache,
            @JsonProperty("indexShardWriterCache") final CacheConfig indexShardWriterCache,
            @JsonProperty("cache") final IndexShardWriterCacheConfig indexShardWriterCacheConfig,
            @JsonProperty("slowIndexWriteWarningThreshold") final StroomDuration slowIndexWriteWarningThreshold) {
        this.activeShardCache = activeShardCache;
        this.indexShardWriterCache = indexShardWriterCache;
        this.indexShardWriterCacheConfig = indexShardWriterCacheConfig;
        this.slowIndexWriteWarningThreshold = slowIndexWriteWarningThreshold;
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

    @Override
    public String toString() {
        return "IndexWriterConfig{" +
                "activeShardCache=" + activeShardCache +
                ", indexShardWriterCache=" + indexShardWriterCache +
                ", indexShardWriterCacheConfig=" + indexShardWriterCacheConfig +
                ", slowIndexWriteWarningThreshold=" + slowIndexWriteWarningThreshold +
                '}';
    }
}
