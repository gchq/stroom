package stroom.properties.global.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import stroom.properties.api.ConnectionConfig;
import stroom.properties.api.ConnectionPoolConfig;
import stroom.properties.api.PropertyService;

import javax.inject.Singleton;
import javax.sql.DataSource;

public class GlobalPropertiesDbModule extends AbstractModule {
    private static final String FLYWAY_LOCATIONS = "stroom/properties/impl/db";
    private static final String FLYWAY_TABLE = "property_schema_history";
    private static final String CONNECTION_PROPERTY_PREFIX = "stroom.properties.";

    @Override
    protected void configure() {
        bind(GlobalPropertyService.class).to(GlobalPropertyServiceImpl.class);
//        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
//        clearableBinder.addBinding().to(Cleanup.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final PropertyService propertyService, final GlobalPropertyService globalPropertyService) {
        final ConnectionConfig connectionConfig = getConnectionConfig(propertyService);
        final ConnectionPoolConfig connectionPoolConfig = getConnectionPoolConfig(propertyService);

        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionConfig.getJdbcDriverUrl());
        config.setUsername(connectionConfig.getJdbcDriverUsername());
        config.setPassword(connectionConfig.getJdbcDriverPassword());
        config.addDataSourceProperty("cachePrepStmts", String.valueOf(connectionPoolConfig.isCachePrepStmts()));
        config.addDataSourceProperty("prepStmtCacheSize", String.valueOf(connectionPoolConfig.getPrepStmtCacheSize()));
        config.addDataSourceProperty("prepStmtCacheSqlLimit", String.valueOf(connectionPoolConfig.getPrepStmtCacheSqlLimit()));

        final ConnectionProvider connectionProvider = new ConnectionProvider(config);
        flyway(connectionProvider);
        globalPropertyService.initialise();

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

    private ConnectionConfig getConnectionConfig(final PropertyService propertyService) {
        return new ConnectionConfig(CONNECTION_PROPERTY_PREFIX, propertyService);
    }

    private ConnectionPoolConfig getConnectionPoolConfig(final PropertyService propertyService) {
        return new ConnectionPoolConfig(CONNECTION_PROPERTY_PREFIX, propertyService);
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
