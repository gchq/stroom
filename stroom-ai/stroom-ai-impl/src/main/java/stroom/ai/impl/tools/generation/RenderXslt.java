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

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.validation.XmlTrees;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/// The XSLT the generator writes for a value rendered from others: a template filled ([FillTemplate],
/// `mcp:fill-template`) and a bit mask's names ([MaskNames], `mcp:mask-names`), with the checks stepping reports
/// (placeholders left unfilled, bits with no name).
///
/// XPath has no bitwise operators: a mask is read as an `xs:integer` (arbitrary precision, so bit 63 of an unsigned
/// 64-bit value is exact) and split into the numbers of its set bits by halving (`idiv 2`, `mod 2`); a mask names a
/// value when every one of its bits is among the value's.
final class RenderXslt {

    private static final String XSL = XmlTrees.XSL;
    private static final String HEX_DIGITS = "0123456789abcdef";

    private final String fill;
    private final String unfilled;
    private final String names;
    private final String maskProblem;
    private final String bits;
    private final String unsigned;
    private final String hex;

    /// @param named A function's base name (e.g. `fill-template`) as the generator names its own functions (the
    ///              style's naming, a module's prefix), without the `mcp:` prefix.
    RenderXslt(final UnaryOperator<String> named) {
        this.fill = named.apply("fill-template");
        this.unfilled = named.apply("unfilled-placeholders");
        this.names = named.apply("mask-names");
        this.maskProblem = named.apply("mask-problem");
        this.bits = named.apply("set-bits");
        this.unsigned = named.apply("unsigned-mask");
        this.hex = named.apply("hex-number");
    }

    /// @param template The template's expression (one value).
    /// @param pattern  The placeholder pattern.
    /// @param values   The values' expression (a sequence, in order).
    /// @return The template filled.
    String fillText(final String template, final String pattern, final String values) {
        return "mcp:" + fill + "(" + template + ", " + XPathText.literal(pattern) + ", " + values + ")";
    }

    /// @param template The template's expression (one value).
    /// @param pattern  The placeholder pattern.
    /// @param values   The values' expression (a sequence, in order).
    /// @return The placeholders the values leave unfilled, as written.
    String unfilledText(final String template, final String pattern, final String values) {
        return "mcp:" + unfilled + "(" + template + ", " + XPathText.literal(pattern) + ", count(" + values + "))";
    }

    /// @param value  The mask's expression (one value).
    /// @param masks  The table's masks' expression.
    /// @param labels The table's names' expression, one for each mask.
    /// @return The names the value holds, in the table's order.
    String namesText(final String value, final String masks, final String labels) {
        return "mcp:" + names + "(" + value + ", " + masks + ", " + labels + ")";
    }

    /// @param value  The mask's expression (one value).
    /// @param masks  The table's masks' expression.
    /// @param labels The table's names' expression.
    /// @return What is wrong with the value for the table (bits no name covers, or not a number), or nothing.
    String problemText(final String value, final String masks, final String labels) {
        return "mcp:" + maskProblem + "(" + value + ", " + masks + ", " + labels + ")";
    }

    /// @param table A static table, mask -> name.
    /// @return Its masks and its names, as two XPath sequences.
    static List<String> staticTable(final Map<String, String> table) {
        return List.of("(" + table.keySet().stream().map(XPathText::literal).collect(Collectors.joining(", ")) + ")",
                "(" + table.values().stream().map(XPathText::literal).collect(Collectors.joining(", ")) + ")");
    }

    /// @param entries The table's entries' expression (e.g. a lookup's value below its path).
    /// @param mask    The mask's path in an entry.
    /// @param name    The name's path in an entry.
    /// @return Its masks and its names, as two XPath sequences, one item an entry.
    static List<String> lookedUpTable(final String entries, final String mask, final String name) {
        return List.of("(" + entries + ") ! string(" + anyNamespace(mask) + ")",
                "(" + entries + ") ! string(" + anyNamespace(name) + ")");
    }

    // A path below a reference value, its elements matched in any namespace (the value's elements are in none, or in
    // the namespace of the data they were copied from).
    private static String anyNamespace(final String path) {
        return ToolText.split(ToolText.strip(path, "/"), "/").stream()
                .filter(step -> !step.isEmpty())
                .map(step -> step.contains(":") || step.startsWith("@") || ".".equals(step) || "*".equals(step)
                        ? step
                        : "*:" + step)
                .collect(Collectors.joining("/"));
    }

