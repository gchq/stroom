package stroom.dashboard;

import stroom.task.api.job.ScheduledJobsModule;
import stroom.task.api.job.TaskConsumer;

import javax.inject.Inject;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;

public class DashboardJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Query History Clean")
                .description("Job to clean up old query history items")
                .schedule(CRON, "0 0 *")
                .advanced(false)
                .to(QueryHistoryClean.class);
    }

    private static class QueryHistoryClean extends TaskConsumer {
        @Inject
        QueryHistoryClean(final QueryHistoryCleanExecutor queryHistoryCleanExecutor) {
            super(task -> queryHistoryCleanExecutor.exec());
        }
    }
}
