/*
 * Copyright 2016 Crown Copyright
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

import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import stroom.dashboard.expression.TypeConverter;
import stroom.query.shared.DateTimeFormatSettings;
import stroom.query.shared.FormatSettings;
import stroom.query.shared.TimeZone;
import stroom.util.date.DateUtil;

public class DateFormatter implements Formatter {
    private final org.joda.time.format.DateTimeFormatter format;

    private DateFormatter(final org.joda.time.format.DateTimeFormatter format) {
        this.format = format;
    }

    public static DateFormatter create(final FormatSettings settings, final String dateTimeLocale) {
        org.joda.time.format.DateTimeFormatter format = null;

        if (settings != null && settings instanceof DateTimeFormatSettings) {
            final DateTimeFormatSettings dateTimeFormatSettings = (DateTimeFormatSettings) settings;
            String pattern = dateTimeFormatSettings.getPattern();
            if (pattern != null && pattern.trim().length() > 0) {
                final TimeZone timeZone = dateTimeFormatSettings.getTimeZone();
                DateTimeZone zone = DateTimeZone.UTC;

                if (timeZone != null) {
                    if (TimeZone.Use.UTC.equals(timeZone.getUse())) {
                        zone = DateTimeZone.UTC;
                    } else if (TimeZone.Use.LOCAL.equals(timeZone.getUse())) {
                        pattern = pattern.replaceAll("'Z'", "Z");
                        zone = DateTimeZone.getDefault();

                        try {
                            if (dateTimeLocale != null) {
                                zone = DateTimeZone.forID(dateTimeLocale);
                            }
                        } catch (final IllegalArgumentException e) {
                            // The client time zone was not recognised so we'll
                            // use the default.
                        }

                    } else if (TimeZone.Use.ID.equals(timeZone.getUse())) {
                        pattern = pattern.replaceAll("'Z'", "Z");
                        zone = DateTimeZone.forID(timeZone.getId());
                    } else if (TimeZone.Use.OFFSET.equals(timeZone.getUse())) {
                        pattern = pattern.replaceAll("'Z'", "Z");
                        zone = DateTimeZone.forOffsetHoursMinutes(getInt(timeZone.getOffsetHours()),
                                getInt(timeZone.getOffsetMinutes()));
                    }
                }

                format = DateTimeFormat.forPattern(pattern).withZone(zone);
            }
        }

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

            return format.print(millis);
        }
        return value.toString();
    }
}
