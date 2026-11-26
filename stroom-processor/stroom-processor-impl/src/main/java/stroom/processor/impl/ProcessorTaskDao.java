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
import stroom.processor.api.ProcessorTaskState;
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
import java.util.Optional;
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

    /// Puts back tasks queued by nodes that are not active, which can only have been left by an earlier master that
    /// died or was removed, as nothing else will put them back. A node that is active puts back its own. Each is put
    /// back by id and version.
    ///
    /// @param statusOlderThan Only put back tasks that have been queued since before this.
    /// @param activeNodeNames The nodes that are active, whose tasks are left alone.
    /// @return The number of tasks put back.
    long sweepQueuedTasksOfInactiveNodes(Instant statusOlderThan, Set<String> activeNodeNames);

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

    /// Puts tasks back to [TaskStatus#CREATED], unowned, so that they can be run, but only those that have not
    /// changed since the caller knew them. A task that has just been started, or put back by something else, is left
    /// alone. They keep their reason.
    ///
    /// @param tasks           The tasks as the caller knows them.
    /// @param currentStatuses The statuses that they may have.
    /// @return The number of tasks that were put back.
    int putBackTasks(Collection<ProcessorTask> tasks, Set<TaskStatus> currentStatuses);

    /// Puts back a task that a run tried to start, when it can't be told whether it was started. It is put back if
    /// it is still [TaskStatus#QUEUED] as the run knew it, or if it is [TaskStatus#PROCESSING] for this node on the
    /// run that the run would have started.
    ///
    /// @param task     The task as the run knew it before trying to start it.
    /// @param nodeName The node the run is on.
    /// @return True if it was put back.
    boolean putBackUnstarted(ProcessorTask task, String nodeName);

    /// Finds tasks that a node has queued and that have been waiting longer than they should, so that those which
    /// have been lost can be put back.
    ///
    /// @param nodeName        The node that queued them.
    /// @param statusOlderThan Only find tasks that have been queued since before this.
    /// @param afterId         Only find tasks with a higher id than this, to find the next page.
    /// @param limit           The most tasks to find.
    /// @return The tasks, with their ids and versions, in order of id.
    List<ProcessorTask> findQueuedTasks(String nodeName, Instant statusOlderThan, long afterId, int limit);

    /// Finds which tasks are being processed by a node, and the versions they have. A run still has its task only if
    /// the task is one of these and has the version that the run knows it by.
    ///
    /// @param nodeName The node.
    /// @param taskIds  The ids of the tasks to look for.
    /// @return The versions of those that the node is processing, by task id.
    Map<Long, Integer> findProcessingVersions(String nodeName, Collection<Long> taskIds);

    /// Changes the status of a task with no reason to record and no fault to count. Any reason it had is cleared.
    /// See [#changeTaskStatus(ProcessorTask, String, TaskStatus, Long, Long, String, boolean)].
    ///
    /// @param processorTask The task as the caller last knew it.
    /// @param nodeName      The node that has the task, or null if none does.
    /// @param status        The status to give it.
    /// @param startTime     When processing started, or null.
    /// @param endTime       When processing ended, or null.
    /// @return What happened.
    default TaskStatusChange changeTaskStatus(final ProcessorTask processorTask,
                                              final String nodeName,
                                              final TaskStatus status,
                                              final Long startTime,
                                              final Long endTime) {
        return changeTaskStatus(processorTask, nodeName, status, startTime, endTime, null, false);
    }

    /// Changes the status of a task, but only if it still has the version the caller last knew. Setting a task
    /// to [TaskStatus#PROCESSING] starts a new run of it, so its run count goes up in the same update.
    ///
    /// This is tried several times if the database can't be reached, and isn't given up if the thread is interrupted.
    ///
    /// @param processorTask The task as the caller last knew it.
    /// @param nodeName      The node that has the task, or null if none does.
    /// @param status        The status to give it.
    /// @param startTime     When processing started, or null.
    /// @param endTime       When processing ended, or null.
    /// @param reason        Why the task is given the status, or null if there is nothing to say. This replaces
    ///                      any reason it had.
    /// @param countFault    True if the task's fault count is to go up in the same update.
    /// @return What happened.
    TaskStatusChange changeTaskStatus(ProcessorTask processorTask,
                                      String nodeName,
                                      TaskStatus status,
                                      Long startTime,
                                      Long endTime,
                                      String reason,
                                      boolean countFault);

    /// Puts tasks of a filter that hit a fault back to [TaskStatus#CREATED], so that they are tried again. Those that
    /// have waited longest are put back first. They keep their reason, so that it can be seen why they are being tried
    /// again.
    ///
    /// @param filterId        The id of the filter.
    /// @param statusOlderThan Only put back tasks that have been waiting since before this.
    /// @param limit           The most tasks to put back.
    /// @return The number of tasks that were put back.
    int putBackRetryTasks(int filterId, Instant statusOlderThan, int limit);

    /// Counts the tasks that hit a fault and are waiting to be tried again.
    ///
    /// @return The number of tasks for each filter that has any, by filter id.
    Map<Integer, Integer> countRetryTasksByFilter();

    /// Sets a task that is `Committing` from a run to `Complete`.
    ///
    /// @param taskId   The id of the task.
    /// @param runCount The run that held it.
    /// @return False if it isn't `Committing` from that run.
    boolean completeHeld(long taskId, int runCount);

    /// Puts back a task that is `Committing` from a run, without counting a fault.
    ///
    /// @param taskId   The id of the task.
    /// @param runCount The run that held it.
    /// @param reason   Why it was put back.
    /// @return False if it isn't `Committing` from that run.
    boolean putBackHeld(long taskId, int runCount, String reason);

    /// Gets the status of tasks, and the run each is on.
    ///
    /// @param taskIds The ids of the tasks, of which there can be any number.
    /// @return The state of each, by its id. A task that no longer exists is `Deleted`, with a run count of zero.
    Map<Long, ProcessorTaskState> getStates(Collection<Long> taskIds);

    /// Finds tasks that are `Committing`.
    ///
    /// @param olderThan Only tasks that were set to `Committing` before this are found.
    /// @param afterId   Only tasks with a higher id than this are found.
    /// @param limit     The most to find.
    /// @return The tasks, in order of their ids.
    List<ProcessorTaskState> findHeld(Instant olderThan, long afterId, int limit);

    /// Finds the reason of the task of a filter that was the last to hit a fault, of those waiting to be tried again.
    ///
    /// @param filterId The id of the filter.
    /// @return The reason, if there is such a task and it has one.
    Optional<String> findLatestRetryReason(int filterId);

    ResultPage<ProcessorTask> find(final ExpressionCriteria criteria);

    ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria);

    void search(ExpressionCriteria criteria, FieldIndex fieldIndex, ValuesConsumer consumer);

    /// Logically deletes the unfinished tasks of a processor. Tasks that are committing are left to finish.
    ///
    /// @param processorId The id of the processor.
    /// @return The number of logically deleted tasks.
    int logicalDeleteByProcessorId(int processorId);

    /// Logically deletes the unfinished tasks of a processor filter. Tasks that are committing are left to finish.
    ///
    /// @param processorFilterId The id of the processor filter.
    /// @return The number of logically deleted tasks.
    int logicalDeleteByProcessorFilterId(int processorFilterId);

    /**
     * Logically delete tasks that are associated with filters that have been logically deleted for longer than the
     * threshold. Unlike when a filter is first deleted, this deletes them whatever their status, including those that
     * are committing or have been aborted, so that the filter can then be physically deleted.
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

    /// Physically delete tasks that have been aborted for longer than the threshold.
    ///
    /// @param deleteThreshold Only physically delete tasks that were aborted before this.
    /// @return The number of physically deleted tasks.
    int physicallyDeleteOldAbortedTasks(Instant deleteThreshold);

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
