package stroom.config.common;

import stroom.util.config.FieldMapper;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.inject.Singleton;

@Singleton
public class CommonDbConfig extends DbConfig {

    private static final String DEFAULT_JDBC_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";
    private static final String DEFAULT_JDBC_DRIVER_URL =
            "jdbc:mysql://localhost:3307/stroom?useUnicode=yes&characterEncoding=UTF-8";
    private static final String DEFAULT_JDBC_DRIVER_USERNAME = "stroomuser";
    private static final String DEFAULT_JDBC_DRIVER_PASSWORD = "stroompassword1";

    public CommonDbConfig() {
        applyDefaultConnectionProperties(this);
    }

    /**
     * Creates a new {@link DbConfig} then deep copies in the values of
     * this, followed by values of otherDbConfig.
     * Thus allowing otherDbConfig to override values in this {@link CommonDbConfig}
     */
    @JsonIgnore
    public DbConfig mergeConfig(final DbConfig otherDbConfig) {
        final DbConfig mergedConfig = new DbConfig();

        // DbConfig has no default values so add them in
        applyDefaultConnectionProperties(mergedConfig);

        final ConnectionPoolConfig vanillaPoolConfig = new ConnectionPoolConfig();
        final ConnectionPoolConfig mergedPoolConfig = new ConnectionPoolConfig();
        // common trumps ctor defaults
        if (this.getConnectionPoolConfig() != null) {
            FieldMapper.copy(this.getConnectionPoolConfig(), mergedPoolConfig);
        }
        // otherDbConfig trumps common, but only if it is not a default value
        // (else a default in otherDbConfig will overwrite a non-default in common)
        FieldMapper.copyNonDefaults(otherDbConfig.getConnectionPoolConfig(),
                mergedPoolConfig,
                vanillaPoolConfig);

        // The ConnectionConfig is a bit different as it has no ctor defaults,
        // the defaults live in CommonDbConfig

        final ConnectionConfig mergedConnConfig = new ConnectionConfig();
        // common trumps ctor defaults, not that there are any
        if (this.getConnectionConfig() != null) {
            FieldMapper.copy(this.getConnectionConfig(), mergedConnConfig);
        }
        // otherDbConfig trumps common, unless it is null
        // We would never want to explicitly set a ConnectionConfig prop to null as we
        // need them all.
        FieldMapper.copyNonNulls(otherDbConfig.getConnectionConfig(), mergedConnConfig);

        mergedConfig.setConnectionPoolConfig(mergedPoolConfig);
        mergedConfig.setConnectionConfig(mergedConnConfig);

        return mergedConfig;
    }

    private void applyDefaultConnectionProperties(final DbConfig dbConfig) {
        // Set some default values.
        final ConnectionConfig connectionConfig = dbConfig.getConnectionConfig();
        connectionConfig.setClassName(DEFAULT_JDBC_DRIVER_CLASS_NAME);
        connectionConfig.setUrl(DEFAULT_JDBC_DRIVER_URL);
        connectionConfig.setUser(DEFAULT_JDBC_DRIVER_USERNAME);
        connectionConfig.setPassword(DEFAULT_JDBC_DRIVER_PASSWORD);
    }
}
