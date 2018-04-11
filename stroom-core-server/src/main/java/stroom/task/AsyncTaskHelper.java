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

package stroom.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.Task;
import stroom.task.TaskContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class AsyncTaskHelper<R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncTaskHelper.class);
    private static final String STATUS = "Executing task {}\n{}";
    private static final String FINISHED = "Finished task {}\n{}";
    private final TaskContext taskContext;
    private final TaskManager taskManager;
    private final int concurrent;
    private final String taskInfo;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<Entry<R>> taskList = new ArrayList<>();
    private final List<Entry<R>> executingTasks = new ArrayList<>();
    private volatile long running;
    private volatile long remaining;
    private volatile long completed;
    private volatile long total;
    private volatile boolean busy;
    public AsyncTaskHelper(final String taskInfo, final TaskContext taskContext, final TaskManager taskManager,
                           final int concurrent) {
        this.taskInfo = taskInfo;
        this.taskContext = taskContext;
        this.taskManager = taskManager;
        this.concurrent = concurrent;
        updateInfo();
    }

    public void fork(final Task<R> task) {
        fork(task, null);
    }

    public void fork(final Task<R> task, final TaskCallback<R> callback) {
        // Add the task to the task list.
        lock.lock();
        try {
            if (!taskContext.isTerminated()) {
                total++;

                remaining++;
                busy = true;
                taskList.add(new Entry<>(task, callback));

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
            remaining = 0;
            busy = running > 0 || remaining > 0;
            taskList.clear();
            updateInfo();
        } finally {
            lock.unlock();
        }
    }

    private void execAsync() {
        lock.lock();
        try {
            if (remaining > 0 && running < concurrent) {
                final Entry<R> entry = taskList.remove(0);
                final Task<R> task = entry.task;
                final TaskCallback<R> callback = entry.callback;

                // Execute the task.
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(STATUS, task, getStatus());
                }

                running++;
                remaining--;
                executingTasks.add(entry);
                updateInfo();

                try {
                    taskManager.execAsync(task, new TaskCallbackAdaptor<R>() {
                        @Override
                        public void onSuccess(final R result) {
                            try {
                                if (callback != null) {
                                    callback.onSuccess(result);
                                }
                            } catch (final RuntimeException e) {
                                LOGGER.error("onSuccess() - Error calling callback", e);
                            } finally {
                                // Remove the task from the task list.
                                recordFinish(entry, task);

                                // Try and run another task.
                                execAsync();
                            }
                        }

                        @Override
                        public void onFailure(final Throwable t) {
                            try {
                                if (callback != null) {
                                    callback.onFailure(t);
                                } else {
                                    LOGGER.error("onFailure() - Unhandled Exception", t);
                                }
                            } catch (final RuntimeException e) {
                                LOGGER.error("onFailure() - Error calling callback", e);
                            } finally {
                                // Remove the task from the task list.
                                recordFinish(entry, task);

                                // Try and run another task.
                                execAsync();
                            }
                        }

                    });
                } catch (final RuntimeException e) {
                    // Failed to even start
                    recordFinish(entry, task);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void recordFinish(final Entry<R> entry, final Task<R> task) {
        lock.lock();
        try {
            executingTasks.remove(entry);
            running--;
            completed++;

            busy = running > 0 || remaining > 0;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(FINISHED, task, getStatus());
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
        return busy;
    }

    private void updateInfo() {
        if (taskInfo != null) {
            final StringBuilder sb = new StringBuilder();
            sb.append(taskInfo);
            appendStatus(sb);
            taskContext.info(sb.toString());
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

    private static class Entry<R> {
        Task<R> task;
        TaskCallback<R> callback;

        public Entry(final Task<R> task, final TaskCallback<R> callback) {
            this.task = task;
            this.callback = callback;
        }
    }
}
