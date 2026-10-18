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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestMarkdownTables {

    @Test
    void testParseRow() {
        assertThat(MarkdownTables.parseRow("| a | b |")).containsExactly("a", "b");
        // Empty cells are kept, so cells line up with their columns.
        assertThat(MarkdownTables.parseRow("| a |  | c |")).containsExactly("a", "", "c");
        // Escaped pipes are part of the cell.
        assertThat(MarkdownTables.parseRow("| a \\| b | c |")).containsExactly("a | b", "c");
        // A row need not end with a separator.
        assertThat(MarkdownTables.parseRow("| a | b")).containsExactly("a", "b");
        assertThat(MarkdownTables.parseRow(null)).isEmpty();
    }

    @Test
    void testIsSeparatorRow() {
        assertThat(MarkdownTables.isSeparatorRow("| --- | --- |")).isTrue();
        assertThat(MarkdownTables.isSeparatorRow("|:--|:-:|--:|")).isTrue();
        assertThat(MarkdownTables.isSeparatorRow("| a | --- |")).isFalse();
        assertThat(MarkdownTables.isSeparatorRow("|  |")).isFalse();
    }

    @Test
    void rowsAreCountedWithoutTheHeaderAndSeparator(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("table.md");
        Files.writeString(file, "| a | b |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |\n\n");
        assertThat(MarkdownTables.countRows(file)).isEqualTo(2);

        Files.writeString(file, "| a | b |\n| --- | --- |\n");
        assertThat(MarkdownTables.countRows(file)).isZero();
    }

    @Test
    void emptyIdColumnsAreDropped(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("table.md");
        // From a data source without streams, e.g. reference data: the ids are always empty.
        Files.writeString(file, """
                | Key | Value \\| note | StreamId | EventId |
                | --- | --- | --- | --- |
                | a | 1 |  |  |
                | b | 2 |  |  |
                """);

        assertThat(MarkdownTables.dropEmptyColumns(file, Set.of("StreamId", "EventId"))).isTrue();

        assertThat(Files.readString(file)).isEqualTo("""
                | Key | Value \\| note |
                | --- | --- |
                | a | 1 |
                | b | 2 |
                """);
    }

    @Test
    void idColumnsWithAnyValueAreKept(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("table.md");
        // Stream ids, but no event ids, e.g. a table of streams.
        final String table = """
                | Feed | StreamId | EventId |
                | --- | --- | --- |
                | ACME | 12 |  |
                | ACME |  |  |
                """;
        Files.writeString(file, table);

        assertThat(MarkdownTables.dropEmptyColumns(file, Set.of("StreamId", "EventId"))).isTrue();
        assertThat(Files.readString(file)).isEqualTo("""
                | Feed | StreamId |
                | --- | --- |
                | ACME | 12 |
                | ACME |  |
                """);

        // Nothing to drop leaves the file as it is.
        Files.writeString(file, table.replace("| ACME |  |  |", "| ACME | 13 | 4 |"));
        final String before = Files.readString(file);
        assertThat(MarkdownTables.dropEmptyColumns(file, Set.of("StreamId", "EventId"))).isFalse();
        assertThat(Files.readString(file)).isEqualTo(before);
    }

}
