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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Stroom MCP server's tests of a mapping's pairs entry (`tests/test_pairs.py`): a text field's key/value pairs,
/// each key a field, written once for many keys. Seen in testing: a Fortigate draft carried its body's 60 keys as
/// 60 extract entries, a regex each, in a mapping of 50 KB the agent had to send whole.
class TestPairs extends GenerationFixtures {

    private static final Map<String, Object> DRAFTS = json(resource("drafts.json"));

    static Stream<Arguments> stylesAndKeys() {
        return Pairs.STYLES.stream().flatMap(style -> Stream.of("srcip", "dst.port", "Source IP", "Item Id")
                .map(key -> Arguments.of(style, key)));
    }

    @ParameterizedTest
    @MethodSource("stylesAndKeys")
    void aPairsRegexIsReadBackAsItsStyleAndKey(final String style, final String key) {
        assertThat(Pairs.pairOf(Pairs.pairRegex(style, key))).contains(new Pairs.StyleAndKey(style, key));
    }

    @Test
    void aRegexOfAnotherShapeIsNoPair() {
        assertThat(Pairs.pairOf("^(\\S+) (\\S+)$")).isEmpty();
        assertThat(Pairs.pairOf(Pairs.pairRegex("equals", "srcip") + "x")).isEmpty();
    }

    // The kv mapping (test_sample_check_speed), without its extract entries, with the overrides.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapping(final Object... overrides) {
        final Map<String, Object> mapping = new LinkedHashMap<>((Map<String, Object>) DRAFTS.get("kv"));
        mapping.put("extract", List.of());
        mapping.putAll(with(overrides));
        return mapping;
    }

    @Test
    void pairsBecomeAnExtractEntryAKey() {
        final TranslationMapping m = TranslationMapping.fromJson(mapping("pairs", json("""
                [{"field": "body", "style": "quoted", "keys": ["type", "user"]},
                 {"field": "body", "style": "equals", "keys": ["srcip", "Source IP"], "names": {"srcip": "source"}}]
                """)));
        assertThat(m.pairs()).isEmpty();
        assertThat(m.extract()).extracting(e -> List.of(e.field(), e.regex(), e.names())).containsExactly(
                List.of("body", Pairs.pairRegex("quoted", "type"), List.of("type")),
                List.of("body", Pairs.pairRegex("quoted", "user"), List.of("user")),
                List.of("body", Pairs.pairRegex("equals", "srcip"), List.of("source")),
                List.of("body", Pairs.pairRegex("equals", "Source IP"), List.of("source_ip")));
    }

    @Test
    void pairsInATextAnExtractionGivesAreReadAfterIt() {
        final TranslationMapping m = TranslationMapping.fromJson(mapping("extract", json("""
                [{"field": "msg", "regex": "^(\\\\S+) (.*)$", "names": ["host", "rest"]}]"""), "pairs", json("""
                [{"field": "rest", "style": "equals", "keys": ["user"]},
                 {"field": "body", "style": "equals", "keys": ["a"]}]""")));
        assertThat(m.extract()).extracting(Extraction::names)
                .containsExactly(List.of("a"), List.of("host", "rest"), List.of("user"));
    }

    @Test
    void namesForKeysItLacksAreRefused() {
        assertThatThrownBy(() -> TranslationMapping.fromJson(mapping("pairs", json("""
                [{"field": "body", "style": "equals", "keys": ["a"], "names": {"other": "x"}}]"""))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("pairs names ['other'], which aren't among its keys");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theDraftFoldsItsKeyValueExtractsIntoPairsAndMeansTheSame() {
        final Map<String, Object> drafted = MappingCompaction.compactRules((Map<String, Object>) DRAFTS.get(
                "fortigate"));
        assertThat(PyJson.dumps(drafted)).isEqualTo(PyJson.dumps(DRAFTS.get("fortigate_compact_rules")));
        final Map<String, Object> folded = MappingCompaction.compactPairs(drafted);
        assertThat(folded).doesNotContainKey("extract");
        final List<Map<String, Object>> pairs = (List<Map<String, Object>>) folded.get("pairs");
        assertThat(pairs.stream().map(p -> p.get("style")).collect(Collectors.toSet()))
                .isEqualTo(Set.of("quoted", "equals"));
        final List<String> keys = new ArrayList<>();
        pairs.forEach(p -> ((List<String>) p.get("keys")).forEach(keys::add));
        final List<String> expected = ((List<Map<String, Object>>) drafted.get("extract")).stream()
                .map(e -> Pairs.keyName(Pairs.pairOf((String) e.get("regex")).orElseThrow().key()))
                .sorted()
                .toList();
        assertThat(keys.stream().sorted().toList()).isEqualTo(expected);
        assertThat(PyJson.dumps(pairs).length()).isLessThan(PyJson.dumps(drafted.get("extract")).length() / 3);
        assertThat(entries(folded)).isEqualTo(entries(drafted));
    }

    private static List<String> entries(final Map<String, Object> mapping) {
        return TranslationMapping.fromJson(mapping).extract().stream()
                .map(e -> PyJson.dumpsCompact(e.toJson(DumpMode.ALL)))
                .sorted()
                .toList();
    }

    @Test
    void extractEntriesOfOtherShapesStayAsWritten() {
        final Map<String, Object> own = json("{\"field\": \"msg\", \"regex\": \"^(\\\\S+) (.*)$\", \"names\": "
                                             + "[\"host\", \"rest\"]}");
        final Map<String, Object> readsDerived = Map.of("field", "rest", "regex", Pairs.pairRegex("equals", "user"),
                "names", List.of("user"));
        final Map<String, Object> flagged = Map.of("field", "body", "regex", Pairs.pairRegex("equals", "a"), "names",
                List.of("a"), "flags", "i");
        final Map<String, Object> paired = Map.of("field", "body", "regex", Pairs.pairRegex("equals", "b"), "names",
                List.of("bee"));
        final Map<String, Object> folded = MappingCompaction.compactPairs(Map.of("extract", List.of(own, readsDerived,
                flagged, paired)));
        assertThat(folded.get("extract")).isEqualTo(List.of(own, readsDerived, flagged));
        assertThat(folded.get("pairs")).isEqualTo(List.of(Map.of("field", "body", "style", "equals", "keys",
                List.of("b"), "names", Map.of("b", "bee"))));
    }

    @Test
    void theXsltFromPairsIsTheXsltFromTheirExtractEntries() {
        final TranslationMapping withPairs = TranslationMapping.fromJson(mapping("pairs", json("""
                [{"field": "body", "style": "quoted", "keys": ["user"]}]""")));
        final TranslationMapping asExtract = TranslationMapping.fromJson(mapping("extract", List.of(Map.of(
                "field", "body", "regex", Pairs.pairRegex("quoted", "user"), "names", List.of("user")))));
        final XsltGenerator.Generated a = XsltGenerator.generate(withPairs, SCHEMA, "4.1.0");
        final XsltGenerator.Generated b = XsltGenerator.generate(asExtract, SCHEMA, "4.1.0");
        assertThat(a.ok()).as(String.valueOf(a.problems())).isTrue();
        assertThat(a.xslt()).isEqualTo(b.xslt());
    }
}
