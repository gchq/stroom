package stroom.core.receive;

import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.CleanupConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
public class ProxyAggregationConfig extends AbstractConfig {

    private ProxyRepoConfig proxyRepoConfig = new ProxyRepoConfig();
    private AggregatorConfig aggregatorConfig = new AggregatorConfig();
    private CleanupConfig cleanupConfig = new CleanupConfig();

    public ProxyRepoConfig getProxyRepoConfig() {
        return proxyRepoConfig;
    }

    public void setProxyRepoConfig(final ProxyRepoConfig proxyRepoConfig) {
        this.proxyRepoConfig = proxyRepoConfig;
    }

    public AggregatorConfig getAggregatorConfig() {
        return aggregatorConfig;
    }

    public void setAggregatorConfig(final AggregatorConfig aggregatorConfig) {
        this.aggregatorConfig = aggregatorConfig;
    }

    public CleanupConfig getCleanupConfig() {
        return cleanupConfig;
    }

    public void setCleanupConfig(final CleanupConfig cleanupConfig) {
        this.cleanupConfig = cleanupConfig;
    }
}
