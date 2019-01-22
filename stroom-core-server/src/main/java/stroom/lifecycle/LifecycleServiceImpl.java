/*
 * Copyright 2017 Crown Copyright
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

package stroom.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.jobsystem.ScheduledTaskExecutor;
import stroom.security.Security;
import stroom.task.ExecutorProvider;
import stroom.task.StroomThreadGroup;
import stroom.task.api.TaskManager;
import stroom.lifecycle.api.ShutdownTask;
import stroom.lifecycle.api.StartupTask;
import stroom.util.logging.LogExecutionTime;
import stroom.util.thread.CustomThreadFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Singleton
class LifecycleServiceImpl implements LifecycleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleServiceImpl.class);
    private static final String STROOM_LIFECYCLE_THREAD_POOL = "Stroom Lifecycle#";

    private static final int ONE_SECOND = 1000;

    private final Deque<Provider<Runnable>> startPending;
    private final Deque<Provider<Runnable>> stopPending;
    private final TaskManager taskManager;
    private final ScheduledTaskExecutor scheduledTaskExecutor;
    private final Security security;
    // The scheduled executor that executes executable beans.
    private final AtomicReference<ScheduledExecutorService> scheduledExecutorService = new AtomicReference<>();
    private final AtomicBoolean startingUp = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final long executionInterval;
    private final Executor executor;

    private volatile CountDownLatch startRemaining;
    private volatile CountDownLatch stopRemaining;

    @Inject
    LifecycleServiceImpl(final TaskManager taskManager,
                                final Map<StartupTask, Provider<Runnable>> startupTaskMap,
                                final Map<ShutdownTask, Provider<Runnable>> shutdownTaskMap,
                                final ScheduledTaskExecutor scheduledTaskExecutor,
                                final Security security,
                                final LifecycleConfig lifecycleConfig,
                                final ExecutorProvider executorProvider) {
        this.taskManager = taskManager;
        this.scheduledTaskExecutor = scheduledTaskExecutor;
        this.security = security;
        this.enabled.set(lifecycleConfig.isEnabled());
        this.executionInterval = lifecycleConfig.getExecutionIntervalMs();

        this.executor = executorProvider.getExecutor();

        startPending = startupTaskMap.entrySet()
                .stream()
                .sorted(Comparator.comparingInt(e -> e.getKey().getPriority()))
                .map(Entry::getValue)
                .collect(Collectors.toCollection(ConcurrentLinkedDeque::new));
        stopPending = shutdownTaskMap.entrySet()
                .stream()
                .sorted(Comparator.comparingInt(e -> e.getKey().getPriority()))
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
            new Thread(() -> {
                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                LOGGER.info("Starting up in background");
                doStart();
                LOGGER.info("Started in {}", logExecutionTime);
            }).start();
        }
    }

    /**
     * Called when the application context is destroyed.
     */
    @Override
    public void stop() {
        LOGGER.info("Stopping Stroom");
        if (enabled.get()) {
            doStop();
        }
        LOGGER.info("Stopped Stroom");
    }

    private void doStart() {
        LOGGER.info("Starting Stroom Lifecycle service");
        try {
            startingUp.set(true);
            taskManager.startup();
            startRemaining = new CountDownLatch(startPending.size());
            startNext();

            // Wait for startup to complete.
            startRemaining.await();

            // Create the runnable object that will perform execution on all
            // scheduled services.
            final ReentrantLock lock = new ReentrantLock();

            final Runnable runnable = () -> {
                if (lock.tryLock()) {
                    try {
                        security.asProcessingUser(() -> {
                            Thread.currentThread().setName("Stroom Lifecycle - ScheduledExecutor");
                            scheduledTaskExecutor.execute();
                        });
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    } finally {
                        lock.unlock();
                    }
                } else {
                    LOGGER.warn("Still trying to execute tasks");
                }
            };

            // Create the thread pool that we will use to startup, shutdown and execute lifecycle beans asynchronously.
            final CustomThreadFactory threadFactory = new CustomThreadFactory(STROOM_LIFECYCLE_THREAD_POOL,
                    StroomThreadGroup.instance(), Thread.MIN_PRIORITY + 1);

            // Create the executor service that will execute scheduled services.
            final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1, threadFactory);
            scheduledExecutorService.scheduleWithFixedDelay(runnable, 0, executionInterval, TimeUnit.MILLISECONDS);
            this.scheduledExecutorService.set(scheduledExecutorService);

            LOGGER.info("Started Stroom Lifecycle service");
            running.set(true);
        } catch (final InterruptedException e) {
            LOGGER.info("Interrupted");
            stop();
        } finally {
            startingUp.set(false);
        }
    }

    private void startNext() {
        final Provider<Runnable> runnableProvider = startPending.pollFirst();
        if (runnableProvider != null) {
            final Runnable runnable = runnableProvider.get();
            CompletableFuture
                    .runAsync(runnable, executor)
                    .thenRun(() -> {
                        startNext();
                        startRemaining.countDown();
                    });
        }
    }

    private void doStop() {
        try {
            // Wait for startup to finish.
            while (startingUp.get()) {
                LOGGER.info("Waiting for startup to finish before shutting down");
                Thread.sleep(ONE_SECOND);
            }

            LOGGER.info("Stopping Stroom Lifecycle service");
            final ScheduledExecutorService scheduledExecutorService = this.scheduledExecutorService.get();
            if (scheduledExecutorService != null) {
                // Stop the scheduled executor.
                scheduledExecutorService.shutdown();
                try {
                    scheduledExecutorService.awaitTermination(1, TimeUnit.MINUTES);
                } catch (final InterruptedException e) {
                    LOGGER.error("Waiting termination interrupted!", e);

                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                }
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

            taskManager.shutdown();

            LOGGER.info("Stopped Stroom Lifecycle service");
        } catch (final InterruptedException e) {
            LOGGER.info("Interrupted");
        } finally {
            running.set(false);
        }
    }

    private void stopNext() {
        final Provider<Runnable> runnableProvider = stopPending.pollLast();
        if (runnableProvider != null) {
            final Runnable runnable = runnableProvider.get();
            CompletableFuture
                    .runAsync(runnable, executor)
                    .thenRun(() -> {
                        stopNext();
                        stopRemaining.countDown();
                    });
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}
