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

package stroom.search.impl.shard;

import stroom.search.coprocessor.Receiver;
import stroom.search.impl.shard.IndexShardSearchTask.IndexShardQueryFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskExecutor;
import stroom.task.api.TaskProducer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Provider;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

class IndexShardSearchTaskProducer extends TaskProducer {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearchTaskProducer.class);
    private static final String TASK_NAME = "Search Index Shard";

    private final Queue<IndexShardSearchRunnable> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger tasksRequested = new AtomicInteger();

    private final IndexShardSearchProgressTracker tracker;

    IndexShardSearchTaskProducer(final TaskExecutor taskExecutor,
                                 final Receiver receiver,
                                 final List<Long> shards,
                                 final IndexShardQueryFactory queryFactory,
                                 final String[] fieldNames,
                                 final int maxThreadsPerTask,
                                 final TaskContextFactory taskContextFactory,
                                 final TaskContext parentContext,
                                 final Provider<IndexShardSearchTaskHandler> handlerProvider,
                                 final IndexShardSearchProgressTracker tracker) {
        super(taskExecutor, maxThreadsPerTask, taskContextFactory, parentContext, TASK_NAME);
        this.tracker = tracker;

        for (final Long shard : shards) {
            final IndexShardSearchTask task = new IndexShardSearchTask(queryFactory, shard, fieldNames, receiver, tracker.getHitCount());
            final IndexShardSearchRunnable runnable = new IndexShardSearchRunnable(task, handlerProvider, tracker);
            taskQueue.add(runnable);
        }
    }

    void process() {
        final int count = taskQueue.size();
        LOGGER.debug(() -> String.format("Queued %s index shard search tasks", count));

        // Attach to the supplied executor.
        attach();

        // Tell the supplied executor that we are ready to deliver tasks.
        signalAvailable();
    }

    protected boolean isComplete() {
        return Thread.currentThread().isInterrupted() || tracker.isComplete();
    }

    @Override
    protected Consumer<TaskContext> getNext() {
        IndexShardSearchRunnable task = null;

        if (!isComplete()) {
            // If there are no open shards that can be used for any tasks then just get the task at the head of the queue.
            task = taskQueue.poll();
            if (task != null) {
                final int no = tasksRequested.incrementAndGet();
                task.getTask().setShardTotal(tracker.getShardCount());
                task.getTask().setShardNumber(no);
            }
        }

        return task;
    }

    @Override
    public String toString() {
        return "IndexShardSearchTaskProducer{" +
                "tracker=" + tracker +
                '}';
    }

    private static class IndexShardSearchRunnable implements Consumer<TaskContext> {
        private final IndexShardSearchTask task;
        private final Provider<IndexShardSearchTaskHandler> handlerProvider;
        private final IndexShardSearchProgressTracker tracker;

        IndexShardSearchRunnable(final IndexShardSearchTask task,
                                 final Provider<IndexShardSearchTaskHandler> handlerProvider,
                                 final IndexShardSearchProgressTracker tracker) {
            this.task = task;
            this.handlerProvider = handlerProvider;
            this.tracker = tracker;
        }

        @Override
        public void accept(final TaskContext taskContext) {
            try {
                final IndexShardSearchTaskHandler handler = handlerProvider.get();
                handler.exec(taskContext, task);
            } finally {
                tracker.incrementCompleteShardCount();
            }
        }

        public IndexShardSearchTask getTask() {
            return task;
        }
    }
}
