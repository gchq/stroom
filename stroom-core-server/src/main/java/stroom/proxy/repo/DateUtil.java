package stroom.proxy.repo;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

final class DateUtil {
    private static final java.time.format.DateTimeFormatter NORMAL_STROOM_TIME_FORMATTER = java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX");


    private DateUtil() {
        // Private constructor.
    }

    /**
     * Create a 'normal' type date with the current system time.
     */
    static String createNormalDateTimeString() {
        return NORMAL_STROOM_TIME_FORMATTER.format(ZonedDateTime.now(ZoneOffset.UTC));
    }

}