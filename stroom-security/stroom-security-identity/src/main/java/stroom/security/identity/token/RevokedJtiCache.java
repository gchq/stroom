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

package stroom.security.identity.token;

import stroom.security.openid.api.RevokedTokenChecker;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Set;

/**
 * The revoked-{@code jti} denylist consulted by the verify path.
 * <p>
 * Holds <b>one</b> entry: the set of jtis that are revoked <em>and</em> not yet expired. That scoping is what
 * keeps it small and self-limiting - a revoked token drops out of the set once it expires, because after that
 * its signature no longer verifies anyway. It is therefore bounded by how many unexpired tokens an admin has
 * revoked, not by traffic or by how long the system has been running.
 * </p>
 * <p>
 * Membership is answered from memory; the database is read only when the entry is (re)loaded. The reload
 * interval is the ceiling on how long a revoked token can still be honoured by a node that missed the
 * revocation fan-out - the fan-out is the fast path, this is the backstop that makes a missed message
 * self-heal rather than persist.
 * </p>
 */
@Singleton
public class RevokedJtiCache implements RevokedTokenChecker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RevokedJtiCache.class);

    private static final String KEY = "revoked";

    /**
     * How long a loaded denylist is trusted before being re-read.
     * <p>
     * Deliberately much shorter than the access token lifetime (60 minutes by default), or a revoked token
     * could outlive the revocation on a node that missed the fan-out. Hardcoded rather than exposed as a
     * {@code CacheConfig} because this cache holds a single entry, which makes the usual {@code maximumSize}
     * knob meaningless and the config surface misleading. Mirrors {@link JwkCache}, which is hardcoded for
     * the same reason.
     * </p>
     * <p>
     * {@code expireAfterWrite}, not {@code refreshAfterWrite}: on expiry the next caller should wait for
     * fresh data rather than be served a stale denylist while a refresh happens in the background. For a
     * security check, a brief pause beats a stale allow.
     * </p>
     */
    private static final Duration RELOAD_INTERVAL = Duration.ofMinutes(1);

    private final LoadingCache<String, Set<String>> cache;

    @Inject
    RevokedJtiCache(final OAuthTokenDao oAuthTokenDao) {
        cache = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(RELOAD_INTERVAL)
                .build(k -> {
                    final Set<String> revoked = oAuthTokenDao.fetchRevokedJtis(System.currentTimeMillis());
                    LOGGER.debug(() -> "Loaded " + revoked.size() + " revoked jti(s) into the denylist");
                    return revoked;
                });
    }

    @Override
    public boolean isRevoked(final String jti) {
        if (jti == null) {
            // Nothing to look up. A token with no id cannot be individually revoked; it is still subject to
            // signature and expiry checks, which is what actually proves we minted it.
            return false;
        }
        final boolean revoked = cache.get(KEY).contains(jti);
        if (revoked) {
            LOGGER.debug(() -> "Token with jti '" + jti + "' has been revoked");
        }
        return revoked;
    }

    /**
     * Force the denylist to be re-read on next use.
     * <p>
     * Called when this node learns of a revocation - locally or via the cluster fan-out - so that a revoke
     * takes effect immediately rather than at the next natural reload.
     * </p>
     */
    public void invalidate() {
        LOGGER.debug("Invalidating the revoked jti denylist");
        cache.invalidateAll();
    }
}
