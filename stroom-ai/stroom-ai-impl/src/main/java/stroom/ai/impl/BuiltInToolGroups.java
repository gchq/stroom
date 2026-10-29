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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The groups the built-in tools that only some questions need are offered in (see [ToolGroups]): the model is
/// given a tool to turn each group on, rather than every tool's definition with every request. Seen: the 103
/// built-in tools' definitions took about 51k tokens of a 131k context window before the question, so a long
/// question had its steps shortened at every step.
///
/// A tool in no group (the core, search and file tools, and the small proposals a tab's guidance relies on) is
/// always offered. A tool added later is in no group until it is put in one here.
public final class BuiltInToolGroups {

    static final String BUILD = "build";
    static final String PROCESSING = "processing";
    static final String INDEXING = "indexing";
    static final String DASHBOARDS = "dashboards";
    static final String ADMINISTRATION = "administration";

    /// What each group is for, as the tool that turns it on says, in the order the groups are offered.
    static final Map<String, String> PURPOSES = purposes();

    private static final Map<String, String> GROUP_BY_TOOL = groupByTool(Map.of(
            BUILD, List.of(
                    "start_onboarding",
                    "start_build",
                    "build_status",
                    "find_pipeline_templates",
                    "describe_template",
                    "create_feed",
                    "upload_sample",
                    "profile_sample",
                    "survey_feed",
                    "summarise_streams",
                    "record_source_notes",
                    "draft_translation_mapping",
                    "draft_cef_mapping",
                    "build_translation_xslt",
                    "build_reference_xslt",
                    "rebuild_mapping",
                    "save_xslt",
                    "check_xslt",
                    "check_events",
                    "build_data_splitter",
                    "save_text_converter",
                    "save_dictionary",
                    "create_pipeline",
                    "update_pipeline",
                    "copy_pipeline",
                    "promote_build",
                    "review_coverage",
                    "write_documentation",
                    "set_shape_handling"),
            PROCESSING, List.of(
                    "step_sample",
                    "step_records",
                    "step_pipeline",
                    "locate_event",
                    "compare_outputs",
                    "summarise_fix",
                    "create_processor_filter",
                    "set_processor_filter_enabled",
                    "reprocess_streams",
                    "wait_for_processing",
                    "processing_status",
                    "processor_tasks",
                    "explain_processing"),
            INDEXING, List.of(
                    "draft_index_mapping",
                    "verify_index",
                    "propose_index_template",
                    "check_index_template",
                    "create_indexing_pipeline",
                    "create_index_doc",
                    "rename_index",
                    "get_field_conventions",
                    "find_elastic_clusters",
                    "elastic_index_templates"),
            DASHBOARDS, List.of(
                    "propose_dashboard",
                    "propose_dashboard_change",
                    "propose_visualisation",
                    "list_visualisations",
                    "preview_extraction",
                    "propose_extraction_pipeline"),
            ADMINISTRATION, List.of(
                    "config_properties",
                    "server_tasks")));

    private BuiltInToolGroups() {
        // Static only.
    }

    /// @param toolName A built-in tool's name.
    /// @return The group it is offered in, or empty if it is always offered.
    static Optional<String> groupOf(final String toolName) {
        return Optional.ofNullable(GROUP_BY_TOOL.get(toolName));
    }

    /// @return The names of the tools in a group, for a test to check that each is a tool.
    public static Set<String> groupedTools() {
        return GROUP_BY_TOOL.keySet();
    }

    /// @param systemPrompt The system prompt a question is asked with, or null.
    /// @return The groups its guidance needs from the start: a dashboard tab's guidance is about the dashboard
    /// tools, so the model isn't made to turn them on before it can follow it.
    static Set<String> neededBy(final String systemPrompt) {
        return systemPrompt != null && systemPrompt.contains(ContextAssembler.DASHBOARD_GUIDANCE)
                ? Set.of(DASHBOARDS)
                : Set.of();
    }

    private static Map<String, String> purposes() {
        final Map<String, String> purposes = new LinkedHashMap<>();
        purposes.put(BUILD, "onboard a feed, or build or change a translation pipeline: samples, source notes, "
                            + "mappings, XSLT, text converters, dictionaries, pipelines, documentation and "
                            + "promoting a build");
        purposes.put(PROCESSING, "step pipelines and records, create or change processor filters, reprocess "
                                 + "streams, see processor tasks, and find out why processing isn't happening");
        purposes.put(INDEXING, "plan, make and check search indexes (Lucene or Elasticsearch), their index "
                               + "templates and their indexing pipelines");
        purposes.put(DASHBOARDS, "make or change dashboards, their charts and visualisations, and the search "
                                 + "extractions their tables use");
        purposes.put(ADMINISTRATION, "read Stroom's configuration properties, and the tasks running on its nodes");
        return Collections.unmodifiableMap(purposes);
    }

    private static Map<String, String> groupByTool(final Map<String, List<String>> toolsByGroup) {
        final Map<String, String> groupByTool = new LinkedHashMap<>();
        toolsByGroup.forEach((group, tools) -> tools.forEach(tool -> {
            if (groupByTool.put(tool, group) != null) {
                throw new IllegalStateException("Tool '" + tool + "' is in more than one group");
            }
        }));
        return Map.copyOf(groupByTool);
    }
}
