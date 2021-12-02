package stroom.dashboard.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class DashboardConfig extends AbstractConfig {

    @JsonProperty
    private final CacheConfig activeQueriesCache;

    public DashboardConfig() {
        activeQueriesCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterAccess(StroomDuration.ofMinutes(1))
                .build();
    }

    @JsonCreator
    public DashboardConfig(@JsonProperty("activeQueriesCache") final CacheConfig activeQueriesCache) {
        this.activeQueriesCache = activeQueriesCache;
    }

    public CacheConfig getActiveQueriesCache() {
        return activeQueriesCache;
    }

    @Override
    public String toString() {
        return "DashboardConfig{" +
                "activeQueriesCache=" + activeQueriesCache +
                '}';
    }
}
