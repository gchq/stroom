package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import stroom.docref.SharedObject;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.Objects;

/**
 * Class to represent a duration. Internally it stores the duration as a {@link Duration} object.
 * It can be parsed from an ISO-8601 string (e.g. P30D or P1DT6H or PT60S) or a stroom {@link ModelStringUtil}
 * duration string (e.g. 30d).
 *
 * Typically suited for durations up to numbers of days.  A day is considered as 24hrs. Does NOT
 * support ISO 8601 units of Months and Years.
 *
 * If {@link StroomDuration} is created from a string then the original string representation
 * is held and used for later serialisation. This avoids java's desire to serialise P30D as P720H.
 */
public class StroomDuration implements SharedObject, Comparable<StroomDuration> {
    private static final long serialVersionUID = -6236040236346916133L;

    private String valueAsStr;
    private Duration duration;

    public static final StroomDuration ZERO = new StroomDuration(Duration.ZERO);

    private StroomDuration(final String valueAsStr, final TemporalAmount temporalAmount) {
        Objects.requireNonNull(valueAsStr);
        Objects.requireNonNull(temporalAmount);
        this.valueAsStr = valueAsStr;
        this.duration = Duration.from(temporalAmount);
    }

    private StroomDuration(TemporalAmount temporalAmount) {
        Objects.requireNonNull(temporalAmount);
        this.duration = Duration.from(temporalAmount);
        this.valueAsStr = duration.toString();
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
        return valueAsStr;
    }

    @SuppressWarnings("unused")
    public void setValueAsStr(final String valueAsStr) {
        Objects.requireNonNull(valueAsStr);
        this.valueAsStr = valueAsStr;
        this.duration = parseToDuration(valueAsStr);
    }

    @JsonIgnore
    public Duration getDuration() {
        return duration;
    }

    public void setDuration(final Duration duration) {
        Objects.requireNonNull(duration);
        this.duration = duration;
        this.valueAsStr = duration.toString();
    }

    private static Duration parseToDuration(final String value) {
        if (value == null) {
            return null;
        } else {
            if (value.startsWith("P")) {
                return Duration.parse(value);
            } else {
                return Duration.ofMillis(ModelStringUtil.parseDurationString(value));
            }
        }
    }

    @Override
    public String toString() {
        return valueAsStr;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final StroomDuration that = (StroomDuration) o;
        return Objects.equals(duration, that.duration);
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
