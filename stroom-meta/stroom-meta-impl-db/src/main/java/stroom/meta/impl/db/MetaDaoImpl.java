package stroom.meta.impl.db;

import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import stroom.collection.api.CollectionService;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.datasource.api.v2.AbstractField;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.db.util.ValueMapper;
import stroom.db.util.ValueMapper.Mapper;
import stroom.dictionary.api.WordListProvider;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.impl.MetaDao;
import stroom.meta.impl.db.jooq.tables.MetaFeed;
import stroom.meta.impl.db.jooq.tables.MetaProcessor;
import stroom.meta.impl.db.jooq.tables.MetaType;
import stroom.meta.impl.db.jooq.tables.MetaVal;
import stroom.meta.shared.ExpressionUtil;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaProperties;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.util.date.DateUtil;
import stroom.util.shared.IdSet;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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
            .status(MetaStatusId.getStatus(record.get(meta.STATUS)))
            .statusMs(record.get(meta.STATUS_TIME))
            .createMs(record.get(meta.CREATE_TIME))
            .effectiveMs(record.get(meta.EFFECTIVE_TIME))
            .build();

    private final ConnectionProvider connectionProvider;
    private final MetaFeedDaoImpl feedDao;
    private final MetaTypeDaoImpl metaTypeDao;
    private final MetaProcessorDaoImpl metaProcessorDao;
    private final MetaKeyDaoImpl metaKeyDao;

    private final ExpressionMapper expressionMapper;
    private final MetaExpressionMapper metaExpressionMapper;
    private final ValueMapper valueMapper;

    @Inject
    MetaDaoImpl(final ConnectionProvider connectionProvider,
                final MetaFeedDaoImpl feedDao,
                final MetaTypeDaoImpl metaTypeDao,
                final MetaProcessorDaoImpl metaProcessorDao,
                final MetaKeyDaoImpl metaKeyDao,
                final ExpressionMapperFactory expressionMapperFactory,
                final WordListProvider wordListProvider,
                final CollectionService collectionService) {
        this.connectionProvider = connectionProvider;
        this.feedDao = feedDao;
        this.metaTypeDao = metaTypeDao;
        this.metaProcessorDao = metaProcessorDao;
        this.metaKeyDao = metaKeyDao;

        // Standard fields.
        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(MetaFields.ID, meta.ID, Long::valueOf);
        expressionMapper.multiMap(MetaFields.FEED, meta.FEED_ID, feedDao::find, true);
        expressionMapper.multiMap(MetaFields.FEED_NAME, meta.FEED_ID, feedDao::find);
        expressionMapper.multiMap(MetaFields.TYPE_NAME, meta.TYPE_ID, metaTypeDao::find);
        expressionMapper.map(MetaFields.PIPELINE, metaProcessor.PIPELINE_UUID, value -> value);
        expressionMapper.map(MetaFields.PARENT_ID, meta.PARENT_ID, Long::valueOf);
        expressionMapper.map(MetaFields.PROCESSOR_ID, meta.PROCESSOR_ID, Integer::valueOf);
        expressionMapper.map(MetaFields.STATUS, meta.STATUS, value -> MetaStatusId.getPrimitiveValue(Status.valueOf(value.toUpperCase())));
        expressionMapper.map(MetaFields.STATUS_TIME, meta.STATUS_TIME, DateUtil::parseNormalDateTimeString);
        expressionMapper.map(MetaFields.CREATE_TIME, meta.CREATE_TIME, DateUtil::parseNormalDateTimeString);
        expressionMapper.map(MetaFields.EFFECTIVE_TIME, meta.EFFECTIVE_TIME, DateUtil::parseNormalDateTimeString);
        expressionMapper.ignoreField(MetaFields.REC_READ);
        expressionMapper.ignoreField(MetaFields.REC_WRITE);
        expressionMapper.ignoreField(MetaFields.REC_INFO);
        expressionMapper.ignoreField(MetaFields.REC_WARN);
        expressionMapper.ignoreField(MetaFields.REC_ERROR);
        expressionMapper.ignoreField(MetaFields.REC_FATAL);
        expressionMapper.ignoreField(MetaFields.DURATION);
        expressionMapper.ignoreField(MetaFields.FILE_SIZE);
        expressionMapper.ignoreField(MetaFields.RAW_SIZE);

        // Extended meta fields.
        metaExpressionMapper = new MetaExpressionMapper(metaKeyDao, metaVal.META_KEY_ID, metaVal.VAL, wordListProvider, collectionService);
//        metaTermHandlers.put(StreamDataSource.NODE, createMetaTermHandler(StreamDataSource.NODE));
        metaExpressionMapper.map(MetaFields.REC_READ);
        metaExpressionMapper.map(MetaFields.REC_WRITE);
        metaExpressionMapper.map(MetaFields.REC_INFO);
        metaExpressionMapper.map(MetaFields.REC_WARN);
        metaExpressionMapper.map(MetaFields.REC_ERROR);
        metaExpressionMapper.map(MetaFields.REC_FATAL);
        metaExpressionMapper.map(MetaFields.DURATION);
        metaExpressionMapper.map(MetaFields.FILE_SIZE);
        metaExpressionMapper.map(MetaFields.RAW_SIZE);

        valueMapper = new ValueMapper();
        valueMapper.map(MetaFields.ID, meta.ID, ValLong::create);
        valueMapper.map(MetaFields.FEED, metaFeed.NAME, ValString::create);
        valueMapper.map(MetaFields.FEED_NAME, metaFeed.NAME, ValString::create);
        valueMapper.map(MetaFields.TYPE_NAME, metaType.NAME, ValString::create);
        valueMapper.map(MetaFields.PIPELINE, metaProcessor.PIPELINE_UUID, ValString::create);
        valueMapper.map(MetaFields.PARENT_ID, meta.PARENT_ID, ValLong::create);
        valueMapper.map(MetaFields.PROCESSOR_ID, meta.PROCESSOR_ID, ValInteger::create);
        valueMapper.map(MetaFields.STATUS, meta.STATUS, v -> Optional.ofNullable(MetaStatusId.getStatus(v))
                .map(w -> (Val) ValString.create(w.getDisplayValue()))
                .orElse(ValNull.INSTANCE));
        valueMapper.map(MetaFields.STATUS_TIME, meta.STATUS_TIME, ValLong::create);
        valueMapper.map(MetaFields.CREATE_TIME, meta.CREATE_TIME, ValLong::create);
        valueMapper.map(MetaFields.EFFECTIVE_TIME, meta.EFFECTIVE_TIME, ValLong::create);
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
                        META.FEED_ID,
                        META.TYPE_ID,
                        META.PROCESSOR_ID)
                .values(
                        metaProperties.getCreateMs(),
                        metaProperties.getEffectiveMs(),
                        metaProperties.getParentId(),
                        MetaStatusId.LOCKED,
                        metaProperties.getStatusMs(),
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
    public void search(final ExpressionCriteria criteria, final AbstractField[] fields, final Consumer<Val[]> consumer) {
        final List<AbstractField> fieldList = Arrays.asList(fields);
        final int feedTermCount = ExpressionUtil.termCount(criteria.getExpression(), Set.of(MetaFields.FEED, MetaFields.FEED_NAME));
        final boolean feedValueExists = fieldList.stream().anyMatch(Set.of(MetaFields.FEED, MetaFields.FEED_NAME)::contains);
        final int typeTermCount = ExpressionUtil.termCount(criteria.getExpression(), MetaFields.TYPE_NAME);
        final boolean typeValueExists = fieldList.stream().anyMatch(Predicate.isEqual(MetaFields.TYPE_NAME));
        final int processorTermCount = ExpressionUtil.termCount(criteria.getExpression(), MetaFields.PIPELINE);
        final boolean processorValueExists = fieldList.stream().anyMatch(Predicate.isEqual(MetaFields.PIPELINE));
//        final int extendedTermCount = ExpressionUtil.termCount(criteria.getExpression(), MetaFields.getExtendedFields());
        final boolean extendedValuesExist = fieldList.stream().anyMatch(MetaFields.getExtendedFields()::contains);

        final PageRequest pageRequest = criteria.getPageRequest();
        final Condition condition = createCondition(criteria.getExpression(), null);
        final OrderField[] orderFields = createOrderFields(criteria);
        final List<Field<?>> dbFields = new ArrayList<>(valueMapper.getFields(fieldList));
        final Mapper[] mappers = valueMapper.getMappers(fields);

        // Deal with extended fields.
        final int[] extendedFieldKeys = new int[fields.length];
        final List<Integer> extendedFieldKeyIdList = new ArrayList<>();
        final Map<Long, Map<Integer, Long>> extendedFieldValueMap = new HashMap<>();
        for (int i = 0; i < fields.length; i++) {
            final int index = i;
            final AbstractField field = fields[i];
            extendedFieldKeys[i] = -1;

            if (MetaFields.getExtendedFields().contains(field)) {
                final Optional<Integer> keyId = metaKeyDao.getIdForName(field.getName());
                keyId.ifPresent(id -> {
                    extendedFieldKeys[index] = id;
                    extendedFieldKeyIdList.add(id);
                });
            }
        }

        // Need to modify requested fields to include id if we are going to fetch extended attributes.
        if (extendedValuesExist) {
            if (dbFields.stream().noneMatch(meta.ID::equals)) {
                dbFields.add(meta.ID);
            }
        }

        JooqUtil.context(connectionProvider, context -> {
            int offset = 0;
            int numberOfRows = 1000000;

            if (pageRequest != null) {
                offset = pageRequest.getOffset().intValue();
                numberOfRows = pageRequest.getLength();
            }

            var select = context.select(dbFields).from(meta);
            if (feedTermCount > 0 || feedValueExists) {
                select = select.join(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID));
            }
            if (typeTermCount > 0 || typeValueExists) {
                select = select.join(metaType).on(meta.TYPE_ID.eq(metaType.ID));
            }
            if (processorTermCount > 0 || processorValueExists) {
                select = select.leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID));
            }

            try (final Cursor<?> cursor = select
                    .where(condition)
                    .orderBy(orderFields)
                    .limit(offset, numberOfRows)
                    .fetchLazy()) {

                while (cursor.hasNext()) {
                    final Result<?> result = cursor.fetchNext(1000);

                    // If we require some extended values then perform another query to get them.
                    if (extendedValuesExist) {
                        final List<Long> idList = result.getValues(meta.ID);
                        fillExtendedFieldValueMap(context, idList, extendedFieldKeyIdList, extendedFieldValueMap);
                    }

                    result.forEach(r -> {
                        final Val[] arr = new Val[fields.length];

                        Map<Integer, Long> extendedValues = null;
                        if (extendedValuesExist) {
                            extendedValues = extendedFieldValueMap.get(r.get(meta.ID));
                        }

                        for (int i = 0; i < fields.length; i++) {
                            Val val = ValNull.INSTANCE;
                            final Mapper<?> mapper = mappers[i];
                            if (mapper != null) {
                                val = mapper.map(r);

                            } else if (extendedValues != null && extendedFieldKeys[i] != -1) {
                                final Long o = extendedValues.get(extendedFieldKeys[i]);
                                if (o != null) {
                                    val = ValLong.create(o);
                                }
                            }
                            arr[i] = val;
                        }
                        consumer.accept(arr);
                    });
                }
            }
        });
    }

    private void fillExtendedFieldValueMap(final DSLContext context, final List<Long> idList, final List<Integer> extendedFieldKeyIdList, final Map<Long, Map<Integer, Long>> extendedFieldValueMap) {
        extendedFieldValueMap.clear();
        context
                .select(
                        META_VAL.META_ID,
                        META_VAL.META_KEY_ID,
                        META_VAL.VAL
                )
                .from(META_VAL)
                .where(META_VAL.META_ID.in(idList))
                .and(META_VAL.META_KEY_ID.in(extendedFieldKeyIdList))
                .fetch()
                .forEach(r -> {
                    final long dataId = r.get(META_VAL.META_ID);
                    final int keyId = r.get(META_VAL.META_KEY_ID);
                    final long value = r.get(META_VAL.VAL);
                    extendedFieldValueMap
                            .computeIfAbsent(dataId, k -> new HashMap<>())
                            .put(keyId, value);
                });
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
        return createCondition(criteria.getExpression(), criteria.getSelectedIdSet());
    }

    private Condition createCondition(final ExpressionOperator expression, final IdSet idSet) {
        Condition condition = expressionMapper.apply(expression);

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

    private OrderField[] createOrderFields(final ExpressionCriteria criteria) {
        if (criteria.getSortList() == null || criteria.getSortList().size() == 0) {
            return new OrderField[]{meta.ID};
        }

        return criteria.getSortList().stream().map(sort -> {
            Field field;
            if (MetaFields.FIELD_ID.equals(sort.getField())) {
                field = meta.ID;
            } else if (MetaFields.FIELD_FEED.equals(sort.getField())) {
                field = metaFeed.NAME;
            } else if (MetaFields.FIELD_TYPE.equals(sort.getField())) {
                field = metaType.NAME;
            } else {
                field = meta.ID;
            }

            OrderField orderField = field;
            if (Sort.Direction.DESCENDING.equals(sort.getDirection())) {
                orderField = field.desc();
            }

            return orderField;
        }).toArray(OrderField[]::new);
    }
}
