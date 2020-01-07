package stroom.db.util;

import com.zaxxer.hikari.HikariConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.DbConfig;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class HikariUtil {
    private HikariUtil() {
        // Utility class.
    }

    public static HikariConfig createConfig(final DbConfig dbConfig) {
        final ConnectionConfig connectionConfig = dbConfig.getConnectionConfig();
        final ConnectionPoolConfig connectionPoolConfig = dbConfig.getConnectionPoolConfig();

        // Validate the connection details.
        DbUtil.validate(connectionConfig);
        // Keep waiting until we can establish a DB connection to allow for the DB to start after the app
        DbUtil.waitForConnection(connectionConfig);

        return create(connectionConfig, connectionPoolConfig);
    }

    private static HikariConfig create(final ConnectionConfig connectionConfig,
                                       final ConnectionPoolConfig connectionPoolConfig) {
        final HikariConfig config = new HikariConfig();

        // Pool properties
        setPoolProp(connectionPoolConfig::getConnectionTimeout, config::setConnectionTimeout, Long::longValue);
        setPoolProp(connectionPoolConfig::getIdleTimeout, config::setIdleTimeout, Long::longValue);
        setPoolProp(connectionPoolConfig::getMaxLifetime, config::setMaxLifetime, Long::longValue);
        setPoolProp(connectionPoolConfig::getMinimumIdle, config::setMinimumIdle, Integer::intValue);
        setPoolProp(connectionPoolConfig::getMaxPoolSize, config::setMaximumPoolSize, Integer::intValue);

        setPoolProp(connectionConfig::getJdbcDriverUrl, config::setJdbcUrl, Function.identity());
        setPoolProp(connectionConfig::getJdbcDriverUsername, config::setUsername, Function.identity());
        setPoolProp(connectionConfig::getJdbcDriverPassword, config::setPassword, Function.identity());

        // JDBC Driver properties
        setPoolProp(connectionPoolConfig::getCachePrepStmts,
            val -> config.addDataSourceProperty("cachePrepStmts", val),
            String::valueOf);
        setPoolProp(connectionPoolConfig::getPrepStmtCacheSize,
            val -> config.addDataSourceProperty("prepStmtCacheSize", val),
            String::valueOf);
        setPoolProp(connectionPoolConfig::getPrepStmtCacheSqlLimit,
            val -> config.addDataSourceProperty("prepStmtCacheSqlLimit", val),
            String::valueOf);

        return config;
    }

    private static <T1, T2> void setPoolProp(final Supplier<T1> source,
                                             final Consumer<T2> dest,
                                             final Function<T1, T2> typeMapper) {
        final T1 sourceValue = source.get();
        if (sourceValue != null) {
            dest.accept(typeMapper.apply(sourceValue));
        }
    }
}
