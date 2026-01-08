/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.meta.impl.db;

import stroom.data.retention.api.DataRetentionConfig;
import stroom.data.retention.api.DataRetentionCreationTimeUtil;
import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.FindDataRetentionImpactCriteria;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.db.util.TermHandlerFactory;
import stroom.db.util.ValueMapper;
import stroom.db.util.ValueMapper.Mapper;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.EffectiveMetaDataCriteria;
import stroom.meta.api.EffectiveMetaSet;
import stroom.meta.api.EffectiveMetaSet.Builder;
import stroom.meta.api.MetaProperties;
import stroom.meta.impl.MetaDao;
import stroom.meta.impl.MetaServiceConfig;
import stroom.meta.impl.db.jooq.tables.MetaFeed;
import stroom.meta.impl.db.jooq.tables.MetaProcessor;
import stroom.meta.impl.db.jooq.tables.MetaType;
import stroom.meta.impl.db.jooq.tables.records.MetaRecord;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.SimpleMeta;
import stroom.meta.shared.SimpleMetaImpl;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.Period;
import stroom.util.collections.BatchingIterator;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.DurationTimer.TimedResult;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;
import stroom.util.time.TimePeriod;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.CaseConditionStep;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertOnDuplicateStep;
import org.jooq.InsertValuesStep10;
import org.jooq.Name;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record11;
import org.jooq.Record12;
import org.jooq.Record2;
import org.jooq.Record4;
import org.jooq.Result;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.SelectField;
import org.jooq.SelectHavingStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectLimitPercentStep;
import org.jooq.SelectOnConditionStep;
import org.jooq.SelectOrderByStep;
import org.jooq.SelectWithTiesAfterOffsetStep;
import org.jooq.Table;
import org.jooq.TableOnConditionStep;
import org.jooq.UpdateConditionStep;
import org.jooq.exception.DataTypeException;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.Instant;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static stroom.meta.impl.db.jooq.tables.Meta.META;
import static stroom.meta.impl.db.jooq.tables.MetaFeed.META_FEED;
import static stroom.meta.impl.db.jooq.tables.MetaProcessor.META_PROCESSOR;
import static stroom.meta.impl.db.jooq.tables.MetaType.META_TYPE;
import static stroom.meta.impl.db.jooq.tables.MetaVal.META_VAL;

