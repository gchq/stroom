/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.processor.impl;

import java.util.concurrent.atomic.AtomicInteger;

public class QueueProcessTasksState {

    private final int initialTotalQueueSize;
    private final int requiredQueueSize;
    private final int halfRequiredQueueSize;

    private final AtomicInteger currentlyQueuedTasks = new AtomicInteger();
    private final AtomicInteger totalQueuedCount = new AtomicInteger();

    public QueueProcessTasksState(final int initialTotalQueueSize,
                                  final int requiredQueueSize) {
        this.initialTotalQueueSize = initialTotalQueueSize;
        this.requiredQueueSize = requiredQueueSize;
        // If a queue is already half full then don't bother adding more
        halfRequiredQueueSize = requiredQueueSize / 2;
    }

    /**
     * Determine if we should keep trying to create tasks for subsequent filters as we will give up if the queue is at
     * least half full.
     *
     * @return True if the queue is less than half the size we would like it to be.
     */
    public boolean keepAddingTasks() {
        return totalQueuedCount.get() < halfRequiredQueueSize;
    }

    /**
     * Get the number of tasks we would still like to create if possible. This is the number of tasks configured as the
     * target queue size minus the number of tasks we have already found or added to the queue as we are examining each
     * associated filter in priority order.
     *
     * @return The number of tasks we would still like to add to the queue.
     */
    public int getRequiredTaskCount() {
        final int requiredTasks = requiredQueueSize - totalQueuedCount.get();
        // because of async processing it is possible to go below zero, but hide that from the caller
        return Math.max(requiredTasks, 0);
    }

    /**
     * Record the number of tasks already queued that could be picked up for processing.
     *
     * @param count The number of tasks currently queued for a filter.
     */
    public void addCurrentlyQueuedTasks(final int count) {
        currentlyQueuedTasks.addAndGet(count);
    }

    /**
     * Record adding tasks that already existed in the database but were unowned being added to the task queue.
     *
     * @param count The number of unowned tasks that were added from the database to the task queue.
     */
    public void addTotalQueuedTasks(final int count) {
        totalQueuedCount.addAndGet(count);
    }

    public void report(final StringBuilder sb) {
        sb.append("Total initial queue size for all filters: ");
        sb.append(initialTotalQueueSize);
        sb.append("\n");
        sb.append("Total initial queue size for considered filters: ");
        sb.append(currentlyQueuedTasks.get());
        sb.append("\n");
        sb.append("Total final queue size for considered filters: ");
        sb.append(totalQueuedCount.get());
    }

    public int getTotalQueuedCount() {
        return totalQueuedCount.get();
    }
}
