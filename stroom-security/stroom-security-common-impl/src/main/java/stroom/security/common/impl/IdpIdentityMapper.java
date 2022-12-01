package stroom.security.common.impl;

import stroom.security.api.UserIdentity;
import stroom.security.openid.api.TokenResponse;

import org.jose4j.jwt.consumer.JwtContext;

import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

/**
 * Maps an identity on the IDP to a local user.
 */
public interface IdpIdentityMapper {

    /**
     * Map the IDP identity provided by the {@link JwtContext} to a local user.
     * @param jwtContext The identity on the IDP to map to a local user.
     * @param request The HTTP request
     * @return A local {@link UserIdentity} if the identity can be mapped.
     */
    Optional<UserIdentity> mapApiIdentity(final JwtContext jwtContext,
                                          final HttpServletRequest request);

    /**
     * Map the IDP identity provided by the {@link JwtContext} and the
     * {@link TokenResponse}to a local user. This is for use in a UI based
     * authentication flow.
     * @param jwtContext The identity on the IDP to map to a local user.
     * @param request The HTTP request
     * @param tokenResponse The token received from the IDP.
     * @return A local {@link UserIdentity} if the identity can be mapped.
     */
    Optional<UserIdentity> mapAuthFlowIdentity(final JwtContext jwtContext,
                                               final HttpServletRequest request,
                                               final TokenResponse tokenResponse);
}
