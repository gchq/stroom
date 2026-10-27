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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// What each built-in tool does, in a line, for an administrator choosing what a tool policy grants. A tool's own
/// description is written for the agent that calls it, so it is too long, and says too much about how to call it,
/// to show in the MCP Tool Policies screen. Every built-in tool needs one here.
public final class ToolSummaries {

    private static final Map<String, String> SUMMARIES = Map.ofEntries(
            Map.entry("ask_user", "Asks the user questions, with options to pick from, and waits for the answers."),
            Map.entry("build_data_splitter", "Onboarding: writes a Data Splitter text converter from the sample."),
            Map.entry("build_reference_xslt", "Writes the XSLT of a reference data pipeline from a mapping."),
            Map.entry("build_status", "Onboarding: shows where a build stands, step by step."),
            Map.entry("build_translation_xslt",
                    "Onboarding: writes an event-logging translation XSLT from a field mapping."),
            Map.entry("check_events", "Onboarding: validates event XML against the event-logging schema."),
            Map.entry("check_index_template", "Onboarding: checks an Elasticsearch index template."),
            Map.entry("check_xslt", "Checks an XSLT for errors before it is saved or stepped."),
            Map.entry("compare_outputs",
                    "Steps records through a pipeline and a changed version of it, and compares the output."),
            Map.entry("config_properties", "Reads Stroom's system properties."),
            Map.entry("copy_pipeline", "Copies an existing pipeline into the build."),
            Map.entry("create_feed", "Onboarding: creates a feed in the build folder."),
            Map.entry("create_index_doc", "Onboarding: creates a Lucene or Elastic index document."),
            Map.entry("create_indexing_pipeline", "Onboarding: creates an indexing pipeline from a template."),
            Map.entry("create_pipeline", "Onboarding: creates a pipeline from a template."),
            Map.entry("create_processor_filter",
                    "Onboarding: creates a processor filter to process streams through the pipeline."),
            Map.entry("current_results", "Takes a snapshot of a table of results shown in a query or dashboard."),
            Map.entry("delete_file", "Deletes a file in the chat's workspace."),
            Map.entry("describe_data_source", "Lists a data source's fields and the conditions each supports."),
            Map.entry("describe_feed", "Describes what a feed holds and what its fields mean."),
            Map.entry("describe_template", "Onboarding: describes how a pipeline template is used."),
            Map.entry("document_documentation", "Reads a document's documentation."),
            Map.entry("document_info", "Describes a document: its type, path, tags and history."),
            Map.entry("document_links", "Follows the dependencies between documents."),
            Map.entry("draft_cef_mapping", "Drafts a mapping of events to CEF (ArcSight Common Event Format)."),
            Map.entry("draft_index_mapping", "Onboarding: drafts the fields of an index."),
            Map.entry("draft_translation_mapping", "Onboarding: drafts a translation mapping from the sample."),
            Map.entry("elastic_index_templates", "Reads the Elasticsearch templates of an Elastic Index's index."),
            Map.entry("fetch_events", "Fetches the source events behind the rows of a table of results."),
            Map.entry("find_elastic_clusters", "Lists the Elastic Cluster documents and the indexes that use them."),
            Map.entry("find_in_content", "Finds text in the content of documents."),
            Map.entry("find_pipeline_templates", "Onboarding: lists the pipeline templates."),
            Map.entry("find_reference_data", "Lists the reference data maps that can be looked up."),
            Map.entry("folder_contents", "Lists what is in an Explorer folder."),
            Map.entry("get_current_date", "Gives today's date (UTC)."),
            Map.entry("get_current_datetime", "Gives the current date and time (UTC)."),
            Map.entry("get_current_time", "Gives the current time of day (UTC)."),
            Map.entry("get_field_conventions", "Onboarding: gives the field naming conventions for indexes."),
            Map.entry("get_instructions",
                    "Reads the standing instructions (AGENTS documents) for folders, feeds or documents."),
            Map.entry("get_utc_time_offset", "Gives a time zone's current offset from UTC."),
            Map.entry("grep", "Finds the lines of workspace files that contain some text."),
            Map.entry("jq", "Runs a jq query on a JSON file."),
            Map.entry("list_data_sources", "Lists the data sources the user can query."),
            Map.entry("list_visualisations", "Lists the charting libraries, chart kinds and Visualisations."),
            Map.entry("locate_event", "Traces a stream or event back to its raw data, pipeline and XSLTs."),
            Map.entry("ls", "Lists the files in the chat's workspace."),
            Map.entry("memory", "Lists, reads and changes the agent's memory of the user and of the chat."),
            Map.entry("pipeline", "Runs data tool steps one after another on workspace files."),
            Map.entry("preview_extraction",
                    "Tries a search extraction for a dashboard table on some of its events, changing nothing."),
            Map.entry("processing_status", "Shows a pipeline's processor filters and their progress."),
            Map.entry("processor_tasks", "Shows processing failures, backlogs and tasks across the cluster."),
            Map.entry("profile_sample", "Profiles raw sample data: its format, fields and timestamps."),
            Map.entry("promote_build", "Onboarding: promotes a build out of the workspace, once approved."),
            Map.entry("propose_code_change", "Proposes a change to the code being stepped, for the user to accept."),
            Map.entry("propose_dashboard", "Offers the user a button to create a dashboard from a query."),
            Map.entry("propose_dashboard_change", "Proposes a change to a dashboard table, for the user to accept."),
            Map.entry("propose_documentation_change",
                    "Proposes a change to a document's documentation, for the user to accept."),
            Map.entry("propose_index_template", "Onboarding: builds the final Elasticsearch index template."),
            Map.entry("propose_extraction_pipeline",
                    "Offers a button to make a dashboard table's search extraction, once the user confirms it."),
            Map.entry("propose_query", "Offers the user a button to create a query."),
            Map.entry("propose_query_change", "Proposes a change to a query, for the user to accept."),
            Map.entry("propose_visualisation",
                    "Plans a chart of a table and offers a button to create it on the dashboard."),
            Map.entry("read_document", "Reads the content of a document."),
            Map.entry("read_file", "Reads the lines of a workspace file."),
            Map.entry("read_turn", "Reads an earlier turn of the chat again."),
            Map.entry("rebuild_mapping", "Rebuilds a translation's mapping from its XSLT."),
            Map.entry("record_source_notes",
                    "Keeps vendor reference documentation, and notes made from it, in the build."),
            Map.entry("rename_index", "Onboarding: changes the Elasticsearch index name the build uses."),
            Map.entry("reprocess_streams", "Processes streams again through a pipeline under development."),
            Map.entry("review_coverage", "Finds the kinds of event in a feed that its sample missed."),
            Map.entry("run_stroomql", "Runs a StroomQL query."),
            Map.entry("save_dictionary", "Saves a Dictionary document."),
            Map.entry("save_text_converter", "Onboarding: saves a text converter."),
            Map.entry("save_xslt", "Onboarding: saves an XSLT."),
            Map.entry("search_events", "Searches each event in a file, giving a table of the results."),
            Map.entry("search_history", "Searches the chat's history for some text."),
            Map.entry("server_tasks", "Shows the tasks running on every node."),
            Map.entry("set_processor_filter_enabled", "Disables a processor filter the build created."),
            Map.entry("set_shape_handling", "Records that some kinds of event are left untranslated on purpose."),
            Map.entry("start_build", "Onboarding: creates, or finds, the build's workspace folder."),
            Map.entry("start_onboarding", "Onboarding: starts onboarding a source from sample files."),
            Map.entry("step_pipeline", "Onboarding: steps one record through a pipeline, element by element."),
            Map.entry("step_records", "Steps chosen records of existing streams through a pipeline."),
            Map.entry("step_sample", "Onboarding: steps every record of the sample streams."),
            Map.entry("str_replace", "Replaces some text in a workspace file."),
            Map.entry("stream_meta", "Finds streams and lists their meta data."),
            Map.entry("summarise_column", "Summarises a free-text column of a table as patterns with counts."),
            Map.entry("summarise_fix", "Proves a fix by stepping streams with it, and sums it up for the user."),
            Map.entry("summarise_streams", "Summarises the errors or events of streams."),
            Map.entry("survey_feed", "Samples a feed's streams over its lifetime, grouping events by kind."),
            Map.entry("table", "Filters, groups and aggregates a table."),
            Map.entry("task_list", "Keeps a task list that the user sees while the agent works."),
            Map.entry("update_pipeline", "Changes a pipeline the build created."),
            Map.entry("upload_sample", "Onboarding: sends sample data to a feed."),
            Map.entry("validate_stroomql", "Checks a StroomQL query without running it."),
            Map.entry("verify_index", "Onboarding: verifies indexed events through Stroom."),
            Map.entry("wait_for_processing", "Onboarding: waits for processing to finish and reports the output."),
            Map.entry("wc", "Counts the lines and bytes of a workspace file."),
            Map.entry("write_documentation", "Onboarding: writes the documentation of a pipeline or index."),
            Map.entry("write_file", "Writes a workspace file."),
            Map.entry("xpath", "Runs an XPath expression on an XML file."),
            Map.entry("xslt_lookups", "Lists the reference data lookups an XSLT makes."));

    private ToolSummaries() {
        // Static only.
    }

    /// @param toolName A built-in tool's name, or null.
    /// @return What it does, in a line, or empty if it has no summary here.
    public static Optional<String> of(final String toolName) {
        return toolName == null
                ? Optional.empty()
                : Optional.ofNullable(SUMMARIES.get(toolName));
    }

    /// @return The names of the tools with a summary.
    public static Set<String> toolNames() {
        return SUMMARIES.keySet();
    }
}
