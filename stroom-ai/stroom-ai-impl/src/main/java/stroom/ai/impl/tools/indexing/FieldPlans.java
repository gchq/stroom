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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// What an index plan's fields mean: which event paths a plan source names (`*` for one element, `//` for any
/// between), how often a source is populated, the logical types and what each backend makes of them.
public final class FieldPlans {

    /// The logical types a planned field may have.
    public static final List<String> LOGICAL_TYPES = List.of("id", "keyword", "text", "date", "long", "double",
            "boolean", "ip");
    /// The backends.
    public static final List<String> BACKENDS = List.of("lucene", "elasticsearch");
    /// Each logical type as a Lucene index doc's field type and analyzer. Lucene has no working KEYWORD field type:
    /// a keyword is TEXT with the KEYWORD analyzer (found against Stroom 7.13).
    public static final Map<String, List<String>> LUCENE = lucene();
    /// Each logical type as an Elasticsearch mapping type.
    public static final Map<String, String> ELASTIC = elastic();
    /// Elasticsearch's metadata fields: a document holding one at its top level is rejected.
    public static final List<String> ES_METADATA = List.of("_id", "_index", "_source", "_routing", "_ignored",
            "_ignored_source", "_seq_no", "_primary_term", "_version", "_field_names", "_doc_count", "_tier",
            "_data_stream_timestamp", "_meta", "_nested_path", "_tsid", "_size", "_feature");
    /// Elasticsearch's built-in component template mapping ECS fields (8.13 and later): composed by an ECS plan's
    /// template.
    public static final String ECS_COMPONENT = EcsSchema.COMPONENT;
    /// The fields of a discovery plan: name, type and source (none: the discovery's timestamp field).
    public static final List<List<String>> DISCOVERY_FIELDS = List.of(
            List.of("StreamId", "id", "stroom:stream-id()"),
            List.of("EventId", "id", "stroom:record-no()"),
            List.of("@timestamp", "date", ""));

    private static final Pattern ANY_ACTION = Pattern.compile("^EventDetail/Network/[^/]+/(?=.)");

    private FieldPlans() {
        // Static only.
    }

    /// @param source A plan field's source.
    /// @return True if it names several paths (`*`, `//`, or a union of paths).
    static boolean wild(final String source) {
        return source.contains("*") || source.contains("//") || branches(source).size() > 1;
    }

    /// The paths of a union (`EventSource/User | EventDetail//User`), each as it is; a source of one path is that
    /// path. A `|` inside a predicate or a quoted value is part of its path.
    ///
    /// @param source A plan field's source.
    /// @return Its paths, stripped.
    public static List<String> branches(final String source) {
        final List<String> out = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < source.length(); i++) {
            final char ch = source.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (ch == '[' || ch == '(') {
                depth++;
            } else if (ch == ']' || ch == ')') {
                depth--;
            } else if (ch == '|' && depth == 0) {
                out.add(current.toString().strip());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        out.add(current.toString().strip());
        return out;
    }

    /// Whether a plan source names this event path: `*` stands for one element, as in the XPath it is
    /// (EventDetail/Network/*/Source/Device/IPAddress names the source address of a Permit, a Deny, a Connect...),
    /// and `//` for any elements between (EventDetail//Object/Name names View/Object/Name and
    /// Export/Source/Object/Name). Seen: an agent's EventDetail//Object/Name taken as matching nothing, its paths
    /// reported as not mapped.
    ///
    /// @param source A plan field's source.
    /// @param path   An event path, e.g. `EventDetail/View/Object/Name`.
    /// @return True if the source names it.
    public static boolean sourceMatches(final String source, final String path) {
        if (!wild(source)) {
            return source.equals(path);
        }
        final List<String> branches = branches(source);
        if (branches.size() > 1) {
            return branches.stream().anyMatch(branch -> sourceMatches(branch, path));
        }
        if (!source.contains("//")) {
            final String[] want = ToolText.strip(source, "/").split("/", -1);
            final String[] have = ToolText.strip(path, "/").split("/", -1);
            if (want.length != have.length) {
                return false;
            }
            for (int i = 0; i < want.length; i++) {
                if (!"*".equals(want[i]) && !want[i].equals(have[i])) {
                    return false;
                }
            }
            return true;
        }
        final String regex = (source.startsWith("//")
                ? "(?:[^/]+/)*"
                : "") + descendants(ToolText.strip(source, "/"));
        return Pattern.compile(regex, Pattern.DOTALL).matcher(ToolText.strip(path, "/")).matches();
    }

    // The source as a regex: '*' one element, '//' any elements between, the rest as it is.
    private static String descendants(final String source) {
        final StringBuilder sb = new StringBuilder();
        final StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < source.length()) {
            if (source.charAt(i) == '*') {
                sb.append(quote(literal)).append("[^/]+");
                i++;
            } else if (source.startsWith("//", i)) {
                sb.append(quote(literal)).append("/(?:[^/]+/)*");
                i += 2;
            } else {
                literal.append(source.charAt(i));
                i++;
            }
        }
        return sb.append(quote(literal)).toString();
    }

