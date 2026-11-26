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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * gh-5699. Returns dead tasks - PROCESSING rows whose heartbeat ({@code status_time_ms}, renewed
 * by {@link ProcessorTaskHeartbeat}) has not been renewed within
 * {@code stroom.processor.taskLeaseTimeout} - to CREATED, unowned, so they can be picked up
 * again. Replaces the retired {@code disownDeadTasks}, which keyed off whole-node contact time
 * (so a node's death took its tasks' recovery with it whenever there was no master) and which
 * only ran on the master node.
 * <p>
 * Reaping is recovery and task creation is production: different failure domains, different
 * cadences, no correctness relationship - so this is its own scheduled job with its own cluster
 * lock, following the established cluster-locked-job pattern ({@code PhysicalDeleteExecutor},
 * {@code SQLStatisticAggregationManager}). The distinct lock name matters: sharing the task
 * creator's lock would starve this job under {@code tryLock} exactly when the cluster is
 * busiest. {@code tryLock} rather than a blocking lock because the sweep is idempotent - if
 * another node is already reaping there is nothing to wait for, and the next scheduled run
 * catches anything missed.
 * <p>
 * The job runs on every node and the lock serialises them; no node is special. Recovery latency
 * is the lease timeout plus at most one job interval.
 * <p>
 * When {@code stroom.processor.claimTasksOnWorker} is true this also sweeps QUEUED and ASSIGNED
 * rows back to CREATED. Nothing in that mode writes either status, so such a row is what the
 * master's queue left behind when the cluster was cut over to claiming - without the
 * sweep it is stranded forever and its stream silently never processed. See
 * {@link ProcessorTaskDao#sweepQueuedTasks(Instant)}.
 * <p>
 * This also puts back tasks that hit a fault, once they have waited for {@code stroom.processor.taskRetryDelay}, and
 * logs a warning of how many are waiting. Faults that do not count are tried again for as long as it takes to put
 * their cause right, so something has to say that there are some.
 * <p>
 * A reaped task's version is bumped (see
 * {@link ProcessorTaskDao#reapDeadTasks(Instant)}), which fences the original owner: if that
 * node is alive but unable to renew, its terminal status write fails its version check and is
 * abandoned. The other half of that protection is self-fencing - a node that cannot renew its
 * heartbeat terminates its own in-flight tasks ({@link ProcessorTaskHeartbeat}) - because
 * fencing driven from here could never reach a node that cannot be contacted.
 */
@Singleton
public class ProcessorTaskReaper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskReaper.class);

    /**
     * Deliberately NOT the task creator's lock name - see the class javadoc.
     */
    private static final String LOCK_NAME = "ProcessorTaskReaper";

    // The most tasks of each filter to put back to be tried again in one go, so that a fault that affects thousands of
    // tasks doesn't return them all at once. The rest are put back the next time the job runs. Each filter has its own
    // limit so that one that can't be run doesn't delay the others.
    private static final int MAX_RETRY_TASKS_TO_PUT_BACK = 1_000;

    private static final Duration RETRY_WARNING_INTERVAL = Duration.ofHours(1);

    private final ClusterLockService clusterLockService;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final ProcessorTaskDao processorTaskDao;
    private final Provider<ProcessorConfig> processorConfigProvider;

    // When this node last warned of a filter's tasks waiting to be tried again, by filter id. Each node keeps its own,
    // so each logs its own warnings. Only used under the cluster lock.
    private final Map<Integer, Instant> lastRetryWarnings = new HashMap<>();

    @Inject
    public ProcessorTaskReaper(final ClusterLockService clusterLockService,
                               final TargetNodeSetFactory targetNodeSetFactory,
                               final ProcessorTaskDao processorTaskDao,
                               final Provider<ProcessorConfig> processorConfigProvider) {
        this.clusterLockService = clusterLockService;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.processorTaskDao = processorTaskDao;
        this.processorConfigProvider = processorConfigProvider;
    }

    /**
     * Scheduled job entry point ("Processor Task Reaper").
     */
    public void exec() {
        reap(Instant.now());
    }

    /**
     * Factored with "now" as a parameter for testing (no injectable clock in stroom-util; same
     * approach as #5683).
     */
    public void reap(final Instant now) {
        try {
            clusterLockService.tryLock(LOCK_NAME, () -> {
                final ProcessorConfig processorConfig = processorConfigProvider.get();
                final StroomDuration leaseTimeout = processorConfig.getTaskLeaseTimeout();
                final Instant statusOlderThan = now.minus(leaseTimeout);
                LOGGER.debug(() -> "Reaping PROCESSING tasks with no heartbeat since " + statusOlderThan);
                processorTaskDao.reapDeadTasks(statusOlderThan);

                if (processorConfig.isClaimTasksOnWorker()) {
                    // Only in claiming mode: with the master queue in use these rows are the queue,
                    // not residue, and sweeping them would destroy live work. In claiming mode
                    // nothing writes either status, so a stale one is what the queue left behind
                    // when the cluster was cut over, and without this it is stranded forever.
                    // A standing duty rather than a one-time migration - idempotent, and in steady
                    // state one indexed probe that finds nothing.
                    LOGGER.debug(() -> "Sweeping QUEUED/ASSIGNED tasks not touched since " + statusOlderThan);
                    processorTaskDao.sweepQueuedTasks(statusOlderThan);
                } else {
                    sweepQueuedTasksOfInactiveNodes(statusOlderThan);
                }

                final Map<Integer, Integer> retryTasks = processorTaskDao.countRetryTasksByFilter();
                warnOfRetryTasks(now, retryTasks);
                final Instant retryThreshold = now.minus(processorConfig.getTaskRetryDelay());
                retryTasks.keySet().forEach(filterId -> putBackRetryTasks(filterId, retryThreshold));
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void putBackRetryTasks(final int filterId, final Instant statusOlderThan) {
        try {
            final int count = processorTaskDao.putBackRetryTasks(
                    filterId, statusOlderThan, MAX_RETRY_TASKS_TO_PUT_BACK);
            if (count > 0) {
                LOGGER.info("Put back {} tasks of processor filter {} that hit a fault to be tried again",
                        count, filterId);
            }
        } catch (final RuntimeException e) {
            // This must not stop those of other filters from being put back.
            LOGGER.error(e::getMessage, e);
        }
    }

    // With the master queue in use, tasks left queued by a master that has died or been removed have nothing else
    // to put them back. A node that is active puts back its own, so whichever node this one thinks is the master,
    // the queue of a live master is never swept.
    private void sweepQueuedTasksOfInactiveNodes(final Instant statusOlderThan) {
        try {
            final Set<String> activeNodes = new HashSet<>(targetNodeSetFactory.getEnabledActiveTargetNodeSet());
            activeNodes.add(targetNodeSetFactory.getMasterNode());
            activeNodes.remove(null);
            if (!activeNodes.isEmpty()) {
                processorTaskDao.sweepQueuedTasksOfInactiveNodes(statusOlderThan, activeNodes);
            }
        } catch (final NodeNotFoundException | NullClusterStateException e) {
            // Without knowing which nodes are active nothing can be done. Tasks are left until it is known.
            LOGGER.debug(e::getMessage, e);
        } catch (final RuntimeException e) {
            // This must not stop tasks from being put back to be tried again.
            LOGGER.error(e::getMessage, e);
        }
    }

    private void warnOfRetryTasks(final Instant now, final Map<Integer, Integer> retryTasks) {
        try {
            lastRetryWarnings.values().removeIf(lastWarning ->
                    !lastWarning.plus(RETRY_WARNING_INTERVAL).isAfter(now));
            retryTasks.forEach((filterId, count) -> {
                if (lastRetryWarnings.putIfAbsent(filterId, now) == null) {
                    final String reason = processorTaskDao.findLatestRetryReason(filterId).orElse("unknown");
                    LOGGER.warn("{} tasks of processor filter {} have hit a fault and are waiting to be tried again. "
                                + "Latest reason: {}", count, filterId, reason);
                }
            });
        } catch (final RuntimeException e) {
            // This must not stop tasks from being put back.
            LOGGER.error(e::getMessage, e);
        }
    }
}
