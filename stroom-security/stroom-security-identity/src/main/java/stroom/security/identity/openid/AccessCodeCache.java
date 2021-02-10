package stroom.security.identity.openid;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.security.identity.config.OpenIdConfig;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class AccessCodeCache {

    private static final String CACHE_NAME = "Access Code Cache";

    private final ICache<String, AccessCodeRequest> cache;

    @Inject
    AccessCodeCache(final CacheManager cacheManager,
                    final OpenIdConfig config) {
        cache = cacheManager.create(CACHE_NAME, config::getAccessCodeCache);
    }

    Optional<AccessCodeRequest> getAndRemove(final String code) {
        final Optional<AccessCodeRequest> optionalAccessCodeRequest = cache.getOptional(code);
        cache.remove(code);
        return optionalAccessCodeRequest;
    }

    void put(final String code, final AccessCodeRequest accessCodeRequest) {
        cache.put(code, accessCodeRequest);
    }
}
