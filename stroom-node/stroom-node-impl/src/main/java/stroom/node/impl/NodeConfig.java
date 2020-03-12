package stroom.node.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.config.common.NodeEndpointConfig;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
public class NodeConfig extends AbstractConfig implements HasDbConfig {

    public static final String PROP_NAME_NAME = "name";
    public static final String PROP_NAME_STATUS = "status";
    public static final String PROP_NAME_BASE_ENDPOINT = "baseEndpoint";

    private DbConfig dbConfig = new DbConfig();
    private String nodeName = "tba";
    private NodeEndpointConfig baseEndpoint = new NodeEndpointConfig();
    private StatusConfig statusConfig = new StatusConfig();

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @NotNull
    @ReadOnly
    @JsonPropertyDescription("Should only be set per node in the application YAML config file")
    @JsonProperty(PROP_NAME_NAME)
    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    @JsonPropertyDescription("This is the base endpoint of the node for all inter-node communications, " +
        "i.e. all cluster management and node info calls. " +
        "This endpoint will typically be hidden behind a firewall and not be publicly available. " +
        "The address must be resolvable from all other nodes in the cluster. " +
        "This does not need to be set for a single node cluster.")
    @JsonProperty(PROP_NAME_BASE_ENDPOINT)
    public NodeEndpointConfig getBaseEndpoint() {
        return baseEndpoint;
    }

    public void setBaseEndpoint(final NodeEndpointConfig baseEndpoint) {
        this.baseEndpoint = baseEndpoint;
    }

    @JsonProperty(PROP_NAME_STATUS)
    public StatusConfig getStatusConfig() {
        return statusConfig;
    }

    public void setStatusConfig(final StatusConfig statusConfig) {
        this.statusConfig = statusConfig;
    }

    @Override
    public String toString() {
        return "NodeConfig{" +
                "nodeName='" + nodeName + '\'' +
                '}';
    }
}
