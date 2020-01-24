package stroom.processor.impl.db;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
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
import stroom.processor.shared.ProcessorFilterDataSource;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.BaseResultList;

import javax.inject.Inject;
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
            ProcessorFilterDataSource.FIELD_ID, PROCESSOR_FILTER.ID);

    private static final Function<Record, Processor> RECORD_TO_PROCESSOR_MAPPER = new RecordToProcessorMapper();
    private static final Function<Record, ProcessorFilter> RECORD_TO_PROCESSOR_FILTER_MAPPER = new RecordToProcessorFilterMapper();
    private static final Function<Record, ProcessorFilterTracker> RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER = new RecordToProcessorFilterTrackerMapper();

    private final ProcessorDbConnProvider processorDbConnProvider;
    private final ProcessorFilterMarshaller marshaller;
    private final GenericDao<ProcessorFilterRecord, ProcessorFilter, Integer> genericDao;
    private final ExpressionMapper expressionMapper;

    @Inject
    ProcessorFilterDaoImpl(final ProcessorDbConnProvider processorDbConnProvider, final ExpressionMapperFactory expressionMapperFactory) {
        this.processorDbConnProvider = processorDbConnProvider;
        this.marshaller = new ProcessorFilterMarshaller();
        this.genericDao = new GenericDao<>(PROCESSOR_FILTER, PROCESSOR_FILTER.ID, ProcessorFilter.class, processorDbConnProvider);

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(ProcessorFilterDataSource.PRIORITY, PROCESSOR_FILTER.PRIORITY, Integer::valueOf);
        expressionMapper.map(ProcessorFilterDataSource.LAST_POLL_MS, PROCESSOR_FILTER_TRACKER.LAST_POLL_MS, Long::valueOf);
        expressionMapper.map(ProcessorFilterDataSource.PROCESSOR_ID, PROCESSOR_FILTER.FK_PROCESSOR_ID, Integer::valueOf);
        expressionMapper.map(ProcessorFilterDataSource.PIPELINE, PROCESSOR.PIPELINE_UUID, value -> value);
        expressionMapper.map(ProcessorFilterDataSource.PROCESSOR_ENABLED, PROCESSOR.ENABLED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterDataSource.PROCESSOR_FILTER_ENABLED, PROCESSOR_FILTER.ENABLED, Boolean::valueOf);
        expressionMapper.map(ProcessorFilterDataSource.CREATE_USER, PROCESSOR_FILTER.CREATE_USER, value -> value);
    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        LAMBDA_LOGGER.debug(LambdaLogUtil.message("Creating a {}", PROCESSOR_FILTER.getName()));

        final ProcessorFilter marshalled = marshaller.marshal(processorFilter);
        return marshaller.unmarshal(JooqUtil.transactionResult(processorDbConnProvider, context -> {
            final ProcessorFilterTrackerRecord processorFilterTrackerRecord = context.newRecord(PROCESSOR_FILTER_TRACKER, new ProcessorFilterTracker());
            processorFilterTrackerRecord.store();
            final ProcessorFilterTracker processorFilterTracker = processorFilterTrackerRecord.into(ProcessorFilterTracker.class);

            marshalled.setProcessorFilterTracker(processorFilterTracker);

            final ProcessorFilterRecord processorFilterRecord = context.newRecord(PROCESSOR_FILTER, marshalled);

            processorFilterRecord.setFkProcessorFilterTrackerId(marshalled.getProcessorFilterTracker().getId());
            processorFilterRecord.setFkProcessorId(marshalled.getProcessor().getId());
            processorFilterRecord.store();

            final ProcessorFilter result = processorFilterRecord.into(ProcessorFilter.class);
            result.setProcessorFilterTracker(result.getProcessorFilterTracker());
            result.setProcessor(result.getProcessor());

            return result;
        }));
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        final ProcessorFilter marshalled = marshaller.marshal(processorFilter);
        return marshaller.unmarshal(JooqUtil.contextResultWithOptimisticLocking(processorDbConnProvider, context -> {
            final ProcessorFilterRecord processorFilterRecord =
                    context.newRecord(PROCESSOR_FILTER, marshalled);

            processorFilterRecord.setFkProcessorFilterTrackerId(marshalled.getProcessorFilterTracker().getId());
            processorFilterRecord.setFkProcessorId(marshalled.getProcessor().getId());
            processorFilterRecord.update();

            final ProcessorFilter result = processorFilterRecord.into(ProcessorFilter.class);
            result.setProcessorFilterTracker(marshalled.getProcessorFilterTracker());
            result.setProcessor(marshalled.getProcessor());

            return result;
        }));
    }

    @Override
    public boolean delete(final int id) {
        return genericDao.delete(id);
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
    public BaseResultList<ProcessorFilter> find(final ExpressionCriteria criteria) {
        return JooqUtil.contextResult(processorDbConnProvider, context -> find(context, criteria));
    }

    private BaseResultList<ProcessorFilter> find(final DSLContext context, final ExpressionCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());

        final OrderField<?>[] orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);

        final List<ProcessorFilter> list = context
                .select()
                .from(PROCESSOR_FILTER)
                .join(PROCESSOR_FILTER_TRACKER).on(PROCESSOR_FILTER.FK_PROCESSOR_FILTER_TRACKER_ID.eq(PROCESSOR_FILTER_TRACKER.ID))
                .join(PROCESSOR).on(PROCESSOR_FILTER.FK_PROCESSOR_ID.eq(PROCESSOR.ID))
                .where(condition)
                .orderBy(orderFields)
                .fetch()
                .map(record -> {
                    final Processor processor = RECORD_TO_PROCESSOR_MAPPER.apply(record);
                    final ProcessorFilter processorFilter = RECORD_TO_PROCESSOR_FILTER_MAPPER.apply(record);
                    final ProcessorFilterTracker processorFilterTracker = RECORD_TO_PROCESSOR_FILTER_TRACKER_MAPPER.apply(record);

                    processorFilter.setProcessor(processor);
                    processorFilter.setProcessorFilterTracker(processorFilterTracker);

                    return marshaller.unmarshal(processorFilter);
                });

        return BaseResultList.createCriterialBasedList(list, criteria);
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
