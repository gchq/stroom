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

package stroom.security.identity.openid;

import stroom.security.identity.token.OAuthToken;
import stroom.security.identity.token.OAuthTokenDao;
import stroom.security.identity.token.OAuthTokenType;
import stroom.security.identity.token.RefreshRotationResult;
import stroom.security.identity.token.RefreshRotationResult.Outcome;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers what {@link RefreshTokenStore} itself is responsible for - token generation, hashing, and mapping
 * DAO outcomes onto a caller-facing result. The transactional guarantees (exactly-once redemption, durable
 * replay detection, family revocation) live in the DAO and are tested against a real database in
 * {@code TestOAuthTokenDaoImpl}.
 */
class TestRefreshTokenStore {

    private static final String CLIENT_ID = "stroom-client";
    private static final String SUBJECT = "jbloggs";
    private static final String SCOPE = "openid email";
    private static final long AUTH_TIME_SECONDS = 1_600_000_000L;

    private FakeOAuthTokenDao dao;
    private RefreshTokenStore store;

    @BeforeEach
    void setUp() {
        dao = new FakeOAuthTokenDao();
        store = new RefreshTokenStore(dao);
    }

    @Test
    void issuedTokenIsOpaqueNotAJwt() {
        final String token = store.issue(record("family-A"));

        // A JWT has two dots separating three Base64 segments; an opaque token has none.
        assertThat(token).doesNotContain(".");
        assertThat(token).isNotBlank();
    }

    @Test
    void issuedTokensAreDistinct() {
        final String first = store.issue(record("family-A"));
        final String second = store.issue(record("family-A"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theRawTokenIsNeverStoredOnlyItsHash() {
        final String token = store.issue(record("family-A"));

        final OAuthToken stored = dao.created.getFirst();
        assertThat(stored.tokenHash())
                .isNotEqualTo(token)
                .isEqualTo(RefreshTokenStore.hash(token));
        assertThat(stored.tokenType()).isEqualTo(OAuthTokenType.REFRESH);
        assertThat(stored.jti()).isNull();
    }

    @Test
    void issueCarriesTheGrantStateOntoTheRow() {
        store.issue(record("family-A"));

        final OAuthToken stored = dao.created.getFirst();
        assertThat(stored.subjectId()).isEqualTo(SUBJECT);
        assertThat(stored.clientId()).isEqualTo(CLIENT_ID);
        assertThat(stored.familyId()).isEqualTo("family-A");
        assertThat(stored.scope()).isEqualTo(SCOPE);
        // The OIDC auth_time claim is in seconds; the schema stores millis like every other time column.
        assertThat(stored.authTimeMs()).isEqualTo(AUTH_TIME_SECONDS * 1000L);
    }

    @Test
    void rotateHashesThePresentedTokenAndPassesANewSuccessorHash() {
        dao.result = RefreshRotationResult.rotated(consumedRow());

        final Optional<RefreshTokenStore.Rotation> rotation = store.rotate("presented-token", 12345L);

        assertThat(rotation).isPresent();
        assertThat(dao.presentedHash).isEqualTo(RefreshTokenStore.hash("presented-token"));
        assertThat(dao.successorExpiresMs).isEqualTo(12345L);
        // The successor handed back to the caller is the one whose hash was stored, and it is not the
        // token that was presented.
        final String successor = rotation.get().successorToken();
        assertThat(dao.successorHash).isEqualTo(RefreshTokenStore.hash(successor));
        assertThat(successor).isNotEqualTo("presented-token");
    }

    @Test
    void rotateReturnsTheStateNeededToMintTheSuccessorsJwts() {
        dao.result = RefreshRotationResult.rotated(consumedRow());

        final RefreshTokenRecord record = store.rotate("presented-token", 12345L).orElseThrow().record();

        assertThat(record.clientId()).isEqualTo(CLIENT_ID);
        assertThat(record.subject()).isEqualTo(SUBJECT);
        assertThat(record.scope()).isEqualTo(SCOPE);
        // Converted back to seconds, so a refreshed id token reports the ORIGINAL login time.
        assertThat(record.authTimeEpochSecond()).isEqualTo(AUTH_TIME_SECONDS);
        assertThat(record.familyId()).isEqualTo("family-A");
    }

    @ParameterizedTest
    @EnumSource(value = Outcome.class, names = {"UNKNOWN", "EXPIRED", "REVOKED", "REPLAYED", "LOST_RACE"})
    void everyNonRotatedOutcomeIsRejected(final Outcome outcome) {
        // Fail closed: anything short of a clean rotation must not yield a usable successor.
        dao.result = outcome == Outcome.REPLAYED
                ? RefreshRotationResult.replayed(2)
                : RefreshRotationResult.of(outcome);

        assertThat(store.rotate("presented-token", 12345L)).isEmpty();
    }

    @Test
    void nullTokenIsRejectedWithoutTouchingTheStore() {
        assertThat(store.rotate(null, 12345L)).isEmpty();

        assertThat(dao.presentedHash).isNull();
    }

    @Test
    void hashingIsStableAndNotTheIdentity() {
        assertThat(RefreshTokenStore.hash("abc")).isEqualTo(RefreshTokenStore.hash("abc"));
        assertThat(RefreshTokenStore.hash("abc")).isNotEqualTo(RefreshTokenStore.hash("abd"));
        assertThat(RefreshTokenStore.hash("abc")).isNotEqualTo("abc");
    }

    private RefreshTokenRecord record(final String familyId) {
        return new RefreshTokenRecord(
                CLIENT_ID,
                SUBJECT,
                SCOPE,
                AUTH_TIME_SECONDS,
                familyId,
                System.currentTimeMillis() + Duration.ofHours(1).toMillis());
    }

    private static OAuthToken consumedRow() {
        return OAuthToken.newRefresh(
                "old-hash", SUBJECT, CLIENT_ID, "family-A", SCOPE,
                AUTH_TIME_SECONDS * 1000L, 0L, Long.MAX_VALUE);
    }
}
