package stroom.refdata.store;

import stroom.task.api.job.ScheduledJobsModule;
import stroom.task.api.job.TaskConsumer;

import javax.inject.Inject;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;

public class RefDataStoreJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Ref Data Off-heap Store Purge")
                .description("Purge old reference data from the off heap store as configured")
                .schedule(CRON, "0 2 *")
                .to(RefDataPurge.class);
    }

    private static class RefDataPurge extends TaskConsumer {
        @Inject
        RefDataPurge(final RefDataStoreFactory refDataStoreFactory) {
            super(task -> refDataStoreFactory.purgeOldData());
        }
    }
}
