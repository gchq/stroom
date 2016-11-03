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

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import stroom.util.logging.StroomLogger;
import org.springframework.stereotype.Component;

import stroom.cache.AbstractCacheBean;
import net.sf.ehcache.CacheManager;

/**
 * <p>
 * Implementation class that stores reference data from reference data feeds.
 * </p>
 */
@Component
public final class MapStoreCache extends AbstractCacheBean<MapStoreCacheKey, MapStore> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(MapStoreCache.class);

    private static final int MAX_CACHE_ENTRIES = 1000000;

    private final ReferenceDataLoader referenceDataLoader;
    private final MapStoreInternPool internPool;

    @Inject
    public MapStoreCache(final CacheManager cacheManager, final ReferenceDataLoader referenceDataLoader,
            final MapStoreInternPool internPool) {
        super(cacheManager, "Reference Data - Map Store Cache", MAX_CACHE_ENTRIES);
        this.referenceDataLoader = referenceDataLoader;
        this.internPool = internPool;
        setMaxIdleTime(10, TimeUnit.MINUTES);
        setMaxLiveTime(10, TimeUnit.MINUTES);
    }

    @Override
    public MapStore create(final MapStoreCacheKey mapStoreCacheKey) {
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
            LOGGER.error(e, e);
        }

        // Make sure this pool always returns some kind of map store even if an
        // exception was thrown during load.
        if (mapStore == null) {
            mapStore = new MapStoreImpl();
        }

        return mapStore;
    }

    public ReferenceDataLoader getReferenceDataLoader() {
        return referenceDataLoader;
    }
}
