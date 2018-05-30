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

package stroom.streamtask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseResultList;
import stroom.jobsystem.JobTrackedSchedule;
import stroom.node.NodeCache;
import stroom.node.shared.Node;
import stroom.properties.StroomPropertyService;
import stroom.query.api.v2.Query;
import stroom.search.EventRef;
import stroom.search.EventRefs;
import stroom.search.EventSearchTask;
import stroom.security.Security;
import stroom.security.UserTokenUtil;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.streamstore.ExpressionToFindCriteria;
import stroom.streamstore.ExpressionToFindCriteria.Context;
import stroom.streamstore.OldFindStreamCriteria;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.shared.Limits;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamtask.StreamTaskCreatorTransactionHelper.CreatedTasks;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.streamtask.shared.StreamProcessorFilterTracker;
import stroom.streamtask.shared.StreamTask;
import stroom.streamtask.shared.TaskStatus;
import stroom.task.TaskCallbackAdaptor;
import stroom.task.TaskContext;
import stroom.task.TaskManager;
import stroom.util.date.DateUtil;
import stroom.util.lifecycle.StroomFrequencySchedule;
import stroom.util.lifecycle.StroomShutdown;
import stroom.util.lifecycle.StroomStartup;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keep a pool of stream tasks ready to go.
 * <p>
 * Fill up our pool if we are below our low water mark (FILL_LOW_SIZE).
 */
