package stroom.security.impl;

import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.impl.exception.AuthenticationException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;

import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;

class StandardJwtContextFactory implements JwtContextFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StandardJwtContextFactory.class);

    private static final String AMZN_OIDC_ACCESS_TOKEN_HEADER = "x-amzn-oidc-accesstoken";
    private static final String AMZN_OIDC_IDENTITY_HEADER = "x-amzn-oidc-identity";
    private static final String AMZN_OIDC_DATA_HEADER = "x-amzn-oidc-data";
    private static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;

    private final OpenIdConfiguration openIdConfiguration;
    private final OpenIdPublicKeysSupplier openIdPublicKeysSupplier;

    @Inject
    StandardJwtContextFactory(final OpenIdConfiguration openIdConfiguration,
                              final OpenIdPublicKeysSupplier openIdPublicKeysSupplier) {
        this.openIdConfiguration = openIdConfiguration;
        this.openIdPublicKeysSupplier = openIdPublicKeysSupplier;
    }

    @Override
    public Optional<JwtContext> getJwtContext(final HttpServletRequest request) {
        LOGGER.debug(() -> AMZN_OIDC_ACCESS_TOKEN_HEADER + "=" + request.getHeader(AMZN_OIDC_ACCESS_TOKEN_HEADER));
        LOGGER.debug(() -> AMZN_OIDC_IDENTITY_HEADER + "=" + request.getHeader(AMZN_OIDC_IDENTITY_HEADER));
        LOGGER.debug(() -> AMZN_OIDC_DATA_HEADER + "=" + request.getHeader(AMZN_OIDC_DATA_HEADER));
        LOGGER.debug(() -> AUTHORIZATION_HEADER + "=" + request.getHeader(AUTHORIZATION_HEADER));

        final Optional<String> optionalJws = JwtUtil.getJwsFromHeader(request, AMZN_OIDC_DATA_HEADER)
                .or(() -> JwtUtil.getJwsFromHeader(request, AUTHORIZATION_HEADER));
        return optionalJws
                .flatMap(this::getJwtContext)
                .or(() -> {
                    LOGGER.debug(() -> "No JWS found in headers in request to " + request.getRequestURI());
                    return Optional.empty();
                });
    }

    /**
     * Verify the JSON Web Signature and then extract the user identity from it
     */
    @Override
    public Optional<JwtContext> getJwtContext(final String jws) {
        Objects.requireNonNull(jws, "Null JWS");
        LOGGER.debug(() -> "Found auth header in request. It looks like this: " + jws);

        try {
            LOGGER.debug(() -> "Verifying token...");
            final JwtConsumer jwtConsumer = newJwtConsumer();
            final JwtContext jwtContext = jwtConsumer.process(jws);

            // TODO : @66 Check against blacklist to see if token has been revoked. Blacklist
            //  is a list of JWI (JWT IDs) on auth service. Only tokens with `jwi` claims are API
            //  keys so only those tokens need checking against the blacklist cache.

//            if (checkTokenRevocation) {
//                LOGGER.debug(() -> "Checking token revocation status in remote auth service...");
//                final String userId = getUserIdFromToken(jws);
//                isRevoked = userId == null;
//            }

            return Optional.ofNullable(jwtContext);

        } catch (final RuntimeException | InvalidJwtException e) {
            LOGGER.debug(() -> "Unable to verify token: " + e.getMessage(), e);
            throw new AuthenticationException(e.getMessage(), e);
        }
    }

    private JwtConsumer newJwtConsumer() {
        // If we don't have a JWK we can't create a consumer to verify anything.
        // Why might we not have one? If the remote authentication service was down when Stroom started
        // then we wouldn't. It might not be up now but we're going to try and fetch it.
        final JsonWebKeySet publicJsonWebKey = openIdPublicKeysSupplier.get();

        final VerificationKeyResolver verificationKeyResolver = new JwksVerificationKeyResolver(
                publicJsonWebKey.getJsonWebKeys());

        final JwtConsumerBuilder builder = new JwtConsumerBuilder()
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account
                //                                   for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setVerificationKeyResolver(verificationKeyResolver)
                .setExpectedAudience(openIdConfiguration.getClientId())
                .setRelaxVerificationKeyValidation() // relaxes key length requirement
//                .setJwsAlgorithmConstraints(// only allow the expected signature algorithm(s) in the given context
//                        new AlgorithmConstraints(
//                                AlgorithmConstraints.ConstraintType.WHITELIST, // which is only RS256 here
//                                AlgorithmIdentifiers.RSA_USING_SHA256))
                .setExpectedIssuer(openIdConfiguration.getIssuer());
        return builder.build();
    }
}
