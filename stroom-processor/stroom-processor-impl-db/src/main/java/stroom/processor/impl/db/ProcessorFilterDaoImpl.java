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
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilterTracker.PROCESSOR_FILTER_TRACKER;

class ProcessorFilterDaoImpl implements ProcessorFilterDao {
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ProcessorFilterDaoImpl.class);

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            ProcessorFilterFields.FIELD_ID, PROCESSOR_FILTER.ID);

    private static final Function<Record, Processor> RECORD_TO_PROCESSOR_MAPPER = new RecordToProcessorMapper();
    private static final Function<Record, ProcessorFilter> RECORD_TO_PROCESSOR_FILTER_MAPPER = new RecordToProcessorFilterMapper();
    private static final Function<Record, ProcessorFilterTracker> RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER = new RecordToProcessorFilterTrackerMapper();

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
        this.genericDao = new GenericDao<>(PROCESSOR_FILTER, PROCESSOR_FILTER.ID, ProcessorFilter.class, processorDbConnProvider);

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(ProcessorFilterFields.PRIORITY, PROCESSOR_FILTER.PRIORITY, Integer::valueOf);
        expressionMapper.map(ProcessorFilterFields.LAST_POLL_MS, PROCESSOR_FILTER_TRACKER.LAST_POLL_MS, Long::valueOf);
        expressionMapper.map(ProcessorFilterFields.PROCESSOR_ID, PROCESSOR_FILTER.FK_PROCESSOR_ID, Integer::valueOf);
        expressionMapper.map(ProcessorFilterFields.PIPELINE, PROCESSOR.PIPELINE_UUID, value -> value);
        expressionMapper.map(ProcessorFilterFields.PROCESSOR_ENABLED, PROCESSOR.ENABLED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterFields.PROCESSOR_DELETED, PROCESSOR.DELETED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterFields.PROCESSOR_FILTER_ENABLED, PROCESSOR_FILTER.ENABLED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterFields.PROCESSOR_FILTER_DELETED, PROCESSOR_FILTER.DELETED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterFields.CREATE_USER, PROCESSOR_FILTER.CREATE_USER, value -> value);
        expressionMapper.map(ProcessorFilterFields.UUID, PROCESSOR_FILTER.UUID, value -> value);
    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        return create(processorFilter, null, null);
    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter,
                                  final Long minMetaCreateMs,
                                  final Long maxMetaCreateMs) {
        LAMBDA_LOGGER.debug(LambdaLogUtil.message("Creating a {}", PROCESSOR_FILTER.getName()));

        final ProcessorFilter marshalled = marshaller.marshal(processorFilter);
        final ProcessorFilter stored = JooqUtil.transactionResult(processorDbConnProvider, context -> {
            ProcessorFilterTracker tracker = new ProcessorFilterTracker();
            tracker.setMinMetaCreateMs(minMetaCreateMs);
            tracker.setMaxMetaCreateMs(maxMetaCreateMs);

            final ProcessorFilterTrackerRecord processorFilterTrackerRecord = context.newRecord(PROCESSOR_FILTER_TRACKER, tracker);
            processorFilterTrackerRecord.store();
            tracker = processorFilterTrackerRecord.into(ProcessorFilterTracker.class);

            marshalled.setProcessorFilterTracker(tracker);

            final ProcessorFilterRecord processorFilterRecord = context.newRecord(PROCESSOR_FILTER, marshalled);

            processorFilterRecord.setFkProcessorFilterTrackerId(marshalled.getProcessorFilterTracker().getId());
            processorFilterRecord.setFkProcessorId(marshalled.getProcessor().getId());
            processorFilterRecord.store();

            final ProcessorFilter result = processorFilterRecord.into(ProcessorFilter.class);
            result.setProcessorFilterTracker(result.getProcessorFilterTracker());
            result.setProcessor(result.getProcessor());

            return result;
        });
        return marshaller.unmarshal(stored);
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        final ProcessorFilter marshalled = marshaller.marshal(processorFilter);
        final ProcessorFilter stored = JooqUtil.contextResultWithOptimisticLocking(processorDbConnProvider, context -> {
            final ProcessorFilterRecord processorFilterRecord =
                    context.newRecord(PROCESSOR_FILTER, marshalled);

//            processorFilterRecord.setFkProcessorFilterTrackerId(marshalled.getProcessorFilterTracker().getId());
//            processorFilterRecord.setFkProcessorId(marshalled.getProcessor().getId());
            processorFilterRecord.update();

            final ProcessorFilter result = processorFilterRecord.into(ProcessorFilter.class);
            result.setProcessorFilterTracker(marshalled.getProcessorFilterTracker());
            result.setProcessor(marshalled.getProcessor());

            return result;
        });
        return marshaller.unmarshal(stored);
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
    }

    @Override
    public boolean logicalDelete(final int id) {
        return JooqUtil.contextResult(processorDbConnProvider, context -> context
                .update(PROCESSOR_FILTER)
                .set(PROCESSOR_FILTER.DELETED, true)
                .where(PROCESSOR_FILTER.ID.eq(id))
                .execute() > 0);
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        return JooqUtil.contextResult(processorDbConnProvider, context ->
                context
                        .select()
                        .from(PROCESSOR_FILTER)
                        .join(PROCESSOR_FILTER_TRACKER).on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                        .join(PROCESSOR).on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                        .where(PROCESSOR_FILTER.ID.eq(id))
                        .fetchOptional()
                        .map(record -> {
                            final Processor processor = RECORD_TO_PROCESSOR_MAPPER.apply(record);
                            final ProcessorFilter processorFilter = RECORD_TO_PROCESSOR_FILTER_MAPPER.apply(record);
                            final ProcessorFilterTracker processorFilterTracker = RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER.apply(record);

                            processorFilter.setProcessor(processor);
                            processorFilter.setProcessorFilterTracker(processorFilterTracker);

                            return marshaller.unmarshal(processorFilter);
                        }));
    }

    @Override
    public ResultPage<ProcessorFilter> find(final ExpressionCriteria criteria) {
        return JooqUtil.contextResult(processorDbConnProvider, context -> find(context, criteria));
    }

    private ResultPage<ProcessorFilter> find(final DSLContext context, final ExpressionCriteria criteria) {
        final Collection<Condition> conditions = expressionMapper.apply(criteria.getExpression());

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);

        final List<ProcessorFilter> list = context
                .select()
                .from(PROCESSOR_FILTER)
                .join(PROCESSOR_FILTER_TRACKER).on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                .join(PROCESSOR).on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                .where(conditions)
                .orderBy(orderFields)
                .limit(JooqUtil.getLimit(criteria.getPageRequest(), true))
                .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                .fetch()
                .map(record -> {
                    final Processor processor = RECORD_TO_PROCESSOR_MAPPER.apply(record);
                    final ProcessorFilter processorFilter = RECORD_TO_PROCESSOR_FILTER_MAPPER.apply(record);
                    final ProcessorFilterTracker processorFilterTracker = RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER.apply(record);

                    processorFilter.setProcessor(processor);
                    processorFilter.setProcessorFilterTracker(processorFilterTracker);

                    return marshaller.unmarshal(processorFilter);
                });

        return ResultPage.createCriterialBasedList(list, criteria);
    }

//    private Collection<Condition> convertCriteria(final FindProcessorFilterCriteria criteria) {
//        return JooqUtil.conditions(
//                JooqUtil.getRangeCondition(PROCESSOR_FILTER.PRIORITY, criteria.getPriorityRange()),
//                JooqUtil.getRangeCondition(PROCESSOR_FILTER_TRACKER.LAST_POLL_MS, criteria.getLastPollPeriod()),
//                JooqUtil.getSetCondition(PROCESSOR_FILTER.FK_PROCESSOR_ID, criteria.getProcessorIdSet()),
//                JooqUtil.getStringCondition(PROCESSOR.PIPELINE_UUID, criteria.getPipelineUuidCriteria()),
//                Optional.ofNullable(criteria.getProcessorEnabled()).map(PROCESSOR.ENABLED::eq),
//                Optional.ofNullable(criteria.getProcessorFilterEnabled()).map(PROCESSOR_FILTER.ENABLED::eq),
//                Optional.ofNullable(criteria.getCreateUser()).map(PROCESSOR_FILTER.CREATE_USER::eq));
//    }
}
