package stroom.security.identity.config;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class OpenIdConfig extends AbstractConfig {

    public static final String PROP_NAME_ACCESS_CODE_CACHE = "accessCodeCache";

    private final CacheConfig accessCodeCache;

    public OpenIdConfig() {
        accessCodeCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
    }

    @JsonCreator
    public OpenIdConfig(@JsonProperty(PROP_NAME_ACCESS_CODE_CACHE) final CacheConfig accessCodeCache) {
        this.accessCodeCache = accessCodeCache;
    }

    @JsonProperty(PROP_NAME_ACCESS_CODE_CACHE)
    public CacheConfig getAccessCodeCache() {
        return accessCodeCache;
    }

    @Override
    public String toString() {
        return "OpenIdConfig{" +
                "accessCodeCache=" + accessCodeCache +
                '}';
    }
}
