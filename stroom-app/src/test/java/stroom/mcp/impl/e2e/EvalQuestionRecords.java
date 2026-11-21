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

package stroom.mcp.impl.e2e;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// A question's records too many to keep in a CSV resource (a `lucene_index`'s or `data_connections` entry's
/// `generate: {kind, ...}` in place of its `events` or `csv`, [EvalCases#table]): made from a seed, the same records
/// every run, so that the answer the case gives (worked out by brute force in `TestEvalCases`) is theirs.
final class EvalQuestionRecords {

    private static final DateTimeFormatter ISO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final long DAY_SECONDS = 24 * 60 * 60;
    private static final List<String> OTHER_ACTIONS = List.of("Logon", "Logon", "Logon", "Logon", "Export",
            "Export", "Export", "Logoff", "Logoff", "Logoff");

    private EvalQuestionRecords() {
        // Static only.
    }

    /// Makes a question's records.
    ///
    /// @param spec The `generate`: its `kind` and the generator's arguments:
    ///             * `staff_activity`: `users` users (`user00001` up), each with one export in the `days` days from
    ///               `export_week` (default 7), then `events` in all, the rest each a random user's logon, export or
    ///               logoff at a random second of the `span` days (default 21) from `from`; an export's `Files` is
    ///               1 to 20, anything else's 0. Columns `EventTime`, `UserId`, `Action`, `Files`, by time.
    ///             * `hr_users`: `users` users (as `staff_activity` names them), each in a department drawn from
    ///               `departments` (each department's name and its weight), each `Active`. Columns `Username`,
    ///               `Department`, `Status`.
    /// @return The records, each its values by column.
    static List<Map<String, String>> generate(final Map<String, Object> spec) {
        final String kind = String.valueOf(spec.get("kind"));
        final SeededRandom random = new SeededRandom(((Number) spec.get("seed")).longValue());
        final int users = ((Number) spec.get("users")).intValue();
        return switch (kind) {
            case "staff_activity" -> staffActivity(spec, random, users);
            case "hr_users" -> hrUsers(spec, random, users);
            default -> throw new IllegalArgumentException("No question records generator " + kind);
        };
    }

    /// @param number A user's number, from 1.
    /// @return The user's name, as each generator names them.
    static String user(final int number) {
        return "user%05d".formatted(number);
    }

    private static List<Map<String, String>> staffActivity(final Map<String, Object> spec,
                                                           final SeededRandom random,
                                                           final int users) {
        final int events = ((Number) spec.get("events")).intValue();
        final long exportWeek = epochSecond(spec.get("export_week"));
        final int exportDays = ((Number) spec.getOrDefault("days", 7)).intValue();
        final long from = epochSecond(spec.get("from"));
        final int span = ((Number) spec.getOrDefault("span", 21)).intValue();
        final List<Event> made = new ArrayList<>();
        for (int i = 1; i <= users; i++) {
            final long time = exportWeek + random.randint(0, (int) (exportDays * DAY_SECONDS - 1));
            made.add(new Event(time, user(i), "Export", random.randint(1, 20)));
        }
        while (made.size() < events) {
            final String user = user(random.randint(1, users));
            final String action = random.choice(OTHER_ACTIONS);
            final long time = from + random.randint(0, (int) (span * DAY_SECONDS - 1));
            made.add(new Event(time, user, action, "Export".equals(action)
                    ? random.randint(1, 20)
                    : 0));
        }
        made.sort(Comparator.comparingLong(Event::time).thenComparing(Event::user));
        final List<Map<String, String>> records = new ArrayList<>();
        for (final Event event : made) {
            final Map<String, String> record = new LinkedHashMap<>();
            record.put("EventTime", ISO_MILLIS.format(Instant.ofEpochSecond(event.time())));
            record.put("UserId", event.user());
            record.put("Action", event.action());
            record.put("Files", String.valueOf(event.files()));
            records.add(record);
        }
        return records;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> hrUsers(final Map<String, Object> spec,
                                                     final SeededRandom random,
                                                     final int users) {
        final List<String> weighted = new ArrayList<>();
        ((Map<String, Object>) spec.get("departments")).forEach((department, weight) -> {
            for (int i = 0; i < ((Number) weight).intValue(); i++) {
                weighted.add(department);
            }
        });
        final List<Map<String, String>> records = new ArrayList<>();
        for (int i = 1; i <= users; i++) {
            final Map<String, String> record = new LinkedHashMap<>();
            record.put("Username", user(i));
            record.put("Department", random.choice(weighted));
            record.put("Status", "Active");
            records.add(record);
        }
        return records;
    }

    // A day (yyyy-MM-dd) as the epoch second of its start, UTC.
    private static long epochSecond(final Object day) {
        return LocalDate.parse(String.valueOf(day)).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    // An activity event: when, whose, what, and how many files.
    private record Event(long time, String user, String action, int files) {

    }
}
