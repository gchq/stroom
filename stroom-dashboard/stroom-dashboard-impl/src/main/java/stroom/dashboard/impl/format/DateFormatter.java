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

package stroom.dashboard.impl.format;

import stroom.query.api.DateTimeFormatSettings;
import stroom.query.api.FormatSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.language.functions.UserTimeZoneUtil;
import stroom.query.language.functions.Val;
import stroom.util.date.DateFormatterCache;
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
        String pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS";
        UserTimeZone userTimeZone = null;

        if (settings instanceof final DateTimeFormatSettings dateTimeFormatSettings) {
            if (dateTimeFormatSettings.getPattern() != null
                && !dateTimeFormatSettings.getPattern().trim().isEmpty()) {
                pattern = dateTimeFormatSettings.getPattern();
            }
            userTimeZone = dateTimeFormatSettings.getTimeZone();
        }

        final ZoneId zoneId = UserTimeZoneUtil.getZoneId(userTimeZone);
        final DateTimeFormatter format = DateFormatterCache.getFormatter(pattern).withZone(zoneId);
        return new DateFormatter(format);
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

            return format.format(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC));
        }
        return value.toString();
    }
}
