package stroom.cluster.api;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;

@Singleton
public class ClusterConfig extends AbstractConfig {
    private boolean clusterCallUseLocal = true;
    private StroomDuration clusterCallReadTimeout = StroomDuration.ofSeconds(30);
    private boolean clusterCallIgnoreSSLHostnameVerifier = true;
    private StroomDuration clusterResponseTimeout = StroomDuration.ofSeconds(30);

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Do local calls when calling our own local services (true is an optimisation)")
    public boolean isClusterCallUseLocal() {
        return clusterCallUseLocal;
    }

    @SuppressWarnings("unused")
    public void setClusterCallUseLocal(final boolean clusterCallUseLocal) {
        this.clusterCallUseLocal = clusterCallUseLocal;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Time before throwing read timeout")
    public StroomDuration getClusterCallReadTimeout() {
        return clusterCallReadTimeout;
    }

    @SuppressWarnings("unused")
    public void setClusterCallReadTimeout(final StroomDuration clusterCallReadTimeout) {
        this.clusterCallReadTimeout = clusterCallReadTimeout;
    }

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("If cluster calls are using SSL then choose if we want to ignore host name " +
            "verification")
    public boolean isClusterCallIgnoreSSLHostnameVerifier() {
        return clusterCallIgnoreSSLHostnameVerifier;
    }

    @SuppressWarnings("unused")
    public void setClusterCallIgnoreSSLHostnameVerifier(final boolean clusterCallIgnoreSSLHostnameVerifier) {
        this.clusterCallIgnoreSSLHostnameVerifier = clusterCallIgnoreSSLHostnameVerifier;
    }

    @JsonPropertyDescription("Time before giving up on cluster results")
    public StroomDuration getClusterResponseTimeout() {
        return clusterResponseTimeout;
    }

    @SuppressWarnings("unused")
    public void setClusterResponseTimeout(final StroomDuration clusterResponseTimeout) {
        this.clusterResponseTimeout = clusterResponseTimeout;
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
