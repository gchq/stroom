package stroom.dashboard;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;

class DashboardJobsModule extends ScheduledJobsModule {
    private final Provider<QueryHistoryCleanExecutor> queryHistoryCleanExecutorProvider;

    @Inject
    DashboardJobsModule(final Provider<QueryHistoryCleanExecutor> queryHistoryCleanExecutorProvider) {
        this.queryHistoryCleanExecutorProvider = queryHistoryCleanExecutorProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Query History Clean")
                .description("Job to clean up old query history items")
                .schedule(CRON, "0 0 *")
                .advanced(false)
                .to(() -> (task) -> queryHistoryCleanExecutorProvider.get().exec());
    }
}
