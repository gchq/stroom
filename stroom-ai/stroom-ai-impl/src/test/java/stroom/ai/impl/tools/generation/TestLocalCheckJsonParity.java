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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.pipeline.xml.converter.json.JSONFactoryConfig;
import stroom.pipeline.xml.converter.json.JSONParser;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.Serializer;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Found in the audit of 2026-11-26: the tools' verdicts on JSON records (LocalCheck) against the generated XSLT run
/// over the records as Stroom's own JSONParser writes them (not a re-implementation of it).
class TestLocalCheckJsonParity extends XsltRuns {

    private static final String PARITY = "urn:audit-parity";

    @Test
    void aJsonNumberIsComparedAsWritten() {
        // Stroom's JSONParser writes a number's text as the source wrote it (Jackson's getValueAsString), so an
        // equals on "1.50" holds in processing; the tools read the number and write it again ("1.5").
        final List<String> lines = List.of(
                "{\"n\": 1.50}",
                "{\"n\": 1e3}",
                "{\"n\": -0}",
                "{\"n\": 1E+2}",
                "{\"n\": 0.10000000000000001}");
        final List<Map<String, Object>> conditions = List.of(
                equalsTo("n", "1.50"), equalsTo("n", "1.5"),
                equalsTo("n", "1e3"), equalsTo("n", "1000.0"),
                equalsTo("n", "-0"), equalsTo("n", "0"),
                equalsTo("n", "1E+2"), equalsTo("n", "100.0"),
                equalsTo("n", "0.10000000000000001"), equalsTo("n", "0.1"));
        assertThat(disagreements(mapping(Map.of(), conditions), lines)).isEmpty();
    }

    @Test
    void aDuplicatedKeyIsReadAsStroomReadsIt() {
        // Stroom's JSONParser streams both members: *[@key='a'] selects both, so equals holds on either.
        final List<String> lines = List.of("{\"a\": \"x\", \"a\": \"y\"}");
        final List<Map<String, Object>> conditions = List.of(equalsTo("a", "x"), equalsTo("a", "y"),
                not(equalsTo("a", "x")));
        assertThat(disagreements(mapping(Map.of(), conditions), lines)).isEmpty();
    }

    @Test
    void aDuplicatedKeyInHeldJsonIsReadAsJsonToXmlReadsIt() {
        final List<String> lines = List.of("{\"held\": \"{\\\"a\\\": \\\"x\\\", \\\"a\\\": \\\"y\\\"}\"}");
        final List<Map<String, Object>> conditions = List.of(equalsTo("held.a", "x"), equalsTo("held.a", "y"),
                not(equalsTo("held.a", "x")));
        assertThat(disagreements(mapping(Map.of("json_fields", List.of("held")), conditions), lines)).isEmpty();
    }

    @Test
    void aNumberInHeldJsonIsComparedAsJsonToXmlWritesIt() {
        final List<String> lines = List.of("{\"held\": \"{\\\"n\\\": 1.50, \\\"e\\\": 1e3}\"}");
        final List<Map<String, Object>> conditions = List.of(equalsTo("held.n", "1.50"), equalsTo("held.n", "1.5"),
                equalsTo("held.e", "1e3"), equalsTo("held.e", "1000.0"));
        assertThat(disagreements(mapping(Map.of("json_fields", List.of("held")), conditions), lines)).isEmpty();
    }

    @Test
    void aDropRuleOnADuplicatedKeyDropsTheRecordsTheToolsSayItDrops() {
        // The rule the tools say each record takes, against the records the XSLT writes an event for.
        final Map<String, Object> json = baseMapping(Map.of());
        json.put("drop_when", List.of(Map.of("reason", "noise", "when", List.of(equalsTo("level", "debug")))));
        json.put("events", List.of(Map.of("name", "all", "allow_unknown", "a test", "fields", fields())));
        final TranslationMapping mapping = TranslationMapping.fromJson(json);
        final List<String> lines = List.of(
                "{\"id\": \"1\", \"level\": \"debug\"}",
                "{\"id\": \"2\", \"level\": \"debug\", \"level\": \"error\"}",
                "{\"id\": \"3\", \"level\": \"error\", \"level\": \"debug\"}");
        final List<Object> records = LocalCheck.sampleRecords(mapping, String.join("\n", lines), null).records();
        final List<String> tools = records.stream().map(r -> LocalCheck.ruleOf(mapping, r, null)).toList();

        final XsltGenerator.Generated generated = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(generated.problems()).isEmpty();
        final XmlElement events = transform(generated.xslt(), stroomXml(lines));
        final List<String> kept = events.iterElements(EVT, "Event").stream()
                .map(event -> having(event, EVT, "Data", "Name", "id").getFirst().get("Value"))
                .toList();
        final List<String> stroom = Stream.of("1", "2", "3")
                .map(id -> kept.contains(id)
                        ? "all"
                        : "drop: noise")
                .toList();
        assertThat(tools).as("the tools' rules, record by record, against processing's").isEqualTo(stroom);
    }

