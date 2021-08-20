package stroom.security.identity.config;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.inject.Singleton;

@Singleton
public class OpenIdConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_ACCESS_CODE_CACHE = "accessCodeCache";
    public static final String PROP_NAME_REFRESH_TOKEN_CACHE = "refreshTokenCache";

    private CacheConfig accessCodeCache = CacheConfig.builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();

    private CacheConfig refreshTokenCache = CacheConfig.builder()
            .maximumSize(10000L)
            .expireAfterAccess(StroomDuration.ofDays(1))
            .build();

    @JsonProperty(PROP_NAME_ACCESS_CODE_CACHE)
    public CacheConfig getAccessCodeCache() {
        return accessCodeCache;
    }

    public void setAccessCodeCache(final CacheConfig accessCodeCache) {
        this.accessCodeCache = accessCodeCache;
    }

    @JsonProperty(PROP_NAME_REFRESH_TOKEN_CACHE)
    public CacheConfig getRefreshTokenCache() {
        return refreshTokenCache;
    }

    public void setRefreshTokenCache(final CacheConfig refreshTokenCache) {
        this.refreshTokenCache = refreshTokenCache;
    }
}
