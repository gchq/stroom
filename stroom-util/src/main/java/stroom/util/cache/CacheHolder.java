package stroom.util.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class CacheHolder {
    private final CacheBuilder cacheBuilder;
    private final Cache cache;

    public CacheHolder(final CacheBuilder cacheBuilder, final Cache cache) {
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