package stroom.cache.impl;

import stroom.cache.api.ICache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.github.benmanes.caffeine.cache.Cache;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

public class AbstractICache<K, V> implements ICache<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractICache.class);

    private final Cache<K, V> cache;
    private final String name;

    public AbstractICache(final Cache<K, V> cache,
                          final String name) {
        LOGGER.debug(() -> LogUtil.message("Creating cache {}", name));
        this.cache = cache;
        this.name = name;
    }

    @Override
    public V get(final K key) {
        LOGGER.trace(() -> buildMessage("get", key));
        return cache.getIfPresent(key);
    }

    @Override
    public void put(final K key, final V value) {
        LOGGER.trace(() -> buildMessage("put", key));
        cache.put(key, value);
    }

    @Override
    public Optional<V> getOptional(final K key) {
        LOGGER.trace(() -> buildMessage("getOptional", key));
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    @Override
    public Map<K, V> asMap() {
        return cache.asMap();
    }

    @Override
    public Collection<V> values() {
        return cache.asMap().values();
    }

    @Override
    public void invalidate(final K key) {
        LOGGER.trace(() -> buildMessage("invalidate", key));
        cache.invalidate(key);
    }

    @Override
    public void invalidateEntries(final BiPredicate<K, V> entryPredicate) {
        LOGGER.trace(() -> buildMessage("invalidateEntries"));
        Objects.requireNonNull(entryPredicate);
        cache.asMap()
                .entrySet()
                .stream()
                .filter(entry -> entryPredicate.test(entry.getKey(), entry.getValue()))
                .forEach(entry -> {
                    LOGGER.trace(() -> buildMessage("invalidateEntries", entry.getKey()));
                    cache.invalidate(entry.getKey());
                });
    }

    @Override
    public void remove(final K key) {
        LOGGER.trace(() -> buildMessage("remove", key));
        cache.invalidate(key);
        cache.cleanUp();
    }

    @Override
    public void evictExpiredElements() {
        LOGGER.trace(() -> buildMessage("evictExpiredElements"));
        cache.cleanUp();
    }

    @Override
    public long size() {
        return cache.estimatedSize();
    }

    @Override
    public void clear() {
        LOGGER.trace(() -> buildMessage("clear"));
        CacheUtil.clear(cache);
    }

    private String buildMessage(final String methodName) {
        return methodName + "() - cache: '" + name + "'";
    }

    private String buildMessage(final String methodName, final K key) {
        return methodName + "() - cache: '" + name + "', key: " + key;
    }

    @Override
    public String toString() {
        return "AbstractICache{" +
                "name='" + name + '\'' +
                '}';
    }
}
