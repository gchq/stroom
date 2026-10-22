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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// One element (or Data entry, or attribute) an XSLT's rule writes, as [XsltReader] reads it (the Stroom MCP
/// server's `xsltread.Leaf`): where it goes, and its constant, the expression filling it (its variables replaced by
/// what they select), or the xsl:for-each repeating it.
public final class LeafReading {

    private final String path;
    private final String dataName;
    private String value;
    private String expr;
    private String repeat;
    private String guard;

    /// @param path     Where the element goes, e.g. `EventDetail/Authenticate/User/Id`, or `.../@Attribute`.
    /// @param dataName For a Data entry, its Name; else null.
    LeafReading(final String path, final String dataName) {
        this.path = path;
        this.dataName = dataName;
    }

    /// @return Where the element goes.
    public String path() {
        return path;
    }

    /// @return For a Data entry, its Name; else null.
    public String dataName() {
        return dataName;
    }

    /// @return Its constant, or null.
    public String value() {
        return value;
    }

    /// @return The expression filling it, its variables replaced; or null.
    public String expr() {
        return expr;
    }

    /// @return The xsl:for-each repeating it, as written (compared whole), or null.
    public String repeat() {
        return repeat;
    }

    /// @return The test of the innermost xsl:if around it (not compared), or null.
    public String guard() {
        return guard;
    }

    /// @param value Its constant.
    void setValue(final String value) {
        this.value = value;
    }

    /// @param expr The expression filling it.
    void setExpr(final String expr) {
        this.expr = expr;
    }

    /// @param repeat The xsl:for-each repeating it, as written.
    void setRepeat(final String repeat) {
        this.repeat = repeat;
    }

    /// @param guard The test of the innermost xsl:if around it.
    void setGuard(final String guard) {
        this.guard = guard;
    }

    /// @return What it is known by: its path and Data name.
    public List<String> key() {
        return Arrays.asList(path, dataName);
    }

    /// @param parser The parser of the XSLT it was read from.
    /// @return What it writes, to compare with another reading's: its constant, its repeat as written, or its
    /// expression whatever its spacing and brackets.
    public List<Object> form(final XPathTreeParser parser) {
        if (value != null) {
            return Arrays.asList("value", value);
        }
        if (repeat != null && !repeat.isEmpty()) {
            return Arrays.asList("repeat", repeat);
        }
        return Arrays.asList("expr", XsltReader.keyOf(expr, parser));
    }

    /// @return It as the Python reading's fields: path, data_name, value, expr, repeat, guard.
    public Map<String, Object> toMap() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("path", path);
        out.put("data_name", dataName);
        out.put("value", value);
        out.put("expr", expr);
        out.put("repeat", repeat);
        out.put("guard", guard);
        return out;
    }
}
