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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.DropRule;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.Extraction;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.Placeholders;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.TimePatterns;
import stroom.ai.impl.tools.sample.XmlTree;
import stroom.ai.impl.tools.validation.CloseMatches;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.workspace.SafePattern;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/// A translation mapping checked against the sample it is for, before anything is created or stepped (the Stroom
/// MCP server's `utils/localcheck.py`).
///
/// The sample is read locally into records the way the pipeline's parser would (JSON keys with dots, Data Splitter
/// names through the splitter spec's dry run, XML paths by local name), and the mapping's inputs are looked up in
/// them: a field no record has (a typo, or a name from a different variant of the source) and a time_format the
/// sample's values do not fit are the two mistakes that otherwise only show at stepping, one record at a time.
///
/// A record is a map of field name to value (data_splitter and json inputs), or an [XmlTree.Element] (xml
/// inputs). Regular expressions written for the mapping (a condition's `matches`, an extraction's regex) are run
/// with Java's regex engine, as Stroom's XSLT runs them in its own; Python's `re` ran them in the server.
public final class LocalCheck {

    private static final Pattern STEP = PyText.re("^(?:@(?<attr>[\\w.-]+)|(?<name>[\\w.-]+|\\*)(?:\\[@(?<pattr>"
                                                  + "[\\w.-]+)=(?<q>['\"])(?<pval>.*?)\\k<q>\\])?)$");
    private static final Pattern TOKEN = PyText.re("^[A-Za-z][\\w.:-]{0,39}$");
    private static final Pattern WORD = PyText.re("[A-Za-z_][\\w.-]*");
    private static final String NAME = "Name";
    // An element that holds a user's id, e.g. EventSource/User/Id, EventDetail/Authenticate/User/Id: without the
    // schema, by its name.
    private static final Pattern USER_ID = Pattern.compile("(^|/)User/Id$");
    // The schema's type of a user (User, RunAs, Approver, Requestor, an email's From).
    private static final String USER_TYPE = "UserComplexType";
    // Users of that type that are an email's addressees: their Ids are email addresses, the domain part of them.
    private static final Set<String> EMAIL_USERS = Set.of("From", "To", "Cc", "Bcc");
    // DOMAIN\\user or user@domain: one name, then a domain.
    private static final Pattern DOMAIN_QUALIFIED = Pattern.compile(
            "[^\\\\\\s@]+\\\\[^\\\\\\s@]+|[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final int CACHED_PATTERNS = 512;
    private static final Map<String, Optional<Pattern>> PATTERNS = new ConcurrentHashMap<>();

    private LocalCheck() {
        // Static only.
    }

    private static Map<String, Object> dict(final Object... keysAndValues) {
        return JsonValues.dict(keysAndValues);
    }

    /// The text a simple relative path selects in a record, matching by local name: `System/Computer`,
    /// `System/TimeCreated/@SystemTime`, `EventData/Data[@Name='IpAddress']`. Anything beyond this shape
    /// (functions, unions) is not evaluated.
    ///
    /// @param record An XML record.
    /// @param path   The path.
    /// @return The text, or null when it selects nothing.
    public static String xmlValue(final XmlTree.Element record, final String path) {
        List<XmlTree.Element> nodes = List.of(record);
        for (final String segment : segments(path)) {
            if (".".equals(segment)) {
                continue;
            }
            final Matcher m = STEP.matcher(segment);
            if (!m.matches()) {
                return null;
            }
            if (m.group("attr") != null) {
                return nodes.stream()
                        .map(node -> node.get(m.group("attr")))
                        .filter(value -> value != null)
                        .findFirst()
                        .orElse(null);
            }
            nodes = children(nodes, m);
            if (nodes.isEmpty()) {
                return null;
            }
        }
        return PyText.strip(String.join("", nodes.getFirst().itertext()));
    }

    private static List<String> segments(final String path) {
        final List<String> out = new ArrayList<>();
        for (final String segment : PyText.strip(path, "/").split("/", -1)) {
            if (!segment.isEmpty()) {
                out.add(segment);
            }
        }
        return out;
    }

    private static List<XmlTree.Element> children(final List<XmlTree.Element> nodes, final Matcher step) {
        final String name = step.group("name");
        final List<XmlTree.Element> found = new ArrayList<>();
        for (final XmlTree.Element node : nodes) {
            for (final XmlTree.Element child : node.elements()) {
                if ("*".equals(name) || child.localName().equals(name)) {
                    if (step.group("pattr") != null && !step.group("pval").equals(child.get(step.group("pattr")))) {
                        continue;
                    }
                    found.add(child);
                }
            }
        }
        return found;
    }

    /// An XML record as path to value, the paths as [#xmlValue(XmlTree.Element, String)] reads them: each leaf
    /// element's text and each attribute, a Windows EventData Data by its Name
    /// (`EventData/Data[@Name='TargetUserName']`), not the last one standing for all.
    ///
    /// @param record An XML record.
    /// @return Its values by path.
    public static Map<String, String> xmlFlat(final XmlTree.Element record) {
        final Map<String, String> flat = new LinkedHashMap<>();
        for (final XmlTree.Element node : record.iter()) {
            final String here = node == record
                    ? ""
                    : PyText.strip(ancestorPath(node, record) + "/" + node.localName(), "/");
            node.attrib().forEach((attribute, value) -> flat.put((here.isEmpty()
                    ? ""
                    : here + "/") + "@" + attribute, value));
            if (node != record && node.isLeaf() && !PyText.strip(node.text()).isEmpty()) {
                final String named = node.get(NAME);
                flat.put(named != null && !named.isEmpty()
                        ? here + "[@Name='" + named + "']"
                        : here, PyText.strip(node.text()));
            }
        }
        return flat;
    }

    // The local names of a node's ancestors from the top, the record and the element it is in left out.
    private static String ancestorPath(final XmlTree.Element node, final XmlTree.Element record) {
        final List<String> names = new ArrayList<>();
        for (XmlTree.Element at = node.parent(); at != null; at = at.parent()) {
            if (at != record && at != record.parent()) {
                names.addFirst(at.localName());
            }
        }
        return String.join("/", names);
    }

    /// Records with the keys of the JSON their json_fields hold added as `<field>.<key>`, as the XSLT reads them.
    ///
    /// @param records    The records (changed in place).
    /// @param jsonFields The fields holding JSON text.
    /// @return The records.
    @SuppressWarnings("unchecked")
    public static List<Object> withJsonFields(final List<Object> records, final List<String> jsonFields) {
        if (jsonFields == null || jsonFields.isEmpty()) {
            return records;
        }
        for (final Object item : records) {
            if (!(item instanceof Map)) {
                continue;
            }
            final Map<String, Object> record = (Map<String, Object>) item;
            for (final String field : jsonFields) {
                final Object held = parsedJson(record.get(field));
                if (held instanceof Map) {
                    SampleProfiler.flatten(held).forEach((key, value) ->
                            record.putIfAbsent(field + "." + key, JsonValues.jsonText(value)));
                }
            }
        }
        return records;
    }

    private static Object parsedJson(final Object value) {
        if (value != null && !(value instanceof String)) {
            return null;
        }
        try {
            return PyJson.loads(value == null
                    ? ""
                    : (String) value);
        } catch (final PyJson.JsonDecodeException e) {
            return null;
        }
    }

    /// Records with the elements and attributes of the XML their xml_fields hold added as `<field>/<path>` from
    /// the root and `<field>//<name>` for anywhere below, by local name, as the XSLT reads them (the first value).
    ///
    /// @param records   The records (changed in place).
    /// @param xmlFields The fields holding XML text.
    /// @return The records.
    @SuppressWarnings("unchecked")
    public static List<Object> withXmlFields(final List<Object> records, final List<String> xmlFields) {
        if (xmlFields == null || xmlFields.isEmpty()) {
            return records;
        }
        for (final Object item : records) {
            if (!(item instanceof Map)) {
                continue;
            }
            final Map<String, Object> record = (Map<String, Object>) item;
            for (final String field : xmlFields) {
                final XmlTree.Element root = parsedXml(record.get(field));
                if (root != null) {
                    addXmlFields(record, field, root);
                }
            }
        }
        return records;
    }

    private static XmlTree.Element parsedXml(final Object value) {
        if (!(value instanceof final String held)) {
            return null;
        }
        final String text = PyText.strip(held);
        if (!text.startsWith("<")) {
            return null;
        }
        try {
            return XmlTree.parse(text);
        } catch (final XmlTree.XmlException e) {
            return null;
        }
    }

    private static void addXmlFields(final Map<String, Object> record, final String field,
                                     final XmlTree.Element root) {
        for (final XmlTree.Element node : root.iter()) {
            final List<String> steps = new ArrayList<>();
            for (XmlTree.Element at = node; at != null; at = at.parent()) {
                steps.addFirst(at.localName());
            }
            final String path = String.join("/", steps);
            final String value = PyText.strip(node.text());
            for (final String name : List.of(field + "/" + path, field + "//" + node.localName())) {
                if (!value.isEmpty() || node.isLeaf()) {
                    record.putIfAbsent(name, value);
                }
            }
            node.attrib().forEach((attribute, attributeValue) -> {
                final String local = localName(attribute);
                for (final String name : List.of(field + "/" + path + "/@" + local,
                        field + "//" + node.localName() + "/@" + local, field + "//@" + local)) {
                    record.putIfAbsent(name, attributeValue);
                }
            });
        }
    }

    private static String localName(final String clarkName) {
        return clarkName.startsWith("{")
                ? clarkName.substring(clarkName.indexOf('}') + 1)
                : clarkName;
    }

    /// The sample's records as the mapping's input reads them, with the fields json_fields and xml_fields hold.
    /// Several sample files are read one by one (each with its own header line).
    ///
    /// @param mapping  The mapping.
    /// @param sample   The sample's text, or a list of texts (one a file).
    /// @param splitter The Data Splitter spec a data_splitter input is read with, or null.
    /// @return The records (maps for data_splitter and json inputs, elements for xml inputs), and a note saying
    /// why nothing could be read, or null.
    public static SampleRecords sampleRecords(final TranslationMapping mapping, final Object sample,
                                              final SplitterSpec splitter) {
        final SampleRecords found = rawRecords(mapping, sample, splitter);
        return new SampleRecords(withXmlFields(withJsonFields(found.records(), mapping.jsonFields()),
                mapping.xmlFields()), found.note());
    }

    private static SampleRecords rawRecords(final TranslationMapping mapping, final Object sample,
                                            final SplitterSpec splitter) {
        if (sample instanceof final List<?> texts) {
            final List<Object> records = new ArrayList<>();
            final List<String> notes = new ArrayList<>();
            for (final Object text : texts) {
                final SampleRecords found = rawRecords(mapping, text, splitter);
                records.addAll(found.records());
                if (found.note() != null && !notes.contains(found.note())) {
                    notes.add(found.note());
                }
            }
            return new SampleRecords(records, notes.isEmpty()
                    ? null
                    : String.join("; ", notes));
        }
        final String text = PyText.strip((String) sample, "﻿\r\n ");
        if ("data_splitter".equals(mapping.input())) {
            if (splitter == null) {
                return new SampleRecords(new ArrayList<>(), "no splitter spec: the sample's fields are not known "
                                                            + "here (give splitter, from build_data_splitter)");
            }
            final List<Object> records = new ArrayList<>();
            SplitterDryRun.dryRun(splitter, text).records().forEach(r -> records.add(new LinkedHashMap<>(r)));
            return new SampleRecords(records, null);
        }
        if ("json".equals(mapping.input())) {
            return jsonRecords(text);
        }
        return xmlRecords(mapping, text);
    }

    private static SampleRecords jsonRecords(final String text) {
        final List<Object> values = new ArrayList<>();
        if (text.startsWith("{") && SampleProfiler.wrappedRecords(text) != null) {
            // One object wrapping the records (CloudTrail): the parser's one record; for_each reads its items.
            values.add(PyJson.loads(text));
        } else if (text.startsWith("[")) {
            try {
                if (PyJson.loads(text) instanceof final List<?> list) {
                    list.stream().filter(Map.class::isInstance).forEach(values::add);
                }
            } catch (final PyJson.JsonDecodeException e) {
                return new SampleRecords(new ArrayList<>(), "the sample is not a JSON array");
            }
        } else {
            for (final String line : PyText.splitlines(text)) {
                if (PyText.isBlank(line)) {
                    continue;
                }
                final Object value;
                try {
                    value = PyJson.loads(line);
                } catch (final PyJson.JsonDecodeException e) {
                    return new SampleRecords(new ArrayList<>(), "the sample is neither a JSON array nor JSON lines");
                }
                if (value instanceof Map) {
                    values.add(value);
                }
            }
        }
        final List<Object> records = new ArrayList<>();
        values.forEach(value -> records.add(SampleProfiler.flatten(value)));
        return new SampleRecords(records, null);
    }

    private static SampleRecords xmlRecords(final TranslationMapping mapping, final String text) {
        XmlTree.Element root = null;
        List<XmlTree.Element> cut = null;
        if ("xml".equals(mapping.input())) {
            try {
                root = XmlTree.parse(PyText.subFirst(xmlDeclaration(), "", text));
            } catch (final XmlTree.XmlException e) {
                // A big document read only to here: its complete records.
                final XmlTree.Pull pull = SampleProfiler.cutDocument(text);
                if (pull != null) {
                    root = pull.root();
                    cut = pull.records();
                }
            }
        }
        if ("xml_fragments".equals(mapping.input()) || root == null) {
            final List<XmlTree.Element> fragments = SampleProfiler.xmlFragments(text);
            if (fragments == null || fragments.isEmpty()) {
                return new SampleRecords(new ArrayList<>(), "the sample is not XML a parser could read");
            }
            final String record = mapping.record() != null && !mapping.record().isEmpty()
                    ? mapping.record()
                    : fragments.getFirst().localName();
            return new SampleRecords(new ArrayList<>(fragments.stream()
                    .filter(fragment -> fragment.localName().equals(record))
                    .toList()), null);
        }
        final List<XmlTree.Element> all = new ArrayList<>();
        if (cut == null) {
            all.addAll(root.iter());
        } else {
            // The root as far as the cut, holding only its complete records.
            all.add(root);
            cut.forEach(record -> all.addAll(record.iter()));
        }
        final String record = mapping.record();
        return new SampleRecords(new ArrayList<>(all.stream()
                .filter(element -> element.localName().equals(record))
                .toList()), null);
    }

    private static Pattern xmlDeclaration() {
        return PyText.re("^\\s*<\\?xml[^>]*\\?>");
    }

    /// @param record A record (a map, or an XML element).
    /// @param field  A field: a map's key, or an XML path.
    /// @return The field's value as text (a value read from JSON as JSON), or null if the record has none.
    public static String valueOf(final Object record, final String field) {
        if (record instanceof final Map<?, ?> map) {
            final Object value = map.get(field);
            return value == null
                    ? null
                    : JsonValues.jsonText(value);
        }
        return record instanceof final XmlTree.Element element
                ? xmlValue(element, field)
                : null;
    }

    /// @param record A record.
    /// @param path   A for_each path.
    /// @return The items the record holds under the path: a JSON array's objects (flattened), or the XML elements
    /// the path selects.
    public static List<Object> itemsOf(final Object record, final String path) {
        final List<Object> items = new ArrayList<>();
        if (record instanceof final Map<?, ?> map) {
            if (map.get(path) instanceof final List<?> list) {
                list.stream().filter(Map.class::isInstance).forEach(v -> items.add(SampleProfiler.flatten(v)));
            }
            return items;
        }
        if (!(record instanceof final XmlTree.Element element)) {
            return items;
        }
        List<XmlTree.Element> nodes = List.of(element);
        for (final String segment : segments(path)) {
            final Matcher m = STEP.matcher(segment);
            if (!m.matches() || m.group("attr") != null) {
                return items;
            }
            final List<XmlTree.Element> found = new ArrayList<>();
            for (final XmlTree.Element node : nodes) {
                for (final XmlTree.Element child : node.elements()) {
                    if ("*".equals(m.group("name")) || child.localName().equals(m.group("name"))) {
                        found.add(child);
                    }
                }
            }
            nodes = found;
        }
        items.addAll(nodes);
        return items;
    }

    // Whether a field condition holds for a record, as the XSLT would test it; null when it cannot be told here
    // (an xpath, a dictionary, or a field an extraction produces, unless its values are given).
    private static Boolean holds(final Condition c, final Object record, final Set<String> derived,
                                 final Map<String, String> extracted) {
        if (c.xpath() != null || c.inDictionary() != null || "record".equals(c.scope())) {
            return null;
        }
        if (derived.contains(c.field()) && (extracted == null || !extracted.containsKey(c.field()))) {
            return null;
        }
        final String value = derived.contains(c.field())
                ? extracted.get(c.field())
                : valueOf(record, c.field());
        if (c.present() != null) {
            return (value != null && !PyText.strip(value).isEmpty()) == c.present();
        }
        if (value == null) {
            return false;
        }
        if (c.equalTo() != null) {
            return value.equals(c.equalTo());
        }
        if (c.oneOf() != null) {
            return c.oneOf().contains(value);
        }
        final Pattern pattern = compiled(c.matches(), 0);
        return pattern == null
                ? null
                : SafePattern.matcher(pattern, value).find();
    }

    // A regex compiled, or null if it can't be (Python's re.error).
    private static Pattern compiled(final String regex, final int flags) {
        if (regex == null) {
            return null;
        }
        if (PATTERNS.size() > CACHED_PATTERNS) {
            PATTERNS.clear();
        }
        return PATTERNS.computeIfAbsent(flags + ":" + regex, key -> {
            try {
                return Optional.of(SafePattern.compile(regex, PyText.FLAGS | flags));
            } catch (final PatternSyntaxException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /// The values the mapping's extractions give a record (the first match's groups, "" where it doesn't match),
    /// as the XSLT's analyze-string does.
    ///
    /// @param mapping The mapping.
    /// @param record  A record.
    /// @return The values by name, or null when one can't be run here (an xpath source, a regex that doesn't
    /// compile).
    public static Map<String, String> extractedValues(final TranslationMapping mapping, final Object record) {
        final Map<String, String> values = new LinkedHashMap<>();
        for (final Extraction ex : mapping.extract()) {
            if (ex.xpath() != null || ex.field() == null || ex.field().isEmpty()) {
                return null;
            }
            final String text = values.containsKey(ex.field())
                    ? values.get(ex.field())
                    : valueOf(record, ex.field());
            final Pattern pattern = compiled(ex.regex(), regexFlags(ex.flags()));
            if (pattern == null) {
                return null;
            }
            final Matcher found = SafePattern.matcher(pattern, text == null
                    ? ""
                    : text);
            final boolean matched = found.find();
            for (int i = 1; i <= ex.names().size(); i++) {
                final String name = ex.names().get(i - 1);
                if (name != null && !name.isEmpty()) {
                    values.put(name, matched && i <= found.groupCount()
                            ? nonNull(found.group(i))
                            : "");
                }
            }
        }
        return values;
    }

    private static String nonNull(final String text) {
        return text == null
                ? ""
                : text;
    }

    private static int regexFlags(final String flags) {
        int out = 0;
        for (final char flag : flags.toCharArray()) {
            out |= switch (flag) {
                case 'i' -> Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                case 'm' -> Pattern.MULTILINE;
                case 's' -> Pattern.DOTALL;
                case 'x' -> Pattern.COMMENTS;
                default -> 0;
            };
        }
        return out;
    }

    // Record n's extracted values: from the list worked out once, else now (null without extract entries).
    private static Map<String, String> extracted(final TranslationMapping mapping, final List<Object> records,
                                                 final List<Map<String, String>> extracted, final int n) {
        if (mapping.extract().isEmpty()) {
            return null;
        }
        return extracted != null
                ? extracted.get(n)
                : extractedValues(mapping, records.get(n));
    }

    private static Set<String> derived(final TranslationMapping mapping) {
        final Set<String> derived = new HashSet<>();
        mapping.extract().forEach(ex -> ex.names().stream()
                .filter(n -> n != null && !n.isEmpty())
                .forEach(derived::add));
        return derived;
    }

    /// The rule a record falls into.
    ///
    /// @param mapping   The mapping.
    /// @param record    A record.
    /// @param extracted The values its extractions give it ([#extractedValues(TranslationMapping, Object)]), or
    ///                  null.
    /// @return A drop rule (`drop: <reason>`), an event rule's name, "" when none matches, or null when a
    /// condition on the way cannot be told here.
    public static String ruleOf(final TranslationMapping mapping, final Object record,
                                final Map<String, String> extracted) {
        final Set<String> derived = derived(mapping);
        final List<Map.Entry<String, List<Condition>>> candidates = new ArrayList<>();
        for (final DropRule drop : mapping.dropWhen()) {
            candidates.add(Map.entry("drop: " + drop.reason(), drop.when()));
        }
        for (final EventRule rule : mapping.events()) {
            candidates.add(Map.entry(rule.name(), rule.when()));
        }
        for (final Map.Entry<String, List<Condition>> candidate : candidates) {
            final List<Boolean> tests = new ArrayList<>();
            candidate.getValue().forEach(c -> tests.add(holds(c, record, derived, extracted)));
            if (tests.contains(null)) {
                return null;
            }
            if (tests.stream().allMatch(Boolean::booleanValue)) {
                return candidate.getKey();
            }
        }
        return "";
    }

    /// What a set of records holds, for a person to judge: each field's distinct short, word-like values (kinds,
    /// actions, levels, users), then an example of the first free-text field. Times, addresses and numbers are
    /// left out.
    ///
    /// @param records The records.
    /// @return The line.
    public static String traits(final List<?> records) {
        final Map<String, List<String>> fields = new LinkedHashMap<>();
        String[] text = null;
        for (final Map<String, Object> record : JsonValues.maps(records)) {
            for (final Map.Entry<String, Object> entry : record.entrySet()) {
                if (!(entry.getValue() instanceof final String value) || PyText.isBlank(value)) {
                    continue;
                }
                if (PyText.match(TOKEN, value)) {
                    final List<String> seen = fields.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
                    if (!seen.contains(value)) {
                        seen.add(value);
                    }
                } else if (text == null && PyText.split(value).size() > 2) {
                    text = new String[]{entry.getKey(), PyText.head(value, 80)};
                }
            }
        }
        final List<String> parts = new ArrayList<>();
        fields.entrySet().stream().limit(6).forEach(entry -> {
            final List<String> values = entry.getValue();
            parts.add(entry.getKey() + ": " + String.join(", ", values.subList(0, Math.min(8, values.size())))
                      + (values.size() > 8
                    ? " ..."
                    : ""));
        });
        if (text != null) {
            parts.add("e.g. " + text[0] + ": \"" + text[1] + "\"");
        }
        return String.join("; ", parts);
    }

    /// The sample records that rules writing EventDetail/Unknown catch. A rule without allow_unknown that catches
    /// any is a problem naming what the records hold (the conditions' rule is refused by the generator too; the
    /// rule for the rest is refused here); a rule with it is reported for the user to confirm.
    ///
    /// @param mapping   The mapping.
    /// @param records   The sample's records.
    /// @param extracted Each record's extracted values, worked out once, or null to work them out.
    /// @return The problems, and the rules kept as Unknown (`rule`, `reason`, `records`, `sample`, and
    /// `suggested` and `against_suggestion` where the values show an action).
    public static Coverage unknownCoverage(final TranslationMapping mapping, final List<Object> records,
                                           final List<Map<String, String>> extracted) {
        if (hasText(mapping.forEach()) || records.isEmpty()) {
            return new Coverage(List.of(), List.of());
        }
        final Map<String, EventRule> writesUnknown = new LinkedHashMap<>();
        mapping.events().stream()
                .filter(rule -> MappingFacts.writesUnknown(mapping, rule))
                .forEach(rule -> writesUnknown.put(rule.name(), rule));
        final Map<String, List<Object>> caught = new LinkedHashMap<>();
        for (int n = 0; n < records.size(); n++) {
            final Map<String, String> values = extracted(mapping, records, extracted, n);
            final String name = ruleOf(mapping, records.get(n), values);
            if (name != null && writesUnknown.containsKey(name)) {
                // An XML record as its paths, and what extract finds in it, so what it holds is shown and its
                // actions found as a flat record's are.
                caught.computeIfAbsent(name, k -> new ArrayList<>()).add(caughtRecord(records.get(n), values));
            }
        }
        final List<String> problems = new ArrayList<>();
        final List<Map<String, Object>> kept = new ArrayList<>();
        final Map<String, String> common = new LinkedHashMap<>();
        mapping.common().stream()
                .filter(entry -> hasText(entry.field()))
                .forEach(entry -> common.put(entry.strippedPath(), entry.field()));
        caught.forEach((name, found) -> coverageOf(writesUnknown.get(name), name, found, records.size(), common,
                problems, kept));
        return new Coverage(problems, kept);
    }

    private static Object caughtRecord(final Object record, final Map<String, String> values) {
        final Object flat = record instanceof final XmlTree.Element element
                ? new LinkedHashMap<String, Object>(xmlFlat(element))
                : record;
        if (!(flat instanceof final Map<?, ?> map) || values == null || values.isEmpty()) {
            return flat;
        }
        final Map<String, Object> merged = new LinkedHashMap<>();
        map.forEach((k, v) -> merged.put((String) k, v));
        values.forEach((k, v) -> {
            if (!v.isEmpty()) {
                merged.put(k, v);
            }
        });
        return merged;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void coverageOf(final EventRule rule,
                                   final String name,
                                   final List<Object> found,
                                   final int total,
                                   final Map<String, String> common,
                                   final List<String> problems,
                                   final List<Map<String, Object>> kept) {
        final String held = traits(found);
        // Values that show the action (ALLOW/DENY between addresses, LOGIN/LOGOUT, CONFIG_CHANGE) get the rules
        // for it.
        final Set<String> names = new LinkedHashSet<>();
        JsonValues.maps(found).forEach(r -> names.addAll(r.keySet()));
        final List<Map<String, Object>> base = new ArrayList<>();
        final Set<String> skip = new HashSet<>();
        for (final Condition c : rule.when()) {
            base.add(c.toJson(DumpMode.WITHOUT_NONE));
            if (hasText(c.field())) {
                skip.add(c.field());
            }
        }
        final ActionRules.Found shown = ActionRules.actionRules(found, new ArrayList<>(names), base, name,
                common.get("EventSource/User/Id"), common.get("EventDetail/Description"), skip, null);
        final String use = shown.rules().isEmpty()
                ? ""
                : " Their values show the action: " + ActionRules.described(shown.rules()) + ". Add these rules "
                  + "before '" + name + "' (they validate as they are; add Data entries as you like)"
                  + (shown.left().isEmpty()
                        ? ""
                        : ", leaving " + shown.split() + " " + PyText.repr(shown.left()) + " to it")
                  + ": " + PyJson.dumps(shown.rules());
        // Connections allowed or denied, and logons, are never unknown; a configuration change is the user's call.
        final List<Map<String, Object>> plain = shown.rules().stream()
                .filter(r -> JsonValues.maps(r.get("fields")).stream().anyMatch(f -> {
                    final String path = (String) f.get("path");
                    return path.startsWith("EventDetail/Network/") || path.startsWith("EventDetail/Authenticate/");
                }))
                .toList();
        // Every record it catches has a rule from its own values (an alert, a service starting, a configuration
        // saved): seen, an agent folded the draft's rules into 'other' as Unknown and the user had to decline the
        // form.
        final boolean covered = !shown.rules().isEmpty() && shown.left().isEmpty();
        final boolean allowed = hasText(rule.allowUnknown());
        if (allowed && (!plain.isEmpty() || covered) && !rule.keepUnknown()) {
            final Set<String> values = new HashSet<>();
            (covered
                    ? shown.rules()
                    : plain).forEach(r -> values.addAll(ActionRules.conditionValues(
                    JsonValues.maps(r.get("when")).getLast())));
            final long known = JsonValues.maps(found).stream()
                    .filter(r -> r.get(shown.split()) instanceof final String v && values.contains(v))
                    .count();
            problems.add("[" + name + "] can't be kept as Unknown: " + known + " of its " + found.size()
                         + " sample records are not unknown events. Only if the user, shown the rules below, still "
                         + "wants them Unknown (they can say so in the chat): keep_unknown: true on the rule with "
                         + "allow_unknown, and they confirm it in the form." + use);
        } else if (allowed) {
            // keep_unknown: the user chose Unknown over the action elements the values suggest; the form shows them.
            final Map<String, Object> entry = dict("rule", name, "reason", rule.allowUnknown(), "records",
                    found.size(), "sample", held);
            if (!shown.rules().isEmpty()) {
                entry.put("suggested", ActionRules.described(shown.rules()) + " (rather than Unknown)");
                if (rule.keepUnknown()) {
                    entry.put("against_suggestion", true);
                }
            }
            kept.add(entry);
        } else if (!rule.when().isEmpty()) {
            problems.add("[" + name + "] keeps EventDetail/Unknown for " + found.size() + " of the " + total
                         + " sample records: " + held + ". Give them the action element these values describe, a "
                         + "rule per kind if they differ (split on the field that tells them apart)." + use);
        } else {
            problems.add("[" + name + "] (the rule for records no other rule matches) writes EventDetail/Unknown "
                         + "for " + found.size() + " of the " + total + " sample records: " + held + ". Give them "
                         + "rules with the action elements these values describe; only if none fits, set "
                         + "allow_unknown on this rule to the reason, which the user confirms.");
        }
    }

    /// A TypeId read from one field for every rule that, in the sample, is the same for records of different
    /// rules: events of different kinds share it. Seen: TypeId the SecretServer category ('User') for logons,
    /// logoffs and role changes alike. Told from the sample, not the mapping's shape: a TypeId field whose values
    /// already differ per kind (a firewall's action, FortiGate's logid) is fine though the rules test other fields
    /// as well. A TypeId joining fields (join) is told the same way, from its joined values.
    ///
    /// @param mapping   The mapping.
    /// @param records   The sample's records.
    /// @param extracted Each record's extracted values, worked out once, or null to work them out.
    /// @return The problem, or an empty list.
    public static List<String> sharedTypeIds(final TranslationMapping mapping, final List<Object> records,
                                             final List<Map<String, String>> extracted) {
        final FieldMapping typeId = mapping.common().stream()
                .filter(e -> "EventDetail/TypeId".equals(e.strippedPath()))
                .findFirst()
                .orElse(null);
        if (typeId == null || (!hasText(typeId.field()) && typeId.join() == null) || hasText(mapping.forEach())) {
            return List.of();
        }
        final Set<String> own = new HashSet<>();
        mapping.events().stream()
                .filter(r -> r.fields().stream().anyMatch(f -> "EventDetail/TypeId".equals(f.strippedPath())))
                .forEach(r -> own.add(r.name()));
        final Set<String> derived = derived(mapping);
        final Map<String, Map<String, Integer>> byValue = new LinkedHashMap<>();
        for (int n = 0; n < records.size(); n++) {
            final Map<String, String> values = mapping.extract().isEmpty()
                    ? Map.of()
                    : extracted(mapping, records, extracted, n);
            final String name = ruleOf(mapping, records.get(n), values);
            if (name == null || name.isEmpty() || name.startsWith("drop: ") || own.contains(name)) {
                continue;
            }
            final Object record = records.get(n);
            final Function<String, String> read = field -> derived.contains(field)
                    ? (values == null
                            ? null
                            : values.get(field))
                    : valueOf(record, field);
            final String value = typeId.join() == null
                    ? read.apply(typeId.field())
                    : typeId.join().stream()
                            .map(read)
                            .filter(LocalCheck::hasText)
                            .collect(Collectors.joining(typeId.joinSeparator()));
            if (value != null && !value.isEmpty()) {
                byValue.computeIfAbsent(value, k -> new LinkedHashMap<>()).merge(name, 1, Integer::sum);
            }
        }
        final List<String> examples = new ArrayList<>();
        byValue.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .limit(3)
                .forEach(entry -> examples.add("'" + entry.getKey() + "' for rules "
                                               + String.join(", ", entry.getValue().keySet().stream()
                        .sorted(PyText::compare)
                        .toList())));
        if (examples.isEmpty()) {
            return List.of();
        }
        // A problem, not the Python server's warning (seen: two runs that left TypeId the category alone, 'User' for
        // logons, logoffs and lockouts, the warning one among many).
        return List.of("EventDetail/TypeId reads " + (typeId.join() == null
                ? "'" + typeId.field() + "'"
                : "join " + PyText.list(typeId.join())) + ", which in the sample is the same for events of different "
                       + "kinds (" + String.join("; ", examples) + "). TypeId names the kind of event, so each "
                       + "kind needs its own: write it in the format the standing instructions (AGENTS docs) set "
                       + "for TypeIds, if they set one; otherwise join the fields that tell the kinds apart, e.g. "
                       + "{\"path\": \"EventDetail/TypeId\", \"join\": [\"" + (typeId.join() == null
                ? typeId.field()
                : typeId.join().getFirst()) + "\", \"<the field telling them apart, e.g. action>\"]} (separator, "
                       + "default '-'), or give each rule its own value.");
    }

    /// Required elements a rule writes only when their inputs have a value, whose inputs are empty in sample records
    /// the rule takes: those events fail the schema, which reports the element written after the one left out
    /// (seen: "Invalid content was found starting with element '{Data}'. One of '{Command}' is expected", read as
    /// Data put in the wrong place, two rounds of stepping before the Command entry was given a default). Not in the
    /// Python server, which warned of such elements without saying whether the sample has any. Only entries read
    /// from fields are counted (an xpath's value isn't known here).
    ///
    /// @param mapping         The mapping.
    /// @param records         The sample's records.
    /// @param requiredOnInput Each rule's required elements written only when their inputs have a value, by rule.
    /// @return The warnings.
    public static List<String> requiredLeftEmpty(final TranslationMapping mapping,
                                                 final List<Object> records,
                                                 final Map<String, List<String>> requiredOnInput) {
        if (hasText(mapping.forEach()) || requiredOnInput.isEmpty()) {
            return List.of();
        }
        final Set<String> derived = derived(mapping);
        final List<String> rules = new ArrayList<>();
        final List<Map<String, String>> extracted = new ArrayList<>();
        for (int n = 0; n < records.size(); n++) {
            final Map<String, String> values = extracted(mapping, records, null, n);
            extracted.add(values);
            rules.add(ruleOf(mapping, records.get(n), values));
        }
        final List<String> out = new ArrayList<>();
        for (final EventRule rule : mapping.events()) {
            for (final String path : requiredOnInput.getOrDefault(rule.name(), List.of())) {
                final List<FieldMapping> entries = new ArrayList<>(mapping.common());
                entries.addAll(rule.fields());
                entries.removeIf(e -> !e.strippedPath().equals(path) && !e.strippedPath().startsWith(path + "/"));
                final List<String> fields = new ArrayList<>();
                boolean readable = !entries.isEmpty();
                for (final FieldMapping entry : entries) {
                    final List<String> inputs = new ArrayList<>();
                    if (hasText(entry.field())) {
                        inputs.add(entry.field());
                    }
                    inputs.addAll(Objects.requireNonNullElse(entry.anyOf(), List.of()));
                    readable = readable && !inputs.isEmpty() && entry.value() == null && !hasText(entry.xpath())
                               && entry.defaultValue() == null && entry.lookup() == null
                               && !hasText(entry.dictionary());
                    fields.addAll(inputs);
                }
                if (!readable) {
                    continue;
                }
                int taken = 0;
                int empty = 0;
                for (int n = 0; n < records.size(); n++) {
                    if (!rule.name().equals(rules.get(n))) {
                        continue;
                    }
                    taken++;
                    final Object record = records.get(n);
                    final Map<String, String> values = extracted.get(n);
                    if (fields.stream().allMatch(field -> !hasText(derived.contains(field)
                            ? (values == null
                                    ? null
                                    : values.get(field))
                            : valueOf(record, field)))) {
                        empty++;
                    }
                }
                if (empty > 0) {
                    out.add("[" + rule.name() + "] " + path + " is required by the schema, but its input ("
                            + fields.stream().map(f -> "'" + f + "'").collect(Collectors.joining(", "))
                            + ") is empty in " + empty + " of the " + taken + " sample records this rule takes: "
                            + "those events fail the schema, which reports the element written after it (often "
                            + "Data) as unexpected. Give the entry a default, or any_of with a field that is "
                            + "filled.");
                }
            }
        }
        return out;
    }

    /// Data entries reading fields that none of the sample records their rule takes holds: Data the kind doesn't
    /// carry, which the documentation then lists as "(not in the sample)". A warning, as the sample may lack them
    /// while other records have them. Not in the Python server; seen: a SecretServer launch rule's rpc_account,
    /// target and duration_ms, in none of its records.
    ///
    /// @param mapping The mapping.
    /// @param records The sample's records.
    /// @return The warnings.
    public static List<String> dataNotHeld(final TranslationMapping mapping, final List<Object> records) {
        if (hasText(mapping.forEach())) {
            return List.of();
        }
        final Set<String> derived = derived(mapping);
        final List<String> rules = new ArrayList<>();
        final List<Map<String, String>> extracted = new ArrayList<>();
        for (int n = 0; n < records.size(); n++) {
            final Map<String, String> values = extracted(mapping, records, null, n);
            extracted.add(values);
            rules.add(ruleOf(mapping, records.get(n), values));
        }
        final List<String> out = new ArrayList<>();
        for (final EventRule rule : mapping.events()) {
            final List<Integer> taken = new ArrayList<>();
            for (int n = 0; n < records.size(); n++) {
                if (rule.name().equals(rules.get(n))) {
                    taken.add(n);
                }
            }
            if (taken.isEmpty()) {
                continue;
            }
            final List<String> unheld = new ArrayList<>();
            for (final FieldMapping entry : rule.fields()) {
                final List<String> inputs = new ArrayList<>();
                if (hasText(entry.field())) {
                    inputs.add(entry.field());
                }
                inputs.addAll(Objects.requireNonNullElse(entry.anyOf(), List.of()));
                if (!hasText(entry.dataName()) || inputs.isEmpty() || entry.defaultValue() != null) {
                    continue;
                }
                final boolean held = taken.stream().anyMatch(n -> inputs.stream().anyMatch(field -> hasText(
                        derived.contains(field)
                                ? (extracted.get(n) == null
                                        ? null
                                        : extracted.get(n).get(field))
                                : valueOf(records.get(n), field))));
                if (!held) {
                    unheld.add("'" + entry.dataKey() + "'");
                }
            }
            if (!unheld.isEmpty()) {
                out.add("[" + rule.name() + "] Data " + String.join(", ", unheld) + " read" + (unheld.size() == 1
                        ? "s a field"
                        : " fields") + " none of the " + taken.size() + " sample records this rule takes holds: "
                        + "remove " + (unheld.size() == 1
                        ? "it"
                        : "them") + ", unless other records will have " + (unheld.size() == 1
                        ? "it"
                        : "them") + ".");
            }
        }
        return out;
    }

    /// Fields mapped as they are into a user's Id whose sample values are domain-qualified (`DOMAIN\\user`,
    /// `user@domain`): an Id is usually the account name alone, which `strip_domain` gives (unlike the Stroom MCP
    /// server, which said nothing: seen in eval cases 35, 36 and 43, the domain kept in every Id).
    ///
    /// @param mapping The mapping.
    /// @param records The sample's records.
    /// @param schema  The event-logging schema, which says which elements are users; null to go by the name User.
    /// @return The warnings.
    public static List<String> domainQualifiedUserIds(final TranslationMapping mapping,
                                                      final List<Object> records,
                                                      final EventSchema schema) {
        return domainQualifiedWarnings(domainQualifiedIds(mapping, records, schema));
    }

    /// @param qualified The fields whose values going into users' Ids carry a domain
    ///                  ([#domainQualifiedIds(TranslationMapping, List)]).
    /// @return A warning for each Id they go into.
    public static List<String> domainQualifiedWarnings(final List<QualifiedIds> qualified) {
        final List<String> out = new ArrayList<>();
        for (final QualifiedIds found : qualified) {
            for (final String path : found.paths()) {
                final String said = path + " takes field '" + found.field() + "', whose values are "
                                    + "domain-qualified (" + PyText.repr(found.examples()) + "): an Id is usually "
                                    + "the account name alone. If so, give the entry transform: strip_domain";
                if (!out.contains(said)) {
                    out.add(said);
                }
            }
        }
        return out;
    }

    /// The fields mapped as they are into users' Ids whose sample values are domain-qualified (`DOMAIN\\user`,
    /// `user@domain`), read from the record or from what the mapping's extractions give it (seen: SecretServer's
    /// `By User`, read from its message's key/value pairs, never checked, and the domain kept in every Id). The Ids
    /// are those of the schema's users (User, RunAs, Approver, Requestor, wherever they are: Send/Destination/User,
    /// say), not an email's addressees (From, To, Cc, Bcc), whose Ids are email addresses.
    ///
    /// @param mapping The mapping.
    /// @param records The sample's records.
    /// @param schema  The event-logging schema, which says which elements are users; null to go by the name User.
    /// @return Each field, in the mapping's order, with the Id paths it is mapped into as it is and up to three of
    /// its domain-qualified values.
    public static List<QualifiedIds> domainQualifiedIds(final TranslationMapping mapping,
                                                        final List<Object> records,
                                                        final EventSchema schema) {
        final FieldValues values = new FieldValues(mapping, records);
        final Map<String, List<String>> paths = new LinkedHashMap<>();
        final Map<String, Set<String>> qualified = new LinkedHashMap<>();
        for (final FieldMapping entry : mapping.allEntries()) {
            if (!hasText(entry.field()) || !isUserId(entry.path(), schema) || entry.hasMap()
                || hasText(entry.transform()) || hasText(entry.xpath()) || entry.value() != null) {
                continue;
            }
            final Set<String> found = new LinkedHashSet<>();
            for (final String value : values.of(entry)) {
                if (value != null && DOMAIN_QUALIFIED.matcher(PyText.strip(value)).matches()) {
                    found.add(PyText.strip(value));
                }
            }
            if (!found.isEmpty()) {
                final List<String> into = paths.computeIfAbsent(entry.field(), field -> new ArrayList<>());
                if (!into.contains(entry.path())) {
                    into.add(entry.path());
                }
                qualified.computeIfAbsent(entry.field(), field -> new LinkedHashSet<>()).addAll(found);
            }
        }
        final List<QualifiedIds> out = new ArrayList<>();
        paths.forEach((field, into) -> out.add(new QualifiedIds(field, List.copyOf(into),
                qualified.get(field).stream().limit(3).toList())));
        return out;
    }

    /// Values the sample's records hold in the fields the mapping writes, that look like stand-ins for none (`-`,
    /// `N/A`, ...; see [Placeholders]), for the user to say whether to leave them out. Not in the Stroom MCP server;
    /// asked for by the user (seen: W3C logs' `-` for a missing address went on into an Elasticsearch ip field).
    ///
    /// @param mapping The mapping.
    /// @param records The sample's records.
    /// @return The stand-ins found, those the mapping's nil_values already leave out aside.
    public static List<Placeholders.Found> placeholders(final TranslationMapping mapping,
                                                        final List<Object> records) {
        final FieldValues values = new FieldValues(mapping, records);
        final Map<String, List<String>> byField = new LinkedHashMap<>();
        for (final FieldMapping entry : mapping.allEntries()) {
            // Only fields written as they are: a value map, lookup or dictionary says what each value becomes.
            if (!hasText(entry.field()) || entry.value() != null || entry.hasMap() || entry.lookup() != null
                || hasText(entry.dictionary()) || hasText(entry.xpath()) || byField.containsKey(entry.field())) {
                continue;
            }
            byField.put(entry.field(), values.of(entry));
        }
        return Placeholders.find(byField, mapping.nilValues() == null
                ? Set.of()
                : Set.copyOf(mapping.nilValues()));
    }

    // An Id of one of the schema's users, not an email's addressee; without the schema, an Id of an element named
    // User.
    private static boolean isUserId(final String path, final EventSchema schema) {
        if (schema == null) {
            return USER_ID.matcher(path).find();
        }
        if (!path.endsWith("/Id")) {
            return false;
        }
        final List<EventSchema.Child> chain;
        try {
            chain = schema.resolve(path);
        } catch (final RuntimeException e) {
            return false;
        }
        if (chain.size() < 2) {
            return false;
        }
        final EventSchema.Child user = chain.get(chain.size() - 2);
        return USER_TYPE.equals(schema.typeName(user.decl())) && !EMAIL_USERS.contains(user.name());
    }

    /// Fields mapped as they are into an element whose schema type has a pattern (an address, a port, a MAC),
    /// whose sample values don't fit it: Stroom's schema check refuses such events. Seen in eval case 40:
    /// CloudTrail's sourceIPAddress is an AWS service's name for calls a service made, and the step failed on
    /// 'cloudformation.amazonaws.com' in EventSource/Client/IPAddress, which nothing had checked before.
    ///
    /// @param mapping The mapping.
    /// @param records The sample's records.
    /// @param schema  The event-logging schema.
    /// @return The problems.
    public static List<String> checkSchemaValues(final TranslationMapping mapping, final List<Object> records,
                                                 final EventSchema schema) {
        final List<String> out = new ArrayList<>();
        // Read from the record or from what the mapping's extractions give it (seen: SecretServer's fields, read
        // from its message's key/value pairs, never checked; not in the Stroom MCP server, which read the record's
        // own fields alone).
        final FieldValues fieldValues = new FieldValues(mapping, records);
        final Set<String> nil = new HashSet<>(mapping.nilValues() == null
                ? List.of()
                : mapping.nilValues());
        for (final FieldMapping entry : mapping.allEntries()) {
            if (!hasText(entry.field()) || entry.hasMap() || hasText(entry.transform()) || hasText(entry.timeFormat())
                || entry.lookup() != null || hasText(entry.dictionary()) || hasText(entry.xpath())
                || entry.value() != null) {
                continue;
            }
            final List<Pattern> patterns = schemaPatterns(schema, entry.path());
            if (patterns.isEmpty()) {
                continue;
            }
            final List<String> values = new ArrayList<>();
            for (final String value : fieldValues.of(entry)) {
                if (value != null && !PyText.isBlank(value) && !nil.contains(PyText.strip(value))) {
                    values.add(value);
                }
            }
            final Set<String> bad = new LinkedHashSet<>();
            for (final String value : values) {
                if (patterns.stream().noneMatch(p -> p.matcher(PyText.strip(value)).matches())) {
                    bad.add(value);
                }
            }
            if (!bad.isEmpty()) {
                final long count = values.stream().filter(bad::contains).count();
                final String caseTransform = caseTransform(bad, patterns);
                final String said = entry.path() + " takes values of a set form (the schema's pattern), but field '"
                                    + entry.field() + "' has " + PyText.repr(bad.stream().limit(3).toList())
                                    + " in " + count + " of the " + values.size() + " sample records: Stroom's "
                                    + "schema check refuses those events. " + (caseTransform != null
                        ? "They fit once " + caseTransform + "-cased: give the entry transform: " + caseTransform
                        : "Map only the values that fit (an extract whose regex matches only them gives an empty "
                          + "field otherwise), or map the field elsewhere (a name to a HostName, say)");
                if (!out.contains(said)) {
                    out.add(said);
                }
            }
        }
        return out;
    }

    // "lower" or "upper" if every value fits one of the patterns once so cased, else null.
    private static String caseTransform(final Set<String> values, final List<Pattern> patterns) {
        for (final String transform : List.of("lower", "upper")) {
            final boolean fits = values.stream()
                    .map(value -> "lower".equals(transform)
                            ? PyText.strip(value).toLowerCase(Locale.ROOT)
                            : PyText.strip(value).toUpperCase(Locale.ROOT))
                    .allMatch(value -> patterns.stream().anyMatch(p -> p.matcher(value).matches()));
            if (fits) {
                return transform;
            }
        }
        return null;
    }

    private static List<Pattern> schemaPatterns(final EventSchema schema, final String path) {
        final List<EventSchema.Child> chain;
        try {
            chain = schema.resolve(path);
        } catch (final RuntimeException e) {
            return List.of();
        }
        final List<Pattern> patterns = new ArrayList<>();
        if (!chain.isEmpty()) {
            for (final String pattern : schema.patterns(chain.getLast().decl())) {
                // An XSD-only construct: not checked here.
                final Pattern compiled = compiled(pattern, 0);
                if (compiled != null) {
                    patterns.add(compiled);
                }
            }
        }
        return patterns;
    }

    private static List<Object> forEachItems(final TranslationMapping mapping, final List<Object> records) {
        final List<Object> items = new ArrayList<>();
        if (hasText(mapping.forEach())) {
            records.forEach(record -> items.addAll(itemsOf(record, mapping.forEach())));
        }
        return items;
    }

    /// Fields the mapping names that no sample record has (with close matches), and time formats the sample's
    /// values do not fit. Fields an extraction produces are known, not looked for.
    ///
    /// @param mapping The mapping.
    /// @param records The sample's records ([#sampleRecords(TranslationMapping, Object, SplitterSpec)]).
    /// @return `records`, `problems`, `warnings`, `fields_seen`, and `not_checked` and `kept_unknown` when there
    /// are any.
    /// @throws stroom.ai.impl.workspace.PatternTimeoutException if the mapping's regexes (its extractions' and
    ///                                                          conditions') take too long to match.
    public static Map<String, Object> checkMapping(final TranslationMapping mapping, final List<Object> records) {
        return SafePattern.limited(null, () -> new MappingCheck(mapping, records).check());
    }

    /// @param text A text, or null.
    /// @return True if it is neither null nor empty (Python's truthiness of a string).
    static boolean hasText(final String text) {
        return text != null && !text.isEmpty();
    }


    // --------------------------------------------------------------------------------


    /// The sample's records as a mapping reads them.
    ///
    /// @param records The records: maps for data_splitter and json inputs, [XmlTree.Element]s for xml inputs.
    /// @param note    Why nothing could be read, or null.
    public record SampleRecords(List<Object> records, String note) {

    }

    /// What [#unknownCoverage(TranslationMapping, List, List)] found.
    ///
    /// @param problems The rules writing Unknown that must not.
    /// @param kept     The rules kept as Unknown, for the user to confirm.
    public record Coverage(List<String> problems, List<Map<String, Object>> kept) {

    }

    // A field the mapping reads, with the scope it reads it in.
    // Each record's value of an entry's field (or each item's, for an entry for_each repeats): the record's own,
    // or what the mapping's extractions give it, worked out once for all the entries.
    private static final class FieldValues {

        private final TranslationMapping mapping;
        private final List<Object> records;
        private final List<Object> items;
        private final Set<String> derived;
        private final Map<List<Object>, List<Map<String, String>>> extracted = new IdentityHashMap<>();

        private FieldValues(final TranslationMapping mapping, final List<Object> records) {
            this.mapping = mapping;
            this.records = records;
            this.items = forEachItems(mapping, records);
            this.derived = derived(mapping);
        }

        // Null where a record has no value, or its extractions can't be run here.
        private List<String> of(final FieldMapping entry) {
            final List<Object> pool = hasText(mapping.forEach()) && !"record".equals(entry.scope())
                    ? items
                    : records;
            final String field = entry.field();
            if (!derived.contains(field)) {
                return pool.stream().map(record -> valueOf(record, field)).toList();
            }
            final List<Map<String, String>> given = extracted.computeIfAbsent(pool, of -> of.stream()
                    .map(record -> extractedValues(mapping, record))
                    .toList());
            final List<String> out = new ArrayList<>();
            given.forEach(values -> out.add(values == null
                    ? null
                    : values.get(field)));
            return out;
        }
    }

    /// A field mapped as it is into users' Ids, whose sample values are domain-qualified.
    ///
    /// @param field    The field.
    /// @param paths    The Id paths it is mapped into as it is, e.g. `EventSource/User/Id`.
    /// @param examples Up to three of its domain-qualified values.
    public record QualifiedIds(String field, List<String> paths, List<String> examples) {

    }

    private record Wanted(String name, String scope) {

    }

    // One mapping checked against its sample's records.
    private static final class MappingCheck {

        private final TranslationMapping mapping;
        private final List<Object> records;
        private final List<String> problems = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> notChecked = new ArrayList<>();
        private final Set<String> derived;
        private final List<Object> items;
        private final boolean xml;

        /// @param mapping The mapping.
        /// @param records The sample's records.
        MappingCheck(final TranslationMapping mapping, final List<Object> records) {
            this.mapping = mapping;
            this.records = records;
            this.derived = derived(mapping);
            this.items = forEachItems(mapping, records);
            this.xml = records.stream().noneMatch(Map.class::isInstance);
        }

        private List<Object> where(final String scope) {
            return hasText(mapping.forEach()) && !"record".equals(scope)
                    ? items
                    : records;
        }

        /// @return What [LocalCheck#checkMapping(TranslationMapping, List)] returns.
        Map<String, Object> check() {
            if (records.isEmpty()) {
                return dict("records", 0, "problems", problems, "warnings", warnings, "fields_seen", List.of());
            }
            final List<String> seen = seenFields();
            final Map<Wanted, List<String>> wanted = wanted();
            final Set<String> suggested = lookedUp(wanted, seen);
            checkTimeFormats();
            unread(wanted, seen, suggested);
            // Once a record, not once a check: seen, 60 extract entries over 8,600 records took 9 s, three times
            // over.
            List<Map<String, String>> extracted = null;
            if (!mapping.extract().isEmpty()) {
                extracted = new ArrayList<>();
                for (final Object record : records) {
                    extracted.add(extractedValues(mapping, record));
                }
            }
            problems.addAll(sharedTypeIds(mapping, records, extracted));
            final Coverage coverage = unknownCoverage(mapping, records, extracted);
            problems.addAll(coverage.problems());
            if (!hasText(mapping.forEach())) {
                untold(extracted);
            }
            final Map<String, Object> out = dict("records", records.size(), "problems", problems, "warnings",
                    warnings, "fields_seen", seen.subList(0, Math.min(60, seen.size())));
            if (!notChecked.isEmpty()) {
                out.put("not_checked", notChecked);
            }
            if (!coverage.kept().isEmpty()) {
                out.put("kept_unknown", coverage.kept());
            }
            return out;
        }

        private List<String> seenFields() {
            final Set<String> seen = new LinkedHashSet<>();
            for (final Object record : records) {
                // An XML record's paths too: seen, fields_seen [] and no word on unread fields for a whole XML
                // sample.
                if (record instanceof final Map<?, ?> map) {
                    map.keySet().forEach(k -> seen.add((String) k));
                } else if (record instanceof final XmlTree.Element element) {
                    seen.addAll(xmlFlat(element).keySet());
                }
            }
            if (hasText(mapping.forEach()) && items.isEmpty()) {
                warnings.add("for_each '" + mapping.forEach() + "' selects no items in any of the " + records.size()
                             + " sample records");
            }
            for (final Object item : items) {
                if (item instanceof final Map<?, ?> map) {
                    map.keySet().forEach(k -> seen.add((String) k));
                }
            }
            return new ArrayList<>(seen);
        }

        private Map<Wanted, List<String>> wanted() {
            final Map<Wanted, List<String>> wanted = new LinkedHashMap<>();
            for (final FieldMapping entry : mapping.allEntries()) {
                final List<String> names = new ArrayList<>();
                if (hasText(entry.field())) {
                    names.add(entry.field());
                }
                if (entry.anyOf() != null) {
                    names.addAll(entry.anyOf());
                }
                if (entry.join() != null) {
                    names.addAll(entry.join());
                }
                if (entry.lookup() != null && hasText(entry.lookup().field())) {
                    names.add(entry.lookup().field());
                }
                names.forEach(name -> want(wanted, name, entry.scope(), entry.path()));
            }
            for (final EventRule rule : mapping.events()) {
                rule.when().stream().filter(c -> hasText(c.field()))
                        .forEach(c -> want(wanted, c.field(), c.scope(), "[" + rule.name() + "] when"));
            }
            for (final DropRule drop : mapping.dropWhen()) {
                drop.when().stream().filter(c -> hasText(c.field()))
                        .forEach(c -> want(wanted, c.field(), c.scope(), "drop: " + drop.reason()));
            }
            mapping.extract().stream().filter(ex -> hasText(ex.field()))
                    .forEach(ex -> want(wanted, ex.field(), ex.scope(), "extract"));
            return wanted;
        }

        private static void want(final Map<Wanted, List<String>> wanted, final String name, final String scope,
                                 final String use) {
            wanted.computeIfAbsent(new Wanted(name, scope), k -> new ArrayList<>()).add(use);
        }

        // The fields looked up in the sample; those it doesn't have are problems (near misses) or warnings.
        // Returns the fields offered as 'did you mean': not said again as unread.
        private Set<String> lookedUp(final Map<Wanted, List<String>> wanted, final List<String> seen) {
            final Set<String> suggested = new HashSet<>();
            final List<String> unreadPaths = new ArrayList<>();
            wanted.forEach((key, used) -> {
                final String name = key.name();
                if (derived.contains(name)) {
                    return;
                }
                if (xml && !segments(name).stream().filter(s -> !".".equals(s))
                        .allMatch(s -> STEP.matcher(s).matches())) {
                    // A path xml_value can't follow (*:Name, a function): not looked up here.
                    unreadPaths.add(name);
                    return;
                }
                final List<Object> pool = where(key.scope());
                if (pool.stream().anyMatch(r -> valueOf(r, name) != null)) {
                    return;
                }
                final List<String> close = seen.isEmpty()
                        ? List.of()
                        : CloseMatches.closeMatches(name, seen, 3, 0.6);
                suggested.addAll(close);
                final String what = pool == items && hasText(mapping.forEach())
                        ? "sample items"
                        : "sample records";
                final String message = "field '" + name + "' (used for " + used.getFirst() + (used.size() > 1
                        ? " and more"
                        : "") + ") is in none of the " + pool.size() + " " + what
                                       + (close.isEmpty()
                        ? ""
                        : "; did you mean " + PyText.repr(close) + "?")
                                       + (!seen.isEmpty() && close.isEmpty()
                        ? ". Fields seen: " + PyText.repr(seen.subList(0, Math.min(30, seen.size())))
                        : "");
                // A near miss of a field the sample has is a slip (srcip for src_ip, or names recalled from
                // another source's logs), and its element would be empty in every event; with nothing close, the
                // field may be one other data carries.
                if (close.isEmpty()) {
                    warnings.add(message);
                } else {
                    problems.add(message + " Use the sample's field names; a field only other data carries needs "
                                 + "a sample that has it.");
                }
            });
            if (!unreadPaths.isEmpty()) {
                notChecked.add("fields " + PyText.repr(unreadPaths.subList(0, Math.min(5, unreadPaths.size())))
                               + " were not looked up in the sample (only plain paths are here)");
            }
            return suggested;
        }

        private void checkTimeFormats() {
            for (final FieldMapping entry : mapping.allEntries()) {
                if (!hasText(entry.timeFormat()) || !hasText(entry.field()) || derived.contains(entry.field())) {
                    continue;
                }
                final List<String> values = new ArrayList<>();
                for (final Object record : where(entry.scope())) {
                    final String value = valueOf(record, entry.field());
                    if (value != null && !value.isEmpty()) {
                        values.add(value);
                    }
                }
                if (values.isEmpty()) {
                    continue;
                }
                final String message = TimePatterns.checkTimeFormat(entry.timeFormat(), values);
                if (message != null) {
                    final long misfits = values.stream()
                            .filter(v -> TimePatterns.checkTimeFormat(entry.timeFormat(), List.of(v)) != null)
                            .count();
                    (values.size() - misfits == 0
                            ? problems
                            : warnings).add(entry.path() + ": " + message);
                }
            }
        }

        private void unread(final Map<Wanted, List<String>> wanted, final List<String> seen,
                            final Set<String> suggested) {
            final Set<String> readWords = new HashSet<>();
            if (xml) {
                // XML is often read through xpaths (.//*:EventData/*:Data): a path counts as read when the mapping
                // names every step of it somewhere.
                final List<String> texts = new ArrayList<>(MappingFacts.mappingXpaths(mapping));
                wanted.keySet().forEach(key -> texts.add(key.name()));
                texts.forEach(text -> readWords.addAll(PyText.findall(WORD, text)));
            }
            final List<String> unread = seen.stream()
                    .filter(k -> !derived.contains(k) && !suggested.contains(k) && !MappingFacts.readsInput(mapping, k))
                    .filter(k -> !(xml && readWords.containsAll(PyText.findall(WORD, k))))
                    .toList();
            if (!unread.isEmpty()) {
                // A parsed field no output reads is lost from every event: often one the request asked for (a
                // source address).
                warnings.add("fields in the sample that nothing reads: "
                             + PyText.repr(unread.subList(0, Math.min(12, unread.size())))
                             + (unread.size() > 12
                        ? " ..."
                        : "")
                             + ": map each to the element that means it (a Data entry if nothing else fits), or "
                             + "leave it out on purpose.");
            }
        }

        private void untold(final List<Map<String, String>> extracted) {
            int untold = 0;
            for (int n = 0; n < records.size(); n++) {
                if (ruleOf(mapping, records.get(n), LocalCheck.extracted(mapping, records, extracted, n)) == null) {
                    untold++;
                }
            }
            if (untold > 0) {
                // Said, not passed over in silence: seen, a clean check of 1000 XML records whose every rule
                // tested a field extracted from an xpath, so no record was checked against its rule.
                notChecked.add("the rule " + untold + " of the " + records.size() + " sample records fall to can't "
                               + "be told here (a condition on an xpath, or on a field extracted from one): Unknown "
                               + "and TypeId were not checked for them; step_sample shows what they become");
            }
        }
    }
}
