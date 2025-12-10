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

package stroom.processor.impl.db;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.FieldIndex;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class TestProcessorTaskDaoImpl extends AbstractProcessorTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProcessorTaskDaoImpl.class);

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

    @Test
    void testRetainOwnedTasks() {
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
        createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE2, FEED);
        createProcessorTask(processorFilter1a, TaskStatus.QUEUED, NODE2, FEED);
        createProcessorTask(processorFilter1a, TaskStatus.PROCESSING, NODE2, FEED);

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isEqualTo(3);

        processorTaskDao.retainOwnedTasks(Set.of(NODE1, NODE2), Instant.now());

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isEqualTo(3);

        processorTaskDao.retainOwnedTasks(Set.of(NODE1), Instant.now().minusSeconds(10));

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isEqualTo(3);

        processorTaskDao.retainOwnedTasks(Set.of(NODE1), Instant.now().plusSeconds(10));

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isZero();
        assertThat(countOwned(null)).isEqualTo(3);
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

        processorFilter1a.setDeleted(true);
        processorFilter1a.setUpdateTimeMs(Instant.now().toEpochMilli());
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
