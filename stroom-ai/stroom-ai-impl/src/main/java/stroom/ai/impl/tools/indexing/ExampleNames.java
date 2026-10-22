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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A field plan's fields named as the user's example index names its fields: the example's own name for an
/// event-logging path, else a name in the example's naming style (PascalCase, camelCase or ECS-style), nested where
/// the example nests fields that belong together. Ported from the Stroom MCP server's `utils/templatecheck.py`
/// (naming_style, fits_style, example_naming, example_dotted, field_group, nested_name, names_from_example and
/// their helpers).
public final class ExampleNames {

    /// The naming styles an example may follow, each as said to the user.
    public static final Map<String, String> STYLE_NAMES = styleNames();
    /// An example's field type as a plan's logical type.
    public static final Map<String, String> PLAN_TYPE = planType();

    // Naming styles, judged per dotted segment: 'lower' (one lower-case word) fits ECS-style and camelCase names
    // alike.
    private static final List<Map.Entry<String, Pattern>> SEGMENT = List.of(
            Map.entry("pascal", Pattern.compile("[A-Z][A-Za-z0-9]*")),
            Map.entry("camel", Pattern.compile("[a-z][a-z0-9]*[A-Z][A-Za-z0-9]*")),
            Map.entry("snake", Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)+")),
            Map.entry("lower", Pattern.compile("[a-z0-9]+")));
    // Named so for Stroom (or Elasticsearch), whatever the index's style.
    private static final Set<String> EXEMPT = Set.of("StreamId", "EventId");
    // Event-logging path segments too general to name a field on their own.
    private static final Set<String> GENERIC = Set.of("id", "name", "value", "type", "text", "data", "code", "state",
            "number", "description");
    private static final Set<String> ROOTS = Set.of("EventSource", "EventDetail", "EventTime", "EventChain");
    // Path elements that only lead to the value, left out of a nested name: Update/After/Configuration/Type is
    // Update.Configuration.Type, Network/*/Source/Device/IPAddress is Source.IPAddress.
    private static final Set<String> FILLER = Set.of("After", "Before", "Device", "*");
    private static final Pattern NOT_ALNUM = Pattern.compile("[^a-z0-9]");
    private static final Pattern PREDICATE = Pattern.compile("\\[.*?\\]", Pattern.UNIX_LINES);
    private static final Pattern WORDS = Pattern.compile("[A-Z]+(?=[A-Z][a-z]|[0-9]|$)|[A-Z]?[a-z]+|[0-9]+",
            Pattern.UNIX_LINES);

    private ExampleNames() {
        // Static only.
    }

    /// @param name A field name.
    /// @return `ecs`, `pascal`, `camel` or `other`, judging each dotted part; null for names exempt from any style:
    /// @timestamp, _id and the like, StreamId and EventId.
    public static String namingStyle(final String name) {
        if (name.startsWith("@") || name.startsWith("_") || EXEMPT.contains(name)) {
            return null;
        }
        final Set<String> parts = new HashSet<>();
        for (final String part : name.split("\\.", -1)) {
            parts.add(segment(part));
        }
        if (Set.of("lower", "snake").containsAll(parts)) {
            return "ecs";
        }
        if (parts.equals(Set.of("pascal"))) {
            return "pascal";
        }
        if (Set.of("lower", "camel").containsAll(parts)) {
            return "camel";
        }
        return "other";
    }

    private static String segment(final String part) {
        for (final Map.Entry<String, Pattern> entry : SEGMENT) {
            if (entry.getValue().matcher(part).matches()) {
                return entry.getKey();
            }
        }
        return "other";
    }

    /// @param name  A field name.
    /// @param style A naming style, or null.
    /// @return True if the name fits it (exempt names fit any; an ECS-style name of one word fits camelCase).
    public static boolean fitsStyle(final String name, final String style) {
        final String found = namingStyle(name);
        return found == null || found.equals(style) || ("camel".equals(style) && "ecs".equals(found)
                                                        && !name.contains("_"));
    }

    /// @param fields An example's field names.
    /// @return The style most of them follow, or null when none does.
    public static String exampleNaming(final List<String> fields) {
        final List<String> names = fields.stream().filter(f -> namingStyle(f) != null).toList();
        if (names.isEmpty()) {
            return null;
        }
        String best = null;
        long most = -1;
        for (final String style : STYLE_NAMES.keySet()) {
            final long fitting = names.stream().filter(f -> fitsStyle(f, style)).count();
            final long exact = names.stream().filter(f -> style.equals(namingStyle(f))).count();
            if (fitting * 2 > names.size() && exact > most) {
                best = style;
                most = exact;
            }
        }
        return best;
    }

