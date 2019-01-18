package stroom.resource;

import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;
import static stroom.task.api.job.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class ResourceJobs implements ScheduledJobs {
    private ResourceStoreImpl resourceStore;

    @Inject
    public ResourceJobs(ResourceStoreImpl resourceStore) {
        this.resourceStore = resourceStore;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Delete temp file")
                        .description("Deletes the resource store temporary file.")
                        .method((task) -> this.resourceStore.execute())
                        .managed(false)
                        .schedule(PERIODIC, "1h").build()
        );
    }
}
