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

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_shared_xslt.py`: shared XSLTs (xsl:import), as an environment keeps its common
/// steps, driving the real tools as the agent would. Covered: its parts 1 (describe_template finds the sibling's
/// imports, reads each shared XSLT by name and reports each named template called), 2 (a translation mapping
/// using the shared templates: refused while it still maps Device; without it, imported and called, stepped clean,
/// valid, one Device and one Meta), 2b (its mapping lost: rebuild_mapping reads it back from the XSLT and the
/// shared XSLT it imports) and 2c (a shared XSLT of functions, called from an xpath entry, and read back when the
/// mapping is lost).
///
/// Part 3 (the index plan taking stroom.feed from a shared indexing XSLT, and an Elasticsearch indexing pipeline
/// stepped) is left out: it needs an Elasticsearch cluster doc and indexes into Elasticsearch.
@SuppressWarnings("unchecked")
class TestE2eSharedXslt extends AbstractE2eTest {

    private static final String TEXT_TEMPLATE = "E2E Shared Text";
    private static final String COMMON_EVENT = E2eFixtures.COMMON_EVENT;
    private static final String COMMON_FUNCTIONS = "E2E-Common-Functions-V1";
    private static final String SHARED_FUNCTIONS = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
            xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:ef="urn:e2e:functions" version="3.0">
              <!-- A value upper-cased, as an environment's shared functions might normalise one. -->
              <xsl:function name="ef:shout" as="xs:string">
                <xsl:param name="text" />
                <xsl:sequence select="upper-case(string($text[1]))" />
              </xsl:function>
            </xsl:stylesheet>
            """;
    // The sibling: written by hand, as an existing environment's would be. Only read, never run.
    private static final String SIBLING_EVENT = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xpath-default-namespace="records:2" xmlns="event-logging:3" xmlns:stroom="stroom"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
              <xsl:import href="E2E-Common-Event-V1" />
              <xsl:template match="records"><Events><xsl:apply-templates /></Events></xsl:template>
              <xsl:template match="record">
                <Event>
                  <xsl:call-template name="eventMeta" />
                  <EventTime><TimeCreated><xsl:value-of select="data[@name='when']/@value" /></TimeCreated>\
            </EventTime>
                  <EventSource>
                    <System><Name>Other</Name><Environment>Dev</Environment></System>
                    <Generator>other</Generator>
                    <xsl:call-template name="eventSourceDevice">
                      <xsl:with-param name="ip" select="data[@name='src']/@value" />
                    </xsl:call-template>
                  </EventSource>
                  <EventDetail><TypeId>Other</TypeId><Unknown /></EventDetail>
                </Event>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @Override
    boolean followsNext() {
        return true;
    }

    @Test
    void sharedXslts() {
        // ### shared XSLTs, and sibling pipelines that use them
        sharedXslt(COMMON_EVENT, E2eFixtures.SHARED_EVENT);
        sharedXslt(COMMON_FUNCTIONS, SHARED_FUNCTIONS);
        final String textTemplate = fixtureTemplate(TEXT_TEMPLATE, "Event Data (Text)");
        final String src = "e2e-shared-src";
        final String sibling = saveXslt(src, "E2E-OTHER-Events", SIBLING_EVENT);
        final String siblingConverter = saveTextConverter(src, "E2E-OTHER", CSV_SPLITTER);
        createPipeline(src, "E2E-OTHER-Events", textTemplate,
                docProperty("dsParser", "textConverter", siblingConverter, "TextConverter"),
                docProperty("translationFilter", "xslt", sibling, "XSLT"));

        // ### 1. describe_template finds what the siblings import, and reads it by name
        final Map<String, Object> described = json("describe_template", args("template_uuid", textTemplate));
        dump("shared_xslt", described.get("shared_xslt"));
        final Map<String, Map<String, Object>> shared = list(described, "shared_xslt").stream()
                .filter(use -> use.get("template") != null)
                .collect(Collectors.toMap(use -> (String) use.get("template"), Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
        final Map<String, Object> device = shared.getOrDefault("eventSourceDevice", Map.of());
        final Map<String, Object> meta = shared.getOrDefault("eventMeta", Map.of());
        assertThat(device).as("the calls and where").containsEntry("at", List.of("EventSource/Device"))
                .containsEntry("href", COMMON_EVENT);
        assertThat(meta).as("the calls and where").containsEntry("at", List.of("Meta"));
        assertThat(device).as("what each writes and reads, and the parameter the sibling passes")
                .containsEntry("paths", List.of("Device/HostName", "Device/IPAddress"))
                .containsEntry("reads_meta", List.of("MyHostName"))
                .containsEntry("with_params", Map.of("ip", "data[@name='src']/@value"));
        assertThat(meta).containsEntry("reads_meta", List.of("GUID"));
        assertThat(list(described, "shared_xslt")).as("the shared document itself, by name and uuid")
                .anyMatch(use -> use.get("document_contents") instanceof final Map<?, ?> contents
                                 && COMMON_EVENT.equals(contents.get("name")));

        // ### 2. the translation: the shared templates called, Device not written twice
        final List<Map<String, Object>> uses = List.of(
                Map.of("href", COMMON_EVENT, "template", "eventMeta", "at", "Meta"),
                Map.of("href", COMMON_EVENT, "template", "eventSourceDevice", "at", "EventSource/Device",
                        "with_params", Map.of("ip", "data[@name='ip']/@value")));
        final Map<String, Object> both = E2eFixtures.csvMapping();
        both.put("shared", uses);
        final Map<String, Object> refused = json("build_translation_xslt", args("mapping", both,
                "schema_version", VERSION));
        assertThat(refused.get("ok")).as("mapping Device as well is refused").isEqualTo(false);
        assertThat(strings(refused, "problems")).as("Device would be written twice")
                .anyMatch(problem -> problem.contains(
                        "EventSource/Device is written by the shared template eventSourceDevice"));
        final Map<String, Object> mapping = E2eFixtures.csvMapping();
        mapping.put("shared", uses);
        mapping.put("common", E2eFixtures.entries(mapping).stream()
                .filter(field -> !String.valueOf(field.get("path")).startsWith("EventSource/Device"))
                .toList());
        final String build = "e2e-shared";
        final String feed = "E2E-SHARED";
        createFeed(build, feed);
        final long raw = upload(feed, CSV_SAMPLE, Map.of("MyHostName", "collector-01",
                "GUID", "guid-e2e"));
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", mapping, "build", build,
                "name", feed + "-Events", "include_xslt", true, "schema_version", VERSION));
        assertThat(saved.get("ok")).as("saved: %s", saved.get("problems")).isEqualTo(true);
        final String xsltUuid = (String) map(saved, "saved").get("uuid");
        final Element sheet = parse((String) saved.get("xslt")).getDocumentElement();
        final Element firstChild = firstElement(sheet);
        assertThat(firstChild.getLocalName()).as("the XSLT imports the shared one first").isEqualTo("import");
        assertThat(firstChild.getAttribute("href")).isEqualTo(COMMON_EVENT);
        assertThat(descendants(sheet, "Device").stream().filter(e -> EVENT_NS.equals(e.getNamespaceURI())))
                .as("and writes no Device itself").isEmpty();
        assertThat(descendants(sheet, "Meta").stream().filter(e -> EVENT_NS.equals(e.getNamespaceURI())))
                .as("and no Meta itself").isEmpty();
        final String converter = saveTextConverter(build, feed, CSV_SPLITTER);
        final String pipeline = (String) createPipeline(build, feed + "-Events", textTemplate,
                docProperty("dsParser", "textConverter", converter, "TextConverter"),
                docProperty("translationFilter", "xslt", xsltUuid, "XSLT")).get("uuid");
        final Map<String, Object> sample = stepSample(pipeline, List.of(raw));
        assertThat(sample.get("verdict")).as("stepped %s records clean: %s", sample.get("records_stepped"),
                said(list(sample, "groups"))).isEqualTo("clean");
        final String output = translationOutput(pipeline, raw, 0);
        final Element event = children(parse(output).getDocumentElement(), "Event").getFirst();
        final Map<String, Object> valid = json("check_events", args("events_xml", output, "schema_version",
                VERSION));
        assertThat(map(valid, "schema").get("valid")).as("valid: %s", valid.get("schema")).isEqualTo(true);
        assertThat(children(at(event, "EventSource").orElseThrow(), "Device")).as("one Device").hasSize(1);
        assertThat(textAt(event, "EventSource/Device/HostName")).as("from the shared template: the header's host "
                                                                   + "name").isEqualTo("collector-01");
        assertThat(textAt(event, "EventSource/Device/IPAddress")).as("and the record's IP").isEqualTo("10.0.0.1");
        assertThat(children(event, "Meta")).as("one Meta").hasSize(1);
        assertThat(textAt(event, "Meta")).as("holding the stream's GUID").isEqualTo("guid-e2e");

        // ### 2b. its mapping lost: rebuilt from the XSLT and the shared XSLT it imports, read by name
        clearDescription(xsltUuid);
        Map<String, Object> restored = agreed("rebuild_mapping", args("uuid", xsltUuid));
        dump("rebuild_mapping", restored);
        // The mapping's own xpath entry (TypeId, a concat) is the only one kept as xpath.
        assertThat(restored.get("saved")).as("rebuilt and saved").isNotNull();
        assertThat(restored).containsEntry("mode", "mapping lost").doesNotContainKey("differences");
        assertThat(strings(map(restored, "rebuilt"), "kept_as_xpath")).allMatch(x -> x.contains("TypeId"));
        Map<String, Object> back = keptMapping(xsltUuid);
        final List<Map<String, Object>> backShared = list(back, "shared").stream()
                .map(TestE2eSharedXslt::truthy)
                .toList();
        assertThat(backShared).as("each shared template, where it is called and the parameter passed")
                .isEqualTo(uses);
        assertThat(stepSample(pipeline, List.of(raw)).get("verdict")).as("the XSLT saved from the rebuilt mapping "
                                                                         + "steps clean").isEqualTo("clean");

        // ### 2c. a shared XSLT of functions: called from an xpath entry, and read back when the mapping is lost
        final List<Map<String, Object>> functions = List.of(Map.of("href", COMMON_FUNCTIONS, "prefix", "ef",
                "namespace", "urn:e2e:functions"));
        final Map<String, Object> shout = Map.of("path", "EventSource/User/Id", "xpath",
                "ef:shout(data[@name='user']/@value)");
        final Map<String, Object> calling = new LinkedHashMap<>(mapping);
        calling.put("functions", functions);
        calling.put("common", E2eFixtures.entries(mapping).stream()
                .map(field -> "EventSource/User/Id".equals(field.get("path"))
                        ? shout
                        : field)
                .toList());
        final Map<String, Object> updated = agreed("build_translation_xslt", args("mapping", calling, "uuid",
                xsltUuid, "change", "User Id upper-cased by the shared functions", "include_xslt", true));
        assertThat(updated.get("ok")).as("updated: %s", updated.get("problems")).isEqualTo(true);
        assertThat((String) updated.get("xslt")).as("the XSLT imports the functions and calls one")
                .contains("href=\"" + COMMON_FUNCTIONS + "\"", "ef:shout(");
        Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        final String user = textAt(children(parse(translationOutput(pipeline, raw, 0)).getDocumentElement(), "Event")
                .getFirst(), "EventSource/User/Id");
        assertThat(stepped.get("verdict")).as("Stroom resolves the import by name and runs the function: %s",
                said(list(stepped, "groups"))).isEqualTo("clean");
        assertThat(user).as("the User Id upper-cased").isNotBlank().isEqualTo(user.toUpperCase());
        clearDescription(xsltUuid);
        restored = agreed("rebuild_mapping", args("uuid", xsltUuid));
        dump("rebuild_mapping functions", restored);
        back = keptMapping(xsltUuid);
        final Map<String, Object> rebuilt = map(restored, "rebuilt");
        final List<Map<String, Object>> entries = new ArrayList<>(list(back, "common"));
        E2eFixtures.rules(back).forEach(rule -> entries.addAll(list(rule, "fields")));
        assertThat(restored.get("saved")).as("rebuilt and saved").isNotNull();
        assertThat(restored).doesNotContainKey("differences");
        assertThat(back.get("functions")).as("the functions entry read back").isEqualTo(functions);
        assertThat(entries).as("and the call").contains(shout);
        assertThat(strings(rebuilt, "calls_imported_functions")).as("the call reported as calling an imported "
                                                                    + "function")
                .anyMatch(call -> call.contains("User/Id"));
        assertThat(strings(rebuilt, "kept_as_xpath")).as("not as unread").noneMatch(x -> x.contains("User/Id"));
        // As rebuild_mapping's next says.
        stepped = stepSample(pipeline, List.of(raw));
        assertThat(stepped.get("verdict")).as("the XSLT saved from the rebuilt mapping steps clean")
                .isEqualTo("clean");
    }

    // An entry with its empty values left out (`{k: v for k, v in s.items() if v}`).
    private static Map<String, Object> truthy(final Map<String, Object> entry) {
        final Map<String, Object> out = new LinkedHashMap<>();
        entry.forEach((key, value) -> {
            final boolean empty = value == null
                                  || Boolean.FALSE.equals(value)
                                  || (value instanceof final String text && text.isEmpty())
                                  || (value instanceof final Map<?, ?> map && map.isEmpty())
                                  || (value instanceof final List<?> items && items.isEmpty());
            if (!empty) {
                out.put(key, value);
            }
        });
        return out;
    }

    private static Element firstElement(final Element parent) {
        final NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            final Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) node;
            }
            if (node.getNodeType() == Node.COMMENT_NODE) {
                // lxml's sheet[0] counts a comment as a child too.
                throw new AssertionError("The XSLT starts with a comment, not its import: " + node.getTextContent());
            }
        }
        throw new AssertionError("No child elements");
    }
}
