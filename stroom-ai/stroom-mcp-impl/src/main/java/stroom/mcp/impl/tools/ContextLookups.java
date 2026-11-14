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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.generation.LookupKeys;
import stroom.ai.impl.tools.sample.RecordSplitter;
import stroom.ai.impl.tools.sample.XmlTree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// How a stream's records pick out entries of their part's context data: the entries read from the context (XML,
/// each entry an element, its fields XML paths relative to it; or JSON, each entry an object, its fields dotted key
/// paths, [ContextJson]), the records' fields read alike, and the key, the
/// combination of up to [LookupKeys#MAX_FIELDS] entry fields whose values pick out exactly one entry for most
/// records. Each part's records are matched against that part's own context, as a Context lookup reads them.
///
/// The key is found cheaply by value sets: an entry field is a candidate when most of some record field's values are
/// among its values; combinations of candidates (smallest first) that tell a part's entries apart are then counted
/// against the records.
final class ContextLookups {

    /// The most candidate entry fields combined into keys.
    static final int MAX_CANDIDATES = 10;
    /// The share of a record field's values that must be among an entry field's for the two to be paired.
    static final double PAIRED = 0.5;
    /// The most keys proposed.
    static final int MAX_KEYS = 3;

    // Better pairs first: most of the record field's values among the entry field's, most of the entry field's
    // values in the record field, the same name, the shorter path.
    private static final Comparator<Pair> PAIR_ORDER = Comparator.comparingDouble(Pair::share).reversed()
            .thenComparing(Comparator.comparingDouble(Pair::coverage).reversed())
            .thenComparing(pair -> !pair.sameName())
            .thenComparingInt(pair -> pair.eventField().length());
    // A path that names an input field as it is (no function or predicate in it).
    private static final Pattern PLAIN_PATH = Pattern.compile("[\\w@./:-]+", Pattern.UNICODE_CHARACTER_CLASS);

    private ContextLookups() {
        // Static only.
    }

    /// @param context The context data: an XML document, or JSON (read as [ContextJson] says).
    /// @param entries XML: the entries' path below its root element (`/`-separated names), or null for the root's
    ///                most common child. JSON: the entries' array's key path in one object, or null to find it.
    /// @return The context's entries, and where they are.
    /// @throws ToolRefusal if the context is neither XML nor JSON, or holds no such entries.
    static Context entries(final String context, final String entries) {
        final String text = context.strip();
        if (text.startsWith("{") || text.startsWith("[")) {
            return ContextJson.entries(text, entries);
        }
        final XmlTree.Element root;
        try {
            root = XmlTree.parse(context);
        } catch (final XmlTree.XmlException e) {
            throw new ToolRefusal("The context data isn't one XML document (" + e.getMessage() + ") or JSON: "
                                  + "context lookups read XML context data, each entry an element, or JSON, each "
                                  + "entry an object");
        }
        String path = entries == null || entries.isBlank()
                ? commonChild(root)
                : entries.strip();
        if (path == null) {
            throw new ToolRefusal("The context data's root element <" + root.localName() + "> holds no entries");
        }
        path = path.replaceFirst("^/+", "");
        if (path.startsWith(root.localName() + "/")) {
            path = path.substring(root.localName().length() + 1);
        }
        List<XmlTree.Element> found = List.of(root);
        for (final String step : path.split("/")) {
            final List<XmlTree.Element> next = new ArrayList<>();
            found.forEach(element -> element.elements().stream()
                    .filter(child -> child.localName().equals(step))
                    .forEach(next::add));
            found = next;
        }
        if (found.isEmpty()) {
            throw new ToolRefusal("The context data has no entries at " + root.localName() + "/" + path);
        }
        final List<Entry> out = new ArrayList<>();
        for (final XmlTree.Element element : found) {
            out.add(entry(element));
        }
        return new Context(root.localName(), root.namespace(), path, out, null);
    }

    /// @param text One part's raw text.
    /// @param max  The most records.
    /// @return Its records, each its fields' values in order (an XML record's fields its XML paths relative to it).
    static List<Map<String, List<String>>> records(final String text, final int max) {
        final RecordSplitter.Chunk chunk = RecordSplitter.split(text, max, false);
        final List<Map<String, List<String>>> out = new ArrayList<>();
        for (int i = 0; i < chunk.records().size(); i++) {
            final String record = chunk.records().get(i);
            if (record.stripLeading().startsWith("<")) {
                try {
                    final Map<String, List<String>> fields = new LinkedHashMap<>();
                    flatten(XmlTree.parse(record), "", fields);
                    out.add(fields);
                    continue;
                } catch (final XmlTree.XmlException e) {
                    // Not one XML element: read as the splitter read it.
                }
            }
            final Map<String, Object> parsed = chunk.parsedAt(i);
            final Map<String, List<String>> fields = new LinkedHashMap<>();
            if (parsed != null) {
                // A JSON array's items are the field's values, in order (a template's values, say).
                parsed.forEach((name, value) -> fields.put(name, value == null
                        ? List.of()
                        : value instanceof final List<?> items
                                ? items.stream().map(String::valueOf).toList()
                                : List.of(String.valueOf(value))));
            }
            out.add(fields);
        }
        return out;
    }

