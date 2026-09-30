/*
 * Copyright 2026 Crown Copyright
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

import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.PublicJsonWebKeyProvider;

import jakarta.servlet.http.HttpServletRequest;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestInternalJwtContextFactory {

    private static final String ISSUER = "stroom";
    private static final String USER_ID = "jbloggs";

    @Mock
    private PublicJsonWebKeyProvider publicJsonWebKeyProvider;
    @Mock
    private OpenIdConfiguration openIdConfiguration;

    private RsaJsonWebKey jwk;
    /**
     * The denylist the factory consults. Mutable so a test can revoke a token after it has been minted,
     * which is what really happens.
     */
    private final Set<String> revokedJtis = new HashSet<>();
    /**
     * Records every jti the factory asked about, so a test can assert the check was not reached at all.
     */
    private final List<String> checkedJtis = new ArrayList<>();
    private InternalJwtContextFactory factory;

    @BeforeEach
    void setUp() throws JoseException {
        jwk = RsaJwkGenerator.generateJwk(2048);
        jwk.setKeyId("test-key");

        final List<PublicJsonWebKey> keys = List.of(jwk);
        lenient().when(publicJsonWebKeyProvider.list()).thenReturn(keys);
        lenient().when(openIdConfiguration.getIssuer()).thenReturn(ISSUER);
        lenient().when(openIdConfiguration.getValidIssuers()).thenReturn(Collections.emptySet());
        // Mirrors the shipped default, which means audience is not validated.
        lenient().when(openIdConfiguration.getAllowedAudiences()).thenReturn(Collections.emptySet());

        revokedJtis.clear();
        checkedJtis.clear();
        factory = new InternalJwtContextFactory(
                publicJsonWebKeyProvider,
                () -> openIdConfiguration,
                jti -> {
                    checkedJtis.add(jti);
                    return revokedJtis.contains(jti);
                },
                new StaleKeySetRecovery(publicJsonWebKeyProvider));
    }

    @Test
    void normalTokenIsAccepted() {
        final Optional<JwtContext> context = factory.getJwtContext(buildToken());

        assertThat(context).isPresent();
        assertThat(context.get().getJwtClaims().getClaimValueAsString(OpenId.CLAIM__SUBJECT))
                .isEqualTo(USER_ID);
    }

    @Test
    void accessTokenIsAcceptedOnTheBearerPath() {
        final String accessToken = buildToken(OpenId.TOKEN_TYPE__ACCESS);
        assertThat(factory.getJwtContext(bearerRequest(accessToken))).isPresent();
    }

    @Test
    void tokenWithoutAccessTypeIsRejectedOnTheBearerPath() {
        // An id or refresh token is a validly signed JWT but is not an access token, so it must not
        // authenticate a request even though its signature verifies.
        final String idTokenShaped = buildToken();
        assertThat(factory.getJwtContext(bearerRequest(idTokenShaped))).isEmpty();
    }

    @Test
    void tokenIsAcceptedOnlyForAnAllowedAudience() {
        // With aud validation on, a token must carry an allowed audience and, since it is required, must
        // carry one at all. The internal provider requires its own client id here.
        when(openIdConfiguration.getAllowedAudiences()).thenReturn(Set.of("stroom-client"));
        when(openIdConfiguration.isAudienceClaimRequired()).thenReturn(true);

        assertThat(factory.getJwtContext(buildToken(null, "stroom-client"))).isPresent();
        assertThat(factory.getJwtContext(buildToken(null, "some-other-client"))).isEmpty();
        assertThat(factory.getJwtContext(buildToken(null, null))).isEmpty();
    }

    private HttpServletRequest bearerRequest(final String token) {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        return request;
    }

    // --- Revocation ----------------------------------------------------------------------------------

    @Test
    void revokedTokenIsRejectedEvenThoughItsSignatureIsValid() {
        final String jti = "revoked-jti";
        final String token = buildToken(OpenId.TOKEN_TYPE__ACCESS, "stroom-client", jti);

        // Before revocation it is accepted, so the rejection below is caused by the revocation and not by
        // something else about the token.
        assertThat(factory.getJwtContext(token)).isPresent();

        revokedJtis.add(jti);

        assertThat(factory.getJwtContext(token))
                .as("a revoked token must be refused before its natural expiry")
                .isEmpty();
    }

    @Test
    void revocationAlsoAppliesOnTheBearerPath() {
        final String jti = "revoked-jti";
        final String accessToken = buildToken(OpenId.TOKEN_TYPE__ACCESS, "stroom-client", jti);
        assertThat(factory.getJwtContext(bearerRequest(accessToken))).isPresent();

        revokedJtis.add(jti);

        assertThat(factory.getJwtContext(bearerRequest(accessToken))).isEmpty();
    }

    @Test
    void tokenThatIsNotRevokedIsUnaffected() {
        final String token = buildToken(OpenId.TOKEN_TYPE__ACCESS, "stroom-client", "live-jti");
        revokedJtis.add("some-other-users-jti");

        assertThat(factory.getJwtContext(token)).isPresent();
    }

    @Test
    void tokenWithNoJtiIsHonoured() {
        // Revocation is a denylist, so a token that cannot be named cannot be on it. Its signature and
        // expiry are what prove it is ours, and those have already been checked.
        assertThat(factory.getJwtContext(buildToken(OpenId.TOKEN_TYPE__ACCESS))).isPresent();
        assertThat(checkedJtis).containsExactly((String) null);
    }

    @Test
    void theDenylistIsConsultedOnlyAfterTheSignatureHasBeenVerified() {
        // Order matters: the jti of an unverified token is attacker-controlled, so it must never be trusted
        // enough to look up. A garbage token must be thrown out before the denylist is touched at all.
        assertThat(factory.getJwtContext("not.a.jwt")).isEmpty();

        assertThat(checkedJtis)
                .as("an unverifiable token must be rejected without consulting the denylist")
                .isEmpty();
    }

    @Test
    void anExpiredTokenIsRejectedWithoutConsultingTheDenylist() {
        // Same ordering point for the expiry check - a revoked-and-expired token needs no denylist entry,
        // which is what lets the denylist stay bounded to unexpired tokens only.
        final String expired = buildExpiredToken("expired-jti");

        assertThat(factory.getJwtContext(expired)).isEmpty();
        assertThat(checkedJtis).isEmpty();
    }


    // --- Key rotation backstop -----------------------------------------------------------------------

    @Test
    void tokenSignedWithAKeyThisNodeHasNotLoadedTriggersRefreshThenVerifies() {
        // After a rotation on another node, a valid token can name a kid this node's cached key set does not
        // contain. Without the refresh-and-retry it would be rejected until the cache happened to reload.
        final RsaJsonWebKey rotatedKey = newKey("rotated-key");
        final String token = buildToken(OpenId.TOKEN_TYPE__ACCESS, "stroom-client", "jti-1", rotatedKey);

        // The provider only learns about the new key once it has been refreshed.
        when(publicJsonWebKeyProvider.list()).thenReturn(List.of(jwk));
        Mockito.doAnswer(inv -> {
            when(publicJsonWebKeyProvider.list()).thenReturn(List.of(jwk, rotatedKey));
            return null;
        }).when(publicJsonWebKeyProvider).refresh();

        assertThat(factory.getJwtContext(token)).isPresent();

        Mockito.verify(publicJsonWebKeyProvider).refresh();
    }

    @Test
    void theRetryAfterAKeyRefreshStillEnforcesRevocation() {
        // The retry must not be a way around the denylist: a revoked token signed with a rotated key has to
        // stay rejected even though the refresh makes its signature verifiable.
        final RsaJsonWebKey rotatedKey = newKey("rotated-key");
        final String token = buildToken(OpenId.TOKEN_TYPE__ACCESS, "stroom-client", "revoked-jti", rotatedKey);
        revokedJtis.add("revoked-jti");

        when(publicJsonWebKeyProvider.list()).thenReturn(List.of(jwk));
        Mockito.doAnswer(inv -> {
            when(publicJsonWebKeyProvider.list()).thenReturn(List.of(jwk, rotatedKey));
            return null;
        }).when(publicJsonWebKeyProvider).refresh();

        assertThat(factory.getJwtContext(token))
                .as("a revoked token must stay revoked on the post-refresh retry")
                .isEmpty();
    }

    @Test
    void repeatedUnknownKidsDoNotDriveRepeatedKeyRefreshes() {
        // A kid is an unauthenticated header read before any signature check, so anyone can name a key that
        // does not exist. Refreshing per request would turn that into database load on demand.
        for (int i = 0; i < 20; i++) {
            final RsaJsonWebKey strangerKey = newKey("stranger-" + i);
            factory.getJwtContext(
                    buildToken(OpenId.TOKEN_TYPE__ACCESS, "stroom-client", "jti-" + i, strangerKey));
        }

        Mockito.verify(publicJsonWebKeyProvider, Mockito.times(1)).refresh();
    }

    @Test
    void badSignatureDoesNotTriggerAKeyRefresh() {
        // Only an unresolvable key is worth reloading for. A wrong signature against a known key means the
        // token is simply not ours.
        final RsaJsonWebKey impostor = newKey("test-key");   // same kid, different key material
        final String token = buildToken(OpenId.TOKEN_TYPE__ACCESS, "stroom-client", "jti-1", impostor);

        assertThat(factory.getJwtContext(token)).isEmpty();

        Mockito.verify(publicJsonWebKeyProvider, Mockito.never()).refresh();
    }

    private static RsaJsonWebKey newKey(final String keyId) {
        try {
            final RsaJsonWebKey key = RsaJwkGenerator.generateJwk(2048);
            key.setKeyId(keyId);
            return key;
        } catch (final JoseException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildToken() {
        return buildToken(null);
    }

    private String buildToken(final String type) {
        return buildToken(type, "stroom-client");
    }

    private String buildToken(final String type, final String audience) {
        return buildToken(type, audience, null);
    }

    private String buildToken(final String type, final String audience, final String jti) {
        return buildToken(type, audience, jti, Instant.now().plusSeconds(600));
    }

    private String buildToken(final String type,
                              final String audience,
                              final String jti,
                              final RsaJsonWebKey signingKey) {
        return buildToken(type, audience, jti, Instant.now().plusSeconds(600), signingKey);
    }

    private String buildExpiredToken(final String jti) {
        // Beyond the 30s clock skew the consumer allows.
        return buildToken(OpenId.TOKEN_TYPE__ACCESS, "stroom-client", jti, Instant.now().minusSeconds(600));
    }

    private String buildToken(final String type,
                              final String audience,
                              final String jti,
                              final Instant expiry) {
        return buildToken(type, audience, jti, expiry, jwk);
    }

    private String buildToken(final String type,
                              final String audience,
                              final String jti,
                              final Instant expiry,
                              final RsaJsonWebKey signingKey) {
        final JwtClaims claims = new JwtClaims();
        claims.setSubject(USER_ID);
        claims.setIssuer(ISSUER);
        if (audience != null) {
            claims.setAudience(audience);
        }
        if (jti != null) {
            claims.setJwtId(jti);
        }
        claims.setExpirationTime(NumericDate.fromSeconds(expiry.getEpochSecond()));

        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKey(signingKey.getPrivateKey());
        jws.setKeyIdHeaderValue(signingKey.getKeyId());
        jws.setDoKeyValidation(true);
        if (type != null) {
            jws.setHeader("typ", type);
        }
        try {
            return jws.getCompactSerialization();
        } catch (final JoseException e) {
            throw new RuntimeException(e);
        }
    }
}
