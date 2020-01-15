package stroom.cache.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class CacheHolder {
    private final Caffeine cacheBuilder;
    private final Cache cache;

    public CacheHolder(final Caffeine cacheBuilder, final Cache cache) {
        this.cacheBuilder = cacheBuilder;
        this.cache = cache;
    }

    public Caffeine getCacheBuilder() {
        return cacheBuilder;
    }

    public Cache getCache() {
        return cache;
    }
}