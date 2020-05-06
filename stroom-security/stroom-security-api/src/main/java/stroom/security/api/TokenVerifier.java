package stroom.security.api;

public interface TokenVerifier {

    /**
     * Checks if the supplied token can be verified against the configured public key(s)
     * and is for the supplied clientId. If there are any problems with the token then a {@link TokenException}
     * will be thrown
     */
    void verifyToken(final String token, final String clientId) throws TokenException;
}
