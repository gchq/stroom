package stroom.pathways.shared.otel.trace;

import stroom.util.shared.StringUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * This class is required because GWT doesn't support java.time.Instant.
 */
@JsonInclude(Include.NON_NULL)
public class NanoTime implements Comparable<NanoTime> {

    private static final int NANOS_IN_SECOND = 1000000000;
    public static NanoTime ZERO = new NanoTime(0, 0);

    /**
     * The number of seconds from the epoch of 1970-01-01T00:00:00Z.
     */
    @JsonProperty
    private final long seconds;
    /**
     * The number of nanoseconds, later along the time-line, from the seconds field.
     * This is always positive, and never exceeds 999,999,999.
     */
    @JsonProperty
    private final int nanos;

    @JsonCreator
    public NanoTime(@JsonProperty("seconds") final long seconds,
                    @JsonProperty("nanos") final int nanos) {
        this.seconds = seconds;
        this.nanos = nanos;
        assert seconds >= 0;
        assert nanos >= 0;
        assert nanos < NANOS_IN_SECOND;
    }

    public static NanoTime ofSeconds(final long seconds) {
        return new NanoTime(seconds, 0);
    }

    public static NanoTime ofMillis(final long milliseconds) {
        return new NanoTime(milliseconds / 1000, (int) ((milliseconds % 1000) * 1000000));
    }

    public static NanoTime ofMicros(final long microseconds) {
        return new NanoTime(microseconds / 1000000, (int) ((microseconds % 1000000) * 1000));
    }

    public static NanoTime ofNanos(final long nanos) {
        return new NanoTime(nanos / NANOS_IN_SECOND, (int) (nanos % NANOS_IN_SECOND));
    }

    public static NanoTime fromString(final String time) {
        if (time == null) {
            return null;
        }
        if (time.length() > 9) {
            final String nanoString = time.substring(time.length() - 9);
            final String secondString = time.substring(0, time.length() - 9);
            return new NanoTime(Long.parseLong(secondString), Integer.parseInt(nanoString));
        }
        return new NanoTime(0, Integer.parseInt(time));
    }

    public String toNanoEpochString() {
        String nanoString = String.valueOf(nanos);
        nanoString = StringUtil.prefix(nanoString, '0', 9 - nanoString.length());
        return seconds + nanoString;
    }

    public long getSeconds() {
        return seconds;
    }

    public int getNanos() {
        return nanos;
    }

    public long toEpochNanos() {
        if (seconds > 9223372036L) {
            throw new RuntimeException("NanoTime too large to convert to nanos");
        }

        return (seconds * NANOS_IN_SECOND) + nanos;
    }

    public long toEpochMillis() {
        if (seconds > 9223372036854775L) {
            throw new RuntimeException("NanoTime too large to convert to millis");
        }

        return (seconds * 1000) + (nanos / 1000000);
    }

    public NanoTime add(final NanoTime nanoTime) {
        if (nanoTime.equals(ZERO)) {
            return this;
        } else if (equals(ZERO)) {
            return nanoTime;
        }

        if (nanos + nanoTime.nanos >= NANOS_IN_SECOND) {
            return new NanoTime(seconds + nanoTime.seconds + 1, nanos - nanoTime.nanos - NANOS_IN_SECOND);
        }
        return new NanoTime(seconds + nanoTime.seconds, nanos + nanoTime.nanos);
    }

    public NanoTime subtract(final NanoTime nanoTime) {
        if (nanoTime.equals(ZERO)) {
            return this;
        } else if (nanoTime.equals(this)) {
            return ZERO;
        }

        if (nanos - nanoTime.nanos < 0) {
            return new NanoTime(seconds - nanoTime.seconds - 1, nanos - nanoTime.nanos + NANOS_IN_SECOND);
        }
        return new NanoTime(seconds - nanoTime.seconds, nanos - nanoTime.nanos);
    }

//    public NanoTime multiplyBy(final double multiplier) {
//        final long seconds = (long) (this.seconds * multiplier);
//        final long nanos = (long) (this.nanos * multiplier);
//        return new NanoTime(seconds + nanos / NANOS_IN_SECOND, (int) (nanos % NANOS_IN_SECOND));
//    }
//
//    public NanoTime divideBy(final double divisor) {
//        final double seconds = this.seconds / divisor;
//        final double nanos = this.nanos / divisor;
//        return new NanoTime(
//                (long) (seconds),
//                (int) (nanos + ((seconds % 1) * NANOS_IN_SECOND)));
//    }

