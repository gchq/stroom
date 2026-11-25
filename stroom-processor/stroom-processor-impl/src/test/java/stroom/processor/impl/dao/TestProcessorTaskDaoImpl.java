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
import stroom.processor.impl.ProgressMonitor;
import stroom.processor.impl.ProgressMonitor.FilterProgressMonitor;
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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        final ProcessorTask result = processorTaskDao.changeTaskStatus(
                taskAsOwned, NODE1, TaskStatus.COMPLETE, now.toEpochMilli(), now.toEpochMilli());

        assertThat(result).isNull();
        // ...leaving the row as the reaper set it, not stamped COMPLETE.
        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskNodeId(taskId)).isNull();

        // A write with the current version still succeeds - abandonment is about lost leases,
        // not a general write freeze.
        final ProcessorTask current = processorTaskDao.find(new ExpressionCriteria())
                .getValues()
                .getFirst();
        final ProcessorTask updated = processorTaskDao.changeTaskStatus(
                current, NODE1, TaskStatus.PROCESSING, now.toEpochMilli(), null);
        assertThat(updated).isNotNull();
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
                queued, NODE1, TaskStatus.PROCESSING, 1L, null);
        assertThat(processing.getRunCount()).isOne();
        assertThat(getTaskRunCount(taskId)).isOne();

        // Nothing but starting a run changes it, and what is returned stays in step with the row.
        processorTaskDao.renewTaskHeartbeats(NODE1, Set.of(taskId), 2L);
        assertThat(getTaskRunCount(taskId)).isOne();
        final ProcessorTask retry = processorTaskDao.changeTaskStatus(
                processing, NODE1, TaskStatus.RETRY, 1L, 2L, "Broken");
        assertThat(retry.getRunCount()).isOne();
        assertThat(getTaskRunCount(taskId)).isOne();
        final ProcessorTask created = processorTaskDao.changeTaskStatus(
                retry, null, TaskStatus.CREATED, null, null);
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
        assertThat(processorTaskDao.releaseTasks(Set.of(taskId), TaskStatus.QUEUED)).isOne();

        assertThat(processorTaskDao.changeTaskStatus(queued, NODE1, TaskStatus.PROCESSING, 1L, null)).isNull();

        assertThat(getTaskStatus(taskId)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskRunCount(taskId)).isZero();
    }

    @Test
    void testReasonIsSetAndClearedWithTheStatus() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);

        final ProcessorTask retry = processorTaskDao.changeTaskStatus(
                findTask(taskId), NODE1, TaskStatus.RETRY, 1L, 2L, "Broken");
        assertThat(retry.getReason()).isEqualTo("Broken");
        assertThat(getTaskReason(taskId)).isEqualTo("Broken");
        assertThat(findTask(taskId).getReason()).isEqualTo("Broken");

        final ProcessorTask created = processorTaskDao.changeTaskStatus(
                retry, null, TaskStatus.CREATED, null, null);
        assertThat(created.getReason()).isNull();
        assertThat(getTaskReason(taskId)).isNull();
    }

    @Test
    void testReasonIsTruncatedToFit() {
        processor1 = createProcessor();
        processorFilter1a = createProcessorFilter(processor1);
        final long taskId = createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE1, FEED);

        final ProcessorTask aborted = processorTaskDao.changeTaskStatus(
                findTask(taskId), NODE1, TaskStatus.ABORTED, 1L, 2L, "x".repeat(1_001));

        assertThat(aborted.getReason()).isEqualTo("x".repeat(1_000));
        assertThat(getTaskReason(taskId)).isEqualTo("x".repeat(1_000));
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
        assertThat(processorTaskDao.releaseTasks(Set.of(swept, owned), TaskStatus.QUEUED)).isEqualTo(2);
        assertThat(getTaskReason(swept)).isEqualTo(ProcessorTaskDaoImpl.REASON_LEFT_QUEUED);
        assertThat(getTaskReason(owned)).isEqualTo(ProcessorTaskDaoImpl.REASON_NODE_STARTED);

        assertThat(processorTaskDao.claimTasks(processorFilter1a.getId(), NODE1, 3))
                .hasSize(3)
                .allSatisfy(task -> assertThat(task.getReason()).isNull());
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

    private long createTaskWithReason(final ProcessorFilter filter) {
        final long taskId = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED);
        processorTaskDao.changeTaskStatus(findTask(taskId), NODE1, TaskStatus.RETRY, 1L, 2L, "Broken");
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
