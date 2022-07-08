package stroom.cluster.impl;

import stroom.cluster.api.ClusterRole;
import stroom.cluster.api.ClusterRoles;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class ClusterConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_NAME = "name";

    // TODO 29/11/2021 AT: Make immutable
    private String nodeName;

    private final List<String> clusterRoles;

    public ClusterConfig() {
        nodeName = "tba";
        clusterRoles = ClusterRoles.ALL_ROLES.stream().map(ClusterRole::getName).toList();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ClusterConfig(
            @JsonProperty(PROP_NAME_NAME) final String nodeName,
            @JsonProperty("clusterRoles") final List<String> clusterRoles) {
        this.nodeName = nodeName;
        this.clusterRoles = clusterRoles;
    }

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

    @JsonPropertyDescription("Roles for this node")
    public List<String> getClusterRoles() {
        return clusterRoles;
    }
}
