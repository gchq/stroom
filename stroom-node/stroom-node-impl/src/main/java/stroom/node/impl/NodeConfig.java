package stroom.node.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.validation.constraints.NotNull;

public class NodeConfig extends AbstractConfig implements HasDbConfig {

    public static final String PROP_NAME_NAME = "name";
    public static final String PROP_NAME_STATUS = "status";

    private final NodeDbConfig dbConfig;
    private final StatusConfig statusConfig;
    // TODO 29/11/2021 AT: Make immutable
    private String nodeName;

    public NodeConfig() {
        dbConfig = new NodeDbConfig();
        statusConfig = new StatusConfig();
        nodeName = "tba";
    }

    @JsonCreator
    public NodeConfig(@JsonProperty("db") final NodeDbConfig dbConfig,
                      @JsonProperty(PROP_NAME_STATUS) final StatusConfig statusConfig,
                      @JsonProperty(PROP_NAME_NAME) final String nodeName) {
        this.dbConfig = dbConfig;
        this.statusConfig = statusConfig;
        this.nodeName = nodeName;
    }

    @Override
    @JsonProperty("db")
    public NodeDbConfig getDbConfig() {
        return dbConfig;
    }

    @NotNull
    @ReadOnly
    @JsonPropertyDescription("The name of the node to identify it in the cluster. " +
            "Should only be set per node in the application YAML config file. The node name should not " +
            "be changed once set.")
    @JsonProperty(PROP_NAME_NAME)
    public String getNodeName() {
        return nodeName;
    }

    @Deprecated(forRemoval = true)
    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    @JsonProperty(PROP_NAME_STATUS)
    public StatusConfig getStatusConfig() {
        return statusConfig;
    }

    @Override
    public String toString() {
        return "NodeConfig{" +
                "nodeName='" + nodeName + '\'' +
                '}';
    }

    public static class NodeDbConfig extends AbstractDbConfig {

        public NodeDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public NodeDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
