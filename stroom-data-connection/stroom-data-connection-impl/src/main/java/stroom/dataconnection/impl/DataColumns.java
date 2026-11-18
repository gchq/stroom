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
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.util.shared.NullSafe;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Checks on the columns a document lists, such as those of its CSV or its LDAP attributes, each of
/// which becomes a field.
final class DataColumns {

    private DataColumns() {
        // Static methods only.
    }

    /// Checks that every column has a name. Detect Columns always gives one, but an imported document
    /// may not.
    ///
    /// @param doc     The document.
    /// @param columns Its columns.
    /// @throws IllegalArgumentException If a column has no name.
    static void requireNamed(final DataConnectionDoc doc, final List<DataColumn> columns) {
        for (final DataColumn column : columns) {
            if (NullSafe.isBlankString(column.getName())) {
                throw new IllegalArgumentException("Data Connection '" + doc.getName() + "' has a column "
                                                   + "with no name. Detect the columns again in its settings.");
            }
        }
    }

    /// Checks that no two columns have the same name, ignoring case, as fields are matched without
    /// regard to case.
    ///
    /// @param doc     The document.
    /// @param columns Its columns, each with a name.
    /// @param noun    What its columns are called, e.g. `column` or `attribute`.
    /// @throws IllegalArgumentException If two columns have the same name.
    static void requireUniqueNames(final DataConnectionDoc doc, final List<DataColumn> columns, final String noun) {
        final Set<String> names = new HashSet<>();
        for (final DataColumn column : columns) {
            if (!names.add(column.getName().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Data Connection '" + doc.getName() + "' has more than one "
                                                   + noun + " named '" + column.getName() + "'.");
            }
        }
    }
}
