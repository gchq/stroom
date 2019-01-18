package stroom.data.store;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;

class DataStoreJobModule extends ScheduledJobsModule {
    private final Provider<StreamRetentionExecutor> streamRetentionExecutorProvider;

    @Inject
    DataStoreJobModule(final Provider<StreamRetentionExecutor> streamRetentionExecutorProvider) {
        this.streamRetentionExecutorProvider = streamRetentionExecutorProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Stream Retention")
                .description("Delete data that exceeds the retention period specified by feed")
                .schedule(CRON, "0 0 *")
                .to(() -> (task) -> streamRetentionExecutorProvider.get().exec());
    }
}
