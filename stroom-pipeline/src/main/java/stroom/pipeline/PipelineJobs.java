package stroom.pipeline;

import stroom.pipeline.destination.RollingDestinations;
import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.PERIODIC;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
class PipelineJobs implements ScheduledJobs {

    private RollingDestinations rollingDestinations;

    @Inject
    public PipelineJobs(RollingDestinations rollingDestinations) {
        this.rollingDestinations = rollingDestinations;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Pipeline Destination Roll")
                        .description("Roll any destinations based on their roll settings")
                        .method((task) -> this.rollingDestinations.roll())
                        .schedule(PERIODIC, "1m")
                        .build()
        );
    }
}
