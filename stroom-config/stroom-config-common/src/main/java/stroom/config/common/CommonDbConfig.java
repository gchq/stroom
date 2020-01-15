package stroom.config.common;

import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class CommonDbConfig extends DbConfig {
    private static final String DEFAULT_JDBC_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";
    private static final String DEFAULT_JDBC_DRIVER_URL = "jdbc:mysql://localhost:3307/stroom?useUnicode=yes&characterEncoding=UTF-8";
    private static final String DEFAULT_JDBC_DRIVER_USERNAME = "stroomuser";
    private static final String DEFAULT_JDBC_DRIVER_PASSWORD = "stroompassword1";

    public CommonDbConfig() {
        // Set some default values.
        final ConnectionConfig connectionConfig = getConnectionConfig();
        connectionConfig.setJdbcDriverClassName(DEFAULT_JDBC_DRIVER_CLASS_NAME);
        connectionConfig.setJdbcDriverUrl(DEFAULT_JDBC_DRIVER_URL);
        connectionConfig.setJdbcDriverUsername(DEFAULT_JDBC_DRIVER_USERNAME);
        connectionConfig.setJdbcDriverPassword(DEFAULT_JDBC_DRIVER_PASSWORD);
    }
}
