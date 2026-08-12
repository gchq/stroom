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
import stroom.util.logging.LogUtil;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

/**
 * A work queue for asynchronous processing of tasks. It uses a fixed number of threads to process tasks from a queue.
 * Tasks are added to the queue using the {@link #exec(Runnable)} method. The queue is bounded, but the number of
 * threads is fixed. The queue will block if it is full.
 */
public class WorkQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(WorkQueue.class);

    private final int threadCount;
    private final int capacity;
    private final ArrayBlockingQueue<Runnable> queue;
    private final CompletableFuture<Void>[] futures;
    private final StampedLock stampedLock = new StampedLock();
    private boolean shuttingDown = false;

    /**
     * @param executor    The executor that will provide the threads for the work queue.
     * @param threadCount The number of threads to use for processing the work queue.
     * @param capacity    Maximum number of items on the queue.
     */
    @SuppressWarnings("unchecked")
    public WorkQueue(final Executor executor,
                     final int threadCount,
                     final int capacity) {
        Objects.requireNonNull(executor);
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount must be > 0");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.threadCount = threadCount;
        queue = new ArrayBlockingQueue<>(capacity);
        futures = new CompletableFuture[threadCount];
        for (int i = 0; i < threadCount; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    Runnable runnable = queue.take();
                    // Use instance equality
                    while (runnable != PoisonPill.INSTANCE) {
                        try {
                            runnable.run();
                        } catch (final UncheckedInterruptedException e) {
                            // This thread is being terminated, e.g. at shutdown, which is expected, so don't
                            // log it as an error. Restore the interrupt flag in case the exception was
                            // created without doing so.
                            LOGGER.trace("Runnable interrupted, dropping out");
                            Thread.currentThread().interrupt();
                            break;
                        } catch (final RuntimeException e) {
                            // Ideally, the runnable should handle its own exceptions, but just in case
                            // we will swallow the exception so that the thread doesn't die.
                            LOGGER.error("Error while executing work queue runnable " +
                                         "(Note: the class that created this runnable should be " +
                                         "handling this itself) - {}", LogUtil.exceptionMessage(e), e);
                        }
                        if (Thread.currentThread().isInterrupted()) {
                            LOGGER.trace("Thread interrupted, dropping out");
                            break;
                        }
                        runnable = queue.take();
                    }
                    if (runnable == PoisonPill.INSTANCE) {
                        LOGGER.trace("Poison pill found, dropping out");
                    }
                } catch (final InterruptedException e) {
                    LOGGER.trace("Take loop interrupted");
                    Thread.currentThread().interrupt();
                    throw UncheckedInterruptedException.create(e);
                }
            }, executor);
        }
    }

    /**
     * Add runnable to the work queue for asynchronous execution.
     *
     * @param runnable The {@link Runnable} to execute. It should handle any exceptions and not re-throw them
     *                 to ensure the queue thread can continue processing other tasks.
     * @throws UncheckedInterruptedException if the thread is interrupted
     * @throws IllegalStateException         if the work queue is shutting down or has been shutdown
     */
    public void exec(final Runnable runnable) {
        Objects.requireNonNull(runnable);
        LOGGER.trace("exec() called");
        boolean queued = false;
        while (!queued) {
            final long lockStamp = stampedLock.readLock();
            try {
                if (shuttingDown) {
                    throw new IllegalStateException("WorkQueue is shutting down, no more tasks can be accepted");
                }
                try {
                    // Allow join() to jump in front of us if the queue is full.
                    // In practice all calls to exec() should happen before join() is called.
                    queued = queue.offer(runnable, 100, TimeUnit.MILLISECONDS);
                } catch (final InterruptedException e) {
                    LOGGER.debug("exec() - Thread interrupted");
                    Thread.currentThread().interrupt();
                    throw UncheckedInterruptedException.create(e);
                }
            } finally {
                stampedLock.unlockRead(lockStamp);
            }
        }
    }

    /**
     * Block until all tasks have been completed.
     *
     * @throws UncheckedInterruptedException if the thread is interrupted
     */
    public void join() {
        LOGGER.debug("join() called");
        final long lockStamp = stampedLock.writeLock();
        try {
            if (!shuttingDown) {
                // Mark work queue as shutting down so no more tasks can be accepted
                shuttingDown = true;
                // Put one poison pill per thread into the queue to signal to the worker threads to gracefully stop
                // once they have completed all work on the queue
                for (int i = 0; i < threadCount; i++) {
                    try {
                        queue.put(PoisonPill.INSTANCE);
                    } catch (final InterruptedException e) {
                        LOGGER.debug("join() - Thread interrupted");
                        Thread.currentThread().interrupt();
                        throw UncheckedInterruptedException.create(e);
                    }
                }
            } else {
                LOGGER.warn("Join called multiple times");
            }
        } finally {
            stampedLock.unlockWrite(lockStamp);
        }
        // All callers will wait for all tasks to complete
        try {
            CompletableFuture.allOf(futures)
                    .join();
        } catch (final CompletionException e) {
            // A worker interrupted while waiting for work completes its future with an
            // UncheckedInterruptedException, which join() wraps. Unwrap it so callers can treat an
            // interrupted work queue the same as being interrupted themselves, i.e. as expected at shutdown
            // rather than as an error.
            if (e.getCause() instanceof final UncheckedInterruptedException cause) {
                throw cause;
            }
            throw e;
        }
    }

    /**
     * @return The current number of tasks in the queue. Note, once {@link WorkQueue#join()} has been called
     * the task count will include one poison pill per thread.
     */
    public long getTaskCount() {
        // Unless we drainTo then count, then put back, there is no way to get
        // a count that excludes the poison pills.
        return queue.size();
    }

    @Override
    public String toString() {
        return "WorkQueue{" +
               "capacity=" + capacity +
               ", threadCount=" + threadCount +
               ", shuttingDown=" + shuttingDown +
               ", taskCount=" + getTaskCount() +
               '}';
    }


    // --------------------------------------------------------------------------------

    /**
     * A runnable to indicate that the queue is shutting down.
     */
    private static class PoisonPill implements Runnable {

        private static final PoisonPill INSTANCE = new PoisonPill();

        @Override
        public void run() {

        }
    }
}
