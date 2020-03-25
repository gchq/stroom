package stroom.data.store.impl;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskRunnable;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class DataRetentionJobModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Data Retention")
                .description("Delete data that exceeds the retention period specified by feed")
                .schedule(CRON, "0 0 *")
                .to(DataRetention.class);
    }

    private static class DataRetention extends TaskRunnable {
        @Inject
        DataRetention(final FeedDataRetentionExecutor feedDataRetentionExecutor) {
            super(feedDataRetentionExecutor::exec);
        }
    }
}
