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

package stroom.security.impl;

import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jose4j.lang.UnresolvableKeyException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Reloads the signing key set when a token names a key this node has not seen.
 * <p>
 * Signing keys are cached, so for a short window after a rotation a node can hold a key set that does not yet
 * contain the {@code kid} a freshly signed token names, and would reject a perfectly valid token. Verifiers use
 * this to reload once and retry before writing such a token off.
 * </p>
 * <p>
 * Shared by every verifier so that the rate limit below - and the reload it guards - is common to all of them:
 * one reload fixes the key set for the whole process, so there is no reason for each verifier to pay for its own.
 * </p>
 */
@Singleton
class StaleKeySetRecovery {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StaleKeySetRecovery.class);

    /**
     * Minimum gap between reloads. Short enough that a real rotation heals promptly, long enough that unknown
     * key ids cannot be used to drive database load - see {@link #tryRefresh()}.
     */
    private static final long MIN_INTERVAL_MS = 10_000L;

    private final PublicJsonWebKeyProvider publicJsonWebKeyProvider;
    private final AtomicLong lastRefreshMs = new AtomicLong(0L);

    @Inject
    StaleKeySetRecovery(final PublicJsonWebKeyProvider publicJsonWebKeyProvider) {
        this.publicJsonWebKeyProvider = publicJsonWebKeyProvider;
    }

    /**
     * Did verification fail because no key matched the token's {@code kid}, rather than because the signature
     * was wrong? Only the former is worth reloading for - a bad signature against a known key means the token
     * simply is not ours.
     */
    boolean isUnresolvableKey(final Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof UnresolvableKeyException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Reload the key set, at most once per {@link #MIN_INTERVAL_MS}.
     * <p>
     * The rate limit is a security control rather than a performance tweak. A {@code kid} is an unauthenticated
     * header value, read before any signature is checked, so anyone who can reach an endpoint can name a key
     * that does not exist. Unlimited, a stream of tokens bearing random key ids would force a database read per
     * request. Limited, the worst case is one reload per interval however many bogus tokens arrive, while a
     * genuine rotation still heals within that interval.
     * </p>
     *
     * @return true if a reload happened, and a retry is therefore worthwhile.
     */
    boolean tryRefresh() {
        final long now = System.currentTimeMillis();
        final long last = lastRefreshMs.get();
        if (now - last < MIN_INTERVAL_MS) {
            LOGGER.debug("Not reloading signing keys - reloaded too recently");
            return false;
        }
        if (!lastRefreshMs.compareAndSet(last, now)) {
            // Another thread is already reloading, so there is nothing to gain from doing it again.
            return false;
        }
        LOGGER.info("Token names an unknown key id - reloading the signing key set in case of a rotation");
        publicJsonWebKeyProvider.refresh();
        return true;
    }
}
