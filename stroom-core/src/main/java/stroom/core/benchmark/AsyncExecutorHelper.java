/*
 * Copyright 2016 Crown Copyright
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

package stroom.core.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

class AsyncExecutorHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncExecutorHelper.class);
    private static final String STATUS = "Executing task {}\n{}";
    private static final String FINISHED = "Finished task {}\n{}";
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;
    private final TaskContext parentTaskContext;
    private final int concurrent;
    private final String taskInfo;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<Consumer<TaskContext>> taskList = new ArrayList<>();
    private final List<Consumer<TaskContext>> executingTasks = new ArrayList<>();
    private final AtomicLong running = new AtomicLong();
    private final AtomicLong remaining = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong total = new AtomicLong();
    private final AtomicBoolean busy = new AtomicBoolean();

    AsyncExecutorHelper(final String taskInfo,
                        final TaskContextFactory taskContextFactory,
                        final Executor executor,
                        final TaskContext parentTaskContext,
                        final int concurrent) {
        this.taskInfo = taskInfo;
        this.taskContextFactory = taskContextFactory;
        this.executor = executor;
        this.parentTaskContext = parentTaskContext;
        this.concurrent = concurrent;
        updateInfo();
    }

    public void fork(final Consumer<TaskContext> consumer) {
        // Add the task to the task list.
        lock.lock();
        try {
            if (!Thread.currentThread().isInterrupted()) {
                total.incrementAndGet();

                remaining.incrementAndGet();
                busy.set(true);
                taskList.add(consumer);

                execAsync();
                updateInfo();
            }
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            remaining.set(0);
            busy.set(running.get() > 0 || remaining.get() > 0);
            taskList.clear();
            updateInfo();
        } finally {
            lock.unlock();
        }
    }

    private void execAsync() {
        lock.lock();
        try {
            if (remaining.get() > 0 && running.get() < concurrent) {
                final Consumer<TaskContext> consumer = taskList.remove(0);
                final Runnable runnable = taskContextFactory.context(parentTaskContext, "Sub task", consumer);

                // Execute the task.
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(STATUS, runnable, getStatus());
                }

                running.incrementAndGet();
                remaining.decrementAndGet();
                executingTasks.add(consumer);
                updateInfo();

                try {
                    CompletableFuture
                            .runAsync(runnable, executor)
                            .whenComplete((r, t) -> {
                                if (t != null) {
                                    try {
                                        LOGGER.error("onFailure() - Unhandled Exception", t);
                                    } catch (final RuntimeException e) {
                                        LOGGER.error("onFailure() - Error calling callback", e);
                                    } finally {
                                        // Remove the task from the task list.
                                        recordFinish(consumer);

                                        // Try and run another task.
                                        execAsync();
                                    }
                                } else {
                                    // Remove the task from the task list.
                                    recordFinish(consumer);

                                    // Try and run another task.
                                    execAsync();
                                }
                            });

                } catch (final RuntimeException e) {
                    // Failed to even start
                    recordFinish(consumer);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void recordFinish(final Consumer<TaskContext> consumer) {
        lock.lock();
        try {
            executingTasks.remove(consumer);
            running.decrementAndGet();
            completed.incrementAndGet();

            busy.set(running.get() > 0 || remaining.get() > 0);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(FINISHED, consumer, getStatus());
            }

            updateInfo();
        } finally {
            lock.unlock();
        }
    }

    public void join() {
        join(100);
    }

    public void join(final int waitMs) {
        try {
            while (busy()) {
                Thread.sleep(waitMs);
            }
        } catch (final InterruptedException e) {
            LOGGER.error("Thread interrupted!", e);

            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        }
    }

    public boolean busy() {
        return busy.get();
    }

    private void updateInfo() {
        if (taskInfo != null) {
            final StringBuilder sb = new StringBuilder();
            sb.append(taskInfo);
            appendStatus(sb);
            parentTaskContext.info(sb::toString);
        }
    }

    private void appendStatus(final StringBuilder sb) {
        sb.append("(");
        sb.append(running);
        sb.append(" running, ");
        sb.append(remaining);
        sb.append(" remaining, ");
        sb.append(completed);
        sb.append(" completed, ");
        sb.append(total);
        sb.append(" total)");
    }

    private String getStatus() {
        final StringBuilder sb = new StringBuilder();
        appendStatus(sb);
        return sb.toString();
    }
}
