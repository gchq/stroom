package stroom.persist;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class CoreConfig implements IsConfig {
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    private ConnectionPoolConfig connectionPoolConfig = new ConnectionPoolConfig();
    private HibernateConfig hibernateConfig = new HibernateConfig();
    private int databaseMultiInsertMaxBatchSize = 500;

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

    @JsonProperty("hibernate")
    public HibernateConfig getHibernateConfig() {
        return hibernateConfig;
    }

    public void setHibernateConfig(final HibernateConfig hibernateConfig) {
        this.hibernateConfig = hibernateConfig;
    }

    @JsonPropertyDescription("The maximum number of rows to insert in a single multi insert statement, e.g. INSERT INTO X VALUES (...), (...), (...)")
    public int getDatabaseMultiInsertMaxBatchSize() {
        return databaseMultiInsertMaxBatchSize;
    }

    public void setDatabaseMultiInsertMaxBatchSize(final int databaseMultiInsertMaxBatchSize) {
        this.databaseMultiInsertMaxBatchSize = databaseMultiInsertMaxBatchSize;
    }

    @Override
    public String toString() {
        return "CoreConfig{" +
                ", databaseMultiInsertMaxBatchSize=" + databaseMultiInsertMaxBatchSize +
                '}';
    }
}
