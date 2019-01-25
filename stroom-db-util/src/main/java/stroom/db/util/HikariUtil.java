package stroom.db.util;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;

public class HikariUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(HikariUtil.class);

    private static boolean testing;

    private HikariUtil() {
        // Utility class.
    }

    public static HikariConfig createConfig(final ConnectionConfig connectionConfig, final ConnectionPoolConfig connectionPoolConfig) {
        // Validate the connection details.
        connectionConfig.validate();

        // Keep waiting until we can establish a DB connection to allow for the DB to start after the app
        DbUtil.waitForConnection(connectionConfig);

        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionConfig.getJdbcDriverUrl());
        config.setUsername(connectionConfig.getJdbcDriverUsername());
        config.setPassword(connectionConfig.getJdbcDriverPassword());
        config.addDataSourceProperty("cachePrepStmts",
                String.valueOf(connectionPoolConfig.isCachePrepStmts()));
        config.addDataSourceProperty("prepStmtCacheSize",
                String.valueOf(connectionPoolConfig.getPrepStmtCacheSize()));
        config.addDataSourceProperty("prepStmtCacheSqlLimit",
                String.valueOf(connectionPoolConfig.getPrepStmtCacheSqlLimit()));

        if (testing) {
            LOGGER.trace("Testing");
            config.setIdleTimeout(000);
            config.setMaxLifetime(1000);
            config.setMaximumPoolSize(2);
        } else {
            LOGGER.trace("Not testing");
        }

        return config;
    }

    public static void setTesting(final boolean testing) {
        HikariUtil.testing = testing;
    }
}
