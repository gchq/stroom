package stroom.meta.impl.db;

import stroom.collection.api.CollectionService;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.data.retention.api.DataRetentionConfig;
import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.FindDataRetentionImpactCriteria;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DateField;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.db.util.ValueMapper;
import stroom.db.util.ValueMapper.Mapper;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaProperties;
import stroom.meta.impl.MetaDao;
import stroom.meta.impl.db.jooq.tables.MetaFeed;
import stroom.meta.impl.db.jooq.tables.MetaProcessor;
import stroom.meta.impl.db.jooq.tables.MetaType;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.common.v2.DateExpressionParser;
import stroom.util.collections.BatchingIterator;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;
import stroom.util.time.TimePeriod;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.jooq.CaseConditionStep;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.Select;
import org.jooq.Table;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static stroom.meta.impl.db.jooq.tables.Meta.META;
import static stroom.meta.impl.db.jooq.tables.MetaFeed.META_FEED;
import static stroom.meta.impl.db.jooq.tables.MetaProcessor.META_PROCESSOR;
import static stroom.meta.impl.db.jooq.tables.MetaType.META_TYPE;
import static stroom.meta.impl.db.jooq.tables.MetaVal.META_VAL;

@Singleton
class MetaDaoImpl implements MetaDao, Clearable {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaDaoImpl.class);

    // This is currently only used for testing so no need to put it in config,
    // unless it gets used in real code.
    private static final int MAX_VALUES_PER_INSERT = 500;

    private static final int FIND_RECORD_LIMIT = 1000000;

    private static final stroom.meta.impl.db.jooq.tables.Meta meta = META.as("m");
    private static final MetaFeed metaFeed = META_FEED.as("f");
    private static final MetaType metaType = META_TYPE.as("t");
    private static final MetaProcessor metaProcessor = META_PROCESSOR.as("p");

    private static final stroom.meta.impl.db.jooq.tables.Meta parent = META.as("parent");
    private static final MetaFeed parentFeed = META_FEED.as("parentFeed");
    private static final MetaType parentType = META_TYPE.as("parentType");
    private static final MetaProcessor parentProcessor = META_PROCESSOR.as("parentProcessor");

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

    private static final Function<Record, Meta> RECORD_TO_PARENT_META_MAPPER = record -> new Meta.Builder()
            .id(record.get(parent.ID))
            .feedName(record.get(parentFeed.NAME))
            .typeName(record.get(parentType.NAME))
            .processorUuid(record.get(parentProcessor.PROCESSOR_UUID))
            .pipelineUuid(record.get(parentProcessor.PIPELINE_UUID))
            .parentDataId(record.get(parent.PARENT_ID))
            .status(MetaStatusId.getStatus(record.get(parent.STATUS)))
            .statusMs(record.get(parent.STATUS_TIME))
            .createMs(record.get(parent.CREATE_TIME))
            .effectiveMs(record.get(parent.EFFECTIVE_TIME))
            .build();

    private final MetaDbConnProvider metaDbConnProvider;
    private final MetaFeedDaoImpl feedDao;
    private final MetaTypeDaoImpl metaTypeDao;
    private final MetaProcessorDaoImpl metaProcessorDao;
    private final MetaKeyDaoImpl metaKeyDao;
    private final DataRetentionConfig dataRetentionConfig;
    private final DocRefInfoService docRefInfoService;

    private final ExpressionMapper expressionMapper;
    private final MetaExpressionMapper metaExpressionMapper;
    private final ValueMapper valueMapper;

    private final Map<String, List<Integer>> feedIdCache = new ConcurrentHashMap<>();
    private final Map<String, List<Integer>> typeIdCache = new ConcurrentHashMap<>();

    @Inject
    MetaDaoImpl(final MetaDbConnProvider metaDbConnProvider,
                final MetaFeedDaoImpl feedDao,
                final MetaTypeDaoImpl metaTypeDao,
                final MetaProcessorDaoImpl metaProcessorDao,
                final MetaKeyDaoImpl metaKeyDao,
                final DataRetentionConfig dataRetentionConfig,
                final ExpressionMapperFactory expressionMapperFactory,
                final WordListProvider wordListProvider,
                final CollectionService collectionService,
                final DocRefInfoService docRefInfoService) {
        this.metaDbConnProvider = metaDbConnProvider;
        this.feedDao = feedDao;
        this.metaTypeDao = metaTypeDao;
        this.metaProcessorDao = metaProcessorDao;
        this.metaKeyDao = metaKeyDao;
        this.dataRetentionConfig = dataRetentionConfig;
        this.docRefInfoService = docRefInfoService;


        // Extended meta fields.
        metaExpressionMapper = new MetaExpressionMapper(
                metaKeyDao,
                META_VAL.META_KEY_ID.getName(),
                META_VAL.VAL.getName(),
                META_VAL.META_ID.getName(),
                MetaFields.getExtendedFields().size(),
                wordListProvider,
                collectionService);
        //Add term handlers
        metaExpressionMapper.map(MetaFields.REC_READ);
        metaExpressionMapper.map(MetaFields.REC_WRITE);
        metaExpressionMapper.map(MetaFields.REC_INFO);
        metaExpressionMapper.map(MetaFields.REC_WARN);
        metaExpressionMapper.map(MetaFields.REC_ERROR);
        metaExpressionMapper.map(MetaFields.REC_FATAL);
        metaExpressionMapper.map(MetaFields.DURATION);
        metaExpressionMapper.map(MetaFields.FILE_SIZE);
        metaExpressionMapper.map(MetaFields.RAW_SIZE);

        // Standard fields.
        expressionMapper = expressionMapperFactory.create(metaExpressionMapper);
        expressionMapper.map(MetaFields.ID, meta.ID, Long::valueOf);
        expressionMapper.map(MetaFields.META_INTERNAL_PROCESSOR_ID, meta.PROCESSOR_ID, Integer::valueOf);
        expressionMapper.multiMap(MetaFields.FEED, meta.FEED_ID, this::getFeedIds, true);
        expressionMapper.multiMap(MetaFields.FEED_NAME, meta.FEED_ID, this::getFeedIds);
        expressionMapper.multiMap(MetaFields.TYPE_NAME, meta.TYPE_ID, this::getTypeIds);
        expressionMapper.map(MetaFields.PIPELINE, metaProcessor.PIPELINE_UUID, value -> value);
        expressionMapper.map(MetaFields.STATUS, meta.STATUS, value -> MetaStatusId.getPrimitiveValue(Status.valueOf(value.toUpperCase())));
        expressionMapper.map(MetaFields.STATUS_TIME, meta.STATUS_TIME, value -> getDate(MetaFields.STATUS_TIME, value));
        expressionMapper.map(MetaFields.CREATE_TIME, meta.CREATE_TIME, value -> getDate(MetaFields.CREATE_TIME, value));
        expressionMapper.map(MetaFields.EFFECTIVE_TIME, meta.EFFECTIVE_TIME, value -> getDate(MetaFields.EFFECTIVE_TIME, value));

        // Parent fields.
        expressionMapper.map(MetaFields.PARENT_ID, meta.PARENT_ID, Long::valueOf);
        expressionMapper.map(MetaFields.PARENT_STATUS, parent.STATUS, value -> MetaStatusId.getPrimitiveValue(Status.valueOf(value.toUpperCase())));
        expressionMapper.map(MetaFields.PARENT_CREATE_TIME, parent.CREATE_TIME, value -> getDate(MetaFields.PARENT_CREATE_TIME, value));
        expressionMapper.multiMap(MetaFields.PARENT_FEED, parent.FEED_ID, this::getFeedIds);


        valueMapper = new ValueMapper();

        valueMapper.map(MetaFields.ID, meta.ID, ValLong::create);
        valueMapper.map(MetaFields.FEED, metaFeed.NAME, ValString::create);
        valueMapper.map(MetaFields.FEED_NAME, metaFeed.NAME, ValString::create);
        valueMapper.map(MetaFields.TYPE_NAME, metaType.NAME, ValString::create);
        valueMapper.map(MetaFields.PIPELINE, metaProcessor.PIPELINE_UUID, this::getPipelineName);
        valueMapper.map(MetaFields.PARENT_ID, meta.PARENT_ID, ValLong::create);
        valueMapper.map(MetaFields.META_INTERNAL_PROCESSOR_ID, meta.PROCESSOR_ID, ValInteger::create);
        valueMapper.map(MetaFields.STATUS, meta.STATUS, v -> Optional.ofNullable(MetaStatusId.getStatus(v))
                .map(w -> (Val) ValString.create(w.getDisplayValue()))
                .orElse(ValNull.INSTANCE));
        valueMapper.map(MetaFields.STATUS_TIME, meta.STATUS_TIME, ValLong::create);
        valueMapper.map(MetaFields.CREATE_TIME, meta.CREATE_TIME, ValLong::create);
        valueMapper.map(MetaFields.EFFECTIVE_TIME, meta.EFFECTIVE_TIME, ValLong::create);
    }

    private long getDate(final DateField field, final String value) {
        try {
            final Optional<ZonedDateTime> optional = DateExpressionParser.parse(value, ZoneOffset.UTC.getId(), System.currentTimeMillis());

            return optional.orElseThrow(() -> new RuntimeException("Expected a standard date value for field \"" + field.getName()
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

    private List<Integer> getFeedIds(final String feedName) {
        return getIds(feedName, feedIdCache, feedDao::find);
    }

    private List<Integer> getTypeIds(final String typeName) {
        return getIds(typeName, typeIdCache, metaTypeDao::find);
    }

    /**
     * This method tries to get a list of ids from the supplied map and if not found it tries to fetch them from the
     * supplied function.
     * <p>
     * NOTE THAT THE CHOICE TO NOT USE COMPUTE ON THE MAP IS DELIBERATE TO PREVENT LOCKING.
     * THIS MIGHT LEAD TO A FEW DUPLICATE ATTEMPTS TO FIND THE SAME ID LIST BUT THAT IS PREFERRED TO SYNCHRONIZING ON
     * THE MAP KEY DURING DB QUERY.
     */
    private List<Integer> getIds(final String name,
                                 final Map<String, List<Integer>> map,
                                 final Function<String, List<Integer>> function) {
        List<Integer> list = map.get(name);
        if (list == null || list.size() == 0) {
            list = function.apply(name);
            if (list != null && list.size() > 0) {
                map.put(name, list);
            }
        }
        return list;
    }

    @Override
    public Long getMaxId() {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(DSL.max(meta.ID))
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

    /**
     * Method currently only here for test purposes as a means of bulk loading data.
     */
    public void create(final List<MetaProperties> metaPropertiesList, final Status status) {
        // ensure we have all the parent records and capture all their ids
        final Map<String, Integer> feedIds = metaPropertiesList.stream()
                .map(MetaProperties::getFeedName)
                .distinct()
                .map(feedName -> Tuple.of(feedName, feedDao.getOrCreate(feedName)))
                .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));

        final Map<String, Integer> typeIds = metaPropertiesList.stream()
                .map(MetaProperties::getTypeName)
                .distinct()
                .map(typeName -> Tuple.of(typeName, metaTypeDao.getOrCreate(typeName)))
                .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));

        final Map<String, Integer> processorIds = metaPropertiesList.stream()
                .map(metaProperties -> Tuple.of(metaProperties.getProcessorUuid(), metaProperties.getPipelineUuid()))
                .distinct()
                .map(tuple -> Tuple.of(
                        tuple._1(),
                        metaProcessorDao.getOrCreate(tuple._1(), tuple._2())))
                .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));

        byte statusId = MetaStatusId.getPrimitiveValue(status);

        // Create a batch of insert stmts, each with n value sets
        JooqUtil.context(metaDbConnProvider, context -> context
                .batch(
                        BatchingIterator.batchedStreamOf(metaPropertiesList, MAX_VALUES_PER_INSERT)
                                .map(metaPropertiesBatch -> {
                                    final var insertStep = context
                                            .insertInto(META,
                                                    META.CREATE_TIME,
                                                    META.EFFECTIVE_TIME,
                                                    META.PARENT_ID,
                                                    META.STATUS,
                                                    META.STATUS_TIME,
                                                    META.FEED_ID,
                                                    META.TYPE_ID,
                                                    META.PROCESSOR_ID);

                                    metaPropertiesBatch.forEach(metaProperties ->
                                            insertStep.values(
                                                    metaProperties.getCreateMs(),
                                                    metaProperties.getEffectiveMs(),
                                                    metaProperties.getParentId(),
                                                    statusId,
                                                    metaProperties.getStatusMs(),
                                                    feedIds.get(metaProperties.getFeedName()),
                                                    typeIds.get(metaProperties.getTypeName()),
                                                    processorIds.get(metaProperties.getProcessorUuid())));
                                    return insertStep;
                                })
                                .collect(Collectors.toList()))
                .execute());
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

        final Condition criteriaCondition = expressionMapper.apply(criteria.getExpression());

        final int updateCount;
        // If a rule means we are retaining data then we will have a 1=0 condition in here
        // and as they are all to be ANDed together there is no point in running the sql.
        if (DSL.falseCondition().equals(criteriaCondition)) {
            LOGGER.info("Condition is FALSE so skipping SQL update");
            updateCount = 0;
        } else {
            // Add a condition if we should check current status.
            final List<Condition> conditions;
            if (currentStatus != null) {
                final byte currentStatusId = MetaStatusId.getPrimitiveValue(currentStatus);
                conditions = Stream.of(criteriaCondition, meta.STATUS.eq(currentStatusId))
                        .collect(Collectors.toList());
            } else {
                conditions = Stream.of(criteriaCondition, meta.STATUS.ne(newStatusId))
                        .collect(Collectors.toList());
            }

            final Set<Integer> usedValKeys = identifyExtendedAttributesFields(criteria.getExpression(), new HashSet<>());

            if (usedValKeys.isEmpty() && !containsPipelineCondition (criteria.getExpression())) {
                updateCount = JooqUtil.contextResult(metaDbConnProvider, context ->
                        context
                                .update(meta)
                                .set(meta.STATUS, newStatusId)
                                .set(meta.STATUS_TIME, statusTime)
                                .where(conditions)
                                .execute());
            } else {
                Select ids = metaExpressionMapper.addJoins(
                        DSL.select(meta.ID).
                                from(meta).leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID)),
                        meta.ID,
                        usedValKeys)
                        .where(conditions);

                Condition extendedAttrCond = meta.ID.in(ids);
                updateCount = JooqUtil.contextResult(metaDbConnProvider, context ->
                        context
                                .update(meta)
                                .set(meta.STATUS, newStatusId)
                                .set(meta.STATUS_TIME, statusTime)
                                .where(extendedAttrCond)
                                .execute());
            }
        }
        return updateCount;
    }

    private boolean containsPipelineCondition(final ExpressionOperator expr) {
        if (expr == null || expr.getChildren() == null)
            return false;
        for (ExpressionItem child : expr.getChildren()) {
            if (child instanceof ExpressionTerm) {
                ExpressionTerm term = (ExpressionTerm) child;

                if (MetaFields.PIPELINE.getName().equals(term.getField())) {
                    return true;
                }
            } else if (child instanceof ExpressionOperator) {
                if (containsPipelineCondition((ExpressionOperator)child)){
                    return true;
                }
            } else {
                //Don't know what this is!
                LOGGER.warn("Unknown ExpressionItem type " + child.getClass().getName() + " unable to optimise meta query");
                //Allow search to succeed without optimisation
                return true;
            }
        }
        return false;
    }


    private Condition getFilterCriteriaCondition(final FindDataRetentionImpactCriteria criteria) {
        final Condition filterCondition;
        if (criteria != null
                && criteria.getExpression() != null
                && !criteria.getExpression().equals(new ExpressionOperator.Builder(Op.AND).build())) {

            filterCondition = expressionMapper.apply(criteria.getExpression());
        } else {
            filterCondition = DSL.noCondition();
        }
        return filterCondition;
    }

    @Override
    public List<DataRetentionDeleteSummary> getRetentionDeletionSummary(
            final DataRetentionRules rules,
            final FindDataRetentionImpactCriteria criteria) {

        final List<DataRetentionDeleteSummary> result;

        final List<DataRetentionRule> activeRules = Optional.ofNullable(rules)
                .flatMap(rules2 -> Optional.ofNullable(rules2.getRules()))
                .map(allRules -> allRules.stream()
                        .filter(DataRetentionRule::isEnabled)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());

        if (!activeRules.isEmpty()) {
            CaseConditionStep<Integer> ruleNoCaseConditionStep = null;
            final Map<Integer, DataRetentionRule> numberToRuleMap = new HashMap<>();
            // Order is critical here as we are building a case statement
            // Highest priority rules first, i.e. largest rule number
            final Map<Integer, Condition> ruleNoToConditionMap = activeRules.stream()
                    .collect(Collectors.toMap(
                            DataRetentionRule::getRuleNumber,
                            rule -> expressionMapper.apply(rule.getExpression())));

            final List<Condition> orConditions = activeRules.stream()
                    .map(rule -> ruleNoToConditionMap.get(rule.getRuleNumber()))
                    .collect(Collectors.toList());

            for (DataRetentionRule rule : activeRules) {
                numberToRuleMap.put(rule.getRuleNumber(), rule);
                final Condition ruleCondition = ruleNoToConditionMap.get(rule.getRuleNumber());

                // We are not interested in counts for stuff being kept forever as they will never
                // be deleted.
                final Integer caseResult = rule.isForever()
                        ? null
                        : rule.getRuleNumber();

                if (ruleNoCaseConditionStep == null) {
                    ruleNoCaseConditionStep = DSL.when(ruleCondition, caseResult);
                } else {
                    ruleNoCaseConditionStep.when(ruleCondition, caseResult);
                }
            }
            // If none of the rules matches then we don't to delete so return false
            final Field<Integer> ruleNoCaseField = ruleNoCaseConditionStep.otherwise((Field<Integer>) null);

            final byte statusIdDeleted = MetaStatusId.getPrimitiveValue(Status.DELETED);

            return JooqUtil.contextResult(
                    metaDbConnProvider, context -> {

                        final String ruleNoFieldName = "rule_no";
                        final String feedNameFieldName = "feed_name";
                        final String typeNameFieldName = "type_name";

                        // Get all meta records that are impacted by a rule and for each determine
                        // which rule wins and get its rule number, along with feed and type
                        // The OR condition is here to try and help the DB use indexes.
                        // TODO Should maybe move the ruleNoCaseField into a sub select so we don't need
                        //   to compute it for the select and the where
                        final var detailTable = context
                                .select(
                                        metaFeed.NAME.as(feedNameFieldName),
                                        metaType.NAME.as(typeNameFieldName),
                                        ruleNoCaseField.as(ruleNoFieldName))
                                .from(meta)
                                .leftJoin(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID))
                                .leftJoin(metaType).on(meta.TYPE_ID.eq(metaType.ID))
                                .where(meta.STATUS.notEqual(statusIdDeleted))
                                .and(ruleNoCaseField.isNotNull()) // only want data that WILL be deleted
                                .and(DSL.or(orConditions)) // Here to help use indexes
                                .and(getFilterCriteriaCondition(criteria)) // UI filtering
                                .asTable("detail");

                        // Now get counts grouped by feed, type and rule
                        return context
                                .select(
                                        detailTable.field(feedNameFieldName),
                                        detailTable.field(typeNameFieldName),
                                        detailTable.field(ruleNoFieldName),
                                        DSL.count())
                                .from(detailTable)
                                .where(detailTable.field(ruleNoFieldName).isNotNull()) // ignore rows not impacted by a rule
                                .groupBy(
                                        detailTable.field(ruleNoFieldName),
                                        detailTable.field(feedNameFieldName),
                                        detailTable.field(typeNameFieldName))
                                .fetch()
                                .map(record -> {
                                    int ruleNo = (int) record.get(ruleNoFieldName);

                                    return new DataRetentionDeleteSummary(
                                            (String) record.get(feedNameFieldName),
                                            (String) record.get(typeNameFieldName),
                                            ruleNo,
                                            numberToRuleMap.get(ruleNo).getName(),
                                            (int) record.get(DSL.count().getName()));
                                });
                    });
        } else {
            // No rules so no point running a query
            result = Collections.emptyList();
        }
        return result;
    }

    @Override
    public int logicalDelete(final List<DataRetentionRuleAction> ruleActions,
                             final TimePeriod period) {

        LOGGER.debug(() ->
                LogUtil.message("logicalDelete called for {} and actions\n{}",
                        period, ruleActions.stream()
                                .map(ruleAction -> ruleAction.getRule().getRuleNumber() + " " +
                                        ruleAction.getRule().getExpression() + " " +
                                        ruleAction.getOutcome().name())
                                .collect(Collectors.joining("\n"))));

        final AtomicInteger totalUpdateCount = new AtomicInteger(0);
        if (ruleActions != null && !ruleActions.isEmpty()) {
            final byte statusIdDeleted = MetaStatusId.getPrimitiveValue(Status.DELETED);

            final List<Condition> baseConditions = createRetentionDeleteConditions(ruleActions);

            final List<Condition> conditions = new ArrayList<>(baseConditions);

            // Time bound the data we are testing the rules over
            conditions.add(meta.CREATE_TIME.greaterOrEqual(period.getFrom().toEpochMilli()));
            conditions.add(meta.CREATE_TIME.lessThan(period.getTo().toEpochMilli()));

            int lastUpdateCount;
            AtomicInteger iteration = new AtomicInteger(1);
            Duration totalSelectDuration = Duration.ZERO;
            Duration totalUpdateDuration = Duration.ZERO;
            Instant startTime;
            Instant startTimeInc = period.getFrom();
            final int batchSize = dataRetentionConfig.getDeleteBatchSize();

            // The aim here is to ensure we lock up the meta table for as short a time
            // as possible. As we are updating by non-unique indexes it is likely we will
            // get next-key locks which may impact other writes.
            // At the cost of the whole retention delete taking longer we split the updates
            // up into smaller chunks to reduce the time meta is locked for. To do the chunking
            // we query the table with the same conditions as the update to find a batch
            // of n records and capture the min/max create_time for that batch.
            // Then we update over that sub-period so we can just rang-scan the create_time or
            // feed_id|create_time index.
            do {
                // Get a sub period of period

                startTime = Instant.now();
                final Optional<TimePeriod> optSubPeriod = getTimeSlice(startTimeInc, batchSize, conditions);
                totalSelectDuration = totalSelectDuration.plus(Duration.between(startTime, Instant.now().plusMillis(1)));

                if (optSubPeriod.isEmpty()) {
                    LOGGER.debug("No time slice found");
                    break;
                }
                TimePeriod subPeriod = optSubPeriod.get();
                LOGGER.debug("Iteration {}, using sub-period {}", iteration, subPeriod);

                startTime = Instant.now();
                lastUpdateCount = LOGGER.logDurationIfDebugEnabled(
                        () -> JooqUtil.contextResult(metaDbConnProvider, context -> context
                                .update(meta)
                                .set(meta.STATUS, statusIdDeleted)
                                .set(meta.STATUS_TIME, Instant.now().toEpochMilli())
                                .where(conditions)
                                .and(meta.CREATE_TIME.greaterOrEqual(subPeriod.getFrom().toEpochMilli()))
                                .and(meta.CREATE_TIME.lessThan(subPeriod.getTo().toEpochMilli()))
                                .execute()),
                        cnt -> LogUtil.message("Logically deleted {} meta records with approx. limit of {}. Iteration {}",
                                cnt, batchSize, iteration));
                totalUpdateDuration = totalUpdateDuration.plus(Duration.between(startTime, Instant.now().plusMillis(1)));

                // Advance the start time
                startTimeInc = subPeriod.getFrom();
                totalUpdateCount.addAndGet(lastUpdateCount);
                iteration.incrementAndGet();
                LOGGER.debug("Logically deleted {} meta rows (total so far {})", lastUpdateCount, totalUpdateCount);

            } while (lastUpdateCount != 0 && !Thread.currentThread().isInterrupted());

            LOGGER.info("Logically deleted {} meta rows, batchSize {}, iterations {}, select avg {}, update avg {}",
                    totalUpdateCount.get(),
                    batchSize,
                    iteration.decrementAndGet(),
                    iteration.get() != 0 ? totalSelectDuration.dividedBy(iteration.get()) : Duration.ZERO,
                    iteration.get() != 0 ? totalUpdateDuration.dividedBy(iteration.get()) : Duration.ZERO);

            if (Thread.currentThread().isInterrupted()) {
                LOGGER.error("Thread interrupted");
            }
        } else {
            LOGGER.debug("Empty ruleActions, nothing to delete");
            totalUpdateCount.set(0);
        }
        return totalUpdateCount.get();
    }

    private Optional<TimePeriod> getTimeSlice(final Instant startTimeInc,
                                              final int batchSize,
                                              final List<Condition> conditions) {
        LOGGER.debug("getTimeSlice({}, {}, {})", startTimeInc, batchSize, conditions);

        // For a given set of conditions (that may already contain some create_time bounds
        // get the create_time range for a batch n records
        final Optional<TimePeriod> timePeriod = JooqUtil.contextResult(metaDbConnProvider, context -> {

            final Table<?> orderedFullSet = context
                    .select(meta.CREATE_TIME)
                    .from(meta)
                    .where(conditions)
                    .and(meta.CREATE_TIME.greaterOrEqual(startTimeInc.toEpochMilli()))
                    .orderBy(meta.CREATE_TIME)
                    .asTable("orderedFullSet");

            final Table<?> limitedSet = context
                    .select(orderedFullSet.fields())
                    .from(orderedFullSet)
                    .limit(batchSize)
                    .asTable("limitedSet");

            final String createTimeCol = meta.CREATE_TIME.getName();
            final String minCreateTimeCol = "min_create_time";
            final String maxCreateTimeCol = "max_create_time";

            return LOGGER.logDurationIfDebugEnabled(() -> context
                            .select(
                                    DSL.min(limitedSet.field(createTimeCol)).as(minCreateTimeCol),
                                    DSL.max(limitedSet.field(createTimeCol)).as(maxCreateTimeCol))
                            .from(limitedSet)
                            .fetchOne()
                            .map(record -> {
                                Object min = record.get(minCreateTimeCol);
                                Object max = record.get(maxCreateTimeCol);

                                if (min == null || max == null) {
                                    return Optional.empty();
                                } else {
                                    return Optional.of(
                                            TimePeriod.between((long) min, (long) max + 1)); // Add one to make it exclusive
                                }
                            }),
                    () -> LogUtil.message("Selecting time slice starting at {}, with batch size {}",
                            startTimeInc, batchSize));
        });
        LOGGER.debug("Returning period {}", timePeriod);

        // NOTE The number of records in the slice may differ from the desired batch size if you have
        // multiple records on the boundary with the same create_time (unlikely with milli precision).

        return timePeriod;
    }

    private List<Condition> createRetentionDeleteConditions(final List<DataRetentionRuleAction> ruleActions) {
        Objects.requireNonNull(ruleActions);
        final byte statusIdUnlocked = MetaStatusId.getPrimitiveValue(Status.UNLOCKED);

        // What we are building is roughly:
        // WHERE (CASE
        //   WHEN <rule 3 condition is true> THEN <outcome => true|false>
        //   WHEN <rule 2 condition is true> THEN <outcome => true|false>
        //   WHEN <rule 1 condition is true> THEN <outcome => true|false>
        //   ELSE false
        //   ) = true
        // I.e. see if a rule matches the data in priority order
        // Needs to be a CASE so we can ensure the order of rule evaluation
        // and drop out when one matches. A matching rule may have an outcome
        // of delete (true) or retain (false)

        CaseConditionStep<Boolean> caseConditionStep = null;
        List<Condition> orConditions = new ArrayList<>();
        // Order is critical here as we are building a case statement
        // Highest priority rules first, i.e. largest rule number
        for (DataRetentionRuleAction ruleAction : ruleActions) {
            final Condition ruleCondition = expressionMapper.apply(ruleAction.getRule().getExpression());

            // TODO make this conditional based on config
            if (dataRetentionConfig.isUseQueryOptimisation()) {
                orConditions.add(ruleCondition);
            }

            // The rule will either result in true or false depending on if it needs
            // to delete or retain the data
            final boolean caseResult = ruleActionToBoolean(ruleAction);
            if (caseConditionStep == null) {
                caseConditionStep = DSL.when(ruleCondition, caseResult);
            } else {
                caseConditionStep.when(ruleCondition, caseResult);
            }
        }
        // If none of the rules matches then we don't to delete so return false
        final Field<Boolean> caseField = caseConditionStep.otherwise(Boolean.FALSE);

        List<Condition> conditions = new ArrayList<>();

        // Ensure we only 'delete' unlocked records, also ensures we don't touch
        // records we have already deleted in a previous pass
        conditions.add(meta.STATUS.eq(statusIdUnlocked));

        // Add our rule conditions
        conditions.add(caseField.eq(true));

        // Now add all the rule conditions as an OR block
        // This is to improve the performance of the query as the OR can make use of other indexes,
        // e.g. range scanning the feedid+createTime index to reduce the number of rows scanned.
        // We are still reliant on the case statement block to get the right outcome for the rules.
        // It is possible this approach may slow things down as it makes the SQL more complex.
        if (dataRetentionConfig.isUseQueryOptimisation()) {
            conditions.add(DSL.or(orConditions));
        }

        LOGGER.debug("conditions {}", conditions);
        return conditions;
    }

    private boolean ruleActionToBoolean(final DataRetentionRuleAction action) {
        switch (action.getOutcome()) {
            case DELETE:
                return true;
            case RETAIN:
                return false;
            default:
                throw new RuntimeException("Unexpected type " + action.getOutcome().name());
        }
    }

    @Override
    public int count(final FindMetaCriteria criteria) {

        final Collection<Condition> conditions = createCondition(criteria);
        final Set<Integer> usedValKeys = identifyExtendedAttributesFields(criteria.getExpression(), new HashSet<>());

        final Object result = JooqUtil.contextResult(metaDbConnProvider, context ->
                metaExpressionMapper.addJoins(
                        context
                                .selectCount()
                                .from(meta)
                                .join(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID))
                                .join(metaType).on(meta.TYPE_ID.eq(metaType.ID))
                                .leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID)),
                        meta.ID,
                        usedValKeys)
                        .where(conditions)
                        .fetchOne().get(0));

        return (Integer) result;
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
    public ResultPage<Meta> find(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);

        int offset = JooqUtil.getOffset(pageRequest);
        int numberOfRows = JooqUtil.getLimit(pageRequest, true, FIND_RECORD_LIMIT);

        final Set<Integer> extendedAttributeIds = identifyExtendedAttributesFields(criteria.getExpression(), new HashSet<>());

        final List<Meta> list = find(conditions, orderFields, offset, numberOfRows, extendedAttributeIds);
        if (list.size() >= FIND_RECORD_LIMIT) {
            LOGGER.warn("Hit max record limit of '" + FIND_RECORD_LIMIT + "' when finding meta records");
        }

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private final Collection<String> extendedFieldNames =
            MetaFields.getExtendedFields().stream().map(AbstractField::getName).collect(Collectors.toList());

    private Set<Integer> identifyExtendedAttributesFields(final ExpressionOperator expr, final Set<Integer> identified) {

        if (expr == null || expr.getChildren() == null)
            return identified;
        for (ExpressionItem child : expr.getChildren()) {
            if (child instanceof ExpressionTerm) {
                ExpressionTerm term = (ExpressionTerm) child;

                if (extendedFieldNames.contains(term.getField())) {
                    Optional<Integer> key = metaKeyDao.getIdForName(term.getField());
                    key.ifPresent(identified::add);
                }
            } else if (child instanceof ExpressionOperator) {
                identified.addAll(identifyExtendedAttributesFields((ExpressionOperator) child, identified));
            } else {
                //Don't know what this is!
                LOGGER.warn("Unknown ExpressionItem type " + child.getClass().getName() + " unable to optimise meta query");
                //Allow search to succeed without optimisation
                return IntStream.range(metaKeyDao.getMinId(), metaKeyDao.getMaxId()).boxed().collect(Collectors.toSet());
            }
        }
        return identified;
    }


    private List<Meta> find(final Collection<Condition> conditions,
                            final Collection<OrderField<?>> orderFields,
                            final int offset,
                            final int numberOfRows,
                            final Set<Integer> usedValKeys) {

        return JooqUtil.contextResult(metaDbConnProvider, context ->
                metaExpressionMapper.addJoins(context
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
                                .leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID)),
                        meta.ID,
                        usedValKeys)
                        .where(conditions)
                        .orderBy(orderFields)
                        .limit(offset, numberOfRows)
                        .fetch()
                        .map(RECORD_TO_META_MAPPER::apply));
    }


    @Override
    public ResultPage<Meta> findReprocess(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);

        int offset = JooqUtil.getOffset(pageRequest);
        int numberOfRows = JooqUtil.getLimit(pageRequest, true, FIND_RECORD_LIMIT);

        final Set<Integer> extendedAttributeIds = identifyExtendedAttributesFields(criteria.getExpression(), new HashSet<>());

        final List<Meta> list = findReprocess(conditions, offset, numberOfRows, extendedAttributeIds);
        if (list.size() >= FIND_RECORD_LIMIT) {
            LOGGER.warn("Hit max record limit of '" + FIND_RECORD_LIMIT + "' when finding meta records");
        }

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private List<Meta> findReprocess(final Collection<Condition> conditions,
                                     final int offset,
                                     final int numberOfRows,
                                     final Set<Integer> usedValKeys) {
        return JooqUtil.contextResult(metaDbConnProvider, context ->
                metaExpressionMapper.addJoins(
                        (context
                                .select(
                                        parent.ID,
                                        parentFeed.NAME,
                                        parentType.NAME,
                                        parentProcessor.PROCESSOR_UUID,
                                        parentProcessor.PIPELINE_UUID,
                                        parent.PARENT_ID,
                                        parent.STATUS,
                                        parent.STATUS_TIME,
                                        parent.CREATE_TIME,
                                        parent.EFFECTIVE_TIME
                                )
                                .from(meta)
                                .leftOuterJoin(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID))
                                .leftOuterJoin(metaType).on(meta.TYPE_ID.eq(metaType.ID))
                                .leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID))
                                .leftOuterJoin(parent).on(meta.PARENT_ID.eq(parent.ID))
                                .leftOuterJoin(parentFeed).on(parent.FEED_ID.eq(parentFeed.ID))
                                .leftOuterJoin(parentType).on(parent.TYPE_ID.eq(parentType.ID))
                                .leftOuterJoin(parentProcessor).on(parent.PROCESSOR_ID.eq(parentProcessor.ID))),
                        meta.ID,
                        usedValKeys)
                        .where(conditions)
                        .and(parent.ID.isNotNull())
                        .groupBy(parent.ID)
                        .orderBy(parent.ID)
                        .limit(offset, numberOfRows)
                        .fetch()
                        .map(RECORD_TO_PARENT_META_MAPPER::apply));
    }

    @Override
    public SelectionSummary getSelectionSummary(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);
        final Set<Integer> usedValKeys = identifyExtendedAttributesFields(criteria.getExpression(), new HashSet<>());

        int offset = JooqUtil.getOffset(pageRequest);
        int numberOfRows = JooqUtil.getLimit(pageRequest, false);

        return getSelectionSummary(conditions, offset, numberOfRows, usedValKeys);
    }

    private SelectionSummary getSelectionSummary(final Collection<Condition> conditions,
                                                 final int offset,
                                                 final int numberOfRows,
                                                 final Set<Integer> usedValKeys) {
        return JooqUtil.contextResult(metaDbConnProvider, context ->
                metaExpressionMapper.addJoins(
                        context
                                .select(
                                        DSL.count(),
                                        DSL.countDistinct(metaFeed.NAME),
                                        DSL.countDistinct(metaType.NAME),
                                        DSL.countDistinct(metaProcessor.PROCESSOR_UUID),
                                        DSL.countDistinct(metaProcessor.PIPELINE_UUID),
                                        DSL.countDistinct(meta.STATUS),
                                        DSL.min(meta.CREATE_TIME),
                                        DSL.max(meta.CREATE_TIME)
                                )
                                .from(meta)
                                .join(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID))
                                .join(metaType).on(meta.TYPE_ID.eq(metaType.ID))
                                .leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID)),
                        meta.ID,
                        usedValKeys)
                        .where(conditions)
                        .limit(offset, numberOfRows)
                        .fetchOptional()
                        .map(record -> new SelectionSummary(
                                (Integer) record.get(0),
                                (Integer) record.get(1),
                                (Integer) record.get(2),
                                (Integer) record.get(3),
                                (Integer) record.get(4),
                                (Integer) record.get(5),
                                new Range<>((Long) record.get(6), (Long) record.get(7))))
                        .orElse(null));
    }

    @Override
    public SelectionSummary getReprocessSelectionSummary(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);

        final Set<Integer> usedValKeys = identifyExtendedAttributesFields(criteria.getExpression(), new HashSet<>());

        int offset = JooqUtil.getOffset(pageRequest);
        int numberOfRows = JooqUtil.getLimit(pageRequest, false);

        return getReprocessSelectionSummary(conditions, offset, numberOfRows, usedValKeys);
    }

    private SelectionSummary getReprocessSelectionSummary(final Collection<Condition> conditions,
                                                          final int offset,
                                                          final int numberOfRows,
                                                          final Set<Integer> usedValKeys) {
        return JooqUtil.contextResult(metaDbConnProvider, context ->
                metaExpressionMapper.addJoins(context
                                .select(
                                        DSL.countDistinct(parent.ID),
                                        DSL.countDistinct(parent.FEED_ID),
                                        DSL.countDistinct(parent.TYPE_ID),
                                        DSL.countDistinct(meta.PROCESSOR_ID),
                                        DSL.countDistinct(metaProcessor.PIPELINE_UUID),
                                        DSL.countDistinct(parent.STATUS),
                                        DSL.min(parent.CREATE_TIME),
                                        DSL.max(parent.CREATE_TIME)
                                )
                                .from(meta)
                                .join(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID))
                                .join(metaType).on(meta.TYPE_ID.eq(metaType.ID))
                                .leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID))
                                .leftOuterJoin(parent).on(meta.PARENT_ID.eq(parent.ID)),
                        meta.ID,
                        usedValKeys)
                        .where(conditions)
                        .and(parent.ID.isNotNull())
                        .and(parent.STATUS.eq(MetaStatusId.getPrimitiveValue(Status.UNLOCKED)))
                        .limit(offset, numberOfRows)
                        .fetchOptional()
                        .map(record -> new SelectionSummary(
                                (Integer) record.get(0),
                                (Integer) record.get(1),
                                (Integer) record.get(2),
                                (Integer) record.get(3),
                                (Integer) record.get(4),
                                (Integer) record.get(5),
                                new Range<>((Long) record.get(6), (Long) record.get(7))))
                        .orElse(null));
    }


    @Override
    public int delete(final List<Long> metaIdList) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .deleteFrom(meta)
                .where(meta.ID.in(metaIdList))
                .execute());
    }

    @Override
    public Optional<Long> getMaxId(final FindMetaCriteria criteria) {
        final Condition condition = expressionMapper.apply(criteria.getExpression());

        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(DSL.max(meta.ID))
                .from(meta)
                .where(condition)
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
        feedIdCache.clear();
        typeIdCache.clear();
    }

    private Collection<Condition> createCondition(final FindMetaCriteria criteria) {
        return createCondition(criteria.getExpression());
    }

    private Collection<Condition> createCondition(final ExpressionOperator expression) {
        Condition criteriaCondition = expressionMapper.apply(expression);

        return Collections.singletonList(criteriaCondition);
    }

    private Collection<OrderField<?>> createOrderFields(final ExpressionCriteria criteria) {
        if (criteria.getSortList() == null || criteria.getSortList().size() == 0) {
            return Collections.singleton(meta.ID);
        }

        return criteria.getSortList().stream().map(sort -> {
            Field<?> field;
            if (MetaFields.ID.getName().equals(sort.getId())) {
                field = meta.ID;
            } else if (MetaFields.CREATE_TIME.getName().equals(sort.getId())) {
                field = meta.CREATE_TIME;
            } else if (MetaFields.FEED_NAME.getName().equals(sort.getId())) {
                field = metaFeed.NAME;
            } else if (MetaFields.TYPE_NAME.getName().equals(sort.getId())) {
                field = metaType.NAME;
            } else if (MetaFields.PARENT_ID.getName().equals(sort.getId())) {
                field = meta.PARENT_ID;
            } else if (MetaFields.PARENT_CREATE_TIME.getName().equals(sort.getId())) {
                field = parent.CREATE_TIME;
            } else {
                field = meta.ID;
            }

            OrderField<?> orderField = field;
            if (sort.isDesc()) {
                orderField = field.desc();
            }

            return orderField;
        }).collect(Collectors.toList());
    }

    @Override
    public List<String> getProcessorUuidList(final FindMetaCriteria criteria) {
        final Collection<Condition> conditions = createCondition(criteria);
        return getProcessorUuidList(conditions);
    }

    private List<String> getProcessorUuidList(final Collection<Condition> conditions) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(metaProcessor.PROCESSOR_UUID)
                .from(meta)
                .join(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID))
                .join(metaType).on(meta.TYPE_ID.eq(metaType.ID))
                .leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID))
                .where(conditions)
                .groupBy(metaProcessor.PROCESSOR_UUID)
                .fetch()
                .map(Record1::value1));
    }
}
