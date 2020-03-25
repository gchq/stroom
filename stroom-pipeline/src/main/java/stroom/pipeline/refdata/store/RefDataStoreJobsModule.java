package stroom.pipeline.refdata.store;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskRunnable;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

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

    private static class RefDataPurge extends TaskRunnable {
        @Inject
        RefDataPurge(final RefDataStoreFactory refDataStoreFactory) {
            super(refDataStoreFactory::purgeOldData);
        }
    }
}
