/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
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
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

class InternalJwtContextFactory implements JwtContextFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InternalJwtContextFactory.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final OpenIdClientFactory openIdClientDetailsFactory;
    private final PublicJsonWebKeyProvider publicJsonWebKeyProvider;
    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;

    @Inject
    InternalJwtContextFactory(final OpenIdClientFactory openIdClientDetailsFactory,
                              final PublicJsonWebKeyProvider publicJsonWebKeyProvider,
                              final Provider<OpenIdConfiguration> openIdConfigurationProvider) {
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
        this.publicJsonWebKeyProvider = publicJsonWebKeyProvider;
        this.openIdConfigurationProvider = openIdConfigurationProvider;
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
                .or(() -> {
                    LOGGER.debug(() -> "No JWS found in headers in request to " + request.getRequestURI());
                    return Optional.empty();
                });
    }

    private Optional<String> getJwtFromHeader(final HttpServletRequest request) {
        return JwtUtil.getJwsFromHeader(request, AUTHORIZATION_HEADER);
    }

    /**
     * Verify the JSON Web Signature and then extract the user identity from it
     */
    @Override
    public Optional<JwtContext> getJwtContext(final String jwt) {
        Optional<JwtContext> optionalJwtContext = Optional.empty();

        Objects.requireNonNull(jwt, "Null JWS");
        LOGGER.trace(() -> "Found auth header in request. It looks like this: " + jwt);

        try {
            final JwtConsumer jwtConsumer = newJwtConsumer();
            final JwtContext jwtContext = jwtConsumer.process(jwt);

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

            optionalJwtContext = Optional.ofNullable(jwtContext);

        } catch (final RuntimeException | InvalidJwtException e) {
            // You will likely come in here when trying to decode an external IDP jws using the internal IDP
            // first.
            LOGGER.debug(() -> "Unable to verify token: " + e.getMessage(), e);
        }

        return optionalJwtContext;
    }

    @Override
    public Optional<JwtContext> getJwtContext(final String jwt, final boolean doVerification) {
        Optional<JwtContext> optJwtContext = Optional.empty();
        if (doVerification) {
            optJwtContext = getJwtContext(jwt);
        } else {
            final JwtConsumer simpleJwtConsumer = new JwtConsumerBuilder()
                    .setSkipSignatureVerification()
                    .setSkipDefaultAudienceValidation()
                    .build();
            try {
                optJwtContext = Optional.of(simpleJwtConsumer.process(jwt));
            } catch (final Exception e) {
                LOGGER.debug(() -> "Unable to extract token: " + e.getMessage(), e);
            }
        }
        return optJwtContext;
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
//                .setExpectedIssuer(InternalIdpConfigurationProvider.INTERNAL_ISSUER);
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
