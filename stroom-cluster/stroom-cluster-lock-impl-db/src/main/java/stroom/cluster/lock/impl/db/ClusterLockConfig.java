package stroom.cluster.lock.impl.db;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

public class ClusterLockConfig extends AbstractConfig implements HasDbConfig {

    private final ClusterLockDbConfig dbConfig;

    public ClusterLockConfig() {
        dbConfig = new ClusterLockDbConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ClusterLockConfig(@JsonProperty("db") final ClusterLockDbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Override
    @JsonProperty("db")
    public ClusterLockDbConfig getDbConfig() {
        return dbConfig;
    }

    public static class ClusterLockDbConfig extends AbstractDbConfig {

        public ClusterLockDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public ClusterLockDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
