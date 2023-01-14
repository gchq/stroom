package stroom.cache.impl;

import stroom.cache.api.LoadingStroomCache;
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

class LoadingStroomCacheImpl<K, V> extends AbstractStroomCache<K, V> implements LoadingStroomCache<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LoadingStroomCacheImpl.class);

    private final Function<K, V> loadFunction;

    public LoadingStroomCacheImpl(final String name,
                                  final Supplier<CacheConfig> cacheConfigSupplier,
                                  final Function<K, V> loadFunction,
                                  final BiConsumer<K, V> removalNotificationConsumer) {
        super(name, cacheConfigSupplier, removalNotificationConsumer);
        Objects.requireNonNull(loadFunction);
        this.loadFunction = loadFunction;
        rebuild();
    }

    public LoadingStroomCacheImpl(final String name,
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
        return Optional.ofNullable(getWithCacheUnderOptimisticReadLock(cache ->
                cache.getIfPresent(key)));
    }
}
