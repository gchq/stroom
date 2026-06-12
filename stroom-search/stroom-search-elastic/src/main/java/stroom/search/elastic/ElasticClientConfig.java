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

package stroom.search.elastic;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class ElasticClientConfig extends AbstractConfig implements IsStroomConfig {

    private static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = Rest5ClientBuilder.DEFAULT_MAX_CONN_PER_ROUTE;
    private static final int DEFAULT_MAX_CONNECTIONS = Rest5ClientBuilder.DEFAULT_MAX_CONN_TOTAL;

    private final int maxConnectionsPerRoute;
    private final int maxConnections;

    public ElasticClientConfig() {
        maxConnectionsPerRoute = DEFAULT_MAX_CONNECTIONS_PER_ROUTE;
        maxConnections = DEFAULT_MAX_CONNECTIONS;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticClientConfig(@JsonProperty("maxConnectionsPerRoute") final Integer maxConnectionsPerRoute,
                               @JsonProperty("maxConnections") final Integer maxConnections) {
        this.maxConnectionsPerRoute = Objects.requireNonNullElse(maxConnectionsPerRoute, DEFAULT_MAX_CONNECTIONS_PER_ROUTE);
        this.maxConnections = Objects.requireNonNullElse(maxConnections, DEFAULT_MAX_CONNECTIONS);
    }

    @JsonPropertyDescription("Maximum number of connections maintained by the Elastic Java client pool on a per-" +
            "route basis. This should be set to at least the number of concurrent indexing tasks you expect each " +
            "node to be performing against a given index.")
    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    @JsonPropertyDescription("Total number of connections maintained by the Elastic Java client pool.")
    public int getMaxConnections() {
        return maxConnections;
    }

    @Override
    public String toString() {
        return "ElasticClientConfig{" +
                "maxConnectionsPerRoute=" + maxConnectionsPerRoute +
                ", maxConnections=" + maxConnections +
                "}";
    }
}
