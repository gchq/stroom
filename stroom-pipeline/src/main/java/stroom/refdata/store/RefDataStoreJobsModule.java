package stroom.refdata.store;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;

public class RefDataStoreJobsModule extends ScheduledJobsModule {
    private final Provider<RefDataStoreFactory> refDataStoreFactoryProvider;

    @Inject
    RefDataStoreJobsModule(final Provider<RefDataStoreFactory> refDataStoreFactoryProvider) {
        this.refDataStoreFactoryProvider = refDataStoreFactoryProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Ref Data Off-heap Store Purge")
                .description("Purge old reference data from the off heap store as configured")
                .schedule(CRON, "0 2 *")
                .to(() -> (task) -> refDataStoreFactoryProvider.get().purgeOldData());
    }
}
