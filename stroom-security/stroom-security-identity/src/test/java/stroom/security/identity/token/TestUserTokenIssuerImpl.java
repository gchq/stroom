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

package stroom.security.identity.token;

import stroom.security.identity.config.IdentityConfig;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.security.openid.api.RevokedTokenChecker;

import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserTokenIssuerImpl {

    private final OAuthTokenDao oAuthTokenDao = Mockito.mock(OAuthTokenDao.class);
    private final RevokedTokenChecker revokedTokenChecker = Mockito.mock(RevokedTokenChecker.class);
    private final OpenIdConfiguration openIdConfiguration = Mockito.mock(OpenIdConfiguration.class);
    private UserTokenIssuerImpl issuer;

    @BeforeEach
    void setUp() throws JoseException {
        final RsaJsonWebKey jwk = RsaJwkGenerator.generateJwk(2048);
        jwk.setKeyId("test-key");
        final PublicJsonWebKeyProvider keys = Mockito.mock(PublicJsonWebKeyProvider.class);
        Mockito.when(keys.getActiveKey()).thenReturn(jwk);
        Mockito.when(openIdConfiguration.getIssuer()).thenReturn("http://stroom");
        Mockito.when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.INTERNAL_IDP);
        final TokenBuilderFactory factory = new TokenBuilderFactory(IdentityConfig::new, keys,
                () -> openIdConfiguration);
        issuer = new UserTokenIssuerImpl(factory, oAuthTokenDao, revokedTokenChecker, () -> openIdConfiguration);
    }

    @Test
    void aTokenIsForTheUserAndAudienceAndShortLived() throws Exception {
        final String token = issuer.getAccessToken("jbloggs", "stroom-mcp").orElseThrow();

        final JwtClaims claims = claimsOf(token);
        assertThat(claims.getSubject()).isEqualTo("jbloggs");
        assertThat(claims.getAudience()).containsExactly("stroom-mcp");
        assertThat(claims.getIssuer()).isEqualTo("http://stroom");
        final long lifetimeSeconds = claims.getExpirationTime().getValue() - System.currentTimeMillis() / 1000;
        assertThat(lifetimeSeconds).isBetween(UserTokenIssuerImpl.LIFETIME.toSeconds() - 5,
                UserTokenIssuerImpl.LIFETIME.toSeconds());
        // Recorded, so it can be seen and revoked.
        final ArgumentCaptor<OAuthToken> recorded = ArgumentCaptor.forClass(OAuthToken.class);
        Mockito.verify(oAuthTokenDao).create(recorded.capture());
        assertThat(recorded.getValue().jti()).isEqualTo(claims.getJwtId());
        assertThat(recorded.getValue().subjectId()).isEqualTo("jbloggs");
    }

    @Test
    void aTokenIsReusedUntilRevoked() {
        final String first = issuer.getAccessToken("jbloggs", "stroom-mcp").orElseThrow();

        assertThat(issuer.getAccessToken("jbloggs", "stroom-mcp")).contains(first);
        // Another audience or user gets its own.
        assertThat(issuer.getAccessToken("jbloggs", "other-mcp")).isNotEqualTo(Optional.of(first));
        assertThat(issuer.getAccessToken("asmith", "stroom-mcp")).isNotEqualTo(Optional.of(first));

        // E.g. the user signed out.
        Mockito.when(revokedTokenChecker.isRevoked(Mockito.anyString())).thenReturn(true);
        assertThat(issuer.getAccessToken("jbloggs", "stroom-mcp")).isNotEqualTo(Optional.of(first));
    }

    @Test
    void noTokensWithAnExternalIdentityProvider() {
        Mockito.when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.EXTERNAL_IDP);

        assertThat(issuer.isAvailable()).isFalse();
        assertThat(issuer.getAccessToken("jbloggs", "stroom-mcp")).isEmpty();
        Mockito.verifyNoInteractions(oAuthTokenDao);
    }

    @Test
    void noTokenWithoutAUserOrAudience() {
        assertThat(issuer.getAccessToken(" ", "stroom-mcp")).isEmpty();
        assertThat(issuer.getAccessToken("jbloggs", null)).isEmpty();
    }

    private static JwtClaims claimsOf(final String jwt) throws Exception {
        return new JwtConsumerBuilder()
                .setSkipSignatureVerification()
                .setSkipAllValidators()
                .build()
                .processToClaims(jwt);
    }
}