    /// @param fills True to write the template functions.
    /// @param masks True to write the mask functions.
    /// @return The functions, to write at the stylesheet's end.
    List<XmlElement> functions(final boolean fills, final boolean masks) {
        final List<XmlElement> out = new ArrayList<>();
        if (fills) {
            out.add(fillFunction());
            out.add(unfilledFunction());
        }
        if (masks) {
            out.add(namesFunction());
            out.add(problemFunction());
            out.add(bitsFunction());
            out.add(unsignedFunction());
            out.add(hexFunction());
        }
        return out;
    }

    private XmlElement fillFunction() {
        final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:" + fill, "as", "xs:string?");
        function.append(new XmlComment(XsltComments.comment("A template filled: each placeholder the pattern "
                                                            + "matches, its first group a number from 1, replaced "
                                                            + "by that value; a placeholder with no value is left "
                                                            + "as written")));
        function.addElement(XSL, "param", "name", "template", "as", "item()?");
        function.addElement(XSL, "param", "name", "pattern", "as", "xs:string");
        function.addElement(XSL, "param", "name", "values", "as", "item()*");
        final XmlElement given = function.addElement(XSL, "if", "test", "exists($template)");
        final XmlElement parts = given.addElement(XSL, "variable", "name", "parts", "as", "xs:string*");
        final XmlElement analyse = parts.addElement(XSL, "analyze-string", "select", "string($template)", "regex",
                "{$pattern}");
        analyse.addElement(XSL, "matching-substring").addElement(XSL, "sequence", "select",
                "if (regex-group(1) castable as xs:integer and xs:integer(regex-group(1)) = 1 to count($values)) "
                + "then string($values[xs:integer(regex-group(1))]) else .");
        analyse.addElement(XSL, "non-matching-substring").addElement(XSL, "sequence", "select", ".");
        given.addElement(XSL, "sequence", "select", "string-join($parts, '')");
        return function;
    }

