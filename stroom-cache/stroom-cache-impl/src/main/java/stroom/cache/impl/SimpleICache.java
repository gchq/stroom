package stroom.cache.impl;

import com.github.benmanes.caffeine.cache.Cache;

public class SimpleICache<K, V> extends AbstractICache<K, V> {

    public SimpleICache(final Cache<K, V> cache, final String name) {
        super(cache, name);
    }
}
