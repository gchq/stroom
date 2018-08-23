package stroom.data.meta.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.DataSecurityFilter;
import stroom.entity.shared.Clearable;
import stroom.properties.api.ConnectionConfig;
import stroom.properties.api.ConnectionPoolConfig;
import stroom.properties.api.PropertyService;

import javax.inject.Singleton;
import javax.sql.DataSource;

public class DataMetaDbModule extends AbstractModule {
    private static final String FLYWAY_LOCATIONS = "stroom/data/meta/impl/db";
    private static final String FLYWAY_TABLE = "data_meta_schema_history";
    private static final String CONNECTION_PROPERTY_PREFIX = "stroom.data.meta.";

    @Override
    protected void configure() {
        bind(FeedService.class).to(FeedServiceImpl.class);
        bind(DataTypeService.class).to(DataTypeServiceImpl.class);
        bind(ProcessorService.class).to(ProcessorServiceImpl.class);
        bind(MetaKeyService.class).to(MetaKeyServiceImpl.class);
        bind(MetaValueService.class).to(MetaValueServiceImpl.class);
        bind(DataMetaService.class).to(DataMetaServiceImpl.class);
        bind(DataSecurityFilter.class).to(DataSecurityFilterImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(Cleanup.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final PropertyService propertyService) {
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
