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

package stroom.proxy.app.event;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class EventStoreConfig extends AbstractConfig implements IsProxyConfig {

    public static final StroomDuration DEFAULT_ROLL_FREQUENCY = StroomDuration.ofSeconds(10);
    public static final StroomDuration DEFAULT_MAX_AGE = StroomDuration.ofMinutes(1);
    public static final long DEFAULT_MAX_EVENT_COUNT = Long.MAX_VALUE;
    public static final long DEFAULT_MAX_BYTE_COUNT = Long.MAX_VALUE;
    public static final int DEFAULT_FORWARD_QUEUE_SIZE = 1_000;
    public static final CacheConfig DEFAULT_OPEN_FILES_CACHE_CONFIG = CacheConfig.builder()
            .maximumSize(100)
            .statisticsMode(CacheConfig.PROXY_DEFAULT_STATISTICS_MODE)
            .build();

    @JsonProperty
    private final StroomDuration rollFrequency;
    @JsonProperty
    private final StroomDuration maxAge;
    @JsonProperty
    private final long maxEventCount;
    @JsonProperty
    private final long maxByteCount;
    @JsonProperty
    private final CacheConfig openFilesCache;
    @JsonProperty
    private final int forwardQueueSize;

    public EventStoreConfig() {
        rollFrequency = DEFAULT_ROLL_FREQUENCY;
        maxAge = DEFAULT_MAX_AGE;
        maxEventCount = DEFAULT_MAX_EVENT_COUNT;
        maxByteCount = DEFAULT_MAX_BYTE_COUNT;
        openFilesCache = DEFAULT_OPEN_FILES_CACHE_CONFIG;
        forwardQueueSize = DEFAULT_FORWARD_QUEUE_SIZE;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public EventStoreConfig(@JsonProperty("rollFrequency") final StroomDuration rollFrequency,
                            @JsonProperty("maxAge") final StroomDuration maxAge,
                            @JsonProperty("maxEventCount") final Long maxEventCount,
                            @JsonProperty("maxByteCount") final Long maxByteCount,
                            @JsonProperty("openFilesCache") final CacheConfig openFilesCache,
                            @JsonProperty("forwardQueueSize") final Integer forwardQueueSize) {
        this.rollFrequency = Objects.requireNonNullElse(rollFrequency, DEFAULT_ROLL_FREQUENCY);
        this.maxAge = Objects.requireNonNullElse(maxAge, DEFAULT_MAX_AGE);
        this.maxEventCount = Objects.requireNonNullElse(maxEventCount, DEFAULT_MAX_EVENT_COUNT);
        this.maxByteCount = Objects.requireNonNullElse(maxByteCount, DEFAULT_MAX_BYTE_COUNT);
        this.openFilesCache = Objects.requireNonNullElse(openFilesCache, DEFAULT_OPEN_FILES_CACHE_CONFIG);
        this.forwardQueueSize = Objects.requireNonNullElse(forwardQueueSize, DEFAULT_FORWARD_QUEUE_SIZE);
    }

    @NotNull
    public StroomDuration getRollFrequency() {
        return rollFrequency;
    }

    @NotNull
    public StroomDuration getMaxAge() {
        return maxAge;
    }

    @Min(0)
    public long getMaxEventCount() {
        return maxEventCount;
    }

    @Min(0)
    public long getMaxByteCount() {
        return maxByteCount;
    }

    @RequiresProxyRestart
    public CacheConfig getOpenFilesCache() {
        return openFilesCache;
    }

    @RequiresProxyRestart
    @Min(0)
    public int getForwardQueueSize() {
        return forwardQueueSize;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EventStoreConfig that = (EventStoreConfig) o;
        return maxEventCount == that.maxEventCount
               && maxByteCount == that.maxByteCount
               && openFilesCache == that.openFilesCache
               && forwardQueueSize == that.forwardQueueSize
               && Objects.equals(rollFrequency, that.rollFrequency)
               && Objects.equals(maxAge, that.maxAge);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rollFrequency, maxAge, maxEventCount, maxByteCount, openFilesCache, forwardQueueSize);
    }

    @Override
    public String toString() {
        return "EventStoreConfig{" +
               "rollFrequency=" + rollFrequency +
               ", maxAge=" + maxAge +
               ", maxEventCount=" + maxEventCount +
               ", maxByteCount=" + maxByteCount +
               ", openFilesCache=" + openFilesCache +
               ", forwardQueueSize=" + forwardQueueSize +
               '}';
    }
}