    public boolean isLessThan(final NanoTime nanoTime) {
        return seconds < nanoTime.seconds || (seconds == nanoTime.seconds && nanos < nanoTime.nanos);
    }

    public boolean isLessThanEquals(final NanoTime nanoTime) {
        return seconds < nanoTime.seconds || (seconds == nanoTime.seconds && nanos <= nanoTime.nanos);
    }

    public boolean isBefore(final NanoTime nanoTime) {
        return isLessThan(nanoTime);
    }

    public boolean isGreaterThan(final NanoTime nanoTime) {
        return seconds > nanoTime.seconds || (seconds == nanoTime.seconds && nanos > nanoTime.nanos);
    }

    public boolean isGreaterThanEquals(final NanoTime nanoTime) {
        return seconds > nanoTime.seconds || (seconds == nanoTime.seconds && nanos >= nanoTime.nanos);
    }

    public boolean isAfter(final NanoTime nanoTime) {
        return isGreaterThan(nanoTime);
    }

    public NanoDuration diff(final NanoTime nanoTime) {
        if (equals(nanoTime)) {
            return NanoDuration.ZERO;
        } else if (isLessThan(nanoTime)) {
            return NanoDuration.ofNanos(nanoTime.subtract(this).toEpochNanos());
        }
        return NanoDuration.ofNanos(subtract(nanoTime).toEpochNanos());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NanoTime nanoTime = (NanoTime) o;
        return seconds == nanoTime.seconds && nanos == nanoTime.nanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(seconds, nanos);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }

    public void append(final StringBuilder sb) {
        if (seconds > 0) {
            final long minutes = seconds / 60;
            if (minutes > 0) {
                final long hours = minutes / 60;
                if (hours > 0) {
                    sb.append(format(seconds / 3600F));
                    sb.append("h");
                } else {
                    sb.append(format(seconds / 60F));
                    sb.append("m");
                }
            } else {
                final float fractionalSeconds = seconds + (nanos / 1000000000F);
                sb.append(format(fractionalSeconds));
                sb.append("s");
            }
        } else {
            final long ms = nanos / 1000000;
            if (ms > 0) {
                sb.append(format(nanos / 1000000F));
                sb.append("ms");
            } else {
                final long micro = nanos / 1000;
                if (micro > 0) {
                    sb.append(format(nanos / 1000F));
                    sb.append("µs");
                } else {
                    sb.append(nanos);
                    sb.append("ns");
                }
            }
        }
    }

    /**
     * We have to use Double.toString and trim trailing 0's rather than use DecimalFormat as it isn't supported by GWT.
     *
     * @param d The double to format to 2 decimal places.
     * @return A decimal place (or less) string.
     */
    private String format(final double d) {
        final double rounded = Math.round(d * 100D) / 100D;
        final String string = Double.toString(rounded);
        final int index = string.indexOf(".");
        if (index == -1) {
            return string;
        }

        final char[] chars = string.toCharArray();
        int end = chars.length - 1;
        for (; end >= index; end--) {
            if (chars[end] != '0') {
                break;
            }
        }
        if (end == index) {
            return string.substring(0, index);
        } else if (end < chars.length - 1) {
            return string.substring(0, end + 1);
        }
        return string;
    }

    @Override
    public int compareTo(final NanoTime o) {
        if (seconds == o.seconds) {
            return Integer.compare(nanos, o.nanos);
        }
        return Long.compare(seconds, o.seconds);
    }
}
