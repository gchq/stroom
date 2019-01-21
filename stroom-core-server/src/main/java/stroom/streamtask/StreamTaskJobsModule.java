package stroom.streamtask;

import stroom.task.api.job.ScheduledJobsModule;
import stroom.task.api.job.TaskConsumer;

import javax.inject.Inject;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;
import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

public class StreamTaskJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Stream Task Queue Statistics")
                .description("Write statistics about the size of the task queue")
                .schedule(PERIODIC, "1m")
                .to(StreamTaskQueueStatistics.class);
        bindJob()
                .name("Stream Task Retention")
                .description("Physically delete stream tasks that have been logically " +
                        "deleted or complete based on age (stroom.process.deletePurgeAge)")
                .schedule(PERIODIC, "1m")
                .to(StreamTaskRetention.class);
        bindJob()
                .name("Proxy Aggregation")
                .description("Job to pick up the data written by the proxy and store it in Stroom")
                .schedule(CRON, "0,10,20,30,40,50 * *")
                .to(ProxyAggregation.class);
    }

    private static class StreamTaskQueueStatistics extends TaskConsumer {
        @Inject
        StreamTaskQueueStatistics(final StreamTaskCreator streamTaskCreator) {
            super(task -> streamTaskCreator.writeQueueStatistics());
        }
    }

    private static class StreamTaskRetention extends TaskConsumer {
        @Inject
        StreamTaskRetention(final StreamTaskDeleteExecutor streamTaskDeleteExecutor) {
            super(task -> streamTaskDeleteExecutor.exec());
        }
    }

    private static class ProxyAggregation extends TaskConsumer {
        @Inject
        ProxyAggregation(final ProxyAggregationExecutor proxyAggregationExecutor) {
            super(task -> proxyAggregationExecutor.exec());
        }
    }
}