@Singleton
public class MetaDaoImpl implements MetaDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaDaoImpl.class);
    private static final String GROUP_CONCAT_DELIMITER = "¬";
    private static final Pattern GROUP_CONCAT_DELIMITER_PATTERN = Pattern.compile(GROUP_CONCAT_DELIMITER);
    private static final Collection<String> EXTENDED_FIELD_NAMES = MetaFields.getExtendedFields()
            .stream()
            .map(QueryField::getFldName)
            .toList();

    // This is currently only used for testing so no need to put it in config,
    // unless it gets used in real code.
    private static final int MAX_VALUES_PER_INSERT = 500;

    private static final int FIND_RECORD_LIMIT = 1000000;

    static final stroom.meta.impl.db.jooq.tables.Meta META_M = META.as("m");
    static final MetaFeed META_FEED_F = META_FEED.as("f");
    static final MetaType META_TYPE_T = META_TYPE.as("t");
    static final MetaProcessor META_PROCESSOR_P = META_PROCESSOR.as("p");

    private static final stroom.meta.impl.db.jooq.tables.Meta parent = META.as("parent");
    private static final MetaFeed PARENT_FEED = META_FEED.as("parentFeed");
    private static final MetaType PARENT_TYPE = META_TYPE.as("parentType");
    private static final MetaProcessor PARENT_PROCESSOR = META_PROCESSOR.as("parentProcessor");

    private static final List<SelectField<?>> SIMPLE_META_SELECT_FIELDS = List.of(
            META_M.ID,
            META_TYPE_T.NAME,
            META_FEED_F.NAME,
            META_M.CREATE_TIME,
            META_M.STATUS_TIME);

    private static final Function<Record, Meta> RECORD_TO_META_MAPPER = record -> Meta.builder()
            .id(record.get(META_M.ID))
            .feedName(record.get(META_FEED_F.NAME))
            .typeName(record.get(META_TYPE_T.NAME))
            .processorUuid(record.get(META_PROCESSOR_P.PROCESSOR_UUID))
            .pipelineUuid(record.get(META_PROCESSOR_P.PIPELINE_UUID))
            .processorFilterId(record.get(META_M.PROCESSOR_FILTER_ID))
            .processorTaskId(record.get(META_M.PROCESSOR_TASK_ID))
            .parentDataId(record.get(META_M.PARENT_ID))
            .status(MetaStatusId.getStatus(record.get(META_M.STATUS)))
            .statusMs(record.get(META_M.STATUS_TIME))
            .createMs(record.get(META_M.CREATE_TIME))
            .effectiveMs(record.get(META_M.EFFECTIVE_TIME))
            .build();

    private static final Function<Record, Meta> RECORD_TO_PARENT_META_MAPPER = record -> Meta.builder()
            .id(record.get(parent.ID))
            .feedName(record.get(PARENT_FEED.NAME))
            .typeName(record.get(PARENT_TYPE.NAME))
            .processorUuid(record.get(PARENT_PROCESSOR.PROCESSOR_UUID))
            .pipelineUuid(record.get(PARENT_PROCESSOR.PIPELINE_UUID))
            .processorFilterId(record.get(parent.PROCESSOR_FILTER_ID))
            .processorTaskId(record.get(parent.PROCESSOR_TASK_ID))
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
    private final Provider<DataRetentionConfig> dataRetentionConfigProvider;
    private final Provider<MetaServiceConfig> metaServiceConfigProvider;
    private final DocRefInfoService docRefInfoService;
    private final ExpressionMapper expressionMapper;
    private final MetaExpressionMapper metaExpressionMapper;
    private final ValueMapper valueMapper;

    @Inject
    MetaDaoImpl(final MetaDbConnProvider metaDbConnProvider,
                final MetaFeedDaoImpl feedDao,
                final MetaTypeDaoImpl metaTypeDao,
                final MetaProcessorDaoImpl metaProcessorDao,
                final MetaKeyDaoImpl metaKeyDao,
                final Provider<DataRetentionConfig> dataRetentionConfigProvider,
                final Provider<MetaServiceConfig> metaServiceConfigProvider,
                final ExpressionMapperFactory expressionMapperFactory,
                final DocRefInfoService docRefInfoService,
                final TermHandlerFactory termHandlerFactory) {
        this.metaDbConnProvider = metaDbConnProvider;
        this.feedDao = feedDao;
        this.metaTypeDao = metaTypeDao;
        this.metaProcessorDao = metaProcessorDao;
        this.metaKeyDao = metaKeyDao;
        this.dataRetentionConfigProvider = dataRetentionConfigProvider;
        this.metaServiceConfigProvider = metaServiceConfigProvider;
        this.docRefInfoService = docRefInfoService;

        // Extended meta fields.
        metaExpressionMapper = new MetaExpressionMapper(metaKeyDao, termHandlerFactory);
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
        expressionMapper.map(MetaFields.ID, META_M.ID, Long::valueOf);
        expressionMapper.map(MetaFields.META_INTERNAL_PROCESSOR_ID, META_M.PROCESSOR_ID, Integer::valueOf);
        expressionMapper.map(MetaFields.META_PROCESSOR_FILTER_ID, META_M.PROCESSOR_FILTER_ID, Integer::valueOf);
        expressionMapper.map(MetaFields.META_PROCESSOR_TASK_ID, META_M.PROCESSOR_TASK_ID, Long::valueOf);
        expressionMapper.multiMap(MetaFields.FEED, META_M.FEED_ID, this::getFeedIds, true);
        expressionMapper.multiMap(MetaFields.TYPE, META_M.TYPE_ID, this::getTypeIds);
        // Get a uuid for the selected pipe doc
        expressionMapper.map(MetaFields.PIPELINE, META_PROCESSOR_P.PIPELINE_UUID, value -> value, false);
        // Get 0-many uuids for a pipe name (partial/wild-carded)
        expressionMapper.multiMap(
                MetaFields.PIPELINE_NAME, META_PROCESSOR_P.PIPELINE_UUID, this::getPipelineUuidsByName, true);
        expressionMapper.map(MetaFields.STATUS, META_M.STATUS, value ->
                MetaStatusId.getPrimitiveValue(value.toUpperCase()));
        expressionMapper.map(MetaFields.STATUS_TIME, META_M.STATUS_TIME, value ->
                DateExpressionParser.getMs(MetaFields.STATUS_TIME.getFldName(), value));
        expressionMapper.map(MetaFields.CREATE_TIME, META_M.CREATE_TIME, value ->
                DateExpressionParser.getMs(MetaFields.CREATE_TIME.getFldName(), value));
        expressionMapper.map(MetaFields.EFFECTIVE_TIME, META_M.EFFECTIVE_TIME, value ->
                DateExpressionParser.getMs(MetaFields.EFFECTIVE_TIME.getFldName(), value));

        // Parent fields.
        expressionMapper.map(MetaFields.PARENT_ID, META_M.PARENT_ID, Long::valueOf);
        expressionMapper.map(MetaFields.PARENT_STATUS, parent.STATUS, value ->
                MetaStatusId.getPrimitiveValue(value.toUpperCase()));
        expressionMapper.map(MetaFields.PARENT_CREATE_TIME, parent.CREATE_TIME, value ->
                DateExpressionParser.getMs(MetaFields.PARENT_CREATE_TIME.getFldName(), value));
        expressionMapper.map(MetaFields.PARENT_EFFECTIVE_TIME, parent.EFFECTIVE_TIME, value ->
                DateExpressionParser.getMs(MetaFields.PARENT_EFFECTIVE_TIME.getFldName(), value));
        expressionMapper.multiMap(MetaFields.PARENT_FEED, parent.FEED_ID, this::getFeedIds);

        valueMapper = new ValueMapper();
        valueMapper.map(MetaFields.ID, META_M.ID, ValLong::create);
        valueMapper.map(MetaFields.FEED, META_FEED_F.NAME, ValString::create);
        valueMapper.map(MetaFields.TYPE, META_TYPE_T.NAME, ValString::create);
        valueMapper.map(MetaFields.PIPELINE, META_PROCESSOR_P.PIPELINE_UUID, this::getPipelineName);
        valueMapper.map(MetaFields.PIPELINE_NAME, META_PROCESSOR_P.PIPELINE_UUID, this::getPipelineName);
        valueMapper.map(MetaFields.PARENT_ID, META_M.PARENT_ID, ValLong::create);
        valueMapper.map(MetaFields.META_INTERNAL_PROCESSOR_ID, META_M.PROCESSOR_ID, ValInteger::create);
        valueMapper.map(MetaFields.META_PROCESSOR_FILTER_ID, META_M.PROCESSOR_FILTER_ID, ValInteger::create);
        valueMapper.map(MetaFields.META_PROCESSOR_TASK_ID, META_M.PROCESSOR_TASK_ID, ValLong::create);
        valueMapper.map(MetaFields.STATUS, META_M.STATUS, v -> Optional.ofNullable(MetaStatusId.getStatus(v))
                .map(w -> (Val) ValString.create(w.getDisplayValue()))
                .orElse(ValNull.INSTANCE));
        valueMapper.map(MetaFields.STATUS_TIME, META_M.STATUS_TIME, ValDate::create);
        valueMapper.map(MetaFields.CREATE_TIME, META_M.CREATE_TIME, ValDate::create);
        valueMapper.map(MetaFields.EFFECTIVE_TIME, META_M.EFFECTIVE_TIME, ValDate::create);
    }

    private Val getPipelineName(final String uuid) {
        String val = uuid;
        if (docRefInfoService != null) {
            val = docRefInfoService.name(new DocRef(PipelineDoc.TYPE, uuid))
                    .orElse(uuid);
        }
        return ValString.create(val);
    }

    /**
     * Supports wild-carded feed names. Each wild-carded feed name results in 0-* IDs
     */
    private List<Integer> getFeedIds(final List<String> wildCardedFeedNames) {
        if (NullSafe.isEmptyCollection(wildCardedFeedNames)) {
            return Collections.emptyList();
        } else {
            return new ArrayList<>(feedDao.find(wildCardedFeedNames).values());
        }
    }

    /**
     * Supports wild-carded type names
     */
    private List<Integer> getTypeIds(final List<String> wildCardedTypeNames) {
        if (NullSafe.isEmptyCollection(wildCardedTypeNames)) {
            return Collections.emptyList();
        } else {
            return new ArrayList<>(metaTypeDao.find(wildCardedTypeNames).values());
        }
    }

    private List<String> getPipelineUuidsByName(final List<String> pipelineNames) {
        // Can't cache this in a simple map due to pipes being renamed, but
        // docRefInfoService should cache most of this anyway.
        return docRefInfoService.findByNames(PipelineDoc.TYPE, pipelineNames, true)
                .stream()
                .map(DocRef::getUuid)
                .collect(Collectors.toList());
    }

    @Override
    public Long getMaxId() {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                        .select(DSL.max(META_M.ID))
                        .from(META_M)
                        .fetchOptional())
                .map(Record1::value1)
                .orElse(null);
    }

    @Override
    public Long getMaxId(final long maxCreateTimeMs) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                        .select(DSL.max(META_M.ID))
                        .from(META_M)
                        .where(META_M.CREATE_TIME.le(maxCreateTimeMs))
                        .fetchOptional())
                .map(Record1::value1)
                .orElse(null);
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
                                META.PROCESSOR_ID,
                                META.PROCESSOR_FILTER_ID,
                                META.PROCESSOR_TASK_ID)
                        .values(
                                metaProperties.getCreateMs(),
                                metaProperties.getEffectiveMs(),
                                metaProperties.getParentId(),
                                MetaStatusId.LOCKED,
                                metaProperties.getStatusMs(),
                                feedId,
                                typeId,
                                processorId,
                                metaProperties.getProcessorFilterId(),
                                metaProperties.getProcessorTaskId())
                        .returning(META.ID)
                        .fetchOne())
                .getId();

        return Meta
                .builder()
                .id(id)
                .feedName(metaProperties.getFeedName())
                .typeName(metaProperties.getTypeName())
                .processorUuid(metaProperties.getProcessorUuid())
                .pipelineUuid(metaProperties.getPipelineUuid())
                .processorFilterId((metaProperties.getProcessorFilterId()))
                .processorTaskId(metaProperties.getProcessorTaskId())
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
    public void bulkCreate(final List<MetaProperties> metaPropertiesList, final Status status) {
        // ensure we have all the parent records and capture all their ids
        final Map<String, Integer> feedIds = metaPropertiesList.stream()
                .map(MetaProperties::getFeedName)
                .filter(Objects::nonNull)
                .distinct()
                .map(feedName -> Tuple.of(feedName, feedDao.getOrCreate(feedName)))
                .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));

        final Map<String, Integer> typeIds = metaPropertiesList.stream()
                .map(MetaProperties::getTypeName)
                .filter(Objects::nonNull)
                .distinct()
                .map(typeName -> Tuple.of(typeName, metaTypeDao.getOrCreate(typeName)))
                .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));

        final Map<String, Integer> processorIds = metaPropertiesList.stream()
                .filter(metaProperties ->
                        metaProperties.getProcessorUuid() != null &&
                        metaProperties.getPipelineUuid() != null)
                .map(metaProperties -> Tuple.of(metaProperties.getProcessorUuid(), metaProperties.getPipelineUuid()))
                .distinct()
                .map(tuple -> Tuple.of(
                        tuple._1(),
                        metaProcessorDao.getOrCreate(tuple._1(), tuple._2())))
                .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));

        final byte statusId = MetaStatusId.getPrimitiveValue(status);

        // Create a batch of insert stmts, each with n value sets
        JooqUtil.context(metaDbConnProvider, context -> context
                .batch(
                        BatchingIterator.batchedStreamOf(metaPropertiesList, MAX_VALUES_PER_INSERT)
                                .map(metaPropertiesBatch -> {
                                    final InsertValuesStep10<
                                            MetaRecord,
                                            Long,
                                            Long,
                                            Long,
                                            Byte,
                                            Long,
                                            Integer,
                                            Integer,
                                            Integer,
                                            Integer,
                                            Long> insertStep = context
                                            .insertInto(META,
                                                    META.CREATE_TIME,
                                                    META.EFFECTIVE_TIME,
                                                    META.PARENT_ID,
                                                    META.STATUS,
                                                    META.STATUS_TIME,
                                                    META.FEED_ID,
                                                    META.TYPE_ID,
                                                    META.PROCESSOR_ID,
                                                    META.PROCESSOR_FILTER_ID,
                                                    META.PROCESSOR_TASK_ID);

                                    metaPropertiesBatch.forEach(metaProperties ->
                                            insertStep.values(
                                                    metaProperties.getCreateMs(),
                                                    metaProperties.getEffectiveMs(),
                                                    metaProperties.getParentId(),
                                                    statusId,
                                                    metaProperties.getStatusMs(),
                                                    metaProperties.getFeedName() == null
                                                            ? null
                                                            : feedIds.get(metaProperties.getFeedName()),
                                                    metaProperties.getTypeName() == null
                                                            ? null
                                                            : typeIds.get(metaProperties.getTypeName()),
                                                    metaProperties.getProcessorUuid() == null
                                                            ? null
                                                            : processorIds.get(metaProperties.getProcessorUuid()),
                                                    metaProperties.getProcessorFilterId(),
                                                    metaProperties.getProcessorTaskId()));
                                    return insertStep;
                                })
                                .collect(Collectors.toList()))
                .execute());
    }

    @Override
    public int updateStatus(final FindMetaCriteria criteria,
                            final Status currentStatus,
                            final Status newStatus,
                            final long statusTime,
                            final boolean usesUniqueIds) {

        Objects.requireNonNull(newStatus, "New status is null");
        if (Objects.equals(newStatus, currentStatus)) {
            // The status is not being updated.
            throw new RuntimeException("New and current status are equal");
        }

        Objects.requireNonNull(criteria);
        final ExpressionOperator expression = criteria.getExpression();

        if (usesUniqueIds) {
            return updateStatusWithId(expression, currentStatus, newStatus, statusTime);
        } else {
            return updateStatusWithTempTable(expression, currentStatus, newStatus, statusTime);
        }
    }

    private int updateStatusWithId(final ExpressionOperator expression,
                                   final Status currentStatus,
                                   final Status newStatus,
                                   final long statusTime) {
        LOGGER.debug(() ->
                LogUtil.message("updateStatusWithId, expression: {}, currentStatus: {}, " +
                                "newStatus: {}, statusTime: {}",
                        expression, currentStatus, newStatus, LogUtil.instant(statusTime)));

        final byte newStatusId = MetaStatusId.getPrimitiveValue(newStatus);
        final Table<?> metaWithJoins = buildMeteWithOptionalJoins(expression);

        final Condition conditions = createUpdateStatusCondition(expression, currentStatus, newStatus);

        final DurationTimer durationTimer = DurationTimer.start();
        final Integer updateCount = JooqUtil.contextResult(metaDbConnProvider, context -> {
            // If expression is not targeting rows by ID then this is likely to create gap or next key locks
            // which will block ingest/processing.
            final UpdateConditionStep<?> update = context.update(metaWithJoins)
                    .set(META_M.STATUS, newStatusId)
                    .set(META_M.STATUS_TIME, statusTime)
                    .where(conditions);

            LOGGER.trace("Update SQL:\n{}", update);

            return update.execute();
        });

        LOGGER.debug("Set the status of {} meta rows to {} in {}. Expression: {}",
                updateCount, newStatus, durationTimer, expression);

        return updateCount;
    }

    private int updateStatusWithTempTable(final ExpressionOperator expression,
                                          final Status currentStatus,
                                          final Status newStatus,
                                          final long statusTime) {
        LOGGER.debug(() ->
                LogUtil.message("updateStatusWithTempTable, expression: {}, currentStatus: {}, " +
                                "newStatus: {}, statusTime: {}",
                        expression, currentStatus, newStatus, LogUtil.instant(statusTime)));

        final byte newStatusId = MetaStatusId.getPrimitiveValue(newStatus);
        final int batchSize = metaServiceConfigProvider.get().getMetaStatusUpdateBatchSize();

        final Table<?> metaWithJoins = buildMeteWithOptionalJoins(expression);
        final Condition conditions = createUpdateStatusCondition(expression, currentStatus, newStatus);
        final Condition statusCondition = getStatusCondition(currentStatus, newStatus);

        final Select<Record1<Long>> select;
        // One big batch should be more efficient as long as it only locks the rows being changed
        // and no others. If it does lock other rows then smaller batches may be needed.
        if (batchSize <= 0) {
            // 0 == one big batch
            select = DSL.selectDistinct(META_M.ID)
                    .from(metaWithJoins)
                    .where(conditions);
        } else {
            select = DSL.selectDistinct(META_M.ID)
                    .from(metaWithJoins)
                    .where(conditions)
                    .limit(batchSize);
        }

        final Name metaIdsTempTblName = DSL.name("meta_ids_temp");
        final Table<Record> metaIdsTempTbl = DSL.table(metaIdsTempTblName);
        final Name metaIdColName = DSL.name("meta_ids_temp", "id");
        final Field<Long> metaIdColField = DSL.field(metaIdColName, Long.class);

        final AtomicInteger insertCount = new AtomicInteger();
        final AtomicInteger totalUpdateCount = new AtomicInteger();
        final DurationTimer durationTimer = DurationTimer.start();

        // We do this multi-stage deletion to try and avoid next-key or gap locks that end up locking
        // other meta rows and stopping processing. This approach means we update using ID to uniquely
        // access rows.
        do {
            JooqUtil.context(metaDbConnProvider, context -> {
                try {
                    // Temp table of all the IDs we want to update
                    // Need to do it as a create then insert so we get the count
                    context.createTemporaryTable(metaIdsTempTblName)
                            .column(metaIdColField)
                            .execute();

                    final InsertOnDuplicateStep<Record> insert = context.insertInto(metaIdsTempTbl)
                            .select(select);
                    LOGGER.trace("Insert SQL:\n{}", insert);

                    LOGGER.logDurationIfDebugEnabled(
                            () -> insertCount.set(insert.execute()),
                            () -> LogUtil.message("Inserted {} meta ids into temporary table", insertCount));

                    if (insertCount.get() > 0) {
                        final UpdateConditionStep<Record> update = context
                                .update(META_M.innerJoin(metaIdsTempTbl)
                                        .on(META_M.ID.eq(metaIdColField)))
                                .set(META_M.STATUS, newStatusId)
                                .set(META_M.STATUS_TIME, statusTime)
                                .where(statusCondition); // Re-use the status condition in case anything changed
                        LOGGER.trace("Update SQL:\n{}", update);

                        final int updateCount = LOGGER.logDurationIfDebugEnabled(
                                update::execute,
                                cnt -> LogUtil.message("Updated {} meta rows to status: {}", cnt, newStatus));
                        totalUpdateCount.addAndGet(updateCount);

                        // Remove all the temp rows
                        LOGGER.logDurationIfDebugEnabled(
                                () -> context.deleteFrom(metaIdsTempTbl)
                                        .execute(),
                                cnt -> LogUtil.message("Deleted {} rows from temp table", cnt));
                    } else {
                        LOGGER.debug("No more records to update, dropping out");
                    }
                } catch (final Exception e) {
                    LOGGER.error(LogUtil.message(
                            "Error updating the meta status from {} to {} with expression: {}. {}",
                            currentStatus, newStatus, expression, e.getMessage()), e);
                    throw e;
                } finally {
                    // Temp table lives for the life of the session so if we go round again
                    // or if the session is returned to the pool, make sure it is gone.
                    LOGGER.logDurationIfDebugEnabled(
                            () -> context.dropTableIfExists(metaIdsTempTblName)
                                    .execute(),
                            cnt -> LogUtil.message("Dropped temp table", cnt));
                }
            });
        } while (batchSize > 0 && insertCount.get() >= batchSize);

        LOGGER.debug("Set the status of {} meta rows to {} using batchSize {} in {}. Expression: {}",
                totalUpdateCount, newStatus, batchSize, durationTimer, expression);

        return totalUpdateCount.get();
    }

    private Condition getStatusCondition(
            final Status currentStatus,
            final Status newStatus) {

        if (currentStatus != null) {
            final byte currentStatusId = MetaStatusId.getPrimitiveValue(currentStatus);
            return META_M.STATUS.eq(currentStatusId);
        } else {
            final byte newStatusId = MetaStatusId.getPrimitiveValue(newStatus);
            return META_M.STATUS.ne(newStatusId);
        }
    }

    private Table<?> buildMeteWithOptionalJoins(final ExpressionOperator expression) {
        Table<?> table = META_M;

        // Add a condition if we should check current status.
        final boolean containsPipelineCondition = NullSafe.test(
                expression,
                expr ->
                        expr.containsField(MetaFields.PIPELINE.getFldName(), MetaFields.PIPELINE_NAME.getFldName()));

        if (containsPipelineCondition) {
            // Only add in the join to meta_processor if we need it
            table = table.leftOuterJoin(META_PROCESSOR_P)
                    .on(META_M.PROCESSOR_ID.eq(META_PROCESSOR_P.ID));
        }

        final Set<Integer> usedValKeys = identifyExtendedAttributesFields(expression, new HashSet<>());
        if (NullSafe.hasItems(usedValKeys)) {
            // Add 1-* joins to meta_val if we need them.
            table = metaExpressionMapper.addJoins(table, META_M.ID, usedValKeys);
        }

        return table;
    }

    private Condition createUpdateStatusCondition(final ExpressionOperator expression,
                                                  final Status currentStatus,
                                                  final Status newStatus) {

        Objects.requireNonNull(newStatus, "New status is null");
        if (Objects.equals(newStatus, currentStatus)) {
            // The status is not being updated.
            throw new RuntimeException("New and current status are equal");
        }

        final Condition criteriaCondition = expressionMapper.apply(expression);
        final Condition statusCondition = getStatusCondition(currentStatus, newStatus);
        return JooqUtil.andConditions(criteriaCondition, statusCondition);
    }

