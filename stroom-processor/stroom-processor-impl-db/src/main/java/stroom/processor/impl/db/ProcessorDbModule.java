package stroom.processor.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.process.ProcessorConfig;
import stroom.processor.impl.db.dao.ProcessorDao;
import stroom.processor.impl.db.dao.ProcessorDaoImpl;
import stroom.util.db.DbUtil;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class ProcessorDbModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorDbModule.class);
    private static final String FLYWAY_LOCATIONS = "stroom/processor/impl/db";
    private static final String FLYWAY_TABLE = "processor_schema_history";

    @Override
    protected void configure() {
        bind(ProcessorServiceImpl.class).asEagerSingleton();

        bind(ProcessorDao.class).to(ProcessorDaoImpl.class);

//        bind(FeedService.class).to(FeedServiceImpl.class);

        // TODO do we need any clean up in process?
//        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
//        clearableBinder.addBinding().to(Cleanup.class);

    }

    @SuppressWarnings("unused")
    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<ProcessorConfig> configProvider) {
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
        LOGGER.info("Applying Flyway migrations to stroom-data-meta in {} from {}", FLYWAY_TABLE, FLYWAY_LOCATIONS);
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            LOGGER.error("Error migrating stroom-data-meta database",e);
            throw e;
        }
        LOGGER.info("Completed Flyway migrations for stroom-data-meta in {}", FLYWAY_TABLE);
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
