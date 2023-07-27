package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticNotificationStateDao;
import stroom.analytics.shared.AnalyticNotificationState;
import stroom.db.util.JooqUtil;

import org.jooq.Record;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.analytics.impl.db.jooq.tables.AnalyticNotificationState.ANALYTIC_NOTIFICATION_STATE;

@Singleton
public class AnalyticNotificationStateDaoImpl implements AnalyticNotificationStateDao {

    private final AnalyticsDbConnProvider analyticsDbConnProvider;


    @Inject
    public AnalyticNotificationStateDaoImpl(final AnalyticsDbConnProvider analyticsDbConnProvider) {
        this.analyticsDbConnProvider = analyticsDbConnProvider;
    }


    @Override
    public Optional<AnalyticNotificationState> get(final String notificationUuid) {
        final var result = JooqUtil.contextResult(analyticsDbConnProvider, context -> context
                .select(ANALYTIC_NOTIFICATION_STATE.FK_ANALYTIC_NOTIFICATION_UUID,
                        ANALYTIC_NOTIFICATION_STATE.LAST_EXECUTION_TIME,
                        ANALYTIC_NOTIFICATION_STATE.MESSAGE)
                .from(ANALYTIC_NOTIFICATION_STATE)
                .where(ANALYTIC_NOTIFICATION_STATE.FK_ANALYTIC_NOTIFICATION_UUID.eq(notificationUuid))
                .fetchOptional());
        return result.map(this::recordToAnalyticNotificationState);
    }

    @Override
    public void create(final AnalyticNotificationState notificationState) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .insertInto(ANALYTIC_NOTIFICATION_STATE,
                        ANALYTIC_NOTIFICATION_STATE.FK_ANALYTIC_NOTIFICATION_UUID,
                        ANALYTIC_NOTIFICATION_STATE.LAST_EXECUTION_TIME,
                        ANALYTIC_NOTIFICATION_STATE.MESSAGE)
                .values(notificationState.getNotificationUuid(),
                        notificationState.getLastExecutionTime(),
                        notificationState.getMessage())
                .execute());
    }

    @Override
    public void update(final AnalyticNotificationState notificationState) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .update(ANALYTIC_NOTIFICATION_STATE)
                .set(ANALYTIC_NOTIFICATION_STATE.LAST_EXECUTION_TIME, notificationState.getLastExecutionTime())
                .set(ANALYTIC_NOTIFICATION_STATE.MESSAGE, notificationState.getMessage())
                .where(ANALYTIC_NOTIFICATION_STATE.FK_ANALYTIC_NOTIFICATION_UUID
                        .eq(notificationState.getNotificationUuid()))
                .execute());
    }

    @Override
    public void delete(final AnalyticNotificationState notificationState) {
        JooqUtil.context(analyticsDbConnProvider, context -> context
                .deleteFrom(ANALYTIC_NOTIFICATION_STATE)
                .where(ANALYTIC_NOTIFICATION_STATE.FK_ANALYTIC_NOTIFICATION_UUID
                        .eq(notificationState.getNotificationUuid()))
                .execute());
    }

    private AnalyticNotificationState recordToAnalyticNotificationState(final Record record) {
        return AnalyticNotificationState.builder()
                .notificationUuid(record.get(ANALYTIC_NOTIFICATION_STATE.FK_ANALYTIC_NOTIFICATION_UUID))
                .lastExecutionTime(record.get(ANALYTIC_NOTIFICATION_STATE.LAST_EXECUTION_TIME))
                .message(record.get(ANALYTIC_NOTIFICATION_STATE.MESSAGE))
                .build();
    }
}
