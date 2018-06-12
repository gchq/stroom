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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Clearable;
import stroom.security.Security;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * Implementation class that stores reference data from reference data feeds.
 * </p>
 */
@Singleton
@Deprecated
public final class MapStoreCache implements Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapStoreCache.class);

    private static final int MAX_CACHE_ENTRIES = 100;

    private final LoadingCache<MapStoreCacheKey, MapStore> cache;
    private final ReferenceDataLoader referenceDataLoader;
    private final MapStoreInternPool internPool;
    private final Security security;

    @Inject
    @SuppressWarnings("unchecked")
    MapStoreCache(final CacheManager cacheManager,
                  final ReferenceDataLoader referenceDataLoader,
                  final MapStoreInternPool internPool,
                  final Security security) {
        this.referenceDataLoader = referenceDataLoader;
        this.internPool = internPool;
        this.security = security;

        final CacheLoader<MapStoreCacheKey, MapStore> cacheLoader = CacheLoader.from(this::create);
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(1, TimeUnit.HOURS);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Reference Data - Map Store Cache", cacheBuilder, cache);
    }

    public MapStore get(final MapStoreCacheKey key) {
        return cache.getUnchecked(key);
    }

    private MapStore create(final MapStoreCacheKey mapStoreCacheKey) {
        return security.asProcessingUserResult(() -> {
            MapStore mapStore = null;

            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Creating reference data map store: " + mapStoreCacheKey.toString());
                }

                // Load the data into the map store.
//                mapStore = referenceDataLoader.load(ref);
                // Intern the map store so we only have one identical copy in
                // memory.
                if (internPool != null) {
                    mapStore = internPool.intern(mapStore);
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Created reference data map store: " + mapStoreCacheKey.toString());
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            // Make sure this pool always returns some kind of map store even if an
            // exception was thrown during load.
            if (mapStore == null) {
                mapStore = new MapStoreImpl();
            }

            return mapStore;
        });
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
    }
}
