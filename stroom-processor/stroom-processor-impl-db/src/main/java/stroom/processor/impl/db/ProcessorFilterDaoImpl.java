package stroom.processor.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.db.jooq.tables.records.ProcessorFilterRecord;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;
import stroom.security.user.api.UserRefLookup;
import stroom.util.AuditUtil;
import stroom.util.exception.DataChangedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.UpdateConditionStep;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class ProcessorFilterDaoImpl implements ProcessorFilterDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorFilterDaoImpl.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ProcessorFilterDaoImpl.class);

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            ProcessorFilterFields.FIELD_ID, PROCESSOR_FILTER.ID);

    private static final Function<Record, Processor> RECORD_TO_PROCESSOR_MAPPER = new RecordToProcessorMapper();
    private final Function<Record, ProcessorFilter> recordToProcessorFilterMapper;
    private static final Function<Record, ProcessorFilterTracker> RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER =
            new RecordToProcessorFilterTrackerMapper();

    private final ProcessorDbConnProvider processorDbConnProvider;
    private final QueryDataSerialiser queryDataXMLSerialiser;
    private final ExpressionMapper expressionMapper;
    private final SecurityContext securityContext;
    private final ProcessorFilterTrackerDaoImpl processorFilterTrackerDaoImpl;
    private final Provider<ProcessorDaoImpl> processorDaoImplProvider;

    @Inject
    ProcessorFilterDaoImpl(final ProcessorDbConnProvider processorDbConnProvider,
                           final ExpressionMapperFactory expressionMapperFactory,
                           final QueryDataSerialiser queryDataXMLSerialiser,
                           final Provider<UserRefLookup> userRefLookupProvider,
                           final SecurityContext securityContext,
                           final ProcessorFilterTrackerDaoImpl processorFilterTrackerDaoImpl,
                           final Provider<ProcessorDaoImpl> processorDaoImplProvider) {
        this.processorDbConnProvider = processorDbConnProvider;
        this.queryDataXMLSerialiser = queryDataXMLSerialiser;
        this.securityContext = securityContext;
        this.processorFilterTrackerDaoImpl = processorFilterTrackerDaoImpl;
        this.processorDaoImplProvider = processorDaoImplProvider;

        recordToProcessorFilterMapper = new RecordToProcessorFilterMapper(
                queryDataXMLSerialiser,
                userRefLookupProvider);

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(ProcessorFilterFields.ID, PROCESSOR_FILTER.ID, Integer::valueOf);
        expressionMapper.map(ProcessorFilterFields.LAST_POLL_MS, PROCESSOR_FILTER_TRACKER.LAST_POLL_MS, Long::valueOf);
        expressionMapper.map(ProcessorFilterFields.PRIORITY, PROCESSOR_FILTER.PRIORITY, Integer::valueOf);
        expressionMapper.map(ProcessorFilterFields.ENABLED, PROCESSOR_FILTER.ENABLED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterFields.DELETED, PROCESSOR_FILTER.DELETED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterFields.EXPORT, PROCESSOR_FILTER.EXPORT, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterFields.UUID, PROCESSOR_FILTER.UUID, value -> value);
        expressionMapper.map(ProcessorFilterFields.RUN_AS_USER, PROCESSOR_FILTER.RUN_AS_USER_UUID, value -> value);

        expressionMapper.map(ProcessorFields.ID, PROCESSOR_FILTER.FK_PROCESSOR_ID, Integer::valueOf);
        expressionMapper.map(ProcessorFields.PROCESSOR_TYPE, PROCESSOR.TASK_TYPE, String::valueOf);
        expressionMapper.map(ProcessorFields.ANALYTIC_RULE, PROCESSOR.PIPELINE_UUID, value -> value, false);
        expressionMapper.map(ProcessorFields.PIPELINE, PROCESSOR.PIPELINE_UUID, value -> value, false);
        expressionMapper.map(ProcessorFields.ENABLED, PROCESSOR.ENABLED, Boolean::valueOf);
        expressionMapper.map(ProcessorFields.DELETED, PROCESSOR.DELETED, Boolean::valueOf);
        expressionMapper.map(ProcessorFields.UUID, PROCESSOR.UUID, value -> value);
    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Creating a {}", PROCESSOR_FILTER.getName()));

        return JooqUtil.transactionResult(
                processorDbConnProvider,
                context -> {
                    final ProcessorFilterTracker tracker = createTracker(context);
                    processorFilter.setProcessorFilterTracker(tracker);
                    return createFilter(context, processorFilter);
                });
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        return JooqUtil.contextResult(processorDbConnProvider, context -> updateFilter(context, processorFilter));
    }

    private ProcessorFilterTracker createTracker(final DSLContext context) {
        final ProcessorFilterTracker tracker = new ProcessorFilterTracker();
        tracker.setVersion(1);
        tracker.setStatus(ProcessorFilterTrackerStatus.CREATED);
        final Integer id = context
                .insertInto(PROCESSOR_FILTER_TRACKER)
                .columns(
                        PROCESSOR_FILTER_TRACKER.VERSION,
                        PROCESSOR_FILTER_TRACKER.MIN_META_ID,
                        PROCESSOR_FILTER_TRACKER.MIN_EVENT_ID,
                        PROCESSOR_FILTER_TRACKER.MIN_META_CREATE_MS,
                        PROCESSOR_FILTER_TRACKER.MAX_META_CREATE_MS,
                        PROCESSOR_FILTER_TRACKER.META_CREATE_MS,
                        PROCESSOR_FILTER_TRACKER.LAST_POLL_MS,
                        PROCESSOR_FILTER_TRACKER.LAST_POLL_TASK_COUNT,
                        PROCESSOR_FILTER_TRACKER.MESSAGE,
                        PROCESSOR_FILTER_TRACKER.META_COUNT,
                        PROCESSOR_FILTER_TRACKER.EVENT_COUNT,
                        PROCESSOR_FILTER_TRACKER.STATUS)
                .values(
                        tracker.getVersion(),
                        tracker.getMinMetaId(),
                        tracker.getMinEventId(),
                        tracker.getMinMetaCreateMs(),
                        tracker.getMaxMetaCreateMs(),
                        tracker.getMetaCreateMs(),
                        tracker.getLastPollMs(),
                        tracker.getLastPollTaskCount(),
                        tracker.getMessage(),
                        tracker.getMetaCount(),
                        tracker.getEventCount(),
                        NullSafe.get(tracker.getStatus(), ProcessorFilterTrackerStatus::getPrimitiveValue))
                .returning(PROCESSOR_FILTER_TRACKER.ID)
                .fetchOne(PROCESSOR_FILTER_TRACKER.ID);
        Objects.requireNonNull(id);
        tracker.setId(id);
        return tracker;
    }

    private ProcessorFilter createFilter(final DSLContext context, final ProcessorFilter filter) {
        filter.setVersion(1);
        final String data = queryDataXMLSerialiser.serialise(filter.getQueryData());
        final Integer id = context
                .insertInto(PROCESSOR_FILTER)
                .columns(PROCESSOR_FILTER.VERSION,
                        PROCESSOR_FILTER.CREATE_TIME_MS,
                        PROCESSOR_FILTER.CREATE_USER,
                        PROCESSOR_FILTER.UPDATE_TIME_MS,
                        PROCESSOR_FILTER.UPDATE_USER,
                        PROCESSOR_FILTER.UUID,
                        PROCESSOR_FILTER.FK_PROCESSOR_ID,
                        PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID,
                        PROCESSOR_FILTER.DATA,
                        PROCESSOR_FILTER.PRIORITY,
                        PROCESSOR_FILTER.REPROCESS,
                        PROCESSOR_FILTER.ENABLED,
                        PROCESSOR_FILTER.DELETED,
                        PROCESSOR_FILTER.EXPORT,
                        PROCESSOR_FILTER.MIN_META_CREATE_TIME_MS,
                        PROCESSOR_FILTER.MAX_META_CREATE_TIME_MS,
                        PROCESSOR_FILTER.MAX_PROCESSING_TASKS,
                        PROCESSOR_FILTER.RUN_AS_USER_UUID)
                .values(filter.getVersion(),
                        filter.getCreateTimeMs(),
                        filter.getCreateUser(),
                        filter.getUpdateTimeMs(),
                        filter.getUpdateUser(),
                        filter.getUuid(),
                        filter.getProcessor().getId(),
                        filter.getProcessorFilterTracker().getId(),
                        data,
                        filter.getPriority(),
                        filter.isReprocess(),
                        filter.isEnabled(),
                        filter.isDeleted(),
                        filter.isExport(),
                        filter.getMinMetaCreateTimeMs(),
                        filter.getMaxMetaCreateTimeMs(),
                        filter.getMaxProcessingTasks(),
                        NullSafe.get(filter.getRunAsUser(), UserRef::getUuid))
                .returning(PROCESSOR_FILTER.ID)
                .fetchOne(PROCESSOR_FILTER.ID);
        Objects.requireNonNull(id);
        filter.setId(id);
        return filter;
    }

    private ProcessorFilter updateFilter(final DSLContext context, final ProcessorFilter filter) {
        final String data = queryDataXMLSerialiser.serialise(filter.getQueryData());
        final int count = context
                .update(PROCESSOR_FILTER)
                .set(PROCESSOR_FILTER.VERSION, PROCESSOR_FILTER.VERSION.plus(1))
                .set(PROCESSOR_FILTER.UPDATE_TIME_MS, filter.getUpdateTimeMs())
                .set(PROCESSOR_FILTER.UPDATE_USER, filter.getUpdateUser())
                .set(PROCESSOR_FILTER.DATA, data)
                .set(PROCESSOR_FILTER.PRIORITY, filter.getPriority())
                .set(PROCESSOR_FILTER.REPROCESS, filter.isReprocess())
                .set(PROCESSOR_FILTER.ENABLED, filter.isEnabled())
                .set(PROCESSOR_FILTER.DELETED, filter.isDeleted())
                .set(PROCESSOR_FILTER.EXPORT, filter.isExport())
                .set(PROCESSOR_FILTER.MIN_META_CREATE_TIME_MS, filter.getMinMetaCreateTimeMs())
                .set(PROCESSOR_FILTER.MAX_META_CREATE_TIME_MS, filter.getMaxMetaCreateTimeMs())
                .set(PROCESSOR_FILTER.MAX_PROCESSING_TASKS, filter.getMaxProcessingTasks())
                .set(PROCESSOR_FILTER.RUN_AS_USER_UUID, NullSafe.get(filter.getRunAsUser(), UserRef::getUuid))
                .where(PROCESSOR_FILTER.ID.eq(filter.getId()))
                .and(PROCESSOR_FILTER.VERSION.eq(filter.getVersion()))
                .execute();

        if (count == 0) {
            throw new DataChangedException("Failed to update processor filter, " +
                                           "it may have been updated by another user or deleted");
        }

        return fetch(context, filter.getId()).map(this::mapRecord).orElseThrow(() ->
                new RuntimeException("Error fetching updated processor filter"));
    }

    @Override
    public boolean delete(final int id) {
        // We don't want to allow direct physical delete, only logical delete.
        return logicalDeleteByProcessorFilterId(id) > 0;
        //genericDao.delete(id);
    }

    @Override
    public int logicalDeleteByProcessorFilterId(final int processorFilterId) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                logicalDeleteByProcessorFilterId(processorFilterId, context));
    }

    public int logicalDeleteByProcessorFilterId(final int processorFilterId, final DSLContext context) {
        final int count = context
                .update(PROCESSOR_FILTER)
                .set(PROCESSOR_FILTER.DELETED, true)
                .set(PROCESSOR_FILTER.VERSION, PROCESSOR_FILTER.VERSION.plus(1))
                .set(PROCESSOR_FILTER.UPDATE_TIME_MS, Instant.now().toEpochMilli())
                .where(PROCESSOR_FILTER.ID.eq(processorFilterId))
                .and(PROCESSOR_FILTER.DELETED.eq(false))
                .execute();
        LOGGER.debug("Logically deleted {} processor filters for processor filter Id {}",
                count,
                processorFilterId);
        return count;
    }

    public void logicalDeleteByProcessorId(final int processorId, final DSLContext context) {
        final int count = context
                .update(PROCESSOR_FILTER)
                .set(PROCESSOR_FILTER.DELETED, true)
                .set(PROCESSOR_FILTER.VERSION, PROCESSOR_FILTER.VERSION.plus(1))
                .set(PROCESSOR_FILTER.UPDATE_TIME_MS, Instant.now().toEpochMilli())
                .where(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(processorId))
                .and(PROCESSOR_FILTER.DELETED.eq(false))
                .execute();
        LOGGER.debug("Logically deleted {} processor filters for processor Id {}",
                count,
                processorId);
    }

    /**
     * Logically delete COMPLETE processor filters with no outstanding tasks where the tracker last poll is older
     * than the threshold. Note that COMPLETE just means that we have finished producing tasks on the DB, but we
     * can't delete the filter until all associated tasks have been processed otherwise they will never be picked
     * up.
     *
     * @param deleteThreshold Only logically delete filters with a last poll time older than the threshold.
     * @return The number of logically deleted filters.
     */
    @Override
    public int logicallyDeleteOldProcessorFilters(final Instant deleteThreshold) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                logicallyDeleteOldFilters(deleteThreshold, context));
    }

    @Override
    public ProcessorFilter restoreProcessorFilter(final ProcessorFilter processorFilter,
                                                  final boolean resetTracker) {
        LOGGER.debug("restoreProcessorFilter() - processorFilter: {}, resetTracker: {}",
                processorFilter, resetTracker);
        Objects.requireNonNull(processorFilter);

        return JooqUtil.transactionResult(processorDbConnProvider, txnContext -> {
            if (processorFilter.isDeleted()) {
                final Integer processorFilterTrackerId = NullSafe.get(processorFilter,
                        ProcessorFilter::getProcessorFilterTracker,
                        ProcessorFilterTracker::getId);

                // Un-delete the parent processor if needs be
                Processor processor = processorFilter.getProcessor();
                if (processor.isDeleted()) {
                    processor.setDeleted(false);
                    processor = processorDaoImplProvider.get()
                            .update(processor, txnContext);
                    processorFilter.setProcessor(processor);
                }

                // We are un-deleting the filter so reset the tracker back to clean slate
                if (processorFilterTrackerId != null && resetTracker) {
                    resetTracker(processorFilter, txnContext);
                }
                processorFilter.setDeleted(false);
                AuditUtil.stamp(securityContext.getUserIdentity(), processorFilter);
                return updateFilter(txnContext, processorFilter);
            } else {
                LOGGER.debug("restoreProcessorFilter() - Processor filter is not in a deleted state {}",
                        processorFilter);
                return processorFilter;
            }
        });
    }

    private ProcessorFilterTracker resetTracker(final ProcessorFilter processorFilter,
                                                final DSLContext context) {
        final Integer processorFilterId = Objects.requireNonNull(processorFilter.getId());
        final Integer taskCount = context.selectCount()
                .from(PROCESSOR_TASK)
                .where(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(processorFilterId))
                .and(PROCESSOR_TASK.STATUS.notIn(List.of(
                        TaskStatus.COMPLETE,
                        TaskStatus.DELETED,
                        TaskStatus.FAILED)))
                .fetchOneInto(Integer.class);

        // This shouldn't happen as all tasks are set to deleted when the processorFilter is
        // logically deleted, but here just in case
        if (taskCount != null && taskCount > 0) {
            LOGGER.debug("resetTracker() - processorFilterId: {}, count: {}", processorFilterId, taskCount);
            throw new RuntimeException(LogUtil.message(
                    "Unable to reset tracker for filter {} as {} active tasks exist",
                    processorFilter.getId(), taskCount));
        }

        final ProcessorFilterTracker existingTracker = processorFilter.getProcessorFilterTracker();
        final Integer processorFilterTrackerId = existingTracker.getId();
        // Reset the tracker to the state it would be in if it was just created fresh
        final ProcessorFilterTracker tracker = new ProcessorFilterTracker();
        tracker.setId(processorFilterTrackerId);
        tracker.setStatus(ProcessorFilterTrackerStatus.CREATED);
        tracker.setVersion(existingTracker.getVersion());
        final int count = processorFilterTrackerDaoImpl.update(context, tracker);
        LOGGER.debug("resetTracker() - processorFilterId: {}, count: {}", processorFilterTrackerId, count);
        return tracker;
    }

    public int logicallyDeleteOldFilters(final Instant deleteThreshold, final DSLContext context) {
        final UpdateConditionStep<ProcessorFilterRecord> query = context
                .update(PROCESSOR_FILTER)
                .set(PROCESSOR_FILTER.DELETED, true)
                .set(PROCESSOR_FILTER.VERSION, PROCESSOR_FILTER.VERSION.plus(1))
                .set(PROCESSOR_FILTER.UPDATE_TIME_MS, Instant.now().toEpochMilli())
                .where(PROCESSOR_FILTER.DELETED.eq(false))
                .and(DSL.exists(
                        DSL.selectZero()
                                .from(PROCESSOR_FILTER_TRACKER)
                                .where(PROCESSOR_FILTER_TRACKER.ID.eq(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID))
                                .and(PROCESSOR_FILTER_TRACKER.STATUS.eq(
                                        ProcessorFilterTrackerStatus.COMPLETE.getPrimitiveValue()))
                                .and(PROCESSOR_FILTER_TRACKER.LAST_POLL_MS.lessThan(deleteThreshold.toEpochMilli()))))
                .and(DSL.notExists(
                        DSL.selectZero()
                                .from(PROCESSOR_TASK)
                                .where(PROCESSOR_TASK.FK_PROCESSOR_FILTER_ID.eq(PROCESSOR_FILTER.ID))));

        LOGGER.trace("logicallyDeleteOldFilters query:\n{}", query);
        final int count = query.execute();

        LOGGER.debug("Logically deleted {} processor filters with a state of COMPLETE with no outstanding tasks and " +
                     "last poll before {}",
                count, deleteThreshold);
        return count;
    }

    /**
     * Physically delete old processor filters that are logically deleted with an update time older than the threshold.
     *
     * @param deleteThreshold Only physically delete filters with an update time older than the threshold.
     * @return The processor filter UUIDs of all the processor filters deleted.
     */
    @Override
    public Set<String> physicalDeleteOldProcessorFilters(final Instant deleteThreshold) {
        final List<PipeFilterKeys> result =
                JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select(PROCESSOR_FILTER.ID,
                                PROCESSOR_FILTER.UUID,
                                PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID)
                        .from(PROCESSOR_FILTER)
                        .where(PROCESSOR_FILTER.DELETED.eq(true))
                        .and(PROCESSOR_FILTER.UPDATE_TIME_MS.lessThan(deleteThreshold.toEpochMilli()))
                        .fetch()
                        .map(rec -> new PipeFilterKeys(
                                rec.get(PROCESSOR_FILTER.ID),
                                rec.get(PROCESSOR_FILTER.UUID),
                                rec.get(PROCESSOR_FILTER_TRACKER.ID))));

        LAMBDA_LOGGER.debug(() ->
                LogUtil.message("Found {} logically deleted filters with an update time older than {}",
                        result.size(), deleteThreshold));

        final Set<String> processorFilterUuids = new HashSet<>();
        // Delete one by one as we expect some constraint errors.
        result.forEach(dbKeys -> {
            try {
                final int count = JooqUtil.transactionResult(processorDbConnProvider, context -> {
                    final int filterCount = context
                            .deleteFrom(PROCESSOR_FILTER)
                            .where(PROCESSOR_FILTER.ID.eq(dbKeys.processorFilterId))
                            .execute();
                    LOGGER.debug("Physically deleted {} processor filters with id {}",
                            filterCount,
                            dbKeys.processorFilterId);

                    final int trackerCount = context
                            .deleteFrom(PROCESSOR_FILTER_TRACKER)
                            .where(PROCESSOR_FILTER_TRACKER.ID.eq(dbKeys.processorFilterTrackerId))
                            .execute();
                    LOGGER.debug("Physically deleted {} processor filter trackers with id {}",
                            trackerCount, dbKeys.processorFilterTrackerId);

                    return filterCount;
                });

                if (count > 0) {
                    // Make a note of the uuid, so we can delete any doc perms associated with it
                    // The tracker is 1:1 with filter, so don't need to return tracker delete count
                    processorFilterUuids.add(dbKeys.processorFilterUuid);
                }
            } catch (final DataAccessException e) {
                if (e.getCause() instanceof final SQLIntegrityConstraintViolationException sqlEx) {
                    LAMBDA_LOGGER.debug(() -> LogUtil.message("Expected constraint violation, dbKeys: {} - {}",
                            dbKeys, LogUtil.exceptionMessage(e)), e);
                } else {
                    throw e;
                }
            }
        });
        LAMBDA_LOGGER.debug(() -> "physicalDeleteOldProcessorFilters returning: "
                                  + processorFilterUuids.size() + " UUIDs");
        return processorFilterUuids;
    }

    @Override
    public List<ProcessorFilter> fetchByRunAsUser(final String userUuid) {
        Objects.requireNonNull(userUuid);
        return JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select()
                        .from(PROCESSOR_FILTER)
                        .join(PROCESSOR_FILTER_TRACKER)
                        .on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                        .join(PROCESSOR)
                        .on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                        .where(PROCESSOR_FILTER.RUN_AS_USER_UUID.eq(userUuid))
                        .and(PROCESSOR_FILTER.DELETED.isFalse())
                        .fetch())
                .map(this::mapRecord);
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        return JooqUtil.contextResult(processorDbConnProvider, context -> fetch(context, id)).map(this::mapRecord);
    }

    private Optional<Record> fetch(final DSLContext context, final int id) {
        return context
                .select()
                .from(PROCESSOR_FILTER)
                .join(PROCESSOR_FILTER_TRACKER)
                .on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                .join(PROCESSOR)
                .on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                .where(PROCESSOR_FILTER.ID.eq(id))
                .fetchOptional();
    }

    @Override
    public Optional<ProcessorFilter> fetchByUuid(final String uuid) {
        Objects.requireNonNull(uuid);
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                        context
                                .select()
                                .from(PROCESSOR_FILTER)
                                .join(PROCESSOR_FILTER_TRACKER)
                                .on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                                .join(PROCESSOR)
                                .on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                                .where(PROCESSOR_FILTER.UUID.eq(uuid))
                                .fetchOptional())
                .map(this::mapRecord);
    }

    @Override
    public ResultPage<ProcessorFilter> find(final ExpressionCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);
        final List<ProcessorFilter> list = JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select()
                        .from(PROCESSOR_FILTER)
                        .join(PROCESSOR_FILTER_TRACKER)
                        .on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                        .join(PROCESSOR)
                        .on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                        .where(condition)
                        .orderBy(orderFields)
                        .limit(offset, limit)
                        .fetch())
                .map(this::mapRecord);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private ProcessorFilter mapRecord(final Record record) {
        final Processor processor = RECORD_TO_PROCESSOR_MAPPER.apply(record);
        final ProcessorFilter processorFilter = recordToProcessorFilterMapper.apply(record);
        final ProcessorFilterTracker processorFilterTracker =
                RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER.apply(record);
        processorFilter.setProcessor(processor);
        processorFilter.setProcessorFilterTracker(processorFilterTracker);
        return processorFilter;
    }


    // --------------------------------------------------------------------------------


    private record PipeFilterKeys(
            int processorFilterId,
            String processorFilterUuid,
            int processorFilterTrackerId) {

    }
}
