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

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.ai.impl.tools.validation.XsltVersionHistory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// One rebuild of a mapping from an XSLT: the XSLT read, the kept
/// mapping's rules matched to it by their templates, each element kept, read as an idiom, or kept as written, and
/// what every rule writes alike put in common. See [XsltReader#rebuild].
final class MappingRebuild {

    // A rule's spec key, while the rules are read: it logs what it catches and has conditions.
    private static final String CAUGHT_WITH_CONDITIONS = "\u0000caught";
    private static final Pattern EVENT_TYPE = Pattern.compile("^event[_-]type[_-]");
    // What the rebuild reads of loops, said where one isn't read.
    private static final String NOT_A_LOOP_READ = "a group's loop and a repeated Data entry's are read back as the "
                                                  + "generator writes them, not other loops (one written by hand, "
                                                  + "say); carry it into the mapping by hand (a group: for_each, as "
                                                  + "and fields)";
    // A path of XML names and attributes, as a field of an XML input is written.
    private static final Pattern XML_PATH = Pattern.compile("@?[A-Za-z_][A-Za-z0-9_.-]*(/@?[A-Za-z_][A-Za-z0-9_.-]*)*");
    // What makes a regex more than its literal text.
    private static final Pattern REGEX_SPECIAL = Pattern.compile("[\\\\^$.|?*+()\\[\\]{}]");
    // What a repeated entry's value written of the current value may read as: its input, transformed or cut.
    private static final Set<String> EACH_VALUE_KEYS = Set.of("path", "data_name", "data_parent", "field", "any_of",
            "transform", "max_length");
    private static final String XSL = XmlTrees.XSL;
    private static final String EVT = XsltGenerator.EVT;

    private final String code;
    private final TranslationMapping kept;
    private final String keptCode;
    private final Map<String, String> imported;
    private XsltReading reading;
    private RebuiltMapping out;
    // The input: data_splitter, json, xml or xml_fragments.
    private String kind;

    /// @param code     The XSLT as it is.
    /// @param kept     The mapping kept with it before, or null.
    /// @param keptCode What the kept mapping generates now, or null.
    /// @param imported The XSLTs it imports, by href.
    MappingRebuild(final String code,
                   final TranslationMapping kept,
                   final String keptCode,
                   final Map<String, String> imported) {
        this.code = code;
        this.kept = kept;
        this.keptCode = keptCode;
        this.imported = imported;
    }

