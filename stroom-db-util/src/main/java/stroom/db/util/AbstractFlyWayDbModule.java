package stroom.db.util;

import stroom.config.common.AbstractDbConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * @param <T_CONFIG>    A config class that extends {@link AbstractDbConfig}
 * @param <T_CONN_PROV> A class that extends {@link HikariDataSource}
 */
public abstract class AbstractFlyWayDbModule<T_CONFIG extends AbstractDbConfig, T_CONN_PROV extends DataSource>
        extends AbstractDataSourceProviderModule<T_CONFIG, T_CONN_PROV> {

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
        FlywayUtil.migrate(dataSource, getFlyWayLocation(), getFlyWayTableName(), getModuleName());
    }
}
