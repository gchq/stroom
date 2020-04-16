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

import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.*;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.search.api.EventRef;
import stroom.search.api.EventRefs;
import stroom.search.api.EventSearch;
import stroom.security.api.SecurityContext;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleThreadPool;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.shared.ThreadPool;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.PermissionException;
import stroom.util.shared.Sort.Direction;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Keep a pool of stream tasks ready to go.
 * <p>
 * Fill up our pool if we are below our low water mark (FILL_LOW_SIZE).
 */
@Singleton
class ProcessorTaskManagerImpl implements ProcessorTaskManager {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskManagerImpl.class);

    private static final int POLL_INTERVAL_MS = 10000;
    private static final int DELETE_INTERVAL_MS = POLL_INTERVAL_MS * 10;
    private static final ThreadPool THREAD_POOL = new SimpleThreadPool(3);

    private final ProcessorFilterService processorFilterService;
    private final ProcessorFilterTrackerDao processorFilterTrackerDao;
    private final ProcessorTaskDao processorTaskDao;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final NodeInfo nodeInfo;
    private final ProcessorConfig processorConfig;
    private final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider;
    private final MetaService metaService;
    private final EventSearch eventSearch;
    private final SecurityContext securityContext;

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();

    private final ReentrantLock createTasksLock = new ReentrantLock();

    /**
     * Our filter cache
     */
    private final AtomicReference<List<ProcessorFilter>> prioritisedFiltersRef = new AtomicReference<>();

    /**
     * Our queue.
     */
    private final ConcurrentHashMap<ProcessorFilter, StreamTaskQueue> queueMap = new ConcurrentHashMap<>();

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
    private final ConcurrentHashMap<Integer, Boolean> exhaustedFilterMap = new ConcurrentHashMap<>();
    private volatile int lastQueueSizeForStats = -1;

    /**
     * Make sure the task store isn't allowed to be filled until this node has
     * run startup() and has not run shutdown().
     */
    private volatile boolean allowFillTaskStore = false;

    @Inject
    ProcessorTaskManagerImpl(final ProcessorFilterService processorFilterService,
                             final ProcessorFilterTrackerDao processorFilterTrackerDao,
                             final ProcessorTaskDao processorTaskDao,
                             final ExecutorProvider executorProvider,
                             final TaskContextFactory taskContextFactory,
                             final NodeInfo nodeInfo,
                             final ProcessorConfig processorConfig,
                             final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider,
                             final MetaService metaService,
                             final EventSearch eventSearch,
                             final SecurityContext securityContext) {

        this.processorFilterService = processorFilterService;
        this.processorFilterTrackerDao = processorFilterTrackerDao;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.nodeInfo = nodeInfo;
        this.processorTaskDao = processorTaskDao;
        this.processorConfig = processorConfig;
        this.internalStatisticsReceiverProvider = internalStatisticsReceiverProvider;
        this.metaService = metaService;
        this.eventSearch = eventSearch;
        this.securityContext = securityContext;
    }

    @Override
    public void startup() {
        // It shouldn't be possible to create tasks during startup.
        createTasksLock.lock();
        try {
            // Anything that we owned release
            processorTaskDao.releaseOwnedTasks();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            createTasksLock.unlock();
            allowFillTaskStore = true;
        }
    }

    @Override
    public void shutdown() {
        // It shouldn't be possible to create tasks during shutdown.
        createTasksLock.lock();
        try {
            allowFillTaskStore = false;
            clearTaskStore();
//            processorTaskManagerRecentStreamDetails = null;
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            createTasksLock.unlock();
        }
    }

    /**
     * Return back the next task to do. Called by worker threads. Also assigns
     * the task to the node asking for the job
     */
    @Override
    public ProcessorTaskList assignTasks(final String nodeName, final int count) {
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserId(), "Only the processing user is allowed to assign tasks");
        }

        List<ProcessorTask> assignedStreamTasks = Collections.emptyList();

        try {
            if (processorConfig.isAssignTasks() && count > 0) {
                // Get local reference to list in case it is swapped out.
                final List<ProcessorFilter> filters = prioritisedFiltersRef.get();
                if (filters != null && filters.size() > 0) {
                    assignedStreamTasks = new ArrayList<>(count);

                    int index = 0;
                    while (assignedStreamTasks.size() < count && index < filters.size()) {
                        final ProcessorFilter filter = filters.get(index);

                        // Get the queue for this filter.
                        final StreamTaskQueue queue = queueMap.get(filter);
                        if (queue != null) {
                            // Add as many tasks as we can for this filter.
                            ProcessorTask streamTask = queue.poll();
                            while (streamTask != null) {
                                final ProcessorTask assigned = processorTaskDao.changeTaskStatus(streamTask, nodeName,
                                        TaskStatus.ASSIGNED, null, null);
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
            }

            // Have a go at kicking off a fill
            fillTaskStore();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Output some trace logging so we can see where tasks go.
        taskStatusTraceLog.assignTasks(ProcessorTaskManagerImpl.class, assignedStreamTasks, nodeName);

        return new ProcessorTaskList(nodeName, assignedStreamTasks);
    }

    @Override
    public Boolean abandonTasks(final ProcessorTaskList processorTaskList) {
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserId(), "Only the processing user is allowed to abandon tasks");
        }

        // Output some trace logging so we can see where tasks go.
        taskStatusTraceLog.abandonTasks(ProcessorTaskManagerImpl.class, processorTaskList.getList(), processorTaskList.getNodeName());

        for (final ProcessorTask processorTask : processorTaskList.getList()) {
            abandon(processorTask);
        }

        return true;
    }

    private void abandon(final ProcessorTask processorTask) {
        try {
            LOGGER.warn("abandon() - {}", processorTask);
            processorTaskDao.changeTaskStatus(processorTask, null, TaskStatus.UNPROCESSED, null, null);
        } catch (final RuntimeException e) {
            LOGGER.error("abandon() - {}", processorTask, e);
        }
    }

    private void release(final ProcessorTask streamTask) {
        try {
            LOGGER.warn("release() - {}", streamTask);
            processorTaskDao.changeTaskStatus(streamTask, null, TaskStatus.UNPROCESSED, null, null);
        } catch (final RuntimeException e) {
            LOGGER.error("release() - {}", streamTask, e);
        }
    }

//    @Override
//    public ProcessorTaskManagerRecentStreamDetails getProcessorTaskManagerRecentStreamDetails() {
////        return processorTaskManagerRecentStreamDetails;
//        return null;
//    }

    private synchronized void clearTaskStore() {
        for (final Entry<ProcessorFilter, StreamTaskQueue> entry : queueMap.entrySet()) {
            final ProcessorFilter filter = entry.getKey();
            if (filter != null) {
                final StreamTaskQueue queue = queueMap.remove(filter);
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
        for (final StreamTaskQueue queue : queueMap.values()) {
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
        if (allowFillTaskStore) {
            try {
                // Only kick off the work if are not already filling.
                if (filling.compareAndSet(false, true)) {
                    // See if it has been long enough since we last filled.
                    if (isScheduled()) {
                        LOGGER.debug("fillTaskStore() - Executing CreateStreamTasksTask");

                        final Runnable runnable = taskContextFactory.context("Fill TaskStore", taskContext ->
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
                LOGGER.error(e.getMessage(), e);
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
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("isScheduled() - Yes as time has past (queueSize={})", getTaskQueueSize());
            }
            return true;
        }

        return false;
    }

    /**
     * For use in tests and other setup tasks
     */
    @Override
    public void createTasks() {
        taskContextFactory.context("Fill TaskStore", taskContext ->
                securityContext.secure(() ->
                        createTasks(taskContext))).run();
    }

    private void createTasks(final TaskContext taskContext) {
        // We need to make sure that only 1 thread at a time is allowed to
        // create tasks. This should always be the case in production but some
        // tests will call this directly while scheduled execution could also be
        // running.
        createTasksLock.lock();
        try {
            if (allowFillTaskStore) {
                doCreateTasks(taskContext);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            createTasksLock.unlock();
        }
    }

    private void doCreateTasks(final TaskContext taskContext) {
        // We need to make sure that only 1 thread at a time is allowed to
        // create tasks. This should always be the case in production but some
        // tests will call this directly while scheduled execution could also be
        // running.
        LOGGER.debug("doCreateTasks()");

        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.debug("doCreateTasks() - Starting");

        // Get an up to date list of all enabled stream processor filters.
        LOGGER.trace("Getting enabled stream processor filters");
        final ExpressionOperator expression = new Builder()
                .addTerm(ProcessorFilterDataSource.PROCESSOR_ENABLED, Condition.EQUALS, true)
                .addTerm(ProcessorFilterDataSource.PROCESSOR_FILTER_ENABLED, Condition.EQUALS, true)
                .build();

        final ExpressionCriteria findProcessorFilterCriteria = new ExpressionCriteria(expression);
        final List<ProcessorFilter> filters = processorFilterService
                .find(findProcessorFilterCriteria).getValues();
        LOGGER.trace("Found {} stream processor filters", filters.size());

        // Sort the stream processor filters by priority.
        filters.sort(ProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR);

        // Update the stream task store.
        prioritisedFiltersRef.set(filters);

        // Now fill the stream task store with tasks for each filter.
        final int totalQueueSize = processorConfig.getQueueSize();
        final int halfQueueSize = totalQueueSize / 2;

        final String nodeName = nodeInfo.getThisNodeName();
        if (nodeName == null) {
            throw new NullPointerException("Node is null");
        }

        try {
            int remaining = totalQueueSize;
            for (final ProcessorFilter filter : filters) {
                final StreamTaskQueue queue = queueMap.computeIfAbsent(filter, k -> new StreamTaskQueue());
                final int queueSize = queue.size();

                // Reduce the number of tasks we need to get by the size of
                // the
                // current queue.
                remaining -= queueSize;

                // Now go and fill this queue asynchronously.
                if (remaining > 0 && queueSize < halfQueueSize) {
                    if (queue.compareAndSetFilling(false, true)) {
                        // Create tasks for this filter.
                        createTasksForFilter(taskContext, nodeName, filter, queue, totalQueueSize);
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Release items from the queue that no longer have an enabled filter
        final Set<ProcessorFilter> enabledFilterSet = new HashSet<>(filters);
        for (final ProcessorFilter filter : queueMap.keySet()) {
            if (!enabledFilterSet.contains(filter)) {
                final StreamTaskQueue queue = queueMap.remove(filter);
                if (queue != null) {
                    ProcessorTask streamTask = queue.poll();
                    while (streamTask != null) {
                        release(streamTask);
                        streamTask = queue.poll();
                    }
                }
            }
        }

        // We must be the master node so set a time in the future to run a
        // delete
        scheduleDelete();

//        // Set the last stream details for the next call to this method.
//        processorTaskManagerRecentStreamDetails = recentStreamInfo;

        LOGGER.debug("doCreateTasks() - Finished in {}", logExecutionTime);
    }

    private void createTasksForFilter(final TaskContext taskContext,
                                      final String nodeName,
                                      final ProcessorFilter filter,
                                      final StreamTaskQueue queue,
                                      final int maxQueueSize) {
        Optional<ProcessorFilter> optionalProcessorFilter = Optional.empty();

        final AtomicBoolean searching = new AtomicBoolean();
        try {
            // Reload as it could have changed
            optionalProcessorFilter = processorFilterService.fetch(filter.getId());

            // The filter might have been deleted since we found it.
            optionalProcessorFilter.ifPresent(loadedFilter -> {
                // Set the current user to be the one who created the filter so that only streams that that user has access to are processed.
                securityContext.asUser(securityContext.createIdentity(loadedFilter.getCreateUser()), () -> {
                    LOGGER.debug("createTasksForFilter() - processorFilter {}", loadedFilter.toString());

                    // Only try and create tasks if the processor is enabled.
                    if (loadedFilter.isEnabled() && loadedFilter.getProcessor().isEnabled()) {
                        int tasksToCreate = maxQueueSize - queue.size();
                        int count = 0;

                        // If there are any tasks for this filter that were
                        // previously created but are unprocessed, not owned by any
                        // node and their associated stream is unlocked then add
                        // them here.
                        if (processorConfig.isFillTaskQueue()) {
                            count = addUnownedTasks(taskContext, nodeName, loadedFilter, queue, tasksToCreate);
                        }

                        // If we allowing tasks to be created then go ahead and
                        // create some.
                        if (processorConfig.isCreateTasks()) {
                            tasksToCreate -= count;
//                            final String logPrefix = "Creating tasks with filter " + loadedFilter.getId();

                            final Boolean exhausted = exhaustedFilterMap.computeIfAbsent(loadedFilter.getId(), k -> Boolean.FALSE);

                            // Skip once we have done all that is required
                            final int requiredTasks = tasksToCreate;
                            if (requiredTasks > 0 && !Thread.currentThread().isInterrupted()) {
                                final QueryData queryData = loadedFilter.getQueryData();
                                final boolean isStreamStoreSearch = queryData.getDataSource() != null && queryData.getDataSource().getType().equals(MetaFields.STREAM_STORE_TYPE);

                                // Record the time before we are going to query for
                                // streams for tracking purposes.
                                final long streamQueryTime = System.currentTimeMillis();

                                // Get the tracker for this filter.
                                ProcessorFilterTracker tracker = loadedFilter.getProcessorFilterTracker();

                                // Set the latest stream ms time that this filter
                                // will be applicable for. This might always be null
                                // if the filter will be used indefinitely.
                                if (tracker.getMaxMetaCreateMs() == null) {
//                                    final long maxStreamId = recentStreamInfo.getMaxStreamId();
                                    Long streamCreateMaxMs = null;
//
//                                    // If the criteria has a stream id set with the
//                                    // greatest id that is less than the current max
//                                    // stream id then we can bound the max stream
//                                    // create time.
//                                    if (findStreamCriteria.getMetaIdSet() != null) {
//                                        final Long maxId = findStreamCriteria.getMetaIdSet().getMaxId();
//                                        if (maxId != null && maxId.longValue() < maxStreamId) {
//                                            streamCreateMaxMs = min(streamCreateMaxMs, streamQueryTime);
//                                        }
//                                    }
//
//                                    // If the criteria has a stream id range with an
//                                    // upper limit on stream id that is less than
//                                    // the current max stream id then we can bound
//                                    // the max stream create time.
//                                    if (findStreamCriteria.getStreamIdRange() != null) {
//                                        if (findStreamCriteria.getStreamIdRange().getTo() != null
//                                                && findStreamCriteria.getStreamIdRange().getTo().longValue() < maxStreamId) {
//                                            streamCreateMaxMs = min(streamCreateMaxMs, streamQueryTime);
//                                        }
//                                    }
//
//                                    // If the criteria has a stream creation period
//                                    // then determine the maximum stream creation
//                                    // time from this period.
//                                    if (findStreamCriteria.getCreatePeriod() != null && findStreamCriteria.getCreatePeriod().getTo() != null) {
//                                        streamCreateMaxMs = min(streamCreateMaxMs, findStreamCriteria.getCreatePeriod().getTo());
//                                    }
//
//                                    // For the time being we will get task
//                                    // production for queries to end with the latest
//                                    // stream that existed the first time this is
//                                    // called.
//                                    if (!isStreamStoreSearch) {
//                                        streamCreateMaxMs = min(streamCreateMaxMs, streamQueryTime);
//                                    }

                                    tracker.setMaxMetaCreateMs(streamCreateMaxMs);
                                }

                                // Here we do an optimisation and only bother
                                // processing anything that we have had recent
                                // stream data for if we were exhausted last time
                                LOGGER.debug("createTasks() - Filter {} exhausted = {}", loadedFilter.getId(), exhausted);
//                                if (!exhausted || recentStreamInfo.isApplicable(loadedFilter, findStreamCriteria)) {
                                if (ProcessorFilterTracker.COMPLETE.equals(tracker.getStatus())) {
                                    // If the tracker is complete we need to
                                    // make sure the status is updated so we can
                                    // see that it is not delivering any more
                                    // tasks.
                                    if (tracker.getLastPollTaskCount() != null && tracker.getLastPollTaskCount() > 0) {
                                        tracker.setLastPollMs(streamQueryTime);
                                        tracker.setLastPollTaskCount(0);
                                        tracker = processorFilterTrackerDao.update(tracker);
                                    }

                                } else if (!isStreamStoreSearch) {
                                    // Create stream tasks by executing a
                                    // search.
                                    searching.set(true);
                                    createTasksFromSearchQuery(loadedFilter,
                                            queryData,
                                            streamQueryTime,
                                            nodeName,
                                            requiredTasks,
                                            queue,
                                            tracker);

                                } else {
                                    // Create tasks from a standard stream
                                    // filter criteria.
                                    createTasksFromCriteria(loadedFilter, queryData, streamQueryTime, nodeName, requiredTasks, queue, tracker);
                                }
//                                }
                            }
                        } else {
                            // We terminated early so assume this filter is not
                            // exhausted
                            LOGGER.debug("createTasks() - Filter {} no tasks needed at this time - assuming not exhausted",
                                    loadedFilter.getId());
                            exhaustedFilterMap.put(loadedFilter.getId(), Boolean.FALSE);
                        }
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
            LOGGER.error(e::getMessage, e);
        } finally {
            if (!searching.get()) {
                queue.setFilling(false);
            }
        }
    }

    private int addUnownedTasks(final TaskContext taskContext, final String nodeName, final ProcessorFilter filter,
                                final StreamTaskQueue queue, final int tasksToCreate) {
        int count = 0;

        try {
            // First look for any items that are no-longer locked etc
            final ExpressionOperator findProcessorTaskExpression = new ExpressionOperator.Builder()
                    .addTerm(ProcessorTaskDataSource.STATUS, Condition.EQUALS, TaskStatus.UNPROCESSED.getDisplayValue())
                    .addTerm(ProcessorTaskDataSource.NODE_NAME, Condition.IS_NULL, null)
                    .addTerm(ProcessorTaskDataSource.PROCESSOR_FILTER_ID, Condition.EQUALS, filter.getId())
                    .build();
            final ExpressionCriteria findProcessorTaskCriteria = new ExpressionCriteria(findProcessorTaskExpression);
//            findProcessorTaskCriteria.obtainTaskStatusSet().add(TaskStatus.UNPROCESSED);
//            findProcessorTaskCriteria.obtainNodeNameCriteria().setMatchNull(true);
//            findProcessorTaskCriteria.obtainProcessorFilterIdSet().add(filter.getId());
            findProcessorTaskCriteria.obtainPageRequest().setLength(tasksToCreate);

            final List<ProcessorTask> processorTasks = processorTaskDao.find(findProcessorTaskCriteria).getValues();
            final int size = processorTasks.size();

            taskStatusTraceLog.addUnownedTasks(ProcessorTaskManagerImpl.class, processorTasks);

            if (processorTasks.size() > 0) {
                // Find unlocked meta data corresponding to this list of unowned tasks.
                final ExpressionOperator.Builder metaIdExpressionBuilder = new ExpressionOperator.Builder(Op.OR);
                processorTasks.forEach(task -> metaIdExpressionBuilder.addTerm(MetaFields.ID, Condition.EQUALS, task.getMetaId()));

                final ExpressionOperator findMetaExpression = new ExpressionOperator.Builder(Op.AND)
                        .addOperator(metaIdExpressionBuilder.build())
                        .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                        .build();

                final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(findMetaExpression);
                findMetaCriteria.setSort(MetaFields.ID.getName(), Direction.ASCENDING, false);
                final List<Meta> metaList = metaService.find(findMetaCriteria).getValues();

                if (metaList.size() > 0) {
                    // Change the ownership of tasks where we have unlocked meta data.
                    final Map<Long, ProcessorTask> metaIdToTaskMap = processorTasks.stream()
                            .collect(Collectors.toMap(ProcessorTask::getMetaId, task -> task));

                    for (final Meta meta : metaList) {
                        final ProcessorTask processorTask = metaIdToTaskMap.get(meta.getId());

                        try {
                            final ProcessorTask modified = processorTaskDao.changeTaskStatus(processorTask, nodeName,
                                    TaskStatus.UNPROCESSED, null, null);
                            if (modified != null) {
                                queue.add(modified);
                                count++;
                                taskContext.info(LambdaLogUtil.message("Adding {}/{} non owned Tasks", count, size));
                            }

                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error("doCreateTasks() - Failed to grab non owned task {}", processorTask, e);
                        }
                    }
                }
            }

            if (count > 0) {
                LOGGER.debug("doCreateTasks() - Added {} tasks that are no longer locked", count);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return count;
    }

    private void createTasksFromSearchQuery(final ProcessorFilter filter,
                                            final QueryData queryData,
                                            final long streamQueryTime,
                                            final String nodeName,
                                            final int requiredTasks,
                                            final StreamTaskQueue queue,
                                            final ProcessorFilterTracker tracker) {
        final EventRef minEvent = new EventRef(tracker.getMinMetaId(), tracker.getMinEventId());
        final EventRef maxEvent = new EventRef(Long.MAX_VALUE, 0L);
        long maxStreams = requiredTasks;
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

        final Query query = new Query(queryData.getDataSource(), queryData.getExpression());

        // Update the tracker status message.
        tracker.setStatus("Searching...");
        final ProcessorFilterTracker updatedTracker = processorFilterTrackerDao.update(tracker);

        final Long maxMetaId = metaService.getMaxId();
        eventSearch.search(
                query,
                minEvent,
                maxEvent,
                maxStreams,
                maxEvents,
                maxEventsPerStream,
                POLL_INTERVAL_MS,
                eventRefs -> createTasksFromEventRefs(filter, streamQueryTime, nodeName, query, requiredTasks, queue, maxMetaId, updatedTracker, eventRefs));
    }

    private void createTasksFromEventRefs(final ProcessorFilter filter,
                                          final long streamQueryTime,
                                          final String nodeName,
                                          final Query query,
                                          final int requiredTasks,
                                          final StreamTaskQueue queue,
                                          final Long maxMetaId,
                                          final ProcessorFilterTracker updatedTracker,
                                          final EventRefs eventRefs) {
        if (eventRefs == null) {
            queue.setFilling(false);
        } else {

            int resultSize;
            boolean reachedLimit;
            resultSize = eventRefs.size();
            reachedLimit = eventRefs.isReachedLimit();
            final boolean exhausted = resultSize == 0 || reachedLimit;

            // Update the tracker status message.
            ProcessorFilterTracker tracker = updatedTracker;
            tracker.setStatus("Creating...");
            tracker = processorFilterTrackerDao.update(tracker);

            // Create a task for each stream reference.
            final Map<Meta, InclusiveRanges> map = createStreamMap(eventRefs);
            processorTaskDao.createNewTasks(
                    filter,
                    tracker,
                    streamQueryTime,
                    map,
                    nodeName,
                    maxMetaId,
                    reachedLimit,
                    createdTasks -> {
                        // Transfer the newly created (and available) tasks to the
                        // queue.
                        createdTasks.getAvailableTaskList().forEach(queue::add);
                        LOGGER.debug("createTasks() - Created {} tasks (tasksToCreate={}) for filter {}", createdTasks.getTotalTasksCreated(), requiredTasks, filter.toString());

                        exhaustedFilterMap.put(filter.getId(), exhausted);

                        queue.setFilling(false);
                    });
        }
    }

    private void createTasksFromCriteria(final ProcessorFilter filter,
                                         final QueryData queryData,
                                         final long streamQueryTime,
                                         final String nodeName,
                                         final int requiredTasks,
                                         final StreamTaskQueue queue,
                                         final ProcessorFilterTracker tracker) {
        if (termCount(queryData) == 0) {
            throw new RuntimeException("Attempting to create tasks with an unconstrained filter " + filter);
        }

        // Update the tracker status message.
        tracker.setStatus("Creating...");
        final ProcessorFilterTracker updatedTracker = processorFilterTrackerDao.update(tracker);

        // This will contain locked and unlocked streams
        final Long maxMetaId = metaService.getMaxId();
        final List<Meta> streamList = runSelectMetaQuery(
                queryData.getExpression(),
                updatedTracker.getMinMetaId(),
                requiredTasks);

        // Just create regular stream processing tasks.
        final Map<Meta, InclusiveRanges> map = new HashMap<>();
        for (final Meta meta : streamList) {
            map.put(meta, null);
        }

        processorTaskDao.createNewTasks(
                filter,
                updatedTracker,
                streamQueryTime,
                map,
                nodeName,
                maxMetaId,
                false,
                createdTasks -> {
                    // Transfer the newly created (and available) tasks to the queue.
                    createdTasks.getAvailableTaskList().forEach(queue::add);
                    LOGGER.debug("createTasks() - Created {} tasks (tasksToCreate={}) for filter {}", createdTasks.getTotalTasksCreated(), requiredTasks, filter.toString());
                    exhaustedFilterMap.put(filter.getId(), createdTasks.getTotalTasksCreated() == 0);
                });
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
                    // When the stream id changes add the current ranges to the
                    // map.
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

                    ranges.addEvent(ref.getEventId());
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
                                  final long minStreamId,
                                  final int max) {
        // Don't select deleted streams.
        final ExpressionOperator statusExpression = new ExpressionOperator.Builder(Op.OR)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.LOCKED.getDisplayValue())
                .build();

        final ExpressionOperator streamIdExpression = new ExpressionOperator.Builder(Op.AND)
                .addOperator(expression)
                .addTerm(MetaFields.ID, Condition.GREATER_THAN_OR_EQUAL_TO, minStreamId)
                .addOperator(statusExpression)
                .build();

        // Copy the filter
        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(streamIdExpression);
//        findStreamCriteria.copyFrom(criteria);
        findMetaCriteria.setSort(MetaFields.ID.getName(), Direction.ASCENDING, false);
//        findStreamCriteria.setStreamIdRange(new IdRange(minStreamId, null));
//        // Don't care about status
//        findStreamCriteria.obtainStatusSet().add(StreamStatus.LOCKED);
//        findStreamCriteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
        findMetaCriteria.obtainPageRequest().setLength(max);

        return metaService.find(findMetaCriteria).getValues();
    }

//    private Long min(final Long l1, final Long l2) {
//        if (l1 == null) {
//            return l2;
//        }
//        if (l2 == null) {
//            return l1;
//        }
//        if (l1 > l2) {
//            return l2;
//        } else {
//            return l1;
//        }
//    }

    /**
     * Schedule a delete if we don't have one
     */
    private void scheduleDelete() {
        if (nextDeleteMs.get() == 0) {
            nextDeleteMs.set(System.currentTimeMillis() + DELETE_INTERVAL_MS);
            LOGGER.debug("scheduleDelete() - nextDeleteMs={}", DateUtil.createNormalDateTimeString(nextDeleteMs.get()));
        }
    }

    @Override
    public void writeQueueStatistics() {
        try {
            // Avoid writing loads of same value stats So write every min while
            // it changes Under little load the queue size will be 0
            final int queueSize = getTaskQueueSize();
            if (queueSize != lastQueueSizeForStats) {
                try {
                    final InternalStatisticsReceiver internalStatisticsReceiver = internalStatisticsReceiverProvider.get();
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
    public AtomicLong getNextDeleteMs() {
        return nextDeleteMs;
    }
}