    /// The keys, best first: the entry fields' combination, the record fields that match them, and how many records
    /// each picks one entry out for. The key given (both lists) is counted as it is.
    ///
    /// @param parts         Each part's entries and records.
    /// @param contextFields The entry fields of a key given, or null to find them.
    /// @param eventFields   The record fields matching them, in order, or null.
    /// @return The keys, at most [#MAX_KEYS].
    static List<Key> keys(final List<Part> parts,
                          final List<String> contextFields,
                          final List<String> eventFields) {
        if (contextFields != null && eventFields != null) {
            if (contextFields.size() != eventFields.size() || contextFields.isEmpty()
                || contextFields.size() > LookupKeys.MAX_FIELDS) {
                throw new ToolRefusal("key_fields and event_key_fields name the key's fields in the same order, 1 "
                                      + "to " + LookupKeys.MAX_FIELDS + " of each");
            }
            return List.of(count(parts, contextFields, eventFields));
        }
        final List<Pair> pairs = pairs(parts);
        final List<Key> found = new ArrayList<>();
        for (int size = 1; size <= LookupKeys.MAX_FIELDS && size <= pairs.size(); size++) {
            for (final List<Pair> combination : combinations(pairs, size)) {
                final List<String> fields = combination.stream().map(Pair::contextField).toList();
                final List<String> read = combination.stream().map(Pair::eventField).toList();
                if (new HashSet<>(read).size() < read.size() || !tellsApart(parts, fields)) {
                    continue;
                }
                found.add(count(parts, fields, read));
            }
            if (found.stream().anyMatch(key -> key.matched() == key.records() && key.records() > 0)) {
                break;
            }
        }
        found.sort(Comparator.comparingInt(Key::matched).reversed()
                .thenComparingInt(key -> key.contextFields().size()));
        return List.copyOf(found.subList(0, Math.min(MAX_KEYS, found.size())));
    }

    /// @param part    A part.
    /// @param key     The key.
    /// @param record  One of its records.
    /// @return The one entry the record's key values pick out, or null for none (or several).
    static Entry matched(final Part part, final Key key, final Map<String, List<String>> record) {
        final String wanted = keyOf(record, key.eventFields());
        if (wanted == null) {
            return null;
        }
        Entry found = null;
        for (final Entry entry : part.entries()) {
            if (wanted.equals(keyOf(entry, key.contextFields()))) {
                if (found != null) {
                    return null;
                }
                found = entry;
            }
        }
        return found;
    }

    /// @param path A path as given.
    /// @return True if it names a field as it is, so a mapping reads it as `field` rather than `xpath`.
    static boolean plainPath(final String path) {
        return path != null && PLAIN_PATH.matcher(path).matches();
    }

    // The fields paired: each entry field with the record field most of whose values are among its values.
    private static List<Pair> pairs(final List<Part> parts) {
        final Map<String, Set<String>> entryValues = new LinkedHashMap<>();
        final Map<String, Integer> entryCounts = new HashMap<>();
        int entries = 0;
        for (final Part part : parts) {
            for (final Entry entry : part.entries()) {
                entries++;
                entry.fields().forEach((name, value) -> {
                    entryValues.computeIfAbsent(name, ignored -> new HashSet<>()).add(value);
                    entryCounts.merge(name, 1, Integer::sum);
                });
            }
        }
        final Map<String, List<String>> recordValues = new LinkedHashMap<>();
        for (final Part part : parts) {
            for (final Map<String, List<String>> record : part.records()) {
                record.forEach((name, values) -> {
                    if (!values.isEmpty()) {
                        recordValues.computeIfAbsent(name, ignored -> new ArrayList<>()).add(values.getFirst());
                    }
                });
            }
        }
        final List<Pair> out = new ArrayList<>();
        for (final Map.Entry<String, Set<String>> field : entryValues.entrySet()) {
            // A field every entry has.
            if (entryCounts.get(field.getKey()) < entries) {
                continue;
            }
            Pair best = null;
            for (final Map.Entry<String, List<String>> values : recordValues.entrySet()) {
                final long among = values.getValue().stream().filter(field.getValue()::contains).count();
                final double share = (double) among / values.getValue().size();
                if (share < PAIRED) {
                    continue;
                }
                // Of the entry field's values, how many the record field has: told apart from a field whose few
                // values happen to be among them (a level of 0 among versions 0 to 2).
                final Set<String> distinct = new HashSet<>(values.getValue());
                final double coverage = (double) field.getValue().stream().filter(distinct::contains).count()
                                        / field.getValue().size();
                final Pair pair = new Pair(field.getKey(), values.getKey(), share, coverage);
                if (best == null || PAIR_ORDER.compare(pair, best) < 0) {
                    best = pair;
                }
            }
            if (best != null) {
                out.add(best);
            }
        }
        out.sort(PAIR_ORDER);
        return List.copyOf(out.subList(0, Math.min(MAX_CANDIDATES, out.size())));
    }

