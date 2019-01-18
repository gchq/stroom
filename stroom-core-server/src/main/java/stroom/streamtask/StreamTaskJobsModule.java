package stroom.streamtask;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;
import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

class StreamTaskJobsModule extends ScheduledJobsModule {
    private final Provider<StreamTaskDeleteExecutor> streamTaskDeleteExecutorProvider;
    private final Provider<StreamTaskCreator> streamTaskCreatorProvider;
    private final Provider<ProxyAggregationExecutor> proxyAggregationExecutorProvider;

    @Inject
    StreamTaskJobsModule(final Provider<StreamTaskDeleteExecutor> streamTaskDeleteExecutorProvider,
                         final Provider<StreamTaskCreator> streamTaskCreatorProvider,
                         final Provider<ProxyAggregationExecutor> proxyAggregationExecutorProvider) {
        this.streamTaskDeleteExecutorProvider = streamTaskDeleteExecutorProvider;
        this.streamTaskCreatorProvider = streamTaskCreatorProvider;
        this.proxyAggregationExecutorProvider = proxyAggregationExecutorProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Stream Task Queue Statistics")
                .description("Write statistics about the size of the task queue")
                .schedule(PERIODIC, "1m")
                .to(() -> (task) -> streamTaskCreatorProvider.get().writeQueueStatistics());
        bindJob()
                .name("Stream Task Retention")
                .description("Physically delete stream tasks that have been logically " +
                        "deleted or complete based on age (stroom.process.deletePurgeAge)")
                .schedule(PERIODIC, "1m")
                .to(() -> (task) -> streamTaskDeleteExecutorProvider.get().exec());
        bindJob()
                .name("Proxy Aggregation")
                .description("Job to pick up the data written by the proxy and store it in Stroom")
                .schedule(CRON, "0,10,20,30,40,50 * *")
                .to(() -> (task) -> proxyAggregationExecutorProvider.get().exec());
    }
}