    @Test
    void aDuplicatedKeysValueIsWrittenAsTheToolsReadIt() {
        // The XSLT writes a field's first value ($field[1]); the tools read the last member (a map's put).
        final TranslationMapping mapping = mapping(Map.of(), List.of(equalsTo("id", "1")));
        final List<String> lines = List.of("{\"id\": \"1\", \"user\": \"alice\", \"user\": \"mallory\"}");
        final Map<String, Object> json = baseMapping(Map.of());
        json.put("events", List.of(Map.of("name", "all", "allow_unknown", "a test", "fields", List.of(
                Map.of("path", "EventDetail/TypeId", "value", "T"),
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "user", "field", "user")))));
        final TranslationMapping written = TranslationMapping.fromJson(json);
        final Object record = LocalCheck.sampleRecords(written, lines.getFirst(), null).records().getFirst();
        final XmlElement events = transform(XsltGenerator.generate(written, SCHEMA, "4.1.0").xslt(),
                stroomXml(lines));
        final String stroom = having(events.iterElements(EVT, "Event").getFirst(), EVT, "Data", "Name", "user")
                .getFirst().get("Value");
        assertThat(mapping.events()).isNotEmpty();
        assertThat(LocalCheck.valueOf(record, "user")).as("the tools' value against processing's").isEqualTo(stroom);
    }

    @Test
    void anEpochSecondsNumberTheCheckAcceptsIsConvertedInProcessing() {
        // A Java writer's double (Double.toString) puts epoch seconds in exponent form: 1.7641512001E9.
        final Map<String, Object> json = baseMapping(Map.of());
        json.put("common", List.of(
                Map.of("path", "EventTime/TimeCreated", "field", "ts", "time_format", "epoch_s"),
                Map.of("path", "EventSource/System/Name", "value", "Audit"),
                Map.of("path", "EventSource/System/Environment", "value", "Test"),
                Map.of("path", "EventSource/Generator", "value", "Audit"),
                Map.of("path", "EventSource/Device/HostName", "value", "host")));
        json.put("events", List.of(Map.of("name", "all", "allow_unknown", "a test", "fields", fields())));
        final TranslationMapping mapping = TranslationMapping.fromJson(json);
        final List<String> lines = List.of("{\"id\": \"1\", \"ts\": 1.7641512001E9}");
        final Object record = LocalCheck.sampleRecords(mapping, lines.getFirst(), null).records().getFirst();
        final String value = LocalCheck.valueOf(record, "ts");
        assertThat(stroom.ai.impl.tools.sample.TimePatterns.checkTimeFormat("epoch_s", List.of(value)))
                .as("the check of %s", value).isNull();
        String time;
        try {
            final XmlElement events = transform(XsltGenerator.generate(mapping, SCHEMA, "4.1.0").xslt(),
                    stroomXml(lines));
            time = text(events.iterElements(EVT, "Event").getFirst(), "EventTime/TimeCreated");
        } catch (final IllegalStateException e) {
            time = "error: " + e.getMessage();
        }
        // 1764151200.1 s is 2025-11-26T10:00:00.1Z (the audit's expectation said 2026, a slip).
        assertThat(time).as("processing's TimeCreated").isEqualTo("2025-11-26T10:00:00.100Z");
    }

    @Test
    void aDropRuleOnALongTopLevelStringIsWarnedOfAsOneOnHeldJsonIs() {
        // A top-level string past stringTruncateLength: processing tests its first 10,000 characters. The negated
        // rule (drop what isn't an alert) drops the alert in processing; the check, reading it whole, keeps it.
        final Map<String, Object> json = baseMapping(Map.of());
        json.put("drop_when", List.of(Map.of("reason", "not an alert", "when",
                List.of(not(Map.of("field", "msg", "contains", "ALERT"))))));
        json.put("events", List.of(Map.of("name", "all", "allow_unknown", "a test", "fields", fields())));
        final TranslationMapping mapping = TranslationMapping.fromJson(json);
        final List<String> lines = List.of(
                "{\"id\": \"1\", \"msg\": \"" + "x".repeat(LocalCheck.JSON_PARSER_CUT) + " ALERT\"}",
                "{\"id\": \"2\", \"msg\": \"short ALERT\"}");
        final List<Object> records = LocalCheck.sampleRecords(mapping, String.join("\n", lines), null).records();
        final List<String> tools = records.stream().map(r -> LocalCheck.ruleOf(mapping, r, null)).toList();
        final List<String> warnings = stroom.ai.impl.tools.draft.CutHeldDrops.warnings(
                stroom.ai.impl.tools.draft.CheckedRecords.of(mapping, records), LocalCheck.JSON_PARSER_CUT);

        final XmlElement events = transform(XsltGenerator.generate(mapping, SCHEMA, "4.1.0").xslt(),
                stroomXml(lines));
        final List<String> kept = events.iterElements(EVT, "Event").stream()
                .map(event -> having(event, EVT, "Data", "Name", "id").getFirst().get("Value"))
                .toList();
        final List<String> stroom = Stream.of("1", "2")
                .map(id -> kept.contains(id)
                        ? "all"
                        : "drop: not an alert")
                .toList();
        assertThat(tools).as("the check's rules").containsExactly("all", "all");
        assertThat(warnings.isEmpty() && !tools.equals(stroom))
                .as("processing's rules %s differ from the check's %s with no warning", stroom, tools)
                .isFalse();
    }

    @Test
    void aRecordTheToolsReadIsOneStroomsJsonParserReads() {
        // Each a JSON lines sample of two records; the tools read both with no note, so the check speaks of them.
        final List<String> second = List.of(
                "{\"id\": \"2\", \"v\": NaN}",
                "{\"id\": \"2\", \"v\": Infinity}",
                "{\"id\": \"2\", \"v\": \"tab\there\"}",
                "{\"id\": \"2\", \"v\": 01}");
        final List<String> out = new ArrayList<>();
        for (final String line : second) {
            final List<String> lines = List.of("{\"id\": \"1\"}", line);
            final LocalCheck.SampleRecords tools = LocalCheck.sampleRecords(mapping(Map.of(), List.of(
                    equalsTo("id", "1"))), String.join("\n", lines), null);
            String stroom;
            try {
                stroomXml(lines);
                stroom = "parsed";
            } catch (final IllegalStateException e) {
                stroom = "fails: " + e.getMessage();
            }
            if (tools.records().size() == 2 && tools.note() == null && !"parsed".equals(stroom)) {
                out.add(line + ": the tools read it; Stroom's JSONParser " + stroom);
            }
        }
        assertThat(out).isEmpty();
    }

    @Test
    void heldJsonHoldingNaNIsReadAsJsonToXmlReadsIt() {
        // json-to-xml refuses NaN (not JSON), so mcp:json-to-xml gives nothing: no key of the held JSON is there.
        final List<String> lines = List.of("{\"held\": \"{\\\"a\\\": \\\"x\\\", \\\"v\\\": NaN}\"}");
        final List<Map<String, Object>> conditions = List.of(equalsTo("held.a", "x"), not(equalsTo("held.a", "x")));
        assertThat(disagreements(mapping(Map.of("json_fields", List.of("held")), conditions), lines)).isEmpty();
    }

    // The records as Stroom's JSONParser (addRootObject, the default stringTruncateLength) writes them for the XSLT.
    static String stroomXml(final List<String> lines) {
        try {
            final Processor processor = new Processor(false);
            final StringWriter out = new StringWriter();
            final Serializer serializer = processor.newSerializer(out);
            serializer.setOutputProperty(Serializer.Property.VERSION, "1.1");
            final JSONParser parser = new JSONParser(JSONFactoryConfig.builder().build(), true);
            parser.setContentHandler(serializer.getContentHandler());
            parser.parse(new InputSource(new StringReader(String.join("\n", lines))));
            return out.toString();
        } catch (final Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static List<String> disagreements(final TranslationMapping mapping, final List<String> lines) {
        final List<Object> records = LocalCheck.sampleRecords(mapping, String.join("\n", lines), null).records();
        final List<Condition> when = mapping.events().getFirst().when();
        final List<List<Boolean>> xslt = xsltVerdicts(mapping, when, stroomXml(lines));
        assertThat(xslt).hasSize(records.size());
        final List<String> out = new ArrayList<>();
        for (int n = 0; n < records.size(); n++) {
            for (int i = 0; i < when.size(); i++) {
                final Boolean tools = LocalCheck.holds(mapping, when.get(i), records.get(n), null);
                if (!xslt.get(n).get(i).equals(tools)) {
                    out.add(lines.get(n) + " " + ToolJson.dumps(when.get(i).toJson(DumpMode.COMPACT)) + ": tools "
                            + tools + ", processing " + xslt.get(n).get(i));
                }
            }
        }
        return out;
    }

    private static Map<String, Object> baseMapping(final Map<String, Object> input) {
        final Map<String, Object> json = new LinkedHashMap<>(Map.of("input", "json", "json_layout", "lines"));
        json.putAll(input);
        json.put("unmatched", "skip");
        json.put("log_unmapped_fields", false);
        json.put("common", List.of(
                Map.of("path", "EventTime/TimeCreated", "value", "2026-11-19T10:00:00.000Z"),
                Map.of("path", "EventSource/System/Name", "value", "Audit"),
                Map.of("path", "EventSource/System/Environment", "value", "Test"),
                Map.of("path", "EventSource/Generator", "value", "Audit"),
                Map.of("path", "EventSource/Device/HostName", "value", "host")));
        return json;
    }

    private static List<Map<String, Object>> fields() {
        return List.of(Map.of("path", "EventDetail/TypeId", "value", "T"),
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "id", "field", "id"));
    }

    static TranslationMapping mapping(final Map<String, Object> input, final List<Map<String, Object>> conditions) {
        final Map<String, Object> json = baseMapping(input);
        json.put("events", List.of(
                Map.of("name", "all", "when", conditions, "allow_unknown", "a test", "fields", fields()),
                Map.of("name", "rest", "allow_unknown", "a test", "fields", fields())));
        return TranslationMapping.fromJson(json);
    }

    // As TestConditionParity: the record template's choice replaced by each of its first rule's tests' values.
    static List<List<Boolean>> xsltVerdicts(final TranslationMapping mapping,
                                            final List<Condition> when,
                                            final String records) {
        final XsltGenerator.Generated generated = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(generated.problems()).as("problems").isEmpty();
        final XmlElement sheet = XmlParser.parse(generated.xslt());
        XmlElement choose = null;
        List<String> terms = List.of();
        for (final XmlElement each : sheet.iterElements(XSL, "choose")) {
            final List<XmlElement> branches = each.childElements(XSL, "when");
            if (choose == null && !branches.isEmpty()) {
                final XPathNode tree = XsltReader.parseOrNull(branches.getFirst().get("test"), ReadShapes.PARSER);
                final List<String> found = tree == null
                        ? List.of()
                        : XPathTrees.terms(tree, "and").stream().map(XPathNode::text).toList();
                if (found.size() == when.size()) {
                    choose = each;
                    terms = found;
                }
            }
        }
        assertThat(choose).as("the record template's choice of rule").isNotNull();
        final XmlElement parent = choose.ancestors().getFirst();
        final XmlElement out = XmlElement.create(XSL, "element", "name", "r", "namespace", PARITY);
        out.addElement(XSL, "value-of", "select", "string-join((" + String.join(", ", terms.stream()
                .map(term -> "string(boolean(" + term + "))").toList()) + "), ',')");
        parent.insert(parent.indexOf(choose), out);
        parent.remove(choose);
        final XmlElement events = transform(XmlSerialiser.toXml(sheet), records);
        return events.iterElements(PARITY, "r").stream()
                .map(r -> Stream.of(r.text().split(",", -1)).map(Boolean::parseBoolean).toList())
                .toList();
    }

    static Map<String, Object> equalsTo(final String field, final String value) {
        return Map.of("field", field, "equals", value);
    }

    static Map<String, Object> not(final Map<String, Object> condition) {
        final Map<String, Object> out = new LinkedHashMap<>(condition);
        out.put("not", true);
        return out;
    }
}
