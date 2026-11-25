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
import stroom.processor.api.ProcessorResult;
import stroom.processor.api.ProcessorResultImpl;
import stroom.processor.api.ProcessorTaskExecutor;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private Source source;

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
        Mockito.when(executor.exec(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
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
                ProcessorConfig::new);
    }

    @Test
    void queuedTaskIsCompleted() {
        assertThat(handler.exec(task(0), false)).isSameAs(RESULT);

        final InOrder inOrder = Mockito.inOrder(processorTaskDao, executor, processorTaskHeartbeat);
        inOrder.verify(processorTaskDao).changeTaskStatus(
                Mockito.argThat(task -> task.getVersion() == 1), Mockito.eq(NODE), Mockito.eq(TaskStatus.PROCESSING),
                Mockito.anyLong(), Mockito.isNull(), Mockito.isNull(), Mockito.eq(false));
        inOrder.verify(processorTaskHeartbeat).register(TASK_ID, FILTER_ID, taskContext.getTaskId());
        inOrder.verify(executor).exec(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        inOrder.verify(processorTaskDao).changeTaskStatus(
                Mockito.argThat(task -> task.getVersion() == 2), Mockito.eq(NODE), Mockito.eq(TaskStatus.COMPLETE),
                Mockito.anyLong(), Mockito.anyLong(), Mockito.isNull(), Mockito.eq(false));
        inOrder.verify(processorTaskHeartbeat).deregister(TASK_ID);
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
    void taskThatMayNotHaveBeenStartedIsLeftAlone() {
        givenStatusCannotBeChanged(TaskStatusChange.unknown());

        handler.exec(task(0), false);

        verifyOnlyStartTried();
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
        Mockito.verify(processorTaskHeartbeat).deregister(TASK_ID);
    }

    @Test
    void seriousErrorBeforeTheRunStartsIsAFaultAndIsPassedOn() {
        final Error error = new NoClassDefFoundError("Expected");
        Mockito.doThrow(error).when(securityContext).asUser(Mockito.eq(runAsUser), Mockito.any(Runnable.class));

        assertThatThrownBy(() -> handler.exec(task(0), true)).isSameAs(error);

        verifyNotExecuted();
        verifyOnlyStatus(TaskStatus.RETRY, NODE, true, "Unexpected error - NoClassDefFoundError 'Expected'");
        Mockito.verify(processorTaskHeartbeat).deregister(TASK_ID);
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
        Mockito.verify(processorTaskHeartbeat).deregister(TASK_ID);
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

        Mockito.verify(processorTaskHeartbeat).deregister(TASK_ID);
        Mockito.verifyNoInteractions(processorTaskDao);
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

    private void givenTerminated(final TerminationCause cause) {
        Mockito.when(taskContext.isTerminated()).thenReturn(true);
        Mockito.when(taskContext.getTerminationCause()).thenReturn(Optional.of(cause));
    }

    private void givenTerminatedDuringRun(final TerminationCause cause, final boolean hitsFault) {
        Mockito.when(executor.exec(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    givenTerminated(cause);
                    if (hitsFault) {
                        throw new TaskTerminatedException();
                    }
                    return RESULT;
                });
    }

    private void givenExecutorThrows(final Throwable t) {
        Mockito.when(executor.exec(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(t);
    }

    private void givenStatusCannotBeChanged(final TaskStatusChange change) {
        Mockito.doReturn(change).when(processorTaskDao).changeTaskStatus(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.anyBoolean());
    }

    private void verifyOnlyStartTried() {
        verifyNotExecuted();
        verifyOnlyStatus(TaskStatus.PROCESSING, NODE, false, null);
        Mockito.verify(processorTaskHeartbeat).deregister(TASK_ID);
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
