package stroom.meta.impl.db;

import stroom.collection.api.CollectionService;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.data.retention.api.DataRetentionConfig;
import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.shared.DataRetentionRule;
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
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.util.collections.BatchingIterator;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Sort;
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
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
import java.util.concurrent.ConcurrentHashMap;
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
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaDaoImpl.class);

    // This is currently only used for testing so no need to put it in config,
    // unless it gets used in real code.
    private static final int MAX_VALUES_PER_INSERT = 500;

    private static final int FIND_RECORD_LIMIT = 1000000;

    private static final stroom.meta.impl.db.jooq.tables.Meta meta = META.as("m");
    private static final MetaFeed metaFeed = META_FEED.as("f");
    private static final MetaType metaType = META_TYPE.as("t");
    private static final MetaProcessor metaProcessor = META_PROCESSOR.as("p");
    private static final MetaVal metaVal = META_VAL.as("v");

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
                final CollectionService collectionService) {
        this.metaDbConnProvider = metaDbConnProvider;
        this.feedDao = feedDao;
        this.metaTypeDao = metaTypeDao;
        this.metaProcessorDao = metaProcessorDao;
        this.metaKeyDao = metaKeyDao;
        this.dataRetentionConfig = dataRetentionConfig;

        // Standard fields.
        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(MetaFields.ID, meta.ID, Long::valueOf);
        expressionMapper.map(MetaFields.PROCESSOR_ID, meta.PROCESSOR_ID, Integer::valueOf);
        expressionMapper.multiMap(MetaFields.FEED, meta.FEED_ID, this::getFeedIds, true);
        expressionMapper.multiMap(MetaFields.FEED_NAME, meta.FEED_ID, this::getFeedIds);
        expressionMapper.multiMap(MetaFields.TYPE_NAME, meta.TYPE_ID, this::getTypeIds);
        expressionMapper.map(MetaFields.PIPELINE, metaProcessor.PIPELINE_UUID, value -> value);
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

        // Parent fields.
        expressionMapper.map(MetaFields.PARENT_ID, meta.PARENT_ID, Long::valueOf);
        expressionMapper.map(MetaFields.PARENT_STATUS, parent.STATUS, value -> MetaStatusId.getPrimitiveValue(Status.valueOf(value.toUpperCase())));
        expressionMapper.map(MetaFields.PARENT_CREATE_TIME, parent.CREATE_TIME, DateUtil::parseNormalDateTimeString);
        expressionMapper.multiMap(MetaFields.PARENT_FEED, parent.FEED_ID, this::getFeedIds);

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
        JooqUtil.context(metaDbConnProvider, context -> {
            context.batch(
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
                    .execute();
        });
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

            updateCount = JooqUtil.contextResult(metaDbConnProvider, context -> context
                    .update(meta)
                    .set(meta.STATUS, newStatusId)
                    .set(meta.STATUS_TIME, statusTime)
                    .where(conditions)
                    .execute());
        }
        return updateCount;
    }

    public void getRetentionDeletionSummary(final List<DataRetentionRule> rules) {

        // What we are building is roughly:
        // SELECT
        //   v.feed_name, v.rule_no, v.ms_til_delete, count(*)
        // FROM (
        //   SELECT
        //     feed_name,
        //     CASE
        //       WHEN <rule 1 condition is true> THEN m.create_time - <rule min create time ms>
        //       WHEN <rule 2 condition is true> THEN m.create_time - <rule min create time ms>
        //       ELSE null
        //     END as ms_til_delete,
        //     CASE
        //       WHEN <rule 1 condition is true> THEN <rule no>
        //       WHEN <rule 2 condition is true> THEN <rule no>
        //       ELSE 0
        //     END as rule_no
        //   FROM meta m
        //   WHERE <big OR block of all rules>
        //   AND status IN (0,1)
        // ) v
        // WHERE v.ms_til_delete is not null
        // GROUP BY v.feed_name, v.rule_no, v.ms_til_delete

        final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        List<Condition> conditions = new ArrayList<>();

        if (rules != null && !rules.isEmpty()) {
            CaseConditionStep<Integer> ruleNoCaseConditionStep = null;
            CaseConditionStep<Serializable> daysTilDeleteCaseConditionStep = null;
            List<Condition> orConditions = new ArrayList<>();
            // Order is critical here as we are building a case statement
            // Highest priority rules first, i.e. largest rule number
            for (DataRetentionRule rule : rules) {
                if (rule.isEnabled()) {
                    final Condition ruleCondition = expressionMapper.apply(rule.getExpression());

                    if (dataRetentionConfig.isUseQueryOptimisation()) {
                        orConditions.add(ruleCondition);
                    }

                    // The rule will either result in true or false depending on if it needs
                    // to delete or retain the data

                    final Integer ruleNoCaseResult = rule.getRuleNumber();

                    if (ruleNoCaseConditionStep == null) {
                        ruleNoCaseConditionStep = DSL.when(ruleCondition, ruleNoCaseResult);
                    } else {
                        ruleNoCaseConditionStep.when(ruleCondition, ruleNoCaseResult);
                    }

                    final Field<Serializable> daysTilDeleteCaseResult;
                    if (rule.isForever()) {
                        daysTilDeleteCaseResult = null;
                    } else {
                        long minCreateTimeMs = ruleToMinCreateTime(now, rule).toEpochMilli();
                        daysTilDeleteCaseResult = DSL.greatest(0, meta.CREATE_TIME.minus(minCreateTimeMs));
                    }

                    if (daysTilDeleteCaseConditionStep == null) {
                        daysTilDeleteCaseConditionStep = DSL.when(ruleCondition, daysTilDeleteCaseResult);
                    } else {
                        daysTilDeleteCaseConditionStep.when(ruleCondition, daysTilDeleteCaseResult);
                    }
                }
            }
            // If none of the rules matches then we don't to delete so return false
            final Field<Integer> ruleNoCaseField = ruleNoCaseConditionStep.otherwise(0);
            final Field<Serializable> daysTilDeleteCaseField = daysTilDeleteCaseConditionStep.otherwise((Field<Serializable>) null);


            final byte statusIdUnlocked = MetaStatusId.getPrimitiveValue(Status.UNLOCKED);
            conditions.add(meta.STATUS.eq(statusIdUnlocked));

            // Now add all the rule conditions as an OR block
            // This is to improve the performance of the query as the OR can make use of other indexes,
            // e.g. range scanning the feedid+createTime index to reduce the number of rows scanned.
            // We are still reliant on the case statement block to get the right outcome for the rules.
            // It is possible this approach may slow things down as it makes the SQL more complex.
            if (dataRetentionConfig.isUseQueryOptimisation()) {
                conditions.add(DSL.or(orConditions));
            }

        } else {
            // No rules so no point running a query
            // TODO return an empty collection
        }
    }

    private static Instant ruleToMinCreateTime(final Instant now, final DataRetentionRule rule) {
        if (rule.isForever()) {
            return Instant.EPOCH;
        }

        LocalDateTime age = null;
        final LocalDateTime time = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        switch (rule.getTimeUnit()) {
            case MINUTES:
                age = time.minusMinutes(rule.getAge());
                break;
            case HOURS:
                age = time.minusHours(rule.getAge());
                break;
            case DAYS:
                age = time.minusDays(rule.getAge());
                break;
            case WEEKS:
                age = time.minusWeeks(rule.getAge());
                break;
            case MONTHS:
                age = time.minusMonths(rule.getAge());
                break;
            case YEARS:
                age = time.minusYears(rule.getAge());
                break;
        }

        if (age == null) {
            return Instant.EPOCH;
        }

        return age.toInstant(ZoneOffset.UTC);
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

        int totalUpdateCount = 0;
        if (ruleActions != null && !ruleActions.isEmpty()) {
            final byte statusIdDeleted = MetaStatusId.getPrimitiveValue(Status.DELETED);

            final List<Condition> baseConditions = createRetentionDeleteConditions(ruleActions);

            // Looping like this is not ideal as we are then relying on filtering out
            // all the rows we have already touched, based on their status col. An index
            // of (status, feed_id, create_time) may help here. There is a risk that
            // each pass takes longer and longer as it has to scan over old data.
            //
            // We could INSERT...SELECT into a temp table but that still has locking
            // implications on meta.
            int lastUpdateCount = -1;
            final int batchSize = dataRetentionConfig.getDeleteBatchSize();
            do {
                final List<Condition> conditions = new ArrayList<>(baseConditions);

                // Time bound the data we are testing the rules over
                conditions.add(meta.CREATE_TIME.greaterOrEqual(period.getFrom().toEpochMilli()));
                conditions.add(meta.CREATE_TIME.lessThan(period.getTo().toEpochMilli()));

                lastUpdateCount = LOGGER.logDurationIfInfoEnabled(
                        () -> JooqUtil.contextResult(metaDbConnProvider, context -> context
                                .update(meta)
                                .set(meta.STATUS, statusIdDeleted)
                                .set(meta.STATUS_TIME, Instant.now().toEpochMilli())
                                .where(conditions)
                                .limit(batchSize)
                                .execute()),
                        cnt -> LogUtil.message("Logically deleted {} meta records with limit {}", cnt, batchSize));

                totalUpdateCount += lastUpdateCount;

                LOGGER.debug("Logically deleted {} meta rows (total so far {})", lastUpdateCount, totalUpdateCount);

            } while (lastUpdateCount != 0 && !Thread.currentThread().isInterrupted());

            if (Thread.currentThread().isInterrupted()) {
                LOGGER.error("Thread interrupted");
            }
        } else {
            LOGGER.debug("Empty ruleActions, nothing to delete");
            totalUpdateCount = 0;
        }
        return totalUpdateCount;
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

    private Optional<SelectConditionStep<Record1<Long>>> getMetaCondition(final ExpressionOperator expression) {
        if (expression == null) {
            return Optional.empty();
        }

        final Condition condition = metaExpressionMapper.apply(expression);
        if (DSL.trueCondition().equals(condition)) {
            return Optional.empty();
        }

        return Optional.of(selectDistinct(metaVal.META_ID)
                .from(metaVal)
                .where(condition));
    }

    @Override
    public int count(final FindMetaCriteria criteria) {
        final Collection<Condition> conditions = createCondition(criteria);

        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .selectCount()
                .from(meta)
                .join(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID))
                .join(metaType).on(meta.TYPE_ID.eq(metaType.ID))
                .leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID))
                .where(conditions)
                .fetchOne()
                .value1());
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

        final List<Meta> list = find(conditions, orderFields, offset, numberOfRows);
        if (list.size() >= FIND_RECORD_LIMIT) {
            LOGGER.warn("Hit max record limit of '" + FIND_RECORD_LIMIT + "' when finding meta records");
        }

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
    public ResultPage<Meta> findReprocess(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);

        int offset = JooqUtil.getOffset(pageRequest);
        int numberOfRows = JooqUtil.getLimit(pageRequest, true, FIND_RECORD_LIMIT);

        final List<Meta> list = findReprocess(conditions, orderFields, offset, numberOfRows);
        if (list.size() >= FIND_RECORD_LIMIT) {
            LOGGER.warn("Hit max record limit of '" + FIND_RECORD_LIMIT + "' when finding meta records");
        }

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private List<Meta> findReprocess(final Collection<Condition> conditions,
                                     final Collection<OrderField<?>> orderFields,
                                     final int offset,
                                     final int numberOfRows) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
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
                .leftOuterJoin(parentProcessor).on(parent.PROCESSOR_ID.eq(parentProcessor.ID))
                .where(conditions)
                .and(parent.ID.isNotNull())
                .groupBy(parent.ID)
                .orderBy(orderFields)
                .limit(offset, numberOfRows)
                .fetch()
                .map(RECORD_TO_PARENT_META_MAPPER::apply));
    }

    @Override
    public SelectionSummary getSelectionSummary(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);

        int offset = JooqUtil.getOffset(pageRequest);
        int numberOfRows = JooqUtil.getLimit(pageRequest, false);

        return getSelectionSummary(conditions, offset, numberOfRows);
    }

    private SelectionSummary getSelectionSummary(final Collection<Condition> conditions,
                                                 final int offset,
                                                 final int numberOfRows) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
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
                .leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID))
                .where(conditions)
                .limit(offset, numberOfRows)
                .fetchOptional()
                .map(record -> new SelectionSummary(
                        record.value1(),
                        record.value2(),
                        record.value3(),
                        record.value4(),
                        record.value5(),
                        record.value6(),
                        new Range<>(record.value7(), record.value8())))
                .orElse(null));
    }

    @Override
    public SelectionSummary getReprocessSelectionSummary(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);

        int offset = JooqUtil.getOffset(pageRequest);
        int numberOfRows = JooqUtil.getLimit(pageRequest, false);

        return getReprocessSelectionSummary(conditions, offset, numberOfRows);
    }

    private SelectionSummary getReprocessSelectionSummary(final Collection<Condition> conditions,
                                                          final int offset,
                                                          final int numberOfRows) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(
                        DSL.countDistinct(parent.ID),
                        DSL.countDistinct(parent.FEED_ID),
                        DSL.countDistinct(parent.TYPE_ID),
                        DSL.countDistinct(meta.PROCESSOR_ID),
                        DSL.countDistinct(meta.PROCESSOR_ID),
                        DSL.countDistinct(parent.STATUS),
                        DSL.min(parent.CREATE_TIME),
                        DSL.max(parent.CREATE_TIME)
                )
                .from(meta)
                .join(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID))
                .join(metaType).on(meta.TYPE_ID.eq(metaType.ID))
                .leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID))
                .leftOuterJoin(parent).on(meta.PARENT_ID.eq(parent.ID))
                .where(conditions)
                .and(parent.ID.isNotNull())
                .and(parent.STATUS.eq(MetaStatusId.getPrimitiveValue(Status.UNLOCKED)))
                .limit(offset, numberOfRows)
                .fetchOptional()
                .map(record -> new SelectionSummary(
                        record.value1(),
                        record.value2(),
                        record.value3(),
                        record.value4(),
                        record.value5(),
                        record.value6(),
                        new Range<>(record.value7(), record.value8())))
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
                .select(max(meta.ID))
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
        JooqUtil.truncateTable(metaDbConnProvider, META);
        feedIdCache.clear();
        typeIdCache.clear();
    }

    private Collection<Condition> createCondition(final FindMetaCriteria criteria) {
        return createCondition(criteria.getExpression());
    }

    private Collection<Condition> createCondition(final ExpressionOperator expression) {
        Condition criteriaCondition = expressionMapper.apply(expression);

//        // If we aren't being asked to match everything then add constraints to the expression.
//        if (idSet != null && (idSet.getMatchAll() == null || !idSet.getMatchAll())) {
//            condition = and(condition, meta.ID.in(idSet.getSet()));
//        }

        // Get additional selection criteria based on meta data attributes;
        final Optional<SelectConditionStep<Record1<Long>>> metaConditionStep = getMetaCondition(expression);
        List<Condition> conditions = new ArrayList<>();
        conditions.add(criteriaCondition);

        metaConditionStep.ifPresent(record1s ->
                conditions.add(meta.ID.in(record1s)));

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
            } else if (MetaFields.PARENT_ID.getName().equals(sort.getField())) {
                field = meta.PARENT_ID;
            } else if (MetaFields.PARENT_CREATE_TIME.getName().equals(sort.getField())) {
                field = parent.CREATE_TIME;
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
