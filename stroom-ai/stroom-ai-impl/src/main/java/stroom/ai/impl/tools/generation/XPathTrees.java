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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What a parsed XPath expression reads, for reading an XSLT back: the Stroom MCP server's `utils/xpathtree.py`
/// (its functions; the parser is [XPathTreeParser], the nodes [XPathNode]).
///
/// [#normal(XPathNode)] takes off what doesn't change what an expression reads for the reader (brackets around one
/// expression, the generator's has-a-value predicates), and [#unify(XPathNode, XPathNode)] matches a tree against a
/// pattern made by the generator's own functions called with slots, binding each slot to what fills it.
public final class XPathTrees {

    /// An expression slot in a pattern: a name the generator never writes.
    public static final Pattern EXPR_SLOT = Pattern.compile("QXE(\\d+)QX");
    /// A string slot in a pattern.
    public static final Pattern STRING_SLOT = Pattern.compile("QXL(\\d+)QX");

    private XPathTrees() {
        // Static only.
    }

    /// @param n The slot's number.
    /// @return An expression slot, matching any expression.
    public static String exprSlot(final int n) {
        return "QXE" + n + "QX";
    }

    /// @param n The slot's number.
    /// @return A string slot, matching any string.
    public static String stringSlot(final int n) {
        return "QXL" + n + "QX";
    }

    // normalize-space(.)
    private static boolean isDotCheck(final XPathNode node) {
        return "call".equals(node.symbol()) && "normalize-space".equals(node.value()) && node.size() == 1
               && ".".equals(node.get(0).symbol());
    }

    /// @param node A node.
    /// @return Whether it is `not(normalize-space(.) = ('N/A', ...))`: the generator's predicate leaving out a
    /// mapping's nil values.
    public static boolean isNilCheck(final XPathNode node) {
        return "call".equals(node.symbol()) && "not".equals(node.value()) && node.size() == 1
               && "=".equals(node.get(0).symbol()) && isDotCheck(node.get(0).get(0));
    }

    /// @param node A parsed expression.
    /// @return The expression as the reader compares it: brackets around one expression taken off, and the
    /// generator's has-a-value predicates (`[normalize-space(.)]`, and the one leaving out nil values), which only
    /// decide whether an element is written. Its text is the expression's, without what was taken off.
    public static XPathNode normal(final XPathNode node) {
        final XPathNode inner = unbracketed(node);
        return normalOf(inner);
    }

    private static XPathNode normalOf(final XPathNode node) {
        final List<XPathNode> children = new ArrayList<>(node.size());
        for (final XPathNode child : node.children()) {
            children.add(normal(child));
        }
        if ("[".equals(node.symbol()) && children.size() == 2
            && (isDotCheck(children.get(1)) || isNilCheck(children.get(1)))) {
            return children.getFirst();
        }
        if ("(".equals(node.symbol()) && children.size() == 1) {
            return children.getFirst();
        }
        final String text = text(node, children);
        if ("/".equals(node.symbol()) && children.size() == 2 && firstOf(children.get(1)).isPresent()
            && "[".equals(children.get(1).symbol())) {
            // a/b[1] read as (a/b)[1]: the generator writes {input}[1] for an input's first value, which binds to
            // the input's last step; for the reader both are the first value.
            final XPathNode predicate = children.get(1);
            final XPathNode path = new XPathNode("/", node.value(), List.of(children.getFirst(), predicate.get(0)),
                    text.substring(0, text.lastIndexOf('[')), node.start(), node.end());
            return new XPathNode("[", "[", List.of(path, predicate.get(1)), text, node.start(), node.end());
        }
        return new XPathNode(node.symbol(), node.value(), children, text, node.start(), node.end());
    }

