package stroom.volume;

import stroom.node.VolumeService;
import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;
import static stroom.task.api.job.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class VolumeJobs implements ScheduledJobs {
    private VolumeService volumeService;

    @Inject
    public VolumeJobs(VolumeService volumeService){
        this.volumeService = volumeService;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Volume Status")
                        .description("Update the usage status of volumes owned by the node")
                        .method((task) -> this.volumeService.flush())
                        .schedule(PERIODIC, "5m").build()
        );
    }
}
