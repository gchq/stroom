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

package stroom.processor.impl.db;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.db.util.ValueMapper;
import stroom.db.util.ValueMapper.Mapper;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.api.InclusiveRanges.InclusiveRange;
import stroom.processor.impl.ExistingCreatedTask;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorFilterCache;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProgressMonitor.FilterProgressMonitor;
import stroom.processor.impl.ProgressMonitor.Phase;
import stroom.processor.impl.db.jooq.tables.records.ProcessorTaskRecord;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.BatchBindStep;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record6;
import org.jooq.Result;
import org.jooq.SelectJoinStep;
import org.jooq.UpdateConditionStep;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFeed.PROCESSOR_FEED;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;
import static stroom.processor.impl.db.jooq.tables.ProcessorNode.PROCESSOR_NODE;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

@Singleton
class ProcessorTaskDaoImpl implements ProcessorTaskDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskDaoImpl.class);

    private static final int BATCH_SIZE = 1_000;

    //    private static final Function<Record, Processor> RECORD_TO_PROCESSOR_MAPPER = new RecordToProcessorMapper();
//    private static final Function<Record, ProcessorFilter> RECORD_TO_PROCESSOR_FILTER_MAPPER =
//            new RecordToProcessorFilterMapper();
//    private static final Function<Record, ProcessorFilterTracker> RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER =
//            new RecordToProcessorFilterTrackerMapper();
    private static final Function<Record, ProcessorTask> RECORD_TO_PROCESSOR_TASK_MAPPER =
            new RecordToProcessorTaskMapper();
    private static final Condition ACTIVE_TASKS_STATUS_CONDITION = PROCESSOR_TASK.STATUS.in(
            TaskStatus.QUEUED.getPrimitiveValue(),
            TaskStatus.ASSIGNED.getPrimitiveValue(),
            TaskStatus.PROCESSING.getPrimitiveValue());

    private static final Field<Integer> COUNT = DSL.count();

    private static final Map<String, Field<?>> FIELD_MAP = Map.ofEntries(
            Map.entry(ProcessorTaskFields.FIELD_ID, PROCESSOR_TASK.ID),
            Map.entry(ProcessorTaskFields.FIELD_CREATE_TIME, PROCESSOR_TASK.CREATE_TIME_MS),
            Map.entry(ProcessorTaskFields.FIELD_START_TIME, PROCESSOR_TASK.START_TIME_MS),
            Map.entry(ProcessorTaskFields.FIELD_END_TIME_DATE, PROCESSOR_TASK.END_TIME_MS),
            Map.entry(ProcessorTaskFields.FIELD_FEED, PROCESSOR_FEED.NAME),
            Map.entry(ProcessorTaskFields.FIELD_PRIORITY, PROCESSOR_FILTER.PRIORITY),
            Map.entry(ProcessorTaskFields.FIELD_PIPELINE, PROCESSOR.PIPELINE_UUID),
            Map.entry(ProcessorTaskFields.FIELD_PIPELINE_NAME, PROCESSOR.PIPELINE_UUID),
            Map.entry(ProcessorTaskFields.FIELD_STATUS, PROCESSOR_TASK.STATUS),
            Map.entry(ProcessorTaskFields.FIELD_COUNT, COUNT),
            Map.entry(ProcessorTaskFields.FIELD_NODE, PROCESSOR_NODE.NAME),
            Map.entry(ProcessorTaskFields.FIELD_POLL_AGE, PROCESSOR_FILTER_TRACKER.LAST_POLL_MS)
    );

    private static final Field<?>[] PROCESSOR_TASK_COLUMNS = new Field<?>[]{
            PROCESSOR_TASK.VERSION,
            PROCESSOR_TASK.CREATE_TIME_MS,
            PROCESSOR_TASK.STATUS,
            PROCESSOR_TASK.STATUS_TIME_MS,
            PROCESSOR_TASK.FK_PROCESSOR_FEED_ID,
            PROCESSOR_TASK.META_ID,
            PROCESSOR_TASK.DATA,
            PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID};
    private static final Object[] PROCESSOR_TASK_VALUES = new Object[PROCESSOR_TASK_COLUMNS.length];

    private final ProcessorNodeCache processorNodeCache;
    private final ProcessorFeedCache processorFeedCache;
    private final ProcessorFilterTrackerDaoImpl processorFilterTrackerDao;
    private final ProcessorFilterCache processorFilterCache;
    private final ProcessorConfig processorConfig;
    private final ProcessorDbConnProvider processorDbConnProvider;
    //    private final ProcessorFilterMarshaller marshaller;
    private final DocRefInfoService docRefInfoService;
    private final ExpressionMapper expressionMapper;
    private final ValueMapper valueMapper;

    @Inject
    ProcessorTaskDaoImpl(final ProcessorNodeCache processorNodeCache,
                         final ProcessorFeedCache processorFeedCache,
                         final ProcessorFilterTrackerDaoImpl processorFilterTrackerDao,
                         final ProcessorFilterCache processorFilterCache,
                         final ProcessorConfig processorConfig,
                         final ProcessorDbConnProvider processorDbConnProvider,
                         final ExpressionMapperFactory expressionMapperFactory,
                         final DocRefInfoService docRefInfoService) {
        this.processorNodeCache = processorNodeCache;
        this.processorFeedCache = processorFeedCache;
        this.processorFilterTrackerDao = processorFilterTrackerDao;
        this.processorFilterCache = processorFilterCache;
        this.processorConfig = processorConfig;
        this.processorDbConnProvider = processorDbConnProvider;
        this.docRefInfoService = docRefInfoService;

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(ProcessorTaskFields.CREATE_TIME, PROCESSOR_TASK.CREATE_TIME_MS, value ->
                DateExpressionParser.getMs(ProcessorTaskFields.CREATE_TIME.getFldName(), value));
        expressionMapper.map(ProcessorTaskFields.CREATE_TIME_MS, PROCESSOR_TASK.CREATE_TIME_MS, Long::valueOf);
        expressionMapper.map(ProcessorTaskFields.START_TIME, PROCESSOR_TASK.START_TIME_MS, value ->
                DateExpressionParser.getMs(ProcessorTaskFields.START_TIME.getFldName(), value));
        expressionMapper.map(ProcessorTaskFields.START_TIME_MS, PROCESSOR_TASK.START_TIME_MS, Long::valueOf);
        expressionMapper.map(ProcessorTaskFields.END_TIME, PROCESSOR_TASK.END_TIME_MS, value ->
                DateExpressionParser.getMs(ProcessorTaskFields.END_TIME.getFldName(), value));
        expressionMapper.map(ProcessorTaskFields.END_TIME_MS, PROCESSOR_TASK.END_TIME_MS, Long::valueOf);
        expressionMapper.map(ProcessorTaskFields.STATUS_TIME, PROCESSOR_TASK.STATUS_TIME_MS, value ->
                DateExpressionParser.getMs(ProcessorTaskFields.STATUS_TIME.getFldName(), value));
        expressionMapper.map(ProcessorTaskFields.STATUS_TIME_MS, PROCESSOR_TASK.STATUS_TIME_MS, Long::valueOf);
        expressionMapper.map(ProcessorTaskFields.META_ID, PROCESSOR_TASK.META_ID, Long::valueOf);
        expressionMapper.map(ProcessorTaskFields.NODE_NAME, PROCESSOR_NODE.NAME, value -> value);
        expressionMapper.map(ProcessorTaskFields.FEED, PROCESSOR_FEED.NAME, value -> value, true);
        // Get a uuid for the selected pipe doc
        expressionMapper.map(ProcessorTaskFields.PIPELINE, PROCESSOR.PIPELINE_UUID, value -> value, false);
        // Get 0-many uuids for a pipe name (partial/wild-carded)
        expressionMapper.multiMap(
                ProcessorTaskFields.PIPELINE_NAME, PROCESSOR.PIPELINE_UUID, this::getPipelineUuidsByName, true);
        expressionMapper.map(ProcessorTaskFields.PROCESSOR_FILTER_ID, PROCESSOR_FILTER.ID, Integer::valueOf);
        expressionMapper.map(ProcessorTaskFields.PROCESSOR_FILTER_PRIORITY,
                PROCESSOR_FILTER.PRIORITY,
                Integer::valueOf);
        expressionMapper.map(ProcessorTaskFields.PROCESSOR_ID, PROCESSOR.ID, Integer::valueOf);
        expressionMapper.map(ProcessorTaskFields.STATUS,
                PROCESSOR_TASK.STATUS,
                value -> TaskStatus.valueOf(value.toUpperCase()).getPrimitiveValue());
        expressionMapper.map(ProcessorTaskFields.TASK_ID, PROCESSOR_TASK.ID, Long::valueOf);

        // TODO AT: This could be moved out into a singleton class, see IndexShardValueMapper
        //  to save it being create each time
        valueMapper = new ValueMapper();
        valueMapper.map(ProcessorTaskFields.CREATE_TIME, PROCESSOR_TASK.CREATE_TIME_MS, ValLong::create);
        valueMapper.map(ProcessorTaskFields.CREATE_TIME_MS, PROCESSOR_TASK.CREATE_TIME_MS, ValLong::create);
        valueMapper.map(ProcessorTaskFields.START_TIME, PROCESSOR_TASK.START_TIME_MS, ValLong::create);
        valueMapper.map(ProcessorTaskFields.START_TIME_MS, PROCESSOR_TASK.START_TIME_MS, ValLong::create);
        valueMapper.map(ProcessorTaskFields.END_TIME, PROCESSOR_TASK.END_TIME_MS, ValLong::create);
        valueMapper.map(ProcessorTaskFields.END_TIME_MS, PROCESSOR_TASK.END_TIME_MS, ValLong::create);
        valueMapper.map(ProcessorTaskFields.STATUS_TIME, PROCESSOR_TASK.STATUS_TIME_MS, ValLong::create);
        valueMapper.map(ProcessorTaskFields.STATUS_TIME_MS, PROCESSOR_TASK.STATUS_TIME_MS, ValLong::create);
        valueMapper.map(ProcessorTaskFields.META_ID, PROCESSOR_TASK.META_ID, ValLong::create);
        valueMapper.map(ProcessorTaskFields.NODE_NAME, PROCESSOR_NODE.NAME, ValString::create);
        valueMapper.map(ProcessorTaskFields.FEED, PROCESSOR_FEED.NAME, ValString::create);
        valueMapper.map(ProcessorTaskFields.PIPELINE, PROCESSOR.PIPELINE_UUID, this::getPipelineName);
        valueMapper.map(ProcessorTaskFields.PIPELINE_NAME, PROCESSOR.PIPELINE_UUID, this::getPipelineName);
        valueMapper.map(ProcessorTaskFields.PROCESSOR_FILTER_ID, PROCESSOR_FILTER.ID, ValInteger::create);
        valueMapper.map(ProcessorTaskFields.PROCESSOR_FILTER_PRIORITY, PROCESSOR_FILTER.PRIORITY, ValInteger::create);
        valueMapper.map(ProcessorTaskFields.PROCESSOR_ID, PROCESSOR.ID, ValInteger::create);
        valueMapper.map(ProcessorTaskFields.STATUS,
                PROCESSOR_TASK.STATUS,
                v -> ValString.create(TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(v).getDisplayValue()));
        valueMapper.map(ProcessorTaskFields.TASK_ID, PROCESSOR_TASK.ID, ValLong::create);
    }

    private Val getPipelineName(final String uuid) {
        String val = uuid;
        if (docRefInfoService != null) {
            val = docRefInfoService.name(new DocRef("Pipeline", uuid)).orElse(uuid);
        }
        return ValString.create(val);
    }

    private Set<Integer> getNodeIdSet(final Set<String> nodeNames) {
        final Set<Integer> set = new HashSet<>();
        for (final String nodeName : nodeNames) {
            final Integer nodeId = processorNodeCache.getOrCreate(nodeName);
            if (nodeId != null) {
                set.add(nodeId);
            }
        }
        return set;
    }

    /**
     * Release tasks and make them unowned.
     *
     * @param nodeName The node name to release task ownership for.
     * @return The number of tasks released.
     */
    @Override
    public long releaseOwnedTasks(final String nodeName) {
        LOGGER.info(() -> "Releasing owned tasks for " + nodeName);
        final List<Condition> conditions = new ArrayList<>();

        // Release tasks for the specified nodes.
        final Integer nodeId = processorNodeCache.getOrCreate(nodeName);
        conditions.add(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.eq(nodeId));

        // Only alter tasks that are marked as queued, assigned or processing,
        // i.e. ignore complete and failed tasks.
        conditions.add(ACTIVE_TASKS_STATUS_CONDITION);

        // Release tasks.
        final long count = releaseTasks(conditions);

        LOGGER.info(() -> "Set " + count + " tasks back to CREATED that were " +
                          "QUEUED, ASSIGNED, PROCESSING");

        return count;
    }

    /**
     * Retain task ownership
     *
     * @param retainForNodes  A set of nodes to retain task ownership for.
     * @param statusOlderThan Change task ownership for tasks that have a status older than this.
     * @return The number of tasks released.
     */
    @Override
    public long retainOwnedTasks(final Set<String> retainForNodes, final Instant statusOlderThan) {
        LOGGER.info(() -> "Retaining owned tasks");
        final List<Condition> conditions = new ArrayList<>();

        // Keep tasks ownership for active nodes.
        final Set<Integer> nodeIdSet = getNodeIdSet(retainForNodes);
        conditions.add(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.notIn(nodeIdSet));

        // Only change tasks that have not been changed for a certain amount of time.
        conditions.add(PROCESSOR_TASK.STATUS_TIME_MS.lt(statusOlderThan.toEpochMilli()));

        // Only alter tasks that are marked as queued, assigned or processing,
        // i.e. ignore complete and failed tasks.
        conditions.add(ACTIVE_TASKS_STATUS_CONDITION);

        // Release tasks.
        final long count = releaseTasks(conditions);

        LOGGER.info(() -> "Set " + count + " tasks back to CREATED that were " +
                          "QUEUED, ASSIGNED, PROCESSING");

        return count;
    }

    private long releaseTasks(final List<Condition> conditions) {
        final AtomicLong minId = new AtomicLong(-1);
        boolean complete = false;
        long count = 0;

        while (!complete) {
            final List<Record2<Long, Byte>> results = JooqUtil.contextResult(processorDbConnProvider,
                    context ->
                            context
                                    .select(PROCESSOR_TASK.ID, PROCESSOR_TASK.STATUS)
                                    .from(PROCESSOR_TASK)
                                    .where(conditions)
                                    .and(PROCESSOR_TASK.ID.gt(minId.get()))
                                    .orderBy(PROCESSOR_TASK.ID)
                                    .limit(BATCH_SIZE)
                                    .fetch());
            if (results.isEmpty()) {
                complete = true;
            } else {
                // Group by status.
                final Map<TaskStatus, Set<Long>> idSetMap = new HashMap<>();
                for (final Record2<Long, Byte> record : results) {
                    minId.set(record.value1());

                    final TaskStatus status = TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.value2());
                    final long id = record.value1();
                    idSetMap.computeIfAbsent(status, k -> new HashSet<>()).add(id);
                }

                for (final Entry<TaskStatus, Set<Long>> entry : idSetMap.entrySet()) {
                    final Condition updateCondition = PROCESSOR_TASK.ID.in(entry.getValue())
                            .and(PROCESSOR_TASK.STATUS.eq(entry.getKey().getPrimitiveValue()));
                    count += JooqUtil.contextResult(processorDbConnProvider, context ->
                            changeStatus(context,
                                    null,
                                    TaskStatus.CREATED,
                                    System.currentTimeMillis(),
                                    updateCondition));
                }
            }
        }

        return count;
    }

    /**
     * Create new tasks for the specified filter and add them to the queue.
     * <p>
     * This must only be done on one node at a time, i.e. under a cluster lock.
     * <p>
     * Synchronised to avoid the risk of any table locking when being called concurrently
     * by multiple threads on the master node
     *
     * @param filter          The fitter to create tasks for
     * @param tracker         The tracker that tracks the task creation progress for the
     *                        filter.
     * @param streamQueryTime The time that we queried for streams that match the stream
     *                        processor filter.
     * @param streams         The map of streams and optional event ranges to create stream
     *                        tasks for.
     * @param reachedLimit    For search based stream task creation this indicates if we
     *                        have reached the limit of stream tasks created for a single
     *                        search. This limit is imposed to stop search based task
     *                        creation running forever.
     */
    @Override
    public synchronized int createNewTasks(final ProcessorFilter filter,
                                           final ProcessorFilterTracker tracker,
                                           final FilterProgressMonitor filterProgressMonitor,
                                           final long streamQueryTime,
                                           final Map<Meta, InclusiveRanges> streams,
                                           final Long maxMetaId,
                                           final boolean reachedLimit) {
        // Get the current time.
        final long statusTimeMs = System.currentTimeMillis();
        final CreationState creationState = new CreationState();

        try {
            // Create all bind values.
            final Object[][] allBindValues = new Object[streams.size()][];
            int rowCount = 0;
            for (final Entry<Meta, InclusiveRanges> entry : streams.entrySet()) {
                final Meta meta = entry.getKey();
                final InclusiveRanges eventRanges = entry.getValue();

                String eventRangeData = null;
                if (eventRanges != null) {
                    eventRangeData = eventRanges.rangesToString();
                    creationState.eventCount += eventRanges.count();
                }

                // Update the max event id if this stream id is greater than
                // any we have seen before.
                if (creationState.streamIdRange == null || meta.getId() > creationState.streamIdRange.getMax()) {
                    if (eventRanges != null) {
                        creationState.eventIdRange = eventRanges.getOuterRange();
                    } else {
                        creationState.eventIdRange = null;
                    }
                }

                creationState.streamIdRange = InclusiveRange.extend(creationState.streamIdRange, meta.getId());
                creationState.streamMsRange = InclusiveRange.extend(creationState.streamMsRange, meta.getCreateMs());

                final Object[] bindValues = new Object[PROCESSOR_TASK_COLUMNS.length];

                bindValues[0] = 1; //version
                bindValues[1] = statusTimeMs; //create_ms
                bindValues[2] = TaskStatus.CREATED.getPrimitiveValue(); //stat
                bindValues[3] = statusTimeMs; //stat_ms
                bindValues[4] = processorFeedCache.getOrCreate(meta.getFeedName());
                bindValues[5] = meta.getId(); //fk_strm_id
                if (eventRangeData != null && !eventRangeData.isEmpty()) {
                    bindValues[6] = eventRangeData; //dat
                }
                bindValues[7] = filter.getId(); //fk_strm_proc_filt_id
                allBindValues[rowCount++] = bindValues;
            }

            // Do everything within a single transaction.
            JooqUtil.transaction(processorDbConnProvider, context -> {
                if (allBindValues.length > 0) {

                    // Insert tasks.
                    final DurationTimer durationTimer = DurationTimer.start();
                    try {
                        insertTasks(context, allBindValues);
                        creationState.totalTasksCreated = allBindValues.length;
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                        throw e;
                    }
                    filterProgressMonitor.logPhase(Phase.INSERT_NEW_TASKS, durationTimer, allBindValues.length);
                }

                // Update tracker.
                final DurationTimer durationTimer = DurationTimer.start();
                // Anything created?
                if (creationState.totalTasksCreated > 0) {
                    log(creationState, creationState.streamIdRange);

                    // If we have never created tasks before or the last poll gave
                    // us no tasks then start to report a new creation range.
                    if (tracker.getMinMetaCreateMs() == null || (tracker.getLastPollTaskCount() != null
                                                                 && tracker.getLastPollTaskCount().longValue() == 0L)) {
                        tracker.setMinMetaCreateMs(creationState.streamMsRange.getMin());
                    }
                    // Report where we have got to.
                    tracker.setMetaCreateMs(creationState.streamMsRange.getMax());

                    // Only create tasks for streams with an id 1 or more greater
                    // than the greatest stream id we have created tasks for this
                    // time round in future.
                    if (creationState.eventIdRange != null) {
                        tracker.setMinMetaId(creationState.streamIdRange.getMax());
                        tracker.setMinEventId(creationState.eventIdRange.getMax() + 1);
                    } else {
                        tracker.setMinMetaId(creationState.streamIdRange.getMax() + 1);
                        tracker.setMinEventId(0L);
                    }

                } else {
                    // We have completed all tasks so update the window to be from
                    // now
                    tracker.setMinMetaCreateMs(streamQueryTime);

                    // Report where we have got to.
                    tracker.setMetaCreateMs(streamQueryTime);

                    // Only create tasks for streams with an id greater
                    // than the current max stream id in future as we didn't manage
                    // to create any tasks.
                    if (maxMetaId != null) {
                        tracker.setMinMetaId(maxMetaId + 1);
                        tracker.setMinEventId(0L);
                    }
                }

                if (tracker.getMetaCount() != null) {
                    if (creationState.totalTasksCreated > 0) {
                        tracker.setMetaCount(tracker.getMetaCount() + creationState.totalTasksCreated);
                    }
                } else {
                    tracker.setMetaCount((long) creationState.totalTasksCreated);
                }
                if (creationState.eventCount > 0) {
                    if (tracker.getEventCount() != null) {
                        tracker.setEventCount(tracker.getEventCount() + creationState.eventCount);
                    } else {
                        tracker.setEventCount(creationState.eventCount);
                    }
                }

                tracker.setLastPollMs(statusTimeMs);
                tracker.setLastPollTaskCount(creationState.totalTasksCreated);
                tracker.setStatus(ProcessorFilterTrackerStatus.CREATED);

                // If the filter has a max meta creation time then let the tracker know.
                if (filter.getMaxMetaCreateTimeMs() != null && tracker.getMaxMetaCreateMs() == null) {
                    tracker.setMaxMetaCreateMs(filter.getMaxMetaCreateTimeMs());
                }
                // Has this filter finished creating tasks for good, i.e. is there
                // any possibility of getting more tasks in future?
                if (reachedLimit ||
                    (tracker.getMaxMetaCreateMs() != null && tracker.getMetaCreateMs() != null
                     && tracker.getMetaCreateMs() > tracker.getMaxMetaCreateMs())) {
                    LOGGER.debug(() ->
                            "processProcessorFilter() - Completed task creation for bounded filter " +
                            filter.getId());
                    LOGGER.trace(() ->
                            "processProcessorFilter() - Completed task creation for bounded filter " +
                            filter);
                    tracker.setStatus(ProcessorFilterTrackerStatus.COMPLETE);
                }

                // Save the tracker state within the transaction.
                final int updatedTrackerCount = processorFilterTrackerDao.update(context, tracker);
                filterProgressMonitor.logPhase(Phase.UPDATE_TRACKERS, durationTimer, updatedTrackerCount);
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            return 0;
        }

        return creationState.totalTasksCreated;
    }

    /**
     * Change the node ownership of the tasks in the id set and select them back to include in the queue.
     *
     * @param idSet    The ids of the tasks to take ownership of.
     * @param nodeName This node name.
     * @return A list of tasks to queue.
     */
    @Override
    public List<ProcessorTask> queueTasks(final Set<Long> idSet,
                                          final String nodeName) {
        final long now = System.currentTimeMillis();
        final Integer nodeId;
        if (nodeName != null) {
            nodeId = processorNodeCache.getOrCreate(nodeName);
        } else {
            nodeId = null;
        }

        // Do everything within a single transaction.
        final Result<Record> result = JooqUtil.transactionResult(
                processorDbConnProvider, context -> {
                    // Change created tasks to queued.
                    final Condition updateCondition = PROCESSOR_TASK.ID.in(idSet)
                            .and(PROCESSOR_TASK.STATUS.eq(TaskStatus.CREATED.getPrimitiveValue()));
                    final int count = changeStatus(
                            context,
                            nodeId,
                            TaskStatus.QUEUED,
                            now,
                            updateCondition);

                    // Select back the updated records.
                    final Condition selectCondition = PROCESSOR_TASK.ID.in(idSet)
                            .and(PROCESSOR_TASK.STATUS_TIME_MS.eq(now))
                            .and(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.eq(nodeId))
                            .and(PROCESSOR_TASK.STATUS.eq(TaskStatus.QUEUED.getPrimitiveValue()));
                    final Result<Record> r = select(context, selectCondition);

                    if (r.size() != count) {
                        throw new RuntimeException(
                                "Unexpected number of stream tasks selected back after update.");
                    }

                    return r;
                });

        return convert(result);
    }

    @Override
    public int releaseTasks(final Set<Long> idSet, final TaskStatus currentStatus) {
        final long now = System.currentTimeMillis();

        final Condition statusCondition = PROCESSOR_TASK.STATUS.eq(currentStatus.getPrimitiveValue());
        final Condition condition = PROCESSOR_TASK.ID.in(idSet).and(statusCondition);

        return JooqUtil.contextResult(
                processorDbConnProvider, context -> {
                    // Update the records.
                    return changeStatus(
                            context,
                            null,
                            TaskStatus.CREATED,
                            now,
                            condition);
                });
    }

    /**
     * Count the current number of tasks for a filter matching the specified status.
     *
     * @param filterId The filter to count tasks for.
     * @param status   Task status.
     * @return The number of tasks matching the specified status.
     */
    @Override
    public int countTasksForFilter(final int filterId, final TaskStatus status) {
        return JooqUtil.contextResult(
                processorDbConnProvider, context ->
                        context
                                .selectCount()
                                .from(PROCESSOR_TASK)
                                .where(PROCESSOR_TASK.STATUS.eq(status.getPrimitiveValue()))
                                .and(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(filterId))
                                .fetchOne(0, int.class));
    }

    private Result<Record> select(final DSLContext context,
                                  final Condition condition) {
        return context
                .select()
                .from(PROCESSOR_TASK)
                .leftOuterJoin(PROCESSOR_NODE).on(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.eq(PROCESSOR_NODE.ID))
                .leftOuterJoin(PROCESSOR_FEED).on(PROCESSOR_TASK.FK_PROCESSOR_FEED_ID.eq(PROCESSOR_FEED.ID))
//                .join(PROCESSOR_FILTER).on(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(PROCESSOR_FILTER.ID))
//                .join(PROCESSOR_FILTER_TRACKER).on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(
//                        PROCESSOR_FILTER_TRACKER.ID))
//                .join(PROCESSOR).on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                .where(condition)
                .orderBy(PROCESSOR_TASK.ID)
                .fetch();
    }

    private int changeStatus(final DSLContext context,
                             final Integer nodeId,
                             final TaskStatus newStatus,
                             final Long statusTime,
                             final Condition condition) {
        Objects.requireNonNull(condition, "Null condition");
        final Supplier<String> msgSupplier = () -> LogUtil.message(
                "changeStatus - nodeId: {}, newStatus: {}, statusTime: {}, condition: {}",
                nodeId,
                newStatus,
                statusTime,
                condition);
        LOGGER.debug(msgSupplier);

        return JooqUtil.withDeadlockRetries(() ->
                        context
                                .update(PROCESSOR_TASK)
                                .set(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID, nodeId)
                                .set(PROCESSOR_TASK.STATUS, newStatus.getPrimitiveValue())
                                .set(PROCESSOR_TASK.STATUS_TIME_MS, statusTime)
                                .setNull(PROCESSOR_TASK.START_TIME_MS)
                                .setNull(PROCESSOR_TASK.END_TIME_MS)
                                .set(PROCESSOR_TASK.VERSION, PROCESSOR_TASK.VERSION.plus(1))
                                .where(condition)
                                .execute(),
                msgSupplier);
    }

    private void insertTasks(final DSLContext context,
                             final Object[][] allBindValues) {
        BatchBindStep batchBindStep = null;
        int i = 0;

        for (final Object[] bindValues : allBindValues) {
            i++;

            if (batchBindStep == null) {
                batchBindStep = context
                        .batch(
                                context
                                        .insertInto(PROCESSOR_TASK)
                                        .columns(PROCESSOR_TASK_COLUMNS)
                                        .values(PROCESSOR_TASK_VALUES));
            }

            batchBindStep = batchBindStep.bind(bindValues);

            // Execute insert if we have reached batch size.
            if (i >= processorConfig.getDatabaseMultiInsertMaxBatchSize()) {
                executeInsert(batchBindStep, i);
                i = 0;
                batchBindStep = null;
            }
        }

        // Do final execution.
        if (batchBindStep != null) {
            executeInsert(batchBindStep, i);
        }
    }

    private void executeInsert(final BatchBindStep batchBindStep, final int rowCount) {
        try {
            LOGGER.logDurationIfTraceEnabled(
                    batchBindStep::execute,
                    () -> LogUtil.message("Execute for {} rows", rowCount));
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void log(final CreationState creationState,
                     final InclusiveRange streamIdRange) {
        LOGGER.debug(() -> "processProcessorFilter() - Created " +
                           creationState.totalTasksCreated +
                           " tasks in the range " +
                           streamIdRange);
    }

    @Override
    public ResultPage<ProcessorTask> find(final ExpressionCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final Result<Record> result = JooqUtil.contextResult(processorDbConnProvider, context ->
                context
                        .select()
                        .from(PROCESSOR_TASK)
                        .leftOuterJoin(PROCESSOR_NODE).on(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.eq(PROCESSOR_NODE.ID))
                        .leftOuterJoin(PROCESSOR_FEED).on(PROCESSOR_TASK.FK_PROCESSOR_FEED_ID.eq(PROCESSOR_FEED.ID))
                        .join(PROCESSOR_FILTER).on(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(PROCESSOR_FILTER.ID))
                        .join(PROCESSOR_FILTER_TRACKER).on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(
                                PROCESSOR_FILTER_TRACKER.ID))
                        .join(PROCESSOR).on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                        .where(condition)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch());
        return convert(criteria, result);
    }

    private ResultPage<ProcessorTask> convert(final ExpressionCriteria criteria,
                                              final Result<Record> result) {
        final List<ProcessorTask> list = convert(result);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private List<ProcessorTask> convert(final Result<Record> result) {
        return result.map(record -> {
            final Integer processorFilterId = record.get(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID);
            final Optional<ProcessorFilter> processorFilter = processorFilterCache.get(processorFilterId);
            final ProcessorTask processorTask = RECORD_TO_PROCESSOR_TASK_MAPPER.apply(record);
            processorTask.setProcessorFilter(processorFilter.orElse(null));
            return processorTask;
        });
    }

//    private List<ProcessorTask> convert(final Result<Record> result) {
//        final Map<Integer, ProcessorFilter> processorFilterCache = new HashMap<>();
//        return result.map(record -> {
//            final Integer processorFilterId = record.get(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID);
//            final ProcessorFilter processorFilter = processorFilterCache.computeIfAbsent(processorFilterId,
//                    pfid -> {
//                        final Processor processor = RECORD_TO_PROCESSOR_MAPPER.apply(record);
//                        final ProcessorFilterTracker processorFilterTracker =
//                                RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER.apply(record);
//
//                        final ProcessorFilter filter = RECORD_TO_PROCESSOR_FILTER_MAPPER.apply(record);
//                        filter.setProcessor(processor);
//                        filter.setProcessorFilterTracker(processorFilterTracker);
//                        return marshaller.unmarshal(filter);
//                    });
//
//            final ProcessorTask processorTask = RECORD_TO_PROCESSOR_TASK_MAPPER.apply(record);
//            processorTask.setProcessorFilter(marshaller.unmarshal(processorFilter));
//
//            return processorTask;
//        });
//    }

    @Override
    public ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final PageRequest pageRequest = criteria.getPageRequest();
        final int offset = JooqUtil.getOffset(pageRequest);
        final int limit = JooqUtil.getLimit(pageRequest, true);
        final Result<Record6<String, String, String, Integer, Byte, Integer>> result =
                JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select(
                                PROCESSOR.TASK_TYPE,
                                PROCESSOR_FEED.NAME,
                                PROCESSOR.PIPELINE_UUID,
                                PROCESSOR_FILTER.PRIORITY,
                                PROCESSOR_TASK.STATUS,
                                COUNT
                        )
                        .from(PROCESSOR_TASK)
                        .leftOuterJoin(PROCESSOR_NODE).on(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.eq(PROCESSOR_NODE.ID))
                        .join(PROCESSOR_FEED).on(PROCESSOR_TASK.FK_PROCESSOR_FEED_ID.eq(PROCESSOR_FEED.ID))
                        .join(PROCESSOR_FILTER).on(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(PROCESSOR_FILTER.ID))
                        .join(PROCESSOR).on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                        .where(condition)
                        .groupBy(
                                PROCESSOR.TASK_TYPE,
                                PROCESSOR_FEED.NAME,
                                PROCESSOR.PIPELINE_UUID,
                                PROCESSOR_FILTER.PRIORITY,
                                PROCESSOR_TASK.STATUS)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch());

        final List<ProcessorTaskSummary> list = result.map(record -> {
            final ProcessorType processorType = ProcessorType.fromDisplayValue(record.get(PROCESSOR.TASK_TYPE));
            final String docType;
            if (ProcessorType.STREAMING_ANALYTIC.equals(processorType)) {
                docType = AnalyticRuleDoc.TYPE;
            } else {
                docType = PipelineDoc.TYPE;
            }

            final String feed = record.get(PROCESSOR_FEED.NAME);
            final String pipelineUuid = record.get(PROCESSOR.PIPELINE_UUID);
            final int priority = record.get(PROCESSOR_FILTER.PRIORITY);
            final TaskStatus status = TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(
                    PROCESSOR_TASK.STATUS));
            final int count = record.get(COUNT);
            DocRef pipelineDocRef = new DocRef(docType, pipelineUuid);
            final Optional<String> pipelineName = docRefInfoService.name(pipelineDocRef);
            if (pipelineName.isPresent()) {
                pipelineDocRef = pipelineDocRef.copy().name(pipelineName.get()).build();
            }
            return new ProcessorTaskSummary(pipelineDocRef, feed, priority, status, count);
        });

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private boolean isUsed(final Set<String> fieldSet,
                           final String[] resultFields,
                           final ExpressionCriteria criteria) {
        return Arrays.stream(resultFields).filter(Objects::nonNull).anyMatch(fieldSet::contains) ||
               ExpressionUtil.termCount(criteria.getExpression(), fieldSet) > 0;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {
        final Set<String> processorFields = Set.of(
                ProcessorTaskFields.PROCESSOR_FILTER_ID.getFldName(),
                ProcessorTaskFields.PROCESSOR_FILTER_PRIORITY.getFldName());

        validateExpressionTerms(criteria.getExpression());

        final String[] fieldNames = fieldIndex.getFields();
        final boolean nodeUsed = isUsed(Set.of(ProcessorTaskFields.NODE_NAME.getFldName()), fieldNames, criteria);
        final boolean feedUsed = isUsed(Set.of(ProcessorTaskFields.FEED.getFldName()), fieldNames, criteria);
        final boolean processorFilterUsed = isUsed(processorFields, fieldNames, criteria);
        final boolean processorUsed =
                isUsed(Set.of(ProcessorTaskFields.PROCESSOR_ID.getFldName()), fieldNames, criteria);
        final boolean pipelineUsed =
                isUsed(Set.of(
                        ProcessorTaskFields.PIPELINE.getFldName(),
                        ProcessorTaskFields.PIPELINE_NAME.getFldName()), fieldNames, criteria);

        final PageRequest pageRequest = criteria.getPageRequest();
        final Condition condition = expressionMapper.apply(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final List<Field<?>> dbFields = new ArrayList<>(valueMapper.getDbFieldsByName(fieldNames));
        final Mapper<?>[] mappers = valueMapper.getMappersForFieldNames(fieldNames);

        JooqUtil.context(processorDbConnProvider, context -> {
            Integer offset = null;
            Integer numberOfRows = null;

            if (pageRequest != null) {
                offset = pageRequest.getOffset();
                numberOfRows = pageRequest.getLength();
            }

            SelectJoinStep<Record> select = context.select(dbFields).from(PROCESSOR_TASK);
            if (nodeUsed) {
                select = select.leftOuterJoin(PROCESSOR_NODE)
                        .on(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.eq(PROCESSOR_NODE.ID));
            }
            if (feedUsed) {
                select = select.leftOuterJoin(PROCESSOR_FEED)
                        .on(PROCESSOR_TASK.FK_PROCESSOR_FEED_ID.eq(PROCESSOR_FEED.ID));
            }
            if (processorFilterUsed || processorUsed || pipelineUsed) {
                select = select.join(PROCESSOR_FILTER)
                        .on(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(PROCESSOR_FILTER.ID));
            }
            if (processorUsed || pipelineUsed) {
                select = select.join(PROCESSOR)
                        .on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID));
            }

            try (final Cursor<?> cursor = select
                    .where(condition)
                    .orderBy(orderFields)
                    .limit(offset, numberOfRows)
                    .fetchLazy()) {

                while (cursor.hasNext()) {
                    final Result<?> result = cursor.fetchNext(BATCH_SIZE);

                    result.forEach(r -> {
                        final Val[] arr = new Val[fieldNames.length];
                        for (int i = 0; i < fieldNames.length; i++) {
                            Val val = ValNull.INSTANCE;
                            final Mapper<?> mapper = mappers[i];
                            if (mapper != null) {
                                val = mapper.map(r);
                            }
                            arr[i] = val;
                        }
                        consumer.accept(Val.of(arr));
                    });
                }
            }
        });
    }

    @Override
    public ResultPage<ProcessorTask> changeTaskStatus(final ExpressionCriteria criteria,
                                                      final String nodeName,
                                                      final TaskStatus status,
                                                      final Long startTime,
                                                      final Long endTime) {
        LOGGER.debug(() -> LogUtil.message(
                "changeTaskStatus() - Changing task status of {} to node={}, status={}",
                criteria, nodeName, status));
        final long now = System.currentTimeMillis();

        final Integer nodeId;
        if (nodeName != null) {
            nodeId = processorNodeCache.getOrCreate(nodeName);
        } else {
            nodeId = null;
        }

        final Condition condition = expressionMapper.apply(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);

        // Do everything within a single transaction.
        final Result<Record> result = JooqUtil.withDeadlockRetries(
                () -> JooqUtil.transactionResult(processorDbConnProvider, context -> {
                    final int count = context
                            .update(PROCESSOR_TASK)
                            .set(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID, nodeId)
                            .set(PROCESSOR_TASK.STATUS, status.getPrimitiveValue())
                            .set(PROCESSOR_TASK.STATUS_TIME_MS, now)
                            .set(PROCESSOR_TASK.START_TIME_MS, startTime)
                            .set(PROCESSOR_TASK.END_TIME_MS, endTime)
                            .where(condition)
                            .execute();

                    LOGGER.debug("count: {}", count);

                    return context
                            .select()
                            .from(PROCESSOR_TASK)
                            .leftOuterJoin(PROCESSOR_NODE).on(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.eq(PROCESSOR_NODE.ID))
                            .leftOuterJoin(PROCESSOR_FEED).on(PROCESSOR_TASK.FK_PROCESSOR_FEED_ID.eq(PROCESSOR_FEED.ID))
                            .join(PROCESSOR_FILTER).on(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(PROCESSOR_FILTER.ID))
                            .join(PROCESSOR_FILTER_TRACKER).on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(
                                    PROCESSOR_FILTER_TRACKER.ID))
                            .join(PROCESSOR).on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                            .where(condition)
                            .orderBy(orderFields)
                            .limit(offset, limit)
                            .fetch();
                }),
                () -> LogUtil.message("Update processor task status to {}, nodeName: {}, criteria: {}",
                        status, nodeName, criteria));
        return convert(criteria, result);
    }

    @Override
    public ProcessorTask changeTaskStatus(final ProcessorTask processorTask,
                                          final String nodeName,
                                          final TaskStatus status,
                                          final Long startTime,
                                          final Long endTime) {
        LOGGER.debug(() -> LogUtil.message(
                "changeTaskStatus({}) - Changing task status on node {}, {}",
                status, nodeName, processorTask));
        final long now = System.currentTimeMillis();

        final Integer nodeId;
        if (nodeName != null) {
            nodeId = processorNodeCache.getOrCreate(nodeName);
        } else {
            nodeId = null;
        }

        // Do everything within a single transaction.
        final ProcessorTaskRecord result = JooqUtil.transactionResultWithOptimisticLocking(
                processorDbConnProvider, context -> {
                    ProcessorTaskRecord record = context.newRecord(PROCESSOR_TASK);

                    try {
                        try {
                            record.from(processorTask);
                            record.setFkProcessorNodeId(nodeId);
                            record.setStatus(status.getPrimitiveValue());
                            record.setStatusTimeMs(now);
                            record.setStartTimeMs(startTime);
                            record.setEndTimeMs(endTime);
                            record.update();

                        } catch (final RuntimeException e) {
                            // Try this operation a few times.
                            boolean success = false;
                            RuntimeException lastError = null;

                            // Try and do this up to 100 times.
                            for (int tries = 0; tries < 100 && !success; tries++) {
                                success = true;

                                try {
                                    if (LOGGER.isDebugEnabled()) {
                                        LOGGER.warn(() -> LogUtil.message(
                                                "changeTaskStatus({}) - {} - Task has changed, attempting reload {}",
                                                status, e.getMessage(), processorTask), e);
                                    } else {
                                        LOGGER.warn(() -> LogUtil.message(
                                                "changeTaskStatus({}) - Task has changed, attempting reload {}",
                                                status, processorTask));
                                    }

                                    final Optional<ProcessorTaskRecord> optTaskRec = context
                                            .selectFrom(PROCESSOR_TASK)
                                            .where(PROCESSOR_TASK.ID.eq(record.getId()))
                                            .fetchOptional();
                                    LOGGER.debug("Actual DB record {}", optTaskRec);

                                    if (optTaskRec.isEmpty()) {
                                        LOGGER.warn(() -> LogUtil.message(
                                                "changeTaskStatus({}) - Task does not exist, " +
                                                "task may have been physically deleted {}",
                                                processorTask));
                                        record = null;
                                    } else if (TaskStatus.DELETED.getPrimitiveValue() == optTaskRec.get().getStatus()) {
                                        LOGGER.warn(() -> LogUtil.message(
                                                "changeTaskStatus({}) - Task has been logically deleted {}",
                                                status,
                                                processorTask));
                                        record = null;
                                    } else {
                                        LOGGER.warn(() -> LogUtil.message(
                                                "changeTaskStatus({}) - Re-loaded stream task {}",
                                                status,
                                                optTaskRec.get()));
                                        record = optTaskRec.get();
                                        record.setFkProcessorNodeId(nodeId);
                                        record.setStatus(status.getPrimitiveValue());
                                        record.setStatusTimeMs(now);
                                        record.setStartTimeMs(startTime);
                                        record.setEndTimeMs(endTime);
                                        record.update();
                                    }
                                } catch (final RuntimeException e2) {
                                    success = false;
                                    lastError = e2;
                                    // Wait before trying this operation again.
                                    Thread.sleep(1000);
                                }
                            }

                            if (!success) {
                                LOGGER.error("Error changing task status to {} for task '{}': {}",
                                        status, processorTask, lastError.getMessage(), lastError);
                            }
                        }
                    } catch (final InterruptedException e) {
                        LOGGER.error(e::getMessage, e);

                        // Continue to interrupt this thread.
                        Thread.currentThread().interrupt();
                    }

                    return record;
                });

        return convert(result,
                nodeName,
                processorTask.getFeedName(),
                processorTask.getProcessorFilter());
    }

    private ProcessorTask convert(final ProcessorTaskRecord record,
                                  final String nodeName,
                                  final String feedName,
                                  final ProcessorFilter processorFilter) {
        if (record == null) {
            return null;
        }

        return new ProcessorTask(
                record.getId(),
                record.getVersion(),
                record.getMetaId(),
                record.getData(),
                nodeName,
                feedName,
                record.getCreateTimeMs(),
                record.getStatusTimeMs(),
                record.getStartTimeMs(),
                record.getEndTimeMs(),
                TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.getStatus()),
                processorFilter);
    }

    private boolean validateExpressionTerms(final ExpressionItem expressionItem) {
        // TODO: 31/10/2022 Ideally this would be done in CommonExpressionMapper but we
        //  seem to have a load of expressions using unsupported conditions so would get
        //  exceptions all over the place.

        if (expressionItem == null) {
            return true;
        } else {
            final Map<String, QueryField> fieldMap = ProcessorTaskFields.getFieldMap();

            return ExpressionUtil.validateExpressionTerms(expressionItem, term -> {
                final QueryField field = fieldMap.get(term.getField());
                if (field == null) {
                    throw new RuntimeException(LogUtil.message("Unknown field {} in term {}, in expression {}",
                            term.getField(), term, expressionItem));
                } else {
                    final boolean isValid = field.supportsCondition(term.getCondition());
                    if (!isValid) {
                        throw new RuntimeException(LogUtil.message("Condition '{}' is not supported by field '{}' " +
                                                                   "of type {}. Term: {}",
                                term.getCondition(),
                                term.getField(),
                                field.getFldType(), term));
                    } else {
                        return true;
                    }
                }
            });
        }
    }

    private List<String> getPipelineUuidsByName(final List<String> pipelineNames) {
        // Can't cache this in a simple map due to pipes being renamed, but
        // docRefInfoService should cache most of this anyway.
        return docRefInfoService.findByNames(PipelineDoc.TYPE, pipelineNames, true)
                .stream()
                .map(DocRef::getUuid)
                .toList();
    }

    @Override
    public int logicalDeleteByProcessorFilterId(final int processorFilterId) {
        final int count = JooqUtil.withDeadlockRetries(
                () ->
                        JooqUtil.contextResult(processorDbConnProvider, context -> context
                                .update(PROCESSOR_TASK)
                                .set(PROCESSOR_TASK.STATUS, TaskStatus.DELETED.getPrimitiveValue())
                                .set(PROCESSOR_TASK.VERSION, PROCESSOR_TASK.VERSION.plus(1))
                                .set(PROCESSOR_TASK.STATUS_TIME_MS, Instant.now().toEpochMilli())
                                .where(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(processorFilterId))
                                .and(PROCESSOR_TASK.STATUS.notIn(
                                        TaskStatus.DELETED.getPrimitiveValue(),
                                        TaskStatus.COMPLETE.getPrimitiveValue(),
                                        TaskStatus.FAILED.getPrimitiveValue()))
                                .execute()),
                () -> "");

        LOGGER.debug("Logically deleted {} processor tasks", count);
        return count;
    }

    @Override
    public int logicalDeleteByProcessorId(final int processorId) {
        final int count = JooqUtil.withDeadlockRetries(
                () -> JooqUtil.contextResult(processorDbConnProvider, context -> {
                    final UpdateConditionStep<ProcessorTaskRecord> query = context
                            .update(PROCESSOR_TASK)
                            .set(PROCESSOR_TASK.STATUS, TaskStatus.DELETED.getPrimitiveValue())
                            .set(PROCESSOR_TASK.VERSION, PROCESSOR_TASK.VERSION.plus(1))
                            .set(PROCESSOR_TASK.STATUS_TIME_MS, Instant.now().toEpochMilli())
                            .where(DSL.exists(
                                    DSL.selectZero()
                                            .from(PROCESSOR_FILTER)
                                            .innerJoin(PROCESSOR)
                                            .on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                                            .where(PROCESSOR.ID.eq(processorId))
                                            .and(PROCESSOR_FILTER.ID.eq(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID))))
                            .and(PROCESSOR_TASK.STATUS.notIn(
                                    TaskStatus.DELETED.getPrimitiveValue(),
                                    TaskStatus.COMPLETE.getPrimitiveValue(),
                                    TaskStatus.FAILED.getPrimitiveValue()));

                    LOGGER.trace("logicalDeleteByProcessorId query:\n{}", query);
                    return query.execute();
                }),
                () -> "Logically delete tasks for processorId: " + processorId
        );
        LOGGER.debug("Logically deleted {} processor tasks", count);
        return count;
    }

    /**
     * Logically delete tasks that are associated with filters that have been logically deleted for longer than the
     * threshold.
     *
     * @param deleteThreshold Only logically delete tasks with an update time older than the threshold.
     * @return The number of logically deleted tasks.
     */
    @Override
    public int logicalDeleteForDeletedProcessorFilters(final Instant deleteThreshold) {
        final List<Integer> result =
                JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select(PROCESSOR_FILTER.ID)
                        .from(PROCESSOR_FILTER)
                        .where(PROCESSOR_FILTER.DELETED.eq(true))
                        .and(PROCESSOR_FILTER.UPDATE_TIME_MS.lessThan(deleteThreshold.toEpochMilli()))
                        .fetch(PROCESSOR_FILTER.ID));

        LOGGER.debug(() ->
                LogUtil.message("Found {} logically deleted filters with an update time older than {}",
                        result.size(), deleteThreshold));

        final AtomicInteger totalCount = new AtomicInteger();
        // Delete one by one.
        result.forEach(processorFilterId -> {
            try {
                final int count = JooqUtil.withDeadlockRetries(() ->
                                JooqUtil.contextResult(processorDbConnProvider, context -> context
                                        .update(PROCESSOR_TASK)
                                        .set(PROCESSOR_TASK.STATUS, TaskStatus.DELETED.getPrimitiveValue())
                                        .set(PROCESSOR_TASK.VERSION, PROCESSOR_TASK.VERSION.plus(1))
                                        .set(PROCESSOR_TASK.STATUS_TIME_MS, Instant.now().toEpochMilli())
                                        .where(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(processorFilterId))
                                        .and(PROCESSOR_TASK.STATUS.notEqual(TaskStatus.DELETED.getPrimitiveValue()))
                                        .execute()),
                        () -> "Logically delete processor tasks for processorFilterId {}");

                LOGGER.debug("Logically deleted {} processor tasks for processorFilterId {}", count, processorFilterId);
                totalCount.addAndGet(count);
            } catch (final DataAccessException e) {
                if (e.getCause() instanceof final SQLIntegrityConstraintViolationException sqlEx) {
                    LOGGER.debug("Expected constraint violation exception: " + sqlEx.getMessage(), e);
                } else {
                    throw e;
                }
            }
        });
        LOGGER.debug(() -> "logicalDeleteForDeletedProcessorFilters returning: " + totalCount.get());

        return totalCount.get();
    }

    /**
     * Physically delete tasks that are logically deleted or complete for longer than the threshold.
     *
     * @param deleteThreshold Only physically delete tasks with an update time older than the threshold.
     * @return The number of physically deleted tasks.
     */
    @Override
    public int physicallyDeleteOldTasks(final Instant deleteThreshold) {
        LOGGER.debug("Deleting old COMPLETE or DELETED processor tasks");
        final AtomicInteger totalDeleteCount = new AtomicInteger();
        final Condition condition = DSL.and(
                PROCESSOR_TASK.STATUS.eq(TaskStatus.COMPLETE.getPrimitiveValue())
                        .or(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())),
                PROCESSOR_TASK.STATUS_TIME_MS.isNull()
                        .or(PROCESSOR_TASK.STATUS_TIME_MS.lessThan(deleteThreshold.toEpochMilli())));
        LOGGER.debug("Using condition: {}", condition);
        while (true) {
            final List<Long> idList = JooqUtil.contextResult(processorDbConnProvider, context ->
                    context
                            .select(PROCESSOR_TASK.ID)
                            .from(PROCESSOR_TASK)
                            .where(condition)
                            .limit(BATCH_SIZE)
                            .fetch()
                            .map(Record1::value1));

            if (NullSafe.isEmptyCollection(idList)) {
                LOGGER.debug("No IDs found, breaking out of loop");
                break;
            }

            // Ideally we would re-use the additional status/time condition as we are doing this
            // outside of a txn so state may have changed, but we don't have code that un-logically-deletes.
            // The where on the idList should mean we don't create any gap-locks or next-key locks which
            // can deadlock updates on this table
            final int count = JooqUtil.contextResult(processorDbConnProvider, context ->
                    context
                            .deleteFrom(PROCESSOR_TASK)
                            .where(PROCESSOR_TASK.ID.in(idList))
                            .execute());

            totalDeleteCount.addAndGet(count);

            LOGGER.debug(() -> LogUtil.message(
                    "Physically deleted a batch of {} processor tasks with status time older than {} " +
                    "for an idList size of {}, totalDeleteCount: {}",
                    count, deleteThreshold, idList.size(), totalDeleteCount));
        }

        LOGGER.debug("Physically deleted {} processor tasks with status time older than {}",
                totalDeleteCount, deleteThreshold);
        return totalDeleteCount.get();
    }

    @Override
    public List<ExistingCreatedTask> findExistingCreatedTasks(final long lastTaskId,
                                                              final int filterId,
                                                              final int limit) {
        final Result<Record2<Long, Long>> records = JooqUtil.contextResult(processorDbConnProvider, context ->
                context
                        .select(PROCESSOR_TASK.ID, PROCESSOR_TASK.META_ID)
                        .from(PROCESSOR_TASK)
                        .where(PROCESSOR_TASK.ID.gt(lastTaskId))
                        .and(PROCESSOR_TASK.STATUS.eq(TaskStatus.CREATED.getPrimitiveValue()))
                        .and(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(filterId))
                        .orderBy(PROCESSOR_TASK.ID)
                        .limit(limit)
                        .fetch());
        return records.map(r -> new ExistingCreatedTask(r.value1(), r.value2()));
    }


    // --------------------------------------------------------------------------------


    private static class CreationState {

        InclusiveRange streamIdRange;
        InclusiveRange streamMsRange;
        InclusiveRange eventIdRange;
        int totalTasksCreated;
        long eventCount;
    }
}
