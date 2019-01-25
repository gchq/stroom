package stroom.resource.impl;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskConsumer;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class ResourceJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Delete temp file")
                .description("Deletes the resource store temporary file.")
                .managed(false)
                .schedule(PERIODIC, "1h")
                .to(DeleteTempFile.class);
    }

    private static class DeleteTempFile extends TaskConsumer {
        @Inject
        DeleteTempFile(final ResourceStoreImpl resourceStore) {
            super(task -> resourceStore.execute());
        }
    }
}
