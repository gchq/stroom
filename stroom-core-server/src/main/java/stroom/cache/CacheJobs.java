package stroom.cache;

import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.PERIODIC;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
class CacheJobs implements ScheduledJobs {
    private StroomCacheManager stroomCacheManager;

    @Inject
    public CacheJobs(StroomCacheManager stroomCacheManager) {
        this.stroomCacheManager = stroomCacheManager;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Evict expired elements")
                        .description("Evicts expired cache entries")
                        .method((task) -> this.stroomCacheManager.evictExpiredElements())
                        .managed(false)
                        .schedule(PERIODIC, "1m").build()
        );
    }
}
