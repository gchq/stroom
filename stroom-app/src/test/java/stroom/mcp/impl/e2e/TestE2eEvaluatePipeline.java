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

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.mcp.impl.McpTestClient;
import stroom.meta.api.MetaProperties;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.security.api.SecurityContext;
import stroom.util.io.StreamUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// evaluate_pipeline over the real tools, one pipeline of each stage it has checks for:
///
/// 1. An events pipeline onboarded and processed clean: every section, in order, the verdict clean, its newest
///    Events valid with their field population, and the report's outline.
/// 2. An events pipeline made directly in Stroom (production content), processed, one record's event failing the
///    schema (IPAddress 'n/a'): its Error streams' blocking group, the same group stepping its inputs by default, and
///    its stored Events valid.
/// 3. A Lucene indexing pipeline whose XSLT, edited by hand, writes a field its index lacks: the index section
///    names it.
/// 4. A CEF pipeline to Kafka written by hand, its vendor a TODO constant: the cef section reports it, and that
///    delivery isn't tested.
/// 5. A reference data pipeline, processed, whose map an events pipeline looks up: the reference section lists the
///    map, its feed and that pipeline; write_documentation generates its Reference maps section.
@SuppressWarnings("unchecked")
class TestE2eEvaluatePipeline extends AbstractE2eTest {

