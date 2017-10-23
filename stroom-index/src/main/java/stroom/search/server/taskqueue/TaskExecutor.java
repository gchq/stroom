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

import stroom.task.server.ExecutorProvider;
import stroom.util.shared.ThreadPool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskExecutor {
    private static final int DEFAULT_MAX_THREADS = 5;

    private volatile int maxThreads = DEFAULT_MAX_THREADS;

    private final ExecutorProvider executorProvider;
    private final ThreadPool threadPool;
    private final AtomicInteger totalThreads = new AtomicInteger();

    private final ConcurrentSkipListSet<TaskProducer> producers = new ConcurrentSkipListSet<>();
    private volatile TaskProducer lastProducer;

    public TaskExecutor(final ExecutorProvider executorProvider, final ThreadPool threadPool) {
        this.executorProvider = executorProvider;
        this.threadPool = threadPool;
    }

    public void addProducer(final TaskProducer producer) {
        producers.add(producer);
    }

    public void removeProducer(final TaskProducer producer) {
        producers.remove(producer);
    }

    public void exec() {
        Runnable task = execNextTask();
        while (task != null) {
            task = execNextTask();
        }
    }

    private Runnable execNextTask() {
        TaskProducer producer = null;
        Runnable task = null;

        final int total = totalThreads.getAndIncrement();
        boolean executing = false;

        try {
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
                final Runnable currentTask = task;

                if (currentTask != null) {
                    final Executor executor = executorProvider.getExecutor(threadPool);
                    executing = true;
                    CompletableFuture.runAsync(currentTask, executor).thenAccept(result -> taskComplete(currentProducer, currentTask));
                }
            }
        } finally {
            if (!executing) {
                totalThreads.decrementAndGet();
            }
        }

        return task;
    }

    private void taskComplete(final TaskProducer producer, final Runnable task) {
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
