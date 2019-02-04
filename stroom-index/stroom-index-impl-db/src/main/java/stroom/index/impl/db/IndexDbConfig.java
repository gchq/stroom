package stroom.index.impl.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class IndexDbConfig implements IsConfig {
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    private ConnectionPoolConfig connectionPoolConfig = new ConnectionPoolConfig();

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

    public static class Builder {
        private final IndexDbConfig instance;

        public Builder(final IndexDbConfig instance) {
            this.instance = instance;
        }

        public Builder() {
            this(new IndexDbConfig());
        }

        public Builder withConnectionConfig(final ConnectionConfig value) {
            instance.setConnectionConfig(value);
            return this;
        }

        public Builder withConnectionPoolConfig(final ConnectionPoolConfig value) {
            instance.setConnectionPoolConfig(value);
            return this;
        }

        public IndexDbConfig build() {
            return instance;
        }
    }
}
