package stroom.security.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.UserAgentSessionUtil;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class AuthenticationStateSessionUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthenticationStateSessionUtil.class);

    private static final String AUTHENTICATION_STATE_SESSION_ATTRIBUTE = "AUTHENTICATION_STATE_SESSION_ATTRIBUTE";

    private AuthenticationStateSessionUtil() {
    }

    /**
     * A 'state' is a single use, cryptographically random string,
     * and it's use here is to prevent replay attacks.
     * <p>
     * State is used in the authentication flow - the hash is included in the original AuthenticationRequest
     * that Stroom makes to the Authentication Service. When Stroom is subsequently called the state is provided in the
     * URL to allow verification that the return request was expected.
     */
    public static AuthenticationState create(final HttpServletRequest request, final String url) {
        final String stateId = createRandomString(8);
        final String nonce = createRandomString(20);

        LOGGER.debug(() -> {
            final HttpSession session = request.getSession(false);
            return LogUtil.message("Creating new AuthenticationState, stateId: {}, session: {}, requestUri: {}",
                    stateId,
                    session != null ? session.getId() : null,
                    url);
        });

        final AuthenticationState state = new AuthenticationState(stateId, url, nonce);
        final Cache<String, AuthenticationState> cache = getOrCreateCache(request);
        cache.put(stateId, state);
        return state;
    }

    @SuppressWarnings("unchecked")
    public static AuthenticationState pop(final HttpServletRequest request, final String stateId) {
        final HttpSession session = request.getSession(false);
        if (session != null) {
            final Cache<String, AuthenticationState> cache = (Cache) session.getAttribute(
                    AUTHENTICATION_STATE_SESSION_ATTRIBUTE);
            if (cache != null) {
                return cache.getIfPresent(stateId);
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static Cache<String, AuthenticationState> getOrCreateCache(final HttpServletRequest request) {
        final HttpSession session = request.getSession(true);
        final String sessionId = session.getId();

        Cache cache = (Cache) session.getAttribute(AUTHENTICATION_STATE_SESSION_ATTRIBUTE);
        if (cache == null) {
            synchronized (session) {
                cache = (Cache) session.getAttribute(AUTHENTICATION_STATE_SESSION_ATTRIBUTE);
                if (cache == null) {
                    LOGGER.debug("Creating cache for session {}", sessionId);
                    cache = Caffeine.newBuilder()
                            .maximumSize(100)
                            .expireAfterWrite(1, TimeUnit.MINUTES)
                            .removalListener((key, value, cause) ->
                                    LOGGER.debug(() -> LogUtil.message(
                                            "Removing entry: {}, cause: {}, session: {}",
                                            key, cause, sessionId)))
                            .build();
                    session.setAttribute(AUTHENTICATION_STATE_SESSION_ATTRIBUTE, cache);
                    UserAgentSessionUtil.set(request);
                }
            }
        }
        return cache;
    }

    private static String createRandomString(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }
}
