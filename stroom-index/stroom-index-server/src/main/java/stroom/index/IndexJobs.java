package stroom.index;

import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;
import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;
import static stroom.task.api.job.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class IndexJobs implements ScheduledJobs {

    private IndexShardManager indexShardManager;
    private IndexShardWriterCache indexShardWriterCache;

    @Inject
    public IndexJobs(IndexShardManager indexShardManager,
                     IndexShardWriterCache indexShardWriterCache) {
        this.indexShardManager = indexShardManager;
        this.indexShardWriterCache = indexShardWriterCache;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Index Shard Delete")
                        .description("Job to delete index shards from disk that have been marked as deleted")
                        .method((task) -> this.indexShardManager.deleteFromDisk())
                        .schedule(CRON, "0 0 *").build(),
                jobBuilder()
                        .name("Index Shard Retention")
                        .description("Job to set index shards to have a status of deleted that have past their retention period")
                        .method((task) -> this.indexShardManager.checkRetention())
                        .schedule(PERIODIC, "10m").build(),
                jobBuilder()
                        .name("Index Writer Cache Sweep")
                        .description("Job to remove old index shard writers from the cache")
                        .method((task) -> this.indexShardWriterCache.sweep())
                        .schedule(PERIODIC, "10m").build(),
                jobBuilder()
                        .name("Index Writer Flush")
                        .description("Job to flush index shard data to disk")
                        .method((task) -> this.indexShardWriterCache.flushAll())
                        .schedule(PERIODIC, "10m").build()
        );
    }
}
