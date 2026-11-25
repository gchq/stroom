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

import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// CEF forwarders of two shapes through the REAL tools on a real Stroom (decided with the user, 2026-11-25). A
/// forwarding template of two stages: an open flattener slot, then a shared XSLT (its topic set once, in it) turning
/// `<data name="cef" value="CEF:..."/>` pairs into kafka-records:1 for the Kafka producer; and one of one stage, its
/// open XSLT writing the Kafka records itself. In three parts:
///
/// 1. Two stages: draft_cef_mapping names the template and drafts only the flattener (the form the shared XSLT reads,
///    read from it; no topic), checked through the shared XSLT; saved; a child of the template sets only the
///    flattener; stepped clean through the shared stage's schema filter to kafka-records:1 carrying each line;
///    reviewed through both stages (lines at the shared stage, Events at the flattener's input); the shared XSLT
///    untouched.
/// 2. A hand-written forwarder of two stages in production: reviewed through both stages (lines and Events matched);
///    copied, keeping its template; re-parented onto a pre-production template and copied again, flagged beside its
///    siblings; a next version from production's template flagged against its predecessor; a child overriding the
///    shared XSLT flagged as not following its template.
/// 3. One stage: as before, the topic in its own XSLT, stepped and reviewed.
class TestE2eCefChained extends AbstractE2eTest {

