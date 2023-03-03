package stroom.security.common.impl;

import stroom.security.api.UserIdentity;
import stroom.security.common.impl.AbstractUserIdentityFactory.FetchTokenResult;
import stroom.security.openid.api.TokenResponse;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public class UpdatableToken implements HasJwtClaims, Delayed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UpdatableToken.class);

    private static final long EXPIRY_BUFFER = Duration.ofSeconds(10)
            .toMillis();

    private UserIdentity userIdentity = null;
    private volatile TokenResponse tokenResponse;
    private volatile JwtClaims jwtClaims;

    private BooleanSupplier additionalRefreshCondition = null;
    private final Function<UpdatableToken, FetchTokenResult> updateFunction;

    // The time we need to refresh the tokens which will be a bit BEFORE the token expiry time
    private volatile long expireTimeWithBufferEpochMs;
    private volatile long expireTimeEpochMs;

    public UpdatableToken(final TokenResponse tokenResponse,
                          final JwtClaims jwtClaims,
                          final Function<UpdatableToken, FetchTokenResult> updateFunction) {
        this(tokenResponse, jwtClaims, updateFunction, null);
    }

    /**
     * @param tokenResponse
     * @param jwtClaims
     * @param updateFunction            The function to get new up-to-date tokens.
     * @param additionalUpdateCondition A condition that will be tested in addition to token expiry
     *                                  to determine whether a refresh is needed or not, e.g. is the user
     *                                  still in session.
     */
    public UpdatableToken(final TokenResponse tokenResponse,
                          final JwtClaims jwtClaims,
                          final Function<UpdatableToken, FetchTokenResult> updateFunction,
                          final BooleanSupplier additionalUpdateCondition) {
        this.tokenResponse = tokenResponse;
        this.jwtClaims = jwtClaims;
        this.updateFunction = updateFunction;
        this.additionalRefreshCondition = additionalUpdateCondition;
        updateRefreshTime();
    }

    public UserIdentity getUserIdentity() {
        return userIdentity;
    }

    public void setUserIdentity(final UserIdentity userIdentity) {
        this.userIdentity = userIdentity;
    }

    public TokenResponse getTokenResponse() {
        return tokenResponse;
    }

    public JwtClaims getJwtClaims() {
        return jwtClaims;
    }

    public String getIdToken() {
        return NullSafe.get(tokenResponse, TokenResponse::getIdToken);
    }

    public String getAccessToken() {
        return NullSafe.get(tokenResponse, TokenResponse::getAccessToken);
    }

    public String getRefreshToken() {
        return NullSafe.get(tokenResponse, TokenResponse::getRefreshToken);
    }

    public boolean hasRefreshToken() {
        return !NullSafe.isBlankString(tokenResponse, TokenResponse::getRefreshToken);
    }

//    /**
//     * @return True if the token has actually expired without any buffer period.
//     */
//    public boolean hasTokenExpired() {
//        return System.currentTimeMillis() >= expireTimeEpochMs;
//    }

    /**
     * Allows for a small buffer before the actual expiry time.
     * Either means we need to use the refresh token to get new tokens or if there is no
     * refresh token then we need to request new tokens without using a refresh token.
     */
    boolean isTokenRefreshRequired() {
        final boolean isExpired = System.currentTimeMillis() >= expireTimeWithBufferEpochMs;
        if (additionalRefreshCondition != null) {
            LOGGER.trace(() -> LogUtil.message("isExpired: {}, additionalRefreshCondition: {}",
                    isExpired, additionalRefreshCondition.getAsBoolean()));
            return isExpired && additionalRefreshCondition.getAsBoolean();
        } else {
            LOGGER.trace("isExpired: {}", isExpired);
            return isExpired;
        }
    }

    /**
     * @return The time the token will expire (with a small buffer before the actual expiry included)
     */
    public Instant getExpireTime() {
        return Instant.ofEpochMilli(expireTimeWithBufferEpochMs);
    }

    /**
     * If isWorkRequiredPredicate returns true when tested against this, work will be
     * performed on this under synchronisation.
     */
    public boolean refreshIfRequired() {

        final boolean didWork;
        if (updateFunction != null && isTokenRefreshRequired()) {
            synchronized (this) {
                if (isTokenRefreshRequired()) {
                    final FetchTokenResult fetchTokenResult = updateFunction.apply(this);
                    if (fetchTokenResult != null) {
                        this.tokenResponse = Objects.requireNonNull(fetchTokenResult.tokenResponse());
                        this.jwtClaims = Objects.requireNonNull(fetchTokenResult.jwtClaims());
                        updateRefreshTime();
                        didWork = true;
                    } else {
                        LOGGER.trace("Function returned null, can't update state");
                        didWork = false;
                    }
                } else {
                    LOGGER.trace("Refresh not required");
                    didWork = false;
                }
            }
        } else {
            LOGGER.trace("Refresh Work not required");
            didWork = false;
        }
        return didWork;
    }

    @Override
    public int compareTo(final Delayed other) {
        return Math.toIntExact(this.expireTimeWithBufferEpochMs
                - ((UpdatableToken) other).expireTimeWithBufferEpochMs);
    }

    @Override
    public long getDelay(final TimeUnit unit) {
        long diff = expireTimeWithBufferEpochMs - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    private void updateRefreshTime() {
        try {
            final Instant expireTime = Instant.ofEpochMilli(jwtClaims.getExpirationTime().getValueInMillis());
            final Duration timeToExpire = Duration.between(Instant.now(), expireTime);
            final long timeToExpireMs = timeToExpire.toMillis();
            final long expiryBufferMs = Math.min(EXPIRY_BUFFER, (long) (timeToExpireMs * 0.1));
            final Instant expireTimeWithBuffer = expireTime.minusMillis(expiryBufferMs);
            expireTimeEpochMs = expireTime.toEpochMilli();
            expireTimeWithBufferEpochMs = expireTimeWithBuffer.toEpochMilli();

            LOGGER.debug("Updating refresh time - " +
                            "expiryTime: {}, timeToExpire: {}, expiryBufferMs: {}, refreshTime: {}",
                    expireTime, timeToExpire, expiryBufferMs, expireTimeWithBuffer);

        } catch (MalformedClaimException e) {
            throw new RuntimeException("Unable to extract expiry time from jwtClaims " + jwtClaims, e);
        }
    }

    @Override
    public String toString() {
        return "UpdatableToken{" +
                "sub=" + NullSafe.get(jwtClaims, claims ->
                JwtUtil.getClaimValue(claims, "sub").orElse(null)) +
                "expireTimeWithBuffer=" + Instant.ofEpochMilli(expireTimeWithBufferEpochMs) +
                ", timeTilExpire=" + Duration.between(Instant.now(), getExpireTime()) +
                '}';
    }

}
