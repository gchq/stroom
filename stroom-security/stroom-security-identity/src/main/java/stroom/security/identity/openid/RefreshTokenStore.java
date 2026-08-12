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
import stroom.security.identity.token.RefreshRotationResult;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and redeems opaque refresh tokens.
 * <p>
 * A refresh token is a random string with no internal structure; the state needed to mint the next set of
 * tokens is held in the <code>oauth_token</code> table, keyed by a SHA-256 hash of the token, so the raw
 * value is never stored.
 * </p>
 * <p>
 * Refresh tokens rotate: redeeming one consumes it and issues a successor in the same family, atomically.
 * Redeeming a token that has already been consumed is treated as a replay of a stolen token and revokes every
 * live token in that family, forcing re-authentication.
 * </p>
 * <p>
 * The state is deliberately durable rather than cached. A refresh token lives for weeks, so anything held
 * only in memory would expire or be evicted long before the token did - silently breaking valid tokens,
 * and losing the record of which tokens had been spent that replay detection depends on.
 * </p>
 */
@Singleton
class RefreshTokenStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RefreshTokenStore.class);

    private static final int TOKEN_BYTE_LENGTH = 32;
    /**
     * Recorded as {@code revoked_by} when a family is killed because a spent token was replayed, so an
     * admin looking at the audit trail can tell this apart from a revoke they performed.
     */
    private static final String REPLAY_REVOKED_BY = "refresh-token-replay-detection";

    private final OAuthTokenDao oAuthTokenDao;
    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    RefreshTokenStore(final OAuthTokenDao oAuthTokenDao) {
        this.oAuthTokenDao = oAuthTokenDao;
    }

    /**
     * Issue a new opaque refresh token for the given state and return the raw token to hand to the client.
     * Used at initial authentication; a refresh uses {@link #rotate(String, long)} instead so that the
     * predecessor is consumed in the same transaction.
     */
    String issue(final RefreshTokenRecord record) {
        final String token = newToken();
        oAuthTokenDao.create(OAuthToken.newRefresh(
                hash(token),
                record.subject(),
                record.clientId(),
                record.familyId(),
                record.scope(),
                // Stored in millis to match the rest of the schema; the OIDC claim is in seconds.
                record.authTimeEpochSecond() * 1000L,
                System.currentTimeMillis(),
                record.expiryTimeEpochMs()));
        return token;
    }

    /**
     * Redeem a refresh token, consuming it and issuing its successor in one transaction.
     *
     * @param presentedToken     The raw token the client presented.
     * @param successorExpiresMs When the successor should expire.
     * @return the state needed to mint the next set of tokens plus the successor refresh token, or empty if
     * the presented token was unknown, expired, revoked, already redeemed (in which case its whole family is
     * revoked), or lost a race with a concurrent redemption.
     */
    Optional<Rotation> rotate(final String presentedToken, final long successorExpiresMs) {
        if (presentedToken == null) {
            return Optional.empty();
        }
        final String successorToken = newToken();
        final RefreshRotationResult result = oAuthTokenDao.rotateRefreshToken(
                hash(presentedToken),
                hash(successorToken),
                successorExpiresMs,
                System.currentTimeMillis(),
                REPLAY_REVOKED_BY);

        // A switch expression, so that adding an Outcome without deciding what it means here is a compile
        // error rather than a silent fall-through to "rejected".
        return switch (result.outcome()) {
            case ROTATED -> {
                final OAuthToken consumed = result.consumed();
                yield Optional.of(new Rotation(
                        new RefreshTokenRecord(
                                consumed.clientId(),
                                consumed.subjectId(),
                                consumed.scope(),
                                consumed.authTimeMs() != null
                                        ? consumed.authTimeMs() / 1000L
                                        : 0L,
                                consumed.familyId(),
                                consumed.expiresMs()),
                        successorToken));
            }
            case REPLAYED -> {
                LOGGER.warn(() -> "An already-redeemed refresh token was presented again; revoked "
                                  + result.familyRevokedCount()
                                  + " token(s) in its family to force re-authentication");
                yield Optional.empty();
            }
            case REVOKED -> {
                LOGGER.debug("Rejecting a revoked refresh token");
                yield Optional.empty();
            }
            case EXPIRED -> {
                LOGGER.debug("Rejecting an expired refresh token");
                yield Optional.empty();
            }
            case LOST_RACE -> {
                LOGGER.debug("Rejecting a refresh token that lost a race with a concurrent redemption");
                yield Optional.empty();
            }
            case UNKNOWN -> {
                LOGGER.debug("Rejecting an unknown refresh token");
                yield Optional.empty();
            }
        };
    }

    private String newToken() {
        final byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(final String token) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (final NoSuchAlgorithmException e) {
            // SHA-256 is required to be present on every JVM.
            throw new IllegalStateException(e);
        }
    }


// --------------------------------------------------------------------------------


    /**
     * A successful rotation: the state carried forward from the consumed token, and the raw successor token
     * to hand back to the client.
     */
    record Rotation(RefreshTokenRecord record, String successorToken) {

    }
}
