package stroom.processor.impl.db;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.db.jooq.tables.records.ProcessorFilterRecord;
import stroom.processor.impl.db.jooq.tables.records.ProcessorFilterTrackerRecord;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.inject.Inject;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;

class ProcessorFilterDaoImpl implements ProcessorFilterDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorFilterDaoImpl.class);

    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ProcessorFilterDaoImpl.class);

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            ProcessorFilterFields.FIELD_ID, PROCESSOR_FILTER.ID);

    private static final Function<Record, Processor> RECORD_TO_PROCESSOR_MAPPER = new RecordToProcessorMapper();
    private static final Function<Record, ProcessorFilter> RECORD_TO_PROCESSOR_FILTER_MAPPER =
            new RecordToProcessorFilterMapper();
    private static final Function<Record, ProcessorFilterTracker> RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER =
            new RecordToProcessorFilterTrackerMapper();

    private final ProcessorDbConnProvider processorDbConnProvider;
    private final ProcessorFilterMarshaller marshaller;
    private final GenericDao<ProcessorFilterRecord, ProcessorFilter, Integer> genericDao;
    private final ExpressionMapper expressionMapper;

    @Inject
    ProcessorFilterDaoImpl(final ProcessorDbConnProvider processorDbConnProvider,
                           final ExpressionMapperFactory expressionMapperFactory,
                           final ProcessorFilterMarshaller marshaller) {
        this.processorDbConnProvider = processorDbConnProvider;
        this.marshaller = marshaller;
        this.genericDao = new GenericDao<>(
                processorDbConnProvider,
                PROCESSOR_FILTER,
                PROCESSOR_FILTER.ID,
                ProcessorFilter.class);

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(ProcessorFilterFields.ID, PROCESSOR_FILTER.ID, Integer::valueOf);
        expressionMapper.map(ProcessorFilterFields.CREATE_USER, PROCESSOR_FILTER.CREATE_USER, value -> value);
        expressionMapper.map(ProcessorFilterFields.LAST_POLL_MS, PROCESSOR_FILTER_TRACKER.LAST_POLL_MS, Long::valueOf);
        expressionMapper.map(ProcessorFilterFields.PRIORITY, PROCESSOR_FILTER.PRIORITY, Integer::valueOf);
        expressionMapper.map(ProcessorFilterFields.ENABLED, PROCESSOR_FILTER.ENABLED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterFields.DELETED, PROCESSOR_FILTER.DELETED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterFields.PROCESSOR_ID, PROCESSOR_FILTER.FK_PROCESSOR_ID, Integer::valueOf);
        expressionMapper.map(ProcessorFilterFields.UUID, PROCESSOR_FILTER.UUID, value -> value);

        expressionMapper.map(ProcessorFields.ID, PROCESSOR.ID, Integer::valueOf);
        expressionMapper.map(ProcessorFields.CREATE_USER, PROCESSOR_FILTER.CREATE_USER, value -> value);
        expressionMapper.map(ProcessorFields.PIPELINE, PROCESSOR.PIPELINE_UUID, value -> value, false);
        expressionMapper.map(ProcessorFields.ENABLED, PROCESSOR.ENABLED, Boolean::valueOf);
        expressionMapper.map(ProcessorFields.DELETED, PROCESSOR.DELETED, Boolean::valueOf);
        expressionMapper.map(ProcessorFields.UUID, PROCESSOR.UUID, value -> value);
    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Creating a {}", PROCESSOR_FILTER.getName()));

        final ProcessorFilter marshalled = marshaller.marshal(processorFilter);
        final ProcessorFilterTracker tracker = new ProcessorFilterTracker();

        final ProcessorFilterTrackerRecord processorFilterTrackerRecord = PROCESSOR_FILTER_TRACKER.newRecord();
        processorFilterTrackerRecord.from(tracker);

        final ProcessorFilterRecord processorFilterRecord = PROCESSOR_FILTER.newRecord();
        processorFilterRecord.from(marshalled);
        processorFilterRecord.setFkProcessorId(marshalled.getProcessor().getId());

        final Tuple2<ProcessorFilterRecord, ProcessorFilterTracker> result = JooqUtil.transactionResult(processorDbConnProvider, context -> {
            processorFilterTrackerRecord.attach(context.configuration());
            processorFilterTrackerRecord.store();

            final ProcessorFilterTracker persistedTracker =
                    processorFilterTrackerRecord.into(ProcessorFilterTracker.class);
            marshalled.setProcessorFilterTracker(persistedTracker);
            processorFilterRecord.setFkProcessorFilterTrackerId(persistedTracker.getId());

            processorFilterRecord.attach(context.configuration());
            processorFilterRecord.store();

            return Tuple.of(processorFilterRecord, persistedTracker);
        });

        final ProcessorFilterRecord processorFilterRecord2 = result._1;
        final ProcessorFilterTracker processorFilterTracker = result._2;
        final ProcessorFilter processorFilter2 = processorFilterRecord2.into(ProcessorFilter.class);
        processorFilter2.setProcessorFilterTracker(processorFilterTracker);
        processorFilter2.setProcessor(processorFilter.getProcessor());

        return marshaller.unmarshal(processorFilter2);
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        final ProcessorFilter marshalled = marshaller.marshal(processorFilter);
        final ProcessorFilterRecord record = PROCESSOR_FILTER.newRecord();
        record.from(processorFilter);
        final ProcessorFilterRecord persistedRecord = JooqUtil.updateWithOptimisticLocking(processorDbConnProvider,
                record);
        final ProcessorFilter result = persistedRecord.into(ProcessorFilter.class);
        result.setProcessorFilterTracker(marshalled.getProcessorFilterTracker());
        result.setProcessor(marshalled.getProcessor());
        return marshaller.unmarshal(result);
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
                .execute();
        LOGGER.debug("Logically deleted {} processor filters for processor filter Id {}",
                count,
                processorFilterId);
        return count;
    }

    public int logicalDeleteByProcessorId(final int processorId) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                logicalDeleteByProcessorId(processorId, context));
    }

    public int logicalDeleteByProcessorId(final int processorId, final DSLContext context) {
        final int count = context
                .update(PROCESSOR_FILTER)
                .set(PROCESSOR_FILTER.DELETED, true)
                .set(PROCESSOR_FILTER.VERSION, PROCESSOR_FILTER.VERSION.plus(1))
                .set(PROCESSOR_FILTER.UPDATE_TIME_MS, Instant.now().toEpochMilli())
                .where(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(processorId))
                .execute();
        LOGGER.debug("Logically deleted {} processor filters for processor Id {}",
                count,
                processorId);
        return count;
    }

    @Override
    public int logicallyDeleteOldProcessorFilters(final Instant deleteThreshold) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                logicallyDeleteOldFilters(deleteThreshold, context));
    }

    public int logicallyDeleteOldFilters(final Instant deleteThreshold, final DSLContext context) {
        final int count = context
                .update(PROCESSOR_FILTER)
                .set(PROCESSOR_FILTER.DELETED, true)
                .set(PROCESSOR_FILTER.VERSION, PROCESSOR_FILTER.VERSION.plus(1))
                .set(PROCESSOR_FILTER.UPDATE_TIME_MS, Instant.now().toEpochMilli())
                .where(DSL.exists(
                        DSL.selectZero()
                                .from(PROCESSOR_FILTER_TRACKER)
                                .where(PROCESSOR_FILTER_TRACKER.ID.eq(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID))
                                .and(PROCESSOR_FILTER_TRACKER.STATUS.eq(ProcessorFilterTracker.COMPLETE)
                                        .or(PROCESSOR_FILTER_TRACKER.STATUS.eq(ProcessorFilterTracker.ERROR)))
                                .and(PROCESSOR_FILTER_TRACKER.LAST_POLL_MS.lessThan(deleteThreshold.toEpochMilli()))))
                .execute();
        LOGGER.debug("Logically deleted {} processor filters that were complete",
                count);
        return count;
    }

    @Override
    public void physicalDeleteOldProcessorFilters(final Instant deleteThreshold) {
        final Result<Record2<Integer, Integer>> result =
                JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .select(PROCESSOR_FILTER.ID, PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID)
                        .from(PROCESSOR_FILTER)
                        .where(PROCESSOR_FILTER.DELETED.eq(true))
                        .and(PROCESSOR_FILTER.UPDATE_TIME_MS.lessThan(deleteThreshold.toEpochMilli()))
                        .fetch());

        // Delete one by one as we expect some constraint errors.
        result.forEach(record -> {
            final int processorFilterId = record.value1();
            final int processorFilterTrackerId = record.value2();

            try {
                JooqUtil.transaction(processorDbConnProvider, context -> {
                    LOGGER.debug("Deleting processor filter with id {}", processorFilterId);
                    context
                            .deleteFrom(PROCESSOR_FILTER)
                            .where(PROCESSOR_FILTER.ID.eq(processorFilterId))
                            .execute();

                    LOGGER.debug("Deleting processor filter tracker with id {}", processorFilterTrackerId);
                    context
                            .deleteFrom(PROCESSOR_FILTER_TRACKER)
                            .where(PROCESSOR_FILTER_TRACKER.ID.eq(processorFilterTrackerId))
                            .execute();
                });
            } catch (final DataAccessException e) {
                if (e.getCause() != null && e.getCause() instanceof SQLIntegrityConstraintViolationException) {
                    final var sqlEx = (SQLIntegrityConstraintViolationException) e.getCause();
                    LOGGER.debug("Expected constraint violation exception: " + sqlEx.getMessage(), e);
                } else {
                    throw e;
                }
            }
        });
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                        context
                                .select()
                                .from(PROCESSOR_FILTER)
                                .join(PROCESSOR_FILTER_TRACKER)
                                .on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                                .join(PROCESSOR)
                                .on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                                .where(PROCESSOR_FILTER.ID.eq(id))
                                .fetchOptional())
                .map(record -> {
                    final Processor processor = RECORD_TO_PROCESSOR_MAPPER.apply(record);
                    final ProcessorFilter processorFilter = RECORD_TO_PROCESSOR_FILTER_MAPPER.apply(record);
                    final ProcessorFilterTracker processorFilterTracker =
                            RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER.apply(record);

                    processorFilter.setProcessor(processor);
                    processorFilter.setProcessorFilterTracker(processorFilterTracker);

                    return marshaller.unmarshal(processorFilter);
                });
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
                .map(record -> {
                    final Processor processor = RECORD_TO_PROCESSOR_MAPPER.apply(record);
                    final ProcessorFilter processorFilter = RECORD_TO_PROCESSOR_FILTER_MAPPER.apply(record);
                    final ProcessorFilterTracker processorFilterTracker =
                            RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER.apply(record);

                    processorFilter.setProcessor(processor);
                    processorFilter.setProcessorFilterTracker(processorFilterTracker);

                    return marshaller.unmarshal(processorFilter);
                });
        return ResultPage.createCriterialBasedList(list, criteria);
    }
}
