package stroom.volume;

import stroom.node.VolumeService;
import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

class VolumeJobsManager extends ScheduledJobsModule {
    private final Provider<VolumeService> volumeServiceProvider;

    @Inject
    VolumeJobsManager(final Provider<VolumeService> volumeServiceProvider) {
        this.volumeServiceProvider = volumeServiceProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Volume Status")
                .description("Update the usage status of volumes owned by the node")
                .schedule(PERIODIC, "5m")
                .to(() -> (task) -> volumeServiceProvider.get().flush());
    }
}
