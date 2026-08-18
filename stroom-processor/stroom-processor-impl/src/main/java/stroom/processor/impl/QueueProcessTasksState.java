/*
 * Copyright 2023 Crown Copyright
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

import stroom.processor.shared.ProcessorFilter;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * How many tasks a single fill of the task queue is allowed to queue, held separately for each
 * processing profile.
 * <p>
 * Filters that share a processing profile are processed by the same group of nodes, so the tasks
 * queued for them are interchangeable, but tasks queued for one profile are of no use to another
 * profile's nodes. A single budget shared by every filter would therefore let a busy profile fill
 * the queue on every pass, so tasks are never queued for another profile's filters and its nodes
 * ask for work and get nothing. Giving each profile its own budget keeps the short circuit that
 * stops us considering every filter, while making sure every group of nodes has work queued for it.
 * </p>
 * <p>
 * Filters with no profile share one budget of their own; they can be processed by any node.
 * </p>
 */
public class QueueProcessTasksState {

    private static final String NO_PROFILE_LABEL = "<no profile>";

    private final int initialTotalQueueSize;
    private final int requiredQueueSize;
    private final int halfRequiredQueueSize;

    // Fully populated on construction and only read after that, so it is safe to share between
    // threads. Null keys are allowed and mean 'no profile'.
    private final Map<String, ProfileQueueState> profileStates = new HashMap<>();

    /**
     * @param filters               All the filters this fill will consider.
     * @param initialTotalQueueSize The number of tasks queued for all filters before this fill.
     * @param requiredQueueSize     The number of tasks each profile would like to have queued.
     */
    public QueueProcessTasksState(final List<ProcessorFilter> filters,
                                  final int initialTotalQueueSize,
                                  final int requiredQueueSize) {
        this.initialTotalQueueSize = initialTotalQueueSize;
        this.requiredQueueSize = requiredQueueSize;
        // If a queue is already half full then don't bother adding more
        this.halfRequiredQueueSize = requiredQueueSize / 2;
        filters.forEach(filter ->
                profileStates.computeIfAbsent(filter.getProfileName(), profileName -> new ProfileQueueState()));
    }

    /**
     * @return The queueing state that this filter's tasks count against.
     */
    public ProfileQueueState getState(final ProcessorFilter filter) {
        final ProfileQueueState state = profileStates.get(filter.getProfileName());
        Objects.requireNonNull(state, () ->
                "No queueing state for filter " + filter.getFilterInfo() + ", it was not in the filter list");
        return state;
    }

    /**
     * @return True if every profile has enough tasks queued, i.e. there is no point looking at any
     * more filters on this pass.
     */
    public boolean isEveryQueueFull() {
        return profileStates.values()
                .stream()
                .noneMatch(ProfileQueueState::keepAddingTasks);
    }

    public int getTotalQueuedCount() {
        return profileStates.values()
                .stream()
                .mapToInt(state -> state.totalQueuedCount.get())
                .sum();
    }

    private int getTotalCurrentlyQueuedCount() {
        return profileStates.values()
                .stream()
                .mapToInt(state -> state.currentlyQueuedTasks.get())
                .sum();
    }

    public void report(final StringBuilder sb) {
        sb.append("Total initial queue size for all filters: ");
        sb.append(initialTotalQueueSize);
        sb.append("\n");
        sb.append("Total initial queue size for considered filters: ");
        sb.append(getTotalCurrentlyQueuedCount());
        sb.append("\n");
        sb.append("Total final queue size for considered filters: ");
        sb.append(getTotalQueuedCount());
        sb.append("\n");
        // Shown per profile so that the cost of having many profiles, and any profile that never
        // gets its share, are both visible rather than having to be inferred.
        sb.append("Profile queues (");
        sb.append(requiredQueueSize);
        sb.append(" each): ");
        sb.append(describeProfileQueues());
    }

    private String describeProfileQueues() {
        return profileStates.entrySet()
                .stream()
                .sorted(Entry.comparingByKey(Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(entry -> Objects.requireNonNullElse(entry.getKey(), NO_PROFILE_LABEL)
                              + "=" + entry.getValue().totalQueuedCount.get())
                .collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return describeProfileQueues();
    }


    // --------------------------------------------------------------------------------


    public class ProfileQueueState {

        private final AtomicInteger currentlyQueuedTasks = new AtomicInteger();
        private final AtomicInteger totalQueuedCount = new AtomicInteger();

        /**
         * Determine if we should keep trying to queue tasks for subsequent filters of this profile
         * as we will give up if its queues are at least half full.
         *
         * @return True if this profile has less than half the queued tasks we would like it to have.
         */
        public boolean keepAddingTasks() {
            return totalQueuedCount.get() < halfRequiredQueueSize;
        }

        /**
         * Get the number of tasks we would still like to queue for this profile if possible. This is
         * the number of tasks configured as the target queue size minus the number of tasks we have
         * already found or added to the queue as we are examining each associated filter in priority
         * order.
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
         * Record adding tasks that already existed in the database but were unowned being added to
         * the task queue.
         *
         * @param count The number of unowned tasks that were added from the database to the task queue.
         */
        public void addTotalQueuedTasks(final int count) {
            totalQueuedCount.addAndGet(count);
        }

        @Override
        public String toString() {
            return totalQueuedCount.get() + "/" + requiredQueueSize;
        }
    }
}
