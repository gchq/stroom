package stroom.meta.impl.db;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import stroom.db.util.JooqUtil;
import stroom.meta.impl.MetaDao;
import stroom.meta.impl.MetaKeyDao;
import stroom.meta.impl.db.ExpressionMapper.TermHandler;
import stroom.meta.impl.db.MetaExpressionMapper.MetaTermHandler;
import stroom.meta.impl.db.jooq.tables.MetaFeed;
import stroom.meta.impl.db.jooq.tables.MetaProcessor;
import stroom.meta.impl.db.jooq.tables.MetaType;
import stroom.meta.impl.db.jooq.tables.MetaVal;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFieldNames;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.util.date.DateUtil;
import stroom.util.shared.IdSet;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.selectDistinct;
import static stroom.meta.impl.db.jooq.tables.Meta.META;
import static stroom.meta.impl.db.jooq.tables.MetaFeed.META_FEED;
import static stroom.meta.impl.db.jooq.tables.MetaProcessor.META_PROCESSOR;
import static stroom.meta.impl.db.jooq.tables.MetaType.META_TYPE;
import static stroom.meta.impl.db.jooq.tables.MetaVal.META_VAL;

@Singleton
class MetaDaoImpl implements MetaDao {
    private static final stroom.meta.impl.db.jooq.tables.Meta meta = META.as("m");
    private static final MetaFeed metaFeed = META_FEED.as("f");
    private static final MetaType metaType = META_TYPE.as("t");
    private static final MetaProcessor metaProcessor = META_PROCESSOR.as("p");
    private static final MetaVal metaVal = META_VAL.as("v");

    private static final Function<Record, Meta> RECORD_TO_META_MAPPER = record -> new Meta.Builder()
            .id(record.get(meta.ID))
            .feedName(record.get(metaFeed.NAME))
            .typeName(record.get(metaType.NAME))
            .processorUuid(record.get(metaProcessor.PROCESSOR_UUID))
            .pipelineUuid(record.get(metaProcessor.PIPELINE_UUID))
            .parentDataId(record.get(meta.PARENT_ID))
            .processorTaskId(record.get(meta.TASK_ID))
            .status(MetaStatusId.getStatus(record.get(meta.STATUS)))
            .statusMs(record.get(meta.STATUS_TIME))
            .createMs(record.get(meta.CREATE_TIME))
            .effectiveMs(record.get(meta.EFFECTIVE_TIME))
            .build();

    private final ConnectionProvider connectionProvider;
    private final MetaFeedDaoImpl feedDao;
    private final MetaTypeDaoImpl metaTypeDao;
    private final MetaProcessorDaoImpl metaProcessorDao;
    private final MetaKeyDao metaKeyDao;

    private final ExpressionMapper expressionMapper;
    private final MetaExpressionMapper metaExpressionMapper;

