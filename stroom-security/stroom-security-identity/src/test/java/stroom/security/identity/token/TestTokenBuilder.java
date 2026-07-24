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

package stroom.security.identity.token;

import stroom.security.identity.config.IdentityConfig;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.PublicJsonWebKeyProvider;

import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Proves that the tokens minted by {@link TokenBuilder} carry the claims a standard OpenID Connect
 * relying party expects, so the internal provider looks like an external one such as Keycloak.
 */
@ExtendWith(MockitoExtension.class)
class TestTokenBuilder {

    private static final String ISSUER = "stroom";
    private static final String CLIENT_ID = "stroom-client";
    private static final String USER_ID = "jbloggs";

    @Mock
    private PublicJsonWebKeyProvider publicJsonWebKeyProvider;
    @Mock
    private OpenIdConfiguration openIdConfiguration;

    private RsaJsonWebKey jwk;
    private TokenBuilderFactory tokenBuilderFactory;

    @BeforeEach
    void setUp() throws JoseException {
        jwk = RsaJwkGenerator.generateJwk(2048);
        jwk.setKeyId("test-key");

        final List<PublicJsonWebKey> keys = List.of(jwk);
        lenient().when(publicJsonWebKeyProvider.list()).thenReturn(keys);
        lenient().when(publicJsonWebKeyProvider.getActiveKey()).thenReturn(jwk);
        lenient().when(openIdConfiguration.getIssuer()).thenReturn(ISSUER);

        tokenBuilderFactory = new TokenBuilderFactory(
                IdentityConfig::new,
                publicJsonWebKeyProvider,
                () -> openIdConfiguration);
    }

    @Test
    void everyTokenHasAUniqueId() {
        final String first = accessTokenBuilder().build();
        final String second = accessTokenBuilder().build();

        final String firstJti = claimsOf(first).getClaimValueAsString("jti");
        final String secondJti = claimsOf(second).getClaimValueAsString("jti");

        assertThat(firstJti).isNotBlank();
        assertThat(secondJti).isNotBlank();
        assertThat(firstJti).isNotEqualTo(secondJti);
    }

    @Test
    void accessTokenIdentifiesTheClient() {
        final JwtClaims claims = claimsOf(accessTokenBuilder().build());

        // azp (Keycloak style) and client_id (RFC 9068) both name the client.
        assertThat(claims.getClaimValueAsString(OpenId.CLAIM__AUTHORIZED_PARTY)).isEqualTo(CLIENT_ID);
        assertThat(claims.getClaimValueAsString(OpenId.CLIENT_ID)).isEqualTo(CLIENT_ID);
    }

    @Test
    void idTokenHasAzpButNotTheAccessTokenOnlyClientIdClaim() {
        // An id token is not an access token, so it carries azp (an OIDC id token claim) but not the
        // RFC 9068 access-token client_id claim.
        final JwtClaims claims = claimsOf(idTokenBuilder().build());

        assertThat(claims.getClaimValueAsString(OpenId.CLAIM__AUTHORIZED_PARTY)).isEqualTo(CLIENT_ID);
        assertThat(claims.hasClaim(OpenId.CLIENT_ID)).isFalse();
    }

    @Test
    void accessTokenCarriesScope() {
        final JwtClaims claims = claimsOf(accessTokenBuilder().scope("openid email").build());

        assertThat(claims.getClaimValueAsString(OpenId.SCOPE)).isEqualTo("openid email");
    }

    @Test
    void idTokenCarriesAuthTimeAsTheLoginTime() {
        final JwtClaims claims = claimsOf(idTokenBuilder().authTime(1_700_000_000L).build());

        assertThat(claims.getClaimValue(OpenId.CLAIM__AUTH_TIME)).isNotNull();
        assertThat(((Number) claims.getClaimValue(OpenId.CLAIM__AUTH_TIME)).longValue())
                .isEqualTo(1_700_000_000L);
    }

    @Test
    void stateIsNeverAClaim() {
        // state is an OAuth redirect parameter, not a token claim, so it must not appear on any token.
        assertThat(claimsOf(accessTokenBuilder().scope("openid").build()).hasClaim(OpenId.STATE)).isFalse();
        assertThat(claimsOf(idTokenBuilder().authTime(1L).build()).hasClaim(OpenId.STATE)).isFalse();
    }

    private TokenBuilder accessTokenBuilder() {
        return tokenBuilderFactory.builder()
                .expirationTime(Instant.now().plusSeconds(600))
                .clientId(CLIENT_ID)
                .subject(USER_ID)
                .type(OpenId.TOKEN_TYPE__ACCESS);
    }

    private TokenBuilder idTokenBuilder() {
        return tokenBuilderFactory.builder()
                .expirationTime(Instant.now().plusSeconds(600))
                .clientId(CLIENT_ID)
                .subject(USER_ID);
    }

    private JwtClaims claimsOf(final String jwt) {
        final JwtConsumer consumer = new JwtConsumerBuilder()
                .setSkipSignatureVerification()
                .setSkipAllValidators()
                .build();
        try {
            final JwtContext context = consumer.process(jwt);
            return context.getJwtClaims();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
