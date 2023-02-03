package stroom.processor.impl.db;

import stroom.processor.impl.ProcessorTaskDeleteExecutor;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.TaskStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class TestProcessorTaskDeleteExecutorImpl extends AbstractProcessorTest {

    Processor processor1;
    Processor processor2;
    Processor processor3;
    ProcessorFilter processorFilter1a;
    ProcessorFilter processorFilter1b;
    ProcessorFilter processorFilter2;
    ProcessorFilter processorFilter3;
    ProcessorFilterTracker processorFilterTracker1;
    ProcessorFilterTracker processorFilterTracker2;
    ProcessorFilterTracker processorFilterTracker3;

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
        processorFilterTracker1.setStatus(ProcessorFilterTracker.COMPLETE);
        processorFilterTrackerDao.update(processorFilterTracker1);


        processorFilter1b = createProcessorFilter(processor1);
        createProcessorTask(processorFilter1b, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter1b, TaskStatus.ASSIGNED, NODE1, FEED);
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
        processorFilterTracker2.setStatus(ProcessorFilterTracker.ERROR);
        processorFilterTrackerDao.update(processorFilterTracker2);

        createProcessorTask(processorFilter2, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.PROCESSING, NODE1, FEED);

        processorFilter3 = createProcessorFilter(processor3);
        createProcessorTask(processorFilter3, TaskStatus.CREATED, NODE1, FEED);
        createProcessorTask(processorFilter3, TaskStatus.ASSIGNED, NODE1, FEED);
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
    }
}
