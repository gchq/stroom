package stroom.config.global.impl.db;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.PropertyServiceConfig;
import stroom.config.global.impl.ConfigPropertyDao;
import stroom.config.global.impl.GlobalConfigModule;
import stroom.db.util.AbstractFlyWayDbModule;

import java.util.function.Function;

public class GlobalConfigDbModule extends AbstractFlyWayDbModule<PropertyServiceConfig, ConnectionProvider> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalConfigDbModule.class);
    private static final String MODULE = "stroom-config";
    private static final String FLYWAY_LOCATIONS = "stroom/config/global/impl/db/migration";
    private static final String FLYWAY_TABLE = "config_schema_history";

    @Override
    protected void configure() {
        super.configure();
        install(new GlobalConfigModule());

        bind(ConfigPropertyDao.class).to(ConfigPropertyDaoImpl.class);

//        // MultiBind the connection provider so we can see status for all databases.
//        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
//                .addBinding(ConnectionProvider.class);
    }

    @Override
    public String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    public String getModuleName() {
        return MODULE;
    }

    @Override
    public String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    public Function<HikariConfig, ConnectionProvider> getConnectionProviderConstructor() {
        return ConnectionProvider::new;
    }

    @Override
    public Class<ConnectionProvider> getConnectionProviderType() {
        return ConnectionProvider.class;
    }

//    @Provides
//    @Singleton
//    ConnectionProvider getConnectionProvider(final Provider<PropertyServiceConfig> configProvider,
//                                             final HikariConfigHolder hikariConfigHolder) {
//        LOGGER.info("Creating connection provider for {}", MODULE);
//        final HikariConfig config = hikariConfigHolder.getHikariConfig(configProvider.get());
//        final ConnectionProvider connectionProvider = new ConnectionProvider(config);
//        flyway(connectionProvider);
//        return connectionProvider;
//    }
//
//    private Flyway flyway(final DataSource dataSource) {
//        final Flyway flyway = Flyway.configure()
//                .dataSource(dataSource)
//                .locations(FLYWAY_LOCATIONS)
//                .table(FLYWAY_TABLE)
//                .baselineOnMigrate(true)
//                .load();
//        LOGGER.info("Applying Flyway migrations to {} in {} from {}", MODULE, FLYWAY_TABLE, FLYWAY_LOCATIONS);
//        try {
//            flyway.migrate();
//        } catch (FlywayException e) {
//            LOGGER.error("Error migrating {} database", MODULE, e);
//            throw e;
//        }
//        LOGGER.info("Completed Flyway migrations for {} in {}", MODULE, FLYWAY_TABLE);
//        return flyway;
//    }
//
//    @Override
//    public boolean equals(final Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        return true;
//    }
//
//    @Override
//    public int hashCode() {
//        return 0;
//    }
}
