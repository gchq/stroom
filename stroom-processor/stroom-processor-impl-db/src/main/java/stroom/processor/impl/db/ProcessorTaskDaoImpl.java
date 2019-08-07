package stroom.processor.impl.db;

import org.jooq.BatchBindStep;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.impl.DSL;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
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
import stroom.processor.shared.ProcessorTaskDataSource;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.CriteriaSet;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;
import static stroom.processor.impl.db.jooq.tables.ProcessorNode.PROCESSOR_NODE;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class ProcessorTaskDaoImpl implements ProcessorTaskDao {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskDaoImpl.class);

    private static final int RECENT_STREAM_ID_LIMIT = 10000;

    private static final String LOCK_NAME = "ProcessorTaskManager";

    private static final Function<Record, Processor> RECORD_TO_PROCESSOR_MAPPER = new RecordToProcessorMapper();
    private static final Function<Record, ProcessorFilter> RECORD_TO_PROCESSOR_FILTER_MAPPER = new RecordToProcessorFilterMapper();
    private static final Function<Record, ProcessorFilterTracker> RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER = new RecordToProcessorFilterTrackerMapper();
    private static final Function<Record, ProcessorTask> RECORD_TO_PROCESSOR_TASK_MAPPER = new RecordToProcessorTaskMapper();

    private static final Field<Integer> COUNT = DSL.count();

    private static final Map<String, Field> FIELD_MAP = Map.of(
            ProcessorTaskDataSource.FIELD_ID, PROCESSOR_TASK.ID,
            ProcessorTaskDataSource.FIELD_COUNT, COUNT,
            ProcessorTaskDataSource.FIELD_CREATE_TIME, PROCESSOR_TASK.CREATE_TIME_MS,
            ProcessorTaskDataSource.FIELD_END_TIME_DATE, PROCESSOR_TASK.END_TIME_MS,
            ProcessorTaskDataSource.FIELD_NODE, PROCESSOR_NODE.NAME,
            ProcessorTaskDataSource.FIELD_PIPELINE, PROCESSOR.PIPELINE_UUID,
            ProcessorTaskDataSource.FIELD_POLL_AGE, PROCESSOR_FILTER_TRACKER.LAST_POLL_MS,
            ProcessorTaskDataSource.FIELD_PRIORITY, PROCESSOR_FILTER.PRIORITY,
            ProcessorTaskDataSource.FIELD_START_TIME, PROCESSOR_TASK.START_TIME_MS,
            ProcessorTaskDataSource.FIELD_STATUS, PROCESSOR_TASK.STATUS);


    private static final Field[] PROCESSOR_TASK_COLUMNS = new Field[]{
            PROCESSOR_TASK.VERSION,
            PROCESSOR_TASK.CREATE_TIME_MS,
            PROCESSOR_TASK.STATUS,
            PROCESSOR_TASK.START_TIME_MS,
            PROCESSOR_TASK.FK_PROCESSOR_NODE_ID,
            PROCESSOR_TASK.META_ID,
            PROCESSOR_TASK.DATA,
            PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID};
    private static final Object[] PROCESSOR_TASK_VALUES = new Object[PROCESSOR_TASK_COLUMNS.length];


//            return super.createFieldMap()
//                .add(FindProcessorTaskCriteria.FIELD_CREATE_TIME, TABLE_PREFIX_STREAM_TASK + ProcessorTask.CREATE_MS, "createMs")
//                .add(FindProcessorTaskCriteria.FIELD_START_TIME, TABLE_PREFIX_STREAM_TASK + ProcessorTask.START_TIME_MS, "startTimeMs")
//                .add(FindProcessorTaskCriteria.FIELD_END_TIME_DATE, TABLE_PREFIX_STREAM_TASK + ProcessorTask.END_TIME_MS, "endTimeMs")
//                .add(FindProcessorTaskCriteria.FIELD_FEED_NAME, "F_NAME", "stream.feed.name")
//                .add(FindProcessorTaskCriteria.FIELD_PRIORITY, "PRIORITY_1", "processorFilter.priority")
//                .add(FindProcessorTaskCriteria.FIELD_PIPELINE_UUID, "P_NAME", "processorFilter.streamProcessor.pipeline.uuid")
//                .add(FindProcessorTaskCriteria.FIELD_STATUS, "STAT_ID1", "pstatus")
//                .add(FindProcessorTaskCriteria.FIELD_COUNT, SQLNameConstants.COUNT, "NA")
//                .add(FindProcessorTaskCriteria.FIELD_NODE, null, "node.name");


