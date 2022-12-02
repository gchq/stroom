package stroom.security.common.impl;

import stroom.security.openid.api.TokenResponse;
import stroom.util.logging.LogUtil;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;

/**
 * User identity for a service user for this application to authenticate with other
 * applications on the same IDP realm.
 */
public class ServiceUserIdentity extends AbstractTokenUserIdentity {

    public ServiceUserIdentity(final TokenResponse tokenResponse,
                               final JwtClaims jwtClaims) {
        super(
                getSubject(jwtClaims),
                tokenResponse,
                jwtClaims);
    }

    private static String getSubject(final JwtClaims jwtClaims) {
        try {
            return jwtClaims.getSubject();
        } catch (MalformedClaimException e) {
            throw new RuntimeException(
                    LogUtil.message("Unable to extract subject from service user claims " + jwtClaims), e);
        }
    }
}
