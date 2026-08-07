/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.processor.impl.db.jooq.tables.ProcessorTask;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.TaskStatus;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class TestProcessorFilterDaoImpl extends AbstractProcessorTest {

    Processor processor1;
    Processor processor2;
    Processor processor3;
    ProcessorFilter processorFilter1;
    ProcessorFilter processorFilter2;
    ProcessorFilter processorFilter3;
    ProcessorFilterTracker processorFilterTracker1;
    ProcessorFilterTracker processorFilterTracker2;
    ProcessorFilterTracker processorFilterTracker3;

    @Test
    void logicalDeleteByProcessorFilterId() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        processor1 = createProcessor();
        processor2 = createProcessor();

        processorFilter1 = createProcessorFilter(processor1);
        createProcessorTask(processorFilter1, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);

        processorFilter2 = createProcessorFilter(processor2);
        createProcessorTask(processorFilter2, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(2);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);

        dumpProcessorTable();
        dumpProcessorFilterTable();

        processorFilterDao.logicalDeleteByProcessorFilterId(processorFilter1.getId());

        dumpProcessorTable();
        dumpProcessorFilterTable();

        // No change to row counts as they have been logically deleted
        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(2);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);

        // Now make sure the right number have been set to a deleted state
        // Processors not effected
        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(1);
        // Tasks not effected
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(0);
    }

    @Test
    void testLogicallyDeleteOldProcessorFilters() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        processor1 = createProcessor();
        processor2 = createProcessor();
        processor3 = createProcessor();

        // This one is complete but has tasks so won't get logically deleted
        processorFilter1 = createProcessorFilter(processor1);
        processorFilterTracker1 = processorFilter1.getProcessorFilterTracker();
        processorFilterTracker1.setLastPollMs(Instant.now().toEpochMilli());
        processorFilterTracker1.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
        processorFilterTrackerDao.update(processorFilterTracker1);
        createProcessorTask(processorFilter1, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);

        // This one is not COMPLETE but has tasks so won't get logically deleted
        processorFilter2 = createProcessorFilter(processor2);
        processorFilterTracker2 = processorFilter2.getProcessorFilterTracker();
        processorFilterTracker2.setLastPollMs(Instant.now().toEpochMilli());
        processorFilterTracker2.setStatus(ProcessorFilterTrackerStatus.ERROR);
        processorFilterTrackerDao.update(processorFilterTracker2);
        createProcessorTask(processorFilter2, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.PROCESSING, NODE1, FEED);

        // This one is COMPLETE and has no tasks so will get logically deleted
        processorFilter3 = createProcessorFilter(processor3);
        processorFilterTracker3 = processorFilter3.getProcessorFilterTracker();
        processorFilterTracker3.setLastPollMs(Instant.now().toEpochMilli());
        processorFilterTracker3.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
        processorFilterTrackerDao.update(processorFilterTracker3);

        assertThat(getProcessorCount(null))
                .isEqualTo(3);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorFilterTrackerCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);

        dumpProcessorFilterTable();
        dumpProcessorFilterTrackerTable();

        final Instant threshold = Instant.now().plus(1, ChronoUnit.DAYS);

        processorFilterDao.logicallyDeleteOldProcessorFilters(threshold);

        dumpProcessorFilterTable();

        // No change to row counts as they have been logically deleted
        assertThat(getProcessorCount(null))
                .isEqualTo(3);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorFilterTrackerCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);

        // Now make sure the right number have been set to a deleted state
        // Processors not effected
        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(1);
        // Tasks not effected
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(0);

        assertThat(processorFilterDao.fetch(processorFilter3.getId())
                        .orElseThrow()
                        .isDeleted())
                .isTrue();
    }

    @Test
    void testPhysicalDeleteOldProcessorFilters() {
        testLogicallyDeleteOldProcessorFilters();

        final Instant threshold = Instant.now().plus(1, ChronoUnit.DAYS);

        processorTaskDao.logicalDeleteForDeletedProcessorFilters(threshold);
        processorTaskDao.physicallyDeleteOldTasks(threshold);

        dumpProcessorFilterTable();
        dumpProcessorFilterTrackerTable();

        processorFilterDao.physicalDeleteOldProcessorFilters(threshold);

        dumpProcessorFilterTable();

        // No change to processors
        assertThat(getProcessorCount(null))
                .isEqualTo(3);
        // Deleted 1 filters
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3 - 1);
        // Deleted 1 trackers
        assertThat(getProcessorFilterTrackerCount(null))
                .isEqualTo(3 - 1);
        // No tasks deleted
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);
    }

    @Test
    void create() {
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(0);
        final Processor processor1 = createProcessor();

        final ProcessorFilter processorFilter1 = createProcessorFilter(processor1);
        final ProcessorFilterTracker processorFilterTracker1 = processorFilter1.getProcessorFilterTracker();

        assertThat(processorFilter1.getId())
                .isNotNull();
        assertThat(processorFilterTracker1.getId())
                .isNotNull();
        assertThat(processorFilterTracker1.getStatus())
                .isEqualTo(ProcessorFilterTrackerStatus.CREATED); // default value

        final ProcessorFilter processorFilter2 = processorFilterDao.fetch(processorFilter1.getId())
                .orElseThrow();
        final ProcessorFilterTracker processorFilterTracker2 = processorFilter2.getProcessorFilterTracker();

        assertThat(processorFilter1.getProcessor())
                .isEqualTo(processorFilter2.getProcessor());
        assertThat(processorFilter1.getProcessorFilterTracker())
                .isEqualTo(processorFilter2.getProcessorFilterTracker());
        assertThat(processorFilterTracker1.getId())
                .isEqualTo(processorFilterTracker2.getId());
        assertThat(processorFilterTracker1.getStatus())
                .isEqualTo(processorFilterTracker2.getStatus());
    }

    @Test
    void maxTaskCreationDelayRoundTrips() {
        final Processor processor = createProcessor();
        final ProcessorFilter created = createProcessorFilter(processor);
        assertThat(created.getMaxTaskCreationDelay())
                .describedAs("Defaults to the cluster wide maximum")
                .isNull();

        final SimpleDuration delay = new SimpleDuration(30, TimeUnit.SECONDS);
        processorFilterDao.update(created.copy().maxTaskCreationDelay(delay).build());
        assertThat(processorFilterDao.fetch(created.getId()).orElseThrow().getMaxTaskCreationDelay())
                .isEqualTo(delay);

        final ProcessorFilter withDelay = processorFilterDao.fetch(created.getId()).orElseThrow();
        processorFilterDao.update(withDelay.copy().maxTaskCreationDelay(null).build());
        assertThat(processorFilterDao.fetch(created.getId()).orElseThrow().getMaxTaskCreationDelay())
                .describedAs("Can be cleared again")
                .isNull();
    }

    /**
     * gh-5699 Phase 0b. Restoring a deleted filter replaces it rather than reviving it, so that a
     * filter id keeps meaning one fixed body of work - see
     * PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.8.
     */
    @Test
    void restoringADeletedFilterReplacesItWithAReplica() {
        final Processor processor = createProcessor();
        final ProcessorFilter original = createProcessorFilter(processor);
        final String uuid = original.getUuid();
        createProcessorTask(original, TaskStatus.COMPLETE, NODE1, FEED);

        // Work already done, which is exactly what the old tracker reset destroyed.
        final ProcessorFilterTracker tracker = original.getProcessorFilterTracker();
        tracker.setMinMetaId(500L);
        tracker.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
        processorFilterTrackerDao.update(tracker);

        processorFilterDao.logicalDeleteByProcessorFilterId(original.getId());
        final ProcessorFilter deleted = processorFilterDao.fetch(original.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();

        final ProcessorFilter replica = processorFilterDao.restoreProcessorFilter(deleted);

        assertThat(replica.getId())
                .describedAs("a new id, so nothing keyed by filter id is silently left stale")
                .isNotEqualTo(original.getId());
        assertThat(replica.getParentFilterId())
                .describedAs("lineage back to the filter it replaced")
                .isEqualTo(original.getId());
        assertThat(replica.isDeleted()).isFalse();
        assertThat(replica.getUuid())
                .describedAs("the replica takes over the doc ref, so the doc still resolves")
                .isEqualTo(uuid);

        final ProcessorFilterTracker replicaTracker = replica.getProcessorFilterTracker();
        assertThat(replicaTracker.getId()).isNotEqualTo(tracker.getId());
        assertThat(replicaTracker.getMinMetaId()).isEqualTo(0L);
        assertThat(replicaTracker.getStatus()).isEqualTo(ProcessorFilterTrackerStatus.CREATED);

        final ProcessorFilter superseded = processorFilterDao.fetch(original.getId()).orElseThrow();
        assertThat(superseded.isDeleted())
                .describedAs("the filter that did the work stays deleted, with its history intact")
                .isTrue();
        assertThat(superseded.getUuid())
                .describedAs("it has to give up the uuid - only one row may hold it")
                .isNotEqualTo(uuid);
        assertThat(superseded.getProcessorFilterTracker().getMinMetaId()).isEqualTo(500L);
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(original.getId())))
                .isEqualTo(1);
    }

    @Test
    void restoringAFilterWithActiveTasksNoLongerFails() {
        final Processor processor = createProcessor();
        final ProcessorFilter original = createProcessorFilter(processor);
        processorFilterDao.logicalDeleteByProcessorFilterId(original.getId());
        // Resetting a tracker under an active task would have reprocessed data being processed, so
        // the old code refused. A replica leaves those tasks with the filter that owns them, so
        // there is nothing left to refuse.
        createProcessorTask(original, TaskStatus.PROCESSING, NODE1, FEED);

        final ProcessorFilter deleted = processorFilterDao.fetch(original.getId()).orElseThrow();
        final ProcessorFilter replica = processorFilterDao.restoreProcessorFilter(deleted);

        assertThat(replica.getId()).isNotEqualTo(original.getId());
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(replica.getId())))
                .describedAs("the replica starts with no tasks of its own")
                .isEqualTo(0);
    }

    @Test
    void restoringUndeletesTheProcessor() {
        final Processor processor = createProcessor();
        final ProcessorFilter original = createProcessorFilter(processor);
        processorDao.logicalDeleteByProcessorId(processor.getId());

        final ProcessorFilter deleted = processorFilterDao.fetch(original.getId()).orElseThrow();
        assertThat(deleted.getProcessor().isDeleted()).isTrue();

        final ProcessorFilter replica = processorFilterDao.restoreProcessorFilter(deleted);

        assertThat(replica.getProcessor().isDeleted())
                .describedAs("a replica of a filter whose processor is deleted could never run")
                .isFalse();
        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(false))).isEqualTo(1);
    }

    @Test
    void restoringAFilterThatIsNotDeletedChangesNothing() {
        final Processor processor = createProcessor();
        final ProcessorFilter original = createProcessorFilter(processor);

        assertThat(processorFilterDao.restoreProcessorFilter(original)).isEqualTo(original);
        assertThat(getProcessorFilterCount(null)).isEqualTo(1);
    }

    @Test
    void nextPollMsRoundTrips() {
        final Processor processor = createProcessor();
        final ProcessorFilter created = createProcessorFilter(processor);
        final ProcessorFilterTracker tracker = created.getProcessorFilterTracker();
        assertThat(tracker.getNextPollMs())
                .describedAs("A new filter is due a poll straight away")
                .isNull();

        tracker.setNextPollMs(1234L);
        processorFilterTrackerDao.update(tracker);
        assertThat(processorFilterDao.fetch(created.getId())
                .orElseThrow()
                .getProcessorFilterTracker()
                .getNextPollMs())
                .isEqualTo(1234L);
    }
}
