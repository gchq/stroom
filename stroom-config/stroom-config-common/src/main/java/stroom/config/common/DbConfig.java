package stroom.config.common;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@NotInjectableConfig
@JsonPropertyOrder({
        DbConfig.PROP_NAME_CONNECTION,
        DbConfig.PROP_NAME_CONNECTION_POOL
})
public class DbConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_CONNECTION = "connection";
    public static final String PROP_NAME_CONNECTION_POOL = "connectionPool";

    private ConnectionConfig connectionConfig;
    private ConnectionPoolConfig connectionPoolConfig;

    public DbConfig() {
        connectionConfig = new ConnectionConfig();
        connectionPoolConfig = new ConnectionPoolConfig();
    }

    public DbConfig(final ConnectionConfig connectionConfig,
                    final ConnectionPoolConfig connectionPoolConfig) {
        this.connectionConfig = connectionConfig;
        this.connectionPoolConfig = connectionPoolConfig;
    }

    @JsonProperty(PROP_NAME_CONNECTION)
    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    @SuppressWarnings("unused")
    public void setConnectionConfig(final ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    @JsonProperty(PROP_NAME_CONNECTION_POOL)
    public ConnectionPoolConfig getConnectionPoolConfig() {
        return connectionPoolConfig;
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("checkstyle:needbraces")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DbConfig dbConfig = (DbConfig) o;
        return connectionConfig.equals(dbConfig.connectionConfig) &&
                connectionPoolConfig.equals(dbConfig.connectionPoolConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionConfig, connectionPoolConfig);
    }
}
