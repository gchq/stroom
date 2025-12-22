/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.statistics.impl.sql.SQLStatisticsConfig.SQLStatisticsDbConfig;

import java.util.List;
import javax.sql.DataSource;

public class SQLStatisticsDbModule extends AbstractFlyWayDbModule<SQLStatisticsDbConfig, SQLStatisticsDbConnProvider> {

    private static final String MODULE = "stroom-statistics";
    private static final String FLYWAY_LOCATIONS = "stroom/statistics/impl/sql/db/migration";
    private static final String FLYWAY_TABLE = "statistics_schema_history";

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected List<String> getFlyWayLocations() {
        return List.of(FLYWAY_LOCATIONS);
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
            super(dataSource, MODULE);
        }
    }

}
