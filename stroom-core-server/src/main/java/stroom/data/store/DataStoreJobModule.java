package stroom.data.store;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskConsumer;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class DataStoreJobModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Stream Retention")
                .description("Delete data that exceeds the retention period specified by feed")
                .schedule(CRON, "0 0 *")
                .to(StreamRetention.class);
    }

    private static class StreamRetention extends TaskConsumer {
        @Inject
        StreamRetention(final StreamRetentionExecutor dataRetentionExecutor) {
            super(task -> dataRetentionExecutor.exec());
        }
    }
}
