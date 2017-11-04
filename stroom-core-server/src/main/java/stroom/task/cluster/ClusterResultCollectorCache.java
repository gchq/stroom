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

package stroom.task.cluster;

import org.ehcache.Cache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.springframework.stereotype.Component;
import stroom.entity.shared.Clearable;
import stroom.util.cache.CentralCacheManager;
import stroom.util.spring.StroomShutdown;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Component
public class ClusterResultCollectorCache implements Clearable {
    private static final int MAX_CACHE_ENTRIES = 1000000;

    private final Cache<CollectorId, ClusterResultCollector> cache;

    private volatile boolean shutdown;

    @Inject
    public ClusterResultCollectorCache(final CentralCacheManager cacheManager) {
        final CacheConfiguration<CollectorId, ClusterResultCollector> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(CollectorId.class, ClusterResultCollector.class,
                ResourcePoolsBuilder.heap(MAX_CACHE_ENTRIES))
                .withExpiry(Expirations.timeToIdleExpiration(Duration.of(1, TimeUnit.MINUTES)))
                .build();

        cache = cacheManager.createCache("Cluster Result Collector Cache", cacheConfiguration);
    }

    @StroomShutdown
    public void shutdown() {
        shutdown = true;
    }

    public void put(final CollectorId collectorId, final ClusterResultCollector<?> clusterResultCollector) {
        if (shutdown) {
            throw new RuntimeException("Stroom is shutting down");
        }

        final ClusterResultCollector existing = cache.putIfAbsent(collectorId, clusterResultCollector);
        if (existing != null) {
            throw new RuntimeException(
                    "Existing item found in cluster result collector cache for key '" + collectorId.toString() + "'");
        }
    }

    public ClusterResultCollector<?> get(final CollectorId collectorId) {
        return cache.get(collectorId);
    }

    public void remove(final CollectorId id) {
        cache.remove(id);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
