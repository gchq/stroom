package stroom.processor.impl;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.RunnableWrapper;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class ProcessorTaskJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Processor Task Queue Statistics")
                .description("Write statistics about the size of the task queue")
                .schedule(PERIODIC, "1m")
                .to(ProcessorTaskQueueStatistics.class);
        bindJob()
                .name("Processor Task Retention")
                .description("Physically delete processor tasks that have been logically " +
                        "deleted or complete based on age (stroom.process.deletePurgeAge)")
                .schedule(PERIODIC, "1m")
                .to(ProcessorTaskRetention.class);
    }

    private static class ProcessorTaskQueueStatistics extends RunnableWrapper {
        @Inject
        ProcessorTaskQueueStatistics(final ProcessorTaskManager processorTaskManager) {
            super(processorTaskManager::writeQueueStatistics);
        }
    }

    private static class ProcessorTaskRetention extends RunnableWrapper {
        @Inject
        ProcessorTaskRetention(final ProcessorTaskDeleteExecutor processorTaskDeleteExecutor) {
            super(processorTaskDeleteExecutor::exec);
        }
    }
}
