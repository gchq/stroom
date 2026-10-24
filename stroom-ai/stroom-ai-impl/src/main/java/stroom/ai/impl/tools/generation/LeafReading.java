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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// One element (or Data entry, or attribute) an XSLT's rule writes, as [XsltReader] reads it (the Stroom MCP
/// server's `xsltread.Leaf`): where it goes, and its constant, the expression filling it (its variables replaced by
/// what they select), or the xsl:for-each repeating it.
public final class LeafReading {

    /// The step [XsltReader] puts in a path for a Data element holding Data entries: `Data@<its Name>`.
    static final String CONTAINER = "Data@";

    private final String path;
    private final String dataName;
    private final String dataParent;
    private String value;
    private String expr;
    private String repeat;
    private String repeatSelect;
    private String guard;

    /// @param path     Where the element goes, e.g. `EventDetail/Authenticate/User/Id`, or `.../@Attribute`; a
    ///                 Data entry inside Data elements with a [#CONTAINER] step for each.
    /// @param dataName For a Data entry, its Name; else null.
    LeafReading(final String path, final String dataName) {
        final List<String> steps = new ArrayList<>();
        final List<String> parents = new ArrayList<>();
        for (final String step : path.split("/", -1)) {
            if (step.startsWith(CONTAINER)) {
                parents.add(step.substring(CONTAINER.length()));
            } else {
                steps.add(step);
            }
        }
        this.path = String.join("/", steps);
        this.dataName = dataName;
        this.dataParent = parents.isEmpty()
                ? null
                : String.join("/", parents);
    }

    /// @return Where the element goes.
    public String path() {
        return path;
    }

    /// @return For a Data entry, its Name; else null.
    public String dataName() {
        return dataName;
    }

    /// @return For a Data entry inside Data elements, their Names, outermost first, `/`-separated; else null.
    public String dataParent() {
        return dataParent;
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

    /// @return For a Data entry the generator repeats (one per value its xsl:for-each selects, each the current
    /// value), what the loop selects, its variables replaced; else null.
    public String repeatSelect() {
        return repeatSelect;
    }

    /// @param repeatSelect What the loop repeating it selects, for a Data entry of each value.
    void setRepeatSelect(final String repeatSelect) {
        this.repeatSelect = repeatSelect;
    }

    /// @param guard The test of the innermost xsl:if around it.
    void setGuard(final String guard) {
        this.guard = guard;
    }

    /// @return What it is known by: its path and Data name (with the Data it is in, as [FieldMapping#dataKey()]).
    public List<String> key() {
        return Arrays.asList(path, dataName == null || dataParent == null
                ? dataName
                : dataParent + "/" + dataName);
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
        if (dataParent != null) {
            // Not in the Python reading, which had no Data inside Data.
            out.put("data_parent", dataParent);
        }
        return out;
    }
}
