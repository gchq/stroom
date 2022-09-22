package stroom.cache.impl;

import stroom.cache.api.LoadingICache;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

class LoadingCacheImpl<K, V> extends AbstractICache<K, V> implements LoadingICache<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LoadingCacheImpl.class);

    private final Function<K, V> loadFunction;

    public LoadingCacheImpl(final String name,
                            final Supplier<CacheConfig> cacheConfigSupplier,
                            final Function<K, V> loadFunction,
                            final BiConsumer<K, V> removalNotificationConsumer) {
        super(name, cacheConfigSupplier, removalNotificationConsumer);
        Objects.requireNonNull(loadFunction);
        this.loadFunction = loadFunction;
        rebuild();
    }

    public LoadingCacheImpl(final String name,
                            final Supplier<CacheConfig> cacheConfigSupplier,
                            final Function<K, V> loadFunction) {
        this(name, cacheConfigSupplier, loadFunction, null);
    }

    @Override
    Cache<K, V> createCacheFromBuilder(final Caffeine<K, V> cacheBuilder) {
        return cacheBuilder.build(loadFunction::apply);
    }

    @Override
    public V get(final K key) {
        return ((LoadingCache<K, V>) super.cache).get(key);
    }

    @Override
    public Optional<V> getOptional(final K key) {
        return Optional.ofNullable(((LoadingCache<K, V>) super.cache).get(key));
    }

    @Override
    public Optional<V> getIfPresent(final K key) {
        LOGGER.trace(() -> buildMessage("getIfPresent", key));
        return Optional.ofNullable(getWithCacheUnderReadLockOptimistically(cache ->
                cache.getIfPresent(key)));
    }
}
