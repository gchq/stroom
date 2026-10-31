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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// XPath parsed for reading an XSLT back: asked for by the user in place of splitting expressions with regular
/// expressions. Parsing only: Stroom evaluates the XSLT. And the parser against its fixed results: `xpath_cases.json`
/// holds every expression the tests parse (and more forms a hand edit may write) with the tree it must give, or that
/// it is refused; and, per golden XSLT of the generator, a digest of the trees of every select and test in it and of
/// every part of them.
class TestXPathTrees {

    private static final Map<String, String> NS = Map.of("stroom", "stroom", "mcp", "urn:mcp", "local",
            "urn:local", "geo", "urn:geo");

    @TestFactory
    Stream<DynamicTest> theTreesAreElementpaths() {
        final Map<String, Object> cases = ReadingFixtures.json("xpath_cases.json");
        final List<Map<String, String>> namespaces = list(cases.get("namespaces"));
        final List<Map<String, Object>> parses = list(cases.get("parses"));
        assertThat(parses).hasSizeGreaterThan(700);
        return IntStream.range(0, parses.size()).mapToObj(n -> {
            final Map<String, Object> c = parses.get(n);
            final String text = (String) c.get("text");
            return DynamicTest.dynamicTest(n + " " + text, () -> {
                final XPathTreeParser parser = new XPathTreeParser(namespaces.get(((Number) c.get("ns")).intValue()));
                if (c.containsKey("error")) {
                    assertThatThrownBy(() -> parser.parse(text)).isInstanceOf(XPathParseException.class);
                } else {
                    assertThat(ToolJson.dumps(ReadingFixtures.tree(parser.parse(text))))
                            .isEqualTo(ToolJson.dumps(c.get("tree")));
                }
            });
        });
    }

