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

import stroom.ai.impl.tools.sample.ToolJson;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.mcp.impl.McpTestClient;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A CEF forwarding template whose chain after the forwarder's own XSLT (the flattener) is two XSLTs the template
/// sets, through the REAL tools on a real Stroom (seen in external testing, 2026-11-26): the first reads the
/// flattener's `<records><record><data name=".." value=".."/>` in the Events namespace and writes each CEF line in a
/// wrapper element (in the Events namespace too); the second turns those into kafka-records:1 for the Kafka producer.
/// draft_cef_mapping, given the template, drafts only the flattener and checks its hand-off through both.
class TestE2eCefTwoSharedStages extends AbstractE2eTest {

    private static final String STAMP = "1";
    private static final String ROOT = "System/E2E CEF Two Shared " + STAMP;
    private static final String TOPIC = "cef-two-" + STAMP;
    private static final String EVENTS = """
            <?xml version="1.1" encoding="UTF-8"?>
            <Events xmlns="event-logging:3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" Version="3.5.2" \
            xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
            <Event><EventTime><TimeCreated>2026-09-27T23:48:10.000Z</TimeCreated></EventTime>\
            <EventSource><System><Name>Vault</Name><Organisation>Example</Organisation>\
            <Version>11.4</Version></System><Device><HostName>vault-web-02</HostName></Device>\
            <User><Id>priya.patel</Id></User></EventSource><EventDetail><TypeId>User-Login</TypeId>\
            <Description>User logged in</Description><Authenticate><Action>Logon</Action><User><Id>priya.patel</Id>\
            </User><Outcome><Success>true</Success></Outcome></Authenticate></EventDetail></Event>
            <Event><EventTime><TimeCreated>2026-09-27T23:56:15.000Z</TimeCreated></EventTime>\
            <EventSource><System><Name>Vault</Name><Organisation>Example</Organisation>\
            <Version>11.4</Version></System><Device><HostName>vault-web-01</HostName></Device>\
            <User><Id>riley.chen</Id></User></EventSource><EventDetail><TypeId>Secret-View</TypeId>\
            <Description>Secret viewed</Description><View><Resource><Type>Secret</Type><Name>Edge Admin</Name>\
            </Resource></View></EventDetail></Event>
            </Events>
            """;
    // The first shared stage: each record's pair named cef, in the Events namespace, to a line in a wrapper.
    private static final String TO_LINES = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xpath-default-namespace="event-logging:3" xmlns="event-logging:3" version="2.0">
              <xsl:template match="/records">
                <Lines>
                  <xsl:for-each select="record">
                    <Line><xsl:value-of select="data[@name='cef']/@value"/></Line>
                  </xsl:for-each>
                </Lines>
              </xsl:template>
            </xsl:stylesheet>
            """;
    // The second: each line to a Kafka record, the topic set here, once.
    private static final String TO_KAFKA = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" \
            xmlns="kafka-records:1" version="2.0">
              <xsl:template match="/Lines">
                <kafkaRecords xsi:schemaLocation="kafka-records:1 file://kafka-records-v1.1.xsd">
                  <xsl:for-each select="Line">
                    <kafkaRecord topic="%s">
                      <value><xsl:value-of select="."/></value>
                    </kafkaRecord>
                  </xsl:for-each>
                </kafkaRecords>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Inject
    private PipelineStore pipelineStore;

    @Test
    @SuppressWarnings("unchecked")
    void aTemplateOfTwoSharedXsltsAfterTheFlattenerIsCheckedThroughBoth() {
        final String build = "ceftwo-" + STAMP;
        final String feed = "CEF-TWO-EVENTS-" + STAMP;
        agreed("create_feed", args("build", build, "name", feed, "stream_type", "Events"));
        final long eventsId = number(client.json("upload_sample", args("feed", feed, "sample", EVENTS,
                "stream_type", "Events")).get("stream_id"));
        final String template = template(folder(ROOT + "/Templates"));

        // Drafted from the template's shape: only the flattener, checked through both shared stages.
        final String drafted = McpTestClient.text(client.call("draft_cef_mapping", args("stream_ids",
                List.of(eventsId), "custom_keys", false, "template", template)));
        assertThat(drafted).doesNotContain("Can't tell what the shared XSLT", "wrote no kafkaRecords");
        final Map<String, Object> draft = (Map<String, Object>) ToolJson.loads(drafted);
        assertThat(map(draft, "shape")).containsEntry("kind", "chained").containsEntry("flattener", "flattenFilter");
        assertThat(map(draft, "chain_check")).containsEntry("ran", true).containsEntry("problems", List.of());
        assertThat(draft).containsEntry("problems", List.of());
    }

    // A forwarding template: an open flattener slot, then two XSLTs it sets (to lines, then to Kafka records), a
    // kafka-records:1 schema filter and a Kafka producer.
    private String template(final ExplorerNode folder) {
        final DocRef kafka = create(KafkaConfigDoc.TYPE, "CEF-TWO-KAFKA-" + STAMP, folder).getDocRef();
        final DocRef lines = create(XsltDoc.TYPE, "E2E Shared CEF lines " + STAMP, folder).getDocRef();
        editXslt(lines.getUuid(), doc -> doc.copy().data(TO_LINES).build());
        final DocRef records = create(XsltDoc.TYPE, "E2E Shared Kafka records " + STAMP, folder).getDocRef();
        editXslt(records.getUuid(), doc -> doc.copy().data(TO_KAFKA.formatted(TOPIC)).build());
        final List<List<String>> chain = new ArrayList<>(List.of(List.of("xmlParser", "XMLParser"),
                List.of("splitFilter", "SplitFilter"), List.of("flattenFilter", "XSLTFilter"),
                List.of("linesFilter", "XSLTFilter"), List.of("kafkaOutputFilter", "XSLTFilter"),
                List.of("schemaFilter", "SchemaFilter"), List.of("standardKafkaProducer", "StandardKafkaProducer")));
        final PipelineDataBuilder data = new PipelineDataBuilder();
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
        data.addProperty(property("linesFilter", "xslt", lines));
        data.addProperty(property("kafkaOutputFilter", "xslt", records));
        final DocRef ref = create(PipelineDoc.TYPE, "E2E Kafka Forwarding two shared " + STAMP, folder).getDocRef();
        asUser(admin, () -> pipelineStore.writeDocument(pipelineStore.readDocument(ref).copy()
                .pipelineData(data.build()).build()));
        return ref.getUuid();
    }
}
