package stroom.search.shard;

import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;
import static stroom.task.api.job.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class ShardJobs implements ScheduledJobs {
    private IndexShardSearcherCache indexShardSearcherCache;

    @Inject
    public ShardJobs(IndexShardSearcherCache indexShardSearcherCache) {
        this.indexShardSearcherCache = indexShardSearcherCache;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Index Searcher Cache Refresh")
                        .description("Job to refresh index shard searchers in the cache")
                        .method((task) -> this.indexShardSearcherCache.refresh())
                        .schedule(PERIODIC, "10m").build()
        );
    }
}
