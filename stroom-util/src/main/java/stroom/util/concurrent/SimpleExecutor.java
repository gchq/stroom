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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstraction of a ExecutorService that also tracks submitted and completed job
 * counts.
 */
public class SimpleExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleExecutor.class);

    private static final int THREAD_SLEEP_MS = 100;
    private static final int LOGGING_DEBUG_MS = 1000;

    private final ExecutorService executorService;

    private final int threadCount;

    /**
     * Number of jobs submitted
     */
    private final AtomicInteger executorSubmitCount = new AtomicInteger(0);

    /**
     * Number of jobs completed
     */
    private final AtomicInteger executorCompleteCount = new AtomicInteger(0);

    public SimpleExecutor(final int threadCount) {
        this.threadCount = threadCount;
        this.executorService = Executors.newFixedThreadPool(threadCount);
    }

    private void defaultShortSleep() throws InterruptedException {
        Thread.sleep(THREAD_SLEEP_MS);
    }

    /**
     * Submit a job
     */
    public void execute(final Runnable runnable) {
        try {
            executorService.execute(() -> {
                try {
                    runnable.run();
                } catch (final RuntimeException e) {
                    LOGGER.error("run() - Uncaught exception from execution", e);
                } finally {
                    executorCompleteCount.incrementAndGet();
                }
            });

            // If we get a rejected execution then we don't want to increment
            // the submit count.
            executorSubmitCount.incrementAndGet();

        } catch (final RejectedExecutionException e) {
            // don't care about this
            LOGGER.error("run() - ignoring RejectedExecutionException");
        }
    }

    /**
     * Wait for a submitted jobs to complete (without shutting down).
     */
    public void waitForComplete() throws InterruptedException {
        long lastTime = System.currentTimeMillis();
        while (!executorService.isTerminated() && executorCompleteCount.get() < executorSubmitCount.get()) {
            defaultShortSleep();

            if (LOGGER.isDebugEnabled()) {
                final long time = System.currentTimeMillis();
                if (time - lastTime > LOGGING_DEBUG_MS) {
                    LOGGER.debug("waitForComplete() - " + toString());
                }
                lastTime = time;
            }
        }
    }

    /**
     * Wait for the thread pool to stop.
     */
    private void waitForTerminated() throws InterruptedException {
        long lastTime = System.currentTimeMillis();
        while (!executorService.isTerminated()) {
            defaultShortSleep();

            if (LOGGER.isDebugEnabled()) {
                final long time = System.currentTimeMillis();

                if (time - lastTime > LOGGING_DEBUG_MS) {
                    LOGGER.debug("waitForComplete() - " + toString());
                }
                lastTime = time;
            }
        }
    }

    /**
     * Stop and wait for shutdown.
     *
     * @param now don't wait for pending jobs to start
     */
    public void stop(final boolean now) throws InterruptedException {
        if (now) {
            executorService.shutdownNow();
        } else {
            executorService.shutdown();
        }

        waitForTerminated();
    }

    /**
     * @return finished due to stop
     */
    public boolean isStopped() {
        return executorService.isTerminated();
    }

    int getExecutorCompleteCount() {
        return executorCompleteCount.get();
    }

    int getExecutorSubmitCount() {
        return executorSubmitCount.get();
    }

    @Override
    public String toString() {
        return "SimpleExecutor(" + threadCount + ") progress=" + executorCompleteCount + "/" + executorSubmitCount;
    }
}
