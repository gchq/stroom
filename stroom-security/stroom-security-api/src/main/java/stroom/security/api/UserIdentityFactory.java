package stroom.security.api;

import stroom.util.shared.UserDesc;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;

public interface UserIdentityFactory {

    /**
     * This header is used to set the user id that the request should be run as.
     */
    String RUN_AS_USER_HEADER = "stroom-run-as-user";

    /**
     * Extracts the authenticated user's identity from the http request to an API.
     */
    Optional<UserIdentity> getApiUserIdentity(final HttpServletRequest request);

    /**
     * Gets the user identity for a service user on the IDP, i.e. for stroom/proxy to call
     * out to another system that is also using this IDP.
     */
    UserIdentity getServiceUserIdentity();

    /**
     * Return true if userIdentity is the service user identity
     */
    boolean isServiceUser(final UserIdentity userIdentity);

    /**
     * True if the request contains the certs/headers needed to authenticate.
     * Does not perform authentication.
     */
    boolean hasAuthenticationToken(final HttpServletRequest request);

//    boolean hasAuthenticationCertificate(final HttpServletRequest request);

    /**
     * Remove any authentication headers key/value pairs from the map
     */
    void removeAuthEntries(final Map<String, String> headers);

    /**
     * @return The authentication/authorisation headers to enable authentication with this user
     */
    Map<String, String> getAuthHeaders(final UserIdentity userIdentity);

    /**
     * @return The authentication/authorisation headers to enable authentication with the service
     * user.
     */
    Map<String, String> getServiceUserAuthHeaders();

    /**
     * @return The authentication/authorisation headers to enable authentication with this token
     */
    Map<String, String> getAuthHeaders(final String jwt);

    void refresh(final UserIdentity userIdentity);

    /**
     * Allows an identity provider to manually add a userIdentity, e.g. so it is available
     * in stroom for permissions to be granted to it prior to the user logging in.
     * If the userIdentity is present, its displayName and fullName will be updated using
     * the values from userIdentity.
     */
    default void ensureUserIdentity(final UserDesc userDesc) {
        throw new UnsupportedOperationException("Ensuring user identities manually is not supported");
    }
}
