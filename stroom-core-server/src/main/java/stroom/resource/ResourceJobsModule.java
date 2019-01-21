package stroom.resource;

import stroom.task.api.job.ScheduledJobsModule;
import stroom.task.api.job.TaskConsumer;

import javax.inject.Inject;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

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
