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
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
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

        factory = new InternalJwtContextFactory(
                publicJsonWebKeyProvider,
                () -> openIdConfiguration);
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

    private String buildToken() {
        return buildToken(null);
    }

    private String buildToken(final String type) {
        return buildToken(type, "stroom-client");
    }

    private String buildToken(final String type, final String audience) {
        final JwtClaims claims = new JwtClaims();
        claims.setSubject(USER_ID);
        claims.setIssuer(ISSUER);
        if (audience != null) {
            claims.setAudience(audience);
        }
        claims.setExpirationTime(NumericDate.fromSeconds(
                Instant.now().plusSeconds(600).getEpochSecond()));

        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setKey(jwk.getPrivateKey());
        jws.setKeyIdHeaderValue(jwk.getKeyId());
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
