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

import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.RevokedTokenChecker;
import stroom.security.openid.api.UserTokenIssuer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// Issues access tokens for other services to act as a user, signed by the internal identity provider.
///
/// A token lives for [#LIFETIME] and is reused until it has less than [#MIN_REMAINING] left, so a service that is
/// called often doesn't cause a token to be signed and recorded for every call. A caller that needs a token to
/// last longer, e.g. for a request that may take many minutes, during which the service keeps using the token it
/// came with, says how long it must still have left, and gets a token that lasts that long (up to
/// [#MAX_LIFETIME]).
@Singleton
public class UserTokenIssuerImpl implements UserTokenIssuer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserTokenIssuerImpl.class);

    static final Duration LIFETIME = Duration.ofMinutes(5);
    static final Duration MIN_REMAINING = Duration.ofMinutes(1);
    // The most a token lives, however long a caller asks for, as it can act as the user.
    static final Duration MAX_LIFETIME = Duration.ofMinutes(30);
    // The scope recorded for these tokens, so they can be told apart in the token inventory.
    static final String SCOPE = "openid stroom-delegated";

    private final TokenBuilderFactory tokenBuilderFactory;
    private final OAuthTokenDao oAuthTokenDao;
    private final RevokedTokenChecker revokedTokenChecker;
    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final Map<Key, MintedToken> tokens = new ConcurrentHashMap<>();

    @Inject
    UserTokenIssuerImpl(final TokenBuilderFactory tokenBuilderFactory,
                        final OAuthTokenDao oAuthTokenDao,
                        final RevokedTokenChecker revokedTokenChecker,
                        final Provider<OpenIdConfiguration> openIdConfigurationProvider) {
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.oAuthTokenDao = oAuthTokenDao;
        this.revokedTokenChecker = revokedTokenChecker;
        this.openIdConfigurationProvider = openIdConfigurationProvider;
    }

    @Override
    public boolean isAvailable() {
        return IdpType.INTERNAL_IDP.equals(NullSafe.get(openIdConfigurationProvider.get(),
                OpenIdConfiguration::getIdentityProviderType));
    }

    @Override
    public Optional<String> getAccessToken(final String subjectId,
                                           final String audience,
                                           final String signInJwt,
                                           final Duration minRemaining) {
        if (!isAvailable() || NullSafe.isBlankString(subjectId) || NullSafe.isBlankString(audience)) {
            return Optional.empty();
        }
        final Duration lifetime = lifetimeFor(minRemaining);
        final Duration needed = neededFor(minRemaining, lifetime);
        final long nowMs = System.currentTimeMillis();
        final String familyId = familyOf(signInJwt, nowMs);
        final MintedToken token = tokens.compute(new Key(subjectId, audience, familyId), (key, existing) ->
                isReusable(existing, nowMs, needed)
                        ? existing
                        : issue(key, nowMs, lifetime));
        // Forget tokens that have run out, so the map doesn't grow with users who have gone.
        tokens.values().removeIf(other -> other.expiresMs() <= nowMs);
        return Optional.of(token.token());
    }

    /// @return The grant family of the token the user signed in with, or null if there is none, or it isn't one
    /// this identity provider issued.
    private String familyOf(final String signInJwt, final long nowMs) {
        if (NullSafe.isBlankString(signInJwt)) {
            return null;
        }
        try {
            // Only to find which of our tokens it is; it was checked when the user signed in.
            final String jti = new JwtConsumerBuilder()
                    .setSkipSignatureVerification()
                    .setSkipAllValidators()
                    .build()
                    .processToClaims(signInJwt)
                    .getJwtId();
            return NullSafe.isBlankString(jti)
                    ? null
                    : oAuthTokenDao.fetchByJti(jti, nowMs).map(OAuthToken::familyId).orElse(null);
        } catch (final InvalidJwtException | MalformedClaimException e) {
            LOGGER.debug(() -> "Unable to read the sign-in token: " + e.getMessage());
            return null;
        }
    }

    /// @param minRemaining How long a caller needs a token to still have to run, or null for no more than usual.
    /// @return How long to issue a token for: long enough to be reused for as long as a usual one (its
    /// [#LIFETIME] less [#MIN_REMAINING]) before it has less than that left, so a long one isn't issued for every
    /// request, between [#LIFETIME] and [#MAX_LIFETIME].
    static Duration lifetimeFor(final Duration minRemaining) {
        final Duration lifetime = needed(minRemaining).plus(LIFETIME.minus(MIN_REMAINING));
        if (lifetime.compareTo(LIFETIME) < 0) {
            return LIFETIME;
        }
        return lifetime.compareTo(MAX_LIFETIME) > 0
                ? MAX_LIFETIME
                : lifetime;
    }

    /// @return How long a token must still have to run to be reused: what the caller needs, but at least
    /// [#MIN_REMAINING], and no more than one issued now would have, less that.
    static Duration neededFor(final Duration minRemaining, final Duration lifetime) {
        final Duration needed = needed(minRemaining);
        final Duration most = lifetime.minus(MIN_REMAINING);
        return needed.compareTo(most) > 0
                ? most
                : needed;
    }

    private static Duration needed(final Duration minRemaining) {
        return minRemaining == null || minRemaining.compareTo(MIN_REMAINING) < 0
                ? MIN_REMAINING
                : minRemaining;
    }

    /// @return True if the token has long enough left, and hasn't been revoked (e.g. by the user signing out).
    private boolean isReusable(final MintedToken token, final long nowMs, final Duration needed) {
        return token != null
               && token.expiresMs() - nowMs > needed.toMillis()
               && !revokedTokenChecker.isRevoked(token.jti());
    }

    private MintedToken issue(final Key key, final long nowMs, final Duration lifetime) {
        final MintedToken token = tokenBuilderFactory.builder()
                .expirationTime(Instant.ofEpochMilli(nowMs).plus(lifetime))
                .clientId(key.audience())
                .subject(key.subjectId())
                .scope(SCOPE)
                .type(OpenId.TOKEN_TYPE__ACCESS)
                .build();
        // Recorded so it shows to administrators, and is revoked with the user's other tokens.
        oAuthTokenDao.create(OAuthToken.newJwt(
                OAuthTokenType.ACCESS,
                token.jti(),
                key.subjectId(),
                key.audience(),
                // The sign-in's grant, so the token is revoked with it.
                key.familyId(),
                SCOPE,
                null,
                nowMs,
                token.inventoryExpiresMs()));
        LOGGER.debug("Issued an access token for subject {} and audience {}", key.subjectId(), key.audience());
        return token;
    }

    private record Key(String subjectId, String audience, String familyId) {

    }
}
