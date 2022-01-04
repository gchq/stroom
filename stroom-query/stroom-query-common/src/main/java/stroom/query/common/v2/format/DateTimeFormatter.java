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

package stroom.query.common.v2.format;

import stroom.dashboard.expression.v1.DateUtil;
import stroom.dashboard.expression.v1.Val;
import stroom.query.api.v2.DateTimeFormatSettings;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.TimeZone;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;

public class DateTimeFormatter implements Formatter {

    private final java.time.format.DateTimeFormatter format;
    private final ZoneId zone;

    private DateTimeFormatter(final java.time.format.DateTimeFormatter format, final ZoneId zone) {
        this.format = format;
        this.zone = zone;
    }

    public static DateTimeFormatter create(final DateTimeFormatSettings dateTimeFormat,
                                           final DateTimeSettings defaultDateTimeSettings) {
        java.time.format.DateTimeFormatter format = null;
        ZoneId zone = ZoneOffset.UTC;

        String pattern = null;
        TimeZone timeZone = null;
        if (dateTimeFormat != null && !dateTimeFormat.isUsePreferences()) {
            pattern = dateTimeFormat.getPattern();
            timeZone = dateTimeFormat.getTimeZone();
        } else if (defaultDateTimeSettings != null) {
            pattern = defaultDateTimeSettings.getDateTimePattern();
            timeZone = defaultDateTimeSettings.getTimeZone();
        }

        if (pattern != null && pattern.trim().length() > 0) {
            if (timeZone != null) {
                if (TimeZone.Use.UTC.equals(timeZone.getUse())) {
                    zone = ZoneOffset.UTC;
                } else if (TimeZone.Use.LOCAL.equals(timeZone.getUse())) {
                    zone = ZoneId.systemDefault();

                    try {
                        if (defaultDateTimeSettings != null) {
                            zone = ZoneId.of(defaultDateTimeSettings.getLocalZoneId());
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

        return new DateTimeFormatter(format, zone);
    }

    private static int getInt(final Integer i) {
        if (i == null) {
            return 0;
        }
        return i;
    }

    public LocalDateTime parse(final String value) throws DateTimeParseException {
        if (value == null) {
            return null;
        }

        return DateUtil.parseLocal(value, format, zone);
    }

    @Override
    public String format(final Val value) {
        if (value == null) {
            return null;
        }

        final Long millis = value.toLong();
        if (millis != null) {
            if (format == null) {
                return DateUtil.createNormalDateTimeString(millis);
            }

            return format.format(Instant.ofEpochMilli(millis).atZone(zone));
        }
        return value.toString();
    }
}
