package stroom.statistics.sql.search;

import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;
import static stroom.task.api.job.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class SQLStatisticSearchJobs implements ScheduledJobs {

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
