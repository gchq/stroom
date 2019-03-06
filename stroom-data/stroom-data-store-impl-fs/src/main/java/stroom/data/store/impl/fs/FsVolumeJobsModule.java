package stroom.data.store.impl.fs;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskConsumer;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class FsVolumeJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("File System Volume Status")
                .description("Update the usage status of file system volumes")
                .schedule(PERIODIC, "5m")
                .to(FileVolumeStatus.class);
    }

    private static class FileVolumeStatus extends TaskConsumer {
        @Inject
        FileVolumeStatus(final FsVolumeServiceImpl volumeService) {
            super(task -> volumeService.updateStatus());
        }
    }
}
