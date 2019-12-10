package stroom.db.util;

import com.zaxxer.hikari.HikariConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;

public class HikariUtil {
    private HikariUtil() {
        // Utility class.
    }

    public static HikariConfig createConfig(final HasDbConfig dbConfig) {
        final ConnectionConfig connectionConfig = dbConfig.getDbConfig().getConnectionConfig();
        final ConnectionPoolConfig connectionPoolConfig = dbConfig.getDbConfig().getConnectionPoolConfig();

        // Validate the connection details.
        DbUtil.validate(connectionConfig);
        // Keep waiting until we can establish a DB connection to allow for the DB to start after the app
        DbUtil.waitForConnection(connectionConfig);

        return create(connectionConfig, connectionPoolConfig);
    }

    private static HikariConfig create(final ConnectionConfig connectionConfig, final ConnectionPoolConfig connectionPoolConfig) {
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

        if (connectionPoolConfig.getIdleTimeout() != null) {
            config.setIdleTimeout(connectionPoolConfig.getIdleTimeout());
        }
        if (connectionPoolConfig.getMaxLifetime() != null) {
            config.setMaxLifetime(connectionPoolConfig.getMaxLifetime());
        }
        if (connectionPoolConfig.getMaxPoolSize() != null) {
            config.setMaximumPoolSize(connectionPoolConfig.getMaxPoolSize());
        }

        return config;
    }
}
