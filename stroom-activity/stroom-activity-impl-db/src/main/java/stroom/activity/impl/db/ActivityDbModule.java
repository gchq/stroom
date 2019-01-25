package stroom.activity.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.activity.api.ActivityService;
import stroom.activity.api.CurrentActivity;
import stroom.activity.shared.CreateActivityAction;
import stroom.activity.shared.DeleteActivityAction;
import stroom.activity.shared.FetchActivityAction;
import stroom.activity.shared.FindActivityAction;
import stroom.activity.shared.SetCurrentActivityAction;
import stroom.activity.shared.UpdateActivityAction;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.db.util.HikariUtil;
import stroom.task.api.TaskHandlerBinder;

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
        bind(CurrentActivity.class).to(CurrentActivityImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(CreateActivityAction.class, CreateActivityHandler.class)
                .bind(UpdateActivityAction.class, UpdateActivityHandler.class)
                .bind(DeleteActivityAction.class, DeleteActivityHandler.class)
                .bind(FetchActivityAction.class, FetchActivityHandler.class)
                .bind(FindActivityAction.class, FindActivityHandler.class)
                .bind(SetCurrentActivityAction.class, SetCurrentActivityHandler.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<ActivityConfig> configProvider) {
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
