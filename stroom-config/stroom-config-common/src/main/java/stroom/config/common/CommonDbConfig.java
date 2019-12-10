package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

import static java.util.concurrent.TimeUnit.MINUTES;

@Singleton
@JsonInclude(Include.NON_DEFAULT)
public class CommonDbConfig extends DbConfig implements IsConfig {
    private static final String DEFAULT_JDBC_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";
    private static final String DEFAULT_JDBC_DRIVER_URL = "jdbc:mysql://localhost:3307/stroom?useUnicode=yes&characterEncoding=UTF-8";
    private static final String DEFAULT_JDBC_DRIVER_USERNAME = "stroomuser";
    private static final String DEFAULT_JDBC_DRIVER_PASSWORD = "stroompassword1";

    private static final boolean DEFAULT_CACHE_PREP_STMTS = true;
    private static final int DEFAULT_PREP_STMT_CACHE_SIZE = 250;
    private static final int DEFAULT_PREP_STMT_CACHE_SQL_LIMIT = 2048;
    //    private static final long DEFAULT_CONNECTION_TIMEOUT = SECONDS.toMillis(30);
    //    private static final long DEFAULT_VALIDATION_TIMEOUT = SECONDS.toMillis(5);
    private static final long DEFAULT_IDLE_TIMEOUT = MINUTES.toMillis(10);
    private static final long DEFAULT_MAX_LIFETIME = MINUTES.toMillis(30);
    private static final int DEFAULT_MAX_POOL_SIZE = 10;

    public CommonDbConfig() {
        // Set some default values.
        final ConnectionConfig connectionConfig = getConnectionConfig();
        connectionConfig.setJdbcDriverClassName(DEFAULT_JDBC_DRIVER_CLASS_NAME);
        connectionConfig.setJdbcDriverUrl(DEFAULT_JDBC_DRIVER_URL);
        connectionConfig.setJdbcDriverUsername(DEFAULT_JDBC_DRIVER_USERNAME);
        connectionConfig.setJdbcDriverPassword(DEFAULT_JDBC_DRIVER_PASSWORD);

        final ConnectionPoolConfig connectionPoolConfig = getConnectionPoolConfig();
        connectionPoolConfig.setCachePrepStmts(DEFAULT_CACHE_PREP_STMTS);
        connectionPoolConfig.setPrepStmtCacheSize(DEFAULT_PREP_STMT_CACHE_SIZE);
        connectionPoolConfig.setPrepStmtCacheSqlLimit(DEFAULT_PREP_STMT_CACHE_SQL_LIMIT);
        connectionPoolConfig.setIdleTimeout(DEFAULT_IDLE_TIMEOUT);
        connectionPoolConfig.setMaxLifetime(DEFAULT_MAX_LIFETIME);
        connectionPoolConfig.setMaxPoolSize(DEFAULT_MAX_POOL_SIZE);
    }
}
