/*
 * Copyright 2020 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.impl;

import stroom.security.common.impl.JwtContextFactory;
import stroom.security.common.impl.JwtUtil;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.security.openid.api.RevokedTokenChecker;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.jwx.JsonWebStructure;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class InternalJwtContextFactory implements JwtContextFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InternalJwtContextFactory.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final PublicJsonWebKeyProvider publicJsonWebKeyProvider;
    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final RevokedTokenChecker revokedTokenChecker;
    private final StaleKeySetRecovery staleKeySetRecovery;

    @Inject
    InternalJwtContextFactory(final PublicJsonWebKeyProvider publicJsonWebKeyProvider,
                              final Provider<OpenIdConfiguration> openIdConfigurationProvider,
                              final RevokedTokenChecker revokedTokenChecker,
                              final StaleKeySetRecovery staleKeySetRecovery) {
        this.publicJsonWebKeyProvider = publicJsonWebKeyProvider;
        this.openIdConfigurationProvider = openIdConfigurationProvider;
        this.revokedTokenChecker = revokedTokenChecker;
        this.staleKeySetRecovery = staleKeySetRecovery;
    }

    @Override
    public boolean hasToken(final HttpServletRequest request) {
        return getJwtFromHeader(request)
                .isPresent();
    }

    @Override
    public void removeAuthorisationEntries(final Map<String, String> headers) {
        if (NullSafe.hasEntries(headers)) {
            headers.remove(AUTHORIZATION_HEADER);
        }
    }

    @Override
    public Map<String, String> createAuthorisationEntries(final String accessToken) {
        // Should be common to both internal and external IDPs
        if (NullSafe.isBlankString(accessToken)) {
            return Collections.emptyMap();
        } else {
            return Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
    }

    @Override
    public Optional<JwtContext> getJwtContext(final HttpServletRequest request) {
        LOGGER.trace(() -> AUTHORIZATION_HEADER + "=" + request.getHeader(AUTHORIZATION_HEADER));

        final Optional<String> optionalJws = getJwtFromHeader(request);
        return optionalJws
                .flatMap(this::getJwtContext)
                .filter(this::isAccessToken)
                .or(() -> {
                    LOGGER.debug(() -> "No usable access token found in headers in request to "
                                       + request.getRequestURI());
                    return Optional.empty();
                });
    }

    /**
     * Only an access token may authenticate a request. Per RFC 9068 an access token carries the JOSE
     * {@code typ} header {@code at+jwt}; id, refresh and reset tokens do not, so they are rejected from
     * the bearer path here even though their signature is valid.
     */
    private boolean isAccessToken(final JwtContext jwtContext) {
        final List<JsonWebStructure> joseObjects = jwtContext.getJoseObjects();
        final String type = joseObjects.isEmpty()
                ? null
                : joseObjects.getLast().getHeaders().getStringHeaderValue("typ");
        if (!OpenId.TOKEN_TYPE__ACCESS.equals(type)) {
            LOGGER.warn(() -> LogUtil.message(
                    "Rejecting a token presented as a bearer credential that is not an access token "
                    + "(typ '{}', expected '{}')", type, OpenId.TOKEN_TYPE__ACCESS));
            return false;
        }
        return true;
    }

    private Optional<String> getJwtFromHeader(final HttpServletRequest request) {
        return JwtUtil.getJwsFromHeader(request, AUTHORIZATION_HEADER);
    }

    /**
     * Has this token been revoked before its natural expiry?
     * <p>
     * Answered from an in-memory denylist, so this adds no database work to the request path. Only tokens
     * minted by the internal IdP can appear on it - this factory is the {@code INTERNAL_IDP} arm of
     * {@link DelegatingJwtContextFactory}, so externally minted tokens are never tested here, and their
     * revocation remains the external IdP's business.
     * </p>
     */
    private boolean isRevoked(final JwtContext jwtContext) {
        final String jti;
        try {
            jti = jwtContext.getJwtClaims().getJwtId();
        } catch (final MalformedClaimException e) {
            // A jti that is present but not a string. We cannot identify the token, so we cannot show it has
            // been revoked; the signature still proves we minted it. Logged because it should not happen.
            LOGGER.warn(() -> "Token has a malformed jti claim: " + e.getMessage(), e);
            return false;
        }
        if (revokedTokenChecker.isRevoked(jti)) {
            LOGGER.info(() -> LogUtil.message(
                    "Rejecting a revoked token (jti '{}', subject '{}')",
                    jti,
                    // Read as a raw string rather than via getSubject(), which throws a checked exception
                    // that would be pointless noise inside a log message supplier.
                    jwtContext.getJwtClaims().getClaimValueAsString(OpenId.CLAIM__SUBJECT)));
            return true;
        }
        return false;
    }

    /**
     * Verify the JSON Web Signature and then extract the user identity from it
     */
    @Override
    public Optional<JwtContext> getJwtContext(final String jwt) {
        Objects.requireNonNull(jwt, "Null JWS");
        LOGGER.trace(() -> "Found auth header in request. It looks like this: " + jwt);

        try {
            return verify(jwt);
        } catch (final RuntimeException | InvalidJwtException e) {
            if (staleKeySetRecovery.isUnresolvableKey(e) && staleKeySetRecovery.tryRefresh()) {
                // The key set may simply be stale - a rotation on another node can mint tokens with a kid this
                // node has not loaded yet. Reload once and retry before writing the token off.
                LOGGER.debug("Retrying verification after refreshing the key set");
                try {
                    // Deliberately the same verify(), so the retry gets the identical checks - including the
                    // revocation check. Bypassing it here would let a revoked token signed with a rotated key
                    // slip through on the retry path.
                    return verify(jwt);
                } catch (final RuntimeException | InvalidJwtException retryFailure) {
                    LOGGER.debug(() -> "Unable to verify token after refreshing keys: "
                                       + retryFailure.getMessage(), retryFailure);
                    return Optional.empty();
                }
            }
            // You will likely come in here when trying to decode an external IDP jws using the internal IDP
            // first.
            LOGGER.debug(() -> "Unable to verify token: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Extract a token's claims, optionally without verifying it.
     * <p>
     * The unverified form exists for callers that only need to read a claim, and applies <b>none</b> of the
     * checks in {@link #verify(String)} - no signature, no expiry, no revocation. It must never be used to
     * decide whether a request is authorised.
     * </p>
     */
    @Override
    public Optional<JwtContext> getJwtContext(final String jwt, final boolean doVerification) {
        if (doVerification) {
            return getJwtContext(jwt);
        }
        final JwtConsumer simpleJwtConsumer = new JwtConsumerBuilder()
                .setSkipSignatureVerification()
                .setSkipDefaultAudienceValidation()
                .build();
        try {
            return Optional.of(simpleJwtConsumer.process(jwt));
        } catch (final Exception e) {
            LOGGER.debug(() -> "Unable to extract token: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Verify a token and apply every check that governs whether it may be used. The single place both the
     * first attempt and the post-key-reload retry go through, so the two cannot drift apart.
     */
    private Optional<JwtContext> verify(final String jwt) throws InvalidJwtException {
        final JwtContext jwtContext = newJwtConsumer().process(jwt);

        // Only after the signature, issuer, audience and expiry checks above have passed. Order matters: the
        // jti of an unverified token proves nothing, so it must never be trusted enough to look up.
        if (isRevoked(jwtContext)) {
            return Optional.empty();
        }

        if (LOGGER.isDebugEnabled()) {
            final String uniqueIdentityClaim = openIdConfigurationProvider.get().getUniqueIdentityClaim();
            final String userDisplayNameClaim = openIdConfigurationProvider.get().getUserDisplayNameClaim();
            final String uniqueId = NullSafe.isBlankString(uniqueIdentityClaim)
                    ? "<ERROR uniqueIdentityClaim not configured>"
                    : JwtUtil.getClaimValue(jwtContext, uniqueIdentityClaim).orElse(null);
            final String displayName = NullSafe.isBlankString(userDisplayNameClaim)
                    ? "<ERROR userDisplayNameClaim not configured>"
                    : JwtUtil.getClaimValue(jwtContext, userDisplayNameClaim).orElse(null);

            LOGGER.debug(() -> LogUtil.message("Verified token - {}: '{}', {}: '{}'",
                    uniqueIdentityClaim, uniqueId, userDisplayNameClaim, displayName));
        }

        return Optional.of(jwtContext);
    }

    private JwtConsumer newJwtConsumer() {
        // If we don't have a JWK we can't create a consumer to verify anything.
        // Why might we not have one? If the remote authentication service was down when Stroom started
        // then we wouldn't. It might not be up now but we're going to try and fetch it.
        final List<PublicJsonWebKey> publicJsonWebKeys = publicJsonWebKeyProvider.list();
        final JsonWebKeySet publicJsonWebKey = new JsonWebKeySet(publicJsonWebKeys);

        final VerificationKeyResolver verificationKeyResolver = new JwksVerificationKeyResolver(
                publicJsonWebKey.getJsonWebKeys());

        final OpenIdConfiguration openIdConfiguration = openIdConfigurationProvider.get();
        final String[] validIssuers = getValidIssuers();

        final JwtConsumerBuilder builder = new JwtConsumerBuilder()
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims
                // to account for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setVerificationKeyResolver(verificationKeyResolver)
                .setRelaxVerificationKeyValidation() // relaxes key length requirement
                .setJwsAlgorithmConstraints(// only allow the expected signature algorithm(s) in the given context
                        new AlgorithmConstraints(
                                ConstraintType.PERMIT, // which is only RS256 here
                                AlgorithmIdentifiers.RSA_USING_SHA256))
                .setExpectedIssuers(true, validIssuers);

        final Set<String> allowedAudiences = openIdConfiguration.getAllowedAudiences();
        if (NullSafe.hasItems(allowedAudiences)) {
            // The IDP may not supply the aud claim
            builder.setExpectedAudience(
                    openIdConfiguration.isAudienceClaimRequired(),
                    allowedAudiences.toArray(String[]::new));
        } else {
            builder.setSkipDefaultAudienceValidation();
        }
        LOGGER.debug("validIssuers: {}, allowedAudiences: {}, audienceClaimRequired: {}",
                validIssuers,
                allowedAudiences,
                openIdConfiguration.isAudienceClaimRequired());
        return builder.build();
    }

    private String[] getValidIssuers() {
        final OpenIdConfiguration openIdConfiguration = openIdConfigurationProvider.get();
        if (NullSafe.isBlankString(openIdConfiguration.getIssuer())) {
            throw new RuntimeException(LogUtil.message(
                    "'issuer' is not defined in the IDP's or Stroom's configuration"));
        }
        final String[] validIssuers = Stream.concat(
                        Stream.of(openIdConfiguration.getIssuer()),
                        NullSafe.stream(openIdConfiguration.getValidIssuers()))
                .filter(Objects::nonNull)
                .filter(str -> !str.isBlank())
                .distinct()
                .toArray(String[]::new);
        LOGGER.debug(() -> LogUtil.message("Valid issuers:\n{}", String.join("\n", validIssuers)));
        return validIssuers;
    }
}
