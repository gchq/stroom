package stroom.core.receive;

import stroom.proxy.repo.AggregatorConfig;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class ProxyAggregationConfig extends AbstractConfig {

    private String proxyDir = "proxy_repo";
    private volatile int proxyThreads = 10;

    private AggregatorConfig aggregatorConfig = new AggregatorConfig();

    @JsonPropertyDescription("Folder to look for Stroom Proxy Content to aggregate. If the value is a " +
            "relative path then it will be treated as being relative to stroom.path.home.")
    public String getProxyDir() {
        return proxyDir;
    }

    public void setProxyDir(final String proxyDir) {
        this.proxyDir = proxyDir;
    }

    @JsonPropertyDescription("Number of threads used in aggregation")
    public int getProxyThreads() {
        return proxyThreads;
    }

    public void setProxyThreads(final int proxyThreads) {
        this.proxyThreads = proxyThreads;
    }

    @JsonProperty("aggregate")
    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    public void setAggregatorConfig(final AggregatorConfig aggregatorConfig) {
        this.aggregatorConfig = aggregatorConfig;
    }

    @Override
    public String toString() {
        return "ProxyAggregationConfig{" +
                "proxyDir='" + proxyDir + '\'' +
                ", proxyThreads=" + proxyThreads +
                ", aggregatorConfig=" + aggregatorConfig +
                '}';
    }
}
