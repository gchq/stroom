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

import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.search.server.ClusterSearchTask;
import stroom.search.server.shard.IndexShardSearchTask.IndexShardQueryFactory;
import stroom.search.server.shard.IndexShardSearchTask.ResultReceiver;
import stroom.search.server.taskqueue.AbstractTaskProducer;
import stroom.util.shared.Severity;

import javax.inject.Provider;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class IndexShardSearchTaskProducer extends AbstractTaskProducer {
    private static final long ONE_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final ClusterSearchTask clusterSearchTask;
    private final IndexShardSearcherCache indexShardSearcherCache;
    private final ErrorReceiver errorReceiver;

    private final Queue<IndexShardSearchRunnable> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger tasksRequested = new AtomicInteger();

    public IndexShardSearchTaskProducer(final ClusterSearchTask clusterSearchTask,
                                        final TransferList<String[]> storedData,
                                        final IndexShardSearcherCache indexShardSearcherCache,
                                        final List<Long> shards,
                                        final IndexShardQueryFactory queryFactory,
                                        final String[] fieldNames,
                                        final ErrorReceiver errorReceiver,
                                        final AtomicLong hitCount,
                                        final int maxThreadsPerTask,
                                        final Provider<IndexShardSearchTaskHandler> handlerProvider) {
        super(maxThreadsPerTask);
        this.clusterSearchTask = clusterSearchTask;
        this.indexShardSearcherCache = indexShardSearcherCache;
        this.errorReceiver = errorReceiver;

        // Create a deque to capture stored data from the index that can be used
        // by coprocessors.
        final ResultReceiver resultReceiver = (shardId, values) -> {
            try {
                boolean stored = false;
                while (!clusterSearchTask.isTerminated() && !stored) {
                    // Loop until item is added or we terminate.
                    stored = storedData.offer(values, ONE_SECOND);
                }
            } catch (final Throwable e) {
                error(e.getMessage(), e);
            }
        };

        getTasksTotal().set(shards.size());
        for (final Long shard : shards) {
            final IndexShardSearchTask task = new IndexShardSearchTask(queryFactory, shard, fieldNames, resultReceiver, errorReceiver, hitCount);
            final IndexShardSearchRunnable runnable = new IndexShardSearchRunnable(task, handlerProvider);
            taskQueue.add(runnable);
        }
    }

    public boolean isComplete() {
        return remainingTasks() == 0;
    }

    public int remainingTasks() {
        return getTasksTotal().get() - getTasksCompleted().get();
    }

    @Override
    protected Runnable getNext() {
        IndexShardSearchRunnable task = null;
        if (!clusterSearchTask.isTerminated()) {
            // First try and get a task that will make use of an open shard.
            for (final IndexShardSearchRunnable t : taskQueue) {
                if (indexShardSearcherCache.isCached(t.getTask().getIndexShardId())) {
                    if (taskQueue.remove(t)) {
                        task = t;
                        break;
                    }
                }
            }

            // If there are no open shards that can be used for any tasks then
            // just get the task at the head of the queue.
            if (task == null) {
                task = taskQueue.poll();
            }
        }

        if (task != null) {
            final int no = tasksRequested.incrementAndGet();
            task.getTask().setShardTotal(getTasksTotal().get());
            task.getTask().setShardNumber(no);
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
