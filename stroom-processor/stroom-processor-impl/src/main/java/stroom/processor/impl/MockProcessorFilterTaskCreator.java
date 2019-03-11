package stroom.processor.impl;

import stroom.meta.shared.Meta;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.shared.FindProcessorFilterTaskCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTask;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.TaskStatus;
import stroom.util.shared.BaseResultList;

import java.util.Map;
import java.util.function.Consumer;

public class MockProcessorFilterTaskCreator implements ProcessorFilterTaskCreator {
    @Override
    public void releaseOwnedTasks() {

    }

    @Override
    public void createNewTasks(final ProcessorFilter filter, final ProcessorFilterTracker tracker, final long streamQueryTime, final Map<Meta, InclusiveRanges> streams, final String thisNodeName, final Long maxMetaId, final boolean reachedLimit, final Consumer<CreatedTasks> consumer) {

    }

    @Override
    public ProcessorFilterTask changeTaskStatus(final ProcessorFilterTask processorFilterTask, final String nodeName, final TaskStatus status, final Long startTime, final Long endTime) {
        return null;
    }

    @Override
    public BaseResultList<ProcessorFilterTask> find(final FindProcessorFilterTaskCriteria criteria) {
        return null;
    }
}
