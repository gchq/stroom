package stroom.processor.impl.db;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DateField;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.db.util.ValueMapper;
import stroom.db.util.ValueMapper.Mapper;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.processor.api.InclusiveRanges;
import stroom.processor.api.InclusiveRanges.InclusiveRange;
import stroom.processor.impl.CreatedTasks;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.db.jooq.tables.records.ProcessorTaskRecord;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.common.v2.DateExpressionParser;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import org.jooq.BatchBindStep;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record5;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.inject.Inject;

import static java.util.Map.entry;
import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFeed.PROCESSOR_FEED;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;
import static stroom.processor.impl.db.jooq.tables.ProcessorNode.PROCESSOR_NODE;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class ProcessorTaskDaoImpl implements ProcessorTaskDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskDaoImpl.class);

    private static final Object TASK_CREATION_MONITOR = new Object();

    private static final Function<Record, Processor> RECORD_TO_PROCESSOR_MAPPER = new RecordToProcessorMapper();
    private static final Function<Record, ProcessorFilter> RECORD_TO_PROCESSOR_FILTER_MAPPER =
            new RecordToProcessorFilterMapper();
    private static final Function<Record, ProcessorFilterTracker> RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER =
            new RecordToProcessorFilterTrackerMapper();
    private static final Function<Record, ProcessorTask> RECORD_TO_PROCESSOR_TASK_MAPPER =
            new RecordToProcessorTaskMapper();

    private static final Field<Integer> COUNT = DSL.count();

    private static final Map<String, Field<?>> FIELD_MAP = Map.ofEntries(
            entry(ProcessorTaskFields.FIELD_ID, PROCESSOR_TASK.ID),
            entry(ProcessorTaskFields.FIELD_CREATE_TIME, PROCESSOR_TASK.CREATE_TIME_MS),
            entry(ProcessorTaskFields.FIELD_START_TIME, PROCESSOR_TASK.START_TIME_MS),
            entry(ProcessorTaskFields.FIELD_END_TIME_DATE, PROCESSOR_TASK.END_TIME_MS),
            entry(ProcessorTaskFields.FIELD_FEED, PROCESSOR_FEED.NAME),
            entry(ProcessorTaskFields.FIELD_PRIORITY, PROCESSOR_FILTER.PRIORITY),
            entry(ProcessorTaskFields.FIELD_PIPELINE, PROCESSOR.PIPELINE_UUID),
            entry(ProcessorTaskFields.FIELD_STATUS, PROCESSOR_TASK.STATUS),
            entry(ProcessorTaskFields.FIELD_COUNT, COUNT),
            entry(ProcessorTaskFields.FIELD_NODE, PROCESSOR_NODE.NAME),
            entry(ProcessorTaskFields.FIELD_POLL_AGE, PROCESSOR_FILTER_TRACKER.LAST_POLL_MS)
    );

    private static final Field<?>[] PROCESSOR_TASK_COLUMNS = new Field<?>[]{
            PROCESSOR_TASK.VERSION,
            PROCESSOR_TASK.CREATE_TIME_MS,
            PROCESSOR_TASK.STATUS,
            PROCESSOR_TASK.START_TIME_MS,
            PROCESSOR_TASK.FK_PROCESSOR_NODE_ID,
            PROCESSOR_TASK.FK_PROCESSOR_FEED_ID,
            PROCESSOR_TASK.META_ID,
            PROCESSOR_TASK.DATA,
            PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID};
    private static final Object[] PROCESSOR_TASK_VALUES = new Object[PROCESSOR_TASK_COLUMNS.length];

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();
    private final NodeInfo nodeInfo;
    private final ProcessorNodeCache processorNodeCache;
    private final ProcessorFeedCache processorFeedCache;
    private final ProcessorFilterTrackerDaoImpl processorFilterTrackerDao;
    private final ProcessorConfig processorConfig;
    private final ProcessorDbConnProvider processorDbConnProvider;
    private final ProcessorFilterMarshaller marshaller;
    private final DocRefInfoService docRefInfoService;
    private final ExpressionMapper expressionMapper;
    private final ValueMapper valueMapper;

    @Inject
    ProcessorTaskDaoImpl(final NodeInfo nodeInfo,
                         final ProcessorNodeCache processorNodeCache,
                         final ProcessorFeedCache processorFeedCache,
                         final ProcessorFilterTrackerDaoImpl processorFilterTrackerDao,
                         final ProcessorConfig processorConfig,
                         final ProcessorDbConnProvider processorDbConnProvider,
                         final ProcessorFilterMarshaller marshaller,
                         final ExpressionMapperFactory expressionMapperFactory,
                         final DocRefInfoService docRefInfoService) {
        this.nodeInfo = nodeInfo;
        this.processorNodeCache = processorNodeCache;
        this.processorFeedCache = processorFeedCache;
        this.processorFilterTrackerDao = processorFilterTrackerDao;
        this.processorConfig = processorConfig;
        this.processorDbConnProvider = processorDbConnProvider;
        this.marshaller = marshaller;
        this.docRefInfoService = docRefInfoService;

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(ProcessorTaskFields.CREATE_TIME,
                PROCESSOR_TASK.CREATE_TIME_MS,
                value -> getDate(ProcessorTaskFields.CREATE_TIME, value));
        expressionMapper.map(ProcessorTaskFields.CREATE_TIME_MS, PROCESSOR_TASK.CREATE_TIME_MS, Long::valueOf);
        expressionMapper.map(ProcessorTaskFields.START_TIME,
                PROCESSOR_TASK.START_TIME_MS,
                value -> getDate(ProcessorTaskFields.START_TIME, value));
        expressionMapper.map(ProcessorTaskFields.START_TIME_MS, PROCESSOR_TASK.START_TIME_MS, Long::valueOf);
        expressionMapper.map(ProcessorTaskFields.END_TIME,
                PROCESSOR_TASK.END_TIME_MS,
                value -> getDate(ProcessorTaskFields.END_TIME, value));
        expressionMapper.map(ProcessorTaskFields.END_TIME_MS, PROCESSOR_TASK.END_TIME_MS, Long::valueOf);
        expressionMapper.map(ProcessorTaskFields.STATUS_TIME,
                PROCESSOR_TASK.STATUS_TIME_MS,
                value -> getDate(ProcessorTaskFields.STATUS_TIME, value));
        expressionMapper.map(ProcessorTaskFields.STATUS_TIME_MS, PROCESSOR_TASK.STATUS_TIME_MS, Long::valueOf);
        expressionMapper.map(ProcessorTaskFields.META_ID, PROCESSOR_TASK.META_ID, Long::valueOf);
        expressionMapper.map(ProcessorTaskFields.NODE_NAME, PROCESSOR_NODE.NAME, value -> value);
        expressionMapper.map(ProcessorTaskFields.FEED, PROCESSOR_FEED.NAME, value -> value, true);
        expressionMapper.map(ProcessorTaskFields.PIPELINE, PROCESSOR.PIPELINE_UUID, value -> value);
        expressionMapper.map(ProcessorTaskFields.PROCESSOR_FILTER_ID, PROCESSOR_FILTER.ID, Integer::valueOf);
        expressionMapper.map(ProcessorTaskFields.PROCESSOR_FILTER_PRIORITY,
                PROCESSOR_FILTER.PRIORITY,
                Integer::valueOf);
        expressionMapper.map(ProcessorTaskFields.PROCESSOR_ID, PROCESSOR.ID, Integer::valueOf);
        expressionMapper.map(ProcessorTaskFields.STATUS,
                PROCESSOR_TASK.STATUS,
                value -> TaskStatus.valueOf(value.toUpperCase()).getPrimitiveValue());
        expressionMapper.map(ProcessorTaskFields.TASK_ID, PROCESSOR_TASK.ID, Long::valueOf);

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
        valueMapper.map(ProcessorTaskFields.PROCESSOR_FILTER_ID, PROCESSOR_FILTER.ID, ValInteger::create);
        valueMapper.map(ProcessorTaskFields.PROCESSOR_FILTER_PRIORITY, PROCESSOR_FILTER.PRIORITY, ValInteger::create);
        valueMapper.map(ProcessorTaskFields.PROCESSOR_ID, PROCESSOR.ID, ValInteger::create);
        valueMapper.map(ProcessorTaskFields.STATUS,
                PROCESSOR_TASK.STATUS,
                v -> ValString.create(TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(v).getDisplayValue()));
        valueMapper.map(ProcessorTaskFields.TASK_ID, PROCESSOR_TASK.ID, ValLong::create);
    }

    private long getDate(final DateField field, final String value) {
        try {
            final Optional<ZonedDateTime> optional = DateExpressionParser.parse(value);

            return optional.orElseThrow(() ->
                    new RuntimeException("Expected a standard date value for field \"" + field.getName()
                            + "\" but was given string \"" + value + "\"")).toInstant().toEpochMilli();
        } catch (final Exception e) {
            throw new RuntimeException("Expected a standard date value for field \"" + field.getName()
                    + "\" but was given string \"" + value + "\"", e);
        }
    }

    private Val getPipelineName(final String uuid) {
        String val = uuid;
        if (docRefInfoService != null) {
            val = docRefInfoService.name(new DocRef("Pipeline", uuid)).orElse(uuid);
        }
        return ValString.create(val);
    }

    /**
     * Anything that we owned release. Should be done under cluster lock
     */
    @Override
    public void releaseOwnedTasks() {
        LOGGER.info(() -> "Releasing owned tasks for node " + nodeInfo.getThisNodeName());

        final Integer nodeId = processorNodeCache.getOrCreate(nodeInfo.getThisNodeName());

        final Set<Byte> statusSet = Set.of(
                TaskStatus.UNPROCESSED.getPrimitiveValue(),
                TaskStatus.ASSIGNED.getPrimitiveValue(),
                TaskStatus.PROCESSING.getPrimitiveValue());
        final Selection<Byte> selection = Selection.selectNone();
        selection.setSet(statusSet);

        final Collection<Condition> conditions = JooqUtil.conditions(
                Optional.of(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.eq(nodeId)),
                JooqUtil.getSetCondition(PROCESSOR_TASK.STATUS, selection));

        final int results = JooqUtil.contextResult(processorDbConnProvider, context -> context
                .update(PROCESSOR_TASK)
                .set(PROCESSOR_TASK.STATUS, TaskStatus.UNPROCESSED.getPrimitiveValue())
                .set(PROCESSOR_TASK.STATUS_TIME_MS, System.currentTimeMillis())
                .set(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID, (Integer) null)
                .where(conditions)
                .execute());

        LOGGER.info(() -> "Set " + results + " tasks back to UNPROCESSED (Reprocess), NULL that were " +
                "UNPROCESSED, ASSIGNED, PROCESSING for node " + nodeInfo.getThisNodeName());
    }

    /**
     * Create new tasks for the specified filter and add them to the queue.
     * <p>
     * This must only be done on one node at a time, i.e. under a cluster lock.
     *
     * @param filter          The fitter to create tasks for
     * @param tracker         The tracker that tracks the task creation progress for the
     *                        filter.
     * @param streamQueryTime The time that we queried for streams that match the stream
     *                        processor filter.
     * @param streams         The map of streams and optional event ranges to create stream
     *                        tasks for.
     * @param thisNodeName    This node, the node that will own the created tasks.
     * @param reachedLimit    For search based stream task creation this indicates if we
     *                        have reached the limit of stream tasks created for a single
     *                        search. This limit is imposed to stop search based task
     *                        creation running forever.
     */
    @Override
    public synchronized void createNewTasks(final ProcessorFilter filter,
                                            final ProcessorFilterTracker tracker,
                                            final long streamQueryTime,
                                            final Map<Meta, InclusiveRanges> streams,
                                            final String thisNodeName,
                                            final Long maxMetaId,
                                            final boolean reachedLimit,
                                            final Consumer<CreatedTasks> consumer) {

        // Synchronised to avoid the risk of any table locking when being called concurrently
        // by multiple threads on the master node
        synchronized (TASK_CREATION_MONITOR) {
            final Integer nodeId = processorNodeCache.getOrCreate(thisNodeName);

            // Get the current time.
            final long streamTaskCreateMs = System.currentTimeMillis();

            final ExpressionOperator expression = ExpressionOperator.builder()
                    .addTerm(ProcessorTaskFields.NODE_NAME, ExpressionTerm.Condition.EQUALS, thisNodeName)
                    .addTerm(ProcessorTaskFields.CREATE_TIME_MS,
                            ExpressionTerm.Condition.EQUALS,
                            streamTaskCreateMs)
                    .addTerm(ProcessorTaskFields.STATUS,
                            ExpressionTerm.Condition.EQUALS,
                            TaskStatus.UNPROCESSED.getDisplayValue())
                    .addTerm(ProcessorTaskFields.PROCESSOR_FILTER_ID,
                            ExpressionTerm.Condition.EQUALS,
                            filter.getId())
                    .build();
            final ExpressionCriteria findStreamTaskCriteria = new ExpressionCriteria(expression);
            final Condition condition = expressionMapper.apply(expression);
            final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, findStreamTaskCriteria);

            final CreationState creationState = new CreationState();

            // Create all bind values.
            final Object[][] allBindValues = new Object[streams.entrySet().size()][];
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
                bindValues[1] = streamTaskCreateMs; //create_ms
                bindValues[2] = TaskStatus.UNPROCESSED.getPrimitiveValue(); //stat
                bindValues[3] = streamTaskCreateMs; //stat_ms

                if (Status.UNLOCKED.equals(meta.getStatus())) {
                    // If the stream is unlocked then take ownership of the
                    // task, i.e. set the node to this node.
                    bindValues[4] = nodeId; //fk_node_id
                    creationState.availableTasksCreated++;
                }
                bindValues[5] = processorFeedCache.getOrCreate(meta.getFeedName());
                bindValues[6] = meta.getId(); //fk_strm_id
                if (eventRangeData != null && !eventRangeData.isEmpty()) {
                    bindValues[7] = eventRangeData; //dat
                }
                bindValues[8] = filter.getId(); //fk_strm_proc_filt_id
                allBindValues[rowCount++] = bindValues;
            }

            // Do everything within a single transaction.
            JooqUtil.transaction(processorDbConnProvider, context -> {
                if (allBindValues.length > 0) {
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

                        batchBindStep.bind(bindValues);

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

                    creationState.totalTasksCreated = streams.size();

                    // Select them back
                    final Result<Record> availableTaskList = find(context,
                            condition,
                            orderFields,
                            findStreamTaskCriteria.getPageRequest());
                    creationState.availableTaskList = availableTaskList;

                    taskStatusTraceLog.createdTasks(ProcessorTaskDaoImpl.class, availableTaskList);

                    // Ensure that the select has got back the stream tasks that we
                    // have just inserted. If it hasn't this would be very bad.
                    if (availableTaskList.size() != creationState.availableTasksCreated) {
                        throw new RuntimeException("Unexpected number of stream tasks selected back after insertion.");
                    }
                }

                // Anything created?
                if (creationState.totalTasksCreated > 0) {
                    // Done with if because args are not final so can't use lambda
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

                    // Only create tasks for streams with an id 1 or more greater
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

                tracker.setLastPollMs(streamTaskCreateMs);
                tracker.setLastPollTaskCount(creationState.totalTasksCreated);
                tracker.setStatus(null);

                // If the filter has a max meta creation time then let the tracker know.
                if (filter.getMaxMetaCreateTimeMs() != null && tracker.getMaxMetaCreateMs() == null) {
                    tracker.setMaxMetaCreateMs(filter.getMaxMetaCreateTimeMs());
                }
                // Has this filter finished creating tasks for good, i.e. is there
                // any possibility of getting more tasks in future?
                if (tracker.getMaxMetaCreateMs() != null && tracker.getMetaCreateMs() != null
                        && tracker.getMetaCreateMs() > tracker.getMaxMetaCreateMs()) {
                    LOGGER.info(() ->
                            "processProcessorFilter() - Finished task creation for bounded filter " + filter);
                    tracker.setStatus(ProcessorFilterTracker.COMPLETE);
                }

                // Save the tracker state within the transaction.
                processorFilterTrackerDao.update(context, tracker);
            });

            final List<ProcessorTask> list;
            if (creationState.availableTaskList != null) {
                final ResultPage<ProcessorTask> resultPage = convert(
                        findStreamTaskCriteria,
                        creationState.availableTaskList,
                        new HashMap<>());
                list = resultPage.getValues();
            } else {
                list = Collections.emptyList();
            }

            consumer.accept(new CreatedTasks(
                    list,
                    creationState.availableTasksCreated,
                    creationState.totalTasksCreated,
                    creationState.eventCount));
        }
    }

    private void log(final CreationState creationState,
                     final InclusiveRange streamIdRange) {
        LOGGER.debug(() -> "processProcessorFilter() - Created " +
                creationState.totalTasksCreated +
                " tasks (" +
                creationState.availableTasksCreated +
                " available) in the range " +
                streamIdRange);
    }

    private void executeInsert(final BatchBindStep batchBindStep, final int rowCount) {
        try {
            LOGGER.logDurationIfDebugEnabled(
                    batchBindStep::execute,
                    () -> LogUtil.message("Execute for {} rows", rowCount));
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }
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
        return convert(criteria, result, new HashMap<>());
    }

    private Result<Record> find(final DSLContext context,
                                final Condition condition,
                                final Collection<OrderField<?>> orderFields,
                                final PageRequest pageRequest) {
        final int offset = JooqUtil.getOffset(pageRequest);
        final int limit = JooqUtil.getLimit(pageRequest, true);
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
    }

    private ResultPage<ProcessorTask> convert(final ExpressionCriteria criteria,
                                              final Result<Record> result,
                                              final Map<Integer, ProcessorFilter> processorFilterCache) {
        final List<ProcessorTask> list = result.map(record -> {
            final Integer processorFilterId = record.get(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID);
            final ProcessorFilter processorFilter = processorFilterCache.computeIfAbsent(processorFilterId,
                    pfid -> {
                        final Processor processor = RECORD_TO_PROCESSOR_MAPPER.apply(record);
                        final ProcessorFilterTracker processorFilterTracker =
                                RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER.apply(record);

                        final ProcessorFilter filter = RECORD_TO_PROCESSOR_FILTER_MAPPER.apply(record);
                        filter.setProcessor(processor);
                        filter.setProcessorFilterTracker(processorFilterTracker);
                        return marshaller.unmarshal(filter);
                    });

            final ProcessorTask processorTask = RECORD_TO_PROCESSOR_TASK_MAPPER.apply(record);
            processorTask.setProcessorFilter(marshaller.unmarshal(processorFilter));

            return processorTask;
        });
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public ResultPage<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final PageRequest pageRequest = criteria.getPageRequest();
        final int offset = JooqUtil.getOffset(pageRequest);
        final int limit = JooqUtil.getLimit(pageRequest, true);
        final Result<Record5<String, String, Integer, Byte, Integer>> result =
                JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select(
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
                        .groupBy(PROCESSOR_FEED.NAME,
                                PROCESSOR.PIPELINE_UUID,
                                PROCESSOR_FILTER.PRIORITY,
                                PROCESSOR_TASK.STATUS)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch());

        final List<ProcessorTaskSummary> list = result.map(record -> {
            final String feed = record.get(PROCESSOR_FEED.NAME);
            final String pipelineUuid = record.get(PROCESSOR.PIPELINE_UUID);
            final int priority = record.get(PROCESSOR_FILTER.PRIORITY);
            final TaskStatus status = TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(
                    PROCESSOR_TASK.STATUS));
            final int count = record.get(COUNT);
            DocRef pipelineDocRef = new DocRef("Pipeline", pipelineUuid);
            final Optional<String> pipelineName = docRefInfoService.name(pipelineDocRef);
            if (pipelineName.isPresent()) {
                pipelineDocRef = pipelineDocRef.copy().name(pipelineName.get()).build();
            }
            return new ProcessorTaskSummary(pipelineDocRef, feed, priority, status, count);
        });

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private boolean isUsed(final Set<AbstractField> fieldSet,
                           final List<AbstractField> resultFields,
                           final ExpressionCriteria criteria) {
        return resultFields.stream().filter(Objects::nonNull).anyMatch(fieldSet::contains) ||
                ExpressionUtil.termCount(criteria.getExpression(), fieldSet) > 0;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final AbstractField[] fields,
                       final ValuesConsumer consumer) {
        final Set<AbstractField> processorFields = Set.of(
                ProcessorTaskFields.PROCESSOR_FILTER_ID,
                ProcessorTaskFields.PROCESSOR_FILTER_PRIORITY);

        final List<AbstractField> fieldList = Arrays.asList(fields);
        final boolean nodeUsed = isUsed(Set.of(ProcessorTaskFields.NODE_NAME), fieldList, criteria);
        final boolean feedUsed = isUsed(Set.of(ProcessorTaskFields.FEED), fieldList, criteria);
        final boolean processorFilterUsed = isUsed(processorFields, fieldList, criteria);
        final boolean processorUsed = isUsed(Set.of(ProcessorTaskFields.PROCESSOR_ID), fieldList, criteria);
        final boolean pipelineUsed = isUsed(Set.of(ProcessorTaskFields.PIPELINE), fieldList, criteria);

        final PageRequest pageRequest = criteria.getPageRequest();
        final Condition condition = expressionMapper.apply(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final List<Field<?>> dbFields = new ArrayList<>(valueMapper.getFields(fieldList));
        final Mapper<?>[] mappers = valueMapper.getMappers(fields);

        JooqUtil.context(processorDbConnProvider, context -> {
            Integer offset = null;
            Integer numberOfRows = null;

            if (pageRequest != null) {
                offset = pageRequest.getOffset();
                numberOfRows = pageRequest.getLength();
            }

            var select = context.select(dbFields).from(PROCESSOR_TASK);
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
                    final Result<?> result = cursor.fetchNext(1000);

                    result.forEach(r -> {
                        final Val[] arr = new Val[fields.length];
                        for (int i = 0; i < fields.length; i++) {
                            Val val = ValNull.INSTANCE;
                            final Mapper<?> mapper = mappers[i];
                            if (mapper != null) {
                                val = mapper.map(r);
                            }
                            arr[i] = val;
                        }
                        consumer.add(arr);
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
        final Result<Record> result = JooqUtil.transactionResult(processorDbConnProvider, context -> {
            context
                    .update(PROCESSOR_TASK)
                    .set(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID, nodeId)
                    .set(PROCESSOR_TASK.STATUS, status.getPrimitiveValue())
                    .set(PROCESSOR_TASK.STATUS_TIME_MS, now)
                    .set(PROCESSOR_TASK.START_TIME_MS, startTime)
                    .set(PROCESSOR_TASK.END_TIME_MS, endTime)
                    .where(condition)
                    .execute();

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
        });
        return convert(criteria, result, new HashMap<>());
    }

    @Override
    public ProcessorTask changeTaskStatus(final ProcessorTask processorTask,
                                          final String nodeName,
                                          final TaskStatus status,
                                          final Long startTime,
                                          final Long endTime) {
        LOGGER.debug(() -> LogUtil.message(
                "changeTaskStatus() - Changing task status of {} to node={}, status={}",
                processorTask, nodeName, status));
        final long now = System.currentTimeMillis();

        final Integer nodeId;
        if (nodeName != null) {
            nodeId = processorNodeCache.getOrCreate(nodeName);
        } else {
            nodeId = null;
        }

        // Do everything within a single transaction.
        final ProcessorTaskRecord result = JooqUtil.transactionResult(processorDbConnProvider, context -> {
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
                                        "changeTaskStatus() - {} - Task has changed, attempting reload {}",
                                        e.getMessage(), processorTask), e);
                            } else {
                                LOGGER.warn(() -> LogUtil.message(
                                        "changeTaskStatus() - Task has changed, attempting reload {}", processorTask));
                            }

                            final Optional<ProcessorTaskRecord> optional = context
                                    .selectFrom(PROCESSOR_TASK)
                                    .where(PROCESSOR_TASK.ID.eq(record.getId()))
                                    .fetchOptional();

                            if (optional.isEmpty()) {
                                LOGGER.warn(() -> LogUtil.message(
                                        "changeTaskStatus() - Failed to reload task {}", processorTask));
                            } else if (TaskStatus.DELETED.getPrimitiveValue() == optional.get().getStatus()) {
                                LOGGER.warn(() -> LogUtil.message(
                                        "changeTaskStatus() - Task has been deleted {}", processorTask));
                            } else {
                                LOGGER.warn(() -> LogUtil.message(
                                        "changeTaskStatus() - Loaded stream task {}", optional.get()));
                                record = optional.get();
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
                        LOGGER.error("Error changing task status for task '{}': {}",
                                processorTask, lastError.getMessage(), lastError);
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

    private static class CreationState {

        InclusiveRange streamIdRange;
        InclusiveRange streamMsRange;
        InclusiveRange eventIdRange;
        Result<Record> availableTaskList;
        int availableTasksCreated;
        int totalTasksCreated;
        long eventCount;
    }
}
