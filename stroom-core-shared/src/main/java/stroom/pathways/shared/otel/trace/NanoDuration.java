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
public class NanoDuration implements Comparable<NanoDuration> {

    public static NanoDuration ZERO = new NanoDuration(0);

    /**
     * The number of nanoseconds.
     */
    @JsonProperty
    private final long nanos;

    @JsonCreator
    public NanoDuration(@JsonProperty("nanos") final long nanos) {
        this.nanos = nanos;
    }

    public static NanoDuration ofSeconds(final long seconds) {
        return new NanoDuration(seconds * 1000000000);
    }

    public static NanoDuration ofMillis(final long milliseconds) {
        return new NanoDuration(milliseconds * 1000000);
    }

    public static NanoDuration ofMicros(final long microseconds) {
        return new NanoDuration(microseconds * 1000);
    }

    public static NanoDuration ofNanos(final long nanos) {
        return new NanoDuration(nanos);
    }

    public static NanoDuration fromString(final String time) {
        if (time == null) {
            return null;
        }
        return new NanoDuration(Long.parseLong(time));
    }

    public long getNanos() {
        return nanos;
    }

    public NanoDuration add(final NanoDuration nanoDuration) {
        if (nanoDuration.equals(ZERO)) {
            return this;
        } else if (equals(ZERO)) {
            return nanoDuration;
        }
        return new NanoDuration(nanos + nanoDuration.nanos);
    }

    public NanoDuration subtract(final NanoDuration nanoDuration) {
        if (nanoDuration.equals(ZERO)) {
            return this;
        } else if (nanoDuration.equals(this)) {
            return ZERO;
        }
        return new NanoDuration(nanos - nanoDuration.nanos);
    }

    public boolean isLessThan(final NanoDuration nanoDuration) {
        return nanos < nanoDuration.nanos;
    }

    public boolean isLessThanEquals(final NanoDuration nanoDuration) {
        return nanos <= nanoDuration.nanos;
    }

    public boolean isGreaterThan(final NanoDuration nanoDuration) {
        return nanos > nanoDuration.nanos;
    }

    public boolean isGreaterThanEquals(final NanoDuration nanoDuration) {
        return nanos >= nanoDuration.nanos;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NanoDuration nanoTime = (NanoDuration) o;
        return nanos == nanoTime.nanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nanos);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }

    public void append(final StringBuilder sb) {
        final long ms = nanos / 1000000;
        if (ms > 0) {
            sb.append(StringUtil.formatDouble(nanos / 1000000F));
            sb.append("ms");
        } else {
            final long micro = nanos / 1000;
            if (micro > 0) {
                sb.append(StringUtil.formatDouble(nanos / 1000F));
                sb.append("µs");
            } else {
                sb.append(nanos);
                sb.append("ns");
            }
        }
    }

//    /**
//     * We have to use Double.toString and trim trailing 0's rather
//     than use DecimalFormat as it isn't supported by GWT.
//     *
//     * @param d The double to format to 2 decimal places.
//     * @return A decimal place (or less) string.
//     */
//    private String format(final double d) {
//        final double rounded = Math.round(d * 100D) / 100D;
//        final String string = Double.toString(rounded);
//        final int index = string.indexOf(".");
//        if (index == -1) {
//            return string;
//        }
//
//        final char[] chars = string.toCharArray();
//        int end = chars.length - 1;
//        for (; end >= index; end--) {
//            if (chars[end] != '0') {
//                break;
//            }
//        }
//        if (end == index) {
//            return string.substring(0, index);
//        } else if (end < chars.length - 1) {
//            return string.substring(0, end + 1);
//        }
//        return string;
//    }

    @Override
    public int compareTo(final NanoDuration o) {
        return Long.compare(nanos, o.nanos);
    }
}
