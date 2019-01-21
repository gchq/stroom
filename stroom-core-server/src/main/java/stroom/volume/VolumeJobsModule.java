package stroom.volume;

import stroom.node.VolumeService;
import stroom.task.api.job.ScheduledJobsModule;
import stroom.task.api.job.TaskConsumer;

import javax.inject.Inject;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

public class VolumeJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Volume Status")
                .description("Update the usage status of volumes owned by the node")
                .schedule(PERIODIC, "5m")
                .to(VolumeStatus.class);
    }

    private static class VolumeStatus extends TaskConsumer {
        @Inject
        VolumeStatus(final VolumeService volumeService) {
            super(task -> volumeService.flush());
        }
    }
}
