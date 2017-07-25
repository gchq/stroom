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

package stroom.cache;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.shared.CacheInfo;

import java.util.List;

public final class CacheUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCacheBean.class);

    static CacheInfo getInfo(final Ehcache ehcache) {
        CacheInfo info = null;
        if (ehcache != null) {
            final Statistics stats = ehcache.getStatistics();
            if (stats != null) {
                info = new CacheInfo(stats.getAssociatedCacheName(), stats.getCacheHits(),
                        stats.getOnDiskHits(), stats.getOffHeapHits(), stats.getInMemoryHits(), stats.getCacheMisses(),
                        stats.getOnDiskMisses(), stats.getOffHeapMisses(), stats.getInMemoryMisses(), stats.getObjectCount(),
                        stats.getAverageGetTime(), stats.getEvictionCount(), stats.getMemoryStoreObjectCount(),
                        stats.getOffHeapStoreObjectCount(), stats.getDiskStoreObjectCount(), stats.getSearchesPerSecond(),
                        stats.getAverageSearchTime(), stats.getWriterQueueSize());
            }
        }
        return info;
    }

    static void clear(final Ehcache ehcache) {
        // If we do not remove each key individually then the cache items will not be destroyed properly.
        try {
            final List keys = ehcache.getKeys();
            for (final Object key : keys) {
                try {
                    // Try and get the element quietly as we don't want this call to extend the life of sessions that should be dying.
                    ehcache.remove(key);
                } catch (final Throwable t) {
                    LOGGER.error(t.getMessage(), t);
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

//        selfPopulatingCache.removeAll();
    }
}
