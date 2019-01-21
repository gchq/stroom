package stroom.statistics.sql.search;

import stroom.task.api.job.ScheduledJobsModule;
import stroom.task.api.job.TaskConsumer;

import javax.inject.Inject;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

public class SQLStatisticSearchJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Evict expired elements")
                .schedule(PERIODIC, "10s")
                .managed(false)
                .to(EvictExpiredElements.class);
    }

    private static class EvictExpiredElements extends TaskConsumer {
        @Inject
        EvictExpiredElements(final SqlStatisticsSearchResponseCreatorManager sqlStatisticsSearchResponseCreatorManager) {
            super(task -> sqlStatisticsSearchResponseCreatorManager.evictExpiredElements());
        }
    }
}
