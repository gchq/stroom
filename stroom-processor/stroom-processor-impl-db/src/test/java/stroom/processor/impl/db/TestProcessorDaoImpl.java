package stroom.processor.impl.db;

import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.TaskStatus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class TestProcessorDaoImpl extends AbstractProcessorTest {

    @Test
    void logicalDelete() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        final Processor processor1 = createProcessor();

        assertThat(getProcessorCount(null))
                .isEqualTo(1);

        final ProcessorFilter processorFilter1 = createProcessorFilter(processor1);
        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(getProcessorFilterCount(null))
                .isEqualTo(1);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(3);

        final Processor processor2 = createProcessor();

        assertThat(getProcessorCount(null))
                .isEqualTo(2);

        final ProcessorFilter processorFilter2 = createProcessorFilter(processor2);
        createProcessorTask(processorFilter2, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(getProcessorFilterCount(null))
                .isEqualTo(2);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);

        processorDao.logicalDelete(processor1.getId());

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
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(4);
    }
}
