package stroom.legacy.db;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty;


@Deprecated
@JsonPropertyOrder(alphabetic = true)
public class LegacyConfig extends AbstractConfig implements HasDbConfig {

    private final LegacyDbConfig dbConfig;

    public LegacyConfig() {
        dbConfig = new LegacyDbConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public LegacyConfig(@JsonProperty("db") final LegacyDbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Override
    @JsonProperty("db")
    public LegacyDbConfig getDbConfig() {
        return dbConfig;
    }


    @Deprecated
    public static class LegacyDbConfig extends AbstractDbConfig {

        public LegacyDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public LegacyDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
