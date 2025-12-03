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
import stroom.util.metrics.Metrics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Provider;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

class StroomCacheImpl<K, V> extends AbstractStroomCache<K, V> {

    public StroomCacheImpl(final String name,
                           final Supplier<CacheConfig> cacheConfigSupplier,
                           final BiConsumer<K, V> removalNotificationConsumer,
                           final Provider<Metrics> metricsProvider) {
        super(name, cacheConfigSupplier, removalNotificationConsumer, metricsProvider);
        rebuild();
    }

    @Override
    Cache<K, V> createCacheFromBuilder(final Caffeine<K, V> cacheBuilder) {
        return cacheBuilder.build();
    }
}
