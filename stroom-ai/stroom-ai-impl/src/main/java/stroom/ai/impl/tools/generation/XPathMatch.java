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

import java.util.LinkedHashMap;
import java.util.Map;

/// What fills each slot of a pattern an expression was matched against ([XPathTrees#unify(XPathNode, XPathNode)]):
/// the Stroom MCP server's `xpathtree.Match`. An expression slot ([XPathTrees#exprSlot(int)]) is bound to the node
/// filling it, a string slot ([XPathTrees#stringSlot(int)]) to the string.
public final class XPathMatch {

    private final Map<String, XPathNode> nodes = new LinkedHashMap<>();

    /// @param n An expression slot's number.
    /// @return The node filling it.
    public XPathNode expr(final int n) {
        return nodes.get("E" + n);
    }

    /// @param n A string slot's number.
    /// @return The string filling it.
    public String string(final int n) {
        return nodes.get("L" + n).valueText();
    }

    /// @param name A slot's name: `E1` for expression slot 1, `L1` for string slot 1.
    /// @return Whether it is filled.
    public boolean has(final String name) {
        return nodes.containsKey(name);
    }

    /// @return Each filled slot's name and what fills it.
    Map<String, XPathNode> nodes() {
        return nodes;
    }
}
