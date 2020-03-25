package stroom.pipeline;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskRunnable;
import stroom.pipeline.destination.RollingDestinations;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class PipelineJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Pipeline Destination Roll")
                .description("Roll any destinations based on their roll settings")
                .schedule(PERIODIC, "1m")
                .to(PipelineDestinationRoll.class);
    }

    private static class PipelineDestinationRoll extends TaskRunnable {
        @Inject
        PipelineDestinationRoll(final RollingDestinations rollingDestinations) {
            super(rollingDestinations::roll);
        }
    }
}
