/*
 * Copyright 2024 Crown Copyright
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

import stroom.test.common.AssertionUtil;
import stroom.util.exception.ThrowingRunnable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.assertThat;

class TestWorkQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestWorkQueue.class);

    @Test
    void singleThreadCapacity1() {
        try (final ExecutorService executor = Executors.newFixedThreadPool(20)) {
            final int capacity = 1;
            final int queueThreadCount = 1;
            final int itemCount = 1_000;
            final WorkQueue workQueue = new WorkQueue(executor, queueThreadCount, capacity);
            final CountDownLatch completionLatch = new CountDownLatch(itemCount);
            final LongAdder cnt = new LongAdder();

            final HighWaterMarkTracker queueSizeTracker = new HighWaterMarkTracker();
            final HighWaterMarkTracker concurrentThreadTracker = new HighWaterMarkTracker();
            for (int i = 0; i < itemCount; i++) {
                workQueue.exec(() -> {
                    concurrentThreadTracker.doWithHighWaterMarkTracking(() -> {
                        cnt.increment();
                        completionLatch.countDown();
                        queueSizeTracker.setCurrentCount(Math.toIntExact(workQueue.getTaskCount()));
                    });
                });
            }

            AssertionUtil.assertAwait(completionLatch);

            LOGGER.info("queueSizeTracker: {}", queueSizeTracker.getHighWaterMark());
            LOGGER.info("concurrentThreadTracker: {}", concurrentThreadTracker.getHighWaterMark());

            workQueue.join();

            // Work queue has thread count of 1
            assertThat(concurrentThreadTracker.getHighWaterMark())
                    .isEqualTo(queueThreadCount);
            assertThat(queueSizeTracker.getHighWaterMark())
                    .isLessThanOrEqualTo(capacity);
            assertThat(cnt.longValue())
                    .isEqualTo(itemCount);
        }
    }

    @Test
    void singleThreadedCapacity100() {
        try (final ExecutorService executor = Executors.newFixedThreadPool(20)) {
            final int capacity = 100;
            final int queueThreadCount = 1;
            final WorkQueue workQueue = new WorkQueue(executor, queueThreadCount, capacity);
            final int itemCount = 1_000;
            final CountDownLatch completionLatch = new CountDownLatch(itemCount);
            final LongAdder cnt = new LongAdder();

            final HighWaterMarkTracker queueSizeTracker = new HighWaterMarkTracker();
            final HighWaterMarkTracker concurrentThreadTracker = new HighWaterMarkTracker();
            for (int i = 0; i < itemCount; i++) {
                workQueue.exec(() -> {
                    concurrentThreadTracker.doWithHighWaterMarkTracking(() -> {
                        cnt.increment();
                        completionLatch.countDown();
                        queueSizeTracker.setCurrentCount(Math.toIntExact(workQueue.getTaskCount()));
                    });
                });
            }

            AssertionUtil.assertAwait(completionLatch);

            LOGGER.info("queueSizeTracker: {}", queueSizeTracker.getHighWaterMark());
            LOGGER.info("concurrentThreadTracker: {}", concurrentThreadTracker.getHighWaterMark());

            workQueue.join();

            // Work queue has thread count of 1
            assertThat(concurrentThreadTracker.getHighWaterMark())
                    .isEqualTo(queueThreadCount);
            assertThat(queueSizeTracker.getHighWaterMark())
                    .isLessThanOrEqualTo(capacity);
            assertThat(cnt.longValue())
                    .isEqualTo(itemCount);
        }
    }

    @Test
    void multiThreadedCapacity100() {
        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        LOGGER.info("availableProcessors: {}", availableProcessors);
        // Have more threads than we have workers, so we have threads ready to go
        try (final ExecutorService executor = Executors.newFixedThreadPool(availableProcessors * 2)) {
            final int capacity = 100;
            final WorkQueue workQueue = new WorkQueue(executor, availableProcessors, capacity);
            final int itemCount = 1_000;
            final CountDownLatch startLatch = new CountDownLatch(availableProcessors);
            final CountDownLatch completionLatch = new CountDownLatch(itemCount);
            final LongAdder cnt = new LongAdder();


            final HighWaterMarkTracker queueSizeTracker = new HighWaterMarkTracker();
            final HighWaterMarkTracker concurrentThreadTracker = new HighWaterMarkTracker();
            for (int i = 0; i < itemCount; i++) {
                workQueue.exec(ThrowingRunnable.unchecked(() -> {
                    concurrentThreadTracker.doWithHighWaterMarkTracking(() -> {
                        startLatch.countDown();
                        // Wait for the expected number of concurrent workers to be executing together
                        // before we let them all go
                        AssertionUtil.assertAwait(startLatch);
                        queueSizeTracker.setCurrentCount(Math.toIntExact(workQueue.getTaskCount()));
                        cnt.increment();
                        // Tiny sleep to give it more of a chance of running concurrently with other threads
                        ThreadUtil.sleepIgnoringInterrupts(2);
                        completionLatch.countDown();
                    });
                }));
            }

            LOGGER.info("queueSizeTracker: {}", queueSizeTracker.getHighWaterMark());
            LOGGER.info("concurrentThreadTracker: {}", concurrentThreadTracker.getHighWaterMark());

            AssertionUtil.assertAwait(completionLatch);

            workQueue.join();

            // Work queue has thread count of 10 so should get 10 items being exec'd at once.
            // If this fails in CI then might need to change the assertion to > 1 && < availableProcessors
            assertThat(concurrentThreadTracker.getHighWaterMark())
                    .isEqualTo(availableProcessors);
            assertThat(queueSizeTracker.getHighWaterMark())
                    .isLessThanOrEqualTo(capacity);
            assertThat(cnt.longValue())
                    .isEqualTo(itemCount);
        }
    }

    @Test
    void execAfterJoin() {
        try (final ExecutorService executor = Executors.newFixedThreadPool(20)) {
            final WorkQueue workQueue = new WorkQueue(executor, 2, 10);
            final int itemCount = 10;
            final CountDownLatch completionLatch = new CountDownLatch(itemCount);

            for (int i = 0; i < itemCount; i++) {
                workQueue.exec(completionLatch::countDown);
            }

            AssertionUtil.assertAwait(completionLatch);
            workQueue.join();

            // Attempting to exec after join should throw an IllegalStateException
            final Runnable noOp = () -> {
            };
            Assertions.assertThatThrownBy(() -> workQueue.exec(noOp))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void joinAfterNoTasks() {
        try (final ExecutorService executor = Executors.newFixedThreadPool(20)) {
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
    }

    @Test
    void taskCount() {
        final int threadCount = 2;
        try (final ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2)) {
            final WorkQueue workQueue = new WorkQueue(executor, threadCount, 10);
            final int itemCount = 10;
            final CountDownLatch takenLatch = new CountDownLatch(threadCount);
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch completionLatch = new CountDownLatch(itemCount);
            final AtomicInteger execCount = new AtomicInteger();

            for (int i = 0; i < itemCount; i++) {
                workQueue.exec(() -> {
                    takenLatch.countDown();
                    AssertionUtil.assertAwait(startLatch);

                    execCount.incrementAndGet();
                    completionLatch.countDown();
                });
            }

            // Two worker threads will have taken two items of the queue
            AssertionUtil.assertAwait(takenLatch);
            Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(itemCount - threadCount);

            startLatch.countDown();
            AssertionUtil.assertAwait(completionLatch);
            workQueue.join();

            Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(0);
            Assertions.assertThat(execCount).hasValue(itemCount);
        }
    }

    @Test
    void taskCount2() {
        final int threadCount = 2;
        try (final ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2)) {
            final WorkQueue workQueue = new WorkQueue(executor, threadCount, 10);
            final int itemCount = 10;
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch takenLatch = new CountDownLatch(threadCount);
            final CountDownLatch completionLatch = new CountDownLatch(itemCount);
            final AtomicInteger execCount = new AtomicInteger();

            for (int i = 0; i < itemCount; i++) {
                workQueue.exec(() -> {
                    takenLatch.countDown();
                    AssertionUtil.assertAwait(startLatch);

                    execCount.incrementAndGet();
                    completionLatch.countDown();
                });
            }

            // Two worker threads will have taken two items of the queue
            AssertionUtil.assertAwait(takenLatch);
            Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(itemCount - threadCount);

            final Executor delayedExecutor = CompletableFuture.delayedExecutor(500,
                    TimeUnit.MILLISECONDS,
                    Executors.newSingleThreadScheduledExecutor());

            // Make the tasks start after we call join
            CompletableFuture.runAsync(startLatch::countDown, delayedExecutor);

            workQueue.join();

            AssertionUtil.assertAwait(completionLatch);

            Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(0);
            Assertions.assertThat(execCount).hasValue(itemCount);
        }
    }


    @Test
    void runtimeExceptionInTask() {
        try (final ExecutorService executor = Executors.newFixedThreadPool(20)) {
            final WorkQueue workQueue = new WorkQueue(executor, 1, 10);
            final CountDownLatch startedLatch = new CountDownLatch(1);
            final CountDownLatch completionLatch = new CountDownLatch(3);
            final AtomicInteger execCount = new AtomicInteger();

            // Add one task that will throw a RuntimeException,
            // make sure it doesn't stop the queue
            workQueue.exec(() -> {
                startedLatch.countDown();
                completionLatch.countDown();
                // This should be logged and swallowed
                throw new RuntimeException("Test exception");
            });
            // Add two more tasks that should still execute
            workQueue.exec(() -> {
                execCount.incrementAndGet();
                completionLatch.countDown();
            });
            workQueue.exec(() -> {
                execCount.incrementAndGet();
                completionLatch.countDown();
            });

            AssertionUtil.assertAwait(startedLatch);
            AssertionUtil.assertAwait(completionLatch);
            workQueue.join();

            // Both remaining tasks should have executed despite the exception
            Assertions.assertThat(execCount)
                    .hasValue(2);
            Assertions.assertThat(workQueue.getTaskCount())
                    .isEqualTo(0);
        }
    }

    /**
     * A runnable that throws {@link UncheckedInterruptedException} signals that the worker thread is being
     * terminated, e.g. at shutdown. The worker must stop consuming rather than churn through the rest of the
     * queue, and must not treat the interruption as an error.
     */
    @Test
    void interruptedRunnableStopsWorker() {
        final WorkQueue workQueue;
        try (final ExecutorService executor = Executors.newFixedThreadPool(20)) {
            workQueue = new WorkQueue(executor, 1, 10);
            final CountDownLatch startedLatch = new CountDownLatch(1);
            final AtomicInteger execCount = new AtomicInteger();

            // Ade one task that will immediately throw an UncheckedInterruptedException
            workQueue.exec(() -> {
                startedLatch.countDown();
                throw new UncheckedInterruptedException(new InterruptedException("Interrupted at shutdown"));
            });
            // Ade two more that will be queued
            workQueue.exec(execCount::incrementAndGet);
            workQueue.exec(execCount::incrementAndGet);

            AssertionUtil.assertAwait(startedLatch);
            workQueue.join();

            Assertions.assertThat(execCount).hasValue(0);
            // Two queued tasks plus the poison pill
            Assertions.assertThat(workQueue.getTaskCount()).isEqualTo(3);
        }
    }

    /**
     * A worker interrupted while waiting for work completes its future exceptionally. Callers of join()
     * expect an {@link UncheckedInterruptedException} in that case, not the CompletionException that
     * {@link CompletableFuture#join()} wraps it in, so they can treat an interrupted work queue as expected
     * at shutdown rather than as an error.
     */
    @Test
    void joinThrowsUncheckedInterruptedWhenWorkerInterrupted() {
        try (final ExecutorService executorService = Executors.newFixedThreadPool(1)) {
            // The worker's future is completed before its task returns, so this latch tells us the interrupt has
            // taken effect before we call join(). Without it the POISON_PILL that join() adds races the interrupt:
            // if the queue is signalled at the same moment the worker is interrupted, the worker's wait ends
            // normally with the interrupt flag merely re-asserted (see AQS REINTERRUPT), so it consumes the pill,
            // completes normally and join() has nothing to throw.
            final CountDownLatch workerFinishedLatch = new CountDownLatch(1);
            final Executor executor = command -> executorService.execute(() -> {
                try {
                    command.run();
                } finally {
                    workerFinishedLatch.countDown();
                }
            });

            final WorkQueue workQueue = new WorkQueue(executor, 1, 10);
            final CountDownLatch startedLatch = new CountDownLatch(1);
            final AtomicReference<Thread> workerThread = new AtomicReference<>();

            workQueue.exec(() -> {
                workerThread.set(Thread.currentThread());
                startedLatch.countDown();
            });
            AssertionUtil.assertAwait(startedLatch);

            // Wait for the worker to block waiting for more work, then interrupt it.
            final Instant timeout = Instant.now().plusSeconds(10);
            while (workerThread.get().getState() != Thread.State.WAITING) {
                if (Instant.now().isAfter(timeout)) {
                    throw new AssertionError("Timed out waiting for the worker to block waiting for work");
                }
                ThreadUtil.sleepIgnoringInterrupts(1);
            }
            workerThread.get().interrupt();
            AssertionUtil.assertAwait(workerFinishedLatch);

            Assertions.assertThatThrownBy(workQueue::join)
                    .isInstanceOf(UncheckedInterruptedException.class);
        }
    }
}
