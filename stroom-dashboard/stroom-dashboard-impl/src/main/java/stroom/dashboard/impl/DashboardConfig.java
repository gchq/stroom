package stroom.dashboard.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class DashboardConfig extends AbstractConfig implements IsStroomConfig {

    @JsonProperty
    private final CacheConfig applicationInstanceCache;

    public DashboardConfig() {
        applicationInstanceCache = CacheConfig.builder()
                .expireAfterAccess(StroomDuration.ofMinutes(5))
                .build();
    }

    @JsonCreator
    public DashboardConfig(@JsonProperty("applicationInstanceCache") final CacheConfig applicationInstanceCache) {
        this.applicationInstanceCache = applicationInstanceCache;
    }

    public CacheConfig getApplicationInstanceCache() {
        return applicationInstanceCache;
    }

    @Override
    public String toString() {
        return "DashboardConfig{" +
                "applicationInstanceCache=" + applicationInstanceCache +
                '}';
    }
}
