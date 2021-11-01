package stroom.ignite.api;

import java.time.Duration;
import java.time.Instant;

public interface ApacheIgniteService {

    /**
     * Modify a distributed counter
     *
     * @param amount Amount to increment by or zero for don't change (a negative value means decrement)
     * @param keyType Best practice is for key to be camelCase.with.dotSeparators
     * @param key Any key, e.g. a device hostname
     * @param timestamp When the event occurred
     * @param resolution The size of the time bucket to increment
     *
     * @return
     */
    Long incrementAndGetCounter(final long amount,
                                final String keyType, final String key,
                                final Instant timestamp, final Duration resolution);
}
