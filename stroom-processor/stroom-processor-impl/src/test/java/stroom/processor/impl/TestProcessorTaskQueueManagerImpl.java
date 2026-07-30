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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the interaction between processing profiles and the filling of the task queue. Task assignment applies
 * processing profiles but queueing did not, so tasks that no node was allowed to process could be queued, using
 * up the queue and stopping tasks being queued for other filters.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestProcessorTaskQueueManagerImpl {

    private static final String NODE = "node1";
    private static final String OTHER_NODE = "node2";
    private static final String PROFILE = "profile1";
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
    private final AtomicLong taskId = new AtomicLong();
    private final Map<Long, ProcessorTask> tasksById = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        when(nodeInfo.getThisNodeName()).thenReturn(NODE);
        when(securityContext.isProcessingUser()).thenReturn(true);
        // Run anything submitted as the processing user inline so the test is deterministic.
        doRunInline();
        when(executorProvider.get(any())).thenReturn(Runnable::run);
        when(targetNodeSetFactory.getEnabledActiveTargetNodeSet()).thenReturn(Set.of(NODE));
        when(metaService.findLockedMeta(any())).thenReturn(Collections.emptySet());
        when(processorTaskDao.releaseOwnedTasks(anyString())).thenReturn(0L);
        // Queueing a set of task ids returns the matching tasks, as the real DAO does.
        when(processorTaskDao.queueTasks(anySet(), anyString())).thenAnswer(invocation -> {
            final Set<Long> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(tasksById::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        });

        queueManager = new ProcessorTaskQueueManagerImpl(
                processorTaskDao,
                executorProvider,
                new TestTaskContextFactory(),
                nodeInfo,
                ProcessorConfig::new,
                () -> internalStatisticsReceiver,
                metaService,
                securityContext,
                targetNodeSetFactory,
                prioritisedFilters,
                processorProfileCache);
        queueManager.startup();
    }

    private void doRunInline() {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(securityContext).asProcessingUser(any(Runnable.class));
    }

    @Test
    void filterWithNoProfileIsQueued() {
        final ProcessorFilter filter = createFilter(1, null);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);

        queueManager.exec();

        verify(processorTaskDao).queueTasks(anySet(), eq(NODE));
    }

    @Test
    void filterIsQueuedWhenItsProfileAllowsTasks() {
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(UNLIMITED);

        queueManager.exec();

        verify(processorTaskDao).queueTasks(anySet(), eq(NODE));
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
    void filterIsQueuedWhenTheActiveNodesCannotBeDetermined() throws Exception {
        // We can't tell whether the profile allows processing so we should keep queueing rather than stop
        // queueing altogether.
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(targetNodeSetFactory.getEnabledActiveTargetNodeSet())
                .thenThrow(new RuntimeException("No cluster state"));

        queueManager.exec();

        verify(processorTaskDao).queueTasks(anySet(), eq(NODE));
    }

    @Test
    void filterIsQueuedWhenOnlySomeNodesAreInTheProfileNodeGroup() throws Exception {
        // The profile's node group excludes the node doing the queueing but includes another active node, which
        // can still process the tasks, so they must still be queued.
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);
        when(targetNodeSetFactory.getEnabledActiveTargetNodeSet()).thenReturn(Set.of(NODE, OTHER_NODE));
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(NONE);
        when(processorProfileCache.getProfile(OTHER_NODE, PROFILE)).thenReturn(UNLIMITED);

        queueManager.exec();

        verify(processorTaskDao).queueTasks(anySet(), eq(NODE));
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
        givenCreatedTasks(blocked, 1000);
        givenCreatedTasks(allowed, 10);
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(NONE);

        queueManager.exec();

        // The blocked filter was skipped but the allowed filter was still queued.
        verify(processorTaskDao, never()).findExistingCreatedTasks(anyLong(), eq(blocked.getId()), anyInt());
        verify(processorTaskDao).findExistingCreatedTasks(anyLong(), eq(allowed.getId()), anyInt());
        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);
    }

    /**
     * Covers moving from a period where a profile allows processing into one where it does not. The tasks we
     * queued while it was allowed must be released rather than left owned by this node.
     */
    @Test
    void queuedTasksAreReleasedWhenTheProfileStopsAllowingTasks() {
        final ProcessorFilter filter = createFilter(1, PROFILE);
        givenFilters(filter);
        givenCreatedTasks(filter, 10);

        // The profile is currently allowing tasks so they get queued.
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(UNLIMITED);
        queueManager.exec();
        assertThat(queueManager.getTaskQueueSize()).isEqualTo(10);

        // Now move into a period where the profile allows nothing.
        when(processorProfileCache.getProfile(NODE, PROFILE)).thenReturn(NONE);
        queueManager.exec();

        assertThat(queueManager.getTaskQueueSize()).isZero();
        verify(processorTaskDao).releaseTasks(anySet(), eq(TaskStatus.QUEUED));
    }

    /**
     * Covers the assignment side. A filter whose profile cannot be resolved must not stop tasks being assigned
     * for the other filters.
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

        final ProcessorTaskList assigned = queueManager.assignTasks(
                TaskId.createTestTaskId(), NODE, 5);

        assertThat(assigned.getList()).hasSize(5);
        assertThat(assigned.getList())
                .allMatch(task -> good.getId().equals(task.getProcessorFilter().getId()));
    }

    // --------------------------------------------------------------------------------

    private void givenFilters(final ProcessorFilter... filters) {
        when(prioritisedFilters.get()).thenReturn(List.of(filters));
    }

    /**
     * Make the DAO behave as though the filter has the given number of unqueued CREATED tasks.
     */
    private void givenCreatedTasks(final ProcessorFilter filter, final int count) {
        final List<ExistingCreatedTask> existing = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final long id = taskId.incrementAndGet();
            existing.add(new ExistingCreatedTask(id, id));
            tasksById.put(id, ProcessorTask.builder()
                    .id(id)
                    .processorFilter(filter)
                    .build());
        }

        // Return everything on the first page and nothing thereafter so that queueing terminates.
        when(processorTaskDao.findExistingCreatedTasks(anyLong(), eq(filter.getId()), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0, Long.class) == 0L
                        ? existing
                        : Collections.emptyList());
    }

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
}
