package stroom.security.impl;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;

public final class AuthenticationStateSessionUtil {
    private static final String SESSION_AUTHENTICATION_STATE_MAP = "SESSION_AUTHENTICATION_STATE_MAP";

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
    @SuppressWarnings("unchecked")
    public static AuthenticationState create(final HttpServletRequest request, final String url) {
        final String stateId = createRandomString(8);
        final String nonce = createRandomString(20);
        final AuthenticationState state = new AuthenticationState(stateId, url, nonce);

        // Get the current session if there is one. Create a new session if needed.
        final HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_AUTHENTICATION_STATE_MAP, state);

        return state;
    }

    @SuppressWarnings("unchecked")
    public static AuthenticationState pop(final HttpServletRequest request) {
        AuthenticationState state = null;

        // Get the current session if there is one without creating a new one.
        final HttpSession session = request.getSession(false);
        if (session != null) {
            state = (AuthenticationState) session.getAttribute(SESSION_AUTHENTICATION_STATE_MAP);
        }

        return state;
    }

    private static String createRandomString(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }
}
