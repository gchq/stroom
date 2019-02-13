package stroom.cluster.lock.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.db.util.HikariUtil;
import stroom.util.shared.Clearable;
import stroom.task.api.TaskHandlerBinder;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class ClusterLockDbModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterLockDbModule.class);
    private static final String FLYWAY_LOCATIONS = "stroom/cluster/lock/impl/db";
    private static final String FLYWAY_TABLE = "cluster_lock_schema_history";

    @Override
    protected void configure() {
        bind(ClusterLockService.class).to(ClusterLockServiceImpl.class);
        bind(ClusterLockServiceTransactionHelper.class).to(ClusterLockServiceTransactionHelperImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(ClusterLockServiceTransactionHelperImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(ClusterLockClusterTask.class, ClusterLockClusterHandler.class)
                .bind(ClusterLockTask.class, ClusterLockHandler.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<ClusterLockConfig> configProvider) {
        final ConnectionConfig connectionConfig = configProvider.get().getConnectionConfig();
        final ConnectionPoolConfig connectionPoolConfig = configProvider.get().getConnectionPoolConfig();
        final HikariConfig config = HikariUtil.createConfig(connectionConfig, connectionPoolConfig);
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
        LOGGER.info("Applying Flyway migrations to stroom-meta in {} from {}", FLYWAY_TABLE, FLYWAY_LOCATIONS);
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            LOGGER.error("Error migrating stroom-meta database", e);
            throw e;
        }
        LOGGER.info("Completed Flyway migrations for stroom-meta in {}", FLYWAY_TABLE);
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
