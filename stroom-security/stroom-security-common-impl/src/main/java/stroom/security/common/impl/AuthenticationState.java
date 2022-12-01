package stroom.security.common.impl;

public class AuthenticationState {

    private final String id;
    private final String uri;
    private final String nonce;

    public AuthenticationState(final String id, final String uri, final String nonce) {
        this.id = id;
        this.uri = uri;
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
     * The URI of the originating request that this state is linked to.
     *
     * @return The URL of the originating request that this state is linked to.
     */
    public String getUri() {

        return uri;
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

    @Override
    public String toString() {
        return "AuthenticationState{" +
                "id='" + id + '\'' +
                ", uri='" + uri + '\'' +
                ", nonce='" + nonce + '\'' +
                '}';
    }
}
