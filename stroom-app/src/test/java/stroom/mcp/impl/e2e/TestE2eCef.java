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
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.docref.DocRef;
import stroom.documentation.impl.DocumentationStore;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerNode;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.mcp.impl.McpTestClient;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.xslt.XsltStore;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_cef.py`: CEF for ArcSight, through Kafka, from Events, through the REAL tools on
/// a real Stroom. Events go to a build's feed; draft_cef_mapping drafts and saves the CEF XSLT (keys in ArcSight's
/// dictionary only); with no forwarding template, create_pipeline builds a pipeline of its own (XMLParser,
/// SplitFilter one Event a record, XSLTFilter, SchemaFilter on kafka-records:1, StandardKafkaProducer with a
/// KafkaConfig made here for the test); stepping it gives the CEF lines (nothing is sent: there is no Kafka);
/// draft_cef_mapping reviews them, and write_documentation documents them to the field. Then, with an environment
/// around it. All eight of the script's parts:
///
/// 1. Events in a build feed, and a KafkaConfig for the test.
/// 2. The user is asked about keys outside the dictionary; the draft keeps to it.
/// 3. Saved, and a pipeline of its own (no forwarding template here).
/// 4. Stepped (nothing sent), reviewed, documented.
/// 5. A CEF pipeline written by hand in a production folder (lines of text, an unescaped | in a header, a key
///    outside the dictionary): the review finds both, from the mapping its lines imply; a draft lists it among the
///    existing CEF pipelines and its XSLT among those writing CEF.
/// 6. An AGENTS doc in that folder decides: keys outside the dictionary allowed (so no question), the topic, two
///    mappings (one to a key outside the dictionary), and the pipeline template, its own; a header override too.
/// 7. Saved as lines of text, and a pipeline from the template the AGENTS doc names: stepped clean, reviewed as text.
/// 8. The Kafka XSLT changed (one override, with its reason): pending until promotion, which writes one version line
///    for both of its changes, and a version control row in each doc.
///
/// The script's `describe_document` (the XSLT's pending changes and kept mapping) is read here from the XSLT's
/// description, as Stroom AI's read_document shows it; its `templates._shape` from build_status's pipeline stage.
@SuppressWarnings("unchecked")
class TestE2eCef extends AbstractE2eTest {

    private static final String STAMP = "1";
    private static final String BUILD = "cef-" + STAMP;
    private static final String FEED = "CEF-EVENTS-" + STAMP;
    private static final String EVENTS = """
            <?xml version="1.1" encoding="UTF-8"?>
            <Events xmlns="event-logging:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" Version="3.5.2" \
            xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
            <Event><EventTime><TimeCreated>2026-09-27T23:48:10.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Environment>Prod</Environment>\
            <Organisation>Delinea</Organisation><Version>11.4</Version></System>\
            <Generator>SecretServer Audit</Generator>
              <Device><HostName>ss-web-02</HostName><IPAddress>10.1.2.3</IPAddress></Device><Client>\
            <IPAddress>192.0.2.45</IPAddress></Client><User><Id>priya.patel</Id></User></EventSource>
             <EventDetail><TypeId>User-Login</TypeId><Description>User logged in</Description><Authenticate>\
            <Action>Logon</Action><User><Id>priya.patel</Id></User><Outcome><Success>true</Success></Outcome>
              <Data Name="session_id" Value="6857"/><Data Name="ticket" Value="CHG-3000"/></Authenticate></EventDetail>\
            </Event>
            <Event><EventTime><TimeCreated>2026-09-27T23:56:15.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Environment>Prod</Environment>\
            <Organisation>Delinea</Organisation><Version>11.4</Version></System>\
            <Generator>SecretServer Audit</Generator>
              <Device><HostName>ss-web-01</HostName></Device><Client><IPAddress>10.20.4.18</IPAddress></Client><User>\
            <Id>riley.chen</Id></User></EventSource>
             <EventDetail><TypeId>Secret-View</TypeId><Description>Secret viewed: a=b|c</Description><View><Resource>\
            <Type>Secret</Type><Name>Firewall - Edge Admin</Name><Id>10005</Id></Resource>
              <Data Name="folder" Value="Applications\\Finance"/></View></EventDetail></Event>
            </Events>
            """;
    // Written by hand, as a CEF pipeline in production might be: the description's | left unescaped in the header,
    // and a key outside ArcSight's dictionary.
    private static final String HAND_XSLT = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xpath-default-namespace="event-logging:3"
                version="2.0">
              <xsl:output method="text"/>
              <xsl:template match="/Events"><xsl:apply-templates select="Event"/></xsl:template>
              <xsl:template match="Event">
                <xsl:value-of select="concat('CEF:0|Delinea|SecretServer|11.4|', EventDetail/TypeId, '|', \
            EventDetail/Description,
                                      '|3|suser=', EventSource/User/Id, ' dvchost=', EventSource/Device/HostName,
                                      ' myCustomField1=', EventSource/System/Environment)"/>
                <xsl:text>&#10;</xsl:text>
              </xsl:template>
            </xsl:stylesheet>
            """;
    private static final List<List<String>> TEXT_CHAIN = List.of(List.of("Source", "Source"),
            List.of("xmlParser", "XMLParser"), List.of("xsltFilter", "XSLTFilter"),
            List.of("textWriter", "TextWriter"), List.of("streamAppender", "StreamAppender"));
    private static final Pattern VERSION_ROW = Pattern.compile(
            "^\\|\\s*(\\d+)\\s*\\|([^|]*)\\|([^|]*)\\|(.*)\\|([^|]*)\\|\\s*$");

    @Inject
    private XsltStore xsltStore;
    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private DocumentationStore documentationStore;

    @Override
    boolean enablesDisabledFilters() {
        return true;
    }

    @Test
    void eventsAreSentAsCefThroughAPipelineOfItsOwnAndAnEnvironmentDecidesTheRest() {
        // 1. Events in a build feed, and a KafkaConfig for the test.
        final Map<String, Object> feed = agreed("create_feed", args("build", BUILD, "name", FEED,
                "stream_type", "Events"));
        final long eventsId = number(client.json("upload_sample", args("feed", FEED, "sample", EVENTS,
                "stream_type", "Events")).get("stream_id"));
        final ExplorerNode buildFolder = folder(folderOf((String) feed.get("uuid")));
        final DocRef kafka = create(KafkaConfigDoc.TYPE, "CEF-KAFKA-" + STAMP, buildFolder).getDocRef();
        assertThat(kafka.getUuid()).as("KafkaConfig %s", kafka.getName()).isNotBlank();

        // 2. The user is asked about keys outside the dictionary (no form here: the agent asks); the draft keeps
        // to it.
        final Map<String, Object> asked = plainClient.json("draft_cef_mapping", args("stream_ids",
                List.of(eventsId), "topic", "arcsight-cef"));
        assertThat(asked).as("asked first").containsEntry("status", "needs_guidance");
        assertThat((String) asked.get("question")).contains("outside");
        final Map<String, Object> drafted = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "topic", "arcsight-cef"));
        dump("drafted", drafted);
        assertThat(drafted).as("no problems").containsEntry("problems", List.of());
        assertThat(String.valueOf(drafted.get("example_line"))).as("example line")
                .startsWith("CEF:0|Delinea|SecretServer|11.4|User-Login|User logged in|3|")
                .contains("act=Logon", "cn1Label=session_id");
        assertThat(drafted).as("its sample lines review clean").doesNotContainKey("sample_check");
        assertThat(String.valueOf(drafted.get("documentation")))
                .as("the documentation tables name each key's ArcSight field and label")
                .contains("### Authenticate events", "deviceCustomNumber1");

        // 3. Saved, and a pipeline of its own (no forwarding template here).
        final Map<String, Object> saved = agreed("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "topic", "arcsight-cef", "build", BUILD, "name", "CEF-" + STAMP,
                "overrides", List.of(Map.of("path", "EventDetail/Authenticate/Data[@Name='ticket']/@Value",
                        "key", "deviceCustomString6", "label", "Change ticket"))));
        final Map<String, Object> xslt = map(saved, "saved");
        assertThat(xslt.get("uuid")).as("XSLT saved").isNotNull();
        assertThat(strings(saved, "applied")).as("override applied")
                .anyMatch(applied -> applied.contains("ticket") && applied.contains("cs6"));
        dump("forwarding templates", client.json("find_pipeline_templates", args("stage", "forwarding")));
        final Map<String, Object> pipeline = agreed("create_pipeline", args("build", BUILD,
                "name", "CEF-" + STAMP + "-Kafka", "standalone", "kafka", "set_properties", List.of(
                        docProperty("xsltFilter", "xslt", (String) xslt.get("uuid"), XsltDoc.TYPE),
                        docProperty("standardKafkaProducer", "kafkaConfig", kafka.getUuid(), KafkaConfigDoc.TYPE))));
        dump("kafka pipeline", pipeline);
        final String pipelineUuid = (String) pipeline.get("uuid");
        assertThat(String.valueOf(pipeline.get("chain"))).endsWith("standardKafkaProducer");
        assertThat(list(client.json("build_status", args("build", BUILD)), "pipelines"))
                .filteredOn(shown -> pipelineUuid.equals(shown.get("uuid")))
                .singleElement()
                .as("classed as forwarding")
                .satisfies(shown -> assertThat(shown).containsEntry("stage", "forwarding"));

        // 4. Stepped (nothing sent), reviewed, documented.
        final Map<String, Object> step = client.json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(eventsId)));
        assertThat(step).as("stepped: %s", step.get("groups")).containsEntry("verdict", "clean");
        final Map<String, Object> review = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "pipeline_uuid", pipelineUuid));
        dump("review", review);
        assertThat(map(review, "review")).containsEntry("lines", 2).containsEntry("problems", List.of());
        assertThat(String.valueOf(review.get("plan_from"))).startsWith("the plan kept with XSLT");
        assertThat(map(review, "plan")).containsEntry("topic", "arcsight-cef");
        final Map<String, Object> doc = agreed("write_documentation", args("build", BUILD,
                "pipeline_uuid", pipelineUuid,
                "markdown", "# CEF-" + STAMP + "-Kafka\n\n## Purpose and data\n\nSecretServer Events to ArcSight as "
                            + "CEF.\n",
                "change", "Created", "stream_ids", List.of(eventsId)));
        final DocRef kafkaDoc = new DocRef(DocumentationDoc.TYPE, (String) doc.get("uuid"));
        assertThat(documentation(kafkaDoc.getUuid())).as("documentation to the field")
                .contains("## Field mapping", "### View events", "Change ticket", "CHG-3000");

        environment(eventsId, xslt, pipelineUuid, kafkaDoc);
    }

    @Test
    void aMixedSampleKeepsEachSystemsVersionAndAPlanThatWontCompileIsNotSaved() {
        // Not in the Stroom MCP server's e2e_cef.py: what changed here since the port, through the real tools.
        final String build = "cef-mixed";
        final String feedName = "CEF-MIXED-EVENTS";
        final Map<String, Object> feed = agreed("create_feed", args("build", build, "name", feedName,
                "stream_type", "Events"));
        final long eventsId = number(client.json("upload_sample", args("feed", feedName, "sample", mixedEvents(),
                "stream_type", "Events")).get("stream_id"));
        final DocRef kafka = create(KafkaConfigDoc.TYPE, "CEF-MIXED-KAFKA", folder(folderOf((String) feed.get("uuid"))))
                .getDocRef();

        // Only one of the three Events has a version: it is still taken from the Event (a feed can mix systems).
        final Map<String, Object> drafted = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "topic", "arcsight-cef"));
        dump("mixed drafted", drafted);
        assertThat(map(drafted, "plan").get("version")).as("the version from each Event")
                .isEqualTo(Map.of("source", "EventSource/System/Version"));
        // The fields not sent, once, by why: not in the plan as well.
        assertThat(map(drafted, "plan")).doesNotContainKey("not_sent");
        final List<Map<String, Object>> notSent = list(drafted, "not_sent");
        assertThat(notSent).as("not sent, by why").isNotEmpty().allSatisfy(group -> assertThat(group)
                .containsKeys("why", "count", "paths"));
        assertThat(notSent.stream().mapToLong(group -> number(group.get("count"))).sum())
                .as("every field not sent, counted")
                .isEqualTo(notSent.stream()
                        .flatMap(group -> ((Map<String, List<String>>) group.get("paths")).values().stream())
                        .mapToLong(List::size)
                        .sum());

        // A record key with a namespace prefix is refused by the plan's checks; one that doesn't parse, by
        // compiling the XSLT the plan writes, with the real compiler. Neither is saved.
        final Map<String, Object> prefixed = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "topic", "arcsight-cef", "kafka_key", "d:EventSource/Device/HostName",
                "build", build, "name", "CEF-MIXED"));
        assertThat(prefixed.get("saved")).as("prefixed key: not saved").isNull();
        assertThat(String.valueOf(prefixed.get("problems"))).contains("no namespace prefix");
        final Map<String, Object> unparsed = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "topic", "arcsight-cef", "kafka_key", "EventSource/Device/HostName[",
                "build", build, "name", "CEF-MIXED"));
        dump("unparsed key", unparsed);
        assertThat(unparsed.get("saved")).as("unparsed key: not saved").isNull();
        assertThat(String.valueOf(unparsed.get("problems"))).contains("the XSLT doesn't compile");
        assertThat(String.valueOf(unparsed.get("hint"))).startsWith("Not saved: the XSLT the plan writes doesn't "
                                                                    + "compile");

        // Saved as it should be, then stepped: each Event's line has its own system's version, or none.
        final Map<String, Object> saved = agreed("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "topic", "arcsight-cef", "kafka_key", "EventSource/Device/HostName",
                "build", build, "name", "CEF-MIXED"));
        final Map<String, Object> xslt = map(saved, "saved");
        assertThat(xslt.get("uuid")).as("XSLT saved").isNotNull();
        final String pipelineUuid = (String) agreed("create_pipeline", args("build", build,
                "name", "CEF-MIXED-Kafka", "standalone", "kafka", "set_properties", List.of(
                        docProperty("xsltFilter", "xslt", (String) xslt.get("uuid"), XsltDoc.TYPE),
                        docProperty("standardKafkaProducer", "kafkaConfig", kafka.getUuid(), KafkaConfigDoc.TYPE))))
                .get("uuid");
        assertThat(stepOutput(pipelineUuid, eventsId, 0, "xsltFilter")).as("the Event with a version")
                .contains("CEF:0|Delinea|SecretServer|11.4|User-Login|");
        assertThat(stepOutput(pipelineUuid, eventsId, 1, "xsltFilter")).as("an Event without one")
                .contains("CEF:0|Delinea|SecretServer||Secret-View|");

        // The kept plan, its not-sent list long, is saved to the workspace rather than shown whole.
        final String read = McpTestClient.text(client.call("read_document", args("uuid", xslt.get("uuid"),
                "mapping", true)));
        final String path = "/documents/" + xslt.get("uuid") + "/mapping.json";
        assertThat(read).as("read_document's kept mapping")
                .contains("## Kept mapping\nIt is too large to show")
                .contains(path)
                .doesNotContain("\"not_sent\"");
        assertThat(McpTestClient.text(client.call("jq", args("path", path, "query", ".mapping.not_sent | length"))))
                .as("the saved plan, read back")
                .matches("\\d+");
    }

    // Three Events of three kinds, only the first with its system's version, each with more Data than CEF's
    // custom slots hold, so that many fields are not sent.
    private static String mixedEvents() {
        final StringBuilder sb = new StringBuilder("""
                <?xml version="1.1" encoding="UTF-8"?>
                <Events xmlns="event-logging:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
                """);
        final List<List<String>> kinds = List.of(
                List.of("User-Login", "Authenticate", "<Action>Logon</Action><User><Id>priya.patel</Id></User>"),
                List.of("Secret-View", "View", "<Resource><Type>Secret</Type><Name>Edge Admin</Name></Resource>"),
                List.of("Secret-Create", "Create", "<Resource><Type>Secret</Type><Name>DB Admin</Name></Resource>"));
        for (int i = 0; i < kinds.size(); i++) {
            final List<String> kind = kinds.get(i);
            sb.append("<Event><EventTime><TimeCreated>2026-09-27T23:4").append(i)
                    .append(":10.000Z</TimeCreated></EventTime><EventSource><System><Name>SecretServer</Name>")
                    .append("<Organisation>Delinea</Organisation>")
                    .append(i == 0
                            ? "<Version>11.4</Version>"
                            : "")
                    .append("</System><Device><HostName>ss-web-0").append(i).append("</HostName></Device>")
                    .append("<User><Id>priya.patel</Id></User></EventSource><EventDetail><TypeId>")
                    .append(kind.get(0)).append("</TypeId><").append(kind.get(1)).append('>').append(kind.get(2));
            for (int n = 0; n < 40; n++) {
                sb.append("<Data Name=\"").append(kind.get(1).toLowerCase(Locale.ROOT))
                        .append("_attribute_").append(n).append("\" Value=\"value ").append(n).append("\"/>");
            }
            sb.append("</").append(kind.get(1)).append("></EventDetail></Event>\n");
        }
        return sb.append("</Events>\n").toString();
    }

    private void environment(final long eventsId,
                             final Map<String, Object> kafkaXslt,
                             final String kafkaPipeline,
                             final DocRef kafkaDoc) {
        // 5. A CEF pipeline written by hand in production: reviewed, and found by the draft.
        final String folderPath = "System/E2E CEF Production/vault-" + STAMP;
        final ExplorerNode folder = folder(folderPath);
        final DocRef hand = create(XsltDoc.TYPE, "Vault CEF " + STAMP, folder).getDocRef();
        asUser(admin, () -> xsltStore.writeDocument(xsltStore.readDocument(hand).copy().data(HAND_XSLT).build()));
        final String templateName = "E2E CEF to file " + STAMP;
        final DocRef fixture = create(PipelineDoc.TYPE, templateName, folder).getDocRef();
        asUser(admin, () -> pipelineStore.writeDocument(pipelineStore.readDocument(fixture).copy()
                .pipelineData(JsonUtil.readValue(textChain(hand), PipelineData.class))
                .build()));
        final Map<String, Object> reviewed = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "pipeline_uuid", fixture.getUuid()));
        dump("hand review", reviewed);
        final String found = String.valueOf(map(reviewed, "review").get("problems"));
        assertThat(String.valueOf(reviewed.get("plan_from"))).startsWith("the mapping its lines imply");
        assertThat(found).as("the review finds the hand-written faults")
                .contains("unescaped |", "'myCustomField1' is not in ArcSight's CEF dictionary", "aren't allowed");
        // Stroom indexes document content in the background: a new XSLT can take a while to be found by its
        // content.
        final Map<String, Object> listed = eventually(() -> client.json("draft_cef_mapping", args(
                        "stream_ids", List.of(eventsId), "custom_keys", false, "folders", List.of(folderPath))),
                drafted -> names(map(drafted, "templates"), "xslts_writing_cef").contains(hand.getName()));
        assertThat(names(map(listed, "templates"), "existing_cef_pipelines")).as("found by the draft: pipelines")
                .contains(templateName);
        assertThat(names(map(listed, "templates"), "xslts_writing_cef")).as("and XSLTs writing CEF")
                .contains(hand.getName());

        // 6. An AGENTS doc decides: custom keys, topic, mappings, the template.
        final DocRef agents = create(DocumentationDoc.TYPE, "AGENTS", folder).getDocRef();
        asUser(admin, () -> documentationStore.writeDocument(documentationStore.readDocument(agents).copy()
                .data("# CEF output\n\n"
                      + "- EventDetail/View/Resource/Name -> cs4 (label: 'Secret name')\n"
                      + "- EventDetail/View/Data[@Name='folder']/@Value -> secretFolder (label: 'Folder')\n\n"
                      + "Custom CEF keys are allowed.\n"
                      + "Kafka topic: arcsight-vault-" + STAMP + "\n"
                      + "Pipeline template: " + templateName + "\n")
                .build()));
        final Map<String, Object> told = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "folders", List.of(folderPath), "output", "text", "header", Map.of("vendor", "Acme Vault")));
        dump("told", told);
        final Map<String, Object> said = map(told, "from_standing_instructions");
        assertThat(told.get("status")).isNotEqualTo("needs_guidance");
        assertThat(told).as("keys outside the dictionary allowed by the AGENTS doc: the user isn't asked")
                .containsEntry("custom_keys", true);
        assertThat(said).containsEntry("custom_keys", true).containsEntry("topic", "arcsight-vault-" + STAMP);
        assertThat((List<?>) said.get("mappings")).hasSize(2);
        assertThat(names(map(map(told, "templates"), "from_instructions"), "found")).as("the template it names")
                .containsExactly(templateName);
        final Map<String, Object> kinds = map(map(told, "plan"), "events");
        final String view = kinds.keySet().stream().filter(kind -> kind.startsWith("View")).findFirst()
                .orElseThrow();
        final Map<String, Object> keys = new LinkedHashMap<>();
        ((List<Map<String, Object>>) kinds.get(view)).forEach(field -> keys.put((String) field.get("path"),
                field.get("key")));
        assertThat(keys).as("the AGENTS mappings, one outside the dictionary")
                .containsEntry("EventDetail/View/Resource/Name", "cs4")
                .containsEntry("EventDetail/View/Data[@Name='folder']/@Value", "secretFolder");
        assertThat(String.valueOf(told.get("example_line"))).as("the header override")
                .startsWith("CEF:0|Acme Vault|SecretServer|");

        // 7. Saved as text; a pipeline from the template the AGENTS doc names.
        final Map<String, Object> savedText = agreed("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "folders", List.of(folderPath), "output", "text", "header", Map.of("vendor", "Acme Vault"),
                "build", BUILD, "name", "CEF-" + STAMP + "-Text"));
        assertThat(savedText.get("saved")).isNotNull();
        assertThat(String.valueOf(map(savedText, "plan").get("output"))).doesNotContain("kafkaRecord");
        final Map<String, Object> textPipeline = agreed("create_pipeline", args("build", BUILD,
                "name", "CEF-" + STAMP + "-Text", "template_uuid", fixture.getUuid(), "set_properties", List.of(
                        docProperty("xsltFilter", "xslt", (String) map(savedText, "saved").get("uuid"),
                                XsltDoc.TYPE))));
        final String textUuid = (String) textPipeline.get("uuid");
        assertThat(client.json("step_sample", args("pipeline_uuid", textUuid, "stream_ids", List.of(eventsId))))
                .as("the text pipeline steps clean")
                .containsEntry("verdict", "clean");
        final Map<String, Object> asText = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "folders", List.of(folderPath), "pipeline_uuid", textUuid));
        dump("text review", asText);
        assertThat(map(asText, "review")).containsEntry("lines", 2);
        assertThat((List<Object>) map(asText, "review").get("problems")).as("reviewed as text")
                .allSatisfy(problem -> assertThat(String.valueOf(problem)).contains("secretFolder"));
        final Map<String, Object> textDoc = agreed("write_documentation", args("build", BUILD,
                "pipeline_uuid", textUuid,
                "markdown", "# CEF-" + STAMP + "-Text\n\n## Purpose and data\n\nSecretServer Events as CEF lines, "
                            + "for the vault team.\n",
                "change", "Created", "stream_ids", List.of(eventsId)));
        assertThat(textDoc.get("uuid")).as("documented").isNotNull();

        // 8. The Kafka XSLT changed, and promoted: one version line for its changes.
        final DocRef kafkaXsltRef = new DocRef(XsltDoc.TYPE, (String) kafkaXslt.get("uuid"));
        final Map<String, Object> changed = agreed("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "uuid", kafkaXsltRef.getUuid(),
                "overrides", List.of(Map.of("path", "EventDetail/Authenticate/Data[@Name='session_id']/@Value",
                        "key", "cn2", "label", "Session")),
                "change", "Session id as cn2, asked for by the SOC"));
        assertThat(changed.get("saved")).as("changed: %s", changed.get("applied")).isNotNull();
        final String description = asUser(admin, () -> xsltStore.readDocument(kafkaXsltRef).getDescription());
        assertThat(KeptMapping.pendingChanges(KeptMapping.pendingOf(description).orElseThrow()))
                .as("pending")
                .containsExactly("Created from its CEF plan", "Session id as cn2, asked for by the SOC");
        assertThat(KeptMapping.readMapping(description).orElseThrow().kind()).isEqualTo("cef");
        assertThat(description.lines().map(String::strip).filter(line -> line.startsWith("| Unreleased |")))
                .as("previewed in the XSLT, unreleased")
                .singleElement()
                .satisfies(line -> assertThat(line).contains("Created from its CEF plan; Session id as cn2"));
        assertThat(client.json("step_sample", args("pipeline_uuid", kafkaPipeline,
                "stream_ids", List.of(eventsId))))
                .as("the Kafka pipeline steps clean again")
                .containsEntry("verdict", "clean");
        agreed("write_documentation", args("build", BUILD, "pipeline_uuid", kafkaPipeline,
                "markdown", "# CEF-" + STAMP + "-Kafka\n\n## Purpose and data\n\nSecretServer Events to ArcSight as "
                            + "CEF.\n",
                "change", "Session id as cn2", "stream_ids", List.of(eventsId)));
        final String text = documentation(kafkaDoc.getUuid());
        assertThat(text).as("the doc previews its unreleased row").contains("| Unreleased |");
        assertThat(rowsOf(text)).as("nothing released yet").isEmpty();
        final String where = folderPath + "/promoted-" + STAMP;
        folder(where);
        final Map<String, Object> destinations = new LinkedHashMap<>();
        for (final String type : List.of("Feed", "Pipeline", "XSLT", "Documentation", "KafkaConfig")) {
            destinations.put(type, where);
        }
        final Map<String, Object> result = agreed("promote_build", args("build", BUILD,
                "destinations", destinations));
        dump("promoted", result);
        assertThat(strings(result, "promoted").size()).as("promoted documents").isGreaterThanOrEqualTo(7);
        final XsltDoc promoted = asUser(admin, () -> xsltStore.readDocument(kafkaXsltRef));
        final List<XsltVersionHistory.Row> history = XsltVersionHistory.history(promoted.getDescription());
        assertThat(promoted.getData()).as("the history kept out of the code")
                .doesNotContain("stroom-mcp version history");
        assertThat(history).as("the XSLT's version history").singleElement().satisfies(row -> {
            assertThat(row.change()).contains("Session id as cn2", "Created");
            assertThat(row.how()).isEqualTo("agent");
        });
        assertThat(rowsOf(documentation(kafkaDoc.getUuid()))).as("the doc's version control")
                .singleElement()
                .satisfies(row -> assertThat(row).contains("Session id as cn2"));
        assertThat(promoted.getDescription()).as("promoted: the previews replaced by the released line")
                .doesNotContain("| Unreleased |");
        assertThat(documentation(kafkaDoc.getUuid())).as("and row").doesNotContain("| Unreleased |");
    }

    // A text pipeline of its own, its XSLTFilter set to the hand-written XSLT.
    private static String textChain(final DocRef hand) {
        final List<Map<String, Object>> elements = new ArrayList<>();
        final List<Map<String, Object>> links = new ArrayList<>();
        for (int i = 0; i < TEXT_CHAIN.size(); i++) {
            elements.add(Map.of("id", TEXT_CHAIN.get(i).get(0), "type", TEXT_CHAIN.get(i).get(1)));
            if (i > 0) {
                links.add(Map.of("from", TEXT_CHAIN.get(i - 1).get(0), "to", TEXT_CHAIN.get(i).get(0)));
            }
        }
        return JsonUtil.writeValueAsString(Map.of(
                "elements", Map.of("add", elements),
                "links", Map.of("add", links),
                "properties", Map.of("add", List.of(Map.of("element", "xsltFilter", "name", "xslt",
                        "value", Map.of("entity", Map.of("type", XsltDoc.TYPE, "uuid", hand.getUuid(),
                                "name", hand.getName())))))), false);
    }

    private static List<String> names(final Map<String, Object> result, final String key) {
        final Object listed = result.get(key);
        return listed instanceof final List<?> found
                ? found.stream().map(item -> String.valueOf(((Map<String, Object>) item).get("name"))).toList()
                : List.of();
    }

    // The doc's version control rows, released ones only (the Stroom MCP server's versionlog.rows_of).
    private static List<String> rowsOf(final String markdown) {
        final String heading = "## Version control";
        if (!markdown.contains(heading)) {
            return List.of();
        }
        final String below = markdown.substring(markdown.indexOf(heading) + heading.length())
                .replaceAll("(?s)<!-- stroom-mcp pending changes .*? -->", "\n");
        return below.lines()
                .map(String::strip)
                .filter(line -> {
                    final Matcher matcher = VERSION_ROW.matcher(line);
                    return matcher.matches();
                })
                .toList();
    }

    // A result asked for again until it holds what is waited for, for up to a minute.
    private static Map<String, Object> eventually(final Supplier<Map<String, Object>> ask,
                                                  final Predicate<Map<String, Object>> done) {
        Map<String, Object> result = ask.get();
        for (int i = 0; i < 12 && !done.test(result); i++) {
            try {
                Thread.sleep(5_000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            result = ask.get();
        }
        return result;
    }
}
