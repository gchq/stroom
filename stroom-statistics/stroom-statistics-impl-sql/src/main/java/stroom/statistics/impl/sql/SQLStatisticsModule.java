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

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.task.api.TaskHandlerBinder;

import java.util.function.Function;

public class SQLStatisticsModule extends AbstractFlyWayDbModule<SQLStatisticsConfig, SQLStatisticsDbConnProvider> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SQLStatisticsModule.class);
    private static final String MODULE = "stroom-statistics";
    private static final String FLYWAY_LOCATIONS = "stroom/statistics/impl/sql/db/migration";
    private static final String FLYWAY_TABLE = "statistics_schema_history";

    @Override
    protected void configure() {
        super.configure();
        bind(SQLStatisticCache.class).to(SQLStatisticCacheImpl.class);
        bind(Statistics.class).to(SQLStatisticEventStore.class);

        TaskHandlerBinder.create(binder())
                .bind(SQLStatisticFlushTask.class, SQLStatisticFlushTaskHandler.class);

//        // MultiBind the connection provider so we can see status for all databases.
//        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
//                .addBinding(ConnectionProvider.class);
    }

    @Override
    public String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    public String getModuleName() {
        return MODULE;
    }

    @Override
    public String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    public Function<HikariConfig, SQLStatisticsDbConnProvider> getConnectionProviderConstructor() {
        return SQLStatisticsDbConnProvider::new;
    }

    @Override
    public Class<SQLStatisticsDbConnProvider> getConnectionProviderType() {
        return SQLStatisticsDbConnProvider.class;
    }

//    @Provides
//    @Singleton
//    ConnectionProvider getConnectionProvider(final Provider<SQLStatisticsConfig> configProvider,
//                                             final HikariConfigHolder hikariConfigHolder) {
//        LOGGER.info("Creating connection provider for {}", MODULE);
//        final HikariConfig config = hikariConfigHolder.getHikariConfig(configProvider.get());
//        final ConnectionProvider connectionProvider = new ConnectionProvider(config);
//        flyway(connectionProvider);
//        return connectionProvider;
//    }
//
//    private Flyway flyway(final DataSource dataSource) {
//        final Flyway flyway = Flyway.configure()
//                .dataSource(dataSource)
//                .locations(FLYWAY_LOCATIONS)
//                .table(FLYWAY_TABLE)
//                .baselineOnMigrate(true)
//                .load();
//        LOGGER.info("Applying Flyway migrations to {} in {} from {}", MODULE, FLYWAY_TABLE, FLYWAY_LOCATIONS);
//        try {
//            flyway.migrate();
//        } catch (FlywayException e) {
//            LOGGER.error("Error migrating {} database", MODULE, e);
//            throw e;
//        }
//        LOGGER.info("Completed Flyway migrations for {} in {}", MODULE, FLYWAY_TABLE);
//        return flyway;
//    }
//
//    @Override
//    public boolean equals(final Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        return true;
//    }
//
//    @Override
//    public int hashCode() {
//        return 0;
//    }
}