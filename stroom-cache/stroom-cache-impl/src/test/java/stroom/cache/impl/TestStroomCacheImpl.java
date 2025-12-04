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

import stroom.cache.api.StroomCache;
import stroom.test.common.MockMetrics;
import stroom.test.common.TestUtil;
import stroom.util.cache.CacheConfig;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.config.PropertyPathDecorator;
import stroom.util.exception.InterruptibleRunnable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.cache.CacheInfo;
import stroom.util.time.StroomDuration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomCacheImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStroomCacheImpl.class);

    private static final String NAME = "My Cache";
    private static final String MAXIMUM_SIZE = "MaximumSize";
    private static final String EXPIRE_AFTER_WRITE = "ExpireAfterWrite";
    private static final String EXPIRE_AFTER_ACCESS = "ExpireAfterAccess";
    private static final int ALL_MONTHS_COUNT = 12;

    private StroomCache<Integer, String> cache;
    private AtomicReference<BiConsumer<Integer, String>> removalConsumerRef = new AtomicReference<>(null);
    private CacheConfig cacheConfig = CacheConfig.builder()
            .build();
    private final List<Integer> numbers = new CopyOnWriteArrayList<>();
    private final List<String> monthNames = new CopyOnWriteArrayList<>();

    @BeforeEach
    void beforeEach() {
        // Call this to decorate the config with a path
        getCacheConfig();
        cache = createCache();
        populateCache();
    }

    private StroomCache<Integer, String> createCache() {
        return new StroomCacheImpl<>(
                NAME,
                this::getCacheConfig,
                (k, v) -> {
                    LOGGER.debug("Removal consumer called for k: {}, v: {}", k, v);
                    // Allow test to plug in their own consumer if they want
                    if (removalConsumerRef.get() != null) {
                        removalConsumerRef.get().accept(k, v);
                    }
                }, MockMetrics::new);
    }

    private void populateCache() {
        populateCache(ALL_MONTHS_COUNT);
    }

    private void populateCache(final int count) {

        final int limit = Math.min(count, ALL_MONTHS_COUNT);
        for (int i = 1; i <= limit; i++) {
            numbers.add(i);
            final String monthName = numberToMonth(i);
            monthNames.add(monthName);
            cache.put(i, monthName);
        }

        // If a max size is set then some of the 12 may have been removed by now
        // but can't be sure how many as it is async
        if (cacheConfig.getMaximumSize() == null) {
            assertThat(cache.size())
                    .isEqualTo(limit);
        }
    }

    public CacheConfig getCacheConfig() {
        PropertyPathDecorator.decoratePaths(cacheConfig, PropertyPath.fromParts("test", NAME.replace(" ", "")));
        return cacheConfig;
    }

    @Test
    void testName() {
        assertThat(cache.name())
                .isEqualTo(NAME);
    }

    @Test
    void testSize() {
        assertThat(cache.size())
                .isEqualTo(numbers.size());
    }

    @Test
    void testKeySet() {
        assertThat(cache.keySet())
                .containsExactlyInAnyOrderElementsOf(numbers);
    }

    @Test
    void testValues() {
        assertThat(cache.values())
                .containsExactlyInAnyOrderElementsOf(monthNames);
    }

    @Test
    void testGet() {
        numbers.forEach(i -> {
            final String name = cache.get(i);
            LOGGER.info("i: {}, name: {}", i, name);
            assertThat(name)
                    .isEqualTo(monthNames.get(i - 1));
        });
    }

    @Test
    void testGet_miss() {
        final int i = 999;
        final String name = cache.get(i);
        LOGGER.info("i: {}, name: {}", i, name);
        assertThat(name)
                .isNull();
    }

    @Test
    void testGetWithValueProvider() {
        // Remove july onwards
        for (int i = 7; i <= ALL_MONTHS_COUNT; i++) {
            cache.remove(i);
        }
        assertThat(cache.size())
                .isEqualTo(6);

        final AtomicInteger callCounter = new AtomicInteger();

        final Function<Integer, String> valueProvider = k -> {
            callCounter.incrementAndGet();
            return numberToMonth(k);
        };

        // 6 already in the cache, 6 provided by valueProvider
        for (int i = 1; i <= ALL_MONTHS_COUNT; i++) {
            final String name = cache.get(i, valueProvider);
            LOGGER.info("i: {}, name: {}", i, name);
            assertThat(name)
                    .isEqualTo(monthNames.get(i - 1));
        }
        assertThat(callCounter)
                .hasValue(6);

        // Re-get all values, which should all now be in the cache so valueProvider
        // is not used
        for (int i = 1; i <= ALL_MONTHS_COUNT; i++) {
            final String name = cache.get(i, valueProvider);
            LOGGER.info("i: {}, name: {}", i, name);
            assertThat(name)
                    .isEqualTo(monthNames.get(i - 1));
        }

        // No change to call count
        assertThat(callCounter)
                .hasValue(6);
    }

    @Test
    void testGetWithValueProvider_miss() {
        final AtomicInteger callCounter = new AtomicInteger();
        final Function<Integer, String> valueProvider = k -> {
            callCounter.incrementAndGet();
            return numberToMonth(k);
        };
        final int i = 999;
        final String name = cache.get(i, this::numberToMonth);
        LOGGER.info("i: {}, name: {}", i, name);
        assertThat(name)
                .isNull();
        assertThat(callCounter)
                .hasValue(0);
    }

    @Test
    void testPut() {
        Assertions.assertThat(cache.getIfPresent(999))
                .isEmpty();

        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        cache.put(999, "foo");
        assertThat(cache.size())
                .isEqualTo(13);

        Assertions.assertThat(cache.getIfPresent(999))
                .hasValue("foo");
    }

    @Test
    void testPut_overwrite() {

        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        Assertions.assertThat(cache.getIfPresent(5))
                .hasValue("May");

        cache.put(5, "NewMay");
        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        Assertions.assertThat(cache.getIfPresent(5))
                .hasValue("NewMay");
    }

    @Test
    void testGetOptional() {
        numbers.forEach(i -> {
            final Optional<String> optName = cache.getIfPresent(i);
            LOGGER.info("i: {}, name: {}", i, optName);

            assertThat(optName)
                    .hasValue(monthNames.get(i - 1));
        });
    }

    @Test
    void testGetOptional_miss() {
        final int i = 999;
        final Optional<String> optName = cache.getIfPresent(i);
        LOGGER.info("i: {}, name: {}", i, optName);

        assertThat(optName)
                .isEmpty();
    }

    @Test
    void testContainsKey_true() {
        assertThat(cache.containsKey(5))
                .isTrue();
    }

    @Test
    void testContainsKey_false() {
        assertThat(cache.containsKey(999))
                .isFalse();
    }

    @Test
    void testForEach() {
        final List<Integer> numbers = new CopyOnWriteArrayList<>();
        final List<String> monthNames = new CopyOnWriteArrayList<>();

        cache.forEach((i, name) -> {
            numbers.add(i);
            monthNames.add(name);
        });

        assertThat(numbers)
                .containsExactlyInAnyOrderElementsOf(this.numbers);
        assertThat(monthNames)
                .containsExactlyInAnyOrderElementsOf(this.monthNames);
    }

    @Test
    void testRemove() {
        final List<Integer> numbers = new CopyOnWriteArrayList<>();
        final List<String> monthNames = new CopyOnWriteArrayList<>();

        // Set up a removal listener
        removalConsumerRef.set((i, name) -> {
            numbers.add(i);
            monthNames.add(name);
        });

        cache.remove(5);
        assertThat(cache.size())
                .isEqualTo(11);
        assertThat(cache.keySet())
                .doesNotContain(5);
        assertThat(cache.values())
                .doesNotContain("May");

        // removal func seems to be async so wait for it
        TestUtil.waitForIt(
                numbers::size,
                1,
                () -> "numbers size");

        assertThat(numbers)
                .containsExactly(5);
        assertThat(monthNames)
                .containsExactly("May");
    }

    @Test
    void testRemove_emptyCache() {
        cache.clear();

        TestUtil.waitForIt(
                cache::size,
                0L,
                () -> "Cache size");

        assertThat(cache.size())
                .isZero();

        // Should not complain
        cache.remove(5);
    }

    @Test
    void testInvalidate() {
        final List<Integer> numbers = new CopyOnWriteArrayList<>();
        final List<String> monthNames = new CopyOnWriteArrayList<>();

        // Set up a removal listener
        removalConsumerRef.set((i, name) -> {
            numbers.add(i);
            monthNames.add(name);
        });

        cache.invalidate(5);

        // Invalidate appears to be async so allow some time
        TestUtil.waitForIt(
                cache::size,
                11L,
                () -> "Cache");

        assertThat(cache.size())
                .isEqualTo(11);
        assertThat(cache.keySet())
                .doesNotContain(5);
        assertThat(cache.values())
                .doesNotContain("May");

        TestUtil.waitForIt(
                numbers::size,
                1,
                () -> "numbers size");

        assertThat(numbers)
                .containsExactly(5);
        assertThat(monthNames)
                .containsExactly("May");

        // Now invalidate the same entry again to prove that it is repeatable/idempotent
        cache.invalidate(5);
    }

    @Test
    void testInvalidate_emptyCache() {
        cache.clear();

        assertThat(cache.size())
                .isZero();

        // Should not complain
        cache.invalidate(5);
    }

    @Test
    void testInvalidateEntries() {
        final List<Integer> numbersRemoved = new CopyOnWriteArrayList<>();
        final List<String> monthNamesRemoved = new CopyOnWriteArrayList<>();

        // Set up a removal listener
        removalConsumerRef.set((i, name) -> {
            numbersRemoved.add(i);
            monthNamesRemoved.add(name);
        });

        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        // Remove specific entries
        cache.invalidateEntries((i, name) ->
                i == 5 || name.endsWith("ember"));

        // Invalidate is async so give it time
        TestUtil.waitForIt(
                numbersRemoved::size,
                4,
                () -> "4 items to be removed");

        assertThat(monthNamesRemoved)
                .containsExactlyInAnyOrder(
                        "May", "September", "November", "December");

        assertThat(cache.size())
                .isEqualTo(8);
    }

    @Test
    void testInvalidateEntries_empty() {
        final List<String> monthNamesRemoved = new CopyOnWriteArrayList<>();

        // Set up a removal listener
        removalConsumerRef.set((i, name) -> {
            monthNamesRemoved.add(name);
        });

        cache.clear();
        // Clear is async so give it time
        TestUtil.waitForIt(
                cache::size,
                0L,
                () -> "Cache size");

        assertThat(cache.size())
                .isZero();

        TestUtil.waitForIt(
                monthNamesRemoved::size,
                ALL_MONTHS_COUNT,
                () -> "monthNamesRemoved size");

        monthNamesRemoved.clear();

        // Remove specific entries that don't exist as the cache is empty
        cache.invalidateEntries((i, name) ->
                i == 5 || name.endsWith("ember"));

        // To allow for async removal (which we are asserting won't happen)
        ThreadUtil.sleepIgnoringInterrupts(20);

        assertThat(monthNamesRemoved)
                .isEmpty();

        assertThat(cache.size())
                .isZero();
    }

    @Test
    void clear() {
        cache.clear();
        assertThat(cache.size())
                .isZero();
        assertThat(cache.keySet())
                .isEmpty();
        assertThat(cache.values())
                .isEmpty();
    }

    @Test
    void testEvictExpiredElements_afterAccess() {
        CacheInfo cacheInfo = cache.getCacheInfo();
        TestUtil.dumpMapToInfo("cacheInfo", cacheInfo.getMap());

        assertThat(cacheInfo.getMap().keySet())
                .doesNotContain(EXPIRE_AFTER_ACCESS);

        cache.evictExpiredElements();

        // Nothing evicted
        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        // Now change the config and rebuild the cache
        cacheConfig = cacheConfig.copy()
                .expireAfterAccess(StroomDuration.ofMillis(300))
                .build();

        cache.rebuild();
        populateCache();

        cacheInfo = cache.getCacheInfo();
        TestUtil.dumpMapToInfo("cacheInfo", cacheInfo.getMap());

        assertThat(cacheInfo.getMap().keySet())
                .contains(EXPIRE_AFTER_ACCESS);
        assertThat(cacheInfo.getMap().get(EXPIRE_AFTER_ACCESS))
                .isEqualTo(cacheConfig.getExpireAfterAccess().toMillis() + "ms");

        cache.evictExpiredElements();

        ThreadUtil.sleepIgnoringInterrupts(100);

        // Nothing evicted yet, too soon
        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        LOGGER.info("{}", cache.keySet()
                .stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList()));

        // Touch an entry so its access time is later than the others and won't be evicted
        assertThat(cache.get(5))
                .isEqualTo("May");

        ThreadUtil.sleepIgnoringInterrupts(200);

        cache.evictExpiredElements();

        TestUtil.waitForIt(
                cache::size,
                1L,
                () -> "Cache");

        // everything except the touched entry should be evicted now
        assertThat(cache.size())
                .isEqualTo(1);

        assertThat(cache.get(5))
                .isEqualTo("May");

        // Wait for > expiry time
        ThreadUtil.sleepIgnoringInterrupts(300);

        cache.evictExpiredElements();

        TestUtil.waitForIt(
                cache::size,
                0L,
                () -> "Cache");

        // The remaining entry should now be gone
        assertThat(cache.size())
                .isZero();
    }

    @Test
    void testEvictExpiredElements_afterAccess_getOptional() {
        CacheInfo cacheInfo = cache.getCacheInfo();
        TestUtil.dumpMapToInfo("cacheInfo", cacheInfo.getMap());

        assertThat(cacheInfo.getMap().keySet())
                .doesNotContain(EXPIRE_AFTER_ACCESS);

        cache.evictExpiredElements();

        // Nothing evicted
        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        // Now change the config and rebuild the cache
        cacheConfig = cacheConfig.copy()
                .expireAfterAccess(StroomDuration.ofMillis(300))
                .build();

        cache.rebuild();
        populateCache();

        cacheInfo = cache.getCacheInfo();
        TestUtil.dumpMapToInfo("cacheInfo", cacheInfo.getMap());

        assertThat(cacheInfo.getMap().keySet())
                .contains(EXPIRE_AFTER_ACCESS);
        assertThat(cacheInfo.getMap().get(EXPIRE_AFTER_ACCESS))
                .isEqualTo(cacheConfig.getExpireAfterAccess().toMillis() + "ms");

        cache.evictExpiredElements();

        ThreadUtil.sleepIgnoringInterrupts(100);

        // Nothing evicted yet, too soon
        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        LOGGER.info("{}", cache.keySet()
                .stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList()));

        // Touch an entry so its access time is later than the others and won't be evicted
        Assertions.assertThat(cache.getIfPresent(5))
                .hasValue("May");

        ThreadUtil.sleepIgnoringInterrupts(200);

        cache.evictExpiredElements();

        TestUtil.waitForIt(
                cache::size,
                1L,
                () -> "Cache");

        // everything except the touched entry should be evicted now
        assertThat(cache.size())
                .isEqualTo(1);

        Assertions.assertThat(cache.getIfPresent(5))
                .hasValue("May");

        // Wait for > expiry time
        ThreadUtil.sleepIgnoringInterrupts(300);

        cache.evictExpiredElements();

        TestUtil.waitForIt(
                cache::size,
                0L,
                () -> "Cache");

        // The remaining entry should now be gone
        assertThat(cache.size())
                .isZero();
    }

    @Test
    void testEvictExpiredElements_afterWrite() {
        CacheInfo cacheInfo = cache.getCacheInfo();
        TestUtil.dumpMapToInfo("cacheInfo", cacheInfo.getMap());

        assertThat(cacheInfo.getMap().keySet())
                .doesNotContain(EXPIRE_AFTER_WRITE);

        cache.evictExpiredElements();

        // Nothing evicted
        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        // Now change the config and rebuild the cache
        cacheConfig = cacheConfig.copy()
                .expireAfterWrite(StroomDuration.ofMillis(300))
                .build();

        cache.rebuild();
        populateCache();

        cacheInfo = cache.getCacheInfo();
        TestUtil.dumpMapToInfo("cacheInfo", cacheInfo.getMap());

        assertThat(cacheInfo.getMap().keySet())
                .contains(EXPIRE_AFTER_WRITE);
        assertThat(cacheInfo.getMap().get(EXPIRE_AFTER_WRITE))
                .isEqualTo(cacheConfig.getExpireAfterWrite().toMillis() + "ms");

        cache.evictExpiredElements();

        // Nothing evicted yet, too soon
        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        ThreadUtil.sleepIgnoringInterrupts(100);

        // Nothing evicted yet, too soon
        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        // Add a new item that is too new to be evicted
        cache.put(999, "foo");

        ThreadUtil.sleepIgnoringInterrupts(200);

        cache.evictExpiredElements();

        TestUtil.waitForIt(
                cache::size,
                1L,
                () -> "Cache");

        // everything should be evicted now
        assertThat(cache.size())
                .isEqualTo(1);

        assertThat(cache.get(999))
                .isEqualTo("foo");

        ThreadUtil.sleepIgnoringInterrupts(300);

        cache.evictExpiredElements();

        TestUtil.waitForIt(
                cache::size,
                0L,
                () -> "Cache");

        // The remaining entry should now be gone
        assertThat(cache.size())
                .isZero();
    }

    @Test
    void testMaxSize() {
        final List<Integer> removedNumbers = new CopyOnWriteArrayList<>();
        final List<String> removedMonthNames = new CopyOnWriteArrayList<>();

        // Set up a removal listener
        removalConsumerRef.set((i, name) -> {
            removedNumbers.add(i);
            removedMonthNames.add(name);
        });

        // Cache smaller than our total items
        cacheConfig = cacheConfig.copy()
                .maximumSize(6L)
                .build();
        // Rebuild with swap
        cache.rebuild();

//        TestUtil.waitForIt(
//                removedNumbers::size,
//                ALL_MONTHS_COUNT,
//                () -> "removedNumbers size");
//
//        // Rebuild causes removal of all items
//        assertThat(removedNumbers)
//                .hasSize(ALL_MONTHS_COUNT);
//        assertThat(removedMonthNames)
//                .hasSize(ALL_MONTHS_COUNT);

        removedNumbers.clear();
        removedMonthNames.clear();

        // Only 6 can be in the cache so 1st 6 in will get removed
        populateCache();

        ThreadUtil.sleepIgnoringInterrupts(20);

        assertThat(cache.size())
                .isEqualTo(6);

        assertThat(removedNumbers)
                .hasSize(6);
        assertThat(removedMonthNames)
                .hasSize(6);
    }

    @Test
    void testGetCacheInfo() {
        CacheInfo cacheInfo = cache.getCacheInfo();
        assertThat(cacheInfo.getMap().keySet())
                .doesNotContain(MAXIMUM_SIZE);
        assertThat(cacheInfo.getMap().keySet())
                .doesNotContain(EXPIRE_AFTER_ACCESS);
        assertThat(cacheInfo.getMap().keySet())
                .doesNotContain(EXPIRE_AFTER_WRITE);

        cacheConfig = cacheConfig.copy()
                .expireAfterWrite(StroomDuration.ofSeconds(30))
                .expireAfterAccess(StroomDuration.ofMillis(10))
                .maximumSize(100L)
                .build();

        cache.rebuild();

        cacheInfo = cache.getCacheInfo();
        TestUtil.dumpMapToInfo("cacheInfo", cacheInfo.getMap());

        assertThat(cacheInfo.getMap().keySet())
                .contains(MAXIMUM_SIZE);
        assertThat(cacheInfo.getMap().get(MAXIMUM_SIZE))
                .isEqualTo("100");

        assertThat(cacheInfo.getMap().keySet())
                .contains(EXPIRE_AFTER_ACCESS);
        assertThat(cacheInfo.getMap().get(EXPIRE_AFTER_ACCESS))
                .isEqualTo("10ms");

        assertThat(cacheInfo.getMap().keySet())
                .contains(EXPIRE_AFTER_WRITE);
        assertThat(cacheInfo.getMap().get(EXPIRE_AFTER_WRITE))
                .isEqualTo("30s");
    }

    @Test
    void testConcurrentReadAndFullRebuild() throws ExecutionException, InterruptedException {
        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        final CountDownLatch writeFinishedCountDownLatch = new CountDownLatch(1);
        final CountDownLatch readStartedCountDownLatch = new CountDownLatch(1);
        final AtomicInteger callCount = new AtomicInteger();

        // invalidateEntries happens under an optimistic read lock so the BiConsumer
        // may be called more than once for the same key
        final CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
            cache.invalidateEntries((i, name) -> {
                readStartedCountDownLatch.countDown();
                LOGGER.debug("invalidateEntries called for i: {}", i);
                callCount.incrementAndGet();

                // Now wait for the cache rebuild to finish
                // We should continue invalidating the old cache instance
                InterruptibleRunnable.unchecked(writeFinishedCountDownLatch::await).run();

                return i == 5;
            });
        });

        // Wait for the cacheInvalidation to be underway else it will detect the exclusive
        // lock and wait.
        readStartedCountDownLatch.await();

        // Change the config so it does a full rebuild
        touchCacheConfig();

        cache.rebuild();
        TestUtil.waitForIt(
                cache::size,
                (long) 0,
                () -> "cache size");

        // Now fill it up a bit
        populateCache(6);

        TestUtil.waitForIt(
                cache::size,
                (long) 6,
                () -> "cache size");

        LOGGER.info("Rebuild complete");

        // Cache is now fully rebuilt so let the reader continue
        writeFinishedCountDownLatch.countDown();

        // Wait for reader to finish
        readFuture.get();

        // Reader should iterate over all of the original 12 items, not the 6 that are now in the new cache
        assertThat(callCount)
                .hasValue(ALL_MONTHS_COUNT);

        // invalidateEntries should remove one item
        assertThat(cache.size())
                .isEqualTo(6);
    }

    private void touchCacheConfig() {
        cacheConfig = cacheConfig.copy()
                .maximumSize(cacheConfig.getMaximumSize() != null
                        ? cacheConfig.getMaximumSize() + 1
                        : 100)
                .build();
    }

    @Test
    void testConcurrentReadAndRebuild_clearOnly() throws ExecutionException, InterruptedException {
        assertThat(cache.size())
                .isEqualTo(ALL_MONTHS_COUNT);

        final CountDownLatch writeFinishedCountDownLatch = new CountDownLatch(1);
        final CountDownLatch readStartedCountDownLatch = new CountDownLatch(1);
        final AtomicInteger callCount = new AtomicInteger();

        // invalidateEntries happens under an optimistic read lock so the BiConsumer
        // may be called more than once for the same key
        final CompletableFuture<Void> readFuture = CompletableFuture.runAsync(() -> {
            cache.invalidateEntries((i, name) -> {
                readStartedCountDownLatch.countDown();
                LOGGER.info("invalidateEntries called for i: {}, {}",
                        i, callCount.incrementAndGet());

                // Now wait for the cache rebuild to finish
                // We should continue invalidating the old cache instance
                InterruptibleRunnable.unchecked(writeFinishedCountDownLatch::await).run();

                return i == 5;
            });
        });

        // Wait for the cacheInvalidation to be underway else it will detect the exclusive
        // lock and wait.
        readStartedCountDownLatch.await();

        // No change to config so it will clear it only
        cache.rebuild();
        TestUtil.waitForIt(
                cache::size,
                (long) 0,
                () -> "cache size");

        // Now fill it up a bit
        populateCache(6);

        TestUtil.waitForIt(
                cache::size,
                (long) 6,
                () -> "cache size");

        LOGGER.info("Clear complete");

        // Cache is now fully rebuilt so let the reader continue
        writeFinishedCountDownLatch.countDown();

        // Wait for reader to finish
        readFuture.get();

        // Reader should now have only iterated over the 6 items post-clear
        assertThat(callCount)
                .hasValue(6);

        // invalidateEntries should remove one item
        assertThat(cache.size())
                .isEqualTo(5);
    }

    /**
     * Make sure that many threads can keep hitting the cache while another thread
     * periodically rebuilds it.
     */
    @Test
    void testRebuildConcurrency() throws ExecutionException, InterruptedException {
        final int coreCount = Runtime.getRuntime().availableProcessors();
        LOGGER.info("coreCount: {}", coreCount);
        final ExecutorService executorService = Executors.newFixedThreadPool(coreCount);
        final int iterations = 500;

        final LongAdder counter = new LongAdder();

        final Function<Integer, String> valueProvider = monthNo -> {
            counter.increment();
            return numberToMonth(monthNo);
        };

        final CountDownLatch countDownLatch = new CountDownLatch(coreCount);

        for (int i = 0; i < coreCount; i++) {
            final long seed = i;
            CompletableFuture.runAsync(() -> {
                LOGGER.info("Reader thread starting");
                final Random random = new Random(seed);
                for (int j = 0; j < iterations; j++) {

                    final int monthNo = random.nextInt(ALL_MONTHS_COUNT) + 1;

                    final String name = cache.get(monthNo, valueProvider);

                    assertThat(name)
                            .isEqualTo(numberToMonth(monthNo));
                    ThreadUtil.sleepIgnoringInterrupts(random.nextInt(5));
                }
                countDownLatch.countDown();
                LOGGER.info("Reader thread finishing");
            }, executorService);
        }

        final Random random = new Random();

        CompletableFuture.runAsync(() -> {
            LOGGER.info("Re-builder thread starting");
            while (countDownLatch.getCount() > 0) {
                LOGGER.info("Rebuilding");
                cache.rebuild();
                ThreadUtil.sleepIgnoringInterrupts(random.nextInt(50) + 100);
            }
            LOGGER.info("Re-builder thread finishing");
        }).get();

        LOGGER.info("valueProvider call count: {}", counter.sum());
    }

    private String numberToMonth(final int i) {
        if (i >= 1 && i <= ALL_MONTHS_COUNT) {
            return Month.of(i)
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        } else {
            return null;
        }
    }
}