    @TestFactory
    Stream<DynamicTest> everyExpressionOfTheGoldenXsltsIsParsedAsElementpathParsesIt() {
        final Map<String, Object> cases = ReadingFixtures.json("xpath_cases.json");
        final Map<String, List<Object>> golden = map(cases.get("golden"));
        final List<Path> files = ReadingFixtures.golden(".xslt");
        assertThat(files).hasSize(golden.size());
        return files.stream().map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
            final XmlElement root = XmlParser.parse(ReadingFixtures.read(path));
            final XPathTreeParser parser = XPathTreeParser.parserFor(root.namespaces());
            final List<Object> trees = new ArrayList<>();
            for (final XmlElement element : root.iterElements()) {
                for (final String attribute : List.of("select", "test")) {
                    final String text = element.get(attribute);
                    if (text == null || text.isEmpty()) {
                        continue;
                    }
                    final XPathNode node = parser.parse(text);
                    trees.add(ReadingFixtures.tree(node));
                    for (final XPathNode part : node.walk()) {
                        trees.add(ReadingFixtures.tree(parser.parse(part.text())));
                    }
                }
            }
            final List<Object> expected = golden.get(path.getFileName().toString());
            assertThat((long) trees.size()).isEqualTo(((Number) expected.get(0)).longValue());
            assertThat(ReadingFixtures.digest(trees)).isEqualTo(expected.get(1));
        }));
    }

    @Test
    void everyPartOfEveryGeneratedExpressionIsItsExactText() {
        // Each subtree's text, cut from the expression by the token positions, parses back to the same tree: an
        // expression read back is written as it was, not re-serialised.
        int checked = 0;
        for (final Path path : ReadingFixtures.golden(".xslt")) {
            final XmlElement root = XmlParser.parse(ReadingFixtures.read(path));
            final XPathTreeParser parser = XPathTreeParser.parserFor(root.namespaces());
            for (final XmlElement element : root.iterElements()) {
                for (final String attribute : List.of("select", "test")) {
                    final String text = element.get(attribute);
                    if (text == null || text.isEmpty()) {
                        continue;
                    }
                    final XPathNode tree = parser.parse(text);
                    assertThat(tree.text()).isEqualTo(text.strip());
                    for (final XPathNode node : tree.walk()) {
                        assertThat(parser.parse(node.text()).key()).as(text + " / " + node.text())
                                .isEqualTo(node.key());
                        checked++;
                    }
                }
            }
        }
        assertThat(checked).isGreaterThan(2000);
    }

    @ParameterizedTest
    @ValueSource(strings = {"stroom:lookup('M', 'k')", "stroom:record-no()", "mcp:data('n', 1)",
            "stroom:format-date('x', 'yyyy', '+10:00')", "local:clean(geo:city('10.0.0.1'))"})
    void functionsItDoesntKnowAreParsedByAnyPrefixTheStylesheetBindsAndNeverRun(final String call) {
        // Stroom's, the XSLT's own (mcp:, or a prefix added by hand, local: say) and an imported XSLT's (geo:): each
        // a function of any arity whose body is never run; names the standard functions have too (data,
        // format-date) as well.
        final XPathNode tree = new XPathTreeParser(NS).parse(call);
        assertThat(tree.symbol()).isEqualTo("call");
        assertThat(tree.text()).isEqualTo(call);
    }

    @Test
    void theParsersKeptForEachSetOfBindingsAreBounded() {
        // Before, one was kept for every set of bindings ever sent, for as long as the server ran.
        for (int i = 0; i < XPathTreeParser.MAX_PARSERS * 2; i++) {
            final XPathTreeParser parser = XPathTreeParser.parserFor(Map.of("p" + i, "urn:" + i));
            assertThat(XPathTreeParser.parserFor(Map.of("p" + i, "urn:" + i))).isSameAs(parser);
        }
        assertThat(XPathTreeParser.parsersKept()).isLessThanOrEqualTo(XPathTreeParser.MAX_PARSERS);
    }

    @Test
    void aPrefixTheStylesheetDoesntBindIsAnErrorAsItIsInStroom() {
        assertThatThrownBy(() -> new XPathTreeParser(NS).parse("nope:thing(1)"))
                .isInstanceOf(XPathParseException.class)
                .hasMessageContaining("XPST0081");
    }

    @Test
    void slotsMatchWhatFillsThemAndBracketsAndGuardsDontCount() {
        final XPathTreeParser parser = new XPathTreeParser(NS);
        final XPathNode pattern = parser.parse("stroom:lookup('" + XPathTrees.stringSlot(1) + "', string(("
                                               + XPathTrees.exprSlot(1) + ")[1]))");
        final XPathMatch found = XPathTrees.unify(pattern, parser.parse(
                "(stroom:lookup('USERS', string((data[@name='u']/@value[normalize-space(.)])[1])))")).orElseThrow();
        assertThat(found.string(1)).isEqualTo("USERS");
        assertThat(found.expr(1).text()).isEqualTo("data[@name='u']/@value");
        assertThat(XPathTrees.items(parser.parse("(a, (b), c)")).stream().map(XPathNode::text))
                .containsExactly("a", "b", "c");
        assertThat(XPathTrees.unify(pattern, parser.parse("stroom:lookup($m, 'k')"))).isEmpty();
    }

    @Test
    void splicingReplacesNodesByWhereTheyAre() {
        final String text = "concat($a, '$a', $a)";
        final XPathNode tree = new XPathTreeParser(NS).parse(text);
        final List<XPathTrees.Replacement> variables = tree.walk().stream()
                .filter(node -> "$".equals(node.symbol()))
                .map(node -> new XPathTrees.Replacement(node, "(x)"))
                .toList();
        assertThat(XPathTrees.splice(text, variables)).isEqualTo("concat((x), '$a', (x))");
    }

    @Test
    void normalTakesOffBracketsAndHasAValueGuardsAndReadsAFirstValueAsTheInputs() {
        final XPathTreeParser parser = new XPathTreeParser(NS);
        assertThat(XPathTrees.normal(parser.parse("((data[@name='u']/@value[normalize-space(.)]))")).text())
                .isEqualTo("data[@name='u']/@value");
        assertThat(XPathTrees.normal(parser.parse("data[@name='u']/@value[not(normalize-space(.) = ('-'))]"))
                .text()).isEqualTo("data[@name='u']/@value");
        final XPathNode first = XPathTrees.normal(parser.parse("data[@name='u']/@value[1]"));
        assertThat(first.symbol()).isEqualTo("[");
        assertThat(first.get(0).text()).isEqualTo("data[@name='u']/@value");
        assertThat(XPathTrees.firstOf(parser.parse("(a)[1]")).map(XPathNode::text)).contains("a");
        assertThat(XPathTrees.firstOf(parser.parse("a[2]"))).isEmpty();
    }

    @Test
    void termsAndStringsReadOperandsAndSequencesAsWritten() {
        final XPathTreeParser parser = new XPathTreeParser(NS);
        assertThat(XPathTrees.terms(parser.parse("a and (b and c) and d"), "and").stream().map(XPathNode::text))
                .containsExactly("a", "b", "c", "d");
        assertThat(XPathTrees.strings(parser.parse("('a', ('b'), 'c')"))).contains(List.of("a", "b", "c"));
        assertThat(XPathTrees.strings(parser.parse("('a', b)"))).isEmpty();
        assertThat(XPathTrees.isNilCheck(XPathTrees.normal(parser.parse("not(normalize-space(.) = ('-'))"))))
                .isTrue();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(final Object value) {
        return (List<T>) value;
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> map(final Object value) {
        return (Map<String, T>) value;
    }
}
