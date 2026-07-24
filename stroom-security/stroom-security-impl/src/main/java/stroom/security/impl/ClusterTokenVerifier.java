/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.security.common.impl.JwtUtil;
import stroom.security.openid.api.ClusterToken;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;
import java.util.Optional;

/**
 * Verifies the internally-signed inter-node cluster (processing-user) token. This is the <em>only</em> path
 * that may promote a request to the processing user, and it behaves identically in every {@link
 * stroom.security.openid.api.IdpType} mode - the internal signing key is the sole trust anchor.
 * <p>
 * A token is accepted only if <strong>all</strong> of the following hold:
 * <ul>
 *     <li>it is signed (RS256) by the cluster's internal signing key,</li>
 *     <li>{@code iss} = {@link ClusterToken#CLUSTER_ISSUER},</li>
 *     <li>{@code aud} = {@link ClusterToken#CLUSTER_AUDIENCE} (the audience claim is required),</li>
 *     <li>the JOSE {@code typ} header = {@code at+jwt}, and</li>
 *     <li>{@code sub} = {@link ClusterToken#PROCESSING_USER_SUBJECT}.</li>
 * </ul>
 * A token minted by a real (e.g. external) identity provider is rejected because it is not signed with the
 * internal key and does not carry the cluster issuer/audience - even if it carries
 * {@code sub=INTERNAL_PROCESSING_USER}. Only a token signed by the internal key can authorise the processing
 * user.
 */
public class ClusterTokenVerifier {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ClusterTokenVerifier.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String TYP_HEADER = "typ";

    private final PublicJsonWebKeyProvider publicJsonWebKeyProvider;

    @Inject
    ClusterTokenVerifier(final PublicJsonWebKeyProvider publicJsonWebKeyProvider) {
        this.publicJsonWebKeyProvider = publicJsonWebKeyProvider;
    }

    /**
     * @return the verified {@link JwtContext} if the request carries a valid internal cluster token, else
     * empty.
     */
    public Optional<JwtContext> verify(final HttpServletRequest request) {
        return JwtUtil.getJwsFromHeader(request, AUTHORIZATION_HEADER)
                .flatMap(this::verify);
    }

    /**
     * @return the verified {@link JwtContext} if the supplied JWS is a valid internal cluster token, else
     * empty.
     */
    public Optional<JwtContext> verify(final String jwt) {
        if (jwt == null) {
            return Optional.empty();
        }
        try {
            final JwtContext jwtContext = newJwtConsumer().process(jwt);
            if (isClusterToken(jwtContext)) {
                return Optional.of(jwtContext);
            }
        } catch (final RuntimeException | InvalidJwtException e) {
            // Expected when the token is not an internal cluster token (wrong key/issuer/audience/expiry).
            LOGGER.debug(() -> "Not a valid internal cluster token: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    private boolean isClusterToken(final JwtContext jwtContext) {
        // typ is a JOSE header, not a claim, so it is checked here rather than on the consumer.
        final List<JsonWebStructure> joseObjects = jwtContext.getJoseObjects();
        final String type = joseObjects.isEmpty()
                ? null
                : joseObjects.getLast().getHeaders().getStringHeaderValue(TYP_HEADER);
        if (!OpenId.TOKEN_TYPE__ACCESS.equals(type)) {
            LOGGER.debug(() -> "Rejecting cluster token: typ '" + type + "' != '"
                               + OpenId.TOKEN_TYPE__ACCESS + "'");
            return false;
        }
        try {
            final String subject = jwtContext.getJwtClaims().getSubject();
            if (!ClusterToken.PROCESSING_USER_SUBJECT.equals(subject)) {
                LOGGER.debug(() -> "Rejecting cluster token: sub '" + subject + "' != '"
                                   + ClusterToken.PROCESSING_USER_SUBJECT + "'");
                return false;
            }
        } catch (final MalformedClaimException e) {
            return false;
        }
        return true;
    }

    private JwtConsumer newJwtConsumer() {
        final List<PublicJsonWebKey> publicJsonWebKeys = publicJsonWebKeyProvider.list();
        final JsonWebKeySet jsonWebKeySet = new JsonWebKeySet(publicJsonWebKeys);
        final VerificationKeyResolver verificationKeyResolver = new JwksVerificationKeyResolver(
                jsonWebKeySet.getJsonWebKeys());

        return new JwtConsumerBuilder()
                .setAllowedClockSkewInSeconds(30) // leeway for clock skew on the time-based claims
                .setRequireSubject()
                .setVerificationKeyResolver(verificationKeyResolver)
                .setRelaxVerificationKeyValidation() // relaxes the key length requirement
                // Pin RS256 - the internal IdP signs with RS256; this blocks 'none' and HMAC alg-confusion.
                .setJwsAlgorithmConstraints(new AlgorithmConstraints(
                        ConstraintType.PERMIT,
                        AlgorithmIdentifiers.RSA_USING_SHA256))
                .setExpectedIssuers(true, ClusterToken.CLUSTER_ISSUER)
                // The audience claim is required and must be the cluster audience.
                .setExpectedAudience(true, ClusterToken.CLUSTER_AUDIENCE)
                .build();
    }
}
