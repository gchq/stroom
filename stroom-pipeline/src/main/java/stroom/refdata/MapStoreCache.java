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

package stroom.refdata;

import org.ehcache.Cache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.cache.Loader;
import stroom.pool.SecurityHelper;
import stroom.security.SecurityContext;
import stroom.util.cache.CentralCacheManager;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * Implementation class that stores reference data from reference data feeds.
 * </p>
 */
@Component
public final class MapStoreCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapStoreCache.class);

    private static final int MAX_CACHE_ENTRIES = 1000000;

    private final Cache<MapStoreCacheKey, MapStore> cache;
    private final ReferenceDataLoader referenceDataLoader;
    private final MapStoreInternPool internPool;
    private final SecurityContext securityContext;

    @Inject
    public MapStoreCache(final CentralCacheManager cacheManager,
                         final ReferenceDataLoader referenceDataLoader,
                         final MapStoreInternPool internPool,
                         final SecurityContext securityContext) {
        this.referenceDataLoader = referenceDataLoader;
        this.internPool = internPool;
        this.securityContext = securityContext;

        final Loader<MapStoreCacheKey, MapStore> loader = new Loader<MapStoreCacheKey, MapStore>() {
            @Override
            public MapStore load(final MapStoreCacheKey key) throws Exception {
                return create(key);
            }
        };

        final CacheConfiguration<MapStoreCacheKey, MapStore> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(MapStoreCacheKey.class, MapStore.class,
                ResourcePoolsBuilder.heap(MAX_CACHE_ENTRIES))
                .withExpiry(Expirations.timeToIdleExpiration(Duration.of(10, TimeUnit.MINUTES)))
                .withLoaderWriter(loader)
                .build();

        cache = cacheManager.createCache("Reference Data - Map Store Cache", cacheConfiguration);
    }

    public MapStore get(final MapStoreCacheKey key) {
        return cache.get(key);
    }

    private MapStore create(final MapStoreCacheKey mapStoreCacheKey) {
        try (SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            MapStore mapStore = null;

            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Creating reference data map store: " + mapStoreCacheKey.toString());
                }

                // Load the data into the map store.
                mapStore = referenceDataLoader.load(mapStoreCacheKey);
                // Intern the map store so we only have one identical copy in
                // memory.
                if (internPool != null) {
                    mapStore = internPool.intern(mapStore);
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Created reference data map store: " + mapStoreCacheKey.toString());
                }
            } catch (final Throwable e) {
                LOGGER.error(e.getMessage(), e);
            }

            // Make sure this pool always returns some kind of map store even if an
            // exception was thrown during load.
            if (mapStore == null) {
                mapStore = new MapStoreImpl();
            }

            return mapStore;
        }
    }
}
