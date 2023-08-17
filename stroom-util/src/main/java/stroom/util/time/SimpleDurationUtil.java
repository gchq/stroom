package stroom.util.time;

import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleDurationUtil {

    private static final Pattern SIMPLE_FORMAT_PATTERN = Pattern.compile("^(\\d+)(\\w+)$");

    public static SimpleDuration parse(final String string) throws ParseException {
        Objects.requireNonNull(string, "String is null");

        final String trimmed = string.trim();
        final Matcher matcher = SIMPLE_FORMAT_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            try {
                final String number = matcher.group(1);
                final String unit = matcher.group(2);
                final long time = Long.parseLong(number);
                final TimeUnit timeUnit;

                switch (unit) {
                    case "ns":
                        timeUnit = TimeUnit.NANOSECONDS;
                        break;
                    case "ms":
                        timeUnit = TimeUnit.MILLISECONDS;
                        break;
                    case "s":
                        timeUnit = TimeUnit.SECONDS;
                        break;
                    case "m":
                        timeUnit = TimeUnit.MINUTES;
                        break;
                    case "h":
                        timeUnit = TimeUnit.HOURS;
                        break;
                    case "d", "D":
                        timeUnit = TimeUnit.DAYS;
                        break;
                    case "w", "W":
                        timeUnit = TimeUnit.WEEKS;
                        break;
                    case "M":
                        timeUnit = TimeUnit.MONTHS;
                        break;
                    case "y", "Y":
                        timeUnit = TimeUnit.YEARS;
                        break;
                    default:
                        throw new ParseException("Time unit is invalid: " + trimmed, 0);
                }

                return new SimpleDuration(time, timeUnit);
            } catch (final RuntimeException e) {
                throw new ParseException("Format of duration string is invalid: " + trimmed, 0);
            }
        } else {
            throw new ParseException("Format of duration string is invalid: " + trimmed, 0);
        }
    }

    public static Instant plus(final Instant dateTime,
                                final SimpleDuration simpleDuration) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(dateTime, ZoneOffset.UTC);
        localDateTime = plus(localDateTime, simpleDuration);
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    public static LocalDateTime plus(final LocalDateTime dateTime,
                                     final SimpleDuration simpleDuration) {
        if (simpleDuration == null) {
            return dateTime;
        }
        switch (simpleDuration.getTimeUnit()) {
            case NANOSECONDS:
                return dateTime.plusNanos(simpleDuration.getTime());
            case MILLISECONDS:
                return dateTime.plusNanos(simpleDuration.getTime() * 1000000);
            case SECONDS:
                return dateTime.plusSeconds(simpleDuration.getTime());
            case MINUTES:
                return dateTime.plusMinutes(simpleDuration.getTime());
            case HOURS:
                return dateTime.plusHours(simpleDuration.getTime());
            case DAYS:
                return dateTime.plusDays(simpleDuration.getTime());
            case WEEKS:
                return dateTime.plusWeeks(simpleDuration.getTime());
            case MONTHS:
                return dateTime.plusMonths(simpleDuration.getTime());
            case YEARS:
                return dateTime.plusYears(simpleDuration.getTime());
        }
        throw new UnsupportedOperationException("Unknown time unit");
    }

    public static Instant minus(final Instant dateTime,
                                final SimpleDuration simpleDuration) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(dateTime, ZoneOffset.UTC);
        localDateTime = minus(localDateTime, simpleDuration);
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    public static LocalDateTime minus(final LocalDateTime dateTime,
                                      final SimpleDuration simpleDuration) {
        if (simpleDuration == null) {
            return dateTime;
        }
        switch (simpleDuration.getTimeUnit()) {
            case NANOSECONDS:
                return dateTime.minusNanos(simpleDuration.getTime());
            case MILLISECONDS:
                return dateTime.minusNanos(simpleDuration.getTime() * 1000000);
            case SECONDS:
                return dateTime.minusSeconds(simpleDuration.getTime());
            case MINUTES:
                return dateTime.minusMinutes(simpleDuration.getTime());
            case HOURS:
                return dateTime.minusHours(simpleDuration.getTime());
            case DAYS:
                return dateTime.minusDays(simpleDuration.getTime());
            case WEEKS:
                return dateTime.minusWeeks(simpleDuration.getTime());
            case MONTHS:
                return dateTime.minusMonths(simpleDuration.getTime());
            case YEARS:
                return dateTime.minusYears(simpleDuration.getTime());
        }
        throw new UnsupportedOperationException("Unknown time unit");
    }

    public static LocalDateTime roundDown(LocalDateTime dateTime, SimpleDuration simpleDuration) {
        switch (simpleDuration.getTimeUnit()) {
            case NANOSECONDS:
                return LocalDateTime.of(
                        dateTime.getYear(),
                        dateTime.getMonthValue(),
                        dateTime.getDayOfMonth(),
                        dateTime.getHour(),
                        dateTime.getMinute(),
                        dateTime.getSecond(),
                        (dateTime.getNano() / 1000000) & 1000000);
            case MILLISECONDS:
                return LocalDateTime.of(
                        dateTime.getYear(),
                        dateTime.getMonthValue(),
                        dateTime.getDayOfMonth(),
                        dateTime.getHour(),
                        dateTime.getMinute(),
                        dateTime.getSecond());
            case SECONDS:
                return LocalDateTime.of(
                        dateTime.getYear(),
                        dateTime.getMonthValue(),
                        dateTime.getDayOfMonth(),
                        dateTime.getHour(),
                        dateTime.getMinute());
            case MINUTES:
                return LocalDateTime.of(
                        dateTime.getYear(),
                        dateTime.getMonthValue(),
                        dateTime.getDayOfMonth(),
                        dateTime.getHour(),
                        0);
            case HOURS:
                return LocalDateTime.of(
                        dateTime.getYear(),
                        dateTime.getMonthValue(),
                        dateTime.getDayOfMonth(),
                        0,
                        0);
            case DAYS:
                return LocalDateTime.of(
                        dateTime.getYear(),
                        dateTime.getMonthValue(),
                        1,
                        0,
                        0);
            case WEEKS, MONTHS:
                return LocalDateTime.of(
                        dateTime.getYear(),
                        1,
                        1,
                        0,
                        0);
            case YEARS:
                return LocalDateTime.of(
                        1970,
                        1,
                        1,
                        0,
                        0);
        }
        throw new UnsupportedOperationException("Unknown time unit");
    }
}
