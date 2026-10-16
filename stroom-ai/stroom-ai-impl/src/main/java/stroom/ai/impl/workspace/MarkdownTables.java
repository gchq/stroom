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
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /// Removes the named columns from a Markdown table file if they are empty in every row, e.g. the stream and
    /// event ids of a table from a data source that has none.
    ///
    /// @param file  A file holding one Markdown table: a header row, a separator row, then the rows.
    /// @param names The columns to remove if empty, by their headings.
    /// @return True if the file was changed.
    /// @throws UncheckedIOException if the file can't be read or written.
    public static boolean dropEmptyColumns(final Path file, final Set<String> names) {
        try {
            final List<String> header;
            try (final BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                header = parseRow(reader.readLine());
            }
            final Set<Integer> empty = new HashSet<>();
            for (int i = 0; i < header.size(); i++) {
                if (names.contains(header.get(i))) {
                    empty.add(i);
                }
            }
            if (empty.isEmpty()) {
                return false;
            }
            try (final BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                reader.readLine();
                reader.readLine();
                String line;
                while (!empty.isEmpty() && (line = reader.readLine()) != null) {
                    final List<String> cells = parseRow(line);
                    empty.removeIf(index -> index < cells.size() && !cells.get(index).isBlank());
                }
            }
            if (empty.isEmpty()) {
                return false;
            }
            final Path rewritten = file.resolveSibling(file.getFileName() + ".tmp");
            try (final BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                    final BufferedWriter writer = Files.newBufferedWriter(rewritten, StandardCharsets.UTF_8)) {
                writer.write(row(without(parseRow(reader.readLine()), empty)));
                reader.readLine();
                writer.write("|" + " --- |".repeat(header.size() - empty.size()) + "\n");
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(row(without(parseRow(line), empty)));
                }
            }
            Files.move(rewritten, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> without(final List<String> cells, final Set<Integer> indexes) {
        final List<String> kept = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (!indexes.contains(i)) {
                kept.add(cells.get(i));
            }
        }
        return kept;
    }

    private static String row(final List<String> cells) {
        final StringBuilder sb = new StringBuilder("|");
        for (final String cell : cells) {
            sb.append(' ').append(cell.replace("|", "\\|")).append(" |");
        }
        return sb.append('\n').toString();
    }
}
