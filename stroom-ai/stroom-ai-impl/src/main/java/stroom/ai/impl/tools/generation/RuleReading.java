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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// One rule of an XSLT, as [XsltReader] reads it (the Stroom MCP server's `xsltread.RuleReading`): the condition it
/// is chosen by, and each element its Event gets.
public final class RuleReading {

    private final String key;
    private String name;
    private final String test;
    private final List<LeafReading> leaves = new ArrayList<>();
    private final boolean drop;
    private String unknown;
    private final List<String> notes = new ArrayList<>();
    private final List<String> guards = new ArrayList<>();
    private final List<SharedCall> calls = new ArrayList<>();

    /// @param key  Its template's mode or name, `inline-N`, or `drop-NAME`.
    /// @param name Its name, from the comment the generator writes before it, or null.
    /// @param test Its condition (variables replaced), or null for the otherwise branch or the only rule.
    /// @param drop Whether it leaves records untranslated on purpose.
    RuleReading(final String key, final String name, final String test, final boolean drop) {
        this.key = key;
        this.name = name;
        this.test = test;
        this.drop = drop;
    }

    /// @return Its template's mode or name, `inline-N`, or `drop-NAME`.
    public String key() {
        return key;
    }

    /// @return Its name, from the comment the generator writes before it, or null.
    public String name() {
        return name;
    }

    /// @return Its condition, its variables replaced; null for the otherwise branch, or the only rule.
    public String test() {
        return test;
    }

    /// @return The elements its Event gets.
    public List<LeafReading> leaves() {
        return leaves;
    }

    /// @return Whether it leaves records untranslated on purpose.
    public boolean drop() {
        return drop;
    }

    /// @return Why it writes EventDetail/Unknown on purpose (allow_unknown), or null.
    public String unknown() {
        return unknown;
    }

    /// @return What wasn't read.
    public List<String> notes() {
        return notes;
    }

    /// @return While reading: the xsl:if tests around the element being read.
    List<String> guards() {
        return guards;
    }

    /// @return The named templates of an imported XSLT it calls in place.
    public List<SharedCall> calls() {
        return calls;
    }

    /// @param name Its name.
    void setName(final String name) {
        this.name = name;
    }

    /// @param unknown Why it writes EventDetail/Unknown on purpose.
    void setUnknown(final String unknown) {
        this.unknown = unknown;
    }

    /// @return It as the Python reading's fields: key, name, test, leaves, drop, unknown, notes, guards, calls.
    public Map<String, Object> toMap() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", key);
        out.put("name", name);
        out.put("test", test);
        out.put("leaves", leaves.stream().map(LeafReading::toMap).toList());
        out.put("drop", drop);
        out.put("unknown", unknown);
        out.put("notes", notes);
        out.put("guards", guards);
        out.put("calls", calls.stream().map(SharedCall::toMap).toList());
        return out;
    }
}
