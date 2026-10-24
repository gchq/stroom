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
import stroom.ai.impl.tools.generation.FieldHomes;
import stroom.ai.impl.tools.generation.Pairs;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.SampleTexts;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.TimePatterns;
import stroom.ai.impl.tools.sample.XmlTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A starting translation mapping, drafted from the sample (the Stroom MCP server's `utils/draftmap.py`).
///
/// A model asked to write a mapping from nothing tends to send back the field inventory instead. The draft gives it
/// a valid mapping to edit: the input kind from the profile, the obvious event-logging homes for fields by name
/// (time, host, addresses, ports, user, event type, message: [FieldHomes]), one rule per kind of event the naming
/// field shows, a text field's key/value pairs read through extract as fields of their own, and every other field
/// carried as Data, with notes on what a person or model must still decide: the action element for each kind of
/// event, the system name and environment, a time zone for naive timestamps. The result is checked like any
/// mapping, so a wrong guess shows up as a problem, not as a silent mistake.
///
/// The draft is the mapping's JSON (maps and lists, as [stroom.ai.impl.tools.generation.TranslationMapping#parse]
/// reads it), so it can be edited before it is validated.
public final class DraftMapping {

    private static final Pattern TIME = ignoringCase("^(@?timestamp|time|ts|date_?time|datetime|event_?time|"
                                                     + "eventtime|created|created_?at|logged_?at|time_?created|"
                                                     + "occurred|when|_time|rt|system_?time)$");
    private static final Pattern NAMING = ignoringCase("^(event_?type|eventtype|type|event|event_?name|event_?id|"
                                                       + "eventid|action|activity|category|subtype|sub_?type|"
                                                       + "operation|op|logid|log_?type|msg_?id|kind|result_?type|"
                                                       + "act|device_?action)$");
    private static final Pattern AUTH = ignoringCase(
            "(login|logon|signin|sign_in|authenticat|logoff|logout|signout|sign_out|password)");
    private static final Pattern LOGOFF = ignoringCase("(logoff|logout|signout|sign_out)");
    private static final Pattern COMMAND = ignoringCase(
            "^(command|cmd|cmd_?line|command_?line|process_?command)$");
    private static final int MAX_RULES = 12;
    // Values that say a field has nothing (syslog's NILVALUE).
    private static final Set<String> NIL = Set.of("-", "", "null", "none", "n/a");
    // Windows security log event IDs that are logons and logoffs (seen: 4624 and 4634 drafted as Unknown).
    private static final Map<String, WindowsEvent> WINDOWS_LOGON = Map.of(
            "4624", new WindowsEvent("Logon", true), "4625", new WindowsEvent("Logon", false),
            "4648", new WindowsEvent("Logon", true), "4634", new WindowsEvent("Logoff", null),
            "4647", new WindowsEvent("Logoff", null), "4778", new WindowsEvent("Logon", true),
            "4779", new WindowsEvent("Logoff", null));
    private static final Set<String> EVENT_ID_KEYS = Set.of("eventid", "event_id", "eventcode", "event_code");
    // Key/value pairs in a text field, as survey_feed signs them (kv:): the ways a text writes them are Pairs'.
    // Seen in production: the agent writing 33 extractions by hand after the draft carried such a text whole.
    private static final Pattern COLON_KEY = PyText.re(Pairs.BOUND + "(" + Pairs.WORDS + "): (\\[)?");
    private static final Pattern EQ_KEY = PyText.re("(?:^|[\\s,;])([A-Za-z_][A-Za-z0-9_.-]*)=(\")?");
    private static final Pattern SOURCE_SIDE = ignoringCase("^(src|source|client|orig|origin)(_|$)");
    private static final Pattern DESTINATION_SIDE = ignoringCase(
            "^(dst|dest|destination|server|target|resp|responder)(_|$)");
    private static final Pattern KEY_SPLIT = Pattern.compile("[./]");
    private static final Pattern CLOCK = PyText.re("\\d{1,2}:\\d{2}(:\\d{2})?(\\.\\d+)?");
    private static final Pattern ZONE_NAME = PyText.re("[A-Z][a-z]+/[A-Za-z_]+(?:/[A-Za-z_]+)?");
    private static final Pattern LETTER = PyText.re("[A-Za-z]");
    private static final Pattern NOT_NAME = PyText.re("[^A-Za-z0-9]+");
    private static final String TIME_ZONE_PATH = "EventSource/Device/Location/TimeZoneName";
    private static final String TIMESTAMP = "timestamp";
    private static final String TIMESTAMP_PREFIX = "timestamp (";
    private static final String DATA_SPLITTER = "data_splitter";
    private static final String JSON_LINES = SampleProfiler.JSON_LINES;
    private static final String JSON_ARRAY = SampleProfiler.JSON_ARRAY;
    private static final String PATH = "path";
    private static final String FIELD = "field";
    private static final String DESCRIPTION = "EventDetail/Description";
    // Names of free text a person writes about an event, e.g. a ticket's comment.
    private static final Pattern FREE_TEXT = Pattern.compile("(?i)(comments?|remarks?|notes?)");
    private static final String FIELDS = "fields";
    private static final String VALUE = "value";
    private static final String ANY_OF = "any_of";
    private static final String NAMES = "names";
    private static final String EXTRACT = "extract";
    private static final String DATA_NAME = "data_name";
    private static final String EVENTS = "events";

    private DraftMapping() {
        // Static only.
    }

    private static Map<String, Object> dict(final Object... keysAndValues) {
        return JsonValues.dict(keysAndValues);
    }

    private static Pattern ignoringCase(final String regex) {
        return PyText.ignoringCase(regex);
    }

    /// A valid mapping to edit, and what is left to decide.
    ///
    /// @param samples     The samples: a map by file name, a list of texts or `{name: text}` objects, or one text
    ///                    ([SampleTexts#asNamedSamples(Object, String)]).
    /// @param sourceName  The source's name ("" for none): the system name and generator when none is given.
    /// @param systemName  EventSource/System/Name, or null to leave it for the user to confirm.
    /// @param environment EventSource/System/Environment, or null to leave it for the user to confirm.
    /// @param sourceNotes The source notes from the user's documentation ([SourceNotes#readNotes(String)]), or
    ///                    null.
    /// @param detailCheck Why a catalogued event's `event_detail` can't be an action element
    ///                    ([SourceNotes#detailProblem]), or null.
    /// @param splitter    The Data Splitter the text is read with, when the agent settled on one (named columns
    ///                    for a file with no header, say); null to infer it.
    /// @return `mapping` (the draft), `source_notes` (what the notes changed, or null), `splitter` (the spec, or
    /// null), `format`, `records`, `kinds`, `notes`, `unmapped_fields` and `fields_seen`.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static Map<String, Object> draft(final Object samples,
                                            final String sourceName,
                                            final String systemName,
                                            final String environment,
                                            final Map<String, Object> sourceNotes,
                                            final Function<String, String> detailCheck,
                                            final SplitterSpec splitter) {
        final Map<String, String> named = SampleTexts.asNamedSamples(samples, null);
        return new Drafting(records(named, splitter), sourceName == null
                ? ""
                : sourceName).draft(systemName, environment, sourceNotes, detailCheck);
    }

    /// The sample's records as the draft reads them.
    ///
    /// @param named The samples by name.
    /// @param given The Data Splitter spec to read text with, or null to infer one.
    /// @return The profile (merged across files), the records as flat name to value maps, and the splitter spec
    /// for text.
    public static Records records(final Map<String, String> named, final SplitterSpec given) {
        if (named.isEmpty()) {
            throw new IllegalArgumentException("no samples");
        }
        final Map<String, Object> info = named.size() > 1
                ? SampleProfiler.profileMany(named, SampleProfiler.MAX_RECORDS)
                : SampleProfiler.profile(named.values().iterator().next());
        final String fmt = (String) info.get("format");
        final List<Map<String, Object>> records = new ArrayList<>();
        SplitterSpec spec = given;
        for (final String text : named.values()) {
            switch (fmt) {
                case SampleProfiler.JSON_OBJECT -> {
                    // The wrapper's array holds the records: the draft's fields are the items' (for_each reads
                    // them).
                    final Map.Entry<String, List<Object>> found = SampleProfiler.wrappedRecords(PyText.strip(text));
                    if (found != null) {
                        found.getValue().forEach(r -> records.add(asText(SampleProfiler.flatten(r), "")));
                    }
                }
                case JSON_ARRAY, JSON_LINES -> {
                    final boolean badLines = JSON_LINES.equals(fmt) && JsonValues.truthy(info.get("malformed_lines"));
                    // With lines that aren't JSON, the lines are read by a Data Splitter and their keys through
                    // json_fields: json.<key>.
                    final String prefix = badLines
                            ? "json."
                            : "";
                    for (final Object value : jsonValues(PyText.strip(text))) {
                        if (value instanceof Map) {
                            records.add(asText(SampleProfiler.flatten(value), prefix));
                        }
                    }
                    if (badLines && spec == null) {
                        spec = SplitterSpec.builder("regex").pattern("^(.*)$").names(List.of("json")).build();
                    }
                }
                case SampleProfiler.XML, SampleProfiler.XML_FRAGMENTS -> xmlElements(fmt, text)
                        .forEach(element -> records.add(new LinkedHashMap<>(LocalCheck.xmlFlat(element))));
                default -> {
                    if (spec == null) {
                        spec = SplitterDryRun.inferSpec(text).spec();
                    }
                    if (spec != null) {
                        SplitterDryRun.dryRun(spec, text).records().forEach(r -> records.add(new LinkedHashMap<>(r)));
                    }
                }
            }
        }
        return new Records(info, records, spec);
    }

    private static Map<String, Object> asText(final Map<String, Object> flat, final String prefix) {
        final Map<String, Object> out = new LinkedHashMap<>();
        flat.forEach((key, value) -> out.put(prefix + key, JsonValues.jsonText(value)));
        return out;
    }

    private static List<Object> jsonValues(final String stripped) {
        if (stripped.startsWith("[")) {
            return PyJson.loads(stripped) instanceof final List<?> list
                    ? new ArrayList<>(list)
                    : List.of();
        }
        final List<Object> values = new ArrayList<>();
        for (final String line : PyText.splitlines(stripped)) {
            final Object value = jsonOrNull(line);
            if (JsonValues.truthy(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private static Object jsonOrNull(final String line) {
        if (PyText.isBlank(line)) {
            return null;
        }
        try {
            return PyJson.loads(line);
        } catch (final PyJson.JsonDecodeException e) {
            return null;
        }
    }

    private static List<XmlTree.Element> xmlElements(final String fmt, final String text) {
        if (SampleProfiler.XML_FRAGMENTS.equals(fmt)) {
            final List<XmlTree.Element> fragments = SampleProfiler.xmlFragments(text);
            return fragments == null
                    ? List.of()
                    : fragments;
        }
        try {
            return XmlTree.parse(PyText.strip(text)).elements();
        } catch (final XmlTree.XmlException e) {
            // A big document read only to here: its complete records.
            final XmlTree.Pull cut = SampleProfiler.cutDocument(text);
            return cut == null
                    ? List.of()
                    : cut.records();
        }
    }

    /// Fields whose values hold key/value pairs (two or more in at least half of them), each with an extract entry
    /// a key, in order of first appearance. Keys seen once in five values or more are left out: in free text they
    /// are words before a colon, not keys. Each name is the key in snake case, unique among the fields.
    ///
    /// @param values Each field's values.
    /// @param skip   Fields not to read (JSON or XML held in text).
    /// @return For each such field, its extract entries and whether every value had pairs.
    public static Map<String, PairExtracts> pairExtracts(final Map<String, List<String>> values,
                                                         final Set<String> skip) {
        final Map<String, PairExtracts> out = new LinkedHashMap<>();
        final Set<String> used = new HashSet<>(values.keySet());
        for (final Map.Entry<String, List<String>> entry : values.entrySet()) {
            final String field = entry.getKey();
            final List<String> vals = entry.getValue();
            if (skip.contains(field) || vals.isEmpty()) {
                continue;
            }
            final List<Map<String, String>> found = new ArrayList<>();
            vals.forEach(v -> found.add(pairsIn(v)));
            if (found.stream().filter(f -> f.size() >= 2).count() * 2 < vals.size()) {
                continue;
            }
            final Map<String, Map<String, Integer>> styles = new LinkedHashMap<>();
            // Each key counted once a value.
            found.forEach(pairs -> pairs.forEach((key, style) ->
                    styles.computeIfAbsent(key, k -> new LinkedHashMap<>()).merge(style, 1, Integer::sum)));
            final List<Map<String, Object>> extracts = new ArrayList<>();
            styles.entrySet().stream().limit(60).forEach(style -> {
                final int counted = style.getValue().values().stream().mapToInt(Integer::intValue).sum();
                if (counted < 2 && vals.size() >= 5) {
                    return;
                }
                final String base = Pairs.keyName(style.getKey());
                String name = base;
                int n = 2;
                while (used.contains(name)) {
                    name = base + "_" + n;
                    n++;
                }
                used.add(name);
                final String mostCommon = JsonValues.mostCommon(style.getValue()).getFirst();
                extracts.add(dict(FIELD, field, "regex", Pairs.pairRegex(mostCommon, style.getKey()), NAMES,
                        new ArrayList<>(List.of(name))));
            });
            if (!extracts.isEmpty()) {
                out.put(field, new PairExtracts(extracts, found.stream().noneMatch(Map::isEmpty)));
            }
        }
        return out;
    }

    // The keys in a text, each with its style (Pairs.PAIR_REGEX); a key written in two styles has the last.
    private static Map<String, String> pairsIn(final String text) {
        final Map<String, String> found = new LinkedHashMap<>();
        final Matcher colon = COLON_KEY.matcher(text);
        while (colon.find()) {
            found.put(colon.group(1), colon.group(2) != null
                    ? "bracketed"
                    : "colon");
        }
        final Matcher equals = EQ_KEY.matcher(text);
        while (equals.find()) {
            found.put(equals.group(1), equals.group(2) != null
                    ? "quoted"
                    : "equals");
        }
        return found;
    }

    /// Each record given the values the extracts find in it, as the XSLT's analyze-string does (the first match).
    ///
    /// @param records  The records (changed in place).
    /// @param extracts Extract entries (`field`, `regex`, `names`).
    @SuppressWarnings("unchecked")
    public static void withPairs(final List<Map<String, Object>> records, final List<Map<String, Object>> extracts) {
        final List<Pattern> patterns = new ArrayList<>();
        extracts.forEach(ex -> patterns.add(PyText.re((String) ex.get("regex"))));
        for (final Map<String, Object> record : records) {
            for (int i = 0; i < extracts.size(); i++) {
                final Map<String, Object> ex = extracts.get(i);
                final Matcher found = patterns.get(i).matcher(JsonValues.text(record.get((String) ex.get(FIELD))));
                if (found.find() && found.group(1) != null && !PyText.isBlank(found.group(1))) {
                    record.put(((List<String>) ex.get(NAMES)).getFirst(), found.group(1));
                }
            }
        }
    }

    // A field's own name: the last part of a JSON key or XML path, without an attribute's @ (seen: an XML source's
    // action="login" attribute not taken for the kind of event).
    private static String key(final String name) {
        final String[] parts = KEY_SPLIT.split(name, -1);
        String last = parts[parts.length - 1];
        while (last.startsWith("@")) {
            last = last.substring(1);
        }
        return last.toLowerCase(Locale.ROOT);
    }

    // The XPath for an input field, as the generator writes it for the mapping's input kind.
    private static String selector(final Map<String, Object> mapping, final String field) {
        if (DATA_SPLITTER.equals(mapping.get("input"))) {
            final List<String> steps = new ArrayList<>();
            for (final String part : field.split("/", -1)) {
                steps.add("data[@name='" + part + "']");
            }
            return String.join("/", steps) + "/@value";
        }
        if ("json".equals(mapping.get("input"))) {
            final List<String> steps = new ArrayList<>();
            for (final String part : field.split("\\.", -1)) {
                steps.add("*[@key='" + part + "']");
            }
            return String.join("/", steps);
        }
        return field;
    }

    // Where a field with no element of its own goes as Data: on a network action, the side it names (a
    // destination_key under Destination, a source_zone under Source); otherwise the action element itself.
    private static String dataHome(final String element, final String field) {
        if (element.startsWith("EventDetail/Network/")) {
            final String name = PyText.lastPart(field, '.');
            if (PyText.match(SOURCE_SIDE, name)) {
                return element + "/Source";
            }
            if (PyText.match(DESTINATION_SIDE, name)) {
                return element + "/Destination";
            }
        }
        return element;
    }

    private static boolean jsonObject(final String text) {
        try {
            return PyJson.loads(text) instanceof Map;
        } catch (final PyJson.JsonDecodeException e) {
            return false;
        }
    }

    // A field value that is an XML element, as text (a SOAP payload held in a JSON string).
    private static boolean xmlElement(final String value) {
        if (!PyText.strip(value).startsWith("<")) {
            return false;
        }
        try {
            XmlTree.parse(PyText.strip(value));
            return true;
        } catch (final XmlTree.XmlException e) {
            return false;
        }
    }

    private static String reprs(final List<String> names) {
        return String.join(", ", names.stream().map(PyText::repr).toList());
    }


    // --------------------------------------------------------------------------------


    /// The sample as the draft reads it.
    ///
    /// @param info    The profile (merged across files).
    /// @param records The records, as flat name to value maps (the values text).
    /// @param spec    The splitter spec text is read with, or null.
    public record Records(Map<String, Object> info, List<Map<String, Object>> records, SplitterSpec spec) {

    }

    /// A text field's key/value pairs, as extract entries.
    ///
    /// @param extracts The extract entries, one a key (`field`, `regex`, `names`).
    /// @param whole    True if every value of the field had pairs (the text itself needn't be mapped).
    public record PairExtracts(List<Map<String, Object>> extracts, boolean whole) {

    }

    // A Windows logon event: the Authenticate action, and whether it succeeded (null when it says nothing).
    private record WindowsEvent(String action, Boolean success) {

    }

    // One draft being made: the sample's records and what has been decided so far.
    private static final class Drafting {

        private final Map<String, Object> info;
        private final List<Map<String, Object>> records;
        private final SplitterSpec spec;
        private final String sourceName;
        private final String fmt;
        private final List<String> notes = new ArrayList<>();
        private final Map<String, Object> mapping = new LinkedHashMap<>();
        private final List<Map<String, Object>> common = new ArrayList<>();
        private final Set<String> taken = new HashSet<>();
        private List<String> names = new ArrayList<>();
        private Map<String, List<String>> values = new LinkedHashMap<>();
        private Map<String, String> types = new LinkedHashMap<>();

        /// @param read       The sample as the draft reads it.
        /// @param sourceName The source's name.
        Drafting(final Records read, final String sourceName) {
            this.info = read.info();
            this.records = read.records();
            this.spec = read.spec();
            this.sourceName = sourceName;
            this.fmt = (String) info.get("format");
        }

        /// @param systemName  EventSource/System/Name, or null.
        /// @param environment EventSource/System/Environment, or null.
        /// @param sourceNotes The source notes, or null.
        /// @param detailCheck Why an event_detail can't be an action element, or null.
        /// @return What [DraftMapping#draft] returns.
        Map<String, Object> draft(final String systemName,
                                  final String environment,
                                  final Map<String, Object> sourceNotes,
                                  final Function<String, String> detailCheck) {
            input();
            final Set<Set<String>> shapes = new HashSet<>();
            for (final Map<String, Object> record : records) {
                final Set<String> shape = new HashSet<>();
                record.forEach((k, v) -> {
                    if (v != null && !"".equals(v)) {
                        shape.add(k);
                    }
                });
                shapes.add(shape);
            }
            survey();
            List<String> heldFields = heldJson();
            heldFields = heldXml(heldFields);
            heldFields = heldPairs(heldFields);
            taken.addAll(heldFields);
            final String timeField = time();
            system(systemName, environment, timeField);
            homes();
            final Kinds kinds = kinds();
            final String naming = kinds.namings().isEmpty()
                    ? null
                    : kinds.namings().getFirst();
            final String command = commandOrOther(kinds, naming);
            shapesNote(shapes.size(), kinds.namings());
            kindsNote(kinds, naming, command);
            mapping.put("common", common);
            mapping.put(EVENTS, kinds.rules());
            List<String> rest = kinds.rest();
            Map<String, Object> applied = null;
            if (sourceNotes != null && !sourceNotes.isEmpty()
                && (JsonValues.truthy(sourceNotes.get(FIELDS)) || JsonValues.truthy(sourceNotes.get(EVENTS)))) {
                // The user's documentation says where fields belong and what each event is: it wins over guesses
                // from names.
                applied = SourceNotes.applyToDraft(mapping, names, values, sourceNotes, detailCheck);
                rest = withNotes(applied, rest);
            }
            nilValues();
            doubledQuotes();
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("mapping", mapping);
            result.put("source_notes", applied);
            result.put("splitter", spec == null
                    ? null
                    : spec.toMap());
            result.put("format", fmt);
            result.put("records", records.size());
            result.put("kinds", kinds.kinds());
            result.put("notes", notes);
            result.put("unmapped_fields", rest);
            result.put("fields_seen", names);
            return result;
        }

        private void input() {
            if (JSON_LINES.equals(fmt) && JsonValues.truthy(info.get("malformed_lines"))) {
                // Some lines aren't JSON: Stroom's JSONParser would stop at the first, so a Data Splitter reads one
                // record a line and the mapping parses each (a line that isn't JSON gives no fields and is logged).
                mapping.put("input", DATA_SPLITTER);
                mapping.put("json_fields", new ArrayList<>(List.of("json")));
                notes.add(JsonValues.truthy(info.get("note"))
                        ? (String) info.get("note")
                        : "");
            } else if (JSON_ARRAY.equals(fmt) || JSON_LINES.equals(fmt)) {
                mapping.put("input", "json");
                mapping.put("json_layout", JSON_LINES.equals(fmt)
                        ? "lines"
                        : "array");
            } else if (SampleProfiler.JSON_OBJECT.equals(fmt)) {
                mapping.put("input", "json");
                mapping.put("json_layout", "lines");
                mapping.put("for_each", info.get("records_key"));
            } else if (SampleProfiler.XML.equals(fmt)) {
                mapping.put("input", "xml");
                mapping.put("root", info.get("root"));
                mapping.put("record", info.get("record_element"));
                mapping.put("xml_namespace", JsonValues.truthy(info.get("namespace"))
                        ? info.get("namespace")
                        : "");
            } else if (SampleProfiler.XML_FRAGMENTS.equals(fmt)) {
                mapping.put("input", "xml_fragments");
                mapping.put("record", info.get("record_element"));
                mapping.put("xml_namespace", JsonValues.truthy(info.get("namespace"))
                        ? info.get("namespace")
                        : "records:2");
            } else {
                mapping.put("input", DATA_SPLITTER);
                if (spec == null) {
                    notes.add("The text's format could not be inferred (" + fmt + "): build_data_splitter with a "
                              + "regex spec first, then name its fields here.");
                }
            }
        }

        // The fields the records hold, in the order first seen, their values and the commonest type of each.
        private void survey() {
            final Set<String> seen = new LinkedHashSet<>();
            records.forEach(record -> seen.addAll(record.keySet()));
            names = new ArrayList<>(seen);
            values = new LinkedHashMap<>();
            types = new LinkedHashMap<>();
            for (final String name : names) {
                final List<String> held = new ArrayList<>();
                for (final Map<String, Object> record : records) {
                    final Object value = record.get(name);
                    if (value != null && !"".equals(value)) {
                        held.add((String) value);
                    }
                }
                values.put(name, held);
                final Map<String, Integer> counted = new LinkedHashMap<>();
                held.forEach(v -> counted.merge(SampleProfiler.valueType(v), 1, Integer::sum));
                types.put(name, held.isEmpty()
                        ? "empty"
                        : JsonValues.mostCommon(counted).getFirst());
            }
        }

        // Fields holding JSON text: their keys are fields of their own (<field>.<key>, read through json_fields),
        // and the text itself is never mapped into an element. Seen: a whole JSON line written into TypeId.
        // An object only: a JSON array in the record (seen: "tags": ["vpn"]) is the record's own, not text holding
        // JSON.
        private List<String> heldJson() {
            final List<String> jsonFields = names.stream()
                    .filter(n -> "embedded json".equals(types.get(n))
                                 && values.get(n).stream().anyMatch(DraftMapping::jsonObject))
                    .toList();
            if (jsonFields.isEmpty()) {
                return new ArrayList<>();
            }
            LocalCheck.withJsonFields(new ArrayList<>(records), jsonFields);
            survey();
            mapping.put("json_fields", new ArrayList<>(jsonFields));
            final List<String> inside = names.stream()
                    .filter(n -> jsonFields.stream().anyMatch(j -> n.startsWith(j + ".")))
                    .limit(4)
                    .toList();
            notes.add(reprs(jsonFields) + " " + (jsonFields.size() == 1
                    ? "holds"
                    : "hold") + " JSON: its keys are fields of their own, e.g. " + reprs(inside)
                      + " (json_fields). The text itself isn't mapped.");
            return new ArrayList<>(jsonFields);
        }

        // Fields holding XML text (a SOAP payload): their elements are fields of their own (<field>/<path>, read
        // through xml_fields), and the text itself is never mapped.
        private List<String> heldXml(final List<String> jsonFields) {
            final List<String> xmlFields = names.stream()
                    .filter(n -> !jsonFields.contains(n) && !values.get(n).isEmpty()
                                 && values.get(n).stream().filter(DraftMapping::xmlElement).count()
                                    >= 0.8 * values.get(n).size())
                    .toList();
            if (xmlFields.isEmpty()) {
                return jsonFields;
            }
            LocalCheck.withXmlFields(new ArrayList<>(records), xmlFields);
            survey();
            mapping.put("xml_fields", new ArrayList<>(xmlFields));
            final List<String> inside = names.stream()
                    .filter(n -> xmlFields.stream().anyMatch(x -> n.startsWith(x + "/") && !n.contains("//")))
                    .limit(4)
                    .toList();
            notes.add(reprs(xmlFields) + " " + (xmlFields.size() == 1
                    ? "holds"
                    : "hold") + " XML: its elements are fields of their own, e.g. " + reprs(inside)
                      + " (xml_fields; '<field>//<name>' for one anywhere below). The text itself isn't mapped.");
            final List<String> out = new ArrayList<>(jsonFields);
            out.addAll(xmlFields);
            return out;
        }

        // Text holding key/value pairs: each key a field of its own, through extract (keys alike are written as
        // one function, called with the key), and so homed, ruled on and carried as Data like any field. The text
        // itself isn't mapped when every value had pairs.
        @SuppressWarnings("unchecked")
        private List<String> heldPairs(final List<String> heldFields) {
            final List<String> out = new ArrayList<>(heldFields);
            final Map<String, PairExtracts> paired = pairExtracts(values, new HashSet<>(heldFields));
            paired.forEach((field, pairs) -> {
                final List<Map<String, Object>> extracts = pairs.extracts();
                withPairs(records, extracts);
                ((List<Map<String, Object>>) mapping.computeIfAbsent(EXTRACT, k -> new ArrayList<>()))
                        .addAll(extracts);
                if (pairs.whole()) {
                    out.add(field);
                }
                final String shown = String.join(", ", extracts.stream().limit(6)
                        .map(e -> "'" + ((List<String>) e.get(NAMES)).getFirst() + "'")
                        .toList()) + (extracts.size() > 6
                        ? " ..."
                        : "");
                notes.add("'" + field + "' holds key/value pairs: " + extracts.size() + " keys are fields of their "
                          + "own through pairs (" + shown + "), each read as an extract entry would. "
                          + (pairs.whole()
                        ? "The text itself isn't mapped (map it too, to Description say, if it's wanted whole). "
                        : "")
                          + "A key only one record shows is left out: add it the same way if it's wanted.");
            });
            if (!paired.isEmpty()) {
                survey();
            }
            return out;
        }

        // First, then the fields fitting the same test that the records lacking it hold: one record shape's time
        // field, then another's. Seen: two JSON shapes in one sample, and every event of the second without a
        // time.
        private List<String> covering(final String first, final Predicate<String> fits) {
            final List<String> chosen = new ArrayList<>(List.of(first));
            List<Map<String, Object>> lacking = records.stream()
                    .filter(r -> !JsonValues.truthy(r.get(first)))
                    .toList();
            while (!lacking.isEmpty()) {
                final List<Map<String, Object>> without = lacking;
                final String more = names.stream()
                        .filter(n -> !taken.contains(n) && !chosen.contains(n) && fits.test(n)
                                     && without.stream().anyMatch(r -> JsonValues.truthy(r.get(n))))
                        .findFirst()
                        .orElse(null);
                if (more == null) {
                    break;
                }
                chosen.add(more);
                lacking = lacking.stream().filter(r -> !JsonValues.truthy(r.get(more))).toList();
            }
            return chosen;
        }

        private Map<String, Object> readFrom(final Map<String, Object> entry, final List<String> fields) {
            if (fields.size() > 1) {
                entry.remove(FIELD);
                entry.put(ANY_OF, new ArrayList<>(fields));
            }
            taken.addAll(fields);
            return entry;
        }

        private String type(final String name) {
            return types.get(name);
        }

        // The most precise time the records hold: a time whose values carry fractions of a second wins over one
        // without (seen in production: a Bluecoat log's date and time columns were joined, dropping the
        // milliseconds its epoch column had). Read from the values and their patterns, not the fields' names.
        private boolean subSecond(final String name) {
            if (name == null) {
                return false;
            }
            final String kind = type(name);
            if (List.of("timestamp (epoch milliseconds)", "timestamp (epoch microseconds)",
                    "timestamp (epoch nanoseconds)").contains(kind)) {
                return true;
            }
            if ("timestamp (epoch seconds)".equals(kind)) {
                return values.get(name).stream().anyMatch(v -> v.contains("."));
            }
            return kind.startsWith(TIMESTAMP) && kind.contains("S");
        }

        private static String patternOf(final String type) {
            return type.substring(TIMESTAMP_PREFIX.length(), type.length() - 1);
        }

        // Time: the first timestamp-typed field whose name says time, else the first timestamp-typed field; a
        // date-only field beside a time-of-day field (FortiOS's date= and time=) is joined.
        private String time() {
            String timeField = names.stream()
                    .filter(n -> PyText.match(TIME, key(n)) && type(n).startsWith(TIMESTAMP))
                    .findFirst()
                    .orElseGet(() -> names.stream().filter(n -> type(n).startsWith(TIMESTAMP)).findFirst()
                            .orElse(null));
            final String first = timeField;
            boolean dateOnly = timeField != null && !type(timeField).contains("H") && !type(timeField).contains("h");
            String clock = names.stream()
                    .filter(n -> !n.equals(first) && !values.get(n).isEmpty()
                                 && values.get(n).stream().allMatch(v -> PyText.fullmatch(CLOCK, PyText.strip(v))))
                    .findFirst()
                    .orElse(null);
            final boolean chosenPrecise = timeField != null && dateOnly && clock != null
                    ? subSecond(clock)
                    : subSecond(timeField);
            if (timeField != null && !chosenPrecise) {
                final String precise = names.stream()
                        .filter(n -> !n.equals(first) && subSecond(n)
                                     && (type(n).contains("H") || type(n).contains("epoch")))
                        .findFirst()
                        .orElse(null);
                if (precise != null) {
                    notes.add("'" + precise + "' holds the time to a fraction of a second, so it is "
                              + "EventTime/TimeCreated rather than '" + timeField + "'" + (dateOnly && clock != null
                            ? " and '" + clock + "'"
                            : "") + ".");
                    timeField = precise;
                    dateOnly = false;
                    clock = null;
                }
            }
            if (timeField != null && dateOnly && clock != null) {
                joinedTime(timeField, clock);
            } else if (timeField != null) {
                fieldTime(timeField);
            }
            timeZoneField();
            if (timeField == null) {
                // Seen: an else on the zone test above, so a draft that mapped EventTime said no timestamp was
                // recognised.
                notes.add("No timestamp field was recognised: map EventTime/TimeCreated yourself (time_format from "
                          + "the profile).");
            }
            return timeField;
        }

        private void joinedTime(final String timeField, final String clock) {
            final String datePattern = patternOf(type(timeField));
            final List<String> clocks = values.get(clock);
            String seconds = clocks.stream().allMatch(v -> PyText.count(v, ":") == 2)
                    ? ":ss"
                    : "";
            // A fraction of a second the clock values carry is kept, at their width.
            final Set<Integer> widths = new LinkedHashSet<>();
            clocks.stream().filter(v -> v.contains("."))
                    .forEach(v -> widths.add(PyText.len(PyText.strip(v).split("\\.", 2)[1])));
            if (!seconds.isEmpty() && widths.size() == 1
                && clocks.stream().filter(v -> !PyText.isBlank(v)).allMatch(v -> v.contains("."))) {
                seconds += "." + "S".repeat(widths.iterator().next());
            }
            common.add(dict(PATH, "EventTime/TimeCreated",
                    "xpath", "concat(" + selector(mapping, timeField) + ", ' ', " + selector(mapping, clock) + ")",
                    "time_format", datePattern + " HH:mm" + seconds, "timezone", "UTC"));
            taken.add(timeField);
            taken.add(clock);
            notes.add("'" + timeField + "' and '" + clock + "' are joined for EventTime/TimeCreated, read as UTC: "
                      + "change timezone if the source writes local time.");
        }

        @SuppressWarnings("unchecked")
        private void fieldTime(final String timeField) {
            final String pattern = patternOf(type(timeField));
            final Map<String, Object> entry = dict(PATH, "EventTime/TimeCreated", FIELD, timeField);
            final String timeType = type(timeField);
            readFrom(entry, covering(timeField, n -> timeType.equals(type(n))));
            if (TimePatterns.EPOCH_NAMES.containsKey(pattern)) {
                entry.put("time_format", TimePatterns.EPOCH_NAMES.get(pattern));
            } else {
                entry.put("time_format", pattern);
                if (!(pattern.contains("X") || pattern.contains("Z") || pattern.contains("z")
                      || pattern.contains("O") || pattern.contains("V"))) {
                    entry.put("timezone", "UTC");
                    notes.add("'" + timeField + "' has no time zone: timezone is set to UTC; change it if the "
                              + "source writes local time.");
                }
            }
            common.add(entry);
            taken.add(timeField);
            if (pattern.contains("VV") && entry.containsKey(FIELD)) {
                // The time names its region (Australia/Sydney): converted to UTC by it, and the zone kept with the
                // event, where the schema has it (asked for by the user).
                ((List<Map<String, Object>>) mapping.computeIfAbsent(EXTRACT, k -> new ArrayList<>()))
                        .add(dict(FIELD, timeField, "regex", "([A-Z][a-z]+/[A-Za-z_]+)\\]?\\s*$", NAMES,
                                new ArrayList<>(List.of("time_zone"))));
                common.add(dict(PATH, TIME_ZONE_PATH, FIELD, "time_zone"));
                notes.add("'" + timeField + "' names its time zone: kept as " + TIME_ZONE_PATH + ".");
            }
        }

        private void timeZoneField() {
            final String zoneField = names.stream()
                    .filter(n -> !taken.contains(n) && !values.get(n).isEmpty()
                                 && values.get(n).stream().filter(v -> !PyText.isBlank(v))
                                         .allMatch(v -> PyText.fullmatch(ZONE_NAME, PyText.strip(v)))
                                 && values.get(n).stream().anyMatch(v -> !PyText.isBlank(v)))
                    .findFirst()
                    .orElse(null);
            if (zoneField != null && common.stream().noneMatch(e -> TIME_ZONE_PATH.equals(e.get(PATH)))) {
                // A field of IANA zone names (by its values): the zone the source's times are in, kept with the
                // event.
                common.add(dict(PATH, TIME_ZONE_PATH, FIELD, zoneField));
                taken.add(zoneField);
                notes.add("'" + zoneField + "' holds time zone names: kept as " + TIME_ZONE_PATH + "; if the times "
                          + "have no zone of their own, read them in it (timezone on EventTime/TimeCreated).");
            }
        }

        private void system(final String systemName, final String environment, final String timeField) {
            common.add(dict(PATH, "EventSource/System/Name", VALUE, firstText(systemName, sourceName,
                    "TODO system name")));
            common.add(dict(PATH, "EventSource/System/Environment", VALUE, firstText(environment, null,
                    "TODO e.g. Prod")));
            if (!LocalCheck.hasText(systemName) || !LocalCheck.hasText(environment)) {
                notes.add("Set EventSource/System/Name and Environment to what the user confirms (or the standing "
                          + "instructions say). Both are required: if the user doesn't know one, use a placeholder "
                          + "they agree to (such as Unknown) and record it as an open item, rather than stopping.");
            }
        }

        private static String firstText(final String first, final String second, final String otherwise) {
            if (LocalCheck.hasText(first)) {
                return first;
            }
            return LocalCheck.hasText(second)
                    ? second
                    : otherwise;
        }

        private void homes() {
            for (final FieldHomes.Home home : FieldHomes.HOMES) {
                final Predicate<String> fits = n -> FieldHomes.matches(home.regex(), n) && !"empty".equals(type(n));
                final String field = names.stream()
                        .filter(n -> !taken.contains(n) && fits.test(n))
                        .findFirst()
                        .orElse(null);
                if (field != null) {
                    common.add(readFrom(dict(PATH, home.path(), FIELD, field), covering(field, fits)));
                }
            }
            descriptionNote();
            if (common.stream().noneMatch(e -> "EventSource/Generator".equals(e.get(PATH)))) {
                common.add(dict(PATH, "EventSource/Generator", VALUE, firstText(sourceName, null, "TODO generator")));
            }
            if (common.stream().noneMatch(e -> ((String) e.get(PATH)).startsWith("EventSource/Device/"))) {
                notes.add("No host field was recognised: EventSource/Device is the device where the event happened. "
                          + "Map the field that names it (a door, a reader, a switch, a host) to Device/Name, "
                          + "HostName or IPAddress, or a constant; stroom:meta('RemoteAddress'), the sender's "
                          + "address, only when the record names none.");
            }
        }

        // What the notes say of the event's message: where the draft put it, or, if it found none, a field of free text
        // that may be it. Unlike the Stroom MCP server's draft, which said neither (seen in eval cases 22, 23 and 42:
        // the message, a CEF msg or a ticket's comment, went into a Document's Description or a Data entry).
        private void descriptionNote() {
            final Optional<Object> mapped = common.stream()
                    .filter(e -> DESCRIPTION.equals(e.get(PATH)) && e.get(FIELD) != null)
                    .map(e -> e.get(FIELD))
                    .findFirst();
            if (mapped.isPresent()) {
                notes.add("'" + mapped.get() + "' reads as each event's message, so it is " + DESCRIPTION
                          + ", the event's own description: keep it there in every rule, rather than in an "
                          + "object's Description or a Data entry.");
                return;
            }
            names.stream()
                    .filter(n -> FREE_TEXT.matcher(n).matches() && !"empty".equals(type(n)))
                    .findFirst()
                    .ifPresent(n -> notes.add("'" + n + "' reads as free text written about each event: if it says "
                                              + "what happened, map it to " + DESCRIPTION + ", the event's own "
                                              + "description, rather than to an object's Description or a Data "
                                              + "entry."));
        }

        // Kinds of event: the first naming field with a handful of values.
        private boolean namesKinds(final String name) {
            final Set<String> kinds = new HashSet<>(values.get(name));
            kinds.removeAll(NIL);
            return PyText.match(NAMING, key(name)) && !kinds.isEmpty() && kinds.size() <= MAX_RULES;
        }

        // Seen: syslog's msgid, always '-', taken over an action field: a field with only nil values names no kind.
        // One number, the same in every record, names no kind (a word may: SYSTEM, its actions split in another
        // field). Seen: a Windows EventID always 1000 taken over the category in the text's pairs (Event: [User],
        // [Secret]...).
        private boolean oneNumber(final String name) {
            final Set<String> kinds = new LinkedHashSet<>(values.get(name));
            kinds.removeAll(NIL);
            return kinds.size() == 1 && !PyText.search(LETTER, kinds.iterator().next());
        }

        private Object commonSource(final String path) {
            return common.stream()
                    .filter(e -> path.equals(e.get(PATH)))
                    .map(e -> JsonValues.truthy(e.get(FIELD))
                            ? e.get(FIELD)
                            : e.get(ANY_OF))
                    .findFirst()
                    .orElse(null);
        }

        private Kinds kinds() {
            String naming = names.stream()
                    .filter(n -> !taken.contains(n) && namesKinds(n) && !oneNumber(n))
                    .findFirst()
                    .orElse(null);
            if (naming == null) {
                naming = names.stream().filter(n -> !taken.contains(n) && namesKinds(n)).findFirst().orElse(null);
            }
            final List<String> namings = naming == null
                    ? List.of()
                    : covering(naming, this::namesKinds);
            if (naming != null) {
                common.add(readFrom(dict(PATH, "EventDetail/TypeId", FIELD, naming), namings));
            }
            final List<String> rest = names.stream()
                    .filter(n -> !taken.contains(n) && !"empty".equals(type(n)))
                    .toList();
            // (naming field, kind): each shape's kinds, from the records the earlier naming fields leave out.
            final List<String[]> pairs = new ArrayList<>();
            for (int i = 0; i < namings.size(); i++) {
                final String n = namings.get(i);
                final List<String> earlier = namings.subList(0, i);
                final List<String> own = new ArrayList<>();
                for (final Map<String, Object> record : records) {
                    if (JsonValues.truthy(record.get(n))
                        && earlier.stream().noneMatch(m -> JsonValues.truthy(record.get(m)))) {
                        own.add((String) record.get(n));
                    }
                }
                JsonValues.mostCommon(JsonValues.counted(own)).stream().limit(MAX_RULES)
                        .forEach(v -> pairs.add(new String[]{n, v}));
            }
            final List<String> kinds = pairs.stream().map(pair -> pair[1]).toList();
            final List<Map<String, Object>> rules = new ArrayList<>();
            final List<String> recognised = new ArrayList<>();
            final Set<String> usedNames = new HashSet<>();
            final Object user = commonSource("EventSource/User/Id");
            final Object message = commonSource("EventDetail/Description");
            for (final String[] pair : pairs) {
                kindRules(pair[0], pair[1], rest, user, message, usedNames, rules, recognised);
            }
            if (!recognised.isEmpty()) {
                notes.add("Drafted from the sample's values (check them): " + String.join("; ", recognised) + ".");
            }
            return new Kinds(namings, rest, !pairs.isEmpty(), kinds, rules);
        }

        @SuppressWarnings({"checkstyle:ParameterNumber", "unchecked"})
        private void kindRules(final String naming,
                               final String kind,
                               final List<String> rest,
                               final Object user,
                               final Object message,
                               final Set<String> usedNames,
                               final List<Map<String, Object>> rules,
                               final List<String> recognised) {
            String name = PyText.strip(NOT_NAME.matcher(kind).replaceAll("_"), "_").toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                name = "event";
            }
            while (usedNames.contains(name)) {
                name += "_";
            }
            usedNames.add(name);
            final WindowsEvent windows = EVENT_ID_KEYS.contains(key(naming))
                    ? WINDOWS_LOGON.get(kind)
                    : null;
            if (PyText.search(AUTH, kind) || windows != null) {
                final String action;
                final Boolean success;
                if (windows != null) {
                    action = windows.action();
                    success = windows.success();
                } else {
                    action = PyText.search(LOGOFF, kind)
                            ? "Logoff"
                            : "Logon";
                    success = null;
                }
                final List<Map<String, Object>> fields = new ArrayList<>();
                fields.add(dict(PATH, "EventDetail/Authenticate/Action", VALUE, action));
                if (success != null) {
                    fields.add(dict(PATH, "EventDetail/Authenticate/Outcome/Success", VALUE, success
                            ? "true"
                            : "false"));
                }
                if (JsonValues.truthy(user)) {
                    final Map<String, Object> entry = dict(PATH, "EventDetail/Authenticate/User/Id");
                    entry.putAll(ActionRules.read(user));
                    fields.add(entry);
                }
                fields.addAll(dataEntries("Authenticate", rest));
                rules.add(kindRule(name, naming, kind, fields));
                return;
            }
            // The kind's own records often show its actions in another field (a firewall's TRAFFIC: action ALLOW
            // or DENY; ADMIN: LOGIN_SUCCESS, LOGOUT, CONFIG_CHANGE): a rule per action, before the kind's own rule.
            final List<Map<String, Object>> ofKind = records.stream().filter(r -> kind.equals(r.get(naming))).toList();
            ActionRules.Found found = ActionRules.actionRules(ofKind, names,
                    List.of(dict(FIELD, naming, "equals", kind)), name, user, message, Set.of(naming), null);
            if (found.rules().isEmpty()) {
                // The kind itself may be the action (CONNECT, DENY): split on the naming field.
                found = ActionRules.actionRules(ofKind, names, List.of(), "", user, message, Set.of(), naming);
            }
            for (final Map<String, Object> foundRule : found.rules()) {
                // The rest of the record is carried as the action element's Data, as the Unknown rule did.
                final List<Map<String, Object>> fields = (List<Map<String, Object>>) foundRule.get(FIELDS);
                final String firstPath = (String) fields.getFirst().get(PATH);
                final String[] parts = firstPath.split("/", -1);
                final int depth = Math.min(parts.length, firstPath.contains("Network")
                        ? 3
                        : 2);
                final String element = String.join("/", List.of(parts).subList(0, depth));
                final Set<Object> mapped = new HashSet<>();
                fields.forEach(f -> mapped.add(f.get(FIELD)));
                mapped.add(found.split());
                for (final String n : rest) {
                    if (!mapped.contains(n)) {
                        fields.add(dict(PATH, dataHome(element, n) + "/Data", DATA_NAME, n, FIELD, n));
                    }
                }
            }
            rules.addAll(found.rules());
            if (!found.rules().isEmpty()) {
                recognised.add(kind + ": " + ActionRules.described(found.rules()));
            }
            if (found.rules().isEmpty() || !found.left().isEmpty()) {
                rules.add(kindRule(name, naming, kind, dataEntries("Unknown", rest)));
            }
        }

        // A rule for one kind: the records whose naming field holds it.
        private static Map<String, Object> kindRule(final String name, final String naming, final String kind,
                                                    final List<Map<String, Object>> fields) {
            return dict("name", name, "when", new ArrayList<>(List.of(dict(FIELD, naming, "equals", kind))), FIELDS,
                    fields);
        }

        private static List<Map<String, Object>> dataEntries(final String action, final List<String> fields) {
            final List<Map<String, Object>> out = new ArrayList<>();
            fields.forEach(n -> out.add(dict(PATH, "EventDetail/" + action + "/Data", DATA_NAME, n, FIELD, n)));
            return out;
        }

        // No field names kinds, but every record runs a command (sudo, a job scheduler): Process, not an Unknown
        // placeholder (seen: sudo drafted as Unknown, then made Authorise by the agent). Otherwise the rule for
        // the rest. Returns the command field, or null.
        private String commandOrOther(final Kinds kinds, final String naming) {
            final List<Map<String, Object>> rules = kinds.rules();
            final String command = kinds.anyPairs()
                    ? null
                    : kinds.rest().stream().filter(n -> PyText.match(COMMAND, key(n))).findFirst().orElse(null);
            if (command != null) {
                final List<Map<String, Object>> fields = new ArrayList<>();
                fields.add(dict(PATH, "EventDetail/TypeId", VALUE, "Command"));
                fields.add(dict(PATH, "EventDetail/Process/Action", VALUE, "Execute"));
                fields.add(dict(PATH, "EventDetail/Process/Type", VALUE, "Application"));
                fields.add(dict(PATH, "EventDetail/Process/Command", FIELD, command));
                kinds.rest().stream().filter(n -> !n.equals(command))
                        .forEach(n -> fields.add(dict(PATH, "EventDetail/Process/Data", DATA_NAME, n, FIELD, n)));
                rules.add(dict("name", "command", FIELDS, fields));
                final boolean runsAs = common.stream().anyMatch(e -> "EventSource/RunAs/Id".equals(e.get(PATH)));
                notes.add("Every record runs a command ('" + command + "'): drafted as Process (Action Execute, Type "
                          + "Application)" + (runsAs
                        ? ", the account it runs as in EventSource/RunAs"
                        : "") + ". Check it, and split the program from its arguments (Process/Arguments) if you "
                          + "want them apart.");
                return command;
            }
            // The rest's Unknown carries what the record has left; with every field mapped already, the kind field
            // (seen: an empty catch-all rule, which has no action element, failing the whole mapping).
            List<Map<String, Object>> unknownData = dataEntries("Unknown", kinds.rest());
            if (unknownData.isEmpty()) {
                unknownData = dataEntries("Unknown", naming != null
                        ? List.of(naming)
                        : names.subList(0, Math.min(1, names.size())));
            }
            final List<Map<String, Object>> fields = new ArrayList<>();
            if (naming == null) {
                fields.add(dict(PATH, "EventDetail/TypeId", VALUE, "Other"));
            }
            fields.addAll(unknownData);
            rules.add(dict("name", "other", FIELDS, fields));
            return null;
        }

        private void shapesNote(final int shapes, final List<String> namings) {
            final List<String> shared = common.stream()
                    .filter(e -> JsonValues.truthy(e.get(ANY_OF)))
                    .map(e -> e.get(PATH) + " from " + String.join(" or ", anyOf(e)))
                    .toList();
            if (shapes > 1 && namings.size() + shared.size() > 0 && (namings.size() > 1 || !shared.isEmpty())) {
                notes.add("The sample's records come in " + shapes + " shapes (their fields differ): each element "
                          + "reads every shape's field in turn (" + String.join("; ", shared) + "), and each "
                          + "shape's kinds have rules. Check them against each file.");
            }
        }

        @SuppressWarnings("unchecked")
        private static List<String> anyOf(final Map<String, Object> entry) {
            return (List<String>) entry.get(ANY_OF);
        }

        private void kindsNote(final Kinds kinds, final String naming, final String command) {
            final boolean unknownKinds = kinds.rules().stream()
                    .anyMatch(r -> JsonValues.truthy(r.get("when")) && JsonValues.maps(r.get(FIELDS)).stream()
                            .anyMatch(f -> ((String) f.get(PATH)).startsWith("EventDetail/Unknown")));
            final String values = String.join(", ", kinds.kinds());
            if (!kinds.kinds().isEmpty() && !unknownKinds) {
                notes.add("One rule per value of " + String.join(" or ", kinds.namings().stream().map(PyText::repr)
                        .toList()) + " (" + values + "), each with its action element; 'other' catches the rest. "
                          + "Work through the schema problems build_translation_xslt reports: each names what the "
                          + "element takes.");
            } else if (!kinds.kinds().isEmpty()) {
                notes.add("One rule per value of '" + naming + "' (" + values + "): replace each EventDetail/Unknown "
                          + "left by the action element that describes the kind (Authenticate, Network, Process, "
                          + "View, Create, Update, Delete, Alert, Send, Receive...), and move its Data entries into "
                          + "that element (e.g. EventDetail/Alert/Data); 'other' catches the rest. Work through the "
                          + "schema problems build_translation_xslt reports: each names what the element takes. "
                          + "Unknown is for records no action element describes, not a way round a schema error; "
                          + "build_translation_xslt refuses it where the values show an action.");
            } else if (command == null) {
                notes.add("No field names the kind of event: every record is one 'other' event with Unknown/Data. "
                          + "If the records are all one kind of event (the request or the sample says what), "
                          + "replace Unknown with the action element that describes it (a command run is Process, a "
                          + "logon Authenticate, a file read View...) and move its Data entries into that element; "
                          + "otherwise add rules with conditions once you know how kinds are told apart.");
            }
        }

        @SuppressWarnings("unchecked")
        private List<String> withNotes(final Map<String, Object> applied, final List<String> rest) {
            final List<String> appliedFields = (List<String>) applied.get(FIELDS);
            final List<String> appliedEvents = (List<String>) applied.get(EVENTS);
            final List<String> fromSample = (List<String>) applied.get("from_sample");
            final List<String> notInCatalogue = (List<String>) applied.get("not_in_catalogue");
            if (!appliedEvents.isEmpty()) {
                notes.removeIf(n -> n.startsWith("One rule per value of") || n.startsWith("No field names the kind"));
            }
            List<String> left = rest;
            if (!appliedFields.isEmpty() || !appliedEvents.isEmpty()) {
                final List<Map<String, Object>> drafted = JsonValues.maps(mapping.get("common"));
                left = rest.stream().filter(n -> drafted.stream().noneMatch(e -> n.equals(e.get(FIELD)))).toList();
                final List<String> said = new ArrayList<>(appliedFields);
                said.addAll(appliedEvents);
                notes.addFirst("Drafted from the source documentation: " + String.join("; ", said) + ".");
            }
            if (!fromSample.isEmpty()) {
                notes.add(Math.min(1, notes.size()), "Kept from the sample's values, where the catalogue can't be "
                                                     + "followed: " + String.join("; ", fromSample) + ".");
            }
            if (!notInCatalogue.isEmpty()) {
                notes.add("The catalogue does not list " + String.join(", ", notInCatalogue) + ": those records fall "
                          + "to the rule for the rest; read the reference documents (find_in_content pattern=the "
                          + "value) or ask the user.");
            }
            return left;
        }

        private void nilValues() {
            if (spec == null || !List.of("syslog", "cef", "key_value").contains(spec.kind())) {
                return;
            }
            // Syslog's NILVALUE, and many key=value logs' "none": seen written as a user called '-'.
            final Set<String> nil = new HashSet<>();
            records.forEach(r -> r.forEach((k, v) -> {
                if ("-".equals(PyText.strip(PyText.str(v))) && !"msgid".equals(k) && !"structured".equals(k)) {
                    nil.add(k);
                }
            }));
            if (!nil.isEmpty()) {
                mapping.put("nil_values", new ArrayList<>(List.of("-")));
                notes.add(PyText.repr(nil.stream().sorted(PyText::compare).toList()) + " hold '-' for none: "
                          + "nil_values ['-'] leaves those elements out rather than writing '-'.");
            }
        }

        // Quoted CSV values with a doubled quote keep it doubled in Stroom: read them with unescape_quotes.
        @SuppressWarnings("unchecked")
        private void doubledQuotes() {
            if (spec == null || !"delimited".equals(spec.kind()) || !LocalCheck.hasText(spec.quote())) {
                return;
            }
            final String doubledQuote = spec.quote() + spec.quote();
            final Set<String> doubled = new HashSet<>();
            records.forEach(r -> r.forEach((k, v) -> {
                if (PyText.str(v).contains(doubledQuote)) {
                    doubled.add(k);
                }
            }));
            final List<Map<String, Object>> entries = new ArrayList<>(JsonValues.maps(mapping.get("common")));
            JsonValues.maps(mapping.get(EVENTS)).forEach(rule -> entries.addAll(
                    (List<Map<String, Object>>) rule.get(FIELDS)));
            for (final Map<String, Object> entry : entries) {
                if (doubled.contains(entry.get(FIELD)) && !JsonValues.truthy(entry.get("transform"))
                    && !JsonValues.truthy(entry.get("time_format"))) {
                    entry.put("transform", "unescape_quotes");
                }
            }
            if (!doubled.isEmpty()) {
                notes.add(PyText.repr(doubled.stream().sorted(PyText::compare).toList()) + " hold doubled quotes "
                          + "inside quoted values, which Data Splitter keeps doubled: the draft reads them with "
                          + "transform unescape_quotes.");
            }
        }
    }

    // What the kinds of event gave: the naming fields, the fields left for Data, whether any kind was found, the
    // kinds, and the rules so far.
    private record Kinds(List<String> namings,
                         List<String> rest,
                         boolean anyPairs,
                         List<String> kinds,
                         List<Map<String, Object>> rules) {

    }
}
