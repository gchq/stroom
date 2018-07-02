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
import stroom.entity.StroomEntityManager;
import stroom.jobsystem.ScheduledTaskExecutor;
import stroom.properties.api.StroomPropertyService;
import stroom.security.Security;
import stroom.task.StroomThreadGroup;
import stroom.task.TaskCallbackAdaptor;
import stroom.task.TaskManager;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.VoidResult;
import stroom.util.thread.CustomThreadFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public class LifecycleServiceImpl implements LifecycleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleServiceImpl.class);
    private static final String STROOM_LIFECYCLE_THREAD_POOL = "Stroom Lifecycle#";

    private static final int ONE_SECOND = 1000;
    private static final long DEFAULT_INTERVAL = 10 * ONE_SECOND;

    private final TaskManager taskManager;
    private final StroomBeanLifeCycle stroomBeanLifeCycle;
    private final StroomEntityManager entityManager;
    private final ScheduledTaskExecutor scheduledTaskExecutor;
    private final Security security;
    private final AtomicInteger startingBeanCount = new AtomicInteger();
    private final AtomicInteger stoppingBeanCount = new AtomicInteger();
    // The scheduled executor that executes executable beans.
    private final AtomicReference<ScheduledExecutorService> scheduledExecutorService = new AtomicReference<>();
    private final AtomicBoolean startingUp = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final long executionInterval;

    @Inject
    public LifecycleServiceImpl(final TaskManager taskManager,
                                final StroomBeanLifeCycle stroomBeanLifeCycle,
                                final StroomEntityManager entityManager,
                                final ScheduledTaskExecutor scheduledTaskExecutor,
                                final Security security,
                                final StroomPropertyService propertyService) {
        this.taskManager = taskManager;
        this.stroomBeanLifeCycle = stroomBeanLifeCycle;
        this.entityManager = entityManager;
        this.scheduledTaskExecutor = scheduledTaskExecutor;
        this.security = security;
        this.enabled.set(propertyService.getBooleanProperty("stroom.lifecycle.enabled", false));

        Long executionInterval;
        try {
            executionInterval = ModelStringUtil.parseDurationString(propertyService.getProperty("stroom.lifecycle.executionInterval"));
            if (executionInterval == null) {
                executionInterval = DEFAULT_INTERVAL;
            }
        } catch (final NumberFormatException e) {
            LOGGER.error("Unable to parse property 'stroom.lifecycle.executionInterval' value '" + propertyService.getProperty("stroom.lifecycle.executionInterval")
                    + "', using default of '10s' instead", e);
            executionInterval = DEFAULT_INTERVAL;
        }
        this.executionInterval = executionInterval;
    }

    /**
     * Called when the application context is initialised.
     */
    @Override
    public void start() {
        if (enabled.get()) {
            // Do this async so that we don't delay starting the web app up
            new Thread(() -> {
                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                LOGGER.info("init() - Starting up in background");
                doStart();
                LOGGER.info("init() - Started in {}", logExecutionTime);
            }).start();
        }
    }

    /**
     * Called when the application context is destroyed.
     */
    @Override
    public void stop() {
        LOGGER.debug("contextDestroyed()");
        if (enabled.get()) {
            doStop();
        }
    }

    private void doStart() {
        LOGGER.info("Starting Stroom Lifecycle service");
        try {
            startingUp.set(true);

            taskManager.startup();

            startNext();
            // Wait for startup to complete.
            while (startingBeanCount.get() > 0) {
                Thread.sleep(500);
            }

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

            // Create the thread pool that we will use to startup, shutdown and
            // execute lifecycle beans asynchronously.
            final CustomThreadFactory threadFactory = new CustomThreadFactory(STROOM_LIFECYCLE_THREAD_POOL,
                    StroomThreadGroup.instance(), Thread.MIN_PRIORITY + 1);

            // Create the executor service that will execute scheduled
            // services.
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
        final StroomBeanMethodExecutable executable = stroomBeanLifeCycle.getStartExecutable();
        if (executable != null) {
            startingBeanCount.getAndIncrement();
            taskManager.execAsync(new LifecycleTask(executable), new TaskCallbackAdaptor<VoidResult>() {
                @Override
                public void onSuccess(final VoidResult result) {
                    startNext();
                    startingBeanCount.getAndDecrement();
                }
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

            stopNext();

            try {
                // Wait for stop to complete.
                while (stoppingBeanCount.get() > 0) {
                    Thread.sleep(500);
                }
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
        final StroomBeanMethodExecutable executable = stroomBeanLifeCycle.getStopExecutable();
        if (executable != null) {
            stoppingBeanCount.getAndIncrement();
            taskManager.execAsync(new LifecycleTask(executable), new TaskCallbackAdaptor<VoidResult>() {
                @Override
                public void onSuccess(final VoidResult result) {
                    stopNext();
                    stoppingBeanCount.getAndDecrement();
                }
            });
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}
