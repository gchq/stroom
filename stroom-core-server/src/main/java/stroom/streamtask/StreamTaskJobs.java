package stroom.streamtask;

import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;
import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;
import static stroom.task.api.job.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class StreamTaskJobs implements ScheduledJobs {
    private StreamTaskDeleteExecutor streamTaskDeleteExecutor;
    private StreamTaskCreator streamTaskCreator;
    private ProxyAggregationExecutor proxyAggregationExecutor;

    @Inject
    public StreamTaskJobs(StreamTaskDeleteExecutor streamTaskDeleteExecutor, StreamTaskCreator streamTaskCreator, ProxyAggregationExecutor proxyAggregationExecutor) {
        this.streamTaskDeleteExecutor = streamTaskDeleteExecutor;
        this.streamTaskCreator = streamTaskCreator;
        this.proxyAggregationExecutor = proxyAggregationExecutor;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Stream Task Queue Statistics")
                        .description("Write statistics about the size of the task queue")
                        .method((task) -> this.streamTaskCreator.writeQueueStatistics())
                        .schedule(PERIODIC, "1m").build(),
                jobBuilder()
                        .name("Stream Task Retention")
                        .description("Physically delete stream tasks that have been logically " +
                                "deleted or complete based on age (stroom.process.deletePurgeAge)")
                        .method((task) -> this.streamTaskDeleteExecutor.exec())
                        .schedule(PERIODIC, "1m").build(),
                jobBuilder()
                        .name("Proxy Aggregation")
                        .description("Job to pick up the data written by the proxy and store it in Stroom")
                        .schedule(CRON, "0,10,20,30,40,50 * *")
                        .method((task) -> this.proxyAggregationExecutor.exec())
                        .build()
        );
    }
}
