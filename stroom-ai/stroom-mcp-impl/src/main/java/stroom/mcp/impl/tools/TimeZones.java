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

package stroom.mcp.impl.tools;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/// The zone a source writes its times in, guessed for the user to confirm: a source that sends its logs as it writes
/// them has its latest time close to when Stroom received the stream, and where it writes local time, the gap is
/// its offset (a time ten hours ahead of its receipt is written at +10:00). Only offered, as a guess: a sample sent
/// long after its events, or a source whose clock is wrong, says nothing.
final class TimeZones {

    // The most an offset is (UTC-12:00 to UTC+14:00), with an hour for the logs' delay in reaching Stroom.
    private static final Duration MOST = Duration.ofHours(15);
    private static final long QUARTER_HOUR = 15;

    private TimeZones() {
        // Static only.
    }

    /// An offset guessed for the zone of times read with no zone.
    ///
    /// @param label  What the user is shown.
    /// @param offset The offset, e.g. `+10:00`.
    record Guess(String label, String offset) {

    }

    /// @param entries  TimeCreated entries read with no zone.
    /// @param examples Some values of each field read, by field.
    /// @param received The sample streams' ReceivedTime headers.
    /// @return The offset the latest time's gap from the stream's receipt suggests; empty when none can be read, or
    /// the gap is no offset (more than 15 hours, or none).
    static Optional<Guess> guess(final List<Map<String, Object>> entries,
                                 final Map<String, List<String>> examples,
                                 final List<String> received) {
        if (received.isEmpty()) {
            return Optional.empty();
        }
        final Instant receipt;
        try {
            receipt = Instant.parse(received.getFirst());
        } catch (final DateTimeParseException e) {
            return Optional.empty();
        }
        LocalDateTime latest = null;
        for (final Map<String, Object> entry : entries) {
            final List<String> values = fields(entry).stream()
                    .flatMap(field -> examples.getOrDefault(field, List.of()).stream())
                    .toList();
            for (final String value : values) {
                final LocalDateTime read = read(value, String.valueOf(entry.get("time_format")));
                if (read != null && (latest == null || read.isAfter(latest))) {
                    latest = read;
                }
            }
        }
        if (latest == null) {
            return Optional.empty();
        }
        final Duration ahead = Duration.between(receipt, latest.toInstant(ZoneOffset.UTC));
        final long minutes = Math.round(ahead.toMinutes() / (double) QUARTER_HOUR) * QUARTER_HOUR;
        if (minutes == 0 || ahead.abs().compareTo(MOST) > 0) {
            return Optional.empty();
        }
        final String offset = ZoneOffset.ofTotalSeconds((int) minutes * 60).getId();
        final long hours = Math.abs(minutes) / 60;
        final String gap = hours > 0
                ? "about " + hours + " hour" + (hours == 1
                ? ""
                : "s")
                : "about " + Math.abs(minutes) + " minutes";
        return Optional.of(new Guess(offset + " (a guess: the sample's latest time is " + gap + " "
                                     + (minutes > 0
                ? "ahead of"
                : "behind") + " when Stroom received it)", offset));
    }

    /// @param entry A TimeCreated entry.
    /// @return What it reads: its field, its fields in turn (any_of: a record's own time, then its container's),
    /// else its xpath.
    static String readFrom(final Map<String, Object> entry) {
        final List<String> fields = fields(entry);
        return fields.isEmpty()
                ? String.valueOf(entry.get("xpath"))
                : String.join("' or '", fields);
    }

    // The fields an entry reads: its field, or each of its any_of.
    private static List<String> fields(final Map<String, Object> entry) {
        if (entry.get("field") != null) {
            return List.of(String.valueOf(entry.get("field")));
        }
        if (entry.get("any_of") instanceof final List<?> anyOf) {
            return anyOf.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    // A value read with a pattern as a time with no zone; null when it doesn't read.
    private static LocalDateTime read(final String value, final String pattern) {
        try {
            return LocalDateTime.parse(value.strip(), DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
        } catch (final DateTimeParseException | IllegalArgumentException e) {
            return null;
        }
    }
}