    private XmlElement unfilledFunction() {
        final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:" + unfilled, "as",
                "xs:string*");
        function.append(new XmlComment(XsltComments.comment("The placeholders of a template that a count of "
                                                            + "values leaves unfilled, as written")));
        function.addElement(XSL, "param", "name", "template", "as", "item()?");
        function.addElement(XSL, "param", "name", "pattern", "as", "xs:string");
        function.addElement(XSL, "param", "name", "count", "as", "xs:integer");
        final XmlElement analyse = function.addElement(XSL, "if", "test", "exists($template)")
                .addElement(XSL, "analyze-string", "select", "string($template)", "regex", "{$pattern}");
        analyse.addElement(XSL, "matching-substring").addElement(XSL, "sequence", "select",
                "if (regex-group(1) castable as xs:integer and not(xs:integer(regex-group(1)) = 1 to $count)) "
                + "then string(.) else ()");
        return function;
    }

    private XmlElement namesFunction() {
        final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:" + names, "as", "xs:string*");
        function.append(new XmlComment(XsltComments.comment("The names of the masks a value (an unsigned number "
                                                            + "of up to 64 bits, decimal or 0x hex) holds: those "
                                                            + "all of whose bits it has, in the table's order, each "
                                                            + "once")));
        function.addElement(XSL, "param", "name", "value", "as", "item()?");
        function.addElement(XSL, "param", "name", "masks", "as", "xs:string*");
        function.addElement(XSL, "param", "name", "names", "as", "xs:string*");
        function.addElement(XSL, "variable", "name", "set", "as", "xs:integer*", "select",
                "mcp:" + bits + "((mcp:" + unsigned + "(string($value)), 0)[1], 0)");
        function.addElement(XSL, "sequence", "select", "distinct-values(for $i in 1 to count($masks) return "
                                                       + "let $bits := mcp:" + bits + "((mcp:" + unsigned
                                                       + "($masks[$i]), 0)[1], 0) return if (exists($bits) and "
                                                       + "normalize-space($names[$i]) and (every $bit in $bits "
                                                       + "satisfies $bit = $set)) then normalize-space($names[$i]) "
                                                       + "else ())");
        return function;
    }

    private XmlElement problemFunction() {
        final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:" + maskProblem, "as",
                "xs:string?");
        function.append(new XmlComment(XsltComments.comment("What the table can't name in a value: the set bits "
                                                            + "no named mask covers, or a value that isn't a mask")));
        function.addElement(XSL, "param", "name", "value", "as", "item()?");
        function.addElement(XSL, "param", "name", "masks", "as", "xs:string*");
        function.addElement(XSL, "param", "name", "names", "as", "xs:string*");
        function.addElement(XSL, "variable", "name", "number", "as", "xs:integer?", "select",
                "mcp:" + unsigned + "(string($value))");
        function.addElement(XSL, "variable", "name", "set", "as", "xs:integer*", "select",
                "mcp:" + bits + "(($number, 0)[1], 0)");
        function.addElement(XSL, "variable", "name", "named", "as", "xs:integer*", "select",
                "for $i in 1 to count($masks) return let $bits := mcp:" + bits + "((mcp:" + unsigned
                + "($masks[$i]), 0)[1], 0) return if (exists($bits) and normalize-space($names[$i]) and (every $bit "
                + "in $bits satisfies $bit = $set)) then $bits else ()");
        function.addElement(XSL, "variable", "name", "unnamed", "as", "xs:integer*", "select",
                "$set[not(. = $named)]");
        function.addElement(XSL, "sequence", "select", "if (normalize-space(string($value)) and empty($number)) "
                                                       + "then concat('''', string($value), ''' is not an unsigned "
                                                       + "number of up to 64 bits (decimal or 0x hex)') "
                                                       + "else if (exists($unnamed)) then concat('no name for bit', "
                                                       + "if (count($unnamed) gt 1) then 's ' else ' ', "
                                                       + "string-join(for $bit in $unnamed return concat(string($bit),"
                                                       + " ' (0x', ('1', '2', '4', '8')[$bit mod 4 + 1], "
                                                       + "string-join(for $zero in 1 to $bit idiv 4 return '0', ''), "
                                                       + "')'), ', '), ' of ', normalize-space(string($value))) "
                                                       + "else ()");
        return function;
    }

    private XmlElement bitsFunction() {
        final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:" + bits, "as", "xs:integer*");
        function.append(new XmlComment(XsltComments.comment("The numbers of a value's set bits, lowest first "
                                                            + "(XPath has no bitwise operators)")));
        function.addElement(XSL, "param", "name", "value", "as", "xs:integer");
        function.addElement(XSL, "param", "name", "bit", "as", "xs:integer");
        function.addElement(XSL, "sequence", "select", "if ($value le 0) then () else ((if ($value mod 2 = 1) then "
                                                       + "$bit else ()), mcp:" + bits + "($value idiv 2, $bit + 1))");
        return function;
    }

    private XmlElement unsignedFunction() {
        final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:" + unsigned, "as",
                "xs:integer?");
        function.append(new XmlComment(XsltComments.comment("A mask's value: an unsigned number of up to 64 bits, "
                                                            + "decimal or 0x hex; nothing for anything else")));
        function.addElement(XSL, "param", "name", "text", "as", "xs:string?");
        function.addElement(XSL, "variable", "name", "given", "select", "lower-case(normalize-space($text))");
        function.addElement(XSL, "variable", "name", "number", "as", "xs:integer?", "select",
                "if (matches($given, '^0x[0-9a-f]{1,16}$')) then mcp:" + hex + "(substring($given, 3), 0) "
                + "else if (matches($given, '^[0-9]{1,20}$')) then xs:integer($given) else ()");
        function.addElement(XSL, "sequence", "select", "$number[. le 18446744073709551615]");
        return function;
    }

    private XmlElement hexFunction() {
        final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:" + hex, "as", "xs:integer");
        function.append(new XmlComment(XsltComments.comment("Hex digits (lower case) read as a number, added to "
                                                            + "what the digits before them made")));
        function.addElement(XSL, "param", "name", "digits", "as", "xs:string");
        function.addElement(XSL, "param", "name", "total", "as", "xs:integer");
        function.addElement(XSL, "sequence", "select", "if ($digits = '') then $total else mcp:" + hex
                                                       + "(substring($digits, 2), $total * 16 + string-length("
                                                       + "substring-before(" + XPathText.literal(HEX_DIGITS)
                                                       + ", substring($digits, 1, 1))))");
        return function;
    }
}
