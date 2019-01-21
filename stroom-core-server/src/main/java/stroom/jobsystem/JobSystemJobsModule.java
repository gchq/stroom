package stroom.jobsystem;

import stroom.task.api.job.ScheduledJobsModule;
import stroom.task.api.job.TaskConsumer;

import javax.inject.Inject;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

public class JobSystemJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Unlock old locks")
                .description("Every 10 minutes try and unlock/remove any locks that " +
                        "we hold that have not been refreshed by their owner for 10 minutes.")
                .managed(false)
                .schedule(PERIODIC, "10m")
                .to(UnlockOldLocks.class);
        bindJob()
                .name("Keep alive")
                .description("Keeps a locks alive")
                .managed(false)
                .schedule(PERIODIC, "1m")
                .to(KeepAlive.class);
        bindJob()
                .name("Fetch new tasks")
                .description("Every 10 seconds the Stroom lifecycle service will try and fetch new tasks for execution.")
                .managed(false)
                .schedule(PERIODIC, "10s")
                .to(FetchNewTasks.class);
    }

    private static class UnlockOldLocks extends TaskConsumer {
        @Inject
        UnlockOldLocks(final ClusterLockClusterHandler clusterLockClusterHandler) {
            super(task -> clusterLockClusterHandler.unlockOldLocks());
        }
    }

    private static class KeepAlive extends TaskConsumer {
        @Inject
        KeepAlive(final ClusterLockService clusterLockService) {
            super(task -> clusterLockService.keepAlive());
        }
    }

    private static class FetchNewTasks extends TaskConsumer {
        @Inject
        FetchNewTasks(final DistributedTaskFetcher distributedTaskFetcher) {
            super(task -> distributedTaskFetcher.execute());
        }
    }
}
