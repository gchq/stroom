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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// Writes the tables the stream tools make to the workspace as CSV, for the table tool to work on, and shows the
/// model their first rows.
final class ToolTables {

    /// The longest a cell is shown in a preview.
    static final int MAX_PREVIEW_CELL_CHARS = 200;

    private ToolTables() {
        // Static only.
    }

    /// Writes a table to a workspace file as CSV.
    ///
    /// @param workspace     The chat's workspace.
    /// @param chatId        The chat.
    /// @param out           The file to write.
    /// @param columns       The column headings.
    /// @param rows          The rows, each with a cell per column.
    /// @param sourceDocUuid The document the table came from, so access to it is checked again when the file is
    ///                      used, or null.
    static void writeCsv(final ChatWorkspace workspace,
                         final int chatId,
                         final WorkspacePath out,
                         final List<String> columns,
                         final List<List<String>> rows,
                         final String sourceDocUuid) {
        final Path temp = workspace.tempFile();
        try {
            try (final BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8);
                    final CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
                printer.printRecord(columns);
                for (final List<String> row : rows) {
                    printer.printRecord(row);
                }
            }
            workspace.write(chatId, out, temp, sourceDocUuid);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            ChatWorkspace.deleteQuietly(temp);
        }
    }

    /// Shows the first rows of a table, as Markdown.
    ///
    /// @param columns The column headings.
    /// @param rows    The rows.
    /// @param maxRows The most rows to show.
    /// @return The table's first rows, with a note of how many more there are.
    static String preview(final List<String> columns, final List<List<String>> rows, final int maxRows) {
        final StringBuilder sb = new StringBuilder(row(columns));
        sb.append('|').append(" --- |".repeat(columns.size())).append('\n');
        rows.stream().limit(maxRows).forEach(row -> sb.append(row(row)));
        if (rows.size() > maxRows) {
            sb.append("\n(").append(rows.size() - maxRows).append(" more rows in the file.)");
        }
        return sb.toString().strip();
    }

    private static String row(final List<String> cells) {
        final StringBuilder sb = new StringBuilder("|");
        for (final String cell : cells) {
            final String value = cell == null
                    ? ""
                    : cell;
            final String shortened = value.length() > MAX_PREVIEW_CELL_CHARS
                    ? value.substring(0, MAX_PREVIEW_CELL_CHARS) + "…"
                    : value;
            sb.append(' ').append(shortened.replace("|", "\\|").replaceAll("\\s+", " ")).append(" |");
        }
        return sb.append('\n').toString();
    }
}
