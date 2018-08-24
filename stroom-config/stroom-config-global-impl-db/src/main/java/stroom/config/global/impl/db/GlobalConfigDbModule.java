package stroom.config.global.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import stroom.config.app.PropertyServiceConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class GlobalConfigDbModule extends AbstractModule {
    private static final String FLYWAY_LOCATIONS = "stroom/config/global/impl/db";
    private static final String FLYWAY_TABLE = "config_schema_history";

    @Override
    protected void configure() {
        bind(GlobalConfigService.class).to(GlobalConfigServiceImpl.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<PropertyServiceConfig> configProvider) {
        final ConnectionConfig connectionConfig = configProvider.get().getConnectionConfig();
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
        flyway.migrate();
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
