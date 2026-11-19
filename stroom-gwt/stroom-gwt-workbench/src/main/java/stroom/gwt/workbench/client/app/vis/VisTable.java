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

package stroom.gwt.workbench.client.app.vis;

import stroom.gwt.workbench.client.app.rest.JsonValues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The test data of a visualisation story: a dashboard table's columns and rows, as a search
/// returns them, from which [VisData] makes the visualisation's data as Stroom's server would.
///
/// A row's values are a `String` for a [Type#TEXT] column, a number for a [Type#NUMBER] column
/// and the milliseconds since the epoch for a [Type#DATE_TIME] column (see [#date(String)]). Any
/// value may be null. The table is immutable.
public final class VisTable {

    private final List<Column> columns;
    private final List<List<Object>> rows;

    private VisTable(final List<Column> columns, final List<List<Object>> rows) {
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
    }

    /// @return A builder for a table.
    public static Builder builder() {
        return new Builder();
    }

    /// The time of an ISO 8601 date and time in UTC, e.g. `2026-10-19T09:30:00Z` or
    /// `2026-10-19T09:30:00.250Z`.
    ///
    /// @param iso The date and time.
    /// @return The milliseconds since the epoch.
    /// @throws IllegalArgumentException If it isn't in that form.
    public static long date(final String iso) {
        Objects.requireNonNull(iso, "iso");
        if (!iso.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z")) {
            throw new IllegalArgumentException("Not a UTC date and time such as 2026-10-19T09:30:00Z: " + iso);
        }
        final int year = Integer.parseInt(iso.substring(0, 4));
        final int month = Integer.parseInt(iso.substring(5, 7));
        final int day = Integer.parseInt(iso.substring(8, 10));
        final int hour = Integer.parseInt(iso.substring(11, 13));
        final int minute = Integer.parseInt(iso.substring(14, 16));
        final int second = Integer.parseInt(iso.substring(17, 19));
        final int millis = iso.length() > 20
                ? Integer.parseInt(iso.substring(20, 23))
                : 0;
        if (month < 1 || month > 12 || day < 1 || day > 31 || hour > 23 || minute > 59 || second > 59) {
            throw new IllegalArgumentException("Not a valid date and time: " + iso);
        }
        return ((((daysFromEpoch(year, month, day) * 24L + hour) * 60 + minute) * 60 + second) * 1000) + millis;
    }

    /// The ISO 8601 form of a time in UTC, with milliseconds, as Stroom shows a date and time in a
    /// table by default.
    ///
    /// @param millis The milliseconds since the epoch.
    /// @return The date and time, e.g. `2026-10-19T09:30:00.000Z`.
    public static String formatDate(final long millis) {
        final long days = Math.floorDiv(millis, 86_400_000L);
        final long millisOfDay = Math.floorMod(millis, 86_400_000L);
        // Howard Hinnant's civil_from_days
        final long z = days + 719_468;
        final long era = Math.floorDiv(z, 146_097);
        final long dayOfEra = z - era * 146_097;
        final long yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365;
        final long dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        final long monthIndex = (5 * dayOfYear + 2) / 153;
        final long day = dayOfYear - (153 * monthIndex + 2) / 5 + 1;
        final long month = monthIndex < 10
                ? monthIndex + 3
                : monthIndex - 9;
        final long year = yearOfEra + era * 400 + (month <= 2
                ? 1
                : 0);
        return pad(year, 4) + "-" + pad(month, 2) + "-" + pad(day, 2) + "T"
               + pad(millisOfDay / 3_600_000, 2) + ":" + pad(millisOfDay / 60_000 % 60, 2) + ":"
               + pad(millisOfDay / 1000 % 60, 2) + "." + pad(millisOfDay % 1000, 3) + "Z";
    }

    /// @return The columns, in order.
    public List<Column> getColumns() {
        return columns;
    }

    /// @return The rows, each with a value for each column.
    public List<List<Object>> getRows() {
        return rows;
    }

    /// @param name A column's name.
    /// @return The index of the column with the name, or -1 if there is none.
    public int indexOf(final String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /// The table's columns as a dashboard Table component's (`Column`s), without the brackets,
    /// e.g. for `DashboardDocs.table`. Each has the id `f-<index>` and its type's format.
    ///
    /// @return The columns' JSON.
    public String columnsJson() {
        final List<String> json = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            final Map<String, Object> column = new LinkedHashMap<>();
            column.put("id", "f-" + i);
            column.put("name", columns.get(i).getName());
            column.put("format", Collections.singletonMap("type", columns.get(i).getType().name()));
            json.add(JsonValues.toJson(column));
        }
        return String.join(", ", json);
    }