    @Inject
    MetaDaoImpl(final ConnectionProvider connectionProvider,
                final MetaFeedDaoImpl feedDao,
                final MetaTypeDaoImpl metaTypeDao,
                final MetaProcessorDaoImpl metaProcessorDao,
                final MetaKeyDao metaKeyDao) {
        this.connectionProvider = connectionProvider;
        this.feedDao = feedDao;
        this.metaTypeDao = metaTypeDao;
        this.metaProcessorDao = metaProcessorDao;
        this.metaKeyDao = metaKeyDao;

        // Standard fields.
        final Map<String, TermHandler<?>> termHandlers = new HashMap<>();
        termHandlers.put(MetaFieldNames.ID, new TermHandler<>(meta.ID, Long::valueOf));
        termHandlers.put(MetaFieldNames.FEED_NAME, new TermHandler<>(meta.FEED_ID, feedDao::getOrCreate));
        termHandlers.put(MetaFieldNames.FEED_ID, new TermHandler<>(meta.FEED_ID, Integer::valueOf));
        termHandlers.put(MetaFieldNames.TYPE_NAME, new TermHandler<>(meta.TYPE_ID, metaTypeDao::getOrCreate));
        termHandlers.put(MetaFieldNames.PIPELINE_UUID, new TermHandler<>(metaProcessor.PIPELINE_UUID, value -> value));
        termHandlers.put(MetaFieldNames.PARENT_ID, new TermHandler<>(meta.PARENT_ID, Long::valueOf));
        termHandlers.put(MetaFieldNames.TASK_ID, new TermHandler<>(meta.TASK_ID, Long::valueOf));
        termHandlers.put(MetaFieldNames.PROCESSOR_ID, new TermHandler<>(meta.PROCESSOR_ID, Integer::valueOf));
        termHandlers.put(MetaFieldNames.STATUS, new TermHandler<>(meta.STATUS, value -> MetaStatusId.getPrimitiveValue(Status.valueOf(value.toUpperCase()))));
        termHandlers.put(MetaFieldNames.STATUS_TIME, new TermHandler<>(meta.STATUS_TIME, DateUtil::parseNormalDateTimeString));
        termHandlers.put(MetaFieldNames.CREATE_TIME, new TermHandler<>(meta.CREATE_TIME, DateUtil::parseNormalDateTimeString));
        termHandlers.put(MetaFieldNames.EFFECTIVE_TIME, new TermHandler<>(meta.EFFECTIVE_TIME, DateUtil::parseNormalDateTimeString));
        expressionMapper = new ExpressionMapper(termHandlers);


        // Extended meta fields.
        final Map<String, MetaTermHandler> metaTermHandlers = new HashMap<>();

//        metaTermHandlers.put(StreamDataSource.NODE, createMetaTermHandler(StreamDataSource.NODE));
        addMetaTermHandler(metaTermHandlers, MetaFieldNames.REC_READ);
        addMetaTermHandler(metaTermHandlers, MetaFieldNames.REC_WRITE);
        addMetaTermHandler(metaTermHandlers, MetaFieldNames.REC_INFO);
        addMetaTermHandler(metaTermHandlers, MetaFieldNames.REC_WARN);
        addMetaTermHandler(metaTermHandlers, MetaFieldNames.REC_ERROR);
        addMetaTermHandler(metaTermHandlers, MetaFieldNames.REC_FATAL);
        addMetaTermHandler(metaTermHandlers, MetaFieldNames.DURATION);
        addMetaTermHandler(metaTermHandlers, MetaFieldNames.FILE_SIZE);
        addMetaTermHandler(metaTermHandlers, MetaFieldNames.RAW_SIZE);

        metaExpressionMapper = new MetaExpressionMapper(metaTermHandlers);
    }

    private void addMetaTermHandler(final Map<String, MetaTermHandler> metaTermHandlers, final String fieldName) {
        final MetaTermHandler handler = new MetaTermHandler(
                metaKeyDao,
                metaVal.META_KEY_ID,
                fieldName,
                new TermHandler<>(metaVal.VAL, Long::valueOf));
        metaTermHandlers.put(fieldName, handler);
    }

