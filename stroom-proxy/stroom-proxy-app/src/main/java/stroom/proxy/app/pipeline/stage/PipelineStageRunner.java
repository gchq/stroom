/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.stage;

import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-loop runner for a single pipeline stage.
 * <p>
 * Manages N consumer threads that each call
 * {@link FileGroupQueueWorker#processNext()} in a loop. When the queue is
 * empty the thread sleeps for a configurable backoff duration before polling
 * again. When a stop is requested the threads complete their current item
 * (if any) and exit.
 * </p>
 * <p>
 * Threads are named {@code stage-<configName>-<n>} for diagnostics.
 * </p>
 */
public class PipelineStageRunner implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineStageRunner.class);

    public static final Duration DEFAULT_EMPTY_POLL_BACKOFF = Duration.ofMillis(100);
    public static final Duration DEFAULT_ERROR_BACKOFF = Duration.ofSeconds(1);

    /**
     * First delay after an item fails, doubling up to {@link #DEFAULT_MAX_FAILURE_BACKOFF}
     * while failures continue and resetting as soon as an item is processed.
     * <p>
     * Without this a failed item is retried immediately: {@code fail()} returns it to
     * the queue and the loop picks it straight back up, so an item that can never
     * succeed spins a core at thousands of attempts per second. That is merely
     * wasteful in most stages, but the forward stage does real work per attempt -
     * copying the file group to every healthy destination - so an unreachable
     * destination turned into a self-inflicted flood of duplicates downstream.
     * </p>
     */
    public static final Duration DEFAULT_FAILURE_BACKOFF = Duration.ofSeconds(1);
    public static final Duration DEFAULT_MAX_FAILURE_BACKOFF = Duration.ofSeconds(30);

    private final PipelineStageName stageName;
    private final FileGroupQueueWorker worker;
    private final int threadCount;
    private final Duration emptyPollBackoff;
    private final Duration errorBackoff;
    private final Duration failureBackoff;
    private final Duration maxFailureBackoff;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger activeThreadCount = new AtomicInteger(0);
    private volatile ExecutorService executorService;

    public PipelineStageRunner(final PipelineStageName stageName,
                               final FileGroupQueueWorker worker,
                               final int threadCount) {
        this(stageName, worker, threadCount, DEFAULT_EMPTY_POLL_BACKOFF, DEFAULT_ERROR_BACKOFF);
    }

    public PipelineStageRunner(final PipelineStageName stageName,
                               final FileGroupQueueWorker worker,
                               final int threadCount,
                               final Duration emptyPollBackoff,
                               final Duration errorBackoff) {
        this(stageName,
                worker,
                threadCount,
                emptyPollBackoff,
                errorBackoff,
                DEFAULT_FAILURE_BACKOFF,
                DEFAULT_MAX_FAILURE_BACKOFF);
    }

    public PipelineStageRunner(final PipelineStageName stageName,
                               final FileGroupQueueWorker worker,
                               final int threadCount,
                               final Duration emptyPollBackoff,
                               final Duration errorBackoff,
                               final Duration failureBackoff,
                               final Duration maxFailureBackoff) {
        this.stageName = Objects.requireNonNull(stageName, "stageName");
        this.worker = Objects.requireNonNull(worker, "worker");

        if (threadCount < 1) {
            throw new IllegalArgumentException("threadCount must be >= 1, got " + threadCount);
        }
        this.threadCount = threadCount;
        // Negative values are rejected here, not left to Thread.sleep. A negative backoff made
        // sleepUninterruptibly throw IllegalArgumentException from inside a sibling catch clause,
        // which no other clause caught - retiring the consumer thread while isRunning() still
        // reported true. That was the second trigger for the silent-death defect.
        this.emptyPollBackoff = requireNonNegative(emptyPollBackoff, "emptyPollBackoff");
        this.errorBackoff = requireNonNegative(errorBackoff, "errorBackoff");
        this.failureBackoff = requireNonNegative(failureBackoff, "failureBackoff");
        this.maxFailureBackoff = requireNonNegative(maxFailureBackoff, "maxFailureBackoff");
    }

    private static Duration requireNonNegative(final Duration duration, final String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative, got " + duration);
        }
        return duration;
    }

    /**
     * Start the consumer threads. Idempotent — does nothing if already running.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.debug(() -> LogUtil.message("Stage runner {} is already running", stageName));
            return;
        }

        LOGGER.info(() -> LogUtil.message(
                "Starting stage runner {} with {} consumer thread(s), " +
                "queue {}, emptyPollBackoff {}, errorBackoff {}",
                stageName.getConfigName(),
                threadCount,
                worker.getQueueName(),
                emptyPollBackoff,
                errorBackoff));

        executorService = Executors.newFixedThreadPool(
                threadCount,
                runnable -> {
                    final int threadIndex = activeThreadCount.incrementAndGet();
                    final Thread thread = new Thread(runnable);
                    thread.setName("stage-" + stageName.getConfigName() + "-" + threadIndex);
                    thread.setDaemon(true);
                    return thread;
                });

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(this::consumerLoop);
        }
    }

    /**
     * Request a clean shutdown and wait for threads to finish.
     *
     * @param timeout Maximum time to wait for threads to drain.
     * @return True if all threads completed within the timeout.
     */
    public boolean stop(final Duration timeout) {
        if (!running.compareAndSet(true, false)) {
            return true;
        }

        LOGGER.info(() -> LogUtil.message(
                "Stopping stage runner {} (waiting up to {})",
                stageName.getConfigName(),
                timeout));

        final ExecutorService executor = this.executorService;
        if (executor == null) {
            return true;
        }

        executor.shutdown();
        try {
            final boolean terminated = executor.awaitTermination(
                    timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!terminated) {
                LOGGER.warn(() -> LogUtil.message(
                        "Stage runner {} did not terminate within {}, forcing shutdown",
                        stageName.getConfigName(),
                        timeout));
                executor.shutdownNow();
            }

            return terminated;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            return false;
        }
    }

    /**
     * @return True if the consumer threads are currently running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * @return The number of threads that have been created (may include
     * threads that have exited due to errors).
     */
    public int getActiveThreadCount() {
        return activeThreadCount.get();
    }

    public PipelineStageName getStageName() {
        return stageName;
    }

    public FileGroupQueueWorker getWorker() {
        return worker;
    }

    public int getThreadCount() {
        return threadCount;
    }

    @Override
    public void close() {
        stop(Duration.ofSeconds(30));
    }

    private void consumerLoop() {
        final String threadName = Thread.currentThread().getName();

        LOGGER.info(() -> LogUtil.message(
                "Consumer thread {} started for stage {}",
                threadName,
                stageName.getConfigName()));

        // Consecutive failures on this thread, driving the failure backoff below.
        int consecutiveFailures = 0;

        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    final FileGroupQueueWorkerResult result = worker.processNext();

                    if (result.isNoItem()) {
                        sleepUninterruptibly(emptyPollBackoff);

                    } else if (result.isFailed()) {
                        consecutiveFailures++;
                        final int failureCount = consecutiveFailures;
                        final Duration delay = failureBackoffFor(failureCount);

                        LOGGER.debug(() -> LogUtil.message(
                                "Consumer thread {} in stage {} had {} consecutive failure(s), "
                                + "backing off for {}",
                                threadName,
                                stageName.getConfigName(),
                                failureCount,
                                delay));

                        sleepUninterruptibly(delay);

                    } else {
                        // Processed successfully - clear the backoff and loop immediately.
                        consecutiveFailures = 0;
                    }

                } catch (final IOException e) {
                    LOGGER.error(() -> LogUtil.message(
                            "Consumer thread {} encountered error in stage {}, " +
                            "backing off for {}",
                            threadName,
                            stageName.getConfigName(),
                            errorBackoff), e);
                    sleepUninterruptibly(errorBackoff);

                } catch (final Throwable e) {
                    // Throwable, not RuntimeException. An Error - an OutOfMemoryError merging a large
                    // zip, a StackOverflowError from the recursive directory helpers - would otherwise
                    // unwind this loop, and because the loop was handed to ExecutorService.submit() the
                    // throwable lands in a Future nobody reads. The thread would return to the pool,
                    // running would still be true, getActiveThreadCount() counts threads created rather
                    // than live, and the health check inspects only queue and store backends - so the
                    // stage would stop draining its queue with no signal anywhere.
                    LOGGER.error(() -> LogUtil.message(
                            "Consumer thread {} encountered unexpected error in stage {}, " +
                            "backing off for {}",
                            threadName,
                            stageName.getConfigName(),
                            errorBackoff), e);
                    sleepUninterruptibly(errorBackoff);
                }
            }
        } finally {
            LOGGER.info(() -> LogUtil.message(
                    "Consumer thread {} stopped for stage {}",
                    threadName,
                    stageName.getConfigName()));
        }
    }

    /**
     * Exponential backoff, doubling per consecutive failure and capped at
     * {@code maxFailureBackoff}.
     * <p>
     * The count is per thread, not per item - the pipeline deliberately does not
     * track per-message attempt counts. With several consumer threads a poison item
     * can therefore still be retried once per thread per backoff period, which
     * bounds the rate at roughly {@code threadCount} attempts per interval rather
     * than eliminating retries entirely.
     * </p>
     */
    private Duration failureBackoffFor(final int consecutiveFailures) {
        if (consecutiveFailures <= 1) {
            return failureBackoff;
        }

        final int doublings = Math.min(consecutiveFailures - 1, 32);
        final long millis = failureBackoff.toMillis() << doublings;

        // Guard against overflow from a very long-running failure streak.
        if (millis <= 0 || millis > maxFailureBackoff.toMillis()) {
            return maxFailureBackoff;
        }
        return Duration.ofMillis(millis);
    }

    private void sleepUninterruptibly(final Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
