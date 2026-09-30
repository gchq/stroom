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

package stroom.security.identity.openid;

import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.token.OAuthToken;
import stroom.security.identity.token.OAuthTokenType;
import stroom.security.identity.token.RefreshRotationResult;
import stroom.security.identity.token.TokenBuilderFactory;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdClient;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.security.openid.api.TokenResponse;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Every JWT the internal IdP mints must leave an inventory row recording its {@code jti}, because that row
 * is the only thing that makes the token revocable. Exercised through the real refresh flow rather than by
 * calling the private writer, so a refactor that silently stops recording is caught.
 */
@ExtendWith(MockitoExtension.class)
class TestOpenIdServiceInventory {

    private static final String CLIENT_ID = "stroom-client";
    private static final String CLIENT_SECRET = "shhh";
    private static final String SUBJECT = "jbloggs";
    private static final String SCOPE = "openid email";
    private static final String FAMILY_ID = "family-A";
    private static final long AUTH_TIME_SECONDS = 1_600_000_000L;

    @Mock
    private PublicJsonWebKeyProvider publicJsonWebKeyProvider;
    @Mock
    private OpenIdConfiguration openIdConfiguration;
    @Mock
    private OpenIdClientFactory openIdClientFactory;
    @Mock
    private OpenIdClient openIdClient;

    private RsaJsonWebKey jwk;
    private FakeOAuthTokenDao dao;
    private OpenIdService openIdService;

    @BeforeEach
    void setUp() throws JoseException {
        jwk = RsaJwkGenerator.generateJwk(2048);
        jwk.setKeyId("test-key");
        final List<PublicJsonWebKey> keys = List.of(jwk);
        lenient().when(publicJsonWebKeyProvider.list()).thenReturn(keys);
        lenient().when(publicJsonWebKeyProvider.getActiveKey()).thenReturn(jwk);
        lenient().when(openIdConfiguration.getIssuer()).thenReturn("https://stroom/oauth2/v1/noauth");

        lenient().when(openIdClient.getClientSecret()).thenReturn(CLIENT_SECRET);
        lenient().when(openIdClient.getName()).thenReturn("stroom");
        lenient().when(openIdClientFactory.getClient(CLIENT_ID)).thenReturn(openIdClient);

        dao = new FakeOAuthTokenDao();
        // A rotation that succeeds, carrying the state a real consumed row would have.
        dao.result = RefreshRotationResult.rotated(OAuthToken.newRefresh(
                "old-hash", SUBJECT, CLIENT_ID, FAMILY_ID, SCOPE,
                AUTH_TIME_SECONDS * 1000L, 0L, Long.MAX_VALUE));

        openIdService = new OpenIdService(
                null,
                new RefreshTokenStore(dao),
                new TokenBuilderFactory(
                        IdentityConfig::new, publicJsonWebKeyProvider, () -> openIdConfiguration),
                dao,
                null,
                openIdClientFactory,
                new IdentityConfig(),
                null);
    }

    @Test
    void refreshingRecordsAnInventoryRowForBothTheAccessAndIdToken() {
        openIdService.refreshIdToken(refreshFormParams());

        assertThat(dao.createdOfType(OAuthTokenType.ACCESS)).hasSize(1);
        assertThat(dao.createdOfType(OAuthTokenType.ID)).hasSize(1);
        // Two rows, not three: the successor refresh row is inserted inside rotateRefreshToken's own
        // transaction rather than through create(), so it does not show up here.
        assertThat(dao.created).hasSize(2);
    }

    @Test
    void theRecordedJtiIsTheOneInsideTheIssuedToken() throws Exception {
        // If these ever diverged, revoking the row would leave the real token working.
        final TokenResponse response = openIdService.refreshIdToken(refreshFormParams());

        final String recordedAccessJti = dao.createdOfType(OAuthTokenType.ACCESS).getFirst().jti();
        final String recordedIdJti = dao.createdOfType(OAuthTokenType.ID).getFirst().jti();

        assertThat(jtiOf(response.getAccessToken())).isEqualTo(recordedAccessJti);
        assertThat(jtiOf(response.getIdToken())).isEqualTo(recordedIdJti);
        assertThat(recordedAccessJti).isNotEqualTo(recordedIdJti);
    }

    @Test
    void inventoryRowsCarryTheGrantStateSoTheyCanBeGroupedAndRevoked() {
        openIdService.refreshIdToken(refreshFormParams());

        for (final OAuthToken recorded : dao.created) {
            assertThat(recorded.subjectId()).isEqualTo(SUBJECT);
            assertThat(recorded.clientId()).isEqualTo(CLIENT_ID);
            // Same family as the refresh lineage, so revoking a family kills the JWTs it produced too.
            assertThat(recorded.familyId()).isEqualTo(FAMILY_ID);
            assertThat(recorded.authTimeMs()).isEqualTo(AUTH_TIME_SECONDS * 1000L);
            assertThat(recorded.revoked()).isFalse();
        }
    }

    @Test
    void theRowOutlivesItsTokenSoARevokedTokenIsNeverBrieflyHonouredAgain() throws Exception {
        final TokenResponse response = openIdService.refreshIdToken(refreshFormParams());

        final OAuthToken recorded = dao.createdOfType(OAuthTokenType.ACCESS).getFirst();
        final long exp = expiryMsOf(response.getAccessToken());

        // exp is compared in whole seconds, so the token is accepted throughout the second it expires in.
        // The row must therefore outlast exp, or the denylist entry would lapse first.
        assertThat(recorded.expiresMs()).isEqualTo(exp + 1000L);
        assertThat(recorded.expiresMs()).isGreaterThan(exp);
    }

    @Test
    void noTokenIsMintedOrRecordedWhenTheRefreshTokenIsRejected() {
        dao.result = RefreshRotationResult.of(RefreshRotationResult.Outcome.REPLAYED);

        try {
            openIdService.refreshIdToken(refreshFormParams());
        } catch (final RuntimeException e) {
            // Expected - a rejected refresh token must not yield tokens.
        }

        assertThat(dao.created).isEmpty();
    }

    private MultivaluedMap<String, String> refreshFormParams() {
        final MultivaluedMap<String, String> formParams = new MultivaluedHashMap<>();
        formParams.putSingle(OpenId.CLIENT_ID, CLIENT_ID);
        formParams.putSingle(OpenId.CLIENT_SECRET, CLIENT_SECRET);
        formParams.putSingle(OpenId.REFRESH_TOKEN, "presented-refresh-token");
        return formParams;
    }

    private String jtiOf(final String jwt) throws Exception {
        return claimsOf(jwt).getJwtId();
    }

    private long expiryMsOf(final String jwt) throws Exception {
        return claimsOf(jwt).getExpirationTime().getValueInMillis();
    }

    private org.jose4j.jwt.JwtClaims claimsOf(final String jwt) throws Exception {
        return new JwtConsumerBuilder()
                .setVerificationKey(jwk.getKey())
                .setSkipDefaultAudienceValidation()
                .setSkipAllValidators()
                .build()
                .processToClaims(jwt);
    }
}
