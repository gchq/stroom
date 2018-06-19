package stroom.streamstore.meta.impl.db;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SQLDialect;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.IdSet;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Period;
import stroom.entity.shared.Sort.Direction;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.security.Security;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.streamstore.meta.api.EffectiveMetaDataCriteria;
import stroom.streamstore.meta.api.FindStreamCriteria;
import stroom.streamstore.meta.api.Stream;
import stroom.streamstore.meta.api.StreamMetaService;
import stroom.streamstore.meta.api.StreamProperties;
import stroom.streamstore.meta.api.StreamSecurityFilter;
import stroom.streamstore.meta.api.StreamStatus;
import stroom.streamstore.meta.impl.db.ExpressionMapper.TermHandler;
import stroom.streamstore.meta.impl.db.MetaExpressionMapper.MetaTermHandler;
import stroom.streamstore.meta.impl.db.StreamImpl.Builder;
import stroom.streamstore.meta.impl.db.stroom.tables.MetaNumericValue;
import stroom.streamstore.meta.impl.db.stroom.tables.StreamFeed;
import stroom.streamstore.meta.impl.db.stroom.tables.StreamProcessor;
import stroom.streamstore.meta.impl.db.stroom.tables.StreamType;
import stroom.streamstore.meta.impl.db.stroom.tables.Strm;
import stroom.streamstore.meta.api.StreamDataRow;
import stroom.streamstore.meta.api.StreamDataSource;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.jooq.impl.DSL.selectDistinct;
import static stroom.streamstore.meta.impl.db.stroom.tables.MetaNumericValue.META_NUMERIC_VALUE;
import static stroom.streamstore.meta.impl.db.stroom.tables.StreamFeed.STREAM_FEED;
import static stroom.streamstore.meta.impl.db.stroom.tables.StreamProcessor.STREAM_PROCESSOR;
import static stroom.streamstore.meta.impl.db.stroom.tables.StreamType.STREAM_TYPE;
import static stroom.streamstore.meta.impl.db.stroom.tables.Strm.STRM;

