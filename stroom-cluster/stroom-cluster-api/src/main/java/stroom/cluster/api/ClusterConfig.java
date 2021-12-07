package stroom.cluster.api;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ClusterConfig extends AbstractConfig {

    private final boolean clusterCallUseLocal;
    private final StroomDuration clusterCallReadTimeout;
    private final boolean clusterCallIgnoreSSLHostnameVerifier;
    private final StroomDuration clusterResponseTimeout;

    public ClusterConfig() {
        clusterCallUseLocal = true;
        clusterCallReadTimeout = StroomDuration.ofSeconds(30);
        clusterCallIgnoreSSLHostnameVerifier = true;
        clusterResponseTimeout = StroomDuration.ofSeconds(30);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ClusterConfig(
            @JsonProperty("clusterCallUseLocal") final boolean clusterCallUseLocal,
            @JsonProperty("clusterCallReadTimeout") final StroomDuration clusterCallReadTimeout,
            @JsonProperty("clusterCallIgnoreSSLHostnameVerifier") final boolean clusterCallIgnoreSSLHostnameVerifier,
            @JsonProperty("clusterResponseTimeout") final StroomDuration clusterResponseTimeout) {
        this.clusterCallUseLocal = clusterCallUseLocal;
        this.clusterCallReadTimeout = clusterCallReadTimeout;
        this.clusterCallIgnoreSSLHostnameVerifier = clusterCallIgnoreSSLHostnameVerifier;
        this.clusterResponseTimeout = clusterResponseTimeout;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Do local calls when calling our own local services (true is an optimisation)")
    public boolean isClusterCallUseLocal() {
        return clusterCallUseLocal;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Time before throwing read timeout")
    public StroomDuration getClusterCallReadTimeout() {
        return clusterCallReadTimeout;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("If cluster calls are using SSL then choose if we want to ignore host name " +
            "verification")
    public boolean isClusterCallIgnoreSSLHostnameVerifier() {
        return clusterCallIgnoreSSLHostnameVerifier;
    }

    @JsonPropertyDescription("Time before giving up on cluster results")
    public StroomDuration getClusterResponseTimeout() {
        return clusterResponseTimeout;
    }

    @Override
    public String toString() {
        return "ClusterConfig{" +
                "clusterCallUseLocal=" + clusterCallUseLocal +
                ", clusterCallReadTimeout='" + clusterCallReadTimeout + '\'' +
                ", clusterCallIgnoreSSLHostnameVerifier=" + clusterCallIgnoreSSLHostnameVerifier +
                ", clusterResponseTimeout='" + clusterResponseTimeout + '\'' +
                '}';
    }
}
