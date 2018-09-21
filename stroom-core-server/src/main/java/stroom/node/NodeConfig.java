package stroom.node;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NodeConfig {
    private String nodeName = "tba";
    private String rackName = "tba";
    private StatusConfig statusConfig;

    public NodeConfig() {
        this.statusConfig = new StatusConfig();
    }

    @Inject
    public NodeConfig(final StatusConfig statusConfig) {
        this.statusConfig = statusConfig;
    }

    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Should only be set per node in application property file")
    @JsonProperty("node")
    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    @ReadOnly
    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Should only be set per node in application property file")
    @JsonProperty("rack")
    public String getRackName() {
        return rackName;
    }

    public void setRackName(final String rackName) {
        this.rackName = rackName;
    }

    @JsonProperty("status")
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
                ", rackName='" + rackName + '\'' +
                '}';
    }
}
