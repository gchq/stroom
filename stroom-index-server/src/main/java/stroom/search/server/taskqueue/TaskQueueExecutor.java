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

package stroom.search.server.taskqueue;

import stroom.task.server.TaskCallbackAdaptor;
import stroom.task.server.TaskManager;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Task;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskQueueExecutor {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TaskQueueExecutor.class);

    private static final int DEFAULT_CONCURRENCY = 5;
    private final TaskManager taskManager;
    private final AtomicInteger currentlyExecuting = new AtomicInteger();
    private final TaskQueue taskQueue;
    private int concurrency = DEFAULT_CONCURRENCY;

    public TaskQueueExecutor(final TaskManager taskManager, final TaskQueue taskQueue) {
        this.taskManager = taskManager;
        this.taskQueue = taskQueue;
    }

    public synchronized void submit(final Task<?> task) {
        try {
            // Block submission if we have got too many tasks in the queue.
            while (!taskQueue.offer(task, 1, TimeUnit.SECONDS)) {
                execNextTask();
            }

            execNextTask();
        } catch (final InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void execNextTask() {
        final int taskCount = currentlyExecuting.getAndIncrement();
        if (taskCount < concurrency) {
            Task<?> task = null;

            try {
                task = taskQueue.poll(1, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                LOGGER.error(e.getMessage(), e);
            }

            if (task != null) {
                if (task.isTerminated()) {
                    currentlyExecuting.decrementAndGet();
                    execNextTask();

                } else {
                    taskManager.execAsync(task, new TaskCallbackAdaptor() {
                        @Override
                        public void onSuccess(final Object result) {
                            currentlyExecuting.decrementAndGet();
                            execNextTask();
                        }

                        @Override
                        public void onFailure(final Throwable t) {
                            currentlyExecuting.decrementAndGet();
                            execNextTask();
                        }
                    });
                }
            } else {
                currentlyExecuting.decrementAndGet();
            }
        } else {
            currentlyExecuting.decrementAndGet();
        }
    }

    public void setConcurrency(final int concurrency) {
        this.concurrency = concurrency;
    }
}
