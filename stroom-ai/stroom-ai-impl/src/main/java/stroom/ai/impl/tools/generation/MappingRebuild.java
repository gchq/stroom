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

import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.ai.impl.tools.validation.XsltVersionHistory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// One rebuild of a mapping from an XSLT (the Stroom MCP server's `xsltread.rebuild`): the XSLT read, the kept
/// mapping's rules matched to it by their templates, each element kept, read as an idiom, or kept as written, and
/// what every rule writes alike put in common. See [XsltReader#rebuild].
final class MappingRebuild {

    private static final Pattern EVENT_TYPE = Pattern.compile("^event[_-]type[_-]");
    private static final String XSL = XmlTrees.XSL;
    private static final String EVT = XsltGenerator.EVT;

    private final String code;
    private final TranslationMapping kept;
    private final String keptCode;
    private final Map<String, String> imported;
    private XsltReading reading;
    private RebuiltMapping out;

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
        String kind = kept != null
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

    // One rule read: its spec (name, conditions, drop, unknown), and its entries, each with what it is known by.
    private List<Written> readRule(final RuleReading rule,
                                   final Before old,
                                   final XPathTreeParser baseParser,
                                   final ReadIdioms idioms,
                                   final Map<String, Object> spec) {
        for (final String note : rule.notes()) {
            out.notes().add("rule " + (XsltReader.truthy(rule.name())
                    ? rule.name()
                    : rule.key()) + ": " + note);
        }
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
            if (previous != null && previous.form(baseParser).equals(form) && old.effective().containsKey(leaf.key())) {
                entries.add(new Written(Arrays.asList(leaf.key(), form),
                        old.effective().get(leaf.key()).toJson(DumpMode.WITHOUT_NONE)));
                reused.add(entries.size() - 1);
                out.reusedOne();
                continue;
            }
            if (XsltReader.truthy(leaf.repeat()) && leaf.repeatSelect() == null) {
                out.problems().add("rule " + name + ": " + leaf.path() + " repeats (xsl:for-each) in a way no entry "
                                   + "it had writes");
                continue;
            }
            final ReadIdioms.Entry entry = idioms.entry(repeated(leaf));
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

    // A Data entry the generator repeats, read as an entry over what its loop selects (the entry's input); any other
    // reading as it is.
    private static LeafReading repeated(final LeafReading leaf) {
        if (leaf.repeatSelect() == null) {
            return leaf;
        }
        final String parent = leaf.dataParent();
        final LeafReading over = new LeafReading(parent == null
                ? leaf.path()
                : withContainers(leaf.path(), parent), leaf.dataName());
        over.setExpr(leaf.repeatSelect());
        return over;
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
                position.put(Arrays.asList(PyText.strip(entry.path(), "/"), entry.dataKey()), position.size());
            }
            common.sort(Comparator.comparingInt(entry -> position.getOrDefault(Arrays.asList(
                    PyText.strip((String) entry.get("path"), "/"), dataKey(entry)), position.size())));
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
        mapping.common().forEach(entry -> out.add(Arrays.asList(PyText.strip(entry.path(), "/"), entry.dataKey())));
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
                fields.put(Arrays.asList(PyText.strip(entry.path(), "/"), entry.dataKey()), entry);
            }
            for (final FieldMapping entry : rule.fields()) {
                fields.put(Arrays.asList(PyText.strip(entry.path(), "/"), entry.dataKey()), entry);
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
