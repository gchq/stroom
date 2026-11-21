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

import stroom.ai.impl.tools.validation.XmlTrees;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// A loop written by hand that writes one element per value (an `xsl:for-each` with no variable holding its item, as
/// the generator's group loops have, e.g. one `Email` per address, each with its `From/EmailAddress`): read by
/// [XsltReader] as a group's loop on that element, each read of the loop's current value (`.`) put as a read of an
/// item variable, so the rebuild reads it back as a `for_each` group ([MappingRebuild]).
///
/// Only a loop whose current value is read as a value is read so: one that reads below it (a relative path, `@a`,
/// `x/y`) is left as it was, a loop no entry writes.
final class HandLoops {

    private static final String XSL = XmlTrees.XSL;
    private static final String EVT = XsltGenerator.EVT;
    // What, read where the loop's current value is the focus, reads below it (or beside it): a name, an attribute,
    // a wildcard, a parent, a prefixed name, an axis or a kind test.
    private static final Set<String> STEPS = Set.of("(name)", "@", "..", ":");
    // The XSLT instructions a hand loop's element may hold: what the generator writes inside an element.
    private static final Set<String> INSTRUCTIONS = Set.of("value-of", "if", "attribute", "variable", "text",
            "choose", "when", "otherwise");
    // The item variable's name, the first not already taken.
    private static final List<String> NAMES = List.of("item", "value", "each", "current_value");

    private HandLoops() {
        // Static only.
    }

    /// @param loop An `xsl:for-each` that isn't a group's loop.
    /// @return The one element it writes per value, with elements of its own inside (a group's element, not a
    /// leaf), when that is all it writes; else null (several elements, a Data, a leaf, a guard around it, or another
    /// loop or a template call inside).
    static XmlElement element(final XmlElement loop) {
        final List<XmlElement> body = loop.childElements();
        if (body.size() != 1) {
            return null;
        }
        final XmlElement element = body.getFirst();
        if (!EVT.equals(element.namespace()) || "Data".equals(element.localName())) {
            return null;
        }
        boolean holds = false;
        for (final XmlElement inner : element.iterElements()) {
            if (inner == element) {
                continue;
            }
            if (XSL.equals(inner.namespace())) {
                if (!INSTRUCTIONS.contains(inner.localName())) {
                    return null;
                }
            } else {
                holds |= EVT.equals(inner.namespace());
            }
        }
        return holds
                ? element
                : null;
    }

    /// @param loop  The loop.
    /// @param scope The variables in scope where it is, by name.
    /// @return A name for the variable its item is read through, used nowhere in it or around it.
    static String variableFor(final XmlElement loop, final Map<String, String> scope) {
        final String written = XmlSerialiser.toXml(loop);
        for (final String name : NAMES) {
            if (!scope.containsKey(name) && !written.contains("$" + name)) {
                return name;
            }
        }
        return null;
    }

    /// The element with each read of the loop's current value inside it put as a read of the variable: its
    /// expressions (an instruction's select and test, a literal element's attribute value templates). A copy: the
    /// element itself may be read again (a template more than one rule calls).
    ///
    /// @param element  The element the loop writes per value.
    /// @param variable The item variable's name.
    /// @param parser   The parser of the XSLT.
    /// @return The copy; null if any expression reads below the current value or can't be parsed.
    static XmlElement itemise(final XmlElement element, final String variable, final XPathTreeParser parser) {
        final XmlElement copy;
        try {
            copy = XmlParser.parse(XmlSerialiser.toXmlWithoutTail(element));
        } catch (final XmlParser.XmlParseException e) {
            return null;
        }
        final List<Change> changes = new ArrayList<>();
        for (final XmlElement inner : copy.iterElements()) {
            for (final XmlElement.Attribute attribute : inner.attributes()) {
                if (attribute.namespace() != null) {
                    continue;
                }
                final String value = attribute.value();
                final String changed;
                if (XSL.equals(inner.namespace())) {
                    changed = "select".equals(attribute.name()) || "test".equals(attribute.name())
                            ? expression(value, variable, parser)
                            : value.contains("{")
                                    ? null
                                    : value;
                } else {
                    changed = template(value, variable, parser);
                }
                if (changed == null) {
                    return null;
                }
                if (!changed.equals(value)) {
                    changes.add(new Change(inner, attribute.name(), changed));
                }
            }
        }
        changes.forEach(change -> change.element().set(change.name(), change.value()));
        return copy;
    }

    // An attribute value template with each expression's reads of the current value put as the variable's; null if
    // one can't be.
    private static String template(final String value, final String variable, final XPathTreeParser parser) {
        final StringBuilder out = new StringBuilder();
        int at = 0;
        while (at < value.length()) {
            final char c = value.charAt(at);
            if ((c == '{' || c == '}') && at + 1 < value.length() && value.charAt(at + 1) == c) {
                out.append(c).append(c);
                at += 2;
            } else if (c == '{') {
                final int close = value.indexOf('}', at + 1);
                if (close < 0) {
                    return null;
                }
                final String changed = expression(value.substring(at + 1, close), variable, parser);
                if (changed == null) {
                    return null;
                }
                out.append('{').append(changed).append('}');
                at = close + 1;
            } else if (c == '}') {
                return null;
            } else {
                out.append(c);
                at++;
            }
        }
        return out.toString();
    }

    // The expression with each read of the current value as the variable; null if it reads below the current value
    // or can't be parsed.
    private static String expression(final String text, final String variable, final XPathTreeParser parser) {
        if (text == null || text.isBlank()) {
            return text;
        }
        final XPathNode tree = XsltReader.parseOrNull(text, parser);
        if (tree == null) {
            return null;
        }
        final List<XPathTrees.Replacement> dots = new ArrayList<>();
        if (!visit(tree, true, dots, variable)) {
            return null;
        }
        return dots.isEmpty()
                ? text
                : XPathTrees.splice(tree.text(), dots);
    }

    // Each `.` read where the loop's current value is the focus (outside predicates and the steps after a path's
    // first), collected as the variable; false at a step reading below it there.
    private static boolean visit(final XPathNode node,
                                 final boolean focus,
                                 final List<XPathTrees.Replacement> dots,
                                 final String variable) {
        final String symbol = node.symbol();
        if ("$".equals(symbol)) {
            return true;
        }
        if (focus && ".".equals(symbol)) {
            dots.add(new XPathTrees.Replacement(node, "$" + variable));
            return true;
        }
        if (focus && (STEPS.contains(symbol) || ("*".equals(symbol) && node.size() == 0)
                      || XPathFunctions.AXES.contains(symbol)
                      || ("call".equals(symbol) && XPathFunctions.KIND_TESTS.contains(node.valueText())))) {
            return false;
        }
        final boolean step = "/".equals(symbol) || "//".equals(symbol) || "!".equals(symbol) || "[".equals(symbol);
        for (int i = 0; i < node.size(); i++) {
            // A path's (or a simple map's, or a filter's) first part keeps the focus; what follows has its own. A
            // path from the root has none of the loop's.
            final boolean keeps = step
                    ? focus && i == 0 && node.size() > 1
                    : focus;
            if (!visit(node.get(i), keeps, dots, variable)) {
                return false;
            }
        }
        return true;
    }


    // --------------------------------------------------------------------------------


    private record Change(XmlElement element, String name, String value) {

    }
}
