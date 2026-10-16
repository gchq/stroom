/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.processor.impl.dao;

import stroom.processor.impl.EligibleFilters;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorTaskAvailability;
import stroom.processor.impl.ProcessorTaskAvailability.FilterAvailability;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.TaskStatus;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gh-5699 Phase 1. Covers the availability summary against real rows, and the node local cache
 * over it - see PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.2. The query plan the summary depends
 * on is a separate gate, asserted by {@link TestProcessorTaskQueryPlans}.
 */
class TestProcessorTaskAvailability extends AbstractProcessorTest {

    @Test
    void onlyFiltersWithCreatedTasksAreReported() {
        final Processor processor = createProcessor();
        final ProcessorFilter withWork = createProcessorFilter(processor);
        final ProcessorFilter withNoWork = createProcessorFilter(processor);
        final ProcessorFilter notAsked = createProcessorFilter(processor);

        final long oldest = createProcessorTask(withWork, TaskStatus.CREATED, null, FEED);
        createProcessorTask(withWork, TaskStatus.CREATED, null, FEED);
        // Tasks that are not waiting to be claimed must not make a filter look available.
        createProcessorTask(withNoWork, TaskStatus.PROCESSING, NODE1, FEED);
        createProcessorTask(withNoWork, TaskStatus.COMPLETE, NODE1, FEED);
        createProcessorTask(notAsked, TaskStatus.CREATED, null, FEED);

        final Map<Integer, Long> availability = processorTaskDao.getTaskAvailability(
                List.of(withWork.getId(), withNoWork.getId()));

        assertThat(availability)
                .describedAs("filters with no created tasks are absent, and a filter that was not "
                             + "asked about is never reported")
                .containsOnlyKeys(withWork.getId());
        assertThat(availability.get(withWork.getId()))
                .describedAs("the oldest waiting task comes free with the summary")
                .isEqualTo(oldest);
    }

    @Test
    void anEmptyFilterListIsNotAQuery() {
        assertThat(processorTaskDao.getTaskAvailability(List.of()))
                .describedAs("a node eligible for nothing must not ask the database anything")
                .isEmpty();
    }

    @Test
    void filtersWithWorkKeepPriorityOrderAndDropTheRest() {
        final Processor processor = createProcessor();
        final ProcessorFilter first = createProcessorFilter(processor);
        final ProcessorFilter second = createProcessorFilter(processor);
        final ProcessorFilter idle = createProcessorFilter(processor);
        createProcessorTask(first, TaskStatus.CREATED, null, FEED);
        createProcessorTask(second, TaskStatus.CREATED, null, FEED);

        // Eligibility is covered by TestEligibleFilters; here it is just the input set, handed
        // over in the priority order this must preserve.
        final ProcessorTaskAvailability availability = create(
                List.of(first, second, idle), StroomDuration.ZERO);

        assertThat(availability.getFiltersWithWork(Instant.now().toEpochMilli()))
                .extracting(filterAvailability -> filterAvailability.filter().getId())
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void cachedSummaryIsReusedUntilTheIntervalExpires() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final ProcessorFilter laterFilter = createProcessorFilter(processor);
        createProcessorTask(filter, TaskStatus.CREATED, null, FEED);

        final ProcessorTaskAvailability availability = create(
                List.of(filter, laterFilter), StroomDuration.ofSeconds(5));

        final long start = Instant.now().toEpochMilli();
        assertThat(availability.getFiltersWithWork(start))
                .extracting(FilterAvailability::filter)
                .containsExactly(filter);

        // Work appears for a filter that had none. Stale availability costs latency, never
        // correctness, so within the interval the node carries on with what it last saw.
        createProcessorTask(laterFilter, TaskStatus.CREATED, null, FEED);
        assertThat(availability.getFiltersWithWork(start + 4_999))
                .extracting(FilterAvailability::filter)
                .containsExactly(filter);

        assertThat(availability.getFiltersWithWork(start + 5_000))
                .describedAs("the new work is picked up once the summary is refreshed")
                .extracting(FilterAvailability::filter)
                .containsExactly(filter, laterFilter);
    }

    @Test
    void filterThatBecomesEligibleIsPickedUpOnTheNextRefresh() {
        final Processor processor = createProcessor();
        final ProcessorFilter alwaysEligible = createProcessorFilter(processor);
        final ProcessorFilter becomesEligible = createProcessorFilter(processor);
        createProcessorTask(alwaysEligible, TaskStatus.CREATED, null, FEED);
        createProcessorTask(becomesEligible, TaskStatus.CREATED, null, FEED);

        final EligibleFilters eligibleFilters = Mockito.mock(EligibleFilters.class);
        Mockito.when(eligibleFilters.getEligibleFilters(Mockito.any(Instant.class)))
                .thenReturn(List.of(alwaysEligible));
        final ProcessorTaskAvailability availability = create(eligibleFilters, StroomDuration.ofSeconds(5));

        final long start = Instant.now().toEpochMilli();
        assertThat(availability.getFiltersWithWork(start))
                .extracting(FilterAvailability::filter)
                .containsExactly(alwaysEligible);

        // The summary was taken over the old eligible set, so it says nothing about the filter
        // this node has just become eligible for. It is intersected with the current eligible set
        // every time, so the filter appears as soon as a refresh covers it - one interval at most.
        Mockito.when(eligibleFilters.getEligibleFilters(Mockito.any(Instant.class)))
                .thenReturn(List.of(alwaysEligible, becomesEligible));
        assertThat(availability.getFiltersWithWork(start + 1_000))
                .extracting(FilterAvailability::filter)
                .containsExactly(alwaysEligible);
        assertThat(availability.getFiltersWithWork(start + 5_000))
                .extracting(FilterAvailability::filter)
                .containsExactly(alwaysEligible, becomesEligible);
    }

    private ProcessorTaskAvailability create(final List<ProcessorFilter> eligible,
                                             final StroomDuration interval) {
        final EligibleFilters eligibleFilters = Mockito.mock(EligibleFilters.class);
        Mockito.when(eligibleFilters.getEligibleFilters(Mockito.any(Instant.class))).thenReturn(eligible);
        return create(eligibleFilters, interval);
    }

    private ProcessorTaskAvailability create(final EligibleFilters eligibleFilters,
                                             final StroomDuration interval) {
        final ProcessorConfig processorConfig = Mockito.mock(ProcessorConfig.class);
        Mockito.when(processorConfig.getTaskAvailabilityInterval()).thenReturn(interval);
        return new ProcessorTaskAvailability(eligibleFilters, processorTaskDao, () -> processorConfig);
    }
}
