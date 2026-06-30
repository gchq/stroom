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

import stroom.docref.DocRef;
import stroom.processor.impl.ProcessorTaskDeleteExecutor;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.TaskStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

@ExtendWith(MockitoExtension.class)
class TestProcessorTaskDeleteExecutorImpl extends AbstractProcessorTest {

    @Captor
    private ArgumentCaptor<Set<DocRef>> filterUuidsCaptor;

    Processor processor1;
    Processor processor2;
    Processor processor3;
    ProcessorFilter processorFilter1a;
    ProcessorFilter processorFilter1b;
    ProcessorFilter processorFilter2;
    ProcessorFilter processorFilter3;
    ProcessorFilterTracker processorFilterTracker1;
    ProcessorFilterTracker processorFilterTracker2;

    @Test
    void delete1() {

        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        processor1 = createProcessor();
        processor2 = createProcessor();
        processor3 = createProcessor();

        processorFilter1a = createProcessorFilter(processor1);
        processorFilterTracker1 = processorFilter1a.getProcessorFilterTracker();
        processorFilterTracker1.setLastPollMs(Instant.now().minus(3, ChronoUnit.DAYS).toEpochMilli());
        processorFilterTracker1.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
        processorFilterTrackerDao.update(processorFilterTracker1);


        processorFilter1b = createProcessorFilter(processor1);
        createProcessorTask(processorFilter1b, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter1b, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter1b, TaskStatus.PROCESSING, NODE1, FEED);

        // These two are older than the threshold time, the rest are not
        createProcessorTask(processorFilter1b,
                TaskStatus.COMPLETE,
                NODE1,
                FEED,
                Instant.now().minus(3, ChronoUnit.DAYS));
        createProcessorTask(
                processorFilter1b,
                TaskStatus.DELETED,
                NODE1,
                FEED,
                Instant.now().minus(3, ChronoUnit.DAYS));

        processorFilter2 = createProcessorFilter(processor2);
        processorFilterTracker2 = processorFilter2.getProcessorFilterTracker();
        processorFilterTracker2.setLastPollMs(Instant.now().minus(3, ChronoUnit.DAYS).toEpochMilli());
        processorFilterTracker2.setStatus(ProcessorFilterTrackerStatus.ERROR);
        processorFilterTrackerDao.update(processorFilterTracker2);

        createProcessorTask(processorFilter2, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.PROCESSING, NODE1, FEED);

        processorFilter3 = createProcessorFilter(processor3);
        createProcessorTask(processorFilter3, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter3, TaskStatus.QUEUED, NODE1, FEED);
        createProcessorTask(processorFilter3, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(getProcessorCount(null))
                .isEqualTo(3);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(4);
        assertThat(getProcessorFilterTrackerCount(null))
                .isEqualTo(4);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(11);

        dumpProcessorTable();
        dumpProcessorFilterTable();
        dumpProcessorFilterTrackerTable();

        // threshold is before all the data so nothing will get deleted
        final Instant threshold = Instant.now().minus(5, ChronoUnit.DAYS);

        final ProcessorTaskDeleteExecutor processorTaskDeleteExecutor = getInjector()
                .getInstance(ProcessorTaskDeleteExecutor.class);

        processorTaskDeleteExecutor.delete(threshold);

        dumpProcessorTable();
        dumpProcessorFilterTable();
        dumpProcessorFilterTrackerTable();

        assertThat(getProcessorCount(null))
                .isEqualTo(3);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(4);
        assertThat(getProcessorFilterTrackerCount(null))
                .isEqualTo(4);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(11);

        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(1);
    }

    @Test
    void delete2() {
        delete1();

        final Instant threshold = Instant.now().minus(2, ChronoUnit.DAYS);

        final ProcessorTaskDeleteExecutor processorTaskDeleteExecutor = getInjector()
                .getInstance(ProcessorTaskDeleteExecutor.class);

//        Mockito.verify(mockDocumentPermissionService, Mockito.times(1))
//                .deleteDocumentPermissions(filterUuidsCaptor.capture());

        processorTaskDeleteExecutor.delete(threshold);

        dumpProcessorTable();
        dumpProcessorFilterTable();
        dumpProcessorFilterTrackerTable();

        assertThat(getProcessorCount(null))
                .isEqualTo(3);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(4);
        assertThat(getProcessorFilterTrackerCount(null))
                .isEqualTo(4);
        // Two phys deleted as complete and old
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(11 - 2);

        // None deleted as none have deleted filter at this point
        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(0);
        // 1 is complete with no tasks
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(1);
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(0);

//        final List<String> filterUuids = filterUuidsCaptor.getValue();
    }

    @Test
    void delete3() {
        delete2();

        final Instant threshold = Instant.now().plus(1, ChronoUnit.DAYS);

        final ProcessorTaskDeleteExecutor processorTaskDeleteExecutor = getInjector()
                .getInstance(ProcessorTaskDeleteExecutor.class);

        processorTaskDeleteExecutor.delete(threshold);

        dumpProcessorTable();
        dumpProcessorFilterTable();
        dumpProcessorFilterTrackerTable();

        assertThat(getProcessorCount(null))
                .isEqualTo(3);
        // Two were logically deleted last time so now they are gone
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(4 - 1);
        // Two were logically deleted last time so now they are gone
        assertThat(getProcessorFilterTrackerCount(null))
                .isEqualTo(4 - 1);
        // Two phys deleted as complete/error and old
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(11 - 2);

        // None deleted as none have deleted filter at this point
        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(0);
        // The 1 were phys deleted
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(0);

        Mockito.verify(mockDocumentPermissionService, Mockito.times(1))
                .removeAllDocumentPermissions(filterUuidsCaptor.capture());

        // Make sure we ask to delete the perms for the phys deleted filter
        final Set<DocRef> filterUuids = filterUuidsCaptor.getValue();
        assertThat(filterUuids)
                .hasSize(1)
                .containsExactly(processorFilter1a.asDocRef());
    }
}