//    private static final SqlBuilder MAX_STREAM_ID_SQL;
//
//    static {
//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT MAX(");
//        sql.append(StreamEntity.ID);
//        sql.append(") FROM ");
//        sql.append(StreamEntity.TABLE_NAME);
//        MAX_STREAM_ID_SQL = sql;
//    }

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();
    private final NodeInfo nodeInfo;
    private final ProcessorNodeCache processorNodeCache;
    private final ClusterLockService clusterLockService;
    private final ProcessorFilterTrackerDaoImpl processorFilterTrackerDao;
    private final ProcessorConfig processorConfig;
    private final ConnectionProvider connectionProvider;
    private final ProcessorFilterMarshaller marshaller;

    private final GenericDao<ProcessorTaskRecord, ProcessorTask, Long> genericDao;
    private final ExpressionMapper expressionMapper;

    @Inject
    ProcessorTaskDaoImpl(final NodeInfo nodeInfo,
                         final ProcessorNodeCache processorNodeCache,
                         final ClusterLockService clusterLockService,
                         final ProcessorFilterTrackerDaoImpl processorFilterTrackerDao,
                         final ProcessorConfig processorConfig,
                         final ConnectionProvider connectionProvider,
                         final ProcessorFilterMarshaller marshaller,
                         final ExpressionMapperFactory expressionMapperFactory) {
        this.nodeInfo = nodeInfo;
        this.processorNodeCache = processorNodeCache;
        this.clusterLockService = clusterLockService;
        this.processorFilterTrackerDao = processorFilterTrackerDao;
        this.processorConfig = processorConfig;
        this.connectionProvider = connectionProvider;
        this.marshaller = marshaller;

        this.genericDao = new GenericDao<>(PROCESSOR_TASK, PROCESSOR_TASK.ID, ProcessorTask.class, connectionProvider);
        this.genericDao.setObjectToRecordMapper((processorTask, record) -> {
            record.from(processorTask);
            if (processorTask.getStatus() != null) {
                record.set(PROCESSOR_TASK.STATUS, processorTask.getStatus().getPrimitiveValue());
            }
            record.set(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID, processorTask.getProcessorFilter().getId());
            record.set(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID, processorNodeCache.getOrCreate(processorTask.getNodeName()));
            return record;
        });
        this.genericDao.setRecordToObjectMapper(new RecordToProcessorTaskMapper());

//        this.genericDao.setRecordToObjectMapper(record -> {
//            final ProcessorTask processorTask = new ProcessorTask();
//            processorTask.setId(record.get(PROCESSOR_TASK.ID));
//            processorTask.setVersion(record.get(PROCESSOR_TASK.VERSION));
//            processorTask.setMetaId(record.get(PROCESSOR_TASK.META_ID));
//            processorTask.setData(record.get(PROCESSOR_TASK.DATA));
//            processorTask.setStatus(TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(PROCESSOR_TASK.STATUS)));
//            processorTask.setStartTimeMs(record.get(PROCESSOR_TASK.STATUS_TIME_MS));
//            processorTask.setCreateTimeMs(record.get(PROCESSOR_TASK.CREATE_TIME_MS));
//            processorTask.setStatusTimeMs(record.get(PROCESSOR_TASK.STATUS_TIME_MS));
//            processorTask.setEndTimeMs(record.get(PROCESSOR_TASK.END_TIME_MS));
//            return processorTask;
//        });

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(ProcessorTaskDataSource.CREATE_TIME, PROCESSOR_TASK.CREATE_TIME_MS, DateUtil::parseNormalDateTimeString);
        expressionMapper.map(ProcessorTaskDataSource.CREATE_TIME_MS, PROCESSOR_TASK.CREATE_TIME_MS, Long::valueOf);
        expressionMapper.map(ProcessorTaskDataSource.META_ID, PROCESSOR_TASK.META_ID, Long::valueOf);
        expressionMapper.map(ProcessorTaskDataSource.NODE_NAME, PROCESSOR_NODE.NAME, value -> value);
        expressionMapper.map(ProcessorTaskDataSource.PIPELINE_UUID, PROCESSOR.PIPELINE_UUID, value -> value);
        expressionMapper.map(ProcessorTaskDataSource.PROCESSOR_FILTER_ID, PROCESSOR_FILTER.ID, Integer::valueOf);
        expressionMapper.map(ProcessorTaskDataSource.PROCESSOR_ID, PROCESSOR.ID, Integer::valueOf);
//        expressionMapper.map(ProcessorTaskDataSource.FEED_UUID, PROCESSOR_FILTER.ENABLED, Boolean::valueOf);
        expressionMapper.map(ProcessorTaskDataSource.STATUS, PROCESSOR_TASK.STATUS, value -> TaskStatus.valueOf(value.toUpperCase()).getPrimitiveValue());
        expressionMapper.map(ProcessorTaskDataSource.TASK_ID, PROCESSOR_TASK.ID, Long::valueOf);
    }

    /**
     * Anything that we owned release
     */
    @Override
    public void releaseOwnedTasks() {
        final Integer nodeId = processorNodeCache.getOrCreate(nodeInfo.getThisNodeName());

        final Set<Byte> statusSet = Set.of(
                TaskStatus.UNPROCESSED.getPrimitiveValue(),
                TaskStatus.ASSIGNED.getPrimitiveValue(),
                TaskStatus.PROCESSING.getPrimitiveValue());
        final CriteriaSet<Byte> criteriaSet = new CriteriaSet<>();
        criteriaSet.setSet(statusSet);

        final Collection<Condition> conditions = JooqUtil.conditions(
                Optional.of(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.eq(nodeId)),
                JooqUtil.getSetCondition(PROCESSOR_TASK.STATUS, criteriaSet));

        final int results = JooqUtil.contextResult(connectionProvider, context -> context
                .update(PROCESSOR_TASK)
                .set(PROCESSOR_TASK.STATUS, TaskStatus.UNPROCESSED.getPrimitiveValue())
                .set(PROCESSOR_TASK.STATUS_TIME_MS, System.currentTimeMillis())
                .set(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID, (Integer) null)
                .where(conditions)
                .execute());

        LOGGER.info(LambdaLogUtil.message(
                "doStartup() - Set {} Tasks back to UNPROCESSED (Reprocess), NULL that were UNPROCESSED, ASSIGNED, PROCESSING for node {}",
                results, nodeInfo.getThisNodeName()));

//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("UPDATE ");
//        sql.append(ProcessorTask.TABLE_NAME);
//        sql.append(" SET ");
//        sql.append(ProcessorTask.STATUS);
//        sql.append(" = ");
//        sql.arg(TaskStatus.UNPROCESSED.getPrimitiveValue());
//        sql.append(", ");
//        sql.append(Node.FOREIGN_KEY);
//        sql.append(" = NULL WHERE ");
//        sql.append(Node.FOREIGN_KEY);
//        sql.append(" = ");
//        sql.arg(nodeInfo.getThisNode().getId());
//        final CriteriaSet<TaskStatus> criteriaSet = new CriteriaSet<>();
//        criteriaSet.addAll(Arrays.asList(TaskStatus.UNPROCESSED, TaskStatus.ASSIGNED, TaskStatus.PROCESSING));
//        sql.appendPrimitiveValueSetQuery(ProcessorTask.STATUS, criteriaSet);
//
//        final long results = stroomEntityManager.executeNativeUpdate(sql);
//
//        LOGGER.info(
//                "doStartup() - Set {} Tasks back to UNPROCESSED (Reprocess), NULL that were UNPROCESSED, ASSIGNED, PROCESSING for node {}",
//                results, nodeInfo.getThisNodeName());
    }


