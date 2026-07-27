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

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.security.identity.config.OpenIdConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Issues and redeems opaque refresh tokens. A refresh token is a random string with no internal structure;
 * the state needed to mint the next set of tokens lives in a {@link RefreshTokenRecord} held here and keyed
 * by a hash of the token, so the raw token value is never stored.
 * <p>
 * Refresh tokens rotate: redeeming one consumes it and the caller issues a successor in the same family.
 * Redeeming a token that has already been consumed is treated as a replay of a stolen token and revokes
 * every live token in that family, forcing re-authentication.
 * </p>
 */
@Singleton
class RefreshTokenCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RefreshTokenCache.class);

    private static final String ACTIVE_CACHE_NAME = "Refresh Token Cache";
    private static final String CONSUMED_CACHE_NAME = "Consumed Refresh Token Cache";
    private static final int TOKEN_BYTE_LENGTH = 32;

    // Maps a token hash to the state behind a currently redeemable refresh token.
    private final StroomCache<String, RefreshTokenRecord> activeCache;
    // Remembers the hashes of tokens that have been redeemed, mapped to their family, so a replay can be
    // detected for as long as an entry survives.
    private final StroomCache<String, String> consumedCache;
    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    RefreshTokenCache(final CacheManager cacheManager,
                      final Provider<OpenIdConfig> openIdConfigProvider) {
        activeCache = cacheManager.create(
                ACTIVE_CACHE_NAME,
                () -> openIdConfigProvider.get().getRefreshTokenCache());
        consumedCache = cacheManager.create(
                CONSUMED_CACHE_NAME,
                () -> openIdConfigProvider.get().getRefreshTokenCache());
    }

    /**
     * Issue a new opaque refresh token for the given state and return the raw token to hand to the client.
     */
    String issue(final RefreshTokenRecord record) {
        final byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        activeCache.put(hash(token), record);
        return token;
    }

    /**
     * Redeem a refresh token, consuming it so it cannot be redeemed again. Returns the state to mint the
     * next set of tokens, or empty if the token is unknown, expired, or a replay of an already-redeemed
     * token (in which case its whole family is revoked).
     */
    Optional<RefreshTokenRecord> consume(final String presentedToken) {
        if (presentedToken == null) {
            return Optional.empty();
        }
        final String hash = hash(presentedToken);

        // Atomically take and remove the active record so two concurrent redemptions of the same token
        // cannot both succeed and issue rival successors.
        final AtomicReference<RefreshTokenRecord> taken = new AtomicReference<>();
        activeCache.compute(hash, (h, existing) -> {
            taken.set(existing);
            return null;
        });
        final RefreshTokenRecord record = taken.get();
        if (record != null) {
            if (record.isExpired(System.currentTimeMillis())) {
                LOGGER.debug("Rejecting an expired refresh token");
                return Optional.empty();
            }
            consumedCache.put(hash, record.familyId());
            return Optional.of(record);
        }

        final String familyId = consumedCache.getIfPresent(hash).orElse(null);
        if (familyId != null) {
            // The token was valid once but has already been redeemed, so this is a replay. Revoke every
            // live token descended from the same login.
            LOGGER.warn(() -> "An already-redeemed refresh token was presented again; revoking its token "
                              + "family to force re-authentication");
            activeCache.invalidateEntries((h, rec) -> Objects.equals(rec.familyId(), familyId));
            return Optional.empty();
        }

        LOGGER.debug("Rejecting an unknown or expired refresh token");
        return Optional.empty();
    }

    private static String hash(final String token) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (final NoSuchAlgorithmException e) {
            // SHA-256 is required to be present on every JVM.
            throw new IllegalStateException(e);
        }
    }
}