@Singleton
public class StreamTaskCreatorImpl implements StreamTaskCreator {
    public static final String STREAM_TASKS_FILL_TASK_QUEUE_PROPERTY = "stroom.streamTask.fillTaskQueue";
    public static final String STREAM_TASKS_CREATE_TASKS_PROPERTY = "stroom.streamTask.createTasks";
    public static final String STREAM_TASKS_ASSIGN_TASKS_PROPERTY = "stroom.streamTask.assignTasks";
    public static final String STREAM_TASKS_QUEUE_SIZE_PROPERTY = "stroom.streamTask.queueSize";
    public static final int POLL_INTERVAL_MS = 10000;
    public static final int DELETE_INTERVAL_MS = POLL_INTERVAL_MS * 10;
    public static final int MAX_DELETE_STREAM_RANGE = 10000;
    public static final int MAX_DELETE_COUNT = 50;

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamTaskCreatorImpl.class);

    private static final String INTERNAL_STAT_KEY_STREAM_TASK_QUEUE_SIZE = "streamTaskQueueSize";

    private final StreamProcessorFilterService streamProcessorFilterService;
    private final StreamTaskCreatorTransactionHelper streamTaskTransactionHelper;
    private final TaskManager taskManager;
    private final NodeCache nodeCache;
    private final StreamTaskService streamTaskService;
    private final StreamTaskHelper streamTaskHelper;
    private final StroomPropertyService propertyService;
    private final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider;
    private final StreamStore streamStore;
    private final Security security;
    private final ExpressionToFindCriteria expressionToFindCriteria;

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();

    private final ReentrantLock createTasksLock = new ReentrantLock();

    /**
     * Our filter cache
     */
    private final AtomicReference<List<StreamProcessorFilter>> prioritisedFiltersRef = new AtomicReference<>();

    /**
     * Our queue.
     */
    private final ConcurrentHashMap<StreamProcessorFilter, StreamTaskQueue> queueMap = new ConcurrentHashMap<>();

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
    private final ConcurrentHashMap<Long, Boolean> exhaustedFilterMap = new ConcurrentHashMap<>();
    private volatile StreamTaskCreatorRecentStreamDetails streamTaskCreatorRecentStreamDetails;
    private volatile int totalQueueSize = 1000;
    private volatile int lastQueueSizeForStats = -1;

    /**
     * Make sure the task store isn't allowed to be filled until this node has
     * run startup() and has not run shutdown().
     */
    private volatile boolean allowFillTaskStore = false;

    @Inject
    StreamTaskCreatorImpl(final StreamProcessorFilterService streamProcessorFilterService,
                          final StreamTaskCreatorTransactionHelper streamTaskTransactionHelper,
                          final TaskManager taskManager,
                          final NodeCache nodeCache,
                          final StreamTaskService streamTaskService,
                          final StreamTaskHelper streamTaskHelper,
                          final StroomPropertyService propertyService,
                          final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider,
                          final StreamStore streamStore,
                          final Security security,
                          final ExpressionToFindCriteria expressionToFindCriteria) {

        this.streamProcessorFilterService = streamProcessorFilterService;
        this.streamTaskTransactionHelper = streamTaskTransactionHelper;
        this.taskManager = taskManager;
        this.nodeCache = nodeCache;
        this.streamTaskService = streamTaskService;
        this.streamTaskHelper = streamTaskHelper;
        this.propertyService = propertyService;
        this.internalStatisticsReceiverProvider = internalStatisticsReceiverProvider;
        this.streamStore = streamStore;
        this.security = security;
        this.expressionToFindCriteria = expressionToFindCriteria;
    }

    @StroomStartup
    @Override
    public void startup() {
        // It shouldn't be possible to create tasks during startup.
        createTasksLock.lock();
        try {
            // Anything that we owned release
            streamTaskTransactionHelper.releaseOwnedTasks();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            createTasksLock.unlock();
            allowFillTaskStore = true;
        }
    }

    @StroomShutdown
    @Override
    public void shutdown() {
        // It shouldn't be possible to create tasks during shutdown.
        createTasksLock.lock();
        try {
            allowFillTaskStore = false;
            clearTaskStore();
            streamTaskCreatorRecentStreamDetails = null;
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
    public List<StreamTask> assignStreamTasks(final Node node, final int count) {
        List<StreamTask> assignedStreamTasks = Collections.emptyList();

        try {
            if (isAssignTasksEnabled() && count > 0) {
                // Get local reference to list in case it is swapped out.
                final List<StreamProcessorFilter> filters = prioritisedFiltersRef.get();
                if (filters != null && filters.size() > 0) {
                    assignedStreamTasks = new ArrayList<>(count);

                    int index = 0;
                    while (assignedStreamTasks.size() < count && index < filters.size()) {
                        final StreamProcessorFilter filter = filters.get(index);

                        // Get the queue for this filter.
                        final StreamTaskQueue queue = queueMap.get(filter);
                        if (queue != null) {
                            // Add as many tasks as we can for this filter.
                            StreamTask streamTask = queue.poll();
                            while (streamTask != null) {
                                final StreamTask assigned = streamTaskHelper.changeTaskStatus(streamTask, node,
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
        taskStatusTraceLog.assignTasks(StreamTaskCreatorImpl.class, assignedStreamTasks, node);

        return assignedStreamTasks;
    }

    @Override
    public void abandonStreamTasks(final Node node, final List<StreamTask> tasks) {
        // Output some trace logging so we can see where tasks go.
        taskStatusTraceLog.abandonTasks(StreamTaskCreatorImpl.class, tasks, node);

        for (final StreamTask streamTask : tasks) {
            abandon(streamTask);
        }
    }

    private void abandon(final StreamTask streamTask) {
        try {
            LOGGER.warn("abandon() - {}", streamTask);
            streamTaskHelper.changeTaskStatus(streamTask, null, TaskStatus.UNPROCESSED, null, null);
        } catch (final RuntimeException e) {
            LOGGER.error("abandon() - {}", streamTask, e);
        }
    }

    private void release(final StreamTask streamTask) {
        try {
            LOGGER.warn("release() - {}", streamTask);
            streamTaskHelper.changeTaskStatus(streamTask, null, TaskStatus.UNPROCESSED, null, null);
        } catch (final RuntimeException e) {
            LOGGER.error("release() - {}", streamTask, e);
        }
    }

    @Override
    public StreamTaskCreatorRecentStreamDetails getStreamTaskCreatorRecentStreamDetails() {
        return streamTaskCreatorRecentStreamDetails;
    }

    private synchronized void clearTaskStore() {
        for (final Entry<StreamProcessorFilter, StreamTaskQueue> entry : queueMap.entrySet()) {
            final StreamProcessorFilter filter = entry.getKey();
            if (filter != null) {
                final StreamTaskQueue queue = queueMap.remove(filter);
                if (queue != null) {
                    StreamTask streamTask = queue.poll();
                    while (streamTask != null) {
                        streamTask = queue.poll();
                    }
                }
            }
        }
    }

    @Override
    public int getStreamTaskQueueSize() {
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
                        taskManager.execAsync(new CreateStreamTasksTask(), new TaskCallbackAdaptor<VoidResult>() {
                            @Override
                            public void onSuccess(final VoidResult result) {
                                scheduleNextPollMs();
                                filling.set(false);
                            }

                            @Override
                            public void onFailure(final Throwable t) {
                                filling.set(false);
                            }
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
                LOGGER.trace("isScheduled() - Yes as time has past (queueSize={})", getStreamTaskQueueSize());
            }
            return true;
        }

        return false;
    }

    /**
     * Task call back
     */
    @Override
    public void createTasks(final TaskContext taskContext) {
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

        try {
            final Integer newTotalQueueSize = ModelStringUtil
                    .parseNumberStringAsInt(propertyService.getProperty(STREAM_TASKS_QUEUE_SIZE_PROPERTY));
            if (newTotalQueueSize != null) {
                totalQueueSize = newTotalQueueSize;
            }
        } catch (final NumberFormatException e) {
            LOGGER.error("doCreateTasks() - error reading {}", STREAM_TASKS_QUEUE_SIZE_PROPERTY, e);
        }

        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.debug("doCreateTasks() - Starting");

        // Get an up to date list of all enabled stream processor filters.
        LOGGER.trace("Getting enabled stream processor filters");
        final FindStreamProcessorFilterCriteria findStreamProcessorFilterCriteria = new FindStreamProcessorFilterCriteria();
        findStreamProcessorFilterCriteria.setStreamProcessorEnabled(true);
        findStreamProcessorFilterCriteria.setStreamProcessorFilterEnabled(true);
        final List<StreamProcessorFilter> filters = streamProcessorFilterService
                .find(findStreamProcessorFilterCriteria);
        LOGGER.trace("Found {} stream processor filters", filters.size());

        // Sort the stream processor filters by priority.
        Collections.sort(filters, StreamProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR);

        // Update the stream task store.
        prioritisedFiltersRef.set(filters);

        // Get information about streams that have arrived since we last tried
        // to create tasks.
        final StreamTaskCreatorRecentStreamDetails recentStreamInfo = streamTaskTransactionHelper
                .getRecentStreamInfo(streamTaskCreatorRecentStreamDetails);

        // Now fill the stream task store with tasks for each filter.
        final int halfQueueSize = totalQueueSize / 2;

        final Node node = nodeCache.getDefaultNode();
        if (node == null) {
            throw new NullPointerException("Node is null");
        }

        try {
            int remaining = totalQueueSize;
            for (final StreamProcessorFilter filter : filters) {
                StreamTaskQueue queue = queueMap.get(filter);
                if (queue == null) {
                    queueMap.putIfAbsent(filter, new StreamTaskQueue());
                    queue = queueMap.get(filter);
                }

                if (queue != null) {
                    final int queueSize = queue.size();

                    // Reduce the number of tasks we need to get by the size of
                    // the
                    // current queue.
                    remaining -= queueSize;

                    // Now go and fill this queue asynchronously.
                    if (remaining > 0 && queueSize < halfQueueSize) {
                        if (queue.compareAndSetFilling(false, true)) {
                            // Create tasks for this filter.
                            createTasksForFilter(taskContext, node, filter, queue, totalQueueSize, recentStreamInfo);
                        }
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        // Release items from the queue that no longer have an enabled filter
        final Set<StreamProcessorFilter> enabledFilterSet = new HashSet<>(filters);
        for (final StreamProcessorFilter filter : queueMap.keySet()) {
            if (!enabledFilterSet.contains(filter)) {
                final StreamTaskQueue queue = queueMap.remove(filter);
                if (queue != null) {
                    StreamTask streamTask = queue.poll();
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

        // Set the last stream details for the next call to this method.
        streamTaskCreatorRecentStreamDetails = recentStreamInfo;

        LOGGER.debug("doCreateTasks() - Finished in {}", logExecutionTime);
    }

    private void createTasksForFilter(final TaskContext taskContext, final Node node,
                                      final StreamProcessorFilter filter, final StreamTaskQueue queue, final int maxQueueSize,
                                      final StreamTaskCreatorRecentStreamDetails recentStreamInfo) {
        final AtomicBoolean searching = new AtomicBoolean();
        try {
            // Reload as it could have changed
            final StreamProcessorFilter loadedFilter = streamProcessorFilterService.load(filter,
                    Collections.singleton(StreamProcessor.ENTITY_TYPE));

            // The filter might have been deleted since we found it.
            if (loadedFilter != null) {

                // Set the current user to be the one who created the filter so that only streams that that user has access to are processed.
                security.asUser(UserTokenUtil.create(loadedFilter.getCreateUser(), null), () -> {
                    LOGGER.debug("createTasksForFilter() - streamProcessorFilter {}", loadedFilter.toString());

                    // Only try and create tasks if the processor is enabled.
                    if (loadedFilter.isEnabled() && loadedFilter.getStreamProcessor().isEnabled()) {
                        int tasksToCreate = maxQueueSize - queue.size();
                        int count = 0;

                        // If there are any tasks for this filter that were
                        // previously created but are unprocessed, not owned by any
                        // node and their associated stream is unlocked then add
                        // them here.
                        if (isFillTaskQueueEnabled()) {
                            count = addUnownedTasks(taskContext, node, loadedFilter, queue, tasksToCreate);
                        }

                        // If we allowing tasks to be created then go ahead and
                        // create some.
                        if (isCreateTasksEnabled()) {
                            tasksToCreate -= count;
                            final String logPrefix = "Creating tasks with filter " + loadedFilter.getId();

                            Boolean exhausted = exhaustedFilterMap.get(loadedFilter.getId());
                            if (exhausted == null) {
                                exhaustedFilterMap.putIfAbsent(loadedFilter.getId(), Boolean.FALSE);
                                exhausted = exhaustedFilterMap.get(loadedFilter.getId());
                            }

                            // Skip once we have done all that is required
                            final int requiredTasks = tasksToCreate;
                            if (requiredTasks > 0 && !Thread.currentThread().isInterrupted()) {
                                final QueryData queryData = loadedFilter.getQueryData();

                                final Context context = new Context(null, System.currentTimeMillis());
                                final OldFindStreamCriteria findStreamCriteria = expressionToFindCriteria.convert(queryData, context);
                                boolean isStreamStoreSearch = (queryData.getDataSource() != null) && queryData.getDataSource().getType().equals(StreamDataSource.STREAM_STORE_TYPE);

                                // Record the time before we are going to query for
                                // streams for tracking purposes.
                                final long streamQueryTime = System.currentTimeMillis();

                                // Get the tracker for this filter.
                                StreamProcessorFilterTracker tracker = loadedFilter.getStreamProcessorFilterTracker();

                                // Set the latest stream ms time that this filter
                                // will be applicable for. This might always be null
                                // if the filter will be used indefinitely.
                                if (tracker.getMaxStreamCreateMs() == null) {
                                    final long maxStreamId = recentStreamInfo.getMaxStreamId();
                                    Long streamCreateMaxMs = null;

                                    // If the criteria has a stream id set with the
                                    // greatest id that is less than the current max
                                    // stream id then we can bound the max stream
                                    // create time.
                                    if (findStreamCriteria.getStreamIdSet() != null) {
                                        final Long maxId = findStreamCriteria.getStreamIdSet().getMaxId();
                                        if (maxId != null && maxId.longValue() < maxStreamId) {
                                            streamCreateMaxMs = min(streamCreateMaxMs, streamQueryTime);
                                        }
                                    }

                                    // If the criteria has a stream id range with an
                                    // upper limit on stream id that is less than
                                    // the current max stream id then we can bound
                                    // the max stream create time.
                                    if (findStreamCriteria.getStreamIdRange() != null) {
                                        if (findStreamCriteria.getStreamIdRange().getTo() != null
                                                && findStreamCriteria.getStreamIdRange().getTo().longValue() < maxStreamId) {
                                            streamCreateMaxMs = min(streamCreateMaxMs, streamQueryTime);
                                        }
                                    }

                                    // If the criteria has a stream creation period
                                    // then determine the maximum stream creation
                                    // time from this period.
                                    if (findStreamCriteria.getCreatePeriod() != null && findStreamCriteria.getCreatePeriod().getTo() != null) {
                                        streamCreateMaxMs = min(streamCreateMaxMs, findStreamCriteria.getCreatePeriod().getTo());
                                    }

                                    // For the time being we will get task
                                    // production for queries to end with the latest
                                    // stream that existed the first time this is
                                    // called.
                                    if (!isStreamStoreSearch) {
                                        streamCreateMaxMs = min(streamCreateMaxMs, streamQueryTime);
                                    }

                                    tracker.setMaxStreamCreateMs(streamCreateMaxMs);
                                }

                                // Here we do an optimisation and only bother
                                // processing anything that we have had recent
                                // stream data for if we were exhausted last time
                                LOGGER.debug("createTasks() - Filter {} exhausted = {}", loadedFilter.getId(), exhausted);
                                if (!exhausted || recentStreamInfo.isApplicable(loadedFilter, findStreamCriteria)) {
                                    if (StreamProcessorFilterTracker.COMPLETE.equals(tracker.getStatus())) {
                                        // If the tracker is complete we need to
                                        // make sure the status is updated so we can
                                        // see that it is not delivering any more
                                        // tasks.
                                        if (tracker.getLastPollTaskCount() != null && tracker.getLastPollTaskCount() > 0) {
                                            tracker.setLastPollMs(streamQueryTime);
                                            tracker.setLastPollTaskCount(0);
                                            tracker = streamTaskTransactionHelper.saveTracker(tracker);
                                        }

                                    } else if (!isStreamStoreSearch) {
                                        // Create stream tasks by executing a
                                        // search.
                                        searching.set(true);
                                        createTasksFromSearchQuery(loadedFilter,
                                                queryData,
                                                streamQueryTime,
                                                node,
                                                requiredTasks,
                                                queue,
                                                recentStreamInfo,
                                                tracker);

                                    } else {
                                        // Create tasks from a standard stream
                                        // filter criteria.
                                        createTasksFromCriteria(loadedFilter, findStreamCriteria, logPrefix,
                                                streamQueryTime, node, requiredTasks, queue, recentStreamInfo, tracker);
                                    }
                                }
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
            }
        } catch (final RuntimeException e) {
            LOGGER.error("Error processing filter with id = " + filter.getId());
            LOGGER.error(e.getMessage(), e);
        } finally {
            if (!searching.get()) {
                queue.setFilling(false);
            }
        }
    }

    private int addUnownedTasks(final TaskContext taskContext, final Node node, final StreamProcessorFilter filter,
                                final StreamTaskQueue queue, final int tasksToCreate) {
        int count = 0;

        try {
            // First look for any items that are no-longer locked etc
            final FindStreamTaskCriteria findStreamTaskCriteria = new FindStreamTaskCriteria();
            findStreamTaskCriteria.obtainStreamTaskStatusSet().add(TaskStatus.UNPROCESSED);
            findStreamTaskCriteria.obtainNodeIdSet().setMatchNull(true);
            findStreamTaskCriteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
            findStreamTaskCriteria.obtainStreamProcessorFilterIdSet().add(filter.getId());
            findStreamTaskCriteria.obtainPageRequest().setLength(tasksToCreate);

            final BaseResultList<StreamTask> streamTasks = streamTaskService.find(findStreamTaskCriteria);
            final int size = streamTasks.size();

            taskStatusTraceLog.addUnownedTasks(StreamTaskCreatorImpl.class, streamTasks);

            for (final StreamTask streamTask : streamTasks) {
                try {
                    final StreamTask modified = streamTaskHelper.changeTaskStatus(streamTask, node,
                            TaskStatus.UNPROCESSED, null, null);
                    if (modified != null) {
                        queue.add(modified);
                        count++;
                        taskContext.info("Adding {}/{} non owned Tasks", count, size);
                    }

                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error("doCreateTasks() - Failed to grab non owned task {}", streamTask, e);
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

    private void createTasksFromSearchQuery(final StreamProcessorFilter filter,
                                            final QueryData queryData,
                                            final long streamQueryTime,
                                            final Node node, final int requiredTasks, final StreamTaskQueue queue,
                                            final StreamTaskCreatorRecentStreamDetails recentStreamInfo, final StreamProcessorFilterTracker tracker) {
        final EventRef minEvent = new EventRef(tracker.getMinStreamId(), tracker.getMinEventId());
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
                final long start = filter.getCreateTime();
                final long end = start + limits.getDurationMs();
                if (end < System.currentTimeMillis()) {
                    tracker.setStatus(StreamProcessorFilterTracker.COMPLETE);
                    streamTaskTransactionHelper.saveTracker(tracker);
                    return;
                }
            }

            if (limits.getStreamCount() != null) {
                long streamLimit = limits.getStreamCount();
                if (tracker.getStreamCount() != null) {
                    streamLimit -= tracker.getStreamCount();
                }

                maxStreams = Math.min(streamLimit, maxStreams);

                if (maxStreams <= 0) {
                    tracker.setStatus(StreamProcessorFilterTracker.COMPLETE);
                    streamTaskTransactionHelper.saveTracker(tracker);
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
                    tracker.setStatus(StreamProcessorFilterTracker.COMPLETE);
                    streamTaskTransactionHelper.saveTracker(tracker);
                    return;
                }
            }
        }

        final Query query = new Query(queryData.getDataSource(), queryData.getExpression());

        // Update the tracker status message.
        tracker.setStatus("Searching...");
        final StreamProcessorFilterTracker updatedTracker = streamTaskTransactionHelper.saveTracker(tracker);

        final EventSearchTask eventSearchTask = new EventSearchTask(UserTokenUtil.create(filter.getUpdateUser(), null), query,
                minEvent, maxEvent, maxStreams, maxEvents, maxEventsPerStream, POLL_INTERVAL_MS);
        taskManager.execAsync(eventSearchTask, new TaskCallbackAdaptor<EventRefs>() {
            @Override
            public void onSuccess(final EventRefs result) {
                int resultSize = 0;
                boolean reachedLimit = false;
                if (result != null) {
                    resultSize = result.size();
                    reachedLimit = result.isReachedLimit();
                }

                // Update the tracker status message.
                StreamProcessorFilterTracker tracker = updatedTracker;
                tracker.setStatus("Creating...");
                tracker = streamTaskTransactionHelper.saveTracker(tracker);

                // Create a task for each stream reference.
                final Map<Stream, InclusiveRanges> map = createStreamMap(result);
                final CreatedTasks createdTasks = streamTaskTransactionHelper.createNewTasks(filter, tracker,
                        streamQueryTime, map, node, recentStreamInfo, reachedLimit);
                // Transfer the newly created (and available) tasks to the
                // queue.
                createdTasks.getAvailableTaskList().forEach(queue::add);
                LOGGER.debug("createTasks() - Created {} tasks (tasksToCreate={}) for filter {}", createdTasks.getTotalTasksCreated(), requiredTasks, filter.toString());

                exhaustedFilterMap.put(filter.getId(), resultSize == 0 || reachedLimit);

                queue.setFilling(false);
            }

            @Override
            public void onFailure(final Throwable t) {
                queue.setFilling(false);
            }
        });
    }

    private void createTasksFromCriteria(final StreamProcessorFilter filter,
                                         final OldFindStreamCriteria findStreamCriteria,
                                         final String logPrefix,
                                         final long streamQueryTime,
                                         final Node node,
                                         final int requiredTasks,
                                         final StreamTaskQueue queue,
                                         final StreamTaskCreatorRecentStreamDetails recentStreamInfo,
                                         final StreamProcessorFilterTracker tracker) {
        // Update the tracker status message.
        tracker.setStatus("Creating...");
        final StreamProcessorFilterTracker updatedTracker = streamTaskTransactionHelper.saveTracker(tracker);

        // This will contain locked and unlocked streams
        final List<Stream> streamList = streamTaskTransactionHelper.runSelectStreamQuery(
                findStreamCriteria,
                updatedTracker.getMinStreamId(),
                requiredTasks);

        // Just create regular stream processing tasks.
        final Map<Stream, InclusiveRanges> map = new HashMap<>();
        for (final Stream stream : streamList) {
            map.put(stream, null);
        }

        final CreatedTasks createdTasks = streamTaskTransactionHelper.createNewTasks(filter, updatedTracker,
                streamQueryTime, map, node, recentStreamInfo, false);
        // Transfer the newly created (and available) tasks to the queue.
        createdTasks.getAvailableTaskList().forEach(queue::add);
        LOGGER.debug("createTasks() - Created {} tasks (tasksToCreate={}) for filter {}", createdTasks.getTotalTasksCreated(), requiredTasks, filter.toString());
        exhaustedFilterMap.put(filter.getId(), createdTasks.getTotalTasksCreated() == 0);
    }

    private Map<Stream, InclusiveRanges> createStreamMap(final EventRefs eventRefs) {
        final int maxRangesPerStream = 1000;
        final Map<Stream, InclusiveRanges> streamMap = new HashMap<>();

        if (eventRefs != null) {
            long currentStreamId = -1;
            Stream currentStream = null;
            InclusiveRanges ranges = null;
            boolean trimmed = false;
            for (final EventRef ref : eventRefs) {
                if (!trimmed) {
                    // When the stream id changes add the current ranges to the
                    // map.
                    if (currentStreamId != ref.getStreamId()) {
                        if (ranges != null) {
                            if (ranges.getRanges().size() > maxRangesPerStream) {
                                ranges = ranges.subRanges(maxRangesPerStream);
                                trimmed = true;
                            }

                            if (currentStream != null) {
                                streamMap.put(currentStream, ranges);
                            }
                        }

                        currentStreamId = ref.getStreamId();
                        currentStream = streamStore.loadStreamById(currentStreamId);
                        ranges = new InclusiveRanges();
                    }

                    ranges.addEvent(ref.getEventId());
                }
            }

            // Add the final ranges to the map.
            if (!trimmed && ranges != null) {
                if (ranges.getRanges().size() > maxRangesPerStream) {
                    ranges = ranges.subRanges(maxRangesPerStream);
                    trimmed = true;
                }

                if (currentStream != null) {
                    streamMap.put(currentStream, ranges);
                }
            }
        }

        return streamMap;
    }

    private Long min(final Long l1, final Long l2) {
        if (l1 == null) {
            return l2;
        }
        if (l2 == null) {
            return l1;
        }
        if (l1.longValue() > l2.longValue()) {
            return l2;
        } else {
            return l1;
        }
    }

    /**
     * Schedule a delete if we don't have one
     */
    private void scheduleDelete() {
        if (nextDeleteMs.get() == 0) {
            nextDeleteMs.set(System.currentTimeMillis() + DELETE_INTERVAL_MS);
            LOGGER.debug("scheduleDelete() - nextDeleteMs={}", DateUtil.createNormalDateTimeString(nextDeleteMs.get()));
        }
    }

    @StroomFrequencySchedule("1m")
    @JobTrackedSchedule(jobName = "Stream Task Queue Statistics", description = "Write statistics about the size of the task queue")
    public void writeQueueStatistics() {
        try {
            // Avoid writing loads of same value stats So write every min while
            // it changes Under little load the queue size will be 0
            final int queueSize = getStreamTaskQueueSize();
            if (queueSize != lastQueueSizeForStats) {
                try {
                    final InternalStatisticsReceiver internalStatisticsReceiver = internalStatisticsReceiverProvider.get();
                    if (internalStatisticsReceiver != null) {
                        // Value type event as the queue size is not additive
                        internalStatisticsReceiver.putEvent(InternalStatisticEvent.createValueStat(
                                INTERNAL_STAT_KEY_STREAM_TASK_QUEUE_SIZE,
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

    private boolean isFillTaskQueueEnabled() {
        return propertyService.getBooleanProperty(STREAM_TASKS_FILL_TASK_QUEUE_PROPERTY, true);
    }

    private boolean isCreateTasksEnabled() {
        return propertyService.getBooleanProperty(STREAM_TASKS_CREATE_TASKS_PROPERTY, true);
    }

    private boolean isAssignTasksEnabled() {
        return propertyService.getBooleanProperty(STREAM_TASKS_ASSIGN_TASKS_PROPERTY, true);
    }

    public AtomicLong getNextDeleteMs() {
        return nextDeleteMs;
    }
}