//    private ExpressionOperator copyExpression(final ExpressionOperator expression) {
//        ExpressionOperator.Builder builder;
//
//        if (expression != null) {
//            builder = new ExpressionOperator.Builder(expression.getOp());
//
//            if (expression.enabled() && expression.getChildren() != null) {
//                addChildren(builder, expression);
//            }
//
//        } else {
//            builder = new ExpressionOperator.Builder(Op.AND);
//        }
//
//        final ExpressionOperator.Builder or = new ExpressionOperator.Builder(Op.OR)
//                .addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.LOCKED.getDisplayValue())
//                .addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue());
//        builder.addOperator(or.build());
//        return builder.build();
//    }
//
//    private void addChildren(final ExpressionOperator.Builder builder, final ExpressionOperator parent) {
//        for (final ExpressionItem item : parent.getChildren()) {
//            if (item.enabled()) {
//                if (item instanceof ExpressionOperator) {
//                    final ExpressionOperator expressionOperator = (ExpressionOperator) item;
//                    final ExpressionOperator.Builder child = new ExpressionOperator.Builder(Op.OR);
//                    addChildren(child, expressionOperator);
//                    builder.addOperator(child.build());
//                } else if (item instanceof ExpressionTerm) {
//                    final ExpressionTerm expressionTerm = (ExpressionTerm) item;
//
//                    // Don't copy stream status terms as we are going to set them later.
//                    if (!StreamDataSource.STATUS.equals(expressionTerm.getField())) {
//                        if (Condition.IN_DICTIONARY.equals(expressionTerm.getCondition())) {
//                            builder.addTerm(expressionTerm.getField(), expressionTerm.getCondition(), expressionTerm.getDictionary());
//                        } else {
//                            builder.addTerm(expressionTerm.getField(), expressionTerm.getCondition(), expressionTerm.getValue());
//                        }
//                    }
//                }
//            }
//        }
//    }

    /**
     * Create new tasks for the specified filter and add them to the queue.
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
     * @return A list of tasks that we have created and that are owned by this
     * node and available to be handed to workers (i.e. their associated
     * streams are not locked).
     */
    @Override
    public void createNewTasks(final ProcessorFilter filter,
                               final ProcessorFilterTracker tracker,
                               final long streamQueryTime,
                               final Map<Meta, InclusiveRanges> streams,
                               final String thisNodeName,
                               final Long maxMetaId,
                               final boolean reachedLimit,
                               final Consumer<CreatedTasks> consumer) {
        final Integer nodeId = processorNodeCache.getOrCreate(thisNodeName);

        // Lock the cluster so that only this node can create tasks for this
        // filter at this time.
        clusterLockService.lock(LOCK_NAME, () -> {
            // Do everything within a single transaction.
            JooqUtil.transaction(connectionProvider, context -> {
                List<ProcessorTask> availableTaskList = Collections.emptyList();
                int availableTasksCreated = 0;
                int totalTasksCreated = 0;
                long eventCount = 0;

                // Get the current time.
                final long streamTaskCreateMs = System.currentTimeMillis();

                InclusiveRange streamIdRange = null;
                InclusiveRange streamMsRange = null;
                InclusiveRange eventIdRange = null;

                if (streams.size() > 0) {
                    //                    final Field[] columns = new Field[] {
                    //                            PROCESSOR_TASK.VERSION,
                    //                            PROCESSOR_TASK.CREATE_TIME_MS,
                    //                            PROCESSOR_TASK.STATUS,
                    //                            PROCESSOR_TASK.START_TIME_MS,
                    //                            PROCESSOR_TASK.NODE_NAME,
                    //                            PROCESSOR_TASK.STREAM_ID,
                    //                            PROCESSOR_TASK.DATA,
                    //                            PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID};

                    BatchBindStep batchBindStep = null;
                    int rowCount = 0;

                    for (final Entry<Meta, InclusiveRanges> entry : streams.entrySet()) {
                        rowCount++;

                        if (batchBindStep == null) {
                            batchBindStep = context
                                    .batch(
                                            context
                                                    .insertInto(PROCESSOR_TASK)
                                                    .columns(PROCESSOR_TASK_COLUMNS)
                                                    .values(PROCESSOR_TASK_VALUES));
                        }

                        final Meta meta = entry.getKey();
                        final InclusiveRanges eventRanges = entry.getValue();

                        String eventRangeData = null;
                        if (eventRanges != null) {
                            eventRangeData = eventRanges.rangesToString();
                            eventCount += eventRanges.count();
                        }

                        // Update the max event id if this stream id is greater than
                        // any we have seen before.
                        if (streamIdRange == null || meta.getId() > streamIdRange.getMax()) {
                            if (eventRanges != null) {
                                eventIdRange = eventRanges.getOuterRange();
                            } else {
                                eventIdRange = null;
                            }
                        }

                        streamIdRange = InclusiveRange.extend(streamIdRange, meta.getId());
                        streamMsRange = InclusiveRange.extend(streamMsRange, meta.getCreateMs());

                        final Object[] BindValues = new Object[PROCESSOR_TASK_COLUMNS.length];

                        BindValues[0] = 1; //version
                        BindValues[1] = streamTaskCreateMs; //create_ms
                        BindValues[2] = TaskStatus.UNPROCESSED.getPrimitiveValue(); //stat
                        BindValues[3] = streamTaskCreateMs; //stat_ms

                        if (Status.UNLOCKED.equals(meta.getStatus())) {
                            // If the stream is unlocked then take ownership of the
                            // task, i.e. set the node to this node.
                            BindValues[4] = nodeId; //fk_node_id
                            availableTasksCreated++;
                        }
                        BindValues[5] = meta.getId(); //fk_strm_id
                        if (eventRangeData != null && !eventRangeData.isEmpty()) {
                            BindValues[6] = eventRangeData; //dat
                        }
                        BindValues[7] = filter.getId(); //fk_strm_proc_filt_id

                        batchBindStep.bind(BindValues);

                        // Execute insert if we have reached batch size.
                        if (rowCount >= processorConfig.getDatabaseMultiInsertMaxBatchSize()) {
                            executeInsert(batchBindStep, rowCount);
                            rowCount = 0;
                            batchBindStep = null;
                        }
                    }

                    // Do final execution.
                    if (batchBindStep != null) {
                        executeInsert(batchBindStep, rowCount);
                        rowCount = 0;
                        batchBindStep = null;
                    }

                    totalTasksCreated = streams.size();

                    // Select them back
                    final ExpressionOperator expression = new ExpressionOperator.Builder()
                            .addTerm(ProcessorTaskDataSource.NODE_NAME, ExpressionTerm.Condition.EQUALS, thisNodeName)
                            .addTerm(ProcessorTaskDataSource.CREATE_TIME_MS, ExpressionTerm.Condition.EQUALS, streamTaskCreateMs)
                            .addTerm(ProcessorTaskDataSource.STATUS, ExpressionTerm.Condition.EQUALS, TaskStatus.UNPROCESSED.getDisplayValue())
                            .addTerm(ProcessorTaskDataSource.PROCESSOR_FILTER_ID, ExpressionTerm.Condition.EQUALS, filter.getId())
                            .build();
                    final ExpressionCriteria findStreamTaskCriteria = new ExpressionCriteria(expression);
//                    findStreamTaskCriteria.obtainNodeNameCriteria().setString(thisNodeName);
//                    findStreamTaskCriteria.setCreateMs(streamTaskCreateMs);
//                    findStreamTaskCriteria.obtainTaskStatusSet().add(TaskStatus.UNPROCESSED);
//                    findStreamTaskCriteria.obtainProcessorFilterIdSet().add(filter.getId());
                    availableTaskList = find(context, findStreamTaskCriteria);

                    taskStatusTraceLog.createdTasks(ProcessorTaskDaoImpl.class, availableTaskList);

                    // Ensure that the select has got back the stream tasks that we
                    // have just inserted. If it hasn't this would be very bad.
                    if (availableTaskList.size() != availableTasksCreated) {
                        throw new RuntimeException("Unexpected number of stream tasks selected back after insertion.");
                    }
                }

                // Anything created?
                if (totalTasksCreated > 0) {
                    LOGGER.debug(LambdaLogUtil.message("processProcessorFilter() - Created {} tasks ({} available) in the range {}", totalTasksCreated, availableTasksCreated, streamIdRange));

                    // If we have never created tasks before or the last poll gave
                    // us no tasks then start to report a new creation range.
                    if (tracker.getMinMetaCreateMs() == null || (tracker.getLastPollTaskCount() != null
                            && tracker.getLastPollTaskCount().longValue() == 0L)) {
                        tracker.setMinMetaCreateMs(streamMsRange.getMin());
                    }
                    // Report where we have got to.
                    tracker.setMetaCreateMs(streamMsRange.getMax());

                    // Only create tasks for streams with an id 1 or more greater
                    // than the greatest stream id we have created tasks for this
                    // time round in future.
                    if (eventIdRange != null) {
                        tracker.setMinMetaId(streamIdRange.getMax());
                        tracker.setMinEventId(eventIdRange.getMax() + 1);
                    } else {
                        tracker.setMinMetaId(streamIdRange.getMax() + 1);
                        tracker.setMinEventId(0L);
                    }

                } else {
                    // We have completed all tasks so update the window to be from
                    // now
                    tracker.setMinMetaCreateMs(streamTaskCreateMs);

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
                    if (totalTasksCreated > 0) {
                        tracker.setMetaCount(tracker.getMetaCount() + totalTasksCreated);
                    }
                } else {
                    tracker.setMetaCount((long) totalTasksCreated);
                }
                if (eventCount > 0) {
                    if (tracker.getEventCount() != null) {
                        tracker.setEventCount(tracker.getEventCount() + eventCount);
                    } else {
                        tracker.setEventCount(eventCount);
                    }
                }

                tracker.setLastPollMs(streamTaskCreateMs);
                tracker.setLastPollTaskCount(totalTasksCreated);
                tracker.setStatus(null);

                // Has this filter finished creating tasks for good, i.e. is there
                // any possibility of getting more tasks in future?
                if (tracker.getMaxMetaCreateMs() != null && tracker.getMetaCreateMs() != null
                        && tracker.getMetaCreateMs() > tracker.getMaxMetaCreateMs()) {
                    LOGGER.info(LambdaLogUtil.message("processProcessorFilter() - Finished task creation for bounded filter {}", filter));
                    tracker.setStatus(ProcessorFilterTracker.COMPLETE);
                }

                // Save the tracker state within the transaction.
                processorFilterTrackerDao.update(context, tracker);

                consumer.accept(new CreatedTasks(availableTaskList, availableTasksCreated, totalTasksCreated, eventCount));
            });
            //            } catch (final RuntimeException e) {
            //                LOGGER.error("createNewTasks", e);
            //            }
        });
    }

    private void executeInsert(final BatchBindStep batchBindStep, final int rowCount) {
        try {
            if (LOGGER.isDebugEnabled()) {
                final Instant startTime = Instant.now();
                batchBindStep.execute();
                LOGGER.debug(LambdaLogUtil.message("execute completed in %s for %s rows",
                        Duration.between(startTime, Instant.now()), rowCount));
            } else {
                batchBindStep.execute();
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }


//    public ProcessorTaskManagerRecentStreamDetails getRecentStreamInfo(
//            final ProcessorTaskManagerRecentStreamDetails lastRecent) {
//        ProcessorTaskManagerRecentStreamDetails recentStreamInfo = new ProcessorTaskManagerRecentStreamDetails(lastRecent,
//                getMaxStreamId());
//        if (recentStreamInfo.hasRecentDetail()) {
//            // Only do this check if not that many streams have come in.
//            if (recentStreamInfo.getRecentStreamCount() > RECENT_STREAM_ID_LIMIT) {
//                // Forget the history and start again.
//                recentStreamInfo = new ProcessorTaskManagerRecentStreamDetails(null, recentStreamInfo.getMaxStreamId());
//            } else {
//                final SqlBuilder sql = new SqlBuilder();
//                sql.append("SELECT DISTINCT(");
//                sql.append(FeedEntity.FOREIGN_KEY);
//                sql.append("), 'A' FROM ");
//                sql.append(StreamEntity.TABLE_NAME);
//                sql.append(" WHERE ");
//                sql.append(StreamEntity.ID);
//                sql.append(" > ");
//                sql.arg(recentStreamInfo.getRecentStreamId());
//                sql.append(" AND ");
//                sql.append(StreamEntity.ID);
//                sql.append(" <= ");
//                sql.arg(recentStreamInfo.getMaxStreamId());
//
//                // Find out about feeds that have come in recently
//                @SuppressWarnings("unchecked") final List<Object[]> resultSet = stroomEntityManager.executeNativeQueryResultList(sql);
//
//                for (final Object[] row : resultSet) {
//                    recentStreamInfo.addRecentFeedId(((Number) row[0]).longValue());
//                }
//            }
//        }
//        return recentStreamInfo;
//    }
//
//    private long getMaxStreamId() {
//        return stroomEntityManager.executeNativeQueryLongResult(MAX_STREAM_ID_SQL);
//    }


    //    @Override
//    public Class<ProcessorTask> getEntityClass() {
//        return ProcessorTask.class;
//    }
//
//    @Override


//getSqlFieldMap().getSqlFieldMap(), criteria, null);
//            return getEntityManager().executeNativeQuerySummaryDataResult(sql, 3);
//
//
//;
//
//        return security.secureResult(permission(), () -> {
//            final SqlBuilder sql = new SqlBuilder();
//            sql.append("SELECT D.* FROM (");
//            sql.append("SELECT 0");
////            sql.append(StreamProcessor.PIPELINE_UUID);
////            sql.append(" PIPE_UUID,");
//            sql.append(", SP.");
//            sql.append(Processor.PIPELINE_UUID);
//            sql.append(" PIPE_UUID,");
////            sql.append(" F.");
////            sql.append(FeedEntity.ID);
////            sql.append(" FEED_ID, F.");
////            sql.append(SQLNameConstants.NAME);
////            sql.append(" F_NAME,");
//            sql.append(" SPF.");
//            sql.append(ProcessorFilter.PRIORITY);
//            sql.append(" PRIORITY_1, SPF.");
//            sql.append(ProcessorFilter.PRIORITY);
//            sql.append(" PRIORITY_2, ST.");
//            sql.append(ProcessorTask.STATUS);
//            sql.append(" STAT_ID1, ST.");
//            sql.append(ProcessorTask.STATUS);
//            sql.append(" STAT_ID2, COUNT(*) AS ");
//            sql.append(SQLNameConstants.COUNT);
//            sql.append(" FROM ");
//            sql.append(ProcessorTask.TABLE_NAME);
//            sql.append(" ST JOIN ");
////            sql.append(StreamEntity.TABLE_NAME);
////            sql.append(" S ON (S.");
////            sql.append(StreamEntity.ID);
////            sql.append(" = ST.");
////            sql.append(StreamEntity.FOREIGN_KEY);
////            sql.append(") JOIN ");
////            sql.append(FeedEntity.TABLE_NAME);
////            sql.append(" F ON (F.");
////            sql.append(FeedEntity.ID);
////            sql.append(" = S.");
////            sql.append(FeedEntity.FOREIGN_KEY);
////            sql.append(") JOIN ");
//            sql.append(ProcessorFilter.TABLE_NAME);
//            sql.append(" SPF ON (SPF.");
//            sql.append(ProcessorFilter.ID);
//            sql.append(" = ST.");
//            sql.append(ProcessorFilter.FOREIGN_KEY);
//            sql.append(") JOIN ");
//            sql.append(Processor.TABLE_NAME);
//            sql.append(" SP ON (SP.");
//            sql.append(Processor.ID);
//            sql.append(" = SPF.");
//            sql.append(Processor.FOREIGN_KEY);
//            sql.append(")");
//            sql.append(" WHERE 1=1");
//
////            sql.appendPrimitiveValueSetQuery("S." + StreamEntity.STATUS, StreamStatusId.convertStatusSet(criteria.getStatusSet()));
//            sql.appendDocRefSetQuery("SP." + Processor.PIPELINE_UUID, criteria.getPipelineUuidCriteria());
////            sql.appendEntityIdSetQuery("F." + BaseEntity.ID, feedService.convertNameSet(criteria.getFeedNameSet()));
//
//            sql.append(" GROUP BY PIPE_UUID, PRIORITY_1, STAT_ID1");
//            sql.append(") D");
//
//            sql.appendOrderBy(getSqlFieldMap().getSqlFieldMap(), criteria, null);
//
//            return getEntityManager().executeNativeQuerySummaryDataResult(sql, 3);
//        });
//    }
//
//    public BaseResultList<SummaryDataRow> executeNativeQuerySummaryDataResult(final List<Object[]> list, final int numberKeys) {
//        final ArrayList<SummaryDataRow> summaryData = new ArrayList<>();
//
//        for (final Object[] row : list) {
//            final SummaryDataRow summaryDataRow = new SummaryDataRow();
//            int pos = 0;
//            for (int i = 0; i < numberKeys; i++) {
//                final Object key = row[pos++];
//                final Object value = row[pos++];
//                summaryDataRow.getKey().add(((Number) key).longValue());
//                summaryDataRow.getLabel().add((String.valueOf(value)));
//            }
//            summaryDataRow.setCount(((Number) row[pos++]).longValue());
//            summaryData.add(summaryDataRow);
//        }
//
//        return BaseResultList.createUnboundedList(summaryData);
//    }
//
//    @Override
//    public FindProcessorTaskCriteria createCriteria() {
//        return new FindProcessorTaskCriteria();
//    }

//    @Override
//    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindProcessorTaskCriteria criteria) {
//        CriteriaLoggingUtil.appendCriteriaSet(items, "streamTaskStatusSet", criteria.getTaskStatusSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "streamIdSet", criteria.getMetaIdSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "nodeIdSet", criteria.getNodeIdSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "streamTaskIdSet", criteria.getProcessorTaskIdSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "processorFilterIdSet", criteria.getProcessorFilterIdSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "statusSet", criteria.getStatusSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "pipelineSet", criteria.getPipelineUuidCriteria());
////        CriteriaLoggingUtil.appendCriteriaSet(items, "feedNameSet", criteria.getFeedNameSet());
//        CriteriaLoggingUtil.appendDateTerm(items, "createMs", criteria.getCreateTimeMs());
//        CriteriaLoggingUtil.appendRangeTerm(items, "createPeriod", criteria.getCreatePeriod());
////        CriteriaLoggingUtil.appendRangeTerm(items, "effectivePeriod", criteria.getEffectivePeriod());
//        super.appendCriteria(items, criteria);
//    }

//    @Override
//    protected QueryAppender<ProcessorTask, FindProcessorTaskCriteria> createQueryAppender(StroomEntityManager entityManager) {
//        return new StreamTaskServiceImpl.StreamTaskQueryAppender(entityManager);
//    }
//
//    @Override
//    protected FieldMap createFieldMap() {
//        return super.createFieldMap()
//                .add(FindProcessorTaskCriteria.FIELD_CREATE_TIME, TABLE_PREFIX_STREAM_TASK + ProcessorTask.CREATE_MS, "createMs")
//                .add(FindProcessorTaskCriteria.FIELD_START_TIME, TABLE_PREFIX_STREAM_TASK + ProcessorTask.START_TIME_MS, "startTimeMs")
//                .add(FindProcessorTaskCriteria.FIELD_END_TIME_DATE, TABLE_PREFIX_STREAM_TASK + ProcessorTask.END_TIME_MS, "endTimeMs")
//                .add(FindProcessorTaskCriteria.FIELD_FEED_NAME, "F_NAME", "stream.feed.name")
//                .add(FindProcessorTaskCriteria.FIELD_PRIORITY, "PRIORITY_1", "processorFilter.priority")
//                .add(FindProcessorTaskCriteria.FIELD_PIPELINE_UUID, "P_NAME", "processorFilter.streamProcessor.pipeline.uuid")
//                .add(FindProcessorTaskCriteria.FIELD_STATUS, "STAT_ID1", "pstatus")
//                .add(FindProcessorTaskCriteria.FIELD_COUNT, SQLNameConstants.COUNT, "NA")
//                .add(FindProcessorTaskCriteria.FIELD_NODE, null, "node.name");
//    }
//
//    @Override
//    protected String permission() {
//        return PermissionNames.MANAGE_PROCESSORS_PERMISSION;
//    }
//
//    private class StreamTaskQueryAppender extends QueryAppender<ProcessorTask, FindProcessorTaskCriteria> {
//
//        StreamTaskQueryAppender(final StroomEntityManager entityManager) {
//            super(entityManager);
//        }
//
//        @Override
//        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
//            super.appendBasicJoin(sql, alias, fetchSet);
//
//            if (fetchSet != null) {
//                if (fetchSet.contains(Node.ENTITY_TYPE)) {
//                    sql.append(" LEFT JOIN FETCH " + alias + ".node AS node");
//                }
//                if (fetchSet.contains(ProcessorFilter.ENTITY_TYPE) || fetchSet.contains(Processor.ENTITY_TYPE)
//                        || fetchSet.contains(PipelineDoc.DOCUMENT_TYPE)) {
//                    sql.append(" JOIN FETCH " + alias + ".processorFilter AS spf");
//                }
//                if (fetchSet.contains(Processor.ENTITY_TYPE) || fetchSet.contains(PipelineDoc.DOCUMENT_TYPE)) {
//                    sql.append(" JOIN FETCH spf.streamProcessor AS sp");
//                }
////                if (fetchSet.contains(StreamEntity.ENTITY_TYPE)) {
////                    sql.append(" JOIN FETCH " + alias + ".stream AS s");
////                }
////                if (fetchSet.contains(FeedEntity.ENTITY_TYPE)) {
////                    sql.append(" JOIN FETCH s.feed AS f");
////                }
////                if (fetchSet.contains(StreamTypeEntity.ENTITY_TYPE)) {
////                    sql.append(" JOIN FETCH s.streamType AS st");
////                }
//            }
//        }
//
//        @Override
//        protected void appendBasicCriteria(final HqlBuilder sql,
//                                           final String alias,
//                                           final FindProcessorTaskCriteria criteria) {
//            super.appendBasicCriteria(sql, alias, criteria);
//
//            // Append all the criteria
//            sql.appendPrimitiveValueSetQuery(alias + ".pstatus", criteria.getTaskStatusSet());
//
//            sql.appendCriteriaSetQuery(alias + ".id", criteria.getProcessorTaskIdSet());
//
//            sql.appendCriteriaSetQuery(alias + ".node.id", criteria.getNodeIdSet());
//
//            sql.appendDocRefSetQuery(alias + ".processorFilter.streamProcessor.pipelineUuid",
//                    criteria.obtainPipelineSet());
//
//            sql.appendCriteriaSetQuery(alias + ".processorFilter.id", criteria.getProcessorFilterIdSet());
//
//            sql.appendValueQuery(alias + ".createMs", criteria.getCreateTimeMs());
//
////            if (criteria.getStatusSet() != null || criteria.getFeedIdSet() != null || criteria.getPipelineUuidCriteria() != null) {
//            sql.appendCriteriaSetQuery(alias + ".streamId", criteria.getMetaIdSet());
//
////            sql.appendCriteriaSetQuery(alias + ".stream.streamType", streamTypeService.convertNameSet(criteria.getStreamTypeNameSet()));
////
////            sql.appendPrimitiveValueSetQuery(alias + ".stream.pstatus", StreamStatusId.convertStatusSet(criteria.getStatusSet()));
//
////            sql.appendEntityIdSetQuery(alias + ".processorFilter.streamProcessor.pipeline",
////                    criteria.getPipelineUuidCriteria());
////
////                sql.appendEntityIdSetQuery(alias + ".processorFilter.streamProcessor",
////                        criteria.getStreamProcessorIdSet());
////
////            sql.appendEntityIdSetQuery(alias + ".stream.feed", feedService.convertNameSet(criteria.getFeedNameSet()));
//
//            sql.appendRangeQuery(alias + ".createMs", criteria.getCreatePeriod());
////            sql.appendRangeQuery(alias + ".stream.effectiveMs", criteria.getEffectivePeriod());
//        }
//    }


    private Optional<ProcessorTask> fetch(final DSLContext context, final ProcessorTask processorTask) {
        return genericDao.fetch(context, processorTask.getId()).map(p -> decorate(p, processorTask));
    }

    private ProcessorTask update(final DSLContext context, final ProcessorTask processorTask) {
        return decorate(genericDao.update(context, processorTask), processorTask);
    }

    private ProcessorTask decorate(final ProcessorTask result, final ProcessorTask original) {
        result.setNodeName(original.getNodeName());
        result.setProcessorFilter(original.getProcessorFilter());
        return result;
    }

    @Override
    public BaseResultList<ProcessorTask> find(final ExpressionCriteria criteria) {
        return JooqUtil.contextResult(connectionProvider, context -> find(context, criteria));
    }

    BaseResultList<ProcessorTask> find(final DSLContext context, final ExpressionCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());
//        final Collection<Condition> conditions = convertCriteria(criteria);

        final OrderField[] orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);

        final Map<Integer, ProcessorFilter> processorFilterCache = new HashMap<>();

        final List<ProcessorTask> list = context
                .select()
                .from(PROCESSOR_TASK)
                .leftOuterJoin(PROCESSOR_NODE).on(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.eq(PROCESSOR_NODE.ID))
                .join(PROCESSOR_FILTER).on(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(PROCESSOR_FILTER.ID))
                .join(PROCESSOR_FILTER_TRACKER).on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                .join(PROCESSOR).on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                .where(condition)
                .orderBy(orderFields)
                .fetch()
                .map(record -> {
                    final Integer processorFilterId = record.get(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID);
                    final ProcessorFilter processorFilter = processorFilterCache.computeIfAbsent(processorFilterId, pfid -> {
                        final Processor processor = RECORD_TO_PROCESSOR_MAPPER.apply(record);
                        final ProcessorFilterTracker processorFilterTracker = RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER.apply(record);

                        final ProcessorFilter filter = RECORD_TO_PROCESSOR_FILTER_MAPPER.apply(record);
                        filter.setProcessor(processor);
                        filter.setProcessorFilterTracker(processorFilterTracker);
                        return marshaller.unmarshal(filter);
                    });

                    final ProcessorTask processorTask = RECORD_TO_PROCESSOR_TASK_MAPPER.apply(record);
                    processorTask.setProcessorFilter(marshaller.unmarshal(processorFilter));

                    return processorTask;
                });

        return BaseResultList.createCriterialBasedList(list, criteria);
    }

    @Override
    public BaseResultList<ProcessorTaskSummary> findSummary(final ExpressionCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());
