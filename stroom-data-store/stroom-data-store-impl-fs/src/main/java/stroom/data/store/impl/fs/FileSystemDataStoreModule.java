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
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.data.store.StreamMaintenanceService;
import stroom.data.store.api.StreamStore;
import stroom.data.store.impl.SteamStoreStreamCloserImpl;
import stroom.io.StreamCloser;
import stroom.node.NodeServiceModule;
import stroom.task.TaskModule;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.db.DbUtil;
import stroom.util.lifecycle.jobmanagement.ScheduledJobsBinder;
import stroom.volume.VolumeModule;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class FileSystemDataStoreModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemDataStoreModule.class);
    private static final String FLYWAY_LOCATIONS = "stroom/data/store/impl/db/migration";
    private static final String FLYWAY_TABLE = "data_store_schema_history";

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

        ScheduledJobsBinder.create(binder()).bind(FileSystemDataStoreJobs.class);

        TaskHandlerBinder.create(binder())
                .bind(FileSystemCleanSubTask.class, FileSystemCleanSubTaskHandler.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<DataStoreServiceConfig> configProvider) {
        final ConnectionConfig connectionConfig = configProvider.get().getConnectionConfig();

        // Keep waiting until we can establish a DB connection to allow for the DB to start after the app
        DbUtil.waitForConnection(
                connectionConfig.getJdbcDriverClassName(),
                connectionConfig.getJdbcDriverUrl(),
                connectionConfig.getJdbcDriverUsername(),
                connectionConfig.getJdbcDriverPassword());

        final ConnectionPoolConfig connectionPoolConfig = configProvider.get().getConnectionPoolConfig();

        connectionConfig.validate();

        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionConfig.getJdbcDriverUrl());
        config.setUsername(connectionConfig.getJdbcDriverUsername());
        config.setPassword(connectionConfig.getJdbcDriverPassword());
        config.addDataSourceProperty("cachePrepStmts",
                String.valueOf(connectionPoolConfig.isCachePrepStmts()));
        config.addDataSourceProperty("prepStmtCacheSize",
                String.valueOf(connectionPoolConfig.getPrepStmtCacheSize()));
        config.addDataSourceProperty("prepStmtCacheSqlLimit",
                String.valueOf(connectionPoolConfig.getPrepStmtCacheSqlLimit()));
        final ConnectionProvider connectionProvider = new ConnectionProvider(config);
        flyway(connectionProvider);
        return connectionProvider;
    }

    private Flyway flyway(final DataSource dataSource) {
        final Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(FLYWAY_LOCATIONS)
                .table(FLYWAY_TABLE)
                .baselineOnMigrate(true)
                .load();
        LOGGER.info("Applying Flyway migrations to stroom-data-store in {} from {}", FLYWAY_TABLE, FLYWAY_LOCATIONS);
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            LOGGER.error("Error migrating stroom-data-store database", e);
            throw e;
        }
        LOGGER.info("Completed Flyway migrations for stroom-data-store in {}", FLYWAY_TABLE);
        return flyway;
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