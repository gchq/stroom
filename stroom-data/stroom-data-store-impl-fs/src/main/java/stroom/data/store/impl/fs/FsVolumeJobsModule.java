package stroom.data.store.impl.fs;

import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class FsVolumeJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        ScheduledJobsBinder.create(binder())
                .bindJobTo(FileVolumeStatus.class, builder -> builder
                        .name("File System Volume Status")
                        .description("Update the usage status of file system volumes")
                        .schedule(PERIODIC, "5m"));
    }

    private static class FileVolumeStatus extends RunnableWrapper {
        @Inject
        FileVolumeStatus(final FsVolumeService volumeService) {
            super(volumeService::updateStatus);
        }
    }
}
