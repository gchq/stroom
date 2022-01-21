package stroom.statistics.impl.sql;

import stroom.job.api.ScheduledJobsBinder;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;
import stroom.util.guice.HasSystemInfoBinder;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;
import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class SqlStatisticsModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(SQLStatisticCache.class).to(SQLStatisticCacheImpl.class);
        bind(Statistics.class).to(SQLStatisticEventStore.class);

        HasSystemInfoBinder.create(binder())
                .bind(SQLStatisticCacheImpl.class)
                .bind(SQLStatisticEventStore.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(EvictFromObjectPool.class, builder -> builder
                        .name("Evict from object pool")
                        .description("Evict from SQL Statistics event store object pool")
                        .managed(false)
                        .schedule(PERIODIC, "1m"))
                .bindJobTo(SQLStatsFlush.class, builder -> builder
                        .name("SQL Stats In Memory Flush")
                        .description("SQL Stats In Memory Flush (Cache to DB)")
                        .schedule(CRON, "0,10,20,30,40,50 * *"))
                .bindJobTo(SQLStatsAggregation.class, builder -> builder
                        .name("SQL Stats Database Aggregation")
                        .description("Run SQL stats database aggregation")
                        .schedule(CRON, "5,15,25,35,45,55 * *"));

        // We need it to shutdown quite late so anything that is generating stats has had
        // a chance to finish generating
        LifecycleBinder.create(binder())
                .bindShutdownTaskTo(SQLStatisticShutdown.class, 1);
    }

    private static class EvictFromObjectPool extends RunnableWrapper {

        @Inject
        EvictFromObjectPool(final SQLStatisticEventStore sqlStatisticEventStore) {
            super(sqlStatisticEventStore::evict);
        }
    }

    private static class SQLStatsFlush extends RunnableWrapper {

        @Inject
        SQLStatsFlush(final SQLStatisticCache sqlStatisticCache) {
            super(sqlStatisticCache::execute);
        }
    }

    private static class SQLStatsAggregation extends RunnableWrapper {

        @Inject
        SQLStatsAggregation(final SQLStatisticAggregationManager sqlStatisticAggregationManager) {
            super(sqlStatisticAggregationManager::aggregate);
        }
    }

    private static class SQLStatisticShutdown extends RunnableWrapper {

        @Inject
        SQLStatisticShutdown(final Statistics statistics) {
            super(statistics::flushAllEvents);
        }
    }
}
