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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Works out the columns of data held in a document, such as CSV or an Excel sheet: their names from
/// the header row, and their types from the values.
final class ColumnDetector {

    /// The rows read to guess each column's type.
    static final int SAMPLE_ROWS = 1_000;

    private ColumnDetector() {
    }

    /// Detects the columns of a table.
    ///
    /// A column keeps the type of a column of the same name already set, so a type the user chose
    /// survives detecting the columns again. Other columns get the type their values suggest.
    ///
    /// @param table    The table, usually its first [#SAMPLE_ROWS] rows.
    /// @param existing The columns already set, or `null` for none.
    /// @return The columns, in the order their values appear in each row.
    static List<DataColumn> detect(final DataTable table, final List<DataColumn> existing) {
        final Map<String, DataColumnType> existingTypes = new HashMap<>();
        for (final DataColumn column : NullSafe.list(existing)) {
            existingTypes.put(column.getName(), column.getType());
        }

        final int columnCount = table.columnCount();
        final List<String> names = uniqueNames(table.header(), columnCount);
        final List<DataColumn> columns = new ArrayList<>(columnCount);
        for (int index = 0; index < columnCount; index++) {
            final String name = names.get(index);
            final DataColumnType type = existingTypes.containsKey(name)
                    ? existingTypes.get(name)
                    : DataValues.guessType(values(table, index));
            columns.add(new DataColumn(name, type));
        }
        return columns;
    }

    private static List<String> values(final DataTable table, final int index) {
        final List<String> values = new ArrayList<>(table.rows().size());
        for (final List<String> row : table.rows()) {
            if (index < row.size()) {
                values.add(row.get(index));
            }
        }
        return values;
    }

    /// Names for columns from a header row, as each becomes a field name: the header name, with
    /// `column<n>` for any missing or blank, and a suffix on any repeated.
    ///
    /// @param header      The names in the header row, or none.
    /// @param columnCount The number of columns.
    /// @return A unique name for each column.
    static List<String> uniqueNames(final List<String> header, final int columnCount) {
        final Set<String> used = new HashSet<>();
        final List<String> names = new ArrayList<>(columnCount);
        for (int index = 0; index < columnCount; index++) {
            final String headerName = index < header.size()
                    ? header.get(index)
                    : null;
            final String base = NullSafe.isBlankString(headerName)
                    ? "column" + (index + 1)
                    : headerName.trim();
            String name = base;
            int suffix = 2;
            while (!used.add(name)) {
                name = base + "_" + suffix;
                suffix++;
            }
            names.add(name);
        }
        return names;
    }
}
