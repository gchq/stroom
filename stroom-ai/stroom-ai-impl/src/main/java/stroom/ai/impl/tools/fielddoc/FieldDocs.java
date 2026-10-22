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

package stroom.ai.impl.tools.fielddoc;

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.cef.CefKeys;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.Lookup;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.EventSchema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// What the Field mapping section of a pipeline's documentation is made of, whatever the pipeline: the layouts, a
/// field's source and conditions in words, the event-logging schema's words for a path, the field descriptions kept
/// with the documentation, and the grouping of event types (or action elements, or CEF kinds) with roughly the same
/// fields. Ported from the Stroom MCP server's `utils/fielddoc.py`; the translation's tables are [EventFieldDocs],
/// an index's [IndexFieldDocs].
public final class FieldDocs {

    /// The event-logging namespace.
    public static final String EVT = XsltGenerator.EVT;
    /// How many of a field's sampled values a cell shows.
    public static final int VALUES_SHOWN = 3;
    /// The Field mapping layout by event type (the default): the fields common to every event type in one table,
    /// then a table per event type.
    public static final String BY_EVENT_TYPE = "by_event_type";
    /// The compact Field mapping layout: two tables (or one, for an index).
    public static final String COMPACT = "compact";
    /// The layouts; an AGENTS doc's Documentation section or the user picks one.
    public static final List<String> LAYOUTS = List.of(BY_EVENT_TYPE, COMPACT);
    /// Groups of fields (event types, action elements, CEF kinds) sharing at least this share of their fields (a
    /// path's action element counted as any) are shown together, under one heading naming each (asked for by the
    /// user: Send and Receive, say).
    public static final double SIMILAR = 0.75;
    /// A field nothing describes, whose sampled values share no kind.
    public static final String NOT_DESCRIBED = "Not described: no plan or schema covers it.";

    /// What the server says of the event ids an index document carries.
    static final Map<String, String> IDS = Map.of(
            "@StreamId", "The id of the Events stream the event came from.",
            "@EventId", "The event's number in its stream; with StreamId, it identifies the event.");

    private static final Pattern ACTION_STEP = Pattern.compile(
            "^(EventDetail/)(?!(?:TypeId|Description|Classification|Purpose|Data)\\b)([^/\\[]+)(?=/)");
    private static final Pattern DESCRIPTIONS = Pattern.compile("<!-- stroom-mcp field descriptions\\n(.*?)\\n-->",
            Pattern.DOTALL);
    private static final Pattern IP = PythonStrings.re("^(\\d{1,3}\\.){3}\\d{1,3}$|^[0-9a-fA-F:]+:[0-9a-fA-F:]*$");
    private static final Pattern EMAIL = PythonStrings.re("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TIME = PythonStrings.re("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}");
    private static final Pattern NUMBER = PythonStrings.re("^-?\\d+(\\.\\d+)?$");
    private static final Pattern DATA = Pattern.compile("Data\\[@Name='([^']+)'\\]/@Value$");
    private static final Pattern PREDICATE = Pattern.compile("\\[[^\\]]*\\]");
    // The schema's descriptive openings, which say nothing about the field itself.
    private static final Pattern BOILERPLATE = PythonStrings.re("^This element (?:contains (?:information about|data "
                                                                + "relating to)|provides details of|describes|details)"
                                                                + "\\s+(?:the details of\\s+)?(?:the\\s+)?");
    private static final Pattern CAMEL_GAP = Pattern.compile("(?<=[a-z])(?=[A-Z])");
    private static final Pattern GENERIC = PythonStrings.re("\\bthe (object|device)\\b");
    private static final Pattern EXAMPLE_TAIL = PythonStrings.re(",?\\s+e\\.?g\\.?\\s.*$");
    private static final List<String> NETWORK_ACTIONS = List.of("Permit", "Deny", "Connect", "Open", "Close", "Bind",
            "Send", "Receive", "Listen");

    private FieldDocs() {
        // Static only.
    }

