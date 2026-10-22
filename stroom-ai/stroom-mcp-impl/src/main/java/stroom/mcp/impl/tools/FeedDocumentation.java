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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.KeptMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// How describe_feed reads the documentation generated for a feed's pipelines and indexes (Markdown, with a
/// `## Field mapping` section of tables): a field's rows, through its source path, and how to search it through
/// Stroom. Pure functions of the text.
final class FeedDocumentation {

    /// The most index fields named like the one asked about, when none is it.
    static final int SIMILAR_SHOWN = 10;

    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private static final Pattern WORD = Pattern.compile("[A-Z]+(?![a-z])|[A-Z]?[a-z]+|\\d+");
    private static final Pattern TABLE_RULE = Pattern.compile("^\\s*\\|[\\s:|-]+\\|\\s*$");
    private static final Pattern CELL_SEPARATOR = Pattern.compile("(?<!\\\\)\\|");
    private static final Pattern NAMED = Pattern.compile("`([^`]+)`");
    private static final Pattern DATA_NAME = Pattern.compile("\\[@Name='([^']+)'\\]");
    private static final Pattern DATA_NAME_TAIL = Pattern.compile("\\[@Name=.*$");
    private static final String FIELD_MAPPING = "Field mapping";
    private static final String DERIVES = "<-";

    private FeedDocumentation() {
    }

    /// @param name A field name.
    /// @return It for comparison: User.DomainName, user_domain_name and EventSource/User/DomainName's tail alike.
    static String norm(final String name) {
        return NOT_ALPHANUMERIC.matcher(Objects.requireNonNullElse(name, "").toLowerCase(Locale.ROOT))
                .replaceAll("");
    }

    /// @param name A field name.
    /// @return Its words, lower-cased: User.DomainName, user_domain_name and userDomainName give user, domain,
    /// name.
    static Set<String> words(final String name) {
        final Set<String> words = new LinkedHashSet<>();
        final Matcher matcher = WORD.matcher(Objects.requireNonNullElse(name, ""));
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return words;
    }

    /// @param field A field name the user may have misremembered.
    /// @param names The names there are.
    /// @return The names sharing the most words with the field, most first.
    static List<String> similar(final String field, final List<String> names) {
        final Set<String> want = words(field);
        return names.stream()
                .map(name -> {
                    final Set<String> shared = new LinkedHashSet<>(words(name));
                    shared.retainAll(want);
                    return new Scored(shared.size(), name);
                })
                .sorted(Comparator.comparing(Scored::score, Comparator.reverseOrder()))
                .filter(scored -> scored.score() > 0)
                .map(Scored::name)
                .limit(SIMILAR_SHOWN)
                .toList();
    }

    /// @param markdown A document's text.
    /// @param heading  A `##` heading, without the hashes.
    /// @return The text of the section, its own `###` subsections included, or an empty string.
    static String section(final String markdown, final String heading) {
        final Matcher matcher = Pattern.compile("^## " + Pattern.quote(heading) + "[^\\n]*\\n(.*?)(?=^## |\\z)",
                Pattern.MULTILINE | Pattern.DOTALL).matcher(Objects.requireNonNullElse(markdown, ""));
        return matcher.find()
                ? matcher.group(1).strip()
                : "";
    }

