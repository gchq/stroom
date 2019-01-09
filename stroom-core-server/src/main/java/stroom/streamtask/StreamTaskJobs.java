package stroom.streamtask;

import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.PERIODIC;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
class StreamTaskJobs  implements ScheduledJobs {
    private StreamTaskDeleteExecutor streamTaskDeleteExecutor;
    private StreamTaskCreator streamTaskCreator;

    @Inject
    public StreamTaskJobs(StreamTaskDeleteExecutor streamTaskDeleteExecutor, StreamTaskCreator streamTaskCreator){
        this.streamTaskDeleteExecutor = streamTaskDeleteExecutor;
        this.streamTaskCreator = streamTaskCreator;
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
                        .schedule(PERIODIC, "1m").build()
        );
    }
}