@Singleton
class StreamMetaServiceImpl implements StreamMetaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamMetaServiceImpl.class);

    private final DataSource dataSource;
    private final FeedService feedService;
    private final StreamTypeService streamTypeService;
    private final ProcessorService processorService;
    private final MetaKeyService metaKeyService;
    private final MetaValueService metaValueService;
    private final StreamSecurityFilter streamSecurityFilter;
    private final Security security;

    private final Strm strm = STRM.as("s");
    private final StreamFeed strmFeed = STREAM_FEED.as("f");
    private final StreamType strmType = STREAM_TYPE.as("st");
    private final StreamProcessor strmProcessor = STREAM_PROCESSOR.as("sp");
    private final MetaNumericValue metaNumericValue = META_NUMERIC_VALUE.as("mv");

    private final ExpressionMapper expressionMapper;
    private final MetaExpressionMapper metaExpressionMapper;

    @Inject
    StreamMetaServiceImpl(final StreamMetaDataSource dataSource,
                          final FeedService feedService,
                          final StreamTypeService streamTypeService,
                          final ProcessorService processorService,
                          final MetaKeyService metaKeyService,
                          final MetaValueService metaValueService,
                          final StreamSecurityFilter streamSecurityFilter,
                          final Security security) {
        this.dataSource = dataSource;
        this.feedService = feedService;
        this.streamTypeService = streamTypeService;
        this.processorService = processorService;
        this.metaKeyService = metaKeyService;
        this.metaValueService = metaValueService;
        this.streamSecurityFilter = streamSecurityFilter;
        this.security = security;

        // Standard fields.
        final Map<String, TermHandler<?>> termHandlers = new HashMap<>();
        termHandlers.put(StreamDataSource.STREAM_ID, new TermHandler<>(strm.ID, Long::valueOf));
        termHandlers.put(StreamDataSource.FEED, new TermHandler<>(strm.FK_FD_ID, feedService::getOrCreate));
        termHandlers.put(StreamDataSource.FEED_ID, new TermHandler<>(strm.FK_FD_ID, Integer::valueOf));
        termHandlers.put(StreamDataSource.STREAM_TYPE, new TermHandler<>(strm.FK_STRM_TP_ID, streamTypeService::getOrCreate));
        termHandlers.put(StreamDataSource.PIPELINE, new TermHandler<>(strmProcessor.PIPELINE_UUID, value -> value));
        termHandlers.put(StreamDataSource.PARENT_STREAM_ID, new TermHandler<>(strm.PARNT_STRM_ID, Long::valueOf));
        termHandlers.put(StreamDataSource.STREAM_TASK_ID, new TermHandler<>(strm.STRM_TASK_ID, Long::valueOf));
        termHandlers.put(StreamDataSource.STREAM_PROCESSOR_ID, new TermHandler<>(strm.FK_STRM_PROC_ID, Integer::valueOf));
        termHandlers.put(StreamDataSource.STATUS, new TermHandler<>(strm.STAT, value -> StreamStatusId.getPrimitiveValue(StreamStatus.valueOf(value.toUpperCase()))));
        termHandlers.put(StreamDataSource.STATUS_TIME, new TermHandler<>(strm.STAT_MS, DateUtil::parseNormalDateTimeString));
        termHandlers.put(StreamDataSource.CREATE_TIME, new TermHandler<>(strm.CRT_MS, DateUtil::parseNormalDateTimeString));
        termHandlers.put(StreamDataSource.EFFECTIVE_TIME, new TermHandler<>(strm.EFFECT_MS, DateUtil::parseNormalDateTimeString));
        expressionMapper = new ExpressionMapper(termHandlers);


        // Extended meta fields.
        final Map<String, MetaTermHandler> metaTermHandlers = new HashMap<>();

//        metaTermHandlers.put(StreamDataSource.NODE, createMetaTermHandler(StreamDataSource.NODE));
        addMetaTermHandler(metaTermHandlers, StreamDataSource.REC_READ);
        addMetaTermHandler(metaTermHandlers, StreamDataSource.REC_WRITE);
        addMetaTermHandler(metaTermHandlers, StreamDataSource.REC_INFO);
        addMetaTermHandler(metaTermHandlers, StreamDataSource.REC_WARN);
        addMetaTermHandler(metaTermHandlers, StreamDataSource.REC_ERROR);
        addMetaTermHandler(metaTermHandlers, StreamDataSource.REC_FATAL);
        addMetaTermHandler(metaTermHandlers, StreamDataSource.DURATION);
        addMetaTermHandler(metaTermHandlers, StreamDataSource.FILE_SIZE);
        addMetaTermHandler(metaTermHandlers, StreamDataSource.STREAM_SIZE);

        metaExpressionMapper = new MetaExpressionMapper(metaTermHandlers);
    }

    private void addMetaTermHandler(final Map<String, MetaTermHandler> metaTermHandlers, final String fieldName) {
        final Optional<Integer> optional = metaKeyService.getIdForName(fieldName);
        optional.ifPresent(keyId -> {
            final MetaTermHandler handler = new MetaTermHandler(metaNumericValue.META_KEY_ID,
                    keyId,
                    new TermHandler<>(metaNumericValue.VAL, Long::valueOf));
            metaTermHandlers.put(fieldName, handler);
        });
    }

    @Override
    public Stream createStream(final StreamProperties streamProperties) {
        final Integer streamTypeId = streamTypeService.getOrCreate(streamProperties.getStreamTypeName());
        final Integer feedId = feedService.getOrCreate(streamProperties.getFeedName());
        final Integer processorId = processorService.getOrCreate(streamProperties.getStreamProcessorId(), streamProperties.getPipelineUuid());

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            final long id = context.insertInto(STRM,
                    STRM.VER,
                    STRM.CRT_MS,
                    STRM.EFFECT_MS,
                    STRM.PARNT_STRM_ID,
                    STRM.STAT,
                    STRM.STAT_MS,
                    STRM.STRM_TASK_ID,
                    STRM.FK_FD_ID,
                    STRM.FK_STRM_TP_ID,
                    STRM.FK_STRM_PROC_ID)
                    .values(
                            (byte) 1,
                            streamProperties.getCreateMs(),
                            streamProperties.getEffectiveMs(),
                            streamProperties.getParentId(),
                            StreamStatusId.LOCKED,
                            streamProperties.getStatusMs(),
                            streamProperties.getStreamTaskId(),
                            feedId,
                            streamTypeId,
                            processorId)
                    .returning(STRM.ID)
                    .fetchOne()
                    .getId();

            return new Builder().id(id)
                    .feedName(streamProperties.getFeedName())
                    .streamTypeName(streamProperties.getStreamTypeName())
                    .pipelineUuid(streamProperties.getPipelineUuid())
                    .parentStreamId(streamProperties.getParentId())
                    .streamTaskId(streamProperties.getStreamTaskId())
                    .streamProcessorId(processorId)
                    .status(StreamStatus.LOCKED)
                    .statusMs(streamProperties.getStatusMs())
                    .createMs(streamProperties.getCreateMs())
                    .effectiveMs(streamProperties.getEffectiveMs())
                    .build();

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Stream getStream(final long streamId) {
        return getStream(streamId, false);
    }

    @Override
    public Stream getStream(final long streamId, final boolean anyStatus) {
        final Condition condition = getIdCondition(streamId, anyStatus, DocumentPermissionNames.READ);
        final List<Stream> list = find(condition, 0, 1);
        if (list == null || list.size() == 0) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public boolean canReadStream(final long streamId) {
        return getStream(streamId) != null;
    }

    @Override
    public Stream updateStatus(final long streamId, final StreamStatus streamStatus) {
        return updateStatus(streamId, streamStatus, DocumentPermissionNames.UPDATE);
    }

    private Stream updateStatus(final long streamId, final StreamStatus streamStatus, final String permission) {
        final Condition condition = getIdCondition(streamId, true, permission);

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            return context
                    .update(strm)
                    .set(strm.STAT, StreamStatusId.getPrimitiveValue(streamStatus))
                    .set(strm.STAT_MS, System.currentTimeMillis())
                    .where(condition)
                    .returning(strm.ID,
                            strmFeed.NAME,
                            strmType.NAME,
                            strmProcessor.PIPELINE_UUID,
                            strm.PARNT_STRM_ID,
                            strm.STRM_TASK_ID,
                            strm.FK_STRM_PROC_ID,
                            strm.STAT,
                            strm.STAT_MS,
                            strm.CRT_MS,
                            strm.EFFECT_MS)
                    .fetchOne()
                    .map(r -> new Builder().id(strm.ID.get(r))
                            .feedName(strmFeed.NAME.get(r))
                            .streamTypeName(strmType.NAME.get(r))
                            .pipelineUuid(strmProcessor.PIPELINE_UUID.get(r))
                            .parentStreamId(strm.PARNT_STRM_ID.get(r))
                            .streamTaskId(strm.STRM_TASK_ID.get(r))
                            .streamProcessorId(strm.FK_STRM_PROC_ID.get(r))
                            .status(StreamStatusId.getStreamStatus(strm.STAT.get(r)))
                            .statusMs(strm.STAT_MS.get(r))
                            .createMs(strm.CRT_MS.get(r))
                            .effectiveMs(strm.EFFECT_MS.get(r))
                            .build());

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void addAttributes(final Stream stream, final Map<String, String> attributes) {
        metaValueService.addAttributes(stream, attributes);
    }

    @Override
    public int deleteStream(final long streamId) {
        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> doLogicalDeleteStream(streamId, true));
    }

    @Override
    public int deleteStream(final long streamId, final boolean lockCheck) {
        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> doLogicalDeleteStream(streamId, lockCheck));
    }

    private int doLogicalDeleteStream(final long streamId, final boolean lockCheck) {
        if (lockCheck) {
            final Stream stream = getStream(streamId, true);

            // Don't bother to try and set the status of deleted streams to deleted.
            if (StreamStatus.DELETED.equals(stream.getStatus())) {
                return 0;
            }

            // Don't delete if the stream is not unlocked and we are checking for unlocked.
            if (!StreamStatus.UNLOCKED.equals(stream.getStatus())) {
                return 0;
            }
        }

        // Ensure the user has permission to delete this stream.
        final Stream stream = updateStatus(streamId, StreamStatus.DELETED, DocumentPermissionNames.DELETE);
        if (stream != null) {
            return 1;
        }

        return 0;
    }

    private SelectConditionStep<Record1<Long>> getMetaCondition(final ExpressionOperator expression) {
        if (expression == null) {
            return null;
        }

        final Condition condition = metaExpressionMapper.apply(expression);
        if (condition == null) {
            return null;
        }

        return selectDistinct(metaNumericValue.STREAM_ID)
                .from(metaNumericValue)
                .where(condition);
    }

    @Override
    public BaseResultList<Stream> find(final FindStreamCriteria criteria) {
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

        final List<Stream> results = find(condition, offset, numberOfRows);
        return BaseResultList.createPageResultList(results, criteria.getPageRequest(), null);
    }

    private List<Stream> find(final Condition condition, final int offset, final int numberOfRows) {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            return context
                    .select(
                            strm.ID,
                            strmFeed.NAME,
                            strmType.NAME,
                            strmProcessor.PIPELINE_UUID,
                            strm.PARNT_STRM_ID,
                            strm.STRM_TASK_ID,
                            strm.FK_STRM_PROC_ID,
                            strm.STAT,
                            strm.STAT_MS,
                            strm.CRT_MS,
                            strm.EFFECT_MS
                    )
                    .from(strm)
                    .join(strmFeed).on(strm.FK_FD_ID.eq(strmFeed.ID))
                    .join(strmType).on(strm.FK_STRM_TP_ID.eq(strmType.ID))
                    .leftOuterJoin(strmProcessor).on(strm.FK_STRM_PROC_ID.eq(strmProcessor.ID))
                    .where(condition)
                    .orderBy(strm.ID)
                    .limit(offset, numberOfRows)
                    .fetch()
                    .map(r -> new Builder().id(r.component1())
                            .feedName(r.component2())
                            .streamTypeName(r.component3())
                            .pipelineUuid(r.component4())
                            .parentStreamId(r.component5())
                            .streamTaskId(r.component6())
                            .streamProcessorId(r.component7())
                            .status(StreamStatusId.getStreamStatus(r.component8()))
                            .statusMs(r.component9())
                            .createMs(r.component10())
                            .effectiveMs(r.component11())
                            .build());

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public int findDelete(final FindStreamCriteria criteria) {
        return updateStatus(criteria, StreamStatus.DELETED);

//        final ExpressionOperator secureExpression = addPermissionConstraints(criteria.getExpression(), DocumentPermissionNames.DELETE);
//        final Condition condition = expressionMapper.apply(secureExpression);
//
//        try (final Connection connection = dataSource.getConnection()) {
//            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
//
//            return create
//                    .deleteFrom(strm)
//                    .where(condition)
//                    .execute();
//
//        } catch (final SQLException e) {
//            LOGGER.error(e.getMessage(), e);
//            throw new RuntimeException(e.getMessage(), e);
//        }
    }

    private int updateStatus(final FindStreamCriteria criteria, final StreamStatus streamStatus) {
        // Decide which permission is needed for this update as logical deletes require delete permissions.
        String permission = DocumentPermissionNames.UPDATE;
        if (StreamStatus.DELETED.equals(streamStatus)) {
            permission = DocumentPermissionNames.DELETE;
        }

        final Condition condition = createCondition(criteria, permission);

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            return context
                    .update(strm)
                    .set(strm.STAT, StreamStatusId.getPrimitiveValue(streamStatus))
                    .set(strm.STAT_MS, System.currentTimeMillis())
                    .where(condition)
                    .execute();

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public int delete(final FindStreamCriteria criteria) {
        final Condition condition = createCondition(criteria, DocumentPermissionNames.DELETE);

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            return context
                    .deleteFrom(strm)
                    .where(condition)
                    .execute();

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<Stream> findEffectiveStream(final EffectiveMetaDataCriteria criteria) {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(StreamDataSource.EFFECTIVE_TIME, ExpressionTerm.Condition.GREATER_THAN_OR_EQUAL_TO, String.valueOf(criteria.getEffectivePeriod().getFromMs()))
                .addTerm(StreamDataSource.EFFECTIVE_TIME, ExpressionTerm.Condition.LESS_THAN, String.valueOf(criteria.getEffectivePeriod().getToMs()))
                .addTerm(StreamDataSource.FEED, ExpressionTerm.Condition.EQUALS, criteria.getFeed())
                .addTerm(StreamDataSource.STREAM_TYPE, ExpressionTerm.Condition.EQUALS, criteria.getStreamType())
                .build();
        final ExpressionOperator secureExpression = addPermissionConstraints(expression, DocumentPermissionNames.READ);
        final Condition condition = expressionMapper.apply(secureExpression);
        return find(condition, 0, 1000);
    }

    @Override
    public List<String> getFeeds() {
        return feedService.list();
    }

    @Override
    public List<String> getStreamTypes() {
        return streamTypeService.list();
    }

    @Override
    public int getLockCount() {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            return context
                    .selectCount()
                    .from(strm)
                    .where(strm.STAT.eq(StreamStatusId.LOCKED))
                    .fetchOptional()
                    .map(Record1::value1)
                    .orElse(0);

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Period getCreatePeriod() {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            return context
                    .select(strm.CRT_MS.min(), strm.CRT_MS.max())
                    .from(strm)
                    .fetchOptional()
                    .map(r -> new Period(r.value1(), r.value2()))
                    .orElse(null);

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public BaseResultList<StreamDataRow> findRows(final FindStreamCriteria criteria) {
        return security.useAsReadResult(() -> {
            // Cache Call


            final FindStreamCriteria streamCriteria = new FindStreamCriteria();
            streamCriteria.copyFrom(criteria);
            streamCriteria.setSort(StreamDataSource.CREATE_TIME, Direction.DESCENDING, false);

//            final boolean includeRelations = streamCriteria.getFetchSet().contains(StreamEntity.ENTITY_TYPE);
//            streamCriteria.setFetchSet(new HashSet<>());
//            if (includeRelations) {
//                streamCriteria.getFetchSet().add(StreamEntity.ENTITY_TYPE);
//            }
//            streamCriteria.getFetchSet().add(StreamTypeEntity.ENTITY_TYPE);
            // Share the page criteria
            final BaseResultList<Stream> streamList = find(streamCriteria);

            if (streamList.size() > 0) {
//                // We need to decorate streams with retention rules as a processing user.
//                final List<StreamDataRow> result = security.asProcessingUserResult(() -> {
//                    // Create a data retention rule decorator for adding data retention information to returned stream attribute maps.
//                    List<DataRetentionRule> rules = Collections.emptyList();
//
//                    final DataRetentionService dataRetentionService = dataRetentionServiceProvider.get();
//                    if (dataRetentionService != null) {
//                        final DataRetentionPolicy dataRetentionPolicy = dataRetentionService.load();
//                        if (dataRetentionPolicy != null && dataRetentionPolicy.getRules() != null) {
//                            rules = dataRetentionPolicy.getRules();
//                        }
//                        final MetaMapRetentionRuleDecorator ruleDecorator = new MetaMapRetentionRuleDecorator(dictionaryStore, rules);

                // Query the database for the attribute values
//                        if (criteria.isUseCache()) {
                LOGGER.info("Loading attribute map from DB");
                final List<StreamDataRow> result = metaValueService.decorateStreamsWithAttributes(streamList);
//                        } else {
//                            LOGGER.info("Loading attribute map from filesystem");
//                            loadAttributeMapFromFileSystem(criteria, streamMDList, streamList, ruleDecorator);
//                        }
//                    }
//                });

                return new BaseResultList<>(result, streamList.getPageResponse().getOffset(),
                        streamList.getPageResponse().getTotal(), streamList.getPageResponse().isExact());
            }

            return new BaseResultList<>(Collections.emptyList(), streamList.getPageResponse().getOffset(),
                    streamList.getPageResponse().getTotal(), streamList.getPageResponse().isExact());
        });
    }

    int deleteAll() {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            return context
                    .delete(STRM)
                    .execute();
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Condition getIdCondition(final long streamId, final boolean anyStatus, final String permission) {
        final ExpressionOperator secureExpression = addPermissionConstraints(getIdExpression(streamId, anyStatus), permission);
        return expressionMapper.apply(secureExpression);
    }

    private ExpressionOperator getIdExpression(final long streamId, final boolean anyStatus) {
        if (anyStatus) {
            return new ExpressionOperator.Builder(Op.AND)
                    .addTerm(StreamDataSource.STREAM_ID, ExpressionTerm.Condition.EQUALS, String.valueOf(streamId))
                    .build();
        }

        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(StreamDataSource.STREAM_ID, ExpressionTerm.Condition.EQUALS, String.valueOf(streamId))
                .addTerm(StreamDataSource.STATUS, ExpressionTerm.Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue())
                .build();
    }

    private ExpressionOperator addPermissionConstraints(final ExpressionOperator expression, final String permission) {
        final ExpressionOperator filter = streamSecurityFilter.getExpression(permission).orElse(null);

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

    private Condition createCondition(final FindStreamCriteria criteria, final String permission) {
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
            condition = and(condition, strm.ID.in(idSet.getSet()));
        }

        // Get additional selection criteria based on meta data attributes;
        final SelectConditionStep<Record1<Long>> metaConditionStep = getMetaCondition(secureExpression);
        if (metaConditionStep != null) {
            condition = and(condition, strm.ID.in(metaConditionStep));
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
