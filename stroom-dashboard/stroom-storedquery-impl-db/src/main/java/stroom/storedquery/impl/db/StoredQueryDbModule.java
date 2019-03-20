package stroom.storedquery.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.dashboard.shared.CreateStoredQueryAction;
import stroom.dashboard.shared.DeleteStoredQueryAction;
import stroom.dashboard.shared.FetchStoredQueryAction;
import stroom.dashboard.shared.FindStoredQueryAction;
import stroom.dashboard.shared.UpdateStoredQueryAction;
import stroom.db.util.HikariUtil;
import stroom.storedquery.api.StoredQueryService;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.guice.GuiceUtil;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class StoredQueryDbModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoredQueryDbModule.class);
    private static final String MODULE = "stroom-storedquery";
    private static final String FLYWAY_LOCATIONS = "stroom/storedquery/impl/db";
    private static final String FLYWAY_TABLE = "query_schema_history";

    @Override
    protected void configure() {
        bind(StoredQueryService.class).to(StoredQueryDao.class);

        TaskHandlerBinder.create(binder())
                .bind(CreateStoredQueryAction.class, CreateStoredQueryHandler.class)
                .bind(UpdateStoredQueryAction.class, UpdateStoredQueryHandler.class)
                .bind(DeleteStoredQueryAction.class, DeleteStoredQueryHandler.class)
                .bind(FetchStoredQueryAction.class, FetchStoredQueryHandler.class)
                .bind(FindStoredQueryAction.class, FindStoredQueryHandler.class);

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding(ConnectionProvider.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<StoredQueryConfig> configProvider) {
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
        LOGGER.info("Applying Flyway migrations to {} in {} from {}", MODULE, FLYWAY_TABLE, FLYWAY_LOCATIONS);
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            LOGGER.error("Error migrating {} database", MODULE, e);
            throw e;
        }
        LOGGER.info("Completed Flyway migrations for {} in {}", MODULE, FLYWAY_TABLE);
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
