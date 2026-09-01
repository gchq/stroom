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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTaskCreationBudgets {

    private static final int TASKS_TO_CREATE = 100;

    @Test
    void thereIsOneBudgetPerProfilePlusOneForFiltersWithNoProfile() {
        final TaskCreationBudgets budgets = new TaskCreationBudgets(
                List.of(filter("alerts"), filter("alerts"), filter("bulk"), filter(null)),
                TASKS_TO_CREATE);
        assertThat(budgets.size()).isEqualTo(3);
    }

    @Test
    void filtersWithNoProfileShareOneBudget() {
        final ProcessorFilter first = filter(null);
        final ProcessorFilter second = filter(null);
        final TaskCreationBudgets budgets = new TaskCreationBudgets(
                List.of(first, second), TASKS_TO_CREATE);

        budgets.getBudget(first).getUsed().add(TASKS_TO_CREATE);
        assertThat(budgets.getBudget(second).remaining())
                .describedAs("Any node can process these, so they compete for the same budget")
                .isZero();
    }

    @Test
    void spendingOneProfilesBudgetLeavesTheOthersUntouched() {
        final ProcessorFilter alertFilter = filter("alerts");
        final ProcessorFilter bulkFilter = filter("bulk");
        final ProcessorFilter noProfileFilter = filter(null);
        final TaskCreationBudgets budgets = new TaskCreationBudgets(
                List.of(alertFilter, bulkFilter, noProfileFilter), TASKS_TO_CREATE);

        // A busy profile uses everything it is allowed, and then some, as more than one thread
        // can be creating tasks for it at once.
        budgets.getBudget(bulkFilter).getUsed().add(TASKS_TO_CREATE * 5L);

        assertThat(budgets.getBudget(bulkFilter).remaining()).isNegative();
        assertThat(budgets.getBudget(alertFilter).remaining()).isEqualTo(TASKS_TO_CREATE);
        assertThat(budgets.getBudget(noProfileFilter).remaining()).isEqualTo(TASKS_TO_CREATE);
        assertThat(budgets.isEverySpent())
                .describedAs("Still work to do for the other profiles")
                .isFalse();
    }

    @Test
    void everyBudgetIsSpentOnlyWhenNoProfileHasAnythingLeft() {
        final ProcessorFilter alertFilter = filter("alerts");
        final ProcessorFilter bulkFilter = filter("bulk");
        final TaskCreationBudgets budgets = new TaskCreationBudgets(
                List.of(alertFilter, bulkFilter), TASKS_TO_CREATE);

        budgets.getBudget(bulkFilter).getUsed().add(TASKS_TO_CREATE);
        assertThat(budgets.isEverySpent()).isFalse();

        budgets.getBudget(alertFilter).getUsed().add(TASKS_TO_CREATE - 1);
        assertThat(budgets.isEverySpent())
                .describedAs("One task still allowed for the alerts profile")
                .isFalse();

        budgets.getBudget(alertFilter).getUsed().increment();
        assertThat(budgets.isEverySpent()).isTrue();
    }

    @Test
    void usageIsReportedPerProfile() {
        final ProcessorFilter alertFilter = filter("alerts");
        final ProcessorFilter noProfileFilter = filter(null);
        final TaskCreationBudgets budgets = new TaskCreationBudgets(
                List.of(alertFilter, noProfileFilter), TASKS_TO_CREATE);

        budgets.getBudget(alertFilter).getUsed().add(7);
        budgets.getBudget(noProfileFilter).getUsed().add(3);

        assertThat(budgets.getTotalUsed()).isEqualTo(10);
        assertThat(budgets.describe())
                .isEqualTo("Profile budgets (100 each): <no profile>=3, alerts=7");
    }

    @Test
    void budgetsAreSharedByProfileNotByFilter() {
        final TaskCreationBudgets budgets = new TaskCreationBudgets(
                List.of(filter("alerts")), TASKS_TO_CREATE);
        budgets.getBudget(filter("alerts")).getUsed().add(TASKS_TO_CREATE);
        assertThat(budgets.isEverySpent())
                .describedAs("A different filter object with the same profile shares its budget")
                .isTrue();

        // A profile that wasn't in the filter list can't happen, as the budgets are built from
        // that same list, so fail loudly rather than silently creating tasks with no budget.
        assertThatThrownBy(() -> budgets.getBudget(filter("added later")))
                .isInstanceOf(NullPointerException.class);
    }

    private ProcessorFilter filter(final String profileName) {
        return ProcessorFilter.builder()
                .profileName(profileName)
                .build();
    }
}