    /// `readable`: an xpath with its field selectors written as the field names, for a reader (the kept mapping's
    /// own, [KeptMapping#readable(String)]).
    ///
    /// @param xpath An xpath, e.g. `normalize-space(data[@name='username']/@value)`.
    /// @return It for a reader, e.g. `normalize-space(username)`.
    public static String readable(final String xpath) {
        return KeptMapping.readable(xpath == null
                ? ""
                : xpath);
    }

    /// `condition_text` (the kept mapping's own, [KeptMapping#conditionText(Map)]).
    ///
    /// @param condition A rule's condition.
    /// @return It for a reader, e.g. `` `action` = login ``.
    public static String conditionText(final Condition condition) {
        return KeptMapping.conditionText(condition.toJson(DumpMode.ALL));
    }

    /// @param conditions A rule's conditions.
    /// @return Them for a reader, joined by "and" ("" for none).
    public static String conditionsText(final List<Condition> conditions) {
        return conditions.stream().map(FieldDocs::conditionText).collect(Collectors.joining(" and "));
    }

    /// `source_text`: where an element's value comes from, for a reader: a constant in quotes, a field in code,
    /// and how it is converted (lookup, dictionary, transform, time pattern, value map, default, one per value).
    ///
    /// @param entry A field mapping.
    /// @return Its source, e.g. `` `host`, or "unknown" when empty ``.
    public static String sourceText(final FieldMapping entry) {
        if (entry.value() != null) {
            return "\"" + entry.value() + "\"";
        }
        String text;
        final Lookup lookup = entry.lookup();
        if (lookup != null) {
            final String key = PythonStrings.truthy(lookup.field())
                    ? "`" + lookup.field() + "`"
                    : "`" + readable(lookup.xpath()) + "`";
            text = "lookup `" + lookup.map() + "` by " + key + (PythonStrings.truthy(lookup.path())
                    ? " -> `" + lookup.path() + "`"
                    : "");
        } else if (PythonStrings.truthy(entry.anyOf())) {
            text = "first of " + entry.anyOf().stream().map(f -> "`" + f + "`").collect(Collectors.joining(", "));
        } else if (entry.field() != null) {
            text = "`" + entry.field() + "`";
        } else {
            text = "`" + readable(entry.xpath()) + "`";
        }
        if (PythonStrings.truthy(entry.dictionary())) {
            text += " via dictionary `" + entry.dictionary() + "`";
        }
        if (PythonStrings.truthy(entry.transform())) {
            text += " (" + entry.transform() + ")";
        }
        if ("record".equals(entry.scope())) {
            text += " (of the record)";
        }
        if (PythonStrings.truthy(entry.timeFormat())) {
            text += " (time " + entry.timeFormat() + (PythonStrings.truthy(entry.timezone())
                    ? ", " + entry.timezone()
                    : "") + ")";
        } else if (PythonStrings.truthy(entry.timezone())) {
            text += " (" + entry.timezone() + ")";
        }
        if (entry.hasMap()) {
            text += ": " + entry.map().entrySet().stream()
                    .map(e -> e.getKey() + " -> " + e.getValue())
                    .collect(Collectors.joining(", "));
            if (entry.defaultValue() != null) {
                text += "; otherwise " + entry.defaultValue();
            }
        } else if (entry.defaultValue() != null) {
            text += ", or \"" + entry.defaultValue() + "\" when empty";
        }
        if (entry.repeat()) {
            text += ", one per value";
        }
        return text;
    }

    /// `any_action_path`: a path with its action element as `*`.
    ///
    /// @param path An event path, e.g. `EventDetail/Send/Source/User/Id`; null is taken as "".
    /// @return It with the action as `*`, e.g. `EventDetail/*/Source/User/Id`.
    public static String anyActionPath(final String path) {
        return ACTION_STEP.matcher(path == null
                ? ""
                : path).replaceFirst("$1*");
    }

