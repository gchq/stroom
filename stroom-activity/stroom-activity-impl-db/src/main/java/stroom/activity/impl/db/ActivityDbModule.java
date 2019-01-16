package stroom.activity.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.activity.shared.DeleteActivityAction;
import stroom.activity.shared.FetchActivityAction;
import stroom.activity.shared.SaveActivityAction;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.db.DbUtil;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class ActivityDbModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityDbModule.class);
    private static final String FLYWAY_LOCATIONS = "stroom/activity/impl/db";
    private static final String FLYWAY_TABLE = "activity_schema_history";

    @Override
    protected void configure() {
        bind(ActivityService.class).to(ActivityServiceImpl.class);
//        bind(FeedService.class).to(FeedServiceImpl.class);
//        bind(DataTypeService.class).to(DataTypeServiceImpl.class);
//        bind(ProcessorService.class).to(ProcessorServiceImpl.class);
//        bind(MetaKeyService.class).to(MetaKeyServiceImpl.class);
//        bind(MetaValueService.class).to(MetaValueServiceImpl.class);
//        bind(DataMetaService.class).to(DataMetaServiceImpl.class);
//        bind(DataSecurityFilter.class).to(DataSecurityFilterImpl.class);
//
//        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
//        clearableBinder.addBinding().to(Cleanup.class);

        TaskHandlerBinder.create(binder())
                .bind(DeleteActivityAction.class, DeleteActivityHandler.class)
                .bind(FetchActivityAction.class, FetchActivityHandler.class)
                .bind(SaveActivityAction.class, SaveActivityHandler.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<ActivityConfig> configProvider) {
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
        final Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(FLYWAY_LOCATIONS)
                .table(FLYWAY_TABLE)
                .baselineOnMigrate(true)
                .load();
        LOGGER.info("Applying Flyway migrations to stroom-data-meta in {} from {}", FLYWAY_TABLE, FLYWAY_LOCATIONS);
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            LOGGER.error("Error migrating stroom-data-meta database", e);
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
