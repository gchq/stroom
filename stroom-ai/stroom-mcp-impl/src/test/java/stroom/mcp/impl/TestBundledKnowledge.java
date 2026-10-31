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

package stroom.mcp.impl;

import stroom.ai.impl.tools.StroomTool;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// The guides, the server instructions and every tool's description name only tools that exist: knowledge lives in
/// several places, and this keeps it from drifting apart from the tools. Seen in testing: hints still sent the agent
/// to find_documents, find_streams and describe_document, which Stroom doesn't have.
class TestBundledKnowledge {

    // A word that looks like a tool's name: snake_case, a known verb first.
    private static final Pattern TOOL_LIKE = Pattern.compile("\\b([a-z]+_[a-z_]+)\\b");
    private static final Set<String> VERBS = Set.of("find", "get", "list", "describe", "create", "update", "copy",
            "set", "build", "check", "validate", "step", "compare", "profile", "upload", "record", "start", "write",
            "promote", "reprocess", "wait", "summarise", "survey", "locate", "read", "run", "propose", "draft", "test",
            "processing", "onboard", "index", "evaluate", "fix", "generate");
    // Words that look like tool names (snake_case, a known verb first) but are not tools: arguments, result fields,
    // settings and the like.
    private static final Set<String> NOT_TOOLS = Set.of("data_splitter", "record_start", "record_end",
            "xml_fragments", "json_layout", "xpath_default_namespace", "time_format", "any_of", "in_dictionary",
            "data_name", "xml_namespace", "key_xpath", "pair_separator", "body_field", "strip_domain",
            "event_logging_path", "type_id", "event_detail", "stream_type", "stream_ids", "loader_pipeline",
            "replace_parser", "output_type", "source_pipeline_uuid", "draft_code", "pipeline_uuid", "index_uuid",
            "index_itself", "records_per_stream", "skip_stream_ids", "known_signatures", "expect_events", "filter_id",
            "events_stream_ids",
            // An Elasticsearch privilege.
            "create_index",
            "source_confirmation_id", "confirmation_id", "approval_id", "field_mapping", "pipeline_link",
            "extra_fields", "index_name", "timestamp_field", "source_docs", "sample_size", "input_fields",
            "working_copy", "set_properties", "dev_tools", "set_properties_note", "xslt_input", "text_converter",
            "parser_properties", "variable_min_reads", "inline_map_max_keys", "snake_case", "manual_steps",
            "expected_paths", "raw_stream", "path_population", "sample_check", "fields_seen", "converter_type",
            "reference_data", "index_patterns", "event_types", "max_chars_per_stream", "max_parts_per_stream",
            "max_records", "ignore_warnings", "index_plan", "drop_when", "for_each", "mark_rules",
            "reuse_existing_docs", "accept_parser_mismatch", "build_status", "index_dropped",
            // Named in Stroom's own tools' descriptions: an argument, a state and kinds of dashboard change.
            "test_feed", "index_template", "set_expression", "set_setting", "set_vis_settings");
    // Tool names Stroom doesn't have, though hints once named them: Stroom's built-in tools do their work.
    private static final List<String> MERGED = List.of("find_documents", "describe_document", "find_streams",
            "describe_stream", "read_stream", "find_events", "describe_pipeline", "survey_index");

    private final List<StroomTool> tools = new EveryTool().tools();

    @Test
    void theGuidesAndInstructionsNameToolsThatExist() {
        final Set<String> names = toolNames();
        final Map<String, String> texts = new TreeMap<>();
        final BundledKnowledge knowledge = new BundledKnowledge();
        for (final String guide : BundledKnowledge.GUIDES) {
            texts.put("guide " + guide, knowledge.guide(guide).orElseThrow());
        }
        texts.put("the server instructions", StroomMcpServer.INSTRUCTIONS);

        assertThat(unknownTools(texts, names)).isEmpty();
    }

    @Test
    void everyToolsDescriptionNamesToolsThatExist() {
        final Set<String> names = toolNames();
        final Map<String, String> texts = new TreeMap<>();
        for (final StroomTool tool : tools) {
            final ToolDefinition definition = tool.definition();
            texts.put(definition.name(), definition.description());
            if (definition.parameters() != null) {
                addSchema(texts, definition.name() + " parameters", definition.parameters());
            }
        }

        assertThat(unknownTools(texts, names)).isEmpty();
    }

