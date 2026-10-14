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
}
