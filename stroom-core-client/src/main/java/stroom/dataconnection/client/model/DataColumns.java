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

package stroom.dataconnection.client.model;

import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// The rules for the columns of a Data Connection's data (CSV or Excel columns, or LDAP attributes)
/// as they are edited: which names may be added, and whether what is shown is what is stored.
public final class DataColumns {

    // Every LDAP entry has a dn field of its own
    private static final String DN_FIELD = "dn";

    private DataColumns() {
        // Static utility
    }

    /// Says whether the columns shown are those stored. A stored column with no type is shown as
    /// Text, and no columns as an empty list, so that reading a document and writing it back
    /// changes nothing.
    ///
    /// @param stored The columns stored, or `null` for none.
    /// @param shown  The columns shown.
    /// @return True if they are the same.
    public static boolean same(final List<DataColumn> stored, final List<DataColumn> shown) {
        final List<DataColumn> storedColumns = NullSafe.list(stored);
        if (storedColumns.size() != shown.size()) {
            return false;
        }
        for (int index = 0; index < shown.size(); index++) {
            final DataColumn storedColumn = storedColumns.get(index);
            final DataColumn shownColumn = shown.get(index);
            if (!Objects.equals(storedColumn.getName(), shownColumn.getName())
                || typeOf(storedColumn) != typeOf(shownColumn)) {
                return false;
            }
        }
        return true;
    }

    /// @param column A column.
    /// @return Its type, or Text if it has none.
    public static DataColumnType typeOf(final DataColumn column) {
        return Objects.requireNonNullElse(column.getType(), DataColumnType.TEXT);
    }

    /// Says what is wrong with the name of an attribute to add, as LDAP names ignore case and every
    /// entry has a `dn` field of its own.
    ///
    /// @param name     The name typed.
    /// @param existing The names already listed.
    /// @return What is wrong with it, or `null` if it may be added.
    public static String validateNewName(final String name, final List<String> existing) {
        final String trimmed = NullSafe.trim(name);
        if (trimmed.isEmpty()) {
            return "An attribute name must be given";
        }
        if (DN_FIELD.equalsIgnoreCase(trimmed)) {
            return "dn is always the first field, so it needn't be added";
        }
        final String lower = trimmed.toLowerCase(Locale.ROOT);
        for (final String existingName : existing) {
            if (existingName != null && existingName.toLowerCase(Locale.ROOT).equals(lower)) {
                return "'" + existingName + "' is already listed";
            }
        }
        return null;
    }

    /// @param columns Columns.
    /// @return Their names, in order.
    public static List<String> names(final List<DataColumn> columns) {
        final List<String> names = new ArrayList<>(columns.size());
        for (final DataColumn column : columns) {
            names.add(column.getName());
        }
        return names;
    }
}
