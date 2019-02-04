package stroom.cluster.lock.impl.db;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskConsumer;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class ClusterLockJobsModule extends ScheduledJobsModule {
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
    }

    private static class UnlockOldLocks extends TaskConsumer {
        @Inject
        UnlockOldLocks(final ClusterLockClusterHandler clusterLockClusterHandler) {
            super(task -> clusterLockClusterHandler.unlockOldLocks());
        }
    }

    private static class KeepAlive extends TaskConsumer {
        @Inject
        KeepAlive(final ClusterLockServiceImpl clusterLockService) {
            super(task -> clusterLockService.keepAlive());
        }
    }
}