    /// `similar_groups`: the groups, in order, those with roughly the same fields together: each joins the first
    /// earlier cluster whose fields it shares at least `share` of (of the two together); the rest stand alone.
    ///
    /// @param signatures Each group's fields, in order.
    /// @param share      The share of fields in common that puts two together.
    /// @return The groups' names, clustered.
    public static List<List<String>> similarGroups(final Map<String, ? extends Set<String>> signatures,
                                                   final double share) {
        final List<List<String>> members = new ArrayList<>();
        final List<Set<String>> together = new ArrayList<>();
        for (final Map.Entry<String, ? extends Set<String>> entry : signatures.entrySet()) {
            final Set<String> fields = entry.getValue();
            boolean joined = false;
            for (int i = 0; i < members.size(); i++) {
                final Set<String> cluster = together.get(i);
                if (!fields.isEmpty() && !cluster.isEmpty() && shared(fields, cluster) >= share * union(fields,
                        cluster)) {
                    members.get(i).add(entry.getKey());
                    cluster.addAll(fields);
                    joined = true;
                    break;
                }
            }
            if (!joined) {
                members.add(new ArrayList<>(List.of(entry.getKey())));
                together.add(new HashSet<>(fields));
            }
        }
        return members;
    }

    /// [#similarGroups(Map, double)] at [#SIMILAR].
    ///
    /// @param signatures Each group's fields, in order.
    /// @return The groups' names, clustered.
    public static List<List<String>> similarGroups(final Map<String, ? extends Set<String>> signatures) {
        return similarGroups(signatures, SIMILAR);
    }

    /// `combined_path`: one path for a row shared by groups: as it is when they agree; with the action elements as
    /// `(Send|Receive)` when only those differ; else each on its own line.
    ///
    /// @param paths The groups' paths.
    /// @return The one path.
    public static String combinedPath(final List<String> paths) {
        final List<String> distinct = new ArrayList<>(new LinkedHashSet<>(paths));
        if (distinct.size() == 1) {
            return distinct.getFirst();
        }
        final Set<String> anyAction = distinct.stream().map(FieldDocs::anyActionPath).collect(Collectors.toSet());
        if (anyAction.size() == 1) {
            final List<String> actions = new ArrayList<>();
            for (final String path : distinct) {
                final Matcher matcher = ACTION_STEP.matcher(path);
                if (!matcher.find()) {
                    // Python's match() would be None here, and fail.
                    throw new IllegalArgumentException("No action element in " + path);
                }
                actions.add(matcher.group(2));
            }
            final Matcher first = ACTION_STEP.matcher(distinct.getFirst());
            if (first.find()) {
                return first.group(1) + "(" + String.join("|", actions) + ")" + distinct.getFirst()
                        .substring(first.end());
            }
            return distinct.getFirst();
        }
        return String.join("\n", distinct);
    }

    /// `data_key`: the key one description of a Data name goes under, for every element it is under.
    ///
    /// @param name A Data element's Name.
    /// @return The key, e.g. `Data[@Name='Folder']`.
    public static String dataKey(final String name) {
        return "Data[@Name='" + name + "']";
    }

