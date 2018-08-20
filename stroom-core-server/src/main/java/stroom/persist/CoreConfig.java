package stroom.persist;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.properties.api.ConnectionConfig;
import stroom.properties.api.ConnectionPoolConfig;

import javax.inject.Singleton;

@Singleton
public class CoreConfig {
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    private ConnectionPoolConfig connectionPoolConfig = new ConnectionPoolConfig();
    private HibernateConfig hibernateConfig;
    private String node;
    private String rack;
    private String temp;
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

    public String getNode() {
        return node;
    }

    public void setNode(final String node) {
        this.node = node;
    }

    public String getRack() {
        return rack;
    }

    public void setRack(final String rack) {
        this.rack = rack;
    }

    @JsonPropertyDescription("Temp folder to write stuff to. Should only be set per node in application property file")
    public String getTemp() {
        return temp;
    }

    public void setTemp(final String temp) {
        this.temp = temp;
    }

    @JsonPropertyDescription("The maximum number of rows to insert in a single multi insert statement, e.g. INSERT INTO X VALUES (...), (...), (...)")
    public int getDatabaseMultiInsertMaxBatchSize() {
        return databaseMultiInsertMaxBatchSize;
    }

    public void setDatabaseMultiInsertMaxBatchSize(final int databaseMultiInsertMaxBatchSize) {
        this.databaseMultiInsertMaxBatchSize = databaseMultiInsertMaxBatchSize;
    }
}
