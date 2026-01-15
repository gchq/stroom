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

package stroom.proxy.repo;

import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import io.dropwizard.lifecycle.Managed;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class ParallelExecutor implements Managed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ParallelExecutor.class);

    private final ExecutorService executorService;
    private final Supplier<Runnable> runnableSupplier;
    private final int threadCount;
    private final String threadNamePrefix;
    private final Semaphore pauseSemaphore;
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isStopped = new AtomicBoolean(true);
    private final AtomicBoolean isStopping = new AtomicBoolean(false);

    public ParallelExecutor(final String threadNamePrefix,
                            final Supplier<Runnable> runnableSupplier,
                            final int threadCount) {
        this.runnableSupplier = runnableSupplier;
        this.threadCount = threadCount;
        this.threadNamePrefix = threadNamePrefix;
        final ThreadFactory threadFactory = new CustomThreadFactory(
                threadNamePrefix + " ",
                StroomThreadGroup.instance(),
                Thread.NORM_PRIORITY - 1);
        executorService = Executors.newFixedThreadPool(threadCount, threadFactory);
        pauseSemaphore = new Semaphore(threadCount);
    }

    /**
     * Pause the executor. Any currently running tasks will continue to run,
     * but no new tasks will be executed.
     * If already paused, is a noop.
     * Call resume to resume processing.
     */
    public synchronized void pause() {
        final boolean didChange = isPaused.compareAndSet(false, true);
        if (didChange) {
            // Drain all permits currently available. Any running tasks will still hold a permit though
            pauseSemaphore.drainPermits();
            LOGGER.info(() -> LogUtil.message(
                    "Paused parallel executor '{}', threadCount: {}, availablePermits: {}",
                    threadNamePrefix, threadCount, pauseSemaphore.availablePermits()));
        }
    }

    /**
     * Immediately resume all the executor's threads if it is currently paused.
     * If already resumed, is a noop.
     */
    public synchronized void resume() {
        final boolean didChange = isPaused.compareAndSet(true, false);
        if (didChange) {
            pauseSemaphore.release(threadCount);
            LOGGER.info(() -> LogUtil.message(
                    "Resumed parallel executor '{}', threadCount: {}, availablePermits: {}",
                    threadNamePrefix, threadCount, pauseSemaphore.availablePermits()));
        }
    }

    /**
     * If true, pause the executor's threads after they have completed their current task.
     * if false, resume all the executor's threads.
     * If already in the desired state, is a noop.
     */
    public void setPauseState(final boolean isPaused) {
        if (isPaused) {
            pause();
        } else {
            resume();
        }
    }

    @Override
    public synchronized void start() {
        if (isStopping.get()) {
            throw new IllegalStateException(LogUtil.message(
                    "Attempt to start parallel executor '{}' when it is shutting down.",
                    threadNamePrefix));
        }
        if (!isStopped.get()) {
            throw new IllegalStateException(LogUtil.message(
                    "Attempt to start parallel executor '{}' when it is already started",
                    threadNamePrefix));
        }

        LOGGER.debug("Starting parallel executor '{}', threadCount: {}",
                threadNamePrefix, threadCount);
        // Start.
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(this::run);
        }
        isStopped.set(false);
    }

    @Override
    public synchronized void stop() throws Exception {
        if (isStopping.compareAndSet(false, true)) {
            if (isPaused.get()) {
                // We need to release the blocked threads so the executor can shut down
                resume();
            }
            LOGGER.info("Stopping parallel executor '{}', threadCount: {}",
                    threadNamePrefix, threadCount);
            final DurationTimer timer = DurationTimer.start();
            executorService.shutdownNow();
            final boolean didTerminate = executorService.awaitTermination(1, TimeUnit.DAYS);
            LOGGER.info("Stopped parallel executor '{}', threadCount: {}, didTerminate: {}, duration: {}",
                    threadNamePrefix, threadCount, didTerminate, timer);
            isStopped.set(true);
        } else {
            throw new IllegalStateException(LogUtil.message(
                    "Attempt to stop parallel executor '{}' when it is already stopped or shutting down.",
                    threadNamePrefix));
        }
    }

    public boolean isStopped() {
        return isStopped.get();
    }

    public boolean isPaused() {
        return isPaused.get();
    }

    private void run() {
        try {
            while (!Thread.currentThread().isInterrupted() && !isStopping.get()) {
                try {
                    // If we have been paused, then block until we are resumed.
                    while (!pauseSemaphore.tryAcquire(5, TimeUnit.MINUTES)) {
                        // Drop out periodically and debug in case we need to know why things are stuck
                        LOGGER.debug(() -> LogUtil.message(
                                "Parallel executor '{}' waiting to be " +
                                "resumed, availablePermits: {}, isPaused: {}, isStopped: {}",
                                threadNamePrefix, pauseSemaphore.availablePermits(), isPaused, isStopped));
                    }
                    if (isStopping.get()) {
                        // Stop any tasks being kicked off when we are shutting down
                        break;
                    }
                    // Got our permit, so run the task
                    runTask();
                } catch (final InterruptedException e) {
                    // Don't reset the interrupted flag as the thread is going straight back to the pool
                    throw new UncheckedInterruptedException(e);
                } finally {
                    // Release the one permit our thread acquired
                    LOGGER.debug(() -> LogUtil.message("About to release. availablePermits: {}, isPaused: {}",
                            pauseSemaphore.availablePermits(), isPaused));
                    // If we are paused we don't want to release the permit as that would allow another
                    // task to run with that permit.

                    // There is a risk here that isPaused is set to true AFTER we check it, and thus we release
                    // when we shouldn't. All that means is the semaphore has more permits than threads. This is
                    // fine as a subsequent pause will drain all. The converse case of not releasing when we should
                    // have is more troublesome as we end up with fewer permits than threads. Hence, the sync block
                    // for that case, plus a lock while paused is not a performance hit.
                    if (isPaused.get()) {
                        synchronized (this) {
                            if (!isPaused.get()) {
                                pauseSemaphore.release();
                            }
                        }
                    } else {
                        pauseSemaphore.release();
                    }
                }
            }
        } catch (final UncheckedInterruptedException e) {
            LOGGER.debug(e::getMessage, e);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void runTask() {
        // Got our permit, so run the task
        final Runnable task = runnableSupplier.get();
        if (task != null) {
            LOGGER.debug("Running task");
            try {
                task.run();
            } catch (final UncheckedInterruptedException e) {
                // Swallow the exception to keep this thread running
                LOGGER.debug("Parallel executor interrupted: '{}' task: {}",
                        threadNamePrefix, LogUtil.exceptionMessage(e), e);
            } catch (final Exception e) {
                // Swallow the exception to keep this thread running
                LOGGER.error("Error running parallel executor '{}' task: {}",
                        threadNamePrefix, LogUtil.exceptionMessage(e), e);
            }
        } else {
            LOGGER.warn("Null task on parallel executor '{}'", threadNamePrefix);
        }
    }

    @Override
    public String toString() {
        return "ParallelExecutor{" +
               "executorService=" + executorService +
               ", isPaused=" + isPaused +
               ", isStopped=" + isStopped +
               ", threadCount=" + threadCount +
               ", threadNamePrefix='" + threadNamePrefix + '\'' +
               '}';
    }
}
