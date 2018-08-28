package stroom.security;

import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.auth.service.ApiException;
import stroom.auth.service.api.ApiKeyApi;
import stroom.security.AuthenticationConfig.JwtConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

@Singleton
class JWTService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JWTService.class);

    private static final String BEARER = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private PublicJsonWebKey jwk;
    private final String authJwtIssuer;
    private AuthenticationServiceClients authenticationServiceClients;
    private final boolean checkTokenRevocation;

    @Inject
    JWTService(final AuthenticationConfig securityConfig,
               final JwtConfig jwtConfig,
               final AuthenticationServiceClients authenticationServiceClients) {
        this.authJwtIssuer = jwtConfig.getJwtIssuer();
        this.authenticationServiceClients = authenticationServiceClients;
        this.checkTokenRevocation = jwtConfig.isEnableTokenRevocationCheck();

        if (securityConfig.isAuthenticationRequired()) {
            fetchNewPublicKeys();

            if (securityConfig.getAuthServicesBaseUrl() == null) {
                throw new SecurityException("No authentication service URL is defined");
            }
        }
    }

    /**
     * Check to see if the remote authentication service has published a public key.
     * <p>
     * We need this key to verify id tokens.
     * <p>
     * We need to do this if the remote public key changes and verification fails.
     */
    private void fetchNewPublicKeys() {
        // We need to fetch the public key from the remote authentication service.
        final ApiKeyApi apiKeyApi = authenticationServiceClients.newApiKeyApi();
        try {
            String jwkAsJson = apiKeyApi.getPublicKey();
            jwk = RsaJsonWebKey.Factory.newPublicJwk(jwkAsJson);
        } catch (JoseException | ApiException e) {
            LOGGER.error("Unable to fetch the remote authentication service's public key!", e);
        }
    }

    public boolean containsValidJws(ServletRequest request) {
        Optional<String> authHeader = getAuthHeader(request);
        String jws;
        if (authHeader.isPresent()) {
            String bearerString = authHeader.get();

            if (bearerString.startsWith(BEARER)) {
                // This chops out 'Bearer' so we get just the token.
                jws = bearerString.substring(BEARER.length());
            } else {
                jws = bearerString;
            }
            LOGGER.debug("Found auth header in request. It looks like this: {}", jws);
        } else {
            // If there's no token then we've nothing to do.
            return false;
        }

        try {
            if (checkTokenRevocation) {
                LOGGER.debug("Checking token revocation status in remote auth service...");
                AuthenticationToken authenticationToken = checkToken(jws);
                return authenticationToken.getUserId() != null;
            } else {
                LOGGER.debug("Verifying token...");
                Optional<JwtClaims> jwtClaimsOptional = verifyToken(jws);
                // If we have claims then we successfully verified the token.
                return jwtClaimsOptional.isPresent();
            }

        } catch (final RuntimeException e) {
            LOGGER.error("Unable to verify token:", e.getMessage(), e);
            // If we get an exception verifying the token then we need to log the message
            // and continue as if the token wasn't provided.
            // TODO: decide if this should be handled by an exception and how
            return false;
        }

    }

    public Optional<String> getJws(ServletRequest request) {
        Optional<String> authHeader = getAuthHeader(request);
        Optional<String> jws = Optional.empty();
        if (authHeader.isPresent()) {
            String bearerString = authHeader.get();
            if (bearerString.startsWith(BEARER)) {
                // This chops out 'Bearer' so we get just the token.
                jws = Optional.of(bearerString.substring(BEARER.length()));
            } else {
                jws = Optional.of(bearerString);
            }
            LOGGER.debug("Found auth header in request. It looks like this: {}", jws);
        }
        return jws;
    }

    private AuthenticationToken checkToken(String token) {
        try {
            LOGGER.debug("Checking with the Authentication Service that a token is valid.");
            String usersEmail = authenticationServiceClients.newAuthenticationApi().verifyToken(token);
            return new AuthenticationToken(usersEmail, token);
        } catch (ApiException e) {
            throw new RuntimeException("Unable to verify token remotely!", e);
        }
    }

    public Optional<JwtClaims> verifyToken(String token) {
        try {
            return Optional.of(toClaims(token));
        } catch (InvalidJwtException e) {
            LOGGER.warn("Unable to verify token! I'm going to refresh the verification key and try again.");
            fetchNewPublicKeys();
            try {
                return Optional.of(toClaims(token));
            } catch (InvalidJwtException e1) {
                LOGGER.warn("I refreshed the verification key but was still unable to verify this token.");
                return Optional.empty();
            }
        }
    }

    private static Optional<String> getAuthHeader(ServletRequest request) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        return (getAuthHeader(httpServletRequest));
    }

    private static Optional<String> getAuthHeader(HttpServletRequest httpServletRequest) {
        String authHeader = httpServletRequest.getHeader(AUTHORIZATION_HEADER);
        return authHeader == null || authHeader.isEmpty() ? Optional.empty() : Optional.of(authHeader);
    }

    private JwtClaims toClaims(String token) throws InvalidJwtException {
        final JwtConsumer jwtConsumer = newJwsConsumer();
        return jwtConsumer.processToClaims(token);
    }

    private JwtConsumer newJwsConsumer() {
        // If we don't have a JWK we can't create a consumer to verify anything.
        // Why might we not have one? If the remote authentication service was down when Stroom started
        // then we wouldn't. It might not be up now but we're going to try and fetch it.
        if (jwk == null) {
            fetchNewPublicKeys();
        }

        JwtConsumerBuilder builder = new JwtConsumerBuilder()
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setVerificationKey(this.jwk.getPublicKey()) // verify the signature with the public key
                .setRelaxVerificationKeyValidation() // relaxes key length requirement
                .setJwsAlgorithmConstraints( // only allow the expected signature algorithm(s) in the given context
                        new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.WHITELIST, // which is only RS256 here
                                AlgorithmIdentifiers.RSA_USING_SHA256))
                .setExpectedIssuer(authJwtIssuer);
        return builder.build();
    }
}
