package stroom.job.client.view;

import stroom.expression.api.UserTimeZone;
import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.client.ClientStringUtil;
import stroom.widget.datepicker.client.IntlDateTimeFormat;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Day;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Hour;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Minute;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Month;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Second;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.TimeZoneName;
import stroom.widget.datepicker.client.IntlDateTimeFormat.FormatOptions.Year;
import stroom.widget.datepicker.client.UTCDate;

import com.google.inject.Inject;

public class DateTimeModel {

    private static final String[] EN_LOCALE = {"en-GB"};

    public static final long MILLIS_IN_SECOND = 1000;
    public static final long MILLIS_IN_MINUTE = 60 * MILLIS_IN_SECOND;
    public static final long MILLIS_IN_HOUR = 60 * MILLIS_IN_MINUTE;

    private final UserPreferencesManager userPreferencesManager;

    @Inject
    public DateTimeModel(final UserPreferencesManager userPreferencesManager) {
        this.userPreferencesManager = userPreferencesManager;
    }

    public String formatDateLabel(final UTCDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .year(Year.NUMERIC)
                .month(Month.LONG)
                .day(Day.NUMERIC);
        setTimeZone(builder);

        return IntlDateTimeFormat.format(value, IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());
    }

    public String formatTimeLabel(final UTCDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .hour(Hour.TWO_DIGIT)
                .minute(Minute.TWO_DIGIT)
                .second(Second.TWO_DIGIT)
                .fractionalSecondDigits(3)
                .timeZoneName(TimeZoneName.SHORT);
        setTimeZone(builder);
        return IntlDateTimeFormat.format(value, IntlDateTimeFormat.DEFAULT_LOCALE, builder.build());
    }

    public String formatIso(final UTCDate value) {
        final long offsetMillis = getOffsetMillis(value);
        final UTCDate adjusted = UTCDate.create(value.getTime());
        adjusted.setTime(value.getTime() + offsetMillis);
        final StringBuilder sb = new StringBuilder();
        sb.append(ClientStringUtil.zeroPad(4, adjusted.getFullYear()));
        sb.append("-");
        sb.append(ClientStringUtil.zeroPad(2, adjusted.getMonth() + 1));
        sb.append("-");
        sb.append(ClientStringUtil.zeroPad(2, adjusted.getDate()));
        sb.append("T");
        sb.append(ClientStringUtil.zeroPad(2, adjusted.getHours()));
        sb.append(":");
        sb.append(ClientStringUtil.zeroPad(2, adjusted.getMinutes()));
        sb.append(":");
        sb.append(ClientStringUtil.zeroPad(2, adjusted.getSeconds()));
        sb.append(".");
        sb.append(ClientStringUtil.zeroPad(3, adjusted.getMilliseconds()));
        if (offsetMillis == 0) {
            sb.append("Z");
        } else {
            sb.append(getOffsetString(value));
        }
        return sb.toString();
    }

    public String getOffsetString(final UTCDate value) {
        final String tz = parseTimeZone(value);
        String offset = tz.replaceAll("GMT", "");
        offset = offset.replaceAll(":", "");
        if (offset.length() == 0) {
            offset = "+0000";
        }
        return offset;
    }

    public TimeOffset getOffset(final UTCDate value) {
        final String offsetString = getOffsetString(value);
        int hours = ClientStringUtil.getInt(offsetString.substring(1, 3));
        int minutes = ClientStringUtil.getInt(offsetString.substring(3, 5));
        if (offsetString.charAt(0) == '-') {
            hours = hours * -1;
            minutes = minutes * -1;
        }
        return new TimeOffset(hours, minutes);
    }

    public long getOffsetMillis(final UTCDate value) {
        final String offsetString = getOffsetString(value);
        int hours = ClientStringUtil.getInt(offsetString.substring(1, 3));
        int minutes = ClientStringUtil.getInt(offsetString.substring(3, 5));
        long millis = (hours * MILLIS_IN_HOUR) + (minutes * MILLIS_IN_MINUTE);
        if (offsetString.charAt(0) == '-') {
            millis = millis * -1;
        }
        return millis;
    }

    public String parseTimeZone(final UTCDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .hour(Hour.TWO_DIGIT)
                .minute(Minute.TWO_DIGIT)
                .second(Second.TWO_DIGIT)
                .fractionalSecondDigits(3)
                .timeZoneName(TimeZoneName.LONG_OFFSET);
        setTimeZone(builder);

        final String dateTimeString = IntlDateTimeFormat.format(value, EN_LOCALE, builder.build());
        final int index = dateTimeString.indexOf(" ");
        return dateTimeString.substring(index + 1);
    }

    private void setTimeZone(final FormatOptions.Builder builder) {
        final String timeZone = getTimeZone();
        if (timeZone != null) {
            builder.timeZone(timeZone);
        }
    }

    public DateRecord parseDate(final UTCDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .year(Year.NUMERIC)
                .month(Month.TWO_DIGIT)
                .day(Day.TWO_DIGIT);
        setTimeZone(builder);

        final String dateString = IntlDateTimeFormat
                .format(value, EN_LOCALE, builder.build());

        final String[] dateParts = dateString.split("/");
        final int day = ClientStringUtil.getInt(dateParts[0]);
        final int month = ClientStringUtil.getInt(dateParts[1]) - 1;
        final int year = ClientStringUtil.getInt(dateParts[2]);
        return new DateRecord(year, month, day);
    }

    public TimeRecord parseTime(final UTCDate value) {
        final FormatOptions.Builder builder = FormatOptions
                .builder()
                .hour(Hour.TWO_DIGIT)
                .minute(Minute.TWO_DIGIT)
                .second(Second.TWO_DIGIT)
                .fractionalSecondDigits(3)
                .timeZoneName(TimeZoneName.SHORT);
        setTimeZone(builder);

        String timeString = IntlDateTimeFormat
                .format(value, EN_LOCALE, builder.build());
        final int timeZoneIndex = timeString.indexOf(" ");
        if (timeZoneIndex != -1) {
            timeString = timeString.substring(0, timeZoneIndex);
        }
        final String[] parts = timeString.split(":");
        final String[] secondParts = parts[2].split("\\.");
        final int hour = ClientStringUtil.getInt(parts[0]);
        final int minute = ClientStringUtil.getInt(parts[1]);
        final int second = ClientStringUtil.getInt(secondParts[0]);
        final int millisecond = ClientStringUtil.getInt(secondParts[1]);
        return new TimeRecord(hour, minute, second, millisecond);
    }

    public String getTimeZone() {
        final UserPreferences userPreferences = userPreferencesManager.getCurrentUserPreferences();
        final UserTimeZone userTimeZone = userPreferences.getTimeZone();
        String timeZone = null;
        switch (userTimeZone.getUse()) {
            case UTC: {
                timeZone = "GMT";
                break;
            }
            case ID: {
                timeZone = userTimeZone.getId();
                break;
            }
            case OFFSET: {
                final String hours = ClientStringUtil.zeroPad(2, userTimeZone.getOffsetHours());
                final String minutes = ClientStringUtil.zeroPad(2, userTimeZone.getOffsetMinutes());
                String offset = hours + minutes;
                if (userTimeZone.getOffsetHours() >= 0 && userTimeZone.getOffsetMinutes() >= 0) {
                    offset = "+" + offset;
                } else {
                    offset = "-" + offset;
                }

                timeZone = "GMT" + offset;
                break;
            }
        }
        return timeZone;
    }
}
