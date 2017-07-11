package stroom.streamstore.server;

import stroom.streamstore.shared.DataRetentionRule;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * A utility class to calculate times from supplied retention ages.
 */
public final class DataRetentionAgeUtil {
    private DataRetentionAgeUtil() {
        // Utility class.
    }

    public static Long minus(final LocalDateTime now, final DataRetentionRule rule) {
        if (rule.isForever()) {
            return null;
        }

        LocalDateTime age = null;
        switch (rule.getTimeUnit()) {
            case MINUTES:
                age = now.minusMinutes(rule.getAge());
                break;
            case HOURS:
                age = now.minusHours(rule.getAge());
                break;
            case DAYS:
                age = now.minusDays(rule.getAge());
                break;
            case WEEKS:
                age = now.minusWeeks(rule.getAge());
                break;
            case MONTHS:
                age = now.minusMonths(rule.getAge());
                break;
            case YEARS:
                age = now.minusYears(rule.getAge());
                break;
        }

        if (age == null) {
            return null;
        }

        return age.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public static Long plus(final LocalDateTime now, final DataRetentionRule rule) {
        if (rule.isForever()) {
            return null;
        }

        LocalDateTime age = null;
        switch (rule.getTimeUnit()) {
            case MINUTES:
                age = now.plusMinutes(rule.getAge());
                break;
            case HOURS:
                age = now.plusHours(rule.getAge());
                break;
            case DAYS:
                age = now.plusDays(rule.getAge());
                break;
            case WEEKS:
                age = now.plusWeeks(rule.getAge());
                break;
            case MONTHS:
                age = now.plusMonths(rule.getAge());
                break;
            case YEARS:
                age = now.plusYears(rule.getAge());
                break;
        }

        if (age == null) {
            return null;
        }

        return age.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
