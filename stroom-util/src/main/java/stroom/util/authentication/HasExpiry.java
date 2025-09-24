package stroom.util.authentication;

import java.time.Duration;
import java.time.Instant;

/**
 * Something that has an expiry date/time
 */
public interface HasExpiry {

    /**
     * @return The instant when this object will expire or {@link Instant#MAX} if there
     * is no expiry.
     */
    Instant getExpireTime();

    default long getExpireTimeEpochMs() {
        return getExpireTime().toEpochMilli();
    }

    /**
     * @return True if this object has already expired.
     */
    default boolean hasExpired() {
        return Instant.now()
                .isAfter(getExpireTime());
    }

    /**
     * @return The duration until expiry. Will be negative if already expired.
     */
    default Duration getTimeTilExpired() {
        return Duration.between(Instant.now(), getExpireTime());
    }
}
