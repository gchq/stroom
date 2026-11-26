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

import stroom.node.api.NodeInfo;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskHeartbeat;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.task.api.TaskManager;
import stroom.task.api.TerminationCause;
import stroom.task.shared.TaskId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * gh-5699. The heartbeat may only ever stamp rows that are PROCESSING <em>and</em> owned by this
 * node <em>and</em> registered — a task that has completed, lost its lease, or was never moved to
 * PROCESSING by us must be left alone, because a wrongly-fresh status time would hide a dead task
 * from the reaper, and a stamp on a row another node now owns would mask a lost lease. And once
 * renewal has failed for longer than the lease timeout, the node must terminate its own in-flight
 * work (self-fencing) — the reaper may already have handed those tasks to other nodes.
 */
class TestProcessorTaskHeartbeat extends AbstractProcessorTest {

    private static final Instant SEED_TIME = Instant.ofEpochMilli(1_000);
    private static final long HEARTBEAT_TIME_MS = 555_000;
    private static final long LATER_HEARTBEAT_TIME_MS = 666_000;
    private static final int FILTER_ID = 1;
    private static final TaskId RUN1 = new TaskId("run1", null);
    private static final TaskId RUN2 = new TaskId("run2", null);

    private final TaskManager taskManager = Mockito.mock(TaskManager.class);

    private ProcessorFilter filter;

    @Test
    void renewStampsOnlyOwnedProcessingRegisteredTasks() {
        final ProcessorTaskHeartbeat heartbeat = getInjector().getInstance(ProcessorTaskHeartbeat.class);
        Mockito.when(getInjector().getInstance(NodeInfo.class).getThisNodeName()).thenReturn(NODE1);

        // Nothing registered — no work, no DB interaction needed.
        assertThat(heartbeat.renew(HEARTBEAT_TIME_MS)).isZero();

        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);

