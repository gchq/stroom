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

package stroom.search.server.shard;

import stroom.dashboard.expression.v1.Val;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.search.server.ClusterSearchTask;
import stroom.search.server.shard.IndexShardSearchTask.IndexShardQueryFactory;
import stroom.search.server.shard.IndexShardSearchTask.ResultReceiver;
import stroom.search.server.taskqueue.TaskExecutor;
import stroom.search.server.taskqueue.TaskProducer;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;
import stroom.util.shared.ThreadPool;

import javax.inject.Provider;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class IndexShardSearchTaskProducer extends TaskProducer {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearchTaskProducer.class);

    static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Search Index Shard", 5, 0, Integer.MAX_VALUE);

    private final ClusterSearchTask clusterSearchTask;
    private final ErrorReceiver errorReceiver;

    private final Queue<IndexShardSearchRunnable> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger tasksRequested = new AtomicInteger();

    public IndexShardSearchTaskProducer(final TaskExecutor taskExecutor,
                                        final ClusterSearchTask clusterSearchTask,
                                        final LinkedBlockingQueue<Val[]> storedData,
                                        final List<Long> shards,
                                        final IndexShardQueryFactory queryFactory,
                                        final String[] fieldNames,
                                        final ErrorReceiver errorReceiver,
                                        final AtomicLong hitCount,
                                        final int maxThreadsPerTask,
                                        final ExecutorProvider executorProvider,
                                        final Provider<IndexShardSearchTaskHandler> handlerProvider) {
        super(taskExecutor, maxThreadsPerTask, executorProvider.getExecutor(THREAD_POOL));
        this.clusterSearchTask = clusterSearchTask;
        this.errorReceiver = errorReceiver;

        // Create a deque to capture stored data from the index that can be used by coprocessors.
        final ResultReceiver resultReceiver = (shardId, values) -> {
            try {
                boolean stored = false;
                while (!clusterSearchTask.isTerminated() && !stored) {
                    // Loop until item is added or we terminate.
                    stored = storedData.offer(values, 1, TimeUnit.SECONDS);
                }
            } catch (final InterruptedException e) {
                // Continue to interrupt.
                Thread.currentThread().interrupt();

                error(e.getMessage(), e);
            } catch (final RuntimeException e) {
                error(e.getMessage(), e);
            }
        };

        getTasksTotal().set(shards.size());
        for (final Long shard : shards) {
            final IndexShardSearchTask task = new IndexShardSearchTask(queryFactory, shard, fieldNames, resultReceiver, errorReceiver, hitCount);
            final IndexShardSearchRunnable runnable = new IndexShardSearchRunnable(task, handlerProvider);
            taskQueue.add(runnable);
        }
        LOGGER.debug(() -> String.format("Queued %s index shard search tasks", shards.size()));

        // Attach to the supplied executor.
        attach();

        // Tell the supplied executor that we are ready to deliver tasks.
        signalAvailable();
    }

    @Override
    public boolean isComplete() {
        return clusterSearchTask.isTerminated() || super.isComplete();
    }

    @Override
    protected Runnable getNext() {
        IndexShardSearchRunnable task = null;

        if (clusterSearchTask.isTerminated()) {
            // Drain the queue and increment the complete task count.
            while (taskQueue.poll() != null) {
                getTasksCompleted().getAndIncrement();
            }
        } else {
            // If there are no open shards that can be used for any tasks then just get the task at the head of the queue.
            task = taskQueue.poll();
            if (task != null) {
                final int no = tasksRequested.incrementAndGet();
                task.getTask().setShardTotal(getTasksTotal().get());
                task.getTask().setShardNumber(no);
            }
        }

        return task;
    }

    private void error(final String message, final Throwable t) {
        errorReceiver.log(Severity.ERROR, null, null, message, t);
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
