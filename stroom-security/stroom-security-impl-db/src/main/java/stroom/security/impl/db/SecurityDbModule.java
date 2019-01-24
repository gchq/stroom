package stroom.security.impl.db;

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
import stroom.security.dao.AppPermissionDao;
import stroom.security.dao.DocumentPermissionDao;
import stroom.security.dao.UserDao;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class SecurityDbModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityDbModule.class);

    private static final String FLYWAY_LOCATIONS = "stroom/security/impl/db";
    private static final String FLYWAY_TABLE = "security_schema_history";

    @Override
    protected void configure() {
        bind(UserDao.class).to(UserDaoImpl.class);
        bind(DocumentPermissionDao.class).to(DocumentPermissionDaoImpl.class);
        bind(AppPermissionDao.class).to(AppPermissionDaoImpl.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<SecurityDbConfig> configProvider) {
        final ConnectionConfig connectionConfig = configProvider.get().getConnectionConfig();
        final ConnectionPoolConfig connectionPoolConfig = configProvider.get().getConnectionPoolConfig();
        final HikariConfig config = HikariUtil.createConfig(connectionConfig, connectionPoolConfig);
        final ConnectionProvider connectionProvider = new ConnectionProvider(config);
        flyway(connectionProvider);
        return connectionProvider;
    }

    private Flyway flyway(final DataSource dataSource) {
        final Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setLocations(FLYWAY_LOCATIONS);
        flyway.setTable(FLYWAY_TABLE);
        LOGGER.info("Applying Flyway migrations to stroom-security in {} from {}", FLYWAY_TABLE, FLYWAY_LOCATIONS);
        flyway.setBaselineOnMigrate(true);
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            LOGGER.error("Error migrating stroom-security database",e);
            throw e;
        }
        LOGGER.info("Completed Flyway migrations for stroom-security in {}", FLYWAY_TABLE);
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
