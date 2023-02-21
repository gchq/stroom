/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.processor.impl;

import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.meta.api.MetaService;
import stroom.node.api.NodeInfo;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.impl.ProgressMonitor.FilterProgressMonitor;
import stroom.processor.impl.ProgressMonitor.Phase;
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
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.NullSafe;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Keep a pool of stream tasks ready to go.
 */
@Singleton
class ProcessorTaskQueueManagerImpl implements ProcessorTaskQueueManager, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskQueueManagerImpl.class);

    private static final int BATCH_SIZE = 1000;
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Fill Task Store", 3);

    private final ProcessorFilterService processorFilterService;
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

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();

    /**
     * Our queue.
     */
    private final ConcurrentHashMap<ProcessorFilter, ProcessorTaskQueue> queueMap = new ConcurrentHashMap<>();
    private final AtomicBoolean fillingQueue = new AtomicBoolean();
    private final AtomicBoolean needToFillQueue = new AtomicBoolean();
    private volatile int lastQueueSizeForStats = -1;

    /**
     * Make sure the task store isn't allowed to be filled until this node has
     * run startup() and has not run shutdown().
     */
    private volatile boolean allowAsyncTaskCreation = false;

    private final Map<String, Instant> lastNodeContactTime = new ConcurrentHashMap<>();
    private Instant lastDisownedTasks = Instant.now();

    @Inject
    ProcessorTaskQueueManagerImpl(final ProcessorFilterService processorFilterService,
                                  final ProcessorTaskDao processorTaskDao,
                                  final ExecutorProvider executorProvider,
                                  final TaskContextFactory taskContextFactory,
                                  final NodeInfo nodeInfo,
                                  final Provider<ProcessorConfig> processorConfigProvider,
                                  final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider,
                                  final MetaService metaService,
                                  final SecurityContext securityContext,
                                  final TargetNodeSetFactory targetNodeSetFactory,
                                  final PrioritisedFilters prioritisedFilters) {
        this.processorFilterService = processorFilterService;
        this.taskContextFactory = taskContextFactory;
        this.nodeInfo = nodeInfo;
        this.processorTaskDao = processorTaskDao;
        this.processorConfigProvider = processorConfigProvider;
        this.internalStatisticsReceiverProvider = internalStatisticsReceiverProvider;
        this.metaService = metaService;
        this.securityContext = securityContext;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.prioritisedFilters = prioritisedFilters;

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

        // Allow task creation.
        allowAsyncTaskCreation = true;
    }

    @Override
    public synchronized void shutdown() {
        // It shouldn't be possible to create tasks during shutdown.
        try {
            allowAsyncTaskCreation = false;
            clearTaskStore();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void fillTaskQueueAsync() {
        if (allowAsyncTaskCreation) {
            if (fillingQueue.compareAndSet(false, true)) {
                try {
                    needToFillQueue.set(false);

                    LOGGER.debug("fillTaskQueueAsync() - Executing fillTaskQueue");
                    securityContext.asProcessingUser(() ->
                            CompletableFuture
                                    .supplyAsync(taskContextFactory.contextResult(
                                            "Fill task queue",
                                            this::queueNewTasks), executor)
                                    .whenComplete((result, error) -> {
                                        try {
                                            if (allowAsyncTaskCreation && result == 0) {
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

    /**
     * Return the next task to perform. Called by worker threads. Also assigns
     * the task to the node asking for the job
     */
    @Override
    public ProcessorTaskList assignTasks(final String nodeName, final int count) {
        LOGGER.debug("assignTasks() called for node {}, count {}", nodeName, count);

        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserId(),
                    "Only the processing user is allowed to assign tasks");
        }

        List<ProcessorTask> assignedStreamTasks = Collections.emptyList();
        try {
            if (processorConfigProvider.get().isAssignTasks() && count > 0) {
                // Get local reference to list in case it is swapped out.
                final List<ProcessorFilter> filters = prioritisedFilters.get();
                if (filters != null && filters.size() > 0) {
                    final List<ProcessorTask> toAssign = new ArrayList<>(count);

                    // Try and get a bunch of tasks from the queue to assign to the requesting node.
                    int index = 0;
                    while (toAssign.size() < count && index < filters.size()) {
                        final ProcessorFilter filter = filters.get(index);

                        // Get the queue for this filter.
                        final ProcessorTaskQueue queue = queueMap.get(filter);
                        if (queue != null) {
                            // Add as many tasks as we can for this filter.
                            ProcessorTask streamTask = queue.poll();
                            while (streamTask != null) {
                                toAssign.add(streamTask);
                                if (toAssign.size() < count) {
                                    streamTask = queue.poll();
                                } else {
                                    streamTask = null;
                                }
                            }
                        }
                        index++;
                    }

                    // Now bulk assign the tasks in one query.
                    if (toAssign.size() > 0) {
                        final Set<Long> idSet = toAssign
                                .stream()
                                .map(ProcessorTask::getId)
                                .collect(Collectors.toSet());
                        assignedStreamTasks = processorTaskDao.assignTasks(idSet, nodeName);
                    }
                }
            } else {
                LOGGER.debug("assignTasks is disabled");
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Output some trace logging, so we can see where tasks go.
        taskStatusTraceLog.assignTasks(ProcessorTaskQueueManagerImpl.class, assignedStreamTasks, nodeName);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Assigning " + assignedStreamTasks.size()
                    + " tasks (" + count + " requested) to node " + nodeName);
        }

        // Kick off a queue fill.
        fillTaskQueueAsync();

        return new ProcessorTaskList(nodeName, assignedStreamTasks);
    }

    @Override
    public Boolean abandonTasks(final ProcessorTaskList processorTaskList) {
        LOGGER.debug(() -> LogUtil.message("abandonTasks() called for {} tasks",
                Optional.ofNullable(processorTaskList)
                        .map(ProcessorTaskList::getList)
                        .map(List::size)
                        .orElse(0)));

        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserId(),
                    "Only the processing user is allowed to abandon tasks");
        }

        // Output some trace logging, so we can see where tasks go.
        taskStatusTraceLog.abandonTasks(ProcessorTaskQueueManagerImpl.class,
                processorTaskList.getList(),
                processorTaskList.getNodeName());

        if (processorTaskList.getList().size() > 0) {
            try {
                LOGGER.warn("abandon() - {}", processorTaskList);
                final Set<Long> idSet = processorTaskList
                        .getList()
                        .stream()
                        .map(ProcessorTask::getId)
                        .collect(Collectors.toSet());
                processorTaskDao.releaseTasks(idSet, Set.of(TaskStatus.ASSIGNED, TaskStatus.PROCESSING));

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
                final Set<Long> taskIdSet = new HashSet<>();
                final ProcessorTaskQueue queue = queueMap.remove(filter);
                if (queue != null) {
                    ProcessorTask processorTask = queue.poll();
                    while (processorTask != null) {
                        taskIdSet.add(processorTask.getId());
                        if (taskIdSet.size() >= BATCH_SIZE) {
                            releaseQueuedTask(taskIdSet);
                            taskIdSet.clear();
                        }
                        processorTask = queue.poll();
                    }
                }
                releaseQueuedTask(taskIdSet);
                return taskIdSet.size();
            }, () -> "Released tasks for filter " + filter.getFilterInfo());
        }
        return 0;
    }

    private void releaseQueuedTask(final Set<Long> taskIdSet) {
        if (taskIdSet.size() > 0) {
            try {
                processorTaskDao.releaseTasks(taskIdSet, Set.of(TaskStatus.QUEUED));
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
        if (queueMap.size() > 0) {
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

    private synchronized int queueNewTasks(final TaskContext taskContext) {
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
        final QueueProcessTasksState queueProcessTasksState =
                new QueueProcessTasksState(getTaskQueueSize(), processorConfig.getQueueSize());
        final ProgressMonitor progressMonitor = new ProgressMonitor(filters.size());

        final String nodeName = nodeInfo.getThisNodeName();
        if (nodeName == null) {
            throw new NullPointerException("Node is null");
        }

        // Now fill the stream task store with tasks for each filter.
        // The aim is to create N tasks in total where N is processorConfig.getQueueSize
        // Also need to ensure each filter queue has no more than N in it.
        try {
            if (processorConfig.isFillTaskQueue()) {
                for (final ProcessorFilter filter : filters) {
                    final ProcessorTaskQueue queue = queueMap.computeIfAbsent(
                            filter,
                            k -> new ProcessorTaskQueue());

                    // If we have enough tasks queued then stop trying to add more to the queues.
                    if (!queueProcessTasksState.keepAddingTasks()) {
                        break;

                    } else {
                        totalAdded += queueTasksForFilter(
                                taskContext,
                                nodeName,
                                filter,
                                progressMonitor,
                                queue,
                                queueProcessTasksState);
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Release items from the queue that no longer have an enabled filter
        info(taskContext, () -> "Releasing tasks for disabled filters");
        final Set<ProcessorFilter> enabledFilterSet = new HashSet<>(filters);
        for (final Entry<ProcessorFilter, ProcessorTaskQueue> entry : queueMap.entrySet()) {
            final ProcessorFilter filter = entry.getKey();
            final ProcessorTaskQueue queue = entry.getValue();
            if (!enabledFilterSet.contains(filter)) {
                final DurationTimer durationTimer = DurationTimer.start();
                final long count = releaseQueuedFilterTasks(filter);
                final FilterProgressMonitor filterProgressMonitor = progressMonitor.logFilter(filter, queue.size());
                filterProgressMonitor.logPhase(Phase.RELEASE_TASKS_FOR_DISABLED_FILTERS, durationTimer, count);
                filterProgressMonitor.complete();
            }
        }

        info(taskContext, () -> "Finished");

        progressMonitor.report("QUEUED TASKS", queueProcessTasksState);

        LOGGER.trace("queueNewTasks() - Finished");
        return totalAdded;
    }

    private int queueTasksForFilter(final TaskContext taskContext,
                                    final String nodeName,
                                    final ProcessorFilter filter,
                                    final ProgressMonitor progressMonitor,
                                    final ProcessorTaskQueue queue,
                                    final QueueProcessTasksState queueProcessTasksState) {
        try {
            // Queue tasks for this filter.
            final int initialQueueSize = queue.size();
            queueProcessTasksState.addCurrentlyQueuedTasks(initialQueueSize);
            final FilterProgressMonitor filterProgressMonitor = progressMonitor.logFilter(filter, initialQueueSize);

            // Reload as it could have changed.
            final Optional<ProcessorFilter> optionalProcessorFilter = processorFilterService.fetch(filter.getId());

            // The filter might have been deleted since we found it.
            if (optionalProcessorFilter.isPresent()) {
                final ProcessorFilter loadedFilter = optionalProcessorFilter.get();
                LOGGER.debug("queueTasksForFilter() - processorFilter {}", loadedFilter.getFilterInfo());

                // Only try and create tasks if the processor is enabled.
                if (loadedFilter.isEnabled() && loadedFilter.getProcessor().isEnabled()) {
                    info(taskContext, loadedFilter::getFilterInfo);

                    // If there are any tasks for this filter that were previously created but aren't queued and
                    // their associated stream is unlocked then add them to the queue here.
                    final DurationTimer durationTimer = DurationTimer.start();
                    final int count = queueCreatedTasks(
                            taskContext,
                            nodeName,
                            loadedFilter,
                            queue,
                            queueProcessTasksState,
                            filterProgressMonitor);
                    filterProgressMonitor.logPhase(Phase.QUEUE_CREATED_TASKS, durationTimer, count);
                    return count;
                }
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
                                  final QueueProcessTasksState queueProcessTasksState,
                                  final FilterProgressMonitor filterProgressMonitor) {
        int totalTasks = 0;
        int totalAddedTasks = 0;
        int tasksToAdd = queueProcessTasksState.getRequiredTaskCount();
        final int batchSize = Math.max(BATCH_SIZE, tasksToAdd);
        long lastTaskId = 0;

        try {
            // Keep adding tasks until we have reached the requested number.
            while (tasksToAdd > 0) {

                // Look for any existing tasks we have created.
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
                if (existingCreatedTasks.size() > 0) {
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
                        final List<ProcessorTask> existingTasks = processorTaskDao.queueExistingTasks(
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
                queueProcessTasksState.addUnownedTasksToQueue(totalAddedTasks);
                LOGGER.debug("doCreateTasks() - Added {} tasks that are no longer locked", totalAddedTasks);
            }

        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
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
        final List<Map<String, Object>> queueInfo = NullSafe.nonNullList(prioritisedFilters.get())
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
}