    private static String quote(final StringBuilder literal) {
        final String text = literal.toString();
        literal.setLength(0);
        return text.isEmpty()
                ? ""
                : Pattern.quote(text);
    }

    /// @param source     A plan field's source.
    /// @param population The % of events each path is populated in.
    /// @return How often the source is populated: a wildcard's paths summed (an event has one Network action), at
    /// most 100; null if none of its paths is.
    public static Double populationOf(final String source, final Map<String, ? extends Number> population) {
        if (!wild(source)) {
            final Number share = population.get(source);
            return share == null
                    ? null
                    : share.doubleValue();
        }
        double sum = 0;
        boolean hit = false;
        for (final Map.Entry<String, ? extends Number> entry : population.entrySet()) {
            if (sourceMatches(source, entry.getKey())) {
                sum += entry.getValue().doubleValue();
                hit = true;
            }
        }
        return hit
                ? Math.min(100.0, sum)
                : null;
    }

    /// @param path An event path.
    /// @return A Network path with its action as `*`: the field is the same whichever action the event records.
    public static String anyAction(final String path) {
        return ANY_ACTION.matcher(path).replaceFirst("EventDetail/Network/*/");
    }

    /// @param path A plain path, e.g. `EventDetail/Data[@Name='a/b']/@Value`.
    /// @return Its steps, a predicate kept with its step (`Data[@Name='a/b']` is one step).
    static List<String> steps(final String path) {
        final List<String> steps = new ArrayList<>();
        int depth = 0;
        final StringBuilder current = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            final char ch = path.charAt(i);
            if (ch == '/' && depth == 0) {
                steps.add(current.toString());
                current.setLength(0);
                continue;
            }
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
            }
            current.append(ch);
        }
        steps.add(current.toString());
        return steps;
    }

    /// @param text An XPath.
    /// @return It as an attribute value written between double quotes. Seen: `Data[@Name="time"]` was written as
    /// it is, and the XSLT didn't parse.
    static String quoted(final String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private static Map<String, List<String>> lucene() {
        final Map<String, List<String>> types = new LinkedHashMap<>();
        types.put("id", List.of("ID", "KEYWORD"));
        types.put("keyword", List.of("TEXT", "KEYWORD"));
        types.put("text", List.of("TEXT", "ALPHA_NUMERIC"));
        types.put("date", List.of("DATE", "KEYWORD"));
        types.put("long", List.of("LONG", "KEYWORD"));
        types.put("double", List.of("DOUBLE", "KEYWORD"));
        types.put("boolean", List.of("TEXT", "KEYWORD"));
        types.put("ip", List.of("TEXT", "KEYWORD"));
        return Collections.unmodifiableMap(types);
    }

    private static Map<String, String> elastic() {
        final Map<String, String> types = new LinkedHashMap<>();
        types.put("id", "long");
        types.put("keyword", "keyword");
        types.put("text", "text");
        types.put("date", "date");
        types.put("long", "long");
        types.put("double", "double");
        types.put("boolean", "boolean");
        types.put("ip", "ip");
        return Collections.unmodifiableMap(types);
    }
}
