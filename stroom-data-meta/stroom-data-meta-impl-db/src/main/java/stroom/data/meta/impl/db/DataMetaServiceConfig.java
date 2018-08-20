package stroom.data.meta.impl.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.properties.api.ConnectionConfig;
import stroom.properties.api.ConnectionPoolConfig;

import javax.inject.Singleton;

@Singleton
public class DataMetaServiceConfig {
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    private ConnectionPoolConfig connectionPoolConfig = new ConnectionPoolConfig();
    private MetaValueConfig metaValueConfig;

    @JsonProperty("connection")
    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(final ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    @JsonProperty("connectionPool")
    public ConnectionPoolConfig getConnectionPoolConfig() {
        return connectionPoolConfig;
    }

    public void setConnectionPoolConfig(final ConnectionPoolConfig connectionPoolConfig) {
        this.connectionPoolConfig = connectionPoolConfig;
    }

    public MetaValueConfig getMetaValueConfig() {
        return metaValueConfig;
    }

    public void setMetaValueConfig(final MetaValueConfig metaValueConfig) {
        this.metaValueConfig = metaValueConfig;
    }
}
