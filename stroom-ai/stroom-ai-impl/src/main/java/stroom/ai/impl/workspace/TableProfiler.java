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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// Profiles a table in one pass, without the model: row and column counts, and for each column its type, how many
/// values are empty, roughly how many distinct values there are, the smallest and largest values, and the most
/// common values. A few sample rows are included.
///
/// The profile is what the model is given in place of the table itself, so that it can decide what to ask of the
/// data (with the table tools) without the whole table taking up its context.
public final class TableProfiler {

    /// Distinct values are counted exactly up to this many per column, and the count is shown as "more than" after.
    static final int MAX_TRACKED_VALUES = 1_000;
    static final int TOP_VALUES = 5;
    static final int SAMPLE_ROWS = 3;
    private static final int MAX_VALUE_LENGTH = 60;

    private static final Pattern DATE_TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}([T ].*)?$");

    private TableProfiler() {
        // Static only.
    }

    /// @param reader The table, as a markdown table.
    /// @param title  What the table is, e.g. its description.
    /// @return The profile, as markdown.
    /// @throws IOException If the table cannot be read.
    public static String profile(final Reader reader, final String title) throws IOException {
        final BufferedReader bufferedReader = reader instanceof final BufferedReader br
                ? br
                : new BufferedReader(reader);
        List<String> header = null;
        final List<ColumnStats> columns = new ArrayList<>();
        final List<String> sampleRows = new ArrayList<>();
        String separator = null;
        long rows = 0;

        String line;
        while ((line = bufferedReader.readLine()) != null) {
            if (line.isBlank() || !line.strip().startsWith("|")) {
                continue;
            }
            if (header == null) {
                header = MarkdownTables.parseRow(line);
                header.forEach(name -> columns.add(new ColumnStats(name)));
            } else if (separator == null && MarkdownTables.isSeparatorRow(line)) {
                separator = line;
            } else {
                rows++;
                final List<String> cells = MarkdownTables.parseRow(line);
                for (int i = 0; i < columns.size(); i++) {
                    columns.get(i).add(i < cells.size()
                            ? cells.get(i)
                            : "");
                }
                if (sampleRows.size() < SAMPLE_ROWS) {
                    sampleRows.add(line);
                }
            }
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("Profile of ").append(title).append(": ")
                .append(rows).append(" rows, ").append(columns.size()).append(" columns.\n\n");
        if (header == null) {
            return sb.append("The table is empty.\n").toString();
        }
        sb.append("| Column | Type | Empty | Distinct | Min | Max | Most common |\n");
        sb.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (final ColumnStats column : columns) {
            sb.append("| ").append(escape(column.name))
                    .append(" | ").append(column.type())
                    .append(" | ").append(column.empty)
                    .append(" | ").append(column.distinct())
                    .append(" | ").append(escape(column.min()))
                    .append(" | ").append(escape(column.max()))
                    .append(" | ").append(escape(column.mostCommon()))
                    .append(" |\n");
        }
        if (!sampleRows.isEmpty()) {
            sb.append("\nFirst ").append(sampleRows.size()).append(" rows:\n\n");
            sb.append(toRow(header)).append('\n');
            sb.append(separator != null
                    ? separator
                    : toRow(header.stream().map(h -> "---").toList())).append('\n');
            sampleRows.forEach(row -> sb.append(row).append('\n'));
        }
        return sb.toString();
    }

    private static String toRow(final List<String> cells) {
        final StringBuilder sb = new StringBuilder();
        cells.forEach(cell -> sb.append("| ").append(escape(cell)).append(' '));
        return sb.append('|').toString();
    }

    private static String escape(final String value) {
        if (value == null) {
            return "";
        }
        final String shortened = value.length() > MAX_VALUE_LENGTH
                ? value.substring(0, MAX_VALUE_LENGTH - 1) + "…"
                : value;
        return shortened.replace("|", "\\|").replace("\n", " ");
    }

    /// What has been seen of one column.
    private static final class ColumnStats {

        private final String name;
        private final Map<String, Long> counts = new HashMap<>();
        private boolean overflowed;
        private long empty;
        private long nonEmpty;
        private long numbers;
        private long dateTimes;
        private Double minNumber;
        private Double maxNumber;
        private String minText;
        private String maxText;

        private ColumnStats(final String name) {
            this.name = name;
        }

        void add(final String value) {
            if (value.isEmpty()) {
                empty++;
                return;
            }
            nonEmpty++;
            final Double number = parseNumber(value);
            if (number != null) {
                numbers++;
                minNumber = minNumber == null
                        ? number
                        : Math.min(minNumber, number);
                maxNumber = maxNumber == null
                        ? number
                        : Math.max(maxNumber, number);
            }
            if (DATE_TIME.matcher(value).matches()) {
                dateTimes++;
            }
            if (minText == null || value.compareTo(minText) < 0) {
                minText = value;
            }
            if (maxText == null || value.compareTo(maxText) > 0) {
                maxText = value;
            }
            if (counts.containsKey(value) || counts.size() < MAX_TRACKED_VALUES) {
                counts.merge(value, 1L, Long::sum);
            } else {
                overflowed = true;
            }
        }

        String type() {
            if (nonEmpty == 0) {
                return "empty";
            } else if (numbers == nonEmpty) {
                return "number";
            } else if (dateTimes == nonEmpty) {
                return "date/time";
            } else {
                return "text";
            }
        }

        String distinct() {
            return overflowed
                    ? "more than " + MAX_TRACKED_VALUES
                    : String.valueOf(counts.size());
        }

        String min() {
            return "number".equals(type())
                    ? formatNumber(minNumber)
                    : minText;
        }

        String max() {
            return "number".equals(type())
                    ? formatNumber(maxNumber)
                    : maxText;
        }

        String mostCommon() {
            if (counts.isEmpty()) {
                return "";
            }
            final List<Map.Entry<String, Long>> top = counts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(TOP_VALUES)
                    .toList();
            if (top.isEmpty()) {
                return "all different";
            }
            final StringBuilder sb = new StringBuilder();
            for (final Map.Entry<String, Long> entry : top) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                final String value = entry.getKey().length() > 20
                        ? entry.getKey().substring(0, 19) + "…"
                        : entry.getKey();
                sb.append(value).append(" (").append(entry.getValue()).append(')');
            }
            return sb.toString();
        }

        private static Double parseNumber(final String value) {
            final char first = value.charAt(0);
            if (!(Character.isDigit(first) || first == '-' || first == '+' || first == '.')) {
                return null;
            }
            try {
                final double number = Double.parseDouble(value);
                return Double.isFinite(number)
                        ? number
                        : null;
            } catch (final NumberFormatException e) {
                return null;
            }
        }

        private static String formatNumber(final Double number) {
            if (number == null) {
                return "";
            }
            return number == Math.rint(number) && Math.abs(number) < 1e15
                    ? String.valueOf(number.longValue())
                    : String.valueOf(number);
        }
    }
}
