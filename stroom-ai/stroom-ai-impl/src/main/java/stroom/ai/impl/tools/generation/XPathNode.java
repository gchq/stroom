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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// One node of a parsed XPath expression (the Stroom MCP server's `xpathtree.Node`): its symbol, as elementpath
/// names it (`/`, `[`, `(name)`, `(string)`, `(integer)`, `$`, `:`, `if`, `=`, ...; `call` for a function call;
/// `map` for a map constructor), its value (a name, a string's value, a number, a function's name as written, an
/// operator's symbol), its children, and its exact text in the expression it was parsed from, with where that is.
///
/// Nodes are compared by [#key()], which is what the expression is whatever its spacing; never by identity.
public final class XPathNode {

    private final String symbol;
    private final Object value;
    private final List<XPathNode> children;
    private final String text;
    private final int start;
    private final int end;

    /// @param symbol   The node's symbol.
    /// @param value    Its value: a String, a `BigInteger` (`(integer)`), a [BigDecimal]
    ///                 (`(decimal)`), a Double (`(float)`), or null (`map`).
    /// @param children Its children.
    /// @param text     Its text, exactly as written.
    /// @param start    Where its text starts in the expression parsed.
    /// @param end      Where its text ends.
    public XPathNode(final String symbol,
                     final Object value,
                     final List<XPathNode> children,
                     final String text,
                     final int start,
                     final int end) {
        this.symbol = symbol;
        this.value = value;
        this.children = List.copyOf(children);
        this.text = text;
        this.start = start;
        this.end = end;
    }

    /// @return The node's symbol, e.g. `/`, `(name)`, `call`.
    public String symbol() {
        return symbol;
    }

    /// @return Its value: a name, a string's value, a number, a function's name as written; null for a map.
    public Object value() {
        return value;
    }

    /// @return Its value as Python's `str()` gives it (`None` for null).
    public String valueText() {
        return value == null
                ? "None"
                : value.toString();
    }

    /// @return Its children.
    public List<XPathNode> children() {
        return children;
    }

    /// @param n A child's index.
    /// @return That child.
    public XPathNode get(final int n) {
        return children.get(n);
    }

    /// @return How many children it has.
    public int size() {
        return children.size();
    }

    /// @return Its text, exactly as written in the expression.
    public String text() {
        return text;
    }

    /// @return Where its text starts in the expression parsed.
    public int start() {
        return start;
    }

    /// @return Where its text ends in the expression parsed.
    public int end() {
        return end;
    }

    /// @return What the expression is, whatever its spacing: the symbol, the value and the children's keys.
    /// Compared to tell one expression from another.
    public List<Object> key() {
        final List<Object> keys = new ArrayList<>(children.size());
        for (final XPathNode child : children) {
            keys.add(child.key());
        }
        return Arrays.asList(symbol, value instanceof final BigDecimal decimal
                ? decimal.stripTrailingZeros()
                : value, keys);
    }

    /// @return The node and every node below it, depth first, the node first.
    public List<XPathNode> walk() {
        final List<XPathNode> out = new ArrayList<>();
        walk(out);
        return out;
    }

    private void walk(final List<XPathNode> out) {
        out.add(this);
        for (final XPathNode child : children) {
            child.walk(out);
        }
    }

    /// @return The node, its place and its text, for messages.
    @Override
    public String toString() {
        return symbol + " " + valueText() + " [" + start + "," + end + "] " + text;
    }
}
