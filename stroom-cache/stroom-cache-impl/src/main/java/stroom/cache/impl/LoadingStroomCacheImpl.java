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

import stroom.cache.api.LoadingStroomCache;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.metrics.Metrics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.inject.Provider;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

class LoadingStroomCacheImpl<K, V> extends AbstractStroomCache<K, V> implements LoadingStroomCache<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LoadingStroomCacheImpl.class);

    private final Function<K, V> loadFunction;

    public LoadingStroomCacheImpl(final String name,
                                  final Supplier<CacheConfig> cacheConfigSupplier,
                                  final Function<K, V> loadFunction,
                                  final BiConsumer<K, V> removalNotificationConsumer,
                                  final Provider<Metrics> metricsProvider) {
        super(name, cacheConfigSupplier, removalNotificationConsumer, metricsProvider);
        Objects.requireNonNull(loadFunction);
        this.loadFunction = loadFunction;
        rebuild();
    }

    public LoadingStroomCacheImpl(final String name,
                                  final Supplier<CacheConfig> cacheConfigSupplier,
                                  final Function<K, V> loadFunction,
                                  final Provider<Metrics> metricsProvider) {
        this(name, cacheConfigSupplier, loadFunction, null, metricsProvider);
    }

    @Override
    Cache<K, V> createCacheFromBuilder(final Caffeine<K, V> cacheBuilder) {
        return cacheBuilder.build(loadFunction::apply);
    }

    @Override
    public V get(final K key) {
        return ((LoadingCache<K, V>) super.getCache()).get(key);
    }

    @Override
    public Optional<V> getOptional(final K key) {
        return Optional.ofNullable(((LoadingCache<K, V>) super.getCache()).get(key));
    }

    @Override
    public Optional<V> getIfPresent(final K key) {
        LOGGER.trace(() -> buildMessage("getIfPresent", key));
        return Optional.ofNullable(super.getCache().getIfPresent(key));
    }
}
