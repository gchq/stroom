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

import stroom.util.exception.ThrowingRunnable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.assertThat;

class TestWorkQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestWorkQueue.class);

    @Test
    void singleThreadCapacity1() throws InterruptedException {
        final Executor executor = Executors.newFixedThreadPool(20);
        final WorkQueue workQueue = new WorkQueue(executor, 1, 1);
        final int itemCount = 1_000;
        final CountDownLatch completionLatch = new CountDownLatch(itemCount);
        final LongAdder cnt = new LongAdder();

        final HighWaterMarkTracker highWaterMarkTracker = new HighWaterMarkTracker();
        for (int i = 0; i < itemCount; i++) {
            workQueue.exec(() -> {
                highWaterMarkTracker.increment();
                cnt.increment();
                completionLatch.countDown();
                highWaterMarkTracker.decrement();
            });
        }

        completionLatch.await();

        // Work queue has thread count of 1
        assertThat(highWaterMarkTracker.getHighWaterMark())
                .isEqualTo(1);
        assertThat(cnt.longValue())
                .isEqualTo(itemCount);
    }

    @Test
    void singleThreadedCapacity100() throws InterruptedException {
        final Executor executor = Executors.newFixedThreadPool(20);
        final WorkQueue workQueue = new WorkQueue(executor, 1, 100);
        final int itemCount = 1_000;
        final CountDownLatch completionLatch = new CountDownLatch(itemCount);
        final LongAdder cnt = new LongAdder();

        final HighWaterMarkTracker highWaterMarkTracker = new HighWaterMarkTracker();
        for (int i = 0; i < itemCount; i++) {
            workQueue.exec(() -> {
                highWaterMarkTracker.increment();
                cnt.increment();
                completionLatch.countDown();
                highWaterMarkTracker.decrement();
            });
        }

        completionLatch.await();

        // Work queue has thread count of 1
        assertThat(highWaterMarkTracker.getHighWaterMark())
                .isEqualTo(1);
        assertThat(cnt.longValue())
                .isEqualTo(itemCount);
    }

    @Test
    void multiThreadedCapacity100() throws InterruptedException {
        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        LOGGER.info("availableProcessors: {}", availableProcessors);
        // Have more threads than we have workers, so we have threads ready to go
        final Executor executor = Executors.newFixedThreadPool(availableProcessors * 2);
        final WorkQueue workQueue = new WorkQueue(executor, availableProcessors, 100);
        final int itemCount = 1_000;
        final CountDownLatch startLatch = new CountDownLatch(availableProcessors);
        final CountDownLatch completionLatch = new CountDownLatch(itemCount);
        final LongAdder cnt = new LongAdder();

        final HighWaterMarkTracker highWaterMarkTracker = new HighWaterMarkTracker();
        for (int i = 0; i < itemCount; i++) {
            workQueue.exec(ThrowingRunnable.unchecked(() -> {
                startLatch.countDown();
                // Wait for the expected number of concurrent workers to be executing together
                // before we let them all go
                final boolean success = startLatch.await(10, TimeUnit.SECONDS);
                if (!success) {
                    throw new RuntimeException("Didn't count down to zero");
                }
                highWaterMarkTracker.increment();
                cnt.increment();
                // Tiny sleep to give it more of a chance of running concurrently with other threads
                ThreadUtil.sleepIgnoringInterrupts(5);
                completionLatch.countDown();
                highWaterMarkTracker.decrement();
            }));
        }

        completionLatch.await();

        // Work queue has thread count of 10 so should get 10 items being exec'd at once.
        // If this fails in CI then might need to change the assertion to > 1 && < availableProcessors
        assertThat(highWaterMarkTracker.getHighWaterMark())
                .isEqualTo(availableProcessors);
        assertThat(cnt.longValue())
                .isEqualTo(itemCount);
    }

    @Test
    void execAfterJoin() throws InterruptedException {
        final Executor executor = Executors.newFixedThreadPool(2);
        final WorkQueue workQueue = new WorkQueue(executor, 2, 10);
        final int itemCount = 10;
        final CountDownLatch completionLatch = new CountDownLatch(itemCount);

        for (int i = 0; i < itemCount; i++) {
            workQueue.exec(completionLatch::countDown);
        }

        completionLatch.await();
        workQueue.join();

        // Attempting to exec after join should throw an IllegalStateException
        final Runnable noOp = () -> {
        };
        Assertions.assertThatThrownBy(() -> workQueue.exec(noOp))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void joinAfterNoTasks() {
        final Executor executor = Executors.newFixedThreadPool(2);
        final WorkQueue workQueue = new WorkQueue(executor, 2, 10);

        Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(0);

        workQueue.join();

        Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(0);

        // Attempting to exec after join should throw an IllegalStateException
        final Runnable noOp = () -> {
        };
        Assertions.assertThatThrownBy(() -> workQueue.exec(noOp))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void taskCount() throws InterruptedException {
        final int threadCount = 2;
        final Executor executor = Executors.newFixedThreadPool(threadCount);
        final WorkQueue workQueue = new WorkQueue(executor, threadCount, 10);
        final int itemCount = 10;
        final CountDownLatch takenLatch = new CountDownLatch(threadCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completionLatch = new CountDownLatch(itemCount);
        final AtomicInteger execCount = new AtomicInteger();

        for (int i = 0; i < itemCount; i++) {
            workQueue.exec(() -> {
                takenLatch.countDown();
                try {
                    startLatch.await();
                } catch (final InterruptedException e) {
                    throw new UncheckedInterruptedException(e);
                }

                execCount.incrementAndGet();
                completionLatch.countDown();
            });
        }

        // Two worker threads will have taken two items of the queue
        takenLatch.await();
        Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(itemCount - threadCount);

        startLatch.countDown();
        completionLatch.await();
        workQueue.join();

        Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(0);
        Assertions.assertThat(execCount).hasValue(itemCount);
    }

    @Test
    void taskCount2() throws InterruptedException {
        final int threadCount = 2;
        final Executor executor = Executors.newFixedThreadPool(threadCount);
        final WorkQueue workQueue = new WorkQueue(executor, threadCount, 10);
        final int itemCount = 10;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch takenLatch = new CountDownLatch(threadCount);
        final CountDownLatch completionLatch = new CountDownLatch(itemCount);
        final AtomicInteger execCount = new AtomicInteger();

        for (int i = 0; i < itemCount; i++) {
            workQueue.exec(() -> {
                takenLatch.countDown();
                try {
                    startLatch.await();
                } catch (final InterruptedException e) {
                    throw new UncheckedInterruptedException(e);
                }

                execCount.incrementAndGet();
                completionLatch.countDown();
            });
        }

        // Two worker threads will have taken two items of the queue
        takenLatch.await();
        Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(itemCount - threadCount);

        final Executor delayedExecutor = CompletableFuture.delayedExecutor(500,
                TimeUnit.MILLISECONDS,
                Executors.newSingleThreadScheduledExecutor());

        // Make the tasks start after we call join
        CompletableFuture.runAsync(startLatch::countDown, delayedExecutor);

        workQueue.join();

        completionLatch.await();

        Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(0);
        Assertions.assertThat(execCount).hasValue(itemCount);
    }

    /**
     * A runnable that throws {@link UncheckedInterruptedException} signals that the worker thread is being
     * terminated, e.g. at shutdown. The worker must stop consuming rather than churn through the rest of the
     * queue, and must not treat the interruption as an error.
     */
    @Test
    void interruptedRunnableStopsWorker() throws InterruptedException {
        final Executor executor = Executors.newFixedThreadPool(1);
        final WorkQueue workQueue = new WorkQueue(executor, 1, 10);
        final CountDownLatch startedLatch = new CountDownLatch(1);
        final AtomicInteger execCount = new AtomicInteger();

        workQueue.exec(() -> {
            startedLatch.countDown();
            throw new UncheckedInterruptedException(new InterruptedException("Interrupted at shutdown"));
        });
        workQueue.exec(execCount::incrementAndGet);
        workQueue.exec(execCount::incrementAndGet);

        startedLatch.await();
        workQueue.join();

        Assertions.assertThat(execCount).hasValue(0);
        Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(2);
    }

    /**
     * A worker interrupted while waiting for work completes its future exceptionally. Callers of join()
     * expect an {@link UncheckedInterruptedException} in that case, not the CompletionException that
     * {@link CompletableFuture#join()} wraps it in, so they can treat an interrupted work queue as expected
     * at shutdown rather than as an error.
     */
    @Test
    void joinThrowsUncheckedInterruptedWhenWorkerInterrupted() throws InterruptedException {
        final Executor executor = Executors.newFixedThreadPool(1);
        final WorkQueue workQueue = new WorkQueue(executor, 1, 10);
        final CountDownLatch startedLatch = new CountDownLatch(1);
        final AtomicReference<Thread> workerThread = new AtomicReference<>();

        workQueue.exec(() -> {
            workerThread.set(Thread.currentThread());
            startedLatch.countDown();
        });
        startedLatch.await();

        // Wait for the worker to block waiting for more work, then interrupt it.
        final Instant timeout = Instant.now().plusSeconds(10);
        while (workerThread.get().getState() != Thread.State.WAITING) {
            if (Instant.now().isAfter(timeout)) {
                throw new AssertionError("Timed out waiting for the worker to block waiting for work");
            }
            TimeUnit.MILLISECONDS.sleep(1);
        }
        workerThread.get().interrupt();

        Assertions.assertThatThrownBy(workQueue::join)
                .isInstanceOf(UncheckedInterruptedException.class);
    }
}
