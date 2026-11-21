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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.UserTimeZone;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/// The time zone a tool call's times are in ([UserTimeZones] resolves it): what `day()` and a time without an
/// offset mean in a query, and the zone its result's times are shown in. Every result that has times says which
/// zone they are in ([#stated()]), so the agent and the user can notice a mismatch.
///
/// @param zone   The zone.
/// @param source Where it came from.
/// @param note   Why it isn't the zone asked for (e.g. the user's device's zone isn't known over MCP), or null.
public record ToolTimeZone(ZoneId zone, Source source, String note) {

    /// UTC, as given by the caller.
    public static final ToolTimeZone UTC = new ToolTimeZone(ZoneOffset.UTC, Source.GIVEN, null);

    /// How a query's results give their times: ISO 8601 with the zone's offset (`Z` in UTC), as Stroom writes them
    /// in UTC. Not the user's own date pattern, which may not be one the agent and the table tool can read.
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    private static final DateTimeFormatter ISO_SECONDS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter IN_ZONE = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN, Locale.ROOT);
    // A time as Stroom writes a date field's value in UTC, e.g. 2026-11-20T14:00:00.000Z.
    private static final Pattern STROOM_UTC_TIME = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");

    /// @param zone   The zone.
    /// @param source Where it came from.
    /// @param note   Why it isn't the zone asked for, or null.
    public ToolTimeZone {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(source, "source");
        zone = zone.normalized() instanceof final ZoneOffset offset && offset.getTotalSeconds() == 0
                ? ZoneOffset.UTC
                : zone;
    }

    /// @return True if the zone is UTC.
    public boolean isUtc() {
        return ZoneOffset.UTC.equals(zone);
    }

    /// @return The zone's id: `UTC`, a region (e.g. `Australia/Canberra`) or an offset (e.g. `+11:00`).
    public String id() {
        return isUtc()
                ? "UTC"
                : zone.getId();
    }

    /// @return The zone's offset from UTC now, e.g. `+11:00`.
    public String offsetNow() {
        final ZoneOffset offset = zone.getRules().getOffset(Instant.now());
        return offset.getTotalSeconds() == 0
                ? "+00:00"
                : offset.getId();
    }

    /// @return The zone as shown: `UTC`, an offset, or a region with its offset now, e.g.
    /// `Australia/Canberra (+11:00)`.
    public String label() {
        if (isUtc()) {
            return "UTC";
        }
        return zone instanceof ZoneOffset
                ? zone.getId()
                : zone.getId() + " (" + offsetNow() + ")";
    }

    /// @return What a result says of its times, e.g. "Times are in Australia/Canberra (+11:00), the user's
    /// device's time zone." with the note, if any, after it.
    public String stated() {
        final String said = "Times are in " + label() + source.said + ".";
        return note == null
                ? said
                : said + " " + note;
    }

    /// @return The zone as a JSON result gives it: `time_zone` (its id), `utc_offset` (now), `time_zone_source`,
    /// and `time_zone_note` if there is one.
    public Map<String, Object> structured() {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("time_zone", id());
        map.put("utc_offset", offsetNow());
        map.put("time_zone_source", source.name().toLowerCase(Locale.ROOT));
        if (note != null) {
            map.put("time_zone_note", note);
        }
        return map;
    }

    /// @param referenceTime What `now()` is, in epoch milliseconds.
    /// @return Query date and time settings in the zone, as the Stroom UI sends them for the user's preference, with
    /// results' times in ISO 8601 ([#DATE_TIME_PATTERN]).
    public DateTimeSettings dateTimeSettings(final long referenceTime) {
        return DateTimeSettings.builder()
                .dateTimePattern(DATE_TIME_PATTERN)
                .timeZone(userTimeZone())
                .referenceTime(referenceTime)
                .build();
    }

    /// @return The zone as a Stroom user time zone setting.
    public UserTimeZone userTimeZone() {
        if (isUtc()) {
            return UserTimeZone.utc();
        } else if (zone instanceof final ZoneOffset offset) {
            final int totalMinutes = offset.getTotalSeconds() / 60;
            return UserTimeZone.fromOffset(totalMinutes / 60, totalMinutes % 60);
        }
        return UserTimeZone.fromId(zone.getId());
    }

    /// @param cell A result's cell.
    /// @return A time as Stroom writes a date in UTC (e.g. `2026-11-20T14:00:00.000Z`, as an unformatted StroomQL
    /// column gives a date field's value, whatever the query's zone) as the same time in the zone (e.g.
    /// `2026-11-21T01:00:00.000+11:00`); anything else as it is.
    public String inZone(final String cell) {
        if (isUtc() || cell == null || !STROOM_UTC_TIME.matcher(cell).matches()) {
            return cell;
        }
        return IN_ZONE.format(Instant.parse(cell).atZone(zone));
    }

    /// @param instant A time.
    /// @return It in the zone, to the second if it has no fraction, e.g. `2026-11-21T10:42:00+11:00`.
    public String format(final Instant instant) {
        final ZonedDateTime time = instant.atZone(zone);
        return ISO_SECONDS.format(instant.getNano() == 0
                ? time
                : time.truncatedTo(ChronoUnit.MILLIS));
    }


    // --------------------------------------------------------------------------------


    /// Where a tool call's time zone came from.
    public enum Source {
        /// The call gave it (`timeZone`).
        GIVEN(""),
        /// The user's device's zone, as the chat's browser sends it.
        BROWSER(", the user's device's time zone"),
        /// UTC, as the user's device's zone isn't known (see the note).
        FALLBACK("");

        private final String said;

        Source(final String said) {
            this.said = said;
        }
    }
}