    @Test
    void theBuildInstructionsKeepTheAgentWorkingWithoutAskingFirst() {
        // The design's "The plan lives in the tools": every write's result carries next, and done false until
        // promotion, and the instructions tell the agent to keep going; the user is asked in the server's forms, or
        // for what only they can give, not in the chat first. The chat is given the same text as an MCP client, so
        // the rules hold whichever runs the model.
        final String text = String.join(" ", BundledKnowledge.serverInstructions().split("\\s+"));
        assertThat(text)
                .contains("One tool call is never the whole job: every write tool's result carries `next`, the "
                          + "plan's next step, and `done: false` until promotion")
                .contains("Keep going until `next` says promote.")
                .contains("end your turn only for the user (a form, a paste, `user_actions`, promotion)")
                .contains("propose it by calling the tool, not by asking in the chat first")
                .contains("Ask only what you can't decide from the result, the standing instructions or what the "
                          + "user has said, and not what a form from this server will ask; then carry on with "
                          + "their answers.");
        assertThat(new BundledKnowledge().buildInstructions()).contains(BundledKnowledge.serverInstructions());
    }

    @Test
    void noMergedToolIsATool() {
        // Each is now a built-in tool's part: find_documents is folder_contents name= (find_in_content for
        // content), describe_document is read_document (with document_links and document_documentation),
        // find_streams and describe_stream are stream_meta, read_stream is fetch_events streamId=, and find_events
        // is search_events index=.
        assertThat(toolNames()).doesNotContainAnyElementsOf(MERGED)
                .contains("folder_contents", "find_in_content", "read_document", "stream_meta", "fetch_events",
                        "search_events", "get_instructions", "write_documentation", "start_build");
    }

    @Test
    void noToolTakesOrMentionsAnIdThatApprovesACall() {
        // The model must never be handed an id it could pass back to approve its own call: the user agrees in a
        // form, and a client without forms is refused.
        final Set<String> idArguments = Set.of("confirmation_id", "approval_id", "source_confirmation_id",
                "template_confirmation_id");
        for (final StroomTool tool : tools) {
            final ToolDefinition definition = tool.definition();
            if (definition.parameters() != null) {
                assertThat(definition.parameters().properties().keySet()).as(definition.name())
                        .doesNotContainAnyElementsOf(idArguments);
            }
            assertThat(definition.description()).as(definition.name())
                    .doesNotContain("confirmation_id", "approval_id", "approve_call");
        }
        assertThat(toolNames()).doesNotContain("approve_call");
        assertThat(StroomMcpServer.INSTRUCTIONS).doesNotContain("approve_call", "needs_confirmation",
                "needs_approval", "pass the id back");
    }

    private Set<String> toolNames() {
        return tools.stream()
                .map(tool -> tool.definition().name())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    // Each text's tool-like words that are no tool's name, by where the text is.
    private static Map<String, Set<String>> unknownTools(final Map<String, String> texts, final Set<String> names) {
        final Map<String, Set<String>> unknown = new TreeMap<>();
        texts.forEach((where, text) -> {
            final Set<String> words = toolLike(text);
            words.removeAll(names);
            if (!words.isEmpty()) {
                unknown.put(where, words);
            }
        });
        return unknown;
    }

    private static Set<String> toolLike(final String text) {
        final Set<String> words = new TreeSet<>();
        final Matcher matcher = TOOL_LIKE.matcher(text == null
                ? ""
                : text);
        while (matcher.find()) {
            final String word = matcher.group(1);
            if (VERBS.contains(word.substring(0, word.indexOf('_'))) && !NOT_TOOLS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    // Every description in a parameter schema, by where it is.
    private static void addSchema(final Map<String, String> texts, final String where, final ToolSchema schema) {
        if (schema.description() != null) {
            texts.put(where, schema.description());
        }
        if (schema instanceof final ObjectSchema object) {
            object.properties().forEach((name, property) -> addSchema(texts, where + "." + name, property));
        } else if (schema instanceof final ArraySchema array && array.items() != null) {
            addSchema(texts, where + "[]", array.items());
        }
    }
}
