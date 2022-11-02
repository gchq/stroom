package stroom.cluster.lock.impl.db;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ClusterLockConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final ClusterLockDbConfig dbConfig;
    private final StroomDuration lockTimeout;

    public ClusterLockConfig() {
        dbConfig = new ClusterLockDbConfig();
        lockTimeout = StroomDuration.ofMinutes(10);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ClusterLockConfig(@JsonProperty("db") final ClusterLockDbConfig dbConfig,
                             @JsonProperty("lockTimeout") final StroomDuration lockTimeout) {
        this.dbConfig = dbConfig;
        this.lockTimeout = lockTimeout;
    }

    @Override
    @JsonProperty("db")
    public ClusterLockDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonProperty("lockTimeout")
    @JsonPropertyDescription("The minimum period of time to keep trying to obtain a cluster lock for. " +
            "This allows a timeout to be set that is greater than that configured on the database, but not less. ")
    public StroomDuration getLockTimeout() {
        return lockTimeout;
    }

    @BootStrapConfig
    public static class ClusterLockDbConfig extends AbstractDbConfig implements IsStroomConfig {

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

    @Override
    public String toString() {
        return "ClusterLockConfig{" +
                "dbConfig=" + dbConfig +
                ", lockTimeout=" + lockTimeout +
                '}';
    }
}
