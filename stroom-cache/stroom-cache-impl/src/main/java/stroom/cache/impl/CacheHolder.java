/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.util.cache.CacheConfig;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

class CacheHolder<K, V> {

    private final Cache<K, V> cache;
    private final Caffeine<K, V> cacheBuilder;
    private final CacheConfig cacheConfig;

    CacheHolder(final Cache<K, V> cache,
                final Caffeine<K, V> cacheBuilder,
                final CacheConfig cacheConfig) {
        this.cache = cache;
        this.cacheBuilder = cacheBuilder;
        this.cacheConfig = cacheConfig;
    }

    public Cache<K, V> getCache() {
        return cache;
    }

    public Caffeine<K, V> getCacheBuilder() {
        return cacheBuilder;
    }

    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }
}
