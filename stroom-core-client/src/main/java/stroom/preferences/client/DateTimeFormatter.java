package stroom.preferences.client;

import stroom.query.api.v2.TimeZone;
import stroom.query.api.v2.TimeZone.Use;
import stroom.ui.config.shared.UserPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DateTimeFormatter {

    private final UserPreferencesManager userPreferencesManager;

    @Inject
    public DateTimeFormatter(final UserPreferencesManager userPreferencesManager) {
        this.userPreferencesManager = userPreferencesManager;
    }

    public String format(final Long ms) {
        if (ms == null) {
            return null;
        }

        Use use = Use.UTC;
        String pattern = "YYYY-MM-DDTHH:mm:ss.SSS[Z]";
        int offsetMinutes = 0;
        String zoneId = "UTC";

        final UserPreferences userPreferences = userPreferencesManager.getCurrentPreferences();
        if (userPreferences != null) {
            if (userPreferences.getDateTimePattern() != null
                    && userPreferences.getDateTimePattern().trim().length() > 0) {
                pattern = userPreferences.getDateTimePattern();
                pattern = convertJavaDateTimePattern(pattern);

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
            }
        }

        return nativeToDateString(ms, use.getDisplayValue(), pattern, zoneId, offsetMinutes);
    }

    private String convertJavaDateTimePattern(final String pattern) {
        String converted = pattern;
        converted = converted.replace('y', 'Y');
        converted = converted.replace('d', 'D');
        converted = converted.replaceAll("'", "");
        converted = converted.replaceAll("SSSXX", "SSS[Z]");
        converted = converted.replaceAll("xxx", "Z");
        converted = converted.replaceAll("xx", "z");
        converted = converted.replaceAll("VV", "ZZ");
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
