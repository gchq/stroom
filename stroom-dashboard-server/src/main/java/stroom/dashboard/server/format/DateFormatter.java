/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.server.format;

import stroom.dashboard.expression.v1.TypeConverter;
import stroom.dashboard.shared.DateTimeFormatSettings;
import stroom.dashboard.shared.FormatSettings;
import stroom.dashboard.shared.TimeZone;
import stroom.dashboard.shared.TimeZone.Use;
import stroom.util.date.DateUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class DateFormatter implements Formatter {
    private final DateTimeFormatter format;

    private DateFormatter(final DateTimeFormatter format) {
        this.format = format;
    }

    public static DateFormatter create(final FormatSettings settings, final String dateTimeLocale) {
        Use use = Use.LOCAL;
        String pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS";
        int offsetHours = 0;
        int offsetMinutes = 0;
        String zoneId = "UTC";

        if (settings != null && settings instanceof DateTimeFormatSettings) {
            final DateTimeFormatSettings dateTimeFormatSettings = (DateTimeFormatSettings) settings;
            if (dateTimeFormatSettings.getPattern() != null && dateTimeFormatSettings.getPattern().trim().length() > 0) {
                pattern = dateTimeFormatSettings.getPattern();

                final TimeZone timeZone = dateTimeFormatSettings.getTimeZone();
                if (timeZone != null) {
                    if (timeZone.getUse() != null) {
                        use = timeZone.getUse();
                    }

                    offsetHours = getInt(timeZone.getOffsetHours());
                    offsetMinutes = getInt(timeZone.getOffsetMinutes());
                    zoneId = timeZone.getId();
                }
            }
        }

        ZoneId zone = ZoneOffset.UTC;
        if (TimeZone.Use.UTC.equals(use)) {
            zone = ZoneOffset.UTC;
        } else if (TimeZone.Use.LOCAL.equals(use)) {
            zone = ZoneId.systemDefault();

            try {
                if (dateTimeLocale != null) {
                    zone = ZoneId.of(dateTimeLocale);
                }
            } catch (final IllegalArgumentException e) {
                // The client time zone was not recognised so we'll
                // use the default.
            }

        } else if (TimeZone.Use.ID.equals(use)) {
            zone = ZoneId.of(zoneId);
        } else if (TimeZone.Use.OFFSET.equals(use)) {
            zone = ZoneOffset.ofHoursMinutes(offsetHours, offsetMinutes);
        }

        final DateTimeFormatter format = DateTimeFormatter.ofPattern(pattern);
        return new DateFormatter(format);
    }

    private static int getInt(final Integer i) {
        if (i == null) {
            return 0;
        }
        return i;
    }

    @Override
    public String format(final Object value) {
        if (value == null) {
            return null;
        }

        final Double dbl = TypeConverter.getDouble(value);
        if (dbl != null) {
            final long millis = dbl.longValue();

            if (format == null) {
                return DateUtil.createNormalDateTimeString(millis);
            }

            return format.format(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC));
        }
        return value.toString();
    }
}
