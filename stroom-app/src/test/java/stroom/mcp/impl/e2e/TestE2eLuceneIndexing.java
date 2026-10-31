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

package stroom.mcp.impl.e2e;

import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.PermissionInheritance;
import stroom.index.impl.IndexShardService;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.mcp.impl.McpTestClient;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_lucene_indexing.py`: indexing on the Lucene backend end to end, driving the REAL
/// tools on a real Stroom. Every part of the script is covered: stage 1 from the translation suite (the CSV build,
/// `e2e_translation.py`'s `onboard`); then convention guidance, the field plan, the Lucene index doc and its fields,
/// the indexing XSLT and pipeline from the `Indexing` template, stepping the Events, processing, the verification
/// dashboard and test searches (each hit traced to its event), reprocessing into the build's own index (emptied
/// first), documentation; a v2 copy adding one field, a diff limited to that field, v2 verified beside v1; then
/// promotion.
///
/// Where the script called the server's internals, the tool that does the same is called: `save_xslt` with the code
/// (its `create_xslt`) or with `index_plan` (the plan's XSLT), `verify_index` (its `create_verification_dashboard` and
/// `run_test_searches`). Its second `set_index_fields` (which must add nothing) has no tool: `create_index_doc` adds
/// the session's plan's fields, which is checked. Stroom's REST reads of the index's shards are read through its
/// index shard service.
@SuppressWarnings("unchecked")
class TestE2eLuceneIndexing extends AbstractE2eTest {

    // The run stamp the scripts put in every name (`run_stamp()`); the test database is fresh for each run.
    private static final String STAMP = "101";
    private static final String SOURCE_USER = "EventSource/User/Id";

    @Inject
    private IndexShardService indexShardService;
    @Inject
    private ExplorerService explorerService;
    @Inject
    private ExplorerNodeService explorerNodeService;

    @Test
    void luceneIndexingFromConventionToPromotion() {
        final Onboarded csv = onboard(CSV_CASE);
        final Indexed v1 = indexStage(csv);
        versionTwo(csv, v1);
        promotion(csv);
    }

    /// Beyond the script, which calls each tool itself: stage 2 on Lucene with the plan's `next` followed as an agent
    /// would, each call made as `next` gives it (the agent filling in only what it names in angle brackets), from the
    /// events stage's documentation to promotion. It pins what `dev/FINDINGS.md` says of the plan's indexing steps:
    /// `next` after create_indexing_pipeline names step_sample first, not create_processor_filter, which would refuse
    /// it ("A new indexing pipeline"); the template step isn't "to do" on Lucene ("Lucene"); `indexed` is done only
    /// once verify_index passes ("The plan"). None of the calls is refused, and none leaves the agent waiting.
    @Test
    void followingNextThroughIndexingOnLuceneLeadsOnWithoutARefusal() {
        final String build = "onboard-acme-logons";
        final String indexName = "ACME-LOGONS-INDEX";
        final EventsStage stage = eventsStageByMapping(build);
        final Map<String, Object> documented = stage.documented();
        final List<Object> events = stage.events();
        assertThat(nextStep(documented)).as("the events stage done: indexing is next").isEqualTo("index");
        final List<String> steps = new ArrayList<>();
        Map<String, Object> result = documented;
        Map<String, Object> plan = null;
        String indexUuid = null;
        String pipelineUuid = null;
        for (int turn = 0; turn < 20 && nextStep(result) != null; turn++) {
            final Map<String, Object> next = map(result, "next");
            final String step = (String) next.get("step");
            steps.add(step);
            assertThat(step).as("the template step isn't to do on Lucene").isNotEqualTo("index_template");
            final Map<String, Object> call = map(next, "call");
            final String tool = (String) call.get("tool");
            final Map<String, Object> arguments = new LinkedHashMap<>((Map<String, Object>) call.get("arguments"));
            dump(step + ": " + tool, arguments);
            if ("promote_build".equals(tool)) {
                arguments.put("destinations", Map.of("Feed", "System/Acme", "Pipeline", "System/Acme",
                        "XSLT", "System/Acme", "TextConverter", "System/Acme", "Documentation", "System/Acme",
                        "Index", "System/Acme", "Dashboard", "System/Acme"));
                result = agreed(tool, arguments);
                assertThat((List<?>) result.get("promoted")).as("promoted").isNotEmpty();
                break;
            }
            if ("get_field_conventions".equals(tool)) {
                // The user picks how the fields are named, in its form; the agent then drafts and makes the index
                // doc as its reply says.
                client.answeringWith(choosing("Stroom flat convention"));
                final Map<String, Object> chosen = client.json(tool, arguments);
                client.answeringWith(McpTestClient::agree);
                assertThat(chosen).as("asked in a form, not left to the agent").containsEntry("status", "chosen");
                plan = map(client.json("draft_index_mapping", Map.of("backend", "lucene", "index_name", indexName,
                        "convention", chosen.get("convention"), "events_stream_ids", events)), "plan");
                result = agreed("create_index_doc", Map.of("build", build, "backend", "lucene", "name", indexName,
                        "time_field", plan.get("time_field")));
                indexUuid = uuidOf(result);
                continue;
            }
            if ("save_xslt".equals(tool)) {
                arguments.put("name", indexName + "-XSLT");
                arguments.put("index_plan", plan);
                final Map<String, Object> saved = client.json(tool, arguments);
                final Map<String, Object> template = templates("indexing").stream()
                        .filter(candidate -> "Indexing".equals(candidate.get("name")))
                        .findFirst()
                        .orElseThrow();
                // As next's hint says: then create_indexing_pipeline with that XSLT and the index.
                result = agreed("create_indexing_pipeline", Map.of("build", build, "name", indexName + " - Indexing",
                        "template_uuid", template.get("uuid"), "xslt_uuid", uuidOf(saved), "index_uuid", indexUuid));
                pipelineUuid = uuidOf(result);
                assertThat(map(result, "next")).as("a new indexing pipeline: step_sample first")
                        .containsEntry("step", "indexed")
                        .satisfies(n -> assertThat(map(n, "call")).containsEntry("tool", "step_sample"));
                continue;
            }
            if ("verify_index".equals(tool)) {
                // The Events records, and the columns the user chose.
                arguments.put("expected_documents", 3);
                arguments.put("fields", List.of("EventTime", "UserId", "HostName"));
            }
            if ("write_documentation".equals(tool)) {
                // The agent's own text, from the documentation guide.
                arguments.put("markdown", "# " + indexName + " - Indexing\n\n## Purpose and data\n\nAcme's logons, "
                                          + "indexed into Lucene.\n");
            }
            assertThat(arguments.values()).as("%s's arguments, all given by next", tool)
                    .noneMatch(value -> String.valueOf(value).startsWith("<"));
            final Called called = callText(tool, arguments);
            assertThat(called.error()).as("%s, as next gave it, is not refused: %s", tool, called.text()).isFalse();
            result = called.json();
            if ("create_processor_filter".equals(tool)) {
                processAll();
                flushIndexShards();
            }
        }
        assertThat(pipelineUuid).isNotNull();
        assertThat(steps).as("the plan's steps, in order, the agent never sent back")
                .containsSubsequence("index", "indexing_pipeline", "indexed", "index_documented", "promoted");
    }

    // The events stage, as TestOnboardingEndToEnd builds it from the mapping (the plan's own path), up to its
    // documentation: the result whose next is indexing.
    private EventsStage eventsStageByMapping(final String build) {
        client.json("start_onboarding", Map.of("source_name", "Acme logons", "samples", List.of(CSV_SAMPLE)));
        client.json("create_feed", Map.of("build", build, "name", "ACME-LOGONS"));
        final long raw = ((Number) client.json("upload_sample", Map.of("feed", "ACME-LOGONS", "sample", CSV_SAMPLE))
                .get("stream_id")).longValue();
        client.json("build_data_splitter", Map.of("stream_ids", List.of(raw), "build", build,
                "save_as", "ACME-LOGONS-Converter"));
        final Map<String, Object> draft = client.json("draft_translation_mapping", Map.of("stream_ids",
                List.of(raw)));
        final Map<String, Object> mapping = new LinkedHashMap<>(map(draft, "mapping"));
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final Map<String, Object> entry : (List<Map<String, Object>>) mapping.get("common")) {
            final Map<String, Object> settled = new LinkedHashMap<>(entry);
            switch (String.valueOf(entry.get("path"))) {
                case "EventSource/System/Name" -> settled.put("value", "Acme");
                case "EventSource/System/Environment" -> settled.put("value", "Test");
                case "EventSource/Generator" -> settled.put("value", "acme-auth");
                default -> {
                }
            }
            common.add(settled);
        }
        mapping.put("common", common);
        mapping.put("events", List.of(Map.of("name", "logon", "fields", List.of(
                Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                Map.of("path", "EventDetail/Description", "value", "User logon"),
                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user"),
                Map.of("path", "EventDetail/Authenticate/Outcome/Success", "field", "result",
                        "map", Map.of("ok", "true", "fail", "false"))))));
        client.json("build_translation_xslt", Map.of("build", build, "name", "ACME-LOGONS-Events",
                "mapping", mapping, "schema_version", draft.get("schema_version"), "stream_ids", List.of(raw),
                "splitter", draft.get("splitter")));
        final String templateUuid = templates("translation").stream()
                .filter(candidate -> "Event Data (Text)".equals(candidate.get("name")))
                .map(candidate -> (String) candidate.get("uuid"))
                .findFirst()
                .orElseThrow();
        final String pipelineUuid = uuidOf(client.json("create_pipeline", Map.of("build", build,
                "name", "ACME-LOGONS-Events", "template_uuid", templateUuid)));
        client.json("step_sample", Map.of("pipeline_uuid", pipelineUuid, "stream_ids", List.of(raw)));
        client.json("create_processor_filter", Map.of("pipeline_uuid", pipelineUuid, "stream_ids", List.of(raw)));
        processAll();
        final Map<String, Object> processed = client.json("wait_for_processing", Map.of(
                "pipeline_uuid", pipelineUuid, "stream_ids", List.of(raw), "timeout_seconds", 10));
        final List<Object> events = (List<Object>) ((List<Map<String, Object>>) processed.get("streams"))
                .getFirst().get("events");
        client.json("check_events", Map.of("stream_ids", events));
        return new EventsStage(agreed("write_documentation", Map.of("build", build, "pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw), "markdown", "# ACME-LOGONS-Events\n\n## Purpose and data\n\nAcme's "
                                                        + "logons.\n", "change", "Created")), events);
    }

    private Called callText(final String tool, final Map<String, Object> arguments) {
        final CallToolResult called = client.call(tool, arguments);
        final String text = McpTestClient.text(called);
        final boolean error = Boolean.TRUE.equals(called.isError());
        Map<String, Object> json = Map.of();
        if (called.structuredContent() instanceof final Map<?, ?> structured) {
            json = (Map<String, Object>) structured;
        } else if (!error) {
            json = JsonUtil.readValue(text, Map.class);
        }
        if (!error && "needs_descriptions".equals(json.get("status"))) {
            json = agreed(tool, arguments);
        }
        return new Called(error, text, json);
    }

    private Indexed indexStage(final Onboarded csv) {
        final Map<String, Object> waited = client.json("wait_for_processing", Map.of(
                "pipeline_uuid", csv.pipelineUuid(), "stream_ids", List.of(csv.raw()), "timeout_seconds", 10));
        final List<Long> events = ((List<Number>) ((List<Map<String, Object>>) waited.get("streams")).getFirst()
                .get("events")).stream().map(Number::longValue).toList();

        // No convention chosen: the tool asks (here in a form, the user picking Stroom flat) instead of assuming one.
        client.answeringWith(choosing("Stroom flat convention"));
        final int formsBefore = client.forms().size();
        final Map<String, Object> guidance = client.json("get_field_conventions", Map.of());
        dump("get_field_conventions", guidance);
        final List<ElicitFormRequest> asked = client.forms().subList(formsBefore, client.forms().size());
        assertThat(asked).as("no convention chosen: the tool asks instead of assuming one").isNotEmpty();
        assertThat(guidance.get("status")).as("asked, not assumed").isIn("needs_guidance", "chosen");
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> conv = client.json("get_field_conventions", Map.of("name", "stroom-flat"));
        assertThat(map(conv, "profile")).as("user chose 'stroom-flat'").containsKey("field_map");

        final Map<String, Object> template = templates("indexing").stream()
                .filter(candidate -> "lucene".equals(candidate.get("backend")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Lucene indexing template"));
        assertThat(template).as("indexing template %s (%s)", template.get("name"), template.get("backend"))
                .containsEntry("name", "Indexing");

        final String indexName = "E2E-CSV-" + STAMP + "-INDEX-V1";
        final Map<String, Object> draft = client.json("draft_index_mapping", Map.of("backend", "lucene",
                "index_name", indexName, "convention", "stroom-flat", "events_stream_ids", events));
        final Map<String, Object> plan = map(draft, "plan");
        dump("plan", plan.get("fields"));
        assertThat((List<?>) draft.get("problems")).as("field plan has StreamId, EventId and the time field")
                .isEmpty();
        final Map<String, Object> index = agreed("create_index_doc", Map.of("build", csv.build(), "backend", "lucene",
                "name", indexName, "time_field", plan.get("time_field")));
        dump("create_index_doc", index);
        // The plan drafted for this index in the session: its fields added with no plan passed again (seen: a
        // 50-field plan pasted five times).
        final List<String> made = index.get("fields") instanceof final Map<?, ?> fields
                                  && fields.get("added") instanceof final List<?> added
                ? added.stream().map(String::valueOf).toList()
                : List.of();
        assertThat(made).as("index fields added from the session's plan").containsExactlyInAnyOrderElementsOf(
                fieldNames(plan));

        final String xsltUuid = uuidOf(client.json("save_xslt", Map.of("build", csv.build(),
                "name", indexName + "-XSLT", "index_plan", plan)));
        final Map<String, Object> pipeline = agreed("create_indexing_pipeline", Map.of("build", csv.build(),
                "name", indexName + " - Indexing", "template_uuid", template.get("uuid"), "xslt_uuid", xsltUuid,
                "index_uuid", uuidOf(index)));
        final String pipelineUuid = uuidOf(pipeline);
        final Map<String, Object> sample = client.json("step_sample", Map.of("pipeline_uuid", pipelineUuid,
                "stream_ids", events));
        assertThat(sample).as("stepped %s events through the indexing pipeline clean", sample.get("records_stepped"))
                .containsEntry("verdict", "clean");
        agreed("create_processor_filter", Map.of("pipeline_uuid", pipelineUuid, "stream_ids", events,
                "source_pipeline_uuid", csv.pipelineUuid()));
        processAll();
        flushIndexShards();
        final Map<String, Object> gate = client.json("wait_for_processing", Map.of("pipeline_uuid", pipelineUuid,
                "stream_ids", events, "expect_events", false, "timeout_seconds", 10));
        assertThat(gate).as("indexing finished with no Error stream: %s", gate.get("streams"))
                .containsEntry("gate", "pass");

        // Lucene is searched only through Stroom; each hit is traced back to its event by stepping the pipeline.
        final Map<String, Object> result = agreed("verify_index", verification(csv.build(), uuidOf(index), events,
                List.of("StreamId", "EventId", "EventTime", "UserId", "HostName"), Map.of(
                        "exact", List.of(Map.of("field", "UserId", "value", "bob"),
                                Map.of("field", "HostName", "value", "ws03")),
                        "time_range", Map.of("field", "EventTime", "from", "2026-09-28T10:04:00.000Z",
                                "to", "2026-09-28T10:08:00.000Z", "expected", 2),
                        "searches", List.of(
                                // The plan's Lucene fields are case-insensitive.
                                search("UserId", "EQUALS", "Bob", 1),
                                search("UserId", "IN", "alice,carol", 2),
                                search("UserId", "EQUALS", "a*", 1),
                                search("HostName", "EQUALS", "ws0*", 3),
                                search("UserId", "NOT_EQUALS", "bob", 2),
                                search("EventTime", "GREATER_THAN", "2026-09-28T10:04:00.000Z", 2)),
                        "pipeline_uuid", pipelineUuid)));
        dump("verify_index", result.get("checks"));
        assertThat(result).as("verification searches pass, each hit traced to its event")
                .containsEntry("passed", true);

        // A search Stroom answers with nothing is refused, from the index's own field list (found reviewing the
        // tools: the analyzers weren't read, so a range on a Lucene text field ran, and found nothing).
        assertThat(refusal("search_events", Map.of("index", uuidOf(index), "where", List.of(Map.of(
                "field", "UserId", "condition", "GREATER_THAN", "value", "b")))))
                .contains("UserId GREATER_THAN 'b': UserId is a text field in Lucene");

        // Reprocessing: Stroom keeps what a stream put in a Lucene index, so the build's own index, holding only
        // these streams, is emptied first; after it, the count is three again, not six.
        final int formsBeforeReprocess = client.forms().size();
        final Map<String, Object> again = client.json("reprocess_streams", Map.of("pipeline_uuid", pipelineUuid,
                "stream_ids", events, "source_pipeline_uuid", csv.pipelineUuid()));
        final String approval = formsText(client.forms().subList(formsBeforeReprocess, client.forms().size()));
        assertThat(approval).as("the approval says the build's index is emptied first").contains("deleted first");
        processAll();
        flushIndexShards();
        final Map<String, Object> redone = client.json("wait_for_processing", Map.of("pipeline_uuid", pipelineUuid,
                "stream_ids", events, "expect_events", false, "filter_id", again.get("filter_id"),
                "timeout_seconds", 10));
        assertThat(redone).as("reprocessed into the index with no Error stream").containsEntry("gate", "pass");
        final List<IndexShard> shards = shards(uuidOf(index));
        assertThat(shards).as("the shard holding the first three documents is deleted: %s", shards.stream()
                        .map(s -> s.getId() + " " + s.getStatus() + " " + s.getDocumentCount())
                        .collect(Collectors.joining(", ")))
                .anySatisfy(shard -> {
                    assertThat(shard.getStatus()).isEqualTo(IndexShardStatus.DELETED);
                    assertThat(shard.getDocumentCount()).isEqualTo(3);
                });
        final Map<String, Object> againFound = agreed("verify_index", verification(csv.build(), uuidOf(index), events,
                List.of("StreamId", "EventId", "EventTime", "UserId", "HostName"),
                Map.of("pipeline_uuid", pipelineUuid)));
        assertThat(againFound).as("searches find each event once: %s", againFound.get("checks"))
                .containsEntry("passed", true);

        final String markdown = "# " + pipeline.get("name") + "\n\n## Output\n\nLucene index " + indexName + ".\n";
        // The script expects documentation without stream_ids to be refused ("Give stream_ids ... values the sample
        // gave"). The server's own write_documentation has since taken the streams the build's record says the
        // pipeline last stepped clean on (tools/builds.py _stepped_streams, "seen: prompts and agents left
        // stream_ids out, and every documentation call was refused once first"), so the script's check is stale:
        // what is asserted is the server's behaviour, the field mapping still going down to the field with the
        // values the sample gave.
        final Map<String, Object> unasked = client.json("write_documentation", Map.of("build", csv.build(),
                "pipeline_uuid", pipelineUuid, "markdown", markdown, "change", "Created"));
        assertThat(unasked).as("without stream_ids, the streams last stepped clean: down to the field, with sample "
                               + "values").containsEntry("status", "needs_descriptions");
        assertThat(((List<Map<String, Object>>) unasked.get("fields")).stream()
                .filter(field -> SOURCE_USER.equals(field.get("from"))))
                .singleElement()
                .satisfies(field -> assertThat((List<Object>) field.get("values")).contains("alice", "bob", "carol"));
        final Map<String, Object> written = agreed("write_documentation", Map.of("build", csv.build(),
                "pipeline_uuid", pipelineUuid, "markdown", markdown, "change", "Created", "stream_ids", events));
        final String userField = ((List<Map<String, Object>>) plan.get("fields")).stream()
                .filter(field -> SOURCE_USER.equals(field.get("source")))
                .map(field -> (String) field.get("name"))
                .findFirst()
                .orElseThrow();
        documentedToTheField(uuidOf(written), fieldNames(plan), Map.of(userField, "alice"));
        return new Indexed(events, template, index, pipeline, plan);
    }

    // v2: index the logon outcome as well.
    private void versionTwo(final Onboarded csv, final Indexed v1) {
        final String name = "E2E-CSV-" + STAMP + "-INDEX-V2";
        final Map<String, Object> draft = client.json("draft_index_mapping", Map.of("backend", "lucene",
                "index_name", name, "convention", "stroom-flat", "events_stream_ids", v1.events(),
                "extra_fields", List.of(Map.of("name", "Success", "type", "keyword",
                        "source", "EventDetail/Authenticate/Outcome/Success"))));
        final Map<String, Object> plan = map(draft, "plan");
        final Map<String, Object> index = agreed("create_index_doc", Map.of("build", csv.build(), "backend", "lucene",
                "name", name, "time_field", plan.get("time_field")));
        final String xsltUuid = uuidOf(client.json("save_xslt", Map.of("build", csv.build(), "name", name + "-XSLT",
                "index_plan", plan)));
        final Map<String, Object> copy = agreed("copy_pipeline", Map.of("build", csv.build(),
                "source_uuid", uuidOf(v1.pipeline()), "new_name", name + " - Indexing", "rename", Map.of("V1", "V2"),
                "set_properties", List.of(
                        Map.of("element", "xsltFilter", "name", "xslt", "doc_uuid", xsltUuid, "doc_type", "XSLT"),
                        Map.of("element", "indexingFilter", "name", "index", "doc_uuid", uuidOf(index),
                                "doc_type", "Index"))));
        final String copyUuid = uuidOf(copy);
        final Map<String, Object> diff = client.json("compare_outputs", Map.of(
                "pipeline_uuid", uuidOf(v1.pipeline()), "stream_ids", v1.events(), "other_pipeline_uuid", copyUuid));
        final List<Object> paths = ((List<Map<String, Object>>) diff.get("fields_changed")).stream()
                .map(field -> field.get("path"))
                .toList();
        assertThat(paths).as("v2 differs from v1 only by the added field")
                .containsExactly("record/data[Success]/@value");
        // The copy's code changed, so it is stepped clean before it processes, as for any pipeline.
        final Map<String, Object> stepped = client.json("step_sample", Map.of("pipeline_uuid", copyUuid,
                "stream_ids", v1.events()));
        assertThat(stepped).as("v2 steps clean").containsEntry("verdict", "clean");
        agreed("create_processor_filter", Map.of("pipeline_uuid", copyUuid, "stream_ids", v1.events(),
                "source_pipeline_uuid", csv.pipelineUuid()));
        processAll();
        flushIndexShards();
        final Map<String, Object> gate = client.json("wait_for_processing", Map.of("pipeline_uuid", copyUuid,
                "stream_ids", v1.events(), "expect_events", false, "timeout_seconds", 10));
        assertThat(gate).as("v2 indexing finished with no Error stream").containsEntry("gate", "pass");
        final Map<String, Object> result = agreed("verify_index", verification(csv.build(), uuidOf(index),
                v1.events(), List.of("StreamId", "EventId", "EventTime", "UserId", "Success"),
                Map.of("exact", List.of(Map.of("field", "Success", "value", "false")))));
        dump("verify_index v2", result.get("checks"));
        assertThat(result).as("v2 finds the one failed logon by the new field").containsEntry("passed", true);
        assertThat(((List<Map<String, Object>>) result.get("checks")).get(1)).containsEntry("returned", 1);
        final Map<String, Object> recheck = new LinkedHashMap<>(verification(csv.build(), uuidOf(v1.index()),
                v1.events(), List.of("StreamId", "UserId"), Map.of()));
        recheck.put("dashboard_name", v1.index().get("name") + "-RECHECK");
        final Map<String, Object> v1Still = agreed("verify_index", recheck);
        assertThat(v1Still).as("v1 index is untouched beside v2").containsEntry("passed", true);
    }

    private void promotion(final Onboarded csv) {
        final String folder = "E2E Indexed " + STAMP;
        asUser(admin, () -> explorerService.create(ExplorerConstants.FOLDER_TYPE, folder,
                explorerNodeService.getRoot(), PermissionInheritance.DESTINATION));
        final Map<String, Object> destinations = new LinkedHashMap<>();
        for (final String type : List.of("Feed", "Pipeline", "XSLT", "TextConverter", "Documentation", "Index",
                "Dashboard")) {
            destinations.put(type, "System/" + folder);
        }
        final Map<String, Object> result = agreed("promote_build", Map.of("build", csv.build(),
                "destinations", destinations));
        dump("promote_build", result);
        assertThat((List<?>) result.get("promoted")).as("promoted documents").hasSizeGreaterThanOrEqualTo(12);
    }

    private static Map<String, Object> verification(final String build,
                                                    final String indexUuid,
                                                    final List<Long> events,
                                                    final List<String> fields,
                                                    final Map<String, Object> more) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("build", build);
        arguments.put("index_uuid", indexUuid);
        arguments.put("backend", "lucene");
        arguments.put("stream_ids", events);
        arguments.put("expected_documents", 3);
        arguments.put("fields", fields);
        arguments.putAll(more);
        return arguments;
    }

    private static Map<String, Object> search(final String field,
                                              final String condition,
                                              final String value,
                                              final int expected) {
        return Map.of("field", field, "condition", condition, "value", value, "expected", expected);
    }

    private static List<String> fieldNames(final Map<String, Object> plan) {
        return ((List<Map<String, Object>>) plan.get("fields")).stream()
                .map(field -> (String) field.get("name"))
                .toList();
    }

    private List<IndexShard> shards(final String indexUuid) {
        final FindIndexShardCriteria criteria = FindIndexShardCriteria.builder()
                .indexUuidSet(Selection.selectNone())
                .build();
        criteria.getIndexUuidSet().add(indexUuid);
        final ResultPage<IndexShard> found = asUser(admin, () -> indexShardService.find(criteria));
        return found.getValues();
    }


    // --------------------------------------------------------------------------------


    /// The events stage's documentation, whose next is indexing, and its Events streams.
    private record EventsStage(Map<String, Object> documented, List<Object> events) {

    }

    /// A call's result: whether it was refused, its text, and its JSON.
    private record Called(boolean error, String text, Map<String, Object> json) {

    }

    /// What the index stage made.
    private record Indexed(List<Long> events,
                           Map<String, Object> template,
                           Map<String, Object> index,
                           Map<String, Object> pipeline,
                           Map<String, Object> plan) {

    }
}
