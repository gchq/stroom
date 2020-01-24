package stroom.util.time;

import java.time.Duration;
import java.time.Instant;

public class TimeUtils {

    private TimeUtils() {
    }

    /**
     * Converts a duration, e.g. 30days into a point in time in the past, i.e.
     * current system time minus the duration.
     */
    public static Instant durationToThreshold(final StroomDuration stroomDuration) {
        return durationToThreshold(stroomDuration.getDuration());
    }

    /**
     * Converts a duration, e.g. 30days into a point in time in the past, i.e.
     * current system time minus the duration.
     * @param now The instant used to represent the current time. Useful for testing purposes.
     */
    public static Instant durationToThreshold(final Instant now, final StroomDuration stroomDuration) {
        return durationToThreshold(stroomDuration.getDuration());
    }

    /**
     * Converts a duration, e.g. 30days into a point in time in the past, i.e.
     * current system time minus the duration.
     * @param now The instant used to represent the current time. Useful for testing purposes.
     */
    public static Instant durationToThreshold(final Instant now, final Duration duration) {
        return now.minus(duration);
    }

    /**
     * Converts a duration, e.g. 30days into a point in time in the past, i.e.
     * current system time minus the duration.
     */
    public static Instant durationToThreshold(final Duration duration) {
        return durationToThreshold(Instant.now(), duration);
    }

}
