package stroom.cluster.api;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Objects;
import javax.inject.Singleton;

@Singleton
public class ClusterConfig extends AbstractConfig {

    private static final Boolean CLUSTER_CALL_USE_LOCAL_DEFAULT = Boolean.TRUE;
    private static final Boolean CLUSTER_CALL_IGNORE_SSL_HOSTNAME_VERIFIER_DEFAULT = Boolean.TRUE;

    private Boolean clusterCallUseLocal = CLUSTER_CALL_USE_LOCAL_DEFAULT;
    private StroomDuration clusterCallReadTimeout = StroomDuration.ofSeconds(30);
    private Boolean clusterCallIgnoreSSLHostnameVerifier = CLUSTER_CALL_IGNORE_SSL_HOSTNAME_VERIFIER_DEFAULT;
    private StroomDuration clusterResponseTimeout = StroomDuration.ofSeconds(30);

    @RequiresRestart(RequiresRestart.RestartScope.SYSTEM)
    @JsonPropertyDescription("Do local calls when calling our own local services (true is an optimisation)")
    public boolean isClusterCallUseLocal() {
        return Objects.requireNonNullElse(clusterCallUseLocal, CLUSTER_CALL_USE_LOCAL_DEFAULT);
    }

    @SuppressWarnings("unused")
    public void setClusterCallUseLocal(final Boolean clusterCallUseLocal) {
        this.clusterCallUseLocal = Objects.requireNonNullElse(clusterCallUseLocal, CLUSTER_CALL_USE_LOCAL_DEFAULT);
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
        return Objects.requireNonNullElse(
                clusterCallIgnoreSSLHostnameVerifier,
                CLUSTER_CALL_IGNORE_SSL_HOSTNAME_VERIFIER_DEFAULT);
    }

    @SuppressWarnings("unused")
    public void setClusterCallIgnoreSSLHostnameVerifier(final Boolean clusterCallIgnoreSSLHostnameVerifier) {
        this.clusterCallIgnoreSSLHostnameVerifier = Objects.requireNonNullElse(
                clusterCallIgnoreSSLHostnameVerifier, CLUSTER_CALL_IGNORE_SSL_HOSTNAME_VERIFIER_DEFAULT);
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
