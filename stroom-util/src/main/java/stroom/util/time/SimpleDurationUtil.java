package stroom.util.time;

import stroom.util.shared.time.SimpleDuration;

import java.time.LocalDateTime;

public class SimpleDurationUtil {

    public static LocalDateTime plus(final LocalDateTime dateTime,
                                     final SimpleDuration simpleDuration) {
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

    public static LocalDateTime minus(final LocalDateTime dateTime,
                                      final SimpleDuration simpleDuration) {
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