    /// `read_descriptions`: the field descriptions kept in a Documentation doc's text.
    ///
    /// @param markdown The doc's text, or null.
    /// @return Field to description; none if there is no block or it doesn't read.
    public static Map<String, String> readDescriptions(final String markdown) {
        final Matcher matcher = DESCRIPTIONS.matcher(markdown == null
                ? ""
                : markdown);
        final Object found;
        try {
            found = matcher.find()
                    ? PyJson.loads(matcher.group(1))
                    : Map.of();
        } catch (final RuntimeException e) {
            return Map.of();
        }
        final Map<String, String> out = new LinkedHashMap<>();
        if (found instanceof final Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k instanceof final String key && v instanceof final String value) {
                    out.put(key, value);
                }
            });
        }
        return out;
    }

    /// `descriptions_block`: the field descriptions as the hidden block they are kept in, in the Documentation
    /// doc's text (`--` written so as not to end the comment).
    ///
    /// @param descriptions Field to description.
    /// @return The block, or "" for none.
    public static String descriptionsBlock(final Map<String, String> descriptions) {
        if (descriptions.isEmpty()) {
            return "";
        }
        final String body = PythonStrings.jsonIndented(descriptions).replace("--", "-\\u002d");
        return "<!-- stroom-mcp field descriptions\n" + body + "\n-->";
    }

    /// `schema_description`: what the event-logging schema says the source path holds: the first sentence of its
    /// documentation, without the schema's descriptive opening; the element a Data or attribute belongs to.
    ///
    /// @param schema The schema, or null.
    /// @param source An event path, e.g. `EventTime/TimeCreated`.
    /// @return What it holds, or "".
    public static String schemaDescription(final EventSchema schema, final String source) {
        if (IDS.containsKey(source)) {
            return IDS.get(source);
        }
        final Matcher data = DATA.matcher(source);
        final boolean isData = data.find();
        String path = PREDICATE.matcher(isData
                ? PythonStrings.split(source, "/Data[").getFirst()
                : source).replaceAll("");
        String attribute = null;
        if (path.contains("/@")) {
            final int at = path.lastIndexOf("/@");
            attribute = path.substring(at + 2);
            path = path.substring(0, at);
        }
        String said = "";
        if (schema != null && path.contains("*")) {
            // Any Network action: what the schema says of the element under the first action it takes (Bind).
            for (final String action : NETWORK_ACTIONS) {
                try {
                    return schema.describe(schema.resolve(path.replaceFirst(Pattern.quote("*"), action)));
                } catch (final RuntimeException e) {
                    // Not under this action: the next.
                }
            }
        }
        if (schema != null) {
            try {
                said = schema.describe(schema.resolve(path));
            } catch (final RuntimeException e) {
                said = "";
            }
        }
        if (!said.isEmpty()) {
            // 'This element contains information about the time the event was created.' -> 'Time the event was
            // created.'
            final String trimmed = BOILERPLATE.matcher(said).replaceFirst("");
            if (!trimmed.equals(said) && !trimmed.isEmpty()) {
                said = trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).toUpperCase()
                       + trimmed.substring(trimmed.offsetByCodePoints(0, 1));
            }
        }
        final List<String> parts = PythonStrings.split(PythonStrings.strip(path, "/"), "/");
        if (!said.isEmpty() && parts.size() > 1) {
            // Ids and names inherit a base type's words ('the object', 'the device'): name the element they
            // belong to.
            final String parent = CAMEL_GAP.matcher(parts.get(parts.size() - 2)).replaceAll(" ").toLowerCase();
            final Matcher generic = GENERIC.matcher(said);
            if (generic.find() && !generic.group(1).equals(parent)) {
                final int at = said.indexOf(generic.group(0));
                final String named = said.substring(0, at) + "the " + parent + said.substring(at + generic.group(0)
                        .length());
                said = EXAMPLE_TAIL.matcher(named).replaceAll(".");
            }
        }
        if (isData) {
            // The element the Data is under, not its description (seen in testing: every Data under Send was
            // described as 'Used for send events that are at a higher level than a network/send event').
            final String stripped = PythonStrings.strip(path, "/");
            final String under = stripped.isEmpty()
                    ? ""
                    : PythonStrings.split(stripped, "/").getLast();
            return "The `" + data.group(1) + "` value recorded in a Data element" + (under.isEmpty()
                    ? "."
                    : " of " + under + ".");
        }
        if (attribute != null) {
            return "The `" + attribute + "` attribute of " + PythonStrings.split(path, "/").getLast() + (said.isEmpty()
                    ? "."
                    : ": " + said);
        }
        return said;
    }

    /// `field_description`: a field's description: what it is (the plan's description, the schema's, or its
    /// source), and only what the other columns do not show (In sample has how often, Sample values the values):
    /// several values in one document. A field nothing describes gets the kind of values the sample shows, when
    /// they share one, else says so.
    ///
    /// @param what        What the field is, or "".
    /// @param perDocument The field's values in each sampled document, or null for no sample.
    /// @return The description, each part ending with a full stop.
    public static String fieldDescription(final String what, final List<List<String>> perDocument) {
        final List<String> parts = new ArrayList<>();
        if (what != null && !PythonStrings.strip(what).isEmpty()) {
            parts.add(PythonStrings.strip(what));
        }
        if (perDocument != null) {
            final List<List<String>> holding = perDocument.stream()
                    .map(values -> values.stream().filter(v -> !v.isEmpty()).toList())
                    .toList();
            final List<String> values = new ArrayList<>(new LinkedHashSet<>(holding.stream()
                    .flatMap(List::stream)
                    .toList()));
            if (parts.isEmpty()) {
                parts.add(valueKind(values));
            }
            if (holding.stream().anyMatch(values1 -> values1.size() > 1)) {
                parts.add("Several values per document.");
            }
        }
        return parts.stream()
                .map(part -> part.endsWith(".")
                        ? part
                        : part + ".")
                .collect(Collectors.joining(" "));
    }

    /// `action_of`: the action element an event-logging path is under.
    ///
    /// @param source An event path, e.g. `EventDetail/Authenticate/User/Id`, or null.
    /// @return Its action (`Authenticate`), or null for what any kind of event has (EventTime, EventSource, TypeId,
    /// Description, the ids) or no path at all.
    public static String actionOf(final String source) {
        final List<String> parts = PythonStrings.split(PythonStrings.strip(PREDICATE.matcher(source == null
                ? ""
                : source).replaceAll(""), "/"), "/");
        return parts.size() > 1 && "EventDetail".equals(parts.get(0)) && !CefKeys.NOT_ACTION.contains(parts.get(1))
               && !"*".equals(parts.get(1))
                ? parts.get(1)
                : null;
    }

    /// @param text Text.
    /// @return It as a table cell: its pipes escaped, its lines as `<br>`.
    static String cell(final String text) {
        return text.replace("|", "\\|").replace("\n", "<br>");
    }

    /// @param cells The cells.
    /// @return A table row of them.
    static String row(final List<String> cells) {
        return "| " + cells.stream().map(FieldDocs::cell).collect(Collectors.joining(" | ")) + " |";
    }

    /// @param cells The cells.
    /// @return A table row of them.
    static String row(final String... cells) {
        return row(List.of(cells));
    }

    /// @param head The column names.
    /// @return The table's head row and its rule.
    static List<String> head(final List<String> head) {
        return List.of(row(head), row(head.stream().map(h -> "---").toList()));
    }

    /// @param values The values.
    /// @param quote  True to quote each.
    /// @return The first few, each on its own line, and how many more.
    static String shown(final List<String> values, final boolean quote) {
        final List<String> out = new ArrayList<>();
        for (final String value : values.subList(0, Math.min(values.size(), VALUES_SHOWN))) {
            out.add(quote
                    ? "\"" + value + "\""
                    : value);
        }
        final int more = values.size() - VALUES_SHOWN;
        if (more > 0) {
            out.add("... and " + more + " more");
        }
        return String.join("\n", out);
    }

    /// @param values The values.
    /// @return The first three distinct values, cut to 40 characters, in code, and how many more; or that the
    /// sample has none.
    static String values(final List<String> values) {
        final List<String> distinct = new ArrayList<>(new LinkedHashSet<>(values.stream()
                .filter(v -> !v.isEmpty())
                .toList()));
        if (distinct.isEmpty()) {
            return "(none in the sample)";
        }
        return distinct.subList(0, Math.min(3, distinct.size())).stream()
                       .map(v -> "`" + PythonStrings.head(v, 40) + "`")
                       .collect(Collectors.joining(", ")) + (distinct.size() > 3
                ? " (+" + (distinct.size() - 3) + ")"
                : "");
    }

    // The values' common kind, when they share one, else NOT_DESCRIBED.
    private static String valueKind(final List<String> values) {
        if (!values.isEmpty()) {
            final Map<String, Pattern> kinds = new LinkedHashMap<>();
            kinds.put("IP addresses", IP);
            kinds.put("Email addresses", EMAIL);
            kinds.put("Timestamps", TIME);
            kinds.put("Numbers", NUMBER);
            for (final Map.Entry<String, Pattern> kind : kinds.entrySet()) {
                if (values.stream().allMatch(v -> kind.getValue().matcher(v).lookingAt())) {
                    return kind.getKey();
                }
            }
        }
        return NOT_DESCRIBED;
    }

    private static int shared(final Set<String> a, final Set<String> b) {
        return (int) a.stream().filter(b::contains).count();
    }

    private static int union(final Set<String> a, final Set<String> b) {
        final Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return all.size();
    }
}
