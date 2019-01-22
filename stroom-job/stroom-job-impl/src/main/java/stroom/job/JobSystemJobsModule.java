package stroom.job;

import stroom.job.api.DistributedTaskFetcher;
import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskConsumer;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class JobSystemJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Fetch new tasks")
                .description("Every 10 seconds the Stroom lifecycle service will try and fetch new tasks for execution.")
                .managed(false)
                .schedule(PERIODIC, "10s")
                .to(FetchNewTasks.class);
    }

    private static class FetchNewTasks extends TaskConsumer {
        @Inject
        FetchNewTasks(final DistributedTaskFetcher distributedTaskFetcher) {
            super(task -> distributedTaskFetcher.execute());
        }
    }
}
