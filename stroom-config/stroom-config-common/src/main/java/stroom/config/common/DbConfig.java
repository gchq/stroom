package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.IsConfig;

public class DbConfig implements IsConfig {

    private ConnectionConfig connectionConfig;
    private ConnectionPoolConfig connectionPoolConfig;

    public DbConfig() {
        this.connectionConfig = new ConnectionConfig();
        this.connectionPoolConfig = new ConnectionPoolConfig();
    }

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

    @Override
    public String toString() {
        return "DbConfig{" +
                "connectionConfig=" + connectionConfig +
                ", connectionPoolConfig=" + connectionPoolConfig +
                '}';
    }
}
