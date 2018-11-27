package stroom.security.server;

public class AuthenticationState {
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
