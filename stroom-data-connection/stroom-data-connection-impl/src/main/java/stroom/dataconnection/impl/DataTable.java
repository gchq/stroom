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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The rows of data held in a document, such as CSV or an Excel sheet, as text.
///
/// @param header The names in the header row, or empty if there is none. A name may be `null` for an
///               empty header cell.
/// @param rows   The rows after the header, each a list of its values. A value may be `null` for an
///               empty cell.
record DataTable(List<String> header, List<List<String>> rows) {

    DataTable {
        header = Collections.unmodifiableList(new ArrayList<>(header));
        rows = List.copyOf(rows);
    }

    /// @return The number of columns: the most values in the header or any row.
    int columnCount() {
        int count = header.size();
        for (final List<String> row : rows) {
            count = Math.max(count, row.size());
        }
        return count;
    }
}
