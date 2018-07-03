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

package stroom.data.store.impl.fs;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import stroom.data.store.StreamMaintenanceService;
import stroom.data.store.api.StreamStore;
import stroom.data.store.impl.SteamStoreStreamCloserImpl;
import stroom.io.StreamCloser;
import stroom.node.NodeServiceModule;
import stroom.properties.api.ConnectionConfig;
import stroom.properties.api.ConnectionPoolConfig;
import stroom.properties.api.StroomPropertyService;
import stroom.task.TaskHandler;
import stroom.task.TaskModule;
import stroom.volume.VolumeModule;

import javax.inject.Singleton;
import javax.sql.DataSource;

public class FileSystemDataStoreModule extends AbstractModule {
    private static final String FLYWAY_LOCATIONS = "stroom/data/store/impl/db/migration";
    private static final String FLYWAY_TABLE = "data_store_schema_history";
    private static final String CONNECTION_PROPERTY_PREFIX = "stroom.data.store.";

    @Override
    protected void configure() {
        install(new NodeServiceModule());
        install(new VolumeModule());
        install(new TaskModule());

        bind(StreamMaintenanceService.class).to(FileSystemStreamMaintenanceService.class);
        bind(StreamStore.class).to(FileSystemStreamStoreImpl.class);
        bind(StreamCloser.class).to(SteamStoreStreamCloserImpl.class);
        bind(FileSystemTypePaths.class).to(FileSystemTypePathsImpl.class);
        bind(DataVolumeService.class).to(DataVolumeServiceImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(FileSystemCleanSubTaskHandler.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final StroomPropertyService stroomPropertyService) {
        final ConnectionConfig connectionConfig = getConnectionConfig(stroomPropertyService);
        final ConnectionPoolConfig connectionPoolConfig = getConnectionPoolConfig(stroomPropertyService);

        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionConfig.getJdbcDriverUrl());
        config.setUsername(connectionConfig.getJdbcDriverUsername());
        config.setPassword(connectionConfig.getJdbcDriverPassword());
        config.addDataSourceProperty("cachePrepStmts", String.valueOf(connectionPoolConfig.isCachePrepStmts()));
        config.addDataSourceProperty("prepStmtCacheSize", String.valueOf(connectionPoolConfig.getPrepStmtCacheSize()));
        config.addDataSourceProperty("prepStmtCacheSqlLimit", String.valueOf(connectionPoolConfig.getPrepStmtCacheSqlLimit()));
        final ConnectionProvider connectionProvider = new ConnectionProvider(config);
        flyway(connectionProvider);
        return connectionProvider;
    }

    private Flyway flyway(final DataSource dataSource) {
        final Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setLocations(FLYWAY_LOCATIONS);
        flyway.setTable(FLYWAY_TABLE);
        flyway.setBaselineOnMigrate(true);
        flyway.migrate();
        return flyway;
    }

    private ConnectionConfig getConnectionConfig(final StroomPropertyService stroomPropertyService) {
        return new ConnectionConfig(CONNECTION_PROPERTY_PREFIX, stroomPropertyService);
    }

    private ConnectionPoolConfig getConnectionPoolConfig(final StroomPropertyService stroomPropertyService) {
        return new ConnectionPoolConfig(CONNECTION_PROPERTY_PREFIX, stroomPropertyService);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}