    /// @param fields An example's field names.
    /// @return True if the example nests its names (User.Id) rather than running them together (UserId).
    public static boolean exampleDotted(final List<String> fields) {
        final List<String> names = fields.stream().filter(f -> namingStyle(f) != null).toList();
        return names.stream().filter(f -> f.contains(".")).count() * 2 > names.size();
    }

    private static String squash(final String text) {
        return NOT_ALNUM.matcher(PyText.lower(text)).replaceAll("");
    }

    // An event-logging path's element names below the root, e.g. EventSource/User/Id -> [User, Id].
    private static List<String> segments(final String source) {
        final List<String> parts = new ArrayList<>();
        for (final String part : source.split("/", -1)) {
            if (!part.isEmpty() && !part.startsWith("@")) {
                parts.add(PREDICATE.matcher(part).replaceAll(""));
            }
        }
        return parts.size() > 1 && ROOTS.contains(parts.getFirst())
                ? new ArrayList<>(parts.subList(1, parts.size()))
                : parts;
    }

    private static List<String> leaves(final Map<String, Map<String, Object>> example) {
        return example.entrySet().stream()
                .filter(entry -> !entry.getValue().containsKey("properties")
                                 && !"object".equals(entry.getValue().get("type")))
                .map(Map.Entry::getKey)
                .toList();
    }

    // The example field that holds this event-logging path: one whose name is the path's trailing elements run
    // together (user.id, User.Id, UserId <- EventSource/User/Id; TypeId <- EventDetail/TypeId), longest first, else
    // one a convention names for the path (ECS's user.name for the user id). The path's own elements come first: an
    // example with user.id and user.name maps the id to user.id, not to the convention's user.name. A single generic
    // element (Id, Name) is not enough on its own.
    private static String match(final String source,
                                final Map<String, Map<String, Object>> example,
                                final Set<String> known) {
        final List<String> leaves = leaves(example);
        final List<String> parts = segments(source);
        final Map<String, String> bySquash = new LinkedHashMap<>();
        leaves.forEach(name -> bySquash.putIfAbsent(squash(name), name));
        for (int i = 0; i < parts.size(); i++) {
            final List<String> run = parts.subList(i, parts.size());
            if (run.size() == 1 && (GENERIC.contains(squash(run.getFirst()))
                                    || run.getFirst().codePointCount(0, run.getFirst().length()) < 4)) {
                continue;
            }
            final String hit = bySquash.get(squash(String.join("", run)));
            if (JsonValues.truthy(hit)) {
                return hit;
            }
        }
        return leaves.stream().filter(known::contains).findFirst().orElse(null);
    }

    // IPAddress -> [IP, Address]; createdOn -> [created, On].
    private static List<String> words(final String part) {
        final List<String> out = new ArrayList<>();
        final Matcher matcher = WORDS.matcher(part);
        while (matcher.find()) {
            out.add(matcher.group());
        }
        return out.isEmpty()
                ? List.of(part)
                : out;
    }

    private static String camel(final List<String> words) {
        final StringBuilder sb = new StringBuilder(PyText.lower(words.getFirst()));
        for (final String word : words.subList(1, words.size())) {
            sb.append(capitalised(word));
        }
        return sb.toString();
    }

    // w[:1].upper() + w[1:].lower()
    private static String capitalised(final String word) {
        if (word.isEmpty()) {
            return word;
        }
        final int first = word.offsetByCodePoints(0, 1);
        return word.substring(0, first).toUpperCase(java.util.Locale.ROOT)
               + PyText.lower(word.substring(first));
    }

    // p[:1].upper() + p[1:]
    private static String upperFirst(final String part) {
        if (part.isEmpty()) {
            return part;
        }
        final int first = part.offsetByCodePoints(0, 1);
        return part.substring(0, first).toUpperCase(java.util.Locale.ROOT) + part.substring(first);
    }

    private static String snakeWords(final String part) {
        return String.join("_", words(part).stream().map(PyText::lower).toList());
    }

