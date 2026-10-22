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

import stroom.ai.shared.AiToolAccess;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.pipeline.xslt.XsltStore;
import stroom.security.api.SecurityContext;
import stroom.test.AbstractAiIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.test.CommonTranslationTestHelper;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Onboarding a source end to end, as an MCP client would, through the REAL tools ([McpTools]) on a real Stroom
/// (the integration test database and stream store, the standard content packs' template pipelines and event-logging
/// schemas): the intent of the Stroom MCP server's `dev/e2e_translation.py` (the CSV case, a field fix, promotion),
/// `dev/e2e_plan_walk.py` (the plan's `next` at each step, and wrong moves refused) and `dev/e2e_errors.py` (invalid
/// data found by stepping). The user's part is played by the test: it agrees to every form, makes the decisions a
/// mapping's notes ask for, and runs the processor tasks Stroom's job would run.
///
/// Indexing (the plan's second stage) is not covered: its Elasticsearch path needs a cluster.
@SuppressWarnings("unchecked")
class TestOnboardingEndToEnd extends AbstractAiIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestOnboardingEndToEnd.class);

    private static final String SOURCE = "Acme logons";
    private static final String BUILD = "onboard-acme-logons";
    private static final String BUILD_FOLDER = "System/MCP Workspace/" + BUILD;
    // The build's documents for its events pipeline, by role.
    private static final String EVENTS_FOLDER = BUILD_FOLDER + "/Events";
    private static final String FEED = "ACME-LOGONS";
    private static final String CONVERTER = "ACME-LOGONS-Converter";
    private static final String EVENTS = "ACME-LOGONS-Events";
    private static final String PROMOTED_FOLDER = "System/Acme/Events";
    private static final String CSV_SAMPLE = """
            time,user,host,ip,result
            2026-09-28T10:00:00,alice,ws01,10.0.0.1,ok
            2026-09-28T10:05:00,bob,ws02,10.0.0.2,fail
            2026-09-28T10:07:30,carol,ws03,10.0.0.3,ok
            """;

    @Inject
    private McpTools mcpTools;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private CommonTranslationTestHelper translationTestHelper;
    @Inject
    private ExplorerNodeService explorerNodeService;
    @Inject
    private MetaService metaService;
    @Inject
    private XsltStore xsltStore;

    private McpTestClient client;

    @BeforeEach
    void setUp() {
        final UserRef admin = administrator();
        // The template pipelines (Event Data (Text), ...) and the event-logging schemas.
        asUser(admin, () -> commonTestControl.createRequiredXMLSchemas());
        toolPolicy("Everything", AiToolAccess.READ_WRITE, admin);
        client = new McpTestClient(mcpTools, securityContext, admin, true);
    }

    @Test
    void aCsvSourceIsOnboardedFromItsSampleToPromotedEvents() {
        final Built built = buildPipeline();

        // Every sample record stepped clean through the real pipeline: parser, translation, schema.
        final Map<String, Object> stepped = client.json("step_sample", Map.of(
                "pipeline_uuid", built.pipelineUuid(), "stream_ids", List.of(built.rawStreamId())));
        assertThat(stepped).containsEntry("verdict", "clean").containsEntry("records_stepped", 3);
        assertNextStep(stepped, "processed");

        final long eventsId = process(built);

        // The Events are valid against the event-logging schema Stroom holds, and pass the quality rules.
        final Map<String, Object> checked = client.json("check_events", Map.of("stream_ids", List.of(eventsId)));
        assertThat(checked).containsEntry("ok", true);
        assertThat(map(checked, "schema")).containsEntry("valid", true)
                .containsEntry("schema", "file://event-logging-v4.1.0.xsd");
        assertThat(map(checked, "quality")).containsEntry("events_checked", 3);
        assertNextStep(checked, "documented");

        final Map<String, Object> documented = writeDocumentation(built);
        assertThat(documented).containsEntry("written", "created").containsEntry("name", EVENTS);
        assertThat((String) documented.get("field_mapping"))
                .contains("`EventDetail/Authenticate/User/Id`", "`EventSource/Device/HostName`");
        assertThat(folderOf((String) documented.get("uuid"))).isEqualTo(EVENTS_FOLDER);
        // The events stage is done: indexing is next.
        assertNextStep(documented, "index");

        // Promotion shows the layout first, then moves the build's documents there once the user agrees.
        final Map<String, Object> destinations = Map.of("Feed", PROMOTED_FOLDER, "Pipeline", PROMOTED_FOLDER,
                "XSLT", PROMOTED_FOLDER, "TextConverter", PROMOTED_FOLDER, "Documentation", PROMOTED_FOLDER);
        final Map<String, Object> review = client.json("promote_build", Map.of(
                "build", BUILD, "destinations", destinations));
        assertThat(review).containsEntry("status", "needs_review");
        assertThat((String) review.get("layout")).startsWith(PROMOTED_FOLDER + "/  (new)")
                .contains(FEED + "  (Feed)", CONVERTER + "  (TextConverter)", EVENTS + "  (Pipeline)",
                        EVENTS + "  (XSLT)", EVENTS + "  (Documentation)");
        final Map<String, Object> promoted = client.json("promote_build", Map.of(
                "build", BUILD, "destinations", destinations, "reviewed", review.get("review_id")));
        dump("promote_build", promoted);
        assertThat(promoted).doesNotContainKey("promoted_with_warnings");
        for (final String uuid : List.of(built.feedUuid(), built.converterUuid(), built.xsltUuid(),
                built.pipelineUuid(), (String) documented.get("uuid"))) {
            assertThat(folderOf(uuid)).as(uuid).isEqualTo(PROMOTED_FOLDER);
        }
        // The emptied build folder is gone, and the pipeline's filter for its feed is handed over disabled.
        assertThat(findFolder(BUILD_FOLDER)).isEmpty();
        assertThat((List<Map<String, Object>>) promoted.get("processing_filters"))
                .singleElement()
                .satisfies(filter -> assertThat(filter)
                        .containsEntry("pipeline", EVENTS)
                        .containsEntry("feed", FEED)
                        .containsEntry("enabled", false));
        assertThat((List<String>) promoted.get("promoted"))
                .startsWith("created folder System/Acme", "created folder " + PROMOTED_FOLDER)
                .contains("removed the build's workspace folder, now empty");
    }

    @Test
    void aReportedFieldFixChangesOnlyThatFieldAndIsReprocessedOnce() {
        final Built built = buildPipeline();
        client.json("step_sample", Map.of(
                "pipeline_uuid", built.pipelineUuid(), "stream_ids", List.of(built.rawStreamId())));
        final long firstEvents = process(built);

        // The fix, as a draft: generated from the changed mapping without saving it.
        final Map<String, Object> fixed = withDescription(built.mapping(), "Interactive user logon");
        final Map<String, Object> draft = client.json("build_translation_xslt", Map.of(
                "mapping", fixed,
                "schema_version", "4.1.0",
                "stream_ids", List.of(built.rawStreamId()),
                "splitter", built.splitter()));
        assertThat(draft).containsEntry("ok", true);
        final String draftXslt = (String) draft.get("xslt");
        assertThat(draftXslt).contains("Interactive user logon");

        // Only the reported field changes.
        final Map<String, Object> compared = client.json("compare_outputs", Map.of(
                "pipeline_uuid", built.pipelineUuid(),
                "stream_ids", List.of(built.rawStreamId()),
                "draft_code", Map.of("translationFilter", draftXslt)));
        dump("compare_outputs", compared);
        assertThat(((List<Map<String, Object>>) compared.get("fields_changed")))
                .extracting(field -> field.get("path"))
                .containsExactly("Event/EventDetail/Description");

        // Saved over the build's XSLT.
        final Map<String, Object> saved = client.json("build_translation_xslt", Map.of(
                "build", BUILD,
                "uuid", built.xsltUuid(),
                "mapping", fixed,
                "schema_version", "4.1.0",
                "stream_ids", List.of(built.rawStreamId()),
                "splitter", built.splitter()));
        assertThat(map(saved, "saved")).containsEntry("uuid", built.xsltUuid());
        assertThat(asUser(administrator(), () -> xsltStore.readDocument(
                new DocRef("XSLT", built.xsltUuid())).getData())).contains("Interactive user logon");
        client.json("step_sample", Map.of(
                "pipeline_uuid", built.pipelineUuid(), "stream_ids", List.of(built.rawStreamId())));

        // A second plain filter is refused: reprocess_streams is the way.
        final String second = McpTestClient.text(client.call("create_processor_filter", Map.of(
                "pipeline_uuid", built.pipelineUuid(), "stream_ids", List.of(built.rawStreamId()))));
        assertThat(second).contains("reprocess_streams");
        final Map<String, Object> reprocess = client.json("reprocess_streams", Map.of(
                "pipeline_uuid", built.pipelineUuid(), "stream_ids", List.of(built.rawStreamId())));
        dump("reprocess_streams", reprocess);
        assertThat(reprocess).containsEntry("max_tasks", 1);
        securityContext.asProcessingUser(() -> translationTestHelper.processAll());
        final Map<String, Object> processed = client.json("wait_for_processing", Map.of(
                "pipeline_uuid", built.pipelineUuid(),
                "stream_ids", List.of(built.rawStreamId()),
                "filter_id", reprocess.get("filter_id"),
                "timeout_seconds", 10));
        assertThat(processed).containsEntry("gate", "pass");
        final long secondEvents = eventsOf(processed);
        assertThat(secondEvents).isNotEqualTo(firstEvents);
        // Stroom supersedes the earlier output, leaving one Events stream.
        assertThat(meta(firstEvents).getStatus()).isEqualTo(Status.DELETED);
        assertThat(meta(secondEvents).getStatus()).isEqualTo(Status.UNLOCKED);
    }

    @Test
    void wrongMovesAreRefusedWithTheRightCallNamedAndLeaveTheNextStepWhereItWas() {
        final Built built = buildPipeline();

        // Processing before a clean step.
        final String early = McpTestClient.text(client.call("create_processor_filter", Map.of(
                "pipeline_uuid", built.pipelineUuid(), "stream_ids", List.of(built.rawStreamId()))));
        dump("early filter", Map.of("text", early));
        assertThat(early).contains("step_sample");
        // Waiting with no processor filter.
        final String waiting = McpTestClient.text(client.call("wait_for_processing", Map.of(
                "pipeline_uuid", built.pipelineUuid(), "stream_ids", List.of(built.rawStreamId()),
                "timeout_seconds", 5)));
        dump("early wait", Map.of("text", waiting));
        assertThat(waiting).contains("no processor filter", "create_processor_filter first");
        assertNextStep(JsonUtil.readValue(waiting, Map.class), "stepped");
        // Promoting early: what is outstanding is shown with the layout, and nothing is moved.
        final String promoting = McpTestClient.text(client.call("promote_build", Map.of(
                "build", BUILD, "destinations", Map.of("Feed", PROMOTED_FOLDER, "Pipeline", PROMOTED_FOLDER,
                        "XSLT", PROMOTED_FOLDER, "TextConverter", PROMOTED_FOLDER))));
        dump("early promotion", Map.of("text", promoting));
        final Map<String, Object> review = JsonUtil.readValue(promoting, Map.class);
        assertThat(review).containsEntry("status", "needs_review");
        assertThat((List<String>) review.get("warnings")).containsExactly(
                "Pipeline '" + EVENTS + "': no clean step_sample or step_records of its current code is recorded",
                "Pipeline '" + EVENTS + "': no documentation (write_documentation)");
        assertThat(findFolder(PROMOTED_FOLDER)).isEmpty();

        // The plan still says stepping is next, as build_status does, and the work isn't done.
        final Map<String, Object> status = client.json("build_status", Map.of("build", BUILD));
        dump("build_status", status);
        assertNextStep(status, "stepped");
        assertThat((List<String>) status.get("before_promotion")).containsExactly(
                "Pipeline '" + EVENTS + "': no clean step_sample or step_records of its current code is recorded",
                "Pipeline '" + EVENTS + "': no documentation (write_documentation)");
        assertThat(folderOf(built.pipelineUuid())).isEqualTo(EVENTS_FOLDER);
    }

    @Test
    void invalidDataIsFoundByStepping() {
        final Built built = buildPipeline();
        // A second sample whose time isn't one: its event has no EventTime, which the schema requires.
        final Map<String, Object> upload = client.json("upload_sample", Map.of(
                "feed", FEED,
                "sample", "time,user,host,ip,result\nyesterday,dave,ws04,10.0.0.4,ok\n"));
        final long invalid = ((Number) upload.get("stream_id")).longValue();

        final Map<String, Object> stepped = client.json("step_sample", Map.of(
                "pipeline_uuid", built.pipelineUuid(), "stream_ids", List.of(built.rawStreamId(), invalid)));
        dump("step_sample invalid", stepped);

        assertThat(stepped).containsEntry("verdict", "blocking");
        assertThat(((Number) stepped.get("records_with_errors")).intValue()).isOne();
        // The translation's own warning says why: the time doesn't parse.
        assertThat(JsonUtil.writeValueAsString(stepped.get("groups"), false))
                .contains("Failed to parse date: \\\"yesterday\\\"");
        // Blocking: the schema, which the record's event fails, and it alone.
        final List<Map<String, Object>> blocking = ((List<Map<String, Object>>) stepped.get("groups")).stream()
                .filter(group -> "blocking".equals(group.get("class")))
                .toList();
        assertThat(blocking).isNotEmpty().allSatisfy(group -> {
            assertThat(group).containsEntry("element", "schemaFilter")
                    .containsEntry("reason", "Output fails schema validation");
            assertThat((List<String>) group.get("records")).containsExactly(invalid + ":0");
        });
        assertThat(JsonUtil.writeValueAsString(blocking, false)).contains("TimeCreated");
    }

    // The events stage up to the pipeline, as start_onboarding's plan lays it out, checking the documents made
    // and the plan's next step at each stage.
    private Built buildPipeline() {
        final Map<String, Object> started = client.json("start_onboarding", Map.of(
                "source_name", SOURCE,
                "samples", List.of(CSV_SAMPLE)));
        assertThat(started)
                .containsEntry("build", BUILD)
                .containsEntry("folder", BUILD_FOLDER)
                .containsEntry("parser", "DSParser")
                .containsEntry("done", false);
        assertThat(map(started, "profile")).containsEntry("format", "delimited")
                .containsEntry("columns", List.of("time", "user", "host", "ip", "result"));
        assertThat((String) started.get("template")).startsWith("Event Data (Text)");
        assertNextStep(started, "feed");
        assertThat(findFolder(BUILD_FOLDER)).isPresent();

        final Map<String, Object> feed = client.json("create_feed", Map.of("build", BUILD, "name", FEED));
        assertThat(feed).containsEntry("type", "Feed").containsEntry("name", FEED);
        final String feedUuid = (String) feed.get("uuid");
        assertThat(folderOf(feedUuid)).isEqualTo(BUILD_FOLDER);
        assertNextStep(feed, "samples");

        final Map<String, Object> upload = client.json("upload_sample", Map.of("feed", FEED, "sample", CSV_SAMPLE));
        final long rawStreamId = ((Number) upload.get("stream_id")).longValue();
        assertThat(meta(rawStreamId).getFeedName()).isEqualTo(FEED);
        assertThat(meta(rawStreamId).getTypeName()).isEqualTo(StreamTypeNames.RAW_EVENTS);
        assertNextStep(upload, "converter");

        // The Data Splitter, inferred from the uploaded sample, and saved.
        final Map<String, Object> splitter = client.json("build_data_splitter", Map.of(
                "stream_ids", List.of(rawStreamId), "build", BUILD, "save_as", CONVERTER));
        assertThat(splitter).containsEntry("unmatched_count", 0).containsEntry("records", 3);
        final Map<String, Object> converter = map(splitter, "saved");
        assertThat(converter).containsEntry("type", "TextConverter").containsEntry("name", CONVERTER);
        final String converterUuid = (String) converter.get("uuid");
        assertThat(folderOf(converterUuid)).isEqualTo(EVENTS_FOLDER);
        assertNextStep(converter, "translation");

        // The mapping drafted from the sample, settled as the user decides, generated and saved with it.
        final Map<String, Object> draft = client.json("draft_translation_mapping",
                Map.of("stream_ids", List.of(rawStreamId)));
        assertThat(draft).containsEntry("schema_version", "4.1.0");
        assertThat((List<String>) draft.get("fields_seen")).containsExactly("time", "user", "host", "ip", "result");
        final Map<String, Object> mapping = settled(map(draft, "mapping"));
        final Object splitterSpec = draft.get("splitter");
        final Map<String, Object> xslt = client.json("build_translation_xslt", Map.of(
                "build", BUILD,
                "name", EVENTS,
                "mapping", mapping,
                "schema_version", draft.get("schema_version"),
                "stream_ids", List.of(rawStreamId),
                "splitter", splitterSpec));
        assertThat(xslt).containsEntry("ok", true).containsEntry("problems", List.of());
        final String xsltUuid = (String) map(xslt, "saved").get("uuid");
        assertThat(folderOf(xsltUuid)).isEqualTo(EVENTS_FOLDER);
        assertNextStep(xslt, "pipeline");

        // A child of the content pack's template, given the build's converter and XSLT.
        final Map<String, Object> templates = client.json("find_pipeline_templates", Map.of("stage", "translation"));
        final String templateUuid = ((List<Map<String, Object>>) templates.get("candidates")).stream()
                .filter(candidate -> "Event Data (Text)".equals(candidate.get("name")))
                .map(candidate -> (String) candidate.get("uuid"))
                .findFirst()
                .orElseThrow();
        final Map<String, Object> pipeline = client.json("create_pipeline", Map.of(
                "build", BUILD,
                "name", EVENTS,
                "template_uuid", templateUuid));
        assertThat(pipeline).containsEntry("type", "Pipeline").containsEntry("template", "Event Data (Text)");
        assertThat((List<String>) pipeline.get("sets"))
                .containsExactlyInAnyOrder("dsParser.textConverter", "translationFilter.xslt");
        final String pipelineUuid = (String) pipeline.get("uuid");
        assertThat(folderOf(pipelineUuid)).isEqualTo(EVENTS_FOLDER);
        assertNextStep(pipeline, "stepped");
        return new Built(feedUuid, rawStreamId, converterUuid, xsltUuid, pipelineUuid, mapping, splitterSpec);
    }

    // The sample processed into Events: the filter made (asked for in a form), its tasks run as Stroom's job
    // would, and waited for.
    private long process(final Built built) {
        final Map<String, Object> filter = client.json("create_processor_filter", Map.of(
                "pipeline_uuid", built.pipelineUuid(), "stream_ids", List.of(built.rawStreamId())));
        assertThat(filter).containsEntry("enabled", true);
        assertNextStep(filter, "processed");
        securityContext.asProcessingUser(() -> translationTestHelper.processAll());
        final Map<String, Object> processed = client.json("wait_for_processing", Map.of(
                "pipeline_uuid", built.pipelineUuid(), "stream_ids", List.of(built.rawStreamId()),
                "timeout_seconds", 10));
        assertThat(processed).containsEntry("gate", "pass").containsEntry("finished", true);
        assertNextStep(processed, "validated");
        final long eventsId = eventsOf(processed);
        final Meta events = meta(eventsId);
        assertThat(events.getTypeName()).isEqualTo(StreamTypeNames.EVENTS);
        assertThat(events.getFeedName()).isEqualTo(FEED);
        assertThat(events.getParentMetaId()).isEqualTo(built.rawStreamId());
        return eventsId;
    }

    // write_documentation, its needs_descriptions round answered as the agent would: a description of each field.
    private Map<String, Object> writeDocumentation(final Built built) {
        final Map<String, Object> arguments = new LinkedHashMap<>(Map.of(
                "build", BUILD,
                "pipeline_uuid", built.pipelineUuid(),
                "stream_ids", List.of(built.rawStreamId()),
                "markdown", "# " + EVENTS + "\n\n## Purpose and data\n\nAcme's logons, from its CSV log.\n",
                "change", "Created"));
        final Map<String, Object> asked = client.json("write_documentation", arguments);
        assertThat(asked).containsEntry("status", "needs_descriptions");
        final Map<String, Object> descriptions = new LinkedHashMap<>();
        for (final Map<String, Object> field : (List<Map<String, Object>>) asked.get("fields")) {
            descriptions.put((String) field.get("field"), "Acme's " + field.get("field") + ", seen as "
                                                             + field.get("values"));
        }
        arguments.put("field_descriptions", descriptions);
        return client.json("write_documentation", arguments);
    }

    // The draft as the user settles what its notes ask: the system's name and environment and the generator, and
    // every record a logon, its outcome in the result field.
    private static Map<String, Object> settled(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>(draft);
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final Map<String, Object> entry : (List<Map<String, Object>>) draft.get("common")) {
            final Map<String, Object> settledEntry = new LinkedHashMap<>(entry);
            final String path = String.valueOf(entry.get("path"));
            if ("EventSource/System/Name".equals(path)) {
                settledEntry.put("value", "Acme");
            } else if ("EventSource/System/Environment".equals(path)) {
                settledEntry.put("value", "Test");
            } else if ("EventSource/Generator".equals(path)) {
                settledEntry.put("value", "acme-auth");
            }
            common.add(settledEntry);
        }
        mapping.put("common", common);
        return withDescription(mapping, "User logon");
    }

    private static Map<String, Object> withDescription(final Map<String, Object> mapping, final String description) {
        final Map<String, Object> changed = new LinkedHashMap<>(mapping);
        changed.put("events", List.of(Map.of(
                "name", "logon",
                "fields", List.of(
                        Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                        Map.of("path", "EventDetail/Description", "value", description),
                        Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                        Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user"),
                        Map.of("path", "EventDetail/Authenticate/Outcome/Success", "field", "result",
                                "map", Map.of("ok", "true", "fail", "false"))))));
        return changed;
    }

    private static void assertNextStep(final Map<String, Object> result, final String step) {
        assertThat(map(result, "next")).as("next of %s", result).containsEntry("step", step);
    }

    private static long eventsOf(final Map<String, Object> processed) {
        final List<Map<String, Object>> streams = (List<Map<String, Object>>) processed.get("streams");
        assertThat(streams).hasSize(1);
        final List<?> events = (List<?>) streams.getFirst().get("events");
        assertThat(events).as("one Events stream per raw stream").hasSize(1);
        return ((Number) events.getFirst()).longValue();
    }

    private Meta meta(final long id) {
        return securityContext.asProcessingUserResult(() -> metaService.getMeta(id, true));
    }

    // The explorer path of a document's folder, e.g. System/MCP Workspace/onboard-acme-logons.
    private String folderOf(final String uuid) {
        return securityContext.asProcessingUserResult(() -> {
            final ExplorerNode node = explorerNodeService.getNodeByUuid(uuid).orElseThrow(() ->
                    new AssertionError("No explorer node for " + uuid));
            return explorerNodeService.getPath(node.getDocRef()).stream()
                    .map(ExplorerNode::getName)
                    .collect(Collectors.joining("/"));
        });
    }

    // The folder at an explorer path, if there is one.
    private Optional<ExplorerNode> findFolder(final String path) {
        return securityContext.asProcessingUserResult(() -> {
            final List<String> names = List.of(path.split("/"));
            Optional<ExplorerNode> folder = Optional.of(explorerNodeService.getRoot());
            for (final String name : names.subList(1, names.size())) {
                folder = folder.flatMap(parent -> explorerNodeService.getNodesByNameAndType(parent, name, "Folder")
                        .stream()
                        .findFirst());
            }
            return folder;
        });
    }

    private static Map<String, Object> map(final Map<String, Object> result, final String key) {
        assertThat(result.get(key)).as("%s of %s", key, result).isInstanceOf(Map.class);
        return (Map<String, Object>) result.get(key);
    }

    private static void dump(final String step, final Map<String, Object> result) {
        LOGGER.info(() -> LogUtil.message("{}: {}", step, JsonUtil.writeValueAsString(result, true)));
    }


    // --------------------------------------------------------------------------------


    /// What the events stage made, up to the pipeline.
    private record Built(String feedUuid,
                         long rawStreamId,
                         String converterUuid,
                         String xsltUuid,
                         String pipelineUuid,
                         Map<String, Object> mapping,
                         Object splitter) {

    }
}