    @Override
    public Long getMaxId() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(max(meta.ID))
                .from(meta)
                .fetchOptional()
                .map(Record1::value1)
                .orElse(null));
    }

    @Override
    public Meta create(final MetaProperties metaProperties) {
        final Integer feedId = feedDao.getOrCreate(metaProperties.getFeedName());
        final Integer typeId = metaTypeDao.getOrCreate(metaProperties.getTypeName());
        final Integer processorId = metaProcessorDao.getOrCreate(metaProperties.getProcessorUuid(), metaProperties.getPipelineUuid());

        final long id = JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(META,
                        META.CREATE_TIME,
                        META.EFFECTIVE_TIME,
                        META.PARENT_ID,
                        META.STATUS,
                        META.STATUS_TIME,
                        META.TASK_ID,
                        META.FEED_ID,
                        META.TYPE_ID,
                        META.PROCESSOR_ID)
                .values(
                        metaProperties.getCreateMs(),
                        metaProperties.getEffectiveMs(),
                        metaProperties.getParentId(),
                        MetaStatusId.LOCKED,
                        metaProperties.getStatusMs(),
                        metaProperties.getProcessorTaskId(),
                        feedId,
                        typeId,
                        processorId)
                .returning(META.ID)
                .fetchOne()
                .getId()
        );

        return new Meta.Builder().id(id)
                .feedName(metaProperties.getFeedName())
                .typeName(metaProperties.getTypeName())
                .processorUuid(metaProperties.getProcessorUuid())
                .pipelineUuid(metaProperties.getPipelineUuid())
                .parentDataId(metaProperties.getParentId())
                .processorTaskId(metaProperties.getProcessorTaskId())
                .status(Status.LOCKED)
                .statusMs(metaProperties.getStatusMs())
                .createMs(metaProperties.getCreateMs())
                .effectiveMs(metaProperties.getEffectiveMs())
                .build();
    }

    @Override
    public int updateStatus(final FindMetaCriteria criteria, final Status newStatus, final Status currentStatus, final long statusTime) {
        Condition condition = expressionMapper.apply(criteria.getExpression());

        // Add a condition if we should check current status.
        if (currentStatus != null) {
            condition = condition.and(meta.STATUS.eq(MetaStatusId.getPrimitiveValue(currentStatus)));
        }

        final Condition c = condition;

        return JooqUtil.contextResult(connectionProvider, context -> context
                .update(meta)
                .set(meta.STATUS, MetaStatusId.getPrimitiveValue(newStatus))
                .set(meta.STATUS_TIME, statusTime)
                .where(c)
                .execute());
    }

    private SelectConditionStep<Record1<Long>> getMetaCondition(final ExpressionOperator expression) {
        if (expression == null) {
            return null;
        }

        final Condition condition = metaExpressionMapper.apply(expression);
        if (condition == null) {
            return null;
        }

        return selectDistinct(metaVal.META_ID)
                .from(metaVal)
                .where(condition);
    }

    @Override
    public List<Meta> find(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Condition condition = createCondition(criteria);
        final OrderField[] orderFields = createOrderFields(criteria);

        int offset = 0;
        int numberOfRows = 1000000;

        if (pageRequest != null) {
            offset = pageRequest.getOffset().intValue();
            numberOfRows = pageRequest.getLength();
        }

        return find(condition, orderFields, offset, numberOfRows);
    }

    private List<Meta> find(final Condition condition, final OrderField[] orderFields, final int offset, final int numberOfRows) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(
                        meta.ID,
                        metaFeed.NAME,
                        metaType.NAME,
                        metaProcessor.PROCESSOR_UUID,
                        metaProcessor.PIPELINE_UUID,
                        meta.PARENT_ID,
                        meta.TASK_ID,
                        meta.STATUS,
                        meta.STATUS_TIME,
                        meta.CREATE_TIME,
                        meta.EFFECTIVE_TIME
                )
                .from(meta)
                .join(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID))
                .join(metaType).on(meta.TYPE_ID.eq(metaType.ID))
                .leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID))
                .where(condition)
                .orderBy(orderFields)
                .limit(offset, numberOfRows)
                .fetch()
                .map(RECORD_TO_META_MAPPER::apply));
    }

    @Override
    public int delete(final FindMetaCriteria criteria) {
        final Condition condition = createCondition(criteria);
        return JooqUtil.contextResult(connectionProvider, context -> context
                .deleteFrom(meta)
                .where(condition)
                .execute());
    }

    @Override
    public Optional<Long> getMaxId(final FindMetaCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());

        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(max(meta.ID))
                .from(meta)
                .where(condition)
                .fetchOptional()
                .map(Record1::value1));
    }

    @Override
    public int getLockCount() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .selectCount()
                .from(meta)
                .where(meta.STATUS.eq(MetaStatusId.LOCKED))
                .fetchOptional()
                .map(Record1::value1)
                .orElse(0));
    }

    @Override
    public void clear() {
        JooqUtil.context(connectionProvider, context -> context
                .delete(META)
                .execute());
    }

    private Condition createCondition(final FindMetaCriteria criteria) {
        Condition condition;

        IdSet idSet = null;
        ExpressionOperator expression = null;

        if (criteria != null) {
            idSet = criteria.getSelectedIdSet();
            expression = criteria.getExpression();
        }

        condition = expressionMapper.apply(expression);

        // If we aren't being asked to match everything then add constraints to the expression.
        if (idSet != null && (idSet.getMatchAll() == null || !idSet.getMatchAll())) {
            condition = and(condition, meta.ID.in(idSet.getSet()));
        }

        // Get additional selection criteria based on meta data attributes;
        final SelectConditionStep<Record1<Long>> metaConditionStep = getMetaCondition(expression);
        if (metaConditionStep != null) {
            condition = and(condition, meta.ID.in(metaConditionStep));
        }

        return condition;
    }

    private Condition and(final Condition c1, final Condition c2) {
        if (c1 == null) {
            return c2;
        }
        if (c2 == null) {
            return c1;
        }
        return c1.and(c2);
    }

    private OrderField[] createOrderFields(final FindMetaCriteria criteria) {
        if (criteria.getSortList() == null || criteria.getSortList().size() == 0) {
            return new OrderField[]{meta.ID};
        }

        return criteria.getSortList().stream().map(sort -> {
            Field field = meta.ID;
            if (FindMetaCriteria.FIELD_ID.equals(sort.getField())) {
                field = meta.ID;
            } else if (FindMetaCriteria.FIELD_FEED.equals(sort.getField())) {
                field = metaFeed.NAME;
            } else if (FindMetaCriteria.FIELD_TYPE.equals(sort.getField())) {
                field = metaType.NAME;
            }

            OrderField orderField = field;
            if (Sort.Direction.DESCENDING.equals(sort.getDirection())) {
                orderField = field.desc();
            }

            return orderField;
        }).toArray(OrderField[]::new);
    }
}
