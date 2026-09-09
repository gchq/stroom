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

import stroom.meta.api.MetaService;
import stroom.node.api.NodeInfo;
import stroom.processor.impl.ProcessorProfileCache.ProfileResult;
import stroom.processor.impl.ProcessorTaskAvailability.FilterAvailability;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * gh-5699. Finds and claims this node's own processor tasks, instead of asking the master node's
 * in-memory queue for them. Used when {@code stroom.processor.claimTasksOnWorker} is true. That
 * is an experimental mode and is <b>off by default</b>, so by default nothing here runs in
 * production and {@code ProcessorTaskQueueManagerImpl} does the work instead. Everything in this
 * class is nonetheless covered by tests that turn the mode on explicitly - see
 * {@code TestProcessorTaskClaimer}, {@code TestProcessorTaskClaiming} and the app level
 * {@code TestWorkerTaskClaiming} - so that it cannot rot silently while unused.
 * See PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.3.
 * <p>
 * <b>Threading.</b> The safe operations here (SKIP LOCKED claims, concurrent maps, atomic
 * counters) mean concurrent calls cannot corrupt anything, but the concurrency limits are computed
 * from a cached cluster count and so would over-provision if several fetches ran at once. In
 * practice they do not: {@code DistributedTaskFetcher} is single flight per node.
 * <p>
 * The shape is: ask {@link ProcessorTaskAvailability} which of the filters this node may process
 * have work, then walk them highest priority first claiming from each until the request is
 * satisfied. There is no cache of task identities anywhere - the scan and the claim happen in one
 * statement pair inside one transaction - so there is no staleness window to manage and no way for
 * two nodes to believe they hold the same task.
 * <p>
 * <b>Concurrency limits.</b> A limit on how many of a filter's tasks <em>this node</em> may run at
 * once needs no query at all: the heartbeat registry already knows exactly what this node is
 * running. Only a genuinely cluster wide limit has to ask the database, and then only for filters
 * that actually have one, and the answer is cached for {@link #CLUSTER_COUNT_CACHE_MS} so that a
 * busy node does not count the cluster once per claim. The cached count is carried forward by what
 * the registry has seen since it was taken, so this node's own claims and completions inside the
 * window are exact and only the rest of the cluster is approximated - see
 * {@link #getClusterCount(ProcessorFilter)}. Overshooting a cluster limit by up to
 * (other nodes x batch) within that window is accepted, consistent with the existing behaviour
 * that all of these limits deliberately over provision rather than throttle exactly.
 */
@Singleton
public class ProcessorTaskClaimer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskClaimer.class);

    /**
     * How long a cluster wide PROCESSING count may be reused. Short enough that it tracks a
     * changing cluster, long enough that a node claiming repeatedly does not re-count for every
     * batch. Not a property: it trades an approximation nobody tunes against a query rate nobody
     * sees, and the limits it feeds are over provisioning limits anyway.
     */
    private static final long CLUSTER_COUNT_CACHE_MS = 1_000;

    private final ProcessorTaskAvailability processorTaskAvailability;
    private final ProcessorTaskDao processorTaskDao;
    private final ProcessorProfileCache processorProfileCache;
    private final ProcessorTaskHeartbeat processorTaskHeartbeat;
    private final FilterFetchBackoff filterFetchBackoff;
    private final MetaService metaService;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final Provider<ProcessorConfig> processorConfigProvider;

    private final Map<Integer, ClusterCount> clusterCounts = new ConcurrentHashMap<>();

    /** Diagnostics only - see {@link #getClaimStats()}. */
    private final AtomicLong claimAttempts = new AtomicLong();
    private final AtomicLong tasksClaimed = new AtomicLong();
    private final AtomicLong emptyClaims = new AtomicLong();
    private final AtomicLong lockedMetaReleases = new AtomicLong();

    @Inject
    public ProcessorTaskClaimer(final ProcessorTaskAvailability processorTaskAvailability,
                                final ProcessorTaskDao processorTaskDao,
                                final ProcessorProfileCache processorProfileCache,
                                final ProcessorTaskHeartbeat processorTaskHeartbeat,
                                final FilterFetchBackoff filterFetchBackoff,
                                final MetaService metaService,
                                final NodeInfo nodeInfo,
                                final SecurityContext securityContext,
                                final Provider<ProcessorConfig> processorConfigProvider) {
        this.processorTaskAvailability = processorTaskAvailability;
        this.processorTaskDao = processorTaskDao;
        this.processorProfileCache = processorProfileCache;
        this.processorTaskHeartbeat = processorTaskHeartbeat;
        this.filterFetchBackoff = filterFetchBackoff;
        this.metaService = metaService;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.processorConfigProvider = processorConfigProvider;
    }

    /**
     * Claim up to {@code count} tasks for this node to process now.
     *
     * @param count The number of free processing slots to fill.
     * @return The claimed tasks, already owned by this node and in PROCESSING, highest priority
     * filter first. Empty if there is nothing this node is both allowed and able to run.
     */
    public List<ProcessorTask> claimTasks(final int count) {
        if (count <= 0) {
            return List.of();
        }
        return securityContext.asProcessingUserResult(() -> doClaimTasks(count));
    }

    private List<ProcessorTask> doClaimTasks(final int count) {
        final String nodeName = nodeInfo.getThisNodeName();
        final StroomDuration skipEmptyFilterFetchDuration =
                processorConfigProvider.get().getSkipEmptyFilterFetchDuration();
        final List<FilterAvailability> filtersWithWork = processorTaskAvailability.getFiltersWithWork();
        final List<ProcessorTask> claimed = new ArrayList<>(count);

        for (final FilterAvailability availability : filtersWithWork) {
            if (claimed.size() >= count) {
                break;
            }
            final ProcessorFilter filter = availability.filter();

            // The summary said this filter had work, so an empty claim means another node got
            // there first. Leave it alone until the summary reports different work waiting.
            if (!filterFetchBackoff.isClaimDue(
                    filter, skipEmptyFilterFetchDuration, availability.oldestTaskId())) {
                LOGGER.trace("Skipping backed off filter {}", filter.getId());
                continue;
            }

            final int limit = getClaimLimit(filter, count - claimed.size(), nodeName);
            if (limit <= 0) {
                continue;
            }

            claimAttempts.incrementAndGet();
            final List<ProcessorTask> claimedForFilter =
                    processorTaskDao.claimTasks(filter.getId(), nodeName, limit);
            if (claimedForFilter.isEmpty()) {
                // We were told this filter had work and got none of it, so another node claimed it
                // first. This is the only thing that counts as an empty claim: SKIP LOCKED is
                // supposed to make it rare, and the count is the evidence for that (§3.3).
                emptyClaims.incrementAndGet();
                filterFetchBackoff.recordEmptyClaim(
                        filter, skipEmptyFilterFetchDuration, availability.oldestTaskId());
                continue;
            }

            final List<ProcessorTask> runnable = releaseLockedMeta(claimedForFilter, filter);
            registerHeartbeats(runnable, filter.getId());
            if (runnable.isEmpty()) {
                // We won the race but every task we took is for a stream still being written.
                // Deliberately not counted as an empty claim - that would make the claim win rate
                // unreadable by mixing in something that says nothing about contention - but still
                // worth leaving the filter alone briefly, because its oldest waiting work is the
                // meta we just found locked.
                filterFetchBackoff.recordEmptyClaim(
                        filter, skipEmptyFilterFetchDuration, availability.oldestTaskId());
            } else {
                tasksClaimed.addAndGet(runnable.size());
                filterFetchBackoff.recordFetchedTasks(filter);
                claimed.addAll(runnable);
            }
        }

        // Don't remember filters we are no longer looking at, e.g. ones that have been disabled or
        // that this node has stopped being eligible for.
        filterFetchBackoff.retainAll(filtersWithWork.stream().map(FilterAvailability::filter).toList());

        LOGGER.debug(() -> LogUtil.message("claimTasks() - asked for {}, claimed {} from {} filters with work",
                count, claimed.size(), filtersWithWork.size()));
        return claimed;
    }

    /**
     * Tasks whose meta is still being written cannot be processed yet, so give them straight back.
     * This deliberately happens <em>after</em> the claim rather than as a filter before it: meta
     * lives behind its own connection provider, so asking about it inside the claim transaction
     * would hold row locks across another module's transaction. Claiming a few tasks we cannot run
     * and returning them costs one extra update; nothing is skipped past, so they are simply
     * claimed again once the stream is complete.
     */
    private List<ProcessorTask> releaseLockedMeta(final List<ProcessorTask> tasks,
                                                  final ProcessorFilter filter) {
        final Set<Long> lockedMetaIds = metaService.findLockedMeta(
                tasks.stream().map(ProcessorTask::getMetaId).toList());
        if (lockedMetaIds.isEmpty()) {
            return tasks;
        }

        final Map<Boolean, List<ProcessorTask>> byLocked = tasks
                .stream()
                .collect(Collectors.partitioningBy(task -> lockedMetaIds.contains(task.getMetaId())));
        final Set<Long> lockedTaskIds = byLocked.get(true)
                .stream()
                .map(ProcessorTask::getId)
                .collect(Collectors.toSet());
        final int released = processorTaskDao.releaseTasks(lockedTaskIds, TaskStatus.PROCESSING);
        lockedMetaReleases.addAndGet(released);
        LOGGER.debug(() -> LogUtil.message("releaseLockedMeta() - released {}/{} tasks claimed for {} "
                                           + "whose meta is still locked",
                released, tasks.size(), filter.getFilterInfo()));

        return byLocked.get(false);
    }

    /**
     * Start heart-beating a claimed task straight away rather than waiting for it to start running.
     * It is in PROCESSING and owned by this node from the moment it is claimed, so from that moment
     * a lapse in heartbeats has to mean this node has died - otherwise a task claimed just before a
     * pause would look dead to the reaper through no fault of its own. The stroom task id that
     * self-fencing needs is only known once processing begins, and
     * {@link DataProcessorTaskHandler} re-registers with it then.
     */
    private void registerHeartbeats(final List<ProcessorTask> tasks, final int filterId) {
        // The filter id comes from the claim rather than from the task, which carries whatever the
        // filter cache had: a cache miss there would leave the task uncounted by the per node
        // concurrency limit that countForFilter feeds.
        tasks.forEach(task -> processorTaskHeartbeat.register(task.getId(), filterId, null));
    }

    /**
     * How many tasks it is worth claiming for this filter now, given everything that limits how
     * many of its tasks may run at once.
     */
    private int getClaimLimit(final ProcessorFilter filter, final int wanted, final String nodeName) {
        int limit = wanted;
        final String profileName = filter.getProfileName();

        if (profileName != null) {
            // A filter with a profile is governed by that profile alone.
            final ProfileResult profileResult;
            try {
                profileResult = processorProfileCache.getProfile(nodeName, profileName);
            } catch (final RuntimeException e) {
                // Eligibility already resolved this profile a moment ago, so this is a
                // configuration change landing mid claim. Claim nothing rather than guess.
                LOGGER.error(() -> "Error getting processing profile for filter (filter=" + filter
                                   + ", profileName=" + profileName + "), claiming no tasks for it", e);
                return 0;
            }
            if (profileResult.maxNodeThreads() < Integer.MAX_VALUE) {
                limit = Math.min(limit,
                        profileResult.maxNodeThreads() - processorTaskHeartbeat.countForFilter(filter.getId()));
            }
            if (limit > 0 && profileResult.maxClusterThreads() < Integer.MAX_VALUE) {
                limit = Math.min(limit, profileResult.maxClusterThreads() - getClusterCount(filter));
            }

        } else if (filter.isProcessingTaskCountBounded()) {
            limit = Math.min(limit, filter.getMaxProcessingTasks() - getClusterCount(filter));
        }

        return Math.max(0, limit);
    }

    /**
     * How many of this filter's tasks the whole cluster is processing, from a short lived cache
     * corrected for what this node has done since the count was taken.
     * <p>
     * The database is asked at most once per {@link #CLUSTER_COUNT_CACHE_MS}, but the cached
     * answer is not used raw: this node's own claims and completions inside that window are known
     * exactly and for free from the heartbeat registry, so the count is carried forward by the
     * change in that registry since the query. A count taken before a claim therefore cannot be
     * spent twice by back to back claims, and capacity freed by this node's tasks finishing is
     * seen without waiting for the cache to expire.
     * <p>
     * The registry gives the <em>change</em> rather than the absolute local figure because it
     * only knows about tasks this JVM claimed. Tasks left in PROCESSING under this node's name by
     * a previous run are the reaper's business, but they are real cluster load in the meantime and
     * the query counts them; taking a delta keeps them counted.
     */
    private int getClusterCount(final ProcessorFilter filter) {
        final long nowMs = System.currentTimeMillis();
        final ClusterCount cached = clusterCounts.get(filter.getId());
        if (cached != null && cached.isCurrent(nowMs)) {
            return cached.estimate(processorTaskHeartbeat.countForFilter(filter.getId()));
        }
        final int count = processorTaskDao.countTasksForFilter(filter.getId(), TaskStatus.PROCESSING);
        // The baseline is taken after the query, not before. A task of this node's that finishes
        // while the query is in flight is already missing from the count that comes back, so a
        // baseline taken beforehand would have the delta subtract it a second time and leave the
        // estimate under the true cluster count for the rest of the window - the direction that
        // over claims. Taking it afterwards can only over count, which claims less. Nothing can be
        // claimed in the gap to make that worse, because claiming is single flight per node.
        final int localCount = processorTaskHeartbeat.countForFilter(filter.getId());
        clusterCounts.put(filter.getId(), new ClusterCount(count, localCount, nowMs));
        return count;
    }

    /**
     * Release tasks this node claimed but is not going to process after all, e.g. because the job
     * was disabled or the node is shutting down, so that another node picks them up rather than
     * waiting for the reaper to decide their lease has expired.
     */
    public int abandonTasks(final List<ProcessorTask> tasks) {
        if (tasks.isEmpty()) {
            return 0;
        }
        final Set<Long> taskIds = tasks.stream().map(ProcessorTask::getId).collect(Collectors.toSet());
        final int released = processorTaskDao.releaseTasks(taskIds, TaskStatus.PROCESSING);
        taskIds.forEach(processorTaskHeartbeat::deregister);
        LOGGER.debug(() -> LogUtil.message("abandonTasks() - released {}/{} claimed tasks",
                released, tasks.size()));
        return released;
    }

    /**
     * Diagnostics for sysinfo. Claim win rate should be uninteresting - {@code SKIP LOCKED} means
     * concurrent nodes get distinct rows - so if empty claims are anything other than rare then an
     * assumption in §3.3 of the design is wrong.
     */
    public ClaimStats getClaimStats() {
        return new ClaimStats(
                claimAttempts.get(),
                tasksClaimed.get(),
                emptyClaims.get(),
                lockedMetaReleases.get(),
                processorTaskHeartbeat.size());
    }


    // --------------------------------------------------------------------------------


    public record ClaimStats(long claimAttempts,
                             long tasksClaimed,
                             long emptyClaims,
                             long lockedMetaReleases,
                             int tasksInFlight) {

    }


    // --------------------------------------------------------------------------------


    /**
     * A cluster wide PROCESSING count for one filter, as at {@code takenMs}, together with how
     * many of that filter's tasks this node held at that moment.
     *
     * @param count      The whole cluster's count when the query ran.
     * @param localCount This node's heartbeat registry count as at just after the query, the
     *                   baseline the estimate is carried forward from.
     * @param takenMs    When the query ran.
     */
    private record ClusterCount(int count, int localCount, long takenMs) {

        private boolean isCurrent(final long nowMs) {
            return nowMs >= takenMs && nowMs - takenMs < CLUSTER_COUNT_CACHE_MS;
        }

        /**
         * @param localCountNow This node's current heartbeat registry count for the filter.
         * @return The cluster count with this node's claims and completions since the query
         * applied. Never negative - the registry and the query can disagree transiently about
         * this node's own rows, and a negative count would read as free capacity.
         */
        private int estimate(final int localCountNow) {
            return Math.max(0, count + localCountNow - localCount);
        }
    }
}
