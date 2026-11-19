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

package stroom.widget.datagrid.client.model;

import java.util.List;

/// The text and CSV rules the grid's Copy, Export and Ask AI menu items use (design R-M3),
/// replicating `MyDataGrid`'s.
public final class TableText {

    private TableText() {
        // Utility class.
    }

    /// @param text Some text, possibly `null`.
    /// @return The text with each run of whitespace, including newlines, replaced by one space, and
    /// leading and trailing whitespace removed. `null` becomes an empty string.
    public static String normaliseWhitespace(final String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    /// @param text A cell's text, possibly `null`.
    /// @return The text as a CSV value: newlines become spaces, and a value containing a comma or a
    /// double quote is quoted, with its quotes doubled.
    public static String escapeCsv(final String text) {
        if (text == null) {
            return "";
        }
        final String cleaned = text.replace('\n', ' ');
        if (cleaned.contains(",") || cleaned.contains("\"")) {
            return "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }

    /// Leading columns whose header has no text (tick boxes, expanders, icons) are left out of
    /// copied rows and exports.
    ///
    /// @param headers The header text of every column, in order.
    /// @return The number of leading columns to leave out.
    public static int columnOffset(final List<String> headers) {
        int offset = 0;
        for (final String header : headers) {
            if (escapeCsv(normaliseWhitespace(header)).isEmpty()) {
                offset++;
            } else {
                break;
            }
        }
        return offset;
    }

    /// @param cells The cells of one row.
    /// @return The cells as one CSV line.
    public static String toCsvLine(final List<String> cells) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escapeCsv(cells.get(i)));
        }
        return sb.toString();
    }

    /// @param lines Lines of text.
    /// @return The lines joined with newlines. Empty lines are kept.
    public static String joinLines(final List<String> lines) {
        return String.join("\n", lines);
    }
}
