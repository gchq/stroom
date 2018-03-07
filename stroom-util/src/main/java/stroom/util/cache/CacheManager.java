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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Singleton;
import stroom.util.spring.StroomShutdown;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class CacheManager implements AutoCloseable {
    private final Map<String, CacheHolder> caches = new ConcurrentHashMap<>();

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

    @Override
    public synchronized void close() {
        caches.forEach((k, v) -> CacheUtil.clear(v.getCache()));
    }

    public void registerCache(final String alias, final CacheBuilder cacheBuilder, final Cache cache) {
        if (caches.containsKey(alias)) {
            throw new RuntimeException("A cache called '" + alias + "' already exists");
        }

        replaceCache(alias, cacheBuilder, cache);
    }

    public void replaceCache(final String alias, final CacheBuilder cacheBuilder, final Cache cache) {
        final CacheHolder existing = caches.put(alias, new CacheHolder(cacheBuilder, cache));
        if (existing != null) {
            CacheUtil.clear(existing.getCache());
        }
    }

    public Map<String, CacheHolder> getCaches() {
        return caches;
    }

    public static class CacheHolder {
        private final CacheBuilder cacheBuilder;
        private final Cache cache;

        CacheHolder(final CacheBuilder cacheBuilder, final Cache cache) {
            this.cacheBuilder = cacheBuilder;
            this.cache = cache;
        }

        public CacheBuilder getCacheBuilder() {
            return cacheBuilder;
        }

        public Cache getCache() {
            return cache;
        }
    }
}
