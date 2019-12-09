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
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.task.api.TaskHandlerBinder;

import java.util.function.Function;

public class SQLStatisticsModule extends AbstractFlyWayDbModule<SQLStatisticsConfig, SQLStatisticsDbConnProvider> {
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
    protected Function<HikariConfig, SQLStatisticsDbConnProvider> getConnectionProviderConstructor() {
        return SQLStatisticsDbConnProvider::new;
    }

    @Override
    protected Class<SQLStatisticsDbConnProvider> getConnectionProviderType() {
        return SQLStatisticsDbConnProvider.class;
    }
}