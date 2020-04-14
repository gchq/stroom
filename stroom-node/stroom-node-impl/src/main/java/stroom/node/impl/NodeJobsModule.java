package stroom.node.impl;

import stroom.job.api.Schedule.ScheduleType;
import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.RunnableWrapper;

import javax.inject.Inject;

public class NodeJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Java Heap Histogram Statistics")
                .description("Generate Java heap map histogram and record statistic events " +
                        "for the entries. CAUTION: this will pause the JVM, only enable this if you understand the " +
                        "consequences!")
                .schedule(ScheduleType.CRON, "0 * *")
                .enabled(false)
                .to(JavaHeapHistogramStatistics.class);
        bindJob()
                .name("Node Status")
                .description("Job to record status of node (CPU and Memory usage)")
                .schedule(ScheduleType.CRON, "* * *")
                .advanced(false)
                .to(NodeStatus.class);
    }

    private static class JavaHeapHistogramStatistics extends RunnableWrapper {
        @Inject
        JavaHeapHistogramStatistics(final HeapHistogramStatisticsExecutor heapHistogramStatisticsExecutor) {
            super(heapHistogramStatisticsExecutor::exec);
        }
    }

    private static class NodeStatus extends RunnableWrapper {
        @Inject
        NodeStatus(final NodeStatusExecutor nodeStatusExecutor) {
            super(nodeStatusExecutor::exec);
        }
    }
}
