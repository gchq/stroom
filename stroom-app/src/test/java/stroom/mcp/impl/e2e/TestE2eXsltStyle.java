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

import stroom.mcp.impl.McpTestClient;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_xslt_style.py`: how generated XSLTs are written, and the gates around them,
/// driving the real tools as the agent would. Every numbered part is covered:
///
/// 1. Layouts: one mapping saved in each layout (modes, the default; named; inline). Each steps clean in Stroom,
///    and all three give exactly the same Events. Conversions used by several elements are the XSLT's own
///    `xsl:function`s, which Stroom runs.
/// 2. The processing gate: once the XSLT is replaced after processing, wait_for_processing fails until the new
///    code steps clean.
/// 3. Shared functions: a house-style sibling calls a shared XSLT's functions and a shared named template from
///    inside part templates; describe_template lists them, and a mapping calling the functions steps clean through
///    a functions entry, and is refused without one.
/// 4. Unknown: kept only after a refusal and keep_unknown, confirmed by the user; a hand-written XSLT's Unknown is
///    blocked, saying what the records hold; and Unknown can't be accepted as a benign error.
/// 5. The naming choice: get_field_conventions asks in a form and gives the next call.
@SuppressWarnings("unchecked")
class TestE2eXsltStyle extends AbstractE2eTest {

    private static final String BUILD = "style";
    private static final String FEED = "STYLE";
    private static final String TEXT_TEMPLATE = "E2E Style Text";
    private static final String COMMON_FUNCTIONS = "E2E-Common-Functions-V1";
    private static final String FUNCTIONS_NS = "urn:e2e:functions";
    private static final String XSL = "http://www.w3.org/1999/XSL/Transform";
    private static final String SHARED_FUNCTIONS = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:e2e="urn:e2e:functions" \
            xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0">
              <!-- How urgent a severity is, the same way in every translation. -->
              <xsl:function name="e2e:severityLevel" as="xs:string">
                <xsl:param name="severity" as="xs:string?" />
                <xsl:sequence select="if ($severity = ('ERROR', 'CRITICAL')) then 'high' else 'normal'" />
              </xsl:function>
              <xsl:function name="e2e:isInternal" as="xs:boolean">
                <xsl:param name="ip" as="xs:string?" />
                <xsl:sequence select="starts-with(string($ip), '192.0.2.')" />
              </xsl:function>
            </xsl:stylesheet>
            """;
    // The sibling, in the house style seen live: each part a template rule with its own mode. Only read, never run.
    private static final String SIBLING = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xpath-default-namespace="records:2" xmlns="event-logging:3" xmlns:stroom="stroom"
                xmlns:e2e="urn:e2e:functions" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
              <xsl:import href="E2E-Common-Event-V1" />
              <xsl:import href="E2E-Common-Functions-V1" />
              <xsl:template match="records"><Events><xsl:apply-templates select="record" mode="event" /></Events>\
            </xsl:template>
              <xsl:template match="node()" mode="event">
                <Event>
                  <EventTime><TimeCreated><xsl:value-of select="data[@name='when']/@value" /></TimeCreated>\
            </EventTime>
                  <EventSource>
                    <System><Name>Other</Name><Environment>Dev</Environment></System>
                    <Generator>other</Generator>
                    <xsl:apply-templates select="." mode="eventSourceDevice" />
                  </EventSource>
                  <EventDetail><TypeId>Other</TypeId><Unknown><xsl:apply-templates select="." mode="commonData" />\
            </Unknown></EventDetail>
                </Event>
              </xsl:template>
              <xsl:template match="node()" mode="eventSourceDevice">
                <xsl:call-template name="eventSourceDevice"><xsl:with-param name="ip" \
            select="data[@name='src']/@value" /></xsl:call-template>
              </xsl:template>
              <xsl:template match="node()" mode="commonData">
                <Data Name="level" Value="{e2e:severityLevel(data[@name='severity']/@value)}" />
              </xsl:template>
            </xsl:stylesheet>
            """;
    // The firewall sample with domain users, for strip_domain.
    private static final String SAMPLE = E2eFixtures.FIREWALL_SAMPLE.replace(",admin,", ",admin@corp.example,");
    private static final String TIME = "yyyy-MM-dd'T'HH:mm:ssXXX";
    private static final int RECORDS = (int) SAMPLE.chars().filter(c -> c == '\n').count() - 1;
    private static final List<Integer> ALL_RECORDS = IntStream.range(0, RECORDS).boxed().toList();

    private long raw;
    private String templateUuid;
    private String converter;

    @Override
    boolean followsNext() {
        return true;
    }

    @Test
    void generatedXsltsAndTheGatesAroundThem() {
        createFeed(BUILD, FEED);
        raw = upload(FEED, SAMPLE);
        templateUuid = templateUuid("translation", "Event Data (Text)");
        converter = saveTextConverter(BUILD, FEED, CSV_SPLITTER);

        final Map<String, Saved> saved = layouts();
        processingGate(saved.get("modes"));
        sharedFunctions();
        unknown();
        namingChoice();
    }

    // ### 1. one mapping in each layout: the same Events, with the conversions as functions
    private Map<String, Saved> layouts() {
        final Map<String, List<String>> outputs = new LinkedHashMap<>();
        final Map<String, Saved> saved = new LinkedHashMap<>();
        for (final String layout : List.of("modes", "named", "inline")) {
            final Map<String, Object> mapping = styleMapping("modes".equals(layout)
                    ? null
                    : layout, "E2E");
            final Map<String, Object> result = agreed("build_translation_xslt", args("mapping", mapping,
                    "stream_ids", List.of(raw), "build", BUILD, "name", FEED + "-" + layout, "include_xslt", true,
                    "schema_version", VERSION));
            assertThat(result.get("ok")).as("%s: saved with its mapping: %s", layout, result.get("problems"))
                    .isEqualTo(true);
            assertThat(result.get("saved")).as("%s: saved", layout).isNotNull();
            final Element sheet = parse((String) result.get("xslt")).getDocumentElement();
            final List<String> functions = children(sheet, "function").stream()
                    .map(function -> function.getAttribute("name"))
                    .sorted()
                    .toList();
            // mcp:data: its Data entries.
            assertThat(functions).as("%s: the time format and strip_domain, each used twice, are functions", layout)
                    .containsExactly("mcp:data", "mcp:parse_time", "mcp:strip_domain");
            final Element record = children(sheet, "template").stream()
                    .filter(template -> "event".equals(template.getAttribute("mode")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(layout + ": no record template"));
            switch (layout) {
                case "modes" -> {
                    final List<String> kinds = xslDescendants(record, "apply-templates").stream()
                            .map(apply -> apply.getAttribute("mode"))
                            .toList();
                    final Set<String> parts = new TreeSet<>();
                    children(sheet, "template").stream()
                            .filter(template -> "node()".equals(template.getAttribute("match")))
                            .forEach(template -> parts.add(template.getAttribute("mode")));
                    final boolean named = children(sheet, "template").stream()
                            .anyMatch(template -> template.hasAttribute("name"));
                    assertThat(kinds).as("modes: each kind a mode template, applied to the record")
                            .containsExactly("event_type_traffic", "event_type_logon", "event_type_logoff",
                                    "event_type_system");
                    assertThat(parts).as("modes: each shared part a mode template").contains("event_time",
                            "event_source");
                    assertThat(named).as("modes: no named templates").isFalse();
                }
                case "named" -> assertThat(xslDescendants(record, "call-template").stream()
                        .map(call -> call.getAttribute("name"))
                        .toList())
                        .as("named: each kind a named template, called")
                        .containsExactly("event_type_traffic", "event_type_logon", "event_type_logoff",
                                "event_type_system");
                default -> assertThat(descendants(record, "Event")).as("inline: every kind written in the "
                                                                       + "record template").hasSize(4);
            }
            final String xsltUuid = (String) map(result, "saved").get("uuid");
            final String pipelineUuid = (String) pipelineFor(BUILD, FEED + "-" + layout + "-Events", templateUuid,
                    converter, xsltUuid).get("uuid");
            final Map<String, Object> sample = stepSample(pipelineUuid, List.of(raw));
            assertThat(sample.get("verdict")).as("%s: stepped clean in Stroom: %s", layout,
                    said(list(sample, "groups"))).isEqualTo("clean");
            outputs.put(layout, eventsOf(pipelineUuid, raw, ALL_RECORDS));
            saved.put(layout, new Saved(xsltUuid, pipelineUuid));
        }
        assertThat(outputs.get("modes")).as("all three layouts give the same %s Events", RECORDS).hasSize(RECORDS);
        assertThat(outputs.get("named")).as("named gives the Events modes gives").isEqualTo(outputs.get("modes"));
        assertThat(outputs.get("inline")).as("inline gives the Events modes gives").isEqualTo(outputs.get("modes"));

        final List<Element> events = new ArrayList<>();
        for (final int record : ALL_RECORDS) {
            events.addAll(children(parse(translationOutput(saved.get("modes").pipeline(), raw, record))
                    .getDocumentElement(), "Event"));
        }
        final StringBuilder all = new StringBuilder("<Events xmlns=\"event-logging:3\" Version=\"4.1.0\">");
        outputs.get("modes").forEach(all::append);
        all.append("</Events>");
        final Map<String, Object> valid = json("check_events", args("events_xml", all.toString(),
                "schema_version", VERSION));
        assertThat(map(valid, "schema").get("valid")).as("valid against the schema: %s", valid).isEqualTo(true);
        final Element logon = events.stream()
                .filter(event -> !descendants(event, "Authenticate").isEmpty())
                .findFirst()
                .orElseThrow();
        assertThat(textAt(logon, "EventSource/User/Id")).as("mcp:strip_domain, run by Stroom: admin@corp.example "
                                                            + "is admin").isEqualTo("admin");
        assertThat(textAt(logon, "EventDetail/Authenticate/User/Id")).as("in both places").isEqualTo("admin");
        final String logged = descendants(events.getFirst(), "Data").stream()
                .filter(data -> "logged".equals(data.getAttribute("Name")))
                .map(data -> data.getAttribute("Value"))
                .findFirst()
                .orElse(null);
        assertThat(logged).as("mcp:parse_time, run by Stroom, gives the event time in both places")
                .isEqualTo(textAt(events.getFirst(), "EventTime/TimeCreated"))
                .isEqualTo("2026-09-30T23:00:12.000Z");
        return saved;
    }

    // ### 2. the processing gate: a replaced XSLT steps clean before its output counts
    private void processingGate(final Saved modes) {
        final Map<String, Object> done = process(modes.pipeline(), List.of(raw));
        assertThat(done.get("gate")).as("processed: %s %s", done.get("streams"), done.get("problems"))
                .isEqualTo("pass");
        final Map<String, Object> replaced = agreedNotFollowed("build_translation_xslt", args("mapping",
                styleMapping(null, "E2E-2"), "stream_ids", List.of(raw), "uuid", modes.xslt()));
        assertThat(replaced.get("ok")).as("the XSLT replaced: %s", replaced.get("problems")).isEqualTo(true);
        final Map<String, Object> gated = json("wait_for_processing", args("pipeline_uuid", modes.pipeline(),
                "stream_ids", List.of(raw), "timeout_seconds", 10));
        assertThat(gated.get("gate")).as("the gate fails on the unstepped code: %s", gated.get("problems"))
                .isEqualTo("fail");
        assertThat(strings(gated, "problems")).as("the gate names the unstepped code")
                .anyMatch(problem -> problem.contains("has not stepped clean"));
        assertThat(stepSample(modes.pipeline(), List.of(raw)).get("verdict")).as("the new code steps clean")
                .isEqualTo("clean");
        final Map<String, Object> after = json("wait_for_processing", args("pipeline_uuid", modes.pipeline(),
                "stream_ids", List.of(raw), "timeout_seconds", 10));
        assertThat(strings(after, "problems")).as("and the gate no longer holds it")
                .noneMatch(problem -> problem.contains("has not stepped clean"));
    }

    // ### 3. shared functions: found in the house-style sibling, imported and called
    private void sharedFunctions() {
        sharedXslt(E2eFixtures.COMMON_EVENT, E2eFixtures.SHARED_EVENT);
        sharedXslt(COMMON_FUNCTIONS, SHARED_FUNCTIONS);
        final String textTemplate = fixtureTemplate(TEXT_TEMPLATE, "Event Data (Text)");
        final String src = "style-src";
        final String sibling = saveXslt(src, "STYLE-OTHER-Events", SIBLING);
        final String siblingConverter = saveTextConverter(src, "STYLE-OTHER", CSV_SPLITTER);
        pipelineFor(src, "STYLE-OTHER-Events", textTemplate, siblingConverter, sibling);
        final Map<String, Object> described = json("describe_template", args("template_uuid", textTemplate));
        dump("describe_template", described.get("shared_xslt"));
        final List<Map<String, Object>> rows = list(described, "shared_xslt").stream()
                .filter(row -> strings(row, "used_by").stream().anyMatch(user -> user.startsWith("STYLE-OTHER-")))
                .toList();
        final Map<String, Object> function = rows.stream()
                .filter(row -> "e2e:severityLevel".equals(row.get("function")))
                .findFirst()
                .orElse(Map.of());
        assertThat(function).as("the function the sibling calls, with its namespace")
                .containsEntry("namespace", FUNCTIONS_NS)
                .containsEntry("href", COMMON_FUNCTIONS)
                .containsEntry("params", List.of("severity as xs:string?"))
                .containsEntry("returns", "xs:string")
                .containsEntry("calls", 1);
        final Map<String, Object> device = rows.stream()
                .filter(row -> "eventSourceDevice".equals(row.get("template")))
                .findFirst()
                .orElse(Map.of());
        assertThat(device.get("at")).as("the shared template, called in a part's mode template, placed where the "
                                        + "part is applied").isEqualTo(List.of("EventSource/Device"));
        final Map<String, Object> contents = list(described, "shared_xslt").stream()
                .map(row -> (Map<String, Object>) row.get("document_contents"))
                .filter(document -> document != null && COMMON_FUNCTIONS.equals(document.get("name")))
                .findFirst()
                .orElse(Map.of());
        assertThat(new TreeSet<>(((Map<String, Object>) contents.getOrDefault("functions", Map.of())).keySet()))
                .as("and everything the shared XSLT offers, called or not")
                .containsExactly("e2e:isInternal", "e2e:severityLevel");

        final Map<String, Object> withFunctions = E2eFixtures.firewallMapping();
        E2eFixtures.entries(E2eFixtures.rule(withFunctions, "system")).add(Map.of("path", "EventDetail/Alert/Data",
                "data_name", "level", "xpath", "e2e:severityLevel(data[@name='severity']/@value)"));
        E2eFixtures.entries(E2eFixtures.rule(withFunctions, "traffic")).add(Map.of("path",
                "EventDetail/Network/Open/Data", "data_name", "internal", "xpath",
                "string(e2e:isInternal(data[@name='src_ip']/@value))"));
        final Map<String, Object> unbound = json("build_translation_xslt", args("mapping", withFunctions,
                "stream_ids", List.of(raw), "schema_version", VERSION));
        assertThat(unbound.get("ok")).as("calling them without a functions entry is refused").isEqualTo(false);
        assertThat(strings(unbound, "problems")).as("naming the prefix no entry binds")
                .anyMatch(problem -> problem.contains("no functions entry binds 'e2e'"));
        withFunctions.put("functions", List.of(Map.of("href", function.get("href"), "prefix", "e2e",
                "namespace", function.get("namespace"))));
        final Map<String, Object> result = agreed("build_translation_xslt", args("mapping", withFunctions,
                "stream_ids", List.of(raw), "build", BUILD, "name", FEED + "-functions", "include_xslt", true,
                "schema_version", VERSION));
        assertThat(result.get("ok")).as("saved: %s", result.get("problems")).isEqualTo(true);
        final Element sheet = parse((String) result.get("xslt")).getDocumentElement();
        assertThat(children(sheet, "import").stream().map(i -> i.getAttribute("href")).toList())
                .as("the XSLT imports the shared one").containsExactly(COMMON_FUNCTIONS);
        assertThat(sheet.lookupNamespaceURI("e2e")).as("and binds its prefix").isEqualTo(FUNCTIONS_NS);
        final String pipelineUuid = (String) pipelineFor(BUILD, FEED + "-functions-Events", templateUuid, converter,
                (String) map(result, "saved").get("uuid")).get("uuid");
        final Map<String, Object> sample = stepSample(pipelineUuid, List.of(raw));
        assertThat(sample.get("verdict")).as("steps clean in Stroom, which resolves the import: %s",
                said(list(sample, "groups"))).isEqualTo("clean");
        final List<String> levels = new ArrayList<>();
        final List<String> internal = new ArrayList<>();
        for (final int record : ALL_RECORDS) {
            for (final Element event : children(parse(translationOutput(pipelineUuid, raw, record))
                    .getDocumentElement(), "Event")) {
                at(event, "EventDetail/Alert").ifPresent(alert -> children(alert, "Data").stream()
                        .filter(data -> "level".equals(data.getAttribute("Name")))
                        .forEach(data -> levels.add(data.getAttribute("Value"))));
                descendants(event, "Data").stream()
                        .filter(data -> "internal".equals(data.getAttribute("Name")))
                        .forEach(data -> internal.add(data.getAttribute("Value")));
            }
        }
        assertThat(levels).as("the shared functions' values in the Events: levels").containsExactly("normal",
                "high", "normal");
        assertThat(internal).as("the shared functions' values in the Events: internal").containsExactly("true",
                "false");
    }

    // ### 4. Unknown: kept only as the user chooses, and said for what it holds
    private void unknown() {
        final Map<String, Object> unknown = E2eFixtures.firewallMapping();
        final Map<String, Object> traffic = new LinkedHashMap<>(Map.of("name", "traffic",
                "when", List.of(Map.of("field", "event_type", "equals", "TRAFFIC")),
                "allow_unknown", "the user wants traffic kept as it came",
                "fields", List.of(Map.of("path", "EventDetail/Unknown/Data", "data_name", "action",
                        "field", "action"))));
        E2eFixtures.rules(unknown).set(0, traffic);
        final Map<String, Object> refused = json("build_translation_xslt", args("mapping", unknown, "stream_ids",
                List.of(raw), "schema_version", VERSION));
        assertThat(refused.get("ok")).as("refused first").isEqualTo(false);
        assertThat(strings(refused, "problems")).as("naming keep_unknown")
                .anyMatch(problem -> problem.contains("can't be kept as Unknown")
                                     && problem.contains("keep_unknown: true"));
        traffic.put("keep_unknown", true);
        final Map<String, Object> keep = args("mapping", unknown, "stream_ids", List.of(raw), "build", BUILD,
                "name", FEED + "-unknown", "schema_version", VERSION);
        // The detail shown in the chat first, the form kept short (asked for by the user: too much tiny text in a
        // form).
        final Map<String, Object> shown = json("build_translation_xslt", keep);
        assertThat(shown).as("the user is shown the records first").containsEntry("status", "needs_review");
        assertThat((String) shown.get("show_user")).as("told what the values suggest")
                .contains("Their values suggest", "Records rule `traffic` matches");
        final int formsBefore = client.forms().size();
        final Map<String, Object> kept = json("build_translation_xslt", with(keep, "reviewed",
                shown.get("review_id")));
        final List<ElicitFormRequest> asked = client.forms().subList(formsBefore, client.forms().size());
        assertThat(asked).as("then a short form").extracting(ElicitFormRequest::message)
                .anyMatch(message -> message.contains("Translate the records shown in the chat as Unknown events"));
        assertThat(kept.get("ok")).as("kept as Unknown once the user agrees: %s", kept.get("problems"))
                .isEqualTo(true);
        final String keptUuid = (String) map(kept, "saved").get("uuid");
        String pipelineUuid = (String) pipelineFor(BUILD, FEED + "-unknown-Events", templateUuid, converter,
                keptUuid).get("uuid");
        assertThat(stepSample(pipelineUuid, List.of(raw)).get("verdict")).as("the agreed Unknown steps clean")
                .isEqualTo("clean");

        // The same code saved by hand, without its mapping: nobody agreed to its Unknown.
        final String hand = saveXslt(BUILD, FEED + "-hand", readXslt(keptUuid).getData());
        pipelineUuid = (String) pipelineFor(BUILD, FEED + "-hand-Events", templateUuid, converter, hand).get("uuid");
        final Map<String, Object> sample = stepSample(pipelineUuid, List.of(raw));
        final Map<String, Object> block = list(sample, "groups").stream()
                .filter(group -> String.valueOf(group.get("reason")).contains("Unknown"))
                .findFirst()
                .orElse(Map.of());
        final String message = String.valueOf(list(block, "examples").stream()
                .findFirst()
                .orElse(Map.of())
                .get("message"));
        assertThat(sample.get("verdict")).as("a hand-written XSLT's Unknown is blocked: %s",
                said(list(sample, "groups"))).isEqualTo("blocking");
        assertThat(message).as("saying what the records hold").contains("they hold", "ALLOW", "DENY");
        final String accepted = refusal("write_documentation", args("build", BUILD, "pipeline_uuid", pipelineUuid,
                "markdown", "## Purpose\n\nE2E.", "accept_errors", List.of(Map.of("element", "translationFilter",
                        "example", message.substring(0, Math.min(200, message.length())),
                        "reason", "they are fine"))));
        assertThat(accepted).as("Unknown isn't accepted as a benign error").contains("aren't an error to accept");
    }

    // ### 5. the naming choice, in a form
    private void namingChoice() {
        final List<List<Object>> picked = new ArrayList<>();
        client.answeringWith(form -> {
            // The choices, as the form's one field lists them; the last picked.
            final Map<String, Object> properties = (Map<String, Object>) form.requestedSchema().get("properties");
            final Map<String, Object> field = (Map<String, Object>) properties.values().iterator().next();
            final List<Object> options = (List<Object>) field.get("enum");
            picked.add(options);
            final String name = properties.keySet().iterator().next();
            return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of(name, options.getLast()), null);
        });
        final Map<String, Object> chosen = json("get_field_conventions", args("backend", "elasticsearch"));
        client.answeringWith(McpTestClient::agree);
        dump("get_field_conventions", chosen);
        assertThat(picked).as("the user picks from a picker").isNotEmpty();
        assertThat(picked.getFirst().getFirst()).as("the first choice").isEqualTo("From an index template");
        assertThat(chosen).as("the reply").containsEntry("status", "chosen");
        assertThat((String) chosen.get("hint")).as("the reply gives the next call")
                .contains("draft_index_mapping convention=", "isn't asked again");
    }

    // The firewall mapping, with strip_domain on two users and the time format twice: both become functions.
    private static Map<String, Object> styleMapping(final String layout, final String systemName) {
        final Map<String, Object> mapping = E2eFixtures.firewallMapping();
        for (final Map<String, Object> field : E2eFixtures.entries(mapping)) {
            if ("EventSource/User/Id".equals(field.get("path"))) {
                field.put("transform", "strip_domain");
            }
            if ("EventSource/System/Name".equals(field.get("path"))) {
                field.put("value", systemName);
            }
        }
        for (final Map<String, Object> rule : E2eFixtures.rules(mapping)) {
            for (final Map<String, Object> field : E2eFixtures.entries(rule)) {
                if ("EventDetail/Authenticate/User/Id".equals(field.get("path"))) {
                    field.put("transform", "strip_domain");
                }
            }
            if ("traffic".equals(rule.get("name"))) {
                E2eFixtures.entries(rule).add(Map.of("path", "EventDetail/Network/Open/Data", "data_name", "logged",
                        "field", "timestamp", "time_format", TIME));
            }
        }
        if (layout != null) {
            mapping.put("style", Map.of("layout", layout));
        }
        return mapping;
    }

    private Map<String, Object> pipelineFor(final String build,
                                            final String name,
                                            final String template,
                                            final String textConverter,
                                            final String xslt) {
        return createPipeline(build, name, template,
                docProperty("dsParser", "textConverter", textConverter, "TextConverter"),
                docProperty("translationFilter", "xslt", xslt, "XSLT"));
    }

    private static Map<String, Object> with(final Map<String, Object> arguments, final String key,
                                            final Object value) {
        final Map<String, Object> changed = new LinkedHashMap<>(arguments);
        changed.put(key, value);
        return changed;
    }

    private static List<Element> xslDescendants(final Element parent, final String name) {
        return descendants(parent, name).stream()
                .filter(element -> XSL.equals(element.getNamespaceURI()))
                .toList();
    }


    // --------------------------------------------------------------------------------


    /// An XSLT saved in one layout, and the pipeline running it.
    private record Saved(String xslt, String pipeline) {

    }
}
