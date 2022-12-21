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
import stroom.processor.shared.TaskStatus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.inject.Inject;

import static stroom.processor.impl.db.jooq.Tables.PROCESSOR_TASK;
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

        final ProcessorFilterRecord stored = JooqUtil.transactionResult(processorDbConnProvider, context -> {
            processorFilterTrackerRecord.attach(context.configuration());
            processorFilterTrackerRecord.store();

            final ProcessorFilterTracker persistedTracker =
                    processorFilterTrackerRecord.into(ProcessorFilterTracker.class);
            marshalled.setProcessorFilterTracker(persistedTracker);
            processorFilterRecord.setFkProcessorFilterTrackerId(persistedTracker.getId());

            processorFilterRecord.attach(context.configuration());
            processorFilterRecord.store();

            return processorFilterRecord;
        });

        final ProcessorFilter result = stored.into(ProcessorFilter.class);
        result.setProcessorFilterTracker(result.getProcessorFilterTracker());
        result.setProcessor(result.getProcessor());

        return marshaller.unmarshal(result);
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
        return genericDao.delete(id);
    }

    @Override
    public boolean logicalDelete(final int id) {
        final boolean success = JooqUtil.transactionResult(processorDbConnProvider, context -> {
            // Logically delete any associated unprocessed tasks.
            // Once the filter is logically deleted no new tasks will be created for it
            // but we may still have active tasks for 'deleted' filters.
            final int processor_task_update_count = context
                    .update(PROCESSOR_TASK)
                    .set(PROCESSOR_TASK.STATUS, TaskStatus.DELETED.getPrimitiveValue())
                    .set(PROCESSOR_TASK.VERSION, PROCESSOR_TASK.VERSION.plus(1))
                    .where(DSL.exists(
                            DSL.selectZero()
                                    .from(PROCESSOR_FILTER)
                                    .where(PROCESSOR_FILTER.ID.eq(id))))
                    .and(PROCESSOR_TASK.STATUS.in(
                            TaskStatus.UNPROCESSED.getPrimitiveValue(),
                            TaskStatus.ASSIGNED.getPrimitiveValue()))
                    .execute();

            LOGGER.debug("Logically deleted {} tasks for processor filter Id {}",
                    processor_task_update_count, id);

            return context
                    .update(PROCESSOR_FILTER)
                    .set(PROCESSOR_FILTER.DELETED, true)
                    .set(PROCESSOR_FILTER.VERSION, PROCESSOR_FILTER.VERSION.plus(1))
                    .where(PROCESSOR_FILTER.ID.eq(id))
                    .execute();
        }) > 0;

        LOGGER.debug("Logically deleted processor filter {}, success: {}", id, success);
        return success;
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                        context
                                .select()
                                .from(PROCESSOR_FILTER)
                                .join(PROCESSOR_FILTER_TRACKER).on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(
                                        PROCESSOR_FILTER_TRACKER.ID))
                                .join(PROCESSOR).on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
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
                        .join(PROCESSOR_FILTER_TRACKER).on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(
                                PROCESSOR_FILTER_TRACKER.ID))
                        .join(PROCESSOR).on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
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
