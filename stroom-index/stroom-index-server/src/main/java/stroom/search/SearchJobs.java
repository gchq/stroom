package stroom.search;

import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.PERIODIC;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
class SearchJobs implements ScheduledJobs {
    private LuceneSearchResponseCreatorManager luceneSearchResponseCreatorManager;

    @Inject
    public SearchJobs (LuceneSearchResponseCreatorManager luceneSearchResponseCreatorManager) {
        this.luceneSearchResponseCreatorManager = luceneSearchResponseCreatorManager;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
            jobBuilder()
                .name("Evict expired elements")
                .managed(false)
                .schedule(PERIODIC, "10s")
                .method((task) -> this.luceneSearchResponseCreatorManager.evictExpiredElements())
                .build()
        );
    }
}
