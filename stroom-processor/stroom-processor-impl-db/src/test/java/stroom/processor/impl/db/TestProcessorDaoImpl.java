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
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

@ExtendWith(MockitoExtension.class)
class TestProcessorDaoImpl extends AbstractProcessorTest {

    Processor processor1;
    Processor processor2;
    ProcessorFilter processorFilter1;
    ProcessorFilter processorFilter2;

    @Test
    void testChangeTaskStatus() {
        processor1 = createProcessor();
        processorFilter1 = createProcessorFilter(processor1);

        createProcessorTask(processorFilter1, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);

        dumpProcessorTaskTable();

        final ExpressionCriteria expressionCriteria = new ExpressionCriteria(ExpressionOperator.builder()
                .addTextTerm(ProcessorTaskFields.FEED, Condition.EQUALS, FEED)
                .build());

        assertThat(getProcessorTaskCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.COMPLETE.getPrimitiveValue())))
                .isEqualTo(0);

        processorTaskDao.changeTaskStatus(
                expressionCriteria,
                NODE1,
                TaskStatus.COMPLETE,
                0L,
                Long.MAX_VALUE);

        dumpProcessorTaskTable();

        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.COMPLETE.getPrimitiveValue())))
                .isEqualTo(3);
    }

    @Test
    void testLogicalDeleteByProcessorId() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        processor1 = createProcessor();
        processor2 = createProcessor();

        assertThat(getProcessorCount(null))
                .isEqualTo(2);

        processorFilter1 = createProcessorFilter(processor1);
        createProcessorTask(processorFilter1, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(getProcessorFilterCount(null))
                .isEqualTo(1);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(3);

        processorFilter2 = createProcessorFilter(processor2);
        createProcessorTask(processorFilter2, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(getProcessorFilterCount(null))
                .isEqualTo(2);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);

        dumpProcessorTable();
        dumpProcessorTaskTable();

        processorDao.logicalDeleteByProcessorId(processor1.getId());

        dumpProcessorTable();
        dumpProcessorTaskTable();

        // No change to row counts as they have been logically deleted
        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(2);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);

        // Now make sure the right number have been set to a deleted state
        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(1);
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(1);
        // Tasks not effected
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(0);
    }

    @Test
    void testPhysicalDeleteOldProcessors_notOldEnough() {

        // Logically delete everything first
        testLogicalDeleteByProcessorId();

        // Threshold in past so nothing physically deleted
        processorDao.physicalDeleteOldProcessors(Instant.now().minus(1, ChronoUnit.DAYS));

        // All values the same as after the logical delete
        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(2);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);

        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(1);
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(1);
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(0);
    }

    @Test
    void testPhysicalDeleteOldProcessors_oldEnough() {

        // Logically delete everything first
        testLogicalDeleteByProcessorId();

        final Instant threshold = Instant.now().plus(1, ChronoUnit.DAYS);

        processorFilterDao.logicalDeleteByProcessorFilterId(processorFilter1.getId());
//        processorFilterDao.logicalDeleteByProcessorFilterId(processorFilter2.getId());

        processorTaskDao.logicalDeleteForDeletedProcessorFilters(threshold);

        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);

        processorTaskDao.physicallyDeleteOldTasks(threshold);

        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(0);

        // Have to delete the filters first else we can't delete the procs
        processorFilterDao.physicalDeleteOldProcessorFilters(threshold);

        dumpProcessorTable();
        dumpProcessorFilterTable();
        dumpProcessorTaskTable();

        // Threshold in future so nothing physically deleted
        processorDao.physicalDeleteOldProcessors(threshold);

        // processor + filters gone
        assertThat(getProcessorCount(null))
                .isEqualTo(1);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(1);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(3);

        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(0);
    }

}
