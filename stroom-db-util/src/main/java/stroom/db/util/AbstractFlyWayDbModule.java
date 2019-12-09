package stroom.db.util;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.HasDbConfig;
import stroom.util.guice.GuiceUtil;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.util.function.Function;

/**
 * @param <T_Config> A config class that implements {@link HasDbConfig}
 * @param <T_ConnProvider> A class that extends {@link HikariDataSource}
 */
public abstract class AbstractFlyWayDbModule<T_Config extends HasDbConfig, T_ConnProvider extends DataSource>
        extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFlyWayDbModule.class);

    protected abstract String getFlyWayTableName();

    protected abstract String getFlyWayLocation();

    protected abstract String getModuleName();

    protected abstract Function<HikariConfig, T_ConnProvider> getConnectionProviderConstructor();

    protected abstract Class<T_ConnProvider> getConnectionProviderType();

    @Override
    protected void configure() {
        super.configure();

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding((getConnectionProviderType()));
    }

    @Provides
    @Singleton
    public T_ConnProvider getConnectionProvider(final Provider<T_Config> configProvider,
                                                final HikariConfigHolder hikariConfigHolder) {
        LOGGER.info("Creating connection provider for {}", getModuleName());

        final HikariConfig config = hikariConfigHolder.getOrCreateHikariConfig(configProvider.get());
        // We could do this with reflection and getConnectionProviderType but sacrifices type safety
        T_ConnProvider connectionProvider = getConnectionProviderConstructor().apply(config);

        runFlywayMigration(connectionProvider);

        return connectionProvider;
    }

    private Flyway runFlywayMigration(final DataSource dataSource) {
        final Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(getFlyWayLocation())
                .table(getFlyWayTableName())
                .baselineOnMigrate(true)
                .load();

        LOGGER.info("Applying Flyway migrations to {} in {} from {}",
                getModuleName(), getFlyWayTableName(), getFlyWayLocation());
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            LOGGER.error("Error migrating {} database", getModuleName(), e);
            throw e;
        }
        LOGGER.info("Completed Flyway migrations for {} in {}",
                getModuleName(), getFlyWayTableName());
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
