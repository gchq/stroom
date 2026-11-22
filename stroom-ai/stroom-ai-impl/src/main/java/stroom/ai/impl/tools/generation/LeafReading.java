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

/// One element (or Data entry, or attribute) an XSLT's rule writes, as [XsltReader] reads it: where it goes, and its
/// constant, the expression filling it (its variables replaced by what they select), or the xsl:for-each repeating it.
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
    private boolean leafLoop;
    private String guard;
    private GroupReading group;

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

    /// @return For a Data entry the generator repeats, or a bare leaf a loop writes (one per value its xsl:for-each
    /// selects, each the current value), what the loop selects, its variables replaced; else null.
    public String repeatSelect() {
        return repeatSelect;
    }

    /// @param repeatSelect What the loop repeating it selects, for a Data entry of each value.
    void setRepeatSelect(final String repeatSelect) {
        this.repeatSelect = repeatSelect;
    }

    /// @return True for a bare leaf element written a value each by its loop (not a Data): read back as a repeat
    /// only where the schema lets the leaf repeat in its own parent.
    public boolean leafLoop() {
        return leafLoop;
    }

    /// @param leafLoop True for a bare leaf element written a value each by its loop.
    void setLeafLoop(final boolean leafLoop) {
        this.leafLoop = leafLoop;
    }

    /// @param guard The test of the innermost xsl:if around it.
    void setGuard(final String guard) {
        this.guard = guard;
    }

    /// @return For a repeated group's element (written once per item by the generator's loop), the loop read;
    /// else null.
    public GroupReading group() {
        return group;
    }

    /// @param group The group's loop read.
    void setGroup(final GroupReading group) {
        this.group = group;
    }

    /// @return What it is known by: its path and Data name (with the Data it is in, as [FieldMapping#dataKey()]).
    public List<String> key() {
        return Arrays.asList(path, dataName == null || dataParent == null
                ? dataName
                : dataParent + "/" + dataName);
    }

    /// @param parser The parser of the XSLT it was read from.
    /// @return What it writes, to compare with another reading's: its repeat as written (a loop written by hand
    /// read as a group's too), its group's loop as read, its constant, or its expression whatever its spacing and
    /// brackets.
    public List<Object> form(final XPathTreeParser parser) {
        // A loop written by hand read as a group's is known by the loop as written, as it was before it was read so:
        // a kept entry the generator wrote it from (a repeat) is still the same.
        if (repeat != null && !repeat.isEmpty()) {
            return Arrays.asList("repeat", repeat);
        }
        if (group != null) {
            return group.form(parser);
        }
        if (value != null) {
            return Arrays.asList("value", value);
        }
        return Arrays.asList("expr", XsltReader.keyOf(expr, parser));
    }

    /// @return It as a map, keys in the order the reader fixtures pin: path, data_name, value, expr, repeat, guard.
    public Map<String, Object> toMap() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("path", path);
        out.put("data_name", dataName);
        out.put("value", value);
        out.put("expr", expr);
        out.put("repeat", repeat);
        out.put("guard", guard);
        if (dataParent != null) {
            // Only for a Data inside Data, so other readings keep the keys the reader fixtures pin.
            out.put("data_parent", dataParent);
        }
        if (group != null) {
            // Only for a group's loop, so other readings keep the keys the reader fixtures pin.
            out.put("group", group.toMap());
        }
        return out;
    }
}
