package stroom.security;

import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    public static AuthenticationState create(final HttpSession session, final String url) {
        Map<String, AuthenticationState> map = (Map) session.getAttribute(SESSION_AUTHENTICATION_STATE_MAP);
        if (map == null) {
            map = new ConcurrentHashMap<>();
            session.setAttribute(SESSION_AUTHENTICATION_STATE_MAP, map);
        }

        final String stateId = createRandomString(8);
        final String nonce = createRandomString(20);
        final AuthenticationState state = new AuthenticationState(stateId, url, nonce);
        map.put(stateId, state);
        return state;
    }

    @SuppressWarnings("unchecked")
    public static AuthenticationState remove(final HttpSession session, final String stateId) {
        if (session == null) {
            return null;
        }

        final Map<String, AuthenticationState> map = (Map) session.getAttribute(SESSION_AUTHENTICATION_STATE_MAP);
        if (map == null) {
            return null;
        }

        return map.remove(stateId);
    }

    private static String createRandomString(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    public static class AuthenticationState {
        private final String id;
        private final String url;
        private final String nonce;

        AuthenticationState(final String id, final String url, final String nonce) {
            this.id = id;
            this.url = url;
            this.nonce = nonce;
        }

        /**
         * The id of this state.
         *
         * @return The id of this state.
         */
        public String getId() {
            return id;
        }

        /**
         * The URL of the originating request that this state is linked to.
         *
         * @return The URL of the originating request that this state is linked to.
         */
        public String getUrl() {
            return url;
        }

        /**
         * A 'nonce' is a single use, cryptographically random string,
         * and it's use here is to validate the authenticity of a token.
         * <p>
         * A nonce is used in the authentication flow - the hash is included in the original AuthenticationRequest
         * that Stroom makes to the Authentication Service. When Stroom subsequently receives an access code
         * it retrieves the ID token from the Authentication Service and expects to see
         * the hash of the nonce on the token. It can then compare the hashes.
         *
         * @return The nonce string.
         */
        public String getNonce() {
            return nonce;
        }
    }
}