//        final Collection<Condition> conditions = convertCriteria(criteria);

        final OrderField[] orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);

        final List<ProcessorTaskSummary> list = JooqUtil.contextResult(connectionProvider, context -> context
                .select(
                        PROCESSOR.PIPELINE_UUID,
                        PROCESSOR_FILTER.PRIORITY,
                        PROCESSOR_TASK.STATUS,
                        COUNT
                )
                .from(PROCESSOR_TASK)
                .leftOuterJoin(PROCESSOR_NODE).on(PROCESSOR_TASK.FK_PROCESSOR_NODE_ID.eq(PROCESSOR_NODE.ID))
                .join(PROCESSOR_FILTER).on(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(PROCESSOR_FILTER.ID))
                .join(PROCESSOR).on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                .where(condition)
                .groupBy(PROCESSOR.PIPELINE_UUID, PROCESSOR_FILTER.PRIORITY, PROCESSOR_TASK.STATUS)
                .orderBy(orderFields)
                .fetch()
                .map(record -> {
                    final String pipelineUuid = record.get(PROCESSOR.PIPELINE_UUID);
                    final int priority = record.get(PROCESSOR_FILTER.PRIORITY);
                    final TaskStatus status = TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(PROCESSOR_TASK.STATUS));
                    final int count = record.get(COUNT);

                    // TODO : @66 Do something about the fact that feed is missing from processor tasks.
                    return new ProcessorTaskSummary(new DocRef("Pipeline", pipelineUuid), null, priority, status, count);
                }));

        return BaseResultList.createUnboundedList(list);
    }

