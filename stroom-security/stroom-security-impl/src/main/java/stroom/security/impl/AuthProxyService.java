package stroom.security.impl;

import stroom.security.common.impl.ClientCredentials;

/**
 * Acts as a proxy for the Identity Provider. This is to allow callers with no details of the
 * identity provider (other than the {@link ClientCredentials} to make a token request on the
 * identity provider.
 */
public interface AuthProxyService {

    /**
     * Fetch an access token from the configured IDP using the supplied client credentials.
     */
    String fetchToken(final ClientCredentials clientCredentials);

}
