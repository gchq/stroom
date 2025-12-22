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
import stroom.processor.impl.ProgressMonitor.FilterProgressMonitor;
import stroom.processor.impl.ProgressMonitor.Phase;
import stroom.processor.shared.Limits;
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
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                             final PrioritisedFilters prioritisedFilters) {
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

                final LongAdder totalTasksCreated = new LongAdder();
                final TaskContext taskContext = taskContextFactory.current();
                createNewTasks(taskContext, totalTasksCreated);
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void createNewTasks(final TaskContext parentTaskContext,
                                final LongAdder totalTasksCreated) {
        LOGGER.trace("createNewTasks() - Starting");
        info(parentTaskContext, () -> "Starting");

        // Get the current list of filters.
        final List<ProcessorFilter> filters = prioritisedFilters.get();
        final ProcessorConfig processorConfig = processorConfigProvider.get();
        final ProgressMonitor progressMonitor = new ProgressMonitor(filters.size());

        parentTaskContext.info(() -> "Creating tasks for " +
                                     filters.size() +
                                     " filters");

        try {
            final LinkedBlockingQueue<ProcessorFilter> filterQueue = new LinkedBlockingQueue<>(filters);
            final AtomicInteger filterCount = new AtomicInteger();

            // Now execute all the runnable items.
            final Executor executor = executorProvider.get(THREAD_POOL);
            final int threadCount = Math.min(filters.size(), processorConfig.getTaskCreationThreadCount());
            final CompletableFuture<?>[] futures = new CompletableFuture[threadCount];
            for (int i = 0; i < threadCount; i++) {
                futures[i] = CompletableFuture.runAsync(() -> {
                    boolean run = true;
                    while (run) {
                        final int remaining = processorConfig.getTasksToCreate() - totalTasksCreated.intValue();
                        final ProcessorFilter filter = filterQueue.poll();
                        if (remaining > 0 && filter != null && !Thread.currentThread().isInterrupted()) {
                            try {
                                createTasksForFilter(
                                        parentTaskContext,
                                        filters,
                                        progressMonitor,
                                        filterCount,
                                        filter,
                                        remaining,
                                        totalTasksCreated);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        } else {
                            run = false;
                        }
                    }
                }, executor);
            }
            // Wait for all task creation to complete.
            CompletableFuture.allOf(futures).join();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        info(parentTaskContext, () -> "Finished");

        progressMonitor.report("CREATE NEW TASKS", null);

        LOGGER.trace("createNewTasks() - Finished");
    }

    private void createTasksForFilter(final TaskContext parentTaskContext,
                                      final List<ProcessorFilter> filters,
                                      final ProgressMonitor progressMonitor,
                                      final AtomicInteger filterCount,
                                      final ProcessorFilter filter,
                                      final int remaining,
                                      final LongAdder totalTasksCreated) {
        // Set the current user to be the one who created the filter so that only streams that
        // the user has access to are processed.
        final UserRef runAs = getFilterRunAs(filter);
        securityContext.asUser(runAs, () ->
                taskContextFactory.childContext(parentTaskContext,
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
                                    totalTasksCreated);
                        }).run());
    }

    private UserRef getFilterRunAs(final ProcessorFilter filter) {
        if (filter.getRunAsUser() == null) {
            throw new RuntimeException(
                    LogUtil.message("No run as user specified for filter uuid: {}", filter.getUuid()));
        }
        return filter.getRunAsUser();
    }

    public void createTasksForFilter(final TaskContext taskContext,
                                     final ProcessorFilter filter,
                                     final ProgressMonitor progressMonitor,
                                     final int remaining,
                                     final LongAdder totalTasksCreated) {
        try {
            // The filter might have been deleted since we found it.
            processorFilterService.fetch(filter.getId()).ifPresent(loadedFilter -> {

                // Only try and create tasks if the processor is enabled.
                if (loadedFilter.isEnabled() && loadedFilter.getProcessor().isEnabled()) {

                    // Get the tracker for this filter.
                    final ProcessorFilterTracker tracker = loadedFilter.getProcessorFilterTracker();
                    if (ProcessorFilterTrackerStatus.COMPLETE.equals(tracker.getStatus()) ||
                        ProcessorFilterTrackerStatus.ERROR.equals(tracker.getStatus())) {
                        // If the tracker is complete we need to make sure the status is updated, so we can
                        // see that it is not delivering any more tasks.
                        if (tracker.getLastPollTaskCount() != null && tracker.getLastPollTaskCount() > 0) {
                            tracker.setLastPollMs(System.currentTimeMillis());
                            tracker.setLastPollTaskCount(0);
                            updateTracker(tracker, null);
                        }

                    } else {
                        doCreateTasksForFilter(
                                taskContext,
                                loadedFilter,
                                progressMonitor,
                                remaining,
                                totalTasksCreated);
                    }
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error processing filter: {} {} - {}",
                    filter.getFilterInfo(),
                    e.getClass().getSimpleName(),
                    e.getMessage()), e);
            LOGGER.debug(e::getMessage, e);
        }
    }

    private void doCreateTasksForFilter(final TaskContext taskContext,
                                        final ProcessorFilter filter,
                                        final ProgressMonitor progressMonitor,
                                        final int remaining,
                                        final LongAdder totalTasksCreated) {
        // Don't try and create tasks for this filter if we didn't manage to create any last time and not much time has
        // passed since the last attempt.
        final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
        if (tracker.getLastPollTaskCount() == null ||
            tracker.getLastPollTaskCount() > 0 ||
            Instant
                    .ofEpochMilli(tracker.getLastPollMs())
                    .plus(processorConfigProvider.get().getSkipNonProducingFiltersDuration())
                    .isBefore(Instant.now())) {
            final int currentCreatedTasks = processorTaskDao.countTasksForFilter(filter.getId(), TaskStatus.CREATED);
            totalTasksCreated.add(currentCreatedTasks);

            int maxTasks = remaining - currentCreatedTasks;
            if (filter.isProcessingTaskCountBounded() &&
                !processorConfigProvider.get().isCreateTasksBeyondProcessLimit()) {
                // The max concurrent tasks for this filter is bounded, so only create tasks up to that limit
                maxTasks = Math.min(remaining, filter.getMaxProcessingTasks()) - currentCreatedTasks;
            }

            // Skip filters that already have enough tasks.
            if (maxTasks > 0) {
                info(taskContext, filter::getFilterInfo);
                final FilterProgressMonitor filterProgressMonitor =
                        progressMonitor.logFilter(filter, currentCreatedTasks);

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
                                totalTasksCreated);
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
                                totalTasksCreated);
                    }
                } catch (final RuntimeException e) {
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
                            updateTracker(tracker, filterProgressMonitor);
                        });
                    } catch (final RuntimeException e2) {
                        LOGGER.error(e.getMessage(), e);
                    }

                } finally {
                    filterProgressMonitor.complete();
                }
            }
        }
    }

    private void createTasksFromCriteria(final ProcessorFilter filter,
                                         final FilterProgressMonitor filterProgressMonitor,
                                         final QueryData queryData,
                                         final long streamQueryTime,
                                         final int maxTasks,
                                         final ProcessorFilterTracker tracker,
                                         final TaskContext taskContext,
                                         final LongAdder totalTasksCreated) {
        if (termCount(queryData) == 0) {
            throw new RuntimeException("Attempting to create tasks with an unconstrained filter " + filter);
        }

        LOGGER.debug("Creating tasks from criteria, requiredTasks: {}, filter: {}", maxTasks, filter);

        // This will contain locked and unlocked streams
        final Long maxMetaId = metaService.getMaxId();

        final DurationTimer durationTimer = DurationTimer.start();
        final List<Meta> metaList = runSelectMetaQuery(
                queryData.getExpression(),
                tracker.getMinMetaId(),
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

        final int createdTasks = processorTaskDao.createNewTasks(
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
        totalTasksCreated.add(createdTasks);
    }

    private void createTasksFromSearchQuery(final ProcessorFilter filter,
                                            final FilterProgressMonitor filterProgressMonitor,
                                            final QueryData queryData,
                                            final long streamQueryTime,
                                            final int maxTasks,
                                            final ProcessorFilterTracker tracker,
                                            final TaskContext taskContext,
                                            final LongAdder totalTasksCreated) {
        final AtomicInteger totalTasks = new AtomicInteger();
        final EventRef minEvent = new EventRef(tracker.getMinMetaId(), tracker.getMinEventId());
        final EventRef maxEvent = new EventRef(Long.MAX_VALUE, 0L);
        long maxStreams = maxTasks;
        LOGGER.debug("Creating search query tasks maxStreams: {}, filer: {}", maxStreams, filter);
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
                    tracker.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
                    updateTracker(tracker, filterProgressMonitor);
                }
            }

            if (limits.getStreamCount() != null) {
                long streamLimit = limits.getStreamCount();
                if (tracker.getMetaCount() != null) {
                    streamLimit -= tracker.getMetaCount();
                }

                maxStreams = Math.min(streamLimit, maxStreams);

                if (streamLimit <= 0) {
                    tracker.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
                    updateTracker(tracker, filterProgressMonitor);
                }
            }

            if (limits.getEventCount() != null) {
                long eventLimit = limits.getEventCount();
                if (tracker.getEventCount() != null) {
                    eventLimit -= tracker.getEventCount();
                }

                maxEvents = Math.min(eventLimit, maxEvents);

                if (maxEvents <= 0) {
                    tracker.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
                    updateTracker(tracker, filterProgressMonitor);
                }
            }
        }

        final Query query = Query.builder()
                .dataSource(queryData.getDataSource())
                .expression(queryData.getExpression())
                .params(getParams(queryData))
                .build();

        final Long maxMetaId = metaService.getMaxId();

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

                    final int createdTasks = processorTaskDao.createNewTasks(
                            filter,
                            tracker,
                            filterProgressMonitor,
                            streamQueryTime,
                            map,
                            maxMetaId,
                            reachedLimit);
                    totalTasks.addAndGet(createdTasks);
                    totalTasksCreated.add(createdTasks);

                    info(taskContext, () ->
                            LogUtil.message("createTasks() - Created {} tasks for filter {}",
                                    createdTasks,
                                    filter.getFilterInfo()));
                }
            } catch (final Exception e) {
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
        final CompletableFuture<Void> future2 = future.whenComplete((v, throwable) -> {
            filterProgressMonitor.logPhase(
                    Phase.CREATE_TASKS_FROM_SEARCH_QUERY,
                    durationTimer, totalTasks.get());
            filterProgressMonitor.add(totalTasks.get());
            filterProgressMonitor.complete();
        });
        future2.join();
    }

    private void updateTracker(final ProcessorFilterTracker tracker,
                               final FilterProgressMonitor filterProgressMonitor) {
        final DurationTimer durationTimer = DurationTimer.start();
        processorFilterTrackerDao.update(tracker);
        if (filterProgressMonitor != null) {
            filterProgressMonitor.logPhase(Phase.UPDATE_TRACKERS,
                    durationTimer,
                    0);
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
                                  final Long minMetaCreateTimeMs,
                                  final Long maxMetaCreateTimeMs,
                                  final DocRef pipelineDocRef,
                                  final boolean reprocess,
                                  final int length) {

        final ExpressionOperator effectiveExpression = sanitiseAndValidateExpression(expression);
        ExpressionOperator.Builder builder = ExpressionOperator.builder()
                .addOperator(effectiveExpression);

        if (reprocess) {
            builder = builder.addIdTerm(MetaFields.PARENT_ID, Condition.GREATER_THAN_OR_EQUAL_TO, minMetaId);

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
            builder = builder.addIdTerm(MetaFields.ID, Condition.GREATER_THAN_OR_EQUAL_TO, minMetaId);

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

