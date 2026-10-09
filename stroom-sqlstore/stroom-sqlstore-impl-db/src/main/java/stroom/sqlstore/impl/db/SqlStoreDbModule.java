/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqlstore.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.sqlstore.impl.SqlStoreConfig.SqlStoreDbConfig;

import java.util.List;
import javax.sql.DataSource;

/**
 * Datasource and Flyway migration for the SQL temporal store.
 *
 * <p>Install this in {@code DbConnectionsModule}, alongside every other {@code *DbModule}, and
 * {@link SqlStoreDaoModule} in {@code CoreModule}. The split matters: {@code DbConnectionsModule}
 * is installed by {@code BootStrapModule}, which is the injector that runs during the
 * bootstrap/migration phase, so a module listed there gets its connection provider created — and
 * therefore its migration run — while {@code DbMigrationState.haveBootstrapMigrationsBeenDone()}
 * is still false.</p>
 *
 * <p>This class previously also bound the DAO, which forced it into {@code CoreModule} instead,
 * because the DAO needs {@code ExpressionMapperFactory} and hence services the bootstrap injector
 * does not have. The consequence was that the migration never ran: by the time anything asked for
 * {@link SqlStoreDbConnProvider} the bootstrap flag was set, so {@code FlywayUtil.migrate}
 * short-circuited and returned without consulting Flyway at all. The table therefore only ever
 * existed by accident of timing, and once dropped no restart would recreate it. Keep the DAO
 * binding out of this module.</p>
 */
public class SqlStoreDbModule
        extends AbstractFlyWayDbModule<SqlStoreDbConfig, SqlStoreDbConnProvider> {

    /** Name of this module */
    private static final String MODULE = "stroom-sqlstore";

    /** Where the Flyway SQL scripts are */
    private static final String FLYWAY_LOCATIONS = "stroom/sqlstore/impl/db/migration";

    /** Table with the Flyway history */
    private static final String FLYWAY_TABLE = "sqlstore_schema_history";

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
    protected Class<SqlStoreDbConnProvider> getConnectionProviderType() {
        return SqlStoreDbConnProvider.class;
    }

    @Override
    protected SqlStoreDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new SqlStoreDbConnProvider(dataSource);
    }
}
