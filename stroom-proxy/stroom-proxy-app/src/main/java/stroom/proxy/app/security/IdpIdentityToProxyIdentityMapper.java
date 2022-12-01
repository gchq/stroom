package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;
import stroom.security.common.impl.IdpIdentityMapper;
import stroom.security.openid.api.TokenResponse;

import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public class IdpIdentityToProxyIdentityMapper implements IdpIdentityMapper {

    @Override
    public Optional<UserIdentity> mapApiIdentity(final JwtContext jwtContext,
                                                 final HttpServletRequest request) {
        Objects.requireNonNull(jwtContext);
        // No notion of a local user identity so just wrap the claims in the jwt context
        return Optional.of(new ProxyClientUserIdentity(jwtContext));
    }

    @Override
    public Optional<UserIdentity> mapAuthFlowIdentity(final JwtContext jwtContext,
                                                      final HttpServletRequest request,
                                                      final TokenResponse tokenResponse) {
        throw new UnsupportedOperationException("UI Auth flow not applicable to stroom-proxy");
    }
}
