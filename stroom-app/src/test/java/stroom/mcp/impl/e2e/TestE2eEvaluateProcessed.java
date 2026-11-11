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

import stroom.ai.shared.AiToolAccess;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.kafka.impl.KafkaConfigStore;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.mcp.impl.McpTestClient;
import stroom.mcp.impl.McpTestClients;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.api.DocumentPermissionService;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// evaluate_pipeline over the real tools, on what only its unit tests covered: pipelines processed with errors, a
/// pipeline of no stage it has checks for, and users without the permissions some sections need.
///
/// 1. A Lucene indexing pipeline whose XSLT writes a field its index lacks, processed: Stroom's warning for each
///    document is in its Error stream, and the processing section triages it.
/// 2. A CEF pipeline to Kafka, processed with one Event its XSLT logs an error for, through a KafkaConfig whose
///    broker isn't there: its Error stream is triaged.
/// 3. A reference data pipeline, processed with a malformed record (no key) its XSLT logs an error for: its Error
///    stream is triaged.
/// 4. A pipeline of no stage evaluate_pipeline has checks for (text that isn't CEF, to a file): the general
///    sections, its checks not applicable.
/// 5. A user with View Data but not Stepping, and one with Stepping but not View Data: each is told which sections
///    they can't have, and given the rest.
@SuppressWarnings("unchecked")
class TestE2eEvaluateProcessed extends AbstractE2eTest {

    private static final String STAMP = String.valueOf(System.currentTimeMillis() % 100_000);
    // Two Events: the second has no user.
    private static final String EVENTS = """
            <?xml version="1.1" encoding="UTF-8"?>
            <Events xmlns="event-logging:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" Version="3.5.2" \
            xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
            <Event><EventTime><TimeCreated>2026-09-27T23:48:10.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Environment>Prod</Environment></System>\
            <Generator>SecretServer Audit</Generator>
              <Device><HostName>ss-web-02</HostName></Device><User><Id>priya.patel</Id></User></EventSource>
             <EventDetail><TypeId>User-Login</TypeId><Description>User logged in</Description><Authenticate>\
            <Action>Logon</Action><User><Id>priya.patel</Id></User></Authenticate></EventDetail></Event>
            <Event><EventTime><TimeCreated>2026-09-27T23:56:15.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Environment>Prod</Environment></System>\
            <Generator>SecretServer Audit</Generator>
              <Device><HostName>ss-web-01</HostName></Device></EventSource>
             <EventDetail><TypeId>Secret-View</TypeId><Description>Secret viewed</Description><View><Resource>\
            <Type>Secret</Type><Name>Firewall</Name></Resource></View></EventDetail></Event>
            </Events>
            """;
    // Written by hand: an Event with no user is logged as an error, and still sent.
    private static final String KAFKA_CEF = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xpath-default-namespace="event-logging:3" xmlns="kafka-records:1" \
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:stroom="stroom" \
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.0">
              <xsl:template match="/Events">
                <kafkaRecords xsi:schemaLocation="kafka-records:1 file://kafka-records-v1.1.xsd">
                  <xsl:apply-templates select="Event"/>
                </kafkaRecords>
              </xsl:template>
              <xsl:template match="Event">
                <xsl:if test="not(EventSource/User/Id)">
                  <xsl:sequence select="stroom:log('ERROR', concat('No user to send for event ', \
            EventDetail/TypeId))"/>
                </xsl:if>
                <kafkaRecord topic="arcsight-cef">
                  <value><xsl:value-of select="concat('CEF:0|Delinea|SecretServer|11.4|', EventDetail/TypeId, \
            '|', EventDetail/Description, '|3|suser=', EventSource/User/Id)"/></value>
                </kafkaRecord>
              </xsl:template>
            </xsl:stylesheet>
            """;
    // A broker that isn't there, given up on quickly.
    private static final String NO_BROKER = """
            bootstrap.servers=localhost:1
            max.block.ms=500
            request.timeout.ms=500
            delivery.timeout.ms=1000
            linger.ms=0
            """;
    // Text that isn't CEF: a CSV line per Event, as a batch export might write.
    private static final String CSV_LINES = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xpath-default-namespace="event-logging:3" version="2.0">
              <xsl:output method="text"/>
              <xsl:template match="/Events"><xsl:apply-templates select="Event"/></xsl:template>
              <xsl:template match="Event">
                <xsl:value-of select="string-join((EventTime/TimeCreated, EventDetail/TypeId, \
            EventSource/User/Id), ',')"/>
                <xsl:text>&#10;</xsl:text>
              </xsl:template>
            </xsl:stylesheet>
            """;
    // A Raw Reference directory with a malformed record: no user, the map's key.
    private static final String DIRECTORY = """
            user,name,department
            alice,Alice Anderson,Operations
            ,Nobody Known,Finance
            """;
    // Added to the generated reference XSLT: a record with no key is logged as an error, and not loaded.
    private static final String NO_KEY = """
            <xsl:template xmlns:stroom="stroom" xpath-default-namespace="records:2" \
            match="record[not(normalize-space(data[@name='user']/@value))]" priority="10">
                <xsl:sequence select="stroom:log('ERROR', concat('Malformed directory record, no user: ', \
            data[@name='name']/@value))"/>
              </xsl:template>
            </xsl:stylesheet>""";

