/*
 * Copyright 2017 Crown Copyright
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

package stroom.util.cache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.springframework.stereotype.Component;
import stroom.util.spring.StroomShutdown;
import stroom.util.spring.StroomStartup;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CentralCacheManager implements AutoCloseable {
    private CacheManager cacheManager;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    @StroomStartup
    public void start() {
        getCacheManager();
    }

    @StroomShutdown
    public void stop() {
        try {
            close();
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private synchronized CacheManager getCacheManager() {
        if (cacheManager == null) {
            cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
//                .withCache(alias, cacheConfiguration)
                    .build(true);
        }
        return cacheManager;
    }

    @Override
    public synchronized void close() throws Exception {
        if (cacheManager != null) {
            cacheManager.close();
            cacheManager = null;
        }
    }



    //    public CentralCacheManager() {
//        CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
//                .withCache("preConfigured",
//                        CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class,
//                                ResourcePoolsBuilder.heap(100))
//                                .build())
//                .build(true);
//
//        Cache<Long, String> preConfigured
//                = cacheManager.getCache("preConfigured", Long.class, String.class);
//
//        Cache<Long, String> myCache = cacheManager.createCache("myCache",
//                CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class,
//                        ResourcePoolsBuilder.heap(100)).build());
//
//        myCache.put(1L, "da one!");
//        String value = myCache.get(1L);
//
//        cacheManager.close();
//    }

    public <K, V> Cache<K, V> createCache(final String alias, final CacheConfiguration<K, V> cacheConfiguration) {
        if (caches.containsKey(alias)) {
            throw new RuntimeException("A cache called '" + alias + "' already exists");
        }

        final Cache<K, V> cache = getCacheManager().createCache(alias, cacheConfiguration);
        caches.put(alias, cache);
        return cache;
    }

    public Map<String, Cache> getCaches() {
        return caches;
    }

    //    	<defaultCache eternal="true" maxElementsInMemory="100"
//    overflowToDisk="false" />
//
//
//	<!-- Cache where nothing much changes ... 10min old -->
//	<cache name="serviceCacheLong" maxElementsInMemory="1000"
//    eternal="false" overflowToDisk="false" timeToIdleSeconds="600"
//    timeToLiveSeconds="600" />
//
//	<!-- The following caches are all for Statistics -->
//
//	<cache name="UIDCacheGetOrCreateId" eternal="false"
//    maxElementsInMemory="100000" overflowToDisk="false" timeToIdleSeconds="600"
//    timeToLiveSeconds="600" />
//
//	<cache name="UIDCacheGetName" eternal="false"
//    maxElementsInMemory="100000" overflowToDisk="false" timeToIdleSeconds="600"
//    timeToLiveSeconds="600" />
//
//	<cache name="RowKeyCache" eternal="false" maxElementsInMemory="100000"
//    overflowToDisk="false" timeToIdleSeconds="600" timeToLiveSeconds="600" />
//
//	<cache name="StatisticDataSourceCacheById" eternal="false"
//    maxElementsInMemory="1000" overflowToDisk="false" timeToIdleSeconds="600"
//    timeToLiveSeconds="600" />
//
//	<cache name="StatisticDataSourceCacheByNameEngine" eternal="false"
//    maxElementsInMemory="1000" overflowToDisk="false" timeToIdleSeconds="600"
//    timeToLiveSeconds="600" />
}
