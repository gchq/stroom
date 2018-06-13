package stroom.streamstore.meta.db;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Period;
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
import stroom.streamstore.meta.db.ExpressionMapper.TermHandler;
import stroom.streamstore.meta.db.StreamImpl.Builder;
import stroom.streamstore.meta.impl.db.stroom.tables.Strm;
import stroom.streamstore.meta.impl.db.stroom.tables.StrmFeed;
import stroom.streamstore.meta.impl.db.stroom.tables.StrmProcessor;
import stroom.streamstore.meta.impl.db.stroom.tables.StrmType;
import stroom.streamstore.shared.StreamDataSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static stroom.streamstore.meta.impl.db.stroom.tables.Strm.STRM;
import static stroom.streamstore.meta.impl.db.stroom.tables.StrmFeed.STRM_FEED;
import static stroom.streamstore.meta.impl.db.stroom.tables.StrmProcessor.STRM_PROCESSOR;
import static stroom.streamstore.meta.impl.db.stroom.tables.StrmType.STRM_TYPE;

@Singleton
class StreamMetaServiceImpl implements StreamMetaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamMetaServiceImpl.class);

    private final DataSource dataSource;
    private final FeedService feedService;
    private final StreamTypeService streamTypeService;
    private final ProcessorService processorService;
    private final StreamSecurityFilter streamSecurityFilter;
    private final Security security;

    private final Strm strm = STRM.as("s");
    private final StrmFeed strmFeed = STRM_FEED.as("f");
    private final StrmType strmType = STRM_TYPE.as("st");
    private final StrmProcessor strmProcessor = STRM_PROCESSOR.as("sp");

    private final ExpressionMapper expressionMapper;

    @Inject
    StreamMetaServiceImpl(final StreamMetaDataSource dataSource,
                          final FeedService feedService,
                          final StreamTypeService streamTypeService,
                          final ProcessorService processorService,
                          final StreamSecurityFilter streamSecurityFilter,
                          final Security security) {
        this.dataSource = dataSource;
        this.feedService = feedService;
        this.streamTypeService = streamTypeService;
        this.processorService = processorService;
        this.streamSecurityFilter = streamSecurityFilter;
        this.security = security;

        final Map<String, TermHandler<?>> termHandlers = new HashMap<>();
        termHandlers.put(StreamDataSource.STREAM_ID, new TermHandler<>(strm.ID, Long::valueOf));
        termHandlers.put(StreamDataSource.FEED, new TermHandler<>(strm.FK_FD_ID, feedService::getOrCreate));
        termHandlers.put(StreamDataSource.FEED_ID, new TermHandler<>(strm.FK_FD_ID, Integer::valueOf));
        termHandlers.put(StreamDataSource.STREAM_TYPE, new TermHandler<>(strm.FK_STRM_TP_ID, streamTypeService::getOrCreate));
        termHandlers.put(StreamDataSource.PIPELINE, new TermHandler<>(strmProcessor.PIPE_UUID, value -> value));
        termHandlers.put(StreamDataSource.PARENT_STREAM_ID, new TermHandler<>(strm.PARNT_STRM_ID, Long::valueOf));
        termHandlers.put(StreamDataSource.STREAM_TASK_ID, new TermHandler<>(strm.STRM_TASK_ID, Long::valueOf));
        termHandlers.put(StreamDataSource.STREAM_PROCESSOR_ID, new TermHandler<>(strm.FK_STRM_PROC_ID, Integer::valueOf));
        termHandlers.put(StreamDataSource.STATUS, new TermHandler<>(strm.STAT, value -> StreamStatusId.getPrimitiveValue(StreamStatus.valueOf(value.toUpperCase()))));
        termHandlers.put(StreamDataSource.STATUS_TIME, new TermHandler<>(strm.STAT_MS, Long::valueOf));
        termHandlers.put(StreamDataSource.CREATE_TIME, new TermHandler<>(strm.CRT_MS, Long::valueOf));
        termHandlers.put(StreamDataSource.EFFECTIVE_TIME, new TermHandler<>(strm.EFFECT_MS, Long::valueOf));
        expressionMapper = new ExpressionMapper(termHandlers);
    }

        @Override
    public Stream createStream(final StreamProperties streamProperties) {
        final Integer streamTypeId = streamTypeService.getOrCreate(streamProperties.getStreamTypeName());
        final Integer feedId = feedService.getOrCreate(streamProperties.getFeedName());
        final Integer processorId = processorService.getOrCreate(streamProperties.getStreamProcessorId(), streamProperties.getPipelineUuid());

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

            final long id = create.insertInto(STRM,
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
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .update(strm)
                    .set(strm.STAT, StreamStatusId.getPrimitiveValue(streamStatus))
                    .set(strm.STAT_MS, System.currentTimeMillis())
                    .where(condition)
                    .returning(strm.ID,
                            strmFeed.NAME,
                            strmType.NAME,
                            strmProcessor.PIPE_UUID,
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
                            .pipelineUuid(strmProcessor.PIPE_UUID.get(r))
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

    @Override
    public BaseResultList<Stream> find(final FindStreamCriteria criteria) {
        final ExpressionOperator secureExpression = addPermissionConstraints(criteria.getExpression(), DocumentPermissionNames.READ);
        final Condition condition = expressionMapper.apply(secureExpression);

        int offset = 0;
        int numberOfRows = 1000000;

        PageRequest pageRequest = criteria.getPageRequest();
        if (pageRequest != null) {
            offset = pageRequest.getOffset().intValue();
            numberOfRows = pageRequest.getLength();
        }

        return BaseResultList.createCriterialBasedList(find(condition, offset, numberOfRows), criteria);
    }

    private List<Stream> find(final Condition condition, final int offset, final int numberOfRows) {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

            return create
                    .select(
                            strm.ID,
                            strmFeed.NAME,
                            strmType.NAME,
                            strmProcessor.PIPE_UUID,
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
        return updateStatus(criteria, StreamStatus.DELETED, DocumentPermissionNames.DELETE);

//        final ExpressionOperator secureExpression = addPermissionConstraints(criteria.getExpression(), DocumentPermissionNames.DELETE);
//        final Condition condition = expressionMapper.apply(secureExpression);
//
//        try (final Connection connection = dataSource.getConnection()) {
//            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
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

    private int updateStatus(final FindStreamCriteria criteria, final StreamStatus streamStatus, final String permission) {
        final ExpressionOperator secureExpression = addPermissionConstraints(criteria.getExpression(), permission);
        final Condition condition = expressionMapper.apply(secureExpression);

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
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
        final ExpressionOperator secureExpression = addPermissionConstraints(criteria.getExpression(), DocumentPermissionNames.DELETE);
        final Condition condition = expressionMapper.apply(secureExpression);

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

            return create
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
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

            return create
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
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

            return create
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
}
