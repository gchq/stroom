package stroom.data.meta.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import stroom.data.meta.api.DataMetaService;
import stroom.data.meta.api.DataSecurityFilter;
import stroom.entity.shared.Clearable;

import javax.inject.Singleton;
import javax.sql.DataSource;

public class DataMetaDbModule extends AbstractModule {
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
    ConnectionProvider getConnectionProvider() {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3307/stroom?useUnicode=yes&characterEncoding=UTF-8");
        config.setUsername("stroomuser");
        config.setPassword("stroompassword1");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        final ConnectionProvider connectionProvider = new ConnectionProvider(config);
        flyway(connectionProvider);
        return connectionProvider;
    }

    private Flyway flyway(final DataSource dataSource) {
        final Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setLocations("stroom/data/meta/impl/db");
        flyway.setTable("data_meta_schema_history");
        flyway.setBaselineOnMigrate(true);
        flyway.migrate();
        return flyway;
    }
}
