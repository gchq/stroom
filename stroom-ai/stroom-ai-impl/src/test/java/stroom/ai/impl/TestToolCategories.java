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

package stroom.ai.impl;

import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolSummary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Each built-in tool's category, as tool policies grant it: as the design settled them, so a tool that moves
/// category, or a new tool, is a change made on purpose.
class TestToolCategories {

    private static final Map<AiToolCategory, List<String>> EXPECTED = Map.of(
            AiToolCategory.CORE, List.of("ask_user", "delete_file", "document_documentation", "document_info",
                    "document_links", "find_in_content", "folder_contents", "get_current_date",
                    "get_current_datetime", "get_current_time", "get_instructions", "get_utc_time_offset", "grep",
                    "jq", "ls", "memory", "pipeline", "read_document", "read_file", "read_turn", "str_replace",
                    "summarise_column", "table", "task_list", "wc", "write_file", "xpath"),
            AiToolCategory.SEARCH, List.of("current_results", "describe_data_source", "fetch_events",
                    "list_data_sources", "run_stroomql", "search_events", "search_history", "validate_stroomql"),
            AiToolCategory.CONTENT, List.of("list_visualisations", "preview_extraction", "propose_code_change",
                    "propose_dashboard",
                    "propose_dashboard_change", "propose_documentation_change", "propose_extraction_pipeline",
                    "propose_query",
                    "propose_query_change", "propose_visualisation", "xslt_lookups"),
            AiToolCategory.INDEXING, List.of("elastic_index_templates"),
            AiToolCategory.PROCESSING, List.of("explain_processing", "processor_tasks", "stream_meta"),
            AiToolCategory.ADMINISTRATION, List.of("config_properties", "server_tasks"));

    @Test
    void testEachToolIsInItsCategory(@TempDir final Path tempDir) {
        final List<AiToolSummary> summaries;
        try (final BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            summaries = world.builtInTools().summaries();
        }

        final Map<AiToolCategory, List<String>> actual = summaries.stream()
                .collect(Collectors.groupingBy(AiToolSummary::getCategory,
                        Collectors.mapping(AiToolSummary::getName, Collectors.toList())));
        assertThat(actual).isEqualTo(EXPECTED);
    }

    @Test
    void testAToolFromAnotherServerIsInTheCategoryItsServerNames() {
        assertThat(McpToolset.categoryOf("search", null, true)).isEqualTo(AiToolCategory.SEARCH);
        assertThat(McpToolset.categoryOf("Administration", AiToolCategory.SEARCH, true))
                .isEqualTo(AiToolCategory.ADMINISTRATION);
        // A group that isn't a category gives way to the server's category, then Core.
        assertThat(McpToolset.categoryOf("streams", AiToolCategory.PROCESSING, true))
                .isEqualTo(AiToolCategory.PROCESSING);
        assertThat(McpToolset.categoryOf(null, AiToolCategory.CONTENT, true)).isEqualTo(AiToolCategory.CONTENT);
        assertThat(McpToolset.categoryOf("streams", null, true)).isEqualTo(AiToolCategory.CORE);
    }

    @Test
    void testAnUntrustedServerCannotPutItsToolsInAnotherCategory() {
        // Its _meta.group is only a hint: a server could name a category its users' policies grant for writing.
        assertThat(McpToolset.categoryOf("Core", AiToolCategory.ADMINISTRATION, false))
                .isEqualTo(AiToolCategory.ADMINISTRATION);
        assertThat(McpToolset.categoryOf("search", null, false)).isEqualTo(AiToolCategory.CORE);
    }

    @Test
    void testOnlyMemoryHasSomeCallsThatReadAndSomeThatChange(@TempDir final Path tempDir) {
        final BuiltInTools tools;
        try (final BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            tools = world.builtInTools();
        }
        assertThat(tools.summaries().stream()
                .map(AiToolSummary::getName)
                .filter(name -> !tools.isReadOnly(name) && tools.hasReadOnlyCalls(name))
                .toList())
                .containsExactly("memory");
    }
}
