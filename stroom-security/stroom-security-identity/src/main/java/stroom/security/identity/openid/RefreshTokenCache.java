package stroom.security.identity.openid;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.security.identity.config.OpenIdConfig;
import stroom.security.openid.api.TokenResponse;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class RefreshTokenCache {

    private static final String CACHE_NAME = "Refresh Token Cache";

    private final ICache<String, TokenResponse> cache;

    @Inject
    RefreshTokenCache(final CacheManager cacheManager,
                      final OpenIdConfig config) {
        cache = cacheManager.create(CACHE_NAME, config::getRefreshTokenCache);
    }

    Optional<TokenResponse> getAndRemove(final String code) {
        final Optional<TokenResponse> optionalAccessCodeRequest = cache.getOptional(code);
        cache.remove(code);
        return optionalAccessCodeRequest;
    }

    void put(final String code, final TokenResponse accessCodeRequest) {
        cache.put(code, accessCodeRequest);
    }
}
