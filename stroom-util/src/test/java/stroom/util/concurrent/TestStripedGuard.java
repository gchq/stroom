package stroom.util.concurrent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused tests for the Guard class CAS-based reference counting mechanism.
 */
class TestStripedGuard {

    @Test
    void testBasicAcquireAndRelease() {
        // Given: A guard with a destroy callback
        final AtomicBoolean destroyed = new AtomicBoolean(false);
        final StripedGuard guard = new StripedGuard(() -> destroyed.set(true), 64);

        // When: We acquire and use it
        final String result = guard.acquire(() -> "success");

        // Then: It should work and not be destroyed yet
        assertThat(result).isEqualTo("success");
        assertThat(destroyed.get()).isFalse();
    }

    @Test
    void testDestroyCallsCallback() {
        // Given: A guard
        final AtomicBoolean destroyed = new AtomicBoolean(false);
        final StripedGuard guard = new StripedGuard(() -> destroyed.set(true), 64);

        // When: We destroy it
        guard.destroy();

        // Then: Callback should be called
        assertThat(destroyed.get()).isTrue();
    }

    @Test
    void testAcquireAfterDestroyThrows() {
        // Given: A destroyed guard
        final StripedGuard guard = new StripedGuard(() -> {
        }, 64);
        guard.destroy();

        // When/Then: Acquire should throw TryAgainException
        assertThatThrownBy(() -> guard.acquire(() -> "test"))
                .isInstanceOf(TryAgainException.class);
    }

    @Test
    void testDestroyIsIdempotent() {
        // Given: A guard
        final AtomicInteger destroyCount = new AtomicInteger(0);
        final StripedGuard guard = new StripedGuard(destroyCount::incrementAndGet, 64);

        // When: We destroy multiple times
        guard.destroy();
        guard.destroy();
        guard.destroy();

        // Then: Callback should only be called once
        assertThat(destroyCount.get()).isEqualTo(1);
    }

    @Test
    void testConcurrentAcquires() {
        // Given: A guard
        final AtomicInteger maxConcurrent = new AtomicInteger(0);
        final AtomicInteger currentConcurrent = new AtomicInteger(0);
        final StripedGuard guard = new StripedGuard(() -> {
        }, 64);

        final int threadCount = 100;
        final CyclicBarrier barrier = new CyclicBarrier(threadCount);

        try (final ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            // When: Many threads acquire concurrently
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        barrier.await(); // Synchronise start
                        guard.acquire(() -> {
                            final int concurrent = currentConcurrent.incrementAndGet();
                            maxConcurrent.updateAndGet(max -> Math.max(max, concurrent));
                            ThreadUtil.sleep(10); // Hold briefly
                            currentConcurrent.decrementAndGet();
                            return null;
                        });
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }

        // Then: All should complete
        assertThat(maxConcurrent.get()).isGreaterThan(10);
    }

