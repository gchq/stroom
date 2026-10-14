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

import java.util.ArrayList;
import java.util.List;

/// Reads the markdown tables that table attachments are stored as: a header row, a separator row of dashes, then
/// one row per line. Cells are separated by `|`, and a `|` inside a cell is escaped as `\|`.
public final class MarkdownTables {

    private MarkdownTables() {
        // Static only.
    }

    /// @param line A table row, e.g. `| a | b \| c |  |`.
    /// @return The cells, unescaped and trimmed, e.g. `["a", "b | c", ""]`. Empty cells are kept, so the
    /// cells line up with the columns.
    public static List<String> parseRow(final String line) {
        final List<String> cells = new ArrayList<>();
        if (line == null) {
            return cells;
        }
        final String trimmed = line.strip();
        final StringBuilder cell = new StringBuilder();
        boolean started = false;
        for (int i = 0; i < trimmed.length(); i++) {
            final char chr = trimmed.charAt(i);
            if (chr == '\\' && i + 1 < trimmed.length() && trimmed.charAt(i + 1) == '|') {
                cell.append('|');
                i++;
            } else if (chr == '|') {
                if (started) {
                    cells.add(cell.toString().strip());
                }
                cell.setLength(0);
                started = true;
            } else {
                cell.append(chr);
            }
        }
        // Text after the last separator is a cell too, if the row does not end with a separator.
        if (!cell.toString().isBlank() || !started) {
            cells.add(cell.toString().strip());
        }
        return cells;
    }

    /// @param line A table row.
    /// @return True if the row is the separator row between the header and the data, e.g. `| --- | :-: |`.
    public static boolean isSeparatorRow(final String line) {
        final List<String> cells = parseRow(line);
        if (cells.isEmpty()) {
            return false;
        }
        for (final String cell : cells) {
            if (!cell.matches(":?-+:?")) {
                return false;
            }
        }
        return true;
    }
}
