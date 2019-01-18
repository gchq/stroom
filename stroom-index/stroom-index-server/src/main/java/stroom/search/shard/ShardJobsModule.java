package stroom.search.shard;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

class ShardJobsModule extends ScheduledJobsModule {
    private final Provider<IndexShardSearcherCache> indexShardSearcherCacheProvider;

    @Inject
    ShardJobsModule(final Provider<IndexShardSearcherCache> indexShardSearcherCacheProvider) {
        this.indexShardSearcherCacheProvider = indexShardSearcherCacheProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Index Searcher Cache Refresh")
                .description("Job to refresh index shard searchers in the cache")
                .schedule(PERIODIC, "10m")
                .to(() -> (task) -> indexShardSearcherCacheProvider.get().refresh());
    }
}
