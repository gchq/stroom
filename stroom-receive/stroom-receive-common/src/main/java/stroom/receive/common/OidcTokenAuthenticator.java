package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

public class OidcTokenAuthenticator implements AuthenticatorFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OidcTokenAuthenticator.class);

    private final UserIdentityFactory userIdentityFactory;

    @Inject
    public OidcTokenAuthenticator(final UserIdentityFactory userIdentityFactory) {
        this.userIdentityFactory = userIdentityFactory;
    }

    @Override
    public Optional<UserIdentity> authenticate(final HttpServletRequest request,
                                               final AttributeMap attributeMap) {
        Optional<UserIdentity> optUserIdentity = Optional.empty();
        final boolean foundToken = userIdentityFactory.hasAuthenticationToken(request);
        if (foundToken) {
            try {
                optUserIdentity = userIdentityFactory.getApiUserIdentity(request);
            } catch (StroomStreamException e) {
                throw e;
            } catch (Exception e) {
                throw new StroomStreamException(
                        StroomStatusCode.CLIENT_TOKEN_NOT_AUTHENTICATED, attributeMap, e.getMessage());
            }
        }
        LOGGER.debug("Returning optUserIdentity: {}", optUserIdentity);
        return optUserIdentity;
    }
}