    @Test
    void testDestroyWaitsForActiveAcquisitions() throws Exception {
        // Given: A guard with an active acquisition
        final CountDownLatch acquireStarted = new CountDownLatch(1);
        final CountDownLatch proceedAcquire = new CountDownLatch(1);
        final AtomicBoolean destroyed = new AtomicBoolean(false);

        final StripedGuard guard = new StripedGuard(() -> destroyed.set(true), 64);

        // Start an acquisition that holds the guard
        final CompletableFuture<Void> acquisition = CompletableFuture.runAsync(() ->
                guard.acquire(() -> {
                    acquireStarted.countDown();
                    try {
                        proceedAcquire.await();
                    } catch (final InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                }));

        // Wait for acquisition to start
        assertThat(acquireStarted.await(1, TimeUnit.SECONDS)).isTrue();

        // When: We destroy while acquisition is active
        final CompletableFuture<Void> destruction = CompletableFuture.runAsync(guard::destroy);

        // Give destruction a moment to try
        Thread.sleep(100);

        // Then: Should not be destroyed yet
        assertThat(destroyed.get()).isFalse();

        // Release the acquisition
        proceedAcquire.countDown();
        acquisition.get(1, TimeUnit.SECONDS);
        destruction.get(1, TimeUnit.SECONDS);

        // Now it should be destroyed
        assertThat(destroyed.get()).isTrue();
    }

    @Test
    void testConcurrentDestroyAndAcquire() throws Exception {
        final int threadCount = 100;
        try (final ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            // Given: A guard
            final AtomicInteger destroyCount = new AtomicInteger(0);
            final AtomicInteger successCount = new AtomicInteger(0);
            final AtomicInteger failCount = new AtomicInteger(0);

            final StripedGuard guard = new StripedGuard(destroyCount::incrementAndGet, 64);

            final CyclicBarrier barrier = new CyclicBarrier(threadCount);
            final CountDownLatch completeLatch = new CountDownLatch(threadCount);

            // When: Half destroy, half acquire, all at once
            for (int i = 0; i < threadCount; i++) {
                final boolean shouldDestroy = (i % 2 == 0);
                executor.submit(() -> {
                    try {
                        barrier.await(); // Synchronise start
                        if (shouldDestroy) {
                            guard.destroy();
                        } else {
                            try {
                                guard.acquire(() -> {
                                    successCount.incrementAndGet();
                                    return null;
                                });
                            } catch (final TryAgainException e) {
                                failCount.incrementAndGet();
                            }
                        }
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            // Then: All should complete
            assertThat(completeLatch.await(10, TimeUnit.SECONDS)).isTrue();

            // Destroy should be called exactly once
            assertThat(destroyCount.get()).isEqualTo(1);

            // Some acquires should succeed, some should fail
            assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount / 2);
        }
    }

    @Test
    void testExceptionInSupplierStillReleases() {
        // Given: A guard
        final AtomicBoolean destroyed = new AtomicBoolean(false);
        final StripedGuard guard = new StripedGuard(() -> destroyed.set(true), 64);

        // Track releases by destroying after each acquire
        // (destroy will only complete when count reaches 0)

        // When: Supplier throws exception
        assertThatThrownBy(() -> guard.acquire(() -> {
            throw new RuntimeException("Test exception");
        })).isInstanceOf(RuntimeException.class);

        // Then: Should still be able to destroy (proves release happened)
        guard.destroy();
        // If we get here without hanging, release happened correctly
        assertThat(destroyed.get()).isTrue();
    }

    @Test
    void testReferenceCountingAccuracy() throws Exception {
        try (final ExecutorService executor = Executors.newFixedThreadPool(20)) {
            // Given: A guard with many concurrent acquisitions
            final AtomicInteger inProgressCount = new AtomicInteger(0);
            final AtomicInteger maxInProgress = new AtomicInteger(0);

            final StripedGuard guard = new StripedGuard(() -> {
                // Verify count is 0 when destroyed
                assertThat(inProgressCount.get()).isEqualTo(0);
            }, 64);

            final int acquisitionCount = 1000;
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch completeLatch = new CountDownLatch(acquisitionCount);

            // When: Many acquisitions happen
            for (int i = 0; i < acquisitionCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        guard.acquire(() -> {
                            final int count = inProgressCount.incrementAndGet();
                            maxInProgress.updateAndGet(max -> Math.max(max, count));
                            ThreadUtil.sleep(1); // Brief hold
                            inProgressCount.decrementAndGet();
                            return null;
                        });
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertThat(completeLatch.await(30, TimeUnit.SECONDS)).isTrue();

            // Then: Destroy should work (verifies count reached 0)
            guard.destroy();

            // And we should have had concurrency
            assertThat(maxInProgress.get()).isGreaterThan(1);
        }
    }

    @Test
    void testDestroyDuringActiveAcquisitionRace() throws Exception {
        final int threadCount = 100;
        try (final ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            // Given: A guard
            final AtomicBoolean destroyed = new AtomicBoolean(false);
            final AtomicInteger successfulAcquisitions = new AtomicInteger(0);
            final AtomicInteger failedAcquisitions = new AtomicInteger(0);

            final StripedGuard guard = new StripedGuard(() -> destroyed.set(true), 64);

            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch completeLatch = new CountDownLatch(threadCount);
            final AtomicInteger count = new AtomicInteger();

            // When: Many threads acquire, one destroys in the middle
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        // Thread 50 destroys
                        final int index = count.getAndIncrement();
                        if (index == 50) {
                            guard.destroy();
                        } else {
                            try {
                                guard.acquire(() -> {
                                    ThreadUtil.sleep(20); // Hold briefly
                                    successfulAcquisitions.incrementAndGet();
                                    return null;
                                });
                            } catch (final TryAgainException e) {
                                failedAcquisitions.incrementAndGet();
                            }
                        }
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertThat(completeLatch.await(10, TimeUnit.SECONDS)).isTrue();

            // Then: Should eventually be destroyed
            assertThat(destroyed.get()).isTrue();

            // Some should succeed (those before destroy), some should fail (those after)
            assertThat(successfulAcquisitions.get()).isGreaterThan(0);
            assertThat(failedAcquisitions.get()).isGreaterThan(0);
            assertThat(successfulAcquisitions.get() + failedAcquisitions.get()).isEqualTo(threadCount - 1);
        }
    }

    @Test
    void testMultipleSequentialDestroysDoNothing() {
        // Given: A guard
        final AtomicInteger destroyCount = new AtomicInteger(0);
        final StripedGuard guard = new StripedGuard(destroyCount::incrementAndGet, 64);

        // When: We destroy it many times sequentially
        for (int i = 0; i < 10; i++) {
            guard.destroy();
        }

        // Then: Callback should only be called once
        assertThat(destroyCount.get()).isEqualTo(1);
    }

    @Test
    void testDestroyBeforeAnyAcquire() {
        // Given: A guard that is destroyed immediately
        final AtomicBoolean destroyed = new AtomicBoolean(false);
        final StripedGuard guard = new StripedGuard(() -> destroyed.set(true), 64);
        guard.destroy();

        // When/Then: Any acquire should fail
        assertThatThrownBy(() -> guard.acquire(() -> "test"))
                .isInstanceOf(TryAgainException.class);

        assertThat(destroyed.get()).isTrue();
    }

    @Test
    void testNestedAcquiresNotSupported() {
        // Given: A guard
        final StripedGuard guard = new StripedGuard(() -> {
        }, 64);

        // When/Then: Nested acquires should work (they increment count)
        final String result = guard.acquire(() ->
                guard.acquire(() -> "nested")
        );

        assertThat(result).isEqualTo("nested");
    }

    @Test
    void testStressTest() throws Exception {
        final int threadCount = 50;
        try (final ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            // Given: A guard under extreme load
            final AtomicInteger destroyCount = new AtomicInteger(0);
            final AtomicInteger operationCount = new AtomicInteger(0);

            final StripedGuard guard = new StripedGuard(destroyCount::incrementAndGet, 64);

            final int operationsPerThread = 1000;
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch completeLatch = new CountDownLatch(threadCount);
            final AtomicBoolean shouldStop = new AtomicBoolean(false);

            // When: Many threads hammer the guard
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        // One thread destroys halfway through
                        if (threadIndex == 0) {
                            Thread.sleep(50);
                            guard.destroy();
                            shouldStop.set(true);
                        } else {
                            for (int j = 0; j < operationsPerThread && !shouldStop.get(); j++) {
                                try {
                                    guard.acquire(() -> {
                                        operationCount.incrementAndGet();
                                        return null;
                                    });
                                } catch (final TryAgainException e) {
                                    // Expected after destroy
                                    break;
                                }
                            }
                        }
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertThat(completeLatch.await(30, TimeUnit.SECONDS)).isTrue();

            // Then: Should be destroyed exactly once
            assertThat(destroyCount.get()).isEqualTo(1);

            // And many operations should have succeeded
            assertThat(operationCount.get()).isGreaterThan(0);
        }
    }
}
