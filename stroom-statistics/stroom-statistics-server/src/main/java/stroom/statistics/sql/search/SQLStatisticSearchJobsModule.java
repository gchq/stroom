package stroom.statistics.sql.search;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

class SQLStatisticSearchJobsModule extends ScheduledJobsModule {
    private final Provider<SqlStatisticsSearchResponseCreatorManager> sqlStatisticsSearchResponseCreatorManagerProvider;

    @Inject
    SQLStatisticSearchJobsModule(final Provider<SqlStatisticsSearchResponseCreatorManager> sqlStatisticsSearchResponseCreatorManagerProvider) {
        this.sqlStatisticsSearchResponseCreatorManagerProvider = sqlStatisticsSearchResponseCreatorManagerProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Evict expired elements")
                .schedule(PERIODIC, "10s")
                .managed(false)
                .to(() -> (task) -> sqlStatisticsSearchResponseCreatorManagerProvider.get().evictExpiredElements());
    }
}