    // Whether the fields' values tell every part's entries apart (each part read on its own, as its records are).
    private static boolean tellsApart(final List<Part> parts, final List<String> fields) {
        for (final Part part : parts) {
            final Set<String> seen = new HashSet<>();
            for (final Entry entry : part.entries()) {
                final String key = keyOf(entry, fields);
                if (key != null && !seen.add(key)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Key count(final List<Part> parts, final List<String> contextFields, final List<String> eventFields) {
        final Key unmatched = new Key(contextFields, eventFields, 0, 0);
        int matched = 0;
        int records = 0;
        for (final Part part : parts) {
            for (final Map<String, List<String>> record : part.records()) {
                records++;
                if (matched(part, unmatched, record) != null) {
                    matched++;
                }
            }
        }
        return new Key(contextFields, eventFields, matched, records);
    }

    private static String keyOf(final Map<String, List<String>> record, final List<String> fields) {
        final List<String> values = new ArrayList<>();
        for (final String field : fields) {
            final List<String> found = record.get(field);
            if (found == null || found.isEmpty()) {
                return null;
            }
            values.add(found.getFirst());
        }
        return LookupKeys.key(values);
    }

    private static String keyOf(final Entry entry, final List<String> fields) {
        final List<String> values = new ArrayList<>();
        for (final String field : fields) {
            final String found = entry.fields().get(field);
            if (found == null) {
                return null;
            }
            values.add(found);
        }
        return LookupKeys.key(values);
    }

    private static List<List<Pair>> combinations(final List<Pair> pairs, final int size) {
        final List<List<Pair>> out = new ArrayList<>();
        combine(pairs, size, 0, new ArrayList<>(), out);
        return out;
    }

    private static void combine(final List<Pair> pairs,
                                final int size,
                                final int from,
                                final List<Pair> chosen,
                                final List<List<Pair>> out) {
        if (chosen.size() == size) {
            out.add(List.copyOf(chosen));
            return;
        }
        for (int i = from; i < pairs.size(); i++) {
            chosen.add(pairs.get(i));
            combine(pairs, size, i + 1, chosen, out);
            chosen.removeLast();
        }
    }

    // The root's child element name that comes up most: the entries, when none are named.
    private static String commonChild(final XmlTree.Element root) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        root.elements().forEach(child -> counts.merge(child.localName(), 1, Integer::sum));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    // An entry: its fields that come up once, and its tables (a list element's items, each with fields).
    private static Entry entry(final XmlTree.Element element) {
        final Map<String, List<String>> all = new LinkedHashMap<>();
        final Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
        collect(element, "", all, tables);
        final Map<String, String> fields = new LinkedHashMap<>();
        all.forEach((name, values) -> {
            if (values.size() == 1) {
                fields.put(name, values.getFirst());
            }
        });
        return new Entry(fields, tables);
    }

    private static void collect(final XmlTree.Element element,
                                final String prefix,
                                final Map<String, List<String>> fields,
                                final Map<String, List<Map<String, String>>> tables) {
        element.attrib().forEach((name, value) -> fields.computeIfAbsent(join(prefix, "@" + name),
                ignored -> new ArrayList<>()).add(value));
        final List<XmlTree.Element> children = element.elements();
        final Set<String> names = new HashSet<>();
        children.forEach(child -> names.add(child.localName()));
        // A list: children all of one name, each holding fields of its own and no text.
        final boolean list = names.size() == 1 && !children.isEmpty() && children.stream().allMatch(child ->
                (!child.attrib().isEmpty() || !child.elements().isEmpty()) && child.text().isBlank());
        if (list) {
            final String path = join(prefix, children.getFirst().localName());
            final List<Map<String, String>> items = tables.computeIfAbsent(path, ignored -> new ArrayList<>());
            for (final XmlTree.Element child : children) {
                final Map<String, List<String>> inner = new LinkedHashMap<>();
                flatten(child, "", inner);
                final Map<String, String> item = new LinkedHashMap<>();
                inner.forEach((name, values) -> item.put(name, values.getFirst()));
                items.add(item);
            }
            return;
        }
        for (final XmlTree.Element child : children) {
            final String path = join(prefix, child.localName());
            if (child.isLeaf() && child.attrib().isEmpty()) {
                fields.computeIfAbsent(path, ignored -> new ArrayList<>()).add(child.text().strip());
            } else {
                collect(child, path, fields, tables);
            }
        }
    }

    // Every value an element holds, by its XML path relative to the element, in document order.
    private static void flatten(final XmlTree.Element element,
                                final String prefix,
                                final Map<String, List<String>> out) {
        element.attrib().forEach((name, value) -> {
            if (!name.startsWith("{")) {
                out.computeIfAbsent(join(prefix, "@" + name), ignored -> new ArrayList<>()).add(value);
            }
        });
        for (final XmlTree.Element child : element.elements()) {
            final String path = join(prefix, child.localName());
            if (child.isLeaf()) {
                out.computeIfAbsent(path, ignored -> new ArrayList<>()).add(child.text().strip());
            }
            flatten(child, path, out);
        }
    }

    private static String join(final String prefix, final String step) {
        return prefix.isEmpty()
                ? step
                : prefix + "/" + step;
    }


    // --------------------------------------------------------------------------------


    /// A part's context data.
    ///
    /// @param root       XML: the root element's name; JSON: null.
    /// @param namespace  XML: its namespace, or null.
    /// @param path       XML: the entries' path below the root; JSON: the entries' array's key path (dotted) in a
    ///                   [ContextJson#WRAPPED] object, else empty.
    /// @param entries    The entries.
    /// @param jsonLayout JSON: how the entries are laid out ([ContextJson#ARRAY], [ContextJson#LINES],
    ///                   [ContextJson#WRAPPED] or [ContextJson#OBJECT]); XML: null.
    record Context(String root, String namespace, String path, List<Entry> entries, String jsonLayout) {

        /// @return True if the context data is JSON.
        boolean json() {
            return jsonLayout != null;
        }

        /// @return Where its entries are, as the agent is told.
        String describe() {
            return json()
                    ? ContextJson.describe(this)
                    : root + "/" + path;
        }
    }

    /// One context entry.
    ///
    /// @param fields Its fields that come up once, by XML path relative to it (`@Id`, `Info/Name`), or by JSON's
    ///               dotted key path (`Id`, `Info.Name`).
    /// @param tables Its lists (a list element's items, a JSON array's objects or a JSON object keyed by mask, each
    ///               item its fields by path relative to it), by path.
    record Entry(Map<String, String> fields, Map<String, List<Map<String, String>>> tables) {

    }

    /// One part: its context's entries and its records.
    ///
    /// @param stream  The stream.
    /// @param part    The part's index.
    /// @param entries Its context's entries.
    /// @param records Its records' fields.
    record Part(long stream, int part, List<Entry> entries, List<Map<String, List<String>>> records) {

    }

    /// A key: the entry fields and the record fields matching them, in order, and how many records it picks one
    /// entry out for.
    ///
    /// @param contextFields The entry fields.
    /// @param eventFields   The record fields.
    /// @param matched       The records it picks exactly one entry out for.
    /// @param records       The records.
    record Key(List<String> contextFields, List<String> eventFields, int matched, int records) {

        /// @return The matched share, as a whole percentage.
        int percent() {
            return records == 0
                    ? 0
                    : (int) Math.floor(100.0 * matched / records);
        }

        /// @return It as `{key_fields, event_key_fields, matched, records, match_rate}`.
        Map<String, Object> toMap() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("key_fields", contextFields);
            out.put("event_key_fields", eventFields);
            out.put("matched", matched);
            out.put("records", records);
            out.put("match_rate", percent() + "%");
            return out;
        }
    }

    // An entry field paired with the record field most of whose values are among its values.
    private record Pair(String contextField, String eventField, double share, double coverage) {

        // Whether the record field's name is the entry field's (`System/Version` and `@Version`).
        boolean sameName() {
            return lastStep(contextField).equalsIgnoreCase(lastStep(eventField));
        }

        private static String lastStep(final String path) {
            final String last = path.substring(path.lastIndexOf('/') + 1);
            return last.startsWith("@")
                    ? last.substring(1)
                    : last;
        }
    }
}
