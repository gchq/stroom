package stroom.annotations.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.annotations.impl.AnnotationsDao;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.db.util.HikariUtil;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class AnnotationsDbModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationsDbModule.class);
    private static final String MODULE = "stroom-annotations";
    private static final String FLYWAY_LOCATIONS = "stroom/annotations/impl/db/migration";
    private static final String FLYWAY_TABLE = "annotation_schema_history";

    private final AnnotationsConfig annotationsConfig;

    public AnnotationsDbModule(final AnnotationsConfig annotationsConfig) {
        this.annotationsConfig = annotationsConfig;
    }

    @Override
    protected void configure() {
        // Bind the application config.
        bind(AnnotationsConfig.class).toInstance(this.annotationsConfig);

        bind(AnnotationsDao.class).to(AnnotationsDaoImpl.class).asEagerSingleton();

//        // MultiBind the connection provider so we can see status for all databases.
//        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
//                .addBinding(ConnectionProvider.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<AnnotationsConfig> configProvider) {
        LOGGER.info("Creating connection provider for {}", MODULE);
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
