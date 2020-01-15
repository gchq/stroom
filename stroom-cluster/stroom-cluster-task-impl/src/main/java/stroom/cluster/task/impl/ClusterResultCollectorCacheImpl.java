/*
 * Copyright 2016 Crown Copyright
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

package stroom.cluster.task.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.cluster.task.api.ClusterResultCollector;
import stroom.cluster.task.api.ClusterResultCollectorCache;
import stroom.cluster.task.api.CollectorId;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class ClusterResultCollectorCacheImpl implements ClusterResultCollectorCache, Clearable {
    private static final String CACHE_NAME = "Cluster Result Collector Cache";

    private final ICache<CollectorId, ClusterResultCollector> cache;

    private volatile boolean shutdown;

    @Inject
    public ClusterResultCollectorCacheImpl(final CacheManager cacheManager, final ClusterTaskConfig clusterTaskConfig) {
        cache = cacheManager.create(CACHE_NAME, clusterTaskConfig::getClusterResultCollectorCache);
    }

    void shutdown() {
        shutdown = true;
    }

    @Override
    public void put(final CollectorId collectorId, final ClusterResultCollector<?> clusterResultCollector) {
        if (shutdown) {
            throw new RuntimeException("Stroom is shutting down");
        }

        final Optional<ClusterResultCollector> existing = cache.getOptional(collectorId);
        if (existing.isPresent()) {
            throw new RuntimeException(
                    "Existing item found in cluster result collector cache for key '" + collectorId.toString() + "'");
        }

        cache.put(collectorId, clusterResultCollector);
    }

    @Override
    public ClusterResultCollector<?> get(final CollectorId collectorId) {
        return cache.getOptional(collectorId).orElse(null);
    }

    @Override
    public void remove(final CollectorId id) {
        cache.invalidate(id);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
