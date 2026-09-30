/*
 * Copyright 2016 Crown Copyright
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
import java.util.Collection;
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
     * Return tasks whose processing lease has expired - PROCESSING tasks whose heartbeat
     * (status time) is older than {@code statusOlderThan} - to CREATED, unowned, so another
     * node can pick them up. The staleness condition is checked atomically in the update, so a
     * task whose heartbeat renews concurrently is not reaped. The version is incremented,
     * which is the fence: if the original node is in fact alive but unable to renew, its
     * eventual terminal status write fails its version check and is abandoned rather than
     * overwriting a task that may now be owned elsewhere.
     *
     * @param statusOlderThan Reap PROCESSING tasks whose status time is older than this.
     * @return The number of tasks reaped.
     */
    long reapDeadTasks(Instant statusOlderThan);

    /**
     * Count tasks the dead task reap would take: PROCESSING rows whose heartbeat is older than
     * {@code statusOlderThan}.
     * <p>
     * Exists so that something other than the reaper can notice the reaper is not working. A
     * disabled or failing reaper is silent by nature - the symptom is dead tasks accumulating
     * rather than any error - so this measures the symptom, which also catches causes other than
     * the job being switched off. In a healthy cluster it is zero or close to it.
     *
     * @param statusOlderThan Count PROCESSING tasks whose status time is older than this.
     * @return The number of tasks currently eligible for reaping.
     */
    int countDeadTasks(Instant statusOlderThan);

    /**
     * Renew the heartbeat of tasks this node is currently processing by setting their status
     * time to {@code nowMs}. A row is only stamped if it is still
     * {@link TaskStatus#PROCESSING} and still owned by the named node, so a task that has
     * completed, or been disowned and claimed elsewhere, is never touched. The row version is
     * deliberately not incremented so optimistic locking on status changes is unaffected.
     *
     * @param nodeName The node whose tasks these are.
     * @param taskIds  The tasks the node believes it is processing.
     * @param nowMs    The heartbeat time to stamp.
     * @return The number of task rows stamped, which may be less than the number of ids
     * supplied if tasks changed state concurrently.
     */
    int renewTaskHeartbeats(String nodeName,
                            Collection<Long> taskIds,
                            long nowMs);

    /**
     * Take ownership of up to {@code limit} of a filter's oldest waiting tasks, moving them
     * straight from {@link TaskStatus#CREATED} to {@link TaskStatus#PROCESSING} owned by this
     * node, in one transaction (PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.3).
     * <p>
     * The rows are locked with {@code FOR UPDATE SKIP LOCKED}, which is what makes decentralised
     * claiming cheap: concurrent nodes each get a <em>distinct</em> set of rows on the first
     * attempt rather than one winning and the rest retrying, so contention stops being wasted
     * work. Every node scans from the head in id order, so FIFO is preserved, and because the
     * scan and the claim are adjacent there is no staleness window and so no need for cursors or
     * low water marks.
     * <p>
     * The caller must deal with tasks whose meta is still locked <em>after</em> this returns, by
     * releasing them. Meta lives behind a different connection provider, so checking it here
     * would mean holding InnoDB row locks across another module's transaction.
     *
     * @param filterId The filter to claim tasks for.
     * @param nodeName The node claiming them.
     * @param limit    The most tasks to claim.
     * @return The claimed tasks, oldest first. Fewer than {@code limit}, possibly none, if the
     * filter has run out of waiting tasks or other nodes are claiming them at the same time.
     */
    List<ProcessorTask> claimTasks(int filterId, String nodeName, int limit);

    /**
     * Find which of the supplied filters currently have tasks waiting in
     * {@link TaskStatus#CREATED}. This is the availability summary a worker node uses to decide
     * which of the filters it is eligible to process are worth claiming from, instead of asking
     * each filter in turn (PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.2).
     * <p>
     * Supplying the filter ids explicitly gives equality on the leading two columns of
     * {@code processor_task_filter_id_status_id_meta_id} with MIN on the third, so this is one
     * index descent per filter with no row lookups and no dependence on optimiser skip-scan
     * heuristics. {@code TestProcessorTaskQueryPlans} asserts that plan; if it ever regresses to
     * a scan of the CREATED backlog this stops being affordable to run continuously on every
     * node.
     *
     * @param filterIds The filters to summarise, i.e. the ones this node is eligible to process.
     * @return Filter id to the id of the oldest CREATED task for that filter. Filters with no
     * CREATED tasks are absent, so an empty map means there is nothing here for this node to do.
     */
    Map<Integer, Long> getTaskAvailability(Collection<Integer> filterIds);

    /**
     * Return tasks left behind by the master node's task queue when the cluster switched to
     * worker node claiming - rows still {@link TaskStatus#QUEUED} or {@link TaskStatus#ASSIGNED}
     * whose status time is older than {@code statusOlderThan} - to CREATED, unowned
     * (PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.4).
     * <p>
     * Nothing in the new mode writes either status, so such a row is residue by definition, and
     * without this it is stranded forever: the claim path only looks for CREATED, the dead task
     * reap only looks at PROCESSING, and everything that used to recover QUEUED rows belongs to
     * the master queue that has gone. The stranded row means a stream that is silently never
     * processed. The version is bumped, so an in flight assignment from a node still running in
     * the old mode fails its version check and is abandoned rather than forced.
     *
     * @param statusOlderThan Sweep rows whose status time is older than this.
     * @return The number of tasks returned to CREATED.
     */
    long sweepQueuedTasks(Instant statusOlderThan);

    /**
     * Count the current number of tasks for a filter matching the specified status.
     *
     * @param filterId The filter to count tasks for.
     * @param status   Task status.
     * @return The number of tasks matching the specified status.
     */
    int countTasksForFilter(int filterId, TaskStatus status);

    /**
     * Count the current number of tasks for a filter matching the specified status, both for a single node and
     * for the whole cluster. Both counts come from one query so that they are a consistent view of the same
     * moment, as the number of tasks being processed changes constantly.
     *
     * @param filterId The filter to count tasks for.
     * @param nodeName The node to count tasks for.
     * @param status   Task status.
     * @return The number of tasks matching the specified status for the node and for the cluster.
     */
    FilterTaskCounts countTasksForFilter(int filterId, String nodeName, TaskStatus status);

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
     * Only used by the master queue, i.e. when stroom.processor.claimTasksOnWorker is false.
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

    /**
     * Only used by the master queue, i.e. when stroom.processor.claimTasksOnWorker is false.
     */
    List<ExistingCreatedTask> findExistingCreatedTasks(long lastTaskId, int filterId, int limit);



    // --------------------------------------------------------------------------------


    /**
     * The number of tasks a filter has in a given status, for a single node and for the whole cluster.
     */
    record FilterTaskCounts(int nodeCount, int clusterCount) {

    }
}
