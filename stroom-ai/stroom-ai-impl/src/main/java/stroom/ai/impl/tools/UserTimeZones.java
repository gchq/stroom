/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.ToolTimeZone.Source;
import stroom.util.shared.NullSafe;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

/// Resolves the time zone of a tool call that has times in it (run_stroomql, sequence_query, esql_query,
/// get_current_datetime): the zone the call gives (`timeZone`), else the user's device's zone, which the Stroom AI
/// chat's browser sends with each question. Where that isn't known (over MCP, whose clients send no zone, or a chat
/// run resumed after a restart before its next question) the zone is UTC, with a note saying so. The user's Stroom
/// time zone preference isn't used (decided by the user, 2026-11-21): Stroom's default for it is UTC.
///
/// A zone is given as `local` (the device's), `UTC` or `GMT`, a zone id (e.g. `Australia/Canberra`) or an offset
/// (e.g. `+11:00`).
public final class UserTimeZones {

    /// The argument the tools take.
    public static final String ARG = "timeZone";
    /// The argument's description, kept short as it is paid for on every call.
    public static final String DESCRIPTION = "Time zone for the times and results: local, UTC, a zone id or an "
                                             + "offset; default the user's.";

    static final String LOCAL = "local";
    static final String DEVICE_NOT_KNOWN = "The user's time zone isn't known here, so times are in UTC: give "
                                           + "timeZone to use another.";

    private UserTimeZones() {
        // Static methods only.
    }

    /// @param given   The zone the call gives (`timeZone`), or null/blank for the user's.
    /// @param context The call's context, with the user's device's zone if the session's client sent it.
    /// @return The call's zone.
    /// @throws ToolRefusal for a zone that isn't one, saying what to give.
    public static ToolTimeZone resolve(final String given, final ToolCallContext context) {
        final String text = NullSafe.trim(given);
        final String lower = text.toLowerCase(Locale.ROOT);
        if (text.isEmpty() || LOCAL.equals(lower)) {
            return device(context);
        } else if ("utc".equals(lower) || "gmt".equals(lower) || "z".equals(lower)) {
            return ToolTimeZone.UTC;
        }
        return new ToolTimeZone(parse(text).orElseThrow(() -> new ToolRefusal(unknown(text))), Source.GIVEN, null);
    }

    /// @param text A zone as the user or agent wrote it.
    /// @return What a call is told for a zone that isn't one.
    static String unknown(final String text) {
        return "unknown time zone '" + text + "': give local, UTC, a zone id such as Europe/London, or an offset "
               + "such as +01:00";
    }

    /// @param text A zone id (in any case) or an offset (e.g. `+11:00`, `+11`, `UTC+11:00`).
    /// @return The zone, or empty if it isn't one.
    static Optional<ZoneId> parse(final String text) {
        try {
            return Optional.of(ZoneId.of(text));
        } catch (final DateTimeException e) {
            // A region written in another case, e.g. australia/canberra.
            return ZoneId.getAvailableZoneIds().stream()
                    .filter(id -> id.equalsIgnoreCase(text))
                    .findFirst()
                    .map(ZoneId::of);
        }
    }

    // The user's device's zone if the client sent it, else UTC, saying so.
    private static ToolTimeZone device(final ToolCallContext context) {
        return context.localZoneId()
                .flatMap(UserTimeZones::parse)
                .map(zone -> new ToolTimeZone(zone, Source.BROWSER, null))
                .orElseGet(() -> new ToolTimeZone(ZoneOffset.UTC, Source.FALLBACK, DEVICE_NOT_KNOWN));
    }
}
