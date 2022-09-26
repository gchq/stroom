package stroom.cache.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

class SimpleStroomCacheImpl<K, V> extends AbstractStroomCache<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SimpleStroomCacheImpl.class);

    public SimpleStroomCacheImpl(final String name,
                                 final Supplier<CacheConfig> cacheConfigSupplier,
                                 final BiConsumer<K, V> removalNotificationConsumer) {
        super(name, cacheConfigSupplier, removalNotificationConsumer);
        rebuild();
    }

    public SimpleStroomCacheImpl(final String name,
                                 final Supplier<CacheConfig> cacheConfigSupplier) {
        this(name, cacheConfigSupplier, null);
    }

    @Override
    Cache<K, V> createCacheFromBuilder(final Caffeine<K, V> cacheBuilder) {
        return cacheBuilder.build();
    }

}
