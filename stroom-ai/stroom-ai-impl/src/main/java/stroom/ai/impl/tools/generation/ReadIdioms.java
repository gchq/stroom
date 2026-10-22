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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// An expression read for what the generator writes (its shapes, [ReadShapes]): which input it reads, and how. The
/// Stroom MCP server's `xsltread._Idioms`.
///
/// Entries are built as JSON-like maps, in the order the Python reader builds its dicts, so the mapping read back
/// is the one the server reads back.
final class ReadIdioms {

    private static final Pattern WORD_KEY = Pattern.compile("\\w+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]+");
    private static final Pattern NOT_WORD = Pattern.compile("\\W+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> PLAIN_SOURCES = Set.of("field", "any_of");
    private static final List<String> TIMES = List.of("time, zone", "time", "time, ms", "epoch_ms", "epoch_s",
            "epoch_us", "epoch_ns", "epoch_s, truncated");

    private final XsltReading reading;
    private final String kind;
    private final XPathTreeParser parser;
    // The mapping's extract list, added to as extracted values are read.
    private final List<Map<String, Object>> extracts;
    // Dictionaries: the stylesheet variables holding them, by kind (their Dictionary doc's name, and map or list).
    private final Map<String, List<String>> dictionaries = new LinkedHashMap<>();

    /// @param reading  The XSLT read.
    /// @param kind     The input: data_splitter, json, xml or xml_fragments.
    /// @param extracts The mapping's extract entries (as JSON-like maps), added to as extracted values are read.
    ReadIdioms(final XsltReading reading, final String kind, final List<Map<String, Object>> extracts) {
        this.reading = reading;
        this.kind = kind;
        this.parser = reading.parser();
        this.extracts = extracts;
        reading.globals().forEach((name, select) -> {
            final XPathNode node = XsltReader.parseOrNull(select, parser);
            for (final String dictionaryKind : List.of("map", "list")) {
                final XPathMatch match = unify("dictionary " + dictionaryKind, node);
                if (match != null) {
                    dictionaries.put(name, List.of(match.string(1), dictionaryKind));
                }
            }
        });
    }

    /// @return The extract entries, with those read.
    List<Map<String, Object>> extracts() {
        return extracts;
    }

    private static XPathMatch unify(final String shape, final XPathNode node) {
        return unify(ReadShapes.SHAPES.get(shape), node);
    }

    private static XPathMatch unify(final XPathNode shape, final XPathNode node) {
        return node == null
                ? null
                : XPathTrees.unify(shape, node).orElse(null);
    }

    // --- inputs ---------------------------------------------------------------------------------------------------

    /// @param given An expression.
    /// @return The input field it reads (its first value, or as it is), or the name of a value an extraction gives;
    /// null if it is neither.
    String fieldOf(final XPathNode given) {
        final XPathNode normal = XPathTrees.normal(given);
        // A first value's input, unless it is a node of no children (Python takes those for false).
        final XPathNode first = XPathTrees.firstOf(normal).orElse(null);
        final XPathNode node = XsltReader.truthy(first)
                ? first
                : normal;
        final List<XPathNode> fields = ReadShapes.FIELDS.getOrDefault(kind, List.of());
        for (int depth = 1; depth <= fields.size(); depth++) {
            final XPathMatch match = unify(fields.get(depth - 1), node);
            if (match != null) {
                final List<String> names = new ArrayList<>();
                for (int n = 1; n <= depth; n++) {
                    names.add(match.string(n));
                }
                return String.join(ReadShapes.SEPARATOR.get(kind), names);
            }
        }
        return extracted(node);
    }

    // A value an extraction gives: a key=value function's (key="..." in a text field) or an analyze-string group.
    private String extracted(final XPathNode node) {
        final String function = "call".equals(node.symbol()) && node.valueText().startsWith("mcp:")
                ? node.valueText().substring(4)
                : null;
        if (function != null && reading.keyed().containsKey(function) && node.size() == 2
            && "(string)".equals(XPathTrees.normal(node.get(1)).symbol())) {
            final XsltReading.KeyedFunction keyed = reading.keyed().get(function);
            // A rule's condition passes the text as normalize-space(...) when the mapping has no nil values (the
            // generator's own form there, which regenerating writes again).
            final XPathNode called = call(node.get(0), "normalize-space");
            final XPathNode text = XsltReader.truthy(called)
                    ? called
                    : node.get(0);
            final String source = fieldOf(text);
            if (XsltReader.truthy(source)) {
                final String key = XPathTrees.normal(node.get(1)).valueText();
                // A key of several words ('Source IP') is named in snake case, as the generator reads it back.
                final String name = WORD_KEY.matcher(key).matches()
                        ? key
                        : PyStr.strip(NOT_ALPHANUMERIC.matcher(key).replaceAll("_"), "_").toLowerCase(Locale.ROOT);
                return extraction(source, keyed.before() + key + keyed.after(), keyed.flags(), List.of(name))
                        .getFirst();
            }
        }
        for (final String shape : List.of("group, flags", "group")) {
            final XPathMatch match = unify(shape, node);
            if (match != null && "(integer)".equals(XPathTrees.normal(match.expr(2)).symbol())) {
                final String source = fieldOf(match.expr(1));
                if (XsltReader.truthy(source)) {
                    final String regex = match.string(1);
                    final String flags = "group, flags".equals(shape)
                            ? match.string(2)
                            : null;
                    String base = PyStr.strip(NOT_WORD.matcher(source).replaceAll("_"), "_");
                    if (base.isEmpty()) {
                        base = "text";
                    }
                    final List<String> named = new ArrayList<>();
                    for (int n = 1; n <= regexGroups(regex); n++) {
                        named.add(base + "_" + n);
                    }
                    // The entry's own names: a group past them is named onto them, as the Python reader does.
                    final List<String> names = extraction(source, regex, flags, named);
                    final int nr = ((BigInteger) XPathTrees.normal(match.expr(2)).value()).intValue();
                    while (names.size() < nr) {
                        names.add(names.getFirst() + "_" + (names.size() + 1));
                    }
                    // Python's names[nr - 1]: group 0 is the last name.
                    final int at = nr - 1 < 0
                            ? names.size() + nr - 1
                            : nr - 1;
                    final String found = at >= 0 && at < names.size()
                            ? names.get(at)
                            : null;
                    return XsltReader.truthy(found)
                            ? found
                            : null;
                }
            }
        }
        return null;
    }

    // How many groups a regular expression has (Stroom's are Java's; Python reads the ones the generator writes).
    private static int regexGroups(final String regex) {
        try {
            return Math.max(1, PythonRegex.groups(regex));
        } catch (final PythonRegex.PythonRegexException e) {
            return 1;
        }
    }

    // The extract entry for this regex on this field (the kept one, or a new one), and its names.
    @SuppressWarnings("unchecked")
    private List<String> extraction(final String source,
                                    final String regex,
                                    final String flags,
                                    final List<String> names) {
        for (final Map<String, Object> entry : extracts) {
            if (Objects.equals(entry.get("regex"), regex) && Objects.equals(entry.get("field"), source)) {
                return (List<String>) entry.get("names");
            }
        }
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("field", source);
        entry.put("regex", regex);
        entry.put("names", names);
        if (XsltReader.truthy(flags)) {
            entry.put("flags", flags);
        }
        extracts.add(entry);
        return names;
    }

    /// @param node An expression.
    /// @return An input, as a mapping entry gives it: a field, the first of several (any_of), a reference data
    /// lookup, a dictionary value; or null.
    Map<String, Object> sourceOf(final XPathNode node) {
        final String field = fieldOf(node);
        if (XsltReader.truthy(field)) {
            return entry("field", field);
        }
        // A single-valued input written where only its having a value matters (a map key, say): the input itself.
        XPathMatch match = unify("has value", node);
        if (match != null && XPathTrees.firstOf(match.expr(1)).isEmpty()
            && ("data_splitter".equals(kind) || "json".equals(kind)) && reading.nilValues().isEmpty()) {
            final String inner = fieldOf(match.expr(1));
            if (XsltReader.truthy(inner)) {
                return entry("field", inner);
            }
        }
        match = unify("any of", node);
        if (match != null) {
            final List<XPathNode> parts = XPathTrees.items(match.expr(1));
            final List<String> fields = parts.stream().map(this::fieldOf).toList();
            if (parts.size() > 1 && fields.stream().allMatch(XsltReader::truthy)) {
                return entry("any_of", fields);
            }
        }
        final Map<String, Object> lookup = lookupOf(node);
        if (lookup != null) {
            return lookup;
        }
        match = unify("dictionary", node);
        if (match != null && dictionaries.containsKey(match.expr(1).valueText())
            && "map".equals(dictionaries.get(match.expr(1).valueText()).get(1))) {
            final Map<String, Object> inner = sourceOf(match.expr(2));
            if (inner != null && !inner.isEmpty() && PLAIN_SOURCES.containsAll(inner.keySet())) {
                final Map<String, Object> out = new LinkedHashMap<>(inner);
                out.put("dictionary", dictionaries.get(match.expr(1).valueText()).getFirst());
                return out;
            }
        }
        return null;
    }

    private Map<String, Object> lookupOf(final XPathNode node) {
        final List<XPathNode> shapes = new ArrayList<>();
        shapes.add(ReadShapes.SHAPES.get("lookup"));
        shapes.addAll(ReadShapes.LOOKUP_BELOW);
        for (int depth = 0; depth < shapes.size(); depth++) {
            final XPathMatch match = unify(shapes.get(depth), node);
            if (match != null) {
                final String key = fieldOf(match.expr(1));
                final Map<String, Object> lookup = new LinkedHashMap<>();
                lookup.put("map", match.string(1));
                if (XsltReader.truthy(key)) {
                    lookup.put("field", key);
                } else {
                    lookup.put("xpath", portable(XPathTrees.normal(match.expr(1)).text()));
                }
                if (depth > 0) {
                    final List<String> steps = new ArrayList<>();
                    for (int n = 2; n < depth + 2; n++) {
                        steps.add(match.expr(n).valueText());
                    }
                    lookup.put("path", String.join("/", steps));
                }
                return entry("lookup", lookup);
            }
        }
        return null;
    }

    // --- values ---------------------------------------------------------------------------------------------------

    /// @param leaf An element read.
    /// @return The entry writing it, and whether it had to keep the expression as it is (an xpath entry).
    Entry entry(final LeafReading leaf) {
        final Map<String, Object> base = new LinkedHashMap<>();
        base.put("path", leaf.path());
        if (XsltReader.truthy(leaf.dataName())) {
            base.put("data_name", leaf.dataName());
        }
        if (leaf.value() != null) {
            base.put("value", leaf.value());
            return new Entry(base, false);
        }
        final String text = inline(Objects.requireNonNullElse(leaf.expr(), ""));
        final XPathNode node = XsltReader.parseOrNull(text, parser);
        if (node == null) {
            base.put("xpath", text);
            return new Entry(base, true);
        }
        // First: a map of one key is its value, guarded by the key.
        final Map<String, Object> found = idiom(node, leaf.guard());
        if (found != null && !found.isEmpty()) {
            base.putAll(found);
            return new Entry(base, false);
        }
        final XPathNode normal = XPathTrees.normal(node);
        if ("(string)".equals(normal.symbol())) {
            // A string, passed as a parameter, say.
            base.put("value", normal.valueText());
            return new Entry(base, false);
        }
        base.put("xpath", portable(normal.text()));
        return new Entry(base, true);
    }

    private Map<String, Object> idiom(final XPathNode node, final String guard) {
        final Map<String, Object> found = sourceOf(node);
        if (found != null && !found.isEmpty()) {
            return found;
        }
        XPathMatch match = unify("default", node);
        if (match != null) {
            final Map<String, Object> inner = idiom(match.expr(2), null);
            final XPathNode called = call(match.expr(1), "normalize-space", "exists");
            final XPathNode has = XsltReader.truthy(called)
                    ? called
                    : match.expr(1);
            final Map<String, Object> source = sourceOf(has);
            if (inner != null && !inner.isEmpty() && source != null && !source.isEmpty()
                && source.entrySet().stream().allMatch(e -> Objects.equals(inner.get(e.getKey()), e.getValue()))
                && !inner.containsKey("default")) {
                final Map<String, Object> out = new LinkedHashMap<>(inner);
                out.put("default", match.string(1));
                return out;
            }
        }
        final Map<String, Object> mapped = inlineMap(node, guard);
        if (mapped != null && !mapped.isEmpty()) {
            return mapped;
        }
        for (final String shape : List.of("map lookup, default", "map lookup")) {
            match = unify(shape, node);
            if (match != null && reading.maps().containsKey(match.expr(1).valueText())) {
                final Map<String, Object> inner = converted(match.expr(2));
                if (inner != null && !inner.isEmpty()) {
                    final Map<String, Object> out = new LinkedHashMap<>(inner);
                    out.put("map", reading.maps().get(match.expr(1).valueText()));
                    if ("map lookup, default".equals(shape)) {
                        out.put("default", match.string(1));
                    }
                    return out;
                }
            }
        }
        for (final String shape : TIMES) {
            match = unify(shape, node);
            if (match != null) {
                final Map<String, Object> inner = converted(match.expr(1));
                if (inner != null && !inner.isEmpty()) {
                    final String format = shape.startsWith("epoch")
                            ? shape.split(",")[0]
                            : match.string(1);
                    final String zone = "time, zone".equals(shape) || "time, ms".equals(shape)
                            ? match.string(2)
                            : null;
                    final Map<String, Object> out = new LinkedHashMap<>(inner);
                    out.put("time_format", format);
                    if (XsltReader.truthy(zone)) {
                        out.put("timezone", zone);
                    }
                    return out;
                }
            }
        }
        return converted(node);
    }

    // An input, transformed or not.
    private Map<String, Object> converted(final XPathNode node) {
        final Map<String, Object> found = sourceOf(node);
        if (found != null && !found.isEmpty()) {
            return found;
        }
        for (final Map.Entry<String, XPathNode> transform : ReadShapes.TRANSFORMS.entrySet()) {
            final XPathMatch match = unify(transform.getValue(), node);
            if (match != null) {
                final Map<String, Object> inner = sourceOf(match.expr(1));
                if (inner != null && !inner.isEmpty() && PLAIN_SOURCES.containsAll(inner.keySet())) {
                    final Map<String, Object> out = new LinkedHashMap<>(inner);
                    out.put("transform", transform.getKey());
                    return out;
                }
            }
        }
        return null;
    }

    // X = ('a', 'b'): the input and the keys (oneOfText).
    private KeysListed keysListed(final XPathNode node) {
        final XPathMatch match = unify("one of", node);
        final List<String> listed = match != null
                ? XPathTrees.strings(match.expr(2)).orElse(null)
                : null;
        return listed != null && !listed.isEmpty()
                ? new KeysListed(XPathTrees.normal(match.expr(1)), listed)
                : null;
    }

    // A value map written inline (mapStepText, key after key): its keys, and its default or its last key.
    private Map<String, Object> inlineMap(final XPathNode node, final String guard) {
        final Map<String, String> pairs = new LinkedHashMap<>();
        XPathNode rest = XPathTrees.normal(node);
        XPathNode key = null;
        while (true) {
            final XPathMatch match = unify("map step", rest);
            if (match == null) {
                break;
            }
            final XPathNode step = XPathTrees.normal(match.expr(1));
            if (key != null && !step.key().equals(key.key())) {
                return null;
            }
            key = step;
            pairs.put(match.string(1), match.string(2));
            rest = XPathTrees.normal(match.expr(2));
        }
        if (!"(string)".equals(rest.symbol())) {
            return null;
        }
        // Without a default the generator writes the last key's value as the final else, the element guarded by
        // the keys (oneOfText): that guard naming one key more than the steps is the last key, not a default. A
        // map of one key is then no steps at all: the value, guarded by its key.
        final KeysListed keys = XsltReader.truthy(guard)
                ? keysListed(XsltReader.parseOrNull(guard, parser))
                : null;
        if (keys != null && (key == null || keys.input().key().equals(key.key()))) {
            key = keys.input();
            final List<String> extra = keys.listed().stream().filter(listed -> !pairs.containsKey(listed)).toList();
            final Map<String, Object> inner = converted(key);
            if (inner != null && !inner.isEmpty() && extra.size() == 1
                && keys.listed().size() == pairs.size() + 1) {
                final Map<String, String> map = new LinkedHashMap<>(pairs);
                map.put(extra.getFirst(), rest.valueText());
                final Map<String, Object> out = new LinkedHashMap<>(inner);
                out.put("map", map);
                return out;
            }
        }
        if (pairs.isEmpty()) {
            return null;
        }
        final Map<String, Object> inner = converted(key);
        if (inner == null || inner.isEmpty()) {
            return null;
        }
        final Map<String, Object> out = new LinkedHashMap<>(inner);
        out.put("map", pairs);
        out.put("default", rest.valueText());
        return out;
    }

    /// @param given An expression.
    /// @return It with the XSLT's own single-expression functions replaced by their bodies (mcp:data aside).
    String inline(final String given) {
        String text = given;
        for (int round = 0; round < 5; round++) {
            final XPathNode tree = XsltReader.parseOrNull(text, parser);
            if (tree == null) {
                return text;
            }
            final List<XPathTrees.Replacement> calls = new ArrayList<>();
            for (final XPathNode node : tree.walk()) {
                final XsltReading.Function function = "call".equals(node.symbol())
                        ? reading.functions().get(node.valueText())
                        : null;
                if (function != null && function.params().size() == node.size()) {
                    final Map<String, String> bound = new LinkedHashMap<>();
                    for (int i = 0; i < node.size(); i++) {
                        bound.put(function.params().get(i), node.get(i).text());
                    }
                    calls.add(new XPathTrees.Replacement(node, "(" + XsltReader.resolve(function.body(), bound,
                            parser) + ")"));
                }
            }
            if (calls.isEmpty()) {
                break;
            }
            text = XPathTrees.splice(text, calls);
        }
        return text;
    }

    /// @param given An expression.
    /// @return It as it reads in a regenerated XSLT: its stylesheet variables written in, and JSON read through
    /// json-to-xml() (the generator puts its guarded helper back).
    String portable(final String given) {
        String text = given;
        XPathNode tree = XsltReader.parseOrNull(text, parser);
        if (tree != null) {
            final List<XPathTrees.Replacement> maps = new ArrayList<>();
            for (final XPathNode variable : XsltReader.variables(tree)) {
                final Map<String, String> map = reading.maps().get(variable.valueText());
                if (map != null) {
                    final List<String> entries = new ArrayList<>();
                    map.forEach((key, value) -> entries.add(XPathText.literal(key) + ": " + XPathText.literal(value)));
                    maps.add(new XPathTrees.Replacement(variable, "map{" + String.join(", ", entries) + "}"));
                }
            }
            if (!maps.isEmpty()) {
                text = XPathTrees.splice(text, maps);
            }
        }
        text = XsltReader.resolve(text, reading.globals(), parser);
        tree = XsltReader.parseOrNull(text, parser);
        if (tree != null) {
            final List<XPathTrees.Replacement> helper = new ArrayList<>();
            for (final XPathNode node : tree.walk()) {
                if ("call".equals(node.symbol()) && "mcp:json-to-xml".equals(node.value())) {
                    helper.add(new XPathTrees.Replacement(node, node.text().substring("mcp:".length())));
                }
            }
            if (!helper.isEmpty()) {
                text = XPathTrees.splice(text, helper);
            }
        }
        return text;
    }

    // --- conditions -----------------------------------------------------------------------------------------------

    /// @param test A rule's test.
    /// @return Its conditions, and whether any had to be kept as the expression itself.
    Conditions conditions(final String test) {
        final XPathNode tree = XsltReader.parseOrNull(test, parser);
        if (tree == null) {
            final Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("xpath", "self::*[" + test + "]");
            raw.put("present", true);
            return new Conditions(List.of(raw), true);
        }
        final List<Map<String, Object>> out = new ArrayList<>();
        boolean raw = false;
        for (final XPathNode part : XPathTrees.terms(tree, "and")) {
            final XPathNode negated = call(part, "not");
            final XPathNode body = negated != null
                    ? XPathTrees.unbracketed(negated)
                    : part;
            Map<String, Object> found = null;
            if (negated == null) {
                found = comparison(body);
            }
            if (found == null || found.isEmpty()) {
                final XPathNode called = call(body, "exists", "normalize-space");
                final XPathNode inner = XsltReader.truthy(called)
                        ? called
                        : body;
                final String field = fieldOf(inner);
                if (XsltReader.truthy(field)) {
                    found = new LinkedHashMap<>();
                    found.put("field", fieldOf(inner));
                    found.put("present", negated == null);
                }
            }
            if (found == null || found.isEmpty()) {
                found = new LinkedHashMap<>();
                found.put("xpath", "self::*[" + portable(part.text()) + "]");
                found.put("present", true);
                raw = true;
            }
            out.add(found);
        }
        return new Conditions(out, raw);
    }

    // A condition on one field: equal to a value (equalsText), one of several (oneOfText, its values in brackets),
    // matching a regex, or in a dictionary.
    private Map<String, Object> comparison(final XPathNode node) {
        XPathMatch match = unify("in dictionary", node);
        if (match != null && XsltReader.truthy(fieldOf(match.expr(1)))
            && dictionaries.containsKey(match.expr(2).valueText())) {
            return entry("field", fieldOf(match.expr(1)), "in_dictionary",
                    dictionaries.get(match.expr(2).valueText()).getFirst());
        }
        match = unify("matches", node);
        if (match != null && XsltReader.truthy(fieldOf(match.expr(1)))) {
            return entry("field", fieldOf(match.expr(1)), "matches", match.string(1));
        }
        // equalsText and oneOfText differ only by the brackets around the values: told apart as written.
        final XPathNode written = XPathTrees.unbracketed(node);
        final boolean bracketed = "=".equals(written.symbol()) && written.size() == 2
                                  && "(".equals(written.get(1).symbol());
        match = bracketed
                ? null
                : unify("equals", node);
        if (match != null && XsltReader.truthy(fieldOf(match.expr(1)))) {
            return entry("field", fieldOf(match.expr(1)), "equals", match.string(1));
        }
        final KeysListed keys = keysListed(node);
        if (keys != null && XsltReader.truthy(fieldOf(keys.input()))) {
            return entry("field", fieldOf(keys.input()), "one_of", keys.listed());
        }
        return null;
    }

    // name(x): x as written, when node is a call of one of these functions with one argument.
    private static XPathNode call(final XPathNode node, final String... names) {
        final XPathNode top = XPathTrees.unbracketed(node);
        if ("call".equals(top.symbol()) && List.of(names).contains(top.valueText()) && top.size() == 1) {
            return top.get(0);
        }
        return null;
    }

    private static Map<String, Object> entry(final String key, final Object value) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put(key, value);
        return out;
    }

    private static Map<String, Object> entry(final String key, final Object value, final String other,
                                             final Object otherValue) {
        final Map<String, Object> out = entry(key, value);
        out.put(other, otherValue);
        return out;
    }


    // --------------------------------------------------------------------------------


    /// An entry read, and whether it is kept as the expression itself (an xpath entry).
    ///
    /// @param json The entry, as JSON-like values.
    /// @param raw  Whether it is an xpath entry: not read as an idiom.
    record Entry(Map<String, Object> json, boolean raw) {

    }


    // --------------------------------------------------------------------------------


    /// A rule's conditions read, and whether any is kept as the expression itself.
    ///
    /// @param when The conditions, as JSON-like values.
    /// @param raw  Whether any is an xpath condition: not read as an idiom.
    record Conditions(List<Map<String, Object>> when, boolean raw) {

    }


    // --------------------------------------------------------------------------------


    private record KeysListed(XPathNode input, List<String> listed) {

    }
}
