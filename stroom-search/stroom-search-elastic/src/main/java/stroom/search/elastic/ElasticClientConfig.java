package stroom.search.elastic;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ElasticClientConfig extends AbstractConfig implements IsStroomConfig {

    private final int maxConnectionsPerRoute;
    private final int maxConnections;

    public ElasticClientConfig() {
        maxConnectionsPerRoute = Rest5ClientBuilder.DEFAULT_MAX_CONN_PER_ROUTE;
        maxConnections = Rest5ClientBuilder.DEFAULT_MAX_CONN_TOTAL;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticClientConfig(@JsonProperty("maxConnectionsPerRoute") final int maxConnectionsPerRoute,
                               @JsonProperty("maxConnections") final int maxConnections) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
        this.maxConnections = maxConnections;
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
