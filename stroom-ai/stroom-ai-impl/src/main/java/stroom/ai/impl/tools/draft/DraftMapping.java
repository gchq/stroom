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
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.FieldHomes;
import stroom.ai.impl.tools.generation.Pairs;
import stroom.ai.impl.tools.generation.ZoneSource;
import stroom.ai.impl.tools.sample.KindFields;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.SampleTexts;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.SyslogHeaders;
import stroom.ai.impl.tools.sample.TimePatterns;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.sample.XmlTree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A starting translation mapping, drafted from the sample.
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

    /// What a draft's EventSource/System/Name says until the user names the system.
    public static final String SYSTEM_NAME_PLACEHOLDER = "TODO system name";
    /// What a draft's EventSource/System/Environment says until the user gives it.
    public static final String ENVIRONMENT_PLACEHOLDER = "TODO e.g. Prod";

    private static final Pattern NAMING = ignoringCase("^(event_?type|eventtype|type|event|event_?name|event_?id|"
                                                       + "event_?code|eventid|action|activity|category|subtype|"
                                                       + "sub_?type|operation|op|logid|log_?type|msg_?id|kind|"
                                                       + "result_?type|act|device_?action)$");
    private static final Pattern AUTH = ignoringCase(
            "(login|logon|signin|sign_in|authenticat|logoff|logout|signout|sign_out|password)");
    private static final Pattern LOGOFF = ignoringCase("(logoff|logout|signout|sign_out)");
    private static final Pattern COMMAND = ignoringCase(
            "^(command|cmd|cmd_?line|command_?line|process_?command)$");
    // A kinds field's values past which it is an identifier, not kinds of event: the field's name already says
    // kinds (event id, type, action), so up to 200 (asked for by the user: a source of a hundred event types; until
    // 2026-11-07, 12, when a domain controller's 40 event ids were drafted as no kinds at all; then 64).
    private static final int MAX_KINDS = 200;
    // The records a naming field's values must each be in one of, a value a record, for it to read as an
    // identifier rather than kinds.
    private static final int EACH_ONCE_RECORDS = 5;
    // Values a kinds field may have without repeating (the limit before it was raised to 200): a sample's excerpt
    // may show each of a few dozen kinds once (seen: a Windows excerpt's event ids).
    private static final int FEW_KINDS = 64;
    // A field tying records written of one change or action (a correlation id), by generic names.
    private static final Pattern CORRELATION = ignoringCase("(correlation_?id|corr_?id|correlation|op_?correlation_?id|"
                                                            + "activity_?id|transaction_?id)$");
    // A user field's name: who acted, or who (or what account) it was done to, by generic prefixes.
    private static final Pattern USER_FIELD = ignoringCase("(user|user_?name|user_?id|account|account_?name)$");
    private static final Pattern ACTOR = ignoringCase("^(subject|source|src|actor|initiator|caller|requester|acting|"
                                                      + "by(?=_)|s(?=user))");
    private static final Pattern AFFECTED = ignoringCase("^(target|dest|dst|member|object|affected|victim|"
                                                         + "d(?=user))");
    /// The draft's key for the kinds (or the whole sample) where two times could each be the event's: each with
    /// what tells them apart, for the agent to choose from.
    public static final String TIME_CANDIDATES = "time_candidates";
    /// The key a drafted time entry carries while its time is one of several the agent must choose from: the
    /// fields, the one drafted first. build_translation_xslt refuses the mapping until it is removed.
    public static final String CHOOSE_FROM = "choose_from";
    // Records holding a time for it to be put to the agent beside another as the event's.
    private static final int CANDIDATE_SHARE = 90;
    private static final String BYTE_ORDER_MARK = String.valueOf((char) 0xFEFF);
    // The fields a CEF or LEEF header names each kind of event in, best first: the format's own, so taken before a
    // field whose name only looks like a kind's (seen: NetScaler's CEF split by act, blocked or not, rather than by
    // its name, APPFW_SQL...).
    private static final List<String> HEADER_KINDS = List.of("cef_name", "cef_signature_id", "leef_event_id");
    /// The draft's key for the variants whose times have no date and no header giving it: `field`, `variant_field`
    /// and `variants` (each variant's time format, e.g. `HH:mm:ss.SSS`), for the user to say where the date comes
    /// from.
    public static final String DATELESS_TIMES = "dateless_times";

    /// The draft's `time_zones` entry, in a fixed order (Map.of's order differs from one run to the next).
    ///
    /// @param examples  Some values of each time read with no zone, by field.
    /// @param zoneNames The zones a field of zone names holds.
    /// @param zoneField That field, or null.
    /// @return The entry.
    static Map<String, Object> timeZones(final Map<String, List<String>> examples,
                                         final List<String> zoneNames,
                                         final String zoneField) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("examples", examples);
        entry.put("zone_names", zoneNames);
        if (zoneField != null) {
            entry.put("zone_field", zoneField);
        }
        return entry;
    }

    /// The draft's key for times with no zone or offset: `examples` (some values of each such field, by field) and
    /// `zone_names` (the zones a field of zone names holds), for the user to say the zone the source writes in.
    public static final String TIME_ZONES = "time_zones";
    // What the notes say of a time with no zone.
    private static final String NO_ZONE = "The zone the source writes it in is the user's to say: "
                                          + "draft_translation_mapping asks (timezone=), and Stroom reads it as UTC "
                                          + "until then.";
    // An epoch time in seconds (ten digits, a fraction allowed) inside a record's key, and the replace that takes it
    // out.
    private static final Pattern KEY_EPOCH = Pattern.compile("(?<![\\d.])\\d{10}(?:\\.\\d{1,9})?(?![\\d])");
    // (XPath's regexes: plain groups, no (?:...).)
    private static final String KEY_EPOCH_REPLACE = "^\\D*(\\d{10}(\\.\\d{1,9})?).*$";
    /// The field the exception a record's stack trace starts with is extracted as.
    public static final String EXCEPTION_FIELD = "exception";
    /// The field its message is extracted as.
    public static final String EXCEPTION_MESSAGE_FIELD = "exception_message";
    // The exception a stack trace starts with (java.lang.IllegalStateException: boom), in XPath's regex dialect,
    // which the extraction runs in; the message to the end of its line.
    private static final String EXCEPTION_REGEX = "^([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+(?:Exception|Error"
                                                  + "|Throwable))(?::\\s*(.*))?";
    private static final Pattern EXCEPTION = Pattern.compile(EXCEPTION_REGEX);
    // Values that say a field has nothing (syslog's NILVALUE).
    private static final Set<String> NIL = Set.of("-", "", "null", "none", "n/a");
    // Key/value pairs in a text field, as survey_feed signs them (kv:): the ways a text writes them are Pairs'.
    // Seen in production: the agent writing 33 extractions by hand after the draft carried such a text whole.
    private static final Pattern COLON_KEY = ToolText.re(Pairs.BOUND + "(" + Pairs.WORDS + "): (\\[)?");
    private static final Pattern EQ_KEY = ToolText.re("(?:^|[\\s,;])([A-Za-z_][A-Za-z0-9_.-]*)=(\")?");
    // A key = value with spaces round the '=' (Pairs, spaced_equals), after a space, a comma, a semicolon or an
    // opening bracket: "(cpu = 99, threshold = 90)" (seen in chat 171: NetScaler's SNMP traps).
    private static final Pattern SPACED_EQ_KEY = ToolText.re("(?:^|[\\s,;(])([A-Za-z_][A-Za-z0-9_.-]*) = (?=[^\\s=])");
    // A key a spaced dash parts from the pair before it, one word then a space and its value (Pairs, dashed).
    // Not a colon key's first word ("Source IP: ..."): those are the colon style's.
    private static final Pattern DASH_KEY = ToolText.re("(?:^|\\s-\\s+)(?!" + Pairs.WORDS
                                                        + ": )([A-Za-z][A-Za-z0-9_()]*)\\s+(\")?(?=\\S)");
    private static final String SPACED_DASH = " - ";
    // A key=, and a quoted value (its escaped quotes in it): for the keys a line gives more than once.
    private static final Pattern REPEATABLE_KEY = ToolText.re("(?:^|[\\s,;])([A-Za-z_][A-Za-z0-9_.-]*)=");
    private static final Pattern QUOTED_VALUE = ToolText.re("\"(?:\\\\.|[^\"\\\\])*+\"");
    // The info key the keys given more than once in a line are kept under.
    private static final String REPEATED_KEYS = "repeated_keys";
    // An unquoted value with two items or more (group 1 the second's separator), to white space or the end.
    private static final Pattern LIST_VALUE = ToolText.re("[^\\s,;\"=]+([,;][^\\s,;\"=]+)+(?:\\s|$)");
    // The type of a value ending in XML after words of its own.
    private static final String XML_AFTER_WORDS = SampleProfiler.EMBEDDED_XML + " after prefix";
    // The XML a text ends with, after words of its own: from the first < opening an element (as
    // SampleProfiler.xmlStart finds it). No lookahead: the XSLT's regexes have none.
    private static final String XML_TAIL = "^.*?(<[A-Za-z_?].*>)\\s*$";
    // What follows a quoted value's closing quote when the quote closes its own pair: a spaced dash or the end.
    private static final Pattern QUOTE_ENDS_PAIR = ToolText.re("\\s+-\\s|\\s*$");
    private static final Pattern SOURCE_SIDE = ignoringCase("^(src|source|client|orig|origin)(_|$)");
    private static final Pattern DESTINATION_SIDE = ignoringCase(
            "^(dst|dest|destination|server|target|resp|responder)(_|$)");
    private static final Pattern KEY_SPLIT = Pattern.compile("[./]");
    // A time zone offset is a whole number of quarter hours, and at most 14 hours.
    private static final long QUARTER_HOUR_SECONDS = 900L;
    private static final long MAX_OFFSET_SECONDS = 14L * 3_600L;
    private static final Pattern CLOCK = ToolText.re("\\d{1,2}:\\d{2}(:\\d{2})?(\\.\\d+)?");
    private static final Pattern ZONE_NAME = ToolText.re("[A-Z][a-z]+/[A-Za-z_]+(?:/[A-Za-z_]+)?");
    private static final Pattern LETTER = ToolText.re("[A-Za-z]");
    private static final Pattern NOT_NAME = ToolText.re("[^A-Za-z0-9]+");
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
    static final Pattern FREE_TEXT = Pattern.compile("(?i)(comments?|remarks?|notes?)");
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
        return ToolText.ignoringCase(regex);
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
        final Records read = records(named, splitter);
        final String source = sourceName == null
                ? ""
                : sourceName;
        if (read.spec() != null && SplitterSpec.ALTERNATIVES.equals(read.spec().kind())) {
            final String field = read.spec().variantField();
            return withDrops(VariantDrafts.draft(read, (variant, records) -> new Drafting(records, source)
                    .draft(systemName, environment, SourceNotes.forVariant(sourceNotes, field, variant), detailCheck)),
                    sourceNotes);
        }
        final Optional<SplitterSpec.ConverterVariants> formats = read.spec() == null
                ? Optional.empty()
                : read.spec().converterVariants();
        if (formats.isPresent()) {
            return withDrops(VariantDrafts.draft(read, formats.get(), (variant, records) -> new Drafting(records,
                    source).draft(systemName, environment, SourceNotes.forVariant(sourceNotes, formats.get().field(),
                    variant), detailCheck)), sourceNotes);
        }
        return withDrops(new Drafting(read, source).draft(systemName, environment, sourceNotes, detailCheck),
                sourceNotes);
    }

    // The draft with a drop rule for each kind the user said to leave untranslated (classify_kinds), so their records
    // are neither translated nor logged as Unknown (asked for by the user: a drop left to the agent could be
    // forgotten, and the records then kept as Unknown, logged in every Error stream).
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withDrops(final Map<String, Object> result,
                                                 final Map<String, Object> sourceNotes) {
        final List<String> dropped = sourceNotes == null
                ? List.of()
                : JsonValues.strings(sourceNotes.get(SourceNotes.DROPPED));
        if (dropped.isEmpty() || !(result.get("mapping") instanceof final Map<?, ?> drafted)) {
            return result;
        }
        final Map<String, Object> mapping = (Map<String, Object>) drafted;
        final List<Object> drops = new ArrayList<>((List<Object>) mapping.getOrDefault("drop_when", List.of()));
        final List<String> added = new ArrayList<>();
        for (final String key : dropped) {
            final List<Map<String, Object>> when = SourceNotes.dropConditions(key);
            final Map<String, Object> drop = dict("reason", SourceNotes.DROPPED_REASON + ": " + key, "when", when);
            if (!when.isEmpty() && !drops.contains(drop)) {
                drops.add(drop);
                added.add(key);
            }
        }
        if (!added.isEmpty()) {
            mapping.put("drop_when", drops);
            final List<Object> notes = new ArrayList<>(JsonValues.listOrEmpty(result.get("notes")));
            notes.add("Kinds the user said to leave untranslated are dropped (drop_when), neither translated nor "
                      + "logged: " + String.join("; ", added) + ".");
            result.put("notes", notes);
        }
        return result;
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
        final Set<String> repeated = new LinkedHashSet<>();
        SplitterSpec spec = given;
        for (final String text : named.values()) {
            switch (fmt) {
                case SampleProfiler.JSON_OBJECT -> {
                    // The wrapper's array holds the records: the draft's fields are the items' (for_each reads
                    // them).
                    final Map.Entry<String, List<Object>> found = SampleProfiler.wrappedRecords(ToolText.strip(text));
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
                    for (final Object value : jsonValues(ToolText.strip(text))) {
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
                    repeated.addAll(repeatedKeys(text));
                }
            }
        }
        if (repeated.isEmpty()) {
            return new Records(info, records, spec);
        }
        final Map<String, Object> withRepeated = new LinkedHashMap<>(info);
        withRepeated.put(REPEATED_KEYS, List.copyOf(repeated));
        return new Records(withRepeated, records, spec);
    }

    // The keys a line of text gives more than once (rcpt= once a recipient), outside quoted values: Stroom's Data
    // Splitter writes each as a data element of its own, and the dry run's record keeps the first.
    private static Set<String> repeatedKeys(final String text) {
        final Set<String> repeated = new LinkedHashSet<>();
        for (final String line : ToolText.splitlines(text)) {
            if (line.indexOf('=') < 0) {
                continue;
            }
            final Set<String> seen = new HashSet<>();
            final Matcher key = REPEATABLE_KEY.matcher(QUOTED_VALUE.matcher(line).replaceAll("\"\""));
            while (key.find()) {
                if (!seen.add(key.group(1))) {
                    repeated.add(key.group(1));
                }
            }
        }
        return repeated;
    }

    private static Map<String, Object> asText(final Map<String, Object> flat, final String prefix) {
        final Map<String, Object> out = new LinkedHashMap<>();
        flat.forEach((key, value) -> out.put(prefix + key, JsonValues.jsonText(value)));
        return out;
    }

    private static List<Object> jsonValues(final String stripped) {
        if (stripped.startsWith("[")) {
            return ToolJson.loads(stripped) instanceof final List<?> list
                    ? new ArrayList<>(list)
                    : List.of();
        }
        final List<Object> values = new ArrayList<>();
        for (final String line : ToolText.splitlines(stripped)) {
            final Object value = jsonOrNull(line);
            if (JsonValues.truthy(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private static Object jsonOrNull(final String line) {
        if (ToolText.isBlank(line)) {
            return null;
        }
        try {
            // A line encoded twice (a JSON string holding the object) read as the object.
            return SampleProfiler.loadsDecoded(line);
        } catch (final ToolJson.JsonDecodeException e) {
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
            return XmlTree.parse(ToolText.strip(text)).elements();
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
            // A text of pairs by its other styles: key = value with spaces is read only in such a text (seen in
            // testing: SQL's "SET credit_limit = 5000 WHERE id = 42" taken for pairs, and a log's message lost).
            if (found.stream().filter(f -> f.values().stream().filter(style -> !Pairs.SPACED_EQUALS.equals(style))
                    .count() >= 2).count() * 2 < vals.size()) {
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
                final String mostCommon = readsEachValue(style.getKey(), style.getValue(), vals, found,
                        styles.keySet());
                extracts.add(dict(FIELD, field, "regex", Pairs.pairRegex(mostCommon, style.getKey()), NAMES,
                        new ArrayList<>(List.of(name))));
            });
            if (!extracts.isEmpty()) {
                out.put(field, new PairExtracts(extracts, found.stream().noneMatch(Map::isEmpty)));
            }
        }
        return out;
    }

    // Whether the unquoted value starting at the index is a list: items parted by , or ; to the next white space,
    // none of them a pair (seen in testing: a mail log's recipients=a@example.com,b@example.com read as its first).
    private static boolean listed(final String text, final int start) {
        final Matcher value = LIST_VALUE.matcher(text).region(start, text.length());
        return value.lookingAt() && value.group(1) != null;
    }

    // The style a key is read in, the one most of its values are written in: listed in place of equals where some
    // values are lists, and dashed in place of dashed_quoted where the quoted regex reads a value holding the
    // key wrongly, nothing (its quote unclosed) or past another of the text's keys (to the next quote anywhere). Seen
    // in testing: an unclosed quote in a browser's name, `Browser_type "Mozilla/5.0 (...) - Next_key value`.
    private static String readsEachValue(final String key,
                                         final Map<String, Integer> counts,
                                         final List<String> vals,
                                         final List<Map<String, String>> found,
                                         final Set<String> keys) {
        final String style = JsonValues.mostCommon(counts).getFirst();
        if ("equals".equals(style) && counts.containsKey("listed")) {
            // A list in some values: the list's regex reads a single value too.
            return "listed";
        }
        if (!"dashed_quoted".equals(style)) {
            return style;
        }
        final Pattern regex = ToolText.re(Pairs.pairRegex(style, key));
        for (int i = 0; i < vals.size(); i++) {
            if (!found.get(i).containsKey(key)) {
                continue;
            }
            final Matcher matched = regex.matcher(vals.get(i));
            if (!matched.find() || ToolText.isBlank(matched.group(1))
                || spansKey(matched.group(1), key, keys)) {
                return "dashed";
            }
        }
        return style;
    }

    // Whether a value holds another key after a spaced dash: the next pair, read into this one.
    private static boolean spansKey(final String value, final String key, final Set<String> keys) {
        for (final String other : keys) {
            if (!other.equals(key) && value.contains(SPACED_DASH + other + " ")) {
                return true;
            }
        }
        return false;
    }

    // Whether the quote opening at the index closes inside its own pair: its closing quote is followed by a spaced
    // dash or the text's end. A spaced dash inside the quotes is the value's (`Group(s) "Staff - North"`).
    private static boolean quoteClosesInPair(final String text, final int opening) {
        final int closing = text.indexOf('"', opening + 1);
        return closing >= 0 && QUOTE_ENDS_PAIR.matcher(text).region(closing + 1, text.length()).lookingAt();
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
                    : listed(text, equals.end())
                            ? "listed"
                            : "equals");
        }
        final Matcher spaced = SPACED_EQ_KEY.matcher(text);
        while (spaced.find()) {
            found.putIfAbsent(spaced.group(1), Pairs.SPACED_EQUALS);
        }
        // Pairs a spaced dash parts, where the text has at least two such dashes (one is as likely prose) and more keys
        // written so than colon keys (a colon-pairs text may hold a dash in a value: Secret Server's "Secret: Firewall
        // - Edge Admin"); its colon keys then read to the next spaced dash too (seen in chat 163: "SessionId: 2639 -
        // User grace - ..." read to the text's end).
        if (text.indexOf(SPACED_DASH) != text.lastIndexOf(SPACED_DASH)) {
            final Map<String, String> dashedKeys = new LinkedHashMap<>();
            final Matcher dashed = DASH_KEY.matcher(text);
            while (dashed.find()) {
                if (!found.containsKey(dashed.group(1))) {
                    dashedKeys.put(dashed.group(1), dashed.group(2) != null && quoteClosesInPair(text,
                            dashed.start(2))
                            ? "dashed_quoted"
                            : "dashed");
                }
            }
            final long colonKeys = found.values().stream().filter("colon"::equals).count();
            if (dashedKeys.size() >= 2 && dashedKeys.size() > colonKeys) {
                found.replaceAll((key, style) -> "colon".equals(style)
                        ? "dashed"
                        : style);
                dashedKeys.forEach(found::putIfAbsent);
            }
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
        extracts.forEach(ex -> patterns.add(ToolText.re((String) ex.get("regex"))));
        for (final Map<String, Object> record : records) {
            for (int i = 0; i < extracts.size(); i++) {
                final Map<String, Object> ex = extracts.get(i);
                final Matcher found = patterns.get(i).matcher(JsonValues.text(record.get((String) ex.get(FIELD))));
                if (found.find() && found.group(1) != null && !ToolText.isBlank(found.group(1))) {
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

    /// A drafted mapping with each entry that carries a record's continuation (its stack trace) cut to its first
    /// lines, as the user chose: the whole trace is kept unless they choose less.
    ///
    /// @param mapping A drafted mapping, as JSON-like values.
    /// @param lines   The most lines of each trace to keep; 0 or less keeps it whole.
    /// @return How many entries were cut.
    @SuppressWarnings("unchecked")
    public static int withContinuationLines(final Map<String, Object> mapping, final int lines) {
        if (lines <= 0) {
            return 0;
        }
        final String cut = "string-join(tokenize(" + selector(mapping, SplitterSpec.CONTINUATION) + ", '\\n')"
                           + "[position() le " + lines + "], codepoints-to-string(10))";
        int changed = 0;
        final List<Map<String, Object>> entries = new ArrayList<>(
                (List<Map<String, Object>>) mapping.getOrDefault("common", List.of()));
        for (final Object rule : (List<Object>) mapping.getOrDefault(EVENTS, List.of())) {
            if (rule instanceof final Map<?, ?> map && map.get(FIELDS) instanceof final List<?> fields) {
                fields.forEach(field -> entries.add((Map<String, Object>) field));
            }
        }
        for (final Map<String, Object> entry : entries) {
            if (SplitterSpec.CONTINUATION.equals(entry.get(FIELD))) {
                entry.remove(FIELD);
                entry.put("xpath", cut);
                changed++;
            }
        }
        return changed;
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
            final String name = ToolText.lastPart(field, '.');
            if (ToolText.match(SOURCE_SIDE, name)) {
                return element + "/Source";
            }
            if (ToolText.match(DESTINATION_SIDE, name)) {
                return element + "/Destination";
            }
        }
        return element;
    }

    private static boolean jsonObject(final String text) {
        try {
            return SampleProfiler.loadsDecoded(text) instanceof Map;
        } catch (final ToolJson.JsonDecodeException e) {
            return false;
        }
    }

    // A field value that is an XML element, as text (a SOAP payload held in a JSON string).
    private static boolean xmlElement(final String value) {
        if (!ToolText.strip(value).startsWith("<")) {
            return false;
        }
        try {
            XmlTree.parse(ToolText.strip(value));
            return true;
        } catch (final XmlTree.XmlException e) {
            return false;
        }
    }

    private static String reprs(final List<String> names) {
        return String.join(", ", names.stream().map(ToolText::repr).toList());
    }


    // A text field holding a syslog line, or the end of an RFC 5424 header after its host, in most of its values (at
    // least four in five): how to read it; else null.
    private static HeldHeader heldHeader(final List<String> held) {
        final List<String> texts = held.stream().map(value -> ToolText.strip(value.replace(BYTE_ORDER_MARK, "")))
                .filter(value -> !value.isEmpty())
                .toList();
        if (texts.isEmpty()) {
            return null;
        }
        final Map<String, Integer> formats = new LinkedHashMap<>();
        texts.forEach(text -> formats.merge(Objects.requireNonNullElse(SyslogHeaders.formatOf(text), ""), 1,
                Integer::sum));
        final Map.Entry<String, Integer> most = formats.entrySet().stream()
                .filter(entry -> !entry.getKey().isEmpty())
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (most != null && most.getValue() >= 0.8 * texts.size()) {
            final boolean rfc5424 = SampleProfiler.SYSLOG_5424_FORMAT.equals(most.getKey());
            return new HeldHeader(rfc5424
                    ? SplitterDryRun.SYSLOG_5424
                    : SplitterDryRun.SYSLOG_3164, "", rfc5424
                    ? SplitterDryRun.SYSLOG_5424_NAMES
                    : SplitterDryRun.SYSLOG_3164_NAMES, "a syslog line (" + (rfc5424
                    ? "RFC 5424"
                    : "RFC 3164") + ")", true);
        }
        final long tails = texts.stream().filter(SyslogHeaders::isTail).count();
        if (tails >= 0.8 * texts.size() && texts.stream().anyMatch(SyslogHeaders::hasStructuredData)) {
            return new HeldHeader(SyslogHeaders.TAIL_5424, "s", SyslogHeaders.TAIL_5424_NAMES, "what follows the "
                                  + "host in an RFC 5424 syslog header (app, process id, message id, structured "
                                  + "data, message)", false);
        }
        return null;
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
        // The values of each time read with no zone or offset, and the zone names a field holds: for the user to
        // say the zone in.
        private final Map<String, List<String>> timeExamples = new LinkedHashMap<>();
        private final Set<String> zoneNames = new LinkedHashSet<>();
        // The field holding zone names, for each record's zone to be read from it.
        private String zoneFieldName;
        // The fields EventTime/TimeCreated is read from, and the time fields that could be the event's (an object's
        // times aside), best first.
        private final List<String> chosenTimes = new ArrayList<>();
        private final List<String> usableTimes = new ArrayList<>();
        // A field of times of day alone (FortiOS's time=), which a date-only field may be joined with, or null.
        private String clockField;
        private Set<String> containerFields = Set.of();
        // The headers' fields around the source's own (a relay's, a collector's): their times last.
        private Set<String> relayFields = Set.of();
        private final List<Map<String, Object>> timeCandidates = new ArrayList<>();

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
            heldContinuation();
            survey();
            List<String> heldFields = heldJson();
            heldFields = heldXml(heldFields, false);
            heldFields = heldSyslog(heldFields);
            // A syslog line's message, read from the line, may hold XML too: read before its pairs (an element's
            // attributes aren't the text's pairs), and once more after them (a pair's value holding XML).
            heldFields = heldXml(heldFields, true);
            heldFields = heldPairs(heldFields);
            heldFields = heldXml(heldFields, true);
            taken.addAll(heldFields);
            wrappedLines();
            final String keyTime = keyedTime();
            final String timeField = keyTime != null
                    ? keyTime
                    : time();
            system(systemName, environment, timeField);
            homes();
            users();
            correlated();
            final Kinds kinds = kinds(sourceNotes == null
                    ? List.of()
                    : JsonValues.strings(sourceNotes.get(SourceNotes.KIND_FIELDS)));
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
                applied = SourceNotes.applyToDraft(mapping, names, values, sourceNotes, detailCheck, records);
                rest = withNotes(applied, rest);
            }
            final List<String> unusedTimes = unusedTimes(rest);
            if (!unusedTimes.isEmpty()) {
                mapping.put("ignored_fields", unusedTimes);
                rest = rest.stream().filter(name -> !unusedTimes.contains(name)).toList();
                notes.add(reprs(unusedTimes) + " only repeat the time EventTime/TimeCreated reads, in every "
                          + "record: listed in ignored_fields, so processing doesn't log them as fields no rule "
                          + "reads. Mapping one (changes=) reads it all the same.");
            }
            nilValues();
            doubledQuotes();
            repeatedKeys();
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
            if (!timeExamples.isEmpty() || !zoneNames.isEmpty()) {
                result.put(TIME_ZONES, timeZones(timeExamples, List.copyOf(zoneNames), zoneFieldName));
            }
            if (!timeCandidates.isEmpty()) {
                result.put(TIME_CANDIDATES, timeCandidates);
            }
            result.put("fields_seen", names);
            return result;
        }

        // Records whose lines after the first (a stack trace) are kept as the continuation: the exception its first
        // line names, and its message, are fields of their own through extract; the trace itself is carried as Data,
        // whole unless the user chooses to keep less ([DraftMapping#withContinuationLines]).
        @SuppressWarnings("unchecked")
        private void heldContinuation() {
            boolean any = false;
            for (final Map<String, Object> record : records) {
                if (record.get(SplitterSpec.CONTINUATION) instanceof final String text) {
                    final Matcher matcher = EXCEPTION.matcher(text);
                    if (matcher.lookingAt()) {
                        any = true;
                        record.put(EXCEPTION_FIELD, matcher.group(1));
                        if (matcher.group(2) != null) {
                            record.put(EXCEPTION_MESSAGE_FIELD, matcher.group(2));
                        }
                    }
                }
            }
            if (!any) {
                return;
            }
            ((List<Map<String, Object>>) mapping.computeIfAbsent(EXTRACT, k -> new ArrayList<>()))
                    .add(dict(FIELD, SplitterSpec.CONTINUATION, "regex", EXCEPTION_REGEX, NAMES,
                            List.of(EXCEPTION_FIELD, EXCEPTION_MESSAGE_FIELD)));
            notes.add("Records run over several lines (stack traces): the exception the trace starts with and its "
                      + "message are fields of their own (" + EXCEPTION_FIELD + ", " + EXCEPTION_MESSAGE_FIELD
                      + ", through extract), and the trace is carried whole as Data '" + SplitterSpec.CONTINUATION
                      + "'. The exception is not a kind of event: rules come from the records' own fields.");
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
                        : namedTime(name, held).orElse(JsonValues.mostCommon(counted).getFirst()));
            }
        }

        // A field named as a time whose values are all one kind of number time (an epoch time, a FILETIME): typed
        // as that time. Only by name: such widths are otherwise counts and ids.
        private Optional<String> namedTime(final String name, final List<String> held) {
            if (held.isEmpty() || !EventTimes.namedAsTime(timeKey(name))) {
                return Optional.empty();
            }
            final Set<String> patterns = new HashSet<>();
            for (final String value : held) {
                final String pattern = TimePatterns.inferNamedTimePattern(value);
                if (pattern == null || !TimePatterns.isEpoch(pattern)) {
                    return Optional.empty();
                }
                patterns.add(pattern);
            }
            return patterns.size() == 1
                    ? Optional.of(TIMESTAMP_PREFIX + patterns.iterator().next() + ")")
                    : Optional.empty();
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

        // Fields holding XML text (a SOAP payload, a syslog message): their elements are fields of their own
        // (<field>/<path>, read through xml_fields), and the text itself is never mapped. With prefixed, XML after
        // words of the source's own ("EventLog: <Event>...") too: the XML is a field of its own through extract
        // (<field>.xml), read the same way. Only once a syslog line is read into its parts: a line's PRI and header
        // are words before XML too, and the line's own.
        @SuppressWarnings("unchecked")
        private List<String> heldXml(final List<String> heldFields, final boolean prefixed) {
            final List<String> already = mapping.get("xml_fields") instanceof final List<?> held
                    ? (List<String>) held
                    : List.of();
            final Set<String> skipped = new HashSet<>(heldFields);
            skipped.addAll(already);
            skipped.addAll(EventTimes.wrappedTexts(spec));
            final List<String> candidates = names.stream()
                    .filter(n -> !skipped.contains(n) && !values.get(n).isEmpty()
                                 && already.stream().noneMatch(x -> n.startsWith(x + "/")))
                    .toList();
            final List<String> wholes = candidates.stream()
                    .filter(n -> mostly(values.get(n), DraftMapping::xmlElement))
                    .toList();
            final List<String> afterWords = prefixed
                    ? candidates.stream()
                            .filter(n -> !wholes.contains(n) && mostly(values.get(n), v ->
                                    XML_AFTER_WORDS.equals(SampleProfiler.valueType(v))))
                            .toList()
                    : List.of();
            if (wholes.isEmpty() && afterWords.isEmpty()) {
                return heldFields;
            }
            final List<String> xmlFields = new ArrayList<>(wholes);
            for (final String name : afterWords) {
                final String tail = name + ".xml";
                final Pattern pattern = ToolText.re(XML_TAIL, Pattern.DOTALL);
                for (final Map<String, Object> record : records) {
                    final Matcher found = pattern.matcher(JsonValues.text(record.get(name)));
                    if (found.find()) {
                        record.put(tail, found.group(1));
                    }
                }
                ((List<Map<String, Object>>) mapping.computeIfAbsent(EXTRACT, k -> new ArrayList<>())).add(dict(
                        FIELD, name, "regex", XML_TAIL, "flags", "s", NAMES, new ArrayList<>(List.of(tail))));
                xmlFields.add(tail);
            }
            LocalCheck.withXmlFields(new ArrayList<>(records), xmlFields);
            survey();
            final List<String> allXml = new ArrayList<>(already);
            allXml.addAll(xmlFields);
            mapping.put("xml_fields", allXml);
            final List<String> inside = names.stream()
                    .filter(n -> xmlFields.stream().anyMatch(x -> n.startsWith(x + "/") && !n.contains("//")))
                    .limit(4)
                    .toList();
            final List<String> holding = new ArrayList<>(wholes);
            holding.addAll(afterWords);
            notes.add(reprs(holding) + " " + (holding.size() == 1
                    ? "holds"
                    : "hold") + " XML" + (afterWords.isEmpty()
                    ? ""
                    : " (" + reprs(afterWords) + " after words of its own, the XML read out through extract as "
                      + reprs(afterWords.stream().map(n -> n + ".xml").toList()) + ")")
                      + ": its elements are fields of their own, e.g. " + reprs(inside)
                      + " (xml_fields; '<field>//<name>' for one anywhere below), read in the translation: not by "
                      + "regexes in the splitter or a second parser. The text itself isn't mapped.");
            final List<String> out = new ArrayList<>(heldFields);
            out.addAll(holding);
            out.addAll(xmlFields.stream().filter(n -> !out.contains(n)).toList());
            return out;
        }

        // Keys a line gives more than once (rcpt= once a recipient): each Data entry reading one writes every value
        // (repeat), not just the first.
        @SuppressWarnings("unchecked")
        private void repeatedKeys() {
            if (!(info.get(REPEATED_KEYS) instanceof final List<?> keys)) {
                return;
            }
            final List<Map<String, Object>> entries = new ArrayList<>(common);
            for (final Object rule : (List<Object>) mapping.getOrDefault(EVENTS, List.of())) {
                if (rule instanceof final Map<?, ?> map && map.get(FIELDS) instanceof final List<?> fields) {
                    fields.forEach(field -> entries.add((Map<String, Object>) field));
                }
            }
            final Set<String> repeated = new LinkedHashSet<>();
            for (final Map<String, Object> entry : entries) {
                if (entry.get(DATA_NAME) != null && keys.contains(entry.get(FIELD))) {
                    entry.put("repeat", true);
                    repeated.add((String) entry.get(FIELD));
                }
            }
            if (!repeated.isEmpty()) {
                notes.add(reprs(List.copyOf(repeated)) + " " + (repeated.size() == 1
                        ? "is"
                        : "are") + " given more than once in a record (once a value): " + (repeated.size() == 1
                        ? "its Data entry has"
                        : "their Data entries have") + " repeat: true, writing each value, not only the first.");
            }
        }

        // Whether the field's text holds pairs (two or more) that are each already a field of its record, in at
        // least four in five of the records holding it.
        private boolean pairsAlreadyRead(final String name) {
            int holding = 0;
            int read = 0;
            for (final Map<String, Object> record : records) {
                if (!(record.get(name) instanceof final String text) || text.isEmpty()) {
                    continue;
                }
                holding++;
                final Set<String> keys = pairsIn(text).keySet();
                // The same values too: not a record's own fields that a text happens to name.
                if (keys.size() >= 2 && keys.stream().allMatch(key -> !key.equals(name)
                        && record.get(key) instanceof final String value && text.contains(key + "=")
                        && text.contains(value))) {
                    read++;
                }
            }
            return holding > 0 && read >= 0.8 * holding;
        }

        // Whether at least four in five of the values fit.
        private boolean mostly(final List<String> held, final Predicate<String> fits) {
            return held.stream().filter(fits).count() >= 0.8 * held.size();
        }

        // The fields a splitter spec reads further with a body, its parts' and bodies' too.
        private static Set<String> parsedFurther(final SplitterSpec at) {
            final Set<String> out = new LinkedHashSet<>();
            if (at == null) {
                return out;
            }
            if (at.body() != null && at.bodyField() != null) {
                out.add(at.bodyField());
                out.addAll(parsedFurther(at.body()));
            }
            (at.parts() == null
                    ? List.<SplitterSpec>of()
                    : at.parts()).forEach(part -> out.addAll(parsedFurther(part)));
            return out;
        }

        // Text holding a syslog line (a shipper's JSON around the source's own line: a container runtime's log, a
        // collector's message), or what follows the host in an RFC 5424 header (a shipper that read the header up to
        // the host into keys of its own, the rest kept as one value: seen in testing): the header's parts are fields
        // of their own through extract (<field>.time, <field>.app, ... <field>.message), any pairs in them read
        // after, and the text itself isn't mapped. A line's header is the source's, so its time and host are read
        // before the wrapper's.
        @SuppressWarnings("unchecked")
        private List<String> heldSyslog(final List<String> heldFields) {
            final List<String> out = new ArrayList<>(heldFields);
            final Set<String> skipped = new HashSet<>(heldFields);
            skipped.addAll(EventTimes.wrappedTexts(spec));
            // Nor text the splitter reads further itself (seen in testing: a mail gateway's message, its words and
            // pairs already fields, read again as a header's app, procid and msgid: `message.pid` 2026-11-10).
            skipped.addAll(parsedFurther(spec));
            final List<String> derived = new ArrayList<>();
            for (final String name : List.copyOf(names)) {
                // Text, or text ending in XML (a syslog line whose message is XML).
                final HeldHeader header = skipped.contains(name) || !"string".equals(type(name))
                                          && !XML_AFTER_WORDS.equals(type(name))
                        ? null
                        : heldHeader(values.get(name));
                if (header == null) {
                    continue;
                }
                final List<String> fields = header.names().stream().map(part -> name + "." + part).toList();
                if (fields.stream().anyMatch(names::contains)) {
                    // Names the record already has: not read again.
                    continue;
                }
                final Pattern pattern = ToolText.re(header.regex(), header.flags().contains("s")
                        ? Pattern.DOTALL
                        : 0);
                for (final Map<String, Object> record : records) {
                    final Matcher found = pattern.matcher(JsonValues.text(record.get(name)));
                    if (found.find()) {
                        for (int n = 1; n <= fields.size(); n++) {
                            if (found.group(n) != null && !ToolText.isBlank(found.group(n))) {
                                record.put(fields.get(n - 1), found.group(n));
                            }
                        }
                    }
                }
                final Map<String, Object> extract = dict(FIELD, name, "regex", header.regex(), NAMES,
                        new ArrayList<>(fields));
                if (!header.flags().isEmpty()) {
                    extract.put("flags", header.flags());
                }
                ((List<Map<String, Object>>) mapping.computeIfAbsent(EXTRACT, k -> new ArrayList<>())).add(extract);
                derived.addAll(fields);
                out.add(name);
                notes.add("'" + name + "' holds " + header.what() + ": its parts are fields of their own through "
                          + "extract (" + reprs(fields) + "), read before the record's other fields"
                          + (header.line()
                        ? " (the line's header is the source's, so its time and host come before the wrapper's)"
                        : "") + ". The text itself isn't mapped.");
            }
            if (!derived.isEmpty()) {
                // First in each record, so a name read by order (the time, the host) is the source's own.
                for (final Map<String, Object> record : records) {
                    final Map<String, Object> rest = new LinkedHashMap<>(record);
                    record.clear();
                    derived.stream().filter(rest::containsKey).forEach(field -> record.put(field, rest.get(field)));
                    rest.forEach(record::putIfAbsent);
                }
                survey();
            }
            return out;
        }

        // The splitter's text that is a whole line it reads further as a syslog header (a relay's message holding
        // the source's line, a collector's line after its words): all of it is in the fields read from it.
        private void wrappedLines() {
            final List<String> wrapped = EventTimes.wrappedTexts(spec).stream().filter(names::contains).toList();
            if (wrapped.isEmpty()) {
                return;
            }
            taken.addAll(wrapped);
            final List<String> relays = EventTimes.relayFields(spec).stream()
                    .filter(name -> names.contains(name) && !wrapped.contains(name))
                    .toList();
            notes.add("The source's syslog line is wrapped (a relay's syslog header, or a collector's words, before "
                      + "it): " + reprs(wrapped) + " " + (wrapped.size() == 1
                    ? "is"
                    : "are") + " the source's whole line, read into its fields, and isn't mapped; the wrapper's "
                      + "fields (" + reprs(relays) + ") are read after the source's own (its time last), and left "
                      + "as Data.");
        }

        // Text holding key/value pairs: each key a field of its own, through extract (keys alike are written as
        // one function, called with the key), and so homed, ruled on and carried as Data like any field. The text
        // itself isn't mapped when every value had pairs.
        @SuppressWarnings("unchecked")
        private List<String> heldPairs(final List<String> heldFields) {
            final List<String> out = new ArrayList<>(heldFields);
            final Set<String> skipped = new HashSet<>(heldFields);
            // A relay's message, or a collector's line: the source's whole line, its pairs read from its own message.
            skipped.addAll(EventTimes.wrappedTexts(spec));
            // The text the splitter read its pairs from (a syslog line's key=value message): its pairs are fields
            // already, so it isn't read again (seen in testing: every key twice, the second read short) or mapped.
            final List<String> read = names.stream()
                    .filter(name -> !skipped.contains(name) && pairsAlreadyRead(name))
                    .toList();
            if (!read.isEmpty()) {
                skipped.addAll(read);
                out.addAll(read);
                notes.add(reprs(read) + " " + (read.size() == 1
                        ? "is"
                        : "are") + " the text the splitter read its key/value pairs from: they are fields "
                          + "already, so the text isn't read again or mapped.");
            }
            final Map<String, PairExtracts> paired = pairExtracts(values, skipped);
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

        // Records of lines tied by a key (auditd's msg=audit(1791709234.390:4506):): the key and each line's copy of
        // it are not fields of the event; an epoch in every key (the envelope's time) is the event's time. Returns
        // the key field when it gives the time, else null.
        private String keyedTime() {
            if (spec == null || spec.recordKey() == null) {
                return null;
            }
            final String keyField = spec.keyField();
            names.stream()
                    .filter(name -> name.equals(keyField) || name.endsWith("/" + keyField))
                    .forEach(taken::add);
            if (spec.lineName() != null) {
                // Each line's own name, under itself (SYSCALL/type): the record's is the lead field.
                names.stream().filter(name -> name.endsWith("/" + spec.leadField())).forEach(taken::add);
            }
            final List<String> keys = values.getOrDefault(keyField, List.of()).stream()
                    .filter(value -> !ToolText.isBlank(value))
                    .toList();
            if (keys.isEmpty() || !keys.stream().allMatch(key -> ToolText.search(KEY_EPOCH, key))) {
                notes.add("Each record is the lines holding one '" + keyField + "' (left out of the fields, as each "
                          + "line's copy is); it holds no time: map EventTime/TimeCreated from a line's field.");
                return null;
            }
            common.add(dict(PATH, "EventTime/TimeCreated", "xpath", "replace(" + selector(mapping, keyField)
                                                                     + ", '" + KEY_EPOCH_REPLACE + "', '$1')",
                    "time_format", "epoch_s"));
            notes.add("Each record is the lines holding one '" + keyField + "' (e.g. " + keys.getFirst() + "): its "
                      + "epoch time is EventTime/TimeCreated, and it and each line's copy are left out of the fields; "
                      + "add the rest of it (an event serial) as Data if the events need it.");
            return keyField;
        }

        // Time: the timestamp-typed fields ranked by what their names say (an event's time, then one that may be,
        // then one whose name says nothing; an object's never), the record's own before its container's (a syslog
        // header's), in the order seen; a date-only field beside a time-of-day field (FortiOS's date= and time=)
        // is joined. The container's time is the fallback for records without their own.
        private String time() {
            containerFields = EventTimes.containerFields(spec);
            relayFields = EventTimes.relayFields(spec);
            final List<String> stamps = names.stream().filter(n -> type(n).startsWith(TIMESTAMP)).toList();
            stamps.stream().filter(n -> EventTimes.rank(timeKey(n)) != EventTimes.Rank.OBJECT)
                    .sorted(Comparator.comparingInt(this::timeOrder))
                    .forEach(usableTimes::add);
            String timeField = usableTimes.isEmpty()
                    ? null
                    : usableTimes.getFirst();
            final String first = timeField;
            boolean dateOnly = timeField != null && !type(timeField).contains("H") && !type(timeField).contains("h")
                               && !type(timeField).contains("filetime");
            String clock = names.stream()
                    .filter(n -> !n.equals(first) && !values.get(n).isEmpty()
                                 && values.get(n).stream().allMatch(v -> ToolText.fullmatch(CLOCK, ToolText.strip(v))))
                    .findFirst()
                    .orElse(null);
            clockField = clock;
            final boolean chosenPrecise = timeField != null && dateOnly && clock != null
                    ? subSecond(clock)
                    : subSecond(timeField);
            if (timeField != null && !chosenPrecise) {
                // Not a container's time over the record's own.
                final String precise = usableTimes.stream()
                        .filter(n -> !n.equals(first) && subSecond(n)
                                     && (type(n).contains("H") || type(n).contains("epoch"))
                                     && (containerFields.contains(first) || !containerFields.contains(n))
                                     && (relayFields.contains(first) || !relayFields.contains(n)))
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
                chosenTimes.addAll(List.of(timeField, clock));
            } else if (timeField != null) {
                timeField = ownOrContainersTime(timeField);
                candidatesOf(timeField, records, null, common.stream()
                        .filter(e -> "EventTime/TimeCreated".equals(e.get(PATH)))
                        .findFirst()
                        .orElse(null));
            }
            timesNote(stamps);
            timeZoneField();
            if (timeField == null) {
                // Seen: an else on the zone test above, so a draft that mapped EventTime said no timestamp was
                // recognised.
                notes.add("No timestamp field was recognised: map EventTime/TimeCreated yourself (time_format from "
                          + "the profile).");
            }
            return timeField;
        }

        // A field's own name for what it says of a time: an XML Data element's Name (Data[@Name='lastLogon']), else
        // its key.
        private static String timeKey(final String name) {
            // Without a byte order mark the file's first name carries (seen: a BOM before 'time').
            return FieldHomes.keys(name).getFirst().replace(BYTE_ORDER_MARK, "");
        }

        // A time to the second or finer: not a date alone.
        private boolean fullTime(final String name) {
            final String type = type(name);
            return type.contains("H") || type.contains("h") || type.contains("epoch") || type.contains("filetime");
        }

        // Where the records' time is first in the order: an event's time, one that may be, one whose name says
        // nothing; the record's own before its container's; a container's without a year (RFC 3164's) after any
        // of the record's own; a date beside a time of day as an event's time.
        private int timeOrder(final String name) {
            EventTimes.Rank rank = EventTimes.rank(timeKey(name));
            final String type = type(name);
            final boolean dateOnly = !type.contains("H") && !type.contains("h") && !type.contains("epoch")
                                     && !type.contains("filetime");
            if (dateOnly && names.stream().anyMatch(n -> !n.equals(name) && clockField(n))) {
                rank = EventTimes.Rank.EVENT;
            }
            final boolean container = containerFields.contains(name);
            if (container && !type.contains("y") && !type.contains("epoch") && !type.contains("filetime")) {
                rank = EventTimes.Rank.PLAIN;
            }
            // A date alone, with no time of day to join, after every full time.
            final int dateAlone = dateOnly && rank != EventTimes.Rank.EVENT
                    ? 100
                    : 0;
            // A relay's or collector's time after any of the source's own: when it was passed on, not when it
            // happened.
            final int relay = relayFields.contains(name)
                    ? 50
                    : 0;
            return dateAlone + relay + rank.ordinal() * 2 + (container
                    ? 1
                    : 0);
        }

        private boolean clockField(final String name) {
            return !values.get(name).isEmpty()
                   && values.get(name).stream().allMatch(v -> ToolText.fullmatch(CLOCK, ToolText.strip(v)));
        }

        // The record's own time, with its container's for records without one, in one format reading both; where
        // none can (a number beside a pattern), the container's for every record. Returns the field chosen first.
        private String ownOrContainersTime(final String timeField) {
            final String outer = containerFields.contains(timeField)
                    ? null
                    : usableTimes.stream()
                            .filter(n -> containerFields.contains(n) && timeOrder(n) % 2 == 1
                                         && timeOrder(n) / 2 != EventTimes.Rank.PLAIN.ordinal())
                            .findFirst()
                            .orElse(null);
            if (outer == null) {
                fieldTime(timeField);
                return timeField;
            }
            final List<String> own = covering(timeField, n -> type(timeField).equals(type(n))
                                                              && !containerFields.contains(n)
                                                              && timeOrder(n) == timeOrder(timeField));
            final List<String> both = new ArrayList<>(values.get(outer));
            own.forEach(n -> both.addAll(values.get(n)));
            final String format = TimePatterns.combined(List.of(patternOf(type(timeField)), patternOf(type(outer))),
                    both);
            if (format == null) {
                notes.add("'" + timeField + "' (" + formatName(timeField) + ") and '" + outer + "' (" + formatName(
                        outer) + ") can't be read in one format, so EventTime/TimeCreated is '" + outer + "', the "
                          + "container's time, for every record; '" + timeField + "' is left as Data.");
                fieldTime(outer);
                return outer;
            }
            final List<String> fields = new ArrayList<>(own);
            fields.add(outer);
            final Map<String, Object> entry = dict(PATH, "EventTime/TimeCreated", ANY_OF, fields, "time_format",
                    TimePatterns.EPOCH_NAMES.getOrDefault(format, format));
            for (final String field : fields) {
                final String pattern = patternOf(type(field));
                if (!TimePatterns.isEpoch(pattern) && !TimePatterns.hasZone(pattern)) {
                    timeExamples.put(field, values.get(field).stream().limit(5).toList());
                    notes.add("'" + field + "' has no time zone or offset. " + NO_ZONE);
                }
            }
            common.add(entry);
            taken.addAll(fields);
            chosenTimes.addAll(fields);
            return timeField;
        }

        // The time fields EventTime/TimeCreated doesn't read, nor anything else (in rest), that only say again the
        // time it reads: in every record, the same instant (or the same shifted by a whole zone offset, a local copy
        // of a UTC time), a date and a time of day together counted as one. Asked for by the user (seen in testing:
        // a proxy's date and time columns, left unread beside its x-cs-timestamp, were logged to the Error streams as
        // fields no rule reads). A time saying something else (a session's start, a log's receipt) stays to be
        // mapped, as does one named as a start or a creation (the sample's values may agree by chance), and any where
        // a record lacks the time EventTime reads.
        private List<String> unusedTimes(final List<String> rest) {
            if (chosenTimes.isEmpty()
                || !records.stream().allMatch(r -> chosenTimes.stream().anyMatch(f -> EventTimes.held(r, f)))) {
                return List.of();
            }
            final List<String> unused = new ArrayList<>();
            for (final String name : usableTimes) {
                if (chosenTimes.contains(name) || !rest.contains(name)) {
                    continue;
                }
                // A time named as a period's start or a creation may say something else, the sample's values aside.
                if (fullTime(name) && EventTimes.rank(timeKey(name)) != EventTimes.Rank.AMBIGUOUS
                    && sameTime(List.of(name))) {
                    unused.add(name);
                } else if (!fullTime(name) && clockField != null && !chosenTimes.contains(clockField)
                           && rest.contains(clockField) && sameTime(List.of(name, clockField))) {
                    unused.add(name);
                    unused.add(clockField);
                }
            }
            return unused.stream().distinct().toList();
        }

        // Whether these fields (one time, or a date and a time of day) hold the time EventTime reads in every record
        // that has them: the same instant, or one shifted by the same whole quarter hours in each.
        private boolean sameTime(final List<String> fields) {
            Long shift = null;
            int compared = 0;
            for (final Map<String, Object> record : records) {
                if (fields.stream().noneMatch(f -> EventTimes.held(record, f))) {
                    continue;
                }
                final Long theirs = instant(record, fields);
                final Long chosen = instant(record, chosenTimes.size() == 2 && !fullTime(chosenTimes.getFirst())
                        ? chosenTimes
                        : chosenTimes.stream().filter(f -> EventTimes.held(record, f)).limit(1).toList());
                if (theirs == null || chosen == null) {
                    return false;
                }
                // To the second: one may carry a fraction the other doesn't.
                final long gap = theirs / 1_000L - chosen / 1_000L;
                if (shift == null) {
                    shift = gap;
                }
                if (gap != shift || gap % QUARTER_HOUR_SECONDS != 0 || Math.abs(gap) > MAX_OFFSET_SECONDS) {
                    return false;
                }
                compared++;
            }
            return compared > 0;
        }

        // A record's time from one field, or a date and a time of day joined; null if it doesn't parse.
        private Long instant(final Map<String, Object> record, final List<String> fields) {
            if (fields.isEmpty() || fields.stream().anyMatch(f -> !EventTimes.held(record, f))) {
                return null;
            }
            if (fields.size() == 1) {
                return TimePatterns.epochMillis(patternOf(type(fields.getFirst())), String.valueOf(record.get(
                        fields.getFirst())));
            }
            final String clock = ToolText.strip(String.valueOf(record.get(fields.get(1)))).split("\\.", 2)[0];
            return TimePatterns.epochMillis(patternOf(type(fields.getFirst())) + " HH:mm" + (ToolText.count(clock,
                    ":") == 2
                    ? ":ss"
                    : ""), ToolText.strip(String.valueOf(record.get(fields.getFirst()))) + " " + clock);
        }

        // The time_format of a time field.
        private String formatName(final String name) {
            final String pattern = patternOf(type(name));
            return TimePatterns.EPOCH_NAMES.getOrDefault(pattern, pattern);
        }

        // Two or more times each as likely the event's as the one chosen, each in nearly every record and not the
        // same values: put to the agent, with what tells them apart (B10). rule is null for the whole sample.
        private void candidatesOf(final String chosen,
                                  final List<Map<String, Object>> scope,
                                  final String rule,
                                  final Map<String, Object> entry) {
            final List<String> alike = usableTimes.stream()
                    .filter(n -> timeOrder(n) == timeOrder(chosen) && !n.equals(chosen) && fullTime(n)
                                 && fullTime(chosen) && EventTimes.share(n, scope) >= CANDIDATE_SHARE
                                 && type(n).contains("H") == type(chosen).contains("H")
                                 && scope.stream().anyMatch(r -> EventTimes.held(r, n) && EventTimes.held(r, chosen)
                                                                 && !r.get(n).equals(r.get(chosen))))
                    .toList();
            if (alike.isEmpty() || (rule == null && EventTimes.share(chosen, scope) < CANDIDATE_SHARE)) {
                return;
            }
            final List<String> fields = new ArrayList<>(List.of(chosen));
            fields.addAll(alike);
            final Map<String, String> formats = new LinkedHashMap<>();
            final Map<String, String> keys = new LinkedHashMap<>();
            fields.forEach(n -> {
                formats.put(n, formatName(n));
                keys.put(n, timeKey(n));
            });
            final Map<String, Object> candidate = new LinkedHashMap<>();
            if (rule != null) {
                candidate.put("rule", rule);
            }
            candidate.put(FIELDS, EventTimes.evidence(fields, formats, scope, keys));
            timeCandidates.add(candidate);
            if (entry != null) {
                // Asked for by the user: the choice can't be left unmade (seen: the agent kept the draft's first
                // without a word).
                entry.put(CHOOSE_FROM, new ArrayList<>(fields));
            }
            notes.add((rule == null
                    ? "The records' "
                    : "Rule '" + rule + "': its records' ") + reprs(fields) + " could each be the event's time; "
                      + "drafted with '" + chosen + "', its entry marked " + CHOOSE_FROM + ", which "
                      + "build_translation_xslt refuses until you choose. " + TIME_CANDIDATES + " says what tells "
                      + "them apart: choose one (changes=: the entry with its field, without " + CHOOSE_FROM + "), "
                      + "say which and why, and ask the user if unsure.");
        }

        // A kind whose records lack the common time: its own, from the times its records hold, best first, and a
        // choice put to the agent where two are alike.
        private void kindTime(final Map<String, Object> rule, final List<Map<String, Object>> ofKind) {
            if (chosenTimes.isEmpty() || ofKind.isEmpty()
                || ofKind.stream().allMatch(r -> chosenTimes.stream().anyMatch(f -> EventTimes.held(r, f)))) {
                return;
            }
            final String best = usableTimes.stream()
                    .filter(n -> !chosenTimes.contains(n) && ofKind.stream().anyMatch(r -> EventTimes.held(r, n)))
                    .findFirst()
                    .orElse(null);
            if (best == null) {
                return;
            }
            final String name = (String) rule.get("name");
            final Map<String, Object> entry = dict(PATH, "EventTime/TimeCreated", FIELD, best, "time_format",
                    formatName(best));
            final List<Map<String, Object>> fields = new ArrayList<>(JsonValues.maps(rule.get(FIELDS)));
            fields.removeIf(field -> best.equals(field.get(FIELD)));
            fields.addFirst(entry);
            rule.put(FIELDS, fields);
            final String pattern = patternOf(type(best));
            if (!TimePatterns.isEpoch(pattern) && !TimePatterns.hasZone(pattern)) {
                timeExamples.put(best, values.get(best).stream().limit(5).toList());
            }
            notes.add("Rule '" + name + "': its records have no " + reprs(chosenTimes) + ", so its time is '" + best
                      + "'.");
            candidatesOf(best, ofKind, name, entry);
        }

        // Every timestamp field, what it is taken for, and which EventTime/TimeCreated reads (B11): when there is
        // more than one, or the one chosen may not be the event's.
        private void timesNote(final List<String> stamps) {
            final boolean sure = chosenTimes.stream()
                    .allMatch(n -> EventTimes.rank(timeKey(n)) == EventTimes.Rank.EVENT || timeOrder(n) / 2 == 0);
            if (stamps.size() < 2 && sure) {
                return;
            }
            final List<String> said = new ArrayList<>();
            for (final String name : stamps) {
                final String where = containerFields.contains(name)
                        ? "the container's, "
                        : containerFields.isEmpty()
                                ? ""
                                : "the record's own, ";
                said.add("'" + name + "' (" + where + EventTimes.rank(timeKey(name)).said() + ", in "
                         + EventTimes.share(name, records) + "% of records, " + formatName(name) + ")");
            }
            notes.add("Timestamp fields: " + String.join("; ", said) + ". EventTime/TimeCreated reads " + (chosenTimes
                    .isEmpty()
                    ? "none of them"
                    : reprs(chosenTimes)) + ".");
        }

        private void joinedTime(final String timeField, final String clock) {
            final String datePattern = patternOf(type(timeField));
            final List<String> clocks = values.get(clock);
            String seconds = clocks.stream().allMatch(v -> ToolText.count(v, ":") == 2)
                    ? ":ss"
                    : "";
            // A fraction of a second the clock values carry is kept, at their width.
            final Set<Integer> widths = new LinkedHashSet<>();
            clocks.stream().filter(v -> v.contains("."))
                    .forEach(v -> widths.add(ToolText.len(ToolText.strip(v).split("\\.", 2)[1])));
            if (!seconds.isEmpty() && widths.size() == 1
                && clocks.stream().filter(v -> !ToolText.isBlank(v)).allMatch(v -> v.contains("."))) {
                seconds += "." + "S".repeat(widths.iterator().next());
            }
            common.add(dict(PATH, "EventTime/TimeCreated",
                    "xpath", "concat(" + selector(mapping, timeField) + ", ' ', " + selector(mapping, clock) + ")",
                    "time_format", datePattern + " HH:mm" + seconds));
            taken.add(timeField);
            taken.add(clock);
            notes.add("'" + timeField + "' and '" + clock + "' are joined for EventTime/TimeCreated. " + NO_ZONE);
        }

        @SuppressWarnings("unchecked")
        private void fieldTime(final String timeField) {
            final String pattern = patternOf(type(timeField));
            final Map<String, Object> entry = dict(PATH, "EventTime/TimeCreated", FIELD, timeField);
            final String timeType = type(timeField);
            // Only times alike: an object's never, nor a container's beside the record's own.
            final List<String> covered = covering(timeField, n -> timeType.equals(type(n))
                                                                  && usableTimes.contains(n)
                                                                  && timeOrder(n) == timeOrder(timeField));
            readFrom(entry, covered);
            chosenTimes.addAll(covered);
            if (TimePatterns.EPOCH_NAMES.containsKey(pattern)) {
                entry.put("time_format", TimePatterns.EPOCH_NAMES.get(pattern));
            } else {
                entry.put("time_format", pattern);
                if (!TimePatterns.hasZone(pattern)) {
                    // The zone the source writes in is the user's to say: never assumed (asked for by the user).
                    timeExamples.put(timeField, values.get(timeField).stream().limit(5).toList());
                    notes.add("'" + timeField + "' has no time zone or offset. " + NO_ZONE);
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
                                 && values.get(n).stream().filter(v -> !ToolText.isBlank(v))
                                         .allMatch(v -> ToolText.fullmatch(ZONE_NAME, ToolText.strip(v))
                                                        // A zone Stroom reads, not any Word/Word (seen: Windows'
                                                        // CategoryId, Logon/Logoff, taken for zone names).
                                                        && !ZoneSource.unreadable(ToolText.strip(v)))
                                 && values.get(n).stream().anyMatch(v -> !ToolText.isBlank(v)))
                    .findFirst()
                    .orElse(null);
            if (zoneField != null && common.stream().noneMatch(e -> TIME_ZONE_PATH.equals(e.get(PATH)))) {
                // A field of IANA zone names (by its values): the zone the source's times are in, kept with the
                // event.
                common.add(dict(PATH, TIME_ZONE_PATH, FIELD, zoneField));
                taken.add(zoneField);
                zoneFieldName = zoneField;
                zoneNames.addAll(new LinkedHashSet<>(values.get(zoneField).stream()
                        .filter(v -> !ToolText.isBlank(v))
                        .map(ToolText::strip)
                        .toList()));
                notes.add("'" + zoneField + "' holds time zone names: kept as " + TIME_ZONE_PATH + "; if the times "
                          + "have no zone of their own, read them in it (timezone on EventTime/TimeCreated).");
            }
        }

        // Only what the user (or a standing instruction) gives: seen in testing, the source's name went in as the
        // System/Name unasked, and the user was never asked for either. A placeholder left in is refused on saving.
        private void system(final String systemName, final String environment, final String timeField) {
            common.add(dict(PATH, "EventSource/System/Name", VALUE, firstText(systemName, null,
                    SYSTEM_NAME_PLACEHOLDER)));
            common.add(dict(PATH, "EventSource/System/Environment", VALUE, firstText(environment, null,
                    ENVIRONMENT_PLACEHOLDER)));
            if (!LocalCheck.hasText(systemName) || !LocalCheck.hasText(environment)) {
                notes.add("Ask the user for EventSource/System/Name and Environment (unless the standing "
                          + "instructions give them): the draft has placeholders, which saving refuses. Both are "
                          + "required: if the user doesn't know one, use a value they agree to (such as Unknown) and "
                          + "record it as an open item, rather than stopping.");
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
                // A name that is a user only by its values (subject) fits the user home when they read as one.
                final boolean byValue = FieldHomes.USER_ID.equals(home.path());
                final Predicate<String> fits = n -> !"empty".equals(type(n)) && (FieldHomes.matches(home.regex(), n)
                        || byValue && FieldHomes.matches(FieldHomes.USER_BY_VALUE, n)
                           && FieldHomes.readsAsIdentifiers(values.get(n)));
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

        // Several user fields in a record (who acted, who it was done to): the actor's in EventSource/User where
        // their names say which is which (Subject*, Target*), and the agent asked to decide kind by kind.
        private void users() {
            final List<String> named = names.stream()
                    .filter(n -> !"empty".equals(type(n)) && !n.contains("//") && FieldHomes.keys(n).stream()
                            .anyMatch(k -> ToolText.search(USER_FIELD, k)))
                    .toList();
            // Only fields in a record together: one user in two shapes' fields (user, message.user) isn't several.
            final List<String> users = named.stream()
                    .filter(n -> records.stream().anyMatch(r -> JsonValues.truthy(r.get(n)) && named.stream()
                            .anyMatch(m -> !m.equals(n) && JsonValues.truthy(r.get(m)))))
                    .toList();
            if (users.size() < 2) {
                return;
            }
            final Map<String, Object> user = common.stream()
                    .filter(e -> "EventSource/User/Id".equals(e.get(PATH)) && e.get(FIELD) != null)
                    .findFirst()
                    .orElse(null);
            final String actor = users.stream().filter(n -> role(n).equals("actor")).findFirst().orElse(null);
            if (user != null && actor != null && !"actor".equals(role((String) user.get(FIELD)))) {
                // The field it replaces is left for the rules (as Data until the agent places it), not lost.
                taken.remove((String) user.get(FIELD));
                taken.add(actor);
                user.put(FIELD, actor);
            }
            final List<String> said = users.stream().map(n -> "'" + n + "' (" + role(n) + ")").toList();
            notes.add("Records hold several user fields: " + String.join(", ", said) + ". EventSource/User is who "
                      + "acted" + (user == null
                    ? ""
                    : " (drafted as '" + user.get(FIELD) + "')") + "; for each kind, decide which field is the "
                      + "actor and which the user or account acted on, and map that one in the action element (e.g. "
                      + "Authenticate/User, or the object a Create, Update or Delete names), not as Data.");
        }

        // Records tied by a correlation id that two or more of them share (one change written as several records):
        // each mapped alone first, as drafted; pairing them is the agent's to propose once it knows the source.
        private void correlated() {
            for (final String name : names) {
                if (FieldHomes.keys(name).stream().noneMatch(k -> ToolText.search(CORRELATION, k))) {
                    continue;
                }
                final List<String> held = values.get(name);
                final long shared = JsonValues.counted(held).values().stream().filter(count -> count > 1).count();
                if (shared > 0) {
                    notes.add("'" + name + "' ties records together (" + shared + " of its values are in more than "
                              + "one record): such records may be one change written as several (a value removed "
                              + "and a value added, a request and its response). Each is mapped alone as drafted; "
                              + "keep the id as Data, and if the user wants them as one event, say how they pair "
                              + "before changing the rules.");
                    return;
                }
            }
        }

        // A user field's role by its name's prefix: actor, affected, or unsaid.
        private static String role(final String name) {
            for (final String key : FieldHomes.keys(name)) {
                if (ToolText.search(ACTOR, key)) {
                    return "actor";
                }
                if (ToolText.search(AFFECTED, key)) {
                    return "affected";
                }
            }
            return "unsaid";
        }

        // What the notes say of the event's message: where the draft put it, or, if it found none, a field of free text
        // that may be it (seen in eval cases 22, 23 and 42, with neither said: the message, a CEF msg or a ticket's
        // comment, went into a Document's Description or a Data entry).
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
            // By its own name or with its parent's (Winlogbeat's event.code is event_code).
            // And by its values: short codes or phrases, not messages or addresses (seen in testing: a Synology DSM
            // `event` field of sentences naming the user, drafted as a kind per user and action).
            return FieldHomes.keys(name).stream().anyMatch(k -> ToolText.match(NAMING, k)) && !kinds.isEmpty()
                   && kinds.size() <= MAX_KINDS && repeats(name, kinds.size())
                   && KindFields.messagesNotKinds(name, kinds).isEmpty();
        }

        // Past 64 values, kinds repeat: each value in two records or more on average. Seen when the limit was
        // raised to 200: a mail log's msgid (a message's id, a value a record) drafted as 200 kinds.
        private boolean repeats(final String name, final int kinds) {
            final long held = values.get(name).stream().filter(value -> !NIL.contains(value)).count();
            return kinds <= FEW_KINDS || kinds * 2L <= held;
        }

        // Whether each of the field's values is in one record only, over EACH_ONCE_RECORDS records or more: an
        // identifier's shape, such as a message's id. Fewer records each of another kind is usual (a firewall's
        // TRAFFIC and LOGIN), and a short excerpt may show each kind once (an event id each once), so it is only a
        // preference.
        private boolean eachOnce(final String name) {
            final List<String> held = values.get(name).stream()
                    .filter(value -> value != null && !NIL.contains(value))
                    .toList();
            return held.size() >= EACH_ONCE_RECORDS && new HashSet<>(held).size() == held.size();
        }

        // Seen: syslog's msgid, always '-', taken over an action field: a field with only nil values names no kind.
        // One number, the same in every record, names no kind (a word may: SYSTEM, its actions split in another
        // field). Seen: a Windows EventID always 1000 taken over the category in the text's pairs (Event: [User],
        // [Secret]...).
        private boolean oneNumber(final String name) {
            final Set<String> kinds = new LinkedHashSet<>(values.get(name));
            kinds.removeAll(NIL);
            return kinds.size() == 1 && !ToolText.search(LETTER, kinds.iterator().next());
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

        // The CEF or LEEF header field with the most kinds (more than one, no more than rules are drafted for).
        private String headerKinds() {
            return HEADER_KINDS.stream()
                    .filter(n -> names.contains(n) && !taken.contains(n))
                    .filter(n -> {
                        final int count = kindCount(n);
                        return count > 1 && count <= MAX_KINDS;
                    })
                    .max(Comparator.comparingInt(this::kindCount))
                    .orElse(null);
        }

        private int kindCount(final String name) {
            final Set<String> kinds = new HashSet<>(values.get(name));
            kinds.removeAll(NIL);
            return kinds.size();
        }

        // The kinds: by the kind fields the user chose (given: classify_kinds and survey_feed put the fields that could
        // be to them, where none is named like one), else by the fields named like kind fields.
        private Kinds kinds(final List<String> given) {
            if (KindFields.oneKind(given)) {
                return kindsBy(List.of());
            }
            // The user's fields as the records spell them; none of them here (a variant without them, a name
            // mistyped), the kinds are chosen by name as before rather than none.
            final List<String> present = given.stream()
                    .flatMap(g -> names.stream().filter(n -> n.equalsIgnoreCase(g) && !taken.contains(n)).limit(1))
                    .distinct()
                    .toList();
            if (!present.isEmpty()) {
                return kindsBy(present);
            }
            if (!given.isEmpty()) {
                // Said, not chosen another way unsaid (seen in testing: a kind field the records didn't have as such,
                // and the kinds silently taken from other fields).
                notes.add("The kind field(s) " + given + " chosen for these records aren't fields of theirs as read "
                          + "here, so the kinds below are by the fields named like kinds: check the kind field with "
                          + "the user (classify_kinds kind_field=).");
            }
            String naming = headerKinds();
            if (naming == null) {
                // A naming field whose values repeat before one with a value a record: seen in testing, a mail log's
                // msgid (each message's own id) drafted as the kinds over its action (deliver, reject...).
                naming = names.stream()
                        .filter(n -> !taken.contains(n) && namesKinds(n) && !oneNumber(n) && !eachOnce(n))
                        .findFirst()
                        .orElse(null);
            }
            if (naming == null) {
                naming = names.stream()
                        .filter(n -> !taken.contains(n) && namesKinds(n) && !oneNumber(n))
                        .findFirst()
                        .orElse(null);
            }
            if (naming == null) {
                naming = names.stream().filter(n -> !taken.contains(n) && namesKinds(n)).findFirst().orElse(null);
            }
            return kindsBy(naming == null
                    ? List.of()
                    : covering(naming, this::namesKinds));
        }

        // The kinds, each a value of the naming fields: rules a kind, and the fields left for Data.
        private Kinds kindsBy(final List<String> namings) {
            if (!namings.isEmpty()) {
                common.add(readFrom(dict(PATH, "EventDetail/TypeId", FIELD, namings.getFirst()), namings));
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
                JsonValues.mostCommon(JsonValues.counted(own)).stream().limit(MAX_KINDS)
                        .forEach(v -> pairs.add(new String[]{n, v}));
            }
            final List<String> kinds = pairs.stream().map(pair -> pair[1]).toList();
            final List<Map<String, Object>> rules = new ArrayList<>();
            final List<String> recognised = new ArrayList<>();
            final Set<String> usedNames = new HashSet<>();
            final Object user = commonSource("EventSource/User/Id");
            final Object message = commonSource("EventDetail/Description");
            for (final String[] pair : pairs) {
                final int before = rules.size();
                kindRules(pair[0], pair[1], rest, user, message, usedNames, rules, recognised);
                if (rules.size() > before) {
                    kindTime(rules.getLast(), records.stream().filter(r -> pair[1].equals(r.get(pair[0]))).toList());
                }
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
            String name = ToolText.strip(NOT_NAME.matcher(kind).replaceAll("_"), "_").toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                name = "event";
            }
            while (usedNames.contains(name)) {
                name += "_";
            }
            usedNames.add(name);
            final List<Map<String, Object>> ofKind = records.stream().filter(r -> kind.equals(r.get(naming))).toList();
            // A kind named as an authentication. No product's codes are known here (asked for by the user: the tools
            // hold no one source's handling): what a code means comes from the agent, the user's documentation
            // (source notes) and the user.
            if (ToolText.search(AUTH, kind)) {
                final String action = ToolText.search(LOGOFF, kind)
                        ? "Logoff"
                        : "Logon";
                final List<Map<String, Object>> fields = new ArrayList<>();
                fields.add(dict(PATH, "EventDetail/Authenticate/Action", VALUE, action));
                if (JsonValues.truthy(user)) {
                    final Map<String, Object> entry = dict(PATH, "EventDetail/Authenticate/User/Id");
                    entry.putAll(ActionRules.read(user));
                    fields.add(entry);
                }
                fields.addAll(dataEntries("Authenticate", held(ofKind, rest)));
                rules.add(kindRule(name, naming, kind, fields));
                return;
            }
            // The kind's own records often show its actions in another field (a firewall's TRAFFIC: action ALLOW
            // or DENY; ADMIN: LOGIN_SUCCESS, LOGOUT, CONFIG_CHANGE): a rule per action, before the kind's own rule.
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
                for (final String n : held(taken(ofKind, foundRule), rest)) {
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
                final List<String> kept = held(ofKind, rest);
                // Its records holding none of the rest, the kind field: a rule with no action element fails the
                // mapping (seen with event phrases, the message mapped to Description and nothing left).
                rules.add(kindRule(name, naming, kind, dataEntries("Unknown", kept.isEmpty()
                        ? List.of(naming)
                        : kept)));
            }
        }

        // The fields of the rest a rule's records hold: what its Data carries (seen in testing, with every rule
        // given the whole rest: 23 SecretServer rules, each with the same 63 Data entries, a logon's
        // checkout_id and old_role among them, kept by the agent beside the elements it then mapped them to).
        private static List<String> held(final List<Map<String, Object>> taken, final List<String> rest) {
            return rest.stream()
                    .filter(n -> taken.stream()
                            .anyMatch(r -> r.get(n) != null && !ToolText.isBlank(ToolText.str(r.get(n)))))
                    .toList();
        }

        // The records a drafted rule takes, by its conditions (each a field equal to a value, or one of values).
        @SuppressWarnings("unchecked")
        private static List<Map<String, Object>> taken(final List<Map<String, Object>> records,
                                                       final Map<String, Object> rule) {
            final List<Map<String, Object>> when = (List<Map<String, Object>>) rule.getOrDefault("when", List.of());
            return records.stream().filter(r -> when.stream().allMatch(c -> {
                final Object value = r.get(c.get(FIELD));
                final String text = value == null
                        ? null
                        : ToolText.str(value);
                if (c.get("equals") != null) {
                    return ToolText.str(c.get("equals")).equals(text);
                }
                if (c.get("one_of") instanceof final List<?> values) {
                    return values.stream().map(ToolText::str).toList().contains(text);
                }
                return true;
            })).toList();
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
                    : kinds.rest().stream().filter(n -> ToolText.match(COMMAND, key(n))).findFirst().orElse(null);
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
                notes.add("One rule per value of " + String.join(" or ", kinds.namings().stream().map(ToolText::repr)
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
            final List<String> unread = (List<String>) applied.getOrDefault("unread_fields", List.of());
            if (!unread.isEmpty()) {
                notes.add("The source notes name what the draft couldn't use, so those rules keep the sample's: "
                          + String.join("; ", unread) + ". The fields are " + reprs(names) + ": correct the notes "
                          + "(classify_kinds from_user) if a kind's user or object is one of them.");
            }
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
                if ("-".equals(ToolText.strip(ToolText.str(v))) && !"msgid".equals(k) && !"structured".equals(k)) {
                    nil.add(k);
                }
            }));
            if (!nil.isEmpty()) {
                mapping.put("nil_values", new ArrayList<>(List.of("-")));
                notes.add(ToolText.repr(nil.stream().sorted(ToolText::compare).toList()) + " hold '-' for none: "
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
                if (ToolText.str(v).contains(doubledQuote)) {
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
                notes.add(ToolText.repr(doubled.stream().sorted(ToolText::compare).toList()) + " hold doubled quotes "
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

    // How a text field's syslog header is read: the extraction's regex, flags and field names (each under the
    // field's own), what the notes call it, and whether it is a whole line (its time and host the source's).
    private record HeldHeader(String regex, String flags, List<String> names, String what, boolean line) {

    }
}
