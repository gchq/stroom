/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.data;

import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.db.trace.TraceDb.CheckpointIndex;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.function.Supplier;

/**
 * In-memory cache of merged (live + archive) DFS {@link CheckpointIndex}es for large split traces, so a
 * random-access / last-page request pages by offset cheaply instead of walking the whole merged tree
 * every time. Keyed by trace plus the versions of every contributing store, so it self-invalidates when
 * either the live shard or an archive bucket changes; bounded by a TTL and a maximum trace count.
 */
@Singleton
public class MergedCheckpointCache {

    // A checkpoint index for a multi-million-span trace is only a few MB; bound the number of traces held.
    private static final long MAX_TRACES = 50;

    private final Cache<String, CheckpointIndex> cache;

    @Inject
    public MergedCheckpointCache(final Provider<PlanBConfig> configProvider) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_TRACES)
                .expireAfterAccess(configProvider.get().getMergedCheckpointCacheTtl().getDuration())
                .build();
    }

    /**
     * Returns the cached checkpoint index for {@code key}, building it with {@code builder} on a miss.
     * Caffeine serialises concurrent builds for the same key, so the O(n) build runs at most once.
     */
    public CheckpointIndex getOrBuild(final String key, final Supplier<CheckpointIndex> builder) {
        return cache.get(key, k -> builder.get());
    }

    /** Returns the cached index for {@code key}, or {@code null} if not present (never builds). */
    public CheckpointIndex getIfPresent(final String key) {
        return cache.getIfPresent(key);
    }
}