    private static final String STAMP = "1";
    private static final String ROOT = "System/E2E CEF Chained " + STAMP;
    private static final String TOPIC = "arcsight-chain-" + STAMP;
    private static final String EVENTS = """
            <?xml version="1.1" encoding="UTF-8"?>
            <Events xmlns="event-logging:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" Version="3.5.2" \
            xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
            %s
            </Events>
            """;
    private static final List<String> EVENT_ELEMENTS = List.of("""
            <Event><EventTime><TimeCreated>2026-09-27T23:48:10.000Z</TimeCreated></EventTime>\
            <EventSource><System><Name>SecretServer</Name><Organisation>Delinea</Organisation>\
            <Version>11.4</Version></System><Device><HostName>ss-web-02</HostName></Device>\
            <User><Id>priya.patel</Id></User></EventSource><EventDetail><TypeId>User-Login</TypeId>\
            <Description>User logged in</Description><Authenticate><Action>Logon</Action><User><Id>priya.patel</Id>\
            </User><Outcome><Success>true</Success></Outcome></Authenticate></EventDetail></Event>""", """
            <Event><EventTime><TimeCreated>2026-09-27T23:56:15.000Z</TimeCreated></EventTime>\
            <EventSource><System><Name>SecretServer</Name><Organisation>Delinea</Organisation>\
            <Version>11.4</Version></System><Device><HostName>ss-web-01</HostName></Device>\
            <User><Id>riley.chen</Id></User></EventSource><EventDetail><TypeId>Secret-View</TypeId>\
            <Description>Secret viewed</Description><View><Resource><Type>Secret</Type><Name>Edge Admin</Name>\
            </Resource></View></EventDetail></Event>""");
    // The template's shared stage: each record's pair named cef to a Kafka record, the topic set here, once.
    private static final String SHARED = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="kafka-records:1" version="2.0">
              <xsl:template match="/records">
                <kafkaRecords xsi:schemaLocation="kafka-records:1 file://kafka-records-v1.1.xsd">
                  <xsl:for-each select="record">
                    <kafkaRecord topic="%s">
                      <value><xsl:value-of select="data[@name='cef']/@value"/></value>
                    </kafkaRecord>
                  </xsl:for-each>
                </kafkaRecords>
              </xsl:template>
            </xsl:stylesheet>
            """;
    // A flattener written by hand, as production might hold one: a CEF line an Event, in a pair named cef.
    private static final String HAND_FLATTENER = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xpath-default-namespace="event-logging:3" version="2.0">
              <xsl:template match="/Events">
                <records>
                  <xsl:for-each select="Event">
                    <record><data name="cef" value="{concat('CEF:0|Delinea|SecretServer|11.4|', \
            EventDetail/TypeId, '|', EventDetail/Description, '|3|suser=', EventSource/User/Id, ' dvchost=', \
            EventSource/Device/HostName)}"/></record>
                  </xsl:for-each>
                </records>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Inject
    private PipelineStore pipelineStore;

    @Test
    @SuppressWarnings("unchecked")
    void aForwarderOfTwoStagesGetsOnlyItsFlattenerAndStepsThroughTheSharedStage() {
        final String build = "cefchain-" + STAMP;
        final String feed = "CEF-CHAIN-EVENTS-" + STAMP;
        agreed("create_feed", args("build", build, "name", feed,
                "stream_type", "Events"));
        final long eventsId = number(client.json("upload_sample", args("feed", feed, "sample", events(),
                "stream_type", "Events")).get("stream_id"));
        final Templates templates = templates(folder(ROOT + "/Templates A"));

        // Drafted from the template's shape: only the flattener, in the form its shared XSLT reads, no topic.
        final Map<String, Object> drafted = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "template", templates.twoStages(), "topic", "not-this-one"));
        dump("chained draft", drafted);
        final Map<String, Object> shape = map(drafted, "shape");
        assertThat(shape).containsEntry("kind", "chained").containsEntry("flattener", "flattenFilter")
                .containsEntry("handoff_checked", true);
        assertThat(String.valueOf(shape.get("handoff"))).contains("<records><record><data name=\"cef\" "
                                                                 + "value=\"CEF line\"/></record></records>");
        assertThat(map(drafted, "plan")).containsEntry("output", "chained").doesNotContainKey("topic");
        assertThat(strings(drafted, "notes")).anySatisfy(note -> assertThat(note)
                .startsWith("topic 'not-this-one' not used"));
        assertThat(map(drafted, "chain_check")).containsEntry("ran", true).containsEntry("problems", List.of());
        assertThat(drafted).containsEntry("problems", List.of());

        // Saved: the flattener's XSLT, its plan kept, writing no Kafka record or topic.
        final Map<String, Object> saved = agreed("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "template", templates.twoStages(), "build", build, "name",
                "CEF-CHAIN-" + STAMP));
        final String xslt = (String) map(saved, "saved").get("uuid");
        final XsltDoc flattener = readXslt(xslt);
        assertThat(flattener.getData()).contains("<data name=\"cef\" value=\"{$line}\"")
                .doesNotContain("kafkaRecord", "topic=");
        assertThat(KeptMapping.readMapping(flattener.getDescription()).orElseThrow().payload())
                .containsEntry("output", "chained");
        assertThat(String.valueOf(saved.get("hint"))).contains("setting only flattenFilter.xslt");

        // A child of the template setting only the flattener: stepped through the shared stage to Kafka records.
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build, "name",
                "CEF-CHAIN-" + STAMP + "-Kafka", "template_uuid", templates.twoStages(), "set_properties",
                List.of(docProperty("flattenFilter", "xslt", xslt, XsltDoc.TYPE)))));
        final PipelineDoc child = readPipeline(pipeline);
        assertThat(child.getParentPipeline().getUuid()).as("a child of the template").isEqualTo(templates.twoStages());
        assertThat(child.getPipelineData().getAddedProperties()).as("only the flattener set")
                .extracting(PipelineProperty::getElement, PipelineProperty::getName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("flattenFilter", "xslt"));
        final Map<String, Object> step = client.json("step_sample", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(eventsId)));
        assertThat(step).as("stepped through the shared stage's schema filter: %s", step.get("groups"))
                .containsEntry("verdict", "clean");
        assertThat(sharedStageOutput(pipeline, eventsId)).as("kafka-records:1 carrying the line")
                .contains("kafkaRecord topic=\"" + TOPIC + "\"",
                        "<value>CEF:0|Delinea|SecretServer|11.4|User-Login|User logged in|3|");

        // Reviewed through both stages: lines at the shared stage, Events at the flattener's input.
        final Map<String, Object> review = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "pipeline_uuid", pipeline));
        dump("chained review", review);
        assertThat(map(review, "review")).containsEntry("lines", 2).containsEntry("problems", List.of());
        assertThat(String.valueOf(map(review, "review").get("implied_mapping")))
                .as("each key matched to the Event value it carries").contains("EventSource/User/Id");
        assertThat(map(review, "shape")).containsEntry("lines_read_at", "kafkaOutputFilter");
        assertThat(String.valueOf(review.get("plan_from"))).startsWith("the plan kept with XSLT");
        assertThat(map(review, "inheritance").get("inherits")).as("its template stated")
                .isEqualTo(Map.of("type", PipelineDoc.TYPE, "uuid", templates.twoStages(), "name",
                        templates.twoStagesName()));
        assertThat(readXslt(templates.shared()).getData()).as("the shared XSLT never changed")
                .isEqualTo(SHARED.formatted(TOPIC));

        // One stage, as before: its own XSLT writes the Kafka records, the topic in it.
        final Map<String, Object> single = agreed("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "template", templates.oneStage(), "topic", "arcsight-cef", "build", build,
                "name", "CEF-ONE-" + STAMP));
        assertThat(map(single, "shape")).containsEntry("kind", "single");
        assertThat(map(single, "plan")).containsEntry("topic", "arcsight-cef");
        final String singlePipeline = uuidOf(agreed("create_pipeline", args("build", build, "name",
                "CEF-ONE-" + STAMP + "-Kafka", "template_uuid", templates.oneStage(), "set_properties",
                List.of(docProperty("xsltFilter", "xslt", (String) map(single, "saved").get("uuid"),
                        XsltDoc.TYPE)))));
        assertThat(client.json("step_sample", args("pipeline_uuid", singlePipeline, "stream_ids",
                List.of(eventsId)))).containsEntry("verdict", "clean");
        final Map<String, Object> singleReview = client.json("draft_cef_mapping", args("stream_ids",
                List.of(eventsId), "custom_keys", false, "pipeline_uuid", singlePipeline));
        assertThat(map(singleReview, "review")).containsEntry("lines", 2);
        assertThat(singleReview).as("one stage").doesNotContainKey("shape");
        assertThat(map(singleReview, "plan")).containsEntry("topic", "arcsight-cef");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aHandWrittenForwarderIsReviewedThroughBothStagesAndItsTemplateKeptAndFlagged() {
        final ExplorerNode production = folder(ROOT + "/Production");
        final Templates templates = templates(folder(ROOT + "/Templates B"));
        final String feed = "CEF-HAND-EVENTS-" + STAMP;
        create(FeedDoc.TYPE, feed, production);
        final long eventsId = storeEvents(feed, EVENT_ELEMENTS);
        final DocRef hand = create(XsltDoc.TYPE, "Vault CEF flattener", production).getDocRef();
        editXslt(hand.getUuid(), doc -> doc.copy().data(HAND_FLATTENER).build());
        final String vault = childPipeline(production, "Vault CEF", templates.twoStages(),
                List.of(property("flattenFilter", "xslt", hand)));
        childPipeline(production, "Accounts CEF", templates.twoStages(), List.of(property("flattenFilter", "xslt",
                hand)));

        // Reviewed through both stages: its lines and the Events they came from matched.
        final Map<String, Object> review = client.json("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "pipeline_uuid", vault));
        dump("hand review", review);
        assertThat(map(review, "review")).containsEntry("lines", 2);
        assertThat(String.valueOf(map(review, "review").get("implied_mapping")))
                .contains("path=EventSource/User/Id, key=suser", "path=EventSource/Device/HostName, key=dvchost");
        assertThat(String.valueOf(review.get("plan_from"))).startsWith("the mapping its lines imply");
        assertThat(map(review, "plan")).as("a plan from it hands its lines on").containsEntry("output", "chained")
                .doesNotContainKey("topic");
        assertThat(map(review, "inheritance")).doesNotContainKey("flags");

        // Copied, keeping its template: nothing inherited made the copy's own.
        final Map<String, Object> copied = agreed("copy_pipeline", args("build", "cefhand-" + STAMP, "source_uuid",
                vault, "new_name", "Vault CEF-V1.1"));
        dump("copy", copied);
        assertThat(map(copied, "inheritance").get("inherits")).isEqualTo(Map.of("type", PipelineDoc.TYPE, "uuid",
                templates.twoStages(), "name", templates.twoStagesName()));
        assertThat(map(copied, "inheritance")).doesNotContainKey("flags");
        final PipelineDoc copy = readPipeline(uuidOf(copied));
        assertThat(copy.getParentPipeline().getUuid()).isEqualTo(templates.twoStages());
        assertThat(copy.getPipelineData().getAddedProperties()).extracting(PipelineProperty::getElement)
                .as("the shared stage still the template's").doesNotContain("kafkaOutputFilter");

        // Re-parented onto pre-production, then copied: flagged beside its siblings, as a fact.
        asUser(admin, () -> pipelineStore.writeDocument(pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE,
                vault)).copy().parentPipeline(new DocRef(PipelineDoc.TYPE, templates.preProd())).build()));
        final Map<String, Object> reparented = agreed("copy_pipeline", args("build", "cefpre-" + STAMP,
                "source_uuid", vault, "new_name", "Vault CEF-V1.1"));
        dump("re-parented copy", reparented);
        assertThat(readPipeline(uuidOf(reparented)).getParentPipeline().getUuid()).as("its template kept")
                .isEqualTo(templates.preProd());
        assertThat((List<String>) map(reparented, "inheritance").get("flags")).anySatisfy(flag -> assertThat(flag)
                .contains("inherits '" + templates.preProdName() + "'; the other forwarding pipelines in folder",
                        "inherit '" + templates.twoStagesName() + "' ('Accounts CEF')"));

        // A next version from production's template: flagged against its predecessor. Its shape decided by the
        // predecessor the call names.
        final String next = "cefnext-" + STAMP;
        agreed("create_feed", args("build", next, "name", "CEF-NEXT-EVENTS-" + STAMP, "stream_type", "Events"));
        final Map<String, Object> drafted = agreed("draft_cef_mapping", args("stream_ids", List.of(eventsId),
                "custom_keys", false, "template", vault, "build", next, "name", "Vault CEF-V1.2"));
        assertThat(map(drafted, "shape")).containsEntry("kind", "chained");
        final Map<String, Object> version = agreed("create_pipeline", args("build", next, "name", "Vault CEF-V1.2",
                "template_uuid", templates.twoStages(), "version_of", vault, "set_properties", List.of(
                        docProperty("flattenFilter", "xslt", (String) map(drafted, "saved").get("uuid"),
                                XsltDoc.TYPE))));
        dump("next version", version);
        assertThat((List<String>) map(version, "inheritance").get("flags")).anySatisfy(flag -> assertThat(flag)
                .contains("inherits '" + templates.twoStagesName() + "'; its predecessor 'Vault CEF' inherits '"
                          + templates.preProdName() + "'"));

        // A child setting its own Kafka XSLT where its template supplies one: it won't follow the template.
        final DocRef own = create(XsltDoc.TYPE, "My Kafka output", production).getDocRef();
        editXslt(own.getUuid(), doc -> doc.copy().data(SHARED.formatted("my-own-topic")).build());
        final String overriding = childPipeline(production, "Override CEF", templates.twoStages(), List.of(
                property("flattenFilter", "xslt", hand), property("kafkaOutputFilter", "xslt", own)));
        final Map<String, Object> overridden = client.json("draft_cef_mapping", args("stream_ids",
                List.of(eventsId), "custom_keys", false, "pipeline_uuid", overriding));
        assertThat(map(overridden, "review")).containsEntry("lines", 2);
        assertThat((List<String>) map(overridden, "inheritance").get("flags")).anySatisfy(flag -> assertThat(flag)
                .contains("sets its own XSLT 'My Kafka output' on 'kafkaOutputFilter', where its template '"
                          + templates.twoStagesName() + "' supplies '" + templates.sharedName() + "'",
                        "won't follow the template"));
    }

    // Two forwarding templates of two stages (production and pre-production, each its shared XSLT and topic) and one
    // of one stage, in the folder given, with a KafkaConfig.
    private Templates templates(final ExplorerNode folder) {
        final DocRef kafka = create(KafkaConfigDoc.TYPE, "CEF-CHAIN-KAFKA-" + STAMP, folder).getDocRef();
        final DocRef shared = create(XsltDoc.TYPE, "E2E Shared Kafka output " + STAMP, folder).getDocRef();
        editXslt(shared.getUuid(), doc -> doc.copy().data(SHARED.formatted(TOPIC)).build());
        final DocRef preShared = create(XsltDoc.TYPE, "E2E Shared Kafka output (pre-prod) " + STAMP, folder)
                .getDocRef();
        editXslt(preShared.getUuid(), doc -> doc.copy().data(SHARED.formatted(TOPIC + "-pre")).build());
        final String twoStages = "E2E Kafka Forwarding " + STAMP;
        final String preProd = "E2E Kafka Forwarding (pre-prod) " + STAMP;
        return new Templates(template(folder, twoStages, kafka, shared), twoStages,
                template(folder, preProd, kafka, preShared), preProd,
                template(folder, "E2E CEF one stage " + STAMP, kafka, null), shared.getUuid(), shared.getName());
    }

    // A forwarding template: one Event a record, an open XSLT slot (the flattener), the shared XSLT if given, a schema
    // filter on kafka-records:1 and the Kafka producer.
    private String template(final ExplorerNode folder, final String name, final DocRef kafka, final DocRef shared) {
        final PipelineDataBuilder data = new PipelineDataBuilder();
        final List<List<String>> chain = shared == null
                ? List.of(List.of("xmlParser", "XMLParser"), List.of("splitFilter", "SplitFilter"),
                List.of("xsltFilter", "XSLTFilter"), List.of("schemaFilter", "SchemaFilter"),
                List.of("standardKafkaProducer", "StandardKafkaProducer"))
                : List.of(List.of("xmlParser", "XMLParser"), List.of("splitFilter", "SplitFilter"),
                        List.of("flattenFilter", "XSLTFilter"), List.of("kafkaOutputFilter", "XSLTFilter"),
                        List.of("schemaFilter", "SchemaFilter"),
                        List.of("standardKafkaProducer", "StandardKafkaProducer"));
        data.addElement(new PipelineElement("Source", "Source"));
        String previous = "Source";
        for (final List<String> element : chain) {
            data.addElement(new PipelineElement(element.get(0), element.get(1)));
            data.addLink(previous, element.get(0));
            previous = element.get(0);
        }
        data.addProperty(new PipelineProperty("splitFilter", "splitCount", new PipelinePropertyValue(1)));
        data.addProperty(new PipelineProperty("schemaFilter", "namespaceURI",
                new PipelinePropertyValue("kafka-records:1")));
        data.addProperty(property("standardKafkaProducer", "kafkaConfig", kafka));
        if (shared != null) {
            data.addProperty(property("kafkaOutputFilter", "xslt", shared));
        }
        final DocRef ref = create(PipelineDoc.TYPE, name, folder).getDocRef();
        asUser(admin, () -> pipelineStore.writeDocument(pipelineStore.readDocument(ref).copy()
                .pipelineData(data.build()).build()));
        return ref.getUuid();
    }

    // The first record's output of the shared stage, as step_pipeline gives it.
    private String sharedStageOutput(final String pipeline, final long stream) {
        final Map<String, Object> step = json("step_pipeline", args("pipeline_uuid", pipeline, "stream_id", stream,
                "record", 0, "show", List.of("kafkaOutputFilter")));
        return String.valueOf(map(map(step, "elements"), "kafkaOutputFilter").get("output"));
    }

    private PipelineDoc readPipeline(final String uuid) {
        return asUser(admin, () -> pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE, uuid)));
    }

    private static String events() {
        return EVENTS.formatted(String.join("\n", EVENT_ELEMENTS));
    }


    // --------------------------------------------------------------------------------


    // The templates made for a part: each's UUID (and the two-stage ones' names), and the production shared XSLT.
    private record Templates(String twoStages,
                             String twoStagesName,
                             String preProd,
                             String preProdName,
                             String oneStage,
                             String shared,
                             String sharedName) {

    }
}
