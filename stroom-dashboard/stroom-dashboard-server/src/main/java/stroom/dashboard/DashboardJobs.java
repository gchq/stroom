package stroom.dashboard;

import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;
import static stroom.task.api.job.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
class DashboardJobs implements ScheduledJobs {

    private QueryHistoryCleanExecutor queryHistoryCleanExecutor;

    @Inject
    public DashboardJobs(QueryHistoryCleanExecutor queryHistoryCleanExecutor) {
        this.queryHistoryCleanExecutor = queryHistoryCleanExecutor;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Query History Clean")
                        .description("Job to clean up old query history items")
                        .schedule(CRON, "0 0 *")
                        .method((task) -> this.queryHistoryCleanExecutor.exec())
                        .advanced(false)
                        .build()
        );
    }
}
