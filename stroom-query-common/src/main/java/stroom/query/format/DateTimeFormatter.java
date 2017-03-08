/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.format;

import stroom.dashboard.expression.DateUtil;
import stroom.dashboard.expression.TypeConverter;
import stroom.query.api.DateTimeFormat;
import stroom.query.api.TimeZone;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class DateTimeFormatter implements Formatter {
    private final java.time.format.DateTimeFormatter format;
    private final ZoneId zone;

    private DateTimeFormatter(final java.time.format.DateTimeFormatter format, final ZoneId zone) {
        this.format = format;
        this.zone = zone;
    }

    public static DateTimeFormatter create(final DateTimeFormat dateTimeFormat, final String dateTimeLocale) {
        java.time.format.DateTimeFormatter format = null;
        ZoneId zone = ZoneOffset.UTC;

        if (dateTimeFormat != null) {
            String pattern = dateTimeFormat.getPattern();
            if (pattern != null && pattern.trim().length() > 0) {
                final TimeZone timeZone = dateTimeFormat.getTimeZone();

                if (timeZone != null) {
                    if (TimeZone.Use.UTC.equals(timeZone.getUse())) {
                        zone = ZoneOffset.UTC;
                    } else if (TimeZone.Use.LOCAL.equals(timeZone.getUse())) {
                        zone = ZoneId.systemDefault();

                        try {
                            if (dateTimeLocale != null) {
                                zone = ZoneId.of(dateTimeLocale);
                            }
                        } catch (final IllegalArgumentException e) {
                            // The client time zone was not recognised so we'll
                            // use the default.
                        }

                    } else if (TimeZone.Use.ID.equals(timeZone.getUse())) {
                        zone = ZoneId.of(timeZone.getId());
                    } else if (TimeZone.Use.OFFSET.equals(timeZone.getUse())) {
                        zone = ZoneOffset.ofHoursMinutes(getInt(timeZone.getOffsetHours()),
                                getInt(timeZone.getOffsetMinutes()));
                    }
                }

                format = java.time.format.DateTimeFormatter.ofPattern(pattern);
            }
        }

        return new DateTimeFormatter(format, zone);
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

            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone).format(format);
        }
        return value.toString();
    }
}
