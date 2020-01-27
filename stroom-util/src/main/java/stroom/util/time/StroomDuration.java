package stroom.util.time;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import stroom.util.shared.ModelStringUtil;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.Objects;

/**
 * Class to represent a duration. Internally it stores the duration as a {@link Duration} object.
 * It can be parsed from an ISO-8601 string (e.g. P30D or P1DT6H or PT60S), a stroom {@link ModelStringUtil}
 * type duration string (e.g. 30d), or a raw milliseconds value.
 *
 * Typically suited for durations up to numbers of days.  A day is considered as 24hrs. Does NOT
 * support ISO 8601 units of Months and Years.
 *
 * If {@link StroomDuration} is created from a string then the original string representation
 * is held and used for later serialisation. This avoids java's desire to serialise P30D as P720H.
 */
public class StroomDuration implements Comparable<StroomDuration> {

    @Nullable
    private final String valueAsStr;

    private final Duration duration;

    public static final StroomDuration ZERO = new StroomDuration(Duration.ZERO);

    private StroomDuration(@Nullable final String valueAsStr,
                           final Duration duration) {
        Objects.requireNonNull(duration);
        this.duration = duration;

        if (valueAsStr != null && !valueAsStr.equals(duration.toString())) {
            // Custom string form so store it
            this.valueAsStr = valueAsStr;
        } else {
            this.valueAsStr = null;
        }
    }

    private StroomDuration(final TemporalAmount temporalAmount) {
        Objects.requireNonNull(temporalAmount);
        if (Duration.class.isAssignableFrom(temporalAmount.getClass())) {
            this.duration = (Duration) temporalAmount;
        } else {
            this.duration = Duration.from(temporalAmount);
        }
        String durationStr = duration.toString();
        // Duration won't output in days, instead using multiple hours, e.g. P30D => PT720H
        // which is a bit grim, so do a simple hack to deal with whole numbers of days.
        if (durationStr.matches("^PT[0-9]+[hH]$")) {
            // get the number of hours
            int hours = Integer.parseInt(durationStr.substring(2,durationStr.length() - 1));
            int remainderHours = hours % 24;
//            if (hours >= 24 && hours % 24 == 0) {
            if (hours >= 24) {
                String valueAsStr = "P" + hours / 24 + "D";
                if (remainderHours > 0) {
                    valueAsStr += "T" + remainderHours + "H";
                }
                // Just make sure our constructed string is a valid ISO 8601 string
                Duration.parse(valueAsStr);
                this.valueAsStr = valueAsStr;
            } else {
                this.valueAsStr = null;
            }
        } else {
            this.valueAsStr = null;
        }
    }

    @JsonCreator
    public static StroomDuration parse(final String value) {
        return new StroomDuration(value, parseToDuration(value));
    }

    public static StroomDuration of(final TemporalAmount temporalAmount) {
        return new StroomDuration(temporalAmount);
    }

    public static StroomDuration of(final long amount, final TemporalUnit unit) {
        return new StroomDuration(Duration.of(amount, unit));
    }

    public static StroomDuration ofDays(final long days) {
        return new StroomDuration(Duration.ofDays(days));
    }

    public static StroomDuration ofHours(final long hours) {
        return new StroomDuration(Duration.ofHours(hours));
    }

    public static StroomDuration ofMinutes(final long minutes) {
        return new StroomDuration(Duration.ofMinutes(minutes));
    }

    public static StroomDuration ofSeconds(final long seconds) {
        return new StroomDuration(Duration.ofSeconds(seconds));
    }

    public static StroomDuration ofMillis(final long millis) {
        return new StroomDuration(Duration.ofMillis(millis));
    }

    public static StroomDuration ofNanos(final long nanos) {
        return new StroomDuration(Duration.ofNanos(nanos));
    }


    @SuppressWarnings("unused")
    @JsonValue
    public String getValueAsStr() {
        return valueAsStr != null
            ? valueAsStr
            : duration.toString();
    }

    public Duration getDuration() {
        return duration;
    }

    private static Duration parseToDuration(final String value) {
        if (value == null) {
            return null;
        } else {
            if (value.startsWith("P")) {
                // This is ISO 8601 format so use Duration to parse it
                return Duration.parse(value);
            } else {
                // Not ISO 8601 so have a go with our ModelStringUtil format
                return Duration.ofMillis(ModelStringUtil.parseDurationString(value));
            }
        }
    }

    public long toMillis() {
        return duration.toMillis();
    }

    public long toNanos() {
        return duration.toNanos();
    }

    public boolean isZero() {
        return duration.isZero();
    }

    @Override
    public String toString() {
        return getValueAsStr();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final StroomDuration that = (StroomDuration) o;
        return duration.equals(that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration);
    }

    @Override
    public int compareTo(final StroomDuration o) {
        return duration.compareTo(o.duration);
    }
}
