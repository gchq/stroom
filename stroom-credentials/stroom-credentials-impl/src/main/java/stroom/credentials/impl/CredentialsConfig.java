package stroom.credentials.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class CredentialsConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    public static final String DEFAULT_KEY_STORE_CACHE_PATH = "${stroom.home}/keystores";

    /**
     * Database config
     */
    private final CredentialsDbConfig dbConfig;
    private final String keyStoreCachePath;

    /**
     * Default constructor. Configuration created with default values.
     */
    public CredentialsConfig() {
        dbConfig = new CredentialsDbConfig();
        keyStoreCachePath = DEFAULT_KEY_STORE_CACHE_PATH;
    }

    /**
     * Constructor called when creating configuration from JSON or YAML.
     * @param dbConfig The DB configuration.
     */
    @SuppressWarnings("unused")
    @JsonCreator
    public CredentialsConfig(@JsonProperty("db") final CredentialsDbConfig dbConfig,
                             @JsonProperty("keyStoreCachePath") final String keyStoreCachePath) {
        this.dbConfig = dbConfig;
        this.keyStoreCachePath = keyStoreCachePath;
    }

    @Override
    @JsonProperty("db")
    public CredentialsDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonProperty
    @JsonPropertyDescription("The path to stored cached key stores.")
    public String getKeyStoreCachePath() {
        return keyStoreCachePath;
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

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CredentialsConfig that = (CredentialsConfig) o;
        return Objects.equals(dbConfig, that.dbConfig) && Objects.equals(keyStoreCachePath,
                that.keyStoreCachePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dbConfig, keyStoreCachePath);
    }

    @Override
    public String toString() {
        return "CredentialsConfig{" +
               "dbConfig=" + dbConfig +
               ", keyStoreCachePath='" + keyStoreCachePath + '\'' +
               '}';
    }
}
