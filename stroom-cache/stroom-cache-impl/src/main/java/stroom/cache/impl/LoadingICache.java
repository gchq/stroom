package stroom.cache.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.benmanes.caffeine.cache.LoadingCache;

public class LoadingICache<K, V> extends AbstractICache<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LoadingICache.class);

    private final LoadingCache<K, V> loadingCache;

    public LoadingICache(final LoadingCache<K, V> loadingCache, final String name) {
        super(loadingCache, name);
        this.loadingCache = loadingCache;
    }

    @Override
    public V get(final K key) {
        LOGGER.trace(() -> "get() - " + key);
        return loadingCache.get(key);
    }
}
