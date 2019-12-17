package stroom.cluster.task.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class ClusterTaskConfig implements IsConfig {
    private CacheConfig clusterResultCollectorCache = new CacheConfig.Builder()
            .maximumSize(1000000L)
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build();

    public CacheConfig getClusterResultCollectorCache() {
        return clusterResultCollectorCache;
    }

    public void setClusterResultCollectorCache(final CacheConfig clusterResultCollectorCache) {
        this.clusterResultCollectorCache = clusterResultCollectorCache;
    }

    @Override
    public String toString() {
        return "ClusterTaskConfig{" +
                "clusterResultCollectorCache=" + clusterResultCollectorCache +
                '}';
    }
}