    @Inject
    private McpTestClients clients;
    @Inject
    private KafkaConfigStore kafkaConfigStore;
    @Inject
    private DocumentPermissionService documentPermissionService;

    @Test
    void anIndexingPipelineProcessedWithAFieldItsIndexLacksHasItsWarningsTriaged() {
        final Onboarded csv = onboard(CSV_CASE);
        client.answeringWith(choosing("Stroom flat convention"));
        client.json("get_field_conventions", Map.of());
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> template = templates("indexing").stream()
                .filter(candidate -> "lucene".equals(candidate.get("backend")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Lucene indexing template"));
        final String indexName = "E2E-PROC-" + STAMP + "-INDEX";
        final Map<String, Object> draft = client.json("draft_index_mapping", Map.of("backend", "lucene",
                "index_name", indexName, "convention", "stroom-flat", "events_stream_ids", List.of(csv.events())));
        final Map<String, Object> plan = map(draft, "plan");
        final Map<String, Object> index = agreed("create_index_doc", Map.of("build", csv.build(), "backend",
                "lucene", "name", indexName, "time_field", plan.get("time_field")));
        final String xsltUuid = uuidOf(client.json("save_xslt", Map.of("build", csv.build(),
                "name", indexName + "-XSLT", "index_plan", plan)));
        // Edited by hand: a field the index doc doesn't have, which Stroom warns of for each document.
        editXslt(xsltUuid, doc -> doc.copy().data(doc.getData().replaceFirst("</record>",
                "<data name=\"NotInIndex\" value=\"x\"/></record>")).build());
        final String pipelineUuid = uuidOf(agreed("create_indexing_pipeline", Map.of("build", csv.build(),
                "name", indexName + " - Indexing", "template_uuid", template.get("uuid"), "xslt_uuid", xsltUuid,
                "index_uuid", uuidOf(index))));
        // Made as the environment's own: create_processor_filter would want a clean step first.
        feedFilter(csv.feed(), StreamTypeNames.EVENTS, pipelineUuid);
        processAll();
        flushIndexShards();

        final Map<String, Object> result = json("evaluate_pipeline", args("pipeline_uuid", pipelineUuid));
        dump("evaluate_pipeline indexing processed", result);

        assertThat(result.keySet()).containsExactly("pipeline", "processing", "stepping", "index", "report");
        final Map<String, Object> processing = map(result, "processing");
        assertThat(processing).containsEntry("status", "processed").containsEntry("error_streams", 1);
        assertThat(list(processing, "groups")).as("Stroom's warning, from its Error stream")
                .anySatisfy(group -> assertThat(group).containsEntry("severity", "WARNING"));
        assertThat(jsonOf(processing.get("groups"))).contains("Attempt to index unknown field: NotInIndex");
        final Map<String, Object> stepping = map(result, "stepping");
        assertThat(stepping).containsEntry("streams_from", "the pipeline's newest inputs");
        assertThat(ids(stepping, "streams")).as("the Error stream's input").containsExactly(csv.events());
        assertThat(strings(map(result, "index"), "written_not_in_index")).containsExactly("NotInIndex");
    }

    @Test
    void aKafkaCefPipelineProcessedWithErrorsHasThemTriaged() {
        final String build = "proc-cef-" + STAMP;
        final String feed = "PROC-CEF-" + STAMP;
        final Sample sample = eventsFeed(build, feed);
        final long eventsId = sample.eventsId();
        final ExplorerNode folder = folder(folderOf(sample.feedUuid()));
        final DocRef kafka = create(KafkaConfigDoc.TYPE, "PROC-KAFKA-" + STAMP, folder).getDocRef();
        asUser(admin, () -> kafkaConfigStore.writeDocument(kafkaConfigStore.readDocument(kafka).copy()
                .data(NO_BROKER).build()));
        final String xsltUuid = saveXslt(build, feed + "-CEF", KAFKA_CEF);
        final String pipelineUuid = uuidOf(agreed("create_pipeline", args("build", build,
                "name", feed + "-Kafka", "standalone", "kafka", "set_properties", List.of(
                        docProperty("xsltFilter", "xslt", xsltUuid, XsltDoc.TYPE),
                        docProperty("standardKafkaProducer", "kafkaConfig", kafka.getUuid(), KafkaConfigDoc.TYPE)))));
        feedFilter(feed, StreamTypeNames.EVENTS, pipelineUuid);
        processAll();

        final Map<String, Object> result = json("evaluate_pipeline", args("pipeline_uuid", pipelineUuid));
        dump("evaluate_pipeline kafka cef processed", result);

        assertThat(result.keySet()).containsExactly("pipeline", "processing", "stepping", "cef", "report");
        assertThat(map(result, "pipeline")).containsEntry("stage", "forwarding").containsEntry("backend", "kafka");
        final Map<String, Object> processing = map(result, "processing");
        assertThat(processing).containsEntry("status", "processed").containsEntry("error_streams", 1);
        assertThat(jsonOf(processing.get("groups"))).as("the error its XSLT logged")
                .contains("No user to send for event Secret-View");
        assertThat(ids(map(result, "stepping"), "streams")).containsExactly(eventsId);
        final Map<String, Object> cef = map(result, "cef");
        assertThat(cef).containsEntry("status", "checked").containsEntry("lines_stepped", 2);
        assertThat(jsonOf(cef.get("notes"))).contains("delivery to Kafka");
    }

    @Test
    void aReferencePipelineProcessedWithAMalformedRecordHasItsErrorTriaged() {
        final String build = "proc-ref-" + STAMP;
        final String feed = "PROC-USERS-" + STAMP;
        final Map<String, Object> template = map(client.json("find_reference_data", args()),
                "reference_data_template");
        agreed("create_feed", args("build", build, "name", feed, "stream_type", "Raw Reference"));
        final long raw = number(client.json("upload_sample", args("feed", feed, "sample", DIRECTORY,
                "stream_type", "Raw Reference", "effective_time", "2000-01-01T00:00:00.000Z")).get("stream_id"));
        client.json("build_data_splitter", args("stream_ids", List.of(raw), "save_as", feed, "build", build));
        final Map<String, Object> code = client.json("build_reference_xslt", args("mapping", args("input",
                "data_splitter", "maps", List.of(args("name", "USER_DIRECTORY", "key", "user", "values",
                        List.of(Map.of("element", "name", "field", "name")))))));
        assertThat(code).as("reference XSLT: %s", code.get("problems")).containsEntry("ok", true);
        final String xsltUuid = uuidOf(client.json("save_xslt", args("build", build, "name", feed + "-Reference",
                "code", code.get("xslt"))));
        editXslt(xsltUuid, doc -> doc.copy().data(doc.getData().replace("</xsl:stylesheet>", NO_KEY)).build());
        final String loaderUuid = uuidOf(agreed("create_pipeline", args("build", build,
                "name", feed + "-Reference", "template_uuid", template.get("uuid"))));
        feedFilter(feed, StreamTypeNames.RAW_REFERENCE, loaderUuid);
        processAll();

        final Map<String, Object> result = json("evaluate_pipeline", args("pipeline_uuid", loaderUuid));
        dump("evaluate_pipeline reference processed", result);

        assertThat(result.keySet()).containsExactly("pipeline", "processing", "stepping", "reference", "report");
        final Map<String, Object> processing = map(result, "processing");
        assertThat(processing).containsEntry("status", "processed").containsEntry("error_streams", 1);
        assertThat(jsonOf(processing.get("groups"))).as("the malformed record, from its Error stream")
                .contains("Malformed directory record, no user: Nobody Known");
        assertThat(ids(map(result, "stepping"), "streams")).containsExactly(raw);
        assertThat(list(map(result, "reference"), "maps")).singleElement()
                .satisfies(map -> assertThat(map).containsEntry("map", "USER_DIRECTORY"));
    }

    @Test
    void aPipelineOfNoKnownStageGetsTheGeneralSections() {
        final String build = "proc-csv-" + STAMP;
        final String feed = "PROC-CSV-" + STAMP;
        final long eventsId = eventsFeed(build, feed).eventsId();
        final String xsltUuid = saveXslt(build, feed + "-CSV", CSV_LINES);
        final String pipelineUuid = textPipeline(build, feed, textTemplate(feed), xsltUuid);

        final Map<String, Object> result = json("evaluate_pipeline", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(eventsId)));
        dump("evaluate_pipeline other", result);

        assertThat(result.keySet()).containsExactly("pipeline", "processing", "stepping", "checks", "report");
        assertThat(map(result, "pipeline")).as("text that isn't CEF: no stage of its own")
                .containsEntry("stage", "other").doesNotContainKey("backend");
        assertThat(map(result, "processing")).containsEntry("status", "no_error_streams");
        assertThat(map(result, "stepping")).containsEntry("status", "stepped")
                .containsEntry("streams_from", "stream_ids");
        assertThat(map(result, "checks")).containsEntry("status", "not_applicable");
        assertThat((String) map(result, "report").get("outline")).contains("## Processing errors", "## Stepping",
                "## Recommendations").doesNotContain("## CEF output", "## Index fields");
    }

    @Test
    void aUserWithoutAPermissionAPartNeedsIsToldSoAndGivenTheRest() {
        final String build = "proc-perm-" + STAMP;
        final String feed = "PROC-PERM-" + STAMP;
        final Sample sample = eventsFeed(build, feed);
        final long eventsId = sample.eventsId();
        final String xsltUuid = saveXslt(build, feed + "-CSV", CSV_LINES);
        final String template = textTemplate(feed);
        final String pipelineUuid = textPipeline(build, feed, template, xsltUuid);
        final UserRef viewer = user("proc-viewer-" + STAMP, Set.of(AppPermission.USE_MCP_TOOLS,
                AppPermission.VIEW_DATA_PERMISSION));
        final UserRef stepper = user("proc-stepper-" + STAMP, Set.of(AppPermission.USE_MCP_TOOLS,
                AppPermission.STEPPING_PERMISSION));
        toolPolicy("Read only " + STAMP, AiToolAccess.READ_ONLY, viewer, stepper);
        for (final UserRef user : List.of(viewer, stepper)) {
            mayView(user, new DocRef(PipelineDoc.TYPE, pipelineUuid), new DocRef(PipelineDoc.TYPE, template),
                    new DocRef(XsltDoc.TYPE, xsltUuid), new DocRef(FeedDoc.TYPE, sample.feedUuid()));
        }

        final Map<String, Object> viewed = clients.client(viewer, false).json("evaluate_pipeline", args(
                "pipeline_uuid", pipelineUuid, "stream_ids", List.of(eventsId)));
        dump("evaluate_pipeline without Stepping", viewed);
        assertThat(viewed.keySet()).containsExactly("pipeline", "processing", "stepping", "checks", "report");
        assertThat(map(viewed, "pipeline")).containsEntry("uuid", pipelineUuid);
        assertThat(map(viewed, "processing")).as("View Data: its streams are read")
                .containsEntry("status", "no_error_streams");
        assertThat(map(viewed, "stepping")).containsEntry("status", "unavailable");
        assertThat((String) map(viewed, "stepping").get("reason")).contains("Stepping");

        final Map<String, Object> stepped = clients.client(stepper, false).json("evaluate_pipeline", args(
                "pipeline_uuid", pipelineUuid, "stream_ids", List.of(eventsId)));
        dump("evaluate_pipeline without View Data", stepped);
        assertThat(stepped.keySet()).containsExactly("pipeline", "processing", "stepping", "checks", "report");
        assertThat(map(stepped, "processing")).containsEntry("status", "unavailable");
        assertThat((String) map(stepped, "processing").get("reason")).contains("View Data");
        assertThat(map(stepped, "report")).containsKey("outline");
    }

    // A feed of Events in the build, with the sample uploaded.
    private Sample eventsFeed(final String build, final String feed) {
        final String feedUuid = uuidOf(agreed("create_feed", args("build", build, "name", feed,
                "stream_type", "Events")));
        return new Sample(feedUuid, number(client.json("upload_sample", args("feed", feed, "sample", EVENTS,
                "stream_type", "Events")).get("stream_id")));
    }

    // A template made directly in Stroom writing an XSLT's text to a file.
    private String textTemplate(final String feed) {
        final ExplorerNode folder = folder("System/E2E Evaluate Processed/" + feed);
        final Path output = Path.of(System.getProperty("java.io.tmpdir"), "e2e-" + feed + ".csv");
        return textFileTemplate(folder, "E2E text to file " + feed, output.toString());
    }

    // A pipeline in the build from the template, its XSLT set.
    private String textPipeline(final String build, final String feed, final String template, final String xslt) {
        return uuidOf(createPipeline(build, feed + "-File", template,
                docProperty("xsltFilter", "xslt", xslt, XsltDoc.TYPE)));
    }

    // Each document viewable by the user, as an administrator grants it.
    private void mayView(final UserRef user, final DocRef... docs) {
        asUser(admin, () -> {
            for (final DocRef doc : docs) {
                documentPermissionService.setPermission(doc, user, DocumentPermission.VIEW);
            }
        });
    }


    // --------------------------------------------------------------------------------


    /// A sample of Events uploaded to a feed.
    ///
    /// @param feedUuid The feed.
    /// @param eventsId The Events stream.
    private record Sample(String feedUuid, long eventsId) {

    }
}
