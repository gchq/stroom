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

import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.XsltVersionHistory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A hand edit kept through the agent's next change: the Stroom MCP server's `utils/handedit.py`. Asked for by the
/// user: an XSLT the server saved (from a mapping, an index or CEF plan, or written by the agent), then edited by
/// hand in Stroom's editor (say to write http.request.body.bytes as well), was saved over by the next
/// regeneration, and the edit was gone.
///
/// What the edit changed is what the XSLT writes and reads that the code the server saved doesn't (and the
/// reverse): the names of the elements and keys it writes (`<number key="bytes">`, `<Data Name="device_class">`)
/// and the constants in them, and the XPath it reads (select, test and `{...}` in attribute values). New code keeps
/// the edit when it still writes and reads those, whichever way it is written: a hand edit carried into the plan
/// comes back in the generator's own style, and an object the generator writes with a template of its own reads
/// its fields by relative paths.
///
/// Where the agent's own change is to a field the edit changed too (asked for by the user: "keep their hand edit or
/// overwrite it with the proposed field"), the two collide: the user decides, field by field. Each thing the edit
/// changed carries the names of what it is written as (the key or element round an expression), which are matched
/// to the plan's fields the change touches.
public final class HandEdits {

    private static final String XSL = MappingFacts.XSL;
    private static final Pattern AVT = Pattern.compile("\\{([^{}]+)\\}");
    private static final Pattern SPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern DECLARATION = Pattern.compile("^\\s*<\\?xml[^>]*\\?>",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern VARIABLE = Pattern.compile("\\$([\\w.-]+)", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SIMPLE_PATH = Pattern.compile("[\\w@*./:\\[\\]='-]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> CONTAINERS = Set.of("map", "array", "record", "Event");

    private HandEdits() {
        // Static only.
    }

    /// @param text An XPath expression.
    /// @return It with the differences that are not edits removed: whitespace, and brackets round the whole.
    public static String expression(final String text) {
        String out = SPACE.matcher(text).replaceAll("");
        while (out.startsWith("(") && out.endsWith(")")) {
            int depth = 0;
            for (int i = 0; i < out.length(); i++) {
                final char ch = out.charAt(i);
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                }
                if (depth == 0 && i < out.length() - 1) {
                    return out;         // (a) and (b): its brackets are not round the whole
                }
            }
            out = out.substring(1, out.length() - 1);
        }
        return out;
    }

    private static boolean isXsl(final XmlElement element) {
        return XSL.equals(element.namespace());
    }

    private static String firstSet(final String... values) {
        for (final String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    // The name a result element is written as: its key or Name (a JSON key, a Data element's) or its own name.
    private static String named(final XmlElement element) {
        if (isXsl(element)) {
            return "element".equals(element.localName()) || "attribute".equals(element.localName())
                    ? element.get("name")
                    : null;
        }
        return firstSet(element.get("key"), element.get("Name"), element.get("name"), element.localName());
    }

    // What a result element writes: its name and the key or name it is written under, and a constant in it.
    private static List<String> written(final XmlElement element) {
        final String text = element.text() == null
                ? ""
                : PyStr.strip(element.text());
        if (isXsl(element)) {
            if ("text".equals(element.localName()) && !text.isEmpty()) {
                return List.of("text = " + text);
            }
            final String name = named(element);
            return name != null && !name.isEmpty()
                    ? List.of(element.localName() + " " + name)
                    : List.of();
        }
        final String named = firstSet(element.get("key"), element.get("Name"), element.get("name"));
        final List<String> out = new ArrayList<>();
        out.add(named != null
                ? element.localName() + " " + named
                : element.localName());
        if (!text.isEmpty()) {
            out.add((named != null
                    ? named
                    : element.localName()) + " = " + text);
        }
        return out;
    }

    // The names an expression's value is written as: the result element round it, and those directly in it (an
    // xsl:if's test reads for what it holds). A variable's: where it is read (a source changed by hand in the
    // variable a template reads it into is its field's).
    private static Set<String> context(final XmlElement element) {
        if (element.is(XSL, "variable") && firstSet(element.get("name")) != null) {
            final Pattern reading = Pattern.compile("\\$" + Pattern.quote(element.get("name")) + "(?![\\w.-])",
                    Pattern.UNICODE_CHARACTER_CLASS);
            final XmlElement scope = element.parent() != null
                    ? element.parent()
                    : element;
            final Set<String> out = new HashSet<>();
            for (final XmlElement el : scope.iterElements()) {
                if (el != element && el.attributes().stream()
                        .anyMatch(attribute -> reading.matcher(attribute.value()).find())) {
                    out.addAll(context(el));
                }
            }
            return out;
        }
        final Set<String> names = new HashSet<>();
        for (final XmlElement up : element.ancestors()) {
            if (!isXsl(up) && firstSet(up.get("key"), up.get("Name"), up.get("name")) != null) {
                names.add(named(up));
                break;
            }
            if (!isXsl(up) && !CONTAINERS.contains(up.localName())) {
                names.add(named(up));
                break;
            }
        }
        for (final XmlElement down : element.iterElements()) {
            if (down != element && !isXsl(down)) {
                names.add(named(down));
                break;
            }
        }
        names.removeIf(name -> name == null || name.isEmpty());
        return names;
    }

    // The XPaths an element reads itself: an XSL instruction's select and test, a result element's {...}.
    private static List<String> read(final XmlElement element) {
        final List<String> out = new ArrayList<>();
        for (final XmlElement.Attribute attribute : element.attributes()) {
            if (isXsl(element)) {
                if ("select".equals(attribute.key()) || "test".equals(attribute.key())) {
                    out.add(attribute.value());
                }
            } else {
                final Matcher matcher = AVT.matcher(attribute.value());
                while (matcher.find()) {
                    out.add(matcher.group(1));
                }
            }
        }
        return out;
    }

    /// @param code An XSLT's code.
    /// @return What it writes (element and key names, constants) and reads (XPaths), each counted; empty if it
    /// isn't well-formed.
    public static Outline outline(final String code) {
        final Outline outline = new Outline(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>());
        final XmlElement root;
        try {
            root = XmlParser.parse(DECLARATION.matcher(XsltVersionHistory.strip(Objects.requireNonNullElse(code, "")))
                    .replaceFirst(""));
        } catch (final XmlParser.XmlParseException e) {
            return outline;
        }
        // A variable is read as the expression it holds, where it is read: the same field, read once into a variable
        // or three times where it's used (variable_min_reads, or the generator before it wrote variables), is the
        // same.
        final Map<String, String> held = new LinkedHashMap<>();
        for (final XmlElement variable : root.iterElements(XSL, "variable")) {
            final String name = variable.get("name");
            final String select = variable.get("select");
            if (name != null && !name.isEmpty() && select != null && !select.isEmpty() && !select.contains("$")) {
                held.put(name, select);
            }
        }
        for (final XmlElement element : root.iterElements()) {
            if (element.is(XSL, "variable") && held.containsKey(element.get("name"))) {
                continue;       // read where it is used
            }
            for (final String item : written(element)) {
                outline.writes().merge(item, 1, Integer::sum);
                final String[] beforeValue = item.split(" = ", -1)[0].split(" ", -1);
                outline.names().computeIfAbsent(item, ignored -> new HashSet<>())
                        .add(beforeValue[beforeValue.length - 1]);
                // What it is written from: a key renamed by hand still reads its field's source.
                final Set<String> within = outline.within().computeIfAbsent(item, ignored -> new HashSet<>());
                for (final XmlElement inside : element.iterElements()) {
                    for (final String value : read(inside)) {
                        if (!expression(value).isEmpty()) {
                            within.add(expression(expanded(value, held)));
                        }
                    }
                }
            }
            final boolean inXsl = isXsl(element);
            for (final String value : read(element)) {
                final String item = expression(expanded(value, held));
                if (!item.isEmpty()) {
                    outline.reads().merge(item, 1, Integer::sum);
                    final Set<String> names = outline.names().computeIfAbsent(item, ignored -> new HashSet<>());
                    if (inXsl) {
                        names.addAll(context(element));
                    } else {
                        names.add(named(element));
                    }
                }
            }
        }
        return outline;
    }

    private static String expanded(final String value, final Map<String, String> held) {
        final Matcher matcher = VARIABLE.matcher(value);
        final StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            final String select = held.get(matcher.group(1));
            final String replacement = select == null
                    ? matcher.group()
                    : SIMPLE_PATH.matcher(select).matches()
                            ? select
                            : "(" + select + ")";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // The same path, or one a step relative to the other (a template's field read from its own element).
    private static boolean related(final String a, final String b) {
        return a.equals(b) || a.endsWith("/" + b) || b.endsWith("/" + a);
    }

    /// What the hand edit (current, against base: what the server saved, regenerated) changed that new code undoes.
    /// With no base (nothing to regenerate it from), everything the XSLT writes and reads counts as the edit's.
    ///
    /// @param base    The code the server saved, regenerated, or null.
    /// @param current The code as it is, edited by hand.
    /// @param code    The new code.
    /// @return What the new code undoes of the edit.
    public static List<Undone> undone(final String base, final String current, final String code) {
        final Outline b = outline(Objects.requireNonNullElse(base, ""));
        final Outline c = outline(current);
        final Outline n = outline(code);
        final List<Undone> out = new ArrayList<>();
        undone(out, "writes", b.writes(), c.writes(), n.writes(), b, c, n);
        undone(out, "reads", b.reads(), c.reads(), n.reads(), b, c, n);
        return out;
    }

    private static void undone(final List<Undone> out,
                               final String verb,
                               final Map<String, Integer> bc,
                               final Map<String, Integer> cc,
                               final Map<String, Integer> nc,
                               final Outline b,
                               final Outline c,
                               final Outline n) {
        for (final Map.Entry<String, Integer> entry : cc.entrySet()) {
            final String item = entry.getKey();
            final int count = entry.getValue();
            if (count > bc.getOrDefault(item, 0)) {
                final int have = nc.entrySet().stream()
                        .filter(x -> "reads".equals(verb)
                                ? related(item, x.getKey())
                                : x.getKey().equals(item))
                        .mapToInt(Map.Entry::getValue)
                        .sum();
                if (have < count) {
                    out.add(new Undone(verb, item, false, Set.copyOf(c.names().getOrDefault(item, Set.of())),
                            Set.copyOf(c.within().getOrDefault(item, Set.of()))));
                }
            }
        }
        for (final Map.Entry<String, Integer> entry : bc.entrySet()) {
            final String item = entry.getKey();
            final int count = entry.getValue();
            final int now = cc.getOrDefault(item, 0);
            if (now < count && nc.getOrDefault(item, 0) > now) {
                final Set<String> names = new HashSet<>(n.names().getOrDefault(item, Set.of()));
                names.addAll(b.names().getOrDefault(item, Set.of()));
                final Set<String> within = new HashSet<>(n.within().getOrDefault(item, Set.of()));
                within.addAll(b.within().getOrDefault(item, Set.of()));
                out.add(new Undone(verb, item, true, Set.copyOf(names), Set.copyOf(within)));
            }
        }
    }

    /// @param base    The code the server saved, regenerated, or null.
    /// @param current The code as it is, edited by hand.
    /// @param code    The new code.
    /// @return What the new code undoes of the edit, said: `no longer writes number bytes`, `reads x again`.
    public static List<String> lost(final String base, final String current, final String code) {
        return undone(base, current, code).stream().map(Undone::toString).toList();
    }

    /// A mapping's or plan's fields, by a label the user knows them by: an index field's name, a CEF key (per kind
    /// of event), a translation's path (per rule).
    ///
    /// @param kind    `index`, `cef` or `translation`.
    /// @param payload The plan or mapping kept (for a translation, its block, holding `mapping`), or null.
    /// @return Each field, by label.
    @SuppressWarnings("unchecked")
    public static Map<String, PlanField> planFields(final String kind, final Map<String, Object> payload) {
        final Map<String, PlanField> out = new LinkedHashMap<>();
        if (payload == null || payload.isEmpty()) {
            return out;
        }
        switch (kind) {
            case "index" -> {
                for (final Map<String, Object> f : maps(payload.get("fields"))) {
                    final String name = (String) f.get("name");
                    final List<String> parts = PyStr.split(name, ".");
                    out.put(name, new PlanField(PyJson.dumpsSortedKeys(f), name + " (" + str(f.get("type"))
                                                                          + ") from " + str(f.get("source"))
                                                                          + (truthy(f.get("transform"))
                            ? ", as " + str(f.get("transform"))
                            : ""), Set.of(parts.getLast()), Set.of(expression(
                            f.get("source") instanceof final String source
                                    ? source
                                    : ""))));
                }
            }
            case "cef" -> cefFields(payload, out);
            case "translation" -> {
                final Map<String, Object> mapping = payload.containsKey("mapping")
                        ? (Map<String, Object>) payload.get("mapping")
                        : payload;
                final String input = truthy(mapping.get("input"))
                        ? (String) mapping.get("input")
                        : "data_splitter";
                final List<Object[]> rules = new ArrayList<>();
                rules.add(new Object[]{"common", maps(mapping.get("common"))});
                for (final Map<String, Object> rule : maps(mapping.get("events"))) {
                    rules.add(new Object[]{rule.get("name"), maps(rule.get("fields"))});
                }
                for (final Object[] rule : rules) {
                    for (final Map<String, Object> e : (List<Map<String, Object>>) rule[1]) {
                        translationField(String.valueOf(rule[0]), e, input, out);
                    }
                }
            }
            default -> {
                // No other kind has fields.
            }
        }
        return out;
    }

    private static void cefFields(final Map<String, Object> payload, final Map<String, PlanField> out) {
        for (final String header : List.of("vendor", "product", "version", "signature", "name", "severity")) {
            final Map<String, Object> value = payload.get(header) instanceof final Map<?, ?> given && !given.isEmpty()
                    ? map(given)
                    : Map.of();
            final String said = "header " + header + ": " + (value.get("value") != null
                    ? "'" + str(value.get("value")) + "'"
                    : "from " + str(value.get("source")));
            out.put("header " + header, new PlanField(PyJson.dumpsSortedKeys(value), said, Set.of(),
                    truthy(value.get("source"))
                            ? Set.of(expression((String) value.get("source")))
                            : Set.of()));
        }
        final List<Object[]> events = new ArrayList<>();
        events.add(new Object[]{"common", maps(payload.get("common"))});
        if (payload.get("events") instanceof final Map<?, ?> byKind) {
            byKind.forEach((event, fields) -> events.add(new Object[]{event, maps(fields)}));
        }
        for (final Object[] event : events) {
            @SuppressWarnings("unchecked") final List<Map<String, Object>> fields =
                    (List<Map<String, Object>>) event[1];
            for (final Map<String, Object> f : fields) {
                final String key = str(f.get("key"));
                out.put(event[0] + ": " + key, new PlanField(PyJson.dumpsSortedKeys(f), key + " from "
                                                                                       + str(f.get("path"))
                                                                                       + (truthy(f.get("label"))
                        ? " (label '" + str(f.get("label")) + "')"
                        : ""), Set.of(key), Set.of(expression(str(f.get("path"))), "'" + key + "'")));
            }
        }
    }

    private static void translationField(final String rule,
                                         final Map<String, Object> e,
                                         final String input,
                                         final Map<String, PlanField> out) {
        final String path = str(e.get("path"));
        final String label = rule + ": " + path + (truthy(e.get("data_name"))
                ? " [" + str(e.get("data_name")) + "]"
                : "");
        final List<String> inputs = new ArrayList<>();
        if (truthy(e.get("field"))) {
            inputs.add(str(e.get("field")));
        }
        if (e.get("any_of") instanceof final List<?> anyOf) {
            anyOf.forEach(field -> inputs.add(str(field)));
        }
        final Set<String> sources = new HashSet<>();
        inputs.forEach(field -> sources.add(expression(XPathText.fieldText(input, field))));
        if (truthy(e.get("xpath"))) {
            sources.add(expression(str(e.get("xpath"))));
        }
        final List<String> said = new ArrayList<>();
        e.forEach((key, value) -> {
            if (!"path".equals(key)) {
                said.add(key + " " + (value instanceof final String text
                        ? text
                        : PythonRepr.repr(value)));
            }
        });
        final Set<String> names = new HashSet<>();
        final List<String> steps = PyStr.split(path, "/");
        names.add(steps.getLast());
        if (truthy(e.get("data_name"))) {
            names.add(str(e.get("data_name")));
        }
        out.put(label, new PlanField(PyJson.dumpsSortedKeys(e), label + ": " + String.join(", ", said),
                Set.copyOf(names), Set.copyOf(sources)));
    }

    /// Whether something the edit changed is (part of) the field: written as its name, reading what it reads, or
    /// written from what it reads (a key renamed by hand: `<string key="created_at">` still reads event.created's
    /// source).
    ///
    /// @param undone Something the edit changed.
    /// @param field  A plan's field.
    /// @return True if it is the field's.
    public static boolean touches(final Undone undone, final PlanField field) {
        if (undone.names().stream().anyMatch(field.names()::contains)) {
            return true;
        }
        final List<String> read = "reads".equals(undone.verb())
                ? List.of(undone.item())
                : List.copyOf(undone.within());
        for (final String s : field.sources()) {
            for (final String r : read) {
                if (!s.isEmpty() && !r.isEmpty() && (r.contains(s) || related(r, s))) {
                    return true;
                }
            }
        }
        return false;
    }

    /// The fields the agent's change (kept -> new) changes that the hand edit changed too.
    ///
    /// @param kind  `index`, `cef` or `translation`.
    /// @param kept  The plan or mapping kept with the XSLT, or null.
    /// @param plan  The plan or mapping the change proposes, or null.
    /// @param items What new code undoes of the edit.
    /// @return By label, sorted: what the plan had, what the change proposes (null: removed), and what of the edit
    /// it would undo.
    public static Map<String, Collision> collisions(final String kind,
                                                    final Map<String, Object> kept,
                                                    final Map<String, Object> plan,
                                                    final List<Undone> items) {
        final Map<String, PlanField> before = planFields(kind, kept);
        final Map<String, PlanField> after = planFields(kind, plan);
        final Set<String> labels = new TreeSet<>(before.keySet());
        labels.addAll(after.keySet());
        final Map<String, Collision> out = new LinkedHashMap<>();
        for (final String label : labels) {
            final PlanField old = before.get(label);
            final PlanField proposed = after.get(label);
            if (old != null && proposed != null && old.spec().equals(proposed.spec())) {
                continue;
            }
            final List<Undone> hit = items.stream()
                    .filter(item -> (old != null && touches(item, old)) || (proposed != null && touches(item,
                            proposed)))
                    .toList();
            if (!hit.isEmpty()) {
                out.put(label, new Collision(old == null
                        ? null
                        : old.said(), proposed == null
                        ? null
                        : proposed.said(), hit));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(final Object value) {
        if (!(value instanceof final List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Map<?, ?> value) {
        return (Map<String, Object>) value;
    }

    // Python's str() of a value in an f-string.
    private static String str(final Object value) {
        return value instanceof final String text
                ? text
                : PythonRepr.repr(value);
    }

    private static boolean truthy(final Object value) {
        return switch (value) {
            case null -> false;
            case final String text -> !text.isEmpty();
            case final Boolean bool -> bool;
            case final List<?> list -> !list.isEmpty();
            case final Map<?, ?> map -> !map.isEmpty();
            case final Number number -> number.doubleValue() != 0;
            default -> true;
        };
    }


    // --------------------------------------------------------------------------------


    /// What an XSLT writes and reads.
    ///
    /// @param writes Each element or key name written (`number bytes`), and constant (`Environment = Test`),
    ///               counted.
    /// @param reads  Each XPath read, counted.
    /// @param names  Each item: the names it is written as.
    /// @param within Each item written: the XPaths read inside it, its value's source.
    public record Outline(Map<String, Integer> writes,
                         Map<String, Integer> reads,
                         Map<String, Set<String>> names,
                         Map<String, Set<String>> within) {

    }

    /// Something the edit changed that new code undoes: an item it added and new code lacks, or one it took out
    /// that new code has again.
    ///
    /// @param verb   `writes` or `reads`.
    /// @param item   What is written or read.
    /// @param again  True if the edit took it out and new code has it again.
    /// @param names  What it is written as, to match it to a plan's field.
    /// @param within What an element written reads inside it (its value's source).
    public record Undone(String verb, String item, boolean again, Set<String> names, Set<String> within) {

        @Override
        public String toString() {
            return again
                    ? verb + " " + item + " again"
                    : "no longer " + verb + " " + item;
        }
    }

    /// A field of a plan or mapping.
    ///
    /// @param spec    What the plan says of it, to tell a change (its JSON, keys sorted).
    /// @param said    The same, for the user.
    /// @param names   What it is written as.
    /// @param sources What it reads, as expressions.
    public record PlanField(String spec, String said, Set<String> names, Set<String> sources) {

    }

    /// A field the agent's change and the hand edit both change.
    ///
    /// @param had      What the plan had, or null if the field is new.
    /// @param proposes What the change proposes, or null if it removes the field.
    /// @param undone   What of the edit the change would undo.
    public record Collision(String had, String proposes, List<Undone> undone) {

    }
}
