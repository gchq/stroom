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

package stroom.ai.impl.workspace;

import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableEngine.Query;
import stroom.ai.impl.workspace.TableEngine.Result;
import stroom.util.date.DateUtil;
import stroom.util.shared.NullSafe;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Joins two tables in the workspace on the columns they share a value in, optionally only where their times are
/// close, for the table tool to query as one. Seen: a chat matched mail items accessed then deleted by the same user
/// (by message id) across two flattened files, which took some 15 calls of grep and jq, the jq join failing, as no
/// tool joined two files.
public final class TableJoin {

    /// The prefix given to a column of the right table whose name the left table has too.
    public static final String RIGHT_PREFIX = "right.";

    private TableJoin() {
        // Static only.
    }

    /// Joins two tables.
    ///
    /// @param left        The left table (the table queried).
    /// @param leftFormat  Its format.
    /// @param right       The right table (the one joined to it).
    /// @param rightFormat Its format.
    /// @param spec        How to join them.
    /// @return The joined table: the left table's columns then the right's (bar its key columns), or, for
    /// [Keep#UNMATCHED], the left rows no right row matches.
    /// @throws WorkspaceException if a column isn't in its table, or the join would make too many rows.
    public static Joined join(final Path left,
                              final Format leftFormat,
                              final Path right,
                              final Format rightFormat,
                              final Spec spec) {
        final Result leftTable = all(left, leftFormat);
        final Result rightTable = all(right, rightFormat);
        final List<String> rightOn = spec.rightOn().isEmpty()
                ? spec.on()
                : spec.rightOn();
        if (spec.on().isEmpty()) {
            throw new WorkspaceException("Give join.on: the columns whose values must be the same in both tables.");
        }
        if (rightOn.size() != spec.on().size()) {
            throw new WorkspaceException("join.rightOn must name as many columns as join.on (" + spec.on().size()
                                         + "), in the same order.");
        }
        final List<Integer> leftKeys = indexes(spec.on(), leftTable.columns(), "the table");
        final List<Integer> rightKeys = indexes(rightOn, rightTable.columns(), "join.path");
        final Integer leftTime = spec.within() == null
                ? null
                : index(spec.within().column(), leftTable.columns(), "the table");
        final Integer rightTime = spec.within() == null
                ? null
                : index(Objects.requireNonNullElse(spec.within().rightColumn(), spec.within().column()),
                        rightTable.columns(), "join.path");

        // The right rows by key; rows with an empty key cell are never matched.
        final Map<List<String>, List<List<String>>> byKey = new HashMap<>();
        for (final List<String> row : rightTable.rows()) {
            final List<String> key = key(row, rightKeys);
            if (key != null) {
                byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            }
        }

        final Set<Integer> rightKeySet = new HashSet<>(rightKeys);
        final List<Integer> rightKept = new ArrayList<>();
        for (int i = 0; i < rightTable.columns().size(); i++) {
            if (!rightKeySet.contains(i)) {
                rightKept.add(i);
            }
        }
        final List<String> columns = new ArrayList<>(leftTable.columns());
        if (spec.keep() == Keep.BOTH) {
            final Set<String> leftNames = new HashSet<>(leftTable.columns());
            for (final int i : rightKept) {
                final String name = rightTable.columns().get(i);
                columns.add(leftNames.contains(name)
                        ? RIGHT_PREFIX + name
                        : name);
            }
        }

        final List<List<String>> rows = new ArrayList<>();
        long leftMatched = 0;
        long untimed = 0;
        long read = 0;
        for (final List<String> leftRow : leftTable.rows()) {
            if (++read % 1_000 == 0) {
                ToolDeadline.check();
            }
            final List<String> key = key(leftRow, leftKeys);
            final List<List<String>> candidates = key == null
                    ? List.of()
                    : byKey.getOrDefault(key, List.of());
            final Long leftMillis = leftTime == null || candidates.isEmpty()
                    ? null
                    : millis(cell(leftRow, leftTime));
            if (leftTime != null && !candidates.isEmpty() && leftMillis == null) {
                untimed++;
            }
            boolean matched = false;
            for (final List<String> rightRow : candidates) {
                if (leftTime != null && !within(spec.within(), leftMillis, millis(cell(rightRow, rightTime)))) {
                    continue;
                }
                matched = true;
                if (spec.keep() == Keep.UNMATCHED) {
                    break;
                }
                if (rows.size() >= TableEngine.MAX_ROWS_HELD) {
                    throw new WorkspaceException("The join makes more than " + TableEngine.MAX_ROWS_HELD
                                                 + " rows. Join on more columns, or filter the tables first.");
                }
                final List<String> joined = new ArrayList<>(leftRow);
                padTo(joined, leftTable.columns().size());
                for (final int i : rightKept) {
                    joined.add(cell(rightRow, i));
                }
                rows.add(joined);
            }
            if (matched) {
                leftMatched++;
            } else if (spec.keep() == Keep.UNMATCHED) {
                final List<String> unmatched = new ArrayList<>(leftRow);
                padTo(unmatched, leftTable.columns().size());
                rows.add(unmatched);
            }
        }
        return new Joined(columns, rows, leftTable.rows().size(), rightTable.rows().size(), leftMatched, untimed);
    }

