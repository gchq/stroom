package stroom.dashboard.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class DashboardConfig extends AbstractConfig {
    private CacheConfig activeQueriesCache = new CacheConfig.Builder()
            .maximumSize(100L)
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build();

    public CacheConfig getActiveQueriesCache() {
        return activeQueriesCache;
    }

    public void setActiveQueriesCache(final CacheConfig activeQueriesCache) {
        this.activeQueriesCache = activeQueriesCache;
    }

    @Override
    public String toString() {
        return "DashboardConfig{" +
                "activeQueriesCache=" + activeQueriesCache +
                '}';
    }
}