//    private Collection<Condition> convertCriteria(final FindProcessorTaskCriteria criteria) {
//        return JooqUtil.conditions(
//                JooqUtil.getSetCondition(PROCESSOR_TASK.STATUS, CriteriaSet.convert(criteria.getTaskStatusSet(), TaskStatus::getPrimitiveValue)),
//                JooqUtil.getSetCondition(PROCESSOR_TASK.ID, criteria.getProcessorTaskIdSet()),
//                JooqUtil.getStringCondition(PROCESSOR_NODE.NAME, criteria.getNodeNameCriteria()),
//                JooqUtil.getStringCondition(PROCESSOR.PIPELINE_UUID, criteria.getPipelineUuidCriteria()),
//                JooqUtil.getSetCondition(PROCESSOR_FILTER.ID, criteria.getProcessorFilterIdSet()),
//                Optional.ofNullable(criteria.getCreateMs()).map(PROCESSOR_TASK.CREATE_TIME_MS::eq),
//                JooqUtil.getRangeCondition(PROCESSOR_TASK.CREATE_TIME_MS, criteria.getCreatePeriod()),
//                JooqUtil.getSetCondition(PROCESSOR_TASK.META_ID, criteria.getMetaIdSet()));
//    }

    @Override
    public ProcessorTask changeTaskStatus(final ProcessorTask processorTask,
                                          final String nodeName,
                                          final TaskStatus status,
                                          final Long startTime,
                                          final Long endTime) {
        // Do everything within a single transaction.
        return JooqUtil.transactionResult(connectionProvider, context -> {
            LOGGER.debug(LambdaLogUtil.message("changeTaskStatus() - Changing task status of {} to node={}, status={}", processorTask, nodeName, status));
            final long now = System.currentTimeMillis();

            ProcessorTask result = null;

            try {
                try {
                    modify(processorTask, nodeName, status, now, startTime, endTime);
                    result = update(context, processorTask);

                } catch (final RuntimeException e) {
                    // Try this operation a few times.
                    boolean success = false;
                    RuntimeException lastError = null;

                    // Try and do this up to 100 times.
                    for (int tries = 0; tries < 100 && !success; tries++) {
                        success = true;

                        try {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.warn(LambdaLogUtil.message("changeTaskStatus() - {} - Task has changed, attempting reload {}", e.getMessage(), processorTask), e);
                            } else {
                                LOGGER.warn(LambdaLogUtil.message("changeTaskStatus() - Task has changed, attempting reload {}", processorTask));
                            }

                            final ProcessorTask loaded = fetch(context, processorTask).orElse(null);
                            if (loaded == null) {
                                LOGGER.warn(LambdaLogUtil.message("changeTaskStatus() - Failed to reload task {}", processorTask));
                            } else if (TaskStatus.DELETED.equals(loaded.getStatus())) {
                                LOGGER.warn(LambdaLogUtil.message("changeTaskStatus() - Task has been deleted {}", processorTask));
                            } else {
                                LOGGER.warn(LambdaLogUtil.message("changeTaskStatus() - Loaded stream task {}", loaded));
                                modify(loaded, nodeName, status, now, startTime, endTime);
                                result = update(context, loaded);
                            }
                        } catch (final RuntimeException e2) {
                            success = false;
                            lastError = e2;
                            // Wait before trying this operation again.
                            Thread.sleep(1000);
                        }
                    }

                    if (!success) {
                        LOGGER.error(LambdaLogUtil.message("Error changing task status for task '{}': {}", processorTask, lastError.getMessage()), lastError);
                    }
                }
            } catch (final InterruptedException e) {
                LOGGER.error(e::getMessage, e);

                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();
            }

            return result;
        });
    }

    private void modify(final ProcessorTask processorTask,
                        final String nodeName,
                        final TaskStatus status,
                        final Long statusMs,
                        final Long startTimeMs,
                        final Long endTimeMs) {
        processorTask.setNodeName(nodeName);
        processorTask.setStatus(status);
        processorTask.setStatusTimeMs(statusMs);
        processorTask.setStartTimeMs(startTimeMs);
        processorTask.setEndTimeMs(endTimeMs);
    }
}