    /// @param within      The time condition.
    /// @param leftMillis  The left row's time, or null if it has none that can be read.
    /// @param rightMillis The right row's time, or null.
    /// @return True if both have times and the right row's is within the condition's seconds of the left's: at it
    /// or after it if [Within#after], else either side.
    static boolean within(final Within within, final Long leftMillis, final Long rightMillis) {
        if (leftMillis == null || rightMillis == null) {
            return false;
        }
        final long difference = rightMillis - leftMillis;
        final long limit = within.seconds() * 1_000L;
        return within.after()
                ? difference >= 0 && difference <= limit
                : Math.abs(difference) <= limit;
    }

    /// A cell's time: an ISO 8601 date and time (one without a zone is taken as UTC), or epoch milliseconds.
    ///
    /// @param cell The cell.
    /// @return The time in epoch milliseconds, or null if the cell is empty or isn't a time.
    public static Long millis(final String cell) {
        if (NullSafe.isBlankString(cell)) {
            return null;
        }
        final String text = cell.strip();
        try {
            return DateUtil.parseUnknownString(text);
        } catch (final RuntimeException e) {
            // Not ISO 8601 with a zone, nor a number: perhaps a local date and time, which is UTC here.
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (final DateTimeParseException e) {
            return null;
        }
    }

    // Every row of a table.
    private static Result all(final Path file, final Format format) {
        return TableEngine.run(file, format, new Query(List.of(), List.of(), List.of(), List.of(), false, false,
                List.of(), null));
    }

    // The key of a row, or null if any of its cells is empty.
    private static List<String> key(final List<String> row, final List<Integer> indexes) {
        final List<String> key = new ArrayList<>(indexes.size());
        for (final int index : indexes) {
            final String cell = cell(row, index);
            if (cell.isEmpty()) {
                return null;
            }
            key.add(cell);
        }
        return key;
    }

    private static String cell(final List<String> row, final int index) {
        return index < row.size()
                ? Objects.requireNonNullElse(row.get(index), "")
                : "";
    }

    private static void padTo(final List<String> row, final int size) {
        while (row.size() < size) {
            row.add("");
        }
    }

    private static List<Integer> indexes(final List<String> names, final List<String> columns, final String table) {
        final List<Integer> indexes = new ArrayList<>(names.size());
        for (final String name : names) {
            indexes.add(index(name, columns, table));
        }
        return indexes;
    }

    private static int index(final String name, final List<String> columns, final String table) {
        final int index = columns.indexOf(name);
        if (index >= 0) {
            return index;
        }
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equalsIgnoreCase(NullSafe.trim(name))) {
                return i;
            }
        }
        throw new WorkspaceException("There is no column '" + name + "' in " + table + ". Its columns are: "
                                     + String.join(", ", columns) + ".");
    }

    /// What a join keeps.
    public enum Keep {
        /// Each left row with each right row it matches.
        BOTH,
        /// The left rows that no right row matches.
        UNMATCHED
    }

    /// How to join two tables.
    ///
    /// @param on      The left table's columns that must equal the right's.
    /// @param rightOn The right table's columns, in the same order, if named differently; empty if named as `on`.
    /// @param keep    What to keep.
    /// @param within  A time condition as well, or null.
    public record Spec(List<String> on, List<String> rightOn, Keep keep, Within within) {

        public Spec {
            on = List.copyOf(Objects.requireNonNullElse(on, List.of()));
            rightOn = List.copyOf(Objects.requireNonNullElse(rightOn, List.of()));
            keep = Objects.requireNonNullElse(keep, Keep.BOTH);
        }
    }

    /// Rows match only where their times are close.
    ///
    /// @param column      The left table's time column.
    /// @param rightColumn The right table's, or null if named as `column`.
    /// @param seconds     The most seconds apart.
    /// @param after       True if the right row's time must be at or after the left's, false for either side.
    public record Within(String column, String rightColumn, long seconds, boolean after) {

    }

    /// A joined table.
    ///
    /// @param columns     Its columns.
    /// @param rows        Its rows.
    /// @param leftRows    The rows of the left table.
    /// @param rightRows   The rows of the right table.
    /// @param leftMatched The left rows matched by at least one right row.
    /// @param untimed     The left rows with a key match whose time couldn't be read, so matched nothing.
    public record Joined(List<String> columns,
                         List<List<String>> rows,
                         long leftRows,
                         long rightRows,
                         long leftMatched,
                         long untimed) {

    }
}
