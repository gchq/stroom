package stroom.search;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskConsumer;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class SearchJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Evict expired elements")
                .managed(false)
                .schedule(PERIODIC, "10s")
                .to(EvictExpiredElements.class);
    }

    private static class EvictExpiredElements extends TaskConsumer {
        @Inject
        EvictExpiredElements(final LuceneSearchResponseCreatorManager luceneSearchResponseCreatorManager) {
            super(task -> luceneSearchResponseCreatorManager.evictExpiredElements());
        }
    }
}
