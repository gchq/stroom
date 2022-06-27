package stroom.processor.impl.db;

import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.TaskStatus;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorTaskRetain extends AbstractProcessorTest {

    @Test
    void testRetainOwned() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        final Processor processor1 = createProcessor();

        assertThat(getProcessorCount(null))
                .isEqualTo(1);

        final ProcessorFilter processorFilter1 = createProcessorFilter(processor1);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(1);

        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE2, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE2, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE2, FEED);

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isEqualTo(3);

        processorTaskDao.retainOwnedTasks(Set.of(NODE1, NODE2), System.currentTimeMillis());

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isEqualTo(3);

        processorTaskDao.retainOwnedTasks(Set.of(NODE1), System.currentTimeMillis() - 10000);

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isEqualTo(3);

        processorTaskDao.retainOwnedTasks(Set.of(NODE1), System.currentTimeMillis() + 10000);

        assertThat(countTasks()).isEqualTo(6);
        assertThat(countOwned(NODE1)).isEqualTo(3);
        assertThat(countOwned(NODE2)).isEqualTo(0);
        assertThat(countOwned(null)).isEqualTo(3);
    }
}
