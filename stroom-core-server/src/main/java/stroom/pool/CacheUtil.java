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

package stroom.pool;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheUtil.class);

    private CacheUtil() {
        // Utility class.
    }

    public static <K, V> void removeAll(final Cache<K, V> cache) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Removing all items from cache " + cache);
        }

        try {
            cache.forEach(entry -> {
                try {
                    cache.remove(entry.getKey());
                } catch (final Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            });
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
