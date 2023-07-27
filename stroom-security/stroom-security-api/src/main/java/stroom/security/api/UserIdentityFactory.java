package stroom.security.api;

import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

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
     * Return true if subject and issuer match those of the service userIdentity
     */
    boolean isServiceUser(final String subject, final String issuer);

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
     * @return The authentication/authorisation headers to enable authentication with the service
     * user.
     */
    Map<String, String> getServiceUserAuthHeaders();

    /**
     * @return The authentication/authorisation headers to enable authentication with this token
     */
    Map<String, String> getAuthHeaders(final String jwt);

    void refresh(final UserIdentity userIdentity);
}
