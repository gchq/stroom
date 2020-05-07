package stroom.node.impl;

import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
public class NodeConfig extends AbstractConfig implements HasDbConfig {
    public static final String PROP_NAME_NAME = "name";
    public static final String PROP_NAME_STATUS = "status";

    private DbConfig dbConfig = new DbConfig();
    private StatusConfig statusConfig = new StatusConfig();

    private String nodeName = "tba";

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @NotNull
    @ReadOnly
    @JsonPropertyDescription("The name of the node to identify it in the cluster. " +
            "Should only be set per node in the application YAML config file")
    @JsonProperty(PROP_NAME_NAME)
    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
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
