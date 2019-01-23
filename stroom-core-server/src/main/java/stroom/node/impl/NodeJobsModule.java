package stroom.node.impl;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskConsumer;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;

public class NodeJobsModule extends ScheduledJobsModule {
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
                .to(JavaHeapHistogramStatistics.class);
        bindJob()
                .name("Node Status")
                .description("Job to record status of node (CPU and Memory usage)")
                .schedule(CRON, "* * *")
                .advanced(false)
                .to(NodeStatus.class);
    }

    private static class JavaHeapHistogramStatistics extends TaskConsumer {
        @Inject
        JavaHeapHistogramStatistics(final HeapHistogramStatisticsExecutor heapHistogramStatisticsExecutor) {
            super(task -> heapHistogramStatisticsExecutor.exec());
        }
    }

    private static class NodeStatus extends TaskConsumer {
        @Inject
        NodeStatus(final NodeStatusExecutor nodeStatusExecutor) {
            super(task -> nodeStatusExecutor.exec());
        }
    }
}
