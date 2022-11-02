package stroom.security.identity.openid;

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.security.identity.config.OpenIdConfig;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
class RefreshTokenCache {

    private static final String CACHE_NAME = "Refresh Token Cache";

    private final StroomCache<String, TokenProperties> cache;

    @Inject
    RefreshTokenCache(final CacheManager cacheManager,
                      final Provider<OpenIdConfig> openIdConfigProvider) {
        cache = cacheManager.create(
                CACHE_NAME,
                () -> openIdConfigProvider.get().getRefreshTokenCache());
    }

    Optional<TokenProperties> getAndRemove(final String refreshToken) {
        final Optional<TokenProperties> optionalAccessCodeRequest = cache.getOptional(refreshToken);
        cache.remove(refreshToken);
        return optionalAccessCodeRequest;
    }

    void put(final String refreshToken, final TokenProperties properties) {
        cache.put(refreshToken, properties);
    }
}
