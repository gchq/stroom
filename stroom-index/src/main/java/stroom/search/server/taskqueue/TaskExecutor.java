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
import stroom.util.shared.Task;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskExecutor {
    private static final int DEFAULT_MAX_THREADS = 5;

    private volatile int maxThreads = DEFAULT_MAX_THREADS;

    private final TaskManager taskManager;
    private final AtomicInteger totalThreads = new AtomicInteger();

    private final ConcurrentSkipListSet<TaskProducer> producers = new ConcurrentSkipListSet<>();
    private volatile TaskProducer lastProducer;

    public TaskExecutor(final TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void addProducer(final TaskProducer producer) {
        producers.add(producer);
    }

    public void removeProducer(final TaskProducer producer) {
        producers.remove(producer);
    }

    public void exec() {
        Task<?> task = execNextTask();
        while (task != null) {
            task = execNextTask();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Task execNextTask() {
        TaskProducer producer = null;
        Task<?> task = null;

        final int total = totalThreads.getAndIncrement();
        if (total < maxThreads) {
            // Try and get a task from usable producers.
            final int tries = producers.size();
            for (int i = 0; i < tries && task == null; i++) {
                producer = nextProducer();
                if (producer != null) {
                    task = producer.next();
                }
            }

            final TaskProducer currentProducer = producer;
            final Task<?> currentTask = task;

            if (currentTask != null) {
                if (currentTask.isTerminated()) {
                    taskComplete(currentProducer, currentTask);

                } else {
                    taskManager.execAsync(currentTask, new TaskCallbackAdaptor() {
                        @Override
                        public void onSuccess(final Object result) {
                            taskComplete(currentProducer, currentTask);
                        }

                        @Override
                        public void onFailure(final Throwable t) {
                            taskComplete(currentProducer, currentTask);
                        }
                    });
                }
            } else {
                totalThreads.decrementAndGet();
            }
        } else {
            totalThreads.decrementAndGet();
        }

        return task;
    }

    private void taskComplete(final TaskProducer producer, final Task<?> task) {
        totalThreads.decrementAndGet();
        producer.complete(task);
        exec();
    }

    private synchronized TaskProducer nextProducer() {
        TaskProducer producer = null;
        try {
            if (lastProducer == null) {
                producer = producers.first();
            } else {
                producer = producers.higher(lastProducer);
                if (producer == null) {
                    producer = producers.first();
                }
            }
        } catch (final Exception e) {
            // Ignore.
        }
        lastProducer = producer;
        return producer;
    }

    public void setMaxThreads(final int maxThreads) {
        this.maxThreads = maxThreads;
    }
}
