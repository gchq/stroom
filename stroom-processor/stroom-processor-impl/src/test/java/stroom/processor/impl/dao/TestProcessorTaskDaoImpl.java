/*
 * Copyright 2022 Crown Copyright
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

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.api.ProcessorTaskState;
import stroom.processor.impl.ProgressMonitor;
import stroom.processor.impl.ProgressMonitor.FilterProgressMonitor;
import stroom.processor.impl.TaskStatusChange;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.FieldIndex;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class TestProcessorTaskDaoImpl extends AbstractProcessorTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProcessorTaskDaoImpl.class);

    @SuppressWarnings("deprecation") // ASSIGNED is deprecated but old rows may have it.
    private static final Set<TaskStatus> UNFINISHED_STATUSES = Set.of(
            TaskStatus.CREATED,
            TaskStatus.QUEUED,
            TaskStatus.ASSIGNED,
            TaskStatus.PROCESSING,
            TaskStatus.RETRY);

    Processor processor1;
    Processor processor2;
    Processor processor3;
    ProcessorFilter processorFilter1a;
    ProcessorFilter processorFilter1b;
    ProcessorFilter processorFilter2;
    ProcessorFilter processorFilter3;

    @Test
    void testReleaseOwnedTasks() {
        assertThat(getProcessorCount(null)).isZero();
        assertThat(countTasks()).isZero();
        assertThat(countOwned(NODE1)).isZero();
        assertThat(countOwned(NODE2)).isZero();

        processor1 = createProcessor();

        assertThat(getProcessorCount(null)).isOne();

        processorFilter1a = createProcessorFilter(processor1);
        assertThat(getProcessorFilterCount(null)).isOne();

        createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(countTasks()).isEqualTo(3);
        assertThat(countOwned(NODE1)).isEqualTo(3);

        processorTaskDao.releaseOwnedTasks(NODE1);

        assertThat(countTasks()).isEqualTo(3);
        assertThat(countOwned(NODE1)).isZero();
        assertThat(countOwned(null)).isEqualTo(3);

        createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE2, FEED);
        createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE2, FEED);
        createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE2, FEED);

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isZero();
        assertThat(countOwned(NODE2)).isEqualTo(3);
        assertThat(countOwned(null)).isEqualTo(3);

        processorTaskDao.releaseOwnedTasks(NODE1);

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isZero();
        assertThat(countOwned(NODE2)).isEqualTo(3);
        assertThat(countOwned(null)).isEqualTo(3);
    }

    /**
     * gh-5699. The reaper's condition: a PROCESSING task whose heartbeat has gone un-renewed past
     * the lease is dead and goes back to CREATED; anything else is left alone. The version bump is
     * the fence that stops the original owner writing over it afterwards.
     */
    @Test
    void testReapDeadTasks() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);

        final Instant now = Instant.now();
        final Instant stale = now.minus(20, ChronoUnit.MINUTES);
        final Instant fresh = now.minus(1, ChronoUnit.MINUTES);

        final long dead = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED, stale);
        final long alive = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED, fresh);
        final long queued = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED, stale);
        final long complete = createProcessorTask(processorFilter1a, TaskStatus.COMPLETE, NODE1, FEED, stale);
        final int deadVersionBefore = getTaskVersion(dead);

        assertThat(processorTaskDao.reapDeadTasks(now.minus(10, ChronoUnit.MINUTES))).isEqualTo(1);

        assertThat(getTaskStatus(dead)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskNodeId(dead))
                .describedAs("a reaped task must be unowned so any node can claim it")
                .isNull();
        assertThat(getTaskVersion(dead))
                .describedAs("the version bump is what fences the original owner")
                .isEqualTo(deadVersionBefore + 1);

        assertThat(getTaskStatus(alive))
                .describedAs("a heartbeat within the lease means the node is alive")
                .isEqualTo(TaskStatus.PROCESSING);
        assertThat(getTaskNodeId(alive)).isNotNull();
        assertThat(getTaskStatus(queued))
                .describedAs("only PROCESSING rows are dead task candidates")
                .isEqualTo(TaskStatus.QUEUED);
        assertThat(getTaskStatus(complete)).isEqualTo(TaskStatus.COMPLETE);
    }

    /**
     * gh-5699. A failed version check means we lost the lease, so the write is abandoned rather
     * than forced - forcing it is exactly how a half dead node stamps COMPLETE over a task another
     * node now owns.
     */
    @Test
    void testChangeTaskStatusAbandonsOnLostLease() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);

        final Instant now = Instant.now();
        final long taskId = createProcessorTask(
                processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED, now.minus(20, ChronoUnit.MINUTES));

        final ProcessorTask taskAsOwned = processorTaskDao.find(new ExpressionCriteria())
                .getValues()
                .getFirst();
        assertThat(taskAsOwned.getId()).isEqualTo(taskId);

        // The reaper takes the task from us: version bumps, so our copy is now stale.
        assertThat(processorTaskDao.reapDeadTasks(now.minus(10, ChronoUnit.MINUTES))).isEqualTo(1);

        // Our belated attempt to complete it must be abandoned...
        final TaskStatusChange result = processorTaskDao.changeTaskStatus(
                taskAsOwned, NODE1, TaskStatus.COMPLETE, now.toEpochMilli(), now.toEpochMilli());

        assertThat(result).isEqualTo(TaskStatusChange.lost());
        // ...leaving the row as the reaper set it, not stamped COMPLETE.
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskNodeId(taskId)).isNull();

        // A write with the current version still succeeds - abandonment is about lost leases,
        // not a general write freeze.
        final ProcessorTask current = processorTaskDao.find(new ExpressionCriteria())
                .getValues()
                .getFirst();
        final TaskStatusChange updated = processorTaskDao.changeTaskStatus(
                current, NODE1, TaskStatus.PROCESSING, now.toEpochMilli(), null);
        assertThat(updated.isDone()).isTrue();
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.PROCESSING);
    }

    @Test
    void testRunCountGoesUpOnlyWhenARunStarts() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.CREATED, null, FEED);
        assertThat(getTaskRunCount(taskId)).isZero();

        final ProcessorTask queued = processorTaskDao.queueTasks(Set.of(taskId), NODE1).getFirst();
        assertThat(queued.getRunCount()).isZero();

        // Started from the master's queue.
        final ProcessorTask processing = processorTaskDao.changeTaskStatus(
                queued, NODE1, TaskStatus.PROCESSING, 1L, null).task();
        assertThat(processing.getRunCount()).isOne();
        assertThat(getTaskRunCount(taskId)).isOne();

        // Nothing but starting a run changes it, and what is returned stays in step with the row.
        processorTaskDao.renewTaskHeartbeats(NODE1, Set.of(taskId), 2L);
        assertThat(getTaskRunCount(taskId)).isOne();
        final ProcessorTask retry = processorTaskDao.changeTaskStatus(
                processing, NODE1, TaskStatus.RETRY, 1L, 2L, "Broken", false).task();
        assertThat(retry.getRunCount()).isOne();
        assertThat(getTaskRunCount(taskId)).isOne();
        final ProcessorTask created = processorTaskDao.changeTaskStatus(
                retry, null, TaskStatus.CREATED, null, null).task();
        assertThat(created.getRunCount()).isOne();
        assertThat(getTaskRunCount(taskId)).isOne();

        // Started by a worker's claim.
        final ProcessorTask claimed = processorTaskDao.claimTasks(processorFilter1a.getId(), NODE2, 1).getFirst();
        assertThat(claimed.getRunCount()).isEqualTo(2);
        assertThat(getTaskRunCount(taskId)).isEqualTo(2);

        // Nothing here counts a fault.
        assertThat(claimed.getFaultCount()).isZero();
        assertThat(getTaskFaultCount(taskId)).isZero();
    }

    @Test
    void testRunCountDoesNotGoUpIfTheTaskWasLost() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.CREATED, null, FEED);
        final ProcessorTask queued = processorTaskDao.queueTasks(Set.of(taskId), NODE1).getFirst();
        assertThat(processorTaskDao.putBackTasks(List.of(queued), Set.of(TaskStatus.QUEUED), "Expected")).isOne();

        assertThat(processorTaskDao.changeTaskStatus(queued, NODE1, TaskStatus.PROCESSING, 1L, null))
                .isEqualTo(TaskStatusChange.lost());

        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskRunCount(taskId)).isZero();
    }

    @Test
    void testReasonIsSetAndClearedWithTheStatus() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);

        final ProcessorTask retry = processorTaskDao.changeTaskStatus(
                findTask(taskId), NODE1, TaskStatus.RETRY, 1L, 2L, "Broken", false).task();
        assertThat(retry.getReason()).isEqualTo("Broken");
        assertThat(getTaskReason(taskId)).isEqualTo("Broken");
        assertThat(findTask(taskId).getReason()).isEqualTo("Broken");

        final ProcessorTask created = processorTaskDao.changeTaskStatus(
                retry, null, TaskStatus.CREATED, null, null).task();
        assertThat(created.getReason()).isNull();
        assertThat(getTaskReason(taskId)).isNull();
    }

    @Test
    void testReasonIsTruncatedToFit() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);

        final ProcessorTask aborted = processorTaskDao.changeTaskStatus(
                findTask(taskId), NODE1, TaskStatus.ABORTED, 1L, 2L, "x".repeat(1_001), false).task();

        assertThat(aborted.getReason()).isEqualTo("x".repeat(1_000));
        assertThat(getTaskReason(taskId)).isEqualTo("x".repeat(1_000));
    }

    @Test
    void testFaultIsOnlyCountedIfAskedFor() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);

        final ProcessorTask counted = processorTaskDao.changeTaskStatus(
                findTask(taskId), NODE1, TaskStatus.RETRY, 1L, 2L, "Counted", true).task();
        assertThat(counted.getFaultCount()).isOne();
        assertThat(getTaskFaultCount(taskId)).isOne();

        final ProcessorTask notCounted = processorTaskDao.changeTaskStatus(
                counted, NODE1, TaskStatus.RETRY, 1L, 2L, "Not counted", false).task();
        assertThat(notCounted.getFaultCount()).isOne();
        assertThat(getTaskFaultCount(taskId)).isOne();
        assertThat(findTask(taskId).getFaultCount()).isOne();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"PROCESSING", "CREATED", "RETRY", "ABORTED", "COMPLETE"})
    void testStatusChangeWhoseReplyIsLostIsDone(final TaskStatus status) {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED);
        final ProcessorTask task = findTask(taskId);
        final ProcessorTaskDaoImpl dao = Mockito.spy((ProcessorTaskDaoImpl) processorTaskDao);
        Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new DataAccessException("Expected: connection lost after update");
        }).doCallRealMethod().when(dao).updateProcessorTask(Mockito.any(), Mockito.any(), Mockito.any());

        final String nodeName = TaskStatus.CREATED.equals(status)
                ? null
                : NODE1;
        final TaskStatusChange change = dao.changeTaskStatus(task, nodeName, status, 1L, 2L, "Reason", true);

        assertThat(change.isDone()).isTrue();
        assertThat(change.task().getVersion()).isEqualTo(getTaskVersion(taskId)).isEqualTo(task.getVersion() + 1);
        assertThat(change.task().getRunCount()).isEqualTo(getTaskRunCount(taskId));
        assertThat(getTaskStatus(taskId)).isEqualTo(status);
        // Once, though it was tried twice.
        assertThat(getTaskFaultCount(taskId)).isOne();
        Mockito.verify(dao, Mockito.times(2)).updateProcessorTask(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void testStatusChangeIsLostIfTheTaskIsTakenBeforeItIsTriedAgain() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);
        final ProcessorTaskDaoImpl dao = Mockito.spy((ProcessorTaskDaoImpl) processorTaskDao);
        Mockito.doAnswer(invocation -> {
            // The update isn't made, and another node completes the task.
            processorTaskDao.changeTaskStatus(findTask(taskId), NODE2, TaskStatus.COMPLETE, 1L, 2L);
            throw new DataAccessException("Expected");
        }).doCallRealMethod().when(dao).updateProcessorTask(Mockito.any(), Mockito.any(), Mockito.any());

        assertThat(dao.changeTaskStatus(findTask(taskId), NODE1, TaskStatus.COMPLETE, 1L, 2L))
                .isEqualTo(TaskStatusChange.lost());
    }

    @Test
    void testStatusChangeIsUnknownIfTheDatabaseCannotBeReached() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);
        final ProcessorTaskDaoImpl dao = Mockito.spy((ProcessorTaskDaoImpl) processorTaskDao);
        Mockito.doThrow(new DataAccessException("Expected"))
                .when(dao).updateProcessorTask(Mockito.any(), Mockito.any(), Mockito.any());

        assertThat(dao.changeTaskStatus(findTask(taskId), NODE1, TaskStatus.COMPLETE, 1L, 2L))
                .isEqualTo(TaskStatusChange.unknown());
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.PROCESSING);
    }

    @Test
    void testStatusIsChangedByAnInterruptedThread() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);
        final ProcessorTask task = findTask(taskId);
        final ProcessorTaskDaoImpl dao = Mockito.spy((ProcessorTaskDaoImpl) processorTaskDao);
        Mockito.doAnswer(invocation -> {
            // Interrupted during the first try too, which fails.
            Thread.currentThread().interrupt();
            throw new DataAccessException("Expected");
        }).doCallRealMethod().when(dao).updateProcessorTask(Mockito.any(), Mockito.any(), Mockito.any());

        final TaskStatusChange change;
        final boolean interrupted;
        try {
            Thread.currentThread().interrupt();
            change = dao.changeTaskStatus(task, null, TaskStatus.CREATED, null, null);
        } finally {
            interrupted = Thread.interrupted();
        }

        assertThat(change.isDone()).isTrue();
        assertThat(interrupted)
                .describedAs("the interrupt is passed on once the status has been changed")
                .isTrue();
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.CREATED);
    }

    @Test
    void testPutBackTasksOnlyPutsBackThoseThatHaveNotChanged() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long unchanged = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED);
        final long started = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED);
        final long otherStatus = createProcessorTask(processorFilter1a, TaskStatus.COMPLETE, NODE1, FEED);
        final List<ProcessorTask> tasks = processorTaskDao.find(new ExpressionCriteria()).getValues();
        processorTaskDao.changeTaskStatus(findTask(started), NODE1, TaskStatus.PROCESSING, 1L, null);

        assertThat(processorTaskDao.putBackTasks(tasks, Set.of(TaskStatus.QUEUED, TaskStatus.PROCESSING), "Expected"))
                .isOne();

        assertThat(getTaskStatus(unchanged)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskNodeId(unchanged)).isNull();
        assertThat(getTaskStatus(started)).isEqualTo(TaskStatus.PROCESSING);
        assertThat(getTaskStatus(otherStatus)).isEqualTo(TaskStatus.COMPLETE);
        assertThat(processorTaskDao.putBackTasks(List.of(), Set.of(TaskStatus.QUEUED), "Expected")).isZero();
    }

    @Test
    void testPutBackTasksGiveTheirReasonOnlyToTasksThatHaveNone() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long withReason = createTaskWithReason(processorFilter1a);
        processorTaskDao.putBackRetryTasks(processorFilter1a.getId(), Instant.now().plusSeconds(1), 10);
        final long withoutReason = createProcessorTask(processorFilter1a, TaskStatus.CREATED, null, FEED);
        final List<ProcessorTask> queued = processorTaskDao.queueTasks(Set.of(withReason, withoutReason), NODE1);

        assertThat(processorTaskDao.putBackTasks(queued, Set.of(TaskStatus.QUEUED), "Master stopped")).isEqualTo(2);

        assertThat(getTaskReason(withReason)).isEqualTo("Broken");
        assertThat(getTaskReason(withoutReason)).isEqualTo("Master stopped");
    }

    @Test
    void testPutBackTasksInBatches() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        for (int i = 0; i < 1_001; i++) {
            createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED);
        }
        final List<ProcessorTask> tasks = processorTaskDao.find(new ExpressionCriteria()).getValues();

        assertThat(processorTaskDao.putBackTasks(tasks, Set.of(TaskStatus.QUEUED), "Expected")).isEqualTo(1_001);
    }

    @Test
    void testPutBackUnstartedTaskThatWasNotStarted() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE2, FEED);

        assertThat(processorTaskDao.putBackUnstarted(findTask(taskId), NODE1)).isTrue();

        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskReason(taskId)).isEqualTo(ProcessorTaskDaoImpl.REASON_NOT_STARTED);
    }

    @Test
    void testPutBackUnstartedTaskThatWasStarted() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE2, FEED);
        final ProcessorTask queued = findTask(taskId);
        // The reply to this was lost.
        processorTaskDao.changeTaskStatus(queued, NODE1, TaskStatus.PROCESSING, 1L, null);

        // Not by another node.
        assertThat(processorTaskDao.putBackUnstarted(queued, NODE2)).isFalse();
        assertThat(processorTaskDao.putBackUnstarted(queued, NODE1)).isTrue();

        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskNodeId(taskId)).isNull();
    }

    @Test
    void testStartOfATaskThatHasBeenPutBackIsRefused() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE2, FEED);
        final ProcessorTask handedOut = findTask(taskId);
        processorTaskDao.putBackTasks(List.of(handedOut), Set.of(TaskStatus.QUEUED), "Expected");

        assertThat(processorTaskDao.changeTaskStatus(handedOut, NODE1, TaskStatus.PROCESSING, 1L, null))
                .isEqualTo(TaskStatusChange.lost());
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.CREATED);
    }

    @Test
    void testPutBackUnstartedTaskThatHasChangedSince() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE2, FEED);
        final ProcessorTask queued = findTask(taskId);
        final ProcessorTask started = processorTaskDao.changeTaskStatus(
                queued, NODE1, TaskStatus.PROCESSING, 1L, null).task();
        // It has been reaped and started again since.
        processorTaskDao.reapDeadTasks(Instant.now().plusSeconds(1));
        processorTaskDao.changeTaskStatus(findTask(taskId), NODE1, TaskStatus.PROCESSING, 1L, null);

        assertThat(processorTaskDao.putBackUnstarted(queued, NODE1)).isFalse();
        assertThat(processorTaskDao.putBackUnstarted(started, NODE1)).isFalse();

        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.PROCESSING);
    }

    @Test
    void testFindQueuedTasks() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final Instant now = Instant.now();
        final Instant threshold = now.minus(10, ChronoUnit.MINUTES);
        final long old = createProcessorTask(
                processorFilter1a, TaskStatus.QUEUED, NODE1, FEED, threshold.minusMillis(1));
        createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED, threshold);
        createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE2, FEED, threshold.minusMillis(1));
        createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED, threshold.minusMillis(1));

        final long older = createProcessorTask(
                processorFilter1a, TaskStatus.QUEUED, NODE1, FEED, threshold.minusMillis(1));
        assertThat(processorTaskDao.findQueuedTasks(NODE1, threshold, -1, 10))
                .extracting(ProcessorTask::getId)
                .containsExactly(old, older);
        assertThat(processorTaskDao.findQueuedTasks(NODE1, threshold, -1, 1))
                .extracting(ProcessorTask::getId)
                .containsExactly(old);
        assertThat(processorTaskDao.findQueuedTasks(NODE1, threshold, old, 10))
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.getId()).isEqualTo(older);
                    assertThat(task.getVersion()).isEqualTo(getTaskVersion(older));
                });
        assertThat(processorTaskDao.findQueuedTasks(NODE1, threshold, -1, 0)).isEmpty();
    }

    @Test
    void testSweepQueuedTasksOfInactiveNodes() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final Instant now = Instant.now();
        final Instant threshold = now.minus(10, ChronoUnit.MINUTES);
        final Instant old = threshold.minusMillis(1);
        final long otherNode = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE2, FEED, old);
        final long master = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED, old);
        final long active = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, "node3", FEED, old);
        final long recent = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE2, FEED, now);
        final long processing = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE2, FEED, old);
        final long noNode = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, null, FEED, old);

        assertThat(processorTaskDao.sweepQueuedTasksOfInactiveNodes(threshold, Set.of(NODE1, "node3")))
                .isEqualTo(2);

        assertThat(getTaskStatus(otherNode)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskReason(otherNode)).isEqualTo(ProcessorTaskDaoImpl.REASON_MASTER_GONE);
        assertThat(getTaskStatus(noNode)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskStatus(master)).isEqualTo(TaskStatus.QUEUED);
        assertThat(getTaskStatus(active)).isEqualTo(TaskStatus.QUEUED);
        assertThat(getTaskStatus(recent)).isEqualTo(TaskStatus.QUEUED);
        assertThat(getTaskStatus(processing)).isEqualTo(TaskStatus.PROCESSING);
        assertThat(processorTaskDao.sweepQueuedTasksOfInactiveNodes(threshold, Set.of(NODE1, "node3"))).isZero();
    }

    @Test
    void testFindProcessingVersions() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final Map<TaskStatus, Long> tasks = EnumSet.allOf(TaskStatus.class)
                .stream()
                .collect(Collectors.toMap(Function.identity(), status ->
                        createProcessorTask(processorFilter1a, status, NODE1, FEED)));
        final long processing = tasks.get(TaskStatus.PROCESSING);
        final long otherNode = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE2, FEED);
        final long notAskedFor = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);
        final Set<Long> taskIds = new HashSet<>(tasks.values());
        taskIds.add(otherNode);
        taskIds.add(Long.MAX_VALUE);

        assertThat(processorTaskDao.findProcessingVersions(NODE1, taskIds))
                .doesNotContainKey(notAskedFor)
                .isEqualTo(Map.of(processing, getTaskVersion(processing)));
        assertThat(processorTaskDao.findProcessingVersions(NODE1, Set.of())).isEmpty();
    }

    @Test
    void testFindProcessingVersionsOfMoreTasksThanFitInABatch() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);
        final List<Long> taskIds = new ArrayList<>();
        for (long i = 1; i <= 5_000; i++) {
            taskIds.add(taskId + i);
        }
        taskIds.add(taskId);

        assertThat(processorTaskDao.findProcessingVersions(NODE1, taskIds)).containsOnlyKeys(taskId);
    }

    @Test
    void testRetryTasksArePutBackOnceTheyHaveWaited() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final Instant now = Instant.now();
        final Instant threshold = now.minus(10, ChronoUnit.MINUTES);
        final long newest = createProcessorTask(
                processorFilter1a, TaskStatus.RETRY, NODE1, FEED, now.minus(11, ChronoUnit.MINUTES));
        final long oldest = createProcessorTask(
                processorFilter1a, TaskStatus.RETRY, NODE1, FEED, now.minus(12, ChronoUnit.MINUTES));
        final long waiting = createProcessorTask(
                processorFilter1a, TaskStatus.RETRY, NODE1, FEED, threshold);
        final Set<TaskStatus> others = EnumSet.complementOf(EnumSet.of(TaskStatus.RETRY));
        final Map<TaskStatus, Long> otherTasks = others
                .stream()
                .collect(Collectors.toMap(Function.identity(), status -> createProcessorTask(
                        processorFilter1a, status, NODE1, FEED, now.minus(1, ChronoUnit.DAYS))));
        final int version = getTaskVersion(oldest);

        // The one that has waited longest goes first.
        assertThat(processorTaskDao.putBackRetryTasks(processorFilter1a.getId(), threshold, 1)).isOne();
        assertThat(getTaskStatus(oldest)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskNodeId(oldest)).isNull();
        assertThat(getTaskVersion(oldest)).isEqualTo(version + 1);
        assertThat(getTaskStatus(newest)).isEqualTo(TaskStatus.RETRY);

        assertThat(processorTaskDao.putBackRetryTasks(processorFilter1a.getId(), threshold, 10)).isOne();
        assertThat(getTaskStatus(newest)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskStatus(waiting)).isEqualTo(TaskStatus.RETRY);
        otherTasks.forEach((status, taskId) -> assertThat(getTaskStatus(taskId)).isEqualTo(status));
        assertThat(processorTaskDao.putBackRetryTasks(processorFilter1a.getId(), threshold, 10)).isZero();
    }

    @Test
    void testRetryTasksOfOneFilterDoNotHoldUpThoseOfAnother() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        processorFilter1b = createProcessorFilter(processor1);
        final Instant now = Instant.now();
        final Instant threshold = now.minus(10, ChronoUnit.MINUTES);
        for (int i = 0; i < 2; i++) {
            createProcessorTask(processorFilter1a, TaskStatus.RETRY, NODE1, FEED, now.minus(1, ChronoUnit.DAYS));
        }
        final long taskId = createProcessorTask(
                processorFilter1b, TaskStatus.RETRY, NODE1, FEED, now.minus(11, ChronoUnit.MINUTES));

        assertThat(processorTaskDao.putBackRetryTasks(processorFilter1b.getId(), threshold, 1)).isOne();

        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.CREATED);
        assertThat(processorTaskDao.countRetryTasksByFilter()).isEqualTo(Map.of(processorFilter1a.getId(), 2));
    }

    @Test
    void testRetryTasksKeepTheirFaultsAndReasonWhenPutBack() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);
        processorTaskDao.changeTaskStatus(findTask(taskId), NODE1, TaskStatus.RETRY, 1L, 2L, "Broken", true);

        assertThat(processorTaskDao.putBackRetryTasks(
                processorFilter1a.getId(), Instant.now().plus(1, ChronoUnit.MINUTES), 10)).isOne();

        final ProcessorTask task = findTask(taskId);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CREATED);
        assertThat(task.getFaultCount()).isOne();
        assertThat(task.getReason()).isEqualTo("Broken");
        assertThat(task.getStartTimeMs()).isNull();
        assertThat(task.getEndTimeMs()).isNull();
    }

    @Test
    void testRetryTasksAreCountedByFilter() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        processorFilter1b = createProcessorFilter(processor1);
        assertThat(processorTaskDao.countRetryTasksByFilter()).isEmpty();
        assertThat(processorTaskDao.findLatestRetryReason(processorFilter1a.getId())).isEmpty();

        for (final String reason : List.of("First", "Last")) {
            final long taskId = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);
            processorTaskDao.changeTaskStatus(findTask(taskId), NODE1, TaskStatus.RETRY, 1L, 2L, reason, false);
        }
        createProcessorTask(processorFilter1a, TaskStatus.CREATED, null, FEED);
        createProcessorTask(processorFilter1b, TaskStatus.RETRY, NODE1, FEED, Instant.now());

        assertThat(processorTaskDao.countRetryTasksByFilter())
                .isEqualTo(Map.of(processorFilter1a.getId(), 2, processorFilter1b.getId(), 1));
        assertThat(processorTaskDao.findLatestRetryReason(processorFilter1a.getId())).contains("Last");
        assertThat(processorTaskDao.findLatestRetryReason(processorFilter1b.getId())).isEmpty();
    }

    @Test
    void testTasksPutBackSayWhyUntilTheyAreRun() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final Instant now = Instant.now();
        final Instant stale = now.minus(20, ChronoUnit.MINUTES);
        final Instant threshold = now.minus(10, ChronoUnit.MINUTES);
        final long reaped = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED, stale);
        final long swept = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED, stale);
        final long owned = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE2, FEED, now);

        processorTaskDao.reapDeadTasks(threshold);
        processorTaskDao.sweepQueuedTasks(threshold);
        processorTaskDao.releaseOwnedTasks(NODE2);

        assertThat(getTaskReason(reaped)).isEqualTo(ProcessorTaskDaoImpl.REASON_NO_HEARTBEAT);
        assertThat(getTaskReason(swept)).isEqualTo(ProcessorTaskDaoImpl.REASON_LEFT_QUEUED);
        assertThat(getTaskReason(owned)).isEqualTo(ProcessorTaskDaoImpl.REASON_NODE_STARTED);

        // Being queued by the master, and taken off its queue again, isn't being run.
        assertThat(processorTaskDao.queueTasks(Set.of(swept, owned), NODE1))
                .extracting(ProcessorTask::getReason)
                .containsExactlyInAnyOrder(
                        ProcessorTaskDaoImpl.REASON_LEFT_QUEUED,
                        ProcessorTaskDaoImpl.REASON_NODE_STARTED);
        assertThat(processorTaskDao.putBackTasks(
                processorTaskDao.queueTasks(Set.of(swept, owned), NODE1), Set.of(TaskStatus.QUEUED), "Expected"))
                .isZero();
        assertThat(processorTaskDao.putBackTasks(
                processorTaskDao.find(new ExpressionCriteria())
                        .stream()
                        .filter(task -> Set.of(swept, owned).contains(task.getId()))
                        .toList(),
                Set.of(TaskStatus.QUEUED), "Expected")).isEqualTo(2);
        assertThat(getTaskReason(swept)).isEqualTo(ProcessorTaskDaoImpl.REASON_LEFT_QUEUED);
        assertThat(getTaskReason(owned)).isEqualTo(ProcessorTaskDaoImpl.REASON_NODE_STARTED);

        // A claim may be given back without being run, so it keeps the reason too.
        final List<ProcessorTask> claimed = processorTaskDao.claimTasks(processorFilter1a.getId(), NODE1, 3);
        assertThat(claimed).hasSize(3).allSatisfy(task -> assertThat(task.getReason()).isNotNull());
        assertThat(processorTaskDao.putBackTasks(claimed, Set.of(TaskStatus.PROCESSING), "Given up")).isEqualTo(3);
        assertThat(getTaskReason(reaped)).isEqualTo(ProcessorTaskDaoImpl.REASON_NO_HEARTBEAT);

        // The run sets its own when it ends.
        final ProcessorTask run = processorTaskDao.claimTasks(processorFilter1a.getId(), NODE1, 3)
                .stream()
                .filter(task -> task.getId() == reaped)
                .findFirst()
                .orElseThrow();
        processorTaskDao.changeTaskStatus(run, NODE1, TaskStatus.COMPLETE, 1L, 2L, null, false);
        assertThat(getTaskReason(reaped)).isNull();
    }

    @Test
    void testWaitingCommittingAndAbortedTasksAreNotPutBack() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final Instant now = Instant.now();
        final Instant stale = now.minus(20, ChronoUnit.MINUTES);
        final Set<TaskStatus> statuses = Set.of(TaskStatus.RETRY, TaskStatus.COMMITTING, TaskStatus.ABORTED);
        statuses.forEach(status -> createProcessorTask(processorFilter1a, status, NODE1, FEED, stale));
        final Set<Long> taskIds = processorTaskDao.find(new ExpressionCriteria())
                .stream()
                .map(ProcessorTask::getId)
                .collect(Collectors.toSet());

        assertThat(processorTaskDao.reapDeadTasks(now)).isZero();
        assertThat(processorTaskDao.sweepQueuedTasks(now)).isZero();
        assertThat(processorTaskDao.releaseOwnedTasks(NODE1)).isZero();
        assertThat(processorTaskDao.renewTaskHeartbeats(NODE1, taskIds, now.toEpochMilli())).isZero();
        assertThat(processorTaskDao.claimTasks(processorFilter1a.getId(), NODE1, 10)).isEmpty();
        assertThat(processorTaskDao.queueTasks(taskIds, NODE1)).isEmpty();
        assertThat(processorTaskDao.getTaskAvailability(Set.of(processorFilter1a.getId()))).isEmpty();

        assertThat(processorTaskDao.find(new ExpressionCriteria()).getValues())
                .extracting(ProcessorTask::getStatus)
                .containsExactlyInAnyOrderElementsOf(statuses);
    }

    @Test
    void testDeletingAProcessorDeletesOnlyUnfinishedTasks() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final Map<TaskStatus, Long> taskIds = createTaskOfEachStatus(processorFilter1a);

        assertThat(processorTaskDao.logicalDeleteByProcessorId(processor1.getId()))
                .isEqualTo(UNFINISHED_STATUSES.size());

        assertOnlyUnfinishedDeleted(taskIds);
    }

    @Test
    void testDeletingAFilterDeletesOnlyUnfinishedTasks() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final Map<TaskStatus, Long> taskIds = createTaskOfEachStatus(processorFilter1a);

        assertThat(processorTaskDao.logicalDeleteByProcessorFilterId(processorFilter1a.getId()))
                .isEqualTo(UNFINISHED_STATUSES.size());

        assertOnlyUnfinishedDeleted(taskIds);
    }

    @Test
    void testTasksOfALongDeletedFilterAreDeletedWhateverTheirStatus() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final Map<TaskStatus, Long> taskIds = createTaskOfEachStatus(processorFilter1a);
        processorFilterDao.update(processorFilter1a.copy()
                .deleted(true)
                .updateTimeMs(Instant.now().toEpochMilli())
                .build());

        processorTaskDao.logicalDeleteForDeletedProcessorFilters(Instant.now().plus(1, ChronoUnit.DAYS));

        assertThat(taskIds.values())
                .allSatisfy(taskId -> assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.DELETED));
    }

    @Test
    void testReasonIsClearedByLogicalDeletion() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);

        final long deletedWithFilter = createTaskWithReason(processorFilter1a);
        processorTaskDao.logicalDeleteByProcessorFilterId(processorFilter1a.getId());
        assertDeletedWithoutReason(deletedWithFilter);

        final long deletedWithProcessor = createTaskWithReason(processorFilter1a);
        processorTaskDao.logicalDeleteByProcessorId(processor1.getId());
        assertDeletedWithoutReason(deletedWithProcessor);

        final long deletedLater = createTaskWithReason(processorFilter1a);
        processorFilterDao.update(processorFilter1a.copy()
                .deleted(true)
                .updateTimeMs(Instant.now().toEpochMilli())
                .build());
        processorTaskDao.logicalDeleteForDeletedProcessorFilters(Instant.now().plus(1, ChronoUnit.DAYS));
        assertDeletedWithoutReason(deletedLater);
    }

    @Test
    void testOldTasksAreAgedOffByStatus() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final Map<TaskStatus, Long> taskIds = createTaskOfEachStatus(processorFilter1a);
        final Instant afterAll = Instant.now().plus(1, ChronoUnit.DAYS);

        assertThat(processorTaskDao.physicallyDeleteOldTasks(afterAll)).isEqualTo(2);
        assertThat(remainingStatuses(taskIds))
                .doesNotContain(TaskStatus.COMPLETE, TaskStatus.DELETED)
                .contains(TaskStatus.ABORTED, TaskStatus.RETRY, TaskStatus.COMMITTING);

        // Only ones that were aborted before the threshold.
        assertThat(processorTaskDao.physicallyDeleteOldAbortedTasks(Instant.now().minus(1, ChronoUnit.DAYS)))
                .isZero();
        assertThat(processorTaskDao.physicallyDeleteOldAbortedTasks(afterAll)).isOne();
        assertThat(remainingStatuses(taskIds))
                .doesNotContain(TaskStatus.ABORTED)
                .containsAll(UNFINISHED_STATUSES)
                .contains(TaskStatus.COMMITTING);
    }

    @Test
    void testHeldTaskIsCompletedOnlyByTheRunThatHeldIt() {
        final long taskId = createHeldTask();

        assertThat(processorTaskDao.completeHeld(taskId, 2)).isFalse();
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.COMMITTING);

        assertThat(processorTaskDao.completeHeld(taskId, 1)).isTrue();
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.COMPLETE);
        assertThat(getTaskRunCount(taskId)).isOne();
        // Its run is kept, as it is that run that has been completed.
        final ProcessorTask task = findTask(taskId);
        assertThat(task.getNodeName()).isEqualTo(NODE1);
        assertThat(task.getStartTimeMs()).isEqualTo(1L);
        assertThat(task.getEndTimeMs()).isEqualTo(2L);

        // Not twice.
        assertThat(processorTaskDao.completeHeld(taskId, 1)).isFalse();
    }

    @Test
    void testHeldTaskIsPutBackOnlyByTheRunThatHeldIt() {
        final long taskId = createHeldTask();

        assertThat(processorTaskDao.putBackHeld(taskId, 2, "Lost")).isFalse();
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.COMMITTING);

        assertThat(processorTaskDao.putBackHeld(taskId, 1, "Lost")).isTrue();
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskReason(taskId)).isEqualTo("Lost");
        assertThat(getTaskNodeId(taskId)).isNull();
        assertThat(getTaskFaultCount(taskId)).isZero();

        assertThat(processorTaskDao.putBackHeld(taskId, 1, "Lost")).isFalse();
        assertThat(processorTaskDao.completeHeld(taskId, 1)).isFalse();
    }

    @Test
    void testTaskThatIsNotHeldIsNeitherCompletedNorPutBack() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED);
        processorTaskDao.changeTaskStatus(findTask(taskId), NODE1, TaskStatus.PROCESSING, 1L, null);

        assertThat(processorTaskDao.completeHeld(taskId, 1)).isFalse();
        assertThat(processorTaskDao.putBackHeld(taskId, 1, "Lost")).isFalse();
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.PROCESSING);
    }

    @Test
    void testGetStates() {
        final long held = createHeldTask();
        final long created = createProcessorTask(processorFilter1a, TaskStatus.CREATED, null, FEED);
        // More than fit in a batch, of which only two exist.
        final List<Long> taskIds = LongStream.range(0, 2_500)
                .map(i -> 1_000_000 + i)
                .boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        taskIds.add(held);
        taskIds.add(created);
        // The same task twice.
        taskIds.add(held);

        final Map<Long, ProcessorTaskState> states = processorTaskDao.getStates(taskIds);

        assertThat(states).hasSize(2_502);
        assertThat(states.get(held)).isEqualTo(new ProcessorTaskState(held, TaskStatus.COMMITTING, 1));
        assertThat(states.get(created)).isEqualTo(new ProcessorTaskState(created, TaskStatus.CREATED, 0));
        assertThat(states.get(1_000_000L)).isEqualTo(new ProcessorTaskState(1_000_000L, TaskStatus.DELETED, 0));
        assertThat(processorTaskDao.getStates(List.of())).isEmpty();
    }

    @Test
    void testFindHeld() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final Instant threshold = Instant.now().minus(10, ChronoUnit.MINUTES);
        final Instant old = threshold.minusMillis(1);
        final long first = createProcessorTask(processorFilter1a, TaskStatus.COMMITTING, NODE1, FEED, old);
        createProcessorTask(processorFilter1a, TaskStatus.COMMITTING, NODE1, FEED, threshold);
        createProcessorTask(processorFilter1a, TaskStatus.COMPLETE, NODE1, FEED, old);
        createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED, old);
        final long second = createProcessorTask(processorFilter1a, TaskStatus.COMMITTING, NODE2, FEED, old);

        assertThat(processorTaskDao.findHeld(threshold, -1, 10))
                .extracting(ProcessorTaskState::taskId)
                .containsExactly(first, second);
        assertThat(processorTaskDao.findHeld(threshold, -1, 1))
                .extracting(ProcessorTaskState::taskId)
                .containsExactly(first);
        assertThat(processorTaskDao.findHeld(threshold, first, 10))
                .containsExactly(new ProcessorTaskState(second, TaskStatus.COMMITTING, 0));
    }

    // A task that run 1 has held as COMMITTING, with a start time of 1 and an end time of 2.
    private long createHeldTask() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED);
        final ProcessorTask started = processorTaskDao.changeTaskStatus(
                findTask(taskId), NODE1, TaskStatus.PROCESSING, 1L, null).task();
        processorTaskDao.changeTaskStatus(started, NODE1, TaskStatus.COMMITTING, 1L, 2L, null, false);
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.COMMITTING);
        return taskId;
    }

    private long createTaskWithReason(final ProcessorFilter filter) {
        final long taskId = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED);
        processorTaskDao.changeTaskStatus(findTask(taskId), NODE1, TaskStatus.RETRY, 1L, 2L, "Broken", false);
        assertThat(getTaskReason(taskId)).isEqualTo("Broken");
        return taskId;
    }

    private void assertDeletedWithoutReason(final long taskId) {
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.DELETED);
        assertThat(getTaskReason(taskId)).isNull();
    }

    private Map<TaskStatus, Long> createTaskOfEachStatus(final ProcessorFilter filter) {
        return Arrays.stream(TaskStatus.values())
                .collect(Collectors.toMap(
                        Function.identity(),
                        status -> createProcessorTask(filter, status, NODE1, FEED)));
    }

    private void assertOnlyUnfinishedDeleted(final Map<TaskStatus, Long> taskIds) {
        taskIds.forEach((status, taskId) ->
                assertThat(getTaskStatus(taskId))
                        .describedAs("task that was %s", status)
                        .isEqualTo(UNFINISHED_STATUSES.contains(status)
                                ? TaskStatus.DELETED
                                : status));
    }

    private Set<TaskStatus> remainingStatuses(final Map<TaskStatus, Long> taskIds) {
        return taskIds.entrySet()
                .stream()
                .filter(entry -> getProcessorTaskCount(PROCESSOR_TASK.ID.eq(entry.getValue())) > 0)
                .map(Entry::getKey)
                .collect(Collectors.toSet());
    }

    private ProcessorTask findTask(final long taskId) {
        return processorTaskDao.find(new ExpressionCriteria())
                .stream()
                .filter(task -> task.getId() == taskId)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void testLogicalDeleteByProcessorId() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        processor1 = createProcessor();
        processor2 = createProcessor();

        processorFilter1a = createProcessorFilter(processor1);
        createTasksForFilter(processorFilter1a);

        processorFilter1b = createProcessorFilter(processor1);
        createTasksForFilter(processorFilter1b);

        processorFilter2 = createProcessorFilter(processor2);
        createTasksForFilter(processorFilter2);

        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(15);

        dumpProcessorTaskTable();

        final int count = processorTaskDao.logicalDeleteByProcessorId(processor1.getId());
        assertThat(count).isEqualTo(8);

        dumpProcessorTaskTable();

        // No change to row counts as they have been logically deleted
        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(15);

        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(0);
        // logically deletes the CREATED, QUEUED and ASSIGNED for each of two filters on one processor
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(8);
    }

    private void createTasksForFilter(final ProcessorFilter filter) {
        createProcessorTask(filter, TaskStatus.CREATED, null, FEED);
        createProcessorTask(filter, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(filter, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED);
        createProcessorTask(filter, TaskStatus.COMPLETE, NODE1, FEED);
    }

    @Test
    void testLogicalDeleteByProcessorFilterId() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        processor1 = createProcessor();
        processor2 = createProcessor();

        processorFilter1a = createProcessorFilter(processor1);
        createTasksForFilter(processorFilter1a);

        processorFilter1b = createProcessorFilter(processor1);
        createTasksForFilter(processorFilter1b);

        processorFilter2 = createProcessorFilter(processor2);
        createTasksForFilter(processorFilter2);

        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(15);

        dumpProcessorTaskTable();

        final int count = processorTaskDao.logicalDeleteByProcessorFilterId(processorFilter1a.getId());
        assertThat(count).isEqualTo(4);

        dumpProcessorTaskTable();

        // No change to row counts as they have been logically deleted
        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(15);

        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(0);
        // logically deletes the CREATED, QUEUED and ASSIGNED for one processor
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(4);
    }

    @Test
    void testLogicalDeleteForDeletedProcessorFilters() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        processor1 = createProcessor();
        processor2 = createProcessor();

        processorFilter1a = createProcessorFilter(processor1);
        createProcessorTask(processorFilter1a, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);

        processorFilter1a = processorFilter1a.copy()
                .deleted(true)
                .updateTimeMs(Instant.now().toEpochMilli())
                .build();
        processorFilterDao.update(processorFilter1a);

        processorFilter1b = createProcessorFilter(processor1);
        createProcessorTask(processorFilter1b, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter1b, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter1b, TaskStatus.PROCESSING, NODE1, FEED);

        processorFilter2 = createProcessorFilter(processor2);
        createProcessorTask(processorFilter2, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(9);

        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(1);

        dumpProcessorTaskTable();

        final Instant threshold = Instant.now().plus(1, ChronoUnit.DAYS);

        processorTaskDao.logicalDeleteForDeletedProcessorFilters(threshold);

        dumpProcessorTaskTable();

        // No change to row counts as they have been logically deleted
        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(9);

        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(1);
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(3);
    }

    void setup() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        processor1 = createProcessor();
        processor2 = createProcessor();

        processorFilter1a = createProcessorFilter(processor1);
        createProcessorTask(processorFilter1a, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);

        processorFilter1b = createProcessorFilter(processor1);
        createProcessorTask(processorFilter1b, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter1b, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter1b, TaskStatus.PROCESSING, NODE1, FEED);
        createProcessorTask(processorFilter1b, TaskStatus.COMPLETE, NODE1, FEED);
        createProcessorTask(processorFilter1b, TaskStatus.DELETED, NODE1, FEED);
    }

    @Test
    void testPhysicallyDeleteOldTasks() {
        setup();

        // These two are older than the threshold time, the rest are not
        createProcessorTask(processorFilter1b,
                TaskStatus.COMPLETE,
                NODE1,
                FEED,
                Instant.now().minus(2, ChronoUnit.DAYS));
        createProcessorTask(
                processorFilter1b,
                TaskStatus.DELETED,
                NODE1,
                FEED,
                Instant.now().minus(2, ChronoUnit.DAYS));

        processorFilter2 = createProcessorFilter(processor2);
        createProcessorTask(processorFilter2, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(13);

        dumpProcessorTaskTable();

        final Instant threshold = Instant.now().minus(1, ChronoUnit.DAYS);

        processorTaskDao.physicallyDeleteOldTasks(threshold);

        dumpProcessorTaskTable();

        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(13 - 2);

        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(1);
    }

    @Test
    void testTrackerDoesNotMoveBackwards() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);

        // A poll that creates no tasks moves the tracker on to just past the max meta id it was given.
        assertThat(pollWithNoTasks(processorFilter1a, 99L).getMinMetaId())
                .isEqualTo(100);

        // Simulate a filter that has part processed the events within a stream.
        final ProcessorFilterTracker tracker = fetchTracker(processorFilter1a);
        tracker.setMinEventId(5L);
        processorFilterTrackerDao.update(tracker);

        // A poll bounded by a lower max meta id must leave the tracker alone. Winding it back would
        // re-scan meta we have already created tasks for, and there is no unique constraint on
        // (filter, meta) to stop the duplicates.
        ProcessorFilterTracker result = pollWithNoTasks(processorFilter1a, 49L);
        assertThat(result.getMinMetaId()).isEqualTo(100);
        assertThat(result.getMinEventId()).isEqualTo(5);

        // The same max meta id is not greater either, so it must not reset the event position within
        // the stream we are part way through.
        result = pollWithNoTasks(processorFilter1a, 99L);
        assertThat(result.getMinMetaId()).isEqualTo(100);
        assertThat(result.getMinEventId()).isEqualTo(5);

        // A higher max meta id still moves the tracker on, and starts the new stream from its first event.
        result = pollWithNoTasks(processorFilter1a, 149L);
        assertThat(result.getMinMetaId()).isEqualTo(150);
        assertThat(result.getMinEventId()).isZero();
    }

    /**
     * Create tasks for a filter that has no meta to create tasks for, returning the persisted tracker.
     */
    private ProcessorFilterTracker pollWithNoTasks(final ProcessorFilter filter,
                                                   final long maxMetaId) {
        final FilterProgressMonitor filterProgressMonitor = new ProgressMonitor(1)
                .logFilter(filter, 0);
        // Re-fetch the tracker as each poll would, so that its version matches the DB.
        final int createdTasks = processorTaskDao.createNewTasks(
                filter,
                fetchTracker(filter),
                filterProgressMonitor,
                System.currentTimeMillis(),
                Map.of(),
                maxMetaId,
                false);
        assertThat(createdTasks).isZero();
        return fetchTracker(filter);
    }

    private ProcessorFilterTracker fetchTracker(final ProcessorFilter filter) {
        return processorFilterTrackerDao
                .fetch(filter.getProcessorFilterTracker().getId())
                .orElseThrow();
    }

    @Test
    void testSearch() {
        setup();

        final List<QueryField> fields = ProcessorTaskFields.getFields();
        assertThat(fields.size()).isEqualTo(18);

        for (final QueryField field : fields) {
            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(field.getFldName());

            final AtomicInteger count = new AtomicInteger();
            processorTaskDao.search(new ExpressionCriteria(), fieldIndex, values -> {
                count.incrementAndGet();
                assertThat(values.length).isEqualTo(1);
            });
            assertThat(count.get()).isEqualTo(8);
        }
    }
}
