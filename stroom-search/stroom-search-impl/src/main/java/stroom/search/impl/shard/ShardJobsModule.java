package stroom.search.impl.shard;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskRunnable;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class ShardJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Index Searcher Cache Refresh")
                .description("Job to refresh index shard searchers in the cache")
                .schedule(PERIODIC, "10m")
                .to(IndexSearcherCacheRefresh.class);
    }

    private static class IndexSearcherCacheRefresh extends TaskRunnable {
        @Inject
        IndexSearcherCacheRefresh(final IndexShardSearcherCache indexShardSearcherCache) {
            super(indexShardSearcherCache::refresh);
        }
    }
}
