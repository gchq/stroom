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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

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

        final Set<String> deletedUuids = processorFilterDao.logicallyDeleteOldProcessorFilters(threshold);

        dumpProcessorFilterTable();

        // The caller is told which filters were deleted
        assertThat(deletedUuids)
                .containsExactly(processorFilter3.getUuid());

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
    void testFetchUuidsByProcessorId() {
        processor1 = createProcessor();
        processor2 = createProcessor();
        processorFilter1 = createProcessorFilter(processor1);
        processorFilter2 = createProcessorFilter(processor1);
        processorFilter3 = createProcessorFilter(processor2);
        // Deleted filters are included
        processorFilterDao.logicalDeleteByProcessorFilterId(processorFilter2.getId());

        final Set<String> uuids = processorFilterDao.fetchUuidsByProcessorId(processor1.getId());

        assertThat(uuids)
                .containsExactlyInAnyOrder(processorFilter1.getUuid(), processorFilter2.getUuid());
    }

    @Test
    void testFetchUuidsByProcessorId_noFilters() {
        processor1 = createProcessor();

        assertThat(processorFilterDao.fetchUuidsByProcessorId(processor1.getId()))
                .isEmpty();
    }

    @Test
    void testFetchExistingUuids() {
        processor1 = createProcessor();
        processorFilter1 = createProcessorFilter(processor1);
        processorFilter2 = createProcessorFilter(processor1);
        // Deleted filters still exist
        processorFilterDao.logicalDeleteByProcessorFilterId(processorFilter2.getId());

        final Set<String> uuids = processorFilterDao.fetchExistingUuids(List.of(
                processorFilter1.getUuid(),
                processorFilter2.getUuid(),
                "unknown-uuid"));

        assertThat(uuids)
                .containsExactlyInAnyOrder(processorFilter1.getUuid(), processorFilter2.getUuid());
    }

    @Test
    void testFetchExistingUuids_empty() {
        assertThat(processorFilterDao.fetchExistingUuids(List.of()))
                .isEmpty();
        assertThat(processorFilterDao.fetchExistingUuids(null))
                .isEmpty();
    }

    @Test
    void testFindWithStaleDependencies() {
        final int currentVersion = 2;
        processor1 = createProcessor();
        // Never stamped
        processorFilter1 = createProcessorFilter(processor1);
        // Stamped by an older version
        processorFilter2 = createProcessorFilter(processor1);
        processorFilterDao.setDependencyVersion(processorFilter2.getId(), processorFilter2.getVersion(), 1);
        // Stamped by the current version
        processorFilter3 = createProcessorFilter(processor1);
        processorFilterDao.setDependencyVersion(
                processorFilter3.getId(), processorFilter3.getVersion(), currentVersion);
        // Deleted filters are included so their edges can be removed
        final ProcessorFilter deletedFilter = createProcessorFilter(processor1);
        processorFilterDao.logicalDeleteByProcessorFilterId(deletedFilter.getId());

        final List<ProcessorFilter> staleFilters = processorFilterDao.findWithStaleDependencies(
                currentVersion, 0, 100);

        assertThat(staleFilters)
                .extracting(ProcessorFilter::getUuid)
                .containsExactly(processorFilter1.getUuid(), processorFilter2.getUuid(), deletedFilter.getUuid());
        assertThat(staleFilters)
                .last()
                .extracting(ProcessorFilter::isDeleted)
                .isEqualTo(true);
        // The processor is included so its pipeline and deleted state are known
        assertThat(staleFilters)
                .first()
                .extracting(ProcessorFilter::getPipelineUuid)
                .isEqualTo(processor1.getPipelineUuid());
    }

    @Test
    void testFindWithStaleDependencies_batches() {
        processor1 = createProcessor();
        processorFilter1 = createProcessorFilter(processor1);
        processorFilter2 = createProcessorFilter(processor1);
        processorFilter3 = createProcessorFilter(processor1);

        final List<ProcessorFilter> firstBatch = processorFilterDao.findWithStaleDependencies(1, 0, 2);
        final List<ProcessorFilter> secondBatch = processorFilterDao.findWithStaleDependencies(
                1, firstBatch.getLast().getId(), 2);

        assertThat(firstBatch)
                .extracting(ProcessorFilter::getUuid)
                .containsExactly(processorFilter1.getUuid(), processorFilter2.getUuid());
        assertThat(secondBatch)
                .extracting(ProcessorFilter::getUuid)
                .containsExactly(processorFilter3.getUuid());
    }

    @Test
    void testLogicallyDeleteOldProcessorFilters_noneToDelete() {
        processor1 = createProcessor();
        processorFilter1 = createProcessorFilter(processor1);

        assertThat(processorFilterDao.logicallyDeleteOldProcessorFilters(Instant.now().plus(1, ChronoUnit.DAYS)))
                .isEmpty();
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(0);
    }

    @Test
    void testSetDependencyVersion() {
        processor1 = createProcessor();
        processorFilter1 = createProcessorFilter(processor1);

        final boolean stamped = processorFilterDao.setDependencyVersion(
                processorFilter1.getId(), processorFilter1.getVersion(), 1);

        assertThat(stamped)
                .isTrue();
        assertThat(processorFilterDao.findWithStaleDependencies(1, 0, 100))
                .isEmpty();
        // Stamping is not a change to the filter, so its version is unchanged
        assertThat(processorFilterDao.fetch(processorFilter1.getId()).orElseThrow().getVersion())
                .isEqualTo(processorFilter1.getVersion());
    }

    @Test
    void testSetDependencyVersion_filterChangedSinceRead() {
        processor1 = createProcessor();
        processorFilter1 = createProcessorFilter(processor1);
        processorFilterDao.update(processorFilter1.copy().priority(99).build());

        final boolean stamped = processorFilterDao.setDependencyVersion(
                processorFilter1.getId(), processorFilter1.getVersion(), 1);

        assertThat(stamped)
                .isFalse();
        assertThat(processorFilterDao.findWithStaleDependencies(1, 0, 100))
                .extracting(ProcessorFilter::getUuid)
                .containsExactly(processorFilter1.getUuid());
    }

    @Test
    void testSetDependencyVersion_unknownFilter() {
        assertThat(processorFilterDao.setDependencyVersion(-1, 1, 1))
                .isFalse();
    }
}
