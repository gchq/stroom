package stroom.authentication.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

public class OAuth2Config extends AbstractConfig {
    public static final String PROP_NAME_ACCESS_CODE_CACHE = "accessCodeCache";

    private CacheConfig accessCodeCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();

    @JsonProperty(PROP_NAME_ACCESS_CODE_CACHE)
    public CacheConfig getAccessCodeCache() {
        return accessCodeCache;
    }

    public void setAccessCodeCache(final CacheConfig accessCodeCache) {
        this.accessCodeCache = accessCodeCache;
    }
}