    /// A name in the example's style for a path it has no field for, from the path's last elements:
    /// Client.IPAddress or ClientIPAddress (PascalCase), client.ipAddress or clientIpAddress (camelCase),
    /// client.ip_address or client_ip_address (ECS-style) (the Stroom MCP server's `_derive`, which
    /// draft_index_mapping also names fields with).
    ///
    /// @param source An event-logging path.
    /// @param style  The naming style, one of [#STYLE_NAMES].
    /// @param dotted True to join the parts with dots.
    /// @param taken  The names already taken.
    /// @return The name, or null if every name it could take is taken (or the style is none of [#STYLE_NAMES]).
    public static String derive(final String source, final String style, final boolean dotted,
                                final Set<String> taken) {
        final List<String> parts = segments(source);
        if (parts.isEmpty() || !STYLE_NAMES.containsKey(style)) {
            return null;
        }
        for (int take = Math.min(2, parts.size()); take <= parts.size(); take++) {
            final List<String> run = parts.subList(parts.size() - take, parts.size());
            if (take == 1 && GENERIC.contains(squash(run.getFirst())) && parts.size() > 1) {
                continue;
            }
            final String name = build(run, style, dotted);
            if (!taken.contains(name)) {
                return name;
            }
        }
        return null;
    }

    private static String build(final List<String> run, final String style, final boolean dotted) {
        if ("pascal".equals(style)) {
            final List<String> names = run.stream().map(ExampleNames::upperFirst).toList();
            return String.join(dotted
                    ? "."
                    : "", names);
        }
        if ("camel".equals(style)) {
            if (dotted) {
                return String.join(".", run.stream().map(p -> camel(words(p))).toList());
            }
            final List<String> all = new ArrayList<>();
            run.forEach(p -> all.addAll(words(p)));
            return camel(all);
        }
        final List<String> names = run.stream().map(ExampleNames::snakeWords).toList();
        return String.join(dotted
                ? "."
                : "_", names);
    }

    /// The group of an event-detail path, the element its fields belong together under: the action element
    /// (Alert, Authenticate), or for a network event the side (Source, Destination); and the rest of the path below
    /// it, without the elements that only lead to the value (After, Before, Device).
    ///
    /// @param source An event-logging path.
    /// @return Its group and the rest, or null if it has none.
    public static FieldGroup fieldGroup(final String source) {
        final List<String> parts = segments(source);
        if (parts.isEmpty() || !PyText.strip(source, "/").startsWith("EventDetail/") || parts.size() < 2) {
            return null;
        }
        final String group;
        final List<String> rest = new ArrayList<>();
        if ("Network".equals(parts.getFirst()) && parts.size() >= 4) {
            group = parts.get(2);
            parts.subList(3, parts.size()).stream().filter(p -> !FILLER.contains(p)).forEach(rest::add);
        } else {
            group = parts.getFirst();
            parts.subList(1, parts.size()).stream().filter(p -> !FILLER.contains(p)).forEach(rest::add);
        }
        return rest.isEmpty()
                ? null
                : new FieldGroup(group, rest);
    }

    // Which fields count together when deciding to nest: a connection's two sides are one (Source.Port nests, so
    // Destination.IPAddress does), the rest by their group.
    private static String together(final String source) {
        final FieldGroup grouped = fieldGroup(source);
        if (grouped == null) {
            return null;
        }
        return PyText.strip(source, "/").startsWith("EventDetail/Network/")
                ? "Network"
                : grouped.group();
    }

    /// @param source An event-logging path.
    /// @param style  The example's naming style.
    /// @return A nested name in that style for a path whose group has other fields (Alert.Type, Source.Port), or
    /// null if the path has no group or the style is none of [#STYLE_NAMES].
    public static String nestedName(final String source, final String style) {
        final FieldGroup grouped = fieldGroup(source);
        if (grouped == null || !STYLE_NAMES.containsKey(style)) {
            return null;
        }
        final List<String> parts = new ArrayList<>();
        parts.add(grouped.group());
        parts.addAll(grouped.rest());
        if ("pascal".equals(style)) {
            return String.join(".", parts.stream().map(ExampleNames::upperFirst).toList());
        }
        if ("camel".equals(style)) {
            return String.join(".", parts.stream().map(p -> camel(words(p))).toList());
        }
        return String.join(".", parts.stream().map(ExampleNames::snakeWords).toList());
    }

