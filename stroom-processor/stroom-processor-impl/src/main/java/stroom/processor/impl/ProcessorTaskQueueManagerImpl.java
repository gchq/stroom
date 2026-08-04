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

import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.meta.api.MetaService;
import stroom.node.api.NodeInfo;
import stroom.processor.impl.ProcessorProfileCache.ProfileResult;
import stroom.processor.impl.ProcessorTaskDao.FilterTaskCounts;
import stroom.processor.impl.ProgressMonitor.FilterProgressMonitor;
import stroom.processor.impl.ProgressMonitor.Phase;
import stroom.processor.impl.ProgressMonitor.SkipReason;
import stroom.processor.impl.QueueProcessTasksState.ProfileQueueState;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.TaskId;
import stroom.task.shared.ThreadPool;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Keep a pool of stream tasks ready to go.
 */
@Singleton
class ProcessorTaskQueueManagerImpl implements ProcessorTaskQueueManager, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskQueueManagerImpl.class);

    private static final int BATCH_SIZE = 1000;
    private static final int MAX_ASSIGNMENT_ATTEMPTS = 10;
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Fill Task Store", 3);

    private final ProcessorTaskDao processorTaskDao;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final NodeInfo nodeInfo;
    private final Provider<ProcessorConfig> processorConfigProvider;
    private final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider;
    private final MetaService metaService;
    private final SecurityContext securityContext;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final PrioritisedFilters prioritisedFilters;
    private final ProcessorProfileCache processorProfileCache;
    private final FilterFetchBackoff filterFetchBackoff;

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();

    /**
     * Our queue.
     */
    private final ConcurrentHashMap<ProcessorFilter, ProcessorTaskQueue> queueMap = new ConcurrentHashMap<>();
    private final AtomicBoolean fillingQueue = new AtomicBoolean();
    /**
     * Incremented every time the queue is filled, however the fill was requested, so that a request waiting to
     * fill the queue can tell whether another fill has already happened.
     */
    private final AtomicLong fillCount = new AtomicLong();
    /**
     * Whether the last fill to complete added any tasks, so that a request that waited for it can tell whether
     * there is any point filling again itself.
     */
    private volatile boolean lastFillAddedTasks = true;
    private final AtomicBoolean needToFillQueue = new AtomicBoolean();
    private volatile int lastQueueSizeForStats = -1;

    /**
     * Make sure the task store isn't allowed to be filled until this node has
     * run startup() and has not run shutdown().
     */
    private volatile boolean allowTaskQueueFill = false;

    private final Map<String, Instant> lastNodeContactTime = new ConcurrentHashMap<>();
    private Instant lastDisownedTasks = Instant.now();

    @Inject
    ProcessorTaskQueueManagerImpl(final ProcessorTaskDao processorTaskDao,
                                  final ExecutorProvider executorProvider,
                                  final TaskContextFactory taskContextFactory,
                                  final NodeInfo nodeInfo,
                                  final Provider<ProcessorConfig> processorConfigProvider,
                                  final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider,
                                  final MetaService metaService,
                                  final SecurityContext securityContext,
                                  final TargetNodeSetFactory targetNodeSetFactory,
                                  final PrioritisedFilters prioritisedFilters,
                                  final ProcessorProfileCache processorProfileCache,
                                  final FilterFetchBackoff filterFetchBackoff) {
        this.taskContextFactory = taskContextFactory;
        this.nodeInfo = nodeInfo;
        this.processorTaskDao = processorTaskDao;
        this.processorConfigProvider = processorConfigProvider;
        this.internalStatisticsReceiverProvider = internalStatisticsReceiverProvider;
        this.metaService = metaService;
        this.securityContext = securityContext;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.prioritisedFilters = prioritisedFilters;
        this.processorProfileCache = processorProfileCache;
        this.filterFetchBackoff = filterFetchBackoff;

        executor = executorProvider.get(THREAD_POOL);
    }

    @Override
    public synchronized void startup() {
        // It shouldn't be possible to create tasks during startup.
        try {
            // Anything that we owned release
            // Lock the cluster so that only this node is able to release owned tasks at this time.
            final String nodeName = nodeInfo.getThisNodeName();
            LOGGER.info(() -> "Locking cluster to release owned tasks for node " + nodeName);
            final DurationTimer durationTimer = DurationTimer.start();
            final long count = processorTaskDao.releaseOwnedTasks(nodeName);
            if (count > 0) {
                LOGGER.info(() -> "Released " +
                                  count +
                                  " previously owned tasks in " +
                                  durationTimer.get());
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Allow task queueing.
        allowTaskQueueFill = true;
    }

    @Override
    public synchronized void shutdown() {
        // It shouldn't be possible to queue tasks during shutdown.
        try {
            allowTaskQueueFill = false;
            clearTaskStore();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Return the next task to perform. Called by worker threads. Also assigns
     * the task to the node asking for the job
     */
    @Override
    public ProcessorTaskList assignTasks(final TaskId sourceTaskId,
                                         final String nodeName,
                                         final int count) {
        final Supplier<ProcessorTaskList> runnable = taskContextFactory.contextResult(
                "Assign Tasks",
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> {
                    try {
                        taskContext.getTaskId().setParentId(sourceTaskId);
                        return assignTasks(nodeName, count, taskContext);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                    return null;
                });
        return runnable.get();
    }


    private ProcessorTaskList assignTasks(final String nodeName,
                                          final int count,
                                          final TaskContext taskContext) {
        LOGGER.debug("assignTasks() called for node {}, count {}", nodeName, count);
        if (!processorConfigProvider.get().isAssignTasks()) {
            LOGGER.debug("assignTasks is disabled");
            return new ProcessorTaskList(nodeName, Collections.emptyList());
        }
        if (count <= 0) {
            LOGGER.warn("assignTasks called for {} tasks", count);
            return new ProcessorTaskList(nodeName, Collections.emptyList());
        }
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(),
                    "Only the processing user is allowed to assign tasks");
        }

        final List<ProcessorTask> assignedStreamTasks = new ArrayList<>();
        final AtomicInteger attempt = new AtomicInteger();
        boolean addedNothingToQueue = false;
        while (attempt.getAndIncrement() < MAX_ASSIGNMENT_ATTEMPTS) {
            try {
                info(taskContext, () -> "Attempting task assignment " +
                                        "(attempt=" +
                                        attempt.get() +
                                        ")");
                // Get local reference to list in case it is swapped out.
                final List<ProcessorFilter> filters = prioritisedFilters.get();
                // Try and get a bunch of tasks from the queue to assign to the requesting node.
                info(taskContext, () ->
                        "Attempting task assignment for " +
                        filters.size() +
                        " filters " +
                        "(attempt=" +
                        attempt.get() +
                        ")");
                final AtomicInteger filterCount = new AtomicInteger();
                for (final ProcessorFilter filter : filters) {
                    info(taskContext, () ->
                            "Attempting task assignment for " +
                            filterCount.incrementAndGet() +
                            "/" +
                            filters.size() +
                            " filters " +
                            " (filter=" +
                            filter +
                            ", attempt=" +
                            attempt.get() +
                            ")");

                    if (assignedStreamTasks.size() >= count) {
                        break;
                    }

                    // Get the queue for this filter.
                    final ProcessorTaskQueue queue = queueMap.get(filter);
                    if (queue != null) {
                        int filterTasksAssigned = 0;
                        int maxFilterTasks = count - assignedStreamTasks.size();

                        // A filter with a profile is governed by that profile alone, so we never fall back to
                        // the non profile limits below, even if we fail to resolve the profile.
                        if (filter.getProfileName() != null) {
                            try {
                                final ProfileResult profileResult = processorProfileCache
                                        .getProfile(nodeName, filter.getProfileName());
                                Objects.requireNonNull(profileResult, "No processing profile found (filter=" +
                                                                      filter +
                                                                      ", profileName=" +
                                                                      filter.getProfileName() +
                                                                      ")");

                                // If either max node or max cluster tasks is 0 then add 0 tasks.
                                if (profileResult.maxNodeThreads() == 0 ||
                                    profileResult.maxClusterThreads() == 0) {
                                    maxFilterTasks = 0;
                                }

                                // If either the node or cluster threads are constrained then figure out the
                                // remaining max filter tasks. Both counts come from a single query so that
                                // they are a consistent view of the same moment.
                                if (maxFilterTasks > 0
                                    && (profileResult.maxNodeThreads() < Integer.MAX_VALUE
                                        || profileResult.maxClusterThreads() < Integer.MAX_VALUE)) {
                                    final FilterTaskCounts counts = processorTaskDao.countTasksForFilter(
                                            filter.getId(),
                                            nodeName,
                                            TaskStatus.PROCESSING);

                                    if (profileResult.maxNodeThreads() < Integer.MAX_VALUE) {
                                        final int remaining = Math.max(0,
                                                profileResult.maxNodeThreads() - counts.nodeCount());
                                        maxFilterTasks = Math.min(maxFilterTasks, remaining);
                                    }
                                    if (profileResult.maxClusterThreads() < Integer.MAX_VALUE) {
                                        final int remaining = Math.max(0,
                                                profileResult.maxClusterThreads() - counts.clusterCount());
                                        maxFilterTasks = Math.min(maxFilterTasks, remaining);
                                    }
                                }

                            } catch (final RuntimeException e) {
                                // We don't know what limits the profile would have imposed so assign no tasks
                                // for this filter, but carry on assigning tasks for all the other filters.
                                LOGGER.error(() -> "Error getting processing profile for filter (filter=" +
                                                   filter +
                                                   ", profileName=" +
                                                   filter.getProfileName() +
                                                   "), assigning no tasks for filter", e);
                                maxFilterTasks = 0;
                            }

                        } else {
                            // Maximum number of tasks to assign for this filter. If the filter task limit is
                            // unbounded, assign as many tasks up to the specified `count`. Otherwise, only assign
                            // tasks up to the filter's configured limit.
                            if (filter.isProcessingTaskCountBounded()) {
                                final int maxFilterTasksToCreate = filter.getMaxProcessingTasks() -
                                                                   processorTaskDao.countTasksForFilter(filter.getId(),
                                                                           TaskStatus.PROCESSING);
                                maxFilterTasks = Math.min(
                                        maxFilterTasks,
                                        Math.max(0, maxFilterTasksToCreate));
                            }
                        }

                        if (maxFilterTasks > 0) {
                            // Add as many tasks as we can for this filter.
                            ProcessorTask streamTask = queue.poll();
                            while (streamTask != null) {
                                assignedStreamTasks.add(streamTask);
                                filterTasksAssigned++;

                                if (assignedStreamTasks.size() < count && filterTasksAssigned < maxFilterTasks) {
                                    streamTask = queue.poll();
                                } else {
                                    streamTask = null;
                                }
                            }
                        }
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            // Output some trace logging, so we can see where tasks go.
            taskStatusTraceLog.assignTasks(ProcessorTaskQueueManagerImpl.class, assignedStreamTasks, nodeName);

            if (LOGGER.isDebugEnabled()) {
                info(taskContext, () -> "Assigning " +
                                        assignedStreamTasks.size() +
                                        " tasks (" +
                                        count +
                                        " requested) to node " +
                                        nodeName);
                LOGGER.debug("Assigning " + assignedStreamTasks.size()
                             + " tasks (" + count + " requested) to node " + nodeName);
            }

            // If we don't get any tasks then force synchronous queueing of tasks.
            if (allowTaskQueueFill) {
                if (assignedStreamTasks.isEmpty()) {
                    if (addedNothingToQueue) {
                        // We filled the queue ourselves, found nothing to add, and still can't assign
                        // anything, so trying again can't help.
                        break;
                    }

                    info(taskContext, () -> "Assigned " +
                                            assignedStreamTasks.size() +
                                            " tasks, filling queue synchronously");
                    // Only want to see an empty progress report on the first attempt.
                    // If another request filled the queue while we waited then we go round again to assign
                    // the tasks it queued, and if the queue is empty again we will fill it ourselves.
                    addedNothingToQueue =
                            FillOutcome.ADDED_NOTHING == fillTaskQueueSync(attempt.get() <= 1);
                } else {
                    // Kick off a queue fill.
                    info(taskContext, () -> "Assigned " +
                                            assignedStreamTasks.size() +
                                            " tasks, filling queue asynchronously");
                    fillTaskQueueAsync();
                    attempt.set(MAX_ASSIGNMENT_ATTEMPTS);
                }
            } else {
                attempt.set(MAX_ASSIGNMENT_ATTEMPTS);
            }
        }

        return new ProcessorTaskList(nodeName, assignedStreamTasks);
    }

    /**
     * Fill the task queue, waiting for any fill that is already in progress rather than performing a redundant
     * one. Filling is expensive and only one request needs to do it, but the others must wait for it rather
     * than give up, as a node that is told there are no tasks won't ask again for some time.
     */
    private FillOutcome fillTaskQueueSync(final boolean isEmptyReportRequired) {
        if (allowTaskQueueFill) {
            // Remember how many fills have completed so we can tell whether another request filled the queue
            // while we were waiting to fill it ourselves.
            final long fillCountBeforeWaiting = fillCount.get();

            synchronized (this) {
                if (fillCount.get() != fillCountBeforeWaiting) {
                    LOGGER.debug("fillTaskQueueSync() - Queue was filled by another request while we waited");
                    // If that fill found nothing to add then we won't either, so report it as our own empty
                    // fill rather than making every waiting request repeat it.
                    return lastFillAddedTasks
                            ? FillOutcome.FILLED_BY_ANOTHER
                            : FillOutcome.ADDED_NOTHING;
                }

                try {
                    LOGGER.debug("fillTaskQueueSync() - Executing fillTaskQueue, isEmptyReportRequired: {}",
                            isEmptyReportRequired);
                    final int added = securityContext.asProcessingUserResult(() ->
                            taskContextFactory.contextResult(
                                    "Fill task queue",
                                    taskContext -> queueNewTasks(taskContext, isEmptyReportRequired)).get());
                    return added > 0
                            ? FillOutcome.ADDED_TASKS
                            : FillOutcome.ADDED_NOTHING;

                } catch (final RuntimeException e) {
                    // The fill will have been recorded as one that added tasks, so we and any waiting requests
                    // will try to assign again rather than giving up, as the failure may well be transient.
                    LOGGER.error(e::getMessage, e);
                    return FillOutcome.FILLED_BY_ANOTHER;
                }
            }
        }
        return FillOutcome.ADDED_NOTHING;
    }

    private void fillTaskQueueAsync() {
        if (allowTaskQueueFill) {
            if (fillingQueue.compareAndSet(false, true)) {
                try {
                    needToFillQueue.set(false);

                    LOGGER.debug("fillTaskQueueAsync() - Executing fillTaskQueue");
                    securityContext.asProcessingUser(() ->
                            CompletableFuture
                                    .supplyAsync(taskContextFactory.contextResult(
                                                    "Fill task queue",
                                                    this::queueNewTasks),
                                            executor)
                                    .whenComplete((result, error) -> {
                                        try {
                                            if (error != null) {
                                                LOGGER.error("Error filling task queue:" + error.getMessage(), error);
                                            }

                                            if (allowTaskQueueFill && (error != null || result == 0)) {
                                                ThreadUtil.sleep(processorConfigProvider.get()
                                                        .getWaitToQueueTasksDuration().toMillis());
                                            }
                                        } finally {
                                            fillingQueue.set(false);
                                        }

                                        // See if we are required to fill again.
                                        if (needToFillQueue.get()) {
                                            fillTaskQueueAsync();
                                        }
                                    }));
                } catch (final RuntimeException e) {
                    fillingQueue.set(false);
                    LOGGER.error(e::getMessage, e);
                }
            } else {
                needToFillQueue.set(true);
            }
        }
    }

    @Override
    public Boolean abandonTasks(final ProcessorTaskList processorTaskList) {
        LOGGER.debug(() -> LogUtil.message("abandonTasks() called for {} tasks",
                Optional.ofNullable(processorTaskList)
                        .map(ProcessorTaskList::getList)
                        .map(List::size)
                        .orElse(0)));

        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(),
                    "Only the processing user is allowed to abandon tasks");
        }

        // Output some trace logging, so we can see where tasks go.
        taskStatusTraceLog.abandonTasks(ProcessorTaskQueueManagerImpl.class,
                processorTaskList.getList(),
                processorTaskList.getNodeName());

        if (!processorTaskList.getList().isEmpty()) {
            try {
                LOGGER.warn("abandon() - {}", processorTaskList);
                final Set<Long> idSet = processorTaskList
                        .getList()
                        .stream()
                        .map(ProcessorTask::getId)
                        .collect(Collectors.toSet());
                processorTaskDao.releaseTasks(idSet, TaskStatus.PROCESSING);

            } catch (final RuntimeException e) {
                LOGGER.error("abandon() - {}", processorTaskList, e);
            }
        }

        return true;
    }

    private long releaseAll() {
        long total = 0;
        for (final Entry<ProcessorFilter, ProcessorTaskQueue> entry : queueMap.entrySet()) {
            final ProcessorFilter filter = entry.getKey();
            total += releaseQueuedFilterTasks(filter);
        }
        return total;
    }

    private long releaseQueuedFilterTasks(final ProcessorFilter filter) {
        if (filter != null) {
            return LOGGER.logDurationIfDebugEnabled(() -> {
                long totalReleased = 0;
                final Set<Long> taskIdSet = new HashSet<>();
                final ProcessorTaskQueue queue = queueMap.remove(filter);
                if (queue != null) {
                    ProcessorTask processorTask = queue.poll();
                    while (processorTask != null) {
                        taskIdSet.add(processorTask.getId());
                        if (taskIdSet.size() >= BATCH_SIZE) {
                            releaseQueuedTask(taskIdSet);
                            totalReleased += taskIdSet.size();
                            taskIdSet.clear();
                        }
                        processorTask = queue.poll();
                    }
                }
                releaseQueuedTask(taskIdSet);
                totalReleased += taskIdSet.size();
                return totalReleased;
            }, () -> "Released tasks for filter " + filter.getFilterInfo());
        }
        return 0;
    }

    private void releaseQueuedTask(final Set<Long> taskIdSet) {
        if (!taskIdSet.isEmpty()) {
            try {
                processorTaskDao.releaseTasks(taskIdSet, TaskStatus.QUEUED);
            } catch (final RuntimeException e) {
                LOGGER.error("release() - {}", taskIdSet, e);
            }
        }
    }

    private synchronized void clearTaskStore() {
        for (final Entry<ProcessorFilter, ProcessorTaskQueue> entry : queueMap.entrySet()) {
            final ProcessorFilter filter = entry.getKey();
            if (filter != null) {
                final ProcessorTaskQueue queue = queueMap.remove(filter);
                if (queue != null) {
                    ProcessorTask streamTask = queue.poll();
                    while (streamTask != null) {
                        streamTask = queue.poll();
                    }
                }
            }
        }
    }

    @Override
    public int getTaskQueueSize() {
        int size = 0;
        for (final ProcessorTaskQueue queue : queueMap.values()) {
            if (queue != null) {
                size += queue.size();
            }
        }

        return size;
    }

    public void disownDeadTasks() {
        LOGGER.trace(() -> "disownDeadTasks()");
        try {
            final String node = nodeInfo.getThisNodeName();
            final String masterNode = targetNodeSetFactory.getMasterNode();
            if (node != null && node.equals(masterNode)) {
                // If this is the master node then see if there are any nodes that we haven't had contact with
                // for some time.

                // If we haven't had contact with a node for 10 minutes then forcibly release the tasks owned
                // by that node.
                final Instant now = Instant.now();
                final Set<String> activeNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();
                activeNodes.forEach(activeNode -> lastNodeContactTime.put(activeNode, now));
                final Instant disownTaskAge = now.minus(processorConfigProvider.get().getDisownDeadTasksAfter());
                if (lastDisownedTasks.isBefore(disownTaskAge)) {
                    lastDisownedTasks = now;

                    // Remove nodes we haven't had contact with for 10 minutes.
                    lastNodeContactTime.forEach((k, v) -> {
                        if (v.isBefore(disownTaskAge)) {
                            lastNodeContactTime.remove(k);
                        }
                    });

                    // Retain all tasks that have had their status updated in the last 10 minutes or belong to
                    // nodes we know have been active in the last 10 minutes.
                    final DurationTimer durationTimer = DurationTimer.start();
                    final long count = processorTaskDao.retainOwnedTasks(lastNodeContactTime.keySet(), disownTaskAge);
                    if (count > 0) {
                        LOGGER.warn(() ->
                                "Removed task ownership for dead nodes (count = " +
                                count +
                                ") in " +
                                durationTimer.get());
                    }
                }
            }
        } catch (final RuntimeException | NodeNotFoundException | NullClusterStateException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }

    public synchronized void releaseOldQueuedTasks() {
        LOGGER.trace(() -> "releaseOldQueuedTasks()");
        if (!queueMap.isEmpty()) {
            try {
                final String node = nodeInfo.getThisNodeName();
                final String masterNode = targetNodeSetFactory.getMasterNode();
                if (node != null && !node.equals(masterNode)) {
                    // This is no longer the master node so release all tasks.
                    final DurationTimer durationTimer = DurationTimer.start();
                    final long count = releaseAll();
                    if (count > 0) {
                        LOGGER.info(() ->
                                "Released All Queued Tasks (count = " +
                                count +
                                ") in " +
                                durationTimer.get());
                    }
                }
            } catch (final RuntimeException | NodeNotFoundException | NullClusterStateException e) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
    }

    public void exec() {
        queueNewTasks(taskContextFactory.current());
    }

    private int queueNewTasks(final TaskContext taskContext) {
        return queueNewTasks(taskContext, true);
    }

    private synchronized int queueNewTasks(final TaskContext taskContext,
                                           final boolean isEmptyReportRequired) {
        // Assume a fill that failed part way through added tasks so that requests waiting to fill the queue
        // try to assign again rather than giving up, as the failure may well be transient.
        boolean addedTasks = true;
        try {
            final int totalAdded = doQueueNewTasks(taskContext, isEmptyReportRequired);
            addedTasks = totalAdded > 0;
            return totalAdded;
        } finally {
            lastFillAddedTasks = addedTasks;
            // Record that the queue has been filled, however the fill was requested, so that a request waiting
            // to fill the queue synchronously can tell that it no longer needs to.
            fillCount.incrementAndGet();
        }
    }

    /**
     * Only to be called via {@link #queueNewTasks(TaskContext, boolean)}, which synchronizes and records that
     * the fill happened.
     */
    private int doQueueNewTasks(final TaskContext taskContext,
                                final boolean isEmptyReportRequired) {
        LOGGER.trace("queueNewTasks() - Starting");
        int totalAdded = 0;

        // We need to make sure that only 1 thread at a time is allowed to
        // create tasks. This should always be the case in production but some
        // tests will call this directly while scheduled execution could also be
        // running.
        info(taskContext, () -> "Starting");

        // Update the stream task store.
        final List<ProcessorFilter> filters = prioritisedFilters.get();
        final ProcessorConfig processorConfig = processorConfigProvider.get();
        // Each processing profile gets its own queueing budget, as do the filters that have no
        // profile, so that a busy profile can't fill the queue on every pass and leave another
        // profile's nodes asking for work that never gets queued.
        final QueueProcessTasksState queueProcessTasksState = new QueueProcessTasksState(
                filters, getTaskQueueSize(), processorConfig.getQueueSize());
        final ProgressMonitor progressMonitor = new ProgressMonitor(filters.size());

        final String nodeName = nodeInfo.getThisNodeName();
        if (nodeName == null) {
            throw new NullPointerException("Node is null");
        }

        // Don't remember filters we are no longer considering, e.g. disabled or deleted ones.
        filterFetchBackoff.retainAll(filters);

        // Now fill the stream task store with tasks for each filter.
        // The aim is to create N tasks in total where N is processorConfig.getQueueSize
        // Also need to ensure each filter queue has no more than N in it.
        try {
            if (processorConfig.isFillTaskQueue()) {
                final StroomDuration skipEmptyFilterFetchDuration =
                        processorConfig.getSkipEmptyFilterFetchDuration();

                for (final ProcessorFilter filter : filters) {
                    // If we have enough tasks queued for this filter's profile then move on to the
                    // next filter rather than stopping altogether, as another profile's nodes can't
                    // process anything queued for this one. Only stop once every profile has enough
                    // queued, so we don't needlessly consider the rest of the filters.
                    final ProfileQueueState profileQueueState = queueProcessTasksState.getState(filter);
                    if (!profileQueueState.keepAddingTasks()) {
                        progressMonitor.logSkippedFilter(filter, SkipReason.QUEUE_FULL_FOR_PROFILE);
                        if (queueProcessTasksState.isEveryQueueFull()) {
                            break;
                        }

                    } else if (!filterFetchBackoff.isFetchDue(filter, skipEmptyFilterFetchDuration)) {
                        // We looked for tasks for this filter recently and there were none. A
                        // profile with nothing to do never has enough tasks queued, so without this
                        // every fill would query for every one of its filters.
                        progressMonitor.logSkippedFilter(filter, SkipReason.NO_TASKS_ON_LAST_FETCH);

                    } else {
                        final ProcessorTaskQueue queue = queueMap.computeIfAbsent(
                                filter,
                                k -> new ProcessorTaskQueue());
                        totalAdded += queueTasksForFilter(
                                taskContext,
                                nodeName,
                                filter,
                                progressMonitor,
                                queue,
                                profileQueueState,
                                skipEmptyFilterFetchDuration);
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Release items from the queue that no longer have an enabled filter or whose profile no longer allows
        // any tasks to be processed. This considers every queue, not just the filters we tried to queue tasks
        // for, as the loop above stops once enough tasks are queued and so may never reach a filter whose
        // profile has closed while busier filters are using up the queue budget.
        info(taskContext, () -> "Releasing tasks for disabled filters and inactive profiles");
        final Set<ProcessorFilter> enabledFilterSet = new HashSet<>(filters);
        for (final Entry<ProcessorFilter, ProcessorTaskQueue> entry : queueMap.entrySet()) {
            final ProcessorFilter filter = entry.getKey();
            final ProcessorTaskQueue queue = entry.getValue();
            if (!enabledFilterSet.contains(filter)) {
                final int initialQueueSize = queue.size();
                final DurationTimer durationTimer = DurationTimer.start();
                final long count = releaseQueuedFilterTasks(filter);
                final FilterProgressMonitor filterProgressMonitor =
                        progressMonitor.logFilter(filter, initialQueueSize);
                filterProgressMonitor.logPhase(Phase.RELEASE_TASKS_FOR_DISABLED_FILTERS, durationTimer, count);
                filterProgressMonitor.complete();
            } else if (filter.getProfileName() != null && queue.hasItems()) {
                releaseTasksIfProfileInactive(filter, queue, progressMonitor);
            }
        }

        info(taskContext, () -> "Finished");

        // No point spamming the logs when this method is called MAX_ASSIGNMENT_ATTEMPTS in succession
        if (queueProcessTasksState.getTotalQueuedCount() > 0
            || isEmptyReportRequired
            || LOGGER.isDebugEnabled()) {
            progressMonitor.report("ADD TASKS TO QUEUE", queueProcessTasksState);
        }

        LOGGER.trace("queueNewTasks() - Finished");
        return totalAdded;
    }

    /**
     * Release the queued tasks for a filter whose profile no longer allows any tasks to be processed, so that
     * they don't remain owned by this node and unable to be processed until the profile allows them again.
     */
    private void releaseTasksIfProfileInactive(final ProcessorFilter filter,
                                               final ProcessorTaskQueue queue,
                                               final ProgressMonitor progressMonitor) {
        try {
            if (getMaxConcurrentTasks(filter) <= 0) {
                final int initialQueueSize = queue.size();
                final DurationTimer durationTimer = DurationTimer.start();
                final long released = releaseQueuedFilterTasks(filter);
                final FilterProgressMonitor filterProgressMonitor =
                        progressMonitor.logFilter(filter, initialQueueSize);
                filterProgressMonitor.logPhase(Phase.RELEASE_TASKS_FOR_INACTIVE_PROFILES, durationTimer, released);
                filterProgressMonitor.complete();
                LOGGER.debug("releaseTasksIfProfileInactive() - No tasks can currently be assigned for {}, " +
                             "released {} queued tasks", filter.getFilterInfo(), released);
            }
        } catch (final RuntimeException e) {
            // We can't resolve the profile so we don't know whether the tasks can be processed, which is not
            // the same as knowing that they can't, so leave the queue alone. Assignment reports this error.
            LOGGER.debug(() -> "Error getting processing profile for filter (filter=" +
                               filter +
                               ", profileName=" +
                               filter.getProfileName() +
                               "), leaving queued tasks alone", e);
        }
    }

    /**
     * Get the maximum number of tasks for the filter that could currently be assigned across the whole cluster.
     * Task assignment applies processing profiles but queueing does not, so without this we would fill the queue
     * with tasks that no node is allowed to process, using up the queue and stopping us from queueing tasks for
     * other filters.
     * <p>
     * All of the ways that concurrent processing can be limited are treated the same, i.e. the filter's own
     * maximum processing tasks, a profile's maximum cluster threads, and a profile's maximum threads per node
     * multiplied by the number of nodes it allows.
     * </p>
     *
     * @return The maximum number of tasks that can currently be assigned for the filter, 0 if no enabled node
     * is currently allowed to process tasks for it, or {@link Integer#MAX_VALUE} if there is no limit.
     * @throws RuntimeException If the filter's profile can't be resolved, so that the caller can tell the
     *                          difference between a profile that allows no tasks and one we know nothing about.
     */
    private int getMaxConcurrentTasks(final ProcessorFilter filter) {
        if (filter.getProfileName() == null) {
            return filter.isProcessingTaskCountBounded()
                    ? filter.getMaxProcessingTasks()
                    : Integer.MAX_VALUE;
        }

        final Set<String> enabledNodes;
        try {
            // Deliberately all enabled nodes rather than just the currently active ones. A node that drops out
            // briefly would otherwise make us release this filter's queued tasks and queue them all again when
            // it returns.
            enabledNodes = targetNodeSetFactory.getEnabledTargetNodeSet();
        } catch (final RuntimeException | NodeNotFoundException | NullClusterStateException e) {
            // We can't tell which nodes are enabled so assume tasks can be queued rather than queueing nothing.
            LOGGER.debug(() -> "Unable to get enabled nodes, assuming tasks can be queued for " +
                               filter.getFilterInfo(), e);
            return Integer.MAX_VALUE;
        }

        try {
            // A profile can stop all nodes processing tasks for the filter, e.g. when the current time is
            // outside its processing periods or its node group is disabled, so see if any enabled node is
            // currently allowed to process tasks for it.
            int maxConcurrentTasks = 0;
            long totalMaxNodeThreads = 0;
            for (final String node : enabledNodes) {
                final ProfileResult profileResult = processorProfileCache
                        .getProfile(node, filter.getProfileName());
                if (profileResult.maxNodeThreads() > 0 && profileResult.maxClusterThreads() > 0) {
                    maxConcurrentTasks = Math.max(maxConcurrentTasks, profileResult.maxClusterThreads());
                    totalMaxNodeThreads += profileResult.maxNodeThreads();
                }
            }

            // A limit on the threads each node may use is just as much a limit on what the cluster can process
            // at once as an explicit cluster thread limit, so treat the two the same way.
            return (int) Math.min(maxConcurrentTasks, Math.min(totalMaxNodeThreads, Integer.MAX_VALUE));

        } catch (final RuntimeException e) {
            // We can't tell whether tasks can be processed, which is not the same as knowing that they can't,
            // so let this propagate. The caller then reports it and leaves the queue alone rather than
            // releasing tasks because of what is most likely a configuration error.
            throw new RuntimeException("Error getting processing profile for filter (filter=" +
                                       filter +
                                       ", profileName=" +
                                       filter.getProfileName() +
                                       ")", e);
        }
    }

    private int queueTasksForFilter(final TaskContext taskContext,
                                    final String nodeName,
                                    final ProcessorFilter filter,
                                    final ProgressMonitor progressMonitor,
                                    final ProcessorTaskQueue queue,
                                    final ProfileQueueState profileQueueState,
                                    final StroomDuration skipEmptyFilterFetchDuration) {
        try {
            LOGGER.debug("queueTasksForFilter() - processorFilter {}", filter.getFilterInfo());

            // Queue tasks for this filter.
            final int initialQueueSize = queue.size();
            final FilterProgressMonitor filterProgressMonitor = progressMonitor.logFilter(filter,
                    initialQueueSize);

            // Only try and create tasks if the processor is enabled.
            if (filter.isEnabled() && filter.getProcessor().isEnabled()) {
                // Don't queue tasks that no node is currently allowed to process as they would just use up the
                // queue and stop us queueing tasks for other filters.
                final int maxConcurrentTasks = getMaxConcurrentTasks(filter);
                if (maxConcurrentTasks <= 0) {
                    // Release any tasks that we queued while the profile was still allowing them, otherwise
                    // they would remain owned by this node and unable to be processed until it allows them
                    // again, which could be a long time for a profile that only processes overnight etc.
                    final DurationTimer durationTimer = DurationTimer.start();
                    final long released = releaseQueuedFilterTasks(filter);
                    filterProgressMonitor.logPhase(
                            Phase.RELEASE_TASKS_FOR_INACTIVE_PROFILES, durationTimer, released);
                    filterProgressMonitor.complete();
                    LOGGER.debug("queueTasksForFilter() - No tasks can currently be assigned for {}, " +
                                 "released {} queued tasks", filter.getFilterInfo(), released);
                    return 0;
                }

                info(taskContext, filter::getFilterInfo);

                // If there are any tasks for this filter that were previously created but aren't queued and
                // their associated stream is unlocked then add them to the queue here.
                final DurationTimer durationTimer = DurationTimer.start();
                final int count = queueCreatedTasks(
                        taskContext,
                        nodeName,
                        filter,
                        queue,
                        profileQueueState,
                        filterProgressMonitor,
                        maxConcurrentTasks,
                        skipEmptyFilterFetchDuration);
                filterProgressMonitor.logPhase(Phase.QUEUE_CREATED_TASKS, durationTimer, count);
                return count;
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return 0;
    }

    private int queueCreatedTasks(final TaskContext taskContext,
                                  final String nodeName,
                                  final ProcessorFilter filter,
                                  final ProcessorTaskQueue queue,
                                  final ProfileQueueState profileQueueState,
                                  final FilterProgressMonitor filterProgressMonitor,
                                  final int maxConcurrentTasks,
                                  final StroomDuration skipEmptyFilterFetchDuration) {
        // Queue tasks for this filter.
        final int initialQueueSize = queue.size();
        profileQueueState.addCurrentlyQueuedTasks(initialQueueSize);

        int totalTasks = 0;
        int totalAddedTasks = 0;
        int tasksToAdd = profileQueueState.getRequiredTaskCount() - initialQueueSize;
        final int batchSize = Math.max(BATCH_SIZE, tasksToAdd);
        long lastTaskId = 0;
        // Only what we learn from actually looking tells us whether to look again soon. A filter
        // whose queue is already full hasn't been looked at, so it must not be backed off, or it
        // wouldn't be refilled promptly once its queue drains.
        boolean fetched = false;
        // Read before we look so that we can tell whether task creation has produced anything for
        // this filter while we were looking, in which case finding nothing means nothing.
        final long creationVersion = filterFetchBackoff.getCreationVersion(filter);

        try {
            // Keep adding tasks until we have reached the requested number.
            while (tasksToAdd > 0) {

                // Look for any existing tasks we have created.
                fetched = true;
                DurationTimer durationTimer = DurationTimer.start();
                final List<ExistingCreatedTask> existingCreatedTasks = processorTaskDao
                        .findExistingCreatedTasks(lastTaskId, filter.getId(), batchSize);
                filterProgressMonitor.logPhase(Phase.QUEUE_CREATED_TASKS_FETCH_TASKS,
                        durationTimer,
                        existingCreatedTasks.size());

                // If we got fewer tasks returned than we asked for then we won't need to ask for more.
                if (existingCreatedTasks.size() < batchSize) {
                    tasksToAdd = 0;
                }

                // If we have some existing tasks then queue them unless they belong to locked meta.
                if (!existingCreatedTasks.isEmpty()) {
                    try {
                        // Increment the total number of unowned tasks.
                        totalTasks += existingCreatedTasks.size();

                        final List<Long> metaIdList = new ArrayList<>(existingCreatedTasks.size());
                        for (final ExistingCreatedTask task : existingCreatedTasks) {
                            metaIdList.add(task.getMetaId());
                            // Ensure we don't see this task again in the next attempt.
                            lastTaskId = Math.max(lastTaskId, task.getTaskId());
                        }

                        // Find all locked meta entries for the selected processor tasks.
                        durationTimer = DurationTimer.start();
                        final Set<Long> lockedMetaIdSet = metaService.findLockedMeta(metaIdList);
                        filterProgressMonitor.logPhase(Phase.QUEUE_CREATED_TASKS_FETCH_META,
                                durationTimer,
                                lockedMetaIdSet.size());

                        // Filter out tasks associated with meta that is still locked.
                        final Set<Long> processorTaskIdSet = existingCreatedTasks
                                .stream()
                                .filter(processorTask -> !lockedMetaIdSet.contains(processorTask.getMetaId()))
                                .map(ExistingCreatedTask::getTaskId)
                                .collect(Collectors.toSet());

                        durationTimer = DurationTimer.start();
                        final List<ProcessorTask> existingTasks = processorTaskDao.queueTasks(
                                processorTaskIdSet,
                                nodeName);
                        filterProgressMonitor.logPhase(Phase.QUEUE_CREATED_TASKS_QUEUE_TASKS,
                                durationTimer,
                                existingTasks.size());

                        queue.addAll(existingTasks);
                        tasksToAdd -= existingTasks.size();
                        totalAddedTasks += existingTasks.size();

                        final int finalTotalAddedTasks = totalAddedTasks;
                        final int finalTotalTasks = totalTasks;
                        info(taskContext, () ->
                                LogUtil.message("Adding {}/{} non owned Tasks",
                                        finalTotalAddedTasks,
                                        finalTotalTasks));

                        if (Thread.currentThread().isInterrupted()) {
                            // Stop trying to add tasks.
                            tasksToAdd = 0;
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error("doCreateTasks() - Failed to grab non owned tasks", e);
                    }
                }
            }

            if (totalAddedTasks > 0) {
                filterProgressMonitor.add(totalAddedTasks);
                LOGGER.debug("doCreateTasks() - Added {} tasks that are no longer locked", totalAddedTasks);
            }

            if (fetched) {
                if (totalTasks == 0) {
                    // There is nothing created for this filter, so leave it alone for a while
                    // rather than asking again on every fill.
                    filterFetchBackoff.recordEmptyFetch(filter, skipEmptyFilterFetchDuration, creationVersion);
                } else {
                    filterFetchBackoff.recordFetchedTasks(filter);
                }
            }

        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        if (maxConcurrentTasks < Integer.MAX_VALUE) {
            // If the number of tasks that can be processed at once is limited, either by the filter or by its
            // processing profile, then limit the number we report as being added to the queue otherwise we
            // might stop adding other tasks early.
            profileQueueState
                    .addTotalQueuedTasks(Math.min(maxConcurrentTasks, initialQueueSize + totalAddedTasks));
        } else {
            profileQueueState.addTotalQueuedTasks(initialQueueSize + totalAddedTasks);
        }

        return totalAddedTasks;
    }

    private void info(final TaskContext taskContext,
                      final Supplier<String> messageSupplier) {
        LOGGER.debug(messageSupplier);
        taskContext.info(messageSupplier);
    }

    @Override
    public void writeQueueStatistics() {
        final TaskContext taskContext = taskContextFactory.current();
        info(taskContext, () -> "Writing processor task queue statistics");
        try {
            // Avoid writing loads of same value stats So write every min while
            // it changes Under little load the queue size will be 0
            final int queueSize = getTaskQueueSize();
            if (queueSize != lastQueueSizeForStats) {
                try {
                    final InternalStatisticsReceiver internalStatisticsReceiver =
                            internalStatisticsReceiverProvider.get();
                    if (internalStatisticsReceiver != null) {
                        // Value type event as the queue size is not additive
                        internalStatisticsReceiver.putEvent(InternalStatisticEvent.createValueStat(
                                InternalStatisticKey.STREAM_TASK_QUEUE_SIZE,
                                System.currentTimeMillis(),
                                null,
                                queueSize));
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                }
                lastQueueSizeForStats = queueSize;
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        // Iterate over the latest picture of prioritised filters to get a detailed view of queue sizes
        // by filter
        final List<Map<String, Object>> queueInfo = NullSafe.list(prioritisedFilters.get())
                .stream()
                .map(processorFilter ->
                        new SimpleEntry<>(processorFilter, queueMap.get(processorFilter)))
                .filter(entry -> NullSafe.test(entry.getValue(), ProcessorTaskQueue::hasItems))
                .map(entry -> {
                    final ProcessorFilter processorFilter = entry.getKey();
                    final ProcessorTaskQueue queue = entry.getValue();
                    final String pipelineName = Objects.requireNonNullElseGet(
                            processorFilter.getPipelineName(),
                            processorFilter::getPipelineUuid);

                    return Map.<String, Object>of(
                            "filterId", processorFilter.getId(),
                            "pipelineName", pipelineName,
                            "priority", processorFilter.getPriority(),
                            "queueSize", NullSafe.get(queue, ProcessorTaskQueue::size));
                })
                .collect(Collectors.toList());

        return SystemInfoResult.builder(this)
                .description("Processor task queue info")
                .addDetail("filterQueues", queueInfo)
                .addDetail("overallQueueSize", getTaskQueueSize())
                .build();
    }


    // --------------------------------------------------------------------------------


    /**
     * The outcome of trying to fill the task queue synchronously.
     */
    private enum FillOutcome {
        /**
         * We filled the queue and added tasks to it.
         */
        ADDED_TASKS,
        /**
         * We filled the queue but there were no tasks to add to it.
         */
        ADDED_NOTHING,
        /**
         * Another request filled the queue while we were waiting to, so we didn't fill it again.
         */
        FILLED_BY_ANOTHER
    }

}

