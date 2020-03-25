package stroom.cluster.lock.impl.db;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskRunnable;

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

    private static class UnlockOldLocks extends TaskRunnable {
        @Inject
        UnlockOldLocks(final ClusterLockClusterHandler clusterLockClusterHandler) {
            super(clusterLockClusterHandler::unlockOldLocks);
        }
    }

    private static class KeepAlive extends TaskRunnable {
        @Inject
        KeepAlive(final ClusterLockServiceImpl clusterLockService) {
            super(clusterLockService::keepAlive);
        }
    }
}
