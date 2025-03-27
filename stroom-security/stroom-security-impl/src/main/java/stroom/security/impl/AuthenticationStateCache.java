package stroom.security.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.security.common.impl.AuthenticationState;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Singleton
public class AuthenticationStateCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthenticationStateCache.class);

    private final StroomCache<String, AuthenticationState> cache;

    @Inject
    public AuthenticationStateCache(final Provider<AuthenticationConfig> configProvider,
                                    final CacheManager cacheManager) {
        cache = cacheManager.create("Authentication State Cache",
                () -> configProvider.get().getAuthenticationStateCache());
    }

    /**
     * A 'state' is a single use, cryptographically random string,
     * and it's use here is to prevent replay attacks.
     * <p>
     * State is used in the authentication flow - the hash is included in the original AuthenticationRequest
     * that Stroom makes to the Authentication Service. When Stroom is subsequently called the state is provided in the
     * URL to allow verification that the return request was expected.
     */
    public AuthenticationState create(final String url,
                                      final boolean prompt) {
        final String stateId = createRandomString(20);
        final String nonce = createRandomString(20);

        final AuthenticationState state = new AuthenticationState(stateId, url, nonce, prompt);
        LOGGER.debug(() -> LogUtil.message("Creating {}", state));

        cache.put(stateId, state);
        return state;
    }

    @SuppressWarnings("unchecked")
    public Optional<AuthenticationState> getAndRemove(final String stateId) {
        final Optional<AuthenticationState> optional = cache.getIfPresent(stateId);
        if (optional.isPresent()) {
            cache.invalidate(stateId);
        }
        return optional;
    }

    private String createRandomString(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }
}
