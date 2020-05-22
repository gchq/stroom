package stroom.meta.impl.db;

import stroom.collection.api.CollectionService;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.data.retention.shared.DataRetentionRuleAction;
import stroom.datasource.api.v2.AbstractField;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.db.util.ValueMapper;
import stroom.db.util.ValueMapper.Mapper;
import stroom.dictionary.api.WordListProvider;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaProperties;
import stroom.meta.impl.MetaDao;
import stroom.meta.impl.db.jooq.tables.MetaFeed;
import stroom.meta.impl.db.jooq.tables.MetaProcessor;
import stroom.meta.impl.db.jooq.tables.MetaType;
import stroom.meta.impl.db.jooq.tables.MetaVal;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.util.date.DateUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Sort;
import stroom.util.time.TimePeriod;

import org.jooq.CaseConditionStep;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.selectDistinct;
import static stroom.meta.impl.db.jooq.tables.Meta.META;
import static stroom.meta.impl.db.jooq.tables.MetaFeed.META_FEED;
import static stroom.meta.impl.db.jooq.tables.MetaProcessor.META_PROCESSOR;
import static stroom.meta.impl.db.jooq.tables.MetaType.META_TYPE;
import static stroom.meta.impl.db.jooq.tables.MetaVal.META_VAL;

@Singleton
class MetaDaoImpl implements MetaDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetaDaoImpl.class);

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

    private final MetaDbConnProvider metaDbConnProvider;
    private final MetaFeedDaoImpl feedDao;
    private final MetaTypeDaoImpl metaTypeDao;
    private final MetaProcessorDaoImpl metaProcessorDao;
    private final MetaKeyDaoImpl metaKeyDao;

    private final ExpressionMapper expressionMapper;
    private final MetaExpressionMapper metaExpressionMapper;
    private final ValueMapper valueMapper;

    @Inject
    MetaDaoImpl(final MetaDbConnProvider metaDbConnProvider,
                final MetaFeedDaoImpl feedDao,
                final MetaTypeDaoImpl metaTypeDao,
                final MetaProcessorDaoImpl metaProcessorDao,
                final MetaKeyDaoImpl metaKeyDao,
                final ExpressionMapperFactory expressionMapperFactory,
                final WordListProvider wordListProvider,
                final CollectionService collectionService) {
        this.metaDbConnProvider = metaDbConnProvider;
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
        metaExpressionMapper = new MetaExpressionMapper(
                metaKeyDao,
                metaVal.META_KEY_ID,
                metaVal.VAL,
                wordListProvider,
                collectionService);
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
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
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
        final Integer processorId = metaProcessorDao.getOrCreate(
                metaProperties.getProcessorUuid(), metaProperties.getPipelineUuid());

        final long id = JooqUtil.contextResult(metaDbConnProvider, context -> context
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
    public int updateStatus(final FindMetaCriteria criteria,
                            final Status currentStatus,
                            final Status newStatus,
                            final long statusTime) {
        Objects.requireNonNull(newStatus, "New status is null");
        if (Objects.equals(newStatus, currentStatus)) {
            // The status is not being updated.
            throw new RuntimeException("New and current status are equal");
        }

        final byte newStatusId = MetaStatusId.getPrimitiveValue(newStatus);

        Collection<Condition> conditions = expressionMapper.apply(criteria.getExpression());


        // select
        // 	v2.*
        // from (
        // 	select
        // 	v.*,
        // 	CASE
        // 		WHEN
        // 			-- purge DS events older than 10 mins
        //             v.create_time < DATE_SUB(now(), INTERVAL 10 MINUTE)
        // 			AND feed_name = 'DATA_SPLITTER-EVENTS'
        // 			THEN 1
        // 		WHEN
        //             -- purge all events except json_events older than 1 month
        // 			v.create_time < DATE_SUB(now(), INTERVAL 1 MONTH)
        //             AND feed_name <> 'JSON-EVENTS'
        // 			THEN 2
        // 		WHEN
        // 			-- purge all events older than 1 year
        //             v.create_time < DATE_SUB(now(), INTERVAL 1 YEAR)
        // 			THEN 3
        // 		ELSE NULL
        // 	END effective_rule
        // 	from (
        // 		select
        // 			from_unixtime(m.create_time /1000) create_time,
        // 			from_unixtime(m.effective_time /1000) effective_time,
        // 			mf.name feed_name,
        // 			mt.name type_name
        // 		from meta m
        // 		left join meta_feed mf on m.feed_id = mf.id
        // 		left join meta_type mt on m.type_id = mt.id
        // 		order by m.create_time desc
        // 	) v
        // ) v2
        // order by v2.effective_rule;


        final int updateCount;
        // If a rule means we are retaining data then we will have a 1=0 condition in here
        // and as they are all to be ANDed together there is no point in running the sql.
        if (conditions.size() == 1 && conditions.contains(DSL.falseCondition())) {
            LOGGER.info("Condition is FALSE so skipping SQL update");
            updateCount = 0;
        } else {
            // Add a condition if we should check current status.
            if (currentStatus != null) {
                final byte currentStatusId = MetaStatusId.getPrimitiveValue(currentStatus);
                conditions = Stream
                        .concat(conditions.stream(), Stream.of(meta.STATUS.eq(currentStatusId)))
                        .collect(Collectors.toList());
            } else {
                conditions = Stream
                        .concat(conditions.stream(), Stream.of(meta.STATUS.ne(newStatusId)))
                        .collect(Collectors.toList());
            }

            final Collection<Condition> c = conditions;

            // TODO consider using criteria.getPageLength (i.e. batch size) to limit update size
            //   and number of records locked.  Would mean ignoring already updated rows which may
            //   make it more costly.
            updateCount = JooqUtil.contextResult(metaDbConnProvider, context -> context
                    .update(meta)
                    .set(meta.STATUS, newStatusId)
                    .set(meta.STATUS_TIME, statusTime)
                    .where(c)
                    .execute());
        }
        return updateCount;
    }

    @Override
    public int logicalDelete(final List<DataRetentionRuleAction> ruleActions,
                             final TimePeriod period,
                             final int batchSize) {

        final int updateCount;
        if (ruleActions != null && !ruleActions.isEmpty()) {
            final byte statusIdDeleted = MetaStatusId.getPrimitiveValue(Status.DELETED);
            final byte statusIdUnlocked = MetaStatusId.getPrimitiveValue(Status.UNLOCKED);

            // What we are building is roughly:
            // WHERE (CASE
            //   WHEN <rule 3 condition is true> THEN true
            //   WHEN <rule 2 condition is true> THEN true
            //   WHEN <rule 1 condition is true> THEN true
            //   ELSE false
            //   ) = true
            // I.e. see if a rule matches the data in priority order
            // Needs to be a CASE so we can ensure the order of rule evaluation

            CaseConditionStep<Boolean> caseConditionStep = null;
            // Order is critical here as we are building a case statement
            // Highest priority rules first, i.e. largest rule number
            for (DataRetentionRuleAction ruleAction : ruleActions) {
                // TODO change mapper to return a Condition not a collection

                final Collection<Condition> ruleConditions = expressionMapper.apply(ruleAction.getRule().getExpression());
                final Condition ruleCondition = ruleConditions.iterator().next();

                if (caseConditionStep == null) {
                    caseConditionStep = DSL.when(ruleCondition, Boolean.TRUE);
                } else {
                    caseConditionStep.when(ruleCondition, Boolean.TRUE);
                }
            }
            // If none of the rules matches then we don't to delete so return false
            final Field<Boolean> caseField = caseConditionStep.otherwise(Boolean.FALSE);

            List<Condition> conditions = new ArrayList<>();

            // Time bound the data we are testing the rules over
            conditions.add(meta.CREATE_TIME.greaterOrEqual(period.getFrom().toEpochMilli()));
            conditions.add(meta.CREATE_TIME.lessThan(period.getTo().toEpochMilli()));

            // Ensure we only 'delete' unlocked records
            conditions.add(meta.STATUS.eq(statusIdUnlocked));

            // Add our rule conditions
            conditions.add(caseField.eq(true));

            LOGGER.debug("conditions {}", conditions);

            updateCount = JooqUtil.contextResult(metaDbConnProvider, context -> context
                    .update(meta)
                    .set(meta.STATUS, statusIdDeleted)
                    .set(meta.STATUS_TIME, Instant.now().toEpochMilli())
                    .where(conditions)
                    .limit(batchSize)
                    .execute());
        } else {
            updateCount = 0;
        }
        return updateCount;
    }

    private Optional<SelectConditionStep<Record1<Long>>> getMetaCondition(final ExpressionOperator expression) {
        if (expression == null) {
            return Optional.empty();
        }

        final Collection<Condition> conditions = metaExpressionMapper.apply(expression);
        if (conditions.size() == 0) {
            return Optional.empty();
        }

        return Optional.of(selectDistinct(metaVal.META_ID)
                .from(metaVal)
                .where(conditions));
    }

    @Override
    public ResultPage<Meta> find(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);

        int offset = 0;
        int numberOfRows = 1000000;

        if (pageRequest != null && pageRequest.getOffset() != null)
            offset = pageRequest.getOffset().intValue();
        if (pageRequest != null && pageRequest.getLength() != null)
            numberOfRows = pageRequest.getLength();


        final List<Meta> list = find(conditions, orderFields, offset, numberOfRows + 1);
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private List<Meta> find(final Collection<Condition> conditions,
                            final Collection<OrderField<?>> orderFields,
                            final int offset,
                            final int numberOfRows) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
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
                .where(conditions)
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
        final Collection<Condition> conditions = createCondition(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);
        final List<Field<?>> dbFields = new ArrayList<>(valueMapper.getFields(fieldList));
        final Mapper<?>[] mappers = valueMapper.getMappers(fields);

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

        JooqUtil.context(metaDbConnProvider, context -> {
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
                    .where(conditions)
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

    private void fillExtendedFieldValueMap(final DSLContext context,
                                           final List<Long> idList,
                                           final List<Integer> extendedFieldKeyIdList,
                                           final Map<Long, Map<Integer, Long>> extendedFieldValueMap) {
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

//    @Override
//    public int delete(final FindMetaCriteria criteria) {
//        final Condition condition = createCondition(criteria);
//        return JooqUtil.contextResult(metaDbConnProvider, context -> context
//                .deleteFrom(meta)
//                .where(condition)
//                .execute());
//    }

    @Override
    public int delete(final List<Long> metaIdList) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .deleteFrom(meta)
                .where(meta.ID.in(metaIdList))
                .execute());
    }

    @Override
    public Optional<Long> getMaxId(final FindMetaCriteria criteria) {
        final Collection<Condition> conditions = expressionMapper.apply(criteria.getExpression());

        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(max(meta.ID))
                .from(meta)
                .where(conditions)
                .fetchOptional()
                .map(Record1::value1));
    }

    @Override
    public int getLockCount() {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .selectCount()
                .from(meta)
                .where(meta.STATUS.eq(MetaStatusId.LOCKED))
                .fetchOptional()
                .map(Record1::value1)
                .orElse(0));
    }

    @Override
    public void clear() {
        JooqUtil.context(metaDbConnProvider, context -> context
                .delete(META)
                .execute());
    }

    private Collection<Condition> createCondition(final FindMetaCriteria criteria) {
        return createCondition(criteria.getExpression());
    }

    private Collection<Condition> createCondition(final ExpressionOperator expression) {
        Collection<Condition> conditions = expressionMapper.apply(expression);

//        // If we aren't being asked to match everything then add constraints to the expression.
//        if (idSet != null && (idSet.getMatchAll() == null || !idSet.getMatchAll())) {
//            condition = and(condition, meta.ID.in(idSet.getSet()));
//        }

        // Get additional selection criteria based on meta data attributes;
        final Optional<SelectConditionStep<Record1<Long>>> metaConditionStep = getMetaCondition(expression);
        if (metaConditionStep.isPresent()) {
            conditions = Stream
                    .concat(conditions.stream(), Stream.of(meta.ID.in(metaConditionStep.get())))
                    .collect(Collectors.toList());
        }

        return conditions;
    }

    private Collection<OrderField<?>> createOrderFields(final ExpressionCriteria criteria) {
        if (criteria.getSortList() == null || criteria.getSortList().size() == 0) {
            return Collections.singleton(meta.ID);
        }

        return criteria.getSortList().stream().map(sort -> {
            Field<?> field;
            if (MetaFields.ID.getName().equals(sort.getField())) {
                field = meta.ID;
            } else if (MetaFields.CREATE_TIME.getName().equals(sort.getField())) {
                field = meta.CREATE_TIME;
            } else if (MetaFields.FEED_NAME.getName().equals(sort.getField())) {
                field = metaFeed.NAME;
            } else if (MetaFields.TYPE_NAME.getName().equals(sort.getField())) {
                field = metaType.NAME;
            } else {
                field = meta.ID;
            }

            OrderField<?> orderField = field;
            if (Sort.Direction.DESCENDING.equals(sort.getDirection())) {
                orderField = field.desc();
            }

            return orderField;
        }).collect(Collectors.toList());
    }
}