//    private Select<Record1<Long>> buildUpdateStatusSelect(final ExpressionOperator expression,
//                                                          final Status currentStatus,
//                                                          final Status newStatus,
//                                                          final int batchSize) {
//
//        Objects.requireNonNull(newStatus, "New status is null");
//        if (Objects.equals(newStatus, currentStatus)) {
//            // The status is not being updated.
//            throw new RuntimeException("New and current status are equal");
//        }
//
//        final Condition criteriaCondition = expressionMapper.apply(expression);
//        final Condition statusCondition = getStatusCondition(currentStatus, newStatus);
//        final Condition combinedConditions = JooqUtil.andConditions(criteriaCondition, statusCondition);
//
//        // Add a condition if we should check current status.
//        final boolean containsPipelineCondition = NullSafe.test(
//                expression,
//                expr ->
//                        expr.containsField(MetaFields.PIPELINE.getName(), MetaFields.PIPELINE_NAME.getName()));
//
//        Table<?> fromPart = meta;
//
//        if (containsPipelineCondition) {
//            // Only add in the join to meta_processor if we need it
//            fromPart = fromPart.leftOuterJoin(metaProcessor)
//                    .on(meta.PROCESSOR_ID.eq(metaProcessor.ID));
//        }
//
//        // TODO: 21/02/2023 See https://github.com/gchq/stroom/issues/3253 for changing meta_val
//        //  to be a single row to avoid all these horrible joins
//        final Set<Integer> usedValKeys = identifyExtendedAttributesFields(expression, new HashSet<>());
//        if (NullSafe.hasItems(usedValKeys)) {
//            // Add 1-* joins to meta_val if we need them.
//            fromPart = metaExpressionMapper.addJoins(fromPart, meta.ID, usedValKeys);
//        }
//
//        // As we are limiting, it is key that the conditions ensure we don't pick up
//        // rows we have updated, i.e. the condition on status being == or !=
//        final Select<Record1<Long>> select;
//        if (batchSize <= 0) {
//            // 0 == one big batch
//            select = DSL.selectDistinct(meta.ID)
//                    .from(fromPart)
//                    .where(combinedConditions);
//        } else {
//            select = DSL.selectDistinct(meta.ID)
//                    .from(fromPart)
//                    .where(combinedConditions)
//                    .limit(batchSize);
//        }
//
//        LOGGER.trace("SQL:\n{}", select);
//        return select;
//    }

    private Condition getFilterCriteriaCondition(final ExpressionCriteria criteria) {
        final Condition filterCondition;
        if (criteria != null
            && criteria.getExpression() != null
            && !criteria.getExpression().equals(ExpressionOperator.builder().build())) {

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
            // The highest priority rules first, i.e. the largest rule number
            final Map<Integer, Condition> ruleNoToConditionMap = activeRules.stream()
                    .collect(Collectors.toMap(
                            DataRetentionRule::getRuleNumber,
                            rule ->
                                    expressionMapper.apply(rule.getExpression())));

            final List<Condition> orConditions = activeRules.stream()
                    .map(rule -> ruleNoToConditionMap.get(rule.getRuleNumber()))
                    .collect(Collectors.toList());

            for (final DataRetentionRule rule : activeRules) {
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

            final Field<Integer> ruleNoField = DSL.field("rule_no", Integer.class);
            final Field<String> feedNameField = DSL.field("feed_name", String.class);
            final Field<String> typeNameField = DSL.field("type_name", String.class);
            final Field<Long> metaCreateTimeField = DSL.field("meta_create_time_ms", Long.class);

            activeRules.forEach(rule ->
                    validateExpressionTerms(rule.getExpression()));

            final boolean requiresMetaProcessorTable = rulesContainField(
                    activeRules,
                    MetaFields.PIPELINE.getFldName(),
                    MetaFields.PIPELINE_NAME.getFldName());

            return JooqUtil.contextResult(metaDbConnProvider,
                            context -> {
                                TableOnConditionStep<Record> fromClause = META_M
                                        .straightJoin(META_FEED_F).on(META_M.FEED_ID.eq(META_FEED_F.ID))
                                        .straightJoin(META_TYPE_T).on(META_M.TYPE_ID.eq(META_TYPE_T.ID));
                                // If any of the rules have a predicate on the Pipeline field then we need to
                                // add the join to meta_processor
                                if (requiresMetaProcessorTable) {
                                    fromClause = fromClause.leftOuterJoin(META_PROCESSOR_P)
                                            .on(META_M.PROCESSOR_ID.eq(META_PROCESSOR_P.ID));
                                }
                                // Get all meta records that are impacted by a rule and for each determine
                                // which rule wins and get its rule number, along with feed and type
                                // The OR condition is here to try and help the DB use indexes.
                                final Table<Record4<String, String, Integer, Long>> detailTable = context
                                        .select(
                                                META_FEED_F.NAME.as(feedNameField),
                                                META_TYPE_T.NAME.as(typeNameField),
                                                ruleNoCaseField.as(ruleNoField),
                                                META_M.CREATE_TIME.as(metaCreateTimeField))
                                        .from(fromClause)
                                        .where(META_M.STATUS.notEqual(statusIdDeleted))
//                                        .and(ruleNoCaseField.isNotNull()) // only want data that WILL be deleted
                                        .and(DSL.or(orConditions)) // Here to help use indexes
                                        .and(getFilterCriteriaCondition(criteria)) // UI filtering
                                        .asTable("detail");

                                final List<Condition> ruleAgeConditions = getRuleAgeConditions(
                                        activeRules,
                                        ruleNoField,
                                        metaCreateTimeField,
                                        detailTable);

                                // Now get counts grouped by feed, type and rule
                                final SelectHavingStep<Record4<String, String, Integer, Integer>> query = context
                                        .select(
                                                detailTable.field(feedNameField),
                                                detailTable.field(typeNameField),
                                                detailTable.field(ruleNoField),
                                                DSL.count())
                                        .from(detailTable)
                                        // Ignore rows where the effective rule is a forever one as they
                                        // will never be deleted
                                        .where(detailTable.field(ruleNoField).isNotNull())
                                        // Only include rows that are beyond the rules' retention period
                                        .and(DSL.or(ruleAgeConditions))
                                        .groupBy(
                                                detailTable.field(ruleNoField),
                                                detailTable.field(feedNameField),
                                                detailTable.field(typeNameField));

                                // Dump the query in case we want to run it in a mysql shell.
                                LOGGER.debug("query:\n{}", query);

                                return query.fetch();
                            })
                    .map(record -> {
                        final int ruleNo = record.get(ruleNoField);
                        return new DataRetentionDeleteSummary(
                                record.get(feedNameField),
                                record.get(typeNameField),
                                ruleNo,
                                numberToRuleMap.get(ruleNo).getName(),
                                (int) record.get(DSL.count().getName()));
                    });
        } else {
            // No rules so no point running a query
            result = Collections.emptyList();
        }
        return result;
    }

    /**
     * @return True if any term in any expression uses field {@code field}.
     */
    private boolean ruleActionsContainField(final String field,
                                            final List<DataRetentionRuleAction> ruleActions) {
        return ruleActions.stream()
                .anyMatch(ruleAction ->
                        NullSafe.test(
                                ruleAction.getRule(),
                                DataRetentionRule::getExpression,
                                expr -> expr.containsField(field)));
    }

    /**
     * @return True if any term in any rule expression uses at least one of {@code fields}.
     */
    private boolean rulesContainField(final List<DataRetentionRule> rules, final String... fields) {
        return rules.stream()
                .anyMatch(rule ->
                        NullSafe.test(
                                rule.getExpression(),
                                expr -> expr.containsField(fields)));
    }

    private List<Condition> getRuleAgeConditions(final List<DataRetentionRule> activeRules,
                                                 final Field<Integer> ruleNoField,
                                                 final Field<Long> metaCreateTimeField,
                                                 final Table<Record4<String, String, Integer, Long>> detailTable) {

        final Instant now = Instant.now();
        LOGGER.debug("now: {}", now);

        return activeRules.stream()
                // We already exclude meta rows where the effective rule is a forever one
                // so no need to add an age condition for forever rules
                .filter(rule -> !rule.isForever())
                .map(rule -> {
                    // Any meta record with a creation time older than this is a candidate for deletion
                    final Instant oldestRetainedCreateTime = DataRetentionCreationTimeUtil.minus(
                            now, rule);
                    LOGGER.debug(() -> LogUtil.message("RuleNo: {}, ageStr: {}, latestCreateTime: {}",
                            rule.getRuleNumber(),
                            rule.getAgeString(),
                            oldestRetainedCreateTime));

                    // Each meta row in the detailTable will have an effective rule and the meta creation time
                    // so include the ones where the meta is older than the delete cut off for the rule.
                    return DSL.and(
                            detailTable.field(ruleNoField)
                                    .eq(rule.getRuleNumber()),
                            detailTable.field(metaCreateTimeField)
                                    .lessThan(oldestRetainedCreateTime.toEpochMilli()));
                })
                .collect(Collectors.toList());
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
            final DataRetentionConfig dataRetentionConfig = dataRetentionConfigProvider.get();
            final byte statusIdDeleted = MetaStatusId.getPrimitiveValue(Status.DELETED);

            final List<Condition> baseConditions = createRetentionDeleteConditions(ruleActions);
            final boolean rulesUsePipelineField = ruleActionsContainField(MetaFields.PIPELINE.getFldName(),
                    ruleActions)
                                                  || ruleActionsContainField(MetaFields.PIPELINE_NAME.getFldName(),
                    ruleActions);

            final List<Condition> conditions = new ArrayList<>(baseConditions);

            // Time bound the data we are testing the rules over
            conditions.add(META_M.CREATE_TIME.greaterOrEqual(period.getFrom().toEpochMilli()));
            conditions.add(META_M.CREATE_TIME.lessThan(period.getTo().toEpochMilli()));

            int lastUpdateCount;
            final AtomicInteger iteration = new AtomicInteger(1);
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
                final Optional<TimePeriod> optSubPeriod = getTimeSlice(
                        startTimeInc, batchSize, conditions, rulesUsePipelineField);

                totalSelectDuration = totalSelectDuration.plus(Duration.between(startTime,
                        Instant.now().plusMillis(1)));

                if (optSubPeriod.isEmpty()) {
                    LOGGER.debug("No time slice found");
                    break;
                }
                final TimePeriod subPeriod = optSubPeriod.get();
                LOGGER.debug("Iteration {}, using sub-period {}", iteration, subPeriod);

                startTime = Instant.now();
                lastUpdateCount = LOGGER.logDurationIfDebugEnabled(
                        () -> JooqUtil.contextResult(metaDbConnProvider, context -> {

                            // If any of the rules have a predicate on the Pipeline field then we need to
                            // add the join to meta_processor
                            final Table<?> tableClause = rulesUsePipelineField
                                    ? META_M.leftOuterJoin(META_PROCESSOR_P)
                                    .on(META_M.PROCESSOR_ID.eq(META_PROCESSOR_P.ID))
                                    : META_M;

                            // We might want to do this delete using a temp table like we do for
                            // MetaDaoImpl#updateStatusWithTempTable. Only if locking other rows is
                            // an issue.
                            final UpdateConditionStep<?> query = context
                                    .update(tableClause)
                                    .set(META_M.STATUS, statusIdDeleted)
                                    .set(META_M.STATUS_TIME, Instant.now().toEpochMilli())
                                    .where(conditions)
                                    .and(META_M.CREATE_TIME.greaterOrEqual(subPeriod.getFrom().toEpochMilli()))
                                    .and(META_M.CREATE_TIME.lessThan(subPeriod.getTo().toEpochMilli()));

                            LOGGER.debug("update:\n{}", query);

                            return query.execute();
                        }),
                        cnt -> LogUtil.message(
                                "Logically deleted {} meta records with approx. limit of {}. Iteration {}",
                                cnt,
                                batchSize,
                                iteration));
                totalUpdateDuration = totalUpdateDuration.plus(Duration.between(startTime,
                        Instant.now().plusMillis(1)));

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
                    iteration.get() != 0
                            ? totalSelectDuration.dividedBy(iteration.get())
                            : Duration.ZERO,
                    iteration.get() != 0
                            ? totalUpdateDuration.dividedBy(iteration.get())
                            : Duration.ZERO);

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
                                              final List<Condition> conditions,
                                              final boolean includesMetaProcessorTbl) {
        LOGGER.debug("getTimeSlice({}, {}, {}, {})", startTimeInc, batchSize, conditions, includesMetaProcessorTbl);

        final String createTimeCol = META_M.CREATE_TIME.getName();
        final String minCreateTimeCol = "min_create_time";
        final String maxCreateTimeCol = "max_create_time";

        // For a given set of conditions (that may already contain some create_time bounds
        // get the create_time range for a batch n records
        final Optional<TimePeriod> timePeriod =
                LOGGER.logDurationIfDebugEnabled(() -> JooqUtil
                        .contextResult(metaDbConnProvider,
                                context -> {
                                    // If any of the rules have a predicate on the Pipeline field then we need to
                                    // add the join to meta_processor
                                    final Table<?> fromClause = includesMetaProcessorTbl
                                            ? META_M.straightJoin(META_PROCESSOR_P)
                                            .on(META_M.PROCESSOR_ID.eq(META_PROCESSOR_P.ID))
                                            : META_M;

                                    final Table<?> orderedFullSet = context
                                            .select(META_M.CREATE_TIME)
                                            .from(fromClause)
                                            .where(conditions)
                                            .and(META_M.CREATE_TIME.greaterOrEqual(startTimeInc.toEpochMilli()))
                                            .orderBy(META_M.CREATE_TIME)
                                            .asTable("orderedFullSet");

                                    final Table<?> limitedSet = context
                                            .select(orderedFullSet.fields())
                                            .from(orderedFullSet)
                                            .limit(batchSize)
                                            .asTable("limitedSet");

                                    final var query = context
                                            .select(
                                                    DSL.min(limitedSet.field(createTimeCol)).as(minCreateTimeCol),
                                                    DSL.max(limitedSet.field(createTimeCol)).as(maxCreateTimeCol))
                                            .from(limitedSet);

                                    LOGGER.debug("query:\n{}", query);

                                    return query.fetchOne();
                                })
                        .map(record -> {
                            final Object min = record.get(minCreateTimeCol);
                            final Object max = record.get(maxCreateTimeCol);

                            if (min == null || max == null) {
                                return Optional.empty();
                            } else {
                                // Add one to make it exclusive
                                return Optional.of(
                                        TimePeriod.between((long) min, (long) max + 1));
                            }
                        }), () -> LogUtil.message("Selecting time slice starting at {}, with batch size {}",
                        startTimeInc, batchSize));

        LOGGER.debug("Returning period {}", timePeriod);

        // NOTE The number of records in the slice may differ from the desired batch size if you have
        // multiple records on the boundary with the same create_time (unlikely with milli precision).

        return timePeriod;
    }

    private List<Condition> createRetentionDeleteConditions(final List<DataRetentionRuleAction> ruleActions) {
        Objects.requireNonNull(ruleActions);
        if (ruleActions.isEmpty()) {
            throw new IllegalArgumentException("Expected one or more rules");
        }

        final List<Condition> conditions = new ArrayList<>();
        final byte statusIdUnlocked = MetaStatusId.getPrimitiveValue(Status.UNLOCKED);

        // Ensure we only 'delete' unlocked records, also ensures we don't touch
        // records we have already deleted in a previous pass
        conditions.add(META_M.STATUS.eq(statusIdUnlocked));

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
        final List<Condition> orConditions = new ArrayList<>();
        final DataRetentionConfig dataRetentionConfig = dataRetentionConfigProvider.get();
        // Order is critical here as we are building a case statement
        // Highest priority rules first, i.e. largest rule number
        for (final DataRetentionRuleAction ruleAction : ruleActions) {
            final Condition ruleCondition = expressionMapper.apply(ruleAction.getRule().getExpression());
            if (dataRetentionConfig.isUseQueryOptimisation() &&
                !DSL.noCondition().equals(ruleCondition)) {
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

        if (caseConditionStep != null) {
            // If none of the rules matches then we don't to delete so return false
            final Field<Boolean> caseField = caseConditionStep.otherwise(Boolean.FALSE);

            // Add our rule conditions
            conditions.add(caseField.eq(true));
        }

        // Now add all the rule conditions as an OR block
        // This is to improve the performance of the query as the OR can make use of other indexes,
        // e.g. range scanning the feedid+createTime index to reduce the number of rows scanned.
        // We are still reliant on the case statement block to get the right outcome for the rules.
        // It is possible this approach may slow things down as it makes the SQL more complex.
        if (dataRetentionConfig.isUseQueryOptimisation()) {
            if (orConditions.size() > 1) {
                conditions.add(DSL.or(orConditions));
            } else if (orConditions.size() == 1) {
                conditions.add(orConditions.get(0));
            }
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
        final Set<Integer> usedValKeys = identifyExtendedAttributesFields(criteria.getExpression(),
                new HashSet<>());

        final Object result = JooqUtil.contextResult(metaDbConnProvider, context ->
                        metaExpressionMapper.addJoins(
                                        context
                                                .selectCount()
                                                .from(META_M)
                                                .straightJoin(META_FEED_F).on(META_M.FEED_ID.eq(META_FEED_F.ID))
                                                .straightJoin(META_TYPE_T).on(META_M.TYPE_ID.eq(META_TYPE_T.ID))
                                                .leftOuterJoin(META_PROCESSOR_P)
                                                .on(META_M.PROCESSOR_ID.eq(META_PROCESSOR_P.ID)),
                                        META_M.ID,
                                        usedValKeys)
                                .where(conditions)
                                .fetchOne())
                .get(0);

        return (Integer) result;
    }

    private boolean isUsed(final Set<String> fieldSet,
                           final String[] resultFields,
                           final ExpressionCriteria criteria) {
        return Arrays.stream(resultFields).filter(Objects::nonNull).anyMatch(fieldSet::contains) ||
               ExpressionUtil.termCount(criteria.getExpression(), fieldSet) > 0;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {
        final String[] fieldNames = fieldIndex.getFields();
        final boolean feedUsed = isUsed(Set.of(MetaFields.FEED.getFldName()), fieldNames, criteria);
        final boolean typeUsed = isUsed(Set.of(MetaFields.TYPE.getFldName()), fieldNames, criteria);
        final boolean pipelineUsed = isUsed(Set.of(
                        MetaFields.PIPELINE.getFldName(),
                        MetaFields.PIPELINE_NAME.getFldName()),
                fieldNames, criteria);
        final Set<String> extendedFieldNames = MetaFields
                .getExtendedFields()
                .stream()
                .map(QueryField::getFldName)
                .collect(Collectors.toSet());
        final boolean extendedValuesUsed = isUsed(extendedFieldNames, fieldNames, criteria);

        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);
        final List<Field<?>> dbFields = valueMapper.getDbFieldsByName(fieldNames);
        final Mapper<?>[] mappers = valueMapper.getMappersForFieldNames(fieldNames);

        // Deal with extended fields.
        final int[] extendedFieldKeys = new int[fieldNames.length];
        final List<Integer> extendedFieldKeyIdList = new ArrayList<>();
        final Map<Long, Map<Integer, Long>> extendedFieldValueMap = new HashMap<>();
        for (int i = 0; i < fieldNames.length; i++) {
            final int index = i;
            final String fieldName = fieldNames[i];
            extendedFieldKeys[i] = -1;

            if (extendedFieldNames.contains(fieldName)) {
                final Optional<Integer> keyId = metaKeyDao.getIdForName(fieldName);
                keyId.ifPresent(id -> {
                    extendedFieldKeys[index] = id;
                    extendedFieldKeyIdList.add(id);
                });
            }
        }

        // Need to modify requested fields to include id if we are going to fetch extended attributes.
        if (extendedValuesUsed) {
            if (dbFields.stream().noneMatch(META_M.ID::equals)) {
                dbFields.add(META_M.ID);
            }
        }

        JooqUtil.context(metaDbConnProvider, context -> {
            Integer offset = null;
            Integer numberOfRows = null;

            if (pageRequest != null) {
                offset = pageRequest.getOffset();
                numberOfRows = pageRequest.getLength();
            }

            SelectJoinStep<Record> select = context.select(dbFields).from(META_M);
            if (feedUsed) {
                select = select.straightJoin(META_FEED_F).on(META_M.FEED_ID.eq(META_FEED_F.ID));
            }
            if (typeUsed) {
                select = select.straightJoin(META_TYPE_T).on(META_M.TYPE_ID.eq(META_TYPE_T.ID));
            }
            if (pipelineUsed) {
                select = select.leftOuterJoin(META_PROCESSOR_P).on(META_M.PROCESSOR_ID.eq(META_PROCESSOR_P.ID));
            }

            // Need to add one join to meta_val for each meta key id used in the criteria
            final Set<Integer> usedValKeys = identifyExtendedAttributesFields(
                    criteria.getExpression(),
                    new HashSet<>());
            select = metaExpressionMapper.addJoins(select, META_M.ID, usedValKeys);

            try (final Cursor<?> cursor = select
                    .where(conditions)
                    .orderBy(orderFields)
                    .limit(offset, numberOfRows)
                    .fetchLazy()) {

                while (cursor.hasNext()) {
                    final Result<?> result = cursor.fetchNext(1000);

                    // If we require some extended values then perform another query to get them.
                    if (extendedValuesUsed) {
                        final List<Long> idList = result.getValues(META_M.ID);
                        fillExtendedFieldValueMap(context, idList, extendedFieldKeyIdList, extendedFieldValueMap);
                    }

                    result.forEach(r -> {
                        final Val[] arr = new Val[fieldNames.length];

                        Map<Integer, Long> extendedValues = null;
                        if (extendedValuesUsed) {
                            extendedValues = extendedFieldValueMap.get(r.get(META_M.ID));
                        }

                        for (int i = 0; i < fieldNames.length; i++) {
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
                        consumer.accept(Val.of(arr));
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

        validateExpressionTerms(criteria.getExpression());

        final Collection<Condition> conditions = createCondition(criteria);

        final Collection<OrderField<?>> orderFields = NullSafe.isEmptyCollection(criteria.getSortList())
                ? Collections.singleton(META_M.ID)
                : createOrderFields(criteria);

        final int offset = JooqUtil.getOffset(pageRequest);
        final int numberOfRows = JooqUtil.getLimit(pageRequest, true, FIND_RECORD_LIMIT);

        final Set<Integer> extendedAttributeIds = identifyExtendedAttributesFields(criteria.getExpression(),
                new HashSet<>());

        final List<Meta> list = find(conditions, orderFields, offset, numberOfRows, extendedAttributeIds);
        if (list.size() >= FIND_RECORD_LIMIT) {
            LOGGER.warn("Hit max record limit of '" + FIND_RECORD_LIMIT + "' when finding meta records");
        }

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    private Set<Integer> identifyExtendedAttributesFields(final ExpressionOperator expr,
                                                          final Set<Integer> identified) {

        if (expr == null || expr.getChildren() == null) {
            return identified;
        }
        for (final ExpressionItem child : expr.getChildren()) {
            if (child.enabled()) {
                if (child instanceof ExpressionTerm) {
                    final ExpressionTerm term = (ExpressionTerm) child;

                    if (EXTENDED_FIELD_NAMES.contains(term.getField())) {
                        final Optional<Integer> key = metaKeyDao.getIdForName(term.getField());
                        key.ifPresent(identified::add);
                    }
                } else if (child instanceof ExpressionOperator) {
                    identified.addAll(identifyExtendedAttributesFields((ExpressionOperator) child, identified));
                } else {
                    //Don't know what this is!
                    LOGGER.warn("Unknown ExpressionItem type " + child.getClass().getName() +
                                " unable to optimise meta query");
                    //Allow search to succeed without optimisation
                    return IntStream.range(metaKeyDao.getMinId(),
                            metaKeyDao.getMaxId()).boxed().collect(Collectors.toSet());
                }
            }
        }
        return identified;
    }

    private List<Meta> find(final Collection<Condition> conditions,
                            final Collection<OrderField<?>> orderFields,
                            final int offset,
                            final int numberOfRows,
                            final Set<Integer> usedValKeys) {

        return JooqUtil.contextResult(
                        metaDbConnProvider,
                        context -> {
                            final SelectWithTiesAfterOffsetStep<Record12<
                                    Long,
                                    String,
                                    String,
                                    String,
                                    String,
                                    Long,
                                    Byte,
                                    Long,
                                    Long,
                                    Long,
                                    Integer,
                                    Long>> select = metaExpressionMapper.addJoins(
                                            context
                                                    .selectDistinct(
                                                            META_M.ID,
                                                            META_FEED_F.NAME,
                                                            META_TYPE_T.NAME,
                                                            META_PROCESSOR_P.PROCESSOR_UUID,
                                                            META_PROCESSOR_P.PIPELINE_UUID,
                                                            META_M.PARENT_ID,
                                                            META_M.STATUS,
                                                            META_M.STATUS_TIME,
                                                            META_M.CREATE_TIME,
                                                            META_M.EFFECTIVE_TIME,
                                                            META_M.PROCESSOR_FILTER_ID,
                                                            META_M.PROCESSOR_TASK_ID
                                                    )
                                                    .from(META_M)
                                                    .straightJoin(META_FEED_F).on(META_M.FEED_ID.eq(META_FEED_F.ID))
                                                    .straightJoin(META_TYPE_T).on(META_M.TYPE_ID.eq(META_TYPE_T.ID))
                                                    .leftOuterJoin(META_PROCESSOR_P)
                                                    .on(META_M.PROCESSOR_ID.eq(META_PROCESSOR_P.ID)),
                                            META_M.ID,
                                            usedValKeys)
                                    .where(conditions)
                                    .orderBy(orderFields)
                                    .limit(offset, numberOfRows);

                            LOGGER.debug("Find SQL:\n{}", select);

                            return select.fetch();
                        })
                .map(RECORD_TO_META_MAPPER::apply);
    }


    @Override
    public ResultPage<Meta> findReprocess(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);

        final int offset = JooqUtil.getOffset(pageRequest);
        final int numberOfRows = JooqUtil.getLimit(pageRequest, true, FIND_RECORD_LIMIT);

        final Set<Integer> extendedAttributeIds = identifyExtendedAttributesFields(criteria.getExpression(),
                new HashSet<>());

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
                                                .selectDistinct(
                                                        parent.ID,
                                                        PARENT_FEED.NAME,
                                                        PARENT_TYPE.NAME,
                                                        PARENT_PROCESSOR.PROCESSOR_UUID,
                                                        PARENT_PROCESSOR.PIPELINE_UUID,
                                                        parent.PARENT_ID,
                                                        parent.STATUS,
                                                        parent.STATUS_TIME,
                                                        parent.CREATE_TIME,
                                                        parent.EFFECTIVE_TIME,
                                                        parent.PROCESSOR_FILTER_ID,
                                                        parent.PROCESSOR_TASK_ID
                                                )
                                                .from(META_M)
                                                .straightJoin(META_FEED_F).on(META_M.FEED_ID.eq(META_FEED_F.ID))
                                                .straightJoin(META_TYPE_T).on(META_M.TYPE_ID.eq(META_TYPE_T.ID))
                                                .leftOuterJoin(META_PROCESSOR_P).on(META_M.PROCESSOR_ID.eq(
                                                        META_PROCESSOR_P.ID))
                                                .leftOuterJoin(parent).on(META_M.PARENT_ID.eq(parent.ID))
                                                .leftOuterJoin(PARENT_FEED).on(parent.FEED_ID.eq(PARENT_FEED.ID))
                                                .leftOuterJoin(PARENT_TYPE).on(parent.TYPE_ID.eq(PARENT_TYPE.ID))
                                                .leftOuterJoin(PARENT_PROCESSOR).on(parent.PROCESSOR_ID
                                                        .eq(PARENT_PROCESSOR.ID))),
                                        META_M.ID,
                                        usedValKeys)
                                .where(conditions)
                                .and(parent.ID.isNotNull())
                                .groupBy(parent.ID)
                                .orderBy(parent.ID)
                                .limit(offset, numberOfRows)
                                .fetch())
                .map(RECORD_TO_PARENT_META_MAPPER::apply);
    }

    @Override
    public SelectionSummary getSelectionSummary(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);
        final Set<Integer> usedValKeys = identifyExtendedAttributesFields(criteria.getExpression(),
                new HashSet<>());

        final int offset = JooqUtil.getOffset(pageRequest);
        final int numberOfRows = JooqUtil.getLimit(pageRequest, false);

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
                                                        DSL.countDistinct(META_M.ID),
                                                        DSL.countDistinct(META_FEED_F.NAME),
                                                        DSL.groupConcatDistinct(META_FEED_F.NAME)
                                                                .separator(GROUP_CONCAT_DELIMITER),
                                                        DSL.countDistinct(META_TYPE_T.NAME),
                                                        DSL.groupConcatDistinct(META_TYPE_T.NAME)
                                                                .separator(GROUP_CONCAT_DELIMITER),
                                                        DSL.countDistinct(META_PROCESSOR_P.PROCESSOR_UUID),
                                                        DSL.countDistinct(META_PROCESSOR_P.PIPELINE_UUID),
                                                        DSL.countDistinct(META_M.STATUS),
                                                        DSL.groupConcatDistinct(META_M.STATUS)
                                                                .separator(GROUP_CONCAT_DELIMITER),
                                                        DSL.min(META_M.CREATE_TIME),
                                                        DSL.max(META_M.CREATE_TIME)
                                                )
                                                .from(META_M)
                                                .straightJoin(META_FEED_F).on(META_M.FEED_ID.eq(META_FEED_F.ID))
                                                .straightJoin(META_TYPE_T).on(META_M.TYPE_ID.eq(META_TYPE_T.ID))
                                                .leftOuterJoin(META_PROCESSOR_P)
                                                .on(META_M.PROCESSOR_ID.eq(META_PROCESSOR_P.ID)),
                                        META_M.ID,
                                        usedValKeys)
                                .where(conditions)
                                .limit(offset, numberOfRows)
                                .fetchOptional())
                .map(record -> {
                    final Set<String> distinctFeeds = splitGroupConcat(record.get(2, String.class));
                    final Set<String> distinctTypes = splitGroupConcat(record.get(4, String.class));
                    final Set<String> distinctStatuses = getDistinctStatuses(record.get(8, String.class));

                    return new SelectionSummary(
                            NullSafe.getInt(record.get(0, Integer.class)),
                            NullSafe.getInt(record.get(1, Integer.class)),
                            distinctFeeds,
                            NullSafe.getInt(record.get(3, Integer.class)),
                            distinctTypes,
                            NullSafe.getInt(record.get(5, Integer.class)),
                            NullSafe.getInt(record.get(6, Integer.class)),
                            NullSafe.getInt(record.get(7, Integer.class)),
                            distinctStatuses,
                            new Range<>((Long) record.get(9), (Long) record.get(10)));
                })
                .orElse(null);
    }

    private Set<String> getDistinctStatuses(final String str) {
        return splitGroupConcat(str)
                .stream()
                .map(Byte::parseByte)
                .map(MetaStatusId::getStatus)
                .map(Status::getDisplayValue)
                .collect(Collectors.toSet());
    }

    private Set<String> splitGroupConcat(final String str) {
        if (NullSafe.isEmptyString(str)) {
            return Collections.emptySet();
        } else {
            // Could potentially be loads of parts so truncate to a sensible number.
            // MySQL will also truncate the length of the returned string to
            // 1024 (or the value of param 'group_concat_max_len')
            final String[] parts = GROUP_CONCAT_DELIMITER_PATTERN.split(str);
            return NullSafe.stream(parts)
                    .limit(SelectionSummary.MAX_GROUP_CONCAT_PARTS)
                    .collect(Collectors.toSet());
        }
    }

    @Override
    public SelectionSummary getReprocessSelectionSummary(final FindMetaCriteria criteria) {
        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria);

        final Set<Integer> usedValKeys = identifyExtendedAttributesFields(criteria.getExpression(),
                new HashSet<>());

        final int offset = JooqUtil.getOffset(pageRequest);
        final int numberOfRows = JooqUtil.getLimit(pageRequest, false);

        return getReprocessSelectionSummary(conditions, offset, numberOfRows, usedValKeys);
    }

    private SelectionSummary getReprocessSelectionSummary(final Collection<Condition> conditions,
                                                          final int offset,
                                                          final int numberOfRows,
                                                          final Set<Integer> usedValKeys) {
        // CheckStyle is a bit fussy about this block
        return JooqUtil.contextResult(
                        metaDbConnProvider,
                        context -> {
                            final SelectOnConditionStep<Record11<
                                    Integer,
                                    Integer,
                                    String,
                                    Integer,
                                    String,
                                    Integer,
                                    Integer,
                                    Integer,
                                    String,
                                    Long,
                                    Long>> baseQuery = context.select(
                                            DSL.countDistinct(parent.ID),
                                            DSL.countDistinct(parent.FEED_ID),
                                            DSL.groupConcatDistinct(PARENT_FEED.NAME)
                                                    .separator(GROUP_CONCAT_DELIMITER),
                                            DSL.countDistinct(parent.TYPE_ID),
                                            DSL.groupConcatDistinct(PARENT_TYPE.NAME)
                                                    .separator(GROUP_CONCAT_DELIMITER),
                                            DSL.countDistinct(META_M.PROCESSOR_ID),
                                            DSL.countDistinct(META_PROCESSOR_P.PIPELINE_UUID),
                                            DSL.countDistinct(parent.STATUS),
                                            DSL.groupConcatDistinct(parent.STATUS)
                                                    .separator(GROUP_CONCAT_DELIMITER),
                                            DSL.min(parent.CREATE_TIME),
                                            DSL.max(parent.CREATE_TIME)
                                    )
                                    .from(META_M)
                                    .straightJoin(META_FEED_F).on(META_M.FEED_ID.eq(META_FEED_F.ID))
                                    .straightJoin(META_TYPE_T).on(META_M.TYPE_ID.eq(META_TYPE_T.ID))
                                    .leftOuterJoin(META_PROCESSOR_P).on(META_M.PROCESSOR_ID.eq(META_PROCESSOR_P.ID))
                                    .leftOuterJoin(parent).on(META_M.PARENT_ID.eq(parent.ID))
                                    .straightJoin(PARENT_FEED).on(parent.FEED_ID.eq(PARENT_FEED.ID))
                                    .straightJoin(PARENT_TYPE).on(parent.TYPE_ID.eq(PARENT_TYPE.ID));

                            // Status predicates need to be consistent with those in
                            // stroom.processor.impl.ProcessorTaskCreatorImpl.runSelectMetaQuery
                            // The default status=Unlocked term gets removed by the UI prior to
                            // getting here.
                            final SelectWithTiesAfterOffsetStep<Record11<
                                    Integer,
                                    Integer,
                                    String,
                                    Integer,
                                    String,
                                    Integer,
                                    Integer,
                                    Integer,
                                    String,
                                    Long,
                                    Long>> sql = metaExpressionMapper.addJoins(
                                            baseQuery,
                                            META_M.ID,
                                            usedValKeys)
                                    .where(conditions)
                                    .and(parent.ID.isNotNull())
                                    .and(parent.STATUS.notEqual(MetaStatusId.getPrimitiveValue(Status.DELETED)))
                                    .and(META_M.STATUS.notEqual(MetaStatusId.getPrimitiveValue(Status.DELETED)))
                                    .limit(offset, numberOfRows);

                            LOGGER.debug("getReprocessSelectionSummary() - sql:\n{}", sql);
                            return sql.fetchOptional();
                        })
                .map(record -> {
                    final Set<String> distinctFeeds = splitGroupConcat(record.get(2, String.class));
                    final Set<String> distinctTypes = splitGroupConcat(record.get(4, String.class));
                    final Set<String> distinctStatuses = getDistinctStatuses(record.get(8, String.class));
                    return new SelectionSummary(
                            NullSafe.getInt(record.get(0, Integer.class)),
                            NullSafe.getInt(record.get(1, Integer.class)),
                            distinctFeeds,
                            NullSafe.getInt(record.get(3, Integer.class)),
                            distinctTypes,
                            NullSafe.getInt(record.get(5, Integer.class)),
                            NullSafe.getInt(record.get(6, Integer.class)),
                            NullSafe.getInt(record.get(7, Integer.class)),
                            distinctStatuses,
                            new Range<>((Long) record.get(9), (Long) record.get(10)));
                })
                .orElse(null);
    }

    @Override
    public int delete(final Collection<Long> metaIds) {
        if (NullSafe.hasItems(metaIds)) {
            return JooqUtil.contextResult(metaDbConnProvider, context -> context
                    .deleteFrom(META_M)
                    .where(META_M.ID.in(metaIds))
                    .execute());
        } else {
            return 0;
        }
    }

    @Override
    public int getLockCount() {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                        .selectCount()
                        .from(META_M)
                        .where(META_M.STATUS.eq(MetaStatusId.LOCKED))
                        .fetchOptional()
                        .map(Record1::value1))
                .orElse(0);
    }

    private Collection<Condition> createCondition(final FindMetaCriteria criteria) {
        return createCondition(criteria.getExpression());
    }

    private Collection<Condition> createCondition(final ExpressionOperator expression) {
        final Condition criteriaCondition = expressionMapper.apply(expression);

        return Collections.singletonList(criteriaCondition);
    }

    private Collection<OrderField<?>> createOrderFields(final ExpressionCriteria criteria) {
        if (criteria.getSortList() == null) {
            return Collections.emptyList();
        }
        return criteria.getSortList().stream().map(sort -> {
            final Field<?> field;
            if (MetaFields.ID.getFldName().equals(sort.getId())) {
                field = META_M.ID;
            } else if (MetaFields.CREATE_TIME.getFldName().equals(sort.getId())) {
                field = META_M.CREATE_TIME;
            } else if (MetaFields.EFFECTIVE_TIME.getFldName().equals(sort.getId())) {
                field = META_M.EFFECTIVE_TIME;
            } else if (MetaFields.FEED.getFldName().equals(sort.getId())) {
                field = META_FEED_F.NAME;
            } else if (MetaFields.TYPE.getFldName().equals(sort.getId())) {
                field = META_TYPE_T.NAME;
            } else if (MetaFields.PARENT_ID.getFldName().equals(sort.getId())) {
                field = META_M.PARENT_ID;
            } else if (MetaFields.PARENT_CREATE_TIME.getFldName().equals(sort.getId())) {
                field = parent.CREATE_TIME;
            } else if (MetaFields.PARENT_EFFECTIVE_TIME.getFldName().equals(sort.getId())) {
                field = parent.EFFECTIVE_TIME;
            } else {
                field = META_M.ID;
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
        final Set<Integer> usedValKeys = identifyExtendedAttributesFields(criteria.getExpression(),
                new HashSet<>());

        return JooqUtil.contextResult(metaDbConnProvider,
                        context -> {
                            SelectJoinStep<Record1<String>> select = context
                                    .selectDistinct(META_PROCESSOR_P.PROCESSOR_UUID)
                                    .from(META_M);

                            select = select
                                    .straightJoin(META_FEED_F).on(META_M.FEED_ID.eq(META_FEED_F.ID))
                                    .straightJoin(META_TYPE_T).on(META_M.TYPE_ID.eq(META_TYPE_T.ID))
                                    .leftOuterJoin(META_PROCESSOR_P).on(META_M.PROCESSOR_ID.eq(META_PROCESSOR_P.ID));

                            // If the criteria contain many terms that come from meta_val then we need to join
                            // to meta_val multiple times, each time with a new table alias
                            select = metaExpressionMapper.addJoins(select, META_M.ID, usedValKeys);

                            return select
                                    .where(conditions)
                                    .groupBy(META_PROCESSOR_P.PROCESSOR_UUID)
                                    .fetch();
                        })
                .map(Record1::value1);
    }

    private SelectConditionStep<Record2<Long, Long>> createBaseEffectiveStreamsQuery(
            final DSLContext context,
            final int feedId,
            final int metaTypeId) {

        final byte unlockedId = MetaStatusId.getPrimitiveValue(Status.UNLOCKED);

        // Force the idx to ensure mysql uses the idx with feed_id|effective_time rather than
        return context.select(
                        META_M.ID,
                        META_M.EFFECTIVE_TIME)
                .from(META_M)
                .where(META_M.FEED_ID.eq(feedId))
                .and(META_M.TYPE_ID.eq(metaTypeId))
                .and(META_M.STATUS.eq(unlockedId));
    }

    @Override
    public EffectiveMetaSet getEffectiveStreams(final EffectiveMetaDataCriteria effectiveMetaDataCriteria) {

        LOGGER.debug("getEffectiveStreams({})", effectiveMetaDataCriteria);

        Objects.requireNonNull(effectiveMetaDataCriteria);
        final Period period = Objects.requireNonNull(effectiveMetaDataCriteria.getEffectivePeriod());
        // Inclusive
        final long fromMs = Objects.requireNonNull(period.getFromMs());
        // Exclusive
        final long toMs = Objects.requireNonNull(period.getToMs());
        final String feedName = effectiveMetaDataCriteria.getFeed();
        final String typeName = effectiveMetaDataCriteria.getType();

        // Debatable whether we should throw an exception if the feed/type don't exist
        final Optional<Integer> optFeedId = feedDao.get(feedName);
        if (optFeedId.isEmpty()) {
            LOGGER.debug("Feed {} not found in the {} table.",
                    feedName, META_FEED.NAME);
            return EffectiveMetaSet.empty();
        }
        final int feedId = optFeedId.get();

        final Optional<Integer> optTypeId = metaTypeDao.get(typeName);
        if (optTypeId.isEmpty()) {
            LOGGER.warn("Meta Type {} not found in the database", typeName);
            return EffectiveMetaSet.empty();
        }
        final int typeId = optTypeId.get();

        final Builder metaSetBuilder = EffectiveMetaSet.builder(feedName, typeName);

        JooqUtil.context(metaDbConnProvider,
                context -> {
                    // Try to get a single stream that is just before our range.  This is so that we always
                    // have a stream (unless there are no streams at all) that was effective at the start
                    // of our range.
                    final SelectLimitPercentStep<Record2<Long, Long>> selectUpToRange =
                            createBaseEffectiveStreamsQuery(
                                    context, feedId, typeId)
                                    .and(META_M.EFFECTIVE_TIME.lessOrEqual(fromMs))
                                    .orderBy(META_M.EFFECTIVE_TIME.desc())
                                    .limit(1);

                    // Get the streams in our range
                    final SelectConditionStep<Record2<Long, Long>> selectInRange = createBaseEffectiveStreamsQuery(
                            context, feedId, typeId)
                            .and(META_M.EFFECTIVE_TIME.greaterThan(fromMs))
                            .and(META_M.EFFECTIVE_TIME.lessThan(toMs));

                    // We want to include dups so that we can log and remove them later
                    final SelectOrderByStep<Record2<Long, Long>> select = selectUpToRange.unionAll(selectInRange);

                    LOGGER.debug("select:\n{}", select);

                    select.fetch()
                            .forEach(rec ->
                                    metaSetBuilder.add(
                                            rec.get(META_M.ID),
                                            rec.get(META_M.EFFECTIVE_TIME)));
                });

        final EffectiveMetaSet streamsInOrBelowRange = metaSetBuilder.build();

        LOGGER.debug(() -> LogUtil.message("returning {} effective streams for feedId: {}, typeId: {}, criteria: {}",
                streamsInOrBelowRange.size(),
                streamsInOrBelowRange.getFeedName(),
                streamsInOrBelowRange.getTypeName(),
                effectiveMetaDataCriteria));

        return streamsInOrBelowRange;
    }

    @Override
    public List<SimpleMeta> getLogicallyDeleted(final Instant deleteThreshold,
                                                final int batchSize,
                                                final Set<Long> metaIdExcludeSet) {
        LOGGER.debug(() -> LogUtil.message("getLogicallyDeleted() - deleteThreshold: {}, batchSize: {}, " +
                                           "metaIdExcludeSet size: {}",
                deleteThreshold, batchSize, metaIdExcludeSet.size()));

        Objects.requireNonNull(deleteThreshold);
        final List<SimpleMeta> simpleMetas;
        if (batchSize > 0) {
            final byte statusIdDeleted = MetaStatusId.getPrimitiveValue(Status.DELETED);
            // Get a batch starting from the cut off threshold and working backwards in time.
            // This is so next time we can work from the previous min status time.
            final TimedResult<List<SimpleMeta>> timedResult = JooqUtil.timedContextResult(
                    metaDbConnProvider,
                    LOGGER.isDebugEnabled(),
                    context -> {
                        final SelectConditionStep<Record> select = context
                                .select(SIMPLE_META_SELECT_FIELDS)
                                .from(META_M)
                                .straightJoin(META_TYPE_T).on(META_M.TYPE_ID.eq(META_TYPE_T.ID))
                                .straightJoin(META_FEED_F).on(META_M.FEED_ID.eq(META_FEED_F.ID))
                                .where(META_M.STATUS.eq(statusIdDeleted))
                                .and(META_M.STATUS_TIME.lessOrEqual(deleteThreshold.toEpochMilli()));

                        // Here to stop us trying to pick up any failed ones from the previous batch.
                        // This is because this may be called repeatedly with the same deleteThreshold
                        // if there are many metas with the same statusTime. In healthy operation this will be
                        // empty so won't impact the query performance.
                        if (NullSafe.hasItems(metaIdExcludeSet)) {
                            select.and(META_M.ID.notIn(metaIdExcludeSet));
                        }

                        // Should be able to scan down the status_status_time_idx
                        return select.orderBy(META_M.STATUS_TIME.desc())
                                .limit(batchSize)
                                .fetch(this::mapToSimpleMeta);
                    });

            simpleMetas = timedResult.getResult();

            LOGGER.debug(() -> LogUtil.message("getLogicallyDeleted() - Selected {} meta records in {}",
                    simpleMetas.size(), timedResult.getDuration()));
        } else {
            simpleMetas = Collections.emptyList();
        }
        LOGGER.debug(() -> LogUtil.message("Found {} meta records", simpleMetas.size()));
        return simpleMetas;
    }

    private SimpleMeta mapToSimpleMeta(final Record record) {
        try {
            return new SimpleMetaImpl(
                    record.get(META_M.ID, long.class),
                    record.get(META_TYPE_T.NAME, String.class),
                    record.get(META_FEED_F.NAME, String.class),
                    record.get(META_M.CREATE_TIME, long.class),
                    record.get(META_M.STATUS_TIME, Long.class));
        } catch (final IllegalArgumentException | DataTypeException e) {
            throw new RuntimeException(LogUtil.message("Error mapping record {} to {}: {}",
                    record, SimpleMetaImpl.class.getSimpleName(), e.getMessage()), e);
        }
    }

    public boolean validateExpressionTerms(final ExpressionItem expressionItem) {
        // TODO: 31/10/2022 Ideally this would be done in CommonExpressionMapper but we
        //  seem to have a load of expressions using unsupported conditions so would get
        //  exceptions all over the place.

        if (expressionItem == null) {
            return true;
        } else {
            final Map<String, QueryField> fieldMap = MetaFields.getAllFieldMap();

            return ExpressionUtil.validateExpressionTerms(expressionItem, term -> {
                final QueryField field = fieldMap.get(term.getField());
                if (field == null) {
                    throw new RuntimeException(LogUtil.message("Unknown field {} in term {}, in expression {}",
                            term.getField(), term, expressionItem));
                } else {
                    final boolean isValid = field.supportsCondition(term.getCondition());
                    if (!isValid) {
                        throw new RuntimeException(LogUtil.message("Condition '{}' is not supported by field '{}' " +
                                                                   "of type {}. Term: {}",
                                term.getCondition(),
                                term.getField(),
                                field.getFldType().getTypeName(), term));
                    } else {
                        return true;
                    }
                }
            });
        }
    }

    @Override
    public Set<Long> findLockedMeta(final Collection<Long> metaIdCollection) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META.ID)
                .from(META)
                .where(META.ID.in(metaIdCollection))
                .and(META.STATUS.eq(MetaStatusId.LOCKED))
                .fetchSet(META.ID));
    }

    @Override
    public List<SimpleMeta> findBatch(final long minId,
                                      final Long maxId,
                                      final int batchSize) {

        LOGGER.debug("findBatch() - minId: {}, maxId: {}, batchSize: {}",
                minId, maxId, batchSize);

        if ((maxId != null && maxId < minId)
            || batchSize <= 0) {
            return Collections.emptyList();
        } else {
            final Collection<Condition> conditions = JooqUtil.conditions(
                    Optional.of(META_M.ID.greaterOrEqual(minId)),
                    Optional.ofNullable(maxId)
                            .map(META_M.ID::lessOrEqual));

            return JooqUtil.contextResult(metaDbConnProvider, context -> {
                final var select = context
                        .select(SIMPLE_META_SELECT_FIELDS)
                        .from(META_M)
                        .straightJoin(META_TYPE_T).on(META_M.TYPE_ID.eq(META_TYPE_T.ID))
                        .straightJoin(META_FEED_F).on(META_M.FEED_ID.eq(META_FEED_F.ID))
                        .where(conditions)
                        .orderBy(META_M.ID)
                        .limit(batchSize);

                LOGGER.debug("Find SQL:\n{}", select);

                return select.fetch(this::mapToSimpleMeta);
            });
        }
    }

    @Override
    public Set<Long> exists(final Set<Long> ids) {
        if (NullSafe.isEmptyCollection(ids)) {
            return Collections.emptySet();
        } else {
            return JooqUtil.contextResult(metaDbConnProvider, context -> {
                final var select = context
                        .select(META_M.ID)
                        .from(META_M)
                        .where(META_M.ID.in(ids));

                LOGGER.debug("Find SQL:\n{}", select);

                return new HashSet<>(select.fetch(META_M.ID));
            });
        }
    }
}
