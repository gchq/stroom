package stroom.security.common.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.UserIdentity;
import stroom.security.openid.api.OpenId;
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
import java.util.Optional;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public abstract class AbstractTokenUserIdentity
        implements UserIdentity, HasJwtClaims, HasJwt, Delayed {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractTokenUserIdentity.class);

    private static final long EXPIRY_BUFFER = Duration.ofSeconds(10).toMillis();

    private final String id;

    // Should only mutate all these volatiles using mutateUnderLock()
    private volatile TokenResponse tokenResponse;
    private volatile JwtClaims jwtClaims;
    // The time we need to refresh the tokens which will be a bit BEFORE the token expiry time
    private volatile long expireTimeWithBufferEpochMs;
    private volatile long expireTimeEpochMs;

    /**
     * @param id            The unique ID of the user identity on the IDP
     * @param tokenResponse The response from the token request
     * @param jwtClaims     The {@link JwtClaims} of the ID token
     */
    public AbstractTokenUserIdentity(final String id,
                                     final TokenResponse tokenResponse,
                                     final JwtClaims jwtClaims) {
        this.id = id;
        this.tokenResponse = Objects.requireNonNull(tokenResponse);
        this.jwtClaims = Objects.requireNonNull(jwtClaims);
        updateRefreshTime();
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * @return The ID token JWT
     */
    @Override
    public String getJwt() {
        return tokenResponse.getIdToken();
    }

    public TokenResponse getTokenResponse() {
        return tokenResponse;
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

    public void updateTokens(final TokenResponse tokenResponse, final JwtClaims jwtClaims) {
        synchronized (this) {
            this.tokenResponse = Objects.requireNonNull(tokenResponse);
            this.jwtClaims = Objects.requireNonNull(jwtClaims);
            updateRefreshTime();
        }
    }

    public JwtClaims getJwtClaims() {
        return jwtClaims;
    }

    /**
     * @return True if the token has actually expired without any buffer period.
     */
    public boolean hasTokenExpired() {
        return System.currentTimeMillis() >= expireTimeEpochMs;
    }

    /**
     * Allows for a small buffer before the actual expiry time.
     * Either means we need to use the refresh token to get new tokens or if there is no
     * refresh token then we need to request new tokens without using a refresh token.
     */
    boolean isTokenRefreshRequired() {
        LOGGER.trace(() -> LogUtil.message("expireTimeWithBufferEpochMs",
                Instant.ofEpochMilli(expireTimeWithBufferEpochMs)));
        return System.currentTimeMillis() >= expireTimeWithBufferEpochMs;
    }

    /**
     * @return The time the token will expire (with a small buffer before the actual expiry included)
     */
    public Instant getExpireTime() {
        return Instant.ofEpochMilli(expireTimeWithBufferEpochMs);
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

    /**
     * If isWorkRequiredPredicate returns true when tested against this, work will be
     * performed on this under synchronisation.
     */
    public boolean refresh(final Supplier<UpdatableToken> newTokenSupplier) {

        final boolean didWork;
        if (newTokenSupplier != null && isTokenRefreshRequired()) {
            synchronized (this) {
                if (isTokenRefreshRequired()) {
                    final UpdatableToken newToken = newTokenSupplier.get();
                    updateTokens(
                            newToken.getTokenResponse(),
                            newToken.getJwtClaims());
                    didWork = true;
                } else {
                    LOGGER.trace("Work not required");
                    didWork = false;
                }
            }
        } else {
            LOGGER.trace("Work not required");
            didWork = false;
        }
        return didWork;
    }

    @Override
    public Optional<String> getFullName() {
        return Optional.ofNullable(jwtClaims)
                .flatMap(jwtClaims2 ->
                        JwtUtil.getClaimValue(jwtClaims2, OpenId.CLAIM__NAME));
    }

    @Override
    public String getPreferredUsername() {
        return Optional.ofNullable(jwtClaims)
                .flatMap(jwtClaims2 ->
                        JwtUtil.getClaimValue(jwtClaims2, OpenId.CLAIM__PREFERRED_USERNAME))
                .orElseGet(this::getId);
    }

    // CUSTOM equals
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractTokenUserIdentity that = (AbstractTokenUserIdentity) o;
        return Objects.equals(id, that.id);
    }

    // CUSTOM hash
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AbstractTokenUserIdentity{" +
                "id='" + id + '\'' +
                ", fullName='" + getFullName().orElse("") + '\'' +
                ", preferredUsername='" + getPreferredUsername() + '\'' +
                ", timeTilExpire='" + Duration.between(Instant.now(), getExpireTime()) + '\'' +
                '}';
    }

    @Override
    public int compareTo(final Delayed other) {
        return Math.toIntExact(this.expireTimeWithBufferEpochMs
                - ((AbstractTokenUserIdentity) other).expireTimeWithBufferEpochMs);
    }

    @Override
    public long getDelay(final TimeUnit unit) {
        long diff = expireTimeWithBufferEpochMs - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }
}
