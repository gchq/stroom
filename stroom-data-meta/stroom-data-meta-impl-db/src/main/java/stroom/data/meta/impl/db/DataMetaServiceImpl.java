package stroom.data.meta.impl.db;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SQLDialect;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.api.AttributeMap;
import stroom.data.meta.api.EffectiveMetaDataCriteria;
import stroom.data.meta.api.FindDataCriteria;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataRow;
import stroom.data.meta.api.MetaDataSource;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.DataProperties;
import stroom.data.meta.api.DataSecurityFilter;
import stroom.data.meta.api.DataStatus;
import stroom.data.meta.impl.db.ExpressionMapper.TermHandler;
import stroom.data.meta.impl.db.MetaExpressionMapper.MetaTermHandler;
import stroom.data.meta.impl.db.DataImpl.Builder;
import stroom.data.meta.impl.db.stroom.tables.DataType;
import stroom.data.meta.impl.db.stroom.tables.MetaVal;
import stroom.data.meta.impl.db.stroom.tables.DataFeed;
import stroom.data.meta.impl.db.stroom.tables.DataProcessor;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.IdSet;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Sort.Direction;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.Security;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.jooq.impl.DSL.selectDistinct;
import static stroom.data.meta.impl.db.stroom.tables.MetaVal.META_VAL;
import static stroom.data.meta.impl.db.stroom.tables.Data.DATA;
import static stroom.data.meta.impl.db.stroom.tables.DataFeed.DATA_FEED;
import static stroom.data.meta.impl.db.stroom.tables.DataProcessor.DATA_PROCESSOR;
import static stroom.data.meta.impl.db.stroom.tables.DataType.DATA_TYPE;

