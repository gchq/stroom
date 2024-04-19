package stroom.config.app;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CrossModuleDbConfig extends AbstractDbConfig {

    public CrossModuleDbConfig() {
        super();
    }

    @JsonCreator
    public CrossModuleDbConfig(
            @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
            @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
        super(connectionConfig, connectionPoolConfig);
    }
}
