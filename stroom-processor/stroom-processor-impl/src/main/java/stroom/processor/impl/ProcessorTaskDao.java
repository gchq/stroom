package stroom.processor.impl;

import stroom.dashboard.expression.v1.Val;
import stroom.datasource.api.v2.AbstractField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.Meta;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.TaskStatus;
import stroom.util.shared.ResultPage;

import java.util.Map;
import java.util.function.Consumer;

public interface ProcessorTaskDao {
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

    ProcessorTask changeTaskStatus(ProcessorTask processorTask,
                                         String nodeName,
                                         TaskStatus status,
                                         Long startTime,
                                         Long endTime);

    ResultPage<ProcessorTask> find(final ExpressionCriteria criteria);

    ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria);

    void search(ExpressionCriteria criteria, AbstractField[] fields, Consumer<Val[]> consumer);
}
