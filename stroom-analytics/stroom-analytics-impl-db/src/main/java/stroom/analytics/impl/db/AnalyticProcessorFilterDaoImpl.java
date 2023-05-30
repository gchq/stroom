package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticProcessorFilterDao;
import stroom.analytics.shared.AnalyticProcessorFilter;
import stroom.db.util.JooqUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jooq.Record;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.analytics.impl.db.jooq.tables.AnalyticProcessorFilter.ANALYTIC_PROCESSOR_FILTER;

@Singleton
class AnalyticProcessorFilterDaoImpl implements AnalyticProcessorFilterDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticProcessorFilterDaoImpl.class);

    private final AnalyticsDbConnProvider analyticsDbConnProvider;
    private final SecurityContext securityContext;
    private final ObjectMapper mapper;


    @Inject
    public AnalyticProcessorFilterDaoImpl(final AnalyticsDbConnProvider analyticsDbConnProvider,
                                          final SecurityContext securityContext) {
        this.analyticsDbConnProvider = analyticsDbConnProvider;
        this.securityContext = securityContext;
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    @Override
    public Optional<AnalyticProcessorFilter> getByUuid(final String uuid) {
        final var result = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .select(ANALYTIC_PROCESSOR_FILTER.UUID,
                        ANALYTIC_PROCESSOR_FILTER.VERSION,
                        ANALYTIC_PROCESSOR_FILTER.CREATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.CREATE_USER,
                        ANALYTIC_PROCESSOR_FILTER.UPDATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.UPDATE_USER,
                        ANALYTIC_PROCESSOR_FILTER.ANALYTIC_UUID,
                        ANALYTIC_PROCESSOR_FILTER.EXPRESSION,
                        ANALYTIC_PROCESSOR_FILTER.MIN_META_CREATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.MAX_META_CREATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.NODE,
                        ANALYTIC_PROCESSOR_FILTER.ENABLED)
                .from(ANALYTIC_PROCESSOR_FILTER)
                .where(ANALYTIC_PROCESSOR_FILTER.UUID.eq(uuid))
                .fetchOptional());

        return result.map(this::recordToAnalyticProcessorFilter);
    }

    @Override
    public Optional<AnalyticProcessorFilter> getByAnalyticUuid(final String analyticUuid) {
        final var result = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .select(ANALYTIC_PROCESSOR_FILTER.UUID,
                        ANALYTIC_PROCESSOR_FILTER.VERSION,
                        ANALYTIC_PROCESSOR_FILTER.CREATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.CREATE_USER,
                        ANALYTIC_PROCESSOR_FILTER.UPDATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.UPDATE_USER,
                        ANALYTIC_PROCESSOR_FILTER.ANALYTIC_UUID,
                        ANALYTIC_PROCESSOR_FILTER.EXPRESSION,
                        ANALYTIC_PROCESSOR_FILTER.MIN_META_CREATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.MAX_META_CREATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.NODE,
                        ANALYTIC_PROCESSOR_FILTER.ENABLED)
                .from(ANALYTIC_PROCESSOR_FILTER)
                .where(ANALYTIC_PROCESSOR_FILTER.ANALYTIC_UUID.eq(analyticUuid))
                .fetchOptional());

        return result.map(this::recordToAnalyticProcessorFilter);
    }

    @Override
    public AnalyticProcessorFilter create(final AnalyticProcessorFilter filter) {
        Objects.requireNonNull(filter, "Filter is null");
        if (filter.getUuid() != null) {
            throw new RuntimeException("Filter already has UUID");
        }

        final String expression = serialise(filter.getExpression());
        final String filterUuid = UUID.randomUUID().toString();
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .insertInto(ANALYTIC_PROCESSOR_FILTER,
                        ANALYTIC_PROCESSOR_FILTER.UUID,
                        ANALYTIC_PROCESSOR_FILTER.VERSION,
                        ANALYTIC_PROCESSOR_FILTER.CREATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.CREATE_USER,
                        ANALYTIC_PROCESSOR_FILTER.UPDATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.UPDATE_USER,
                        ANALYTIC_PROCESSOR_FILTER.ANALYTIC_UUID,
                        ANALYTIC_PROCESSOR_FILTER.EXPRESSION,
                        ANALYTIC_PROCESSOR_FILTER.MIN_META_CREATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.MAX_META_CREATE_TIME_MS,
                        ANALYTIC_PROCESSOR_FILTER.NODE,
                        ANALYTIC_PROCESSOR_FILTER.ENABLED)
                .values(filterUuid,
                        1,
                        now,
                        userId,
                        now,
                        userId,
                        filter.getAnalyticUuid(),
                        expression,
                        filter.getMinMetaCreateTimeMs(),
                        filter.getMaxMetaCreateTimeMs(),
                        filter.getNode(),
                        filter.isEnabled())
                .execute());
        return getByUuid(filterUuid).orElseThrow();
    }

    @Override
    public AnalyticProcessorFilter update(final AnalyticProcessorFilter filter) {
        Objects.requireNonNull(filter, "Filter is null");
        Objects.requireNonNull(filter.getUuid(), "Filter UUID is null");

        final String expression = serialise(filter.getExpression());
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .update(ANALYTIC_PROCESSOR_FILTER)
                .set(ANALYTIC_PROCESSOR_FILTER.VERSION, ANALYTIC_PROCESSOR_FILTER.VERSION.plus(1))
                .set(ANALYTIC_PROCESSOR_FILTER.UPDATE_TIME_MS, now)
                .set(ANALYTIC_PROCESSOR_FILTER.UPDATE_USER, userId)
                .set(ANALYTIC_PROCESSOR_FILTER.ANALYTIC_UUID, filter.getAnalyticUuid())
                .set(ANALYTIC_PROCESSOR_FILTER.EXPRESSION, expression)
                .set(ANALYTIC_PROCESSOR_FILTER.MIN_META_CREATE_TIME_MS, filter.getMinMetaCreateTimeMs())
                .set(ANALYTIC_PROCESSOR_FILTER.MAX_META_CREATE_TIME_MS, filter.getMaxMetaCreateTimeMs())
                .set(ANALYTIC_PROCESSOR_FILTER.NODE, filter.getNode())
                .set(ANALYTIC_PROCESSOR_FILTER.ENABLED, filter.isEnabled())
                .where(ANALYTIC_PROCESSOR_FILTER.UUID.eq(filter.getUuid()))
                .execute());
        return getByUuid(filter.getUuid()).orElseThrow();
    }

    @Override
    public boolean delete(final AnalyticProcessorFilter filter) {
        Objects.requireNonNull(filter, "Filter is null");
        Objects.requireNonNull(filter.getUuid(), "Filter UUID is null");

        final int count = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .deleteFrom(ANALYTIC_PROCESSOR_FILTER)
                .where(ANALYTIC_PROCESSOR_FILTER.UUID.eq(filter.getUuid()))
                .execute());
        return count > 0;
    }

    private AnalyticProcessorFilter recordToAnalyticProcessorFilter(final Record record) {
        return AnalyticProcessorFilter.builder()
                .uuid(record.get(ANALYTIC_PROCESSOR_FILTER.UUID))
                .version(record.get(ANALYTIC_PROCESSOR_FILTER.VERSION))
                .createTimeMs(record.get(ANALYTIC_PROCESSOR_FILTER.CREATE_TIME_MS))
                .createUser(record.get(ANALYTIC_PROCESSOR_FILTER.CREATE_USER))
                .updateTimeMs(record.get(ANALYTIC_PROCESSOR_FILTER.UPDATE_TIME_MS))
                .updateUser(record.get(ANALYTIC_PROCESSOR_FILTER.UPDATE_USER))
                .analyticUuid(record.get(ANALYTIC_PROCESSOR_FILTER.ANALYTIC_UUID))
                .expression(deserialise(record.get(ANALYTIC_PROCESSOR_FILTER.EXPRESSION)))
                .minMetaCreateTimeMs(record.get(ANALYTIC_PROCESSOR_FILTER.MIN_META_CREATE_TIME_MS))
                .maxMetaCreateTimeMs(record.get(ANALYTIC_PROCESSOR_FILTER.MAX_META_CREATE_TIME_MS))
                .node(record.get(ANALYTIC_PROCESSOR_FILTER.NODE))
                .enabled(record.get(ANALYTIC_PROCESSOR_FILTER.ENABLED))
                .build();
    }

    private ExpressionOperator deserialise(final String string) {
        if (string == null) {
            return null;
        }
        try {
            return mapper.readValue(string, ExpressionOperator.class);
        } catch (final JsonProcessingException e) {
            LOGGER.error(e::getMessage, e);
        }
        return null;
    }

    private String serialise(final ExpressionOperator config) {
        if (config == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(config);
        } catch (final JsonProcessingException e) {
            LOGGER.error(e::getMessage, e);
        }
        return null;
    }
}
