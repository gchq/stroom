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
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskHeartbeat;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskManager;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gh-5699 Phase 2. What a worker node does with the tasks it can see: takes them highest priority
 * first, respects the limits on how many may run at once, and hands back anything it cannot
 * actually run. See PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.3.
 */
class TestProcessorTaskClaimer extends AbstractProcessorTest {

    @Test
    void claimsFromFiltersWithWorkHighestPriorityFirst() {
        final Processor processor = createProcessor();
        final ProcessorFilter first = createProcessorFilter(processor);
        final ProcessorFilter second = createProcessorFilter(processor);
        final List<Long> firstTasks = createTasks(first, 2);
        final List<Long> secondTasks = createTasks(second, 2);
        final ProcessorTaskClaimer claimer = claimer(List.of(first, second));

        final List<ProcessorTask> claimed = claimer.claimTasks(3);

        assertThat(claimed).extracting(ProcessorTask::getId)
                .describedAs("the first filter is drained before the next is asked")
                .containsExactly(firstTasks.get(0), firstTasks.get(1), secondTasks.get(0));
        assertThat(getTaskStatus(secondTasks.get(1)))
                .describedAs("only as many as were asked for are taken")
                .isEqualTo(TaskStatus.CREATED);
    }

    @Test
    void tasksWhoseMetaIsStillLockedAreGivenStraightBack() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final List<Long> tasks = createTasks(filter, 3);
        // createProcessorTask gives every task the same meta id, so lock it and nothing is runnable.
        final MetaService metaService = Mockito.mock(MetaService.class);
        Mockito.when(metaService.findLockedMeta(Mockito.anyList())).thenReturn(Set.of(123L));
        final ProcessorTaskClaimer claimer = claimer(List.of(filter), metaService, null);

        final List<ProcessorTask> claimed = claimer.claimTasks(3);

