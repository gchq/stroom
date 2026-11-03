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

import stroom.ai.impl.tools.KeptMapping;
import stroom.docref.DocRef;
import stroom.mcp.impl.McpTestClient;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.xmlschema.XmlSchemaStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.xmlschema.shared.XmlSchemaDoc;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// An environment with its own fork of the event-logging schema, through the REAL tools on a real Stroom. Seen in
/// testing: an environment whose Events are all written in a fork (`file://xml/schema/accounting/events`) whose
/// schema is in the EVENTS group beside event-logging's; the tools only knew event-logging:3, so the Events were
/// profiled as none, check_events refused them, and the agent dropped the environment's common import to write
/// event-logging:3 instead.
///
/// The fork here is the test's own: event-logging 4.1.0 in namespace [#FORK_NS], with one extra EventDetail activity,
/// Charge, versioned 1.2.0 (real forks have only a few additions to the generally available schema, and versions
/// of their own). Covered whole, in one
/// environment:
///
/// 1. The fork in event-logging's schema group, a shared import writing it, and an AGENTS doc requiring that import.
/// 2. build_translation_xslt, given no version, infers the fork from the import and writes the fork's Charge activity;
///    the pipeline from Stroom's template steps clean (its schema filter validates the fork).
/// 3. Processed: check_events validates the Events against the fork; summarise_streams profiles them and says their
///    namespace.
/// 4. An index plan and its indexing XSLT read them in the fork's namespace; a CEF draft reads them too.
/// 5. check_xslt refuses an event-logging:3 translation importing the fork's import, rather than the import being
///    dropped.
class TestE2eForkedSchema extends AbstractE2eTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestE2eForkedSchema.class);

    /// The test fork's namespace.
    static final String FORK_NS = "file://xml/schema/test-fork/events";
    /// The test fork's system id.
    static final String FORK_SYSTEM_ID = "file://test-fork-events-v1.2.0.xsd";
    private static final String STAMP = "FORK1";
    private static final String WORKSPACE = "System/MCP Workspace/";
    private static final String BUILD = "fork";
    private static final String FEED = "E2E-FORK";
    private static final String SHARED = "E2E-Fork-Common-" + STAMP;
    private static final String SAMPLE = """
            {"ts": "2026-10-01T08:00:00Z", "u": "alice", "acct": "ACC-1", "amt": "12.50"}
            {"ts": "2026-10-01T08:05:00Z", "u": "bob", "acct": "ACC-2", "amt": "7.25"}
            """;

    @Inject
    private XmlSchemaStore xmlSchemaStore;
    @Inject
    private PipelineStore pipelineStore;

    @Test
    void aForkedEnvironmentIsOnboardedInItsOwnNamespace() {
        // ### 1. The fork, beside event-logging, and the environment's import that writes it.
        final Fork fork = installFork();
        LOGGER.info("The fork is in schema group {}", fork.group());
        createFeed(BUILD, FEED);
        final String lib = create(XsltDoc.TYPE, SHARED, "System").getUuid();
        editXslt(lib, doc -> doc.copy().data(sharedImport(FORK_NS)).build());
        documentationDoc(WORKSPACE + BUILD, "AGENTS", "Build " + STAMP + ": every translation imports the common "
                                                      + "fields.\n\n## Required imports\n- translation: " + SHARED
                                                      + "\n");
        final long raw = upload(FEED, SAMPLE);

        // ### 2. Built with no version given: the fork, inferred from the required import.
        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", mapping(),
                "stream_ids", List.of(raw), "build", BUILD, "name", FEED + "-Events", "feeds", List.of(FEED)));
        dump("build_translation_xslt", built);
        final String xsltUuid = built.get("saved") instanceof final Map<?, ?> saved
                ? (String) saved.get("uuid")
                : null;
        assertThat(xsltUuid).as("saved: %s", built.get("problems")).isNotNull();
        assertThat(String.valueOf(built.get("schema_version_settled"))).as("settled without asking")
                .contains("environment's fork", FORK_NS, "don't ask the user");
        final String code = readXslt(xsltUuid).getData();
        assertThat(code).as("written in the fork, with its own schema location and version")
                .contains("xmlns=\"" + FORK_NS + "\"", FORK_NS + " " + FORK_SYSTEM_ID, "Version=\"1.2.0\"")
                .doesNotContain("event-logging:3")
                .contains("<Charge>", "<xsl:import href=\"" + SHARED + "\"");
        // Kept with the fork's document and version, so it regenerates in the fork; and it matches its mapping.
        final Map<String, Object> kept = KeptMapping.readMapping(readXslt(xsltUuid).getDescription())
                .orElseThrow().payload();
        assertThat(kept).containsEntry("schema_version", "1.2.0").containsKey("schema_uuid");
        assertThat(String.valueOf(built)).as("not taken for a hand edit")
                .doesNotContain("differs from what the mapping generates");
        // Changed (changes=, only what changes): regenerated from the kept mapping, still in the fork.
        final Map<String, Object> changed = agreed("build_translation_xslt", args("uuid", xsltUuid, "build", BUILD,
                "stream_ids", List.of(raw), "feeds", List.of(FEED), "change", "Describe each charge",
                "changes", Map.of("events", List.of(Map.of("name", "charge", "fields", List.of(
                        Map.of("path", "EventDetail/Description", "value", "Account charge")))))));
        dump("build_translation_xslt changes", changed);
        assertThat(changed.get("problems")).as("changed: %s", changed.get("problems")).isIn(null, List.of());
        assertThat(readXslt(xsltUuid).getData()).as("regenerated in the fork, with the change")
                .contains("xmlns=\"" + FORK_NS + "\"", "Account charge", "<Charge>")
                .doesNotContain("event-logging:3");
        assertThat(KeptMapping.readMapping(readXslt(xsltUuid).getDescription()).orElseThrow().payload())
                .as("the fork's document still kept").containsEntry("schema_version", "1.2.0")
                .containsEntry("schema_uuid", fork.uuid());
        assertThat(String.valueOf(changed)).doesNotContain("differs from what the mapping generates");
        final String pipeline = (String) createPipeline(BUILD, FEED + "-Events",
                templateUuid("translation", "Event Data (JSON)")).get("uuid");
        final Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        assertThat(stepped.get("verdict")).as("stepped clean, validated against the fork: %s",
                stepped.get("groups")).isEqualTo("clean");

        // ### 3. Processed, validated and profiled.
        final Map<String, Object> filter = agreed("create_processor_filter", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(raw)));
        assertThat(filter).containsEntry("enabled", true);
        processAll();
        final Map<String, Object> gate = json("wait_for_processing", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(raw), "timeout_seconds", 10));
        assertThat(gate).as("processed: %s", gate.get("streams")).containsEntry("gate", "pass");
        final long eventsId = number(((List<?>) list(gate, "streams").getFirst().get("events")).getFirst());
        final Map<String, Object> checked = json("check_events", args("stream_ids", List.of(eventsId)));
        dump("check_events", checked);
        assertThat(map(checked, "schema")).as("valid against the fork")
                .containsEntry("valid", true)
                .containsEntry("schema", FORK_SYSTEM_ID)
                .containsEntry("namespace", FORK_NS);
        assertThat(checked).as("quality checks pass: %s", checked).containsEntry("ok", true);
        final Map<String, Object> profile = json("summarise_streams", args("stream_ids", List.of(eventsId),
                "kind", "events"));
        dump("summarise_streams", profile);
        assertThat(profile).containsEntry("events", 2).containsEntry("events_namespace", FORK_NS);
        assertThat(map(profile, "path_population")).as("the fork's own activity is profiled")
                .containsKey("EventDetail/Charge/Account");

        // Documented down to the fork's own fields, with the sample's values.
        final Map<String, Object> eventsDoc = agreed("write_documentation", args("build", BUILD,
                "pipeline_uuid", pipeline, "stream_ids", List.of(raw), "change", "Created",
                "markdown", "# " + FEED + "-Events\n\n## Purpose and data\n\nCharges, for the forked schema's e2e "
                            + "test.\n"));
        final String eventsText = documentation(uuidOf(eventsDoc));
        final Map<String, List<String>> rows = fieldRows(eventsText.split("## Field mapping", 2)[1]
                .split("\n## ", 2)[0]);
        assertThat(rows).as("a row for the fork's own activity").containsKey("EventDetail/Charge/Account");
        assertThat(rows.get("EventDetail/Charge/Account").getLast()).contains("ACC-1");

        // ### 4. Indexed and sent as CEF from the fork's Events.
        final String indexName = "E2E-FORK-" + STAMP + "-INDEX-V1";
        final Map<String, Object> draft = client.json("draft_index_mapping", Map.of("backend", "lucene",
                "index_name", indexName, "convention", "stroom-flat", "events_stream_ids", List.of(eventsId)));
        dump("draft_index_mapping", draft);
        final Map<String, Object> plan = map(draft, "plan");
        assertThat(plan).containsEntry("events_namespace", FORK_NS);
        assertThat(String.valueOf(plan.get("fields"))).as("the populated paths are planned")
                .contains("EventSource/User/Id");
        assertThat(draft).as("nothing planned that no Event can hold").doesNotContainKey("fields_not_in_schema");
        final Map<String, Object> savedIndexXslt = client.json("save_xslt", args("build", BUILD,
                "name", indexName + "-XSLT", "index_plan", plan));
        final String indexXsltUuid = uuidOf(savedIndexXslt);
        assertThat(readXslt(indexXsltUuid).getData()).as("the indexing XSLT reads the fork")
                .contains("xpath-default-namespace=\"" + FORK_NS + "\"");
        final Map<String, Object> index = agreed("create_index_doc", Map.of("build", BUILD, "backend", "lucene",
                "name", indexName, "time_field", plan.get("time_field")));
        final Map<String, Object> indexing = agreed("create_indexing_pipeline", Map.of("build", BUILD,
                "name", indexName + " - Indexing", "template_uuid", templateUuid("indexing", "Indexing"),
                "xslt_uuid", indexXsltUuid, "index_uuid", uuidOf(index)));
        final String indexingUuid = uuidOf(indexing);
        assertThat(client.json("step_sample", Map.of("pipeline_uuid", indexingUuid, "stream_ids",
                List.of(eventsId)))).as("the fork's Events stepped through the indexing pipeline clean")
                .containsEntry("verdict", "clean");
        agreed("create_processor_filter", Map.of("pipeline_uuid", indexingUuid, "stream_ids", List.of(eventsId),
                "source_pipeline_uuid", pipeline));
        processAll();
        flushIndexShards();
        assertThat(client.json("wait_for_processing", Map.of("pipeline_uuid", indexingUuid, "stream_ids",
                List.of(eventsId), "expect_events", false, "timeout_seconds", 10)))
                .as("indexed with no Error stream").containsEntry("gate", "pass");
        final String userField = fieldFrom(plan, "EventSource/User/Id");
        final Map<String, Object> verified = agreed("verify_index", Map.of("build", BUILD,
                "index_uuid", uuidOf(index), "backend", "lucene", "stream_ids", List.of(eventsId),
                "expected_documents", 2, "fields", List.of("StreamId", "EventId", userField),
                "exact", List.of(Map.of("field", userField, "value", "alice")), "pipeline_uuid", indexingUuid));
        dump("verify_index", verified.get("checks"));
        assertThat(verified).as("both Events indexed and found, each traced to its event")
                .containsEntry("passed", true);
        agreed("write_documentation", Map.of("build", BUILD, "pipeline_uuid", indexingUuid, "change", "Created",
                "stream_ids", List.of(eventsId), "markdown", "# " + indexName + " - Indexing\n\n## Output\n\n"
                                                            + "Lucene index " + indexName + ".\n"));
        final Map<String, Object> cef = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "topic", "arcsight-cef"));
        dump("draft_cef_mapping", cef);
        assertThat(String.valueOf(cef.get("example_line"))).as("a CEF line from the fork's Events")
                .startsWith("CEF:0|");

        // ### 5. An event-logging:3 translation can't take the fork's import.
        final Map<String, Object> mixed = json("check_xslt", args("xslt", translationImporting(SHARED)));
        dump("check_xslt", mixed);
        assertThat(String.valueOf(mixed.get("errors"))).as("the mixed namespaces are refused, the import kept")
                .contains("The import '" + SHARED + "' writes Events elements in namespace " + FORK_NS,
                        "Don't drop an import");

        // ### 6. Promoted: the translation still writes the fork, its document kept.
        assertThat(list(json("build_status", args("build", BUILD)), "before_promotion"))
                .as("nothing outstanding before promotion").isEmpty();
        final String dest = "System/E2E Fork Promoted";
        folder(dest);
        final Map<String, Object> destinations = new LinkedHashMap<>();
        for (final String type : List.of("Feed", "Pipeline", "XSLT", "Documentation", "Index")) {
            destinations.put(type, dest);
        }
        final Map<String, Object> promoted = agreed("promote_build", args("build", BUILD, "destinations",
                destinations));
        dump("promote_build", promoted);
        assertThat(folderOf(xsltUuid)).as("promoted").startsWith(dest);
        assertThat(readXslt(xsltUuid).getData()).as("the promoted translation writes the fork")
                .contains("xmlns=\"" + FORK_NS + "\"").doesNotContain("event-logging:3");
        assertThat(KeptMapping.readMapping(readXslt(xsltUuid).getDescription()).orElseThrow().payload())
                .containsEntry("schema_uuid", fork.uuid());
    }

    @Test
    void aForkIsOfferedWhenNothingSettlesItAndSettledFromATemplateThatWritesIt() {
        final Fork fork = installFork();

        // ### 1. A Data Splitter feed, nothing written in the fork yet: the fork is offered beside event-logging.
        final String build = "fork-csv";
        final String feed = "E2E-FORK-CSV";
        createFeed(build, feed);
        final long raw = upload(feed, CSV_SAMPLE);
        final String converter = uuidOf(json("save_text_converter", args("build", build, "name", feed,
                "converter_type", "DATA_SPLITTER", "code", CSV_SPLITTER)));
        // The user is asked in a form, and picks the fork; every other form is agreed to.
        final List<Object> offered = new ArrayList<>();
        client.answeringWith(form -> {
            if (!String.valueOf(form.message()).contains("Which Events schema should the translation write")) {
                return McpTestClient.agree(form);
            }
            final Map<String, Object> content = new LinkedHashMap<>();
            if (form.requestedSchema().get("properties") instanceof final Map<?, ?> fields) {
                fields.forEach((name, field) -> {
                    if (field instanceof final Map<?, ?> schema
                        && schema.get("enum") instanceof final List<?> options) {
                        offered.addAll(options);
                        options.stream()
                                .filter(option -> String.valueOf(option).contains("this environment's fork"))
                                .findFirst()
                                .ifPresent(option -> content.put(String.valueOf(name), option));
                    }
                });
            }
            return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
        });
        final Map<String, Object> built;
        try {
            built = agreed("build_translation_xslt", args("mapping", csvMapping(), "stream_ids", List.of(raw),
                    "build", build, "name", feed + "-Events", "feeds", List.of(feed)));
        } finally {
            client.answeringWith(McpTestClient::agree);
        }
        dump("chosen in the form", built);
        assertThat(offered).as("the fork offered beside event-logging").anySatisfy(option -> assertThat(
                String.valueOf(option)).contains("this environment's fork", FORK_NS))
                .anySatisfy(option -> assertThat(String.valueOf(option)).contains("event-logging " + VERSION));
        assertThat(String.valueOf(built.get("schema_version_settled"))).contains("The user chose the environment's "
                                                                                 + "fork");
        final String xsltUuid = (String) map(built, "saved").get("uuid");
        assertThat(readXslt(xsltUuid).getData()).as("the Data Splitter's records written in the fork")
                .contains("xpath-default-namespace=\"records:2\"", "xmlns=\"" + FORK_NS + "\"");
        final String pipeline = (String) createPipeline(build, feed + "-Events",
                templateUuid("translation", "Event Data (Text)"),
                docProperty("dsParser", "textConverter", converter, "TextConverter")).get("uuid");
        assertThat(stepSample(pipeline, List.of(raw)).get("verdict")).as("stepped clean against the fork")
                .isEqualTo("clean");

        // ### 2. Another build, where the environment's translation template sets an XSLT writing the fork (and is
        // inherited by its pipelines): settled from it, without asking.
        final String header = create(XsltDoc.TYPE, "E2E-Fork-Header-" + STAMP, "System").getUuid();
        editXslt(header, doc -> doc.copy().data(sharedImport(FORK_NS)).build());
        final String template = fixtureTemplate("E2E Fork Events Template", "Event Data (Text)");
        asUser(admin, () -> {
            final DocRef ref = new DocRef(PipelineDoc.TYPE, template);
            final PipelineDoc doc = pipelineStore.readDocument(ref);
            pipelineStore.writeDocument(doc.copy().pipelineData(new PipelineDataBuilder(doc.getPipelineData())
                    .addProperty(property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, header)))
                    .build()).build());
        });
        for (final String child : List.of("E2E Fork Child A", "E2E Fork Child B")) {
            childPipeline(folder("System/E2E Fork Feeds"), child, template, List.of());
        }
        final String other = "fork-template";
        createFeed(other, "E2E-FORK-TPL");
        final long otherRaw = upload("E2E-FORK-TPL", CSV_SAMPLE);
        final Map<String, Object> settled = agreed("build_translation_xslt", args("mapping", csvMapping(),
                "stream_ids", List.of(otherRaw), "build", other, "name", "E2E-FORK-TPL-Events",
                "feeds", List.of("E2E-FORK-TPL")));
        dump("settled from the template", settled);
        assertThat(String.valueOf(settled.get("schema_version_settled"))).as("settled from the template")
                .contains("environment's fork", FORK_NS);
        assertThat(readXslt((String) map(settled, "saved").get("uuid")).getData())
                .contains("xmlns=\"" + FORK_NS + "\"");

        // ### 3. Raw XML fragments already Events in the fork: read in its namespace.
        final String fragments = ("<Event xmlns=\"" + FORK_NS + "\"><EventTime><TimeCreated>2026-10-01T08:00:00.000Z"
                                  + "</TimeCreated></EventTime><EventSource><System><Name>App</Name></System>"
                                  + "</EventSource><EventDetail><TypeId>t</TypeId></EventDetail></Event>\n")
                .repeat(2);
        final Map<String, Object> profiled = json("profile_sample", args("sample", fragments));
        dump("profile_sample", profiled);
        assertThat(profiled).containsEntry("event_logging", true);
        assertThat(map(profiled, "xslt_input")).containsEntry("namespace", FORK_NS);
    }

    // The test fork as an XML schema document in the event-logging schemas' group (that of the content pack's
    // 4.1.0), as an environment holds its own.
    private Fork installFork() {
        final String group = asUser(admin, () -> xmlSchemaStore.list().stream()
                .map(xmlSchemaStore::readDocument)
                .filter(doc -> ("file://event-logging-v" + VERSION + ".xsd").equals(doc.getSystemId()))
                .map(XmlSchemaDoc::getSchemaGroup)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No event-logging " + VERSION + " schema in the content "
                                                      + "pack")));
        assertThat(group).as("event-logging's schema group").isNotBlank();
        final DocRef ref = create(XmlSchemaDoc.TYPE, "Test Fork Events v" + VERSION, "System").getDocRef();
        asUser(admin, () -> xmlSchemaStore.writeDocument(xmlSchemaStore.readDocument(ref).copy()
                .systemId(FORK_SYSTEM_ID)
                .namespaceURI(FORK_NS)
                .schemaGroup(group)
                .data(textResource("test-fork-events-v1.2.0.xsd"))
                .build()));
        return new Fork(ref.getUuid(), group);
    }

    // A shared XSLT of the environment's: a named template writing part of an Event in the given namespace.
    private static String sharedImport(final String namespace) {
        return "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns=\"" + namespace + "\" "
               + "xmlns:stroom=\"stroom\" version=\"3.0\"><xsl:template name=\"streamFeed\"><Data Name=\"Feed\" "
               + "Value=\"{stroom:feed-name()}\"/></xsl:template></xsl:stylesheet>";
    }

    // A translation written by hand in event-logging:3 that imports the shared XSLT.
    private static String translationImporting(final String shared) {
        return "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns=\"event-logging:3\" "
               + "version=\"3.0\"><xsl:import href=\"" + shared + "\"/><xsl:template match=\"/\"><Events "
               + "Version=\"" + VERSION + "\"><Event><EventDetail><TypeId>t</TypeId></EventDetail></Event></Events>"
               + "</xsl:template></xsl:stylesheet>";
    }

    // The name a plan gives the field from a source path.
    @SuppressWarnings("unchecked")
    private static String fieldFrom(final Map<String, Object> plan, final String source) {
        return ((List<Map<String, Object>>) plan.get("fields")).stream()
                .filter(field -> source.equals(field.get("source")))
                .map(field -> (String) field.get("name"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No field from " + source + " in " + plan.get("fields")));
    }

    // The CSV sample's mapping: a logon, in the fork.
    private static Map<String, Object> csvMapping() {
        return Map.of("input", "data_splitter",
                "common", List.of(
                        Map.of("path", "EventTime/TimeCreated", "field", "time", "time_format",
                                "yyyy-MM-dd'T'HH:mm:ss", "timezone", "UTC"),
                        Map.of("path", "EventSource/System/Name", "value", "ForkCsv"),
                        Map.of("path", "EventSource/System/Environment", "value", "Test"),
                        Map.of("path", "EventSource/Generator", "value", "e2e"),
                        Map.of("path", "EventSource/Device/HostName", "field", "host"),
                        Map.of("path", "EventSource/User/Id", "field", "user"),
                        Map.of("path", "EventDetail/TypeId", "value", "Logon")),
                "events", List.of(Map.of("name", "logon", "fields", List.of(
                        Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                        Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user")))));
    }

    // A charge to an account, in the fork's own activity.
    private static Map<String, Object> mapping() {
        return Map.of("input", "json", "json_layout", "lines",
                "common", List.of(
                        Map.of("path", "EventTime/TimeCreated", "field", "ts", "time_format",
                                "yyyy-MM-dd'T'HH:mm:ssX"),
                        Map.of("path", "EventSource/System/Name", "value", "Fork"),
                        Map.of("path", "EventSource/System/Environment", "value", "Test"),
                        Map.of("path", "EventSource/Generator", "value", "e2e"),
                        Map.of("path", "EventSource/Device/HostName", "value", "h1"),
                        Map.of("path", "EventSource/User/Id", "field", "u"),
                        Map.of("path", "EventDetail/TypeId", "value", "Charge")),
                "events", List.of(Map.of("name", "charge", "fields", List.of(
                        Map.of("path", "EventDetail/Charge/Account", "field", "acct"),
                        Map.of("path", "EventDetail/Charge/Amount", "field", "amt")))));
    }


    // --------------------------------------------------------------------------------


    // The test fork's XML schema document, and the schema group it shares with event-logging.
    private record Fork(String uuid, String group) {

    }
}
