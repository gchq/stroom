/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.node.api.NodeInfo;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gh-5699. An in-memory registry of the processor tasks this node currently holds in
 * {@link stroom.processor.shared.TaskStatus#PROCESSING}, plus the periodic heartbeat that renews
 * their {@code status_time_ms} so that a live, long-running task can be told apart from one whose
 * node has died. The counterpart {@link ProcessorTaskReaper} returns tasks whose heartbeat has
 * gone un-renewed past {@code stroom.processor.taskLeaseTimeout} to CREATED. See
 * PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.4.
 * <p>
 * Tasks are registered when this node moves them to PROCESSING
 * ({@link DataProcessorTaskHandler}) and deregistered - unconditionally - when processing ends,
 * whatever the outcome. The heartbeat job runs on the scheduled-job pool, not the data-processing
 * pool, so a node whose processing threads are saturated keeps renewing; a heartbeat starved by
 * busy workers would cause exactly the false reap it exists to prevent.
 * <p>
 * A row is only stamped while it is still PROCESSING <em>and</em> still owned by this node, so a
 * task that has been reaped or completed elsewhere is never touched, and the row's
 * {@code version} is deliberately left alone so optimistic locking on status changes is
 * unaffected. Note that for PROCESSING rows the "Status Time" shown in the UI becomes
 * "time of last heartbeat".
 * <p>
 * <b>Self-fencing.</b> Once a task's heartbeat has gone un-renewed past the lease timeout, the
 * reaper may hand it to another node - so a node that has failed to renew for that long must
 * assume it has lost its leases and terminates its own in-flight tasks, because two nodes
 * processing the same stream produce duplicate output that nothing downstream catches.
 * Termination is driven by this node's own <em>failure to renew</em>, never by a reaper
 * notification: a node that cannot reach the database cannot be told anything. A node in this
 * state has been unable to write to the database for the whole timeout window, so the risk of
 * killing genuine work over a transient blip is small at the ratios in use (1m heartbeat / 10m
 * lease). The version bump on reap is the other half of the protection: even a terminated
 * task's final status write is abandoned rather than forced ({@code changeTaskStatus}).
 */
@Singleton
public class ProcessorTaskHeartbeat {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskHeartbeat.class);

    /**
     * Processor task id -> what we know about the task we are holding.
     */
    private final Map<Long, HeldTask> processingTasks = new ConcurrentHashMap<>();

    private final ProcessorTaskDao processorTaskDao;
    private final NodeInfo nodeInfo;
    private final TaskManager taskManager;
    private final Provider<ProcessorConfig> processorConfigProvider;

    /**
     * When we last knew our heartbeats to be current: the last successful renewal, or the moment
     * the registry last went from empty to non-empty (a fresh claim is itself evidence the
     * database was reachable). Volatile: written by the job thread and register(), read by both.
     */
    private volatile long lastRenewalSuccessMs;

    @Inject
    public ProcessorTaskHeartbeat(final ProcessorTaskDao processorTaskDao,
                                  final NodeInfo nodeInfo,
                                  final TaskManager taskManager,
                                  final Provider<ProcessorConfig> processorConfigProvider) {
        this.processorTaskDao = processorTaskDao;
        this.nodeInfo = nodeInfo;
        this.taskManager = taskManager;
        this.processorConfigProvider = processorConfigProvider;
        this.lastRenewalSuccessMs = System.currentTimeMillis();
    }

    /**
     * Record that this node has moved the task to PROCESSING and holds it.
     *
     * @param taskId       The processor task id.
     * @param filterId     The filter the task belongs to, so this node can answer how many of a
     *                     filter's tasks it is running without asking the database.
     * @param stroomTaskId The id of the stroom task processing it, so self-fencing can
     *                     terminate it; null before processing starts, and where the context has
     *                     no id.
     */
    public void register(final long taskId, final Integer filterId, final TaskId stroomTaskId) {
        if (processingTasks.isEmpty()) {
            // The renewal clock only matters while we hold tasks; start it now rather than
            // letting a long idle period count against the first task claimed after it.
            lastRenewalSuccessMs = System.currentTimeMillis();
        }
        processingTasks.put(taskId, new HeldTask(filterId, stroomTaskId));
        LOGGER.trace("Registered task {} for heartbeat", taskId);
    }

    /**
     * Record that this node is no longer processing the task. Safe to call for a task that was
     * never registered.
     */
    public void deregister(final long taskId) {
        processingTasks.remove(taskId);
        LOGGER.trace("Deregistered task {} from heartbeat", taskId);
    }

    /**
     * Scheduled job entry point ("Processor Task Heartbeat").
     */
    public void exec() {
        try {
            renew(System.currentTimeMillis());
        } catch (final RuntimeException e) {
            // Failure here is what starts the self-fencing clock, so shout about it.
            LOGGER.error(() -> "Failed to renew processing task heartbeats ("
                               + processingTasks.size() + " tasks held): " + e.getMessage(), e);
        }
        fenceIfRenewalOverdue(System.currentTimeMillis());
    }

    /**
     * Renew the heartbeat of every registered task, stamping {@code nowMs}. Factored with "now"
     * as a parameter for testing (there is no injectable clock in stroom-util; same approach
     * as #5683).
     *
     * @return the number of task rows stamped.
     */
    public int renew(final long nowMs) {
        // Snapshot so the DAO sees a stable collection; tasks completing mid-renewal are the
        // normal case, not an error, so a stamped count below the snapshot size is expected.
        final List<Long> taskIds = List.copyOf(processingTasks.keySet());
        final int count;
        if (taskIds.isEmpty()) {
            LOGGER.trace("No processing tasks to heartbeat");
            count = 0;
        } else {
            count = processorTaskDao.renewTaskHeartbeats(nodeInfo.getThisNodeName(), taskIds, nowMs);
            LOGGER.debug(() -> "Renewed heartbeat of " + count + "/" + taskIds.size() + " processing tasks");
        }
        // An empty registry is also a success - there was nothing whose renewal could be
        // overdue, so the fencing clock must not run either.
        lastRenewalSuccessMs = nowMs;
        return count;
    }

    /**
     * Self-fence: if we hold tasks but have not managed to renew their heartbeats within the
     * lease timeout, the reaper is entitled to have handed them to other nodes, so terminate
     * our in-flight work rather than produce duplicate output. Factored with "now" as a
     * parameter for testing.
     */
    public void fenceIfRenewalOverdue(final long nowMs) {
        if (processingTasks.isEmpty()) {
            return;
        }
        final long leaseTimeoutMs = processorConfigProvider.get().getTaskLeaseTimeout().toMillis();
        final long sinceSuccessMs = nowMs - lastRenewalSuccessMs;
        if (sinceSuccessMs > leaseTimeoutMs) {
            LOGGER.error(() -> "SELF-FENCING - no successful heartbeat renewal for " + sinceSuccessMs
                               + "ms, longer than the lease timeout (" + leaseTimeoutMs + "ms, "
                               + "stroom.processor.taskLeaseTimeout). The tasks this node is processing "
                               + "may have been reaped and given to other nodes, so terminating all "
                               + processingTasks.size() + " in-flight tasks to prevent duplicate output.");
            processingTasks.forEach((taskId, held) -> {
                if (held.stroomTaskId() != null) {
                    LOGGER.warn("Self-fencing - terminating processing of task {}", taskId);
                    taskManager.terminate(held.stroomTaskId());
                } else {
                    // Either the task was claimed but has not started running yet, or the
                    // processing context had no id (only the case in tests). Nothing to
                    // terminate; the version bump on reap is what stops it writing a status.
                    LOGGER.error("Self-fencing - cannot terminate processing of task {}, "
                                 + "no stroom task id was registered for it", taskId);
                }
            });
        }
    }

    /**
     * When this node last knew its heartbeats to be current. Diagnostics: an age here longer than
     * the heartbeat job interval means this node is on its way to fencing itself.
     */
    public long getLastRenewalSuccessMs() {
        return lastRenewalSuccessMs;
    }

    /**
     * The number of tasks this node currently believes it is processing.
     */
    public int size() {
        return processingTasks.size();
    }

    /**
     * How many of a filter's tasks this node is currently processing. Exact and free - a node
     * knows what it is running - so a per node processing limit never needs a query, unlike a
     * cluster wide one.
     */
    public int countForFilter(final int filterId) {
        return (int) processingTasks
                .values()
                .stream()
                .filter(held -> Objects.equals(filterId, held.filterId()))
                .count();
    }


    // --------------------------------------------------------------------------------


    /**
     * @param filterId     The filter the task belongs to, for per filter counting.
     * @param stroomTaskId The stroom task processing it, used to terminate it when self-fencing.
     *                     Null while the task is claimed but not yet running.
     */
    private record HeldTask(Integer filterId, TaskId stroomTaskId) {

    }
}
