package stroom.util.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Objects;

public class TimeUtils {

    private static final Duration PERIOD_DURATION_THRESHOLD = Duration.ofDays(30);

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
     *
     * @param now The instant used to represent the current time. Useful for testing purposes.
     */
    public static Instant durationToThreshold(final Instant now, final StroomDuration stroomDuration) {
        return durationToThreshold(now, stroomDuration.getDuration());
    }

    /**
     * Converts a duration, e.g. 30days into a point in time in the past, i.e.
     * current system time minus the duration.
     *
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

    /**
     * If time is in the past then return the period from time until now().
     * If time is in the future then return the period from now() until time.
     */
    public static Period instantAsAge(final Instant time) {
        return instantAsAge(time, Instant.now());
    }

    /**
     * If time is in the past then return the period from time until now().
     * If time is in the future then return the period from now() until time.
     */
    public static Period instantAsAge(final Instant time, final Instant now) {
        Objects.requireNonNull(time);
        Objects.requireNonNull(now);

        if (time.isBefore(now)) {
            return Period.between(
                    LocalDate.ofInstant(time, ZoneOffset.UTC),
                    LocalDate.ofInstant(now, ZoneOffset.UTC));
        } else {
            return Period.between(
                    LocalDate.ofInstant(now, ZoneOffset.UTC),
                    LocalDate.ofInstant(time, ZoneOffset.UTC));
        }
    }

    /**
     * If time is in the past then return the period from time until now().
     * If time is in the future then return the period from now() until time.
     */
    public static String instantAsAgeStr(final Instant time, final Instant now) {
        Objects.requireNonNull(time);
        Objects.requireNonNull(now);

        return time.isBefore(now)
                ? toPeriodOrDurationStr(time, now)
                : toPeriodOrDurationStr(now, time);
    }

    public static String periodAsAgeStr(final TimePeriod timePeriod) {
        Objects.requireNonNull(timePeriod);
        return toPeriodOrDurationStr(timePeriod.getFrom(), timePeriod.getTo());
    }

    public static String toPeriodOrDurationStr(final Instant from, final Instant to) {
        final Duration duration = Duration.between(from, to);
        return duration.compareTo(PERIOD_DURATION_THRESHOLD) < 0
                ? duration.toString()
                : Period.between(
                        LocalDate.ofInstant(from, ZoneOffset.UTC),
                        LocalDate.ofInstant(to, ZoneOffset.UTC)).toString();
    }
}
