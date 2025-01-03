package stroom.planb.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class PlanBConfig extends AbstractConfig implements IsStroomConfig {

    private final CacheConfig stateDocCache;

    public PlanBConfig() {
        stateDocCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public PlanBConfig(@JsonProperty("stateDocCache") final CacheConfig stateDocCache) {
        this.stateDocCache = stateDocCache;
    }

    public CacheConfig getPlanBDocCache() {
        return stateDocCache;
    }

    @Override
    public String toString() {
        return "StateConfig{" +
               "stateDocCache=" + stateDocCache +
               '}';
    }
}
