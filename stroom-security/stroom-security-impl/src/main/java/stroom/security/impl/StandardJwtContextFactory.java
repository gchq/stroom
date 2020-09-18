package stroom.security.impl;

import stroom.security.api.TokenException;
import stroom.security.api.TokenVerifier;
import stroom.security.impl.exception.AuthenticationException;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.health.HealthCheck;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class StandardJwtContextFactory implements HasHealthCheck, TokenVerifier, JwtContextFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StandardJwtContextFactory.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final ResolvedOpenIdConfig openIdConfig;
    private final OpenIdPublicKeysSupplier openIdPublicKeysSupplier;

    @Inject
    StandardJwtContextFactory(final ResolvedOpenIdConfig openIdConfig,
                              final OpenIdPublicKeysSupplier openIdPublicKeysSupplier) {
        this.openIdConfig = openIdConfig;
        this.openIdPublicKeysSupplier = openIdPublicKeysSupplier;
    }

    @Override
    public Optional<JwtContext> getJwtContext(final HttpServletRequest request) {
        LOGGER.debug(() -> AUTHORIZATION_HEADER + "=" + request.getHeader(AUTHORIZATION_HEADER));

        final Optional<String> optionalJws = JwtUtil.getJwsFromHeader(request, AUTHORIZATION_HEADER);
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
            final JwtContext jwtContext = extractTokenContext(jws);
            boolean isVerified = jwtContext != null;
            boolean isRevoked = false;

            // TODO : @66 Check against blacklist to see if token has been revoked. Blacklist
            //  is a list of JWI (JWT IDs) on auth service. Only tokens with `jwi` claims are API
            //  keys so only those tokens need checking against the blacklist cache.

//            if (checkTokenRevocation) {
//                LOGGER.debug(() -> "Checking token revocation status in remote auth service...");
//                final String userId = getUserIdFromToken(jws);
//                isRevoked = userId == null;
//            }

            if (isVerified && !isRevoked) {
                return Optional.ofNullable(jwtContext);
            }

        } catch (Exception e) {
            LOGGER.error(() -> "Unable to verify token: " + e.getMessage(), e);
            LOGGER.warn(e::getMessage);
            throw new AuthenticationException(e.getMessage(), e);
        }

        return Optional.empty();
    }


    private JwtContext extractTokenContext(final String token) throws InvalidJwtException {
        try {
            return toContext(token);
        } catch (InvalidJwtException e) {
            LOGGER.warn(() -> "Unable to verify token!");
            throw e;
        }
    }

    @Override
    public void verifyToken(final String token, final String clientId) throws TokenException {
        // Will throw if invalid, e.g. if it doesn't match our public key
        final JwtContext jwtContext;

        try {
            jwtContext = toContext(token);
        } catch (InvalidJwtException e) {
            throw new TokenException("Invalid token: " + e.getMessage(), e);
        }

        // TODO : Check against blacklist to see if token has been revoked. Blacklist
        //  is a list of JWI (JWT IDs) on auth service. Only tokens with `jwi` claims are API
        //  keys so only those tokens need checking against the blacklist cache.

        if (jwtContext == null) {
            throw new TokenException("Could not extract claims from token");
        } else {
            try {
                final JwtClaims jwtClaims = jwtContext.getJwtClaims();
                if (jwtClaims.getExpirationTime() != null
                        && jwtClaims.getExpirationTime().isBefore(NumericDate.now())) {
                    throw new TokenException("Token expired on: " +
                            Instant.ofEpochSecond(jwtClaims.getExpirationTime().getValueInMillis()).toString());
                }

                final List<String> audience = jwtClaims.getAudience();
                if (!audience.contains(clientId)) {
                    throw new TokenException("Token audience does not contain clientId: " + clientId);
                }
            } catch (MalformedClaimException e) {
                throw new TokenException("Invalid token claims: " + e.getMessage(), e);
            }
        }
    }

    private JwtContext toContext(final String token) throws InvalidJwtException {
        final JwtConsumer jwtConsumer = newJwsConsumer();
        return jwtConsumer.process(token);
    }

    private JwtConsumer newJwsConsumer() {
        // If we don't have a JWK we can't create a consumer to verify anything.
        // Why might we not have one? If the remote authentication service was down when Stroom started
        // then we wouldn't. It might not be up now but we're going to try and fetch it.
        final JsonWebKeySet publicJsonWebKey = openIdPublicKeysSupplier.get();

        final VerificationKeyResolver verificationKeyResolver = new JwksVerificationKeyResolver(
                publicJsonWebKey.getJsonWebKeys());

        final JwtConsumerBuilder builder = new JwtConsumerBuilder()
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setVerificationKeyResolver(verificationKeyResolver)
                .setExpectedAudience(openIdConfig.getClientId())
                .setRelaxVerificationKeyValidation() // relaxes key length requirement
                .setJwsAlgorithmConstraints( // only allow the expected signature algorithm(s) in the given context
                        new AlgorithmConstraints(
                                AlgorithmConstraints.ConstraintType.WHITELIST, // which is only RS256 here
                                AlgorithmIdentifiers.RSA_USING_SHA256))
                .setExpectedIssuer(openIdConfig.getIssuer());
        return builder.build();
    }

    @Override
    public HealthCheck.Result getHealth() {
        // Defaults to healthy
        HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();
        final String KEY = "public_key_retrieval";
        boolean isUnhealthy = false;
        try {
            final JsonWebKeySet publicJsonWebKey = openIdPublicKeysSupplier.get();
            if (publicJsonWebKey == null) {
                resultBuilder.withDetail(KEY, "Missing public key\n");
            }
        } catch (RuntimeException e) {
            resultBuilder.withDetail(KEY, "Error fetching our identity provider's public key! " +
                    "This means we cannot verify clients' authentication tokens ourselves. " +
                    "This might mean the authentication service is down or unavailable. " +
                    "The error was: [" + e.getMessage() + "]");
        }
        if (isUnhealthy) {
            resultBuilder
                    .withDetail("publicKeysUri", openIdConfig.getJwksUri())
                    .unhealthy();
        } else {
            resultBuilder.healthy()
                    .withMessage("Open ID public keys found");
        }
        return resultBuilder.build();
    }
}
