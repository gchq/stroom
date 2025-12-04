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

package stroom.lifecycle.impl;

import stroom.lifecycle.api.ShutdownTask;
import stroom.lifecycle.api.StartupTask;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LogExecutionTime;

import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Singleton
class LifecycleService implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleService.class);

    private final Deque<Provider<Runnable>> startPending;
    private final Deque<Provider<Runnable>> stopPending;

    // The scheduled executor that executes executable beans.
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private final AtomicBoolean enabled = new AtomicBoolean();

    private volatile CountDownLatch startRemaining = new CountDownLatch(0);
    private volatile CountDownLatch stopRemaining = new CountDownLatch(0);

    @Inject
    LifecycleService(final Map<StartupTask, Provider<Runnable>> startupTaskMap,
                     final Map<ShutdownTask, Provider<Runnable>> shutdownTaskMap,
                     final LifecycleConfig lifecycleConfig) {
        this.enabled.set(lifecycleConfig.isEnabled());

        startPending = startupTaskMap.entrySet()
                .stream()
                .sorted((o1, o2) -> o2.getKey().getPriority() - o1.getKey().getPriority())
                .map(Entry::getValue)
                .collect(Collectors.toCollection(ConcurrentLinkedDeque::new));

        stopPending = shutdownTaskMap.entrySet()
                .stream()
                .sorted((o1, o2) -> o2.getKey().getPriority() - o1.getKey().getPriority())
                .map(Entry::getValue)
                .collect(Collectors.toCollection(ConcurrentLinkedDeque::new));
    }

    /**
     * Called when the application context is initialised.
     */
    @Override
    public void start() {
        LOGGER.info("Starting Stroom");

        if (enabled.get()) {
            // Do this async so that we don't delay starting the web app up
            CompletableFuture.runAsync(() -> {
                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                LOGGER.info("Starting up in background");
                doStart();
                LOGGER.info("Started in {}", logExecutionTime);
            });
        }
    }

    /**
     * Called when the application context is destroyed.
     */
    @Override
    public void stop() {
        LOGGER.info("Stopping Stroom");
        if (enabled.get()) {
            shuttingDown.set(true);
            doStop();
        }
        LOGGER.info("Stopped Stroom");
    }

    private synchronized void doStart() {
        if (!shuttingDown.get()) {
            LOGGER.info("Starting Stroom Lifecycle service");
            try {
                startRemaining = new CountDownLatch(startPending.size());
                startNext();

                // Wait for startup to complete.
                startRemaining.await();

                LOGGER.info("Started Stroom Lifecycle service");
            } catch (final InterruptedException | UncheckedInterruptedException e) {
                LOGGER.info("Interrupted");
                stop();
            }
        }
    }

    private void startNext() {
        if (shuttingDown.get()) {
            // We are shutting down so just exhaust all tasks.
            while (startPending.pollFirst() != null) {
                startRemaining.countDown();
            }

        } else {
            final Provider<Runnable> runnableProvider = startPending.pollFirst();
            if (runnableProvider != null) {
                final Runnable runnable = runnableProvider.get();
                LOGGER.info("Lifecycle " + runnable.getClass().getSimpleName() + " starting up");
                CompletableFuture
                        .runAsync(runnable)
                        .whenComplete((r, t) -> {
                            if (t != null) {
                                while (t instanceof CompletionException) {
                                    t = t.getCause();
                                }
                                LOGGER.error(t.getMessage(), t);
                            }
                            startNext();
                            startRemaining.countDown();
                        });
            }
        }
    }

    private synchronized void doStop() {
        try {
            // Wait for startup to complete.
            while (!startRemaining.await(1, TimeUnit.SECONDS)) {
                LOGGER.info("Waiting for startup to finish before shutting down");
            }

            stopRemaining = new CountDownLatch(stopPending.size());
            stopNext();

            try {
                // Wait for stop to complete.
                stopRemaining.await();

            } catch (final InterruptedException e) {
                LOGGER.error(e.getMessage(), e);

                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();
            }

            LOGGER.info("Stopped Stroom Lifecycle service");
        } catch (final InterruptedException | UncheckedInterruptedException e) {
            LOGGER.info("Interrupted");
        }
    }

    private void stopNext() {
        final Provider<Runnable> runnableProvider = stopPending.pollFirst();
        if (runnableProvider != null) {
            final Runnable runnable = runnableProvider.get();
            LOGGER.info("Lifecycle " + runnable.getClass().getSimpleName() + " shutting down");
            CompletableFuture
                    .runAsync(runnable)
                    .whenComplete((r, t) -> {
                        if (t != null) {
                            while (t instanceof CompletionException) {
                                t = t.getCause();
                            }
                            LOGGER.error(t.getMessage(), t);
                        }
                        stopNext();
                        stopRemaining.countDown();
                    });
        }
    }
}
