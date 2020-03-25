package stroom.index.impl;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskRunnable;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class IndexVolumeJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Index Volume Status")
                .description("Update the usage status of volumes owned by the node")
                .schedule(PERIODIC, "5m")
                .to(VolumeStatus.class);
    }

    private static class VolumeStatus extends TaskRunnable {
        @Inject
        VolumeStatus(final IndexVolumeService volumeService) {
            super(volumeService::rescan);
        }
    }
}
