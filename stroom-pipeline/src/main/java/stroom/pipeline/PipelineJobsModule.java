package stroom.pipeline;

import stroom.benchmark.BenchmarkClusterExecutor;
import stroom.pipeline.destination.RollingDestinations;
import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;
import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

class PipelineJobsModule extends ScheduledJobsModule {
    private final Provider<RollingDestinations> rollingDestinationsProvider;
    private final Provider<BenchmarkClusterExecutor> benchmarkClusterExecutorProvider;

    @Inject
    PipelineJobsModule(final Provider<RollingDestinations> rollingDestinationsProvider,
                       final Provider<BenchmarkClusterExecutor> benchmarkClusterExecutorProvider) {
        this.rollingDestinationsProvider = rollingDestinationsProvider;
        this.benchmarkClusterExecutorProvider = benchmarkClusterExecutorProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Pipeline Destination Roll")
                .description("Roll any destinations based on their roll settings")
                .schedule(PERIODIC, "1m")
                .to(() -> (task) -> rollingDestinationsProvider.get().roll());
        bindJob()
                .name("XX Benchmark System XX")
                .description("Job to generate data in the system in order to benchmark it's performance (do not run in live!!)")
                .schedule(CRON, "* * *")
                .to(() -> (task) -> benchmarkClusterExecutorProvider.get().exec(task));
    }
}
