package stroom.state.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class StateConfig extends AbstractConfig implements IsStroomConfig {

    private final CacheConfig stateDocCache;
    private final CacheConfig scyllaDbDocCache;
    private final CacheConfig sessionCache;

    public StateConfig() {
        stateDocCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
        scyllaDbDocCache = CacheConfig.builder()
                .maximumSize(100L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
        sessionCache = CacheConfig.builder()
                .maximumSize(10L)
                .expireAfterAccess(StroomDuration.ofHours(1))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public StateConfig(@JsonProperty("stateDocCache") final CacheConfig stateDocCache,
                       @JsonProperty("scyllaDbDocCache") final CacheConfig scyllaDbDocCache,
                       @JsonProperty("sessionCache") final CacheConfig sessionCache) {
        this.stateDocCache = stateDocCache;
        this.scyllaDbDocCache = scyllaDbDocCache;
        this.sessionCache = sessionCache;
    }

    public CacheConfig getStateDocCache() {
        return stateDocCache;
    }

    public CacheConfig getScyllaDbDocCache() {
        return scyllaDbDocCache;
    }

    public CacheConfig getSessionCache() {
        return sessionCache;
    }

    @Override
    public String toString() {
        return "StateConfig{" +
                "stateDocCache=" + stateDocCache +
                ", scyllaDbDocCache=" + scyllaDbDocCache +
                ", sessionCache=" + sessionCache +
                '}';
    }
}
