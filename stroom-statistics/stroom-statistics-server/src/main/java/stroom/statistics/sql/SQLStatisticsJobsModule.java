package stroom.statistics.sql;

import stroom.task.api.job.ScheduledJobsModule;

import javax.inject.Inject;
import javax.inject.Provider;

import static stroom.task.api.job.Schedule.ScheduleType.CRON;
import static stroom.task.api.job.Schedule.ScheduleType.PERIODIC;

class SQLStatisticsJobsModule extends ScheduledJobsModule {
    private final Provider<SQLStatisticEventStore> sqlStatisticEventStoreProvider;
    private final Provider<SQLStatisticCache> sqlStatisticCacheProvider;
    private final Provider<SQLStatisticAggregationManager> sqlStatisticAggregationManagerProvider;

    @Inject
    SQLStatisticsJobsModule(final Provider<SQLStatisticEventStore> sqlStatisticEventStoreProvider,
                            final Provider<SQLStatisticCache> sqlStatisticCacheProvider,
                            final Provider<SQLStatisticAggregationManager> sqlStatisticAggregationManagerProvider) {
        this.sqlStatisticEventStoreProvider = sqlStatisticEventStoreProvider;
        this.sqlStatisticCacheProvider = sqlStatisticCacheProvider;
        this.sqlStatisticAggregationManagerProvider = sqlStatisticAggregationManagerProvider;
    }

    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Evict from object pool")
                .description("Evict from SQL Statistics event store object pool")
                .managed(false)
                .schedule(PERIODIC, "1m")
                .to(() -> (task) -> sqlStatisticEventStoreProvider.get().evict());
        bindJob()
                .name("SQL Stats In Memory Flush")
                .description("SQL Stats In Memory Flush (Cache to DB)")
                .schedule(CRON, "0,10,20,30,40,50 * *")
                .to(() -> (task) -> sqlStatisticCacheProvider.get().execute());
        bindJob()
                .name("SQL Stats Database Aggregation")
                .description("Run SQL stats database aggregation")
                .schedule(CRON, "5,15,25,35,45,55 * *")
                .to(() -> (task) -> sqlStatisticAggregationManagerProvider.get().aggregate());
    }
}
