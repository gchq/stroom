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
import stroom.documentation.impl.DocumentationStore;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.mcp.impl.McpTestClient;
import stroom.meta.api.MetaProperties;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.pipeline.xslt.XsltStore;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.util.io.StreamUtil;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_evaluate_and_fix.py`: evaluating an existing events pipeline, and fixing a
/// reported issue in it, driving the real tools. The pipeline is production content the server did not build (a
/// feed, text converter, XSLT and pipeline made directly in Stroom, outside any workspace, processing two raw
/// streams of CSV logons through its own processor filter), with the script's flaws: an 'n/a' IP fails the schema,
/// a locked account's logon is written as a success, the agent column is never read, and a record with no IP
/// leaves IPAddress out. Every numbered part is covered: the evaluation's 1 to 6 (describe, errors first, the
/// input never read, the events' inventory, a suggested fix proven, the report saved beside the pipeline with
/// nothing else changed) and the fix's 1 to 4 (locate the reported event, reproduce it, a fix proven to change only
/// the reported field, applied through a working copy, documented and promoted, the production XSLT backed up).
///
/// The tools the script names that are now part of Stroom's own: find_documents is folder_contents, read_stream
/// is fetch_events with streamId, describe_document is read_document, and update_xslt is save_xslt with uuid.
@SuppressWarnings("unchecked")
class TestE2eEvaluateAndFix extends AbstractE2eTest {

    private static final List<String> STREAMS = List.of("""
            time,user,host,ip,result,agent
            2026-09-28T10:00:00,alice,ws01,10.0.0.1,ok,curl
            2026-09-28T10:05:00,bob,ws02,10.0.0.2,fail,firefox
            2026-09-28T10:07:30,carol,ws03,10.0.0.3,locked,edge
            """, """
            time,user,host,ip,result,agent
            2026-09-29T09:00:00,dave,ws04,,ok,curl
            2026-09-29T09:10:00,erin,ws05,10.0.0.5,locked,chrome
            2026-09-29T09:20:00,frank,ws06,n/a,ok,curl
            """);
    private static final String IP = "data[@name='ip']/@value";
    // An address (IPv4, or IPv6 with colons), not n/a.
    private static final String IP_FIX = "matches(" + IP + ", '^[0-9.]+$|:')";
    private static final String BUG = "data[@name='result']/@value = ('ok', 'locked')";
    private static final String FIX = "data[@name='result']/@value = 'ok'";
    private static final String SUCCESS = "Event/EventDetail/Authenticate/Outcome/Success";
    private static final String FEED = "E2E-LOGINS";
    private static final String FOLDER = "System/E2E Production/logins";

    @Inject
    private Store store;
    @Inject
    private FeedStore feedStore;
    @Inject
    private TextConverterStore textConverterStore;
    @Inject
    private XsltStore xsltStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocumentationStore documentationStore;
    @Inject
    private ProcessorFilterService processorFilterService;
    @Inject
    private ProcessorService processorService;
    @Inject
    private SecurityContext securityContext;

    @Override
    boolean followsNext() {
        return true;
    }

    @Test
    void evaluateThenFix() {
        final Production prod = production();
        evaluate(prod);
        fix(prod);
    }

    // ### production content the server did not build
    private Production production() {
        final ExplorerNode folder = folder(FOLDER);
        final String feed = create(FeedDoc.TYPE, FEED, folder).getUuid();
        asUser(admin, () -> feedStore.writeDocument(feedStore.readDocument(new DocRef(FeedDoc.TYPE, feed)).copy()
                .encoding("UTF-8")
                .streamType(StreamTypeNames.RAW_EVENTS)
                .build()));
        final String converter = create(TextConverterDoc.TYPE, FEED + "-Splitter", folder).getUuid();
        asUser(admin, () -> textConverterStore.writeDocument(textConverterStore.readDocument(
                        new DocRef(TextConverterDoc.TYPE, converter)).copy()
                .converterType(TextConverterType.DATA_SPLITTER)
                .data(CSV_SPLITTER)
                .build()));
        final String xslt = create(XsltDoc.TYPE, FEED + "-Events", folder).getUuid();
        editXslt(xslt, doc -> doc.copy().data(productionXslt()).build());
        final String pipeline = childPipeline(folder, FEED + "-Events",
                templateUuid("translation", "Event Data (Text)"),
                List.of(property("dsParser", "textConverter", new DocRef(TextConverterDoc.TYPE, converter,
                                FEED + "-Splitter")),
                        property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, xslt, FEED + "-Events"))));
        final List<Long> raw = new ArrayList<>();
        for (final String text : STREAMS) {
            raw.add(datafeed(text));
        }
        // Its own processor filter on its feed, as production would have.
        productionFilter(pipeline);
        processAll();
        final Map<String, Object> done = json("wait_for_processing", args("pipeline_uuid", pipeline, "stream_ids",
                raw, "timeout_seconds", 10));
        final Map<Long, Long> byRaw = new LinkedHashMap<>();
        for (final Map<String, Object> stream : list(done, "streams")) {
            byRaw.put(((Number) stream.get("input")).longValue(), ((Number) ((List<?>) stream.get("events"))
                    .getFirst()).longValue());
        }
        assertThat(byRaw).as("production: raw streams %s, Events %s", raw, done.get("streams")).hasSize(2);
        return new Production(pipeline, xslt, converter, raw, raw.stream().map(byRaw::get).toList());
    }

    private void evaluate(final Production prod) {
        final Map<String, String> versions = versions(prod);
        // ### evaluate: 1. describe the pipeline and what it processes
        final String found = McpTestClient.text(client.call("folder_contents", args("name", FEED + "-Events",
                "types", List.of("Pipeline"))));
        assertThat(found).as("found by name").contains(prod.pipeline());
        final String described = McpTestClient.text(client.call("read_document", args("uuid", prod.pipeline())));
        assertThat(described).as("its template, elements and own XSLT").contains("Event Data (Text)",
                "translationFilter", FEED + "-Events");
        final Map<String, Object> status = json("processing_status", args("pipeline_uuid", prod.pipeline()));
        assertThat(jsonOf(status)).as("the feed its processor filter covers").contains(FEED);

        // ### 2. errors worth worrying about, schema compliance first
        final Map<String, Object> errors = json("summarise_streams", args("stream_ids", prod.raw(), "kind",
                "errors"));
        dump("summarise_streams errors", errors);
        final List<Map<String, Object>> blocking = groupsOf(errors).stream()
                .filter(group -> "blocking".equals(group.get("class")))
                .toList();
        // Stroom reports one schema failure as two messages (the pattern facet, then the element's value).
        assertThat(blocking).as("the Error streams' blocking errors are one schema failure: IPAddress 'n/a'")
                .isNotEmpty()
                .allSatisfy(group -> {
                    assertThat(String.valueOf(list(group, "examples").getFirst().get("message"))).contains("'n/a'");
                    assertThat(group).containsEntry("reason", "Output fails schema validation")
                            .containsEntry("count", 1);
                });
        assertThat(jsonOf(blocking)).contains("IPAddress");
        int valid = 0;
        int total = 0;
        for (final long stream : prod.events()) {
            final List<String> records = strings(json("fetch_events", args("streamId", stream, "firstRecord", 0,
                    "recordCount", 20)), "records");
            for (final String record : records) {
                final Map<String, Object> checked = json("check_events", args("events_xml", record));
                total++;
                if (Boolean.TRUE.equals(map(checked, "schema").get("valid"))) {
                    valid++;
                }
            }
        }
        final Map<String, Object> sample = stepSample(prod.pipeline(), prod.raw());
        assertThat(sample.get("verdict")).as("stepping the current code gives the same error now")
                .isEqualTo("blocking");
        assertThat(sample).containsEntry("records_stepped", 6);
        // The event that failed the schema was not stored: the stored events are all valid, and one record is
        // missing.
        assertThat(List.of(valid, total)).as("stored events valid of stored events").containsExactly(5, 5);
        assertThat(6 - total).as("one record produced no event").isOne();

        // ### 3-4. the translation: input it never reads; the events
        final Map<String, Object> translation = section(McpTestClient.text(client.call("read_document",
                args("uuid", prod.xslt()))), "Translation");
        final Set<String> read = new TreeSet<>(strings(translation, "input_fields"));
        final Map<String, Object> first = json("fetch_events", args("streamId", prod.raw().getFirst(),
                "firstRecord", 0, "recordCount", 1));
        assertThat(first.get("header")).as("the raw stream's header line is given with its records").isNotNull();
        final Set<String> unused = new TreeSet<>(List.of(((String) first.get("header")).split(",")));
        unused.removeAll(read);
        assertThat(unused).as("the raw data's fields it never reads (it reads %s)", read).containsExactly("agent");
        final Map<String, Object> inventory = json("summarise_streams", args("stream_ids", prod.events(), "kind",
                "events"));
        assertThat(map(inventory, "type_ids")).as("5 logons").isEqualTo(Map.of("Logon", 5));
        assertThat(((Number) map(inventory, "path_population").get("EventSource/Device/IPAddress")).doubleValue())
                .as("IPAddress in 80% of events").isEqualTo(80.0);

        // ### 5. the user asks for a fix for the schema error: suggested and proven
        final String code = readXslt(prod.xslt()).getData();
        final String ipLine = "<xsl:if test=\"" + IP + " != ''\">";
        assertThat(code.split(Pattern.quote(ipLine), -1)).hasSize(2);
        final String draft = code.replace(ipLine, "<xsl:if test=\"" + IP_FIX + "\">");
        final Map<String, Object> proven = json("summarise_fix", args("pipeline_uuid", prod.pipeline(), "element",
                "translationFilter", "draft", draft, "stream_ids", prod.raw(), "expected_paths",
                List.of("Event/EventSource/Device/IPAddress"), "issue",
                "IPAddress 'n/a' fails the event-logging schema"));
        dump("summarise_fix IPAddress", proven);
        assertThat(proven).as("ready: only IPAddress changes, on the one record, and the schema error is gone")
                .containsEntry("ready", true)
                .containsEntry("records_changed", 1);
        assertThat(paths(proven)).containsExactly("Event/EventSource/Device/IPAddress");
        assertThat(proven.get("errors_resolved")).isNotIn(null, false, "", List.of());
        assertThat(proven.get("errors_before_too")).isIn(null, false, "", List.of());
        assertThat(proven.get("diff")).as("with the diff").isNotIn(null, "");
        assertThat(proven.get("manual_steps")).as("and the steps to apply it by hand").isNotIn(null, "", List.of());

        // ### 6. the report, saved and promoted beside the pipeline; nothing else changed
        final String build = "e2e-evaluate";
        final String name = FEED + "-Events";
        final String report = "# " + name + ": evaluation\n\n## Purpose and data\n\nCSV logons from " + FEED
                              + ".\n\n## Processing\n\nEvent Data (Text), its own Data Splitter and XSLT.\n\n"
                              + "## Errors and schema conformance\n\n6 records, " + total + " events: one record's "
                              + "event fails the schema (IPAddress 'n/a') and is not stored. The " + valid
                              + " stored events are valid.\n\n## Field mapping\n\n| From | To |\n| --- | --- |\n"
                              + "| user | EventSource/User/Id |\n\nNever read: agent.\n\n## Event types\n\n"
                              + "Authenticate/Logon: 5.\n\n## Suggestions\n\n1. Write IPAddress only for an "
                              + "address (proven: one record changes).\n";
        final Map<String, Object> saved = agreed("write_documentation", args("build", build, "pipeline_uuid",
                prod.pipeline(), "markdown", report, "evaluation", true));
        assertThat(saved).as("its own doc").containsEntry("name", name + " - Evaluation");
        // Beside the pipeline it evaluates, with no destination given.
        final Map<String, Object> promoted = agreed("promote_build", args("build", build));
        dump("promote_build evaluation", promoted.get("promoted"));
        final List<ExplorerNode> beside = documentsIn(FOLDER, "Documentation", name + " - Evaluation");
        assertThat(beside).as("the report is beside the pipeline, in its own doc").hasSize(1);
        assertThat(documentation(beside.getFirst().getUuid())).contains("Errors and schema conformance");
        assertThat(versions(prod)).as("the pipeline, its XSLT and its text converter are unchanged")
                .isEqualTo(versions);
    }

    private void fix(final Production prod) {
        final long events = prod.events().getFirst();
        // ### fix: the user reports event 3 of the Events stream: a locked account's logon shows as successful
        final Map<String, Object> located = json("locate_event", args("stream_id", events, "event_id", 3));
        dump("locate_event", located);
        final Map<String, Object> where = map(located, "location");
        assertThat(((Number) located.get("raw_stream")).longValue()).as("located: the raw stream")
                .isEqualTo(prod.raw().getFirst());
        assertThat(((Number) where.get("record")).intValue()).as("record 3 (carol)").isEqualTo(2);
        assertThat((String) located.get("stored_event")).contains("carol", "<Success>true</Success>");
        assertThat(located).as("the stored event is the one stepping gives now").containsEntry("same_as_stored",
                true);
        final String output = (String) map(map(json("step_pipeline", args("pipeline_uuid", prod.pipeline(),
                "stream_id", located.get("raw_stream"), "record", where.get("record"), "part", where.get("part"))),
                "elements"), "translationFilter").get("output");
        assertThat(output).as("reproduced: stepping the record gives Success true for a locked account")
                .contains("<Id>carol</Id>", "<Success>true</Success>");
        assertThat(translationOutput(prod.pipeline(), prod.raw().getFirst(), 0)).as("alice's ok logon is as expected")
                .contains("<Id>alice</Id>", "<Success>true</Success>");

        // ### a draft that changes more than it should is not ready; the right one is
        final String code = readXslt(prod.xslt()).getData();
        assertThat(code.split(Pattern.quote(BUG), -1)).hasSize(2);
        final String sloppy = code.replace(BUG, FIX).replace("<Description>User logon</Description>",
                "<Description>Logon</Description>");
        final Map<String, Object> loose = json("summarise_fix", args("pipeline_uuid", prod.pipeline(), "element",
                "translationFilter", "draft", sloppy, "stream_ids", prod.raw(), "expected_paths", List.of(SUCCESS)));
        assertThat(loose).as("not ready: %s", loose.get("problems")).containsEntry("ready", false);
        assertThat(jsonOf(loose.get("problems"))).contains("Event/EventDetail/Description");
        final String draft = code.replace(BUG, FIX);
        final Map<String, Object> proven = json("summarise_fix", args("pipeline_uuid", prod.pipeline(), "element",
                "translationFilter", "draft", draft, "stream_ids", prod.raw(), "expected_paths", List.of(SUCCESS),
                "issue", "a locked account's logon shows as successful"));
        assertThat(proven).as("ready: only Success changes, on the 2 locked records: %s", proven.get("problems"))
                .containsEntry("ready", true)
                .containsEntry("records_changed", 2);
        assertThat(paths(proven)).containsExactly(SUCCESS);
        assertThat(jsonOf(proven.get("errors_before_too"))).as("the unrelated schema error is reported as there "
                                                               + "before too, not in the fix's way")
                .contains("IPAddress");

        // ### applied in place: a working copy, compared, documented, promoted after approval
        final String build = "e2e-fix";
        final Map<String, Object> copy = agreed("copy_pipeline", args("build", build, "source_uuid",
                prod.pipeline(), "new_name", FEED + "-Events-WORKING", "working_copy", true));
        dump("copy_pipeline", copy);
        final String xsltCopy = list(copy, "copied_documents").stream()
                .filter(document -> "XSLT".equals(document.get("type")))
                .map(document -> (String) document.get("uuid"))
                .findFirst()
                .orElseThrow();
        agreed("save_xslt", args("uuid", xsltCopy, "code", draft));
        final Map<String, Object> diff = json("compare_outputs", args("pipeline_uuid", prod.pipeline(),
                "stream_ids", prod.raw(), "other_pipeline_uuid", copy.get("uuid")));
        assertThat(paths(diff)).as("the working copy differs from production only in Success")
                .containsExactly(SUCCESS);
        agreed("write_documentation", args("build", build, "pipeline_uuid", copy.get("uuid"), "markdown",
                "# " + FEED + "-Events\n\n## Purpose and data\n\nCSV logons.\n\n## Field mapping\n\n| From | To |\n"
                + "| --- | --- |\n| result | " + SUCCESS + " (ok only) |\n",
                "change", "Locked accounts' logons are no longer successes"));
        agreed("promote_build", args("build", build, "destinations", Map.of()));
        final String now = readXslt(prod.xslt()).getData();
        assertThat(now).as("the production XSLT now has the fix").contains(FIX).doesNotContain(BUG);
        final List<String> backups = asUser(admin, () -> xsltStore.listDocuments().stream()
                .map(DocRef::getName)
                .filter(name -> name.startsWith(FEED + "-Events backup"))
                .toList());
        assertThat(backups).as("the original was backed up first").isNotEmpty();
        final List<DocRef> docs = asUser(admin, () -> documentationStore.listDocuments().stream()
                .filter(doc -> (FEED + "-Events").equals(doc.getName()))
                .toList());
        assertThat(docs).as("the pipeline's own documentation: one doc, beside it").hasSize(1);
        assertThat(documentation(docs.getFirst().getUuid())).as("with the change in its change log")
                .contains("Locked accounts' logons are no longer successes", "## Version control");
    }

    // The translation as someone wrote it: locked logons counted as successes, the IP left out when empty.
    private static String productionXslt() {
        final String code = """
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
                          <Outcome><Success><xsl:value-of select="BUG" /></Success></Outcome>
                        </Authenticate>
                      </EventDetail>
                    </Event>
                  </xsl:template>
                </xsl:stylesheet>
                """.replace("VERSION", VERSION).replace("BUG", BUG);
        final String ip = "<IPAddress><xsl:value-of select=\"" + IP + "\" /></IPAddress>";
        assertThat(code.split(Pattern.quote(ip), -1)).hasSize(2);
        return code.replace(ip, "<xsl:if test=\"" + IP + " != ''\">" + ip + "</xsl:if>");
    }

    // A raw stream sent to the feed, as a datafeed POST makes one.
    private long datafeed(final String text) {
        return securityContext.asProcessingUserResult(() -> {
            try (final Target target = store.openTarget(MetaProperties.builder()
                    .feedName(FEED)
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

    private void productionFilter(final String pipeline) {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, FEED)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                .build();
        asUser(admin, () -> {
            final ProcessorFilter filter = processorFilterService.create(CreateProcessFilterRequest.builder()
                    .processorType(ProcessorType.PIPELINE)
                    .pipeline(new DocRef(PipelineDoc.TYPE, pipeline))
                    .queryData(QueryData.builder().dataSource(MetaFields.STREAM_STORE_DOC_REF).expression(expression)
                            .build())
                    .enabled(true)
                    .priority(10)
                    .maxProcessingTasks(2)
                    .build());
            processorService.setEnabled(filter.getProcessor().getId(), true);
        });
    }

    private Map<String, String> versions(final Production prod) {
        return asUser(admin, () -> Map.of(
                prod.pipeline(), pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE, prod.pipeline()))
                        .getVersion(),
                prod.xslt(), xsltStore.readDocument(new DocRef(XsltDoc.TYPE, prod.xslt())).getVersion(),
                prod.converter(), textConverterStore.readDocument(new DocRef(TextConverterDoc.TYPE,
                        prod.converter())).getVersion()));
    }

    private static List<String> paths(final Map<String, Object> result) {
        return list(result, "fields_changed").stream().map(field -> (String) field.get("path")).toList();
    }

    // The groups of an errors summary, under each stream.
    private static List<Map<String, Object>> groupsOf(final Map<String, Object> summary) {
        final List<Map<String, Object>> groups = new ArrayList<>(list(summary, "groups"));
        if (summary.get("streams") instanceof final Map<?, ?> streams) {
            streams.values().forEach(stream -> groups.addAll(list((Map<String, Object>) stream, "groups")));
        }
        return groups;
    }

    // A ## section of JSON in read_document's answer.
    private static Map<String, Object> section(final String text, final String heading) {
        final String start = "\n## " + heading + "\n";
        assertThat(text).as("a %s section", heading).contains(start);
        final String rest = text.substring(text.indexOf(start) + start.length());
        final int json = rest.indexOf('{');
        final int next = rest.indexOf("\n\n## ");
        return JsonUtil.readValue(rest.substring(json, next < 0
                ? rest.length()
                : next), Map.class);
    }


    // --------------------------------------------------------------------------------


    /// The production pipeline and its documents and streams.
    private record Production(String pipeline,
                              String xslt,
                              String converter,
                              List<Long> raw,
                              List<Long> events) {

    }
}
