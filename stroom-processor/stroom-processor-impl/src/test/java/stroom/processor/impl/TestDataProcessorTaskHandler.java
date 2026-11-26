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

import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.processor.api.ErrorStreamException;
import stroom.processor.api.ProcessorResult;
import stroom.processor.api.ProcessorResultImpl;
import stroom.processor.api.ProcessorTaskExecutor;
import stroom.processor.api.ProcessorTaskListener;
import stroom.processor.api.RunIndexing;
import stroom.processor.api.RunOutput;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.api.TerminationCause;
import stroom.task.shared.TaskId;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests the status that each way for a run to end gives its task.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestDataProcessorTaskHandler {

    private static final String NODE = "node1";
    private static final int FILTER_ID = 5;
    private static final long TASK_ID = 10;
    private static final long META_ID = 20;
    private static final long OUTPUT_ID = 30;
    private static final long ERROR_ID = 31;
    private static final ProcessorResult RESULT = new ProcessorResultImpl(1, 2, Collections.emptyMap());

    @Mock
    private ProcessorTaskExecutor executor;
    @Mock
    private ProcessorFilterCache processorFilterCache;
    @Mock
    private ProcessorTaskDao processorTaskDao;
    @Mock
    private Store store;
    @Mock
    private MetaService metaService;
    @Mock
    private NodeInfo nodeInfo;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private TaskContextFactory taskContextFactory;
    @Mock
    private TaskContext taskContext;
    @Mock
    private ProcessorTaskHeartbeat processorTaskHeartbeat;
    @Mock
    private ProcessorTaskOutputCleaner processorTaskOutputCleaner;
    @Mock
    private Source source;
    @Mock
    private ProcessorTaskListener listener;

    private final UserRef runAsUser = UserRef.builder().uuid("run-as").subjectId("run-as").build();
    private final Processor processor = Processor.builder()
            .uuid("processor-uuid")
            .processorType(ProcessorType.PIPELINE)
            .build();
    private final ProcessorFilter filter = ProcessorFilter.builder()
            .id(FILTER_ID)
            .uuid("filter-uuid")
            .processor(processor)
            .runAsUser(runAsUser)
            .build();
    private final AtomicBoolean asProcessingUser = new AtomicBoolean();
    private final AtomicReference<Meta> meta = new AtomicReference<>(Meta.builder()
            .id(META_ID)
            .status(Status.UNLOCKED)
            .build());

    private DataProcessorTaskHandler handler;

    @BeforeEach
    void beforeEach() {
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(NODE);
        Mockito.when(taskContext.getTaskId()).thenReturn(TaskId.createTestTaskId());
        Mockito.when(store.openSource(META_ID)).thenReturn(source);
        Mockito.when(source.getMeta()).thenAnswer(invocation -> meta.get());
        Mockito.when(metaService.getMeta(META_ID, true)).thenAnswer(invocation -> meta.get());
        Mockito.when(processorFilterCache.get(FILTER_ID)).thenReturn(Optional.of(filter));
        Mockito.when(executor.exec(
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any()))
                .thenReturn(RESULT);

        Mockito.doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(securityContext).asUser(Mockito.eq(runAsUser), Mockito.any(Runnable.class));
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(securityContext).useAsRead(Mockito.any());
        Mockito.when(securityContext.asProcessingUserResult(Mockito.any())).thenAnswer(invocation -> {
            asProcessingUser.set(true);
            try {
                return invocation.getArgument(0, Supplier.class).get();
            } finally {
                asProcessingUser.set(false);
            }
        });
        Mockito.doAnswer(invocation -> {
            asProcessingUser.set(true);
            try {
                invocation.getArgument(0, Runnable.class).run();
            } finally {
                asProcessingUser.set(false);
            }
            return null;
        }).when(securityContext).asProcessingUser(Mockito.any());
        Mockito.when(processorTaskHeartbeat.registerStart(Mockito.any(), Mockito.anyInt(), Mockito.any()))
                .thenReturn(Optional.empty());
        Mockito.when(processorTaskHeartbeat.awaitEarlierRuns(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(true);
        Mockito.when(processorTaskOutputCleaner.clearEarlierRuns(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    assertThat(asProcessingUser.get()).isTrue();
                    return invocation.getArgument(1, BooleanSupplier.class).getAsBoolean();
                });
        givenTaskIsHeld(Map.of(TASK_ID, 1));
        Mockito.when(taskContextFactory.context(
                        Mockito.any(), Mockito.any(TerminateHandlerFactory.class), Mockito.any()))
                .thenAnswer(invocation -> {
                    final Consumer<TaskContext> consumer = invocation.getArgument(2);
                    return (Runnable) () -> consumer.accept(taskContext);
                });

        Mockito.when(processorTaskDao.changeTaskStatus(
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.anyBoolean()))
                .thenAnswer(invocation -> {
                    assertThat(asProcessingUser.get())
                            .describedAs("the status is changed as the processing user")
                            .isTrue();
                    final ProcessorTask task = invocation.getArgument(0);
                    final TaskStatus status = invocation.getArgument(2);
                    return TaskStatusChange.done(task.copy()
                            .version(task.getVersion() + 1)
                            .runCount(TaskStatus.PROCESSING.equals(status)
                                    ? task.getRunCount() + 1
                                    : task.getRunCount())
                            .status(status)
                            .build());
                });

        handler = new DataProcessorTaskHandler(
                Map.of(ProcessorType.PIPELINE, () -> executor),
                processorFilterCache,
                processorTaskDao,
                store,
                metaService,
                nodeInfo,
                securityContext,
                taskContextFactory,
                processorTaskHeartbeat,
                processorTaskOutputCleaner,
                ProcessorConfig::new,
                Set.of(listener));
    }

    @Test
    void queuedTaskIsCompleted() {
        final TaskId stroomTaskId = taskContext.getTaskId();

        assertThat(handler.exec(task(0), false)).isSameAs(RESULT);

        final InOrder inOrder = Mockito.inOrder(processorTaskDao, executor, processorTaskHeartbeat);
        inOrder.verify(processorTaskDao).changeTaskStatus(
                Mockito.argThat(task -> task.getVersion() == 1), Mockito.eq(NODE), Mockito.eq(TaskStatus.PROCESSING),
                Mockito.anyLong(), Mockito.isNull(), Mockito.isNull(), Mockito.eq(false));
        inOrder.verify(processorTaskHeartbeat).registerStart(
                Mockito.argThat(task -> task.getVersion() == 2),
                Mockito.eq(FILTER_ID),
                Mockito.eq(stroomTaskId));
        inOrder.verify(executor).exec(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any());
        inOrder.verify(processorTaskHeartbeat).registerEnding(Mockito.argThat(task -> task.getVersion() == 2));
        inOrder.verify(processorTaskDao).changeTaskStatus(
                Mockito.argThat(task -> task.getVersion() == 2), Mockito.eq(NODE), Mockito.eq(TaskStatus.COMPLETE),
                Mockito.anyLong(), Mockito.anyLong(), Mockito.isNull(), Mockito.eq(false));
        inOrder.verify(processorTaskHeartbeat).deregister(Mockito.argThat(task -> task.getVersion() == 2));
    }

    @Test
    void claimedTaskIsCompletedWithoutBeingStartedAgain() {
        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.COMPLETE, NODE, false, null);
    }

    @Test
    void taskThatIsNoLongerOursIsLeftAlone() {
        givenStatusCannotBeChanged(TaskStatusChange.lost());

        handler.exec(task(0), false);

        verifyOnlyStartTried();
    }

    @Test
    void taskThatMayNotHaveBeenStartedIsPutBack() {
        givenStatusCannotBeChanged(TaskStatusChange.unknown());
        Mockito.when(processorTaskDao.putBackUnstarted(Mockito.any(), Mockito.any())).thenAnswer(invocation -> {
            assertThat(asProcessingUser.get()).isTrue();
            return true;
        });

        handler.exec(task(0), false);

        verifyOnlyStartTried();
        Mockito.verify(processorTaskDao).putBackUnstarted(Mockito.argThat(task -> task.getVersion() == 1),
                Mockito.eq(NODE));
    }

    @Test
    void taskThatMayNotHaveBeenStartedAndCannotBePutBack() {
        givenStatusCannotBeChanged(TaskStatusChange.unknown());
        Mockito.when(processorTaskDao.putBackUnstarted(Mockito.any(), Mockito.any()))
                .thenThrow(new RuntimeException("Expected"));

        assertThat(handler.exec(task(0), false).getWritten()).isZero();

        verifyOnlyStartTried();
    }

    @Test
    void taskThatIsNoLongerOursIsNotPutBack() {
        givenStatusCannotBeChanged(TaskStatusChange.lost());

        handler.exec(task(0), false);

        Mockito.verify(processorTaskDao, Mockito.never()).putBackUnstarted(Mockito.any(), Mockito.any());
    }

    @Test
    void dataThatWeCreatedIsSkippedAndCompleted() {
        meta.set(meta.get().copy().processorUuid(processor.getUuid()).build());

        handler.exec(task(0), false);

        verifyNotExecuted();
        verifyOnlyStatus(TaskStatus.COMPLETE, NODE, false, null);
    }

    @Test
    void nodeStoppingPutsTheTaskBack() {
        givenTerminatedDuringRun(TerminationCause.NODE_STOPPING, false);

        handler.exec(task(2), true);

        verifyPutBack(DataProcessorTaskHandler.REASON_NODE_STOPPED);
    }

    @Test
    void nodeStoppingIsNotAFaultEvenIfTheRunHitOne() {
        givenTerminatedDuringRun(TerminationCause.NODE_STOPPING, true);

        handler.exec(task(2), true);

        verifyPutBack(DataProcessorTaskHandler.REASON_NODE_STOPPED);
    }

    @Test
    void overdueHeartbeatPutsTheTaskBack() {
        givenTerminatedDuringRun(TerminationCause.HEARTBEAT_OVERDUE, true);

        handler.exec(task(2), true);

        verifyPutBack(DataProcessorTaskHandler.REASON_HEARTBEAT_OVERDUE);
    }

    @Test
    void userTerminatingTheRunAbortsTheTask() {
        givenTerminatedDuringRun(TerminationCause.user("Someone"), true);

        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.ABORTED, NODE, false, "Terminated by Someone");
    }

    @Test
    void terminationWithoutACauseIsTreatedAsByAUser() {
        givenTerminatedDuringRun(TerminationCause.NOT_GIVEN, false);

        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.ABORTED, NODE, false, "Terminated by a user");
    }

    @Test
    void runThatIsTerminatedBeforeItStartsIsNotStarted() {
        givenTerminated(TerminationCause.NODE_STOPPING);

        handler.exec(task(0), false);

        verifyNotExecuted();
        verifyPutBack(DataProcessorTaskHandler.REASON_NODE_STOPPED);
    }

    @Test
    void terminatedRunIsNotCompletedThoughItsSourceIsDeleted() {
        givenTerminatedDuringRun(TerminationCause.NODE_STOPPING, true);
        meta.set(meta.get().copy().status(Status.DELETED).build());

        handler.exec(task(0), true);

        verifyPutBack(DataProcessorTaskHandler.REASON_NODE_STOPPED);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void faultsBeforeTheLastAreRetried(final int faultCount) {
        givenExecutorThrows(new RuntimeException("Expected"));

        handler.exec(task(faultCount), true);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, true, "Processing failed - RuntimeException 'Expected'");
    }

    @Test
    void lastFaultAbortsTheTask() {
        givenExecutorThrows(new RuntimeException("Expected"));

        handler.exec(task(2), true);

        verifyOnlyStatus(TaskStatus.ABORTED, NODE, true, "Processing failed - RuntimeException 'Expected'");
    }

    @Test
    void seriousErrorIsAFaultAndIsPassedOn() {
        final Error error = new OutOfMemoryError("Expected");
        givenExecutorThrows(error);

        assertThatThrownBy(() -> handler.exec(task(0), true)).isSameAs(error);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, true, "Unexpected error - OutOfMemoryError 'Expected'");
        verifyDeregistered();
    }

    @Test
    void seriousErrorBeforeTheRunStartsIsAFaultAndIsPassedOn() {
        final Error error = new NoClassDefFoundError("Expected");
        Mockito.doThrow(error).when(securityContext).asUser(Mockito.eq(runAsUser), Mockito.any(Runnable.class));

        assertThatThrownBy(() -> handler.exec(task(0), true)).isSameAs(error);

        verifyNotExecuted();
        verifyOnlyStatus(TaskStatus.RETRY, NODE, true, "Unexpected error - NoClassDefFoundError 'Expected'");
        verifyDeregistered();
    }

    @Test
    void sourceThatIsMissingIsAFault() {
        Mockito.when(store.openSource(META_ID)).thenReturn(null);

        handler.exec(task(0), false);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, true, "Unable to open source stream 20");
    }

    @Test
    void sourceThatCannotBeOpenedIsAFault() {
        Mockito.when(store.openSource(META_ID)).thenThrow(new RuntimeException("Expected"));

        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, true,
                "Unable to open source stream 20 - RuntimeException 'Expected'");
    }

    @Test
    void sourceThatCannotBeClosedIsAFault() throws IOException {
        Mockito.doThrow(new IOException("Expected")).when(source).close();

        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, true, "Unexpected error - IOException 'Expected'");
    }

    @Test
    void deletedSourceIsCompleted() {
        Mockito.when(store.openSource(META_ID)).thenReturn(null);
        meta.set(meta.get().copy().status(Status.DELETED).build());

        handler.exec(task(0), false);

        verifyOnlyStatus(TaskStatus.COMPLETE, NODE, false, null);
    }

    @Test
    void sourceThatNoLongerExistsIsCompleted() {
        Mockito.when(store.openSource(META_ID)).thenReturn(null);
        Mockito.when(metaService.getMeta(META_ID, true)).thenReturn(null);

        handler.exec(task(0), false);

        verifyOnlyStatus(TaskStatus.COMPLETE, NODE, false, null);
    }

    @Test
    void faultIsRetriedIfItCannotBeToldWhetherTheSourceIsDeleted() {
        Mockito.when(store.openSource(META_ID)).thenReturn(null);
        Mockito.when(metaService.getMeta(META_ID, true)).thenThrow(new RuntimeException("Expected"));

        handler.exec(task(0), false);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, true, "Unable to open source stream 20");
    }

    @Test
    void filterThatCannotBeFoundIsNotCounted() {
        Mockito.when(processorFilterCache.get(FILTER_ID)).thenReturn(Optional.empty());

        // However many faults there have been, so that a whole backlog isn't aborted.
        handler.exec(task(2), false);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, false, "Unable to load processor filter 5 and its processor");
    }

    @Test
    void filterThatCannotBeLoadedIsNotCounted() {
        Mockito.when(processorFilterCache.get(FILTER_ID)).thenThrow(new RuntimeException("Expected"));

        handler.exec(task(2), true);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, false,
                "Unable to load processor filter 5 and its processor - RuntimeException 'Expected'");
    }

    @Test
    void processorThatCannotBeLoadedIsNotCounted() {
        Mockito.when(processorFilterCache.get(FILTER_ID))
                .thenReturn(Optional.of(filter.copy().processor(null).build()));

        handler.exec(task(2), true);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, false, "Unable to load processor filter 5 and its processor");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void runAsUserThatCannotBeUsedIsNotCounted(final boolean alreadyClaimed) {
        Mockito.doThrow(new RuntimeException("User is not enabled"))
                .when(securityContext).asUser(Mockito.eq(runAsUser), Mockito.any(Runnable.class));

        handler.exec(task(2), alreadyClaimed);

        verifyNotExecuted();
        verifyOnlyStatus(TaskStatus.RETRY, NODE, false,
                "Unable to run as the user of the processor filter - RuntimeException 'User is not enabled'");
        if (alreadyClaimed) {
            verifyDeregistered();
        } else {
            Mockito.verifyNoInteractions(processorTaskHeartbeat);
        }
    }

    @Test
    void filterWithoutARunAsUserIsNotCounted() {
        final ProcessorTask task = task(2).copy()
                .processorFilter(filter.copy().runAsUser(null).build())
                .build();

        handler.exec(task, true);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, false,
                "Unable to run as the user of the processor filter - "
                + "RuntimeException 'No run as user specified for filter uuid: filter-uuid'");
    }

    @Test
    void taskWithoutAFilterIsNotCounted() {
        handler.exec(task(2).copy().processorFilter(null).build(), true);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, false,
                "Unable to run as the user of the processor filter - "
                + "RuntimeException 'The task has no processor filter'");
    }

    @Test
    void heartbeatStopsEvenIfTheStatusCannotBeSet() {
        Mockito.doThrow(new RuntimeException("Expected"))
                .when(securityContext).asProcessingUserResult(Mockito.any());

        assertThat(handler.exec(task(0), true)).isSameAs(RESULT);

        verifyDeregistered();
        Mockito.verifyNoInteractions(processorTaskDao);
    }

    @Test
    void runThatIsToldToStopBeforeItStartsIsPutBack() {
        givenStoppedBeforeStart(TerminationCause.HEARTBEAT_OVERDUE);

        handler.exec(task(0), true);

        verifyNotExecuted();
        verifyPutBack(DataProcessorTaskHandler.REASON_HEARTBEAT_OVERDUE);
        verifyDeregistered();
    }

    @Test
    void runThatLosesItsTaskBeforeItStartsLeavesTheTaskAlone() {
        givenStoppedBeforeStart(TerminationCause.WORK_LOST);

        handler.exec(task(0), true);

        verifyNotExecuted();
        verifyTaskLeftAlone();
        verifyDeregistered();
    }

    @Test
    void runThatLosesItsTaskDeletesAllItWroteAndLeavesTheTaskAlone() {
        givenTerminatedDuringRun(TerminationCause.WORK_LOST, true);

        handler.exec(task(0), true);

        verifyTaskLeftAlone();
        verifyOnlyDeleted(OUTPUT_ID, ERROR_ID);
        verifyDeregistered();
    }

    @Test
    void runThatFindsItsTaskHasGoneDeletesAllItWrote() {
        givenWritesOutput();
        givenStatusCannotBeChanged(TaskStatusChange.lost());

        handler.exec(task(0), true);

        verifyOnlyDeleted(OUTPUT_ID, ERROR_ID);
    }

    @Test
    void nothingIsDeletedIfTheStatusMayHaveBeenSet() {
        givenWritesOutput();
        givenStatusCannotBeChanged(TaskStatusChange.unknown());

        handler.exec(task(0), true);

        verifyNothingDeleted();
    }

    @Test
    void nothingIsDeletedIfTheStatusCannotBeSet() {
        givenWritesOutput();
        Mockito.doThrow(new RuntimeException("Expected"))
                .when(securityContext).asProcessingUserResult(Mockito.any());

        handler.exec(task(0), true);

        verifyNothingDeleted();
    }

    @Test
    void completeRunClearsOutputThatItMakesOutOfDate() {
        givenWritesOutput();

        handler.exec(task(0), true);

        final InOrder inOrder = Mockito.inOrder(processorTaskDao, processorTaskOutputCleaner, processorTaskHeartbeat);
        inOrder.verify(processorTaskDao).changeTaskStatus(
                Mockito.any(), Mockito.any(), Mockito.eq(TaskStatus.COMPLETE), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.anyBoolean());
        inOrder.verify(processorTaskOutputCleaner).clearSuperseded(
                Mockito.argThat(task -> task.getId() == TASK_ID), Mockito.eq(meta.get()), Mockito.eq(processor));
        // The run hasn't gone until it has cleared up.
        inOrder.verify(processorTaskHeartbeat).deregister(Mockito.any());
        verifyNoOutputDeleted();
    }

    @Test
    void onlyARunThatRanClearsOutputThatIsOutOfDate() {
        meta.set(meta.get().copy().processorUuid(processor.getUuid()).build());

        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.COMPLETE, NODE, false, null);
        verifyNothingDeleted();
    }

    @Test
    void runThatIsPutBackDeletesAllItWrote() {
        givenTerminatedDuringRun(TerminationCause.NODE_STOPPING, true);

        handler.exec(task(0), true);

        verifyPutBack(DataProcessorTaskHandler.REASON_NODE_STOPPED);
        verifyOnlyDeleted(OUTPUT_ID, ERROR_ID);
    }

    @Test
    void runThatIsEndedByAUserKeepsItsErrorStream() {
        givenTerminatedDuringRun(TerminationCause.user("Someone"), false);

        handler.exec(task(0), true);

        verifyOnlyDeleted(OUTPUT_ID);
    }

    @Test
    void runThatHitsAFaultKeepsItsErrorStream() {
        givenExecutorThrows(new RuntimeException("Expected"));

        handler.exec(task(0), true);

        verifyOnlyDeleted(OUTPUT_ID);
    }

    @Test
    void runThatHitsTheLastFaultKeepsAllItWrote() {
        givenExecutorThrows(new RuntimeException("Expected"));

        handler.exec(task(2), true);

        verifyNothingDeleted();
    }

    @Test
    void runThatIsEndedByAUserOnItsLastTryDeletesItsOutput() {
        givenTerminatedDuringRun(TerminationCause.user("Someone"), true);

        handler.exec(task(2), true);

        verifyOnlyDeleted(OUTPUT_ID);
    }

    @Test
    void failureToClearUpDoesNotChangeHowARunEnded() {
        givenWritesOutput();
        Mockito.doThrow(new RuntimeException("Expected"))
                .when(processorTaskOutputCleaner).clearSuperseded(Mockito.any(), Mockito.any(), Mockito.any());

        assertThat(handler.exec(task(0), true)).isSameAs(RESULT);

        verifyOnlyStatus(TaskStatus.COMPLETE, NODE, false, null);
        verifyDeregistered();
    }

    @Test
    void interruptIsPutOffUntilTheRunHasClearedUp() {
        givenWritesOutput();
        final AtomicBoolean interrupted = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            interrupted.set(Thread.currentThread().isInterrupted());
            return null;
        }).when(processorTaskOutputCleaner).clearSuperseded(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return TaskStatusChange.done(invocation.getArgument(0));
        }).when(processorTaskDao).changeTaskStatus(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.anyBoolean());

        handler.exec(task(0), true);

        assertThat(interrupted.get()).isFalse();
        assertThat(Thread.interrupted()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void firstRunHasNothingToClear(final int runCount) {
        handler.exec(task(0).copy().runCount(runCount).build(), true);

        Mockito.verify(processorTaskOutputCleaner, Mockito.never()).clearEarlierRuns(Mockito.any(), Mockito.any());
        Mockito.verify(processorTaskHeartbeat, Mockito.never())
                .awaitEarlierRuns(Mockito.any(), Mockito.any(), Mockito.any());
        verifyOnlyStatus(TaskStatus.COMPLETE, NODE, false, null);
    }

    @Test
    void laterRunWaitsForEarlierRunsAndClearsTheirOutputBeforeItStarts() {
        handler.exec(laterRun(), true);

        final InOrder inOrder = Mockito.inOrder(processorTaskHeartbeat, processorTaskOutputCleaner, executor);
        inOrder.verify(processorTaskHeartbeat).registerStart(Mockito.any(), Mockito.anyInt(), Mockito.any());
        inOrder.verify(processorTaskHeartbeat).awaitEarlierRuns(Mockito.any(), Mockito.any(), Mockito.any());
        inOrder.verify(processorTaskOutputCleaner).clearEarlierRuns(
                Mockito.argThat(task -> task.getId() == TASK_ID), Mockito.any());
        inOrder.verify(executor).exec(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any());
        verifyOnlyStatus(TaskStatus.COMPLETE, NODE, false, null);
    }

    @Test
    void queuedTaskThatIsNotStartedLeavesTheEarlierRunRegistered() {
        Mockito.when(store.openSource(META_ID)).thenThrow(new RuntimeException("Expected"));

        handler.exec(task(0).copy().runCount(3).build(), false);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, true,
                "Unable to open source stream 20 - RuntimeException 'Expected'");
        // The run has the run count of the run before, which this node may still have.
        Mockito.verify(processorTaskHeartbeat, Mockito.never()).registerEnding(Mockito.any());
        Mockito.verify(processorTaskHeartbeat, Mockito.never()).deregister(Mockito.any());
    }

    @Test
    void queuedTaskThatCannotBeStartedLeavesTheEarlierRunRegistered() {
        givenStatusCannotBeChanged(TaskStatusChange.lost());

        handler.exec(task(0).copy().runCount(3).build(), false);

        Mockito.verify(processorTaskHeartbeat, Mockito.never()).registerStart(
                Mockito.any(), Mockito.anyInt(), Mockito.any());
        Mockito.verify(processorTaskHeartbeat, Mockito.never()).deregister(Mockito.any());
    }

    @Test
    void claimedTaskThatIsNotStartedIsDeregistered() {
        Mockito.when(store.openSource(META_ID)).thenThrow(new RuntimeException("Expected"));
        final ProcessorTask task = task(0).copy().runCount(3).build();

        handler.exec(task, true);

        Mockito.verify(processorTaskHeartbeat).registerEnding(task);
        Mockito.verify(processorTaskHeartbeat).deregister(task);
    }

    @Test
    void queuedTaskIsDeregisteredAsTheRunThatStartedIt() {
        handler.exec(task(0).copy().runCount(3).build(), false);

        Mockito.verify(processorTaskHeartbeat).registerEnding(Mockito.argThat(task -> task.getRunCount() == 4));
        Mockito.verify(processorTaskHeartbeat).deregister(Mockito.argThat(task -> task.getRunCount() == 4));
        Mockito.verify(processorTaskHeartbeat, Mockito.never())
                .deregister(Mockito.argThat(task -> task.getRunCount() == 3));
    }

    @Test
    void earlierRunThatDoesNotStopPutsTheTaskBack() {
        Mockito.when(processorTaskHeartbeat.awaitEarlierRuns(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(false);

        handler.exec(laterRun(), true);

        verifyNotExecuted();
        // It can't yet clear what the earlier run wrote, and wrote nothing itself.
        Mockito.verify(processorTaskOutputCleaner, Mockito.never()).clearEarlierRuns(Mockito.any(), Mockito.any());
        verifyOnlyDeleted();
        verifyOnlyStatus(TaskStatus.RETRY, NODE, false, DataProcessorTaskHandler.REASON_EARLIER_RUN);
    }

    @Test
    void runThatIsTerminatedWhileWaitingEndsForThatReason() {
        Mockito.when(processorTaskHeartbeat.awaitEarlierRuns(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    givenTerminated(TerminationCause.NODE_STOPPING);
                    assertThat(invocation.getArgument(2, BooleanSupplier.class).getAsBoolean()).isTrue();
                    return false;
                });

        handler.exec(laterRun(), true);

        verifyNotExecuted();
        verifyPutBack(DataProcessorTaskHandler.REASON_NODE_STOPPED);
    }

    @Test
    void earlierOutputThatCannotBeClearedIsNotCounted() {
        Mockito.doThrow(new RuntimeException("Expected"))
                .when(processorTaskOutputCleaner).clearEarlierRuns(Mockito.any(), Mockito.any());

        handler.exec(laterRun().copy().faultCount(2).build(), true);

        verifyNotExecuted();
        verifyOnlyStatus(TaskStatus.RETRY, NODE, false,
                "Unable to clear the output of earlier runs - RuntimeException 'Expected'");
    }

    @Test
    void runThatHasLostItsTaskWhenItClearsUpDoesNotStart() {
        // It has been run again since.
        givenTaskIsHeld(Map.of(TASK_ID, 3));

        handler.exec(laterRun(), true);

        verifyNotExecuted();
        verifyTaskLeftAlone();
    }

    @Test
    void runWhoseTaskIsNoLongerProcessingWhenItClearsUpDoesNotStart() {
        givenTaskIsHeld(Map.of());

        handler.exec(laterRun(), true);

        verifyNotExecuted();
        verifyTaskLeftAlone();
    }

    @Test
    void heldRunIsSetToCommittingAndTheListenerIsToldItIsHeld() {
        givenIndexingSays(RunIndexing::hold);

        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.COMMITTING, NODE, false, null);
        Mockito.verify(listener).held(Mockito.argThat(task -> TaskStatus.COMMITTING.equals(task.getStatus())));
        Mockito.verify(listener, Mockito.never()).ended(Mockito.any(), Mockito.any());
        // It finished well, so output it makes out of date is cleared, and its own is kept.
        Mockito.verify(processorTaskOutputCleaner).clearSuperseded(Mockito.any(), Mockito.any(), Mockito.any());
        verifyNoOutputDeleted();
    }

    @Test
    void completeRunTellsTheListenerItEnded() {
        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.COMPLETE, NODE, false, null);
        Mockito.verify(listener).ended(
                Mockito.argThat(task -> task.getId() == TASK_ID), Mockito.eq(TaskStatus.COMPLETE));
        Mockito.verify(listener, Mockito.never()).held(Mockito.any());
        Mockito.verify(listener, Mockito.never()).beforeRerun(Mockito.any());
    }

    @Test
    void lostDocumentsPutTheTaskBackWithoutCountingTheFaultItHit() {
        givenIndexingSays(indexing -> {
            indexing.hold();
            indexing.couldNotIndex("Bad document", true);
            indexing.documentsLost();
        }, new RuntimeException("Expected"));

        handler.exec(task(0), true);

        verifyPutBack(DataProcessorTaskHandler.REASON_DOCUMENTS_LOST);
        Mockito.verify(listener).ended(Mockito.any(), Mockito.eq(TaskStatus.CREATED));
        verifyOnlyDeleted(OUTPUT_ID, ERROR_ID);
    }

    @Test
    void nodeStoppingWinsOverLostDocuments() {
        Mockito.when(executor.exec(
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any()))
                .thenAnswer(invocation -> {
                    invocation.getArgument(6, RunIndexing.class).documentsLost();
                    givenTerminated(TerminationCause.NODE_STOPPING);
                    return RESULT;
                });

        handler.exec(task(0), true);

        verifyPutBack(DataProcessorTaskHandler.REASON_NODE_STOPPED);
    }

    @Test
    void streamThatCouldNotBeIndexedIsACountedFault() {
        givenIndexingSays(indexing -> {
            indexing.hold();
            indexing.couldNotIndex("Bad document", true);
        });

        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, true, "Bad document");
        Mockito.verify(listener).ended(Mockito.any(), Mockito.eq(TaskStatus.RETRY));
    }

    @Test
    void indexThatCouldNotBeWrittenIsNotCounted() {
        givenIndexingSays(indexing -> indexing.couldNotIndex("Index not writable", false));

        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, false, "Index not writable");
    }

    @Test
    void streamThatCouldNotBeIndexedOnTheLastTryAbortsTheTask() {
        givenIndexingSays(indexing -> indexing.couldNotIndex("Bad document", true));

        handler.exec(task(2), true);

        verifyOnlyStatus(TaskStatus.ABORTED, NODE, true, "Bad document");
        Mockito.verify(listener).ended(Mockito.any(), Mockito.eq(TaskStatus.ABORTED));
    }

    @Test
    void indexThatCouldNotBeWrittenIsNotCountedEvenIfThePipelineThrows() {
        givenIndexingSays(indexing -> indexing.couldNotIndex("Index not writable", false),
                new RuntimeException("Expected"));

        handler.exec(task(2), true);

        // Not Aborted, though it is the third fault.
        verifyOnlyStatus(TaskStatus.RETRY, NODE, false, "Index not writable - RuntimeException 'Expected'");
    }

    @Test
    void streamThatCouldNotBeIndexedIsCountedWithTheIndexReasonIfThePipelineThrows() {
        givenIndexingSays(indexing -> indexing.couldNotIndex("Bad document", true), new RuntimeException("Expected"));

        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.RETRY, NODE, true, "Bad document - RuntimeException 'Expected'");
    }

    @Test
    void outputIsClearedUpEvenIfAListenerThrowsAnError() {
        givenWritesOutput();
        givenStatusCannotBeChanged(TaskStatusChange.lost());
        Mockito.doThrow(new AssertionError("Expected")).when(listener).replaced(Mockito.any());

        assertThatThrownBy(() -> handler.exec(task(0), true)).isInstanceOf(AssertionError.class);

        verifyOnlyDeleted(OUTPUT_ID, ERROR_ID);
    }

    @Test
    void listenerGetsReadyForALaterRunOnceEarlierOutputIsClearedAndBeforeItStarts() {
        Mockito.doAnswer(invocation -> {
            assertThat(asProcessingUser.get()).isTrue();
            return null;
        }).when(listener).beforeRerun(Mockito.any());

        handler.exec(laterRun(), true);

        final InOrder inOrder = Mockito.inOrder(processorTaskOutputCleaner, listener, executor);
        inOrder.verify(processorTaskOutputCleaner).clearEarlierRuns(Mockito.any(), Mockito.any());
        inOrder.verify(listener).beforeRerun(Mockito.argThat(task -> task.getRunCount() == 2));
        inOrder.verify(executor).exec(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any());
        verifyOnlyStatus(TaskStatus.COMPLETE, NODE, false, null);
    }

    @Test
    void listenerThatCannotGetReadyForALaterRunIsAnUncountedFault() {
        Mockito.doThrow(new RuntimeException("Expected")).when(listener).beforeRerun(Mockito.any());

        handler.exec(laterRun(), true);

        verifyNotExecuted();
        verifyOnlyStatus(TaskStatus.RETRY, NODE, false,
                "Unable to get ready for the task to be run again - RuntimeException 'Expected'");
    }

    @Test
    void listenerIsToldThatARunThatLostItsTaskWasReplaced() {
        givenStatusCannotBeChanged(TaskStatusChange.lost());

        handler.exec(task(0), true);

        Mockito.verify(listener).replaced(Mockito.argThat(task -> task.getId() == TASK_ID));
        Mockito.verify(listener, Mockito.never()).ended(Mockito.any(), Mockito.any());
    }

    @Test
    void listenerIsNotToldIfTheStatusMayHaveBeenSet() {
        givenStatusCannotBeChanged(TaskStatusChange.unknown());

        handler.exec(task(0), true);

        Mockito.verifyNoInteractions(listener);
    }

    @Test
    void listenerIsNotToldOfATaskThatWasNotStarted() {
        givenStatusCannotBeChanged(TaskStatusChange.lost());

        handler.exec(task(0), false);

        Mockito.verifyNoInteractions(listener);
    }

    @Test
    void errorStreamThatCannotBeWrittenIsNotCounted() {
        givenExecutorThrows(new ErrorStreamException("Unable to write the error stream - IOException 'Full'",
                new IOException("Full")));

        handler.exec(task(2), true);

        // Not Aborted, though it is the third fault, as every task would hit it while the volume is full.
        verifyOnlyStatus(TaskStatus.RETRY, NODE, false, "Unable to write the error stream - IOException 'Full'");
    }

    @Test
    void listenerThatCannotBeToldChangesNothing() {
        givenIndexingSays(RunIndexing::hold);
        Mockito.doAnswer(invocation -> {
            assertThat(asProcessingUser.get()).isTrue();
            throw new RuntimeException("Expected");
        }).when(listener).held(Mockito.any());

        handler.exec(task(0), true);

        verifyOnlyStatus(TaskStatus.COMMITTING, NODE, false, null);
        Mockito.verify(processorTaskOutputCleaner).clearSuperseded(Mockito.any(), Mockito.any(), Mockito.any());
    }

    private ProcessorTask task(final int faultCount) {
        return ProcessorTask.builder()
                .id(TASK_ID)
                .version(1)
                .metaId(META_ID)
                .faultCount(faultCount)
                .processorFilter(filter)
                .build();
    }

    private ProcessorTask laterRun() {
        return task(0).copy().runCount(2).build();
    }

    private void givenTaskIsHeld(final Map<Long, Integer> versions) {
        Mockito.doReturn(versions).when(processorTaskDao).findProcessingVersions(NODE, List.of(TASK_ID));
    }

    private void givenStoppedBeforeStart(final TerminationCause cause) {
        Mockito.when(processorTaskHeartbeat.registerStart(Mockito.any(), Mockito.anyInt(), Mockito.any()))
                .thenReturn(Optional.of(cause));
    }

    private void givenWritesOutput() {
        Mockito.when(executor.exec(
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any()))
                .thenAnswer(invocation -> {
                    writeOutput(invocation.getArgument(5));
                    return RESULT;
                });
    }

    private void givenIndexingSays(final Consumer<RunIndexing> says) {
        givenIndexingSays(says, null);
    }

    private void givenIndexingSays(final Consumer<RunIndexing> says, final RuntimeException thenThrows) {
        Mockito.when(executor.exec(
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any()))
                .thenAnswer(invocation -> {
                    writeOutput(invocation.getArgument(5));
                    says.accept(invocation.getArgument(6));
                    if (thenThrows != null) {
                        throw thenThrows;
                    }
                    return RESULT;
                });
    }

    private void writeOutput(final RunOutput runOutput) {
        runOutput.addOutput(OUTPUT_ID);
        runOutput.addError(ERROR_ID);
    }

    private void givenTerminated(final TerminationCause cause) {
        Mockito.when(taskContext.isTerminated()).thenReturn(true);
        Mockito.when(taskContext.getTerminationCause()).thenReturn(Optional.of(cause));
    }

    private void givenTerminatedDuringRun(final TerminationCause cause, final boolean hitsFault) {
        Mockito.when(executor.exec(
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any()))
                .thenAnswer(invocation -> {
                    writeOutput(invocation.getArgument(5));
                    givenTerminated(cause);
                    if (hitsFault) {
                        throw new TaskTerminatedException();
                    }
                    return RESULT;
                });
    }

    private void givenExecutorThrows(final Throwable t) {
        Mockito.when(executor.exec(
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any()))
                .thenAnswer(invocation -> {
                    writeOutput(invocation.getArgument(5));
                    throw t;
                });
    }

    private void givenStatusCannotBeChanged(final TaskStatusChange change) {
        Mockito.doReturn(change).when(processorTaskDao).changeTaskStatus(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.anyBoolean());
    }

    private void verifyOnlyStartTried() {
        verifyNotExecuted();
        verifyOnlyStatus(TaskStatus.PROCESSING, NODE, false, null);
        // It never registered, and another run of the task may have.
        Mockito.verifyNoInteractions(processorTaskHeartbeat);
    }

    private void verifyTaskLeftAlone() {
        Mockito.verify(processorTaskDao, Mockito.never()).changeTaskStatus(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.anyBoolean());
    }

    private void verifyDeregistered() {
        Mockito.verify(processorTaskHeartbeat).deregister(Mockito.argThat(task -> task.getId() == TASK_ID));
    }

    private void verifyOnlyDeleted(final Long... metaIds) {
        Mockito.verify(processorTaskOutputCleaner, Mockito.times(1)).delete(List.of(metaIds));
        Mockito.verify(processorTaskOutputCleaner, Mockito.never())
                .clearSuperseded(Mockito.any(), Mockito.any(), Mockito.any());
    }

    private void verifyNoOutputDeleted() {
        Mockito.verify(processorTaskOutputCleaner, Mockito.never()).delete(Mockito.any());
    }

    private void verifyNothingDeleted() {
        Mockito.verifyNoInteractions(processorTaskOutputCleaner);
    }

    private void verifyNotExecuted() {
        Mockito.verifyNoInteractions(executor);
    }

    private void verifyPutBack(final String reason) {
        verifyOnlyStatus(TaskStatus.CREATED, null, false, reason);
    }

    private void verifyOnlyStatus(final TaskStatus status,
                                  final String nodeName,
                                  final boolean countFault,
                                  final String reason) {
        final ArgumentCaptor<TaskStatus> statusCaptor = ArgumentCaptor.forClass(TaskStatus.class);
        final ArgumentCaptor<String> nodeNameCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Boolean> countFaultCaptor = ArgumentCaptor.forClass(Boolean.class);
        Mockito.verify(processorTaskDao, Mockito.times(1)).changeTaskStatus(
                Mockito.any(), nodeNameCaptor.capture(), statusCaptor.capture(), Mockito.any(), Mockito.any(),
                reasonCaptor.capture(), countFaultCaptor.capture());
        assertThat(statusCaptor.getValue()).isEqualTo(status);
        assertThat(nodeNameCaptor.getValue()).isEqualTo(nodeName);
        assertThat(reasonCaptor.getValue()).isEqualTo(reason);
        assertThat(countFaultCaptor.getValue()).isEqualTo(countFault);
    }
}
