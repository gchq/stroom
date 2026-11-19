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

import stroom.ai.impl.tools.AwkwardValues;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Parity of the conditions between the tools and the generated XSLT: every operator (equals, one_of, matches,
/// starts_with, contains, present, each with not), over every field spelling and value case the design's "Fields
/// and values" names, is evaluated by [LocalCheck#holds] over the sample's records and by the generated XSLT run
/// through Saxon over the same records as Stroom's parser gives them; the two verdicts must agree on every record.
/// A few verdicts are also pinned, so the two can't agree by both being wrong.
class TestConditionParity extends XsltRuns {

    private static final String PARITY = "urn:parity";
    private static final String NONE = "zzz-none";

    @TestFactory
    Stream<DynamicTest> everyConditionHoldsForTheRecordsTheXsltTakes() {
        return cases().stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> assertParity(c)));
    }

    @Test
    void theVerdictsAreThoseTheDesignSays() {
        final Map<String, Case> byName = new LinkedHashMap<>();
        cases().forEach(c -> byName.put(c.name(), c));

        final Case nested = byName.get("json, nested and flat keys");
        // A nested key dotted; a flat key holding a dot spelt in brackets, never misread as the nested one.
        assertThat(verdicts(nested, equalsTo("event.app", "Auth"))).startsWith(true, false);
        assertThat(verdicts(nested, equalsTo("log.level", "nested"))).containsExactly(false, false, true, true,
                false, false, false, false, false, false, false, false, false);
        assertThat(verdicts(nested, equalsTo("['log.level']", "flat"))).containsExactly(false, false, true, true,
                false, false, false, false, false, false, false, false, false);
        // Decoded: JSON's escapes are compared as the characters they stand for.
        assertThat(verdicts(nested, equalsTo("msg", "say \"hi\" \\ C:\\temp é 🚀"))).element(4).isEqualTo(true);
        // An array, as the generated XPath reads it today: the array element's text, its values run together
        // (*[@key='tags'] = 'b' compares 'ab'). The design's contract says any of its values: a contract question,
        // raised (2026-11-19), not changed here; the two agree either way.
        assertThat(verdicts(nested, equalsTo("tags", "ab"))).element(5).isEqualTo(true);
        assertThat(verdicts(nested, equalsTo("tags", "b"))).element(5).isEqualTo(false);
        assertThat(verdicts(nested, not(equalsTo("tags", "b")))).element(5).isEqualTo(true);
        // A missing field makes a not condition hold; a nil value counts as missing.
        assertThat(verdicts(nested, not(equalsTo("msg", "x")))).element(12).isEqualTo(true);
        assertThat(verdicts(nested, present("msg", true))).element(7).isEqualTo(false);

        final Case held = byName.get("json held in a string");
        // Held once and twice; a held value that doesn't parse holds nothing, so a not holds.
        assertThat(verdicts(held, equalsTo("held.logger_name", "com.x.Auth"))).element(0).isEqualTo(true);
        assertThat(verdicts(held, equalsTo("held.logger_name", "com.x.Twice"))).element(1).isEqualTo(true);
        assertThat(verdicts(held, present("held.logger_name", true))).element(2).isEqualTo(false);
        assertThat(verdicts(held, not(startsWith("held.logger_name", "com")))).element(2).isEqualTo(true);

        final Case xml = byName.get("xml input, namespaced");
        // XML by local name in the record's namespace, entities and CDATA decoded, repeated elements any.
        assertThat(verdicts(xml, equalsTo("Text", "a & b"))).element(0).isEqualTo(true);
        assertThat(verdicts(xml, equalsTo("C", "<raw>"))).element(0).isEqualTo(true);
        assertThat(verdicts(xml, equalsTo("Data", "y"))).element(0).isEqualTo(true);
        assertThat(verdicts(xml, equalsTo("Header/Op", "Pref"))).element(2).isEqualTo(true);
    }

    @Test
    void theRecordsAreReadAsProcessingReadsThem() {
        // A held JSON string longer than Stroom's JSONParser keeps is cut before json-to-xml reads it: the held keys
        // past the cut, and the held JSON itself, read differently from the sample read whole.
        final Case held = cases().stream().filter(c -> c.name().equals("json held in a string")).findFirst()
                .orElseThrow();
        final TranslationMapping mapping = mapping(held, List.of(equalsTo("held.logger_name", "com.x.Long")));
        final List<Object> whole = LocalCheck.sampleRecords(mapping, held.sample(), null).records();
        final List<Object> cut = LocalCheck.sampleRecords(mapping, held.sample(), null,
                AwkwardValues.JSON_PARSER_CUT).records();
        final Condition long1 = Condition.fromJson(equalsTo("held.logger_name", "com.x.Long"));
        final int at = held.records() - 1;

        assertThat(LocalCheck.holds(mapping, long1, whole.get(at), null)).isTrue();
        assertThat(LocalCheck.holds(mapping, long1, cut.get(at), null)).isFalse();
        assertThat(xsltVerdicts(mapping, List.of(long1), held.xml()).get(at)).containsExactly(false);
    }

    private static void assertParity(final Case c) {
        final List<Map<String, Object>> conditions = conditions(c);
        final TranslationMapping mapping = mapping(c, conditions);
        final List<Object> records = c.cut() > 0
                ? LocalCheck.sampleRecords(mapping, c.sample(), c.splitter(), c.cut()).records()
                : LocalCheck.sampleRecords(mapping, c.sample(), c.splitter()).records();
        assertThat(records).as("the tools' records").hasSize(c.records());
        final List<Condition> when = mapping.events().getFirst().when();
        final List<List<Boolean>> xslt = xsltVerdicts(mapping, when, c.xml() != null
                ? c.xml()
                : splitterXml(records));
        assertThat(xslt).as("the XSLT's records").hasSize(c.records());

        final List<String> disagreements = new ArrayList<>();
        for (int n = 0; n < records.size(); n++) {
            for (int i = 0; i < when.size(); i++) {
                final Boolean tools = LocalCheck.holds(mapping, when.get(i), records.get(n), null);
                if (!xslt.get(n).get(i).equals(tools)) {
                    disagreements.add("record " + n + ", " + ToolJson.dumps(conditions.get(i)) + ": tools " + tools
                                      + ", XSLT " + xslt.get(n).get(i));
                }
            }
        }
        assertThat(disagreements).as("%s: %s of %s verdicts differ", c.name(), disagreements.size(),
                records.size() * when.size()).isEmpty();
    }

    // The verdicts of the tools for one condition, record by record, after checking the XSLT agrees.
    private static List<Boolean> verdicts(final Case c, final Map<String, Object> condition) {
        final TranslationMapping mapping = mapping(c, List.of(condition));
        final List<Object> records = c.cut() > 0
                ? LocalCheck.sampleRecords(mapping, c.sample(), c.splitter(), c.cut()).records()
                : LocalCheck.sampleRecords(mapping, c.sample(), c.splitter()).records();
        final Condition parsed = mapping.events().getFirst().when().getFirst();
        final List<Boolean> tools = records.stream().map(r -> LocalCheck.holds(mapping, parsed, r, null)).toList();
        final List<Boolean> xslt = xsltVerdicts(mapping, List.of(parsed), c.xml() != null
                ? c.xml()
                : splitterXml(records)).stream().map(List::getFirst).toList();
        assertThat(tools).as("%s by the tools and the XSLT", condition).isEqualTo(xslt);
        return tools;
    }

    // Every operator, with not, on every field of the case, over its values (and parts of them).
    private static List<Map<String, Object>> conditions(final Case c) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final String field : c.fields()) {
            out.add(present(field, true));
            out.add(present(field, false));
            final List<Map<String, Object>> tests = new ArrayList<>();
            final Set<String> texts = new LinkedHashSet<>(c.values());
            texts.add(NONE);
            for (final String text : texts) {
                tests.add(equalsTo(field, text));
                tests.add(Map.of("field", field, "one_of", List.of(NONE, text)));
                tests.add(Map.of("field", field, "matches", "^" + regexQuoted(text) + "$"));
                final int[] points = text.codePoints().toArray();
                tests.add(startsWith(field, new String(points, 0, (points.length + 1) / 2)));
                tests.add(Map.of("field", field, "contains", points.length > 2
                        ? new String(points, 1, points.length - 2)
                        : text));
            }
            tests.add(Map.of("field", field, "matches", "a.b"));
            tests.add(Map.of("field", field, "matches", "\\s"));
            tests.add(Map.of("field", field, "matches", "b$"));
            tests.add(startsWith(field, ""));
            for (final Map<String, Object> test : tests) {
                out.add(test);
                out.add(not(test));
            }
        }
        return out;
    }

    private static TranslationMapping mapping(final Case c, final List<Map<String, Object>> conditions) {
        final Map<String, Object> json = new LinkedHashMap<>(c.mapping());
        json.put("unmatched", "skip");
        json.put("log_unmapped_fields", false);
        json.put("common", List.of(
                Map.of("path", "EventTime/TimeCreated", "value", "2026-11-19T10:00:00.000Z"),
                Map.of("path", "EventSource/System/Name", "value", "Parity"),
                Map.of("path", "EventSource/System/Environment", "value", "Test"),
                Map.of("path", "EventSource/Generator", "value", "Parity"),
                Map.of("path", "EventSource/Device/HostName", "value", "host")));
        final List<Map<String, Object>> fields = List.of(Map.of("path", "EventDetail/TypeId", "value", "T"),
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "x", "value", "x"));
        json.put("events", List.of(
                Map.of("name", "all", "when", conditions, "allow_unknown", "a test", "fields", fields),
                Map.of("name", "rest", "allow_unknown", "a test", "fields", fields)));
        return TranslationMapping.fromJson(json);
    }

    // Each record's verdict on each condition, as the generated XSLT tests it: the record template's choice of rule
    // replaced by an element holding each of its first rule's tests' values.
    private static List<List<Boolean>> xsltVerdicts(final TranslationMapping mapping,
                                                    final List<Condition> when,
                                                    final String records) {
        final XsltGenerator.Generated generated = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(generated.problems()).as("problems").isEmpty();
        final XmlElement sheet = XmlParser.parse(generated.xslt());
        // The record template's choice: the first whose first branch tests as many terms as the rule has conditions.
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

    // Data Splitter records as the splitter writes them, from the records the tools read.
    @SuppressWarnings("unchecked")
    private static String splitterXml(final List<Object> records) {
        final StringBuilder out = new StringBuilder("<records xmlns=\"records:2\">");
        for (final Object record : records) {
            out.append("<record>");
            ((Map<String, Object>) record).forEach((name, value) -> out.append("<data name=\"")
                    .append(AwkwardValues.xml11Text(name)).append("\" value=\"")
                    .append(AwkwardValues.xml11Text(String.valueOf(value))).append("\"/>"));
            out.append("</record>");
        }
        return out.append("</records>").toString();
    }

    private static List<Case> cases() {
        final List<Case> out = new ArrayList<>();
        final List<String> nested = List.of(
                "{\"event\": {\"app\": \"Auth\", \"level\": \"INFO\"}, \"msg\": \"Request: get /a\"}",
                "{\"event\": {\"app\": \"Db\"}, \"msg\": \"Response: ok\"}",
                "{\"log.level\": \"flat\", \"log\": {\"level\": \"nested\"}}",
                "{\"log\": {\"level\": \"nested\"}, \"log.level\": \"flat\"}",
                "{\"msg\": \"say \\\"hi\\\" \\\\ C:\\\\temp \\u00e9 \\ud83d\\ude80\"}",
                "{\"tags\": [\"a\", \"b\"], \"msg\": \"a\\u2028b\"}",
                "{\"msg\": \"  padded  \"}",
                "{\"msg\": \"-\"}",
                "{\"msg\": \"\"}",
                "{\"msg\": 42, \"event\": {\"app\": null}}",
                "{\"msg\": \"a\\rb\\n\", \"event\": {\"app\": {\"deep\": \"x\"}}}",
                "{\"msg\": \"nul\\u0000 and ff\\f\", \"tags\": [\"x\", {\"k\": \"b\"}]}",
                "{}");
        out.add(new Case("json, nested and flat keys", Map.of("input", "json", "json_layout", "lines",
                "nil_values", List.of("-")), String.join("\n", nested), TestSourceTypeReadingParity.stroomRecords(
                nested), null, 0, nested.size(),
                List.of("event.app", "event.level", "log.level", "['log.level']", "msg", "tags", "event"),
                List.of("Auth", "INFO", "flat", "nested", "Request: get /a", "say \"hi\" \\ C:\\temp é 🚀", "a",
                        "b", "  padded  ", "-", "42", "a\u2028b", "a\rb\n", "x", "nul and ff\f")));

        final List<String> held = new ArrayList<>(List.of(
                "{\"held\": \"{\\\"logger_name\\\": \\\"com.x.Auth\\\", \\\"eventType\\\": \\\"Password change\\\"}\"}",
                "{\"held\": \"\\\"{\\\\\\\"logger_name\\\\\\\": \\\\\\\"com.x.Twice\\\\\\\"}\\\"\"}",
                "{\"held\": \"{\\\"logger_name\\\": \\\"com.x.Bad\\\\q\\\"}\"}",
                "{\"held\": \"{\\\"logger_name\\\": \\\"cut\"}",
                "{\"held\": \"not json\"}",
                "{\"held\": \"{\\\"logger_name\\\": \\\"a\\\\u0000b\\\\u000cc\\\"}\"}",
                "{\"held\": \"{\\\"logger_name\\\": \\\"\\\\\\\"q\\\\\\\" \\\\\\\\ \\\\u00e9\\\"}\"}",
                "{\"held\": \"{\\\"inner\\\": {\\\"x\\\": \\\"deep\\\"}, \\\"tags\\\": [\\\"t1\\\", \\\"t2\\\"]}\"}",
                "{\"held\": {\"logger_name\": \"obj\"}}",
                "{\"held\": \"{\\\"logger_name\\\": \\\"com.x.Long\\\", \\\"msg\\\": \\\""
                + "L".repeat(AwkwardValues.JSON_PARSER_CUT + 50) + "\\\"}\"}"));
        out.add(new Case("json held in a string", Map.of("input", "json", "json_layout", "lines", "json_fields",
                List.of("held")), String.join("\n", held), TestSourceTypeReadingParity.stroomRecords(held), null,
                AwkwardValues.JSON_PARSER_CUT, held.size(),
                List.of("held.logger_name", "held.eventType", "held.inner.x", "held.inner", "held.tags", "held"),
                List.of("com.x.Auth", "com.x.Twice", "Password change", "com.x.Bad", "cut", "a\u0000b\fc",
                        "a\ufffdb\ufffdc", "ab\fc", "\"q\" \\ é", "deep", "t1", "obj", "com.x.Long")));

        final List<String> xmlHeld = List.of(
                "{\"payload\": \"<Envelope xmlns=\\\"urn:s\\\"><Body><User id=\\\"u1\\\">alice</User></Body>"
                + "</Envelope>\"}",
                "{\"payload\": \"<s:Envelope xmlns:s=\\\"urn:s\\\"><s:Body><s:User s:id=\\\"u2\\\"> bob </s:User>"
                + "</s:Body></s:Envelope>\"}",
                "{\"payload\": \"<Envelope><Body><User>a &amp; b</User><User>second</User></Body></Envelope>\"}",
                "{\"payload\": \"<Envelope><Body><User><![CDATA[x < y]]></User></Body></Envelope>\"}",
                "{\"payload\": \"<Envelope><Body><User>cut\"}",
                "{\"payload\": \"<Envelope><Body><User>has<b>bold</b>text</User></Body></Envelope>\"}",
                "{\"other\": 1}");
        out.add(new Case("xml held in json", Map.of("input", "json", "json_layout", "lines", "xml_fields",
                List.of("payload")), String.join("\n", xmlHeld), TestSourceTypeReadingParity.stroomRecords(xmlHeld),
                null, 0, xmlHeld.size(),
                List.of("payload/Envelope/Body/User", "payload//User", "payload//User/@id",
                        "payload/Envelope/Body/User/@id"),
                List.of("alice", " bob ", "bob", "a & b", "second", "x < y", "hasboldtext", "u1", "u2")));

        final String xml = "<Events xmlns=\"urn:e\">"
                           + "<Event><Header id=\"1\"><Op>Login</Op></Header><Data Name=\"a\">x</Data>"
                           + "<Data Name=\"b\">y</Data><Text>a &amp; b</Text><C><![CDATA[<raw>]]></C></Event>"
                           + "<Event><Header><Op> Login </Op></Header><Text>  </Text></Event>"
                           + "<Event><e:Header xmlns:e=\"urn:e\"><e:Op>Pref</e:Op></e:Header>"
                           + "<Other xmlns=\"urn:other\"><Op>no</Op></Other></Event>"
                           + "<Event/></Events>";
        out.add(new Case("xml input, namespaced", Map.of("input", "xml", "root", "Events", "record", "Event",
                "xml_namespace", "urn:e"), xml, xml, null, 0, 4,
                List.of("Header/Op", "Header/@id", "Data", "Data[@Name='b']", "Text", "C", "Other/Op"),
                List.of("Login", " Login ", "1", "x", "y", "a & b", "<raw>", "Pref", "no")));

        out.add(new Case("csv, doubled quotes", Map.of("input", "data_splitter"),
                "a,b\n\"say \"\"hi\"\"\",x\nplain,\"with, comma\"\n,\"\"\n",
                null, SplitterSpec.fromMap(Map.of("kind", "delimited", "delimiter", ",", "header", true,
                "quote", "\"")), 0, 3, List.of("a", "b"),
                List.of("say \"hi\"", "say \"\"hi\"\"", "plain", "with, comma", "x")));

        out.add(new Case("key=value, escaped quotes", Map.of("input", "data_splitter"),
                "msg=\"say \\\"hi\\\"\" user=bob\nmsg=plain user=\"two words\"\n",
                null, SplitterSpec.fromMap(Map.of("kind", "key_value", "quote", "\"")), 0, 2, List.of("msg", "user"),
                List.of("say \"hi\"", "say \\\"hi\\\"", "plain", "bob", "two words")));
        return out;
    }

    private static Map<String, Object> equalsTo(final String field, final String value) {
        return Map.of("field", field, "equals", value);
    }

    private static Map<String, Object> startsWith(final String field, final String value) {
        return Map.of("field", field, "starts_with", value);
    }

    private static Map<String, Object> present(final String field, final boolean present) {
        return Map.of("field", field, "present", present);
    }

    private static Map<String, Object> not(final Map<String, Object> condition) {
        final Map<String, Object> out = new LinkedHashMap<>(condition);
        out.put("not", true);
        return out;
    }

    // A text as an XPath regex matching it literally.
    private static String regexQuoted(final String text) {
        final StringBuilder out = new StringBuilder();
        for (final char c : text.toCharArray()) {
            if ("\\.?*+{}()[]|^$-".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }


    // --------------------------------------------------------------------------------


    /// One case of the parity suite.
    ///
    /// @param name     What it is.
    /// @param mapping  The mapping's input keys (input, layout, held fields, nil values).
    /// @param sample   The sample's text, as the tools read it.
    /// @param xml      The records as Stroom's parser gives them to the XSLT, or null for a Data Splitter's (made
    ///                 from the records the tools read).
    /// @param splitter The Data Splitter spec, or null.
    /// @param cut      The JSONParser's stringTruncateLength the records are read with, or 0 for whole.
    /// @param records  How many records it has.
    /// @param fields   The fields its conditions test.
    /// @param values   The texts its conditions test for.
    private record Case(String name,
                        Map<String, Object> mapping,
                        String sample,
                        String xml,
                        SplitterSpec splitter,
                        int cut,
                        int records,
                        List<String> fields,
                        List<String> values) {

    }
}
