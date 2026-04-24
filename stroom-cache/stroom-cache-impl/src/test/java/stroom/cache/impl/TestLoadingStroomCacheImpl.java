/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.cache.impl;

import stroom.cache.api.LoadingStroomCache;
import stroom.test.common.MockMetrics;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

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

import static org.assertj.core.api.Assertions.assertThat;

class TestLoadingStroomCacheImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLoadingStroomCacheImpl.class);

    private static final String NAME = "My Cache";
    protected static final int ALL_MONTHS_COUNT = 12;

    private LoadingStroomCache<Integer, String> cache;
    private AtomicReference<BiConsumer<Integer, String>> removalConsumerRef = new AtomicReference<>(null);
    private CacheConfig cacheConfig = CacheConfig.builder()
            .build();
    private final List<Integer> numbers = new CopyOnWriteArrayList<>();
    private final List<String> monthNames = new CopyOnWriteArrayList<>();
    private final AtomicInteger loadFuncCallCounter = new AtomicInteger();

    @BeforeEach
    void beforeEach() {
        cache = new LoadingStroomCacheImpl<>(
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
                },
                MockMetrics::new);
    }

    @Test
    void testGet_hit() {
        assertThat(cache.size())
                .isZero();

        final String val1 = cache.get(5);

        assertThat(loadFuncCallCounter)
                .hasValue(1);
        assertThat(cache.size())
                .isEqualTo(1);
        assertThat(val1)
                .isEqualTo("May");

        final String val2 = cache.get(12);

        assertThat(loadFuncCallCounter)
                .hasValue(2);
        assertThat(cache.size())
                .isEqualTo(2);
        assertThat(val2)
                .isEqualTo("December");
    }

    @Test
    void testGet_miss() {
        assertThat(cache.size())
                .isZero();

        final String val1 = cache.get(999);

        assertThat(loadFuncCallCounter)
                .hasValue(1);
        assertThat(cache.size())
                .isEqualTo(0);
        assertThat(val1)
                .isNull();
    }

    @Test
    void testGetOptional_hit() {
        assertThat(cache.size())
                .isZero();

        final Optional<String> optVal = cache.getOptional(5);

        // getOptional does self load items
        assertThat(loadFuncCallCounter)
                .hasValue(1);

        assertThat(cache.size())
                .isEqualTo(1);

        assertThat(optVal)
                .hasValue(numberToMonth(5));
    }

    @Test
    void testGetOptional_miss() {
        assertThat(cache.size())
                .isZero();

        final Optional<String> optVal = cache.getOptional(999);

        // getOptional does self load items
        assertThat(loadFuncCallCounter)
                .hasValue(1);

        assertThat(cache.size())
                .isEqualTo(0);

        assertThat(optVal)
                .isEmpty();
    }

    @Test
    void testContainsKey_true() {
        cache.get(5);

        assertThat(loadFuncCallCounter)
                .hasValue(1);

        loadFuncCallCounter.set(0);

        assertThat(cache.containsKey(5))
                .isTrue();

        assertThat(loadFuncCallCounter)
                .hasValue(0);
    }

    @Test
    void testContainsKey_false() {
        assertThat(cache.containsKey(999))
                .isFalse();
        assertThat(loadFuncCallCounter)
                .hasValue(0);
    }

    @Test
    void testGetWithValueProvider() {
        assertThat(cache.size())
                .isEqualTo(0);

        final AtomicInteger valueProviderCallCounter = new AtomicInteger();
        final Function<Integer, String> valueProvider = k -> {
            valueProviderCallCounter.incrementAndGet();
            return numberToMonth(k);
        };

        for (int i = 1; i <= ALL_MONTHS_COUNT; i++) {
            final String name = cache.get(i, valueProvider);
            LOGGER.info("i: {}, name: {}", i, name);
            assertThat(name)
                    .isEqualTo(numberToMonth(i));
        }
        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        assertThat(valueProviderCallCounter)
                .hasValue(ALL_MONTHS_COUNT);
        // load func is ignored
        assertThat(loadFuncCallCounter)
                .hasValue(0);

        // Re-get all values, which should all now be in the cache so valueProvider
        // is not used
        for (int i = 1; i <= ALL_MONTHS_COUNT; i++) {
            final String name = cache.get(i, valueProvider);
            LOGGER.info("i: {}, name: {}", i, name);
            assertThat(name)
                    .isEqualTo(numberToMonth(i));
        }

        // No change to call count
        assertThat(valueProviderCallCounter)
                .hasValue(ALL_MONTHS_COUNT);
        assertThat(loadFuncCallCounter)
                .hasValue(0);
    }

    @Test
    void testGetWithValueProvider_valueProviderReturnsNull() {
        assertThat(cache.size())
                .isEqualTo(0);

        final AtomicInteger valueProviderCallCounter = new AtomicInteger();
        final Function<Integer, String> valueProvider = k -> {
            valueProviderCallCounter.incrementAndGet();
            return null;
        };

        // valueProvider will return null but loadFunc will still NOT get used.
        final String name = cache.get(5, valueProvider);

        assertThat(valueProviderCallCounter)
                .hasValue(1);
        assertThat(name)
                .isNull();
        // load func is ignored
        assertThat(loadFuncCallCounter)
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
        assertThat(name)
                .isNull();
        assertThat(valueProviderCallCounter)
                .hasValue(1);
        // LoadFunc ignored as we have a valueProvider
        assertThat(loadFuncCallCounter)
                .hasValue(0);
    }

    @Test
    void testPut() {
        // Make sure put calls work even though this is a loading cache
        assertThat(cache.size())
                .isEqualTo(0);

        cache.put(999, "foo");
        assertThat(cache.size())
                .isEqualTo(1);

        assertThat(cache.getOptional(999))
                .hasValue("foo");

        assertThat(loadFuncCallCounter)
                .hasValue(0);
    }

    @Test
    void testCompute() {
        // Make sure compute works with a loading cache
        assertThat(cache.size())
                .isEqualTo(0);

        assertThat(cache.get(5))
                .isEqualTo("May");

        assertThat(cache.size())
                .isEqualTo(1);

        final String val = cache.compute(5, (k, v) -> {
            assertThat(k)
                    .isEqualTo(5);
            return v + "XXX";
        });
        assertThat(val)
                .isEqualTo("MayXXX");
        assertThat(cache.size())
                .isEqualTo(1);

        // Remove using compute
        final String val2 = cache.compute(5, (ignoredKey, ignoredVal) -> null);
        assertThat(val2)
                .isEqualTo(null);
        assertThat(cache.size())
                .isEqualTo(0);
    }

    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    private String numberToMonth(final int i) {
        if (i >= 1 && i <= ALL_MONTHS_COUNT) {
            return Month.of(i).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        } else {
            return null;
        }
    }
}
