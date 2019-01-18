package stroom.resource;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

class ResourceJobsModule extends ScheduledJobsModule {
    private final Provider<ResourceStoreImpl> resourceStoreProvider;

    @Inject
    ResourceJobsModule(final Provider<ResourceStoreImpl> resourceStoreProvider) {
        this.resourceStoreProvider = resourceStoreProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Delete temp file")
                .description("Deletes the resource store temporary file.")
                .managed(false)
                .schedule(PERIODIC, "1h")
                .to(() -> (task) -> resourceStoreProvider.get().execute());
    }
}
