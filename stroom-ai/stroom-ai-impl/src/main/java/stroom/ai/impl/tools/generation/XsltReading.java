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

/// A translation XSLT read the way the generator writes it, by [XsltReader#read(String)] (the Stroom MCP server's
/// `xsltread.Reading`): its rules, what it reads (input namespace, root, records), its nil values, and the
/// stylesheet's own functions, maps and variables.
public final class XsltReading {

    private final List<RuleReading> rules = new ArrayList<>();
    private final String namespace;
    private final String root;
    private final String records;
    private final List<String> nilValues;
    private boolean unmatchedWarn;
    private final Map<String, Function> functions;
    private final Map<String, Map<String, String>> maps;
    private final Map<String, String> globals;
    private final XPathTreeParser parser;
    private final List<String> notes;
    private final List<String> imports;
    private final Map<String, String> prefixes;
    private final Map<String, KeyedFunction> keyed = new LinkedHashMap<>();

    /// @param namespace The stylesheet's xpath-default-namespace, or empty.
    /// @param root      What the start template matches.
    /// @param records   The records it applies templates to.
    /// @param nilValues The mapping's nil values.
    /// @param functions The XSLT's own single-expression functions.
    /// @param maps      Its stylesheet-level xsl:map variables.
    /// @param globals   Its other stylesheet-level variables.
    /// @param parser    The parser of its expressions.
    /// @param notes     What wasn't read.
    /// @param imports   Its xsl:import hrefs.
    /// @param prefixes  The namespaces bound for imported functions.
    XsltReading(final String namespace,
                final String root,
                final String records,
                final List<String> nilValues,
                final Map<String, Function> functions,
                final Map<String, Map<String, String>> maps,
                final Map<String, String> globals,
                final XPathTreeParser parser,
                final List<String> notes,
                final List<String> imports,
                final Map<String, String> prefixes) {
        this.namespace = namespace;
        this.root = root;
        this.records = records;
        this.nilValues = nilValues;
        this.functions = functions;
        this.maps = maps;
        this.globals = globals;
        this.parser = parser;
        this.notes = notes;
        this.imports = imports;
        this.prefixes = prefixes;
    }

    /// @return The rules read.
    public List<RuleReading> rules() {
        return rules;
    }

    /// @return The stylesheet's xpath-default-namespace (the input's), or empty.
    public String namespace() {
        return namespace;
    }

    /// @return What the start template matches.
    public String root() {
        return root;
    }

    /// @return The records the start template applies templates to, or empty.
    public String records() {
        return records;
    }

    /// @return The mapping's nil values, from the predicate the generator leaves them out with.
    public List<String> nilValues() {
        return nilValues;
    }

    /// @return Whether records no rule matches are logged (unmatched warn).
    public boolean unmatchedWarn() {
        return unmatchedWarn;
    }

    /// @param unmatchedWarn Whether records no rule matches are logged.
    void setUnmatchedWarn(final boolean unmatchedWarn) {
        this.unmatchedWarn = unmatchedWarn;
    }

    /// @return The XSLT's own single-expression functions, by name.
    public Map<String, Function> functions() {
        return functions;
    }

    /// @return Its stylesheet-level xsl:map variables: name to key to value.
    public Map<String, Map<String, String>> maps() {
        return maps;
    }

    /// @return Its other stylesheet-level variables (dictionaries): name to select.
    public Map<String, String> globals() {
        return globals;
    }

    /// @return The parser of its expressions (its namespace bindings).
    public XPathTreeParser parser() {
        return parser;
    }

    /// @return What wasn't read, or was read in a way to note.
    public List<String> notes() {
        return notes;
    }

    /// @return Its xsl:import hrefs, in order.
    public List<String> imports() {
        return imports;
    }

    /// @return The namespaces bound for imported functions, by prefix.
    public Map<String, String> prefixes() {
        return prefixes;
    }

    /// @return Its key=value functions (keyed_text), by name without `mcp:`: filled in by a rebuild.
    public Map<String, KeyedFunction> keyed() {
        return keyed;
    }

    /// @return It as the Python reading's fields (rules, namespace, root, records, nil_values, unmatched_warn,
    /// functions, maps, globals, notes, imports, prefixes, keyed).
    public Map<String, Object> toMap() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("rules", rules.stream().map(RuleReading::toMap).toList());
        out.put("namespace", namespace);
        out.put("root", root);
        out.put("records", records);
        out.put("nil_values", nilValues);
        out.put("unmatched_warn", unmatchedWarn);
        final Map<String, Object> functionsOut = new LinkedHashMap<>();
        functions.forEach((name, function) -> functionsOut.put(name, List.of(function.params(),
                function.body())));
        out.put("functions", functionsOut);
        out.put("maps", maps);
        out.put("globals", globals);
        out.put("notes", notes);
        out.put("imports", imports);
        out.put("prefixes", prefixes);
        final Map<String, Object> keyedOut = new LinkedHashMap<>();
        keyed.forEach((name, function) -> keyedOut.put(name, List.of(function.before(), function.after(),
                function.flags())));
        out.put("keyed", keyedOut);
        return out;
    }


    // --------------------------------------------------------------------------------


    /// One of the XSLT's own single-expression functions (an xsl:function whose body is one xsl:sequence).
    ///
    /// @param params Its parameters' names, in order.
    /// @param body   Its xsl:sequence's select.
    public record Function(List<String> params, String body) {

    }


    // --------------------------------------------------------------------------------


    /// A key=value function of the XSLT's (the generator's keyed_text): the regex around the key.
    ///
    /// @param before The regex before the key.
    /// @param after  The regex after it.
    /// @param flags  Its flags, or empty.
    public record KeyedFunction(String before, String after, String flags) {

    }
}