        final long processing = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, SEED_TIME);
        final long unregistered = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, SEED_TIME);
        final long lostLease = createProcessorTask(filter, TaskStatus.PROCESSING, NODE2, FEED, SEED_TIME);
        final long queued = createProcessorTask(filter, TaskStatus.QUEUED, NODE1, FEED, SEED_TIME);
        final long complete = createProcessorTask(filter, TaskStatus.COMPLETE, NODE1, FEED, SEED_TIME);

        // Register everything except 'unregistered' — including rows the stamp must then
        // refuse: owned by another node (lost lease), no longer PROCESSING.
        heartbeat.registerClaim(task(processing), filter.getId());
        heartbeat.registerClaim(task(lostLease), filter.getId());
        heartbeat.registerClaim(task(queued), filter.getId());
        heartbeat.registerClaim(task(complete), filter.getId());
        assertThat(heartbeat.size()).isEqualTo(4);

        assertThat(heartbeat.renew(HEARTBEAT_TIME_MS)).isEqualTo(1);

        assertThat(getTaskStatusTimeMs(processing)).isEqualTo(HEARTBEAT_TIME_MS);
        assertThat(getTaskStatusTimeMs(unregistered)).isEqualTo(SEED_TIME.toEpochMilli());
        assertThat(getTaskStatusTimeMs(lostLease)).isEqualTo(SEED_TIME.toEpochMilli());
        assertThat(getTaskStatusTimeMs(queued)).isEqualTo(SEED_TIME.toEpochMilli());
        assertThat(getTaskStatusTimeMs(complete)).isEqualTo(SEED_TIME.toEpochMilli());

        // The version must not move — heartbeats must not disturb optimistic locking on
        // status changes.
        assertThat(getTaskVersion(processing)).isEqualTo(getTaskVersion(unregistered));

        // Once deregistered (task finished), later heartbeats leave the row alone.
        heartbeat.deregister(task(processing));
        assertThat(heartbeat.renew(LATER_HEARTBEAT_TIME_MS)).isZero();
        assertThat(getTaskStatusTimeMs(processing)).isEqualTo(HEARTBEAT_TIME_MS);

        // Deregistering something never registered is a no-op.
        heartbeat.deregister(task(unregistered));
        assertThat(heartbeat.size()).isEqualTo(3);
    }

    @Test
    void selfFenceEndsEveryRunEvenIfOneCannotBeEnded() {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final long leaseTimeoutMs = new ProcessorConfig().getTaskLeaseTimeout().toMillis();
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final long first = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, SEED_TIME);
        final long second = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, SEED_TIME);
        final TaskId firstTaskId = new TaskId("first", null);
        final TaskId secondTaskId = new TaskId("second", null);
        heartbeat.registerStart(task(first), filter.getId(), firstTaskId);
        heartbeat.registerStart(task(second), filter.getId(), secondTaskId);
        Mockito.doThrow(new RuntimeException("Expected"))
                .when(taskManager).terminate(Mockito.any(), Mockito.any());
        final long t0 = 1_000_000;
        heartbeat.renew(t0);

        heartbeat.fenceIfRenewalOverdue(t0 + leaseTimeoutMs + 1);

        Mockito.verify(taskManager).terminate(firstTaskId, TerminationCause.HEARTBEAT_OVERDUE);
        Mockito.verify(taskManager).terminate(secondTaskId, TerminationCause.HEARTBEAT_OVERDUE);
    }

    @Test
    void selfFenceTerminatesInFlightTasksWhenRenewalOverdue() {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final long leaseTimeoutMs = new ProcessorConfig().getTaskLeaseTimeout().toMillis();

        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final long taskId = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, SEED_TIME);
        final TaskId stroomTaskId = new TaskId("fence-me", null);
        heartbeat.registerStart(task(taskId), filter.getId(), stroomTaskId);

        final long t0 = 1_000_000;
        assertThat(heartbeat.renew(t0)).isEqualTo(1);

        // Renewal current — nothing terminated, even though the check runs.
        heartbeat.fenceIfRenewalOverdue(t0 + 1_000);
        Mockito.verify(taskManager, Mockito.never()).terminate(Mockito.any(), Mockito.any());

        // No successful renewal for longer than the lease — in-flight work is terminated.
        heartbeat.fenceIfRenewalOverdue(t0 + leaseTimeoutMs + 1);
        Mockito.verify(taskManager).terminate(stroomTaskId, TerminationCause.HEARTBEAT_OVERDUE);

        // A successful renewal resets the clock, so the fence stands down.
        assertThat(heartbeat.renew(t0 + leaseTimeoutMs + 2)).isEqualTo(1);
        heartbeat.fenceIfRenewalOverdue(t0 + leaseTimeoutMs + 3_000);
        Mockito.verify(taskManager, Mockito.times(1)).terminate(Mockito.any(), Mockito.any());

        // With nothing registered there is nothing to fence, however stale the clock.
        heartbeat.deregister(task(taskId));
        heartbeat.fenceIfRenewalOverdue(t0 + (10 * leaseTimeoutMs));
        Mockito.verify(taskManager, Mockito.times(1)).terminate(Mockito.any(), Mockito.any());
    }

    @Test
    void selfFenceStopsClaimedTasksFromStarting() {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final ProcessorTask task = processingTask();
        heartbeat.registerClaim(task, FILTER_ID);

        heartbeat.fenceIfRenewalOverdue(Long.MAX_VALUE);

        assertThat(heartbeat.registerStart(task, FILTER_ID, RUN1)).contains(TerminationCause.HEARTBEAT_OVERDUE);
        Mockito.verifyNoInteractions(taskManager);
    }

    @Test
    void runsThatStillHaveTheirTaskAreLeftAlone() {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        heartbeat.registerStart(processingTask(), FILTER_ID, RUN1);
        heartbeat.registerClaim(processingTask(), FILTER_ID);

        assertThat(heartbeat.endLostRuns()).isZero();
        Mockito.verifyNoInteractions(taskManager);
    }

    @ParameterizedTest
    @EnumSource(Loss.class)
    void runThatHasLostItsTaskIsEnded(final Loss loss) {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final ProcessorTask task = processingTask();
        heartbeat.registerStart(task, FILTER_ID, RUN1);

        lose(task, loss);

        assertThat(heartbeat.endLostRuns()).isOne();
        Mockito.verify(taskManager).terminate(RUN1, TerminationCause.WORK_LOST);
        // It is only counted, and so logged, once.
        assertThat(heartbeat.endLostRuns()).isZero();
    }

    @ParameterizedTest
    @EnumSource(Loss.class)
    void claimedRunThatHasLostItsTaskDoesNotStart(final Loss loss) {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final ProcessorTask task = processingTask();
        heartbeat.registerClaim(task, FILTER_ID);

        lose(task, loss);

        assertThat(heartbeat.endLostRuns()).isOne();
        assertThat(heartbeat.registerStart(task, FILTER_ID, RUN1)).contains(TerminationCause.WORK_LOST);
        Mockito.verifyNoInteractions(taskManager);
    }

    @Test
    void jobEndsLostRuns() {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final ProcessorTask task = processingTask();
        heartbeat.registerStart(task, FILTER_ID, RUN1);
        lose(task, Loss.ABORTED);

        heartbeat.exec();

        Mockito.verify(taskManager).terminate(RUN1, TerminationCause.WORK_LOST);
    }

    @Test
    void runThatIsEndingIsNotTakenForALostOne() {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final ProcessorTask task = processingTask();
        heartbeat.registerStart(task, FILTER_ID, RUN1);

        heartbeat.registerEnding(task);
        processorTaskDao.changeTaskStatus(task, NODE1, TaskStatus.COMPLETE, 1L, 2L);

        assertThat(heartbeat.endLostRuns()).isZero();
        heartbeat.fenceIfRenewalOverdue(Long.MAX_VALUE);
        Mockito.verifyNoInteractions(taskManager);
    }

    @Test
    void runThatEndsWhileLostRunsAreBeingFoundIsNotTakenForOne() {
        final ProcessorTaskDao dao = Mockito.spy(processorTaskDao);
        final ProcessorTaskHeartbeat heartbeat = heartbeat(dao);
        final ProcessorTask task = processingTask();
        heartbeat.registerStart(task, FILTER_ID, RUN1);
        Mockito.doAnswer(invocation -> {
            heartbeat.registerEnding(task);
            processorTaskDao.changeTaskStatus(task, NODE1, TaskStatus.COMPLETE, 1L, 2L);
            heartbeat.deregister(task);
            return invocation.callRealMethod();
        }).when(dao).findProcessingVersions(Mockito.any(), Mockito.any());

        assertThat(heartbeat.endLostRuns()).isZero();

        Mockito.verifyNoInteractions(taskManager);
    }

    @Test
    void runThatStartsWhileLostRunsAreBeingFoundIsNotTakenForOne() {
        final ProcessorTaskDao dao = Mockito.spy(processorTaskDao);
        final ProcessorTaskHeartbeat heartbeat = heartbeat(dao);
        heartbeat.registerStart(processingTask(), FILTER_ID, RUN1);
        final ProcessorTask later = processingTask();
        Mockito.doAnswer(invocation -> {
            final Object versions = invocation.callRealMethod();
            heartbeat.registerStart(later, FILTER_ID, RUN2);
            return versions;
        }).when(dao).findProcessingVersions(Mockito.any(), Mockito.any());

        assertThat(heartbeat.endLostRuns()).isZero();

        Mockito.verifyNoInteractions(taskManager);
    }

    @Test
    void runIsEndedAgainIfThatFailed() {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final ProcessorTask task = processingTask();
        heartbeat.registerStart(task, FILTER_ID, RUN1);
        lose(task, Loss.ABORTED);
        Mockito.doThrow(new RuntimeException("Expected"))
                .doNothing()
                .when(taskManager).terminate(RUN1, TerminationCause.WORK_LOST);

        heartbeat.exec();
        heartbeat.exec();

        Mockito.verify(taskManager, Mockito.times(2)).terminate(RUN1, TerminationCause.WORK_LOST);
    }

    @Test
    void taskThatIsClaimedAgainByTheSameNodeEndsOnlyTheEarlierRun() {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final ProcessorTask first = processingTask();
        heartbeat.registerStart(first, FILTER_ID, RUN1);
        final ProcessorTask second = reapAndClaim(first);
        heartbeat.registerClaim(second, FILTER_ID);
        heartbeat.registerStart(second, FILTER_ID, RUN2);

        assertThat(heartbeat.endLostRuns()).isOne();
        Mockito.verify(taskManager).terminate(RUN1, TerminationCause.WORK_LOST);
        Mockito.verifyNoMoreInteractions(taskManager);

        // The end of the earlier run leaves the later one registered.
        heartbeat.registerEnding(first);
        heartbeat.deregister(first);
        assertThat(heartbeat.size()).isOne();
        assertThat(heartbeat.renew(HEARTBEAT_TIME_MS)).isOne();
        assertThat(getTaskStatusTimeMs(second.getId())).isEqualTo(HEARTBEAT_TIME_MS);
        assertThat(heartbeat.endLostRuns()).isZero();
    }

    @Test
    void laterRunWaitsForTheEarlierOneToGo() throws Exception {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final ProcessorTask first = processingTask();
        heartbeat.registerStart(first, FILTER_ID, RUN1);
        final ProcessorTask second = reapAndClaim(first);
        heartbeat.registerStart(second, FILTER_ID, RUN2);

        final CompletableFuture<Boolean> wait = CompletableFuture.supplyAsync(() ->
                heartbeat.awaitEarlierRuns(second, Duration.ofMinutes(1), () -> false));
        // It is told to stop, without waiting for the job to find that it has lost its task.
        Mockito.verify(taskManager, Mockito.timeout(10_000)).terminate(RUN1, TerminationCause.WORK_LOST);

        // It hasn't gone just because it is ending.
        heartbeat.registerEnding(first);
        assertThatThrownBy(() -> wait.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        heartbeat.deregister(first);
        assertThat(wait.get(10, TimeUnit.SECONDS)).isTrue();
        Mockito.verifyNoMoreInteractions(taskManager);
    }

    @Test
    void laterRunDoesNotWaitForEver() {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final ProcessorTask first = processingTask();
        heartbeat.registerStart(first, FILTER_ID, RUN1);
        final ProcessorTask second = reapAndClaim(first);

        assertThat(heartbeat.awaitEarlierRuns(second, Duration.ofMillis(10), () -> false)).isFalse();
        assertThat(heartbeat.awaitEarlierRuns(second, Duration.ofMinutes(1), () -> true)).isFalse();

        Thread.currentThread().interrupt();
        assertThat(heartbeat.awaitEarlierRuns(second, Duration.ofMinutes(1), () -> false)).isFalse();
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void laterRunDoesNotWaitForOneThatHasNotStartedButStopsItFromStarting() {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final ProcessorTask first = processingTask();
        heartbeat.registerClaim(first, FILTER_ID);
        final ProcessorTask second = reapAndClaim(first);

        assertThat(heartbeat.awaitEarlierRuns(second, Duration.ofMinutes(1), () -> false)).isTrue();

        assertThat(heartbeat.registerStart(first, FILTER_ID, RUN1)).contains(TerminationCause.WORK_LOST);
    }

    @Test
    void earlierRunAndRunsOfOtherTasksAreNotWaitedFor() {
        final ProcessorTaskHeartbeat heartbeat = heartbeat();
        final ProcessorTask first = processingTask();
        heartbeat.registerStart(first, FILTER_ID, RUN1);
        heartbeat.registerStart(reapAndClaim(first), FILTER_ID, RUN2);
        heartbeat.registerStart(processingTask(), FILTER_ID, RUN2);

        // The earlier one doesn't wait for the later one, or they would both wait.
        assertThat(heartbeat.awaitEarlierRuns(first, Duration.ofMinutes(1), () -> false)).isTrue();

        Mockito.verifyNoInteractions(taskManager);
    }

    private ProcessorTaskHeartbeat heartbeat() {
        return heartbeat(processorTaskDao);
    }

    private ProcessorTaskHeartbeat heartbeat(final ProcessorTaskDao dao) {
        final NodeInfo nodeInfo = getInjector().getInstance(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(NODE1);
        return new ProcessorTaskHeartbeat(dao, nodeInfo, taskManager, ProcessorConfig::new);
    }

    // As a run of it knows it.
    private ProcessorTask task(final long taskId) {
        return ProcessorTask.builder().id(taskId).version(getTaskVersion(taskId)).runCount(1).build();
    }

    private ProcessorTask processingTask() {
        filter = createProcessorFilter(createProcessor());
        return task(createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, SEED_TIME));
    }

    private ProcessorTask reapAndClaim(final ProcessorTask task) {
        processorTaskDao.reapDeadTasks(Instant.now());
        assertThat(getTaskStatus(task.getId())).isEqualTo(TaskStatus.CREATED);
        final List<ProcessorTask> claimed = processorTaskDao.claimTasks(filter.getId(), NODE1, 1);
        assertThat(claimed).extracting(ProcessorTask::getId).containsExactly(task.getId());
        return ProcessorTask.builder()
                .id(task.getId())
                .version(claimed.getFirst().getVersion())
                .runCount(task.getRunCount() + 1)
                .build();
    }

    private void lose(final ProcessorTask task, final Loss loss) {
        switch (loss) {
            case ABORTED -> processorTaskDao.changeTaskStatus(task, null, TaskStatus.ABORTED, null, null);
            case REAPED -> processorTaskDao.reapDeadTasks(Instant.now());
            case GIVEN_TO_ANOTHER_NODE -> {
                processorTaskDao.reapDeadTasks(Instant.now());
                processorTaskDao.claimTasks(filter.getId(), NODE2, 1);
            }
            case GIVEN_TO_THIS_NODE -> reapAndClaim(task);
            case DELETED -> processorTaskDao.logicalDeleteByProcessorFilterId(filter.getId());
        }
    }


    // --------------------------------------------------------------------------------


    private enum Loss {
        ABORTED,
        REAPED,
        GIVEN_TO_ANOTHER_NODE,
        GIVEN_TO_THIS_NODE,
        DELETED
    }
}