@Singleton
class DataMetaServiceImpl implements DataMetaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataMetaServiceImpl.class);

    private final ConnectionProvider connectionProvider;
    private final FeedService feedService;
    private final DataTypeService dataTypeService;
    private final ProcessorService processorService;
    private final MetaKeyService metaKeyService;
    private final MetaValueService metaValueService;
    private final DataSecurityFilter dataSecurityFilter;
    private final Security security;

    private final stroom.data.meta.impl.db.stroom.tables.Data data = DATA.as("d");
    private final DataFeed dataFeed = DATA_FEED.as("f");
    private final DataType dataType = DATA_TYPE.as("dt");
    private final DataProcessor dataProcessor = DATA_PROCESSOR.as("dp");
    private final MetaVal metaVal = META_VAL.as("mv");

    private final ExpressionMapper expressionMapper;
    private final MetaExpressionMapper metaExpressionMapper;

    @Inject
    DataMetaServiceImpl(final ConnectionProvider connectionProvider,
                        final FeedService feedService,
                        final DataTypeService dataTypeService,
                        final ProcessorService processorService,
                        final MetaKeyService metaKeyService,
                        final MetaValueService metaValueService,
                        final DataSecurityFilter dataSecurityFilter,
                        final Security security) {
        this.connectionProvider = connectionProvider;
        this.feedService = feedService;
        this.dataTypeService = dataTypeService;
        this.processorService = processorService;
        this.metaKeyService = metaKeyService;
        this.metaValueService = metaValueService;
        this.dataSecurityFilter = dataSecurityFilter;
        this.security = security;

        // Standard fields.
        final Map<String, TermHandler<?>> termHandlers = new HashMap<>();
        termHandlers.put(MetaDataSource.STREAM_ID, new TermHandler<>(data.ID, Long::valueOf));
        termHandlers.put(MetaDataSource.FEED, new TermHandler<>(data.FEED_ID, feedService::getOrCreate));
        termHandlers.put(MetaDataSource.FEED_ID, new TermHandler<>(data.FEED_ID, Integer::valueOf));
        termHandlers.put(MetaDataSource.STREAM_TYPE, new TermHandler<>(data.TYPE_ID, dataTypeService::getOrCreate));
        termHandlers.put(MetaDataSource.PIPELINE, new TermHandler<>(dataProcessor.PIPELINE_UUID, value -> value));
        termHandlers.put(MetaDataSource.PARENT_STREAM_ID, new TermHandler<>(data.PARENT_ID, Long::valueOf));
        termHandlers.put(MetaDataSource.STREAM_TASK_ID, new TermHandler<>(data.TASK_ID, Long::valueOf));
        termHandlers.put(MetaDataSource.STREAM_PROCESSOR_ID, new TermHandler<>(data.PROCESSOR_ID, Integer::valueOf));
        termHandlers.put(MetaDataSource.STATUS, new TermHandler<>(data.STATUS, value -> DataStatusId.getPrimitiveValue(DataStatus.valueOf(value.toUpperCase()))));
        termHandlers.put(MetaDataSource.STATUS_TIME, new TermHandler<>(data.STATUS_TIME, DateUtil::parseNormalDateTimeString));
        termHandlers.put(MetaDataSource.CREATE_TIME, new TermHandler<>(data.CREATE_TIME, DateUtil::parseNormalDateTimeString));
        termHandlers.put(MetaDataSource.EFFECTIVE_TIME, new TermHandler<>(data.EFFECTIVE_TIME, DateUtil::parseNormalDateTimeString));
        expressionMapper = new ExpressionMapper(termHandlers);


        // Extended meta fields.
        final Map<String, MetaTermHandler> metaTermHandlers = new HashMap<>();

//        metaTermHandlers.put(StreamDataSource.NODE, createMetaTermHandler(StreamDataSource.NODE));
        addMetaTermHandler(metaTermHandlers, MetaDataSource.REC_READ);
        addMetaTermHandler(metaTermHandlers, MetaDataSource.REC_WRITE);
        addMetaTermHandler(metaTermHandlers, MetaDataSource.REC_INFO);
        addMetaTermHandler(metaTermHandlers, MetaDataSource.REC_WARN);
        addMetaTermHandler(metaTermHandlers, MetaDataSource.REC_ERROR);
        addMetaTermHandler(metaTermHandlers, MetaDataSource.REC_FATAL);
        addMetaTermHandler(metaTermHandlers, MetaDataSource.DURATION);
        addMetaTermHandler(metaTermHandlers, MetaDataSource.FILE_SIZE);
        addMetaTermHandler(metaTermHandlers, MetaDataSource.STREAM_SIZE);

        metaExpressionMapper = new MetaExpressionMapper(metaTermHandlers);
    }

    private void addMetaTermHandler(final Map<String, MetaTermHandler> metaTermHandlers, final String fieldName) {
        final Optional<Integer> optional = metaKeyService.getIdForName(fieldName);
        optional.ifPresent(keyId -> {
            final MetaTermHandler handler = new MetaTermHandler(metaVal.META_KEY_ID,
                    keyId,
                    new TermHandler<>(metaVal.VAL, Long::valueOf));
            metaTermHandlers.put(fieldName, handler);
        });
    }

    @Override
    public Data create(final DataProperties dataProperties) {
        final Integer feedId = feedService.getOrCreate(dataProperties.getFeedName());
        final Integer typeId = dataTypeService.getOrCreate(dataProperties.getTypeName());
        final Integer processorId = processorService.getOrCreate(dataProperties.getProcessorId(), dataProperties.getPipelineUuid());

        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final long id = create.insertInto(DATA,
                    DATA.CREATE_TIME,
                    DATA.EFFECTIVE_TIME,
                    DATA.PARENT_ID,
                    DATA.STATUS,
                    DATA.STATUS_TIME,
                    DATA.TASK_ID,
                    DATA.FEED_ID,
                    DATA.TYPE_ID,
                    DATA.PROCESSOR_ID)
                    .values(
                            dataProperties.getCreateMs(),
                            dataProperties.getEffectiveMs(),
                            dataProperties.getParentId(),
                            DataStatusId.LOCKED,
                            dataProperties.getStatusMs(),
                            dataProperties.getProcessorTaskId(),
                            feedId,
                            typeId,
                            processorId)
                    .returning(DATA.ID)
                    .fetchOne()
                    .getId();

            return new Builder().id(id)
                    .feedName(dataProperties.getFeedName())
                    .typeName(dataProperties.getTypeName())
                    .pipelineUuid(dataProperties.getPipelineUuid())
                    .parentDataId(dataProperties.getParentId())
                    .processTaskId(dataProperties.getProcessorTaskId())
                    .processorId(processorId)
                    .status(DataStatus.LOCKED)
                    .statusMs(dataProperties.getStatusMs())
                    .createMs(dataProperties.getCreateMs())
                    .effectiveMs(dataProperties.getEffectiveMs())
                    .build();

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Data getData(final long id) {
        return getData(id, false);
    }

    @Override
    public Data getData(final long id, final boolean anyStatus) {
        final Condition condition = getIdCondition(id, anyStatus, DocumentPermissionNames.READ);
        final List<Data> list = find(condition, 0, 1);
        if (list == null || list.size() == 0) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public Data updateStatus(final Data data, final DataStatus status) {
        Objects.requireNonNull(data, "Null data");

        final long now = System.currentTimeMillis();
        final int result = updateStatus(data.getId(), status, now, DocumentPermissionNames.UPDATE);
        if (result > 0) {
            return new Builder(data).status(status).statusMs(now).build();
        } else {
            return null;
        }
    }

    private int updateStatus(final long id, final DataStatus status, final long statusTime, final String permission) {
        final Condition condition = getIdCondition(id, true, permission);

        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .update(data)
                    .set(data.STATUS, DataStatusId.getPrimitiveValue(status))
                    .set(data.STATUS_TIME, statusTime)
                    .where(condition)
                    .execute();
//                    .returning(data.ID,
//                            dataFeed.NAME,
//                            dataType.NAME,
//                            dataProcessor.PIPELINE_UUID,
//                            data.PARNT_STRM_ID,
//                            data.STRM_TASK_ID,
//                            data.FK_STRM_PROC_ID,
//                            data.STAT,
//                            data.STAT_MS,
//                            data.CRT_MS,
//                            data.EFFECT_MS)
//                    .fetchOptional()
//                    .map(r -> new Builder().id(data.ID.get(r))
//                            .feedName(dataFeed.NAME.get(r))
//                            .typeName(dataType.NAME.get(r))
//                            .pipelineUuid(dataProcessor.PIPELINE_UUID.get(r))
//                            .parentDataId(data.PARNT_STRM_ID.get(r))
//                            .processTaskId(data.STRM_TASK_ID.get(r))
//                            .processorId(data.FK_STRM_PROC_ID.get(r))
//                            .status(StreamStatusId.getStatus(data.STAT.get(r)))
//                            .statusMs(data.STAT_MS.get(r))
//                            .createMs(data.CRT_MS.get(r))
//                            .effectiveMs(data.EFFECT_MS.get(r))
//                            .build())
//                    .orElse(null);

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public int updateStatus(final FindDataCriteria criteria, final DataStatus status) {
        // Decide which permission is needed for this update as logical deletes require delete permissions.
        String permission = DocumentPermissionNames.UPDATE;
        if (DataStatus.DELETED.equals(status)) {
            permission = DocumentPermissionNames.DELETE;
        }

        final Condition condition = createCondition(criteria, permission);

        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .update(data)
                    .set(data.STATUS, DataStatusId.getPrimitiveValue(status))
                    .set(data.STATUS_TIME, System.currentTimeMillis())
                    .where(condition)
                    .execute();

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void addAttributes(final Data data, final AttributeMap attributes) {
        metaValueService.addAttributes(data, attributes);
    }

    @Override
    public int delete(final long id) {
        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> doLogicalDelete(id, true));
    }

    @Override
    public int delete(final long id, final boolean lockCheck) {
        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> doLogicalDelete(id, lockCheck));
    }

    private int doLogicalDelete(final long id, final boolean lockCheck) {
        if (lockCheck) {
            final Data data = getData(id, true);

            // Don't bother to try and set the status of deleted data to be deleted.
            if (DataStatus.DELETED.equals(data.getStatus())) {
                return 0;
            }

            // Don't delete if the data is not unlocked and we are checking for unlocked.
            if (!DataStatus.UNLOCKED.equals(data.getStatus())) {
                return 0;
            }
        }

        // Ensure the user has permission to delete this data.
        final long now = System.currentTimeMillis();
        return updateStatus(id, DataStatus.DELETED, now, DocumentPermissionNames.DELETE);
    }

    private SelectConditionStep<Record1<Long>> getMetaCondition(final ExpressionOperator expression) {
        if (expression == null) {
            return null;
        }

        final Condition condition = metaExpressionMapper.apply(expression);
        if (condition == null) {
            return null;
        }

        return selectDistinct(metaVal.DATA_ID)
                .from(metaVal)
                .where(condition);
    }

    @Override
    public BaseResultList<Data> find(final FindDataCriteria criteria) {
        final IdSet idSet = criteria.getSelectedIdSet();
        // If for some reason we have been asked to match nothing then return nothing.
        if (idSet != null && idSet.getMatchNull() != null && idSet.getMatchNull()) {
            return BaseResultList.createPageResultList(Collections.emptyList(), criteria.getPageRequest(), null);
        }

        final Condition condition = createCondition(criteria, DocumentPermissionNames.READ);

        int offset = 0;
        int numberOfRows = 1000000;

        PageRequest pageRequest = criteria.getPageRequest();
        if (pageRequest != null) {
            offset = pageRequest.getOffset().intValue();
            numberOfRows = pageRequest.getLength();
        }

        final List<Data> results = find(condition, offset, numberOfRows);
        return BaseResultList.createPageResultList(results, criteria.getPageRequest(), null);
    }

    private List<Data> find(final Condition condition, final int offset, final int numberOfRows) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .select(
                            data.ID,
                            dataFeed.NAME,
                            dataType.NAME,
                            dataProcessor.PIPELINE_UUID,
                            data.PARENT_ID,
                            data.TASK_ID,
                            data.PROCESSOR_ID,
                            data.STATUS,
                            data.STATUS_TIME,
                            data.CREATE_TIME,
                            data.EFFECTIVE_TIME
                    )
                    .from(data)
                    .join(dataFeed).on(data.FEED_ID.eq(dataFeed.ID))
                    .join(dataType).on(data.TYPE_ID.eq(dataType.ID))
                    .leftOuterJoin(dataProcessor).on(data.PROCESSOR_ID.eq(dataProcessor.ID))
                    .where(condition)
                    .orderBy(data.ID)
                    .limit(offset, numberOfRows)
                    .fetch()
                    .map(r -> new Builder().id(r.component1())
                            .feedName(r.component2())
                            .typeName(r.component3())
                            .pipelineUuid(r.component4())
                            .parentDataId(r.component5())
                            .processTaskId(r.component6())
                            .processorId(r.component7())
                            .status(DataStatusId.getStatus(r.component8()))
                            .statusMs(r.component9())
                            .createMs(r.component10())
                            .effectiveMs(r.component11())
                            .build());

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

//    @Override
//    public int updateStatus(final FindStreamCriteria criteria) {
//        return updateStatus(criteria, StreamStatus.DELETED);
//
////        final ExpressionOperator secureExpression = addPermissionConstraints(criteria.getExpression(), DocumentPermissionNames.DELETE);
////        final Condition condition = expressionMapper.apply(secureExpression);
////
////        try (final Connection connection = connectionProvider.getConnection()) {
////            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
////
////            return create
////                    .deleteFrom(data)
////                    .where(condition)
////                    .execute();
////
////        } catch (final SQLException e) {
////            LOGGER.error(e.getMessage(), e);
////            throw new RuntimeException(e.getMessage(), e);
////        }
//    }


    public int delete(final FindDataCriteria criteria) {
        final Condition condition = createCondition(criteria, DocumentPermissionNames.DELETE);

        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .deleteFrom(data)
                    .where(condition)
                    .execute();

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Set<Data> findEffectiveData(final EffectiveMetaDataCriteria criteria) {
        // See if we can find a data that exists before the earliest specified time.
        final Optional<Long> optionalId = getMaxEffectiveDataIdBeforePeriod(criteria);

        final Set<Data> set = new HashSet<>();
        if (optionalId.isPresent()) {
            // Get the data that occurs just before or ast the start of the period.
            final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                    .addTerm(MetaDataSource.STREAM_ID, ExpressionTerm.Condition.EQUALS, String.valueOf(optionalId.get()))
                    .build();
            // There is no need to apply security here are is has been applied when finding the data id above.
            final Condition condition = expressionMapper.apply(expression);
            set.addAll(find(condition, 0, 1000));
        }

        // Now add all data that occurs within the requested period.
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaDataSource.EFFECTIVE_TIME, ExpressionTerm.Condition.GREATER_THAN, DateUtil.createNormalDateTimeString(criteria.getEffectivePeriod().getFromMs()))
                .addTerm(MetaDataSource.EFFECTIVE_TIME, ExpressionTerm.Condition.LESS_THAN, DateUtil.createNormalDateTimeString(criteria.getEffectivePeriod().getToMs()))
                .addTerm(MetaDataSource.FEED, ExpressionTerm.Condition.EQUALS, criteria.getFeed())
                .addTerm(MetaDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, criteria.getType())
                .addTerm(MetaDataSource.STATUS, ExpressionTerm.Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                .build();

        final ExpressionOperator secureExpression = addPermissionConstraints(expression, DocumentPermissionNames.READ);
        final Condition condition = expressionMapper.apply(secureExpression);
        set.addAll(find(condition, 0, 1000));

        return set;
    }

    private Optional<Long> getMaxEffectiveDataIdBeforePeriod(final EffectiveMetaDataCriteria criteria) {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaDataSource.EFFECTIVE_TIME, ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO, DateUtil.createNormalDateTimeString(criteria.getEffectivePeriod().getFromMs()))
                .addTerm(MetaDataSource.FEED, ExpressionTerm.Condition.EQUALS, criteria.getFeed())
                .addTerm(MetaDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, criteria.getType())
                .addTerm(MetaDataSource.STATUS, ExpressionTerm.Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                .build();

        final ExpressionOperator secureExpression = addPermissionConstraints(expression, DocumentPermissionNames.READ);
        final Condition condition = expressionMapper.apply(secureExpression);

        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .select(data.ID.max())
                    .from(data)
                    .where(condition)
                    .fetchOptional()
                    .map(Record1::value1);

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getFeeds() {
        return feedService.list();
    }

    @Override
    public List<String> getTypes() {
        return dataTypeService.list();
    }

    @Override
    public int getLockCount() {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectCount()
                    .from(data)
                    .where(data.STATUS.eq(DataStatusId.LOCKED))
                    .fetchOptional()
                    .map(Record1::value1)
                    .orElse(0);

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

//    @Override
//    public Period getCreatePeriod() {
//        try (final Connection connection = connectionProvider.getConnection()) {
//            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
//            return context
//                    .select(data.CRT_MS.min(), data.CRT_MS.max())
//                    .from(data)
//                    .fetchOptional()
//                    .map(r -> new Period(r.value1(), r.value2()))
//                    .orElse(null);
//
//        } catch (final SQLException e) {
//            LOGGER.error(e.getMessage(), e);
//            throw new RuntimeException(e.getMessage(), e);
//        }
//    }

    @Override
    public BaseResultList<DataRow> findRows(final FindDataCriteria criteria) {
        return security.useAsReadResult(() -> {
            // Cache Call


            final FindDataCriteria findDataCriteria = new FindDataCriteria();
            findDataCriteria.copyFrom(criteria);
            findDataCriteria.setSort(MetaDataSource.CREATE_TIME, Direction.DESCENDING, false);

//            final boolean includeRelations = findDataCriteria.getFetchSet().contains(StreamEntity.ENTITY_TYPE);
//            findDataCriteria.setFetchSet(new HashSet<>());
//            if (includeRelations) {
//                findDataCriteria.getFetchSet().add(StreamEntity.ENTITY_TYPE);
//            }
//            findDataCriteria.getFetchSet().add(StreamTypeEntity.ENTITY_TYPE);
            // Share the page criteria
            final BaseResultList<Data> list = find(findDataCriteria);

            if (list.size() > 0) {
//                // We need to decorate data with retention rules as a processing user.
//                final List<StreamDataRow> result = security.asProcessingUserResult(() -> {
//                    // Create a data retention rule decorator for adding data retention information to returned data attribute maps.
//                    List<DataRetentionRule> rules = Collections.emptyList();
//
//                    final DataRetentionService dataRetentionService = dataRetentionServiceProvider.get();
//                    if (dataRetentionService != null) {
//                        final DataRetentionPolicy dataRetentionPolicy = dataRetentionService.load();
//                        if (dataRetentionPolicy != null && dataRetentionPolicy.getRules() != null) {
//                            rules = dataRetentionPolicy.getRules();
//                        }
//                        final AttributeMapRetentionRuleDecorator ruleDecorator = new AttributeMapRetentionRuleDecorator(dictionaryStore, rules);

                // Query the database for the attribute values
//                        if (criteria.isUseCache()) {
                LOGGER.info("Loading attribute map from DB");
                final List<DataRow> result = metaValueService.decorateDataWithAttributes(list);
//                        } else {
//                            LOGGER.info("Loading attribute map from filesystem");
//                            loadAttributeMapFromFileSystem(criteria, result, result, ruleDecorator);
//                        }
//                    }
//                });

                return new BaseResultList<>(result, list.getPageResponse().getOffset(),
                        list.getPageResponse().getTotal(), list.getPageResponse().isExact());
            }

            return new BaseResultList<>(Collections.emptyList(), list.getPageResponse().getOffset(),
                    list.getPageResponse().getTotal(), list.getPageResponse().isExact());
        });
    }

    @Override
    public List<DataRow> findRelatedData(final long id, final boolean anyStatus) {
        // Get the starting row.
        final FindDataCriteria findDataCriteria = new FindDataCriteria(getIdExpression(id, anyStatus));
        BaseResultList<Data> rows = find(findDataCriteria);
        final List<Data> result = new ArrayList<>(rows);

        if (rows.size() > 0) {
            Data row = rows.getFirst();
            addChildren(row, anyStatus, result);
            addParents(row, anyStatus, result);
        }

        result.sort(Comparator.comparing(Data::getId));

        return metaValueService.decorateDataWithAttributes(result);
    }

    private void addChildren(final Data parent, final boolean anyStatus, final List<Data> result) {
        final BaseResultList<Data> children = find(new FindDataCriteria(getParentIdExpression(parent.getId(), anyStatus)));
        children.forEach(child -> {
            result.add(child);
            addChildren(child, anyStatus, result);
        });
    }

    private void addParents(final Data child, final boolean anyStatus, final List<Data> result) {
        if (child.getParentDataId() != null) {
            final BaseResultList<Data> parents = find(new FindDataCriteria(getIdExpression(child.getParentDataId(), anyStatus)));
            if (parents != null && parents.size() > 0) {
                parents.forEach(parent -> {
                    result.add(parent);
                    addParents(parent, anyStatus, result);
                });
            } else {
                // Add a dummy parent data as we don't seem to be able to get the real parent.
                // This might be because it is deleted or the user does not have access permissions.
                final Data data = new DataImpl.Builder()
                        .id(child.getParentDataId())
                        .build();
                result.add(data);
            }
        }
    }

    void clear() {
        deleteAll();
    }

    int deleteAll() {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .delete(DATA)
                    .execute();
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Condition getIdCondition(final long id, final boolean anyStatus, final String permission) {
        final ExpressionOperator secureExpression = addPermissionConstraints(getIdExpression(id, anyStatus), permission);
        return expressionMapper.apply(secureExpression);
    }

    private ExpressionOperator getIdExpression(final long id, final boolean anyStatus) {
        if (anyStatus) {
            return new ExpressionOperator.Builder(Op.AND)
                    .addTerm(MetaDataSource.STREAM_ID, ExpressionTerm.Condition.EQUALS, String.valueOf(id))
                    .build();
        }

        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaDataSource.STREAM_ID, ExpressionTerm.Condition.EQUALS, String.valueOf(id))
                .addTerm(MetaDataSource.STATUS, ExpressionTerm.Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                .build();
    }

    private ExpressionOperator getParentIdExpression(final long id, final boolean anyStatus) {
        if (anyStatus) {
            return new ExpressionOperator.Builder(Op.AND)
                    .addTerm(MetaDataSource.PARENT_STREAM_ID, ExpressionTerm.Condition.EQUALS, String.valueOf(id))
                    .build();
        }

        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaDataSource.PARENT_STREAM_ID, ExpressionTerm.Condition.EQUALS, String.valueOf(id))
                .addTerm(MetaDataSource.STATUS, ExpressionTerm.Condition.EQUALS, DataStatus.UNLOCKED.getDisplayValue())
                .build();
    }

    private ExpressionOperator addPermissionConstraints(final ExpressionOperator expression, final String permission) {
        final ExpressionOperator filter = dataSecurityFilter.getExpression(permission).orElse(null);

        if (expression == null) {
            return filter;
        }

        if (filter != null) {
            final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);
            builder.addOperator(expression);
            builder.addOperator(filter);
            return builder.build();
        }

        return expression;
    }

    private Condition createCondition(final FindDataCriteria criteria, final String permission) {
        Condition condition;

        IdSet idSet = null;
        ExpressionOperator expression = null;

        if (criteria != null) {
            idSet = criteria.getSelectedIdSet();
            expression = criteria.getExpression();
        }

        final ExpressionOperator secureExpression = addPermissionConstraints(expression, permission);
        condition = expressionMapper.apply(secureExpression);

        // If we aren't being asked to match everything then add constraints to the expression.
        if (idSet != null && (idSet.getMatchAll() == null || !idSet.getMatchAll())) {
            condition = and(condition, data.ID.in(idSet.getSet()));
        }

        // Get additional selection criteria based on meta data attributes;
        final SelectConditionStep<Record1<Long>> metaConditionStep = getMetaCondition(secureExpression);
        if (metaConditionStep != null) {
            condition = and(condition, data.ID.in(metaConditionStep));
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
}
