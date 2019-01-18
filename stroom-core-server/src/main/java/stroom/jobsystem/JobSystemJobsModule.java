package stroom.jobsystem;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

class JobSystemJobsModule extends ScheduledJobsModule {
    private final Provider<ClusterLockClusterHandler> clusterLockClusterHandlerProvider;
    private final Provider<ClusterLockService> clusterLockServiceProvider;
    private final Provider<DistributedTaskFetcher> distributedTaskFetcherProvider;

    @Inject
    JobSystemJobsModule(final Provider<ClusterLockClusterHandler> clusterLockClusterHandlerProvider,
                               final Provider<ClusterLockService> clusterLockServiceProvider,
                               final Provider<DistributedTaskFetcher> distributedTaskFetcherProvider) {
        this.clusterLockClusterHandlerProvider = clusterLockClusterHandlerProvider;
        this.clusterLockServiceProvider = clusterLockServiceProvider;
        this.distributedTaskFetcherProvider = distributedTaskFetcherProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Unlock old locks")
                .description("Every 10 minutes try and unlock/remove any locks that " +
                        "we hold that have not been refreshed by their owner for 10 minutes.")
                .managed(false)
                .schedule(PERIODIC, "10m")
                .to(() -> (task) -> clusterLockClusterHandlerProvider.get().unlockOldLocks());
        bindJob()
                .name("Keep alive")
                .description("Keeps a locks alive")
                .managed(false)
                .schedule(PERIODIC, "1m")
                .to(() -> (task) -> clusterLockServiceProvider.get().keepAlive());
        bindJob()
                .name("Fetch new tasks")
                .description("Every 10 seconds the Stroom lifecycle service will try and fetch new tasks for execution.")
                .managed(false)
                .schedule(PERIODIC, "10s")
                .to(() -> (task) -> distributedTaskFetcherProvider.get().execute());
    }
}
