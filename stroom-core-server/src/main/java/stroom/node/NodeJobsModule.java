package stroom.node;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;

class NodeJobsModule extends ScheduledJobsModule {
    private final Provider<HeapHistogramStatisticsExecutor> heapHistogramStatisticsExecutorProvider;
    private final Provider<NodeStatusExecutor> nodeStatusExecutorProvider;

    @Inject
    NodeJobsModule(final Provider<HeapHistogramStatisticsExecutor> heapHistogramStatisticsExecutorProvider,
                   final Provider<NodeStatusExecutor> nodeStatusExecutorProvider) {
        this.heapHistogramStatisticsExecutorProvider = heapHistogramStatisticsExecutorProvider;
        this.nodeStatusExecutorProvider = nodeStatusExecutorProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Java Heap Histogram Statistics")
                .description("Generate Java heap map histogram ('jmap -histo:live') and record statistic events " +
                        "for the entries. CAUTION: this will pause the JVM, only enable this if you understand the " +
                        "consequences!")
                .schedule(CRON, "0 * *")
                .enabled(false)
                .to(() -> (task) -> heapHistogramStatisticsExecutorProvider.get().exec());
        bindJob()
                .name("Node Status")
                .description("Job to record status of node (CPU and Memory usage)")
                .schedule(CRON, "* * *")
                .advanced(false)
                .to(() -> (task) -> nodeStatusExecutorProvider.get().exec());
    }
}
