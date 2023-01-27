package stroom.processor.impl;

import stroom.dashboard.expression.v1.ValuesConsumer;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ProcessorTaskDao {

    /**
     * Release tasks and make them unowned.
     *
     * @param nodeName The node name to release task ownership for.
     * @return The number of tasks released.
     */
    long releaseOwnedTasks(String nodeName);

    /**
     * Retain task ownership
     *
     * @param retainForNodes  A set of nodes to retain task ownership for.
     * @param statusOlderThan Change task ownership for tasks that have a status older than this.
     * @return The number of tasks released.
     */
    long retainOwnedTasks(Set<String> retainForNodes,
                          Instant statusOlderThan);

    /**
     * Create new tasks for the specified filter and add them to the queue.
     *
     * @param filter          The filter to create tasks for
     * @param tracker         The tracker that tracks the task creation progress for the filter.
     * @param progressMonitor Monitor and record task creation progress to help identify issues.
     * @param metaQueryTime   The time that we queried for meta data that matches the processor filter.
     * @param metaMap         The map of meta data and optional event ranges to create tasks for.
     * @param thisNodeName    This node, the node that will own the created tasks.
     * @param reachedLimit    For search based task creation this indicates if we have reached the limit of tasks
     *                        created for a single search. This limit is imposed to stop search based task
     *                        creation running forever.
     * @param fillTaskQueue   Should the newly created tasks be added to the task queue immediately.
     * @return A list of tasks that we have created and that are owned by this
     * node and available to be handed to workers (i.e. their associated meta data is not locked).
     */
    CreatedTasks createNewTasks(ProcessorFilter filter,
                                ProcessorFilterTracker tracker,
                                ProgressMonitor progressMonitor,
                                long metaQueryTime,
                                Map<Meta, InclusiveRanges> metaMap,
                                String thisNodeName,
                                Long maxMetaId,
                                boolean reachedLimit,
                                boolean fillTaskQueue);

    /**
     * Change the node ownership of the tasks in the id set and select them back to include in the queue.
     *
     * @param idSet        The ids of the tasks to take ownership of.
     * @param thisNodeName This node name.
     * @return A list of tasks to queue.
     */
    List<ProcessorTask> queueExistingTasks(Set<Long> idSet,
                                           String thisNodeName);

    /**
     * Assign a set of tasks to a node for processing.
     *
     * @param idSet        The ids of the tasks to assign.
     * @param thisNodeName The node name to assign the tasks to.
     * @return A list of tasks to queue.
     */
    List<ProcessorTask> assignTasks(Set<Long> idSet,
                                    String nodeName);

    /**
     * Release ownership for a set of tasks and abandon processing.
     *
     * @param idSet The ids of the tasks to release.
     * @return The number of tasks changed.
     */
    int releaseTasks(Set<Long> idSet);

    ResultPage<ProcessorTask> changeTaskStatus(ExpressionCriteria criteria,
                                               String nodeName,
                                               TaskStatus status,
                                               Long startTime,
                                               Long endTime);

    ProcessorTask changeTaskStatus(ProcessorTask processorTask,
                                   String nodeName,
                                   TaskStatus status,
                                   Long startTime,
                                   Long endTime);

    ResultPage<ProcessorTask> find(final ExpressionCriteria criteria);

    ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria);

    void search(ExpressionCriteria criteria, AbstractField[] fields, ValuesConsumer consumer);

    int logicalDeleteByProcessorId(int processorId);

    int logicalDeleteByProcessorFilterId(int processorFilterId);

    int logicalDeleteForDeletedProcessorFilters(Instant deleteThreshold);

    int physicallyDeleteOldTasks(Instant deleteThreshold);
}
