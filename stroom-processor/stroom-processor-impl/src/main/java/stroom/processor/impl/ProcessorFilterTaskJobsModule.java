package stroom.processor.impl;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskConsumer;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class ProcessorFilterTaskJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Processor Filter Task Queue Statistics")
                .description("Write statistics about the size of the task queue")
                .schedule(PERIODIC, "1m")
                .to(ProcessorFilterTaskQueueStatistics.class);
        bindJob()
                .name("Processor Filter Task Retention")
                .description("Physically delete processor filter tasks that have been logically " +
                        "deleted or complete based on age (stroom.process.deletePurgeAge)")
                .schedule(PERIODIC, "1m")
                .to(ProcessorFilterTaskRetention.class);
    }

    private static class ProcessorFilterTaskQueueStatistics extends TaskConsumer {
        @Inject
        ProcessorFilterTaskQueueStatistics(final ProcessorFilterTaskManager processorFilterTaskManager) {
            super(task -> processorFilterTaskManager.writeQueueStatistics());
        }
    }

    private static class ProcessorFilterTaskRetention extends TaskConsumer {
        @Inject
        ProcessorFilterTaskRetention(final ProcessorFilterTaskDeleteExecutor processorFilterTaskDeleteExecutor) {
            super(task -> processorFilterTaskDeleteExecutor.exec());
        }
    }
}
