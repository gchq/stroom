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

    private final PipelineStageName stageName;
    private final FileGroupQueueWorker worker;
    private final int threadCount;
    private final Duration emptyPollBackoff;
    private final Duration errorBackoff;

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
        this.stageName = Objects.requireNonNull(stageName, "stageName");
        this.worker = Objects.requireNonNull(worker, "worker");

        if (threadCount < 1) {
            throw new IllegalArgumentException("threadCount must be >= 1, got " + threadCount);
        }
        this.threadCount = threadCount;
        this.emptyPollBackoff = Objects.requireNonNull(emptyPollBackoff, "emptyPollBackoff");
        this.errorBackoff = Objects.requireNonNull(errorBackoff, "errorBackoff");
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

        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    final FileGroupQueueWorkerResult result = worker.processNext();

                    if (result.isNoItem()) {
                        sleepUninterruptibly(emptyPollBackoff);
                    }
                    // Processed and failed items loop immediately to pick up
                    // the next item without delay.

                } catch (final IOException e) {
                    LOGGER.error(() -> LogUtil.message(
                            "Consumer thread {} encountered error in stage {}, " +
                            "backing off for {}",
                            threadName,
                            stageName.getConfigName(),
                            errorBackoff), e);
                    sleepUninterruptibly(errorBackoff);

                } catch (final RuntimeException e) {
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

    private void sleepUninterruptibly(final Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
