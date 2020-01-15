package stroom.node.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.config.common.DbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class NodeConfig implements IsConfig, HasDbConfig {
    private DbConfig dbConfig = new DbConfig();
    private String nodeName = "tba";
    private StatusConfig statusConfig = new StatusConfig();

    @JsonProperty("db")
    public DbConfig getDbConfig() {
        return dbConfig;
    }

    public void setDbConfig(final DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @ReadOnly
    @JsonPropertyDescription("Should only be set per node in application property file")
    @JsonProperty("node")
    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
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
                '}';
    }
}
