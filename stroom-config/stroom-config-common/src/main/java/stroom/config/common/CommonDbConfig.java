package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class CommonDbConfig extends AbstractDbConfig {

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

    @SuppressWarnings("unused")
    @JsonCreator
    public CommonDbConfig(@JsonProperty("connection") final ConnectionConfig connectionConfig,
                          @JsonProperty("connectionPool") final ConnectionPoolConfig connectionPoolConfig) {
        super(connectionConfig, connectionPoolConfig);
    }

    /**
     * Creates a new {@link AbstractDbConfig} then deep copies in the values of
     * this, followed by values of otherDbConfig.
     * Thus allowing otherDbConfig to override values in this {@link CommonDbConfig}
     */
    @JsonIgnore
    public AbstractDbConfig mergeConfig(final AbstractDbConfig otherDbConfig) {

//        // These will contain all hard coded defaults
//        final DbConfig outputConfig = new CommonDbConfig();
//        final DbConfig vanillaConfig = new CommonDbConfig();
//
//        // If the custom config has been set in common config then copy it over
//        FieldMapper.copyNonDefaults(this, outputConfig, vanillaConfig);
//        // If the custom config has been set in the module specific config then copy it over
//        FieldMapper.copyNonDefaults(otherDbConfig, outputConfig, vanillaConfig);

//        final DbConfig outputConfig = new DbConfig();

        // A new instance of ConnectionConfig has all nulls so use defaults()
        final ConnectionConfig defaultConnectionConfig = ConnectionConfig.defaults();

        ConnectionConfig mergedConnectionConfig = defaultConnectionConfig.merge(this.getConnectionConfig());
        mergedConnectionConfig = mergedConnectionConfig.merge(otherDbConfig.getConnectionConfig());


        final ConnectionPoolConfig defaultPoolConfig = new ConnectionPoolConfig();

        // common trumps ctor defaults
        ConnectionPoolConfig mergedPoolConfig = defaultPoolConfig.merge(
                this.getConnectionPoolConfig(), true, false);

        // merge non-null values from the module specific config over the common ones.
        mergedPoolConfig = mergedPoolConfig.merge(
                otherDbConfig.getConnectionPoolConfig(),
                false,
                false);

        return new MergedDbConfig(
                mergedConnectionConfig,
                mergedPoolConfig,
                otherDbConfig.getClass().getSimpleName());
    }

//    private void applyDefaultConnectionProperties(final ConnectionConfig connectionConfig) {
//        // Set some default values.
//        connectionConfig.setClassName(ConnectionConfig.DEFAULT_JDBC_DRIVER_CLASS_NAME);
//        connectionConfig.setUrl(ConnectionConfig.DEFAULT_JDBC_DRIVER_URL);
//        connectionConfig.setUser(ConnectionConfig.DEFAULT_JDBC_DRIVER_USERNAME);
//        connectionConfig.setPassword(ConnectionConfig.DEFAULT_JDBC_DRIVER_PASSWORD);
//    }

    public static class MergedDbConfig extends AbstractDbConfig {

        private final String moduleName;

        public MergedDbConfig(final String moduleName) {
            this.moduleName = moduleName;
        }

        @JsonCreator
        public MergedDbConfig(
                @JsonProperty("connectionConfig") final ConnectionConfig connectionConfig,
                @JsonProperty("connectionPoolConfig") final ConnectionPoolConfig connectionPoolConfig,
                final String moduleName) {
            super(connectionConfig, connectionPoolConfig);
            this.moduleName = moduleName;
        }

        public String getModuleName() {
            return moduleName;
        }
    }
}
