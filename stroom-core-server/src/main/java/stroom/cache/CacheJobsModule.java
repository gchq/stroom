package stroom.cache;

import stroom.task.api.job.ScheduledJobsModule;
import stroom.task.api.job.TaskConsumer;

import javax.inject.Inject;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

public class CacheJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Evict expired elements")
                .description("Evicts expired cache entries")
                .managed(false)
                .schedule(PERIODIC, "1m")
                .to(EvictExpiredElements.class);
    }

    private static class EvictExpiredElements extends TaskConsumer {
        @Inject
        EvictExpiredElements(final StroomCacheManager stroomCacheManager) {
            super(task -> stroomCacheManager.evictExpiredElements());
        }
    }
}
