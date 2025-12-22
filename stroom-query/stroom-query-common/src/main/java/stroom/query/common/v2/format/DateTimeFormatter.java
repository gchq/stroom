/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.common.v2.format;

import stroom.query.api.DateTimeFormatSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.language.functions.DateUtil;
import stroom.query.language.functions.UserTimeZoneUtil;
import stroom.query.language.functions.Val;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class DateTimeFormatter implements Formatter {

    private final java.time.format.DateTimeFormatter format;
    private final ZoneId zone;

    private DateTimeFormatter(final java.time.format.DateTimeFormatter format,
                              final ZoneId zone) {
        this.format = format;
        this.zone = zone;
    }

    public static DateTimeFormatter create(final DateTimeFormatSettings dateTimeFormat,
                                           final DateTimeSettings dateTimeSettings) {
        java.time.format.DateTimeFormatter format = null;

        String pattern = null;
        UserTimeZone timeZone = null;
        String localZoneId = null;
        if (dateTimeSettings != null) {
            pattern = dateTimeSettings.getDateTimePattern();
            timeZone = dateTimeSettings.getTimeZone();
            localZoneId = dateTimeSettings.getLocalZoneId();
        }

        if (dateTimeFormat != null && !dateTimeFormat.isUsePreferences()) {
            pattern = NullSafe.getOrElse(dateTimeFormat, DateTimeFormatSettings::getPattern, pattern);
            timeZone = NullSafe.getOrElse(dateTimeFormat, DateTimeFormatSettings::getTimeZone, timeZone);
        }
        final ZoneId zoneId = UserTimeZoneUtil.getZoneId(timeZone, localZoneId);

        if (!NullSafe.isBlankString(pattern)) {
            format = java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
        }
        return new DateTimeFormatter(format, zoneId);
    }

    public LocalDateTime parse(final String value) throws DateTimeParseException {
        return NullSafe.get(
                value,
                val -> DateUtil.parseLocal(val, format, zone));
    }

    @Override
    public String format(final Val value) {
        if (value == null) {
            return null;
        } else {
            final Long millis = value.toLong();
            if (millis != null) {
                if (format == null) {
                    return DateUtil.createNormalDateTimeString(millis);
                } else {
                    return format.format(Instant.ofEpochMilli(millis).atZone(zone));
                }
            } else {
                return value.toString();
            }
        }
    }
}
