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

package stroom.security.identity.openid;

import stroom.cache.impl.CacheManagerImpl;
import stroom.security.identity.config.OpenIdConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class TestRefreshTokenCache {

    private static final String CLIENT_ID = "stroom-client";
    private static final String SUBJECT = "jbloggs";
    private static final String SCOPE = "openid email";

    private CacheManagerImpl cacheManager;
    private RefreshTokenCache refreshTokenCache;

    @BeforeEach
    void setUp() {
        cacheManager = new CacheManagerImpl();
        refreshTokenCache = new RefreshTokenCache(cacheManager, OpenIdConfig::new);
    }

    @AfterEach
    void tearDown() {
        cacheManager.close();
    }

    @Test
    void issuedTokenIsOpaqueNotAJwt() {
        final String token = refreshTokenCache.issue(record("family-A"));
        // A JWT has two dots separating three Base64 segments; an opaque token has none.
        assertThat(token).doesNotContain(".");
        assertThat(token).isNotBlank();
    }

    @Test
    void tokenCanBeRedeemedExactlyOnce() {
        final String token = refreshTokenCache.issue(record("family-A"));

        final Optional<RefreshTokenRecord> first = refreshTokenCache.consume(token);
        assertThat(first).isPresent();
        assertThat(first.get().subject()).isEqualTo(SUBJECT);
        assertThat(first.get().scope()).isEqualTo(SCOPE);

        // Second redemption of the same token fails - it is single use.
        assertThat(refreshTokenCache.consume(token)).isEmpty();
    }

    @Test
    void replayingARedeemedTokenRevokesTheWholeFamily() {
        // t1 is redeemed and rotated to t2 (its successor in the same family).
        final String t1 = refreshTokenCache.issue(record("family-A"));
        assertThat(refreshTokenCache.consume(t1)).isPresent();
        final String t2 = refreshTokenCache.issue(record("family-A"));

        // A stolen copy of the already-redeemed t1 is replayed.
        assertThat(refreshTokenCache.consume(t1))
                .as("a replayed, already-redeemed token is rejected")
                .isEmpty();

        // ...which must also kill the still-live successor t2, forcing re-authentication.
        assertThat(refreshTokenCache.consume(t2))
                .as("the live successor is revoked when a replay is detected")
                .isEmpty();
    }

    @Test
    void replayOnlyRevokesItsOwnFamily() {
        final String familyA = refreshTokenCache.issue(record("family-A"));
        final String familyB = refreshTokenCache.issue(record("family-B"));

        refreshTokenCache.consume(familyA);   // redeem A once
        assertThat(refreshTokenCache.consume(familyA)).isEmpty();   // replay A -> revoke family A

        // family B is untouched.
        assertThat(refreshTokenCache.consume(familyB)).isPresent();
    }

    @Test
    void anExpiredTokenIsRejected() {
        final RefreshTokenRecord expired = new RefreshTokenRecord(
                CLIENT_ID, SUBJECT, SCOPE, 0L, "family-A",
                System.currentTimeMillis() - Duration.ofSeconds(1).toMillis());
        final String token = refreshTokenCache.issue(expired);

        assertThat(refreshTokenCache.consume(token)).isEmpty();
    }

    @Test
    void unknownAndNullTokensAreRejected() {
        assertThat(refreshTokenCache.consume("not-a-real-token")).isEmpty();
        assertThat(refreshTokenCache.consume(null)).isEmpty();
    }

    @Test
    void concurrentRedemptionYieldsExactlyOneSuccess() throws InterruptedException, ExecutionException {
        final String token = refreshTokenCache.issue(record("family-A"));
        final int threads = 16;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            final CountDownLatch startLine = new CountDownLatch(1);
            final List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startLine.await();
                    return refreshTokenCache.consume(token).isPresent();
                }));
            }
            // Release all threads at once to maximise contention on the single token.
            startLine.countDown();

            long successes = 0;
            for (final Future<Boolean> future : futures) {
                if (future.get()) {
                    successes++;
                }
            }
            assertThat(successes)
                    .as("a single-use token must be redeemable exactly once even under contention")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private RefreshTokenRecord record(final String familyId) {
        return new RefreshTokenRecord(
                CLIENT_ID,
                SUBJECT,
                SCOPE,
                0L,
                familyId,
                System.currentTimeMillis() + Duration.ofHours(1).toMillis());
    }
}
