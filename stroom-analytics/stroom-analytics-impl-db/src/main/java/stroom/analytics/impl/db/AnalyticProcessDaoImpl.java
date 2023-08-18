package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticProcessDao;
import stroom.analytics.shared.AnalyticProcess;
import stroom.db.util.JooqUtil;
import stroom.security.api.SecurityContext;

import org.jooq.Record;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.analytics.impl.db.jooq.tables.AnalyticProcess.ANALYTIC_PROCESS;

@Singleton
class AnalyticProcessDaoImpl implements AnalyticProcessDao {

    private final AnalyticsDbConnProvider analyticsDbConnProvider;
    private final SecurityContext securityContext;


    @Inject
    public AnalyticProcessDaoImpl(final AnalyticsDbConnProvider analyticsDbConnProvider,
                                  final SecurityContext securityContext) {
        this.analyticsDbConnProvider = analyticsDbConnProvider;
        this.securityContext = securityContext;
    }

    @Override
    public Optional<AnalyticProcess> getByUuid(final String uuid) {
        final var result = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .select(ANALYTIC_PROCESS.UUID,
                        ANALYTIC_PROCESS.VERSION,
                        ANALYTIC_PROCESS.CREATE_TIME_MS,
                        ANALYTIC_PROCESS.CREATE_USER,
                        ANALYTIC_PROCESS.UPDATE_TIME_MS,
                        ANALYTIC_PROCESS.UPDATE_USER,
                        ANALYTIC_PROCESS.ANALYTIC_UUID,
                        ANALYTIC_PROCESS.NODE,
                        ANALYTIC_PROCESS.ENABLED)
                .from(ANALYTIC_PROCESS)
                .where(ANALYTIC_PROCESS.UUID.eq(uuid))
                .fetchOptional());

        return result.map(this::recordToAnalyticProcessorFilter);
    }

    @Override
    public Optional<AnalyticProcess> getByAnalyticUuid(final String analyticUuid) {
        final var result = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .select(ANALYTIC_PROCESS.UUID,
                        ANALYTIC_PROCESS.VERSION,
                        ANALYTIC_PROCESS.CREATE_TIME_MS,
                        ANALYTIC_PROCESS.CREATE_USER,
                        ANALYTIC_PROCESS.UPDATE_TIME_MS,
                        ANALYTIC_PROCESS.UPDATE_USER,
                        ANALYTIC_PROCESS.ANALYTIC_UUID,
                        ANALYTIC_PROCESS.NODE,
                        ANALYTIC_PROCESS.ENABLED)
                .from(ANALYTIC_PROCESS)
                .where(ANALYTIC_PROCESS.ANALYTIC_UUID.eq(analyticUuid))
                .fetchOptional());

        return result.map(this::recordToAnalyticProcessorFilter);
    }

    @Override
    public AnalyticProcess create(final AnalyticProcess filter) {
        Objects.requireNonNull(filter, "Filter is null");
        if (filter.getUuid() != null) {
            throw new RuntimeException("Filter already has UUID");
        }

        final String filterUuid = UUID.randomUUID().toString();
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .insertInto(ANALYTIC_PROCESS,
                        ANALYTIC_PROCESS.UUID,
                        ANALYTIC_PROCESS.VERSION,
                        ANALYTIC_PROCESS.CREATE_TIME_MS,
                        ANALYTIC_PROCESS.CREATE_USER,
                        ANALYTIC_PROCESS.UPDATE_TIME_MS,
                        ANALYTIC_PROCESS.UPDATE_USER,
                        ANALYTIC_PROCESS.ANALYTIC_UUID,
                        ANALYTIC_PROCESS.NODE,
                        ANALYTIC_PROCESS.ENABLED)
                .values(filterUuid,
                        1,
                        now,
                        userId,
                        now,
                        userId,
                        filter.getAnalyticUuid(),
                        filter.getNode(),
                        filter.isEnabled())
                .execute());
        return getByUuid(filterUuid).orElseThrow();
    }

    @Override
    public AnalyticProcess update(final AnalyticProcess filter) {
        Objects.requireNonNull(filter, "Filter is null");
        Objects.requireNonNull(filter.getUuid(), "Filter UUID is null");

//        final String expression = serialise(filter.getExpression());
        final long now = System.currentTimeMillis();
        final String userId = securityContext.getUserId();
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .update(ANALYTIC_PROCESS)
                .set(ANALYTIC_PROCESS.VERSION, ANALYTIC_PROCESS.VERSION.plus(1))
                .set(ANALYTIC_PROCESS.UPDATE_TIME_MS, now)
                .set(ANALYTIC_PROCESS.UPDATE_USER, userId)
                .set(ANALYTIC_PROCESS.ANALYTIC_UUID, filter.getAnalyticUuid())
                .set(ANALYTIC_PROCESS.NODE, filter.getNode())
                .set(ANALYTIC_PROCESS.ENABLED, filter.isEnabled())
                .where(ANALYTIC_PROCESS.UUID.eq(filter.getUuid()))
                .execute());
        return getByUuid(filter.getUuid()).orElseThrow();
    }

    @Override
    public boolean delete(final AnalyticProcess filter) {
        Objects.requireNonNull(filter, "Filter is null");
        Objects.requireNonNull(filter.getUuid(), "Filter UUID is null");

        final int count = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .deleteFrom(ANALYTIC_PROCESS)
                .where(ANALYTIC_PROCESS.UUID.eq(filter.getUuid()))
                .execute());
        return count > 0;
    }

    private AnalyticProcess recordToAnalyticProcessorFilter(final Record record) {
        return AnalyticProcess.builder()
                .uuid(record.get(ANALYTIC_PROCESS.UUID))
                .version(record.get(ANALYTIC_PROCESS.VERSION))
                .createTimeMs(record.get(ANALYTIC_PROCESS.CREATE_TIME_MS))
                .createUser(record.get(ANALYTIC_PROCESS.CREATE_USER))
                .updateTimeMs(record.get(ANALYTIC_PROCESS.UPDATE_TIME_MS))
                .updateUser(record.get(ANALYTIC_PROCESS.UPDATE_USER))
                .analyticUuid(record.get(ANALYTIC_PROCESS.ANALYTIC_UUID))
                .node(record.get(ANALYTIC_PROCESS.NODE))
                .enabled(record.get(ANALYTIC_PROCESS.ENABLED))
                .build();
    }
}
