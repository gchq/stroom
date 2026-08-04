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

import stroom.processor.shared.ProcessorFilter;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TestFilterFetchBackoff {

    private static final StroomDuration SKIP_DURATION = StroomDuration.ofSeconds(10);

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final FilterFetchBackoff backoff = new FilterFetchBackoff(now::get);

    @Test
    void filterWeHaveNeverLookedAtIsDueAFetch() {
        assertThat(backoff.isFetchDue(filter(1), SKIP_DURATION)).isTrue();
    }

    @Test
    void filterWeFoundNothingForIsLeftAloneUntilTheWaitIsUp() {
        final ProcessorFilter filter = filter(1);
        backoff.recordEmptyFetch(filter, SKIP_DURATION, backoff.getCreationVersion(filter));

        assertThat(backoff.isFetchDue(filter, SKIP_DURATION)).isFalse();

        now.addAndGet(SKIP_DURATION.toMillis() - 1);
        assertThat(backoff.isFetchDue(filter, SKIP_DURATION)).isFalse();

        now.incrementAndGet();
        assertThat(backoff.isFetchDue(filter, SKIP_DURATION)).isTrue();
    }

    /**
     * The wait has to run from when we looked, not from when the fill started. A fill that
     * considers a lot of filters can take longer than the wait itself, which would leave every
     * filter it backed off due again immediately.
     */
    @Test
    void theWaitRunsFromWhenWeLooked() {
        final ProcessorFilter filter = filter(1);
        final long fillStartMs = now.get();
        // The fill spends longer than the wait getting to this filter.
        now.addAndGet(SKIP_DURATION.toMillis() * 2);
        backoff.recordEmptyFetch(filter, SKIP_DURATION, backoff.getCreationVersion(filter));

        assertThat(now.get() - fillStartMs).isGreaterThan(SKIP_DURATION.toMillis());
        assertThat(backoff.isFetchDue(filter, SKIP_DURATION))
                .describedAs("Backed off from now, not from the start of the fill")
                .isFalse();
    }

    @Test
    void filterThatHadTasksIsDueAFetchAgainStraightAway() {
        final ProcessorFilter filter = filter(1);
        backoff.recordEmptyFetch(filter, SKIP_DURATION, backoff.getCreationVersion(filter));
        assertThat(backoff.isFetchDue(filter, SKIP_DURATION)).isFalse();

        backoff.recordFetchedTasks(filter);

        assertThat(backoff.isFetchDue(filter, SKIP_DURATION)).isTrue();
    }

    @Test
    void filterThatTaskCreationHasJustCreatedTasksForIsDueAFetch() {
        final ProcessorFilter filter = filter(1);
        backoff.recordEmptyFetch(filter, SKIP_DURATION, backoff.getCreationVersion(filter));
        assertThat(backoff.isFetchDue(filter, SKIP_DURATION)).isFalse();

        backoff.recordTasksCreated(filter);

        assertThat(backoff.isFetchDue(filter, SKIP_DURATION)).isTrue();
    }

    /**
     * Task creation runs on a different thread from the fill, so it can create tasks between the
     * fill looking and the fill recording that it found nothing. Backing the filter off then would
     * leave the tasks it has just created sitting unqueued.
     */
    @Test
    void tasksCreatedWhileWeWereLookingStopUsBackingTheFilterOff() {
        final ProcessorFilter filter = filter(1);

        // The fill reads the version, then looks and finds nothing.
        final long creationVersion = backoff.getCreationVersion(filter);
        // Task creation gets in before the fill records what it found.
        backoff.recordTasksCreated(filter);
        backoff.recordEmptyFetch(filter, SKIP_DURATION, creationVersion);

        assertThat(backoff.isFetchDue(filter, SKIP_DURATION))
                .describedAs("There are tasks to queue, whatever the fill saw")
                .isTrue();
    }

    @Test
    void tasksCreatedBeforeWeLookedDoNotStopUsBackingTheFilterOff() {
        final ProcessorFilter filter = filter(1);
        backoff.recordTasksCreated(filter);

        // The fill reads the version after that creation, looks, and finds nothing.
        final long creationVersion = backoff.getCreationVersion(filter);
        backoff.recordEmptyFetch(filter, SKIP_DURATION, creationVersion);

        assertThat(backoff.isFetchDue(filter, SKIP_DURATION)).isFalse();
    }

    @Test
    void nothingIsBackedOffWhenTheWaitIsZero() {
        final ProcessorFilter filter = filter(1);
        backoff.recordEmptyFetch(filter, StroomDuration.ZERO, backoff.getCreationVersion(filter));

        assertThat(backoff.isFetchDue(filter, StroomDuration.ZERO)).isTrue();
        assertThat(backoff.size()).isZero();
    }

    @Test
    void backingOffOneFilterLeavesTheOthersAlone() {
        final ProcessorFilter first = filter(1);
        final ProcessorFilter second = filter(2);
        backoff.recordEmptyFetch(first, SKIP_DURATION, backoff.getCreationVersion(first));

        assertThat(backoff.isFetchDue(first, SKIP_DURATION)).isFalse();
        assertThat(backoff.isFetchDue(second, SKIP_DURATION)).isTrue();
    }

    @Test
    void filtersWeNoLongerConsiderAreForgotten() {
        final ProcessorFilter kept = filter(1);
        final ProcessorFilter dropped = filter(2);
        backoff.recordEmptyFetch(kept, SKIP_DURATION, backoff.getCreationVersion(kept));
        backoff.recordEmptyFetch(dropped, SKIP_DURATION, backoff.getCreationVersion(dropped));
        assertThat(backoff.size()).isEqualTo(2);

        backoff.retainAll(List.of(kept));

        assertThat(backoff.size()).isEqualTo(1);
        assertThat(backoff.isFetchDue(kept, SKIP_DURATION)).isFalse();
        assertThat(backoff.isFetchDue(dropped, SKIP_DURATION))
                .describedAs("Forgotten, so we would look again if it came back")
                .isTrue();
    }

    private ProcessorFilter filter(final int id) {
        return ProcessorFilter.builder()
                .id(id)
                .build();
    }
}
