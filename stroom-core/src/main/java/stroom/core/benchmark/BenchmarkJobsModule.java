package stroom.core.benchmark;

import stroom.util.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class BenchmarkJobsModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        ScheduledJobsBinder.create(binder())
                .bindJobTo(BenchmarkSystem.class, builder -> builder
                        .withName("XX Benchmark System XX")
                        .withDescription("Job to generate data in the system in order to benchmark it's performance (do not run in live!!)")
                        .withEnabledState(false)
                        .withSchedule(CRON, "* * *"));
    }

    private static class BenchmarkSystem extends RunnableWrapper {
        @Inject
        BenchmarkSystem(final BenchmarkClusterExecutor benchmarkClusterExecutor) {
            super(benchmarkClusterExecutor::exec);
        }
    }
}
