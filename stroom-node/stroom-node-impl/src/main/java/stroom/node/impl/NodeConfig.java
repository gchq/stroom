/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.node.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class NodeConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    public static final String PROP_NAME_NAME = "name";
    public static final String PROP_NAME_STATUS = "status";

    private final NodeDbConfig dbConfig;
    private final StatusConfig statusConfig;
    private final String nodeName;

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
    @JsonPropertyDescription(
            "The name of the node to identify it in the cluster. " +
            "Should only be set per node in the application YAML config file. The node name should not " +
            "be changed once set. This node name will be used in the 'receiptId' meta attribute so " +
            "if a stroom cluster is forwarding to another stroom cluster, then the " +
            "node name should be unique across all clusters involved.")
    @JsonProperty(PROP_NAME_NAME)
    public String getNodeName() {
        return nodeName;
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

    @BootStrapConfig
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
