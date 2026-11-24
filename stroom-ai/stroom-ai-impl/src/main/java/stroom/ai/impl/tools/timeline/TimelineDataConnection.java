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

package stroom.ai.impl.tools.timeline;

import stroom.dataconnection.shared.CsvSettings;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/// How a timeline's table is written as a CSV Data Connection (stroom#6010), so a dashboard can query it: the
/// table's CSV held in the document as it is, each column typed (its times Date, its counts numbers, the rest
/// text), `start` its time field, named for the entity, the period and the table, e.g. `Timeline sam.patel
/// 2026-10-20 to 2026-10-26 (spans)`, and described as the dated snapshot it is.
public final class TimelineDataConnection {

    /// The detail spans' table (`spans.csv`).
    public static final String SPANS = "spans";
    /// The activities' table (`activities.csv`).
    public static final String ACTIVITIES = "activities";
    /// The tables that can be written.
    public static final List<String> TABLES = List.of(SPANS, ACTIVITIES);
    /// The time field: a dashboard's time range keeps the rows starting in it.
    public static final String TIME_FIELD = "start";

    private static final String PREFIX = "Timeline ";
    // A name Stroom's explorer takes: its characters, as the UI's name pattern allows them.
    private static final Pattern NOT_IN_NAME = Pattern.compile("[^A-Za-z0-9_\\- .()]");
    private static final Map<String, DataColumnType> SPAN_TYPES = Map.of(
            "start", DataColumnType.DATE,
            "end", DataColumnType.DATE,
            "events", DataColumnType.LONG);
    private static final Map<String, DataColumnType> ACTIVITY_TYPES = Map.of(
            "start", DataColumnType.DATE,
            "end", DataColumnType.DATE,
            "active_minutes", DataColumnType.DOUBLE);

    private TimelineDataConnection() {
        // Static only.
    }

    /// @param table [#SPANS] or [#ACTIVITIES].
    /// @return The table's file in the timeline's folder.
    public static String file(final String table) {
        return SPANS.equals(table)
                ? ActivityTimelineTool.SPANS_FILE
                : ActivityTimelineTool.ACTIVITIES_FILE;
    }

    /// The table's columns, typed: its start and end Date (ISO 8601 instants, as the table has them), its counts
    /// numbers, the rest text (a day stays text, as it is a lane's name).
    ///
    /// @param table [#SPANS] or [#ACTIVITIES].
    /// @return The columns, in the table's order.
    public static List<DataColumn> columns(final String table) {
        final boolean spans = SPANS.equals(table);
        final List<String> names = spans
                ? TimelineTables.SPAN_COLUMNS
                : TimelineTables.ACTIVITY_COLUMNS;
        final Map<String, DataColumnType> types = spans
                ? SPAN_TYPES
                : ACTIVITY_TYPES;
        final List<DataColumn> columns = new ArrayList<>();
        names.forEach(name -> columns.add(new DataColumn(name, types.getOrDefault(name, DataColumnType.TEXT))));
        return List.copyOf(columns);
    }

    /// Checks a table's header is the one its columns are typed for, as a table written by an older Stroom may not
    /// be.
    ///
    /// @param table  [#SPANS] or [#ACTIVITIES].
    /// @param header The table's first line.
    /// @return Why it isn't, or empty if it is.
    public static Optional<String> headerProblem(final String table, final String header) {
        final String expected = String.join(",", columns(table).stream().map(DataColumn::getName).toList());
        final String given = NullSafe.string(header).strip();
        return expected.equals(given)
                ? Optional.empty()
                : Optional.of("its columns are '" + given + "', not '" + expected + "' as this Stroom writes "
                              + "them");
    }

    /// The name of a timeline's Data Connection: the entity (with any character a name can't hold made `_`), the
    /// period's first and last days in the user's zone, and the table.
    ///
    /// @param entity The user (or entity).
    /// @param fromMs The period's start.
    /// @param toMs   The period's end (a day's end at midnight is the day before's).
    /// @param zone   The user's zone.
    /// @param table  [#SPANS] or [#ACTIVITIES].
    /// @return The name, e.g. `Timeline sam.patel 2026-10-20 to 2026-10-26 (spans)`.
    public static String name(final String entity,
                              final long fromMs,
                              final long toMs,
                              final ZoneId zone,
                              final String table) {
        final LocalDate first = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate();
        final ZonedDateTime end = Instant.ofEpochMilli(toMs).atZone(zone);
        final LocalDate last = end.toLocalTime().toSecondOfDay() == 0 && toMs > fromMs
                ? end.toLocalDate().minusDays(1)
                : end.toLocalDate();
        return namePrefix(entity) + first + (last.equals(first)
                ? ""
                : " to " + last) + " (" + table + ")";
    }

    /// Says whether a name is that of a timeline's Data Connection of the same entity and table, for any period: one
    /// a re-run of the timeline replaces.
    ///
    /// @param name   A document's name.
    /// @param entity The user (or entity).
    /// @param table  [#SPANS] or [#ACTIVITIES].
    /// @return True if it is.
    public static boolean isFor(final String name, final String entity, final String table) {
        final String text = NullSafe.string(name);
        return text.startsWith(namePrefix(entity)) && text.endsWith(" (" + table + ")");
    }

    /// The description of a timeline's Data Connection: the dated snapshot it is, of whom, for which period, from
    /// which sources.
    ///
    /// @param snapshot The run.
    /// @param table    [#SPANS] or [#ACTIVITIES].
    /// @return The description.
    public static String description(final TimelineSnapshot snapshot, final String table) {
        final ZoneId zone = zone(snapshot.zone());
        return "A snapshot of " + snapshot.entity() + "'s activity timeline ("
               + (SPANS.equals(table)
                ? "the detail spans: focus sessions, pairs and activity periods"
                : "the activities rolled up from the spans, a row per activity per day")
               + ") for " + local(snapshot.fromMs(), zone) + " to " + local(snapshot.toMs(), zone) + " (" + zone.getId()
               + "), built by Stroom AI's activity_timeline at " + snapshot.built() + " from "
               + (snapshot.sources().isEmpty()
                ? "its sources"
                : String.join(", ", snapshot.sources()))
               + ". It holds the user's activity, and isn't updated: run the timeline again to replace it. Times are "
               + "UTC.";
    }

    /// A Data Connection's settings holding a timeline's table.
    ///
    /// @param builder     The document's builder.
    /// @param table       [#SPANS] or [#ACTIVITIES].
    /// @param csv         The table's CSV, as written to the workspace.
    /// @param description Its description ([#description]).
    /// @return The builder, with the settings.
    public static DataConnectionDoc.Builder apply(final DataConnectionDoc.Builder builder,
                                                  final String table,
                                                  final String csv,
                                                  final String description) {
        return builder.sourceType(DataConnectionType.CSV)
                .csv(CsvSettings.builder()
                        .header(true)
                        .columns(columns(table))
                        .data(csv)
                        .build())
                .timeField(TIME_FIELD)
                .description(description);
    }

    /// @param zoneId A zone's id.
    /// @return The zone, or UTC if it can't be read.
    static ZoneId zone(final String zoneId) {
        try {
            return ZoneId.of(NullSafe.nonBlankStringElse(zoneId, "UTC"));
        } catch (final RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }

    private static String namePrefix(final String entity) {
        return PREFIX + NOT_IN_NAME.matcher(NullSafe.string(entity).strip()).replaceAll("_") + " ";
    }

    private static String local(final long ms, final ZoneId zone) {
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString().replace('T', ' ');
    }
}
