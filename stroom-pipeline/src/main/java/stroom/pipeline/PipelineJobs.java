package stroom.pipeline;

import stroom.benchmark.BenchmarkClusterExecutor;
import stroom.pipeline.destination.RollingDestinations;
import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.CRON;
import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.PERIODIC;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
class PipelineJobs implements ScheduledJobs {

    private RollingDestinations rollingDestinations;
    private BenchmarkClusterExecutor benchmarkClusterExecutor;

    @Inject
    public PipelineJobs(RollingDestinations rollingDestinations, BenchmarkClusterExecutor benchmarkClusterExecutor) {
        this.rollingDestinations = rollingDestinations;
        this.benchmarkClusterExecutor = benchmarkClusterExecutor;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Pipeline Destination Roll")
                        .description("Roll any destinations based on their roll settings")
                        .method((task) -> this.rollingDestinations.roll())
                        .schedule(PERIODIC, "1m")
                        .build(),
                jobBuilder()
                        .name("XX Benchmark System XX")
                        .description("Job to generate data in the system in order to benchmark it's performance (do not run in live!!)")
                        .schedule(CRON, "* * *")
                        .method((task) -> this.benchmarkClusterExecutor.exec(task))
                        .build()
        );
    }
}
