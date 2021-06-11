package stroom.preferences.client;

import stroom.query.api.v2.TimeZone;
import stroom.query.api.v2.TimeZone.Use;
import stroom.ui.config.shared.UserPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DateTimeFormatter {

    private final PreferencesManager preferencesManager;

    @Inject
    public DateTimeFormatter(final PreferencesManager preferencesManager) {
        this.preferencesManager = preferencesManager;
    }

    public String format(final Long ms) {
        if (ms == null) {
            return null;
        }

        Use use = Use.UTC;
        String pattern = "YYYY-MM-DDTHH:mm:ss.SSS[Z]";
        String offset = "0000";
        String zoneId = "UTC";

        final UserPreferences userPreferences = preferencesManager.getCurrentPreferences();
        if (userPreferences != null) {
            if (userPreferences.getDateTimePattern() != null
                    && userPreferences.getDateTimePattern().trim().length() > 0) {
                pattern = userPreferences.getDateTimePattern();

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

    private static native String nativeToDateString(final double ms,
                                                    final String use,
                                                    final String dateTimePattern,
                                                    final String id,
                                                    final String offset)/*-{
        var moment = $wnd.moment(ms);
        var m = moment.utc(ms);
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