    /// @return The mapping read back, and how it was come by.
    RebuiltMapping run() {
        reading = XsltReader.read(code);
        reading.keyed().putAll(XsltReader.keyedShapes(code, reading.parser()));
        // Key=value functions are read as extractions, not written into the expression.
        for (final String name : reading.keyed().keySet()) {
            reading.functions().remove("mcp:" + name);
        }
        out = new RebuiltMapping(reading.notes());
        final String records = Objects.requireNonNullElse(reading.records(), "");
        kind = kept != null
                ? kept.input()
                : XsltReader.INPUT_NAMESPACE.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(reading.namespace()))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("xml");
        if ("xml".equals(kind) && records.startsWith("*/")) {
            kind = "xml_fragments";
        }
        final List<Map<String, Object>> extracts = new ArrayList<>();
        if (kept != null) {
            kept.extract().forEach(extraction -> extracts.add(mutable(extraction.toJson(DumpMode.COMPACT))));
        }
        final ReadIdioms idioms = new ReadIdioms(reading, kind, extracts);
        final Map<String, Before> before = new LinkedHashMap<>();
        XPathTreeParser baseParser = reading.parser();
        if (kept != null && XsltReader.truthy(keptCode)) {
            final XsltReading base = XsltReader.read(keptCode);
            baseParser = base.parser();
            final List<Map<List<String>, FieldMapping>> effective = effective(kept);
            for (int n = 0; n < base.rules().size(); n++) {
                if (n < kept.events().size()) {
                    final RuleReading rule = base.rules().get(n);
                    before.put(rule.key(), new Before(rule, kept.events().get(n), effective.get(n)));
                }
            }
        }
        final List<Map<String, Object>> rulesOut = new ArrayList<>();
        final List<List<Written>> perRule = new ArrayList<>();
        for (final RuleReading rule : reading.rules()) {
            final Map<String, Object> spec = new LinkedHashMap<>();
            final List<Written> entries = readRule(rule, before.get(rule.key()), baseParser, idioms, spec);
            rulesOut.add(spec);
            perRule.add(entries);
        }
        lineFormatCatchAlls(rulesOut);
        final Set<String> now = new HashSet<>();
        reading.rules().forEach(rule -> now.add(rule.key()));
        before.forEach((key, old) -> {
            if (!now.contains(key)) {
                out.removed().add("rule " + old.rule().name() + " (no longer in the XSLT)");
            }
        });
        final List<Map<String, Object>> common = common(rulesOut, perRule);
        final Map<String, Object> payload = kept != null
                ? new LinkedHashMap<>(kept.toJson(DumpMode.COMPACT))
                : new LinkedHashMap<>();
        payload.put("input", kind);
        payload.put("common", common);
        payload.put("events", rulesOut);
        payload.put("extract", idioms.extracts());
        if (kept == null) {
            lostMappingFacts(payload, kind, records);
        }
        try {
            out.setMapping(TranslationMapping.parse(payload));
        } catch (final RuntimeException e) {
            out.problems().add("the mapping read back doesn't validate: " + XsltReader.head(e.getMessage(), 400));
        }
        return out;
    }

    // The rules logging what they catch whose conditions are all on one field, the same for each: line formats'
    // catch-alls (each format's variant condition its only condition), read back as marked. Conditions on more than
    // one field, or rules on different fields, are not a line format's: left unmarked, as the generator inferred.
    private static void lineFormatCatchAlls(final List<Map<String, Object>> rules) {
        final Set<String> fields = new HashSet<>();
        boolean lineFormats = true;
        for (final Map<String, Object> rule : rules) {
            if (rule.remove(CAUGHT_WITH_CONDITIONS) == null) {
                continue;
            }
            rule.put(CAUGHT_WITH_CONDITIONS, true);
            final Set<Object> tested = new HashSet<>();
            for (final Object condition : rule.get("when") instanceof final List<?> when
                    ? when
                    : List.of()) {
                tested.add(condition instanceof final Map<?, ?> map && map.get("xpath") == null
                        ? map.get("field")
                        : null);
            }
            lineFormats &= tested.size() == 1 && !tested.contains(null);
            tested.forEach(field -> fields.add(String.valueOf(field)));
        }
        lineFormats &= fields.size() <= 1;
        for (final Map<String, Object> rule : rules) {
            if (rule.remove(CAUGHT_WITH_CONDITIONS) != null && lineFormats) {
                rule.put("catch_all", true);
            }
        }
    }

    // One rule read: its spec (name, conditions, drop, unknown), and its entries, each with what it is known by.
    private List<Written> readRule(final RuleReading rule,
                                   final Before old,
                                   final XPathTreeParser baseParser,
                                   final ReadIdioms idioms,
                                   final Map<String, Object> spec) {
        final String which = "rule " + (XsltReader.truthy(rule.name())
                ? rule.name()
                : rule.key()) + ": ";
        for (final String note : rule.notes()) {
            out.notes().add(which + note);
        }
        rule.lost().forEach(note -> out.lost().add(which + note));
        final String name = ruleName(rule, old);
        spec.put("name", name);
        if (old != null && sameTest(old.reading().test(), rule.test(), baseParser, reading.parser())) {
            spec.put("when", old.rule().when().stream().map(condition -> condition.toJson(DumpMode.WITHOUT_NONE))
                    .toList());
        } else if (XsltReader.truthy(rule.test())) {
            final ReadIdioms.Conditions conditions = idioms.conditions(rule.test());
            spec.put("when", conditions.when());
            if (conditions.raw()) {
                final boolean calls = conditions.when().stream()
                        .anyMatch(condition -> callsImported((String) condition.get("xpath")));
                (calls
                        ? out.importedCalls()
                        : out.raw()).add("rule " + name + ": its condition");
            }
        }
        if (rule.drop()) {
            spec.put("drop", true);
            return List.of();
        }
        if (rule.caught() && !XsltReader.truthy(rule.test())) {
            // The rule for the records no other rule takes, logging them (Caught by): read back as marked.
            spec.put("catch_all", true);
        } else if (rule.caught()) {
            // A line format's (its only conditions on the variant field), marked once every rule is read.
            spec.put(CAUGHT_WITH_CONDITIONS, true);
        }
        if (old != null) {
            if (XsltReader.truthy(old.rule().allowUnknown())) {
                spec.put("allow_unknown", old.rule().allowUnknown());
            }
            if (old.rule().keepUnknown()) {
                spec.put("keep_unknown", true);
            }
        } else if (XsltReader.truthy(rule.unknown())) {
            spec.put("allow_unknown", rule.unknown());
            spec.put("keep_unknown", true);
        }
        final List<Written> entries = new ArrayList<>();
        final Set<Integer> reused = new HashSet<>();
        final Map<List<String>, LeafReading> oldLeaves = new LinkedHashMap<>();
        if (old != null) {
            old.reading().leaves().forEach(leaf -> oldLeaves.put(leaf.key(), leaf));
        }
        for (final LeafReading leaf : rule.leaves()) {
            final LeafReading previous = oldLeaves.get(leaf.key());
            final List<Object> form = leaf.form(reading.parser());
            final FieldMapping same = previous != null && previous.form(baseParser).equals(form)
                    ? keptEntry(old, leaf)
                    : null;
            if (same != null) {
                // A group's dump without its defaults: a group refuses repeat, even false.
                entries.add(new Written(Arrays.asList(leaf.key(), form), same.toJson(same.isGroup()
                        ? DumpMode.COMPACT
                        : DumpMode.WITHOUT_NONE)));
                reused.add(entries.size() - 1);
                out.reusedOne();
                continue;
            }
            if (leaf.group() != null) {
                final String where = "rule " + name + ": " + leaf.path() + (XsltReader.truthy(leaf.dataName())
                        ? " Data " + leaf.key().get(1)
                        : "");
                final Map<String, Object> group = groupEntry(leaf, List.of(), idioms, where);
                if (group != null) {
                    entries.add(new Written(Arrays.asList(leaf.key(), form), group));
                    out.added().add(where);
                }
                continue;
            }
            if (XsltReader.truthy(leaf.repeat()) && leaf.repeatSelect() == null) {
                out.problems().add("rule " + name + ": " + leaf.path() + " repeats (xsl:for-each) in a way no entry "
                                   + "it had writes: " + NOT_A_LOOP_READ);
                continue;
            }
            final ReadIdioms.Entry entry = idioms.entry(repeated(leaf));
            if (leaf.repeatSelect() != null && leaf.expr() != null && !eachValueRead(entry)) {
                out.problems().add("rule " + name + ": " + leaf.path() + " repeats (xsl:for-each), each value "
                                   + "written as " + XsltReader.head(leaf.expr(), 120) + ", in a way no entry it had "
                                   + "writes: " + NOT_A_LOOP_READ);
                continue;
            }
            if (leaf.repeatSelect() != null) {
                entry.json().put("repeat", true);
            }
            entries.add(new Written(Arrays.asList(leaf.key(), form), entry.json()));
            final String where = "rule " + name + ": " + leaf.path() + (XsltReader.truthy(leaf.dataName())
                    ? " Data " + leaf.key().get(1)
                    : "");
            out.added().add(where);
            if (entry.raw()) {
                // An imported XSLT's function is only called through xpath, so that entry is as the mapping had it.
                (callsImported((String) entry.json().get("xpath"))
                        ? out.importedCalls()
                        : out.raw()).add(where);
            }
        }
        if (old == null) {
            return entries;
        }
        final Set<List<String>> written = new HashSet<>();
        rule.leaves().forEach(leaf -> written.add(leaf.key()));
        for (final List<String> key : oldLeaves.keySet()) {
            if (!written.contains(key)) {
                out.removed().add("rule " + name + ": " + key.get(0) + (XsltReader.truthy(key.get(1))
                        ? " Data " + key.get(1)
                        : ""));
            }
        }
        return keptOrder(entries, reused, old);
    }

    // The kept entry writing a leaf: by its key, or, for a repeat's loop (read as a group's, its element the one
    // the repeat writes a value each), the one repeated entry below it. Null if there is none.
    private static FieldMapping keptEntry(final Before old, final LeafReading leaf) {
        final FieldMapping entry = old.effective().get(leaf.key());
        if (entry != null || leaf.group() == null || !leaf.group().byHand()) {
            return entry;
        }
        final List<FieldMapping> below = old.effective().values().stream()
                .filter(each -> each.repeat() && !XsltReader.truthy(each.dataName())
                                && ToolText.strip(each.path(), "/").startsWith(leaf.path() + "/"))
                .toList();
        return below.size() == 1
                ? below.getFirst()
                : null;
    }

    // --- repeated groups ----------------------------------------------------------------------------------------

    // A group's loop read back into its entry: path (and Data name), for_each, as and fields, each field read as an
    // entry is, its reads of an item told apart by putting a sentinel field in the item's place (ReadIdioms reads it
    // as it reads a field of the record). Null, with the problem said, when the loop or a field reads in a way no
    // group entry writes.
    private Map<String, Object> groupEntry(final LeafReading leaf,
                                           final List<ItemFrame> outer,
                                           final ReadIdioms idioms,
                                           final String where) {
        final GroupReading group = leaf.group();
        final ItemFrame frame = new ItemFrame(group.variable(), outer.size(), zipNames(group));
        final Map<String, Object> forEach = itemSource(group, outer, frame, idioms, where);
        if (forEach == null) {
            return null;
        }
        final List<ItemFrame> frames = new ArrayList<>(outer);
        frames.add(frame);
        final List<Map<String, Object>> fields = new ArrayList<>();
        for (final LeafReading child : group.leaves()) {
            final String at = where + " > " + child.path() + (XsltReader.truthy(child.dataName())
                    ? " Data " + child.key().get(1)
                    : "");
            final Map<String, Object> field;
            if (child.group() != null) {
                field = groupEntry(child, frames, idioms, at);
            } else if (XsltReader.truthy(child.repeat())) {
                out.problems().add(at + " repeats (xsl:for-each) inside a group's item in a way no entry writes: "
                                   + NOT_A_LOOP_READ);
                field = null;
            } else {
                field = groupField(child, frames, idioms, at);
            }
            if (field == null) {
                return null;
            }
            fields.add(field);
        }
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", leaf.path());
        if (XsltReader.truthy(leaf.dataName())) {
            entry.put("data_name", leaf.dataName());
            if (leaf.dataParent() != null) {
                entry.put("data_parent", leaf.dataParent());
            }
        }
        entry.put("for_each", forEach);
        if (!"item".equals(group.variable())) {
            entry.put("as", group.variable());
        }
        entry.put("fields", fields);
        return entry;
    }

    // Each zip list's variable ($name) to its name in the zip: the variable without the item's name it starts with
    // (the generator names it after the item and the list).
    private static Map<String, String> zipNames(final GroupReading group) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final String variable : group.lists().keySet()) {
            String name = variable.substring(1);
            if (name.length() > group.variable().length() && name.startsWith(group.variable())) {
                name = name.substring(group.variable().length());
                if (name.startsWith("_") || name.startsWith("-")) {
                    name = name.substring(1);
                } else {
                    name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                }
            }
            out.put(variable, name);
        }
        return out;
    }

    // A group's for_each, from what its loop selects: a split's pieces, a zip's lists, or an array's members.
    private Map<String, Object> itemSource(final GroupReading group,
                                           final List<ItemFrame> outer,
                                           final ItemFrame frame,
                                           final ReadIdioms idioms,
                                           final String where) {
        final Map<String, Object> source = new LinkedHashMap<>();
        final List<SourceField> read = new ArrayList<>();
        if (!frame.lists().isEmpty()) {
            final Map<String, String> zip = new LinkedHashMap<>();
            final Set<String> separators = new HashSet<>();
            for (final Map.Entry<String, String> list : group.lists().entrySet()) {
                final XPathNode node = XsltReader.parseOrNull(list.getValue(), reading.parser());
                final XPathMatch pieces = unify("pieces", node);
                final SourceField field = pieces != null
                        ? sourceField(pieces.expr(1), outer, false)
                        : sourceField(members(node), outer, false);
                separators.add(pieces != null
                        ? pieces.string(1)
                        : "");
                if (field == null) {
                    return notASource(where, list.getValue());
                }
                read.add(field);
                zip.put(frame.lists().get(list.getKey()), field.name());
            }
            if (separators.size() != 1) {
                out.problems().add(where + ": the lists of its zip aren't split alike (some on a separator, some "
                                   + "not, or on different ones), as no zip's lists are; not read");
                return null;
            }
            source.put(ItemSource.ZIP, zip);
            final String on = separators.iterator().next();
            if (!on.isEmpty()) {
                source.put("on", on);
            }
        } else if (group.byHand()) {
            final XPathNode node = XsltReader.parseOrNull(group.items(), reading.parser());
            final String on = tokenized(node);
            final SourceField field = on != null
                    ? sourceField(XPathTrees.unbracketed(XPathTrees.normal(node)).get(0), outer, false)
                    : sourceField(handMembers(node), outer, false);
            if (field == null) {
                out.problems().add(where + ": a loop written by hand, one element per value, over "
                                   + XsltReader.head(group.items(), 120) + ": not a field's values or "
                                   + "tokenize(field, 'separator'), which a group's for_each reads; not read");
                return null;
            }
            read.add(field);
            source.put(on != null
                    ? ItemSource.SPLIT
                    : ItemSource.ARRAY, field.name());
            if (on != null) {
                source.put("on", on);
            }
            out.notes().add(where + ": a loop written by hand, one element per value, read as a for_each group on "
                            + "that element, the value its item" + (on != null
                    ? " (a split's pieces are trimmed, and empty ones dropped)"
                    : ""));
        } else {
            final XPathNode node = XsltReader.parseOrNull(group.items(), reading.parser());
            final XPathMatch pieces = unify("pieces", node);
            final SourceField field = pieces != null
                    ? sourceField(pieces.expr(1), outer, false)
                    : sourceField(members(node), outer, true);
            if (field == null) {
                return notASource(where, group.items());
            }
            read.add(field);
            source.put(pieces != null
                    ? ItemSource.SPLIT
                    : ItemSource.ARRAY, field.name());
            if (pieces != null) {
                source.put("on", pieces.string(1));
            }
        }
        final Set<Integer> from = new HashSet<>();
        read.forEach(field -> from.add(field.frame()));
        if (from.size() != 1) {
            out.problems().add(where + ": its zip's lists are read from different items (or the record), as no "
                               + "zip's are; not read");
            return null;
        }
        final int at = from.iterator().next();
        if (at < 0 && !outer.isEmpty()) {
            source.put("scope", "record");
        } else if (at >= 0 && at < outer.size() - 1) {
            source.put("item", outer.get(at).variable());
        }
        return source;
    }

    // A loop's items written by hand as a field's values: a JSON array's members (its values, `field/*`, or as the
    // generator selects them), else the input as it is.
    private XPathNode handMembers(final XPathNode node) {
        final XPathNode normal = node == null
                ? null
                : XPathTrees.normal(node);
        if (normal != null && "json".equals(kind) && "/".equals(normal.symbol()) && normal.size() == 2
            && "*".equals(normal.get(1).symbol()) && normal.get(1).size() == 0) {
            return normal.get(0);
        }
        return members(node);
    }

    // A loop's items written by hand as tokenize(field, 'separator'): the separator, a literal (a regex of no special
    // characters, or the q flag); null for any other.
    private static String tokenized(final XPathNode node) {
        final XPathNode top = node == null
                ? null
                : XPathTrees.unbracketed(XPathTrees.normal(node));
        if (top == null || !"call".equals(top.symbol()) || !"tokenize".equals(top.valueText()) || top.size() < 2
            || top.size() > 3 || !"(string)".equals(top.get(1).symbol())) {
            return null;
        }
        final String on = top.get(1).valueText();
        final boolean literal = top.size() == 3
                ? "(string)".equals(top.get(2).symbol()) && "q".equals(top.get(2).valueText())
                : !REGEX_SPECIAL.matcher(on).find();
        return literal && !on.isEmpty()
                ? on
                : null;
    }

    private Map<String, Object> notASource(final String where, final String items) {
        out.problems().add(where + ": its loop's items (" + XsltReader.head(items, 120) + ") aren't a split, an array "
                           + "or a zip as the generator writes a group's; not read");
        return null;
    }

    // An array's (or an unsplit zip list's) input: a JSON array's members' input, else the input as it is.
    private XPathNode members(final XPathNode node) {
        final XPathMatch json = "json".equals(kind)
                ? unify("json members", node)
                : null;
        return json != null
                ? json.expr(1)
                : node;
    }

    private static XPathMatch unify(final String shape, final XPathNode node) {
        return node == null
                ? null
                : XPathTrees.unify(ReadShapes.SHAPES.get(shape), node).orElse(null);
    }

    // The field a group's items are read from, and where (an enclosing item's depth, or -1 for the record); null if
    // it isn't a field. Elements: a Data Splitter array's data elements, read without their values.
    private SourceField sourceField(final XPathNode node,
                                    final List<ItemFrame> frames,
                                    final boolean elements) {
        if (node == null) {
            return null;
        }
        String text = substitute(XPathTrees.normal(node).text(), frames);
        if (elements && "data_splitter".equals(kind)) {
            text = text + "/@value";
        }
        final String field;
        if (isXml()) {
            field = XML_PATH.matcher(text).matches()
                    ? text
                    : null;
        } else {
            final XPathNode parsed = XsltReader.parseOrNull(text, reading.parser());
            field = parsed == null
                    ? null
                    : new ReadIdioms(reading, kind, new ArrayList<>()).fieldOf(parsed);
        }
        if (!XsltReader.truthy(field)) {
            return null;
        }
        final ItemRef ref = decode(field, frames);
        return ref.key() == null
                ? null
                : new SourceField(ref.key(), ref.frame());
    }

    // A field of a group: read as an entry is, the item's place held by its sentinel, then the sentinel read back as
    // the item (item: true, a key of it, an outer group's item by its name) or the record (scope: record).
    private Map<String, Object> groupField(final LeafReading leaf,
                                           final List<ItemFrame> frames,
                                           final ReadIdioms idioms,
                                           final String at) {
        if (leaf.value() != null) {
            return idioms.entry(leaf).json();
        }
        final Map<String, Object> xpath = itemXpath(leaf, frames);
        if (xpath != null) {
            return xpath;
        }
        final StringBuilder path = new StringBuilder();
        if (leaf.dataParent() != null) {
            for (final String parent : leaf.dataParent().split("/", -1)) {
                path.append(LeafReading.CONTAINER).append(parent).append('/');
            }
        }
        final LeafReading placed = new LeafReading(path + leaf.path(), leaf.dataName());
        placed.setExpr(substitute(Objects.requireNonNullElse(leaf.expr(), ""), frames));
        if (leaf.guard() != null) {
            placed.setGuard(substitute(leaf.guard(), frames));
        }
        final int extracts = idioms.extracts().size();
        final ReadIdioms.Entry entry = idioms.entry(placed);
        final boolean extracted = idioms.extracts().subList(extracts, idioms.extracts().size()).stream()
                .anyMatch(extraction -> String.valueOf(extraction).contains(ReadIdioms.ITEM_SENTINEL));
        if (extracted) {
            idioms.extracts().subList(extracts, idioms.extracts().size()).clear();
        }
        final Map<String, Object> json = new LinkedHashMap<>(entry.json());
        final String problem = extracted
                ? "extracts from its item, which no group field does"
                : placeItem(json, frames);
        if (problem != null) {
            out.problems().add(at + ": " + problem + "; not read");
            return null;
        }
        if (entry.raw()) {
            (callsImported((String) json.get("xpath"))
                    ? out.importedCalls()
                    : out.raw()).add(at);
        }
        return json;
    }

    // A group's xpath entry, as the generator writes one: `$item ! (xpath)`, the item its context. Null for anything
    // else.
    private Map<String, Object> itemXpath(final LeafReading leaf, final List<ItemFrame> frames) {
        final XPathNode tree = XsltReader.parseOrNull(leaf.expr(), reading.parser());
        if (tree == null) {
            return null;
        }
        final XPathNode node = XPathTrees.firstOf(tree).orElse(XPathTrees.normal(tree));
        if (!"!".equals(node.symbol()) || node.size() != 2 || !"$".equals(node.get(0).symbol())) {
            return null;
        }
        final int frame = frameOf(node.get(0).valueText(), frames);
        final XPathNode inner = XPathTrees.unbracketed(node.get(1));
        if (frame < 0 || XsltReader.variables(inner).stream()
                .anyMatch(variable -> frameOf(variable.valueText(), frames) >= 0 || listOf(variable, frames) >= 0)) {
            return null;
        }
        final Map<String, Object> json = new LinkedHashMap<>();
        json.put("path", leaf.path());
        if (XsltReader.truthy(leaf.dataName())) {
            json.put("data_name", leaf.dataName());
            if (leaf.dataParent() != null) {
                json.put("data_parent", leaf.dataParent());
            }
        }
        json.put("xpath", inner.text());
        if (frame < frames.size() - 1) {
            json.put("item", frames.get(frame).variable());
        }
        return json;
    }

    // An entry's reads of the sentinels put back as the item it reads: a key of it (field), the item itself (no
    // field), from its own group's item or an outer one (item); reads of the record alone are scope: record. The
    // problem, if it reads in a way no group field writes; else null.
    @SuppressWarnings("unchecked")
    private String placeItem(final Map<String, Object> json, final List<ItemFrame> frames) {
        final List<ItemRef> refs = new ArrayList<>();
        if (json.get("field") instanceof final String field) {
            refs.add(decode(field, frames));
        }
        if (json.get("any_of") instanceof final List<?> anyOf) {
            anyOf.forEach(field -> refs.add(decode(String.valueOf(field), frames)));
        }
        final Map<String, Object> lookup = json.get("lookup") instanceof final Map<?, ?> given
                ? new LinkedHashMap<>((Map<String, Object>) given)
                : null;
        if (lookup != null && lookup.get("field") instanceof final String field) {
            refs.add(decode(field, frames));
        }
        final Set<Integer> from = new HashSet<>();
        refs.forEach(ref -> from.add(ref.frame()));
        if (from.size() > 1) {
            return "reads more than one item (or an item and the record) in one value, as no group field does";
        }
        final boolean self = refs.stream().anyMatch(ref -> ref.key() == null);
        if (self && (refs.size() > 1 || lookup != null)) {
            return "reads its item whole with another input, as no group field does";
        }
        if (json.get("field") instanceof String) {
            if (self) {
                json.remove("field");
            } else {
                json.put("field", refs.getFirst().key());
            }
        }
        if (json.get("any_of") instanceof List<?>) {
            json.put("any_of", refs.stream().map(ItemRef::key).toList());
        }
        if (lookup != null && lookup.get("field") instanceof String) {
            lookup.put("field", refs.getLast().key());
            json.put("lookup", lookup);
        }
        final int frame = from.isEmpty()
                ? -1
                : from.iterator().next();
        if (frame < 0) {
            if (!refs.isEmpty() || json.containsKey("xpath")) {
                json.put("scope", "record");
            }
        } else if (frame < frames.size() - 1) {
            json.put("item", frames.get(frame).variable());
        } else if (self) {
            json.put("item", true);
        }
        return json.toString().contains(ReadIdioms.ITEM_SENTINEL)
                ? "reads its item in an expression no group field writes"
                : null;
    }

    // A field read back: the item it is of (its depth, -1 for the record) and its key (null for the item itself).
    private ItemRef decode(final String field, final List<ItemFrame> frames) {
        final String separator = isXml()
                ? "/"
                : ReadShapes.SEPARATOR.get(kind);
        for (final ItemFrame frame : frames) {
            final String sentinel = frame.sentinel();
            if (field.equals(sentinel)) {
                return new ItemRef(frame.depth(), null);
            }
            if (field.startsWith(sentinel + separator)) {
                return new ItemRef(frame.depth(), field.substring(sentinel.length() + separator.length()));
            }
        }
        return new ItemRef(-1, field);
    }

    // The expression with each read of a group's item put as a read of the item's sentinel field: $item/key as
    // the sentinel's key, $item alone as the sentinel, a zip list's $list[$item] as the sentinel's key of that name.
    private String substitute(final String expr, final List<ItemFrame> frames) {
        final XPathNode tree = XsltReader.parseOrNull(expr, reading.parser());
        if (tree == null || frames.isEmpty()) {
            return expr;
        }
        final Set<XPathNode> free = Collections.newSetFromMap(new IdentityHashMap<>());
        free.addAll(XsltReader.variables(tree));
        final List<XPathTrees.Replacement> found = new ArrayList<>();
        for (final XPathNode node : tree.walk()) {
            if ("[".equals(node.symbol()) && node.size() == 2 && free.contains(node.get(0))
                && free.contains(node.get(1))) {
                final int list = listOf(node.get(0), frames);
                if (list >= 0 && frameOf(node.get(1).valueText(), frames) == list) {
                    final ItemFrame frame = frames.get(list);
                    found.add(new XPathTrees.Replacement(node, keyText(frame, frame.lists().get("$"
                                                                                              + node.get(0)
                            .valueText()))));
                    free.remove(node.get(0));
                    free.remove(node.get(1));
                }
            } else if ("/".equals(node.symbol()) && node.size() == 2 && free.contains(node.get(0))) {
                final int frame = frameOf(node.get(0).valueText(), frames);
                if (frame >= 0) {
                    found.add(new XPathTrees.Replacement(node.get(0), containerText(frames.get(frame))));
                    free.remove(node.get(0));
                }
            }
        }
        for (final XPathNode variable : free) {
            final int frame = frameOf(variable.valueText(), frames);
            if (frame >= 0) {
                found.add(new XPathTrees.Replacement(variable, XPathText.fieldText(kind, frames.get(frame)
                        .sentinel())));
            }
        }
        return XPathTrees.splice(tree.text(), found);
    }

    // The innermost group whose item is this variable, or -1.
    private static int frameOf(final String variable, final List<ItemFrame> frames) {
        for (int i = frames.size() - 1; i >= 0; i--) {
            if (frames.get(i).variable().equals(variable)) {
                return i;
            }
        }
        return -1;
    }

    // The innermost group one of whose zip lists is this variable, or -1.
    private static int listOf(final XPathNode variable, final List<ItemFrame> frames) {
        for (int i = frames.size() - 1; i >= 0; i--) {
            if (frames.get(i).lists().containsKey("$" + variable.valueText())) {
                return i;
            }
        }
        return -1;
    }

    // The sentinel as the element its keys are below: a JSON key's, a Data Splitter data element's, an XML element.
    private String containerText(final ItemFrame frame) {
        return switch (kind) {
            case "json" -> "*[@key=" + XPathText.literal(frame.sentinel()) + "]";
            case "data_splitter" -> "data[@name=" + XPathText.literal(frame.sentinel()) + "]";
            default -> frame.sentinel();
        };
    }

    // A key of the sentinel, as a field of the record is read.
    private String keyText(final ItemFrame frame, final String key) {
        return isXml()
                ? frame.sentinel() + "/" + key
                : XPathText.fieldText(kind, frame.sentinel() + ReadShapes.SEPARATOR.get(kind) + key);
    }

    private boolean isXml() {
        return !ReadShapes.SEPARATOR.containsKey(kind);
    }

    // A Data entry the generator repeats, read as an entry over what its loop selects (the entry's input), each value
    // as the loop writes it (transformed or cut, its first value, `.[1]`, put as the input's); any other reading as
    // it is.
    private LeafReading repeated(final LeafReading leaf) {
        if (leaf.repeatSelect() == null) {
            return leaf;
        }
        final String parent = leaf.dataParent();
        final LeafReading over = new LeafReading(parent == null
                ? leaf.path()
                : withContainers(leaf.path(), parent), leaf.dataName());
        over.setExpr(leaf.expr() == null
                ? leaf.repeatSelect()
                : overInput(leaf.expr(), leaf.repeatSelect()));
        return over;
    }

    // A repeated entry's value written of the current value (`.[1]`, as scalar(entry, ".") writes it), put as of
    // the loop's input: each `.[1]` its first value. As it is when it has none.
    private String overInput(final String each, final String select) {
        final XPathNode tree = XsltReader.parseOrNull(each, reading.parser());
        if (tree == null) {
            return each;
        }
        final List<XPathTrees.Replacement> found = new ArrayList<>();
        for (final XPathNode node : tree.walk()) {
            if ("[".equals(node.symbol()) && node.size() == 2 && ".".equals(node.get(0).symbol())
                && XPathTrees.firstOf(node).isPresent()) {
                found.add(new XPathTrees.Replacement(node.get(0), "(" + select + ")"));
            }
        }
        return found.isEmpty()
                ? each
                : XPathTrees.splice(tree.text(), found);
    }

    // Whether a repeated entry's value written of the current value reads as an entry that writes it so: its input
    // transformed or cut, nothing else.
    private static boolean eachValueRead(final ReadIdioms.Entry entry) {
        return !entry.raw() && (entry.json().containsKey("field") || entry.json().containsKey("any_of"))
               && EACH_VALUE_KEYS.containsAll(entry.json().keySet());
    }

    // A Data entry's path with a container step before its last for each Data it is in, as XsltReader reads it.
    private static String withContainers(final String path, final String parents) {
        final int last = path.lastIndexOf('/');
        final StringBuilder out = new StringBuilder(path.substring(0, last));
        for (final String parent : parents.split("/", -1)) {
            out.append('/').append(LeafReading.CONTAINER).append(parent);
        }
        return out.append(path.substring(last)).toString();
    }

    // An entry's Data name with the Data it is in, as FieldMapping.dataKey() gives it.
    private static String dataKey(final Map<String, Object> entry) {
        final String name = (String) entry.get("data_name");
        final Object parent = entry.get("data_parent");
        return name == null || parent == null
                ? name
                : parent + "/" + name;
    }

    // The kept mapping's order (a rule's Data are written in it), each new entry just after the one it follows in
    // the XSLT: where a hand edit put it.
    private static List<Written> keptOrder(final List<Written> entries, final Set<Integer> reused, final Before old) {
        final Map<List<String>, Integer> order = new LinkedHashMap<>();
        for (final List<String> key : old.effective().keySet()) {
            order.put(key, order.size());
        }
        double at = -1.0;
        final List<double[]> places = new ArrayList<>();
        for (int n = 0; n < entries.size(); n++) {
            final Object key = entries.get(n).key().getFirst();
            if (reused.contains(n) && order.containsKey(key)) {
                at = order.get(key);
            } else {
                at += 1e-3;
            }
            places.add(new double[]{at, n});
        }
        places.sort(Comparator.comparingDouble(place -> place[0]));
        return places.stream().map(place -> entries.get((int) place[1])).toList();
    }

    private static String ruleName(final RuleReading rule, final Before old) {
        if (old != null && XsltReader.truthy(old.rule().name())) {
            return old.rule().name();
        }
        if (XsltReader.truthy(rule.name())) {
            return rule.name();
        }
        return EVENT_TYPE.matcher(rule.key()).replaceFirst("").replace('_', '-');
    }

    // Common: what every rule writing an event writes the same way, as the kept mapping had it where it can.
    private List<Map<String, Object>> common(final List<Map<String, Object>> rulesOut,
                                             final List<List<Written>> perRule) {
        final List<List<Written>> written = new ArrayList<>();
        for (int i = 0; i < rulesOut.size(); i++) {
            if (!Boolean.TRUE.equals(rulesOut.get(i).get("drop"))) {
                written.add(perRule.get(i));
            }
        }
        final List<List<Object>> commonKeys = new ArrayList<>();
        // One rule alone: its entries stay its own.
        if (!written.isEmpty() && (kept != null || written.size() > 1)) {
            final Set<List<Object>> shared = new LinkedHashSet<>();
            written.getFirst().forEach(entry -> shared.add(entry.key()));
            for (final List<Written> entries : written.subList(1, written.size())) {
                final Set<List<Object>> these = new HashSet<>();
                entries.forEach(entry -> these.add(entry.key()));
                shared.retainAll(these);
            }
            final Set<List<String>> keptCommon = kept == null
                    ? null
                    : commonKeysOf(kept);
            for (final Written entry : written.getFirst()) {
                final List<String> leafKey = entry.leafKey();
                if (shared.contains(entry.key()) && !(XsltReader.truthy(leafKey.get(1)) && keptCommon == null)
                    && (keptCommon == null || keptCommon.contains(leafKey))) {
                    commonKeys.add(entry.key());
                }
            }
        }
        final List<Map<String, Object>> common = new ArrayList<>();
        if (!written.isEmpty()) {
            for (final Written entry : written.getFirst()) {
                if (commonKeys.contains(entry.key())) {
                    common.add(entry.json());
                }
            }
        }
        if (kept != null) {
            final Map<List<String>, Integer> position = new LinkedHashMap<>();
            for (final FieldMapping entry : kept.common()) {
                position.put(Arrays.asList(ToolText.strip(entry.path(), "/"), entry.dataKey()), position.size());
            }
            common.sort(Comparator.comparingInt(entry -> position.getOrDefault(Arrays.asList(
                    ToolText.strip((String) entry.get("path"), "/"), dataKey(entry)), position.size())));
        }
        for (int i = 0; i < rulesOut.size(); i++) {
            if (!Boolean.TRUE.equals(rulesOut.get(i).get("drop"))) {
                rulesOut.get(i).put("fields", perRule.get(i).stream()
                        .filter(entry -> !commonKeys.contains(entry.key()))
                        .map(Written::json)
                        .toList());
            }
        }
        return common;
    }

    private static Set<List<String>> commonKeysOf(final TranslationMapping mapping) {
        final Set<List<String>> out = new HashSet<>();
        mapping.common().forEach(entry -> out.add(Arrays.asList(ToolText.strip(entry.path(), "/"), entry.dataKey())));
        return out;
    }

    // What a lost mapping takes from the XSLT itself: its imports, nil values, unmatched records, records and root.
    private void lostMappingFacts(final Map<String, Object> payload, final String kind, final String records) {
        if (!reading.imports().isEmpty() || !reading.prefixes().isEmpty()) {
            final Imported found = imported();
            if (!found.shared().isEmpty()) {
                payload.put("shared", found.shared());
            }
            if (!found.functions().isEmpty()) {
                payload.put("functions", found.functions());
            }
            out.problems().addAll(found.problems());
        }
        if (!reading.nilValues().isEmpty()) {
            payload.put("nil_values", reading.nilValues());
        }
        payload.put("unmatched", reading.unmatchedWarn()
                ? "warn"
                : "skip");
        // Turned off in the XSLT (log_unmapped_fields false()), kept off.
        if ("false()".equals(ToolText.strip(String.valueOf(reading.globals().get(XsltGenerator.LOG_UNMAPPED))))) {
            payload.put("log_unmapped_fields", false);
        }
        if ("json".equals(kind)) {
            final String layout = XsltGenerator.JSON_RECORDS.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(records))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (layout != null) {
                payload.put("json_layout", layout);
            } else {
                payload.put("record", reading.records());
            }
        } else if (!"data_splitter".equals(kind) || !"record".equals(reading.records())) {
            payload.put("record", reading.records());
        }
        if (!"/".equals(reading.root()) && !"records".equals(reading.root())) {
            payload.put("root", reading.root());
        }
    }

    // The mapping's shared and functions entries, from the XSLT's imports and the imported XSLTs themselves (by
    // name, as Stroom resolves them): which defines each named template called, and the element it writes; which
    // declares each bound prefix's namespace.
    private Imported imported() {
        final List<String> problems = new ArrayList<>();
        final Map<String, XmlElement> parsed = new LinkedHashMap<>();
        for (final String href : reading.imports()) {
            final String text = imported.get(href);
            if (text == null) {
                continue;
            }
            try {
                parsed.put(href, XmlParser.parse(XsltVersionHistory.strip(text)));
            } catch (final XmlParser.XmlParseException e) {
                // Unreadable: as if it weren't there.
            }
        }
        final List<Map<String, Object>> shared = new ArrayList<>();
        final Set<List<String>> seen = new HashSet<>();
        for (final RuleReading rule : reading.rules()) {
            for (final SharedCall call : rule.calls()) {
                if (!seen.add(Arrays.asList(call.template(), call.path()))) {
                    continue;
                }
                final Home home = homeOf(call.template(), parsed);
                final XmlElement first = home.template() == null
                        ? null
                        : home.template().iterElements().stream()
                                .filter(element -> EVT.equals(element.namespace()))
                                .findFirst()
                                .orElse(null);
                if (!XsltReader.truthy(home.href()) || first == null) {
                    problems.add("template " + call.template() + ", called at " + (XsltReader.truthy(call.path())
                            ? call.path()
                            : "Event") + ": the XSLT it is imported from couldn't be read, so the element it writes "
                                 + "isn't known");
                    continue;
                }
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("href", home.href());
                entry.put("template", call.template());
                entry.put("at", String.join("/", List.of(call.path(), first.localName()).stream()
                        .filter(XsltReader::truthy)
                        .toList()));
                if (!call.params().isEmpty()) {
                    entry.put("with_params", call.params());
                }
                shared.add(entry);
            }
        }
        final List<Map<String, Object>> functions = new ArrayList<>();
        reading.prefixes().forEach((prefix, namespace) -> {
            String href = parsed.entrySet().stream()
                    .filter(entry -> entry.getValue().namespaces().containsValue(namespace))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (!XsltReader.truthy(href)) {
                href = reading.imports().size() == 1
                        ? reading.imports().getFirst()
                        : null;
            }
            if (XsltReader.truthy(href)) {
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("href", href);
                entry.put("prefix", prefix);
                entry.put("namespace", namespace);
                functions.add(entry);
            } else {
                problems.add("prefix " + prefix + " (" + namespace + "): no imported XSLT read declares it");
            }
        });
        return new Imported(shared, functions, problems);
    }

    private Home homeOf(final String template, final Map<String, XmlElement> parsed) {
        for (final Map.Entry<String, XmlElement> entry : parsed.entrySet()) {
            final XmlElement found = entry.getValue().childElements(XSL, "template").stream()
                    .filter(each -> Objects.equals(each.get("name"), template))
                    .findFirst()
                    .orElse(null);
            if (found != null) {
                return new Home(entry.getKey(), found);
            }
        }
        return reading.imports().size() == 1
                ? new Home(reading.imports().getFirst(), null)
                : new Home(null, null);
    }

    // Whether an expression calls a function of an XSLT it imports (one of the prefixes bound for them).
    private boolean callsImported(final String xpath) {
        final XPathNode tree = XsltReader.parseOrNull(xpath, reading.parser());
        return tree != null && tree.walk().stream().anyMatch(node -> {
            final String value = node.valueText();
            return "call".equals(node.symbol()) && value.contains(":")
                   && reading.prefixes().containsKey(value.substring(0, value.indexOf(':')));
        });
    }

    private static boolean sameTest(final String a,
                                    final String b,
                                    final XPathTreeParser parserA,
                                    final XPathTreeParser parserB) {
        return (a == null && b == null)
               || (a != null && b != null && XsltReader.keyOf(a, parserA).equals(XsltReader.keyOf(b, parserB)));
    }

    // Per rule, the entries it writes: the common ones, then its own over them.
    private static List<Map<List<String>, FieldMapping>> effective(final TranslationMapping mapping) {
        final List<Map<List<String>, FieldMapping>> out = new ArrayList<>();
        for (final EventRule rule : mapping.events()) {
            final Map<List<String>, FieldMapping> fields = new LinkedHashMap<>();
            for (final FieldMapping entry : mapping.common()) {
                fields.put(Arrays.asList(ToolText.strip(entry.path(), "/"), entry.dataKey()), entry);
            }
            for (final FieldMapping entry : rule.fields()) {
                fields.put(Arrays.asList(ToolText.strip(entry.path(), "/"), entry.dataKey()), entry);
            }
            out.add(fields);
        }
        return out;
    }

    // A dump with its lists made mutable: an extraction's names are added to as its groups are read.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutable(final Map<String, Object> json) {
        final Map<String, Object> out = new LinkedHashMap<>();
        json.forEach((key, value) -> out.put(key, value instanceof final List<?> list
                ? new ArrayList<>((List<Object>) list)
                : value));
        return out;
    }


    // --------------------------------------------------------------------------------


    /// A group being read back: its item's variable, its depth (0 outermost), and a zip's lists' variables ($name) to
    /// their names in it.
    private record ItemFrame(String variable, int depth, Map<String, String> lists) {

        // What stands in for the item while its fields are read.
        private String sentinel() {
            return ReadIdioms.ITEM_SENTINEL + depth;
        }
    }


    // --------------------------------------------------------------------------------


    /// A field of an item or the record: the item's depth (-1 for the record), and the key (null for the item itself).
    private record ItemRef(int frame, String key) {

    }


    // --------------------------------------------------------------------------------


    /// The field a group's items are read from, and the item it is of (-1 for the record).
    private record SourceField(String name, int frame) {

    }


    // --------------------------------------------------------------------------------


    /// A rule of the kept mapping: as its XSLT reads, the rule, and the entries it writes.
    private record Before(RuleReading reading, EventRule rule, Map<List<String>, FieldMapping> effective) {

    }


    // --------------------------------------------------------------------------------


    /// An entry a rule writes: what it is known by (its leaf's key and form), and its JSON.
    private record Written(List<Object> key, Map<String, Object> json) {

        // The leaf's key: its path and Data name.
        @SuppressWarnings("unchecked")
        private List<String> leafKey() {
            return (List<String>) key.getFirst();
        }
    }


    // --------------------------------------------------------------------------------


    private record Imported(List<Map<String, Object>> shared,
                            List<Map<String, Object>> functions,
                            List<String> problems) {

    }


    // --------------------------------------------------------------------------------


    private record Home(String href, XmlElement template) {

    }
}
