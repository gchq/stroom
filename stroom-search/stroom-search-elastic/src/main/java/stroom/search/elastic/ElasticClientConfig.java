/*
 * Copyright 2022 Crown Copyright
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
    private static final boolean DEFAULT_COMPRESS_REQUESTS = false;

    private final int maxConnectionsPerRoute;
    private final int maxConnections;
    private final boolean compressRequests;

    public ElasticClientConfig() {
        maxConnectionsPerRoute = DEFAULT_MAX_CONNECTIONS_PER_ROUTE;
        maxConnections = DEFAULT_MAX_CONNECTIONS;
        compressRequests = DEFAULT_COMPRESS_REQUESTS;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticClientConfig(@JsonProperty("maxConnectionsPerRoute") final Integer maxConnectionsPerRoute,
                               @JsonProperty("maxConnections") final Integer maxConnections,
                               @JsonProperty("compressRequests") final Boolean compressRequests) {
        this.maxConnectionsPerRoute =
                Objects.requireNonNullElse(maxConnectionsPerRoute, DEFAULT_MAX_CONNECTIONS_PER_ROUTE);
        this.maxConnections =
                Objects.requireNonNullElse(maxConnections, DEFAULT_MAX_CONNECTIONS);
        this.compressRequests =
                Objects.requireNonNullElse(compressRequests, DEFAULT_COMPRESS_REQUESTS);
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

    /// @return Whether the Elastic Java client compresses request bodies with gzip.
    @JsonPropertyDescription("Whether to compress the body of each request to Elasticsearch with gzip, and accept " +
            "compressed responses. Bulk indexing requests usually compress to a fraction of their size, which " +
            "helps where the network between Stroom and the cluster is slow, at the cost of some CPU on both.")
    public boolean isCompressRequests() {
        return compressRequests;
    }

    @Override
    public String toString() {
        return "ElasticClientConfig{" +
                "maxConnectionsPerRoute=" + maxConnectionsPerRoute +
                ", maxConnections=" + maxConnections +
                ", compressRequests=" + compressRequests +
                "}";
    }
}
