package stroom.config.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.IsConfig;

import java.util.Objects;

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

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DbConfig dbConfig = (DbConfig) o;
        return connectionConfig.equals(dbConfig.connectionConfig) &&
                connectionPoolConfig.equals(dbConfig.connectionPoolConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionConfig, connectionPoolConfig);
    }
}
