package stroom.statistics.sql.search;

import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.PERIODIC;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
class SQLStatisticSearchJobs implements ScheduledJobs {

    private SqlStatisticsSearchResponseCreatorManager sqlStatisticsSearchResponseCreatorManager;

    @Inject
    public SQLStatisticSearchJobs(SqlStatisticsSearchResponseCreatorManager sqlStatisticsSearchResponseCreatorManager) {
        this.sqlStatisticsSearchResponseCreatorManager = sqlStatisticsSearchResponseCreatorManager;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Evict expired elements")
                        .schedule(PERIODIC, "10s")
                        .method((task) -> this.sqlStatisticsSearchResponseCreatorManager.evictExpiredElements())
                        .managed(false)
                        .build()
        );
    }
}
