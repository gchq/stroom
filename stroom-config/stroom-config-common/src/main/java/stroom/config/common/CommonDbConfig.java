package stroom.config.common;

import stroom.util.config.FieldMapper;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.inject.Singleton;

@Singleton
public class CommonDbConfig extends DbConfig {

    public CommonDbConfig() {
        // IMPORTANT - setting the defaults in this way means that they will be ignored by
        // jackson when it de-serialises the yaml. We rely on mergeConfig() being aware of these
        // defaults and factoring them in when merging config objects.
        super(new ConnectionConfig(
                        ConnectionConfig.DEFAULT_JDBC_DRIVER_CLASS_NAME,
                        ConnectionConfig.DEFAULT_JDBC_DRIVER_URL,
                        ConnectionConfig.DEFAULT_JDBC_DRIVER_USERNAME,
                        ConnectionConfig.DEFAULT_JDBC_DRIVER_PASSWORD),
                new ConnectionPoolConfig());
    }

    /**
     * Creates a new {@link DbConfig} then deep copies in the values of
     * this, followed by values of otherDbConfig.
     * Thus allowing otherDbConfig to override values in this {@link CommonDbConfig}
     */
    @JsonIgnore
    public DbConfig mergeConfig(final DbConfig otherDbConfig) {

//        // These will contain all hard coded defaults
//        final DbConfig outputConfig = new CommonDbConfig();
//        final DbConfig vanillaConfig = new CommonDbConfig();
//
//        // If the custom config has been set in common config then copy it over
//        FieldMapper.copyNonDefaults(this, outputConfig, vanillaConfig);
//        // If the custom config has been set in the module specific config then copy it over
//        FieldMapper.copyNonDefaults(otherDbConfig, outputConfig, vanillaConfig);

        final DbConfig outputConfig = new DbConfig();

        // DbConfig has no default values so add them in
        applyDefaultConnectionProperties(outputConfig.getConnectionConfig());

        final ConnectionPoolConfig vanillaPoolConfig = new ConnectionPoolConfig();
        final ConnectionPoolConfig mergedPoolConfig = outputConfig.getConnectionPoolConfig();
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
        // the defaults live in CommonDbConfig. This is so the user doesn't see a load
        // of defaults against the non-common db connection props in the UI and get confused.
        // Cleaner to have no defaults and deal with things in this merege.

        final ConnectionConfig mergedConnConfig = outputConfig.getConnectionConfig();
        // common trumps ctor defaults, not that there are any
        if (this.getConnectionConfig() != null) {
            // We would never want to explicitly set a ConnectionConfig prop to null as we
            // need them all.
            FieldMapper.copyNonNulls(this.getConnectionConfig(), mergedConnConfig);
        }
        // otherDbConfig trumps common
        if (otherDbConfig.getConnectionConfig() != null) {
            // We would never want to explicitly set a ConnectionConfig prop to null as we
            // need them all.
            FieldMapper.copyNonNulls(otherDbConfig.getConnectionConfig(), mergedConnConfig);
        }

        return outputConfig;
    }

    private void applyDefaultConnectionProperties(final ConnectionConfig connectionConfig) {
        // Set some default values.
        connectionConfig.setClassName(ConnectionConfig.DEFAULT_JDBC_DRIVER_CLASS_NAME);
        connectionConfig.setUrl(ConnectionConfig.DEFAULT_JDBC_DRIVER_URL);
        connectionConfig.setUser(ConnectionConfig.DEFAULT_JDBC_DRIVER_USERNAME);
        connectionConfig.setPassword(ConnectionConfig.DEFAULT_JDBC_DRIVER_PASSWORD);
    }
}
