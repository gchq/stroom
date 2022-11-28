package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.JwtUtil;
import stroom.security.common.impl.StandardJwtContextFactory;
import stroom.util.NullSafe;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jose4j.jwt.consumer.JwtContext;

import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

public class ApiUserIdentityFactoryImpl implements ApiUserIdentityFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiUserIdentityFactoryImpl.class);

    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final StandardJwtContextFactory standardJwtContextFactory;

    @Inject
    public ApiUserIdentityFactoryImpl(final DefaultOpenIdCredentials defaultOpenIdCredentials,
                                      final StandardJwtContextFactory standardJwtContextFactory) {
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.standardJwtContextFactory = standardJwtContextFactory;
    }

    @Override
    public Optional<UserIdentity> getApiUserIdentity(final HttpServletRequest request) {
        Optional<UserIdentity> optionalUserIdentity;

        // See if we can login with a token if one is supplied.
        try {
            // Always try the internal context factory first.
            Optional<JwtContext> optionalContext = standardJwtContextFactory.getJwtContext(request);

            if (optionalContext.isEmpty()) {
                LOGGER.debug(() -> "No JWS found in headers in request to " + request.getRequestURI());
                optionalUserIdentity = Optional.empty();
            } else {
                optionalUserIdentity = getUserIdentity(request, optionalContext.get());
            }

        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            optionalUserIdentity = Optional.empty();
        }

        if (optionalUserIdentity.isEmpty()) {
            LOGGER.debug(() -> "Cannot get a valid JWS for API request to " + request.getRequestURI() + ". " +
                    "This may be due to Stroom being left open in a browser after Stroom was restarted.");
        }

        return optionalUserIdentity;
    }

    @Override
    public boolean hasAuthenticationToken(final HttpServletRequest request) {
        return standardJwtContextFactory.hasToken(request);
    }

    @Override
    public void removeAuthorisationEntries(final Map<String, String> headers) {

    }

    private Optional<UserIdentity> getUserIdentity(final HttpServletRequest request,
                                                   final JwtContext jwtContext) {
        LOGGER.debug(() -> "Getting user identity from jwtContext=" + jwtContext);

        try {
            final String userId = JwtUtil.getSubject(jwtContext.getJwtClaims());
            if (NullSafe.isBlankString(userId)) {
                throw new AuthenticationException("No subject found in JWT");
            }

            return Optional.of(new ProxyUserIdentity(jwtContext));

        } catch (final AuthenticationException e) {
            LOGGER.error(() -> "Error extracting claims from token in request " + request.getRequestURI());
            return Optional.empty();
        }
    }
}
