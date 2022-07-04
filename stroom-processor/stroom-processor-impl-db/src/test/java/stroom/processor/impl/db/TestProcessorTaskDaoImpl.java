package stroom.processor.impl.db;

import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.TaskStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorTaskDaoImpl extends AbstractProcessorTest {

    @Test
    void testReleaseOwnedTasks() {
        assertThat(getProcessorCount(null)).isZero();
        assertThat(countTasks()).isZero();
        assertThat(countOwned(NODE1)).isZero();
        assertThat(countOwned(NODE2)).isZero();

        final Processor processor1 = createProcessor();

        assertThat(getProcessorCount(null)).isOne();

        final ProcessorFilter processorFilter1 = createProcessorFilter(processor1);
        assertThat(getProcessorFilterCount(null)).isOne();

        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(countTasks()).isEqualTo(3);
        assertThat(countOwned(NODE1)).isEqualTo(3);

        processorTaskDao.releaseOwnedTasks(NODE1);

        assertThat(countTasks()).isEqualTo(3);
        assertThat(countOwned(NODE1)).isZero();
        assertThat(countOwned(null)).isEqualTo(3);

        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE2, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE2, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE2, FEED);

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

        final Processor processor1 = createProcessor();

        assertThat(getProcessorCount(null)).isOne();

        final ProcessorFilter processorFilter1 = createProcessorFilter(processor1);
        assertThat(getProcessorFilterCount(null)).isOne();

        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE2, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE2, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE2, FEED);

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
}
