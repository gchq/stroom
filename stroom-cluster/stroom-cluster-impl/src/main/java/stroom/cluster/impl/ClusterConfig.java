package stroom.cluster.impl;

import stroom.cluster.api.ClusterRole;
import stroom.cluster.api.ClusterRoles;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class ClusterConfig extends AbstractConfig implements IsStroomConfig {

    private final String clusterName;
    private final List<String> clusterRoles;

    public ClusterConfig() {
        clusterName = "Stroom";
        clusterRoles = ClusterRoles.ALL_ROLES.stream().map(ClusterRole::getName).toList();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ClusterConfig(
            @JsonProperty("clusterName") final String clusterName,
            @JsonProperty("clusterRoles") final List<String> clusterRoles) {
        this.clusterName = clusterName;
        this.clusterRoles = clusterRoles;
    }

    @JsonPropertyDescription("The name of the cluster that this member should join")
    public String getClusterName() {
        return clusterName;
    }

    @JsonPropertyDescription("Roles for this node")
    public List<String> getClusterRoles() {
        return clusterRoles;
    }
}
