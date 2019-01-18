package stroom.index;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;
import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

class IndexJobsModule extends ScheduledJobsModule {
    private final Provider<IndexShardManager> indexShardManagerProvider;
    private final Provider<IndexShardWriterCache> indexShardWriterCacheProvider;

    @Inject
    IndexJobsModule(final Provider<IndexShardManager> indexShardManagerProvider,
                    final Provider<IndexShardWriterCache> indexShardWriterCacheProvider) {
        this.indexShardManagerProvider = indexShardManagerProvider;
        this.indexShardWriterCacheProvider = indexShardWriterCacheProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Index Shard Delete")
                .description("Job to delete index shards from disk that have been marked as deleted")
                .schedule(CRON, "0 0 *")
                .to(() -> (task) -> indexShardManagerProvider.get().deleteFromDisk());
        bindJob()
                .name("Index Shard Retention")
                .description("Job to set index shards to have a status of deleted that have past their retention period")
                .schedule(PERIODIC, "10m")
                .to(() -> (task) -> indexShardManagerProvider.get().checkRetention());
        bindJob()
                .name("Index Writer Cache Sweep")
                .description("Job to remove old index shard writers from the cache")
                .schedule(PERIODIC, "10m")
                .to(() -> (task) -> indexShardWriterCacheProvider.get().sweep());
        bindJob()
                .name("Index Writer Flush")
                .description("Job to flush index shard data to disk")
                .schedule(PERIODIC, "10m")
                .to(() -> (task) -> indexShardWriterCacheProvider.get().flushAll());
    }
}
