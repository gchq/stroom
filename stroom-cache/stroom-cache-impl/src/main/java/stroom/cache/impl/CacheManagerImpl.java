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

package stroom.cache.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import stroom.cache.api.CacheHolder;
import stroom.cache.api.CacheManager;
import stroom.cache.api.CacheUtil;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class CacheManagerImpl implements CacheManager {
    private final Map<String, CacheHolder> caches = new ConcurrentHashMap<>();

    @Override
    public synchronized void close() {
        caches.forEach((k, v) -> CacheUtil.clear(v.getCache()));
    }

    @Override
    public void registerCache(final String alias, final CacheBuilder cacheBuilder, final Cache cache) {
        if (caches.containsKey(alias)) {
            throw new RuntimeException("A cache called '" + alias + "' already exists");
        }

        replaceCache(alias, cacheBuilder, cache);
    }

    @Override
    public void replaceCache(final String alias, final CacheBuilder cacheBuilder, final Cache cache) {
        final CacheHolder existing = caches.put(alias, new CacheHolder(cacheBuilder, cache));
        if (existing != null) {
            CacheUtil.clear(existing.getCache());
        }
    }

    @Override
    public Map<String, CacheHolder> getCaches() {
        return caches;
    }
}
