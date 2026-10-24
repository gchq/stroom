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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Shapes: what the generator's functions ([XPathText]) write, called with slots ([XPathTrees#exprSlot(int)] for an
/// expression, [XPathTrees#stringSlot(int)] for a string) and parsed. Read against them with
/// [XPathTrees#unify(XPathNode, XPathNode)], the reader follows the generator: one definition, not two that drift
/// apart (asked for by the user). The Stroom MCP server's `xsltread.SHAPES` and the shapes beside it.
public final class ReadShapes {

    /// The parser of the shapes: the prefixes the generator binds.
    static final XPathTreeParser PARSER = XPathTreeParser.parserFor(Map.of("stroom", "stroom", "mcp",
            MappingFacts.MCP_NS, "fn", MappingFacts.FN, "map", MappingFacts.MAP_NS, "xs", MappingFacts.XS));

    /// Each shape, by name (`lookup`, `dictionary`, `any of`, `time, zone`, ...).
    public static final Map<String, XPathNode> SHAPES = shapes();
    /// A lookup below the value it finds, one to four steps down (lookupText writes each step `*:name`).
    public static final List<XPathNode> LOOKUP_BELOW = lookupsBelow();
    /// Each transform's shape, by transform, in the order they are tried.
    public static final Map<String, XPathNode> TRANSFORMS = transforms();
    /// An input field, one to six levels deep (fieldText): its names, joined as the mapping writes them; by kind.
    public static final Map<String, List<XPathNode>> FIELDS = Map.of("data_splitter", fields("data_splitter", "/"),
            "json", fields("json", "."));
    /// What a field's names are joined with, by kind.
    public static final Map<String, String> SEPARATOR = Map.of("data_splitter", "/", "json", ".");

    private ReadShapes() {
        // Static only.
    }

    /// @param rendered An expression the generator's functions wrote with slots.
    /// @return Its shape: parsed, as normal() leaves it.
    public static XPathNode shape(final String rendered) {
        return XPathTrees.normal(PARSER.parse(rendered));
    }

    private static String expr(final int n) {
        return XPathTrees.exprSlot(n);
    }

    private static String text(final int n) {
        return XPathTrees.stringSlot(n);
    }

    private static Map<String, XPathNode> shapes() {
        final Map<String, XPathNode> out = new LinkedHashMap<>();
        out.put("lookup", shape(XPathText.lookupText(text(1), XPathText.keyText(expr(1)), null)));
        out.put("dictionary", shape(XPathText.dictionaryLookupText(expr(1), XPathText.keyText(expr(2)))));
        out.put("dictionary map", shape(XPathText.dictionaryText(text(1), "map")));
        out.put("dictionary list", shape(XPathText.dictionaryText(text(1), "list")));
        out.put("any of", shape(XPathText.anyOfText(List.of(expr(1)))));
        out.put("has value", shape(XPathText.hasValueText(expr(1))));
        out.put("map lookup", shape(XPathText.mapLookupText("$" + expr(1), expr(2), null)));
        out.put("map lookup, default", shape(XPathText.mapLookupText("$" + expr(1), expr(2), text(1))));
        out.put("map step", shape(XPathText.mapStepText(expr(1), text(1), text(2), expr(2))));
        out.put("default", shape(XPathText.defaultText(expr(1), expr(2), text(1))));
        out.put("group", shape(XPathText.groupText("(" + XPathText.partsText(expr(1), text(1), null) + ")", expr(2))));
        out.put("group, flags", shape(XPathText.groupText("(" + XPathText.partsText(expr(1), text(1), text(2)) + ")",
                expr(2))));
        out.put("keyed", shape(XPathText.keyedText(text(1), text(2), null)));
        out.put("keyed, flags", shape(XPathText.keyedText(text(1), text(2), text(3))));
        out.put("equals", shape(XPathText.equalsText(expr(1), text(1))));
        out.put("matches", shape(XPathText.matchesText(expr(1), text(1))));
        out.put("in dictionary", shape(XPathText.inDictionaryText(expr(1), expr(2))));
        out.put("time", shape(XPathText.timeExpression(text(1), null, expr(1) + "[1]")));
        out.put("time, zone", shape(XPathText.timeExpression(text(1), text(2), expr(1) + "[1]")));
        for (final String epoch : List.of("epoch_ms", "epoch_s", "epoch_us", "epoch_ns")) {
            out.put(epoch, shape(XPathText.timeExpression(epoch, null, expr(1) + "[1]")));
        }
        // Before seconds' fraction was rounded: still epoch_s.
        out.put("epoch_s, truncated", shape("stroom:format-date(string(xs:integer(xs:decimal(" + expr(1)
                                            + "[1]) * 1000)))"));
        // Before finer digits were cut rather than rounded (as the Stroom MCP server's generator rounded them): still
        // the same formats, read from XSLTs saved then.
        for (final String epoch : List.of("epoch_s", "epoch_us", "epoch_ns")) {
            out.put(epoch + ", rounded", shape("stroom:format-date(string(xs:integer(round(xs:decimal(" + expr(1)
                                               + "[1]) " + XPathText.EPOCH_SCALE.get(epoch) + "))))"));
        }
        out.put("time, ms", shape(XPathText.timeMsText(text(1), text(2), expr(1) + "[1]")));
        // oneOfText, its values any number of strings: its shape with one value, the value's place taking any
        // expression.
        final XPathNode one = shape(XPathText.oneOfText(expr(1), List.of(text(1))));
        out.put("one of", new XPathNode(one.symbol(), one.value(), List.of(one.get(0), shape(expr(2))), "", 0, 0));
        return Map.copyOf(out);
    }

    private static List<XPathNode> lookupsBelow() {
        final List<XPathNode> out = new ArrayList<>();
        for (int depth = 1; depth <= 4; depth++) {
            final List<String> steps = new ArrayList<>();
            for (int n = 2; n < depth + 2; n++) {
                steps.add(expr(n));
            }
            out.add(shape(XPathText.lookupText(text(1), XPathText.keyText(expr(1)), String.join("/", steps))));
        }
        return List.copyOf(out);
    }

    private static Map<String, XPathNode> transforms() {
        final Map<String, XPathNode> out = new LinkedHashMap<>();
        for (final String transform : List.of("lower", "upper", "trim", "strip_domain", "domain", "digits")) {
            out.put(transform, shape(XPathText.transformExpression(transform, expr(1) + "[1]")));
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<XPathNode> fields(final String kind, final String separator) {
        final List<XPathNode> out = new ArrayList<>();
        for (int depth = 1; depth <= 6; depth++) {
            final List<String> names = new ArrayList<>();
            for (int n = 1; n <= depth; n++) {
                names.add(text(n));
            }
            out.add(shape(XPathText.fieldText(kind, String.join(separator, names))));
        }
        return List.copyOf(out);
    }
}
