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

package stroom.processor.impl;

import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.meta.api.MetaService;
import stroom.node.api.NodeInfo;
import stroom.processor.impl.ProcessorProfileCache.ProfileResult;
import stroom.processor.impl.ProcessorTaskDao.FilterTaskCounts;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.shared.TaskId;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the filling, assigning and releasing of the processor task queue.
 * <p>
 * The DAO is backed by a small in memory model rather than plain canned responses so that the CREATED to QUEUED
 * to CREATED lifecycle of a task behaves as it does in the database, i.e. a queued task is no longer available
 * to be queued again until it has been released.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestProcessorTaskQueueManagerImpl {

    private static final String NODE = "node1";
    private static final String OTHER_NODE = "node2";
    private static final String PROFILE = "profile1";
    private static final String OTHER_PROFILE = "profile2";
    private static final ProfileResult UNLIMITED =
            new ProfileResult(Integer.MAX_VALUE, Integer.MAX_VALUE);
    private static final ProfileResult NONE = new ProfileResult(0, 0);

    @Mock
    private ProcessorTaskDao processorTaskDao;
    @Mock
    private ExecutorProvider executorProvider;
    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private MetaService metaService;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private TargetNodeSetFactory targetNodeSetFactory;
    @Mock
    private PrioritisedFilters prioritisedFilters;
    @Mock
    private ProcessorProfileCache processorProfileCache;
    @Mock
    private InternalStatisticsReceiver internalStatisticsReceiver;

    private ProcessorTaskQueueManagerImpl queueManager;

    private final TestProcessorConfig processorConfig = new TestProcessorConfig();
    private final FilterFetchBackoff filterFetchBackoff = new FilterFetchBackoff();

    /**
     * Asynchronous queue fills are discarded by default so that tests only see the synchronous filling they
     * are exercising. Call {@link #runAsyncFillsInline()} to exercise the asynchronous path instead.
     */
    private volatile Executor asyncFillExecutor = command -> {
    };
    /**
     * The number of times we have been asked for created tasks, i.e. how many queue fills have done work.
     */
    private final AtomicInteger fetchCount = new AtomicInteger();

    private final AtomicLong taskIdSequence = new AtomicLong();
    /**
     * Every task we have created, by task id. The meta id of a task is the same as its task id.
     */
    private final Map<Long, ProcessorTask> tasksById = new HashMap<>();
    /**
     * The task ids that exist for each filter, in the order they were created.
     */
    private final Map<Integer, List<Long>> taskIdsByFilter = new HashMap<>();
    /**
     * The task ids that are currently QUEUED rather than CREATED.
     */
    private final Set<Long> queuedTaskIds = new HashSet<>();
    /**
     * Meta ids that are locked, so their tasks cannot be queued.
     */
    private final Set<Long> lockedMetaIds = new HashSet<>();

    @BeforeEach
    void setUp() throws Exception {
        when(nodeInfo.getThisNodeName()).thenReturn(NODE);
        when(securityContext.isProcessingUser()).thenReturn(true);
        // Run anything submitted as the processing user inline so the test is deterministic.
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(securityContext).asProcessingUser(any(Runnable.class));
        when(securityContext.asProcessingUserResult(any(Supplier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());
        when(executorProvider.get(any())).thenReturn(command -> asyncFillExecutor.execute(command));
        when(targetNodeSetFactory.getEnabledTargetNodeSet()).thenReturn(Set.of(NODE));
        when(processorTaskDao.releaseOwnedTasks(anyString())).thenReturn(0L);

        // Only CREATED tasks that we haven't already queued are available to queue.
        when(processorTaskDao.findExistingCreatedTasks(anyLong(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    fetchCount.incrementAndGet();
                    return findCreatedTasks(
                            invocation.getArgument(0),
                            invocation.getArgument(1),
                            invocation.getArgument(2));
                });
        // Queueing moves tasks from CREATED to QUEUED and returns them.
        when(processorTaskDao.queueTasks(anySet(), anyString())).thenAnswer(invocation -> {
            final Set<Long> ids = invocation.getArgument(0);
            queuedTaskIds.addAll(ids);
            return ids.stream().sorted().map(tasksById::get).toList();
        });
        // Releasing moves tasks back to CREATED so they can be queued again.
        when(processorTaskDao.releaseTasks(anySet(), any())).thenAnswer(invocation -> {
            final Set<Long> ids = invocation.getArgument(0);
            queuedTaskIds.removeAll(ids);
            return ids.size();
        });
        when(metaService.findLockedMeta(any())).thenAnswer(invocation -> {
            final Collection<Long> metaIds = invocation.getArgument(0);
            return metaIds.stream().filter(lockedMetaIds::contains).collect(java.util.stream.Collectors.toSet());
        });

        queueManager = new ProcessorTaskQueueManagerImpl(
                processorTaskDao,
                executorProvider,
                new TestTaskContextFactory(),
                nodeInfo,
                () -> processorConfig,
                () -> internalStatisticsReceiver,
                metaService,
                securityContext,
                targetNodeSetFactory,
                prioritisedFilters,
                processorProfileCache,
                filterFetchBackoff);
        queueManager.startup();
    }

    // --------------------------------------------------------------------------------
    // Filling the queue
    // --------------------------------------------------------------------------------

    @Test
    void filterWithNoProfileIsQueued() {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);

        queueManager.exec();

        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);
    }

    @Test
    void tasksWithLockedMetaAreNotQueued() {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        final List<Long> ids = givenCreatedTasks(filter, 10);
        // Lock the meta for the first three tasks.
        lockedMetaIds.addAll(ids.subList(0, 3));

        queueManager.exec();

        assertThat(queueManager.getTaskQueueSize()).isEqualTo(7);
    }

    @Test
    void queueingStopsOnceTheConfiguredQueueSizeIsReached() {
        // The first filter alone provides more than the configured queue size so the second is not considered.
        final ProcessorFilter first = createFilter(1, null);
        final ProcessorFilter second = createFilter(2, null);
        givenFilters(first, second);
        givenCreatedTasks(first, new ProcessorConfig().getQueueSize());
        givenCreatedTasks(second, 10);

        queueManager.exec();

        assertThat(queueManager.getTaskQueueSize()).isEqualTo(new ProcessorConfig().getQueueSize());
        verify(processorTaskDao, never()).findExistingCreatedTasks(anyLong(), eq(second.getId()), anyInt());
    }

    /**
     * The queue is deliberately overfilled and this is not a bug. Fetching tasks is expensive, so when we
     * bother to fetch we take a whole batch even if that exceeds the remaining queue budget. The filters we
     * overfill for are high priority and unbounded, so their tasks are the ones we want to process anyway and
     * filling to the batch size beats fetching again shortly afterwards.
     * <p>
     * Note that the configured queue size is therefore a budget for deciding which filters to consider, not a
     * cap on the size of the queue.
     * </p>
     */
    @Test
    void queueIsDeliberatelyOverfilledToAvoidFrequentFetches() {
        final int queueSize = new ProcessorConfig().getQueueSize();
        final ProcessorFilter first = createFilter(1, null);
        final ProcessorFilter second = createFilter(2, null);
        givenFilters(first, second);
        // The first filter uses up part of the budget, leaving the second filter less than a whole batch.
        givenCreatedTasks(first, 400);
        givenCreatedTasks(second, queueSize);

        queueManager.exec();

        // The second filter was only asked for the remaining 600 but still queued a whole batch of 1000.
        assertThat(queueManager.getTaskQueueSize()).isEqualTo(1400);
        assertThat(queueManager.getTaskQueueSize()).isGreaterThan(queueSize);
    }

    @Test
    void busyProfileDoesNotStopAnotherProfilesFiltersBeingQueued() {
        // The first filter alone provides more than the configured queue size, but its tasks are no use to the
        // nodes of the second filter's profile, so the second filter must still be considered.
        final int queueSize = processorConfig.getQueueSize();
        final ProcessorFilter busy = createFilter(1, PROFILE);
        final ProcessorFilter other = createFilter(2, OTHER_PROFILE);
        givenFilters(busy, other);
        givenCreatedTasks(busy, queueSize);
        givenCreatedTasks(other, 10);
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(UNLIMITED);
        when(processorProfileCache.getProfile(NODE, OTHER_PROFILE)).thenReturn(UNLIMITED);

        queueManager.exec();

        verify(processorTaskDao).findExistingCreatedTasks(anyLong(), eq(other.getId()), anyInt());
        assertThat(queueManager.getTaskQueueSize()).isEqualTo(queueSize + 10);
    }

    /**
     * Note that the fill stopping altogether once every profile has enough queued is not observable here, as a
     * filter of a satisfied profile is passed over either way. {@link TestQueueProcessTasksState} covers when
     * that happens.
     */
    @Test
    void furtherFiltersOfAProfileAreNotConsideredOnceItHasEnoughTasks() {
        final int queueSize = processorConfig.getQueueSize();
        final ProcessorFilter first = createFilter(1, PROFILE);
        final ProcessorFilter second = createFilter(2, OTHER_PROFILE);
        final ProcessorFilter third = createFilter(3, PROFILE);
        givenFilters(first, second, third);
        givenCreatedTasks(first, queueSize);
        givenCreatedTasks(second, queueSize);
        givenCreatedTasks(third, 10);
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(UNLIMITED);
        when(processorProfileCache.getProfile(NODE, OTHER_PROFILE)).thenReturn(UNLIMITED);

        queueManager.exec();

        assertThat(queueManager.getTaskQueueSize()).isEqualTo(queueSize * 2);
        verify(processorTaskDao, never()).findExistingCreatedTasks(anyLong(), eq(third.getId()), anyInt());
    }

    /**
     * A profile with nothing to do never has enough tasks queued, so the fill never stops early because of it.
     * Without backing off we would ask the database for every one of its filters on every fill, and fills run
     * more or less back to back.
     */
    @Test
    void filterWithNoCreatedTasksIsNotAskedAboutAgainStraightAway() {
        final ProcessorFilter empty = createFilter(1, null);
        givenFilters(empty);

        queueManager.exec();
        queueManager.exec();

        verify(processorTaskDao, times(1)).findExistingCreatedTasks(anyLong(), eq(empty.getId()), anyInt());
    }

    @Test
    void emptyFilterIsAskedAboutEveryTimeWhenBackingOffIsTurnedOff() {
        processorConfig.setSkipEmptyFilterFetchDuration(StroomDuration.ZERO);
        final ProcessorFilter empty = createFilter(1, null);
        givenFilters(empty);

        queueManager.exec();
        queueManager.exec();

        verify(processorTaskDao, times(2)).findExistingCreatedTasks(anyLong(), eq(empty.getId()), anyInt());
    }

    @Test
    void filterThatHadTasksIsAskedAboutAgainOnTheNextFill() {
        // Only filters we looked at and found nothing for are backed off.
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);

        queueManager.exec();
        queueManager.exec();

        verify(processorTaskDao, times(2)).findExistingCreatedTasks(anyLong(), eq(filter.getId()), anyInt());
    }

    @Test
    void alreadyQueuedTasksAreNotQueuedAgain() {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);

        queueManager.exec();
        queueManager.exec();

        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);
    }

    // --------------------------------------------------------------------------------
    // Filling the queue with processing profiles
    // --------------------------------------------------------------------------------

    @Test
    void filterIsQueuedWhenItsProfileAllowsTasks() {
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(UNLIMITED);

        queueManager.exec();

        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);
    }

    @Test
    void filterIsNotQueuedWhenItsProfileAllowsNoTasks() {
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(NONE);

        queueManager.exec();

        verify(processorTaskDao, never()).queueTasks(anySet(), anyString());
        assertThat(queueManager.getTaskQueueSize()).isZero();
    }

    @Test
    void filterIsNotQueuedWhenItsProfileCannotBeFound() {
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(processorProfileCache.getProfile(NODE, PROFILE))
                .thenThrow(new RuntimeException("No such profile"));

        queueManager.exec();

        verify(processorTaskDao, never()).queueTasks(anySet(), anyString());
    }

    @Test
    void filterIsQueuedWhenTheEnabledNodesCannotBeDetermined() throws Exception {
        // We can't tell whether the profile allows processing so we should keep queueing rather than stop
        // queueing altogether.
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(targetNodeSetFactory.getEnabledTargetNodeSet())
                .thenThrow(new RuntimeException("No cluster state"));

        queueManager.exec();

        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);
    }

    @Test
    void filterIsQueuedWhenOnlySomeNodesAreInTheProfileNodeGroup() throws Exception {
        // The profile's node group excludes the node doing the queueing but includes another enabled node,
        // which can still process the tasks, so they must still be queued.
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(targetNodeSetFactory.getEnabledTargetNodeSet()).thenReturn(Set.of(NODE, OTHER_NODE));
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(NONE);
        when(processorProfileCache.getProfile(OTHER_NODE, PROFILE)).thenReturn(UNLIMITED);

        queueManager.exec();

        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);
    }

    /**
     * A per node task limit caps what the whole cluster can process just as much as an explicit cluster limit
     * does, so a filter limited that way must not use up the queue budget with tasks it can't process yet.
     */
    @Test
    void filterLimitedByItsProfileNodeCapDoesNotUseUpTheQueueBudget() {
        final ProcessorFilter limited = createFilter(1, PROFILE);
        final ProcessorFilter other = createFilter(2, null);
        givenFilters(limited, other);
        givenCreatedTasks(limited, new ProcessorConfig().getQueueSize());
        givenCreatedTasks(other, 10);
        // Five tasks per node and no cluster limit, so with one node only five can be processed at once.
        when(processorProfileCache.getProfile(NODE, PROFILE))
                .thenReturn(new ProfileResult(5, Integer.MAX_VALUE));

        queueManager.exec();

        // The lower priority filter was still considered rather than the budget being used up.
        verify(processorTaskDao).findExistingCreatedTasks(anyLong(), eq(other.getId()), anyInt());
    }

    /**
     * The reported failure mode. A high priority filter that no node may process must not use up the queue and
     * stop a lower priority filter being queued.
     */
    @Test
    void blockedFilterDoesNotStopOtherFiltersBeingQueued() {
        final ProcessorFilter blocked = createFilter(1, PROFILE);
        final ProcessorFilter allowed = createFilter(2, null);
        givenFilters(blocked, allowed);
        // Enough tasks for the blocked filter to fill the whole queue if it were allowed to.
        givenCreatedTasks(blocked, new ProcessorConfig().getQueueSize());
        givenCreatedTasks(allowed, 10);
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(NONE);

        queueManager.exec();

        verify(processorTaskDao, never()).findExistingCreatedTasks(anyLong(), eq(blocked.getId()), anyInt());
        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);
    }

    // --------------------------------------------------------------------------------
    // Releasing tasks
    // --------------------------------------------------------------------------------

    /**
     * Covers moving from a period where a profile allows processing into one where it does not, and back again.
     * The tasks queued while it was allowed must be released rather than left owned by this node, and must
     * become available to queue again once it is.
     */
    @Test
    void queuedTasksAreReleasedAndRequeuedAsTheProfileChanges() {
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);

        // The profile is currently allowing tasks so they get queued.
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(UNLIMITED);
        queueManager.exec();
        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);

        // Move into a period where the profile allows nothing.
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(NONE);
        queueManager.exec();
        assertThat(queueManager.getTaskQueueSize()).isZero();
        verify(processorTaskDao).releaseTasks(anySet(), eq(TaskStatus.QUEUED));

        // Move back into a period where it allows them again.
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(UNLIMITED);
        queueManager.exec();
        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);
    }

    /**
     * The queue fill stops considering filters once enough tasks are queued, so a filter whose profile closes
     * while busier filters are using up the whole queue budget may never be considered again. Its queued tasks
     * must still be released or they would be stuck until the busier filters go quiet.
     */
    @Test
    void queuedTasksAreReleasedWhenTheProfileClosesEvenIfTheFillStopsEarly() {
        final ProcessorFilter busy = createFilter(1, null);
        final ProcessorFilter limited = createFilter(2, PROFILE);
        givenFilters(busy, limited);
        givenCreatedTasks(busy, 100);
        final List<Long> limitedIds = givenCreatedTasks(limited, 10);

        // The profile is open, so both filters get their tasks queued.
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(UNLIMITED);
        queueManager.exec();
        assertThat(queuedTaskIds).containsAll(limitedIds);

        // The profile closes, and the busy filter now has enough tasks to use up the whole queue budget so
        // that the fill stops before the limited filter is considered.
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(NONE);
        givenCreatedTasks(busy, new ProcessorConfig().getQueueSize());
        queueManager.exec();

        // The fill really did stop before the limited filter, i.e. it was only ever fetched for once.
        verify(processorTaskDao, times(1))
                .findExistingCreatedTasks(anyLong(), eq(limited.getId()), anyInt());
        // But its queued tasks have still been released.
        assertThat(queuedTaskIds).doesNotContainAnyElementsOf(limitedIds);
        verify(processorTaskDao).releaseTasks(anySet(), eq(TaskStatus.QUEUED));
    }

    @Test
    void tasksAreReleasedWhenTheFilterIsNoLongerEnabled() {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        queueManager.exec();
        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);

        // The filter is no longer returned as an enabled filter.
        givenFilters();
        queueManager.exec();

        assertThat(queueManager.getTaskQueueSize()).isZero();
        verify(processorTaskDao).releaseTasks(anySet(), eq(TaskStatus.QUEUED));
    }

    @Test
    void queuesAreClearedOnShutdown() {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        queueManager.exec();
        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);

        queueManager.shutdown();

        assertThat(queueManager.getTaskQueueSize()).isZero();
    }

    // --------------------------------------------------------------------------------
    // Assigning tasks
    // --------------------------------------------------------------------------------

    @Test
    void assignsNoMoreThanTheRequestedCount() {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        queueManager.exec();

        final ProcessorTaskList assigned = queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 3);

        assertThat(assigned.getList()).hasSize(3);
    }

    @Test
    void taskIsOnlyAssignedOnce() {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        queueManager.exec();

        final List<Long> assigned = new ArrayList<>();
        queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 5)
                .getList().forEach(task -> assigned.add(task.getId()));
        queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 5)
                .getList().forEach(task -> assigned.add(task.getId()));

        assertThat(assigned).hasSize(10);
        assertThat(new HashSet<>(assigned)).hasSize(10);
    }

    @Test
    void assignsHigherPriorityFilterTasksFirst() {
        final ProcessorFilter high = createFilter(1, null);
        final ProcessorFilter low = createFilter(2, null);
        givenFilters(high, low);
        givenCreatedTasks(high, 10);
        givenCreatedTasks(low, 10);
        queueManager.exec();

        final ProcessorTaskList assigned = queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 5);

        assertThat(assigned.getList()).hasSize(5);
        assertThat(assigned.getList())
                .allMatch(task -> high.getId().equals(task.getProcessorFilter().getId()));
    }

    @Test
    void assignmentRespectsTheFilterProcessingTaskLimit() {
        // The filter may only process 3 tasks at once and is already processing 1.
        final ProcessorFilter filter = createFilter(1, null).copy().maxProcessingTasks(3).build();
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(processorTaskDao.countTasksForFilter(eq(filter.getId()), eq(TaskStatus.PROCESSING))).thenReturn(1);
        queueManager.exec();

        final ProcessorTaskList assigned = queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 10);

        assertThat(assigned.getList()).hasSize(2);
    }

    @Test
    void noTasksAreAssignedWhenTheProfileAllowsNone() {
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(UNLIMITED);
        queueManager.exec();

        // The profile now allows nothing, so nothing may be assigned even though tasks are queued.
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(NONE);
        final ProcessorTaskList assigned = queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 10);

        assertThat(assigned.getList()).isEmpty();
    }

    /**
     * Assignment fills the queue itself when it is empty so that a node doesn't have to wait for its next poll
     * to get any tasks.
     */
    @Test
    void assignmentFillsTheQueueAndThenAssigns() {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);

        // Note that we have not filled the queue first, so assignment has to do it.
        final ProcessorTaskList assigned = queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 10);

        assertThat(assigned.getList()).hasSize(10);
    }

    /**
     * Filling the queue is synchronized across all assignment requests, so every node in the cluster queues up
     * behind it. If a fill produces nothing then repeating it cannot help, and doing so once per node per
     * attempt is what makes assignment take minutes on a large cluster.
     */
    @Test
    void assignmentStopsFillingTheQueueOnceAFillProducesNothing() {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        // There are no created tasks at all, so filling the queue can never produce anything.

        final ProcessorTaskList assigned = queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 10);

        assertThat(assigned.getList()).isEmpty();
        verify(processorTaskDao, times(1))
                .findExistingCreatedTasks(anyLong(), eq(filter.getId()), anyInt());
    }

    /**
     * Filling the queue is expensive and only one request needs to do it. The others must wait for it rather
     * than perform their own redundant fill, but must not give up, as a node that is told there are no tasks
     * won't ask again for some time.
     */
    @Test
    void onlyOneRequestFillsTheQueueWhileTheOthersWait() throws Exception {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);

        final CountDownLatch fillStarted = new CountDownLatch(1);
        final CountDownLatch releaseFill = new CountDownLatch(1);
        // Note that this must use doAnswer().when(), as when(mock.method()) would invoke the answer that is
        // already registered and so consume the first fetch that we want to block on.
        Mockito.doAnswer(invocation -> {
            if (fetchCount.incrementAndGet() == 1) {
                // Hold the first fill open so that the second request has to wait for it.
                fillStarted.countDown();
                releaseFill.await(10, TimeUnit.SECONDS);
            }
            return findCreatedTasks(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2));
        }).when(processorTaskDao).findExistingCreatedTasks(anyLong(), anyInt(), anyInt());

        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            final Future<ProcessorTaskList> first = executorService.submit(() ->
                    queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 5));
            assertThat(fillStarted.await(10, TimeUnit.SECONDS)).isTrue();

            final AtomicReference<Thread> secondThread = new AtomicReference<>();
            final Future<ProcessorTaskList> second = executorService.submit(() -> {
                secondThread.set(Thread.currentThread());
                return queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 5);
            });
            // Wait until the second request is actually blocked waiting to fill the queue. Without this it
            // might not get there until the first request has finished and the test would prove nothing.
            assertThat(awaitBlocked(secondThread)).isEqualTo(Thread.State.BLOCKED);
            releaseFill.countDown();

            // Both requests get tasks, the second from the queue that the first filled.
            assertThat(first.get(10, TimeUnit.SECONDS).getList()).hasSize(5);
            assertThat(second.get(10, TimeUnit.SECONDS).getList()).hasSize(5);
        } finally {
            executorService.shutdownNow();
        }

        assertThat(fetchCount.get()).isEqualTo(1);
    }

    /**
     * Wait for the supplied thread to become blocked on a monitor, i.e. waiting to fill the queue.
     */
    private Thread.State awaitBlocked(final AtomicReference<Thread> threadRef) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        Thread.State state = null;
        while (System.nanoTime() < deadline) {
            final Thread thread = threadRef.get();
            if (thread != null) {
                state = thread.getState();
                if (Thread.State.BLOCKED == state) {
                    return state;
                }
            }
            Thread.sleep(1);
        }
        return state;
    }

    /**
     * A request that needs the queue filled must not repeat a fill that an asynchronous fill has only just
     * finished, as they do exactly the same work back to back.
     */
    @Test
    void syncFillWaitsForAnAsynchronousFillRatherThanRepeatingIt() throws Exception {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 5);
        queueManager.exec();

        // Hold the next fill's fetch open so that we can get another request waiting on it.
        final CountDownLatch fillStarted = new CountDownLatch(1);
        final CountDownLatch releaseFill = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            if (fetchCount.incrementAndGet() == 2) {
                fillStarted.countDown();
                releaseFill.await(10, TimeUnit.SECONDS);
            }
            return findCreatedTasks(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2));
        }).when(processorTaskDao).findExistingCreatedTasks(anyLong(), anyInt(), anyInt());

        final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor();
        final ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
        try {
            asyncFillExecutor = asyncExecutor;
            // More created tasks for the asynchronous fill to queue.
            final List<Long> newTaskIds = givenCreatedTasks(filter, 5);

            // Taking the queued tasks kicks off an asynchronous fill, which blocks holding the fill monitor.
            assertThat(queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 5).getList()).hasSize(5);
            assertThat(fillStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // Discard any further asynchronous fills so that the fetch count below is deterministic.
            asyncFillExecutor = command -> {
            };

            // The next request finds the queue empty and waits to fill it.
            final AtomicReference<Thread> requestThread = new AtomicReference<>();
            final Future<ProcessorTaskList> assigned = requestExecutor.submit(() -> {
                requestThread.set(Thread.currentThread());
                return queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 5);
            });
            assertThat(awaitBlocked(requestThread)).isEqualTo(Thread.State.BLOCKED);
            releaseFill.countDown();

            // It gets the tasks the asynchronous fill queued, without having filled the queue itself, i.e.
            // there were only ever two fetches, one for the first fill and one for the asynchronous fill.
            assertThat(assigned.get(10, TimeUnit.SECONDS).getList())
                    .extracting(ProcessorTask::getId)
                    .containsExactlyInAnyOrderElementsOf(newTaskIds);
            assertThat(fetchCount.get()).isEqualTo(2);
        } finally {
            releaseFill.countDown();
            asyncExecutor.shutdownNow();
            requestExecutor.shutdownNow();
        }
    }

    /**
     * Assigning tasks kicks off an asynchronous fill so that the queue is topped up ready for the next request.
     */
    @Test
    void assigningTasksTriggersAnAsynchronousQueueFill() {
        runAsyncFillsInline();
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 20);
        queueManager.exec();
        final int fetchesBeforeAssigning = fetchCount.get();

        queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 5);

        assertThat(fetchCount.get()).isGreaterThan(fetchesBeforeAssigning);
    }

    /**
     * The asynchronous fill guards itself so that only one runs at a time. That guard has to be cleared once it
     * finishes or the queue would never be filled asynchronously again.
     */
    @Test
    void asynchronousQueueFillsCanRunMoreThanOnce() {
        runAsyncFillsInline();
        // We can only see that a fill ran by the work it does, and the second fill would otherwise find the
        // filter backed off from the first fill finding nothing left to queue for it.
        processorConfig.setSkipEmptyFilterFetchDuration(StroomDuration.ZERO);
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 20);
        queueManager.exec();

        queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 5);
        final int fetchesAfterFirstAssignment = fetchCount.get();
        queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 5);

        assertThat(fetchCount.get()).isGreaterThan(fetchesAfterFirstAssignment);
    }

    /**
     * The node and cluster counts are needed together, so they must come from a single query rather than two
     * reads taken at different moments, as the number of tasks being processed changes constantly.
     */
    @Test
    void assignmentGetsTheNodeAndClusterCountsInOneQuery() {
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(new ProfileResult(5, 8));
        when(processorTaskDao.countTasksForFilter(eq(filter.getId()), eq(NODE), eq(TaskStatus.PROCESSING)))
                .thenReturn(new FilterTaskCounts(2, 3));
        queueManager.exec();

        final ProcessorTaskList assigned = queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 10);

        // The node limit leaves 5-2=3 and the cluster limit leaves 8-3=5, so the node limit wins.
        assertThat(assigned.getList()).hasSize(3);
        verify(processorTaskDao, times(1))
                .countTasksForFilter(eq(filter.getId()), eq(NODE), eq(TaskStatus.PROCESSING));
    }

    @Test
    void assignmentAppliesTheClusterLimitWhenItIsTheLowerOfTheTwo() {
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(new ProfileResult(8, 5));
        when(processorTaskDao.countTasksForFilter(eq(filter.getId()), eq(NODE), eq(TaskStatus.PROCESSING)))
                .thenReturn(new FilterTaskCounts(1, 3));
        queueManager.exec();

        final ProcessorTaskList assigned = queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 10);

        // The node limit leaves 8-1=7 but the cluster limit leaves only 5-3=2.
        assertThat(assigned.getList()).hasSize(2);
    }

    /**
     * Covers the assignment side of a bad profile. A filter whose profile cannot be resolved must not stop tasks
     * being assigned for the other filters.
     */
    @Test
    void badProfileDoesNotStopAssignmentForOtherFilters() {
        final ProcessorFilter bad = createFilter(1, PROFILE);
        final ProcessorFilter good = createFilter(2, null);
        givenFilters(bad, good);
        givenCreatedTasks(bad, 5);
        givenCreatedTasks(good, 5);

        // Queue tasks for both filters while the profile is resolvable.
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(UNLIMITED);
        queueManager.exec();
        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);

        // The profile now fails to resolve, which previously aborted the whole filter loop.
        when(processorProfileCache.getProfile(NODE, PROFILE))
                .thenThrow(new RuntimeException("No such profile"));

        final ProcessorTaskList assigned = queueManager.assignTasks(TaskId.createTestTaskId(), NODE, 5);

        assertThat(assigned.getList()).hasSize(5);
        assertThat(assigned.getList())
                .allMatch(task -> good.getId().equals(task.getProcessorFilter().getId()));
    }

    // --------------------------------------------------------------------------------

    /**
     * Run asynchronous queue fills on the calling thread so that a test can exercise them.
     */
    private void runAsyncFillsInline() {
        asyncFillExecutor = Runnable::run;
    }

    private List<ExistingCreatedTask> findCreatedTasks(final long lastTaskId,
                                                       final int filterId,
                                                       final int limit) {
        return taskIdsByFilter.getOrDefault(filterId, List.of())
                .stream()
                .filter(id -> id > lastTaskId)
                .filter(id -> !queuedTaskIds.contains(id))
                .limit(limit)
                .map(id -> new ExistingCreatedTask(id, id))
                .toList();
    }

    private void givenFilters(final ProcessorFilter... filters) {
        when(prioritisedFilters.get()).thenReturn(List.of(filters));
    }

    /**
     * Give the filter the supplied number of unqueued CREATED tasks.
     *
     * @return The ids of the tasks created, which are also their meta ids.
     */
    private List<Long> givenCreatedTasks(final ProcessorFilter filter, final int count) {
        final List<Long> allIds = taskIdsByFilter.computeIfAbsent(filter.getId(), k -> new ArrayList<>());
        final List<Long> newIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final long id = taskIdSequence.incrementAndGet();
            allIds.add(id);
            newIds.add(id);
            tasksById.put(id, ProcessorTask.builder()
                    .id(id)
                    .metaId(id)
                    .processorFilter(filter)
                    .build());
        }
        return newIds;
    }

    private ProcessorFilter createFilter(final int id, final String profileName) {
        return ProcessorFilter.builder()
                .id(id)
                .version(1)
                .priority(10 - id)
                .enabled(true)
                .deleted(false)
                .profileName(profileName)
                .processor(Processor.builder()
                        .id(id)
                        .enabled(true)
                        .deleted(false)
                        .build())
                .build();
    }

    // --------------------------------------------------------------------------------

    /**
     * {@link SimpleTaskContextFactory} supplies contexts with a null task id but task assignment sets the parent
     * task id on the context, so supply one with a real task id.
     */
    private static class TestTaskContextFactory extends SimpleTaskContextFactory {

        @Override
        public <R> Supplier<R> contextResult(final String taskName,
                                             final TerminateHandlerFactory terminateHandlerFactory,
                                             final Function<TaskContext, R> function) {
            return () -> function.apply(new SimpleTaskContext() {
                @Override
                public TaskId getTaskId() {
                    return TaskId.createTestTaskId();
                }
            });
        }
    }

    // --------------------------------------------------------------------------------

    /**
     * Task assignment sleeps for this duration after a queue fill that adds nothing, which we don't want to do
     * in a test.
     */
    private static class TestProcessorConfig extends ProcessorConfig {

        @Override
        public StroomDuration getWaitToQueueTasksDuration() {
            return StroomDuration.ZERO;
        }
    }
}
