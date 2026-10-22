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

import stroom.ai.impl.tools.sample.PyJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// The reference-data XSLT generator (the Stroom MCP server's `utils/refgen.py`): its tests' cases
/// (`tests/test_sources.py`, `test_multi.py`, `test_required_imports.py`), and the port against the server's own:
/// `reference_cases.json` holds reference mappings (the tests', the evaluation cases', the end-to-end suites', and
/// more forms) with what pydantic makes of each and what `generate_reference` gives for it. Made by running the
/// server's code (`make_reference.py`).
class TestReferenceGenerator extends XsltRuns {

    private static final String REF = ReferenceGenerator.REF;
    private static final String SPLIT_RECORDS = "<records xmlns=\"records:2\"><record><data name=\"user\" "
                                                + "value=\"Alice\"/><data name=\"org\" value=\"Ops\"/><data "
                                                + "name=\"name\" value=\"Alice A\"/></record></records>";

    @TestFactory
    Stream<DynamicTest> theReferenceXsltIsThePythonGeneratorsToTheByte() {
        final List<Map<String, Object>> cases = ReadingFixtures.json("reference_cases.json");
        assertThat(cases).hasSizeGreaterThan(25);
        return IntStream.range(0, cases.size()).mapToObj(n -> {
            final Map<String, Object> c = cases.get(n);
            return DynamicTest.dynamicTest(n + " " + c.get("source"), () -> check(c));
        });
    }

    @SuppressWarnings("unchecked")
    private static void check(final Map<String, Object> c) {
        final ReferenceMapping mapping;
        try {
            mapping = ReferenceMapping.parse(c.get("input"));
        } catch (final MappingValidationException e) {
            assertThat(c).containsKey("errors");
            final List<Object> errors = e.errors().stream().map(error -> {
                final Map<String, Object> map = new LinkedHashMap<>();
                map.put("loc", error.loc());
                map.put("type", error.type());
                map.put("msg", error.message());
                map.put("input", error.input());
                return (Object) map;
            }).toList();
            assertThat(PyJson.dumps(errors, 1)).isEqualTo(PyJson.dumps(c.get("errors"), 1));
            return;
        }
        assertThat(c).doesNotContainKey("errors");
        assertThat(PyJson.dumps(mapping.toJson(DumpMode.COMPACT))).isEqualTo(PyJson.dumps(c.get("compact")));
        assertThat(PyJson.dumps(mapping.toJson(DumpMode.ALL))).isEqualTo(PyJson.dumps(c.get("all")));
        ((Map<String, Object>) c.get("results")).forEach((version, result) ->
                assertThat(PyJson.dumps(ReferenceGenerator.generate(mapping, version).toMap(), 1))
                        .isEqualTo(PyJson.dumps(result, 1)));
    }

    @Test
    void referenceXsltWritesMaps() {
        final ReferenceMapping mapping = ReferenceMapping.fromJson(json("""
                {"input": "data_splitter", "maps": [
                 {"name": "USER_TO_ORG", "key": "user", "values": [{"element": "org", "field": "org"},
                                                                   {"element": "name", "field": "name"}]},
                 {"name": "USER_TO_NAME", "key_xpath": "lower-case(data[@name='user']/@value)",
                  "values": [{"field": "name"}]}]}"""));
        final ReferenceGenerator.Generated result = ReferenceGenerator.generate(mapping);
        assertThat(result.ok()).isTrue();
        assertThat(result.maps()).containsExactly("USER_TO_ORG", "USER_TO_NAME");
        final XmlElement out = transform(result.xslt(), SPLIT_RECORDS);
        final List<XmlElement> references = out.childElements(REF, "reference");
        assertThat(references).hasSize(2);
        final XmlElement first = references.getFirst();
        assertThat(first.childElements(REF, "map").getFirst().text()).isEqualTo("USER_TO_ORG");
        assertThat(first.childElements(REF, "key").getFirst().text()).isEqualTo("Alice");
        // One element is allowed in a value: the parts sit inside it, in no namespace, as lookups read them.
        final List<XmlElement> value = first.childElements(REF, "value").getFirst().childElements();
        assertThat(value).hasSize(1);
        assertThat(value.getFirst().tag()).isEqualTo("details");
        assertThat(value.getFirst().childElements().stream().map(XmlElement::tag)).containsExactly("org", "name");
        final XmlElement second = references.get(1);
        assertThat(second.childElements(REF, "key").getFirst().text()).isEqualTo("alice");
        assertThat(second.childElements(REF, "value").getFirst().text()).isEqualTo("Alice A");
    }

    @Test
    void aMapDefinedTwiceIsAProblem() {
        final ReferenceGenerator.Generated twice = ReferenceGenerator.generate(ReferenceMapping.fromJson(json("""
                {"input": "json", "maps": [{"name": "A", "key": "k", "values": [{"field": "v"}]},
                                           {"name": "A", "key": "k", "values": [{"field": "v"}]}]}""")));
        assertThat(twice.ok()).isFalse();
        assertThat(twice.problems()).contains("map 'A' is defined twice");
        assertThat(twice.xslt()).isNull();
    }

    @Test
    void referenceDataXsltsCanLeaveRecordsOut() {
        final ReferenceGenerator.Generated ref = ReferenceGenerator.generate(ReferenceMapping.fromJson(json("""
                {"input": "data_splitter", "maps": [{"name": "USERS", "key": "user", "values": [{"field": "name"}]}],
                 "drop_when": [{"when": [{"field": "status", "equals": "disabled"}],
                                "reason": "disabled accounts"}]}""")));
        assertThat(ref.ok()).as("%s", ref.problems()).isTrue();
        assertThat(ref.xslt()).contains("<xsl:if test=\"not((data[@name='status']/@value = 'disabled'))\">");
        final XmlElement out = transform(ref.xslt(), "<records xmlns=\"records:2\"><record><data name=\"user\" "
                                                     + "value=\"a\"/><data name=\"name\" value=\"A\"/><data "
                                                     + "name=\"status\" value=\"disabled\"/></record><record><data "
                                                     + "name=\"user\" value=\"b\"/><data name=\"name\" value=\"B\"/>"
                                                     + "<data name=\"status\" value=\"active\"/></record></records>");
        assertThat(out.iterElements(REF, "key").stream().map(XmlElement::text)).containsExactly("b");
    }

    @Test
    void referenceXsltsWriteTheirImportsFirst() {
        final ReferenceGenerator.Generated ref = ReferenceGenerator.generate(ReferenceMapping.fromJson(json("""
                {"input": "json", "imports": ["Ref-Common-V1"],
                 "maps": [{"name": "HOSTS", "key": "host", "values": [{"field": "ip"}]}]}""")));
        assertThat(ref.ok()).isTrue();
        assertThat(ref.xslt().split("<xsl:template", 2)[0]).contains("<xsl:import href=\"Ref-Common-V1\"/>");
    }

    @Test
    void aDictionaryConditionIsAProblemNotXslt() {
        final ReferenceGenerator.Generated ref = ReferenceGenerator.generate(ReferenceMapping.fromJson(json("""
                {"input": "data_splitter", "maps": [{"name": "A", "key": "k", "values": [{"field": "v"}]}],
                 "drop_when": [{"when": [{"field": "status", "in_dictionary": "Disabled"}], "reason": "r"}]}""")));
        assertThat(ref.ok()).isFalse();
        assertThat(ref.problems()).containsExactly("drop_when: a reference mapping's conditions take equals, one_of, "
                                                   + "matches or present, not in_dictionary");
    }
}
