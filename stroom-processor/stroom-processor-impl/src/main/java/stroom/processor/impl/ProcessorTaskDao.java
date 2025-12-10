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

package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.Meta;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.impl.ProgressMonitor.FilterProgressMonitor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.TaskStatus;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
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
     * Count the current number of tasks for a filter matching the specified status.
     *
     * @param filterId The filter to count tasks for.
     * @param status Task status.
     * @return The number of tasks matching the specified status.
     */
    int countTasksForFilter(int filterId, TaskStatus status);

    /**
     * Create new tasks for the specified filter and add them to the queue.
     *
     * @param filter                The filter to create tasks for
     * @param tracker               The tracker that tracks the task creation progress for the filter.
     * @param filterProgressMonitor Monitor and record task creation progress to help identify issues.
     * @param metaQueryTime         The time that we queried for meta data that matches the processor filter.
     * @param metaMap               The map of meta data and optional event ranges to create tasks for.
     * @param maxMetaId             The max id to create tasks up to.
     * @param reachedLimit          For search based task creation this indicates if we have reached the limit of tasks
     *                              created for a single search. This limit is imposed to stop search based task
     *                              creation running forever.
     * @return A list of tasks that we have created and that are owned by this
     * node and available to be handed to workers (i.e. their associated meta data is not locked).
     */
    int createNewTasks(ProcessorFilter filter,
                       ProcessorFilterTracker tracker,
                       FilterProgressMonitor filterProgressMonitor,
                       long metaQueryTime,
                       Map<Meta, InclusiveRanges> metaMap,
                       Long maxMetaId,
                       boolean reachedLimit);

    /**
     * Change the node ownership of the tasks in the id set and select them back to include in the queue.
     *
     * @param idSet        The ids of the tasks to take ownership of.
     * @param thisNodeName This node name.
     * @return A list of tasks to queue.
     */
    List<ProcessorTask> queueTasks(Set<Long> idSet,
                                   String thisNodeName);

    /**
     * Release ownership for a set of tasks and abandon processing.
     *
     * @param idSet         The ids of the tasks to release.
     * @param currentStatus The current status of tasks to release.
     * @return The number of tasks changed.
     */
    int releaseTasks(Set<Long> idSet, TaskStatus currentStatus);

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

    void search(ExpressionCriteria criteria, FieldIndex fieldIndex, ValuesConsumer consumer);

    int logicalDeleteByProcessorId(int processorId);

    int logicalDeleteByProcessorFilterId(int processorFilterId);

    /**
     * Logically delete tasks that are associated with filters that have been logically deleted for longer than the
     * threshold.
     *
     * @param deleteThreshold Only logically delete tasks with an update time older than the threshold.
     * @return The number of logically deleted tasks.
     */
    int logicalDeleteForDeletedProcessorFilters(Instant deleteThreshold);

    /**
     * Physically delete tasks that are logically deleted or complete for longer than the threshold.
     *
     * @param deleteThreshold Only physically delete tasks with an update time older than the threshold.
     * @return The number of physically deleted tasks.
     */
    int physicallyDeleteOldTasks(Instant deleteThreshold);


    List<ExistingCreatedTask> findExistingCreatedTasks(long lastTaskId, int filterId, int limit);
}
