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
        String offset = "0000";
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

                    if (timeZone.getOffsetHours() == null) {
                        offset += "00";
                    } else {
                        if (timeZone.getOffsetHours() < 0) {
                            offset = "-";
                        }
                        if (Math.abs(timeZone.getOffsetHours()) < 10) {
                            offset += "0";
                        }
                        offset += Math.abs(timeZone.getOffsetHours());
                    }
                    if (timeZone.getOffsetMinutes() == null) {
                        offset += "00";
                    } else {
                        if (Math.abs(timeZone.getOffsetMinutes()) < 10) {
                            offset += "0";
                        }
                        offset += Math.abs(timeZone.getOffsetMinutes());
                    }

                    zoneId = timeZone.getId();
                }
            }
        }

        return nativeToDateString(ms, use.getDisplayValue(), pattern, zoneId, offset);
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
                                                    final String offset)/*-{
        var m = $wnd.moment(ms);
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
                m = m.utcOffset(offset);
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
