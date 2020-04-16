package stroom.cache.impl;

import stroom.job.api.Schedule.ScheduleType;
import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.RunnableWrapper;

import javax.inject.Inject;

public class CacheJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Evict expired elements")
                .description("Evicts expired cache entries")
                .managed(false)
                .schedule(ScheduleType.PERIODIC, "1m")
                .to(EvictExpiredElements.class);
    }

    private static class EvictExpiredElements extends RunnableWrapper {
        @Inject
        EvictExpiredElements(final CacheManagerService stroomCacheManager) {
            super(stroomCacheManager::evictExpiredElements);
        }
    }
}
