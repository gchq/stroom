package stroom.cache.impl;

import stroom.cache.api.LoadingStroomCache;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

class TestLoadingCacheImpl {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLoadingCacheImpl.class);
    public static final String NAME = "My Cache";

    private LoadingStroomCache<Integer, String> cache;
    private AtomicReference<BiConsumer<Integer, String>> removalConsumerRef = new AtomicReference<>(null);
    private CacheConfig cacheConfig = CacheConfig.builder()
            .build();
    private final List<Integer> numbers = new CopyOnWriteArrayList<>();
    private final List<String> monthNames = new CopyOnWriteArrayList<>();
    private final AtomicInteger loadFuncCallCounter = new AtomicInteger();

    @BeforeEach
    void beforeEach() {
        cache = new LoadingCacheImpl<>(
                NAME,
                this::getCacheConfig,
                k -> {
                    loadFuncCallCounter.incrementAndGet();
                    return numberToMonth(k);
                },
                (k, v) -> {
                    LOGGER.debug("Removal consumer called for k: {}, v: {}", k, v);
                    // Allow test to plug in their own consumer if they want
                    if (removalConsumerRef.get() != null) {
                        removalConsumerRef.get().accept(k, v);
                    }
                });
    }

    @Test
    void testGet_hit() {
        Assertions.assertThat(cache.size())
                .isZero();

        final String val1 = cache.get(5);

        Assertions.assertThat(loadFuncCallCounter)
                .hasValue(1);
        Assertions.assertThat(cache.size())
                .isEqualTo(1);
        Assertions.assertThat(val1)
                .isEqualTo("May");

        final String val2 = cache.get(12);

        Assertions.assertThat(loadFuncCallCounter)
                .hasValue(2);
        Assertions.assertThat(cache.size())
                .isEqualTo(2);
        Assertions.assertThat(val2)
                .isEqualTo("December");
    }

    @Test
    void testGet_miss() {
        Assertions.assertThat(cache.size())
                .isZero();

        final String val1 = cache.get(999);

        Assertions.assertThat(loadFuncCallCounter)
                .hasValue(1);
        Assertions.assertThat(cache.size())
                .isEqualTo(0);
        Assertions.assertThat(val1)
                .isNull();
    }

    @Test
    void testGetOptional_hit() {
        Assertions.assertThat(cache.size())
                .isZero();

        final Optional<String> optVal = cache.getOptional(5);

        // getOptional does self load items
        Assertions.assertThat(loadFuncCallCounter)
                .hasValue(1);

        Assertions.assertThat(cache.size())
                .isEqualTo(1);

        Assertions.assertThat(optVal)
                .hasValue(numberToMonth(5));
    }

    @Test
    void testGetOptional_miss() {
        Assertions.assertThat(cache.size())
                .isZero();

        final Optional<String> optVal = cache.getOptional(999);

        // getOptional does self load items
        Assertions.assertThat(loadFuncCallCounter)
                .hasValue(1);

        Assertions.assertThat(cache.size())
                .isEqualTo(0);

        Assertions.assertThat(optVal)
                .isEmpty();
    }

    @Test
    void testContainsKey_true() {
        cache.get(5);

        Assertions.assertThat(loadFuncCallCounter)
                .hasValue(1);

        loadFuncCallCounter.set(0);

        Assertions.assertThat(cache.containsKey(5))
                .isTrue();

        Assertions.assertThat(loadFuncCallCounter)
                .hasValue(0);
    }

    @Test
    void testContainsKey_false() {
        Assertions.assertThat(cache.containsKey(999))
                .isFalse();
        Assertions.assertThat(loadFuncCallCounter)
                .hasValue(0);
    }

    @Test
    void testGetWithValueProvider() {
        Assertions.assertThat(cache.size())
                .isEqualTo(0);

        final AtomicInteger valueProviderCallCounter = new AtomicInteger();
        final Function<Integer, String> valueProvider = k -> {
            valueProviderCallCounter.incrementAndGet();
            return numberToMonth(k);
        };

        for (int i = 1; i <= 12; i++) {
            final String name = cache.get(i, valueProvider);
            LOGGER.info("i: {}, name: {}", i, name);
            Assertions.assertThat(name)
                    .isEqualTo(numberToMonth(i));
        }
        Assertions.assertThat(cache.size())
                .isEqualTo(12);

        Assertions.assertThat(valueProviderCallCounter)
                .hasValue(12);
        // load func is ignored
        Assertions.assertThat(loadFuncCallCounter)
                .hasValue(0);

        // Re-get all values, which should all now be in the cache so valueProvider
        // is not used
        for (int i = 1; i <= 12; i++) {
            final String name = cache.get(i, valueProvider);
            LOGGER.info("i: {}, name: {}", i, name);
            Assertions.assertThat(name)
                    .isEqualTo(numberToMonth(i));
        }

        // No change to call count
        Assertions.assertThat(valueProviderCallCounter)
                .hasValue(12);
        Assertions.assertThat(loadFuncCallCounter)
                .hasValue(0);
    }

    @Test
    void testGetWithValueProvider_miss() {
        final AtomicInteger valueProviderCallCounter = new AtomicInteger();
        final Function<Integer, String> valueProvider = k -> {
            valueProviderCallCounter.incrementAndGet();
            return numberToMonth(k);
        };
        final int i = 999;
        final String name = cache.get(i, valueProvider);
        LOGGER.info("i: {}, name: {}", i, name);
        Assertions.assertThat(name)
                .isNull();
        Assertions.assertThat(valueProviderCallCounter)
                .hasValue(1);
        // LoadFunc ignored as we have a valueProvider
        Assertions.assertThat(loadFuncCallCounter)
                .hasValue(0);
    }

    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    private String numberToMonth(final int i) {
        if (i >= 1 && i <= 12) {
            return Month.of(i).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        } else {
            return null;
        }
    }
}
