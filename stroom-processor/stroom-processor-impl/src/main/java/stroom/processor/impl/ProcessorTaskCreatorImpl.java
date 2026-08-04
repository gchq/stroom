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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.impl.ProcessorProfileCache.ProfileResult;
import stroom.processor.impl.ProgressMonitor.FilterProgressMonitor;
import stroom.processor.impl.ProgressMonitor.Phase;
import stroom.processor.impl.ProgressMonitor.SkipReason;
import stroom.processor.impl.TaskCreationBudgets.Budget;
import stroom.processor.shared.FeedDependencies;
import stroom.processor.shared.Limits;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.EventRef;
import stroom.query.common.v2.EventRefs;
import stroom.query.common.v2.EventSearch;
import stroom.query.common.v2.ExpressionValidationException;
import stroom.query.common.v2.ExpressionValidator;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.date.DateUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Keep a pool of stream tasks ready to go.
 */
@Singleton
public class ProcessorTaskCreatorImpl implements ProcessorTaskCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskCreatorImpl.class);

    private static final String LOCK_NAME = "ProcessorTaskCreator";
    private static final int MAX_ERROR_LENGTH = 200;
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Create Processor Tasks", 3);

    private final ProcessorFilterService processorFilterService;
    private final ProcessorFilterTrackerDao processorFilterTrackerDao;
    private final ProcessorTaskDao processorTaskDao;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final Provider<ProcessorConfig> processorConfigProvider;
    private final MetaService metaService;
    private final EventSearch eventSearch;
    private final SecurityContext securityContext;
    private final ClusterLockService clusterLockService;
    private final ProcessorProfileCache processorProfileCache;
    private final FilterFetchBackoff filterFetchBackoff;

    /**
     * Our filter cache
     */
    private final PrioritisedFilters prioritisedFilters;

    @Inject
    ProcessorTaskCreatorImpl(final ProcessorFilterService processorFilterService,
                             final ProcessorFilterTrackerDao processorFilterTrackerDao,
                             final ProcessorTaskDao processorTaskDao,
                             final ExecutorProvider executorProvider,
                             final TaskContextFactory taskContextFactory,
                             final Provider<ProcessorConfig> processorConfigProvider,
                             final MetaService metaService,
                             final EventSearch eventSearch,
                             final SecurityContext securityContext,
                             final ClusterLockService clusterLockService,
                             final PrioritisedFilters prioritisedFilters,
                             final ProcessorProfileCache processorProfileCache,
                             final FilterFetchBackoff filterFetchBackoff) {
        this.processorFilterService = processorFilterService;
        this.processorFilterTrackerDao = processorFilterTrackerDao;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.processorTaskDao = processorTaskDao;
        this.processorConfigProvider = processorConfigProvider;
        this.metaService = metaService;
        this.eventSearch = eventSearch;
        this.securityContext = securityContext;
        this.clusterLockService = clusterLockService;
        this.prioritisedFilters = prioritisedFilters;
        this.processorProfileCache = processorProfileCache;
        this.filterFetchBackoff = filterFetchBackoff;
    }

    @Override
    public void exec() {
        LOGGER.debug("exec() - Executing createTasks");
        // We need to make sure that only 1 thread at a time is allowed to
        // create tasks. This should always be the case in production but some
        // tests will call this directly while scheduled execution could also be
        // running. Also, if the master node changes it is possible for one master
        // to be in the middle of creating tasks when another node assumes master
        // status and tries to create tasks too. Thus, a db backed cluster lock
        // is needed
        try {
            // We need an overarching cluster lock for all task creation
            // Some task creation is async, but we will wait for that
            // to complete so all task creation is encapsulated by this lock
            LOGGER.debug("Locking cluster to create tasks");
            clusterLockService.tryLock(LOCK_NAME, () -> {

                final TaskContext taskContext = taskContextFactory.current();
                createNewTasks(taskContext);
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void createNewTasks(final TaskContext parentTaskContext) {
        final DurationTimer timer = DurationTimer.start();
        LOGGER.trace("createNewTasks() - Starting");
        info(parentTaskContext, () -> "Starting");

        // Get the current list of filters.
        final List<ProcessorFilter> filters = prioritisedFilters.get();
        final ProcessorConfig processorConfig = processorConfigProvider.get();
        final ProgressMonitor progressMonitor = new ProgressMonitor(filters.size());

        parentTaskContext.info(() -> "Creating tasks for " + filters.size() + " filters");

        // Don't remember filters we are no longer considering, e.g. disabled or deleted ones. The
        // queue fill prunes too, but only on the master node, and creation runs on whichever node
        // wins the cluster lock.
        filterFetchBackoff.retainAll(filters);

        // Each processing profile gets its own budget for how many tasks to create, as do the
        // filters that have no profile, so that a busy profile can't use up the whole run and
        // leave another profile's nodes with nothing they are allowed to process.
        final TaskCreationBudgets budgets = new TaskCreationBudgets(
                filters, processorConfig.getTasksToCreate());

        try {
            final LinkedBlockingQueue<ProcessorFilter> filterQueue = new LinkedBlockingQueue<>(filters);
            final AtomicInteger filterCount = new AtomicInteger();

            // Now execute all the runnable items.
            final Executor executor = executorProvider.get(THREAD_POOL);
            final int threadCount = Math.min(filters.size(), processorConfig.getTaskCreationThreadCount());
            LOGGER.trace(() -> LogUtil.message(
                    "createNewTasks() - filterQueue.size: {}, threadCount: {}, budgets: {}",
                    filterQueue.size(), threadCount, budgets));

            final CompletableFuture<?>[] futures = new CompletableFuture[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final int threadNo = i + 1;
                futures[i] = CompletableFuture.runAsync(() -> {
                    while (true) {
                        if (Thread.currentThread().isInterrupted()) {
                            LOGGER.trace("createNewTasks() - Interrupted, filterQueue.size: {}",
                                    filterQueue.size());
                            break;
                        }
                        final ProcessorFilter filter = filterQueue.poll();
                        if (filter == null) {
                            LOGGER.trace("createNewTasks() - Queue empty");
                            break;
                        }

                        final Budget budget = budgets.getBudget(filter);
                        final int remaining = budget.remaining();
                        if (remaining <= 0) {
                            // Skip this filter rather than stopping altogether, as filters for other
                            // profiles may still need tasks creating for nodes that can't process
                            // anything this profile creates. Only stop once every profile has had
                            // its fill, so we don't needlessly walk the rest of the filters.
                            progressMonitor.logSkippedFilter(filter, SkipReason.BUDGET_REACHED);
                            if (budgets.isEverySpent()) {
                                LOGGER.trace(() -> LogUtil.message(
                                        "createNewTasks() - All budgets spent, filterQueue.size: {}, budgets: {}",
                                        filterQueue.size(), budgets));
                                break;
                            }
                            continue;
                        }

                        // The filter list is cached with its trackers attached, so we can tell that
                        // a filter is still backing off from polls that created nothing without the
                        // database fetch that createTasksForFilter does. The cached tracker can be a
                        // few seconds out of date, which at worst delays a filter coming out of
                        // backoff by one run, so the authoritative check still happens against the
                        // freshly loaded filter.
                        if (!FilterPollBackoff.isPollDue(filter,
                                filter.getProcessorFilterTracker(),
                                processorConfig,
                                System.currentTimeMillis())) {
                            progressMonitor.logSkippedFilter(filter, SkipReason.ZERO_TASKS_ON_LAST_POLL);
                            continue;
                        }

                        try {
                            createTasksForFilter(
                                    parentTaskContext,
                                    filters,
                                    progressMonitor,
                                    filterCount,
                                    filter,
                                    remaining,
                                    budget.getUsed(),
                                    processorConfig);
                        } catch (final RuntimeException e) {
                            progressMonitor.logErroredFilter(filter, e);
                            LOGGER.error(e::getMessage, e);
                        }
                    }
                    LOGGER.trace("createNewTasks() - Async task complete, threadNo: {}/{}", threadNo, threadCount);
                }, executor);
            }
            // Wait for all task creation to complete.
            CompletableFuture.allOf(futures).join();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        info(parentTaskContext, () -> "Finished");

        progressMonitor.addSummaryLine(budgets.describe());
        progressMonitor.report("CREATE NEW TASKS", null);

        LOGGER.trace("createNewTasks() - Finished, totalTasksCreated: {}, duration: {}",
                budgets.getTotalUsed(), timer);
    }

    private void createTasksForFilter(final TaskContext parentTaskContext,
                                      final List<ProcessorFilter> filters,
                                      final ProgressMonitor progressMonitor,
                                      final AtomicInteger filterCount,
                                      final ProcessorFilter filter,
                                      final int remaining,
                                      final LongAdder budgetUsed,
                                      final ProcessorConfig processorConfig) {
        // Set the current user to be the one who created the filter so that only streams that
        // the user has access to are processed.
        final UserRef runAs = getFilterRunAs(filter);
        LOGGER.trace(() -> LogUtil.message(
                "createTasksForFilter() - filters.size: {}, filterCount: {}, remaining: {}, " +
                "budgetUsed: {}, runAs: {}, filter: {}",
                filters.size(), filterCount, remaining, budgetUsed, runAs, filter.getFilterInfo()));

        securityContext.asUser(runAs, () ->
                taskContextFactory.childContext(
                        parentTaskContext,
                        "Create Tasks",
                        taskContext -> {
                            final int count = filterCount.incrementAndGet();
                            parentTaskContext.info(() -> "Creating tasks for " +
                                                         count +
                                                         " of " +
                                                         filters.size() +
                                                         " filters (runAs: "
                                                         + runAs
                                                         + ")");
                            createTasksForFilter(
                                    taskContext,
                                    filter,
                                    progressMonitor,
                                    remaining,
                                    budgetUsed,
                                    processorConfig);
                        }).run());
    }

    private UserRef getFilterRunAs(final ProcessorFilter filter) {
        if (filter.getRunAsUser() == null) {
            throw new RuntimeException(
                    LogUtil.message("No run as user specified for filter uuid: {}", filter.getUuid()));
        }
        return filter.getRunAsUser();
    }

    private boolean isValidFilterAndProcessor(final ProcessorFilter filter) {
        if (filter != null && !filter.isDeleted() && filter.isEnabled()) {
            final Processor processor = filter.getProcessor();
            return processor != null
                   && !processor.isDeleted()
                   && processor.isEnabled();
        } else {
            return false;
        }
    }

    public void createTasksForFilter(final TaskContext taskContext,
                                     final ProcessorFilter filter,
                                     final ProgressMonitor progressMonitor,
                                     final int remaining,
                                     final LongAdder budgetUsed,
                                     final ProcessorConfig processorConfig) {
        try {
            // The filter might have been deleted since we found it.
            processorFilterService.fetch(filter.getId()).ifPresent(loadedFilter -> {

                // Only try and create tasks if the processor is enabled.
                if (isValidFilterAndProcessor(loadedFilter)) {

                    // Get the tracker for this filter.
                    final ProcessorFilterTracker tracker = loadedFilter.getProcessorFilterTracker();
                    if (ProcessorFilterTrackerStatus.COMPLETE.equals(tracker.getStatus()) ||
                        ProcessorFilterTrackerStatus.ERROR.equals(tracker.getStatus())) {
                        // If the tracker is complete we need to make sure the status is updated, so we can
                        // see that it is not delivering any more tasks.
                        if (tracker.getLastPollTaskCount() != null && tracker.getLastPollTaskCount() > 0) {
                            tracker.setLastPollMs(System.currentTimeMillis());
                            tracker.setLastPollTaskCount(0);
                            // This isn't a poll that found nothing, it is a filter that has
                            // finished, so don't let it look like the start of a run of non
                            // producing polls if the tracker ever comes back to life.
                            tracker.setNextPollMs(null);
                            updateTracker(tracker, null);
                        }
                        final SkipReason skipReason = ProcessorFilterTrackerStatus.COMPLETE.equals(tracker.getStatus())
                                ? SkipReason.TRACKER_COMPLETE
                                : SkipReason.TRACKER_ERROR;
                        progressMonitor.logSkippedFilter(filter, skipReason);
                        LOGGER.trace(() -> LogUtil.message(
                                "createTasksForFilter() - Skipping filter, tracker.status: {}, filter: {}",
                                tracker.getStatus(), loadedFilter.getFilterInfo()));
                    } else {
                        doCreateTasksForFilter(
                                taskContext,
                                loadedFilter,
                                progressMonitor,
                                remaining,
                                budgetUsed,
                                processorConfig);
                    }
                } else {
                    progressMonitor.logSkippedFilter(filter, SkipReason.DISABLED_OR_DELETED);
                    LOGGER.trace(() -> LogUtil.message(
                            "createTasksForFilter() - Skipping filter: {}, {}",
                            filter.getFilterInfo(), filter));
                }
            });
        } catch (final RuntimeException e) {
            progressMonitor.logErroredFilter(filter, e);
            LOGGER.error(() -> LogUtil.message("Error processing filter: {} {} - {}",
                    filter.getFilterInfo(),
                    e.getClass().getSimpleName(),
                    e.getMessage()), e);
            LOGGER.debug(e::getMessage, e);
        }
    }

    private boolean checkTrackerTaskCount(final ProcessorFilter filter,
                                          final ProcessorFilterTracker tracker,
                                          final ProcessorConfig processorConfig) {
        final long nowMs = System.currentTimeMillis();
        if (FilterPollBackoff.isPollDue(filter, tracker, processorConfig, nowMs)) {
            return true;
        } else {
            LOGGER.debug(() -> LogUtil.message(
                    "checkTrackerTaskCount() - Skipping filter with no tasks on last poll, " +
                    "lastPollMs: {}, nextPollMs: {}, timeUntilNextPollMs: {}, filter: {}",
                    tracker.getLastPollMs(),
                    tracker.getNextPollMs(),
                    FilterPollBackoff.getDueMs(
                            tracker,
                            tracker.getLastPollMs(),
                            processorConfig.getSkipNonProducingFiltersDuration().toMillis()) - nowMs,
                    filter.getFilterInfo()));
            return false;
        }
    }

    private void doCreateTasksForFilter(final TaskContext taskContext,
                                        final ProcessorFilter filter,
                                        final ProgressMonitor progressMonitor,
                                        final int remaining,
                                        final LongAdder budgetUsed,
                                        final ProcessorConfig processorConfig) {
        LOGGER.trace(() -> LogUtil.message("doCreateTasksForFilter() - remaining: {}, filter: {}",
                remaining, filter.getFilterInfo()));

        // Don't try and create tasks for this filter if we didn't manage to create any last time and not much time has
        // passed since the last attempt.
        final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
        if (checkTrackerTaskCount(filter, tracker, processorConfig)) {
            final int currentCreatedTasks = processorTaskDao.countTasksForFilter(filter.getId(), TaskStatus.CREATED);
            budgetUsed.add(currentCreatedTasks);

            int maxTasks = remaining - currentCreatedTasks;

            // See if we are allowing tasks to be created eagerly beyond the process limit.
            if (!processorConfig.isCreateTasksBeyondProcessLimit()) {
                boolean usedProfile = false;

                // We are limiting task creation so see if there is a profile that will limit total cluster tasks.
                if (filter.getProfileName() != null) {
                    try {
                        final ProfileResult profileResult = processorProfileCache.getProfile(filter.getProfileName());
                        Objects.requireNonNull(profileResult, "No processing profile found (filter=" +
                                                              filter +
                                                              ", profileName=" +
                                                              filter.getProfileName() +
                                                              ")");
                        maxTasks = Math.min(remaining, profileResult.maxClusterThreads()) - currentCreatedTasks;
                        usedProfile = true;

                    } catch (final RuntimeException e) {
                        throw new RuntimeException("Error getting processing profile for filter (filter=" +
                                                   filter +
                                                   ", profileName=" +
                                                   filter.getProfileName() +
                                                   ")", e);
                    }
                }

                // We didn't find a profile to limit tasks so see if the filter has a direct limit.
                if (!usedProfile && filter.isProcessingTaskCountBounded()) {
                    // The max concurrent tasks for this filter is bounded, so only create tasks up to that limit
                    maxTasks = Math.min(remaining, filter.getMaxProcessingTasks()) - currentCreatedTasks;
                }
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("doCreateTasksForFilter() - maxTasks: {}, remaining: {}, currentCreatedTasks: {}, " +
                             "isProcessingTaskCountBounded: {}, isCreateTasksBeyondProcessLimit: {}",
                        maxTasks, remaining, currentCreatedTasks, filter.isProcessingTaskCountBounded(),
                        processorConfig.isCreateTasksBeyondProcessLimit());
            }

            // Skip filters that already have enough tasks.
            if (maxTasks > 0) {
                info(taskContext, filter::getFilterInfo);
                final FilterProgressMonitor filterProgressMonitor = progressMonitor.logFilter(
                        filter, currentCreatedTasks);

                final QueryData queryData = filter.getQueryData();
                final boolean isStreamStoreSearch = queryData.getDataSource() != null
                                                    && queryData.getDataSource().getType().equals(
                        MetaFields.STREAM_STORE_TYPE);
                try {
                    LOGGER.debug("createTasksForFilter() - processorFilter {}",
                            filter);
                    // Record the time before we are going to query for streams for tracking purposes.
                    final long streamQueryTime = System.currentTimeMillis();
                    if (!isStreamStoreSearch) {
                        createTasksFromSearchQuery(
                                filter,
                                filterProgressMonitor,
                                queryData,
                                streamQueryTime,
                                maxTasks,
                                tracker,
                                taskContext,
                                budgetUsed);
                    } else {
                        // Create tasks from a standard stream filter criteria.
                        createTasksFromCriteria(
                                filter,
                                filterProgressMonitor,
                                queryData,
                                streamQueryTime,
                                maxTasks,
                                tracker,
                                taskContext,
                                budgetUsed);
                    }
                } catch (final RuntimeException e) {
                    filterProgressMonitor.logException(e);
                    LOGGER.error(() -> LogUtil.message("Error creating tasks for filter: {} {} - {}",
                            filter.getFilterInfo(),
                            e.getClass().getSimpleName(),
                            e.getMessage()), e);
                    LOGGER.debug(e::getMessage, e);

                    // Update the tracker with the error if we can.
                    try {
                        // Reload filter.
                        processorFilterService.fetch(filter.getId()).ifPresent(loadedFilter -> {
                            String error = e.toString();
                            if (error.length() > MAX_ERROR_LENGTH) {
                                error = error.substring(0, MAX_ERROR_LENGTH) + "...";
                            }

                            final ProcessorFilterTracker loadedTracker = loadedFilter.getProcessorFilterTracker();
                            loadedTracker.setStatus(ProcessorFilterTrackerStatus.ERROR);
                            loadedTracker.setMessage(error);
                            updateTracker(loadedTracker, filterProgressMonitor);
                        });
                    } catch (final RuntimeException e2) {
                        LOGGER.error(() -> LogUtil.message(
                                "Error recording error against the tracker for filter: {} {} - {}",
                                filter.getFilterInfo(),
                                e2.getClass().getSimpleName(),
                                e2.getMessage()), e2);
                    }
                } finally {
                    filterProgressMonitor.complete();
                }
            } else {
                LOGGER.trace("doCreateTasksForFilter() - maxTasks is 0, Skipping filter {}", filter.getFilterInfo());
                progressMonitor.logSkippedFilter(filter, SkipReason.MAX_TASKS_REACHED);
            }
        } else {
            progressMonitor.logSkippedFilter(filter, SkipReason.ZERO_TASKS_ON_LAST_POLL);
        }
    }

    private void createTasksFromCriteria(final ProcessorFilter filter,
                                         final FilterProgressMonitor filterProgressMonitor,
                                         final QueryData queryData,
                                         final long streamQueryTime,
                                         final int maxTasks,
                                         final ProcessorFilterTracker tracker,
                                         final TaskContext taskContext,
                                         final LongAdder budgetUsed) {
        if (termCount(queryData) == 0) {
            throw new RuntimeException("Attempting to create tasks with an unconstrained filter " + filter);
        }

        LOGGER.debug("createTasksFromCriteria() - requiredTasks: {}, filter: {}", maxTasks, filter);

        // This will contain locked and unlocked streams
        final OptionalLong optMaxMetaId = getEffectiveMaxMetaId(
                filter, tracker, filterProgressMonitor, getMaxMetaId(filter));
        if (optMaxMetaId.isEmpty()) {
            // We have only just established the max meta id for this filter, so wait for the next poll.
            return;
        }
        final long maxMetaId = optMaxMetaId.getAsLong();

        final DurationTimer durationTimer = DurationTimer.start();
        final List<Meta> metaList = runSelectMetaQuery(
                queryData.getExpression(),
                tracker.getMinMetaId(),
                maxMetaId,
                filter.getMinMetaCreateTimeMs(),
                filter.getMaxMetaCreateTimeMs(),
                filter.getPipeline(),
                filter.isReprocess(),
                maxTasks);
        filterProgressMonitor.logPhase(Phase.FIND_META_FOR_FILTER, durationTimer, metaList.size());

        // Just create regular stream processing tasks.
        final Map<Meta, InclusiveRanges> map = new HashMap<>();
        for (final Meta meta : metaList) {
            map.put(meta, null);
        }

        final int createdTasks = createTasks(
                filter,
                tracker,
                filterProgressMonitor,
                streamQueryTime,
                map,
                maxMetaId,
                false);
        filterProgressMonitor.add(createdTasks);

        info(taskContext, () ->
                LogUtil.message("createTasks() - Created {} tasks for filter {}",
                        createdTasks,
                        filter.getFilterInfo()));
        budgetUsed.add(createdTasks);
    }

    /**
     * Create tasks for a filter and, if any were created, tell the queue fill that this filter now
     * has something to queue.
     * <p>
     * Task creation runs on whichever node wins the cluster lock, so the queue fill only hears about
     * it when that node is also the master node that fills the queue, which is always the case on a
     * single node install. It is a best effort shortcut rather than the mechanism; when creation
     * happens elsewhere {@link ProcessorConfig#getSkipEmptyFilterFetchDuration()} bounds how long
     * the new tasks sit unqueued.
     * </p>
     */
    private int createTasks(final ProcessorFilter filter,
                            final ProcessorFilterTracker tracker,
                            final FilterProgressMonitor filterProgressMonitor,
                            final long metaQueryTime,
                            final Map<Meta, InclusiveRanges> metaMap,
                            final Long maxMetaId,
                            final boolean reachedLimit) {
        final int createdTasks = processorTaskDao.createNewTasks(
                filter,
                tracker,
                filterProgressMonitor,
                metaQueryTime,
                metaMap,
                maxMetaId,
                reachedLimit);

        if (createdTasks > 0) {
            // This must happen AFTER createNewTasks() has committed: the backoff pairs a fill's
            // empty fetch with the creation version it read beforehand, so recording the creation
            // while the tasks were still invisible would let a concurrent fill find nothing, match
            // the already incremented version and back the filter off just as the tasks appear.
            filterFetchBackoff.recordTasksCreated(filter);
        }

        return createdTasks;
    }

    /**
     * Get the max meta id to bound task creation with. The DB allocates meta ids at insert time but only
     * makes the rows visible at commit time, so the current max id can sit above a meta that is still in
     * flight. If we bound task creation with the current max then the tracker moves past the in flight
     * meta and it is silently never processed, so instead we bound with the max id seen on the previous
     * poll, by which time anything below it will have committed or rolled back.
     *
     * @param currentMaxMetaId The max meta id as at this poll.
     * @return The max meta id to use, or empty if this poll must be abandoned because we have only just
     * established the tracker state that subsequent polls will use.
     */
    private OptionalLong getEffectiveMaxMetaId(final ProcessorFilter filter,
                                               final ProcessorFilterTracker tracker,
                                               final FilterProgressMonitor filterProgressMonitor,
                                               final long currentMaxMetaId) {
        if (!processorConfigProvider.get().isUseMaxMetaIdFromPreviousPoll()) {
            return OptionalLong.of(currentMaxMetaId);
        }

        final Long prevMaxMetaId = tracker.getPrevMaxMetaId();
        tracker.setPrevMaxMetaId(currentMaxMetaId);

        if (prevMaxMetaId == null) {
            // There is no previous max id to bound task creation with, e.g. this is the first poll for a
            // new filter, or the first poll after an upgrade or a tracker reset. Record the current max
            // for the next poll to use and create nothing this time round. We can't fall back to the
            // current max as that is exactly the unsafe read we are avoiding, and we can't bound with
            // zero as createNewTasks() would then wind the tracker back to the start of the meta table.
            LOGGER.debug(() -> LogUtil.message(
                    "getEffectiveMaxMetaId() - Establishing max meta id {} for filter: {}",
                    currentMaxMetaId, filter.getFilterInfo()));
            updateTracker(tracker, filterProgressMonitor);
            return OptionalLong.empty();
        }

        // The current max can be lower than the previous one, e.g. feed dependencies can move the
        // effective max backwards, so bound with whichever of the two allows the least.
        final long maxMetaId = Math.min(prevMaxMetaId, currentMaxMetaId);
        LOGGER.trace(() -> LogUtil.message(
                "getEffectiveMaxMetaId() - maxMetaId: {}, prevMaxMetaId: {}, currentMaxMetaId: {}, filter: {}",
                maxMetaId, prevMaxMetaId, currentMaxMetaId, filter.getFilterInfo()));
        return OptionalLong.of(maxMetaId);
    }

    private long getMaxMetaId(final ProcessorFilter filter) {
        // Determine the max effective time for all feed dependencies.
        final Instant now = Instant.now();
        final FeedDependencies feedDependencies =
                NullSafe.get(filter, ProcessorFilter::getQueryData, QueryData::getFeedDependencies);
        if (feedDependencies != null) {
            final SimpleDuration minProcessingDelay = feedDependencies.getMinProcessingDelay();
            final SimpleDuration maxProcessingDelay = feedDependencies.getMaxProcessingDelay();

            Instant maxCreateTime = metaService
                    .getFeedDependencyEffectiveTime(NullSafe.get(feedDependencies,
                            FeedDependencies::getFeedDependencies));
            if (maxCreateTime == null) {
                // If we have a null create time from feed dependencies we might still want to delay processing.
                if (minProcessingDelay != null || (maxProcessingDelay != null && maxProcessingDelay.getTime() > 0)) {
                    maxCreateTime = now;
                }
            }

            if (maxCreateTime != null) {
                // If there is a minimum delay then make sure we wait at least that long to process.
                if (minProcessingDelay != null) {
                    final Instant youngest = SimpleDurationUtil.minus(now, minProcessingDelay);
                    if (maxCreateTime.isAfter(youngest)) {
                        maxCreateTime = youngest;
                    }
                }

                // If there is a maximum delay then make sure we don't process streams older than the delay.
                if (maxProcessingDelay != null) {
                    final Instant oldest = SimpleDurationUtil.minus(now, maxProcessingDelay);
                    if (maxCreateTime.isBefore(oldest)) {
                        maxCreateTime = oldest;
                    }
                }

                // Find the max stream id that belongs to a stream that has a create time less than or equal to the
                // max effective time.
                return Objects.requireNonNullElse(metaService.getMaxId(maxCreateTime.toEpochMilli()), 0L);
            }
        }
        return Objects.requireNonNullElse(metaService.getMaxId(), 0L);
    }

    private void createTasksFromSearchQuery(final ProcessorFilter filter,
                                            final FilterProgressMonitor filterProgressMonitor,
                                            final QueryData queryData,
                                            final long streamQueryTime,
                                            final int maxTasks,
                                            final ProcessorFilterTracker tracker,
                                            final TaskContext taskContext,
                                            final LongAdder budgetUsed) {
        final AtomicInteger totalTasks = new AtomicInteger();
        final EventRef minEvent = new EventRef(tracker.getMinMetaId(), tracker.getMinEventId());
        long maxStreams = maxTasks;
        LOGGER.debug("Creating search query tasks maxStreams: {}, filer: {}", maxStreams, filter);
        long maxEvents = 1000000;
        final long maxEventsPerStream = 1000;

        // Are there any limits set on the query.
        if (queryData.getLimits() != null) {
            final Limits limits = queryData.getLimits();

            // If any of the limits on task creation have been reached then set the tracker to complete and
            // return, as there are no more tasks to create for this filter.
            if (limits.getDurationMs() != null) {
                final long start = filter.getCreateTimeMs();
                final long end = start + limits.getDurationMs();
                if (end < System.currentTimeMillis()) {
                    LOGGER.debug(() -> LogUtil.message(
                            "createTasksFromSearchQuery() - Duration limit reached, filter: {}",
                            filter.getFilterInfo()));
                    tracker.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
                    updateTracker(tracker, filterProgressMonitor);
                    return;
                }
            }

            if (limits.getStreamCount() != null) {
                long streamLimit = limits.getStreamCount();
                if (tracker.getMetaCount() != null) {
                    streamLimit -= tracker.getMetaCount();
                }

                maxStreams = Math.min(streamLimit, maxStreams);

                if (streamLimit <= 0) {
                    LOGGER.debug(() -> LogUtil.message(
                            "createTasksFromSearchQuery() - Stream count limit reached, filter: {}",
                            filter.getFilterInfo()));
                    tracker.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
                    updateTracker(tracker, filterProgressMonitor);
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
                    LOGGER.debug(() -> LogUtil.message(
                            "createTasksFromSearchQuery() - Event count limit reached, filter: {}",
                            filter.getFilterInfo()));
                    tracker.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
                    updateTracker(tracker, filterProgressMonitor);
                    return;
                }
            }
        }

        final Query query = Query.builder()
                .dataSource(queryData.getDataSource())
                .expression(queryData.getExpression())
                .params(getParams(queryData))
                .build();

        final OptionalLong optMaxMetaId = getEffectiveMaxMetaId(
                filter,
                tracker,
                filterProgressMonitor,
                Objects.requireNonNullElse(metaService.getMaxId(), 0L));
        if (optMaxMetaId.isEmpty()) {
            // We have only just established the max meta id for this filter, so wait for the next poll.
            return;
        }
        final long maxMetaId = optMaxMetaId.getAsLong();

        // Bound the search with the same max meta id that the tracker will be moved on to. Without this the
        // search would find events in streams above the max and the tracker would jump to the last of those,
        // skipping any meta in between that was still in flight when we read the max.
        final EventRef maxEvent = new EventRef(maxMetaId, Long.MAX_VALUE);

        final BiConsumer<EventRefs, Throwable> consumer = (eventRefs, throwable) -> {
            LOGGER.debug(() -> LogUtil.message(
                    "createTasksFromEventRefs() called for {} eventRefs, filter {}", eventRefs.size(), filter));
            try {
                if (throwable != null) {
                    final String message =
                            "Error creating tasks for filter (id=" +
                            filter.getId() +
                            "). " +
                            throwable.getMessage();
                    LOGGER.error(message);
                    LOGGER.debug(message, throwable);
                    tracker.setStatus(ProcessorFilterTrackerStatus.ERROR);
                    updateTracker(tracker, filterProgressMonitor);

                } else if (eventRefs == null) {
                    LOGGER.debug(() -> "eventRefs is null");
                    tracker.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
                    updateTracker(tracker, filterProgressMonitor);

                } else {
                    final boolean reachedLimit = eventRefs.isReachedLimit();

                    // Create a task for each stream reference.
                    final DurationTimer durationTimer = DurationTimer.start();
                    final Map<Meta, InclusiveRanges> map = createStreamMap(eventRefs);
                    filterProgressMonitor.logPhase(Phase.CREATE_STREAM_MAP,
                            durationTimer,
                            map.size());

                    final int createdTasks = createTasks(
                            filter,
                            tracker,
                            filterProgressMonitor,
                            streamQueryTime,
                            map,
                            maxMetaId,
                            reachedLimit);
                    totalTasks.addAndGet(createdTasks);
                    budgetUsed.add(createdTasks);

                    info(taskContext, () ->
                            LogUtil.message("createTasks() - Created {} tasks for filter {}",
                                    createdTasks,
                                    filter.getFilterInfo()));
                }
            } catch (final Exception e) {
                filterProgressMonitor.logException(e);
                LOGGER.error("Error creating tasks for filter {}, {}", filter.getId(), e.getMessage(), e);
            }
        };

        final DurationTimer durationTimer = DurationTimer.start();
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
        final CompletableFuture<Void> future2 = future.whenComplete((ignoredVoid, e) -> {
            filterProgressMonitor.logPhase(
                    Phase.CREATE_TASKS_FROM_SEARCH_QUERY,
                    durationTimer, totalTasks.get());
            filterProgressMonitor.add(totalTasks.get());
            if (e != null) {
                // Will come in here if it doesn't get as far as the consumer
                filterProgressMonitor.logException(e);
            }
            filterProgressMonitor.complete();
        });
        future2.join();
    }

    private void updateTracker(final ProcessorFilterTracker tracker,
                               final FilterProgressMonitor filterProgressMonitor) {
        final DurationTimer durationTimer = DurationTimer.start();
        processorFilterTrackerDao.update(tracker);
        if (filterProgressMonitor != null) {
            filterProgressMonitor.logPhase(Phase.UPDATE_TRACKERS, durationTimer, 0);
        }
    }

    private List<Param> getParams(final QueryData queryData) {
        if (queryData.getParams() == null) {
            return Collections.emptyList();
        }
        return queryData.getParams();
    }

    private void info(final TaskContext taskContext,
                      final Supplier<String> messageSupplier) {
        LOGGER.debug(messageSupplier);
        taskContext.info(messageSupplier);
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
                    if (ranges == null || currentMetaId != ref.getStreamId()) {
                        if (currentMeta != null) {
                            if (ranges.getRanges().size() > maxRangesPerStream) {
                                ranges = ranges.subRanges(maxRangesPerStream);
                                trimmed = true;
                            }
                            streamMap.put(currentMeta, ranges);
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
                if (currentMeta != null) {
                    if (ranges.getRanges().size() > maxRangesPerStream) {
                        ranges = ranges.subRanges(maxRangesPerStream);
                    }
                    streamMap.put(currentMeta, ranges);
                }
            }
        }

        return streamMap;
    }

    /**
     * Pkg private for testing
     */
    static ExpressionOperator sanitiseAndValidateExpression(final ExpressionOperator expressionOperator) {
        if (expressionOperator != null) {
            final ExpressionOperator copy;
            if (expressionOperator.containsField(MetaFields.STATUS.getFldName())) {
                // Remove any status terms in case the UI has left any in. This is mostly to deal
                // with a legacy situation where the UI was including status terms in re-process filters.
                // We will be adding in our own status terms after this
                final Predicate<ExpressionItem> excludeStatusTermPredicate = ProcessorTaskCreatorImpl::isNotStatusTerm;
                copy = ExpressionUtil.copyOperator(expressionOperator, excludeStatusTermPredicate);
                LOGGER.debug("""
                        sanitiseAndValidateExpression() - Removed status term(s) from expression
                          Before: {}
                          After: {}""", expressionOperator, copy);
            } else {
                LOGGER.debug("sanitiseAndValidateExpression() - no change to expressionOperator: {}",
                        expressionOperator);
                copy = expressionOperator;
            }

            // Validate expression.
            final ExpressionValidator expressionValidator = new ExpressionValidator(
                    MetaFields.getProcessorFilterFields());
            try {
                expressionValidator.validate(copy);
            } catch (final ExpressionValidationException e) {
                LOGGER.debug(() -> LogUtil.message(
                        "sanitiseAndValidateExpression() - Error validating expression: {} - {}",
                        copy, LogUtil.exceptionMessage(e)));
                throw e;
            }
            return copy;
        } else {
            return null;
        }
    }

    private static boolean isNotStatusTerm(final ExpressionItem expressionItem) {
        final boolean isStatusTerm = expressionItem instanceof final ExpressionTerm term
                                     && MetaFields.STATUS.getFldName().equals(term.getField());
        return !isStatusTerm;
    }

    /**
     * @return streams that have not yet got a stream task for a particular
     * stream processor
     */
    List<Meta> runSelectMetaQuery(final ExpressionOperator expression,
                                  final long minMetaId,
                                  final long maxMetaId,
                                  final Long minMetaCreateTimeMs,
                                  final Long maxMetaCreateTimeMs,
                                  final DocRef pipelineDocRef,
                                  final boolean reprocess,
                                  final int length) {
        // If we can't possibly return any data then return an empty list.
        if (minMetaId > maxMetaId) {
            return Collections.emptyList();
        }

        final ExpressionOperator effectiveExpression = sanitiseAndValidateExpression(expression);
        ExpressionOperator.Builder builder = ExpressionOperator.builder()
                .addOperator(effectiveExpression);

        if (reprocess) {
            builder = builder
                    .addIdTerm(MetaFields.PARENT_ID, Condition.GREATER_THAN_OR_EQUAL_TO, minMetaId)
                    .addIdTerm(MetaFields.PARENT_ID, Condition.LESS_THAN_OR_EQUAL_TO, maxMetaId);

            if (pipelineDocRef != null) {
                builder = builder.addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipelineDocRef);
            }

            if (minMetaCreateTimeMs != null) {
                builder = builder.addDateTerm(MetaFields.PARENT_CREATE_TIME,
                        Condition.GREATER_THAN_OR_EQUAL_TO,
                        DateUtil.createNormalDateTimeString(minMetaCreateTimeMs));
            }
            if (maxMetaCreateTimeMs != null) {
                builder = builder.addDateTerm(MetaFields.PARENT_CREATE_TIME,
                        Condition.LESS_THAN_OR_EQUAL_TO,
                        DateUtil.createNormalDateTimeString(maxMetaCreateTimeMs));
            }
            // Don't select deleted streams.
            final ExpressionOperator statusExpression = ExpressionOperator.builder().op(Op.OR)
                    .addTextTerm(MetaFields.PARENT_STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                    .addTextTerm(MetaFields.PARENT_STATUS, Condition.EQUALS, Status.LOCKED.getDisplayValue())
                    .build();
            builder = builder.addOperator(statusExpression);

            return findMeta(metaService::findReprocess, builder, MetaFields.PARENT_ID, length, reprocess);

        } else {
            builder = builder
                    .addIdTerm(MetaFields.ID, Condition.GREATER_THAN_OR_EQUAL_TO, minMetaId)
                    .addIdTerm(MetaFields.ID, Condition.LESS_THAN_OR_EQUAL_TO, maxMetaId);

            if (minMetaCreateTimeMs != null) {
                builder = builder.addDateTerm(MetaFields.CREATE_TIME,
                        Condition.GREATER_THAN_OR_EQUAL_TO,
                        DateUtil.createNormalDateTimeString(minMetaCreateTimeMs));
            }
            if (maxMetaCreateTimeMs != null) {
                builder = builder.addDateTerm(MetaFields.CREATE_TIME,
                        Condition.LESS_THAN_OR_EQUAL_TO,
                        DateUtil.createNormalDateTimeString(maxMetaCreateTimeMs));
            }

            // Don't select deleted streams.
            final ExpressionOperator statusExpression = ExpressionOperator.builder().op(Op.OR)
                    .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                    .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.LOCKED.getDisplayValue())
                    .build();
            builder = builder.addOperator(statusExpression);

            return findMeta(metaService::find, builder, MetaFields.ID, length, reprocess);
        }
    }

    private List<Meta> findMeta(final Function<FindMetaCriteria, ResultPage<Meta>> findFunc,
                                final ExpressionOperator.Builder builder,
                                final QueryField idField,
                                final int length,
                                final boolean reprocess) {

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(builder.build());
        findMetaCriteria.setSort(idField.getFldName(), false, false);
        findMetaCriteria.obtainPageRequest().setLength(length);
        final List<Meta> list = findFunc.apply(findMetaCriteria).getValues();
        LOGGER.debug(() -> LogUtil.message("findMeta(), reprocess: {}, findMetaCriteria: {}, listSize: {}",
                reprocess, findMetaCriteria, list.size()));
        return list;
    }
}