    /// The field plan's fields named as the user's example names them. A field takes the example's name for its
    /// event-logging path; one the example has no field for is named in the example's style; populated paths the
    /// plan leaves out but the example maps are added, typed as the example types them. StreamId, EventId and
    /// @timestamp keep their names.
    ///
    /// @param fields          The plan's fields, as JSON (name, type, source, ...).
    /// @param example         The example's fields by path ([TemplateMappings#readMappingFields(Map)]).
    /// @param conventionNames The names conventions give each event-logging path.
    /// @param populated       The event paths the sample populates.
    /// @return The fields renamed (and added), and notes saying what was done.
    public static Named namesFromExample(final List<Map<String, Object>> fields,
                                         final Map<String, Map<String, Object>> example,
                                         final Map<String, Set<String>> conventionNames,
                                         final List<String> populated) {
        final List<String> leaves = leaves(example);
        final String style = exampleNaming(leaves);
        final boolean dotted = exampleDotted(leaves);
        // An example that nests any of its names (User.Id) nests fields that belong together: a user asked for
        // Alert.Type and Alert.Severity, then the same wherever fields share an element.
        final boolean nests = leaves.stream().anyMatch(f -> f.contains(".") && namingStyle(f) != null);
        final Map<String, Integer> groups = new LinkedHashMap<>();
        for (final Map<String, Object> field : fields) {
            final String group = together(source(field));
            if (JsonValues.truthy(group)) {
                groups.merge(group, 1, Integer::sum);
            }
        }
        final List<Map<String, Object>> out = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        final List<String> renamed = new ArrayList<>();
        final List<String> derived = new ArrayList<>();
        final List<String> added = new ArrayList<>();
        final List<String> unlike = new ArrayList<>();
        final Set<String> used = new HashSet<>();
        final Set<String> taken = new HashSet<>();
        fields.forEach(field -> taken.add(name(field)));
        for (final Map<String, Object> field : fields) {
            final String name = name(field);
            final String source = source(field);
            if (namingStyle(name) == null) {
                out.add(field);
                used.add(name);
                continue;
            }
            String hit = match(source, unused(example, used), conventionNames.getOrDefault(source, Set.of()));
            final String last = lastSegment(source);
            if (JsonValues.truthy(hit) && squash(hit).equals(squash(last))
                && fields.stream().filter(f -> lastSegment(source(f)).equals(last)).count() > 1) {
                // The example's IpAddress names an address, but which? Seen: it took the source's of two, and the
                // destination's was named apart. Both are named in the example's style instead.
                hit = null;
            }
            final Set<String> inUse = new HashSet<>(taken);
            inUse.addAll(used);
            final String group = together(source);
            String newName;
            if (JsonValues.truthy(hit)) {
                newName = hit;
            } else if (style != null && nests && JsonValues.truthy(group) && groups.getOrDefault(group, 0) > 1
                       && !inUse.contains(nestedName(source, style))) {
                newName = nestedName(source, style);
                derived.add(name + " -> " + newName);
            } else if (style != null && !fits(name, style, dotted)) {
                // A convention's name for the path in the example's style (host.ip), else one built from the path.
                final List<String> conventional = conventionNames.getOrDefault(source, Set.of()).stream()
                        .filter(n -> fits(n, style, dotted) && namingStyle(n) != null && n.contains(".") == dotted
                                     && !inUse.contains(n))
                        .sorted()
                        .toList();
                newName = conventional.isEmpty()
                        ? derive(source, style, dotted, inUse)
                        : conventional.getFirst();
                if (JsonValues.truthy(newName)) {
                    derived.add(name + " -> " + newName);
                } else {
                    unlike.add(name);
                    newName = name;
                }
            } else {
                newName = name;
            }
            if (JsonValues.truthy(hit) && !hit.equals(name)) {
                renamed.add(name + " -> " + hit);
            }
            used.add(newName);
            final Map<String, Object> named = new LinkedHashMap<>(field);
            named.put("name", newName);
            out.add(named);
        }
        final Set<String> sources = new HashSet<>();
        out.forEach(field -> sources.add(source(field)));
        // Network paths once, whichever action: the example's field takes every Permit's and Deny's address, not
        // one.
        final Set<String> paths = new LinkedHashSet<>();
        populated.forEach(path -> paths.add(FieldPlans.anyAction(path)));
        for (final String path : paths) {
            if (path.startsWith("@")
                || sources.stream().anyMatch(s -> FieldPlans.sourceMatches(s, path) || s.equals(path))) {
                continue;
            }
            final String hit = match(path, unused(example, used), conventionNames.getOrDefault(path, Set.of()));
            if (JsonValues.truthy(hit)) {
                final Map<String, Object> field = new LinkedHashMap<>();
                field.put("name", hit);
                field.put("type", PLAN_TYPE.getOrDefault(PyText.str(example.get(hit).get("type")), "keyword"));
                field.put("source", path);
                out.add(field);
                used.add(hit);
                added.add(hit + " <- " + path);
            }
        }
        if (!renamed.isEmpty()) {
            notes.add("named as the example names them: " + PyText.list(renamed));
        }
        if (!derived.isEmpty()) {
            notes.add("not in the example, named in its style (" + STYLE_NAMES.get(style) + "): "
                      + PyText.list(derived));
        }
        if (!added.isEmpty()) {
            notes.add("in the sample and mapped by the example, so added: " + PyText.list(added));
        }
        if (!unlike.isEmpty()) {
            notes.add("named unlike the example (" + STYLE_NAMES.getOrDefault(style, style) + "), with no name "
                      + "derived: " + PyText.list(unlike) + "; agree names with the user and give them as "
                      + "extra_fields");
        }
        final List<String> left = example.entrySet().stream()
                .filter(entry -> !used.contains(entry.getKey()) && !entry.getValue().containsKey("properties")
                                 && !"object".equals(entry.getValue().get("type"))
                                 && namingStyle(entry.getKey()) != null)
                .map(Map.Entry::getKey)
                .toList();
        if (!left.isEmpty()) {
            notes.add("in the example but not in this source's sample, so not in this plan: "
                      + PyText.list(left.subList(0, Math.min(15, left.size())))
                      + (left.size() > 15
                    ? " (+" + (left.size() - 15) + ")"
                    : ""));
        }
        return new Named(out, notes);
    }

