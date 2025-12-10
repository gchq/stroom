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

package stroom.util.concurrent;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestLazyValue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLazyValue.class);

    public static final String VALUE = "foo";
    public static final int SLEEP_MILLIS = 50;

    @Test
    void testWithLocks() throws InterruptedException {
        final AtomicInteger callCount = new AtomicInteger();
        final Supplier<String> valueSupplier = () -> {
            // Slow it down to make it more likely multiple threads would need
            // to call the supplier
            ThreadUtil.sleepIgnoringInterrupts(SLEEP_MILLIS);
            callCount.incrementAndGet();
            return VALUE;
        };

        final LazyValue<String> lazyVal = LazyValue.initialisedBy(valueSupplier);

        assertThat(lazyVal.isInitialised())
                .isFalse();

        final int cnt = 10;
        final ExecutorService executorService = Executors.newFixedThreadPool(cnt);

        final CountDownLatch readyToStartLatch = new CountDownLatch(cnt);
        final CountDownLatch finishedLatch = new CountDownLatch(cnt);

        for (int i = 0; i < cnt; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    readyToStartLatch.countDown();
                    readyToStartLatch.await();

                    final String val = lazyVal.getValueWithLocks();
                    assertThat(val)
                            .isEqualTo(VALUE);
                    finishedLatch.countDown();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, executorService);
        }

        finishedLatch.await();

        LOGGER.debug("callCount: {}", callCount);

        // Locks used, so only called once
        assertThat(callCount)
                .hasValue(1);
    }

    @Test
    void testWithoutLocks() throws InterruptedException {
        final AtomicInteger callCount = new AtomicInteger();
        final Supplier<String> valueSupplier = () -> {
            // Slow it down to make it more likely multiple threads would need
            // to call the supplier
            ThreadUtil.sleepIgnoringInterrupts(SLEEP_MILLIS);
            callCount.incrementAndGet();
            return VALUE;
        };

        final LazyValue<String> lazyVal = LazyValue.initialisedBy(valueSupplier);

        assertThat(lazyVal.isInitialised())
                .isFalse();

        final int cnt = 10;
        final ExecutorService executorService = Executors.newFixedThreadPool(cnt);

        final CountDownLatch readyToStartLatch = new CountDownLatch(cnt);
        final CountDownLatch finishedLatch = new CountDownLatch(cnt);

        for (int i = 0; i < cnt; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    readyToStartLatch.countDown();
                    readyToStartLatch.await();

                    final String val = lazyVal.getValueWithoutLocks();
                    assertThat(val)
                            .isEqualTo(VALUE);
                    finishedLatch.countDown();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, executorService);
        }

        finishedLatch.await();

        LOGGER.debug("callCount: {}", callCount);

        // Locks used, so only called once
        assertThat(callCount)
                .hasValueGreaterThan(1);
    }

    @Test
    void testWithNullValue() throws InterruptedException {
        final AtomicInteger callCount = new AtomicInteger();
        final Supplier<String> valueSupplier = () -> {
            // Slow it down to make it more likely multiple threads would need
            // to call the supplier
            ThreadUtil.sleepIgnoringInterrupts(SLEEP_MILLIS);
            callCount.incrementAndGet();
            return null;
        };

        final LazyValue<String> lazyVal = LazyValue.initialisedBy(valueSupplier);

        assertThat(lazyVal.isInitialised())
                .isFalse();

        final int cnt = 10;
        final ExecutorService executorService = Executors.newFixedThreadPool(cnt);

        final CountDownLatch readyToStartLatch = new CountDownLatch(cnt);
        final CountDownLatch finishedLatch = new CountDownLatch(cnt);

        for (int i = 0; i < cnt; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    readyToStartLatch.countDown();
                    readyToStartLatch.await();

                    final String val = lazyVal.getValueWithLocks();
                    assertThat(val)
                            .isNull();
                    finishedLatch.countDown();
                } catch (final InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, executorService);
        }

        finishedLatch.await();

        LOGGER.debug("callCount: {}", callCount);

        // Locks used, so only called once
        assertThat(callCount)
                .hasValue(1);
    }
}
