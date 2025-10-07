package stroom.credentials.api;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class CredentialsConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    /**
     * Database config
     */
    private final CredentialsDbConfig dbConfig;

    /**
     * Default constructor. Configuration created with default values.
     */
    public CredentialsConfig() {
        dbConfig = new CredentialsDbConfig();
    }

    /**
     * Constructor called when creating configuration from JSON or YAML.
     * @param dbConfig The DB configuration.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public CredentialsConfig(@JsonProperty("db") final CredentialsDbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Override
    @JsonProperty("db")
    public CredentialsDbConfig getDbConfig() {
        return dbConfig;
    }

    /**
     * DB configuration class.
     */
    @BootStrapConfig
    public static class CredentialsDbConfig extends AbstractDbConfig {

        /**
         * Default constructor called from CredentialsConfig default constructor.
         */
        public CredentialsDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public CredentialsDbConfig(
                @JsonProperty(AbstractDbConfig.PROP_NAME_CONNECTION)
                final ConnectionConfig connectionConfig,
                @JsonProperty(AbstractDbConfig.PROP_NAME_CONNECTION_POOL)
                final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
