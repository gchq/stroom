/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.statistics.impl.sql;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.job.api.ScheduledJobsBinder;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.statistics.impl.sql.SQLStatisticsConfig.SQLStatisticsDbConfig;
import stroom.util.RunnableWrapper;
import stroom.util.guice.HasSystemInfoBinder;

import javax.inject.Inject;
import javax.sql.DataSource;

import static stroom.job.api.Schedule.ScheduleType.CRON;
import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class SQLStatisticsModule extends AbstractFlyWayDbModule<SQLStatisticsDbConfig, SQLStatisticsDbConnProvider> {

    private static final String MODULE = "stroom-statistics";
    private static final String FLYWAY_LOCATIONS = "stroom/statistics/impl/sql/db/migration";
    private static final String FLYWAY_TABLE = "statistics_schema_history";

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

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    protected Class<SQLStatisticsDbConnProvider> getConnectionProviderType() {
        return SQLStatisticsDbConnProvider.class;
    }

    @Override
    protected SQLStatisticsDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements SQLStatisticsDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
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
