package stroom.security.api;

import stroom.meta.api.AttributeMap;

import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

/**
 * Handles the authentication of HTTP requests into stroom or stroom-proxy
 */
public interface RequestAuthenticator {

    /**
     * Authenticate an inbound request
     */
    Optional<UserIdentity> authenticate(final HttpServletRequest request, final AttributeMap attributeMap);

    // TODO: 01/12/2022 Rename to canAuthenticate or similar so we can check for presence of certs
    /**
     * Check for presence of tokens/certs on an inbound request that determines if authentication
     * is possible.
     * @return True if the request has the required heaader(s) for authentication.
     */
    boolean hasAuthenticationToken(final HttpServletRequest request);

    /**
     * Remove any headers relating to authorisations, e.g. 'Authorisation',
     * from the passed map
     */
    void removeAuthorisationEntries(final Map<String, String> headers);

    /**
     * @return The authentication/authorisation headers to enable authentication with this user
     */
    Map<String, String> getAuthHeaders(final UserIdentity userIdentity);

    /**
     * @return The authentication/authorisation headers to enable authentication with the service
     * account user
     */
    Map<String, String> getServiceUserAuthHeaders();
}
