package stroom.annotation.impl.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.util.config.StroomProperties;

import javax.inject.Singleton;

@Singleton
public class AnnotationDbConfig {
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    private ConnectionPoolConfig connectionPoolConfig = new ConnectionPoolConfig();

    public AnnotationDbConfig() {
        connectionConfig.setJdbcDriverClassName(StroomProperties.getProperty("stroom.jdbcDriverClassName"));
        connectionConfig.setJdbcDriverUrl(StroomProperties.getProperty("stroom.jdbcDriverUrl|trace"));
        connectionConfig.setJdbcDriverUsername(StroomProperties.getProperty("stroom.jdbcDriverUsername"));
        connectionConfig.setJdbcDriverPassword(StroomProperties.getProperty("stroom.jdbcDriverPassword"));
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
}
