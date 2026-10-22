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

import stroom.ai.impl.tools.validation.XsltVersionHistory;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Reading a generated XSLT back into a mapping (the Stroom MCP server's `tests/test_rebuild.py`, its reader's
/// parts): the mapping lost, or out of step with an XSLT changed by hand. Asked for by the user, with test coverage
/// of hand edits that add a field or stop writing one. Each XSLT is run with plain Saxon, as the server's tests run
/// it with SaxonC; the cases (`rebuild_test_cases.json`) are the server's tests' own, written out by
/// `make_rebuild_tests.py`.
class TestXsltReader extends XsltRuns {

    // Plain Saxon has no Stroom functions: format-date stubbed alike in both XSLTs, so they compare.
    private static final String STUBS = Stream.of(1, 2, 3).map(k -> {
        final StringBuilder params = new StringBuilder();
        final List<String> reads = new ArrayList<>();
        for (int n = 0; n < k; n++) {
            params.append("<xsl:param name=\"p").append(n).append("\"/>");
            reads.add("$p" + n);
        }
        return "<xsl:function name=\"stroom:format-date\">" + params + "<xsl:sequence select=\"concat('D:', "
               + String.join(", ", reads) + ")\"/></xsl:function>";
    }).collect(Collectors.joining());
    // Reference data and dictionaries stubbed alike in both XSLTs: a lookup finds a document of the map and key, a
    // dictionary is key=value lines.
    private static final String REFERENCE_STUBS = "<xsl:function name=\"stroom:lookup\"><xsl:param name=\"map\"/>"
                                                  + "<xsl:param name=\"key\"/><xsl:document><details xmlns=\"\"><org>"
                                                  + "{$map}:{$key}</org><phone>p-{$key}</phone></details>"
                                                  + "</xsl:document></xsl:function><xsl:function "
                                                  + "name=\"stroom:dictionary\"><xsl:param name=\"name\"/>"
                                                  + "<xsl:sequence select=\"concat($name, '&#10;CORP\\alice = Alice "
                                                  + "A&#10;bob@corp.example=Bob B&#10;WS01 = Workstation 1&#10;"
                                                  + "ws02=Workstation 2')\"/></xsl:function>";
    private static final String SEEN = "<xsl:template name=\"seen\"><xsl:param name=\"value\"/><xsl:param "
                                       + "name=\"via\" select=\"'vpn'\"/><xsl:if test=\"normalize-space($value)\">"
                                       + "<Data Name=\"host_seen\" Value=\"{$value}\"/></xsl:if><Data Name=\"via\" "
                                       + "Value=\"{$via}\"/></xsl:template>";
    private static final String LOCAL = "<xsl:function name=\"local:shout\" as=\"xs:string\"><xsl:param "
                                        + "name=\"v\"/>\n  <xsl:sequence select=\"upper-case(string($v[1]))\"/>"
                                        + "</xsl:function>";
    private static final Map<String, Object> FIXTURES = ReadingFixtures.json("rebuild_test_cases.json");

    @TestFactory
    Stream<DynamicTest> aLostMappingIsReadBackFromTheXsltAndWritesTheSameEvents() {
        final Map<String, Map<String, Object>> cases = map(FIXTURES.get("cases"));
        assertThat(cases).hasSize(12);
        return cases.entrySet().stream().map(c -> DynamicTest.dynamicTest(c.getKey(), () -> {
            final TranslationMapping m = TranslationMapping.parse(c.getValue().get("mapping"));
            final String records = (String) c.getValue().get("records");
            final String code = codeOf(m);
            final RebuiltMapping rebuilt = XsltReader.rebuild(code, null, null);
            // Every element read as an idiom.
            assertThat(rebuilt.problems()).as("%s", rebuilt.summary()).isEmpty();
            assertThat(rebuilt.raw()).as("%s", rebuilt.summary()).isEmpty();
            assertThat(events(codeOf(rebuilt.mapping()), records)).isEqualTo(events(code, records));
            // Each entry read as a field is the one written: no transform added, none lost (a lost mapping may
            // order them differently, and a rule whose condition fixes the field's value writes the value it must
            // have, read as such).
            final Map<List<String>, Map<String, Object>> written = new HashMap<>();
            for (final FieldMapping f : m.allEntries()) {
                if (f.field() != null && !f.field().isEmpty() && (f.dataName() == null || f.dataName().isEmpty())) {
                    final Map<String, Object> json = new LinkedHashMap<>(f.toJson(DumpMode.COMPACT));
                    json.remove("scope");
                    written.put(key(f), json);
                }
            }
            final Set<String> extracted = m.extract().stream().flatMap(e -> e.names().stream())
                    .collect(Collectors.toSet());
            for (final FieldMapping f : rebuilt.mapping().allEntries()) {
                final Map<String, Object> expected = written.get(key(f));
                if (expected != null && f.field() != null && !f.field().isEmpty()) {
                    // An extraction's name is the mapping's own, lost with it: read back named after its source.
                    final Set<String> drop = new HashSet<>(extracted.contains(expected.get("field"))
                            ? Set.of("scope", "field")
                            : Set.of("scope"));
                    if ("EventDetail/TypeId".equals(f.path())) {
                        // The generator's fallback (the rule's name), read back as the default it is.
                        drop.add("default");
                    }
                    assertThat(without(f.toJson(DumpMode.COMPACT), drop)).isEqualTo(without(expected, drop));
                }
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> withTheKeptMappingEveryUnchangedEntryIsKeptAndTheXsltRegeneratesIdentically() {
        final Map<String, Map<String, Object>> cases = map(FIXTURES.get("cases"));
        return cases.entrySet().stream().map(c -> DynamicTest.dynamicTest(c.getKey(), () -> {
            final TranslationMapping m = TranslationMapping.parse(c.getValue().get("mapping"));
            final String code = codeOf(m);
            final RebuiltMapping rebuilt = XsltReader.rebuild(code, m, code);
            assertThat(rebuilt.added()).isEmpty();
            assertThat(rebuilt.removed()).isEmpty();
            assertThat(rebuilt.reused()).isPositive();
            assertThat(normalise(codeOf(rebuilt.mapping()))).isEqualTo(normalise(code));
        }));
    }

    @Test
    void whatTheXsltIsReadAs() {
        final String code = codeOf(mapping());
        final XsltReading reading = XsltReader.read(code);
        assertThat(reading.rules()).hasSize(2);
        final RuleReading logon = reading.rules().get(0);
        final RuleReading other = reading.rules().get(1);
        assertThat(List.of(logon.name(), other.name())).containsExactly("logon", "other");
        assertThat(XsltReader.canonical(logon.test())).isEqualTo("data[@name='action']/@value = 'login'");
        final Map<List<String>, LeafReading> leaves = new HashMap<>();
        logon.leaves().forEach(leaf -> leaves.put(leaf.key(), leaf));
        // Through the shared template.
        assertThat(leaves.get(Arrays.asList("EventSource/System/Name", null)).value())
                .isEqualTo("Acme VPN");
        assertThat(XsltReader.canonical(leaves.get(List.of("EventDetail/Authenticate/Data", "session")).expr()))
                .isEqualTo("data[@name='sid']/@value");
        assertThat(other.test()).isNull();
        assertThat(reading.namespace()).isEqualTo("records:2");
    }

    @TestFactory
    Stream<DynamicTest> aHandEditIsCarriedIntoTheMappingAndTheRegeneratedXsltWritesWhatTheEditDid() {
        final Map<String, HandEdit> edits = new LinkedHashMap<>();
        // A new field: a Data element written when its value is there.
        edits.put("a Data element added", new HandEdit(code -> once(code, "</Authenticate>",
                "<xsl:if test=\"normalize-space(data[@name='host']/@value)\"><Data Name=\"host_seen\" "
                + "Value=\"{data[@name='host']/@value}\"/></xsl:if></Authenticate>"),
                List.of("rule logon: EventDetail/Authenticate/Data Data host_seen"), List.of()));
        // An element no longer written: the user in EventSource, for every rule.
        edits.put("an element removed", new HandEdit(code -> {
            final int from = code.indexOf("<xsl:if test=\"normalize-space(data[@name='user']/@value)\">\n        "
                                          + "<User>");
            final int to = code.indexOf("</User>", code.indexOf("<EventSource>")) + "</User>\n      </xsl:if>".length();
            return once(code, code.substring(from, to), "");
        }, List.of(), List.of("rule logon: EventSource/User/Id", "rule other: EventSource/User/Id")));
        edits.put("a constant changed", new HandEdit(code -> once(code, "<Action>Logon</Action>",
                "<Action>Logoff</Action>"), List.of("rule logon: EventDetail/Authenticate/Action"), List.of()));
        // A named template of the XSLT's own, called with a parameter (select, or text) and one left at its
        // default.
        edits.put("a template called with parameters", new HandEdit(code -> once(code, "</Authenticate>",
                "<xsl:call-template name=\"seen\"><xsl:with-param name=\"value\" select=\"data[@name='host']/@value\"/>"
                + "</xsl:call-template></Authenticate>").replace("</xsl:stylesheet>", SEEN + "</xsl:stylesheet>"),
                List.of("rule logon: EventDetail/Authenticate/Data Data host_seen",
                        "rule logon: EventDetail/Authenticate/Data Data via"), List.of()));
        edits.put("a template called with a text parameter", new HandEdit(code -> once(code, "</Authenticate>",
                "<xsl:call-template name=\"seen\"><xsl:with-param name=\"value\">ws01</xsl:with-param>"
                + "</xsl:call-template></Authenticate>").replace("</xsl:stylesheet>", SEEN + "</xsl:stylesheet>"),
                List.of("rule logon: EventDetail/Authenticate/Data Data host_seen",
                        "rule logon: EventDetail/Authenticate/Data Data via"), List.of()));
        edits.put("a condition changed", new HandEdit(code -> once(code, "data[@name='action']/@value = 'login'",
                "data[@name='action']/@value = ('login', 'keepalive')"), List.of(), List.of()));
        return edits.entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), () -> handEdit(e.getKey(),
                e.getValue())));
    }

    private static void handEdit(final String name, final HandEdit edit) {
        final TranslationMapping m = mapping();
        final String code = codeOf(m);
        final String edited = edit.change().apply(code);
        assertThat(edited).isNotEqualTo(code);
        final RebuiltMapping rebuilt = XsltReader.rebuild(edited, m, code);
        assertThat(rebuilt.added()).isEqualTo(edit.added());
        assertThat(rebuilt.removed()).isEqualTo(edit.removed());
        assertThat(rebuilt.problems()).isEmpty();
        final String regenerated = codeOf(rebuilt.mapping());
        assertThat(events(regenerated, RECORDS)).isEqualTo(events(edited, RECORDS));
        if (name.startsWith("a template called")) {
            final Map<String, Map<String, Object>> data = new HashMap<>();
            for (final FieldMapping f : rebuilt.mapping().events().getFirst().fields()) {
                if (f.dataName() != null && !f.dataName().isEmpty()) {
                    data.put(f.dataName(), f.toJson(DumpMode.COMPACT));
                }
            }
            // The parameter's default.
            assertThat(data.get("via")).containsEntry("value", "vpn");
            assertThat(data.get("host_seen")).isEqualTo(name.endsWith("parameters")
                    ? Map.of("path", "EventDetail/Authenticate/Data", "data_name", "host_seen", "field", "host")
                    : Map.of("path", "EventDetail/Authenticate/Data", "data_name", "host_seen", "value", "ws01"));
        }
        if ("a condition changed".equals(name)) {
            // Read back as the idiom.
            assertThat(rebuilt.mapping().events().getFirst().when().getFirst().oneOf())
                    .containsExactly("login", "keepalive");
            assertThat(events(edited, RECORDS)).hasSize(2);
            assertThat(events(edited, RECORDS).get(1)).contains("Logon");
        }
        // And it is now in step: read against itself, nothing is new.
        final RebuiltMapping again = XsltReader.rebuild(regenerated, rebuilt.mapping(), regenerated);
        assertThat(again.added()).isEmpty();
        assertThat(again.removed()).isEmpty();
    }

    @Test
    void anEditAMappingCannotExpressShowsInTheEvents() {
        // A Data element written even when its value is empty: the mapping writes Data only with a value, so the
        // regenerated XSLT differs on the record without a host. rebuild_mapping's proof in Stroom finds exactly
        // this and saves nothing unless the user accepts it.
        final TranslationMapping m = mapping();
        final String code = codeOf(m);
        final String edited = once(code, "</Authenticate>", "<Data Name=\"host_seen\" "
                                                            + "Value=\"{data[@name='host']/@value}\"/></Authenticate>");
        final RebuiltMapping rebuilt = XsltReader.rebuild(edited, m, code);
        assertThat(rebuilt.added()).containsExactly("rule logon: EventDetail/Authenticate/Data Data host_seen");
        final List<String> hand = events(edited, RECORDS);
        final List<String> mapped = events(codeOf(rebuilt.mapping()), RECORDS);
        assertThat(hand).isNotEqualTo(mapped);
        assertThat(hand.getFirst()).contains("Name=\"host_seen\" Value=\"\"");
        assertThat(mapped.getFirst()).doesNotContain("host_seen");
    }

    @TestFactory
    Stream<DynamicTest> referenceDataLookupsAndDictionariesAreReadBackAsTheEntriesThatWroteThem() {
        // Each form the generator writes: read back as the entry that wrote it.
        final Map<String, Map<String, Object>> lookups = map(FIXTURES.get("lookups"));
        final Map<String, Object> sources = map(FIXTURES.get("sources_mapping"));
        final String records = (String) FIXTURES.get("sources_records");
        return lookups.entrySet().stream().map(form -> DynamicTest.dynamicTest(form.getKey(), () -> {
            final Map<String, Object> entry = form.getValue();
            final List<Map<String, Object>> plain = new ArrayList<>();
            for (final Map<String, Object> common : TestXsltReader.<Map<String, Object>>list(sources.get("common"))) {
                if (!common.containsKey("lookup") && !common.containsKey("dictionary")) {
                    plain.add(common);
                }
            }
            plain.add(entry);
            final Map<String, Object> json = new LinkedHashMap<>(sources);
            json.put("common", plain);
            final TranslationMapping m = TranslationMapping.parse(json);
            final String code = codeOf(m);
            final RebuiltMapping rebuilt = XsltReader.rebuild(code, null, null);
            assertThat(rebuilt.problems()).as("%s", rebuilt.summary()).isEmpty();
            assertThat(rebuilt.raw()).as("%s", rebuilt.summary()).isEmpty();
            // Data is read into each rule (a lost mapping keeps it there), anything else into common.
            final List<Map<String, Object>> found = rebuilt.mapping().allEntries().stream()
                    .filter(e -> e.path().equals(entry.get("path"))
                                 && Objects.equals(e.dataName(), entry.get("data_name")))
                    .map(e -> e.toJson(DumpMode.COMPACT))
                    .toList();
            assertThat(found).isNotEmpty().allSatisfy(f -> assertThat(f).isEqualTo(entry));
            assertThat(events(codeOf(rebuilt.mapping()), records)).isEqualTo(events(code, records));
        }));
    }

    @TestFactory
    Stream<DynamicTest> importsAreReadBackFromTheXsltAndTheXsltsItImports() {
        final Map<String, Map<String, Object>> imports = map(FIXTURES.get("imports"));
        return imports.entrySet().stream().map(c -> DynamicTest.dynamicTest(c.getKey(), () -> {
            final TranslationMapping m = TranslationMapping.parse(c.getValue().get("mapping"));
            final Map<String, String> imported = map(c.getValue().get("imported"));
            final String code = codeOf(m);
            final RebuiltMapping rebuilt = XsltReader.rebuild(code, null, null, imported);
            assertThat(rebuilt.problems()).as("%s", rebuilt.summary()).isEmpty();
            assertThat(rebuilt.raw()).as("%s", rebuilt.summary()).isEmpty();
            // A call of an imported function is an xpath entry in the mapping too: told apart from what wasn't read.
            assertThat(rebuilt.importedCalls()).isEqualTo(m.functions().isEmpty()
                    ? List.of()
                    : List.of("rule logon: EventDetail/Authenticate/Data Data loud"));
            assertThat(rebuilt.mapping().shared().stream().map(s -> s.toJson(DumpMode.COMPACT)).toList())
                    .isEqualTo(m.shared().stream().map(s -> s.toJson(DumpMode.COMPACT)).toList());
            assertThat(rebuilt.mapping().functions()).isEqualTo(m.functions());
            assertThat(normalise(codeOf(rebuilt.mapping()))).isEqualTo(normalise(code));
            assertThat(importedEvents(codeOf(rebuilt.mapping()), imported)).isEqualTo(importedEvents(code, imported));
            // With the mapping kept, its imports are kept as they are.
            final RebuiltMapping kept = XsltReader.rebuild(code, m, code);
            assertThat(kept.mapping().shared()).isEqualTo(m.shared());
            assertThat(kept.mapping().functions()).isEqualTo(m.functions());
        }));
    }

    @Test
    void aSharedTemplateWhoseXsltCannotBeReadIsAProblemNotAGuess() {
        // Which element it writes is only in the imported XSLT: without it the mapping can't say where the template
        // goes.
        final Map<String, Map<String, Object>> imports = map(FIXTURES.get("imports"));
        final TranslationMapping m = TranslationMapping.parse(imports.get("a shared template").get("mapping"));
        final RebuiltMapping rebuilt = XsltReader.rebuild(codeOf(m), null, null, Map.of());
        assertThat(rebuilt.problems()).anyMatch(p -> p.contains("eventSourceDevice") && p.contains("couldn't be read"));
    }

    @Test
    void functionsTheXsltDefinesByHandAreItsOwnAndAnImportedPrefixCallsImportedFunctions() {
        // From test_xpathtree.py. A hand edit adding local:shout(), defined in the XSLT: its own, read through (not an
        // imported XSLT's prefix, so no functions entry for it). An imported prefix (geo:) is reported as calling an
        // imported function.
        final String code = codeOf(mapping());
        final String edited = once(once(code, "<xsl:stylesheet ", "<xsl:stylesheet xmlns:local=\"urn:local\" ")
                .replace("</xsl:stylesheet>", LOCAL + "</xsl:stylesheet>"), "<Action>Logon</Action>",
                "<Action><xsl:value-of select=\"local:shout('logon')\"/></Action>");
        final RebuiltMapping rebuilt = XsltReader.rebuild(edited, null, null);
        assertThat(rebuilt.problems()).isEmpty();
        assertThat(rebuilt.mapping().functions()).isEmpty();
        final FieldMapping action = rebuilt.mapping().events().getFirst().fields().stream()
                .filter(f -> f.path().endsWith("Authenticate/Action"))
                .findFirst()
                .orElseThrow();
        // The function read through to its body.
        assertThat(action.xpath()).contains("upper-case");
        final TranslationMapping geo = mapping(with("events", List.of(rule("logon", List.of(), plus(LOGON,
                        json("{\"path\": \"EventDetail/Authenticate/Data\", \"data_name\": \"city\", "
                             + "\"xpath\": \"geo:city(data[@name='host']/@value)\"}")))),
                "functions", json("[{\"href\": \"Geo Functions\", \"prefix\": \"geo\", \"namespace\": \"urn:geo\"}]")));
        final XsltGenerator.Generated result = XsltGenerator.generate(geo, SCHEMA, "4.1.0");
        assertThat(result.ok()).as("%s", result.problems()).isTrue();
        final RebuiltMapping read = XsltReader.rebuild(result.xslt(), null, null, Map.of());
        assertThat(read.raw()).isEmpty();
        assertThat(read.importedCalls()).containsExactly("rule logon: EventDetail/Authenticate/Data Data city");
        assertThat(read.mapping().functions().stream().map(SharedFunctions::prefix)).containsExactly("geo");
    }

    @Test
    void aTranslationReadBackKeepsItsTimeFormats() {
        // From test_times_and_meta.py. The reader takes generated XSLTs back to their mapping: the rounded forms,
        // and epoch_s as it was written before.
        assertThat(XPathTrees.unify(ReadShapes.SHAPES.get("epoch_s, truncated"), ReadShapes.shape(
                "stroom:format-date(string(xs:integer(xs:decimal(data[@name='ts']/@value[1]) * 1000)))"))).isPresent();
        assertThat(XPathTrees.unify(ReadShapes.SHAPES.get("time, ms"), ReadShapes.shape(
                "mcp:time-ms(string(data[@name='ts']/@value[1]), 'yyyy SSSSSS', 'UTC')"))).isPresent();
    }

    @Test
    void anXsltWithNoStartTemplateHasNothingToRead() {
        final String code = "<xsl:stylesheet xmlns:xsl=\"" + MappingFacts.XSL + "\" version=\"3.0\"/>";
        assertThatThrownBy(() -> XsltReader.read(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nothing to read");
        final String noRecords = "<xsl:stylesheet xmlns:xsl=\"" + MappingFacts.XSL + "\" version=\"3.0\">"
                                 + "<xsl:template match=\"/\"/></xsl:stylesheet>";
        assertThat(XsltReader.read(noRecords).notes()).containsExactly("no record template: nothing to read");
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static String codeOf(final TranslationMapping m) {
        final XsltGenerator.Generated result = XsltGenerator.generate(m, SCHEMA, "4.1.0");
        assertThat(result.ok()).as("%s", result.problems()).isTrue();
        return result.xslt();
    }

    private static List<String> events(final String xslt, final String records) {
        String code = xslt;
        if (code.contains("stroom:format-date")) {
            code = code.replace("</xsl:stylesheet>", STUBS + "</xsl:stylesheet>");
        }
        if (code.contains("stroom:lookup(") || code.contains("stroom:dictionary(")) {
            code = code.replace("</xsl:stylesheet>", REFERENCE_STUBS + "</xsl:stylesheet>");
        }
        return children(transform(code, records), "Event").stream().map(TestXsltReader::canonical).toList();
    }

    private static List<String> importedEvents(final String code, final Map<String, String> imported) {
        return children(transform(code, RECORDS, imported), "Event").stream().map(TestXsltReader::canonical)
                .toList();
    }

    // An element as c14n compares it: its name, its attributes in order of name, its text and children.
    private static String canonical(final XmlElement element) {
        final StringBuilder out = new StringBuilder("<").append(element.tag());
        element.attributes().stream()
                .sorted(Comparator.comparing(XmlElement.Attribute::key))
                .forEach(a -> out.append(' ').append(a.key()).append("=\"").append(a.value()).append('"'));
        out.append('>');
        if (element.text() != null) {
            out.append(element.text());
        }
        for (final XmlNode child : element.children()) {
            if (child instanceof final XmlElement inner) {
                out.append(canonical(inner));
            }
            if (child.tail() != null) {
                out.append(child.tail());
            }
        }
        return out.append("</").append(element.tag()).append('>').toString();
    }

    // utils/mappingstore.normalise_xslt: the XSLT without what isn't an edit (the declaration, space between tags).
    private static String normalise(final String text) {
        final String stripped = XsltVersionHistory.strip(text).replaceFirst("^\\s*<\\?xml[^>]*\\?>", "");
        return stripped.replaceAll(">\\s+<", "><").strip();
    }

    private static String once(final String text, final String old, final String replacement) {
        final int at = text.indexOf(old);
        return at < 0
                ? text
                : text.substring(0, at) + replacement + text.substring(at + old.length());
    }

    private static List<String> key(final FieldMapping f) {
        return Arrays.asList(f.path(), f.dataName());
    }

    private static Map<String, Object> without(final Map<String, Object> json, final Set<String> drop) {
        final Map<String, Object> out = new LinkedHashMap<>(json);
        drop.forEach(out::remove);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(final Object value) {
        return (List<T>) value;
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> map(final Object value) {
        return (Map<String, T>) value;
    }


    // --------------------------------------------------------------------------------


    private record HandEdit(UnaryOperator<String> change, List<String> added, List<String> removed) {

    }
}
