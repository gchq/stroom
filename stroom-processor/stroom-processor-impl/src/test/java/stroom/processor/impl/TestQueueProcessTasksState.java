/*
 * Copyright 2025 Crown Copyright
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

import stroom.processor.impl.QueueProcessTasksState.ProfileQueueState;
import stroom.processor.shared.ProcessorFilter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestQueueProcessTasksState {

    private static final int QUEUE_SIZE = 100;

    @Test
    void thereIsOneQueueStatePerProfilePlusOneForFiltersWithNoProfile() {
        final ProcessorFilter alertFilter = filter("alerts");
        final ProcessorFilter bulkFilter = filter("bulk");
        final ProcessorFilter noProfileFilter = filter(null);
        final QueueProcessTasksState state = state(alertFilter, filter("alerts"), bulkFilter, noProfileFilter);

        state.getState(alertFilter).addTotalQueuedTasks(QUEUE_SIZE);

        assertThat(state.getState(bulkFilter).keepAddingTasks()).isTrue();
        assertThat(state.getState(noProfileFilter).keepAddingTasks()).isTrue();
        assertThat(state.getState(filter("alerts")).keepAddingTasks())
                .describedAs("Filters sharing a profile share its queueing budget")
                .isFalse();
    }

    @Test
    void profileStopsBeingConsideredOnceItsQueueIsHalfFull() {
        final ProcessorFilter alertFilter = filter("alerts");
        final QueueProcessTasksState state = state(alertFilter);
        final ProfileQueueState profileQueueState = state.getState(alertFilter);

        profileQueueState.addTotalQueuedTasks((QUEUE_SIZE / 2) - 1);
        assertThat(profileQueueState.keepAddingTasks()).isTrue();

        profileQueueState.addTotalQueuedTasks(1);
        assertThat(profileQueueState.keepAddingTasks()).isFalse();
    }

    @Test
    void eachProfileHasItsOwnRequiredTaskCount() {
        final ProcessorFilter alertFilter = filter("alerts");
        final ProcessorFilter bulkFilter = filter("bulk");
        final QueueProcessTasksState state = state(alertFilter, bulkFilter);

        state.getState(bulkFilter).addTotalQueuedTasks(QUEUE_SIZE);

        assertThat(state.getState(bulkFilter).getRequiredTaskCount()).isZero();
        assertThat(state.getState(alertFilter).getRequiredTaskCount())
                .describedAs("A busy profile doesn't eat into what another profile may queue")
                .isEqualTo(QUEUE_SIZE);
    }

    @Test
    void requiredTaskCountNeverGoesNegative() {
        final ProcessorFilter alertFilter = filter("alerts");
        final QueueProcessTasksState state = state(alertFilter);

        // Async queueing can overshoot, but the caller shouldn't have to deal with that.
        state.getState(alertFilter).addTotalQueuedTasks(QUEUE_SIZE * 2);
        assertThat(state.getState(alertFilter).getRequiredTaskCount()).isZero();
    }

    @Test
    void everyQueueIsFullOnlyWhenNoProfileWantsMore() {
        final ProcessorFilter alertFilter = filter("alerts");
        final ProcessorFilter bulkFilter = filter("bulk");
        final QueueProcessTasksState state = state(alertFilter, bulkFilter);

        state.getState(bulkFilter).addTotalQueuedTasks(QUEUE_SIZE);
        assertThat(state.isEveryQueueFull())
                .describedAs("The alerts profile still needs tasks queueing")
                .isFalse();

        state.getState(alertFilter).addTotalQueuedTasks(QUEUE_SIZE / 2);
        assertThat(state.isEveryQueueFull()).isTrue();
    }

    @Test
    void queuedCountsAreReportedPerProfile() {
        final ProcessorFilter alertFilter = filter("alerts");
        final ProcessorFilter noProfileFilter = filter(null);
        final QueueProcessTasksState state = state(alertFilter, noProfileFilter);

        state.getState(alertFilter).addCurrentlyQueuedTasks(2);
        state.getState(alertFilter).addTotalQueuedTasks(7);
        state.getState(noProfileFilter).addTotalQueuedTasks(3);

        assertThat(state.getTotalQueuedCount()).isEqualTo(10);

        final StringBuilder sb = new StringBuilder();
        state.report(sb);
        assertThat(sb.toString())
                .contains("Total initial queue size for all filters: 50")
                .contains("Total initial queue size for considered filters: 2")
                .contains("Total final queue size for considered filters: 10")
                .contains("Profile queues (100 each): <no profile>=3, alerts=7");
    }

    @Test
    void filterThatWasNotInTheListHasNoQueueState() {
        final QueueProcessTasksState state = state(filter("alerts"));
        assertThatThrownBy(() -> state.getState(filter("added later")))
                .isInstanceOf(NullPointerException.class);
    }

    private QueueProcessTasksState state(final ProcessorFilter... filters) {
        return new QueueProcessTasksState(List.of(filters), 50, QUEUE_SIZE);
    }

    private ProcessorFilter filter(final String profileName) {
        return ProcessorFilter.builder()
                .profileName(profileName)
                .build();
    }
}
