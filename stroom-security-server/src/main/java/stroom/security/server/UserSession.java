package stroom.security.server;

import stroom.security.shared.UserRef;

import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UserSession {
    private static final String USER_SESSION = "USER_SESSION";

    private volatile UserRef userRef;
    private final Map<String, State> stateMap = new ConcurrentHashMap<>();

    private UserSession() {
    }

    public static UserSession getOrCreate(final HttpSession session) {
        UserSession userSession = get(session);
        if (userSession == null) {
            synchronized (USER_SESSION) {
                userSession = get(session);
                if (userSession == null) {
                    userSession = new UserSession();
                    session.setAttribute(USER_SESSION, userSession);
                }
            }
        }
        return userSession;
    }

    public static UserSession get(final HttpSession session) {
        if (session == null) {
            return null;
        }
        return (UserSession) session.getAttribute(USER_SESSION);
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public void setUserRef(final UserRef userRef) {
        this.userRef = userRef;
    }

    /**
     * A 'state' is a single use, cryptographically random string,
     * and it's use here is to prevent replay attacks.
     * <p>
     * State is used in the authentication flow - the hash is included in the original AuthenticationRequest
     * that Stroom makes to the Authentication Service. When Stroom is subsequently called the state is provided in the
     * URL to allow verification that the return request was expected.
     */
    public State createState(final String url) {
        final String stateId = createRandomString(8);
        final String nonce = createRandomString(20);
        final State state = new State(stateId, url, nonce);
        stateMap.put(stateId, state);
        return state;
    }

    public State getState(final String stateId) {
        return stateMap.remove(stateId);
    }

    private String createRandomString(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    public static class State {
        private final String id;
        private final String url;
        private final String nonce;

        public State(final String id, final String url, final String nonce) {
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
