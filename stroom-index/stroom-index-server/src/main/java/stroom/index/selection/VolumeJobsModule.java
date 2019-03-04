package stroom.index.selection;

import stroom.index.service.IndexVolumeService;
import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskConsumer;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

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
        VolumeStatus(final IndexVolumeService volumeService) {
            super(task -> volumeService.flush());
        }
    }
}
