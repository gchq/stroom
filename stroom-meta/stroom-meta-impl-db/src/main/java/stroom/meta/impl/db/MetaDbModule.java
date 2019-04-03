package stroom.meta.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.db.util.HikariUtil;
import stroom.meta.impl.MetaDao;
import stroom.meta.impl.MetaFeedDao;
import stroom.meta.impl.MetaKeyDao;
import stroom.meta.impl.MetaModule;
import stroom.meta.impl.MetaProcessorDao;
import stroom.meta.impl.MetaTypeDao;
import stroom.meta.impl.MetaValueDao;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class MetaDbModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetaModule.class);
    private static final String MODULE = "stroom-meta";
    private static final String FLYWAY_LOCATIONS = "stroom/meta/impl/db";
    private static final String FLYWAY_TABLE = "meta_schema_history";

    @Override
    protected void configure() {
        install(new MetaModule());

        bind(MetaFeedDao.class).to(MetaFeedDaoImpl.class);
        bind(MetaTypeDao.class).to(MetaTypeDaoImpl.class);
        bind(MetaProcessorDao.class).to(MetaProcessorDaoImpl.class);
        bind(MetaKeyDao.class).to(MetaKeyDaoImpl.class);
        bind(MetaValueDao.class).to(MetaValueDaoImpl.class);
        bind(MetaDao.class).to(MetaDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(Cleanup.class);

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding(ConnectionProvider.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<MetaServiceConfig> configProvider) {
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
