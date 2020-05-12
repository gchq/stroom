package stroom.config.common;

import stroom.util.config.FieldMapper;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

    /**
     * Creates a new {@link DbConfig} then deep copies in the values of
     * this, followed by the non-default values of otherDbConfig.
     * Thus allowing otherDbConfig to override values in this {@link CommonDbConfig}
     */
    @JsonIgnore
    public DbConfig mergeConfig(final DbConfig otherDbConfig) {
        final DbConfig mergedConfig = new DbConfig();
        final DbConfig vanillaConfig = new DbConfig();
        FieldMapper.copy(this, mergedConfig);
        FieldMapper.copyNonDefaults(otherDbConfig, mergedConfig, vanillaConfig);
        return mergedConfig;
    }

}
