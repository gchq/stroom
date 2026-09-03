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

import stroom.meta.api.MetaService;
import stroom.node.api.NodeInfo;
import stroom.processor.impl.EligibleFilters;
import stroom.processor.impl.FilterFetchBackoff;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorProfileCache;
import stroom.processor.impl.ProcessorTaskAvailability;
import stroom.processor.impl.ProcessorTaskClaimer;
import stroom.processor.impl.ProcessorTaskHeartbeat;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gh-5699 Phase 3, §8: same priority filters must keep getting comparable throughput once nodes
 * claim for themselves.
 * <p>
 * Fairness between equal priorities is <b>emergent, not round-robin</b> (§3.5):
 * {@code HIGHEST_PRIORITY_FIRST_COMPARATOR} breaks ties on the tracker's {@code minMetaId}, so a
 * filter that gets tasks has its watermark advanced by task creation, falls behind its peers in the
 * sort, and a peer goes first next time. This test drives that loop - claim, advance the watermark
 * of whatever produced work, re-sort - and checks the shares come out level.
 * <p>
 * Two properties of the existing design are deliberately preserved rather than improved, and this
 * test is written to show them rather than to hide them:
 * <ul>
 *     <li><b>There is no fairness within a single fetch.</b> A node asking for more than the top
 *     filter can supply drains it first and only then moves on, exactly as the master's queue fill
 *     does. Fairness lives across fetches.</li>
 *     <li><b>The ordering is only as fresh as {@code PrioritisedFilters}</b>, which refreshes every
 *     10s, so in production the re-sort below happens on that cadence rather than per fetch.</li>
 * </ul>
 */
class TestProcessorTaskClaimFairness extends AbstractProcessorTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProcessorTaskClaimFairness.class);

    private static final int FILTER_COUNT = 3;
    private static final int TASKS_PER_FILTER = 30;
    private static final int ROUNDS = 12;
    private static final int TASKS_PER_ROUND = 5;

    @Test
    void samePriorityFiltersGetComparableThroughput() {
        final Processor processor = createProcessor();
        final List<ProcessorFilter> filters = new ArrayList<>();
        for (int i = 0; i < FILTER_COUNT; i++) {
            final ProcessorFilter filter = createProcessorFilter(processor);
            filters.add(filter);
            for (int t = 0; t < TASKS_PER_FILTER; t++) {
                createProcessorTask(filter, TaskStatus.CREATED, null, FEED);
            }
        }

        final Map<Integer, Integer> claimedByFilter = new HashMap<>();
        final Map<Integer, Long> watermarks = new HashMap<>();
        filters.forEach(filter -> watermarks.put(filter.getId(), 0L));

        for (int round = 0; round < ROUNDS; round++) {
            // What PrioritisedFilters would hand a node: same priority, so ordered by how far each
            // filter's task creation has got - least advanced first.
            final List<ProcessorFilter> prioritised = filters
                    .stream()
                    .map(filter -> withWatermark(filter, watermarks.get(filter.getId())))
                    .sorted(ProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR)
                    .toList();

            final List<ProcessorTask> claimed = claimer(prioritised).claimTasks(TASKS_PER_ROUND);
            assertThat(claimed).hasSize(TASKS_PER_ROUND);

            claimed.forEach(task -> {
                final int filterId = task.getProcessorFilter().getId();
                claimedByFilter.merge(filterId, 1, Integer::sum);
                // Task creation advances the watermark as it produces work for a filter; this is
                // what eventually moves a filter down the order.
                watermarks.merge(filterId, 1L, Long::sum);
            });
        }

        LOGGER.info("Tasks claimed per filter: {}", claimedByFilter);

        final int total = ROUNDS * TASKS_PER_ROUND;
        final int expectedShare = total / FILTER_COUNT;
        assertThat(claimedByFilter.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(total);
        assertThat(claimedByFilter)
                .describedAs("every same priority filter must get served, not just the first")
                .hasSize(FILTER_COUNT);
        claimedByFilter.forEach((filterId, count) ->
                assertThat(count)
                        .describedAs("filter %s should get roughly its share (%s of %s)",
                                filterId, expectedShare, total)
                        .isBetween((int) (expectedShare * 0.6), (int) Math.ceil(expectedShare * 1.4)));
    }

    /**
     * A copy of the filter whose tracker says task creation has got this far, which is what the
     * comparator ties on.
     */
    private ProcessorFilter withWatermark(final ProcessorFilter filter, final long minMetaId) {
        final ProcessorFilterTracker tracker = new ProcessorFilterTracker();
        tracker.setId(filter.getProcessorFilterTracker().getId());
        tracker.setMinMetaId(minMetaId);
        return filter.copy().processorFilterTracker(tracker).build();
    }

    private ProcessorTaskClaimer claimer(final List<ProcessorFilter> eligible) {
        final EligibleFilters eligibleFilters = Mockito.mock(EligibleFilters.class);
        Mockito.when(eligibleFilters.getEligibleFilters(Mockito.any(Instant.class))).thenReturn(eligible);

        final ProcessorConfig processorConfig = Mockito.mock(ProcessorConfig.class);
        Mockito.when(processorConfig.getTaskAvailabilityInterval()).thenReturn(StroomDuration.ZERO);
        Mockito.when(processorConfig.getSkipEmptyFilterFetchDuration()).thenReturn(StroomDuration.ZERO);

        final MetaService metaService = Mockito.mock(MetaService.class);
        Mockito.when(metaService.findLockedMeta(Mockito.anyList())).thenReturn(Set.of());

        final NodeInfo nodeInfo = getInjector().getInstance(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(NODE1);

        return new ProcessorTaskClaimer(
                new ProcessorTaskAvailability(eligibleFilters, processorTaskDao, () -> processorConfig),
                processorTaskDao,
                Mockito.mock(ProcessorProfileCache.class),
                new ProcessorTaskHeartbeat(
                        processorTaskDao, nodeInfo, Mockito.mock(TaskManager.class), ProcessorConfig::new),
                new FilterFetchBackoff(),
                metaService,
                nodeInfo,
                getInjector().getInstance(SecurityContext.class),
                () -> processorConfig);
    }
}
