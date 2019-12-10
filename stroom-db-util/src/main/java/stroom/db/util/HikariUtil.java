package stroom.db.util;

import com.zaxxer.hikari.HikariConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.DbConfig;

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

    private static HikariConfig create(final ConnectionConfig connectionConfig, final ConnectionPoolConfig connectionPoolConfig) {
        final HikariConfig config = new HikariConfig();

        if (connectionPoolConfig.getIdleTimeout() != null) {
            config.setIdleTimeout(connectionPoolConfig.getIdleTimeout());
        }
        if (connectionPoolConfig.getMaxLifetime() != null) {
            config.setMaxLifetime(connectionPoolConfig.getMaxLifetime());
        }
        if (connectionPoolConfig.getMaxPoolSize() != null) {
            config.setMaximumPoolSize(connectionPoolConfig.getMaxPoolSize());
        }

        if (connectionConfig.getJdbcDriverUrl() != null) {
            config.setJdbcUrl(connectionConfig.getJdbcDriverUrl());
        }
        if (connectionConfig.getJdbcDriverUsername() != null) {
            config.setUsername(connectionConfig.getJdbcDriverUsername());
        }
        if (connectionConfig.getJdbcDriverPassword() != null) {
            config.setPassword(connectionConfig.getJdbcDriverPassword());
        }

        if (connectionPoolConfig.getCachePrepStmts() != null) {
            config.addDataSourceProperty("cachePrepStmts", String.valueOf(connectionPoolConfig.getCachePrepStmts()));
        }
        if (connectionPoolConfig.getPrepStmtCacheSize() != null) {
            config.addDataSourceProperty("prepStmtCacheSize", String.valueOf(connectionPoolConfig.getPrepStmtCacheSize()));
        }
        if (connectionPoolConfig.getPrepStmtCacheSqlLimit() != null) {
            config.addDataSourceProperty("prepStmtCacheSqlLimit", String.valueOf(connectionPoolConfig.getPrepStmtCacheSqlLimit()));
        }

        return config;
    }
}
