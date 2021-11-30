package stroom.config.app;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

public class PropertyServiceConfig extends AbstractConfig implements HasDbConfig {

    private final PropertyServiceDbConfig dbConfig;

    public PropertyServiceConfig() {
        dbConfig = new PropertyServiceDbConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public PropertyServiceConfig(@JsonProperty("db") final PropertyServiceDbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Override
    @JsonProperty("db")
    public PropertyServiceDbConfig getDbConfig() {
        return dbConfig;
    }

    public static class PropertyServiceDbConfig extends AbstractDbConfig {

        public PropertyServiceDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public PropertyServiceDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
