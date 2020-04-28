package stroom.security.impl;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Strings;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;

import stroom.security.impl.exception.AuthenticationException;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class JWTService implements HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JWTService.class);

    private static final String BEARER = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final ResolvedOpenIdConfig openIdConfig;
    private final OpenIdPublicKeysSupplier openIdPublicKeysSupplier;

    @Inject
    JWTService(final ResolvedOpenIdConfig openIdConfig,
               final OpenIdPublicKeysSupplier openIdPublicKeysSupplier) {
        this.openIdConfig = openIdConfig;
        this.openIdPublicKeysSupplier = openIdPublicKeysSupplier;
    }

    /**
     * Verify the JSON Web Signature and then extract the user identity from it
     */
    public Optional<String> getUserId(final String jws) {
        Objects.requireNonNull(jws, "Null JWS");
        LOGGER.debug(() -> "Found auth header in request. It looks like this: " + jws);

        try {
            LOGGER.debug(() -> "Verifying token...");
            final JwtClaims jwtClaims = verifyToken(jws);
            boolean isVerified = jwtClaims != null;
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
                return Optional.ofNullable(jwtClaims.getSubject());
            }

        } catch (Exception e) {
            LOGGER.error(() -> "Unable to verify token: " + e.getMessage(), e);
            LOGGER.warn(e::getMessage);
            throw new AuthenticationException(e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * Get the JSON Web Signature from the request headers
     */
    public Optional<String> getJws(final ServletRequest request) {
        final Optional<String> authHeader = getAuthHeader(request);
        return authHeader.map(bearerString -> {
            String jws;
            if (bearerString.startsWith(BEARER)) {
                // This chops out 'Bearer' so we get just the token.
                jws = bearerString.substring(BEARER.length());
            } else {
                jws = bearerString;
            }
            LOGGER.debug(() -> "Found auth header in request. It looks like this: " + jws);
            return jws;
        });
    }

    public JwtClaims verifyToken(final String token) throws InvalidJwtException {
        try {
            return toClaims(token);
        } catch (InvalidJwtException e) {
            LOGGER.warn(() -> "Unable to verify token!");
            throw e;
        }
    }

    private static Optional<String> getAuthHeader(final ServletRequest request) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        return (getAuthHeader(httpServletRequest));
    }

    private static Optional<String> getAuthHeader(final HttpServletRequest httpServletRequest) {
        String authHeader = httpServletRequest.getHeader(AUTHORIZATION_HEADER);
        return Strings.isNullOrEmpty(authHeader) ? Optional.empty() : Optional.of(authHeader);
    }

    private JwtClaims toClaims(String token) throws InvalidJwtException {
        final JwtConsumer jwtConsumer = newJwsConsumer();
        return jwtConsumer.processToClaims(token);
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
        HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder().healthy();
        this.checkHealthForJwkRetrieval(resultBuilder);
        return resultBuilder.build();
    }

    private void checkHealthForJwkRetrieval(HealthCheck.ResultBuilder resultBuilder) {
        final String KEY = "public_key_retrieval";
        try {
            final JsonWebKeySet publicJsonWebKey = openIdPublicKeysSupplier.get();
            if (publicJsonWebKey == null) {
                resultBuilder.withDetail(KEY, "Missing public key\n");
                resultBuilder.unhealthy();
            }
        } catch (RuntimeException e) {
            resultBuilder.withDetail(KEY, "Error fetching our identity provider's public key! " +
                    "This means we cannot verify clients' authentication tokens ourselves. " +
                    "This might mean the authentication service is down or unavailable. " +
                    "The error was: [" + e.getMessage() + "]");
            resultBuilder.unhealthy();
        }
    }
}
