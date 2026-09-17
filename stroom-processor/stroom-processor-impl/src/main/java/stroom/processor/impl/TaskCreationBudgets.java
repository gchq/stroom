/*
 * Copyright 2026 Crown Copyright
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
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * How many tasks a single task creation run is allowed to create, held separately for each
 * processing profile.
 * <p>
 * Filters that share a processing profile are processed by the same group of nodes, so the tasks
 * created for them are interchangeable, but tasks created for one profile are of no use to another
 * profile's nodes. A single budget shared by every filter would therefore let a busy profile use
 * the whole budget on every run, so tasks are never created for another profile's filters and its
 * nodes have nothing they are allowed to process. Giving each profile its own budget keeps the
 * short circuit that stops us walking every filter on a busy cluster, while making sure every
 * group of nodes has work created for it.
 * </p>
 * <p>
 * Filters with no profile share one budget of their own; they can be processed by any node.
 * </p>
 */
public class TaskCreationBudgets {

    private static final String NO_PROFILE_LABEL = "<no profile>";

    // Fully populated on construction and only read after that, so it is safe to share between
    // the task creation threads. Null keys are allowed and mean 'no profile'.
    private final Map<String, Budget> budgets = new HashMap<>();
    private final int tasksToCreate;

    /**
     * @param filters       All the filters this run will consider.
     * @param tasksToCreate The number of tasks each profile is allowed to create this run.
     */
    public TaskCreationBudgets(final List<ProcessorFilter> filters, final int tasksToCreate) {
        this.tasksToCreate = tasksToCreate;
        filters.forEach(filter ->
                budgets.computeIfAbsent(filter.getProfileName(), profileName -> new Budget()));
    }

    /**
     * @return The budget that this filter's task creation counts against.
     */
    public Budget getBudget(final ProcessorFilter filter) {
        final Budget budget = budgets.get(filter.getProfileName());
        Objects.requireNonNull(budget, () ->
                "No budget for filter " + filter.getFilterInfo() + ", it was not in the filter list");
        return budget;
    }

    /**
     * @return True if every profile has used up its budget, i.e. there is no point looking at any
     * more filters this run.
     */
    public boolean isEverySpent() {
        return budgets.values()
                .stream()
                .allMatch(budget -> budget.remaining() <= 0);
    }

    /**
     * @return The number of budgets, i.e. the number of profiles in use plus one if any filters
     * have no profile.
     */
    public int size() {
        return budgets.size();
    }

    public long getTotalUsed() {
        return budgets.values()
                .stream()
                .mapToLong(budget -> budget.used.sum())
                .sum();
    }

    /**
     * @return A description of how much of each profile's budget was used, so that the cost of
     * having many profiles is visible in the report rather than having to be inferred.
     */
    public String describe() {
        final String usage = budgets.entrySet()
                .stream()
                .sorted(Entry.comparingByKey(Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(entry -> Objects.requireNonNullElse(entry.getKey(), NO_PROFILE_LABEL)
                              + "=" + entry.getValue().used.sum())
                .collect(Collectors.joining(", "));
        return "Profile budgets (" + tasksToCreate + " each): " + usage;
    }

    @Override
    public String toString() {
        return describe();
    }


    // --------------------------------------------------------------------------------


    public class Budget {

        private final LongAdder used = new LongAdder();

        /**
         * @return The tasks that may still be created against this budget. Task creation adds to
         * the count itself, so this can go negative; with more than one creation thread running we
         * may create a lot more tasks than the budget allows, which is accepted.
         */
        public int remaining() {
            return tasksToCreate - used.intValue();
        }

        /**
         * @return The count that task creation adds to as it goes.
         */
        public LongAdder getUsed() {
            return used;
        }

        @Override
        public String toString() {
            return used.sum() + "/" + tasksToCreate;
        }
    }
}
