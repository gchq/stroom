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

import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// The generation helpers and mapping models against their fixed results: each case in `helper_cases.json` is a
/// call of a helper (`style_name`, `rooted_path`, `compact_rules`, wrapped comments, ...) and what
/// it must give; each in `model_cases.json` a mapping (or part of one) and what reading it must give: its dumps, or
/// its errors, worded as the agent is shown them. They pin the helpers' and models' established behaviour.
class TestGenerationHelperCases {

    @TestFactory
    Stream<DynamicTest> theHelpersGiveTheRecordedResults() {
        final List<Map<String, Object>> cases = GenerationFixtures.json(GenerationFixtures.resource(
                "helper_cases.json"));
        assertThat(cases).hasSizeGreaterThan(250);
        return IntStream.range(0, cases.size()).mapToObj(n -> {
            final Map<String, Object> c = cases.get(n);
            return DynamicTest.dynamicTest(n + " " + c.get("call") + " " + ToolJson.dumps(c.get("args")), () -> {
                final Object actual = call((String) c.get("call"), list(c.get("args")));
                assertThat(ToolJson.dumps(actual)).isEqualTo(ToolJson.dumps(asThePortMeansIt((String) c.get("call"),
                        list(c.get("args")), c.get("result"))));
            });
        });
    }

    @TestFactory
    Stream<DynamicTest> theModelsReadMappingsAsPydanticDoes() {
        final List<Map<String, Object>> cases = GenerationFixtures.json(GenerationFixtures.resource(
                "model_cases.json"));
        assertThat(cases).hasSizeGreaterThan(80);
        return IntStream.range(0, cases.size()).mapToObj(n -> {
            final Map<String, Object> c = cases.get(n);
            return DynamicTest.dynamicTest(n + " " + c.get("model") + " " + ToolJson.dumps(c.get("input")), () -> {
                final Function<DumpMode, Object> dump;
                try {
                    dump = read((String) c.get("model"), c.get("input"));
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
                    // Entries may join fields too (FieldMapping's join), which the message names.
                    assertThat(ToolJson.dumps(errors, 1)).isEqualTo(ToolJson.dumps(c.get("errors"), 1).replace(
                            "give exactly one of field, any_of, value, xpath or lookup",
                            "give exactly one of field, any_of, join, value, xpath or lookup"));
                    return;
                }
                assertThat(c).doesNotContainKey("errors");
                assertThat(ToolJson.dumps(dump.apply(DumpMode.COMPACT))).isEqualTo(ToolJson.dumps(c.get("compact")));
                assertThat(ToolJson.dumps(dump.apply(DumpMode.WITHOUT_NONE)))
                        .isEqualTo(ToolJson.dumps(c.get("without_none")));
                assertThat(ToolJson.dumps(dump.apply(DumpMode.ALL))).isEqualTo(ToolJson.dumps(c.get("all")));
            });
        });
    }

    // The fixed result, but where the generator now differs on purpose: times finer than a millisecond are cut,
    // not rounded (see XPathText's timeExpression), an epoch time with floor, and a pattern's digits by Stroom's
    // format-date alone, not the XSLT's own mcp:time-ms.
    private static Object asThePortMeansIt(final String call, final List<Object> args, final Object result) {
        if (!"time_expr".equals(call) || !(result instanceof final String said)) {
            return result;
        }
        if (said.startsWith("mcp:time-ms(")) {
            final String zone = (String) args.get(1);
            return "stroom:format-date(" + args.get(2) + ", " + XPathText.literal((String) args.get(0))
                   + (zone == null || zone.isEmpty()
                    ? ""
                    : ", " + XPathText.literal(zone)) + ")";
        }
        return said.replace("xs:integer(round(", "xs:integer(floor(");
    }

    @SuppressWarnings("unchecked")
    private static Function<DumpMode, Object> read(final String model, final Object input) {
        return switch (model) {
            case "TranslationMapping" -> TranslationMapping.parse(input)::toJson;
            case "Condition" -> Condition.fromJson((Map<String, Object>) input)::toJson;
            case "FieldMapping" -> FieldMapping.fromJson((Map<String, Object>) input)::toJson;
            case "EventRule" -> EventRule.fromJson((Map<String, Object>) input)::toJson;
            case "XsltStyle" -> XsltStyle.fromJson((Map<String, Object>) input)::toJson;
            case "Extraction" -> Extraction.fromJson((Map<String, Object>) input)::toJson;
            default -> throw new IllegalArgumentException(model);
        };
    }

    @SuppressWarnings("unchecked")
    private static Object call(final String name, final List<Object> args) {
        return switch (name) {
            case "style_name" -> XsltNames.styleName(text(args, 0), text(args, 1));
            case "data_name_in" -> XsltNames.dataNameIn(text(args, 0), text(args, 1));
            case "unique_name" -> XsltNames.uniqueName(text(args, 0), new HashSet<>((List<String>) args.get(1)),
                    text(args, 2));
            case "rooted_path" -> MappingFacts.rootedPath(text(args, 0));
            case "variable_reads" -> MappingFacts.variableReads(text(args, 0),
                    new HashSet<>((List<String>) args.get(1)));
            case "is_call" -> MappingFacts.isCall(text(args, 0));
            case "function_call" -> MappingFacts.functionCall(text(args, 0));
            case "action_free" -> MappingFacts.actionFree(text(args, 0));
            case "pattern_problem" -> MappingFacts.patternProblem(text(args, 0));
            case "action_home" -> MappingFacts.actionHome((List<String>) args.getFirst());
            case "compact_rules" -> MappingCompaction.compactRules((Map<String, Object>) args.getFirst());
            case "compact_pairs" -> MappingCompaction.compactPairs((Map<String, Object>) args.getFirst());
            case "grouped" -> MappingCompaction.grouped((List<String>) args.getFirst());
            case "pair_of" -> Pairs.pairOf(text(args, 0))
                    .map(found -> (Object) List.of(found.style(), found.key()))
                    .orElse(null);
            case "pair_regex" -> Pairs.pairRegex(text(args, 0), text(args, 1));
            case "key_name" -> Pairs.keyName(text(args, 0));
            case "xml_steps" -> XPathText.xmlSteps(text(args, 0));
            case "comment" -> XsltComments.comment(text(args, 0));
            case "field_text" -> XPathText.fieldText(text(args, 0), text(args, 1));
            case "lookup_text" -> XPathText.lookupText(text(args, 0), text(args, 1), text(args, 2));
            case "time_expr" -> XPathText.timeExpression(text(args, 0), text(args, 1), text(args, 2));
            case "transform_expr" -> XPathText.transformExpression(text(args, 0), text(args, 1));
            case "literal" -> XPathText.literal(text(args, 0));
            case "dictionary_text" -> XPathText.dictionaryText(text(args, 0), text(args, 1));
            case "keys" -> FieldHomes.keys(text(args, 0));
            case "matches" -> FieldHomes.matches(FieldHomes.HOMES.stream()
                    .filter(home -> home.path().equals(text(args, 0)))
                    .findFirst()
                    .orElseThrow()
                    .regex(), text(args, 1));
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static String text(final List<Object> args, final int n) {
        return (String) args.get(n);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(final Object value) {
        return new ArrayList<>((List<Object>) value);
    }
}
