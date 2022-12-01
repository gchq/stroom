package stroom.security.api;

import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

/**
 * Handles the authentication of HTTP requests into stroom or stroom-proxy
 */
public interface RequestAuthenticator {

    /**
     * Authenticate the request
     */
    Optional<UserIdentity> authenticate(final HttpServletRequest request);

    // TODO: 01/12/2022 Rename to canAuthenticate or similar so we can check for presence of certs
    /**
     * @param request
     * @return True if the request has the required heaader(s) for authentication.
     */
    boolean hasAuthenticationToken(final HttpServletRequest request);

    /**
     * Remove any headers relating to authorisations, e.g. 'Authorisation',
     * from the passed map
     */
    void removeAuthorisationEntries(final Map<String, String> headers);
}
