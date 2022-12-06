package stroom.security.common.impl;

import stroom.security.api.UserIdentity;

import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface UserIdentityFactory {

    /**
     * Extracts the authenticated user's identity from the http request to an API.
     */
    Optional<UserIdentity> getApiUserIdentity(final HttpServletRequest request);

    /**
     * Extracts the authenticated user's identity from http request when that
     * request is part of a UI based authentication flow with the IDP
     */
    Optional<UserIdentity> getAuthFlowUserIdentity(final HttpServletRequest request,
                                                   final String code,
                                                   final AuthenticationState state);

    /**
     * Gets the user identity for a service user on the IDP, i.e. for stroom/proxy to call
     * out to another system that is also using this IDP.
     */
    UserIdentity getServiceUserIdentity();

    /**
     * True if the request contains the certs/headers needed to authenticate.
     * Does not perform authentication.
     */
    boolean hasAuthenticationToken(final HttpServletRequest request);

    boolean hasAuthenticationCertificate(final HttpServletRequest request);

    /**
     * Remove any authentication headers key/value pairs from the map
     */
    void removeAuthEntries(final Map<String, String> headers);

    /**
     * @return The authentication/authorisation headers to enable authentication with this user
     */
    Map<String, String> getAuthHeaders(final UserIdentity userIdentity);

    /**
     * @return The authentication/authorisation headers to enable authentication with this token
     */
    Map<String, String> getAuthHeaders(final String jwt);

    /**
     * Refresh the user identity including any tokens associated with that user.
     * @param userIdentity
     */
    void refresh(UserIdentity userIdentity);
}
