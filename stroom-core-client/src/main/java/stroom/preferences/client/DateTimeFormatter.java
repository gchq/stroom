package stroom.preferences.client;

import stroom.expression.api.UserTimeZone;
import stroom.expression.api.UserTimeZone.Use;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.customdatebox.client.MomentJs;

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
                MomentJs.humanise(ms - now, true) +
                ")";
    }

    public String format(final Long ms) {
        if (ms == null) {
            return null;
        }

        Use use = Use.UTC;
        String pattern = "YYYY-MM-DDTHH:mm:ss.SSS[Z]";
        int offsetMinutes = 0;
        String zoneId = "UTC";

        final UserPreferences userPreferences = userPreferencesManager.getCurrentUserPreferences();
        if (userPreferences != null) {
            if (userPreferences.getDateTimePattern() != null &&
                    userPreferences.getDateTimePattern().trim().length() > 0) {
                pattern = userPreferences.getDateTimePattern();
                pattern = convertJavaDateTimePattern(pattern);

                final UserTimeZone timeZone = userPreferences.getTimeZone();
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
            }
        }

        return MomentJs.nativeToDateString(ms, use.getDisplayValue(), pattern, zoneId, offsetMinutes);
    }

    public Long parse(final String dateTime) {
        Long ms = ClientDateUtil.fromISOString(dateTime);
        if (ms == null) {
            String pattern = "YYYY-MM-DDTHH:mm:ss.SSS[Z]";
            final UserPreferences userPreferences = userPreferencesManager.getCurrentUserPreferences();
            if (userPreferences != null) {
                if (userPreferences.getDateTimePattern() != null &&
                        userPreferences.getDateTimePattern().trim().length() > 0) {
                    pattern = userPreferences.getDateTimePattern();
                }
            }
            ms = ClientDateUtil.parseWithJavaFormat(dateTime, pattern).orElse(null);
        }
        return ms;
    }

    String convertJavaDateTimePattern(final String pattern) {
        String converted = pattern;
        converted = converted.replace('y', 'Y');
        converted = converted.replace('d', 'D');
        converted = converted.replaceAll("'", "");
        converted = converted.replaceAll("SSSXX", "SSS[Z]");
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
}
