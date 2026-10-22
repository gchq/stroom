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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.sample.PyJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// The ported hand-edit checks ([HandEdits]), XSLT styling ([StyledXslt]), code diff ([CodeDiff]) and the write side
/// of the kept mapping ([KeptMapping]) against the Stroom MCP server's own `utils/handedit.py`,
/// `utils/xsltstyle.py` and `utils/mappingstore.py`: each case in `store_cases.json` is a call and what the server's
/// code gave (made by `make_parity2.py`), over XSLTs its indexing and translation generators wrote and the hand
/// edits its tests make.
class TestStoreParity {

    @TestFactory
    @SuppressWarnings("unchecked")
    Stream<DynamicTest> theStoreAndHandEditHelpersGiveWhatThePythonOnesGive() {
        final List<Map<String, Object>> cases = GenerationFixtures.json(GenerationFixtures.resource(
                "store_cases.json"));
        final Map<String, String> xslts = (Map<String, String>) cases.getFirst().get("result");
        assertThat(cases).hasSizeGreaterThan(70);
        return IntStream.range(1, cases.size()).mapToObj(n -> {
            final Map<String, Object> c = cases.get(n);
            final String title = n + " " + c.get("call") + " " + PyJson.dumps(c.get("args"));
            return DynamicTest.dynamicTest(title.substring(0, Math.min(200, title.length())), () -> {
                final Object actual = call((String) c.get("call"), (List<Object>) c.get("args"), xslts);
                assertThat(PyJson.dumps(actual, 1)).isEqualTo(PyJson.dumps(c.get("result"), 1));
            });
        });
    }

    @SuppressWarnings("unchecked")
    private static Object call(final String name, final List<Object> args, final Map<String, String> xslts) {
        return switch (name) {
            case "outline" -> outline(HandEdits.outline(xslts.get((String) args.getFirst())));
            case "undone" -> HandEdits.undone(code(xslts, args.get(0)), xslts.get((String) args.get(1)),
                    xslts.get((String) args.get(2))).stream().map(TestStoreParity::undone).toList();
            case "code_diff" -> CodeDiff.diff(xslts.get((String) args.get(0)), xslts.get((String) args.get(1)));
            case "code_diff_limit" -> CodeDiff.diff(xslts.get((String) args.get(0)), xslts.get((String) args.get(1)),
                    ((Number) args.get(2)).intValue());
            case "collisions" -> {
                final List<String> codes = (List<String>) args.get(3);
                final List<HandEdits.Undone> items = HandEdits.undone(xslts.get(codes.get(0)), xslts.get(codes.get(1)),
                        xslts.get(codes.get(2)));
                yield collisions(HandEdits.collisions((String) args.get(0), (Map<String, Object>) args.get(1),
                        (Map<String, Object>) args.get(2), items));
            }
            case "collisions_cef" -> collisions(HandEdits.collisions("cef", (Map<String, Object>) args.get(0),
                    (Map<String, Object>) args.get(1), HandEdits.undone(null, "<xsl:stylesheet xmlns:xsl=\"http://www"
                                                                              + ".w3.org/1999/XSL/Transform\"><string "
                                                                              + "key=\"suser\"><xsl:value-of select="
                                                                              + "\"EventSource/User/Mail\"/></string>"
                                                                              + "</xsl:stylesheet>", "<x/>")));
            case "plan_fields" -> {
                final Map<String, Object> out = new LinkedHashMap<>();
                HandEdits.planFields((String) args.get(0), (Map<String, Object>) args.get(1)).forEach((label, field) ->
                        out.put(label, List.of(field.spec(), field.said(), sorted(field.names()),
                                sorted(field.sources()))));
                yield out;
            }
            case "expression" -> HandEdits.expression((String) args.getFirst());
            case "style_round_trip" -> StyledXslt.serialise(StyledXslt.parse(xslts.get((String) args.getFirst())));
            case "style_variables" -> {
                final XmlElement sheet = StyledXslt.parse(xslts.get((String) args.get(0)));
                final XmlElement template = sheet.childElements().getFirst();
                final Set<String> taken = new HashSet<>((List<String>) args.get(2));
                final Map<String, String> reads = new LinkedHashMap<>();
                reads.put("p/q", "p-q");
                reads.put("x", "ex");
                reads.put("a", "the a");
                reads.put("b", "b");
                StyledXslt.variables(template, reads, XsltStyle.fromJson((Map<String, Object>) args.get(1)), taken);
                yield List.of(StyledXslt.serialise(sheet), sorted(taken));
            }
            case "part_name" -> {
                final Set<String> taken = new HashSet<>((List<String>) args.get(2));
                yield List.of(StyledXslt.partName((String) args.get(0), XsltStyle.DEFAULT, taken), sorted(taken));
            }
            case "with_mapping" -> KeptMapping.withMapping((String) args.get(0), (String) args.get(1),
                    (Map<String, Object>) args.get(2));
            case "with_agreed_template" -> KeptMapping.withAgreedTemplate((String) args.get(0),
                    (Map<String, Object>) args.get(1));
            case "read_agreed_template" -> KeptMapping.readAgreedTemplate((String) args.getFirst()).orElse(null);
            case "replace_section" -> KeptMapping.replaceSection((String) args.get(0), (String) args.get(1),
                    (String) args.get(2), (Boolean) args.get(3));
            case "doc_digest" -> KeptMapping.docDigest((String) args.getFirst()).orElse(null);
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static String code(final Map<String, String> xslts, final Object name) {
        return name == null
                ? null
                : xslts.get((String) name);
    }

    private static Map<String, Object> outline(final HandEdits.Outline outline) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("writes", outline.writes());
        out.put("reads", outline.reads());
        final Map<String, Object> names = new LinkedHashMap<>();
        outline.names().forEach((item, found) -> names.put(item, sorted(found)));
        out.put("names", names);
        final Map<String, Object> within = new LinkedHashMap<>();
        outline.within().forEach((item, found) -> within.put(item, sorted(found)));
        out.put("within", within);
        return out;
    }

    private static Map<String, Object> undone(final HandEdits.Undone undone) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("verb", undone.verb());
        out.put("item", undone.item());
        out.put("again", undone.again());
        out.put("names", sorted(undone.names()));
        out.put("within", sorted(undone.within()));
        out.put("str", undone.toString());
        return out;
    }

    private static Map<String, Object> collisions(final Map<String, HandEdits.Collision> collisions) {
        final Map<String, Object> out = new LinkedHashMap<>();
        collisions.forEach((label, collision) -> out.put(label, java.util.Arrays.asList(collision.had(),
                collision.proposes(), collision.undone().stream().map(HandEdits.Undone::toString).toList())));
        return out;
    }

    private static List<String> sorted(final Set<String> values) {
        return List.copyOf(new TreeSet<>(values));
    }
}
