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
import stroom.processor.shared.ProcessorTask;
import stroom.task.api.TaskManager;
import stroom.task.api.TerminationCause;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * gh-5699. An in-memory registry of the processor tasks this node currently holds in
 * {@link stroom.processor.shared.TaskStatus#PROCESSING}, plus the periodic heartbeat that renews
 * their {@code status_time_ms} so that a live, long-running task can be told apart from one whose
 * node has died. The counterpart {@link ProcessorTaskReaper} returns tasks whose heartbeat has
 * gone un-renewed past {@code stroom.processor.taskLeaseTimeout} to CREATED. See
 * PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.4.
 * <p>
 * What is registered is a <em>run</em> of a task. A run is registered when this node moves its task
 * to PROCESSING ({@link DataProcessorTaskHandler}) and deregistered - unconditionally - when it has
 * ended and cleared up, whatever the outcome. A task that is put back, reaped or aborted may be
 * claimed again by the same node before the old run has noticed, so the runs of a task are kept
 * apart by their run count and one can't be mistaken for another. The heartbeat job runs on the
 * scheduled-job pool, not the data-processing pool, so a node whose processing threads are saturated
 * keeps renewing; a heartbeat starved by busy workers would cause exactly the false reap it exists
 * to prevent.
 * <p>
 * A row is only stamped while it is still PROCESSING <em>and</em> still owned by this node, so a
 * task that has been reaped or completed elsewhere is never touched, and the row's
 * {@code version} is deliberately left alone so optimistic locking on status changes is
 * unaffected. Note that for PROCESSING rows the "Status Time" shown in the UI becomes
 * "time of last heartbeat".
 * <p>
 * <b>Lost runs.</b> Each time, runs whose task is no longer PROCESSING on this node with the version
 * they know it by are ended, as something else has it now, or it has been aborted or deleted. A run
 * that is already ending is left alone, as it is the run that is changing its task.
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

    private static final Duration EARLIER_RUN_POLL = Duration.ofSeconds(1);

    private final Map<RunKey, HeldRun> runs = new ConcurrentHashMap<>();
    // Notified when a run has gone.
    private final Object goneLock = new Object();

    private final ProcessorTaskDao processorTaskDao;
    private final NodeInfo nodeInfo;
    private final TaskManager taskManager;
    private final Provider<ProcessorConfig> processorConfigProvider;

    /**
     * When we last knew our heartbeats to be current: the last successful renewal, or the moment
     * the registry last went from empty to non-empty (a fresh claim is itself evidence the
     * database was reachable). Volatile: written by the job thread and when a run is registered, read by both.
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

    /// Records that this node has claimed a task, so holds a run of it that has not started.
    ///
    /// @param task     The task as it was claimed.
    /// @param filterId The filter the task belongs to, so this node can answer how many of a filter's tasks it is
    ///                 running without asking the database.
    public void registerClaim(final ProcessorTask task, final int filterId) {
        startClock();
        runs.put(RunKey.of(task), new HeldRun(filterId, task.getVersion()));
        LOGGER.trace(() -> "Registered claim of " + task);
    }

    /// Records that a run of a task is starting, whether the task was claimed or has just been moved to PROCESSING.
    ///
    /// @param task         The task as the run knows it.
    /// @param filterId     The filter the task belongs to.
    /// @param stroomTaskId The id of the stroom task that the run is, so that it can be terminated. Null where the
    ///                     context has no id.
    /// @return Why the run must not start after all, or empty if it may.
    public Optional<TerminationCause> registerStart(final ProcessorTask task,
                                                    final int filterId,
                                                    final TaskId stroomTaskId) {
        startClock();
        LOGGER.trace(() -> "Registering start of " + task);
        return runs
                .computeIfAbsent(RunKey.of(task), key -> new HeldRun(filterId, task.getVersion()))
                .start(stroomTaskId);
    }

    /// Records that a run is about to set the status of its task, so must not be ended by this from now on. Does
    /// nothing if the run was never registered.
    ///
    /// @param task The task as the run knows it.
    public void registerEnding(final ProcessorTask task) {
        final HeldRun run = runs.get(RunKey.of(task));
        if (run != null) {
            run.end();
        }
    }

    /// Records that a run has ended, has cleared up and has nothing open. Does nothing if the run was never
    /// registered, and nothing to any other run of the same task.
    ///
    /// @param task The task as the run knows it.
    public void deregister(final ProcessorTask task) {
        runs.remove(RunKey.of(task));
        synchronized (goneLock) {
            goneLock.notifyAll();
        }
        LOGGER.trace(() -> "Deregistered " + task);
    }

    private void startClock() {
        if (runs.isEmpty()) {
            // The renewal clock only matters while we hold tasks; start it now rather than
            // letting a long idle period count against the first task claimed after it.
            lastRenewalSuccessMs = System.currentTimeMillis();
        }
    }

    /// Ends any earlier runs of a task that are still going on this node, and waits for them to go, so that two runs
    /// of one task never overlap on a node. They have lost the task, or there would not be a later run. An earlier run
    /// that has not started doesn't have to be waited for, as it won't.
    ///
    /// @param task    The task as the later run knows it.
    /// @param timeout How long to wait.
    /// @param giveUp  Whether to stop waiting.
    /// @return True if there are none, or false if there still are.
    public boolean awaitEarlierRuns(final ProcessorTask task,
                                    final Duration timeout,
                                    final BooleanSupplier giveUp) {
        final Map<RunKey, HeldRun> earlierRuns = runs
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().isEarlierRunOf(task))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        earlierRuns.forEach((key, run) -> {
            if (stop(key, run, TerminationCause.WORK_LOST)) {
                LOGGER.warn(() -> "Ending run " + key.runCount() + " of processor task " + key.taskId()
                                  + " as this node now has run " + task.getRunCount());
            }
        });

        final long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (goneLock) {
            while (earlierRuns.entrySet().stream().anyMatch(entry ->
                    runs.get(entry.getKey()) == entry.getValue() && entry.getValue().isGoing())) {
                final long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMs <= 0 || giveUp.getAsBoolean()) {
                    return false;
                }
                try {
                    goneLock.wait(Math.min(remainingMs, EARLIER_RUN_POLL.toMillis()));
                } catch (final InterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Scheduled job entry point ("Processor Task Heartbeat").
     */
    public void exec() {
        try {
            renew(System.currentTimeMillis());
            endLostRuns();
        } catch (final RuntimeException e) {
            // Failure to renew is what starts the self-fencing clock, so shout about it.
            LOGGER.error(() -> "Failed to renew processing task heartbeats or to find lost runs ("
                               + runs.size() + " runs held): " + e.getMessage(), e);
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
        final Set<Long> taskIds = runs.keySet().stream().map(RunKey::taskId).collect(Collectors.toSet());
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

    /// Ends the runs that have lost their task, i.e. it is no longer being processed by this node with the version
    /// they know it by. Those that have not started won't.
    ///
    /// @return The number of runs that were found to have lost their task and had not been ended already.
    public int endLostRuns() {
        // Found before the database is asked, so each was registered, and so had its task, before that.
        final Map<RunKey, HeldRun> activeRuns = new HashMap<>();
        runs.forEach((key, run) -> {
            if (run.isActive()) {
                activeRuns.put(key, run);
            }
        });
        if (activeRuns.isEmpty()) {
            return 0;
        }

        final Map<Long, Integer> versions = processorTaskDao.findProcessingVersions(
                nodeInfo.getThisNodeName(),
                activeRuns.keySet().stream().map(RunKey::taskId).collect(Collectors.toSet()));
        int count = 0;
        for (final Map.Entry<RunKey, HeldRun> entry : activeRuns.entrySet()) {
            final RunKey key = entry.getKey();
            final HeldRun run = entry.getValue();
            // A run that has changed the status of its task itself, since it was found to be active, is now ending so
            // isn't stopped.
            if (!Objects.equals(run.version, versions.get(key.taskId()))
                && stop(key, run, TerminationCause.WORK_LOST)) {
                LOGGER.warn(() -> "Ending run " + key.runCount() + " of processor task " + key.taskId()
                                  + " as this node no longer has the task");
                count++;
            }
        }
        return count;
    }

    // Returns true if the run has only just been told to stop.
    private boolean stop(final RunKey key, final HeldRun run, final TerminationCause terminationCause) {
        final boolean first = run.stop(terminationCause);
        // Done every time, in case it failed before.
        final Optional<TaskId> stroomTaskId = run.getStroomTaskIdToTerminate();
        if (stroomTaskId.isPresent()) {
            taskManager.terminate(stroomTaskId.get(), run.getStopCause());
        } else if (run.isRunning()) {
            // The processing context had no id, which is only the case in tests.
            LOGGER.error("Unable to end run {} of processor task {} as no stroom task id was registered for it",
                    key.runCount(), key.taskId());
        }
        return first;
    }

    /**
     * Self-fence: if we hold tasks but have not managed to renew their heartbeats within the
     * lease timeout, the reaper is entitled to have handed them to other nodes, so terminate
     * our in-flight work rather than produce duplicate output. Factored with "now" as a
     * parameter for testing.
     */
    public void fenceIfRenewalOverdue(final long nowMs) {
        if (runs.isEmpty()) {
            return;
        }
        final long leaseTimeoutMs = processorConfigProvider.get().getTaskLeaseTimeout().toMillis();
        final long sinceSuccessMs = nowMs - lastRenewalSuccessMs;
        if (sinceSuccessMs > leaseTimeoutMs) {
            LOGGER.error(() -> "SELF-FENCING - no successful heartbeat renewal for " + sinceSuccessMs
                               + "ms, longer than the lease timeout (" + leaseTimeoutMs + "ms, "
                               + "stroom.processor.taskLeaseTimeout). The tasks this node is processing "
                               + "may have been reaped and given to other nodes, so terminating all "
                               + runs.size() + " in-flight tasks to prevent duplicate output.");
            runs.forEach((key, run) -> {
                if (stop(key, run, TerminationCause.HEARTBEAT_OVERDUE)) {
                    LOGGER.warn("Self-fencing - ending run {} of processor task {}", key.runCount(), key.taskId());
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
        return runs.size();
    }

    /**
     * How many of a filter's tasks this node is currently processing. Exact and free - a node
     * knows what it is running - so a per node processing limit never needs a query, unlike a
     * cluster wide one.
     */
    public int countForFilter(final int filterId) {
        return (int) runs
                .values()
                .stream()
                .filter(run -> run.filterId == filterId)
                .count();
    }


    // --------------------------------------------------------------------------------


    private record RunKey(long taskId, int runCount) {

        private static RunKey of(final ProcessorTask task) {
            return new RunKey(task.getId(), task.getRunCount());
        }

        private boolean isEarlierRunOf(final ProcessorTask task) {
            return taskId == task.getId() && runCount < task.getRunCount();
        }
    }


    // --------------------------------------------------------------------------------


    // A run is claimed, running or ending, and has gone when it is no longer in the registry.
    private static class HeldRun {

        private final int filterId;
        private final Integer version;

        private State state = State.CLAIMED;
        private TaskId stroomTaskId;
        // The first reason the run was given to stop, if it has been.
        private TerminationCause stopCause;

        private HeldRun(final int filterId, final Integer version) {
            this.filterId = filterId;
            this.version = version;
        }

        private synchronized Optional<TerminationCause> start(final TaskId stroomTaskId) {
            if (stopCause == null) {
                this.stroomTaskId = stroomTaskId;
                state = State.RUNNING;
            }
            return Optional.ofNullable(stopCause);
        }

        private synchronized void end() {
            state = State.ENDING;
        }

        private synchronized boolean stop(final TerminationCause terminationCause) {
            if (!isActive() || stopCause != null) {
                return false;
            }
            stopCause = terminationCause;
            return true;
        }

        // Terminating a run that starts to end in the meantime does no harm, as what it was told to stop for is true.
        private synchronized Optional<TaskId> getStroomTaskIdToTerminate() {
            return State.RUNNING.equals(state) && stopCause != null
                    ? Optional.ofNullable(stroomTaskId)
                    : Optional.empty();
        }

        private synchronized TerminationCause getStopCause() {
            return stopCause;
        }

        private synchronized boolean isActive() {
            return !State.ENDING.equals(state);
        }

        private synchronized boolean isRunning() {
            return State.RUNNING.equals(state);
        }

        private synchronized boolean isGoing() {
            return !State.CLAIMED.equals(state);
        }
    }


    // --------------------------------------------------------------------------------


    private enum State {
        CLAIMED,
        RUNNING,
        ENDING
    }
}