    /// The table's rows as a table result's (`Row`s), each value formatted as Stroom shows it.
    ///
    /// @return The JSON array of the rows.
    public String rowsJson() {
        final List<Object> json = new ArrayList<>();
        for (final List<Object> row : rows) {
            final List<Object> values = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                values.add(format(columns.get(i).getType(), row.get(i)));
            }
            final Map<String, Object> jsonRow = new LinkedHashMap<>();
            jsonRow.put("values", values);
            jsonRow.put("depth", 0);
            json.add(jsonRow);
        }
        return JsonValues.toJson(json);
    }

    // A value as Stroom's table shows it
    private static String format(final Type type, final Object value) {
        if (value == null) {
            return null;
        }
        if (type == Type.DATE_TIME) {
            return formatDate(((Number) value).longValue());
        }
        if (type == Type.NUMBER) {
            final double number = ((Number) value).doubleValue();
            return number == Math.rint(number) && Math.abs(number) < 1e15
                    ? String.valueOf((long) number)
                    : String.valueOf(number);
        }
        return String.valueOf(value);
    }

    // Howard Hinnant's days_from_civil
    private static long daysFromEpoch(final int year, final int month, final int day) {
        final long y = month <= 2
                ? year - 1
                : year;
        final long era = Math.floorDiv(y, 400);
        final long yearOfEra = y - era * 400;
        final long dayOfYear = (153L * (month > 2
                ? month - 3
                : month + 9) + 2) / 5 + day - 1;
        final long dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return era * 146_097 + dayOfEra - 719_468;
    }

    private static String pad(final long value, final int width) {
        final StringBuilder sb = new StringBuilder(String.valueOf(value));
        while (sb.length() < width) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


    /// The type of a column, as its format's type in Stroom (`Format.Type`).
    public enum Type {
        /// Text, e.g. a host name.
        TEXT,
        /// A number.
        NUMBER,
        /// A date and time, as milliseconds since the epoch.
        DATE_TIME
    }


    // --------------------------------------------------------------------------------


    /// A column of a table: its name and type.
    public static final class Column {

        private final String name;
        private final Type type;

        /// @param name Its name.
        /// @param type Its type.
        public Column(final String name, final Type type) {
            this.name = Objects.requireNonNull(name, "name");
            this.type = Objects.requireNonNull(type, "type");
        }

        /// @return Its name.
        public String getName() {
            return name;
        }

        /// @return Its type.
        public Type getType() {
            return type;
        }
    }


    // --------------------------------------------------------------------------------


    /// Builds a [VisTable]: its columns, then its rows.
    public static final class Builder {

        private final List<Column> columns = new ArrayList<>();
        private final List<List<Object>> rows = new ArrayList<>();

        private Builder() {
        }

        /// @param name The name of a text column to add.
        /// @return This builder.
        public Builder text(final String name) {
            return column(name, Type.TEXT);
        }

        /// @param name The name of a number column to add.
        /// @return This builder.
        public Builder number(final String name) {
            return column(name, Type.NUMBER);
        }

        /// @param name The name of a date and time column to add.
        /// @return This builder.
        public Builder date(final String name) {
            return column(name, Type.DATE_TIME);
        }

        /// Adds a row. A date and time may be given as its ISO 8601 form (see [VisTable#date]).
        ///
        /// @param values A value for each column.
        /// @return This builder.
        /// @throws IllegalArgumentException If there isn't a value of the right type for each column.
        public Builder row(final Object... values) {
            if (values.length != columns.size()) {
                throw new IllegalArgumentException("A row needs " + columns.size() + " values, not "
                                                   + values.length + ": " + Arrays.asList(values));
            }
            final List<Object> row = new ArrayList<>();
            for (int i = 0; i < values.length; i++) {
                row.add(checked(columns.get(i), values[i]));
            }
            rows.add(row);
            return this;
        }

        /// @return The table.
        public VisTable build() {
            return new VisTable(columns, rows);
        }

        private Builder column(final String name, final Type type) {
            for (final Column column : columns) {
                if (column.getName().equals(name)) {
                    throw new IllegalArgumentException("There is already a column " + name);
                }
            }
            columns.add(new Column(name, type));
            return this;
        }

        // The value to keep, refusing one of the wrong type
        private static Object checked(final Column column, final Object value) {
            if (value == null) {
                return null;
            }
            switch (column.getType()) {
                case TEXT:
                    if (value instanceof String) {
                        return value;
                    }
                    break;
                case NUMBER:
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }
                    break;
                case DATE_TIME:
                    if (value instanceof String) {
                        return (double) VisTable.date((String) value);
                    }
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }
                    break;
                default:
                    break;
            }
            throw new IllegalArgumentException("The value " + value + " isn't a " + column.getType()
                                               + " for the column " + column.getName());
        }
    }
}
