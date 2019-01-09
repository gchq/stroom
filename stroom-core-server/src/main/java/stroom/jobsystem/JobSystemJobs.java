package stroom.jobsystem;

import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.PERIODIC;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class JobSystemJobs implements ScheduledJobs {

    private ClusterLockClusterHandler clusterLockClusterHandler;
    private ClusterLockService clusterLockService;
    private DistributedTaskFetcher distributedTaskFetcher;

    @Inject
    public JobSystemJobs(
            ClusterLockClusterHandler clusterLockClusterHandler,
            ClusterLockService clusterLockService,
            DistributedTaskFetcher distributedTaskFetcher) {
        this.clusterLockClusterHandler = clusterLockClusterHandler;
        this.clusterLockService = clusterLockService;
        this.distributedTaskFetcher = distributedTaskFetcher;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Unlock old locks")
                        .description("Every 10 minutes try and unlock/remove any locks that " +
                                "we hold that have not been refreshed by their owner for 10 minutes.")
                        .method((task) -> this.clusterLockClusterHandler.unlockOldLocks())
                        .managed(false)
                        .schedule(PERIODIC, "10m").build(),
                jobBuilder()
                        .name("Keep alive")
                        .description("Keeps a locks alive")
                        .method((task) -> this.clusterLockService.keepAlive())
                        .managed(false)
                        .schedule(PERIODIC, "1m").build(),
                jobBuilder()
                        .name("Fetch new tasks")
                        .description("Every 10 seconds the Stroom lifecycle service will try and fetch new tasks for execution.")
                        .method((task) -> this.distributedTaskFetcher.execute())
                        .managed(false)
                        .schedule(PERIODIC, "10s").build()
        );
    }
}
