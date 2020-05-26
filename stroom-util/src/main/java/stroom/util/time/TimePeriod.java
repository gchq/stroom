package stroom.util.time;

import stroom.util.logging.LogUtil;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Objects;

/**
 * Defines a period between two instants (the to instant is exclusive)
 */
public class TimePeriod {

    private static final long MS_IN_SECOND = 1000;
    private static final long MS_IN_MINUTE = 1000 * 60;
    private static final long MS_IN_HOUR = 1000 * 60 * 60;

    private final Instant from;
    private final Instant to;

    private TimePeriod(final Instant from, final Instant to) {
        this.from = Objects.requireNonNull(from);
        this.to = Objects.requireNonNull(to);
        if (from.isAfter(to)) {
            throw new RuntimeException(LogUtil.message("from {} is after to {}", from, to));
        }
    }

    /**
     * @param from Is inclusive
     * @param to Is exclusive
     */
    public static TimePeriod between(final Instant from, final Instant to) {
        return new TimePeriod(from, to);
    }

    public static TimePeriod between(final long fromMs, final long toMs) {
        return new TimePeriod(Instant.ofEpochMilli(fromMs), Instant.ofEpochMilli(toMs));
    }

    public Instant getFrom() {
        return from;
    }

    public Instant getTo() {
        return to;
    }

    /**
     * @return The {@link Period} of this {@link TimePeriod} in terms of the UTC time zone.
     */
    public Period getPeriod() {
        return Period.between(
                LocalDate.ofInstant(from, ZoneOffset.UTC),
                LocalDate.ofInstant(to, ZoneOffset.UTC));
    }

    /**
     * @return The {@link Period} of this {@link TimePeriod} in terms of the passed time zone.
     */
    public Period getPeriod(final ZoneOffset zoneOffset) {
        Objects.requireNonNull(zoneOffset);
        return Period.between(
                LocalDate.ofInstant(from, zoneOffset),
                LocalDate.ofInstant(to, zoneOffset));
    }

    public Duration getDuration() {
        return Duration.between(from, to);
    }

    private static String zeroPad(final int amount, final String in) {
        final int left = amount - in.length();
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < left; i++) {
            out.append("0");
        }
        out.append(in);
        return out.toString();
    }

    /**
     * Gets the duration as a user readable string.
     */
    public String getDurationStr() {
        long duration = getDuration().toMillis();
        int totalHours = (int) (duration / MS_IN_HOUR);
        duration = duration - (totalHours * MS_IN_HOUR);

        int hours;
        int days;
        if (totalHours > 24) {
            hours = totalHours % 24;
            days = totalHours / 24;
        } else {
            hours = totalHours;
            days = 0;
        }

        int minutes = (int) (duration / MS_IN_MINUTE);
        duration = duration - (minutes * MS_IN_MINUTE);
        int seconds = (int) (duration / MS_IN_SECOND);
        duration = duration - (seconds * MS_IN_SECOND);

        final StringBuilder sb = new StringBuilder();
        sb.append(zeroPad(2, Integer.toString(totalHours)));
        sb.append(":");
        sb.append(zeroPad(2, Integer.toString(minutes)));
        sb.append(":");
        sb.append(zeroPad(2, Integer.toString(seconds)));
        sb.append(".");
        sb.append(zeroPad(3, Long.toString(duration)));

        if (days > 0) {
            sb.append(" (");
            sb.append(days);
            sb.append(" days ");
            sb.append(hours);
            sb.append(" hrs)");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "from=" + from +
                ", to=" + to +
                ", duration=" + getDuration();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TimePeriod that = (TimePeriod) o;
        return from.equals(that.from) &&
                to.equals(that.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    public static Comparator<TimePeriod> comparingByFromTime() {
        return Comparator.comparing(TimePeriod::getFrom);
    }

    public static Comparator<TimePeriod> comparingByToTime() {
        return Comparator.comparing(TimePeriod::getFrom);
    }
}
