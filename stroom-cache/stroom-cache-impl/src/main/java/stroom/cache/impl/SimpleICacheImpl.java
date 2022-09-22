package stroom.cache.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

class SimpleICacheImpl<K, V> extends AbstractICache<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SimpleICacheImpl.class);

    public SimpleICacheImpl(final String name,
                            final Supplier<CacheConfig> cacheConfigSupplier,
                            final BiConsumer<K, V> removalNotificationConsumer) {
        super(name, cacheConfigSupplier, removalNotificationConsumer);
        rebuild();
    }

    public SimpleICacheImpl(final String name,
                            final Supplier<CacheConfig> cacheConfigSupplier) {
        this(name, cacheConfigSupplier, null);
    }

    @Override
    Cache<K, V> createCacheFromBuilder() {
        return super.cacheBuilder.build();
    }

}
