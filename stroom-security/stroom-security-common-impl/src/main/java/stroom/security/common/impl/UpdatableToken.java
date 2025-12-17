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

package stroom.security.common.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.TokenResponse;
import stroom.util.authentication.Refreshable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionUtil;
import stroom.util.shared.NullSafe;

import jakarta.servlet.http.HttpSession;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public class UpdatableToken implements Refreshable, HasJwtClaims, HasJwt {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UpdatableToken.class);

    private static final long MIN_EXPIRY_BUFFER = Duration.ofSeconds(10)
            .toMillis();
    // The fraction of the remaining expiry time to treat as the threshold when we update the token.
    // This is so we update before it expires.
    private static final double EXPIRY_BUFFER_FRACTION = 0.1;

    private final Function<UpdatableToken, FetchTokenResult> updateFunction;
    private final String uuid;

    private UserIdentity userIdentity = null;
    private BooleanSupplier additionalRefreshCondition = null;
    private final HttpSession session;
    // The time we need to refresh the tokens which will be a bit BEFORE the token expiry time
    private volatile MutableState mutableState;

    public UpdatableToken(final TokenResponse tokenResponse,
                          final JwtClaims jwtClaims,
                          final Function<UpdatableToken, FetchTokenResult> updateFunction) {
        this(tokenResponse, jwtClaims, updateFunction, null);
    }

    /**
     * @param tokenResponse
     * @param jwtClaims
     * @param updateFunction The function to get new up-to-date tokens.
     */
    public UpdatableToken(final TokenResponse tokenResponse,
                          final JwtClaims jwtClaims,
                          final Function<UpdatableToken, FetchTokenResult> updateFunction,
                          final HttpSession session) {
        this.updateFunction = updateFunction;
        this.session = session;
        this.mutableState = createMutableState(tokenResponse, jwtClaims);
        this.uuid = UUID.randomUUID().toString();
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public UserIdentity getUserIdentity() {
        return userIdentity;
    }

    public void setUserIdentity(final UserIdentity userIdentity) {
        this.userIdentity = userIdentity;
    }

    public TokenResponse getTokenResponse() {
        return mutableState.tokenResponse;
    }

    public JwtClaims getJwtClaims() {
        return mutableState.jwtClaims;
    }

    @Override
    public String getJwt() {
        return NullSafe.get(mutableState.tokenResponse, TokenResponse::getAccessToken);
    }

    /**
     * @return The time the token will expire (with a small buffer before the actual expiry included)
     */
    @Override
    public Instant getExpireTime() {
        return Instant.ofEpochMilli(mutableState.expireTimeEpochMs);
    }

    @Override
    public boolean isActive() {
        if (session != null) {
            return isSessionValid(session);
        } else {
            return true;
        }
    }

    @Override
    public boolean isRefreshRequired(final RefreshMode refreshMode) {
        final boolean hasPassedThreshold = System.currentTimeMillis() >= getExpireTimeWithBufferEpochMs(refreshMode);
        boolean isRefreshRequired = hasPassedThreshold;
        LOGGER.trace("hasPassedThreshold: {}", hasPassedThreshold);
        if (additionalRefreshCondition != null) {
            isRefreshRequired = hasPassedThreshold && additionalRefreshCondition.getAsBoolean();
        }
        LOGGER.trace("isRefreshRequired: {}", hasPassedThreshold);
        return isRefreshRequired;
    }

    @Override
    public boolean refresh(final Consumer<Refreshable> onRefreshAction) {
        final boolean didWork;
        if (updateFunction == null) {
            didWork = false;
        } else {
            synchronized (this) {
                final FetchTokenResult fetchTokenResult = fetchToken();
                if (fetchTokenResult != null) {
                    try {
                        this.mutableState = createMutableState(
                                Objects.requireNonNull(fetchTokenResult.tokenResponse()),
                                Objects.requireNonNull(fetchTokenResult.jwtClaims()));
                        NullSafe.consume(this, onRefreshAction);
                        didWork = true;
                    } catch (final Exception e) {
                        LOGGER.error("Error updating token for userIdentity: {}",
                                LogUtil.typedValue(userIdentity), e);
                        throw e;
                    }
                } else {
                    LOGGER.trace("Function returned null, can't update state");
                    didWork = false;
                }
            }
        }
        return didWork;
    }

    private FetchTokenResult fetchToken() {
        try {
            return updateFunction.apply(this);
        } catch (final Exception e) {
            LOGGER.error("Error fetching token - {}. Enable DEBUG for stack trace.", LogUtil.exceptionMessage(e));
            LOGGER.debug("Error fetching token - {}.", LogUtil.exceptionMessage(e), e);
            throw e;
        }
    }

    @Override
    public long getExpireTimeEpochMs() {
        return mutableState.expireTimeEpochMs;
    }

    @Override
    public long getRefreshBufferMs() {
        return mutableState.refreshBufferMs;
    }

    private MutableState createMutableState(final TokenResponse tokenResponse,
                                            final JwtClaims jwtClaims) {
        try {
            final Instant expireTime = Instant.ofEpochMilli(jwtClaims.getExpirationTime().getValueInMillis());
            final Duration timeTilExpiry = Duration.between(Instant.now(), expireTime);
            final long timeToExpireMs = timeTilExpiry.toMillis();
            final long expiryBufferMs = Math.max(MIN_EXPIRY_BUFFER, (long) (timeToExpireMs * EXPIRY_BUFFER_FRACTION));
            final Instant expireTimeWithBuffer = expireTime.minusMillis(expiryBufferMs);

            LOGGER.debug("Updating refresh time - " +
                         "expiryTime: {}, timeToExpire: {}, expiryBufferMs: {}, refreshTime: {}",
                    expireTime, timeTilExpiry, expiryBufferMs, expireTimeWithBuffer);

            return new MutableState(
                    expiryBufferMs,
                    expireTimeWithBuffer.toEpochMilli(),
                    expireTime.toEpochMilli(),
                    tokenResponse,
                    jwtClaims);

        } catch (final MalformedClaimException e) {
            throw new RuntimeException("Unable to extract expiry time from jwtClaims " + jwtClaims, e);
        }
    }

    @Override
    public String toString() {
        return "UpdatableToken{" +
               "sub=" + NullSafe.get(mutableState.jwtClaims, claims ->
                JwtUtil.getClaimValue(claims, OpenId.CLAIM__SUBJECT).orElse(null)) +
               ", preferredUsername=" + NullSafe.get(mutableState.jwtClaims, claims ->
                JwtUtil.getClaimValue(claims, OpenId.CLAIM__PREFERRED_USERNAME).orElse(null)) +
               ", expireTimeWithBuffer=" + Instant.ofEpochMilli(mutableState.expireTimeWithBufferEpochMs) +
               ", timeTilExpire=" + Duration.between(Instant.now(), Instant.ofEpochMilli(
                mutableState.expireTimeWithBufferEpochMs())) +
               ", session=" + SessionUtil.getSessionId(session) +
               '}';
    }

    private boolean isSessionValid(final HttpSession session) {
        try {
            session.getCreationTime();
            return true;
        } catch (final IllegalStateException e) {
            // session has been invalidated
            LOGGER.warn("Invalid session - {}", e.getMessage());
            return false;
        }

    }


    // --------------------------------------------------------------------------------


    /**
     * Hold all the state that gets updated in one immutable object, so we can update the state
     * in one operation.
     */
    private record MutableState(
            long refreshBufferMs,
            long expireTimeWithBufferEpochMs,
            long expireTimeEpochMs,
            TokenResponse tokenResponse,
            JwtClaims jwtClaims) {

    }
}
