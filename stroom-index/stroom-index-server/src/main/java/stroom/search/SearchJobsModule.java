package stroom.search;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

class SearchJobsModule extends ScheduledJobsModule {
    private final Provider<LuceneSearchResponseCreatorManager> luceneSearchResponseCreatorManagerProvider;

    @Inject
    SearchJobsModule(final Provider<LuceneSearchResponseCreatorManager> luceneSearchResponseCreatorManagerProvider) {
        this.luceneSearchResponseCreatorManagerProvider = luceneSearchResponseCreatorManagerProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Evict expired elements")
                .managed(false)
                .schedule(PERIODIC, "10s")
                .to(() -> (task) -> luceneSearchResponseCreatorManagerProvider.get().evictExpiredElements());
    }
}
