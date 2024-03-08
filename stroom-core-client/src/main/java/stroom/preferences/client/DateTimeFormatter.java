package stroom.preferences.client;

import stroom.expression.api.TimeZone;
import stroom.expression.api.TimeZone.Use;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.customdatebox.client.ClientDurationUtil;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DateTimeFormatter {

    private final UserPreferencesManager userPreferencesManager;

    @Inject
    public DateTimeFormatter(final UserPreferencesManager userPreferencesManager) {
        this.userPreferencesManager = userPreferencesManager;
    }

    public String formatWithDuration(final Long ms) {
        if (ms == null) {
            return null;
        }

        final long now = System.currentTimeMillis();
        return format(ms) +
                " (" +
                ClientDurationUtil.humanise(ms - now, true) +
                ")";
    }

    public String format(final Long ms) {
        if (ms == null) {
            return null;
        }

        Use use = Use.UTC;
        String pattern = "YYYY-MM-DD[T]HH:mm:ss.SSSZ";
        int offsetMinutes = 0;
        String zoneId = "UTC";

        final UserPreferences userPreferences = userPreferencesManager.getCurrentUserPreferences();
        if (userPreferences != null) {
            if (userPreferences.getDateTimePattern() != null &&
                    userPreferences.getDateTimePattern().trim().length() > 0) {

                final TimeZone timeZone = userPreferences.getTimeZone();
                if (timeZone != null) {
                    if (timeZone.getUse() != null) {
                        use = timeZone.getUse();
                    }

                    if (timeZone.getOffsetHours() != null) {
                        offsetMinutes += timeZone.getOffsetHours() * 60;
                    }
                    if (timeZone.getOffsetMinutes() != null) {
                        offsetMinutes += timeZone.getOffsetMinutes();
                    }

                    zoneId = timeZone.getId();
                }

                pattern = userPreferences.getDateTimePattern();
                pattern = convertJavaDateTimePattern(pattern);
            }
        }

        // If UTC then just display the `Z` suffix.
        if (Use.UTC.equals(use)) {
            pattern = pattern.replaceAll("Z", "[Z]");
        }
        // Ensure we haven't doubled up square brackets.
        pattern = pattern.replaceAll("\\[+", "[");
        pattern = pattern.replaceAll("]+", "]");

        return nativeToDateString(ms, use.getDisplayValue(), pattern, zoneId, offsetMinutes);
    }

    String convertJavaDateTimePattern(final String pattern) {
        String converted = pattern;
        converted = converted.replace('y', 'Y');
        converted = converted.replace('d', 'D');
        converted = converted.replaceAll("'", "");
        converted = converted.replaceAll("SSSXX", "SSSZ");
        converted = converted.replaceAll("T", "[T]");
        converted = converted.replaceAll("xxx", "Z");
        converted = converted.replaceAll("xx", "z");
        converted = converted.replaceAll("VV", "ZZ");

        // Deal with day name formatting.
        converted = converted.replaceAll("E{2,}", "dddd");
        converted = converted.replaceAll("E", "ddd");
        converted = converted.replaceAll("e{4,}", "dddd");
        converted = converted.replaceAll("e{3,}", "ddd");
        converted = converted.replaceAll("e+", "d");
        converted = converted.replaceAll("c{4,}", "dddd");
        converted = converted.replaceAll("c{3,}", "ddd");
        converted = converted.replaceAll("c+", "d");


        return converted;
    }

    private static native String nativeToDateString(final double ms,
                                                    final String use,
                                                    final String dateTimePattern,
                                                    final String id,
                                                    final Integer offsetMinutes)/*-{
        var m = $wnd.moment.utc(ms);
        switch (use) {
            case "UTC": {
                m = m.utc();
                return m.format(dateTimePattern);
            }
            case "Local": {
                m = m.local();
                return m.format(dateTimePattern);
            }
            case "Offset": {
                m = m.utcOffset(offsetMinutes);
                return m.format(dateTimePattern);
            }
            case "Id": {
                m = m.tz(id);
                return m.format(dateTimePattern);
            }
        }
        return m.format(dateTimePattern);
    }-*/;
}
