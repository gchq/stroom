package stroom.db.util;

import stroom.config.common.HasDbConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import javax.sql.DataSource;

/**
 * @param <T_Config>       A config class that implements {@link HasDbConfig}
 * @param <T_ConnProvider> A class that extends {@link HikariDataSource}
 */
public abstract class AbstractFlyWayDbModule<T_Config extends HasDbConfig, T_ConnProvider extends DataSource>
        extends AbstractDataSourceProviderModule<T_Config, T_ConnProvider> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractFlyWayDbModule.class);

    protected abstract String getFlyWayTableName();

    protected abstract String getFlyWayLocation();

    @Override
    protected void configure() {
        super.configure();
        LOGGER.debug(() -> "Configure() called on " + this.getClass().getCanonicalName());
    }

    @Override
    protected void performMigration(final DataSource dataSource) {
        final Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(getFlyWayLocation())
                .table(getFlyWayTableName())
                .baselineOnMigrate(true)
                .load();

        int pendingMigrations = flyway.info().pending().length;

        if (pendingMigrations > 0) {
            try {
                LOGGER.info("Applying {} Flyway DB migration(s) to {} in table {} from {}",
                        pendingMigrations,
                        getModuleName(),
                        getFlyWayTableName(),
                        getFlyWayLocation());
                flyway.migrate();
                LOGGER.info("Completed Flyway DB migration for {} in table {}",
                        getModuleName(),
                        getFlyWayTableName());
            } catch (FlywayException e) {
                LOGGER.error("Error migrating {} database", getModuleName(), e);
                throw e;
            }
        } else {
            LOGGER.info("No pending Flyway DB migration(s) for {} in {}",
                    getModuleName(),
                    getFlyWayLocation());
        }
    }
}
