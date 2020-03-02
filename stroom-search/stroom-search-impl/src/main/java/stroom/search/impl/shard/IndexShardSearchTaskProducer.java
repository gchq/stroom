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
import stroom.task.api.ExecutorProvider;
import stroom.task.shared.ThreadPool;
import stroom.task.api.ThreadPoolImpl;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.task.taskqueue.TaskExecutor;
import stroom.util.task.taskqueue.TaskProducer;

import javax.inject.Provider;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

class IndexShardSearchTaskProducer extends TaskProducer {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearchTaskProducer.class);

    static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Index Shard",
            5,
            0,
            Integer.MAX_VALUE);

    private final Queue<IndexShardSearchRunnable> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger tasksRequested = new AtomicInteger();

    private final Tracker tracker;

    IndexShardSearchTaskProducer(final TaskExecutor taskExecutor,
                                 final Receiver receiver,
                                 final List<Long> shards,
                                 final IndexShardQueryFactory queryFactory,
                                 final String[] fieldNames,
                                 final int maxThreadsPerTask,
                                 final ExecutorProvider executorProvider,
                                 final Provider<IndexShardSearchTaskHandler> handlerProvider,
                                 final Tracker tracker) {
        super(taskExecutor, maxThreadsPerTask, executorProvider.getExecutor(THREAD_POOL));
        this.tracker = tracker;

        for (final Long shard : shards) {
            final IndexShardSearchTask task = new IndexShardSearchTask(queryFactory, shard, fieldNames, receiver, tracker);
            final IndexShardSearchRunnable runnable = new IndexShardSearchRunnable(task, handlerProvider);
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

        // Set the total number of index shards we are searching.
        // We set this here so that any errors that might have occurred adding shard search tasks would prevent this producer having work to do.
        setTasksTotal(count);

        // Let consumers know there will be no more tasks.
        finishedAddingTasks();
    }

    @Override
    protected Runnable getNext() {
        IndexShardSearchRunnable task = null;

        if (!Thread.currentThread().isInterrupted()) {
            // If there are no open shards that can be used for any tasks then just get the task at the head of the queue.
            task = taskQueue.poll();
            if (task != null) {
                final int no = tasksRequested.incrementAndGet();
                task.getTask().setShardTotal(getTasksTotal());
                task.getTask().setShardNumber(no);
            }
        }

        checkCompletion();

        return task;
    }

    @Override
    protected void incrementTasksCompleted() {
        super.incrementTasksCompleted();
        checkCompletion();
    }

    private void checkCompletion() {
        if (isComplete()) {
            // Set complete.
            tracker.complete();
        }
    }

    private static class IndexShardSearchRunnable implements Runnable {
        private final IndexShardSearchTask task;
        private final Provider<IndexShardSearchTaskHandler> handlerProvider;

        IndexShardSearchRunnable(final IndexShardSearchTask task, final Provider<IndexShardSearchTaskHandler> handlerProvider) {
            this.task = task;
            this.handlerProvider = handlerProvider;
        }

        @Override
        public void run() {
            final IndexShardSearchTaskHandler handler = handlerProvider.get();
            handler.exec(task);
        }

        public IndexShardSearchTask getTask() {
            return task;
        }
    }
}
