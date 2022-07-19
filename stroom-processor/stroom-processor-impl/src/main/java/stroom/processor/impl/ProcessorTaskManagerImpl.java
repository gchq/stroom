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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.Limits;
import stroom.processor.shared.ProcessorFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.ExpressionValidator;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.EventRef;
import stroom.query.common.v2.EventRefs;
import stroom.query.common.v2.EventSearch;
import stroom.security.api.SecurityContext;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.logging.Metrics;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Keep a pool of stream tasks ready to go.
 * <p>
 * Fill up our pool if we are below our low water mark (FILL_LOW_SIZE).
 */
@Singleton
class ProcessorTaskManagerImpl implements ProcessorTaskManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskManagerImpl.class);

    private static final String LOCK_NAME = "ProcessorTaskManager";
    private static final int MAX_ERROR_LENGTH = 200;

    private static final int POLL_INTERVAL_MS = 10000;
    private static final int DELETE_INTERVAL_MS = POLL_INTERVAL_MS * 10;
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Fill Task Store", 3);

    private final ProcessorFilterService processorFilterService;
    private final ProcessorFilterTrackerDao processorFilterTrackerDao;
    private final ProcessorTaskDao processorTaskDao;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final TaskContext taskContext;
    private final NodeInfo nodeInfo;
    private final Provider<ProcessorConfig> processorConfigProvider;
    private final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider;
    private final MetaService metaService;
    private final EventSearch eventSearch;
    private final SecurityContext securityContext;
    private final ClusterLockService clusterLockService;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final ProcessorConfig processorConfig;

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();

    /**
     * Our filter cache
     */
    private final AtomicReference<List<ProcessorFilter>> prioritisedFiltersRef = new AtomicReference<>();

    /**
     * Our queue.
     */
    private final ConcurrentHashMap<ProcessorFilter, ProcessorTaskQueue> queueMap = new ConcurrentHashMap<>();

    /**
     * Time to see if we need filling if we are above our water mark
     */
    private final AtomicLong nextPollMs = new AtomicLong(0);

    /**
     * Time till be check if things need to be deleted
     */
    private final AtomicLong nextDeleteMs = new AtomicLong(0);

    /**
     * Flag to indicate if we are filling
     */
    private final AtomicBoolean filling = new AtomicBoolean();
    private volatile int lastQueueSizeForStats = -1;

    /**
     * Make sure the task store isn't allowed to be filled until this node has
     * run startup() and has not run shutdown().
     */
    private volatile boolean allowAsyncTaskCreation = false;
    private volatile boolean allowTaskCreation = true;

    private final Map<String, Instant> lastNodeContactTime = new ConcurrentHashMap<>();
    private Instant lastDisownedTasks = Instant.now();

    @Inject
    ProcessorTaskManagerImpl(final ProcessorFilterService processorFilterService,
                             final ProcessorFilterTrackerDao processorFilterTrackerDao,
                             final ProcessorTaskDao processorTaskDao,
                             final ExecutorProvider executorProvider,
                             final TaskContextFactory taskContextFactory,
                             final TaskContext taskContext,
                             final NodeInfo nodeInfo,
                             final Provider<ProcessorConfig> processorConfigProvider,
                             final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider,
                             final MetaService metaService,
                             final EventSearch eventSearch,
                             final SecurityContext securityContext,
                             final ClusterLockService clusterLockService,
                             final TargetNodeSetFactory targetNodeSetFactory,
                             final ProcessorConfig processorConfig) {

        this.processorFilterService = processorFilterService;
        this.processorFilterTrackerDao = processorFilterTrackerDao;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.taskContext = taskContext;
        this.nodeInfo = nodeInfo;
        this.processorTaskDao = processorTaskDao;
        this.processorConfigProvider = processorConfigProvider;
        this.internalStatisticsReceiverProvider = internalStatisticsReceiverProvider;
        this.metaService = metaService;
        this.eventSearch = eventSearch;
        this.securityContext = securityContext;
        this.clusterLockService = clusterLockService;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.processorConfig = processorConfig;
    }

    @Override
    public synchronized void startup() {
        // It shouldn't be possible to create tasks during startup.
        try {
            // Anything that we owned release
            // Lock the cluster so that only this node is able to release owned tasks at this time.
            final String nodeName = nodeInfo.getThisNodeName();
            LOGGER.info(() -> "Locking cluster to release owned tasks for node " + nodeName);
            processorTaskDao.releaseOwnedTasks(nodeName);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        } finally {
            allowAsyncTaskCreation = true;
            allowTaskCreation = true;
        }
    }

    @Override
    public synchronized void shutdown() {
        // It shouldn't be possible to create tasks during shutdown.
        try {
            allowAsyncTaskCreation = false;
            allowTaskCreation = false;
            clearTaskStore();
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    /**
     * Return the next task to perform. Called by worker threads. Also assigns
     * the task to the node asking for the job
     */
    @Override
    public ProcessorTaskList assignTasks(final String nodeName, final int count) {
        LOGGER.debug(() -> "assignTasks() called for node " + nodeName + ", count " + count);

        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserId(),
                    "Only the processing user is allowed to assign tasks");
        }

        List<ProcessorTask> assignedStreamTasks = Collections.emptyList();
        try {
            if (processorConfigProvider.get().isAssignTasks() && count > 0) {
                // Get local reference to list in case it is swapped out.
                final List<ProcessorFilter> filters = prioritisedFiltersRef.get();
                if (filters != null && filters.size() > 0) {
                    assignedStreamTasks = new ArrayList<>(count);

                    int index = 0;
                    while (assignedStreamTasks.size() < count && index < filters.size()) {
                        final ProcessorFilter filter = filters.get(index);

                        // Get the queue for this filter.
                        final ProcessorTaskQueue queue = queueMap.get(filter);
                        if (queue != null) {
                            // Add as many tasks as we can for this filter.
                            ProcessorTask streamTask = queue.poll();
                            while (streamTask != null) {
                                final ProcessorTask assigned = processorTaskDao.changeTaskStatus(
                                        streamTask,
                                        nodeName,
                                        TaskStatus.ASSIGNED,
                                        null,
                                        null);
                                if (assigned != null) {
                                    assignedStreamTasks.add(assigned);
                                }

                                if (assignedStreamTasks.size() < count) {
                                    streamTask = queue.poll();
                                } else {
                                    streamTask = null;
                                }
                            }
                        }

                        index++;
                    }
                }
            } else {
                LOGGER.debug("assignTasks is disabled");
            }

            // Have a go at kicking off a fill.
            fillTaskStore();
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }

        // Output some trace logging so we can see where tasks go.
        taskStatusTraceLog.assignTasks(ProcessorTaskManagerImpl.class, assignedStreamTasks, nodeName);

        final int taskCount = assignedStreamTasks.size();
        LOGGER.debug(() -> "Assigning " +
                taskCount
                + " tasks (" +
                count +
                " requested) to node " +
                nodeName);
        return new ProcessorTaskList(nodeName, assignedStreamTasks);
    }

    @Override
    public Boolean abandonTasks(final ProcessorTaskList processorTaskList) {
        LOGGER.debug(() -> "abandonTasks() called for " +
                Optional.ofNullable(processorTaskList)
                        .map(ProcessorTaskList::getList)
                        .map(List::size)
                        .orElse(0) +
                " tasks");

        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserId(),
                    "Only the processing user is allowed to abandon tasks");
        }

        // Output some trace logging so we can see where tasks go.
        taskStatusTraceLog.abandonTasks(ProcessorTaskManagerImpl.class,
                processorTaskList.getList(),
                processorTaskList.getNodeName());

        for (final ProcessorTask processorTask : processorTaskList.getList()) {
            abandon(processorTask);
        }

        return true;
    }

    private void abandon(final ProcessorTask processorTask) {
        try {
            LOGGER.warn(() -> "abandon() - " + processorTask);
            processorTaskDao.changeTaskStatus(
                    processorTask,
                    null,
                    TaskStatus.UNPROCESSED,
                    null,
                    null);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "abandon() - " + processorTask, e);
        }
    }

    private void releaseAll() {
        for (final Entry<ProcessorFilter, ProcessorTaskQueue> entry : queueMap.entrySet()) {
            final ProcessorFilter filter = entry.getKey();
            if (filter != null) {
                final ProcessorTaskQueue queue = queueMap.remove(filter);
                if (queue != null) {
                    ProcessorTask streamTask = queue.poll();
                    while (streamTask != null) {
                        release(streamTask);
                        streamTask = queue.poll();
                    }
                }
            }
        }
    }

    private void release(final ProcessorTask processorTask) {
        try {
            LOGGER.warn("release() - {}", processorTask);
            processorTaskDao.changeTaskStatus(
                    processorTask,
                    null,
                    TaskStatus.UNPROCESSED,
                    null,
                    null);
        } catch (final RuntimeException e) {
            LOGGER.error("release() - {}", processorTask, e);
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

    /**
     * Lazy fill
     */
    private void fillTaskStore() {
        if (allowAsyncTaskCreation) {
            try {
                // Only kick off the work if are not already filling.
                if (filling.compareAndSet(false, true)) {
                    // See if it has been long enough since we last filled.
                    if (isScheduled()) {
                        LOGGER.debug(() -> "fillTaskStore() - Executing CreateStreamTasksTask");

                        final Runnable runnable = taskContextFactory.context(
                                "Fill Task Store",
                                taskContext ->
                                        securityContext.secure(() ->
                                                createTasks(taskContext)));

                        final Executor executor = executorProvider.get(THREAD_POOL);
                        CompletableFuture
                                .runAsync(runnable, executor)
                                .whenComplete((r, t) -> {
                                    if (t == null) {
                                        scheduleNextPollMs();
                                    }
                                    filling.set(false);
                                });
                    } else {
                        filling.set(false);
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    /**
     * Move the timer based schedule forward.
     */
    private void scheduleNextPollMs() {
        nextPollMs.set(System.currentTimeMillis() + POLL_INTERVAL_MS);
    }

    private boolean isScheduled() {
        // If we have past the window the last time we ran then yes.
        final long timeNowMs = System.currentTimeMillis();
        if (timeNowMs > nextPollMs.get()) {
            LOGGER.trace(() -> "isScheduled() - Yes as time has past (queueSize=" + getTaskQueueSize() + ")");
            return true;
        }

        return false;
    }

    /**
     * For use in tests and other setup tasks
     */
    @Override
    public void createTasks() {
        taskContextFactory.context("Create Tasks", taskContext ->
                securityContext.secure(() ->
                        createTasks(taskContext))).run();
    }

    private synchronized void createTasks(final TaskContext taskContext) {
        // We need to make sure that only 1 thread at a time is allowed to
        // create tasks. This should always be the case in production but some
        // tests will call this directly while scheduled execution could also be
        // running. Also, if the master node changes it is possible for one master
        // to be in the middle of creating tasks when another node assumes master
        // status and tries to create tasks too. Thus, a db backed cluster lock
        // is needed
        try {
            if (allowTaskCreation) {
                // We need an overarching cluster lock for all task creation
                // Some task creation is async, but we will wait for that
                // to complete so all task creation is encapsulated by this lock
                LOGGER.debug(() -> "Locking cluster to create tasks");
                clusterLockService.lock(LOCK_NAME, () -> doCreateTasks(taskContext));
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    public void disownDeadTasks() {
        try {
            final String node = nodeInfo.getThisNodeName();
            final String masterNode = targetNodeSetFactory.getMasterNode();
            if (node != null && node.equals(masterNode)) {
                // If this is the master node then see if there are any nodes that we haven't had contact with
                // for some time.

                // IF we haven't had contact with a node for 10 minutes then forcibly release the tasks owned
                // by that node.
                final Instant now = Instant.now();
                final Set<String> activeNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();
                activeNodes.forEach(activeNode -> lastNodeContactTime.put(activeNode, now));
                final Instant disownTaskAge = now.minus(processorConfig.getDisownDeadTasksAfter());
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
                    processorTaskDao.retainOwnedTasks(lastNodeContactTime.keySet(), disownTaskAge);
                }
            }
        } catch (final RuntimeException | NodeNotFoundException | NullClusterStateException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }

    public synchronized void releaseOldQueuedTasks() {
        if (queueMap.size() > 0) {
            try {
                final String node = nodeInfo.getThisNodeName();
                final String masterNode = targetNodeSetFactory.getMasterNode();
                if (node != null && !node.equals(masterNode)) {
                    // This is no longer the master node so release all tasks.
                    releaseAll();
                }
            } catch (final RuntimeException | NodeNotFoundException | NullClusterStateException e) {
                LOGGER.debug(e.getMessage(), e);
            }
        }
    }

    private void doCreateTasks(final TaskContext taskContext) {
        // We need to make sure that only 1 thread at a time is allowed to
        // create tasks. This should always be the case in production but some
        // tests will call this directly while scheduled execution could also be
        // running.
        LOGGER.debug(() -> "doCreateTasks()");

        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.debug(() -> "doCreateTasks() - Starting");
        taskContext.info(() -> "Starting");

        // Get an up-to-date list of all enabled stream processor filters.
        LOGGER.trace(() -> "Getting enabled non deleted filters");
        taskContext.info(() -> "Getting enabled non deleted filters");
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ProcessorFields.ENABLED, Condition.EQUALS, true)
                .addTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
                .addTerm(ProcessorFilterFields.ENABLED, Condition.EQUALS, true)
                .addTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false)
                .build();

        final ExpressionCriteria findProcessorFilterCriteria = new ExpressionCriteria(expression);
        final List<ProcessorFilter> filters = processorFilterService
                .find(findProcessorFilterCriteria).getValues();
        LOGGER.trace(() -> "Found " + filters.size() + " filters");
        taskContext.info(() -> "Found " + filters.size() + " filters");

        // Sort the stream processor filters by priority.
        filters.sort(ProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR);

        // Update the stream task store.
        prioritisedFiltersRef.set(filters);

        final String nodeName = nodeInfo.getThisNodeName();
        if (nodeName == null) {
            throw new NullPointerException("Node is null");
        }

        // Now fill the stream task store with tasks for each filter.
        // The aim is to create N tasks in total where N is processorConfig.getQueueSize
        // Also need to ensure each filter queue has no more than N in it.
        final ProcessorConfig processorConfig = processorConfigProvider.get();
        final int maxTasksToCreate = processorConfig.getQueueSize();
        // If a queue is already half full then don't bother adding more
        final int halfQueueSize = maxTasksToCreate / 2;

        final TaskCreationProgressTracker progressTracker = new TaskCreationProgressTracker(
                maxTasksToCreate,
                queueMap,
                processorConfig);

        try {
            for (final ProcessorFilter filter : filters) {
                final ProcessorTaskQueue queue = queueMap.computeIfAbsent(filter, k -> new ProcessorTaskQueue());
                final int currQueueSize = queue.size();

                if (progressTracker.areTasksRemainingToBeCreated() && currQueueSize < halfQueueSize) {
                    if (queue.compareAndSetFilling(false, true)) {

                        // Create tasks for this filter
                        LOGGER.logDurationIfDebugEnabled(
                                () -> createTasksForFilter(
                                        taskContext,
                                        nodeName,
                                        filter,
                                        queue,
                                        progressTracker),
                                () -> LogUtil.message("Create tasks for filter {} with priority {}",
                                        filter.getId(), filter.getPriority()));
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }

        // Release items from the queue that no longer have an enabled filter
        taskContext.info(() -> "Releasing tasks for disabled filters");
        final Set<ProcessorFilter> enabledFilterSet = new HashSet<>(filters);
        for (final ProcessorFilter filter : queueMap.keySet()) {
            if (!enabledFilterSet.contains(filter)) {
                final ProcessorTaskQueue queue = queueMap.remove(filter);
                if (queue != null) {
                    ProcessorTask streamTask = queue.poll();
                    while (streamTask != null) {
                        release(streamTask);
                        streamTask = queue.poll();
                    }
                }
            }
        }

        // We must be the master node so set a time in the future to run delete.
        scheduleDelete();

        // We may have async search tasks still being created so we need to wait for those
        // in case another node gets master status and tries to do task creation.
        progressTracker.waitForCompletion();

        taskContext.info(() -> "Finished");

        if (progressTracker.getTotalTasksCreated() > 0) {
            LOGGER.info(() -> LogUtil.message("Finished creating tasks in {}. {}",
                    logExecutionTime, progressTracker.getProgressSummaryMessage()));
        }
    }


    private int getTaskCountToCreate(final ProcessorTaskQueue queue,
                                     final TaskCreationProgressTracker progressTracker) {
        return Math.min(
                processorConfigProvider.get().getQueueSize() - queue.size(),
                progressTracker.getTotalRemainingTasksToCreate());
    }

    private void createTasksForFilter(final TaskContext taskContext,
                                      final String nodeName,
                                      final ProcessorFilter filter,
                                      final ProcessorTaskQueue queue,
                                      final TaskCreationProgressTracker progressTracker) {
        Optional<ProcessorFilter> optionalProcessorFilter = Optional.empty();

        final AtomicBoolean isSearching = new AtomicBoolean();
        try {
            // Reload as it could have changed
            optionalProcessorFilter = processorFilterService.fetch(filter.getId());

            // The filter might have been deleted since we found it.
            optionalProcessorFilter.ifPresent(loadedFilter -> {
                // Set the current user to be the one who created the filter so that only streams that that user
                // has access to are processed.
                securityContext.asUser(securityContext.createIdentity(loadedFilter.getCreateUser()), () -> {
                    LOGGER.debug(() -> "createTasksForFilter() - processorFilter " + loadedFilter);

                    // Only try and create tasks if the processor is enabled.
                    if (loadedFilter.isEnabled() && loadedFilter.getProcessor().isEnabled()) {
                        taskContext.info(() -> "Creating tasks: " + loadedFilter);

                        // If there are any tasks for this filter that were
                        // previously created but are unprocessed, not owned by any
                        // node and their associated stream is unlocked then add
                        // them here.
                        final ProcessorConfig processorConfig = processorConfigProvider.get();
                        if (processorConfig.isFillTaskQueue()) {
                            addUnownedTasks(
                                    taskContext,
                                    nodeName,
                                    loadedFilter,
                                    queue,
                                    progressTracker);
                        }

                        // If we are allowing tasks to be created then go ahead and create some.
                        if (processorConfig.isCreateTasks()) {
                            // Skip once we have done all that is required
                            // re-compute tasks to create after adding unowned tasks
                            final int requiredTasks = getTaskCountToCreate(queue, progressTracker);

                            if (requiredTasks > 0 && !Thread.currentThread().isInterrupted()) {
                                final QueryData queryData = loadedFilter.getQueryData();
                                final boolean isStreamStoreSearch = queryData.getDataSource() != null
                                        && queryData.getDataSource().getType().equals(MetaFields.STREAM_STORE_TYPE);

                                // Record the time before we are going to query for
                                // streams for tracking purposes.
                                final long streamQueryTime = System.currentTimeMillis();

                                // Get the tracker for this filter.
                                ProcessorFilterTracker tracker = loadedFilter.getProcessorFilterTracker();
                                LOGGER.debug(() -> "createTasks() - Filter " + loadedFilter.getId());
                                if (ProcessorFilterTracker.COMPLETE.equals(tracker.getStatus()) ||
                                        ProcessorFilterTracker.ERROR.equals(tracker.getStatus())) {
                                    // If the tracker is complete we need to
                                    // make sure the status is updated so we can
                                    // see that it is not delivering any more
                                    // tasks.
                                    if (tracker.getLastPollTaskCount() != null && tracker.getLastPollTaskCount() > 0) {
                                        tracker.setLastPollMs(streamQueryTime);
                                        tracker.setLastPollTaskCount(0);
                                        processorFilterTrackerDao.update(tracker);
                                    }

                                } else if (!isStreamStoreSearch) {
                                    // Create stream tasks by executing a search.
                                    isSearching.set(true);
                                    createTasksFromSearchQuery(
                                            loadedFilter,
                                            queryData,
                                            streamQueryTime,
                                            nodeName,
                                            progressTracker,
                                            queue,
                                            tracker,
                                            processorConfig,
                                            taskContext);
                                } else {
                                    // Create tasks from a standard stream filter criteria.
                                    createTasksFromCriteria(
                                            loadedFilter,
                                            queryData,
                                            streamQueryTime,
                                            nodeName,
                                            progressTracker,
                                            queue,
                                            processorConfig,
                                            tracker);
                                }
                            }
                        } else {
                            LOGGER.debug(() -> "createTasks() - Filter " +
                                    loadedFilter.getId() +
                                    " no tasks needed at this time");
                        }

                        taskContext.info(() -> "Created " + progressTracker.getCreatedCount(loadedFilter)
                                + " tasks: " + loadedFilter);
                    }
                });
            });
        } catch (final RuntimeException e) {
            final String pipelineDetails = optionalProcessorFilter
                    .map(loadedFilter -> {
                        if (loadedFilter.getProcessor() != null &&
                                loadedFilter.getProcessor().getPipelineUuid() != null) {
                            return " for pipeline " + loadedFilter.getProcessor().getPipelineUuid();
                        }
                        return "";
                    })
                    .orElse("");

            LOGGER.error(() -> "Error processing filter with id = " + filter.getId() + pipelineDetails);
            LOGGER.debug(e::getMessage, e);

            // Update the tracker with the error if we can.
            try {
                optionalProcessorFilter = processorFilterService.fetch(filter.getId());
                optionalProcessorFilter.ifPresent(loadedFilter -> {
                    ProcessorFilterTracker tracker = loadedFilter.getProcessorFilterTracker();
                    String error = e.toString();
                    if (error.length() > MAX_ERROR_LENGTH) {
                        error = error.substring(0, MAX_ERROR_LENGTH) + "...";
                    }
                    tracker.setStatus("Error: " + error);
                    processorFilterTrackerDao.update(tracker);
                });
            } catch (final RuntimeException e2) {
                LOGGER.error(e::getMessage, e);
                LOGGER.error(e::getMessage, e2);
            }

        } finally {
            if (!isSearching.get()) {
                queue.setFilling(false);
            }
        }
    }

    private void addUnownedTasks(final TaskContext taskContext,
                                 final String nodeName,
                                 final ProcessorFilter filter,
                                 final ProcessorTaskQueue queue,
                                 final TaskCreationProgressTracker progressTracker) {
        int totalTasks = 0;
        int totalAddedTasks = 0;
        int tasksToAdd = getTaskCountToCreate(queue, progressTracker);
        final int batchSize = Math.max(1000, tasksToAdd);
        long minTaskId = 0;

        try {
            // Keep adding tasks until we have reached the requested number.
            while (tasksToAdd > 0) {

                // First look for any items that are no-longer locked etc
                final ExpressionOperator findProcessorTaskExpression = ExpressionOperator.builder()
                        .addTerm(ProcessorTaskFields.TASK_ID, Condition.GREATER_THAN, minTaskId)
                        .addTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, TaskStatus.UNPROCESSED.getDisplayValue())
                        .addTerm(ProcessorTaskFields.NODE_NAME, Condition.IS_NULL, null)
                        .addTerm(ProcessorTaskFields.PROCESSOR_FILTER_ID, Condition.EQUALS, filter.getId())
                        .build();
                final ExpressionCriteria findProcessorTaskCriteria =
                        new ExpressionCriteria(findProcessorTaskExpression);
                findProcessorTaskCriteria.obtainPageRequest().setLength(batchSize);
                findProcessorTaskCriteria.addSort(ProcessorTaskFields.FIELD_ID);

                final List<ProcessorTask> processorTasks = processorTaskDao.find(findProcessorTaskCriteria).getValues();
                taskStatusTraceLog.addUnownedTasks(ProcessorTaskManagerImpl.class, processorTasks);

                // If we got fewer tasks returned than we asked for then we won't need to ask for more.
                if (processorTasks.size() < batchSize) {
                    tasksToAdd = 0;
                }

                // If we have some processor tasks then see if we can find unlocked meta for them so we can process.
                if (processorTasks.size() > 0) {
                    // Increment the total number of unowned tasks.
                    totalTasks += processorTasks.size();

                    // Find unlocked meta corresponding to this list of unowned tasks.
                    final ExpressionOperator.Builder metaIdExpressionBuilder = ExpressionOperator.builder().op(Op.OR);
                    for (final ProcessorTask task : processorTasks) {
                        metaIdExpressionBuilder.addTerm(MetaFields.ID, Condition.EQUALS, task.getMetaId());
                        // Ensure we don't see this task again in the next attempt.
                        minTaskId = Math.max(minTaskId, task.getId());
                    }

                    // Find all unlocked meta entries for the selected processor tasks.
                    final ExpressionOperator findMetaExpression = ExpressionOperator.builder()
                            .addOperator(metaIdExpressionBuilder.build())
                            .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                            .build();
                    final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(findMetaExpression);
                    findMetaCriteria.setSort(MetaFields.ID.getName(), false, false);
                    final List<Meta> metaList = metaService.find(findMetaCriteria).getValues();

                    if (metaList.size() > 0) {
                        final ExpressionOperator.Builder taskIdExpressionBuilder =
                                ExpressionOperator.builder().op(Op.OR);

                        // Create a map of meta items keyed by id.
                        final Map<Long, Meta> metaMap = metaList
                                .stream()
                                .collect(Collectors.toMap(Meta::getId, Function.identity()));
                        // For each processor task see if we have received meta and if so modify the task and add it to
                        // the queue.
                        for (final ProcessorTask processorTask : processorTasks) {
                            final Meta meta = metaMap.get(processorTask.getMetaId());
                            if (meta != null) {
                                taskIdExpressionBuilder.addTerm(
                                        ProcessorTaskFields.TASK_ID,
                                        Condition.EQUALS,
                                        processorTask.getId());

                                tasksToAdd--;
                                if (tasksToAdd == 0) {
                                    break;
                                }
                            }
                        }

                        final ExpressionCriteria updateProcessorTaskCriteria =
                                new ExpressionCriteria(taskIdExpressionBuilder.build());
                        // Modify matching processor tasks.
                        final ResultPage<ProcessorTask> modifiedTasks = processorTaskDao.changeTaskStatus(
                                updateProcessorTaskCriteria,
                                nodeName,
                                TaskStatus.UNPROCESSED,
                                null,
                                null);

                        // Add modified tasks to the queue.
                        if (modifiedTasks != null && modifiedTasks.size() > 0) {
                            queue.addAll(modifiedTasks.getValues());

                            totalAddedTasks += modifiedTasks.size();

                            final int finalTotalAddedTasks = totalAddedTasks;
                            final int finalTotalTasks = totalTasks;
                            taskContext.info(() -> LogUtil.message("Adding {}/{} non owned Tasks",
                                    finalTotalAddedTasks,
                                    finalTotalTasks));
                        }
                    }
                }
            }

            if (totalAddedTasks > 0) {
                progressTracker.incrementTaskCreationCount(filter, totalAddedTasks);
                LOGGER.debug("doCreateTasks() - Added {} tasks that are no longer locked", totalAddedTasks);
            }

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void createTasksFromSearchQuery(final ProcessorFilter filter,
                                            final QueryData queryData,
                                            final long streamQueryTime,
                                            final String nodeName,
                                            final TaskCreationProgressTracker progressTracker,
                                            final ProcessorTaskQueue queue,
                                            final ProcessorFilterTracker tracker,
                                            final ProcessorConfig processorConfig,
                                            final TaskContext taskContext) {

        final EventRef minEvent = new EventRef(tracker.getMinMetaId(), tracker.getMinEventId());
        final EventRef maxEvent = new EventRef(Long.MAX_VALUE, 0L);
        long maxStreams = progressTracker.getTaskCountToCreate(filter);
        LOGGER.debug(() -> "Creating search query tasks maxStreams: " +
                progressTracker.getTaskCountToCreate(filter) +
                ", filer: " +
                filter);
        long maxEvents = 1000000;
        final long maxEventsPerStream = 1000;

        // Are there any limits set on the query.
        if (queryData.getLimits() != null) {
            final Limits limits = queryData.getLimits();

            // If there is a duration limit set on task creation then set the
            // tracker to complete and return if we have exceeded this duration.
            if (limits.getDurationMs() != null) {
                final long start = filter.getCreateTimeMs();
                final long end = start + limits.getDurationMs();
                if (end < System.currentTimeMillis()) {
                    tracker.setStatus(ProcessorFilterTracker.COMPLETE);
                    processorFilterTrackerDao.update(tracker);
                    return;
                }
            }

            if (limits.getStreamCount() != null) {
                long streamLimit = limits.getStreamCount();
                if (tracker.getMetaCount() != null) {
                    streamLimit -= tracker.getMetaCount();
                }

                maxStreams = Math.min(streamLimit, maxStreams);

                if (maxStreams <= 0) {
                    tracker.setStatus(ProcessorFilterTracker.COMPLETE);
                    processorFilterTrackerDao.update(tracker);
                    return;
                }
            }

            if (limits.getEventCount() != null) {
                long eventLimit = limits.getEventCount();
                if (tracker.getEventCount() != null) {
                    eventLimit -= tracker.getEventCount();
                }

                maxEvents = Math.min(eventLimit, maxEvents);

                if (maxEvents <= 0) {
                    tracker.setStatus(ProcessorFilterTracker.COMPLETE);
                    processorFilterTrackerDao.update(tracker);
                    return;
                }
            }
        }

        final Query query = Query.builder()
                .dataSource(queryData.getDataSource())
                .expression(queryData.getExpression())
                .params(getParams(queryData))
                .build();

        // Update the tracker status message.
        tracker.setStatus("Searching...");
        final ProcessorFilterTracker updatedTracker = processorFilterTrackerDao.update(tracker);

        final Long maxMetaId = metaService.getMaxId();

        final BiConsumer<EventRefs, Throwable> consumer = (eventRefs, throwable) -> {
            LOGGER.debug(() -> LogUtil.message(
                    "createTasksFromEventRefs() called for {} eventRefs, filter {}", eventRefs.size(), filter));
            try {
                if (throwable != null) {
                    final String message = "" +
                            "Error creating tasks for filter (id=" +
                            filter.getId() +
                            "). " +
                            throwable.getMessage();
                    LOGGER.error(message);
                    LOGGER.debug(message, throwable);
                    ProcessorFilterTracker tracker2 = updatedTracker;
                    tracker2.setStatus(ProcessorFilterTracker.ERROR);
                    tracker2 = processorFilterTrackerDao.update(tracker2);

                } else if (eventRefs == null) {
                    LOGGER.debug(() -> "eventRefs is null");
                    ProcessorFilterTracker tracker2 = updatedTracker;
                    tracker2.setStatus(ProcessorFilterTracker.COMPLETE);
                    tracker2 = processorFilterTrackerDao.update(tracker2);

                } else {
                    final int resultSize = eventRefs.size();
                    final boolean reachedLimit = eventRefs.isReachedLimit();

                    // Update the tracker status message.
                    ProcessorFilterTracker tracker2 = updatedTracker;
                    tracker2.setStatus("Creating...");
                    tracker2 = processorFilterTrackerDao.update(tracker2);

                    // Create a task for each stream reference.
                    final Map<Meta, InclusiveRanges> map = createStreamMap(eventRefs);

                    processorTaskDao.createNewTasks(
                            filter,
                            tracker2,
                            streamQueryTime,
                            map,
                            nodeName,
                            maxMetaId,
                            reachedLimit,
                            processorConfig.isFillTaskQueue(),
                            createdTasks -> {
                                // Transfer the newly created (and available) tasks to the queue.
                                final List<ProcessorTask> availableTaskList = createdTasks.getAvailableTaskList();

                                if (!availableTaskList.isEmpty()) {
                                    queue.addAll(availableTaskList);
                                    progressTracker.incrementTaskCreationCount(filter, availableTaskList.size());
                                }

                                LOGGER.debug(() -> "createTasks() - Created " +
                                        createdTasks.getTotalTasksCreated() +
                                        " tasks for filter " +
                                        filter.toString());

                                queue.setFilling(false);
                            });
                }
            } catch (final Exception e) {
                LOGGER.error("Error creating tasks for filter {}, {}", filter.getId(), e.getMessage(), e);
            } finally {
                queue.setFilling(false);
            }
        };

        final CompletableFuture<Void> future = eventSearch.search(
                taskContext,
                query,
                minEvent,
                maxEvent,
                maxStreams,
                maxEvents,
                maxEventsPerStream,
                consumer);

        // record the future so we can wait for it later
        progressTracker.addFuture(future);
    }

    private List<Param> getParams(final QueryData queryData) {
        // Create a parameter map.
        final Map<String, String> parameterMap = ExpressionParamUtil.parse(queryData.getParams());

        final List<Param> params = new ArrayList<>();
        for (final Entry<String, String> entry : parameterMap.entrySet()) {
            params.add(new Param(entry.getKey(), entry.getValue()));
        }
        return params;
    }

    private void createTasksFromCriteria(final ProcessorFilter filter,
                                         final QueryData queryData,
                                         final long streamQueryTime,
                                         final String nodeName,
                                         final TaskCreationProgressTracker progressTracker,
                                         final ProcessorTaskQueue queue,
                                         final ProcessorConfig processorConfig,
                                         final ProcessorFilterTracker tracker) {
        if (termCount(queryData) == 0) {
            throw new RuntimeException("Attempting to create tasks with an unconstrained filter " + filter);
        }

        final int requiredTasks = progressTracker.getTaskCountToCreate(filter);
        LOGGER.debug(() -> "Creating tasks from criteria, requiredTasks: " + requiredTasks + ", filter: " + filter);

        if (requiredTasks > 0) {
            // Update the tracker status message.
            tracker.setStatus("Creating...");
            final ProcessorFilterTracker updatedTracker = Metrics.measure("update tracker",
                    () -> processorFilterTrackerDao.update(tracker));

            // This will contain locked and unlocked streams.
            final Long maxMetaId = Metrics.measure("get max id", metaService::getMaxId);

            final List<Meta> streamList = Metrics.measure("runSelectMetaQuery", () -> runSelectMetaQuery(
                    queryData.getExpression(),
                    updatedTracker.getMinMetaId(),
                    filter.getMinMetaCreateTimeMs(),
                    filter.getMaxMetaCreateTimeMs(),
                    filter.getPipeline(),
                    filter.isReprocess(),
                    requiredTasks));

            // Just create regular stream processing tasks.
            final Map<Meta, InclusiveRanges> map = new HashMap<>();

            Metrics.measure("map", () -> {
                for (final Meta meta : streamList) {
                    map.put(meta, null);
                }
            });

            Metrics.measure("createNewTasks", () ->
                    processorTaskDao.createNewTasks(
                            filter,
                            updatedTracker,
                            streamQueryTime,
                            map,
                            nodeName,
                            maxMetaId,
                            false,
                            processorConfig.isFillTaskQueue(),
                            createdTasks -> {
                                // Transfer the newly created (and available) tasks to the queue.
                                final List<ProcessorTask> availableTaskList = createdTasks.getAvailableTaskList();
                                queue.addAll(availableTaskList);
                                progressTracker.incrementTaskCreationCount(filter, availableTaskList.size());

                                LOGGER.debug(() -> "createTasks() - Created " +
                                        createdTasks.getTotalTasksCreated() +
                                        " tasks (requiredTasks=" +
                                        requiredTasks +
                                        ") for filter " +
                                        filter);
                            }));
        }
    }

    private int termCount(final QueryData queryData) {
        if (queryData == null || queryData.getExpression() == null) {
            return 0;
        }
        return ExpressionUtil.termCount(queryData.getExpression());
    }

    private Map<Meta, InclusiveRanges> createStreamMap(final EventRefs eventRefs) {
        final int maxRangesPerStream = 1000;
        final Map<Meta, InclusiveRanges> streamMap = new HashMap<>();

        if (eventRefs != null) {
            long currentMetaId = -1;
            Meta currentMeta = null;
            InclusiveRanges ranges = null;
            boolean trimmed = false;
            for (final EventRef ref : eventRefs) {
                if (!trimmed) {
                    // When the stream id changes add the current ranges to the map.
                    if (currentMetaId != ref.getStreamId()) {
                        if (ranges != null) {
                            if (ranges.getRanges().size() > maxRangesPerStream) {
                                ranges = ranges.subRanges(maxRangesPerStream);
                                trimmed = true;
                            }

                            if (currentMeta != null) {
                                streamMap.put(currentMeta, ranges);
                            }
                        }

                        currentMetaId = ref.getStreamId();
                        currentMeta = metaService.getMeta(currentMetaId);
                        ranges = new InclusiveRanges();
                    }

                    if (ranges != null) {
                        ranges.addEvent(ref.getEventId());
                    }
                }
            }

            // Add the final ranges to the map.
            if (!trimmed && ranges != null) {
                if (ranges.getRanges().size() > maxRangesPerStream) {
                    ranges = ranges.subRanges(maxRangesPerStream);
                }

                if (currentMeta != null) {
                    streamMap.put(currentMeta, ranges);
                }
            }
        }

        return streamMap;
    }

    /**
     * @return streams that have not yet got a stream task for a particular
     * stream processor
     */
    List<Meta> runSelectMetaQuery(final ExpressionOperator expression,
                                  final long minMetaId,
                                  final Long minMetaCreateTimeMs,
                                  final Long maxMetaCreateTimeMs,
                                  final DocRef pipelineDocRef,
                                  final boolean reprocess,
                                  final int length) {
        // Validate expression.
        final ExpressionValidator expressionValidator = new ExpressionValidator(MetaFields.getAllFields());
        expressionValidator.validate(expression);

        if (reprocess) {
            // Don't select deleted streams.
            final ExpressionOperator statusExpression = ExpressionOperator.builder().op(Op.OR)
                    .addTerm(MetaFields.PARENT_STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                    .addTerm(MetaFields.PARENT_STATUS, Condition.EQUALS, Status.LOCKED.getDisplayValue())
                    .build();

            ExpressionOperator.Builder builder = ExpressionOperator.builder()
                    .addOperator(expression)
                    .addTerm(MetaFields.PARENT_ID, Condition.GREATER_THAN_OR_EQUAL_TO, minMetaId);

            if (pipelineDocRef != null) {
                builder.addTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipelineDocRef);
            }

            if (minMetaCreateTimeMs != null) {
                builder = builder.addTerm(MetaFields.PARENT_CREATE_TIME,
                        Condition.GREATER_THAN_OR_EQUAL_TO,
                        DateUtil.createNormalDateTimeString(minMetaCreateTimeMs));
            }
            if (maxMetaCreateTimeMs != null) {
                builder = builder.addTerm(MetaFields.PARENT_CREATE_TIME,
                        Condition.LESS_THAN_OR_EQUAL_TO,
                        DateUtil.createNormalDateTimeString(maxMetaCreateTimeMs));
            }
            builder = builder.addOperator(statusExpression);

            final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(builder.build());
            findMetaCriteria.setSort(MetaFields.PARENT_ID.getName(), false, false);
            findMetaCriteria.obtainPageRequest().setLength(length);

            return metaService.findReprocess(findMetaCriteria).getValues();

        } else {
            // Don't select deleted streams.
            final ExpressionOperator statusExpression = ExpressionOperator.builder().op(Op.OR)
                    .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                    .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.LOCKED.getDisplayValue())
                    .build();

            ExpressionOperator.Builder builder = ExpressionOperator.builder()
                    .addOperator(expression)
                    .addTerm(MetaFields.ID, Condition.GREATER_THAN_OR_EQUAL_TO, minMetaId);

            if (minMetaCreateTimeMs != null) {
                builder = builder.addTerm(MetaFields.CREATE_TIME,
                        Condition.GREATER_THAN_OR_EQUAL_TO,
                        DateUtil.createNormalDateTimeString(minMetaCreateTimeMs));
            }
            if (maxMetaCreateTimeMs != null) {
                builder = builder.addTerm(MetaFields.CREATE_TIME,
                        Condition.LESS_THAN_OR_EQUAL_TO,
                        DateUtil.createNormalDateTimeString(maxMetaCreateTimeMs));
            }
            builder = builder.addOperator(statusExpression);

            final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(builder.build());
            findMetaCriteria.setSort(MetaFields.ID.getName(), false, false);
            findMetaCriteria.obtainPageRequest().setLength(length);

            return metaService.find(findMetaCriteria).getValues();
        }
    }

    /**
     * Schedule a delete if we don't have one
     */
    private void scheduleDelete() {
        if (nextDeleteMs.get() == 0) {
            nextDeleteMs.set(System.currentTimeMillis() + DELETE_INTERVAL_MS);
            LOGGER.debug(() -> "scheduleDelete() - nextDeleteMs=" +
                    DateUtil.createNormalDateTimeString(nextDeleteMs.get()));
        }
    }

    @Override
    public void writeQueueStatistics() {
        taskContext.info(() -> "Writing processor task queue statistics");
        try {
            // Avoid writing loads of same value stats So write every min while it changes Under little load the queue
            // size will be 0
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
                    LOGGER.error(e::getMessage, e);
                }
                lastQueueSizeForStats = queueSize;
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    @Override
    public AtomicLong getNextDeleteMs() {
        return nextDeleteMs;
    }

    @Override
    public void setAllowAsyncTaskCreation(final boolean allowAsyncTaskCreation) {
        this.allowAsyncTaskCreation = allowAsyncTaskCreation;
    }

    @Override
    public void setAllowTaskCreation(final boolean allowTaskCreation) {
        this.allowTaskCreation = allowTaskCreation;
    }

}
