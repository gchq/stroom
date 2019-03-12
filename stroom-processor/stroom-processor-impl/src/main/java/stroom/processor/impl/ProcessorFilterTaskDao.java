package stroom.processor.impl;

import stroom.meta.shared.Meta;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.shared.FindProcessorFilterTaskCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTask;
import stroom.processor.shared.ProcessorFilterTaskSummaryRow;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.TaskStatus;
import stroom.util.shared.BaseResultList;

import java.util.Map;
import java.util.function.Consumer;

public interface ProcessorFilterTaskDao {
    /**
     * Anything that we owned release
     */
    void releaseOwnedTasks();

    /**
     * Create new tasks for the specified filter and add them to the queue.
     *
     * @param filter          The filter to create tasks for
     * @param tracker         The tracker that tracks the task creation progress for the filter.
     * @param metaQueryTime   The time that we queried for meta data that matches the processor filter.
     * @param metaMap         The map of meta data and optional event ranges to create tasks for.
     * @param thisNodeName    This node, the node that will own the created tasks.
     * @param reachedLimit    For search based task creation this indicates if we have reached the limit of tasks
     *                        created for a single search. This limit is imposed to stop search based task
     *                        creation running forever.
     * @return A list of tasks that we have created and that are owned by this
     * node and available to be handed to workers (i.e. their associated meta data is not locked).
     */
    void createNewTasks(final ProcessorFilter filter,
                        final ProcessorFilterTracker tracker,
                        final long metaQueryTime,
                        final Map<Meta, InclusiveRanges> metaMap,
                        final String thisNodeName,
                        final Long maxMetaId,
                        final boolean reachedLimit,
                        final Consumer<CreatedTasks> consumer);

    ProcessorFilterTask changeTaskStatus(ProcessorFilterTask processorFilterTask,
                                         String nodeName,
                                         TaskStatus status,
                                         Long startTime,
                                         Long endTime);

    BaseResultList<ProcessorFilterTask> find(final FindProcessorFilterTaskCriteria criteria);

    BaseResultList<ProcessorFilterTaskSummaryRow> findSummary(final FindProcessorFilterTaskCriteria criteria);
}
