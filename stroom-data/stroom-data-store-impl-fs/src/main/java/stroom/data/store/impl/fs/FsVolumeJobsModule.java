package stroom.data.store.impl.fs;

import stroom.util.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class FsVolumeJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        ScheduledJobsBinder.create(binder())
                .bindJobTo(FileVolumeStatus.class, builder -> builder
                        .withName("File System Volume Status")
                        .withDescription("Update the usage status of file system volumes")
                        .withSchedule(PERIODIC, "5m"));
    }

    private static class FileVolumeStatus extends RunnableWrapper {
        @Inject
        FileVolumeStatus(final FsVolumeService volumeService) {
            super(volumeService::updateStatus);
        }
    }
}