    private static final String STAMP = String.valueOf(System.currentTimeMillis() % 100_000);
    private static final List<String> PRODUCTION_STREAMS = List.of("""
            time,user,host,ip,result
            2026-09-28T10:00:00,alice,ws01,10.0.0.1,ok
            2026-09-28T10:05:00,bob,ws02,10.0.0.2,fail
            """, """
            time,user,host,ip,result
            2026-09-29T09:00:00,dave,ws04,10.0.0.4,ok
            2026-09-29T09:20:00,frank,ws06,n/a,ok
            """);
    private static final String CEF_EVENTS = """
            <?xml version="1.1" encoding="UTF-8"?>
            <Events xmlns="event-logging:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" Version="3.5.2" \
            xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
            <Event><EventTime><TimeCreated>2026-09-27T23:48:10.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Environment>Prod</Environment></System>\
            <Generator>SecretServer Audit</Generator>
              <Device><HostName>ss-web-02</HostName></Device><User><Id>priya.patel</Id></User></EventSource>
             <EventDetail><TypeId>User-Login</TypeId><Description>User logged in</Description><Authenticate>\
            <Action>Logon</Action><User><Id>priya.patel</Id></User></Authenticate></EventDetail></Event>
            </Events>
            """;
    // Written by hand: the vendor left as a TODO constant for the user to fill in.
    private static final String CEF_XSLT = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xpath-default-namespace="event-logging:3" xmlns="kafka-records:1" \
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.0">
              <xsl:template match="/Events">
                <kafkaRecords xsi:schemaLocation="kafka-records:1 file://kafka-records-v1.1.xsd">
                  <xsl:apply-templates select="Event"/>
                </kafkaRecords>
              </xsl:template>
              <xsl:template match="Event">
                <kafkaRecord topic="arcsight-cef">
                  <value><xsl:value-of select="concat('CEF:0|TODO vendor: ask the user|SecretServer|11.4|', \
            EventDetail/TypeId, '|', EventDetail/Description, '|3|suser=', EventSource/User/Id)"/></value>
                </kafkaRecord>
              </xsl:template>
            </xsl:stylesheet>
            """;
    private static final String DIRECTORY = """
            user,name,department
            alice,Alice Anderson,Operations
            bob,Bob Brown,Finance
            """;

    @Inject
    private Store store;
    @Inject
    private FeedStore feedStore;
    @Inject
    private TextConverterStore textConverterStore;
    @Inject
    private SecurityContext securityContext;

    @Test
    void aCleanProcessedEventsPipelineIsEvaluatedSectionBySection() {
        final Onboarded csv = onboard(CSV_CASE);

        final Map<String, Object> result = json("evaluate_pipeline", args("pipeline_uuid", csv.pipelineUuid()));
        dump("evaluate_pipeline clean", result);

        assertThat(result.keySet()).containsExactly("pipeline", "processing", "stepping", "events", "coverage",
                "report");
        assertThat(map(result, "pipeline")).containsEntry("name", csv.pipelineName())
                .containsEntry("stage", "translation");
        assertThat(map(result, "processing")).containsEntry("status", "processed")
                .containsEntry("events_streams", 1);
        final Map<String, Object> stepping = map(result, "stepping");
        assertThat(stepping).as("stepped clean: %s", stepping.get("groups")).containsEntry("verdict", "clean")
                .containsEntry("streams_from", "the pipeline's newest inputs");
        assertThat(ids(stepping, "streams")).as("its inputs by default: the raw stream").containsExactly(csv.raw());
        final Map<String, Object> events = map(result, "events");
        assertThat(events).containsEntry("status", "checked").containsEntry("ok", true);
        assertThat(number(events.get("stream_id"))).isEqualTo(csv.events());
        assertThat(map(events, "field_population")).as("how often each part of the event is filled in")
                .isNotEmpty();
        assertThat(map(result, "coverage").get("status")).isIn("none_logged", "records_missed");
        final Map<String, Object> report = map(result, "report");
        assertThat((String) report.get("outline")).startsWith("# " + csv.pipelineName() + " - Evaluation")
                .contains("## Summary", "## Processing errors", "## Stepping", "## Event validity", "## Coverage",
                        "## Recommendations");
        assertThat((String) report.get("next")).contains("evaluation=true");
    }

    @Test
    void aProcessedEventsPipelineWithAnInvalidEventReportsItInProcessingAndStepping() {
        final String feed = "E2E-EVAL-LOGINS-" + STAMP;
        final ExplorerNode folder = folder("System/E2E Evaluate/" + feed);
        final String feedUuid = create(FeedDoc.TYPE, feed, folder).getUuid();
        asUser(admin, () -> feedStore.writeDocument(feedStore.readDocument(new DocRef(FeedDoc.TYPE, feedUuid))
                .copy().encoding("UTF-8").streamType(StreamTypeNames.RAW_EVENTS).build()));
        final String converter = create(TextConverterDoc.TYPE, feed + "-Splitter", folder).getUuid();
        asUser(admin, () -> textConverterStore.writeDocument(textConverterStore.readDocument(
                        new DocRef(TextConverterDoc.TYPE, converter)).copy()
                .converterType(TextConverterType.DATA_SPLITTER)
                .data(CSV_SPLITTER)
                .build()));
        final String xslt = create(XsltDoc.TYPE, feed + "-Events", folder).getUuid();
        editXslt(xslt, doc -> doc.copy().data(productionXslt()).build());
        final String pipeline = childPipeline(folder, feed + "-Events", templateUuid("translation",
                        "Event Data (Text)"),
                List.of(property("dsParser", "textConverter", new DocRef(TextConverterDoc.TYPE, converter,
                                feed + "-Splitter")),
                        property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, xslt, feed + "-Events"))));
        final List<Long> raw = new ArrayList<>();
        for (final String text : PRODUCTION_STREAMS) {
            raw.add(datafeed(feed, text));
        }
        feedFilter(feed, StreamTypeNames.RAW_EVENTS, pipeline);
        processAll();
        final Map<String, Object> done = json("wait_for_processing", args("pipeline_uuid", pipeline, "stream_ids",
                raw, "timeout_seconds", 10));
        dump("production processed", done);

        final Map<String, Object> result = json("evaluate_pipeline", args("pipeline_uuid", pipeline));
        dump("evaluate_pipeline invalid event", result);

        assertThat(result.keySet()).containsExactly("pipeline", "processing", "stepping", "events", "coverage",
                "report");
        final Map<String, Object> processing = map(result, "processing");
        assertThat(processing).containsEntry("status", "processed").containsEntry("verdict", "blocking");
        assertThat(list(processing, "groups")).as("the schema failure, from its Error streams")
                .anySatisfy(group -> assertThat(group).containsEntry("class", "blocking")
                        .containsEntry("reason", "Output fails schema validation"));
        assertThat(jsonOf(processing.get("groups"))).contains("'n/a'");
        final Map<String, Object> stepping = map(result, "stepping");
        assertThat(stepping).as("stepping its inputs gives the same error").containsEntry("verdict", "blocking")
                .containsEntry("records_stepped", 4);
        assertThat(ids(stepping, "streams")).containsExactlyInAnyOrderElementsOf(raw);
        assertThat(jsonOf(stepping.get("groups"))).contains("IPAddress");
        // The event that failed the schema was not stored: those stored are valid.
        assertThat(map(result, "events")).containsEntry("status", "checked").containsEntry("ok", true);
    }

    @Test
    void anIndexingPipelineWritingAFieldItsIndexLacksIsReported() {
        final Onboarded csv = onboard(CSV_CASE);
        client.answeringWith(choosing("Stroom flat convention"));
        client.json("get_field_conventions", Map.of());
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> template = templates("indexing").stream()
                .filter(candidate -> "lucene".equals(candidate.get("backend")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Lucene indexing template"));
        final String indexName = "E2E-EVAL-" + STAMP + "-INDEX";
        final Map<String, Object> draft = client.json("draft_index_mapping", Map.of("backend", "lucene",
                "index_name", indexName, "convention", "stroom-flat", "events_stream_ids", List.of(csv.events())));
        final Map<String, Object> plan = map(draft, "plan");
        final Map<String, Object> index = agreed("create_index_doc", Map.of("build", csv.build(), "backend",
                "lucene", "name", indexName, "time_field", plan.get("time_field")));
        final String xsltUuid = uuidOf(client.json("save_xslt", Map.of("build", csv.build(),
                "name", indexName + "-XSLT", "index_plan", plan)));
        // Edited by hand: a field the index doc doesn't have.
        editXslt(xsltUuid, doc -> {
            assertThat(doc.getData()).contains("</record>");
            return doc.copy().data(doc.getData().replaceFirst("</record>",
                    "<data name=\"NotInIndex\" value=\"x\"/></record>")).build();
        });
        final String pipelineUuid = uuidOf(agreed("create_indexing_pipeline", Map.of("build", csv.build(),
                "name", indexName + " - Indexing", "template_uuid", template.get("uuid"), "xslt_uuid", xsltUuid,
                "index_uuid", uuidOf(index))));

        final Map<String, Object> result = json("evaluate_pipeline", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(csv.events())));
        dump("evaluate_pipeline indexing", result);

        assertThat(result.keySet()).containsExactly("pipeline", "processing", "stepping", "index", "report");
        assertThat(map(result, "pipeline")).containsEntry("stage", "indexing").containsEntry("backend", "lucene");
        final Map<String, Object> checked = map(result, "index");
        assertThat(checked).containsEntry("status", "checked").containsEntry("backend", "lucene");
        assertThat(map(checked, "index")).containsEntry("uuid", uuidOf(index));
        assertThat(strings(checked, "written_not_in_index")).containsExactly("NotInIndex");
        assertThat((String) map(result, "report").get("outline")).contains("## Index fields")
                .doesNotContain("## Event validity");
    }

    @Test
    void aCefPipelineWithAHeaderFieldLeftTodoIsReported() {
        final String build = "eval-cef-" + STAMP;
        final String feed = "EVAL-CEF-" + STAMP;
        final Map<String, Object> created = agreed("create_feed", args("build", build, "name", feed,
                "stream_type", "Events"));
        final long eventsId = number(client.json("upload_sample", args("feed", feed, "sample", CEF_EVENTS,
                "stream_type", "Events")).get("stream_id"));
        final ExplorerNode buildFolder = folder(folderOf((String) created.get("uuid")));
        final DocRef kafka = create(KafkaConfigDoc.TYPE, "EVAL-KAFKA-" + STAMP, buildFolder).getDocRef();
        final String xsltUuid = uuidOf(client.json("save_xslt", args("build", build, "name", feed + "-CEF",
                "code", CEF_XSLT)));
        final String pipelineUuid = uuidOf(agreed("create_pipeline", args("build", build,
                "name", feed + "-Kafka", "standalone", "kafka", "set_properties", List.of(
                        docProperty("xsltFilter", "xslt", xsltUuid, XsltDoc.TYPE),
                        docProperty("standardKafkaProducer", "kafkaConfig", kafka.getUuid(), KafkaConfigDoc.TYPE)))));

        final Map<String, Object> result = json("evaluate_pipeline", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(eventsId)));
        dump("evaluate_pipeline cef", result);

        assertThat(result.keySet()).containsExactly("pipeline", "processing", "stepping", "cef", "report");
        assertThat(map(result, "pipeline")).containsEntry("stage", "forwarding");
        final Map<String, Object> cef = map(result, "cef");
        assertThat(cef).containsEntry("status", "checked").containsEntry("lines_stepped", 1);
        assertThat(list(cef, "header_todo")).singleElement()
                .satisfies(todo -> assertThat(todo).containsEntry("field", "Device Vendor")
                        .containsEntry("value", "TODO vendor: ask the user"));
        assertThat(jsonOf(cef.get("notes"))).contains("delivery to Kafka");
        assertThat((String) map(result, "report").get("outline")).contains("## CEF output");
    }

    @Test
    void aReferencePipelineListsItsMapAndThePipelineThatLooksItUp() {
        final String build = "eval-ref-" + STAMP;
        final String feed = "EVAL-USERS-" + STAMP;
        final Map<String, Object> found = client.json("find_reference_data", args());
        final Map<String, Object> template = map(found, "reference_data_template");
        agreed("create_feed", args("build", build, "name", feed, "stream_type", "Raw Reference"));
        final long raw = number(client.json("upload_sample", args("feed", feed, "sample", DIRECTORY,
                "stream_type", "Raw Reference", "effective_time", "2000-01-01T00:00:00.000Z")).get("stream_id"));
        client.json("build_data_splitter", args("stream_ids", List.of(raw), "save_as", feed, "build", build));
        final Map<String, Object> code = client.json("build_reference_xslt", args("mapping", args("input",
                "data_splitter", "maps", List.of(args("name", "USER_DIRECTORY", "key", "user", "values",
                        List.of(Map.of("element", "name", "field", "name"),
                                Map.of("element", "department", "field", "department")))))));
        assertThat(code).as("reference XSLT: %s", code.get("problems")).containsEntry("ok", true);
        client.json("save_xslt", args("build", build, "name", feed + "-Reference", "code", code.get("xslt")));
        final String loaderUuid = uuidOf(agreed("create_pipeline", args("build", build,
                "name", feed + "-Reference", "template_uuid", template.get("uuid"))));
        assertThat(stepSample(loaderUuid, List.of(raw))).as("the reference pipeline steps clean")
                .containsEntry("verdict", "clean");
        agreed("create_processor_filter", args("pipeline_uuid", loaderUuid, "feed", feed, "created_after",
                "2000-01-01T00:00:00.000Z"));
        processAll();
        json("wait_for_processing", args("pipeline_uuid", loaderUuid, "stream_ids", List.of(raw),
                "timeout_seconds", 10));
        // An events pipeline that looks the map up through a reference to the feed.
        final String eventsBuild = "eval-badge-" + STAMP;
        final String eventsFeed = "EVAL-BADGE-" + STAMP;
        agreed("create_feed", args("build", eventsBuild, "name", eventsFeed));
        json("save_text_converter", args("build", eventsBuild, "name", eventsFeed, "converter_type",
                "DATA_SPLITTER", "code", CSV_SPLITTER));
        final Map<String, Object> text = list(client.json("find_pipeline_templates", args("stage", "translation")),
                "candidates").stream()
                .filter(candidate -> "DSParser".equals(candidate.get("parser")))
                .findFirst()
                .orElseThrow();
        final String eventsName = eventsFeed + "-Events";
        agreed("create_pipeline", args("build", eventsBuild, "name", eventsName, "template_uuid", text.get("uuid"),
                "references", List.of(Map.of("feed", feed))));

        final Map<String, Object> result = json("evaluate_pipeline", args("pipeline_uuid", loaderUuid));
        dump("evaluate_pipeline reference", result);

        assertThat(result.keySet()).containsExactly("pipeline", "processing", "stepping", "reference", "report");
        assertThat(map(result, "pipeline")).containsEntry("stage", "reference");
        assertThat(ids(map(result, "stepping"), "streams")).as("its feed's newest Raw Reference stream")
                .containsExactly(raw);
        final Map<String, Object> reference = map(result, "reference");
        assertThat(reference).containsEntry("status", "checked").containsEntry("feeds", List.of(feed));
        assertThat(list(reference, "maps")).singleElement()
                .satisfies(map -> {
                    assertThat(map).containsEntry("map", "USER_DIRECTORY");
                    assertThat(strings(map, "value")).contains("department", "name");
                    assertThat(map(map, "seen")).containsEntry("entries", 2)
                            .containsEntry("example_key", "alice")
                            .containsEntry("example_value", Map.of("name", "Alice Anderson", "department",
                                    "Operations"));
                });
        assertThat(list(reference, "looked_up_by")).anySatisfy(user -> assertThat(user)
                .containsEntry("pipeline", eventsName).containsEntry("feed", feed));
        assertThat((String) map(result, "report").get("outline")).contains("## Reference maps");

        // Documented: the same facts, as a Reference maps section.
        final Map<String, Object> doc = agreed("write_documentation", args("build", build, "pipeline_uuid",
                loaderUuid, "markdown", "# " + feed + "-Reference\n\n## Purpose and data\n\nThe user directory.\n",
                "stream_ids", List.of(raw)));
        dump("write_documentation reference", doc);
        final String section = (String) doc.get("reference_maps");
        assertThat(section).startsWith("Counts are of 2 sample records")
                .contains("| USER_DIRECTORY |", "department", "alice", "name=Alice Anderson", eventsName);
        assertThat(documentation(uuidOf(doc))).contains("## Reference maps", "USER_DIRECTORY");
    }

    // The translation as someone wrote it, by hand: every IP written, n/a too.
    private static String productionXslt() {
        return """
                <?xml version="1.1" encoding="UTF-8"?>
                <xsl:stylesheet xpath-default-namespace="records:2" xmlns="event-logging:3" xmlns:stroom="stroom"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="3.0">
                  <xsl:template match="records">
                    <Events xsi:schemaLocation="event-logging:3 file://event-logging-vVERSION.xsd" Version="VERSION">
                      <xsl:apply-templates />
                    </Events>
                  </xsl:template>
                  <xsl:template match="record">
                    <Event>
                      <EventTime><TimeCreated><xsl:value-of select="stroom:format-date(data[@name='time']/@value, \
                'yyyy-MM-dd''T''HH:mm:ss')" /></TimeCreated></EventTime>
                      <EventSource>
                        <System><Name>E2E</Name><Environment>Dev</Environment></System>
                        <Generator>e2e-translation</Generator>
                        <Device><HostName><xsl:value-of select="data[@name='host']/@value" /></HostName>\
                <IPAddress><xsl:value-of select="data[@name='ip']/@value" /></IPAddress></Device>
                        <User><Id><xsl:value-of select="data[@name='user']/@value" /></Id></User>
                      </EventSource>
                      <EventDetail>
                        <TypeId>Logon</TypeId>
                        <Description>User logon</Description>
                        <Authenticate>
                          <Action>Logon</Action>
                          <User><Id><xsl:value-of select="data[@name='user']/@value" /></Id></User>
                          <Outcome><Success><xsl:value-of select="data[@name='result']/@value = 'ok'" /></Success>\
                </Outcome>
                        </Authenticate>
                      </EventDetail>
                    </Event>
                  </xsl:template>
                </xsl:stylesheet>
                """.replace("VERSION", VERSION);
    }

    // A raw stream sent to the feed, as a datafeed POST makes one.
    private long datafeed(final String feed, final String text) {
        return securityContext.asProcessingUserResult(() -> {
            try (final Target target = store.openTarget(MetaProperties.builder()
                    .feedName(feed)
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .build())) {
                try (final OutputStreamProvider provider = target.next();
                        final OutputStream out = provider.get()) {
                    StreamUtil.streamToStream(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                            out);
                }
                return target.getMeta().getId();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
