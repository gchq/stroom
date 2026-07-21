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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;

public class WorkQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(WorkQueue.class);

    private static final Runnable POISON_PILL = () -> {
    };

    private final int threadCount;
    private final ArrayBlockingQueue<Runnable> queue;
    private final CompletableFuture<Void>[] futures;
    private final StampedLock stampedLock = new StampedLock();
    private boolean shuttingDown = false;

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
        this.threadCount = threadCount;
        queue = new ArrayBlockingQueue<>(capacity);
        futures = new CompletableFuture[threadCount];
        for (int i = 0; i < threadCount; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    Runnable runnable = queue.take();
                    // Use instance equality
                    while (runnable != POISON_PILL) {
                        try {
                            runnable.run();
                        } catch (final RuntimeException e) {
                            // Ideally, the runnable should handle its own exceptions, but just in case
                            // we will swallow the exception so that the thread doesn't die.
                            LOGGER.error("Error while executing runnable - {}", LogUtil.exceptionMessage(e), e);
                        }
                        if (Thread.currentThread().isInterrupted()) {
                            LOGGER.debug("Thread interrupted, dropping out");
                            break;
                        }
                        runnable = queue.take();
                    }
                    LOGGER.debug("POISON_PILL found, dropping out");
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e.getMessage(), e);
                }
            }, executor);
        }
    }

    public void exec(final Runnable runnable) {
        Objects.requireNonNull(runnable);
        LOGGER.debug("exec() called");
        boolean queued = false;
        while (!queued) {
            final long lockStamp = stampedLock.readLock();
            try {
                if (shuttingDown) {
                    throw new IllegalStateException("WorkQueue is shutting down");
                }
                try {
                    // Allow join() to jump in front of us if the queue is full.
                    // In practice all calls to exec() should happen before join() is called.
                    queued = queue.offer(runnable, 100, TimeUnit.MILLISECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e.getMessage(), e);
                }
            } finally {
                stampedLock.unlockRead(lockStamp);
            }
        }
    }

    public void join() {
        LOGGER.debug("join() called");
        final long lockStamp = stampedLock.writeLock();
        try {
            if (!shuttingDown) {
                shuttingDown = true;
                for (int i = 0; i < threadCount; i++) {
                    try {
                        queue.put(POISON_PILL);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }
            } else {
                LOGGER.warn("Join called multiple times");
            }
        } finally {
            stampedLock.unlockWrite(lockStamp);
        }
        // All callers will wait for all tasks to complete
        CompletableFuture.allOf(futures)
                .join();
    }
}
