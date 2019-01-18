package stroom.refdata.store;

import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;
import static stroom.task.api.job.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class RefDataStoreJobs implements ScheduledJobs {

    private RefDataStoreFactory refDataStoreFactory;

    @Inject
    public RefDataStoreJobs(RefDataStoreFactory refDataStoreFactory) {
        this.refDataStoreFactory = refDataStoreFactory;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Ref Data Off-heap Store Purge")
                        .description("Purge old reference data from the off heap store as configured")
                        .schedule(CRON, "0 2 *")
                        .method((task) -> this.refDataStoreFactory.purgeOldData())
                        .build()
        );
    }
}