    /// @param markdown A document's text.
    /// @return Each Markdown table in it, as rows keyed by their header; a cell's escaped bars and `<br>`s undone.
    static List<List<Map<String, String>>> tables(final String markdown) {
        final List<List<Map<String, String>>> tables = new ArrayList<>();
        final List<String> lines = Objects.requireNonNullElse(markdown, "").lines().toList();
        int n = 0;
        while (n < lines.size()) {
            if (lines.get(n).stripLeading().startsWith("|") && n + 1 < lines.size()
                && TABLE_RULE.matcher(lines.get(n + 1)).matches()) {
                final List<String> head = cells(lines.get(n));
                final List<Map<String, String>> rows = new ArrayList<>();
                n += 2;
                while (n < lines.size() && lines.get(n).stripLeading().startsWith("|")) {
                    final List<String> cells = cells(lines.get(n));
                    final Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 0; i < head.size(); i++) {
                        row.put(head.get(i), i < cells.size()
                                ? cells.get(i)
                                : "");
                    }
                    rows.add(row);
                    n++;
                }
                tables.add(rows);
            } else {
                n++;
            }
        }
        return tables;
    }

    /// The Field mapping rows about a field: an index doc's row naming it; an events doc's row (the EventSource
    /// table's, or a rule's, with only its EventDetail lines about the field) whose path is one of the field's
    /// sources, ends with the field's name, or writes it as a Data name, or that reads it as its source field
    /// (`x` <- `field`).
    ///
    /// @param markdown A document's text.
    /// @param field    The field asked about.
    /// @param sources  The event-logging paths the field comes from, if known.
    /// @return The rows, each keyed by its table's headers.
    static List<Map<String, String>> rowsAbout(final String markdown, final String field, final List<String> sources) {
        final String want = norm(field);
        final String mapping = section(markdown, FIELD_MAPPING);
        final List<Map<String, String>> found = new ArrayList<>();
        for (final List<Map<String, String>> table : tables(mapping.isEmpty()
                ? markdown
                : mapping)) {
            for (final Map<String, String> row : table) {
                if (row.isEmpty()) {
                    continue;
                }
                final String first = row.values().iterator().next();
                if (named(first).stream().anyMatch(name -> norm(name).equals(want))) {
                    found.add(row);
                    continue;
                }
                final Map<String, List<String>> matched = new LinkedHashMap<>();
                row.forEach((header, cell) -> {
                    for (final String line : cell.split("\\R")) {
                        if (lineIsAbout(header, line, want, sources)) {
                            matched.computeIfAbsent(header, k -> new ArrayList<>()).add(line.strip());
                        }
                    }
                });
                if (!matched.isEmpty()) {
                    // A rule's EventDetail cell cut to the lines about the field; the other cells as they are.
                    final Map<String, String> cut = new LinkedHashMap<>();
                    row.forEach((header, cell) -> {
                        final List<String> lines = matched.get(header);
                        cut.put(header, lines != null && String.join("", lines).contains(DERIVES)
                                ? String.join("\n", lines)
                                : cell);
                    });
                    found.add(cut);
                }
            }
        }
        return found;
    }

    /// @param markdown A document's text.
    /// @param field    The field asked about.
    /// @return The lines outside the tables and headings that name the field, as a word (a list of extracted
    /// fields, an open item).
    static List<String> mentions(final String markdown, final String field) {
        final Pattern pattern = Pattern.compile("(?<![\\w.])" + Pattern.quote(field) + "(?!\\w)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return Objects.requireNonNullElse(markdown, "").lines()
                .filter(line -> pattern.matcher(line).find())
                .filter(line -> !line.stripLeading().startsWith("|") && !line.stripLeading().startsWith("#"))
                .map(line -> PipelineTemplates.shorten(line.strip(), 300))
                .toList();
    }

    /// @param cell A table cell.
    /// @return The names or paths it gives in backticks (EventDetail lines: `Authenticate/User/Id` <- user =
    /// "bob").
    static List<String> named(final String cell) {
        final List<String> names = new ArrayList<>();
        final Matcher matcher = NAMED.matcher(Objects.requireNonNullElse(cell, ""));
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /// How to search a field of this type through Stroom (a dashboard or query term), from what Stroom 7.13 was
    /// found to do: what works, and what quietly finds nothing.
    ///
    /// @param backend       `elasticsearch` or `lucene`.
    /// @param type          The field's type (Elasticsearch's own where known, e.g. keyword, text, ip).
    /// @param analyzer      A Lucene field's analyzer, or empty.
    /// @param caseSensitive False for a Lucene field that isn't case-sensitive.
    /// @return The advice, a sentence each.
    static List<String> searchAdvice(final String backend,
                                     final String type,
                                     final String analyzer,
                                     final boolean caseSensitive) {
        final String kind = Objects.requireNonNullElse(type, "").toLowerCase(Locale.ROOT);
        final String analyzerName = Objects.requireNonNullElse(analyzer, "").toUpperCase(Locale.ROOT);
        if (Set.of("ip", "ipv4_address").contains(kind)) {
            return List.of("An IP address field: EQUALS an address ('10.1.2.3'), or a CIDR range for a network "
                           + "('10.1.0.0/16').",
                    "A wildcard ('10.1.*') finds nothing on an ip field; use the CIDR range.");
        }
        if (Set.of("date", "date_field").contains(kind)) {
            return List.of("A date: BETWEEN 'from,to' (ISO 8601, e.g. "
                           + "'2026-10-01T00:00:00.000Z,2026-10-02T00:00:00.000Z'), GREATER_THAN or LESS_THAN; "
                           + "relative times work too ('day()-7d,now()').");
        }
        if (Set.of("long", "integer", "int", "id", "float", "double", "long_field", "integer_field", "id_field",
                "float_field", "double_field").contains(kind)) {
            return List.of("A number: EQUALS, GREATER_THAN, LESS_THAN, BETWEEN 'low,high', or IN 'a,b,c'.");
        }
        if (Set.of("boolean", "boolean_field").contains(kind)) {
            return List.of("A boolean: EQUALS 'true' or 'false'.");
        }
        final boolean elastic = "elasticsearch".equals(backend);
        if (elastic && "text".equals(kind)) {
            return List.of("A text field: Elasticsearch splits the value into words and lower-cases them, so EQUALS "
                           + "matches a word of it, not the whole value, and a wildcard matches within one word.",
                    "For the whole value (equals, starts or ends with), search its keyword sub-field when the index "
                    + "has one (listed under sub_fields).");
        }
        final String caseText = caseSensitive
                ? "case-sensitive"
                : "not case-sensitive";
        final List<String> exact = List.of("Exact (" + caseText + "): EQUALS the whole value. Wildcards in EQUALS for "
                                           + "the rest: ends with x is EQUALS '*x', starts with x is EQUALS 'x*', "
                                           + "contains x is EQUALS '*x*'.",
                "Has any value: EQUALS '*'. Several values: IN 'a,b,c' (commas between them).");
        final List<String> advice = new ArrayList<>();
        if (elastic) {
            advice.addAll(exact);
            advice.add("Through Stroom on Elasticsearch, STARTS_WITH and CONTAINS find nothing, and IS_NULL and "
                       + "IS_NOT_NULL match every document: use the wildcards above. A leading wildcard ('*x') reads "
                       + "every value in the index, so narrow the time range first on a large index.");
            return advice;
        }
        if (Set.of("text", "text_field").contains(kind) && !analyzerName.isEmpty() && !"KEYWORD".equals(analyzerName)) {
            return List.of("A text field (analyzer " + analyzerName.toLowerCase(Locale.ROOT) + "): split into words, "
                           + "so EQUALS matches a word of it; CONTAINS works; ranges (BETWEEN, GREATER_THAN) find "
                           + "nothing. A value holding a space in IN finds nothing: search it with EQUALS.");
        }
        advice.addAll(exact);
        advice.add("On a Lucene index STARTS_WITH, ENDS_WITH and CONTAINS find nothing on a field like this: use the "
                   + "wildcards above. A value holding a space in IN finds nothing: search it with EQUALS.");
        return advice;
    }

    /// What the events pipeline writes, a line a rule: its name, the action element, and when it applies.
    ///
    /// @param payload The translation mapping kept with the events XSLT.
    /// @return A line for each rule, or empty if the mapping can't be read.
    static List<String> eventKinds(final Map<?, ?> payload) {
        if (payload == null || !(payload.get("events") instanceof final List<?> events)) {
            return List.of();
        }
        final List<?> common = payload.get("common") instanceof final List<?> list
                ? list
                : List.of();
        final List<String> kinds = new ArrayList<>();
        for (final Object item : events) {
            if (!(item instanceof final Map<?, ?> rule) || !(rule.get("name") instanceof final String name)) {
                return List.of();
            }
            final List<String> conditions = new ArrayList<>();
            if (rule.get("when") instanceof final List<?> when) {
                for (final Object condition : when) {
                    if (condition instanceof final Map<?, ?> map) {
                        conditions.add(KeptMapping.conditionText(stringKeyed(map)));
                    }
                }
            }
            final String whenText = conditions.isEmpty()
                    ? "any other record"
                    : String.join(" and ", conditions);
            if (Boolean.TRUE.equals(rule.get("drop"))) {
                kinds.add(name + ": left untranslated (" + whenText + ")");
                continue;
            }
            final Set<String> actions = new TreeSet<>();
            final List<Object> fields = new ArrayList<>(common);
            if (rule.get("fields") instanceof final List<?> ruleFields) {
                fields.addAll(ruleFields);
            }
            for (final Object field : fields) {
                if (field instanceof final Map<?, ?> map && map.get("path") instanceof final String path) {
                    final String stripped = strip(path, '/');
                    final String[] parts = stripped.split("/");
                    if (stripped.startsWith("EventDetail/") && parts.length > 1
                        && !Set.of("TypeId", "Description").contains(parts[1])) {
                        actions.add(parts[1]);
                    }
                }
            }
            kinds.add(name + ": " + (actions.isEmpty()
                    ? "EventDetail"
                    : String.join(", ", actions)) + " (" + whenText + ")");
        }
        return kinds;
    }

    // Whether one line of a row's cell is about the field.
    private static boolean lineIsAbout(final String header,
                                       final String line,
                                       final String want,
                                       final List<String> sources) {
        final List<String> named = named(line);
        final String path = !named.isEmpty() && named.getFirst().contains("/")
                ? named.getFirst()
                : null;
        final Matcher data = DATA_NAME.matcher(Objects.requireNonNullElse(path, ""));
        final List<String> read;
        if (line.contains(DERIVES)) {
            read = named(line.split(DERIVES, 2)[1]);
        } else if (header.startsWith("From")) {
            read = named;
        } else {
            read = List.of();
        }
        return (path != null && !sources.isEmpty() && sources.stream().anyMatch(source -> pathMatches(source, path)))
               || (path != null && sources.isEmpty() && norm(eventPath(path)).endsWith(want))
               || (data.find() && norm(data.group(1)).equals(want))
               || read.stream().anyMatch(name -> norm(name).equals(want));
    }

    // An event-logging path as the events doc writes it, without Event/ and its Data name: comparable to an index
    // plan's source.
    private static String eventPath(final String path) {
        String value = strip(path, '/');
        if (value.startsWith("Event/")) {
            value = value.substring("Event/".length());
        }
        return DATA_NAME_TAIL.matcher(value).replaceAll("");
    }

    // An index field's source (EventDetail/*/User/Id) against a path the events doc gives (Authenticate/User/Id in
    // the event types table, EventDetail/ left off).
    private static boolean pathMatches(final String source, final String path) {
        final String wanted = eventPath(source);
        final String given = eventPath(path);
        return sourceMatches(wanted, given) || sourceMatches(wanted, "EventDetail/" + given);
    }

    /// Whether a plan source names this event path: '*' stands for one element, as in the XPath it is
    /// (EventDetail/Network/*/Source/Device/IPAddress names the source address of a Permit, a Deny, a Connect...),
    /// and '//' for any elements between (EventDetail//Object/Name names View/Object/Name and
    /// Export/Source/Object/Name). Seen: an agent's EventDetail//Object/Name taken as matching nothing, its paths
    /// reported as not mapped.
    ///
    /// @param source A plan source, e.g. `EventDetail/*/User/Id`.
    /// @param path   An event path, e.g. `EventDetail/Authenticate/User/Id`.
    /// @return True if the source names the path.
    static boolean sourceMatches(final String source, final String path) {
        if (!source.contains("*") && !source.contains("//")) {
            return source.equals(path);
        }
        if (!source.contains("//")) {
            final String[] want = strip(source, '/').split("/", -1);
            final String[] have = strip(path, '/').split("/", -1);
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
        final StringBuilder pattern = new StringBuilder();
        final String stripped = strip(source, '/');
        int i = 0;
        while (i < stripped.length()) {
            if (stripped.startsWith("//", i)) {
                pattern.append("/(?:[^/]+/)*");
                i += 2;
            } else if (stripped.charAt(i) == '*') {
                pattern.append("[^/]+");
                i++;
            } else {
                pattern.append(Pattern.quote(String.valueOf(stripped.charAt(i))));
                i++;
            }
        }
        final String prefix = source.startsWith("//")
                ? "(?:[^/]+/)*"
                : "";
        return Pattern.compile(prefix + pattern).matcher(strip(path, '/')).matches();
    }


    private static List<String> cells(final String line) {
        String inner = line.strip().substring(1);
        if (inner.endsWith("|") && !inner.endsWith("\\|")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        return Arrays.stream(CELL_SEPARATOR.split(inner, -1))
                .map(cell -> cell.strip().replace("\\|", "|").replace("<br>", "\n"))
                .toList();
    }

    private static String strip(final String value, final char c) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == c) {
            start++;
        }
        while (end > start && value.charAt(end - 1) == c) {
            end--;
        }
        return value.substring(start, end);
    }


    // A condition's keys as strings, as KeptMapping reads a condition.
    private static Map<String, Object> stringKeyed(final Map<?, ?> map) {
        final Map<String, Object> keyed = new LinkedHashMap<>();
        map.forEach((key, value) -> keyed.put(String.valueOf(key), value));
        return keyed;
    }

    // --------------------------------------------------------------------------------


    /// A name, and how many words it shares with the one asked about.
    private record Scored(int score, String name) {

    }
}
