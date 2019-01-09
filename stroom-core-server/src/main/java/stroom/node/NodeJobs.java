package stroom.node;

import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.CRON;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class NodeJobs implements ScheduledJobs {

    private HeapHistogramStatisticsExecutor heapHistogramStatisticsExecutor;

    @Inject
    public NodeJobs(HeapHistogramStatisticsExecutor heapHistogramStatisticsExecutor){
        this.heapHistogramStatisticsExecutor = heapHistogramStatisticsExecutor;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                .name("Java Heap Histogram Statistics")
                .description("Generate Java heap map histogram ('jmap -histo:live') and record statistic events " +
                    "for the entries. CAUTION: this will pause the JVM, only enable this if you understand the " +
                    "consequences!")
                .schedule(CRON, "0 * *")
                .method((task) -> this.heapHistogramStatisticsExecutor.exec())
                .enabled(false)
                .build()
        );
    }
}
