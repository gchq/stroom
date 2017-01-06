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
import stroom.util.shared.Task;

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

    private final Queue<IndexShardSearchTask> taskQueue = new ConcurrentLinkedQueue<IndexShardSearchTask>();
    private final int tasksCreated;
    private final AtomicInteger tasksRequested = new AtomicInteger();
    private final AtomicInteger tasksCompleted = new AtomicInteger();

    public IndexShardSearchTaskProducer(final ClusterSearchTask clusterSearchTask,
                                        final TransferList<String[]> storedData, final IndexShardSearcherCache indexShardSearcherCache,
                                        final List<Long> shards, final IndexShardQueryFactory queryFactory, final String[] fieldNames,
                                        final ErrorReceiver errorReceiver, final AtomicLong hitCount, final int maxThreadsPerTask) {
        super(maxThreadsPerTask);
        this.clusterSearchTask = clusterSearchTask;
        this.indexShardSearcherCache = indexShardSearcherCache;
        this.errorReceiver = errorReceiver;

        // Create a deque to capture stored data from the index that can be used
        // by coprocessors.
        final ResultReceiver resultReceiver = new ResultReceiver() {
            @Override
            public void receive(final long shardId, final String[] values) {
                try {
                    while (!clusterSearchTask.isTerminated() && !storedData.offer(values, ONE_SECOND)) {
                        // Loop until item is added or we terminate.
                    }
                } catch (final Throwable e) {
                    error(e.getMessage(), e);
                }
            }

            @Override
            public void complete(final long shardId) {
                tasksCompleted.incrementAndGet();
            }
        };

        tasksCreated = shards.size();
        for (final Long shard : shards) {
            final IndexShardSearchTask task = new IndexShardSearchTask(clusterSearchTask, queryFactory, shard,
                    fieldNames, resultReceiver, errorReceiver, hitCount);
            taskQueue.add(task);
        }
    }

    public boolean isComplete() {
        return tasksCreated == tasksCompleted.get();
    }

    @Override
    public Task<?> next() {
        IndexShardSearchTask task = null;
        if (!clusterSearchTask.isTerminated()) {
            // First try and get a task that will make use of an open shard.
            for (final IndexShardSearchTask t : taskQueue) {
                if (indexShardSearcherCache.get(t.getIndexShardId()) != null) {
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
            task.setShardTotal(tasksCreated);
            task.setShardNumber(no);
        }

        return task;
    }

    private void error(final String message, final Throwable t) {
        errorReceiver.log(Severity.ERROR, null, null, message, t);
    }
}