    // The node's text with each child's replaced by the child's normal() text (what normal() took out, cut).
    private static String text(final XPathNode node, final List<XPathNode> children) {
        final List<int[]> order = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            order.add(new int[]{node.get(i).start(), i});
        }
        order.sort(Comparator.comparingInt(pair -> pair[0]));
        final StringBuilder out = new StringBuilder();
        int at = node.start();
        for (final int[] pair : order) {
            final XPathNode old = node.get(pair[1]);
            final XPathNode now = children.get(pair[1]);
            if (!now.text().equals(old.text()) && old.start() >= at) {
                out.append(slice(node.text(), at - node.start(), old.start() - node.start())).append(now.text());
                at = old.end();
            }
        }
        return out.append(slice(node.text(), at - node.start(), node.text().length())).toString();
    }

    /// @param node A parsed expression.
    /// @return A sequence's members, as normal() leaves them: `(a, b, c)` as a, b, c; anything else alone.
    public static List<XPathNode> items(final XPathNode node) {
        final XPathNode normal = normal(node);
        if (",".equals(normal.symbol())) {
            final List<XPathNode> out = new ArrayList<>();
            for (final XPathNode child : normal.children()) {
                out.addAll(items(child));
            }
            return out;
        }
        return List.of(normal);
    }

    /// @param node A parsed expression.
    /// @return It without brackets around it, otherwise as written (unlike normal(), which reads through more).
    public static XPathNode unbracketed(final XPathNode node) {
        XPathNode out = node;
        while ("(".equals(out.symbol()) && out.size() == 1) {
            out = out.get(0);
        }
        return out;
    }

    /// @param node     A parsed expression.
    /// @param operator An operator, e.g. `and`.
    /// @return Its operands for that operator at the top, as written: `a and b and c` as a, b, c.
    public static List<XPathNode> terms(final XPathNode node, final String operator) {
        final XPathNode top = unbracketed(node);
        if (operator.equals(top.symbol()) && top.size() == 2) {
            final List<XPathNode> out = new ArrayList<>(terms(top.get(0), operator));
            out.addAll(terms(top.get(1), operator));
            return out;
        }
        return List.of(top);
    }

    /// @param node A parsed expression.
    /// @return The values of a string, or of a sequence of strings; empty for anything else.
    public static Optional<List<String>> strings(final XPathNode node) {
        final List<String> out = new ArrayList<>();
        for (final XPathNode member : items(node)) {
            if (!"(string)".equals(member.symbol())) {
                return Optional.empty();
            }
            out.add(member.valueText());
        }
        return Optional.of(out);
    }

    /// @param node A parsed expression.
    /// @return X, for `X[1]`; empty for anything else.
    public static Optional<XPathNode> firstOf(final XPathNode node) {
        final XPathNode normal = normal(node);
        if ("[".equals(normal.symbol()) && normal.size() == 2 && "(integer)".equals(normal.get(1).symbol())
            && BigInteger.ONE.equals(normal.get(1).value())) {
            return Optional.of(normal.get(0));
        }
        return Optional.empty();
    }

    /// @param pattern A pattern: an expression with slots, parsed.
    /// @param node    An expression, parsed.
    /// @return What fills each slot when the node matches the pattern (both as normal() leaves them); empty when it
    /// doesn't.
    public static Optional<XPathMatch> unify(final XPathNode pattern, final XPathNode node) {
        return unify(pattern, node, new XPathMatch());
    }

    /// @param pattern A pattern: an expression with slots, parsed.
    /// @param node    An expression, parsed.
    /// @param found   Slots already filled, which must be filled alike here.
    /// @return The slots filled, when the node matches the pattern; empty when it doesn't.
    public static Optional<XPathMatch> unify(final XPathNode pattern, final XPathNode node, final XPathMatch found) {
        return unifyNodes(normal(pattern), normal(node), found)
                ? Optional.of(found)
                : Optional.empty();
    }

    private static boolean unifyNodes(final XPathNode pattern, final XPathNode node, final XPathMatch found) {
        final String value = pattern.valueText();
        final Matcher slot = "(name)".equals(pattern.symbol()) || "$".equals(pattern.symbol())
                ? EXPR_SLOT.matcher(value)
                : null;
        if (slot != null && slot.matches()) {
            if ("$".equals(pattern.symbol()) && !"$".equals(node.symbol())) {
                return false;
            }
            final String name = "E" + slot.group(1);
            if (found.nodes().containsKey(name)) {
                return found.nodes().get(name).key().equals(node.key());
            }
            found.nodes().put(name, node);
            return true;
        }
        final Matcher string = STRING_SLOT.matcher(value);
        if ("(string)".equals(pattern.symbol()) && string.matches()) {
            if (!"(string)".equals(node.symbol())) {
                return false;
            }
            found.nodes().put("L" + string.group(1), node);
            return true;
        }
        // A prefixed name holding a slot (*:QXE2QX) has the slot's own node to match, not its whole name.
        final boolean sameValue = sameValue(pattern.value(), node.value())
                                  || (":".equals(pattern.symbol()) && EXPR_SLOT.matcher(value).find());
        if (!pattern.symbol().equals(node.symbol()) || !sameValue || pattern.size() != node.size()) {
            return false;
        }
        for (int i = 0; i < pattern.size(); i++) {
            if (!unifyNodes(pattern.get(i), node.get(i), found)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameValue(final Object a, final Object b) {
        if (a instanceof final BigDecimal x && b instanceof final BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }

    /// @param text         An expression's text: the one its nodes were parsed from.
    /// @param replacements Nodes and the text to put in place of each; one inside another already replaced is
    ///                     left out.
    /// @return The text with those nodes' text replaced.
    public static String splice(final String text, final List<Replacement> replacements) {
        final List<Replacement> ordered = new ArrayList<>(replacements);
        ordered.sort(Comparator.comparingInt(replacement -> replacement.target().start()));
        final StringBuilder out = new StringBuilder();
        int at = 0;
        for (final Replacement replacement : ordered) {
            if (replacement.target().start() < at) {
                continue;
            }
            out.append(slice(text, at, replacement.target().start())).append(replacement.by());
            at = replacement.target().end();
        }
        return out.append(slice(text, at, text.length())).toString();
    }

    // Python's text[from:to].
    /// @param text Some text.
    /// @param from Where to start (negative: from the end).
    /// @param to   Where to end (negative: from the end).
    /// @return Python's `text[from:to]`: clamped to the text, empty when to is before from.
    static String slice(final String text, final int from, final int to) {
        final int length = text.length();
        final int start = Math.clamp(from < 0
                ? from + length
                : from, 0, length);
        final int end = Math.clamp(to < 0
                ? to + length
                : to, 0, length);
        return end <= start
                ? ""
                : text.substring(start, end);
    }


    // --------------------------------------------------------------------------------


    /// A node of an expression and the text to put in its place.
    ///
    /// @param target The node.
    /// @param by     The text.
    public record Replacement(XPathNode target, String by) {

    }
}
