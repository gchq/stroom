package stroom.cluster.task.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;

@Singleton
public class ClusterTaskConfig extends AbstractConfig {
    public static final String NAME = "clusterTask";
    
    private CacheConfig clusterResultCollectorCache = new CacheConfig.Builder()
            .maximumSize(1000000L)
            .expireAfterAccess(StroomDuration.ofMinutes(1))
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