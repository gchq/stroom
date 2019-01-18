package stroom.cache;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

class CacheJobsModule extends ScheduledJobsModule {
    private final Provider<StroomCacheManager> stroomCacheManagerProvider;

    @Inject
    CacheJobsModule(final Provider<StroomCacheManager> stroomCacheManagerProvider) {
        this.stroomCacheManagerProvider = stroomCacheManagerProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Evict expired elements")
                .description("Evicts expired cache entries")
                .managed(false)
                .schedule(PERIODIC, "1m")
                .to(() -> (task) -> stroomCacheManagerProvider.get().evictExpiredElements());
    }
}