    // A dotted name in an example that runs its names together (userName) does not fit it either.
    private static boolean fits(final String name, final String style, final boolean dotted) {
        return fitsStyle(name, style) && (dotted || !name.contains("."));
    }

    private static Map<String, Map<String, Object>> unused(final Map<String, Map<String, Object>> example,
                                                           final Set<String> used) {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        example.forEach((key, spec) -> {
            if (!used.contains(key)) {
                out.put(key, spec);
            }
        });
        return out;
    }

    private static String lastSegment(final String source) {
        final List<String> parts = segments(source);
        return parts.isEmpty()
                ? ""
                : parts.getLast();
    }

    private static String name(final Map<String, Object> field) {
        return (String) field.get("name");
    }

    private static String source(final Map<String, Object> field) {
        return (String) field.get("source");
    }

    private static Map<String, String> styleNames() {
        final Map<String, String> names = new LinkedHashMap<>();
        names.put("ecs", "lower case and snake_case (ECS-style, e.g. user.name, source.ip)");
        names.put("pascal", "PascalCase (e.g. User.Id, TypeId)");
        names.put("camel", "camelCase (e.g. user.userId)");
        return Collections.unmodifiableMap(names);
    }

    private static Map<String, String> planType() {
        final Map<String, String> types = new LinkedHashMap<>();
        for (final String type : List.of("keyword", "constant_keyword", "wildcard")) {
            types.put(type, "keyword");
        }
        types.put("text", "text");
        types.put("match_only_text", "text");
        types.put("date", "date");
        types.put("date_nanos", "date");
        for (final String type : List.of("long", "integer", "short", "byte", "unsigned_long")) {
            types.put(type, "long");
        }
        for (final String type : List.of("double", "float", "half_float", "scaled_float")) {
            types.put(type, "double");
        }
        types.put("boolean", "boolean");
        types.put("ip", "ip");
        return Collections.unmodifiableMap(types);
    }


    // --------------------------------------------------------------------------------


    /// The group an event-detail path's field belongs to.
    ///
    /// @param group The element its fields belong together under, e.g. `Alert` or `Source`.
    /// @param rest  The rest of the path below it, e.g. `[Type]` or `[IPAddress]`.
    public record FieldGroup(String group, List<String> rest) {

    }

    /// A plan's fields named as the example names them.
    ///
    /// @param fields The fields, as JSON.
    /// @param notes  What was done, for the agent.
    public record Named(List<Map<String, Object>> fields, List<String> notes) {

    }
}
