package stroom.statistics.sql;

import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.CRON;
import static stroom.util.lifecycle.jobmanagement.Schedule.ScheduleType.PERIODIC;
import static stroom.util.lifecycle.jobmanagement.ScheduledJob.ScheduledJobBuilder.jobBuilder;

@Singleton
public class SQLStatisticsJobs implements ScheduledJobs {

    private SQLStatisticEventStore sqlStatisticEventStore;
    private SQLStatisticCache sqlStatisticCache;
    private SQLStatisticAggregationManager sqlStatisticAggregationManager;

    @Inject
    public SQLStatisticsJobs(
            SQLStatisticEventStore sqlStatisticEventStore,
            SQLStatisticCache sqlStatisticCache,
            SQLStatisticAggregationManager sqlStatisticAggregationManager) {
        this.sqlStatisticEventStore = sqlStatisticEventStore;
        this.sqlStatisticCache = sqlStatisticCache;
        this.sqlStatisticAggregationManager = sqlStatisticAggregationManager;
    }

    @Override
    public List<ScheduledJob> getJobs() {
        return List.of(
                jobBuilder()
                        .name("Evict from object pool")
                        .description("Evict from SQL Statistics event store object pool")
                        .managed(false)
                        .schedule(PERIODIC, "1m")
                        .method((task) -> this.sqlStatisticEventStore.evict())
                        .build(),
                jobBuilder()
                        .name("SQL Stats In Memory Flush")
                        .description("SQL Stats In Memory Flush (Cache to DB)")
                        .schedule(CRON, "0,10,20,30,40,50 * *")
                        .method((task) -> this.sqlStatisticCache.execute())
                        .build(),
                jobBuilder()
                        .name("SQL Stats Database Aggregation")
                        .description("Run SQL stats database aggregation")
                        .schedule(CRON, "5,15,25,35,45,55 * *")
                        .method((task) -> this.sqlStatisticAggregationManager.aggregate())
                        .build()
        );
    }
}
