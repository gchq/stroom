package stroom.index.impl;

import stroom.job.api.ScheduledJobsModule;
import stroom.job.api.TaskRunnable;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;
import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class IndexJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Index Shard Delete")
                .description("Job to delete index shards from disk that have been marked as deleted")
                .schedule(CRON, "0 0 *")
                .to(IndexShardDelete.class);
        bindJob()
                .name("Index Shard Retention")
                .description("Job to set index shards to have a status of deleted that have past their retention period")
                .schedule(PERIODIC, "10m")
                .to(IndexShardRetention.class);
        bindJob()
                .name("Index Writer Cache Sweep")
                .description("Job to remove old index shard writers from the cache")
                .schedule(PERIODIC, "10m")
                .to(IndexWriterCacheSweep.class);
        bindJob()
                .name("Index Writer Flush")
                .description("Job to flush index shard data to disk")
                .schedule(PERIODIC, "10m")
                .to(IndexWriterFlush.class);
    }


    private static class IndexShardDelete extends TaskRunnable {
        @Inject
        IndexShardDelete(final IndexShardManager indexShardManager) {
            super(indexShardManager::deleteFromDisk);
        }
    }

    private static class IndexShardRetention extends TaskRunnable {
        @Inject
        IndexShardRetention(final IndexShardManager indexShardManager) {
            super(indexShardManager::checkRetention);
        }
    }

    private static class IndexWriterCacheSweep extends TaskRunnable {
        @Inject
        IndexWriterCacheSweep(final IndexShardWriterCache indexShardWriterCache) {
            super(indexShardWriterCache::sweep);
        }
    }

    private static class IndexWriterFlush extends TaskRunnable {
        @Inject
        IndexWriterFlush(final IndexShardWriterCache indexShardWriterCache) {
            super(indexShardWriterCache::flushAll);
        }
    }
}
