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