        assertThat(claimed)
                .describedAs("a task whose stream is still being written cannot be processed yet")
                .isEmpty();
        tasks.forEach(taskId -> {
            assertThat(getTaskStatus(taskId))
                    .describedAs("and must go back, not sit in PROCESSING until its lease expires")
                    .isEqualTo(TaskStatus.CREATED);
            assertThat(getTaskNodeId(taskId)).isNull();
        });
    }

    @Test
    void filtersOwnProcessingLimitCapsWhatIsClaimed() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = processorFilterDao.update(
                createProcessorFilter(processor).copy().maxProcessingTasks(2).build());
        createTasks(filter, 5);
        final ProcessorTaskClaimer claimer = claimer(List.of(filter));

        assertThat(claimer.claimTasks(5))
                .describedAs("the limit is on tasks running at once, wherever they are running")
                .hasSize(2);

        // Now the cluster is at the limit, so there is nothing more to take. The count is cached
        // very briefly, so a fresh claimer is what a later fetch would see.
        assertThat(claimer(List.of(filter)).claimTasks(5)).isEmpty();
    }

    @Test
    void backToBackClaimsCannotSpendTheSameClusterCountTwice() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = processorFilterDao.update(
                createProcessorFilter(processor).copy().maxProcessingTasks(2).build());
        createTasks(filter, 5);
        final ProcessorTaskClaimer claimer = claimer(List.of(filter));

        assertThat(claimer.claimTasks(2)).hasSize(2);

        // The cluster count behind the limit is cached for a second, and the same node asking
        // again inside that window is the ordinary case - a fetch is single flight per node, but
        // nothing spaces successive fetches out.
        assertThat(claimer.claimTasks(2))
                .describedAs("a count taken before a claim must not be spent again by the next one")
                .isEmpty();
        assertThat(processorTaskDao.countTasksForFilter(filter.getId(), TaskStatus.PROCESSING))
                .describedAs("so the filter's own limit holds for one node claiming repeatedly, "
                             + "not just across nodes")
                .isEqualTo(2);
    }

    @Test
    void capacityFreedByThisNodesOwnTasksIsSeenWithoutWaitingForTheCountToExpire() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = processorFilterDao.update(
                createProcessorFilter(processor).copy().maxProcessingTasks(2).build());
        // This node is already running both of the tasks the filter allows, with more waiting.
        final List<Long> running = List.of(
                createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED),
                createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED));
        createTasks(filter, 2);
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        running.forEach(taskId -> heartbeat.register(taskId, filter.getId(), null));
        final ProcessorTaskClaimer claimer = claimer(List.of(filter), null, heartbeat);

        assertThat(claimer.claimTasks(2))
                .describedAs("the cluster is at the filter's limit, so there is nothing to take")
                .isEmpty();

        // Those two are done with, which the registry knows about the moment it happens.
        processorTaskDao.releaseTasks(Set.copyOf(running), TaskStatus.PROCESSING);
        running.forEach(heartbeat::deregister);

        assertThat(claimer.claimTasks(2))
                .describedAs("the cached count is corrected by what this node has since finished, "
                             + "rather than the node idling until it expires")
                .hasSize(2);
    }

    @Test
    void taskFinishingWhileTheCountIsInFlightIsNotSubtractedTwice() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = processorFilterDao.update(
                createProcessorFilter(processor).copy().maxProcessingTasks(2).build());
        final long running = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED);
        createTasks(filter, 3);
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        heartbeat.register(running, filter.getId(), null);

        // The task this node was running finishes while the cluster count query is in flight, so
        // the count comes back without it. The registry baseline the count is carried forward from
        // has to be taken on the same side of that, or this node's one task is subtracted twice.
        final ProcessorTaskDao dao = Mockito.spy(processorTaskDao);
        Mockito.doAnswer(invocation -> {
            processorTaskDao.releaseTasks(Set.of(running), TaskStatus.PROCESSING);
            heartbeat.deregister(running);
            return invocation.callRealMethod();
        }).when(dao).countTasksForFilter(filter.getId(), TaskStatus.PROCESSING);
        final ProcessorTaskClaimer claimer = claimer(List.of(filter), null, heartbeat, dao);

        assertThat(claimer.claimTasks(2)).hasSize(2);

        assertThat(claimer.claimTasks(2))
                .describedAs("the filter is at its limit, whatever happened during the query")
                .isEmpty();
        assertThat(processorTaskDao.countTasksForFilter(filter.getId(), TaskStatus.PROCESSING))
                .isEqualTo(2);
    }

    @Test
    void abandonedTasksGoBackForAnotherNode() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        createTasks(filter, 2);
        final ProcessorTaskClaimer claimer = claimer(List.of(filter));
        final List<ProcessorTask> claimed = claimer.claimTasks(2);

        assertThat(claimer.abandonTasks(claimed)).isEqualTo(2);

        claimed.forEach(task -> {
            assertThat(getTaskStatus(task.getId())).isEqualTo(TaskStatus.CREATED);
            assertThat(getTaskNodeId(task.getId())).isNull();
        });
    }

    @Test
    void claimedTasksHeartbeatBeforeTheyStartRunning() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        createTasks(filter, 2);
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        claimer(List.of(filter), null, heartbeat).claimTasks(2);

        // A task is owned from the moment it is claimed, so from that moment a gap in heartbeats
        // has to mean this node has died - otherwise the reaper would take work this node is about
        // to start.
        assertThat(heartbeat.size()).isEqualTo(2);
        assertThat(heartbeat.countForFilter(filter.getId())).isEqualTo(2);
    }

    private ProcessorTaskClaimer claimer(final List<ProcessorFilter> eligible) {
        return claimer(eligible, null, null);
    }

    private ProcessorTaskClaimer claimer(final List<ProcessorFilter> eligible,
                                         final MetaService metaService,
                                         final ProcessorTaskHeartbeat heartbeat) {
        return claimer(eligible, metaService, heartbeat, processorTaskDao);
    }

    private ProcessorTaskClaimer claimer(final List<ProcessorFilter> eligible,
                                         final MetaService metaService,
                                         final ProcessorTaskHeartbeat heartbeat,
                                         final ProcessorTaskDao dao) {
        final EligibleFilters eligibleFilters = Mockito.mock(EligibleFilters.class);
        Mockito.when(eligibleFilters.getEligibleFilters(Mockito.any(Instant.class))).thenReturn(eligible);

        final ProcessorConfig processorConfig = Mockito.mock(ProcessorConfig.class);
        // No availability caching and no fetch backoff, so each test sees exactly what it set up.
        Mockito.when(processorConfig.getTaskAvailabilityInterval()).thenReturn(StroomDuration.ZERO);
        Mockito.when(processorConfig.getSkipEmptyFilterFetchDuration()).thenReturn(StroomDuration.ZERO);

        final ProcessorTaskAvailability availability = new ProcessorTaskAvailability(
                eligibleFilters, dao, () -> processorConfig);

        final MetaService meta = metaService != null
                ? metaService
                : Mockito.mock(MetaService.class);
        if (metaService == null) {
            Mockito.when(meta.findLockedMeta(Mockito.anyList())).thenReturn(Set.of());
        }

        final NodeInfo nodeInfo = getInjector().getInstance(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(NODE1);

        return new ProcessorTaskClaimer(
                availability,
                dao,
                Mockito.mock(ProcessorProfileCache.class),
                heartbeat != null
                        ? heartbeat
                        : heartbeat(),
                new FilterFetchBackoff(),
                meta,
                nodeInfo,
                getInjector().getInstance(SecurityContext.class),
                () -> processorConfig);
    }

    private ProcessorTaskHeartbeat heartbeat() {
        final NodeInfo nodeInfo = getInjector().getInstance(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(NODE1);
        return new ProcessorTaskHeartbeat(
                processorTaskDao, nodeInfo, Mockito.mock(TaskManager.class), ProcessorConfig::new);
    }

    private List<Long> createTasks(final ProcessorFilter filter, final int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> createProcessorTask(filter, TaskStatus.CREATED, null, FEED))
                .toList();
    }
}
