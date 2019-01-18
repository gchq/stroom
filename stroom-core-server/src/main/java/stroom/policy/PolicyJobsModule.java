package stroom.policy;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;

public class PolicyJobsModule extends ScheduledJobsModule {
    private final Provider<DataRetentionExecutor> dataRetentionExecutorProvider;

    @Inject
    PolicyJobsModule(final Provider<DataRetentionExecutor> dataRetentionExecutorProvider) {
        this.dataRetentionExecutorProvider = dataRetentionExecutorProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Data Retention")
                .description("Delete data that exceeds the retention period specified by data retention policy")
                .schedule(CRON, "0 0 *")
                .to(() -> (task) -> dataRetentionExecutorProvider.get().exec());
    }
}
