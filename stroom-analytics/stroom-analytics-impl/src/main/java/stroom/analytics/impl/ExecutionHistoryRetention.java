package stroom.analytics.impl;

import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;

import java.time.Instant;

public class ExecutionHistoryRetention {
    private final ExecutionScheduleDao executionScheduleDao;
    private final AnalyticsConfig analyticsConfig;

    @Inject
    public ExecutionHistoryRetention(final ExecutionScheduleDao executionScheduleDao,
                                     final AnalyticsConfig analyticsConfig) {
        this.executionScheduleDao = executionScheduleDao;
        this.analyticsConfig = analyticsConfig;
    }

    public void exec() {
        final StroomDuration retention = analyticsConfig.getExecutionHistoryRetention();
        if (retention != null) {
            final Instant now = Instant.now();
            final Instant age = now.minus(retention.getDuration());
            executionScheduleDao.deleteOldExecutionHistory(age);
        }
    }
}
