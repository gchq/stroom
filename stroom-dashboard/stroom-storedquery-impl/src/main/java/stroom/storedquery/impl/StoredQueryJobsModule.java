package stroom.storedquery.impl;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.RunnableWrapper;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class StoredQueryJobsModule extends ScheduledJobsModule {
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

    private static class QueryHistoryClean extends RunnableWrapper {
        @Inject
        QueryHistoryClean(final StoredQueryHistoryCleanExecutor queryHistoryCleanExecutor) {
            super(queryHistoryCleanExecutor::exec);
        }
    }
}
