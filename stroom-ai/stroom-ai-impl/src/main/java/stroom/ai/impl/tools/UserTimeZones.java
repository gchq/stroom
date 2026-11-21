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
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

/// Resolves the time zone of a tool call that has times in it (run_stroomql, sequence_query, esql_query,
/// get_current_datetime): the zone the call gives (`timeZone`), else the user's Stroom time zone preference, as
/// the Stroom UI applies it. The tools run as the user (the chat's, or the MCP token's), so the preference is theirs.
///
/// A zone is given as `local`, `UTC` or `GMT`, a zone id (e.g. `Australia/Canberra`) or an offset (e.g. `+11:00`).
/// `local`, and a preference to use the browser's zone, mean the user's browser's zone, which the Stroom AI chat
/// sends with each question; where it isn't known (over MCP), `local` is the preference's zone if that names one,
/// and otherwise UTC, with a note saying so.
@Singleton
public class UserTimeZones {

    /// The argument the tools take.
    public static final String ARG = "timeZone";
    /// The argument's description, kept short as it is paid for on every call.
    public static final String DESCRIPTION = "Time zone for the times and results: local, UTC, a zone id or an "
                                             + "offset; default the user's.";
    /// The zone a tool uses when no user preference can be read (e.g. in a unit test): UTC.
    public static final UserTimeZones UTC_ONLY = new UserTimeZones(UserTimeZonePreference.NONE);

    static final String LOCAL = "local";
    static final String BROWSER_NOT_KNOWN = "The user's Stroom time zone is their browser's, which isn't known "
                                            + "here, so times are in UTC: give timeZone to use another.";

    private final UserTimeZonePreference preference;

    /// @param preference The current user's Stroom time zone preference.
    @Inject
    public UserTimeZones(final UserTimeZonePreference preference) {
        this.preference = preference;
    }

    /// @param given   The zone the call gives (`timeZone`), or null/blank for the user's.
    /// @param context The call's context, with the user's browser's zone if the session's client sent it.
    /// @return The call's zone.
    /// @throws ToolRefusal for a zone that isn't one, saying what to give.
    public ToolTimeZone resolve(final String given, final ToolCallContext context) {
        final String text = NullSafe.trim(given);
        if (text.isEmpty()) {
            return userZone(context);
        }
        final String lower = text.toLowerCase(Locale.ROOT);
        if (LOCAL.equals(lower)) {
            return local(context);
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

    // The user's zone: their preference, the browser's if that is what it says to use.
    private ToolTimeZone userZone(final ToolCallContext context) {
        final UserTimeZone userTimeZone = preference.current().orElse(null);
        if (userTimeZone == null || userTimeZone.getUse() == null || userTimeZone.getUse() == Use.UTC) {
            return new ToolTimeZone(ZoneOffset.UTC, Source.PREFERENCE, null);
        } else if (userTimeZone.getUse() == Use.LOCAL) {
            return browser(context).orElseGet(() -> new ToolTimeZone(ZoneOffset.UTC, Source.FALLBACK,
                    BROWSER_NOT_KNOWN));
        }
        return named(userTimeZone)
                .map(zone -> new ToolTimeZone(zone, Source.PREFERENCE, null))
                .orElseGet(() -> new ToolTimeZone(ZoneOffset.UTC, Source.FALLBACK, "The user's Stroom time zone "
                                                                                   + "can't be read, so times are "
                                                                                   + "in UTC."));
    }

    // 'local': the browser's zone if known, else the preference's.
    private ToolTimeZone local(final ToolCallContext context) {
        return browser(context).orElseGet(() -> userZone(context));
    }

    private static Optional<ToolTimeZone> browser(final ToolCallContext context) {
        return context.localZoneId()
                .flatMap(UserTimeZones::parse)
                .map(zone -> new ToolTimeZone(zone, Source.BROWSER, null));
    }

    // A preference that names a zone (an id or an offset).
    private static Optional<ZoneId> named(final UserTimeZone userTimeZone) {
        if (userTimeZone.getUse() == Use.ID) {
            return NullSafe.isBlankString(userTimeZone.getId())
                    ? Optional.empty()
                    : parse(userTimeZone.getId());
        }
        try {
            return Optional.of(ZoneOffset.ofHoursMinutes(NullSafe.getInt(userTimeZone.getOffsetHours()),
                    NullSafe.getInt(userTimeZone.getOffsetMinutes())));
        } catch (final DateTimeException e) {
            return Optional.empty();
        }
    }
}
