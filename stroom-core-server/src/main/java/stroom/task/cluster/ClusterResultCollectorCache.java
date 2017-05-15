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

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import stroom.entity.shared.Clearable;
import stroom.util.spring.StroomShutdown;

import javax.annotation.Resource;
import java.util.List;

@Component
public class ClusterResultCollectorCache implements Clearable, InitializingBean {
    private static final int MAX_CACHE_ENTRIES = 1000000;

    @Resource
    private CacheManager cacheManager;

    private final Cache cache;
    private volatile boolean shutdown;

    public ClusterResultCollectorCache() {
        final CacheConfiguration cacheConfiguration = new CacheConfiguration("Cluster Result Collector Cache",
                MAX_CACHE_ENTRIES);
        cacheConfiguration.setEternal(false);
        // Allow collectors to idle for 10 minutes.
        cacheConfiguration.setTimeToIdleSeconds(600);
        // Allow collectors to live for a maximum of 24 hours.
        cacheConfiguration.setTimeToLiveSeconds(86400);

        cache = new Cache(cacheConfiguration);
    }

    @StroomShutdown
    public void shutdown() {
        shutdown = true;
        final List<Object> keys = cache.getKeys();
        for (final Object key : keys) {
            final Element element = cache.get(key);
            if (element != null) {
                final ClusterResultCollector<?> collector = (ClusterResultCollector<?>) element.getObjectValue();
            }
        }
    }

    public void put(final CollectorId collectorId, final ClusterResultCollector<?> clusterResultCollector) {
        if (shutdown) {
            throw new RuntimeException("Stroom is shutting down");
        }

        final Element element = new Element(collectorId, clusterResultCollector);
        final Element existing = cache.putIfAbsent(element);
        if (existing != null) {
            throw new RuntimeException(
                    "Existing item found in cluster result collector cache for key '" + collectorId.toString() + "'");
        }
    }

    public ClusterResultCollector<?> get(final CollectorId collectorId) {
        final Element element = cache.get(collectorId);
        if (element == null) {
            return null;
        }
        return (ClusterResultCollector<?>) element.getObjectValue();
    }

    public boolean remove(final CollectorId id) {
        return cache.remove(id);
    }

    @Override
    public void clear() {
        cache.removeAll();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        cacheManager.addCache(cache);
    }
}
