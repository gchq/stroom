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
import java.util.Locale;
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

    /// A row of `aggregated/activity-timeline/any-source.csv` as its source wrote the event: the user as written there
    /// (`XmlUser`), a wrong time (the index's is the event's), its element, Action, TypeId, the key under the element
    /// (a process's id and command) and what it touched.
    ///
    /// @param record The row.
    /// @return Its Events XML event.
    static String anySourceEventXml(final Map<String, String> record) {
        final String element = record.get("Element");
        final StringBuilder sb = new StringBuilder("<Event><EventTime><TimeCreated>2001-01-01T00:00:00.000Z"
                                                   + "</TimeCreated></EventTime><EventSource><System><Name>"
                                                   + record.get("Source") + "</Name><Environment>Test</Environment>"
                                                   + "</System><Generator>e2e</Generator><Device><HostName>"
                                                   + record.get("Host").toUpperCase(Locale.ROOT)
                                                   + ".CORP</HostName></Device><User><Id>"
                                                   + escapeText(record.get("XmlUser")) + "</Id></User></EventSource>"
                                                   + "<EventDetail><TypeId>" + record.get("TypeId") + "</TypeId><")
                .append(element).append('>');
        if (!record.get("Action").isEmpty()) {
            sb.append("<Action>").append(record.get("Action")).append("</Action>");
        }
        final String path = record.get("KeyPath");
        final String value = record.get("KeyValue");
        if ("Process".equals(element)) {
            final String[] parts = value.split("\\|", 2);
            sb.append("<Command>").append(escapeText(parts[1])).append("</Command><ProcessId>").append(parts[0])
                    .append("</ProcessId>");
        } else if (!path.isEmpty()) {
            final String[] names = path.split("/");
            for (final String name : names) {
                sb.append('<').append(name).append('>');
            }
            sb.append(escapeText(value));
            for (int i = names.length - 1; i >= 0; i--) {
                sb.append("</").append(names[i]).append('>');
            }
        }
        return sb.append("</").append(element).append("></EventDetail></Event>").toString();
    }

    private static String escapeText(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;");
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
    ///             * `insider_events`: the insider-risk dataset ([InsiderDataset]) from its `seed`, with `background`
    ///               people (default 5) and `scale` of the routine actions (default 0.3), its days `from` to `to`
    ///               (inclusive, in the dataset's zone), the `sources` named (default all), every source's events
    ///               in one table, by time ([InsiderRecords#flat]); with `streams: true`, their Events XML is stored
    ///               in a stream the index's StreamId and EventId lead to ([#insiderEventXml]); with
    ///               `targets: true`, the events done to people by others too ([InsiderDataset#targetActs], with
    ///               `TargetPersonId` and `TargetUserName`), after the rest; with `noise: true`, the background
    ///               people's archives and personal cloud use ([InsiderDataset#noiseActs]), after those; with
    ///               `cdn: true`, everyone's content delivery network fetches ([InsiderDataset#cdnActs]), last.
    /// @return The records, each its values by column.
    static List<Map<String, String>> generate(final Map<String, Object> spec) {
        final String kind = String.valueOf(spec.get("kind"));
        final SeededRandom random = new SeededRandom(((Number) spec.get("seed")).longValue());
        return switch (kind) {
            case "staff_activity" -> staffActivity(spec, random, users(spec));
            case "hr_users" -> hrUsers(spec, random, users(spec));
            case "insider_events" -> insiderEvents(spec);
            default -> throw new IllegalArgumentException("No question records generator " + kind);
        };
    }

    private static int users(final Map<String, Object> spec) {
        return ((Number) spec.get("users")).intValue();
    }

    private static List<Map<String, String>> insiderEvents(final Map<String, Object> spec) {
        return insiderActs(spec).stream().map(InsiderRecords::flat).toList();
    }

    /// The Events XML of an `insider_events` question's records, in their order (the record at place `i` is event
    /// `i + 1` of a stream holding them), for a case whose index's StreamId and EventId must lead to its events'
    /// XML (`streams: true`): e.g. peers' `Organisation`, which is only in the XML.
    ///
    /// @param spec The `generate`, of kind `insider_events`.
    /// @return Each record's event, as [InsiderRecords#eventXml] writes it.
    static List<String> insiderEventXml(final Map<String, Object> spec) {
        return insiderActs(spec).stream().map(InsiderRecords::eventXml).toList();
    }

    /// @param spec A question's `generate`.
    /// @return True if it is `insider_events` with `streams: true`: its records' Events XML is stored in Stroom.
    static boolean storesInsiderStreams(final Map<String, Object> spec) {
        return spec != null && "insider_events".equals(String.valueOf(spec.get("kind")))
               && Boolean.TRUE.equals(spec.get("streams"));
    }

    @SuppressWarnings("unchecked")
    private static List<InsiderDataset.Act> insiderActs(final Map<String, Object> spec) {
        final InsiderDataset dataset = InsiderDataset.generate(new InsiderDataset.Options(((Number) spec.get("seed"))
                .longValue(), ((Number) spec.getOrDefault("background", 5)).intValue(), ((Number) spec.getOrDefault(
                "scale", 0.3)).doubleValue(), List.of(new InsiderDataset.Period(LocalDate.parse(String.valueOf(
                spec.get("from"))), LocalDate.parse(String.valueOf(spec.get("to")))))));
        final List<String> sources = (List<String>) spec.getOrDefault("sources", List.of());
        final List<InsiderDataset.Act> acts = new ArrayList<>(dataset.acts());
        if (Boolean.TRUE.equals(spec.get("targets"))) {
            // After the rest, so each record's place (its EventId) is the same with or without them.
            acts.addAll(dataset.targetActs());
        }
        if (Boolean.TRUE.equals(spec.get("noise"))) {
            acts.addAll(dataset.noiseActs());
        }
        if (Boolean.TRUE.equals(spec.get("cdn"))) {
            acts.addAll(dataset.cdnActs());
        }
        return acts.stream()
                .filter(act -> sources.isEmpty() || sources.contains(act.source().key()))
                .toList();
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
