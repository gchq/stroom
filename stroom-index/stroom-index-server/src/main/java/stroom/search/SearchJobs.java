package stroom.search;

import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;
import static stroom.task.api.job.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class SearchJobs implements ScheduledJobs {
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
