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

package stroom.security.identity.dao;

import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docstore.mock.MockDocFinderModule;
import stroom.security.api.SecurityContext;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.db.IdentityDbModule;
import stroom.security.identity.token.OAuthToken;
import stroom.security.identity.token.OAuthTokenDao;
import stroom.security.identity.token.OAuthTokenType;
import stroom.security.identity.token.RefreshRotationResult;
import stroom.security.identity.token.RefreshRotationResult.Outcome;
import stroom.security.mock.MockSecurityContext;
import stroom.security.openid.api.TokenSummary;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestOAuthTokenDaoImpl {

    private static final long NOW = System.currentTimeMillis();
    private static final long HOUR = 3_600_000L;

    @Inject
    private OAuthTokenDao oAuthTokenDao;

    @BeforeEach
    void setUp() {
        Guice.createInjector(new TestModule()).injectMembers(this);
    }

    @Test
    void createThenFetchAJwtByJti() {
        final String jti = uid();
        final OAuthToken created = oAuthTokenDao.create(newAccessToken(jti, uid(), NOW + HOUR));

        assertThat(created.id()).isNotNull();

        final OAuthToken found = oAuthTokenDao.fetchByJti(jti, NOW).orElseThrow();
        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.tokenType()).isEqualTo(OAuthTokenType.ACCESS);
        assertThat(found.jti()).isEqualTo(jti);
        assertThat(found.tokenHash()).isNull();
        assertThat(found.revoked()).isFalse();
        assertThat(found.isUsableAt(NOW)).isTrue();
    }

    @Test
    void createThenFetchARefreshTokenByHash() {
        final String hash = uid();
        final OAuthToken created = oAuthTokenDao.create(newRefresh(hash, uid(), uid(), NOW + HOUR));

        final OAuthToken found = oAuthTokenDao.fetchByTokenHash(hash, NOW).orElseThrow();
        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.tokenType()).isEqualTo(OAuthTokenType.REFRESH);
        assertThat(found.jti()).isNull();
        assertThat(found.tokenHash()).isEqualTo(hash);
    }

    @Test
    void anExpiredTokenReadsAsAbsentEvenThoughTheRowIsStillThere() {
        // Correctness must not depend on the purge job having run, so every read filters on expiry.
        final String jti = uid();
        oAuthTokenDao.create(newAccessToken(jti, uid(), NOW - 1));

        assertThat(oAuthTokenDao.fetchByJti(jti, NOW)).isEmpty();
        // ...and it really is still present, i.e. the read filtered rather than the write failing.
        assertThat(oAuthTokenDao.deleteExpired(NOW)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void expiryIsExclusiveOfTheExpiryInstant() {
        final String jti = uid();
        oAuthTokenDao.create(newAccessToken(jti, uid(), NOW + HOUR));

        // expires_ms > now, so the token is dead at exactly its expiry time, not one ms later.
        assertThat(oAuthTokenDao.fetchByJti(jti, NOW + HOUR - 1)).isPresent();
        assertThat(oAuthTokenDao.fetchByJti(jti, NOW + HOUR)).isEmpty();
    }

    @Test
    void jtiIsUnique() {
        final String jti = uid();
        oAuthTokenDao.create(newAccessToken(jti, uid(), NOW + HOUR));

        assertThatThrownBy(() -> oAuthTokenDao.create(newAccessToken(jti, uid(), NOW + HOUR)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void tokenHashIsUnique() {
        final String hash = uid();
        oAuthTokenDao.create(newRefresh(hash, uid(), uid(), NOW + HOUR));

        assertThatThrownBy(() -> oAuthTokenDao.create(newRefresh(hash, uid(), uid(), NOW + HOUR)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void revokingBySubjectHitsEveryLiveTokenForThatSubjectAndNoOthers() {
        final String subject = uid();
        final String otherSubject = uid();
        final String jtiA = uid();
        final String jtiB = uid();
        final String jtiOther = uid();
        oAuthTokenDao.create(newAccessToken(jtiA, subject, NOW + HOUR));
        oAuthTokenDao.create(newAccessToken(jtiB, subject, NOW + HOUR));
        oAuthTokenDao.create(newAccessToken(jtiOther, otherSubject, NOW + HOUR));

        assertThat(oAuthTokenDao.revokeBySubjectId(subject, "admin", NOW)).isEqualTo(2);

        assertThat(oAuthTokenDao.fetchByJti(jtiA, NOW).orElseThrow().revoked()).isTrue();
        assertThat(oAuthTokenDao.fetchByJti(jtiB, NOW).orElseThrow().revoked()).isTrue();
        assertThat(oAuthTokenDao.fetchByJti(jtiOther, NOW).orElseThrow().revoked()).isFalse();

        final OAuthToken revoked = oAuthTokenDao.fetchByJti(jtiA, NOW).orElseThrow();
        assertThat(revoked.revokedBy()).isEqualTo("admin");
        assertThat(revoked.revokedMs()).isEqualTo(NOW);
        assertThat(revoked.isUsableAt(NOW)).isFalse();
    }

    @Test
    void revokingIsIdempotentAndPreservesTheOriginalProvenance() {
        final String subject = uid();
        final String jti = uid();
        oAuthTokenDao.create(newAccessToken(jti, subject, NOW + HOUR));

        assertThat(oAuthTokenDao.revokeBySubjectId(subject, "first", NOW)).isEqualTo(1);
        // Already revoked, so no rows and - importantly - the original who/when is not overwritten.
        assertThat(oAuthTokenDao.revokeBySubjectId(subject, "second", NOW + 1)).isZero();

        final OAuthToken found = oAuthTokenDao.fetchByJti(jti, NOW).orElseThrow();
        assertThat(found.revokedBy()).isEqualTo("first");
        assertThat(found.revokedMs()).isEqualTo(NOW);
    }

    @Test
    void revokingAFamilyKillsTheWholeRotationLineage() {
        final String family = uid();
        final String subject = uid();
        final String hashOld = uid();
        final String hashNew = uid();
        oAuthTokenDao.create(newRefresh(hashOld, subject, family, NOW + HOUR));
        oAuthTokenDao.create(newRefresh(hashNew, subject, family, NOW + HOUR));
        // A token in a different lineage for the same subject must be untouched.
        final String hashUnrelated = uid();
        oAuthTokenDao.create(newRefresh(hashUnrelated, subject, uid(), NOW + HOUR));

        assertThat(oAuthTokenDao.revokeByFamilyId(family, "admin", NOW)).isEqualTo(2);

        assertThat(oAuthTokenDao.fetchByTokenHash(hashOld, NOW).orElseThrow().revoked()).isTrue();
        assertThat(oAuthTokenDao.fetchByTokenHash(hashNew, NOW).orElseThrow().revoked()).isTrue();
        assertThat(oAuthTokenDao.fetchByTokenHash(hashUnrelated, NOW).orElseThrow().revoked()).isFalse();
    }

    @Test
    void revokingBySingleJti() {
        final String jti = uid();
        oAuthTokenDao.create(newAccessToken(jti, uid(), NOW + HOUR));

        assertThat(oAuthTokenDao.revokeByJti(jti, "admin", NOW)).isTrue();
        assertThat(oAuthTokenDao.revokeByJti(jti, "admin", NOW)).isFalse();
        assertThat(oAuthTokenDao.revokeByJti(uid(), "admin", NOW)).isFalse();
    }

    @Test
    void revokedJtisAreListedForTheVerifyPathCacheButOnlyWhileStillLive() {
        final String liveJti = uid();
        final String expiredJti = uid();
        oAuthTokenDao.create(newAccessToken(liveJti, uid(), NOW + HOUR));
        oAuthTokenDao.create(newAccessToken(expiredJti, uid(), NOW + HOUR));
        oAuthTokenDao.revokeByJti(liveJti, "admin", NOW);
        oAuthTokenDao.revokeByJti(expiredJti, "admin", NOW);

        assertThat(oAuthTokenDao.fetchRevokedJtis(NOW)).contains(liveJti, expiredJti);
        // The denylist is self-limiting: once a revoked token expires it drops out, so the set the verify
        // path holds stays bounded rather than growing forever.
        assertThat(oAuthTokenDao.fetchRevokedJtis(NOW + HOUR)).doesNotContain(liveJti, expiredJti);
    }

    @Test
    void refreshTokensNeverAppearInTheRevokedJtiSet() {
        // They have no jti at all, so a null must not leak into the denylist.
        final String hash = uid();
        final String family = uid();
        oAuthTokenDao.create(newRefresh(hash, uid(), family, NOW + HOUR));
        oAuthTokenDao.revokeByFamilyId(family, "admin", NOW);

        assertThat(oAuthTokenDao.fetchRevokedJtis(NOW)).doesNotContainNull();
    }

    @Test
    void findBySubjectIdReturnsLiveTokensIncludingRevokedOnes() {
        final String subject = uid();
        oAuthTokenDao.create(newAccessToken(uid(), subject, NOW + HOUR));
        oAuthTokenDao.create(newAccessToken(uid(), subject, NOW - 1));
        final String revokedJti = uid();
        oAuthTokenDao.create(newAccessToken(revokedJti, subject, NOW + HOUR));
        oAuthTokenDao.revokeByJti(revokedJti, "admin", NOW);

        // The expired one is excluded; the revoked one is kept so an admin can see the revoke took effect.
        assertThat(oAuthTokenDao.findBySubjectId(subject, NOW)).hasSize(2);
    }

    @Test
    void summariseUsableBySubjectExcludesRevokedAndExpired() {
        final String subject = uid();
        oAuthTokenDao.create(newAccessToken(uid(), subject, NOW + HOUR));
        oAuthTokenDao.create(newAccessToken(uid(), subject, NOW - 1));
        final String revokedJti = uid();
        oAuthTokenDao.create(newAccessToken(revokedJti, subject, NOW + HOUR));
        oAuthTokenDao.revokeByJti(revokedJti, "admin", NOW);

        final TokenSummary summary = oAuthTokenDao.summariseUsableBySubject(NOW).get(subject);
        assertThat(summary).isNotNull();
        assertThat(summary.tokenCount()).isEqualTo(1);
        // The one usable token, so both bounds are its expiry.
        assertThat(summary.nextExpiryMs()).isEqualTo(NOW + HOUR);
        assertThat(summary.latestExpiryMs()).isEqualTo(NOW + HOUR);
    }

    @Test
    void deleteExpiredRemovesOnlyExpiredRows() {
        final String liveJti = uid();
        oAuthTokenDao.create(newAccessToken(liveJti, uid(), NOW + HOUR));
        oAuthTokenDao.create(newAccessToken(uid(), uid(), NOW - 1));

        assertThat(oAuthTokenDao.deleteExpired(NOW)).isGreaterThanOrEqualTo(1);
        assertThat(oAuthTokenDao.fetchByJti(liveJti, NOW)).isPresent();
    }

    @Test
    void jwtWithoutAJtiIsRejectedBeforeReachingTheDb() {
        // The unique natural key per type is what redemption and revocation look up, so a row missing it
        // would be unreachable rather than merely odd.
        assertThatThrownBy(() -> OAuthToken.newJwt(
                OAuthTokenType.ACCESS, null, "subject", "client", "family", "openid", NOW, NOW, NOW + HOUR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshTokenCannotBeCreatedViaTheJwtFactory() {
        assertThatThrownBy(() -> OAuthToken.newJwt(
                OAuthTokenType.REFRESH, "jti", "subject", "client", "family", "openid", NOW, NOW, NOW + HOUR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Refresh rotation ----------------------------------------------------------------------------

    @Test
    void rotatingConsumesThePresentedTokenAndIssuesItsSuccessor() {
        final String oldHash = uid();
        final String newHash = uid();
        final String subject = uid();
        final String family = uid();
        oAuthTokenDao.create(newRefresh(oldHash, subject, family, NOW + HOUR));

        final RefreshRotationResult result = oAuthTokenDao.rotateRefreshToken(
                oldHash, newHash, NOW + 2 * HOUR, NOW, "replay");

        assertThat(result.isRotated()).isTrue();
        // The consumed record carries forward everything needed to mint the successor's JWTs.
        assertThat(result.consumed().subjectId()).isEqualTo(subject);
        assertThat(result.consumed().familyId()).isEqualTo(family);
        assertThat(result.consumed().scope()).isEqualTo("openid email");
        assertThat(result.consumed().authTimeMs()).isEqualTo(NOW);

        // The successor exists, in the same family, with the new expiry - written in the same transaction.
        final OAuthToken successor = oAuthTokenDao.fetchByTokenHash(newHash, NOW).orElseThrow();
        assertThat(successor.familyId()).isEqualTo(family);
        assertThat(successor.subjectId()).isEqualTo(subject);
        assertThat(successor.scope()).isEqualTo("openid email");
        assertThat(successor.authTimeMs()).isEqualTo(NOW);
        assertThat(successor.expiresMs()).isEqualTo(NOW + 2 * HOUR);
        assertThat(successor.isConsumed()).isFalse();

        // The predecessor is kept, marked consumed, so a later replay is still detectable.
        final OAuthToken consumed = oAuthTokenDao.fetchByTokenHash(oldHash, NOW).orElseThrow();
        assertThat(consumed.isConsumed()).isTrue();
        assertThat(consumed.consumedMs()).isEqualTo(NOW);
        assertThat(consumed.isUsableAt(NOW)).isFalse();
    }

    @Test
    void replayingASpentTokenRevokesItsWholeFamily() {
        final String t1 = uid();
        final String t2 = uid();
        final String t3 = uid();
        final String family = uid();
        oAuthTokenDao.create(newRefresh(t1, uid(), family, NOW + HOUR));

        // t1 -> t2 legitimately.
        assertThat(oAuthTokenDao.rotateRefreshToken(t1, t2, NOW + HOUR, NOW, "replay").isRotated()).isTrue();

        // A stolen copy of the already-spent t1 is presented again.
        final RefreshRotationResult replay = oAuthTokenDao.rotateRefreshToken(
                t1, t3, NOW + HOUR, NOW, "replay");
        assertThat(replay.outcome()).isEqualTo(Outcome.REPLAYED);
        // Both t1 and the live successor t2 are revoked - the thief may already hold t2.
        assertThat(replay.familyRevokedCount()).isEqualTo(2);
        assertThat(oAuthTokenDao.fetchByTokenHash(t2, NOW).orElseThrow().revoked()).isTrue();
        // No successor was issued for the replay attempt.
        assertThat(oAuthTokenDao.fetchByTokenHash(t3, NOW)).isEmpty();

        // ...and the revoked successor can no longer be rotated either.
        assertThat(oAuthTokenDao.rotateRefreshToken(t2, uid(), NOW + HOUR, NOW, "replay").outcome())
                .isEqualTo(Outcome.REVOKED);
    }

    @Test
    void revokingASubjectLeavesSpentRefreshTokensAloneSoReplayIsStillDetected() {
        // Revoking a spent token would gain nothing - it cannot be redeemed again - but would cost the ability
        // to recognise a replay of it, because the revoked check runs before the consumed check. Presenting a
        // spent token is evidence of theft, so that signal has to survive an admin revoke.
        final String subject = uid();
        final String spent = uid();
        final String family = uid();
        oAuthTokenDao.create(newRefresh(spent, subject, family, NOW + HOUR));
        oAuthTokenDao.rotateRefreshToken(spent, uid(), NOW + HOUR, NOW, "replay");

        // The successor is live and is the only thing worth revoking, so the count is 1 and not 2.
        assertThat(oAuthTokenDao.revokeBySubjectId(subject, "admin", NOW)).isEqualTo(1);
        assertThat(oAuthTokenDao.fetchByTokenHash(spent, NOW).orElseThrow().revoked()).isFalse();

        // ...and replaying the spent token is still reported as a replay rather than as merely revoked.
        assertThat(oAuthTokenDao.rotateRefreshToken(spent, uid(), NOW + HOUR, NOW, "replay").outcome())
                .isEqualTo(Outcome.REPLAYED);
    }

    @Test
    void theRevokedCountMatchesWhatTheAdminWasShown() {
        // The count returned must agree with summariseUsableBySubject, or an admin is told a different number
        // of tokens went away than the screen said they held.
        final String subject = uid();
        oAuthTokenDao.create(newAccessToken(uid(), subject, NOW + HOUR));
        oAuthTokenDao.create(newRefresh(uid(), subject, uid(), NOW + HOUR));
        final String spent = uid();
        oAuthTokenDao.create(newRefresh(spent, subject, uid(), NOW + HOUR));
        oAuthTokenDao.rotateRefreshToken(spent, uid(), NOW + HOUR, NOW, "replay");

        final int shown = oAuthTokenDao.summariseUsableBySubject(NOW).get(subject).tokenCount();

        assertThat(oAuthTokenDao.revokeBySubjectId(subject, "admin", NOW)).isEqualTo(shown);
    }

    @Test
    void revokingAGrantByOneOfItsTokensRetiresTheWholeGrant() {
        // What happens when the session holding a grant ends: the access token names the grant, and all three
        // tokens minted together go with it.
        final String subject = uid();
        final String family = uid();
        final String accessJti = uid();
        final String idJti = uid();
        final String refreshHash = uid();
        oAuthTokenDao.create(newJwtInFamily(accessJti, subject, family));
        oAuthTokenDao.create(newJwtInFamily(idJti, subject, family));
        oAuthTokenDao.create(newRefresh(refreshHash, subject, family, NOW + HOUR));
        // A second, unrelated login by the same user must be left alone.
        final String otherJti = uid();
        oAuthTokenDao.create(newJwtInFamily(otherJti, subject, uid()));

        assertThat(oAuthTokenDao.revokeGrantByJti(accessJti, "session-ended", NOW)).isEqualTo(3);

        assertThat(oAuthTokenDao.fetchByJti(idJti, NOW).orElseThrow().revoked()).isTrue();
        assertThat(oAuthTokenDao.fetchByTokenHash(refreshHash, NOW).orElseThrow().revoked()).isTrue();
        assertThat(oAuthTokenDao.fetchByJti(otherJti, NOW).orElseThrow().revoked()).isFalse();
        // ...and the subject's live token count drops to just the other login's.
        assertThat(oAuthTokenDao.summariseUsableBySubject(NOW).get(subject).tokenCount()).isEqualTo(1);
    }

    @Test
    void grantIsStillRevocableAfterItsAccessTokenHasExpired() {
        // The access token naming the grant is the first thing to expire, while the refresh token that actually
        // needs retiring outlives it by weeks - so the lookup must not filter on expiry.
        final String family = uid();
        final String subject = uid();
        final String expiredJti = uid();
        final String refreshHash = uid();
        oAuthTokenDao.create(OAuthToken.newJwt(
                OAuthTokenType.ACCESS, expiredJti, subject, "client1", family, "openid", NOW, NOW, NOW - 1));
        oAuthTokenDao.create(newRefresh(refreshHash, subject, family, NOW + 30 * 24 * HOUR));

        assertThat(oAuthTokenDao.revokeGrantByJti(expiredJti, "session-ended", NOW)).isEqualTo(1);
        assertThat(oAuthTokenDao.fetchByTokenHash(refreshHash, NOW).orElseThrow().revoked()).isTrue();
    }

    @Test
    void revokingAGrantByAnUnknownTokenDoesNothing() {
        // The normal external-IdP case: the session held a token this table never issued.
        assertThat(oAuthTokenDao.revokeGrantByJti(uid(), "session-ended", NOW)).isZero();
        assertThat(oAuthTokenDao.revokeGrantByJti(null, "session-ended", NOW)).isZero();
    }

    @Test
    void replayOnlyRevokesItsOwnFamily() {
        final String familyA = uid();
        final String familyB = uid();
        final String a1 = uid();
        final String b1 = uid();
        oAuthTokenDao.create(newRefresh(a1, uid(), familyA, NOW + HOUR));
        oAuthTokenDao.create(newRefresh(b1, uid(), familyB, NOW + HOUR));

        oAuthTokenDao.rotateRefreshToken(a1, uid(), NOW + HOUR, NOW, "replay");
        oAuthTokenDao.rotateRefreshToken(a1, uid(), NOW + HOUR, NOW, "replay");

        assertThat(oAuthTokenDao.fetchByTokenHash(b1, NOW).orElseThrow().revoked()).isFalse();
    }

    @Test
    void replayIsDetectedForTheTokensFullLifetimeNotJustWhileCached() {
        // Replay detection must not depend on anything in memory: it has to hold for the token's whole
        // lifetime and across a restart. The consumed row is durable and the DAO holds no state, so
        // reading it back here is exactly what a freshly started process would see.
        final String spent = uid();
        final String family = uid();
        oAuthTokenDao.create(newRefresh(spent, uid(), family, NOW + 30 * 24 * HOUR));
        oAuthTokenDao.rotateRefreshToken(spent, uid(), NOW + 30 * 24 * HOUR, NOW, "replay");

        // 29 days later, well beyond any cache window, the replay is still recognised as a replay rather
        // than being waved through or merely reported as unknown.
        final long muchLater = NOW + 29 * 24 * HOUR;
        assertThat(oAuthTokenDao.rotateRefreshToken(spent, uid(), muchLater + HOUR, muchLater, "replay")
                .outcome())
                .isEqualTo(Outcome.REPLAYED);
    }

    @Test
    void rotatingAnExpiredRevokedOrUnknownTokenIsRejectedAndIssuesNoSuccessor() {
        final String expired = uid();
        oAuthTokenDao.create(newRefresh(expired, uid(), uid(), NOW - 1));
        assertThat(oAuthTokenDao.rotateRefreshToken(expired, uid(), NOW + HOUR, NOW, "replay").outcome())
                .isEqualTo(Outcome.EXPIRED);

        final String revoked = uid();
        final String revokedFamily = uid();
        oAuthTokenDao.create(newRefresh(revoked, uid(), revokedFamily, NOW + HOUR));
        oAuthTokenDao.revokeByFamilyId(revokedFamily, "admin", NOW);
        assertThat(oAuthTokenDao.rotateRefreshToken(revoked, uid(), NOW + HOUR, NOW, "replay").outcome())
                .isEqualTo(Outcome.REVOKED);

        assertThat(oAuthTokenDao.rotateRefreshToken(uid(), uid(), NOW + HOUR, NOW, "replay").outcome())
                .isEqualTo(Outcome.UNKNOWN);
        assertThat(oAuthTokenDao.rotateRefreshToken(null, uid(), NOW + HOUR, NOW, "replay").outcome())
                .isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    void concurrentRotationOfOneTokenYieldsExactlyOneSuccessor()
            throws InterruptedException, ExecutionException {
        // The security-critical property: two clients redeeming the same token must not both get a working
        // successor. Enforced by the conditional update on consumed_ms, so it holds without relying on the
        // row lock alone.
        final String presented = uid();
        oAuthTokenDao.create(newRefresh(presented, uid(), uid(), NOW + HOUR));

        final int threads = 8;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            final CountDownLatch startLine = new CountDownLatch(1);
            final List<String> successorHashes = new ArrayList<>();
            final List<Future<RefreshRotationResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final String successorHash = uid();
                successorHashes.add(successorHash);
                futures.add(pool.submit(() -> {
                    startLine.await();
                    return oAuthTokenDao.rotateRefreshToken(
                            presented, successorHash, NOW + HOUR, NOW, "replay");
                }));
            }
            startLine.countDown();

            int rotated = 0;
            for (final Future<RefreshRotationResult> future : futures) {
                if (future.get().isRotated()) {
                    rotated++;
                }
            }
            assertThat(rotated)
                    .as("a single-use refresh token must rotate exactly once under contention")
                    .isEqualTo(1);

            // Exactly one successor row exists, so the losers really did not write one.
            final long created = successorHashes.stream()
                    .filter(hash -> oAuthTokenDao.fetchByTokenHash(hash, NOW).isPresent())
                    .count();
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private static OAuthToken newAccessToken(final String jti, final String subjectId, final long expiresMs) {
        return OAuthToken.newJwt(
                OAuthTokenType.ACCESS, jti, subjectId, "client1", uid(), "openid email", NOW, NOW, expiresMs);
    }

    private static OAuthToken newJwtInFamily(final String jti, final String subjectId, final String familyId) {
        return OAuthToken.newJwt(
                OAuthTokenType.ACCESS, jti, subjectId, "client1", familyId, "openid email", NOW, NOW,
                NOW + HOUR);
    }

    private static OAuthToken newRefresh(final String tokenHash,
                                         final String subjectId,
                                         final String familyId,
                                         final long expiresMs) {
        return OAuthToken.newRefresh(
                tokenHash, subjectId, "client1", familyId, "openid email", NOW, NOW, expiresMs);
    }

    private static String uid() {
        return UUID.randomUUID().toString();
    }


// --------------------------------------------------------------------------------


    private static class TestModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new IdentityDbModule());
            install(new DbTestModule());
            // Only the oauth_token DAO is under test, so bind it directly rather than installing
            // IdentityDaoModule, which would drag in the account, JWK and OpenID client DAOs too.
            install(new MockCollectionModule());
            install(new MockWordListProviderModule());
            install(new MockDocFinderModule());
            bind(SecurityContext.class).to(MockSecurityContext.class);
            bind(OAuthTokenDao.class).to(OAuthTokenDaoImpl.class);
            bind(IdentityConfig.class).toInstance(new IdentityConfig());
        }
    }
}
