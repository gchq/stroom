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

import stroom.util.shared.Task;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractTaskProducer implements TaskProducer {
    private final long now = System.currentTimeMillis();

    private final AtomicInteger threadsUsed = new AtomicInteger();
    private final int maxThreadsPerTask;

    private final AtomicInteger tasksTotal = new AtomicInteger();
    private final AtomicInteger tasksCompleted = new AtomicInteger();

    public AbstractTaskProducer(final int maxThreadsPerTask) {
        this.maxThreadsPerTask = maxThreadsPerTask;
    }

    @Override
    public final Task<?> next() {
        Task<?> task = null;

        final int count = threadsUsed.incrementAndGet();
        if (count > maxThreadsPerTask) {
            threadsUsed.decrementAndGet();
        } else {
            task = getNext();
            if (task == null) {
                threadsUsed.decrementAndGet();
            }
        }

        return task;
    }

    @Override
    public final void complete(final Task<?> task) {
        threadsUsed.decrementAndGet();
        tasksCompleted.incrementAndGet();
    }

    protected abstract Task<?> getNext();

    protected final AtomicInteger getTasksTotal() {
        return tasksTotal;
    }

    protected final AtomicInteger getTasksCompleted() {
        return tasksCompleted;
    }

    @Override
    public int compareTo(final TaskProducer o) {
        return Long.compare(now, ((AbstractTaskProducer) o).now);
    }
}
