/*
 * Copyright 2025 Crown Copyright
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

package stroom.query.language.functions;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.UserTimeZone;
import stroom.util.shared.NullSafe;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.regex.Pattern;

public class UserTimeZoneUtil {

    // The only names of time zones with no region that are not refused. They all mean the same to everybody.
    private static final Set<String> UTC_NAMES = Set.of("UTC", "GMT", "UT", "Z");
    private static final Pattern OFFSET_PATTERN = Pattern.compile("(?:UTC|GMT|UT)?[+-].*");

    public static ZoneId getZoneId(final DateTimeSettings dateTimeSettings) {
        if (dateTimeSettings == null) {
            return ZoneOffset.UTC;
        }
        return getZoneId(dateTimeSettings.getTimeZone(), dateTimeSettings.getLocalZoneId());
    }

    public static ZoneId getZoneId(final UserTimeZone userTimeZone,
                                   final String localZoneId) {
        if (userTimeZone != null) {
            if (UserTimeZone.Use.UTC.equals(userTimeZone.getUse())) {
                return ZoneOffset.UTC;
            } else if (UserTimeZone.Use.LOCAL.equals(userTimeZone.getUse())) {
                if (localZoneId != null) {
                    try {
                        return ZoneId.of(localZoneId);
                    } catch (final RuntimeException e) {
                        // Ignore.
                    }
                }
                return ZoneId.systemDefault();

            } else if (UserTimeZone.Use.ID.equals(userTimeZone.getUse())) {
                return getChosenZoneId(userTimeZone.getId());
            } else if (UserTimeZone.Use.OFFSET.equals(userTimeZone.getUse())) {
                return ZoneOffset.ofHoursMinutes(
                        NullSafe.getInt(userTimeZone.getOffsetHours()),
                        NullSafe.getInt(userTimeZone.getOffsetMinutes()));
            }
        }
        return ZoneOffset.UTC;
    }

    /// Gets the time zone that the functions of an expression work in unless they are given one.
    ///
    /// @param expressionContext The context of the expression, or `null`.
    /// @return The time zone of the query, or UTC if it has none.
    public static ZoneId getZoneId(final ExpressionContext expressionContext) {
        return getZoneId(NullSafe.get(expressionContext, ExpressionContext::getDateTimeSettings));
    }

    /// Gets a time zone that a function is given.
    ///
    /// @param val The name or offset of the time zone.
    /// @return The time zone.
    /// @throws DateTimeException If there is no name or it can't be used.
    static ZoneId getZoneId(final Val val) {
        return getZoneId(val.type().isValue()
                ? val.toString()
                : null);
    }

    /// Gets a time zone from its name, which must have a region, such as `Europe/London`, or be an offset or UTC.
    ///
    /// Short names such as `BST` and `EST` are refused, as they mean different things to different people. Java
    /// thinks that `BST` is the time in Bangladesh.
    ///
    /// @param name The name or offset of the time zone.
    /// @return The time zone.
    /// @throws DateTimeException If there is no name or it can't be used.
    static ZoneId getZoneId(final String name) {
        if (NullSafe.isBlankString(name)) {
            throw new DateTimeException("There is no time zone");
        }
        final String trimmed = name.trim();
        if (!isRegion(trimmed) && !UTC_NAMES.contains(trimmed) && !OFFSET_PATTERN.matcher(trimmed).matches()) {
            throw unknown(trimmed, null);
        }
        return parse(trimmed);
    }

    /// Tells whether the name of a time zone has a region, such as `Europe/London`.
    ///
    /// @param name The name.
    /// @return True if it does.
    public static boolean isRegion(final String name) {
        return name.contains("/");
    }

    // A time zone that has been chosen from a list. The lists used to have short names, so any that Java knows is still
    // used if it was chosen then.
    private static ZoneId getChosenZoneId(final String name) {
        if (NullSafe.isBlankString(name)) {
            throw new DateTimeException("There is no time zone");
        }
        return parse(name);
    }

    private static ZoneId parse(final String name) {
        try {
            return ZoneId.of(name);
        } catch (final DateTimeException e) {
            throw unknown(name, e);
        }
    }

    private static DateTimeException unknown(final String name, final DateTimeException cause) {
        return new DateTimeException("Unknown time zone '" + name + "'. Use a name such as Europe/London", cause);
    }
}
