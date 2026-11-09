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
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Writes an event-logging translation XSLT from a [TranslationMapping], stable to the byte (the golden XSLTs pin it).
///
/// The mapping says which input field (or constant) goes to which event-logging path, per event type. This writes
/// the XSLT: the right input namespace, a template per record that picks the event kind, elements in schema order,
/// time conversion with stroom:format-date, value maps, and guards so empty inputs leave elements out rather than
/// writing empty ones. Each event kind has its own template (by default a template rule with a mode, applied to the
/// record), and elements that come out the same in several kinds (EventTime, EventSource, ...) are written once, in
/// a template of their own, as is a conversion several elements use (an xsl:function). Shared XSLTs are imported for
/// their named templates and functions. A field read several times in a template (in enclosing guards as well as
/// its own element) goes into a variable holding its non-blank values, declared in the rule that uses it, so guards
/// read `$src_ip or $src_port`; one read only by its element stays inline. Value maps shared by several elements,
/// or too long to read as an if, are declared once as xsl:map variables and read with the lookup operator,
/// `$action_to_success?($action)`, which gives nothing rather than an error for an empty input. Names, the layout and
/// these thresholds follow the mapping's [XsltStyle], which a style guide in the AGENTS docs, or the user, can set.
///
/// Mistakes the schema can catch (unknown paths, invalid constants, two alternatives of a choice) come back as
/// problems per mapping entry instead of as XSLT for the model to debug.
public final class XsltGenerator {

    /// The event-logging namespace.
    public static final String EVT = "event-logging:3";
    /// The comment the documentation run puts in each Event, naming the rule that wrote it; never in saved XSLT.
    public static final String RULE_MARK = "stroom-mcp rule: ";
    /// The processing instruction the documentation run writes in each Event, holding the name of the rule that
    /// wrote it: what Stroom's stepping keeps (it drops comments); never in saved XSLT.
    public static final String RULE_INSTRUCTION = "stroom-mcp-rule";
    /// Values listed per element (EventSource table) or per TypeId (Descriptions) in the documentation.
    public static final int DOC_VALUES_SHOWN = 3;
    /// Logged for each record no rule matches (WARN), then the values the rules test: "No event mapping matched
    /// record 5 (action=view | type=)".
    public static final String UNMATCHED = "No event mapping matched record ";
    /// Logged (WARN) once a stream for each field a record has that no rule reads, nor ignored_fields lists: the
    /// line is this, the field's name and [#UNREAD_FIELD_END].
    public static final String UNREAD_FIELD = "No rule reads field ";
    /// The end of an [#UNREAD_FIELD] line.
    public static final String UNREAD_FIELD_END = " (map it, or list it in ignored_fields to leave it out)";
    /// Logged (INFO) for each record a kept-Unknown rule takes; `{rule}` is the rule's name.
    public static final String KEPT_UNKNOWN = "Kept as Unknown by rule '{rule}': record ";
    /// Logged (WARN) where an entry without `repeat` reads several values from its own xpath (a split list, say): the
    /// first is written. The line starts `Several values (n) for <path>, ` and ends with the record's number.
    public static final String SEVERAL_VALUES = "only the first written (repeat: true writes each): record ";
    /// How many of the fields the rules test the log lines carry.
    public static final int LOGGED_FIELDS = 4;
    /// The variable holding the record's number (`stroom:record-no()`), read once for every record.
    public static final String RECORD_NO = "record-no";
    /// What makes the record's number be read for every record: Stroom's split filter queues a location per record
    /// and only moves on when a location function is called, so reading it only for the records the log lines are
    /// written for gave a later record's number, then none once the queue ran over (seen in testing: every
    /// unmatched record of a 160 MB stream logged without a number, so review_coverage found none of them). A
    /// predicate Saxon can't settle without the value, so the variable isn't left unread; it selects nothing.
    public static final String READ_RECORD_NO = "$" + RECORD_NO + "[string(.) = '#']";
    /// The named template that logs a record's fields no rule reads, called by the rules that translate their records:
    /// not a rule's own template, nor a shared one, for those reading an XSLT back.
    public static final String UNREAD_TEMPLATE = "mcp_unread_fields";
    /// The named template that logs a record of a kind, once a stream for each (the rule and the values the rules
    /// test): called by the rules kept as Unknown, and by the catch-all rules (those taking what rules before them
    /// leave), so a whole feed's Unknown records and its kinds no rule names are found from its Error streams.
    public static final String LOG_KIND_TEMPLATE = "mcp_log_kind";
    /// Logged (INFO), once a stream for each kind, for a record a catch-all rule takes; `{rule}` is the rule's name.
    public static final String CAUGHT = "Caught by rule '{rule}': record ";
    /// The XSLT's variable that turns the logging of fields no rule reads on (true(), the default) or off.
    public static final String LOG_UNMAPPED = "log_unmapped_fields";
    // The field names a template's xpaths read, Data Splitter's and JSON's: data[@name='x'], *[@key='x'].
    private static final Pattern DATA_NAME_READ = Pattern.compile("@name\\s*=\\s*(['\"])([^'\"]+)\\1");
    private static final Pattern JSON_KEY_READ = Pattern.compile("@key\\s*=\\s*(['\"])([^'\"]+)\\1");
    /// A json-to-xml call in a mapping's xpath (bare, fn: or stroom:), read through the guarded mcp:json-to-xml.
    public static final Pattern JSON_CALL = Pattern.compile("(?<![\\w:-])(?:fn:|stroom:)?json-to-xml\\(",
            Pattern.UNICODE_CHARACTER_CLASS);
    /// The JSONParser's records: an array's maps, with or without the parser's root map (addRootObject) around the
    /// array; or, for JSON lines, the maps the parser's root map wraps the top-level objects in.
    public static final Map<String, String> JSON_RECORDS = Map.of("array", "/array/map | /map/array/map",
            "lines", "/map/map");
    /// The guarded json-to-xml: an empty value, or one that isn't JSON, gives nothing rather than stopping the
    /// pipeline ("empty sequence" fatal errors, seen in a test environment).
    public static final String SAFE_JSON = "<xsl:function xmlns:xsl=\"" + XmlTrees.XSL + "\" xmlns:mcp=\""
                                           + MappingFacts.MCP_NS + "\" name=\"mcp:json-to-xml\" as=\"node()?\">"
                                           + "<xsl:param name=\"text\" as=\"item()*\"/>"
                                           + "<xsl:variable name=\"json\" select=\"string(($text)[1])\"/>"
                                           + "<xsl:if test=\"normalize-space($json)\"><xsl:try><xsl:sequence "
                                           + "select=\"json-to-xml($json)\"/><xsl:catch/></xsl:try></xsl:if>"
                                           + "</xsl:function>";
    /// XML held in a field, read safely: an empty value, or one that isn't XML, gives nothing rather than stopping
    /// the pipeline.
    public static final String SAFE_XML = "<xsl:function xmlns:xsl=\"" + XmlTrees.XSL + "\" xmlns:mcp=\""
                                          + MappingFacts.MCP_NS + "\" name=\"mcp:parse-xml\" as=\"node()?\">"
                                          + "<xsl:param name=\"text\" as=\"item()*\"/>"
                                          + "<xsl:variable name=\"xml\" select=\"string(($text)[1])\"/>"
                                          + "<xsl:if test=\"starts-with(normalize-space($xml), '&lt;')\"><xsl:try>"
                                          + "<xsl:sequence select=\"parse-xml($xml)\"/><xsl:catch/></xsl:try>"
                                          + "</xsl:if></xsl:function>";

    private static final String XSL = XmlTrees.XSL;
    private static final Map<String, String> DEFAULT_ROOT = Map.of("data_splitter", "records", "json", "/",
            "xml_fragments", "/");
    private static final Map<String, String> DEFAULT_RECORD = Map.of("data_splitter", "record");
    private static final Set<String> NO_VARIABLES = Set.of("choose", "apply-templates", "call-template",
            "analyze-string");
    private static final Pattern VARIABLE_ONLY = Pattern.compile("\\$[\\w.-]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SNAKE_KEY = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");
    private static final Pattern WORD_KEY = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]+");
    private static final Pattern FOR_EACH_XPATH = Pattern.compile("[/\\[(*@$]");
    private static final Pattern EQUALS_AFTER = Pattern.compile("\\s*!?=", Pattern.UNICODE_CHARACTER_CLASS);
    // The XSLT's own function writing a record's time zone as Stroom reads one.
    private static final String ZONE_FUNCTION = "zone";
    private static final Map<String, String> EPOCH_FUNCTIONS = Map.of("epoch_ms", "from-epoch-ms",
            "epoch_s", "from-epoch-s", "epoch_us", "from-epoch-us", "epoch_ns", "from-epoch-ns",
            "windows_filetime", "from-filetime");

    private final TranslationMapping m;
    private final EventSchema schema;
    private final boolean markRules;
    private final XsltStyle style;
    private final List<String> problems = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final EventTrees eventTrees;
    // selector -> variable name, the same in every template
    private final Map<String, String> names = new LinkedHashMap<>();
    // those holding an xpath entry rather than a field
    private final Set<String> xpathNames = new HashSet<>();
    // variables the template being written uses: name -> select
    private Map<String, String> scope = new LinkedHashMap<>();
    // value map items -> name of the stylesheet variable holding it
    private final Map<List<List<String>>, String> maps = new LinkedHashMap<>();
    // Fields an extraction parses out of a text field: name -> (extraction, capture group). Each extraction's
    // analyze-string() result is held in a variable (parts) declared ahead of the fields read from it.
    private final Map<String, Derived> derived = new LinkedHashMap<>();
    private final Map<Extraction, String> parts = new IdentityHashMap<>();
    private final Set<String> keep = new LinkedHashSet<>();
    // variables holding text an extraction reads
    private final Set<String> extractedTexts = new HashSet<>();
    // Dictionary docs read at run time: (name, 'map' for key=value lines, 'set' for a list) -> variable.
    private final Map<List<String>, String> dicts = new LinkedHashMap<>();
    private final Set<String> referenceMaps = new HashSet<>();
    // for_each: the templates run with an item as context; $record (a tunnel parameter) is the record.
    private final boolean itemMode;
    // Whether a rule that translates its records calls the check of fields no rule reads (see warnOfUnreadFields).
    private boolean unreadCalled;
    // Whether a rule calls the template that logs its records' kinds (LOG_KIND_TEMPLATE).
    private boolean logKindCalled;
    private boolean usesRecord;
    // whether log lines give the record's number
    private boolean logsRecordNo;
    private final Set<List<List<String>>> xslMaps = new HashSet<>();
    private final Map<Conversion, String> ownFunctions;
    private final Map<Extraction, Keyed> keyed = new IdentityHashMap<>();
    private final Map<Shape, String> keyedFunctions = new LinkedHashMap<>();
    private final boolean dataFunctionWanted;
    // Whether a time reads its zone from a field or the stream's meta (mcp:zone).
    private final boolean zoneFunctionWanted;
    private final String dataFunction;
    private boolean dataFunctionUsed;
    private final Map<List<String>, List<Run>> dataPlans = new HashMap<>();
    private final Map<List<String>, String> dataTemplates = new HashMap<>();
    private final Map<FieldMapping, String> tokenCache = new IdentityHashMap<>();
    private final Map<EventNode, String> keys = new IdentityHashMap<>();
    private final Set<String> shared = new HashSet<>();
    private final Map<String, List<String>> users = new HashMap<>();
    private final Map<String, NamedTemplate> templates = new LinkedHashMap<>();
    private final Map<List<String>, List<String>> dataUsers = new HashMap<>();
    private final Map<String, String> ruleNames = new LinkedHashMap<>();
    private final List<RuleTemplate> ruleTemplates = new ArrayList<>();
    private final List<Map<String, Object>> summary = new ArrayList<>();
    private int repeatTokens;
    // The namespace the Events are written in: the schema's, event-logging:3 or an environment's fork's.
    private final String ns;

    private XsltGenerator(final TranslationMapping mapping, final EventSchema schema, final boolean markRules) {
        this.m = mapping;
        this.schema = schema;
        this.ns = schema == null
                ? EVT
                : schema.namespace();
        this.markRules = markRules;
        this.style = mapping.style();
        this.itemMode = mapping.forEach() != null && !mapping.forEach().isEmpty();
        this.eventTrees = new EventTrees(this, mapping, schema, problems, warnings);
        final String naming = style.naming();
        for (int n = 0; n < mapping.extract().size(); n++) {
            final Extraction extraction = mapping.extract().get(n);
            eventTrees.checkExtraction(n, extraction);
            // Named for what it extracts (srcip_parts), not numbered after its source: seen as body_parts_3, _5,
            // _4 and _6 for four extractions of one field, declared out of order.
            final String first = extraction.names().stream().filter(name -> !name.isEmpty()).findFirst().orElse(null);
            final String base = XsltNames.styleName(or(first, or(extraction.field(), "text")) + "-parts", naming);
            final String partsName = XsltNames.uniqueName(base, keep, naming);
            parts.put(extraction, partsName);
            keep.add(partsName);
            for (int nr = 1; nr <= extraction.names().size(); nr++) {
                final String name = extraction.names().get(nr - 1);
                if (derived.containsKey(name)) {
                    note(problems, "extract: '" + name + "' is the name of two extracted fields");
                } else if (!name.isEmpty()) {
                    derived.put(name, new Derived(extraction, nr));
                }
            }
        }
        // A value map becomes an xsl:map when several elements use it or it is too long to read as an if.
        final Map<List<List<String>>, Set<String>> paths = new LinkedHashMap<>();
        for (final FieldMapping entry : mapping.allEntries()) {
            if (entry.hasMap()) {
                paths.computeIfAbsent(items(entry), ignored -> new HashSet<>()).add(entry.strippedPath());
            }
        }
        paths.forEach((items, used) -> {
            if (used.size() > 1 || items.size() > style.inlineMapMaxKeys()) {
                xslMaps.add(items);
            }
        });
        eventTrees.checkFunctions();
        eventTrees.checkXpaths(derived.keySet());
        eventTrees.checkReachable();
        this.ownFunctions = chooseFunctions();
        chooseKeyed();
        // mcp:data writes a Data entry only when its value is present: one line a Data entry instead of an xsl:if
        // and two reads of the value (seen: 135 of them, 31 KB of a 50 KB translation).
        this.zoneFunctionWanted = mapping.allEntries().stream().anyMatch(entry -> entry.zoneSource() != null);
        this.dataFunctionWanted = "function".equals(style.dataEntries()) && mapping.allEntries().stream()
                .anyMatch(entry -> isSet(entry.dataName()) && entry.value() == null
                                   && !(entry.hasMap() || entry.defaultValue() != null || entry.transform() != null
                                        || isSet(entry.timeFormat()) || entry.repeat()));
        final Set<String> taken = new HashSet<>(ownFunctions.values());
        taken.addAll(keyedFunctions.values());
        taken.add("json-to-xml");
        this.dataFunction = XsltNames.uniqueName(XsltNames.styleName("data", naming), taken, naming);
    }

    /// The XSLT for a mapping.
    ///
    /// @param mapping   The mapping.
    /// @param schema    The event-logging schema the events are written in.
    /// @param version   Its version, e.g. `4.1.0`, written in the Events element.
    /// @param markRules True to put a comment naming the rule in each Event, for the documentation run (stepping
    ///                  with draft code, nothing saved): the Field mapping tables then know exactly which rule wrote
    ///                  what.
    /// @return What was generated: the XSLT, or the problems that stopped it.
    public static Generated generate(final TranslationMapping mapping,
                                     final EventSchema schema,
                                     final String version,
                                     final boolean markRules) {
        final XsltGenerator generator = new XsltGenerator(mapping, schema, markRules);
        final String xslt = generator.stylesheet(version);
        final Set<String> dictionaries = new TreeSet<>();
        generator.dicts.keySet().forEach(key -> dictionaries.add(key.getFirst()));
        return new Generated(generator.problems.isEmpty(), List.copyOf(generator.problems),
                List.copyOf(generator.warnings), generator.summary, generator.problems.isEmpty()
                ? xslt
                : null, List.copyOf(new TreeSet<>(generator.referenceMaps)), List.copyOf(dictionaries),
                generator.itemMode
                        ? mapping.forEach()
                        : null, generator.eventTrees.conditionalByRule());
    }

    /// @param mapping The mapping.
    /// @param schema  The event-logging schema.
    /// @param version Its version.
    /// @return What was generated, without rule marks.
    public static Generated generate(final TranslationMapping mapping, final EventSchema schema, final String version) {
        return generate(mapping, schema, version, false);
    }

    // --- checks made before writing ---

    // The conversions an entry's value goes through that could be a function: its transform, its time format.
    private static List<Conversion> conversions(final FieldMapping entry) {
        final List<Conversion> found = new ArrayList<>();
        if (entry.transform() != null && XPathText.FUNCTION_TRANSFORMS.contains(entry.transform())) {
            found.add(Conversion.transform(entry.transform()));
        }
        if (isSet(entry.timeFormat()) && !entry.hasMap() && !entry.repeat() && entry.zoneSource() == null) {
            found.add(Conversion.time(entry.timeFormat(), entry.timezone()));
        }
        return found;
    }

    // The conversions used by style.function_min_uses elements or more -> their function's name.
    private Map<Conversion, String> chooseFunctions() {
        final long least = style.functionMinUses();
        if (least == 0) {
            return Map.of();
        }
        final Map<Conversion, Set<String>> places = new LinkedHashMap<>();
        for (final FieldMapping entry : m.allEntries()) {
            for (final Conversion conversion : conversions(entry)) {
                places.computeIfAbsent(conversion, ignored -> new HashSet<>())
                        .add(entry.strippedPath() + "[" + or(entry.dataKey(), "") + "]");
            }
        }
        final Map<Conversion, String> out = new LinkedHashMap<>();
        final String naming = style.naming();
        places.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= least)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(Conversion::repr))
                .forEach(conversion -> {
                    final String base = conversion.isTime()
                            ? EPOCH_FUNCTIONS.getOrDefault(conversion.value(), "parse-time")
                            : conversion.value();
                    final Set<String> taken = new HashSet<>(out.values());
                    taken.add("json-to-xml");
                    out.put(conversion, XsltNames.uniqueName(XsltNames.styleName(base, naming), taken, naming));
                });
        return out;
    }

    // Extractions that differ only by the key they find in one text field (key="..." for each of thirty keys): one
    // function a shape, called with the key, in place of a variable a key, declared again in every template that
    // reads it (seen: 52 of them, each declared in four rules, in 93 KB of XSLT).
    private void chooseKeyed() {
        final Map<Shape, List<Keyed>> shapes = new LinkedHashMap<>();
        final Map<Shape, List<Extraction>> members = new LinkedHashMap<>();
        for (final Extraction ex : m.extract()) {
            final List<String> given = ex.names().stream().filter(name -> !name.isEmpty()).toList();
            if (ex.names().size() != 1 || given.isEmpty() || (ex.field() != null && derived.containsKey(ex.field()))) {
                continue;
            }
            final String key = given.getFirst();
            List<int[]> found = finds(Pattern.compile("(?<![\\w\\\\])" + Pattern.quote(key) + "(?!\\w)",
                    Pattern.UNICODE_CHARACTER_CLASS), ex.regex());
            if (found.isEmpty() && SNAKE_KEY.matcher(key).matches()) {
                // Or the key as the text writes it, the name its snake case: source_ip from 'Source IP: (...)', as
                // draft_translation_mapping names a text's keys.
                final String words = String.join("[ _-]", ToolText.split(key, "_"));
                final int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
                found = finds(Pattern.compile("(?<![\\w\\\\])" + words + "(?![\\w])", flags), ex.regex());
            }
            if (!WORD_KEY.matcher(key).matches() || found.size() != 1) {
                continue;
            }
            final int at = found.getFirst()[0];
            final int end = found.getFirst()[1];
            final String written = ex.regex().substring(at, end);
            final Shape shape = new Shape(ex.field(), ex.xpath(), ex.scope(), ex.flags(), ex.regex().substring(0, at),
                    ex.regex().substring(end));
            shapes.computeIfAbsent(shape, ignored -> new ArrayList<>()).add(new Keyed(null, written));
            members.computeIfAbsent(shape, ignored -> new ArrayList<>()).add(ex);
        }
        final String naming = style.naming();
        final Set<String> taken = new HashSet<>(ownFunctions.values());
        taken.add("json-to-xml");
        for (final Map.Entry<Shape, List<Keyed>> entry : shapes.entrySet()) {
            if (entry.getValue().size() < 3) {
                continue;
            }
            final String after = entry.getKey().suffix();
            final String base = after.startsWith("=\"") || after.startsWith("=\\\"")
                    ? "quoted-value"
                    : after.startsWith("=")
                            ? "value"
                            : "keyed-value";
            final String name = XsltNames.uniqueName(XsltNames.styleName(base, naming), taken, naming);
            taken.add(name);
            keyedFunctions.put(entry.getKey(), name);
            final List<Extraction> extractions = members.get(entry.getKey());
            for (int i = 0; i < extractions.size(); i++) {
                keyed.put(extractions.get(i), new Keyed(name, entry.getValue().get(i).key()));
            }
        }
    }

    private static List<int[]> finds(final Pattern pattern, final String text) {
        final List<int[]> out = new ArrayList<>();
        final Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            out.add(new int[]{matcher.start(), matcher.end()});
        }
        return out;
    }

    private static void note(final List<String> bucket, final String message) {
        if (!bucket.contains(message)) {
            bucket.add(message);
        }
    }

    // The comment over a rule's template: which records it handles, the event it writes, and the input it reads.
    // Seen: only the rule's name (<!-- traffic_allow -->), so the template had to be read to know.
    private String ruleHeader(final EventRule rule, final boolean isConditional) {
        final String records;
        if (!rule.when().isEmpty()) {
            records = "records where " + rule.when().stream().map(MappingFacts::conditionWords)
                    .collect(Collectors.joining(" and "));
        } else {
            records = isConditional
                    ? "records no other rule matches"
                    : "every record";
        }
        final String home = MappingFacts.actionHome(rule.fields().stream().map(FieldMapping::path).toList());
        final List<FieldMapping> entries = new ArrayList<>(rule.fields());
        entries.addAll(m.common());
        final FieldMapping typeId = entries.stream()
                .filter(entry -> "EventDetail/TypeId".equals(entry.strippedPath()))
                .findFirst()
                .orElse(null);
        final String element = home == null
                ? null
                : home.startsWith("EventDetail/")
                        ? home.substring("EventDetail/".length())
                        : home;
        String writes = element != null
                ? ("AEIOU".indexOf(element.charAt(0)) >= 0
                ? "an"
                : "a") + " " + element + " event"
                : "an event";
        if (typeId != null && typeId.value() != null) {
            writes += " (TypeId " + typeId.value() + ")";
        } else if (typeId != null && isSet(typeId.field())) {
            writes += " (TypeId from " + typeId.field() + ")";
        }
        final List<String> read = new ArrayList<>();
        for (final FieldMapping entry : rule.fields()) {
            final List<String> inputs = new ArrayList<>();
            if (isSet(entry.field())) {
                inputs.add(entry.field());
            }
            if (entry.anyOf() != null) {
                inputs.addAll(entry.anyOf());
            }
            if (entry.join() != null) {
                inputs.addAll(entry.join());
            }
            inputs.stream().filter(name -> !read.contains(name)).forEach(read::add);
        }
        final Set<String> sources = new TreeSet<>();
        read.stream().filter(derived::containsKey)
                .forEach(name -> sources.add(or(derived.get(name).extraction().field(), "an xpath")));
        String reads = "";
        if (!read.isEmpty()) {
            reads = " from " + String.join(", ", read.subList(0, Math.min(10, read.size()))) + (read.size() > 10
                    ? ", ..."
                    : "");
            if (!sources.isEmpty()) {
                reads += read.stream().allMatch(derived::containsKey)
                        ? " (extracted from " + String.join(", ", sources) + ")"
                        : " (" + read.stream().filter(derived::containsKey).collect(Collectors.joining(", "))
                          + " extracted from " + String.join(", ", sources) + ")";
            }
        }
        final String why = isSet(rule.allowUnknown())
                ? " Unknown on purpose: " + rule.allowUnknown() + "."
                : "";
        return XsltComments.comment("Rule '" + rule.name() + "': " + records + ". Writes " + writes + reads + "."
                                    + why);
    }

    private String partsName(final Extraction ex) {
        return parts.get(ex);
    }

    // Declare the variable holding the extraction's analyze-string() result in the template being written, before
    // anything that reads it (an extraction of an extracted field declares its source's first).
    private void declareParts(final Extraction ex) {
        if (keyed.containsKey(ex)) {
            return;     // read through its shape's function, no variable of its own
        }
        final String name = partsName(ex);
        if (scope.containsKey(name)) {
            return;
        }
        if (ex.field() != null && derived.containsKey(ex.field())) {
            declareParts(derived.get(ex.field()).extraction());
        }
        // The text read once, as the field's own variable ($body), however many extractions read it: seen as
        // string((*[@key='body'])[1]) written out for each of four.
        final String text = ref(ex.field(), ex.xpath(), "text", ex.scope());
        extractedTexts.add(text.substring(1));
        scope.put(name, XPathText.partsText(text, ex.regex(), ex.flags()));
    }

    // --- input addressing ---

    // The selector for an input, relative to the context node: the record, or with for_each the item, where scope
    // 'record' reads the record through $record instead.
    private String source(final String fieldName, final String xpath, final String inputScope) {
        if (fieldName != null && derived.containsKey(fieldName)) {
            final Derived found = derived.get(fieldName);
            final Extraction ex = found.extraction();
            if (keyed.containsKey(ex)) {
                final Keyed function = keyed.get(ex);
                return "mcp:" + function.function() + "(" + ref(ex.field(), ex.xpath(), "text", ex.scope()) + ", "
                       + XPathText.literal(function.key()) + ")";
            }
            // Declared in the template being written, whatever reads it (a variable, any_of, a lookup or dictionary
            // key, a repeat): a named template shared between rules has its own scope.
            declareParts(ex);
            return XPathText.groupText("$" + partsName(ex), found.nr());
        }
        final String xmlHeld = xpath == null
                ? longestPrefix(m.xmlFields(), fieldName, "/")
                : null;
        if (xmlHeld != null) {
            // A path into the XML a field holds, read through the guarded helper (an empty field, or one that isn't
            // XML, gives nothing), its steps matched by local name: the XML's own namespaces don't matter.
            final String base = source(xmlHeld, null, inputScope);
            return "mcp:parse-xml((" + base + ")[1])" + XPathText.xmlSteps(fieldName.substring(xmlHeld.length()));
        }
        final String held = xpath == null
                ? longestPrefix(m.jsonFields(), fieldName, ".")
                : null;
        if (held != null) {
            // A key of the JSON a field holds: read through the guarded helper, so an empty field, or one that isn't
            // JSON, gives nothing (seen: a whole JSON line written into TypeId, and fatal "empty sequence" errors).
            final String base = source(held, null, inputScope);
            final List<String> jsonKeys = ToolText.split(fieldName.substring(held.length() + 1), ".");
            return "mcp:json-to-xml((" + base + ")[1])/*" + jsonKeys.stream()
                    .map(key -> "/*[@key=" + XPathText.literal(key) + "]")
                    .collect(Collectors.joining());
        }
        final String selector;
        if (xpath != null) {
            // JSON held in a field, read safely: an empty value, or one that isn't JSON, gives nothing rather than
            // stopping the pipeline ("empty sequence" fatal errors, seen in a test environment).
            selector = JSON_CALL.matcher(xpath).replaceAll(Matcher.quoteReplacement("mcp:json-to-xml("));
        } else {
            selector = XPathText.fieldText(m.input(), fieldName);
        }
        if (itemMode && "record".equals(inputScope)) {
            usesRecord = true;
            return xpath != null
                    ? "$record/(" + selector + ")"
                    : "$record/" + selector;
        }
        return selector;
    }

    // The longest of the fields that the field name starts with, then the separator.
    private static String longestPrefix(final List<String> fields, final String fieldName, final String separator) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        final List<String> sorted = new ArrayList<>(fields);
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        return sorted.stream().filter(field -> fieldName.startsWith(field + separator)).findFirst().orElse(null);
    }

    // The nodes an input has several of: a JSON array's members, else the input's own nodes.
    private String itemsOf(final String fieldName, final String xpath, final String inputScope) {
        final String selector = source(fieldName, xpath, inputScope);
        if ("json".equals(m.input()) && xpath == null && !(fieldName != null && derived.containsKey(fieldName))) {
            return selector + "/*";
        }
        return selector;
    }

    private String entryRef(final FieldMapping entry) {
        final Source given = sourceOf(entry);
        return ref(given.field(), given.xpath(), label(entry), given.scope());
    }

    private String entryHas(final FieldMapping entry) {
        final Source given = sourceOf(entry);
        return has(given.field(), given.xpath(), label(entry), given.scope());
    }

    private String dictVariable(final String name, final String kind) {
        final List<String> key = List.of(name, kind);
        if (!dicts.containsKey(key)) {
            final String naming = style.naming();
            final String base = XsltNames.styleName(name + "-" + ("map".equals(kind)
                    ? "map"
                    : "list"), naming);
            final Set<String> taken = new HashSet<>(names.values());
            taken.addAll(maps.values());
            taken.addAll(keep);
            taken.addAll(dicts.values());
            dicts.put(key, XsltNames.uniqueName(base, taken, naming));
        }
        return dicts.get(key);
    }

    private String keyExpression(final String fieldName, final String xpath, final String inputScope) {
        return XPathText.keyText(source(fieldName, xpath, inputScope));
    }

    /// The entry's input as (field, xpath, scope): a field as it is; any_of, lookup and dictionary as the XPath that
    /// computes them (scope already applied), so the rest of the generator treats them like any computed value.
    ///
    /// @param entry A field mapping.
    /// @return Its input.
    Source sourceOf(final FieldMapping entry) {
        final String entryScope = entry.scope();
        if (entry.lookup() != null) {
            referenceMaps.add(entry.lookup().map());
            final String key = keyExpression(entry.lookup().field(), entry.lookup().xpath(), entryScope);
            // The value's elements are in no namespace, while the stylesheet's default XPath namespace is the
            // input's: *:name selects them whatever that is, at any depth below the value.
            return new Source(null, XPathText.lookupText(entry.lookup().map(), key, entry.lookup().path()), null);
        }
        final String keySource = hasAnyOf(entry)
                ? XPathText.anyOfText(entry.anyOf().stream().map(field -> source(field, null, entryScope)).toList(),
                        nil())
                : null;
        if (isSet(entry.dictionary())) {
            final String key = XPathText.keyText(keySource != null
                    ? keySource
                    : source(entry.field(), null, entryScope));
            return new Source(null, XPathText.dictionaryLookupText(dictVariable(entry.dictionary(), "map"), key), null);
        }
        if (hasAnyOf(entry)) {
            return new Source(null, keySource, null);
        }
        if (entry.join() != null) {
            return new Source(null, XPathText.joinText(entry.join().stream()
                    .map(field -> source(field, null, entryScope))
                    .toList(), entry.joinSeparator(), nil()), null);
        }
        return new Source(entry.field(), entry.xpath(), entryScope);
    }

    private static boolean hasAnyOf(final FieldMapping entry) {
        return entry.anyOf() != null && !entry.anyOf().isEmpty();
    }

    // The entry's one value, transformed as asked; src is the variable or selector holding its values.
    private String scalar(final FieldMapping entry, final String src) {
        final String one = src + "[1]";
        final String name = entry.transform() == null
                ? null
                : ownFunctions.get(Conversion.transform(entry.transform()));
        return name != null
                ? "mcp:" + name + "(" + one + ")"
                : XPathText.transformExpression(entry.transform(), one);
    }

    // The one value an entry without repeat writes, maps or tests as a key: the first with text, transformed if asked.
    // Seen in testing: several values (a field given again, an xpath giving several) were written joined with
    // spaces where the entry had no transform, and only the first where it had one.
    private String one(final FieldMapping entry, final String src) {
        if (entry.transform() != null) {
            return scalar(entry, src);
        }
        // A lookup, a dictionary, any_of (its first value) and join (one string) give one value already.
        final boolean single = entry.lookup() != null || isSet(entry.dictionary()) || hasAnyOf(entry)
                               || entry.join() != null;
        return single
                ? src
                : src + "[1]";
    }

    // A variable holding the input's non-blank values, declared at the top of the template being written, so each
    // selector appears once per template. Named after the field, or for an xpath after `label`.
    private String ref(final String fieldName, final String xpath, final String label, final String inputScope) {
        final String raw = source(fieldName, xpath, inputScope);
        if (!names.containsKey(raw)) {
            final String naming = style.naming();
            final String base = XsltNames.styleName(xpath != null
                    ? label
                    : fieldName, naming);
            final Set<String> taken = new HashSet<>(names.values());
            taken.addAll(maps.values());
            taken.addAll(keep);
            names.put(raw, XsltNames.uniqueName(base, taken, naming));
        }
        final String name = names.get(raw);
        if (xpath != null) {
            xpathNames.add(name);
        }
        if (fieldName != null && derived.containsKey(fieldName)) {
            declareParts(derived.get(fieldName).extraction());
        }
        final boolean wrap = (xpath != null && !MappingFacts.isCall(raw)) || "xml".equals(m.input())
                             || "xml_fragments".equals(m.input());
        // A value the source writes for "none" (syslog's -) counts as empty, as a blank one does.
        scope.putIfAbsent(name, (wrap
                ? "(" + raw + ")[normalize-space(.)]"
                : raw + "[normalize-space(.)]") + nil());
        return "$" + name;
    }

    private String nil() {
        return m.nilValues().isEmpty()
                ? ""
                : "[not(normalize-space(.) = (" + m.nilValues().stream().map(XPathText::literal)
                        .collect(Collectors.joining(", ")) + "))]";
    }

    // Test that the input has a value. Fields select nodes, which are true when present; an xpath may give a number
    // or boolean, which XPath would test by its value, so that needs exists().
    private String has(final String fieldName, final String xpath, final String label, final String inputScope) {
        final String v = ref(fieldName, xpath, label, inputScope);
        return xpath == null
                ? v
                : "exists(" + v + ")";
    }

    // The rule's test; raw: with the input's own selectors, for the summary returned to the model.
    private String condition(final Condition c, final boolean raw) {
        final String src;
        if (raw) {
            src = c.field() != null && derived.containsKey(c.field())
                    ? c.field()
                    : source(c.field(), c.xpath(), c.scope());
        } else {
            src = ref(c.field(), c.xpath(), "condition", c.scope());
        }
        if (c.equalTo() != null) {
            return XPathText.equalsText(src, c.equalTo());
        }
        if (c.oneOf() != null) {
            return XPathText.oneOfText(src, c.oneOf());
        }
        if (c.matches() != null) {
            return XPathText.matchesText(src, c.matches());
        }
        if (c.inDictionary() != null) {
            if (raw) {
                return src + " in dictionary " + XPathText.literal(c.inDictionary());
            }
            return XPathText.inDictionaryText(src, dictVariable(c.inDictionary(), "set"));
        }
        final String test = raw
                ? "exists(" + src + "[normalize-space(.)])"
                : has(c.field(), c.xpath(), "condition", c.scope());
        return Boolean.TRUE.equals(c.present())
                ? test
                : "not(" + test + ")";
    }

    // --- values ---

    private static String label(final FieldMapping entry) {
        if (entry.lookup() != null) {
            return entry.lookup().map();
        }
        if (isSet(entry.dictionary())) {
            return (isSet(entry.field())
                    ? entry.field()
                    : entry.anyOf().getFirst()) + "-" + entry.dictionary();
        }
        if (hasAnyOf(entry)) {
            return entry.anyOf().getFirst();
        }
        if (entry.join() != null) {
            return String.join("-", entry.join());
        }
        final List<String> steps = ToolText.split(entry.strippedPath(), "/");
        return String.join("-", steps.subList(Math.max(0, steps.size() - 2), steps.size()));
    }

    private static List<List<String>> items(final FieldMapping entry) {
        return entry.map().entrySet().stream().map(item -> List.of(item.getKey(), item.getValue())).toList();
    }

    // A stylesheet-level variable holding the entry's value map, declared once however many elements use it. Named
    // after the input and the first element it fills, e.g. $action_to_success.
    private String mapRef(final FieldMapping entry, final String src) {
        final List<List<String>> items = items(entry);
        if (!maps.containsKey(items)) {
            final String naming = style.naming();
            final List<String> steps = ToolText.split(entry.strippedPath(), "/");
            final String base = XsltNames.styleName(src.substring(1) + "-to-" + steps.getLast(), naming);
            final Set<String> taken = new HashSet<>(names.values());
            taken.addAll(maps.values());
            taken.addAll(keep);
            maps.put(items, XsltNames.uniqueName(base, taken, naming));
        }
        return "$" + maps.get(items);
    }

    // Whether the entry's value map is declared as an xsl:map rather than written inline as an if.
    private boolean asXslMap(final FieldMapping entry) {
        return xslMaps.contains(items(entry));
    }

    private String leafTest(final FieldMapping entry) {
        if (entry.value() != null || entry.defaultValue() != null) {
            return null;
        }
        if (entry.repeat() && !entry.hasMap()) {
            // Several values (a key given again, as rcpt= once per recipient): whether any has text. Seen in eval
            // case 50: normalize-space() over them all, which takes one, failed every record with two recipients.
            // exists(), as an xpath may give strings (tokenize), and two or more strings have no effective boolean
            // value: seen in testing, a split group list failed every record holding two groups.
            final Source given = sourceOf(entry);
            return "exists((" + source(given.field(), given.xpath(), given.scope()) + ")[normalize-space(.)])";
        }
        if (entry.hasMap()) {
            final String src = entryRef(entry);
            final String key = one(entry, src);
            if (asXslMap(entry)) {
                return "exists(" + mapRef(entry, src) + "?(" + key + "))";
            }
            return key + " = (" + entry.map().keySet().stream().map(XPathText::literal)
                    .collect(Collectors.joining(", ")) + ")";
        }
        if (entry.metaFallback() != null) {
            return "(" + entryHas(entry) + ") or exists(" + metaFallbackText(entry) + ")";
        }
        return entryHas(entry);
    }

    // The first of the entry's meta_fallback attributes the stream has a value for (none if it has none).
    private static String metaFallbackText(final FieldMapping entry) {
        return "(" + entry.metaFallback().stream()
                .map(name -> "string(stroom:meta(" + XPathText.literal(name) + "))")
                .collect(Collectors.joining(", ")) + ")[normalize-space(.)][1]";
    }

    private String valueExpression(final FieldMapping entry) {
        if (entry.repeat()) {
            // Written inside xsl:for-each over the values: the current value, transformed if asked.
            return entry.transform() != null
                    ? scalar(entry, ".")
                    : ".";
        }
        final String src = entryRef(entry);
        final String key = one(entry, src);
        if (entry.hasMap() && asXslMap(entry)) {
            // The lookup operator takes any number of keys, so an empty input gives no value rather than an error.
            return XPathText.mapLookupText(mapRef(entry, src), key, entry.defaultValue());
        }
        if (entry.hasMap()) {
            final List<Map.Entry<String, String>> mapped = new ArrayList<>(entry.map().entrySet());
            String expression;
            if (entry.defaultValue() != null) {
                expression = XPathText.literal(entry.defaultValue());
            } else {
                // Without a default the element is only written when the input is one of the keys, so the last key
                // needs no test of its own.
                expression = XPathText.literal(mapped.removeLast().getValue());
            }
            for (int i = mapped.size() - 1; i >= 0; i--) {
                expression = XPathText.mapStepText(key, mapped.get(i).getKey(), mapped.get(i).getValue(), expression);
            }
            return expression;
        }
        final String format = entry.timeFormat();
        final String zone = entry.timezone();
        // A time is converted from one value, whatever the source.
        final String one = entry.transform() != null
                ? scalar(entry, src)
                : src + "[1]";
        final String expression;
        if (isSet(format) && entry.zoneSource() != null) {
            // The zone read record by record (asked for by the user): from a field or the stream's meta, mapped,
            // normalised and defaulted by mcp:zone.
            expression = "stroom:format-date(" + one + ", " + XPathText.literal(format) + ", " + zoneText(
                    entry.zoneSource()) + ")";
        } else if (isSet(format) && ownFunctions.containsKey(Conversion.time(format, zone))) {
            expression = "mcp:" + ownFunctions.get(Conversion.time(format, zone)) + "(" + one + ")";
        } else if (isSet(format)) {
            expression = XPathText.timeExpression(format, zone, one);
        } else {
            expression = key;
        }
        if (entry.defaultValue() != null) {
            return XPathText.defaultText(entryHas(entry), expression, entry.defaultValue());
        }
        if (entry.metaFallback() != null) {
            // A record whose input gives none (a stand-in for none left out) takes the stream's, as the user chose.
            return "if (" + entryHas(entry) + ") then " + expression + " else " + metaFallbackText(entry);
        }
        return expression;
    }

    private void writeValue(final XmlElement element, final FieldMapping entry) {
        if (entry.value() != null) {
            element.setText(entry.value());
        } else if (entry.rollover()) {
            writeRolledOver(element, entry);
        } else if (isSet(entry.timeFallback())) {
            writeWithFallback(element, entry);
        } else {
            warnOfSeveral(element, entry);
            element.addElement(XSL, "value-of", "select", valueExpression(entry));
        }
    }

    // A time of day on a date that doesn't move on by itself (a header's date joined with each line's time): a time
    // earlier than the one before it on the same date is the next day. The stream's last time and the days added are
    // one value kept with stroom:put, read before and written after, the put's argument depending on both, so
    // Saxon's lazy evaluation can't reorder them. A new date (a new header) starts again at no days. Stroom's own
    // completion of a dateless time moves it back a month instead.
    private void writeRolledOver(final XmlElement element, final FieldMapping entry) {
        final String key = XPathText.literal("mcp-rollover:" + entry.strippedPath());
        element.addElement(XSL, "variable", "name", "mcp_iso", "select", "string(" + valueExpression(entry) + ")");
        final XmlElement choose = element.addElement(XSL, "choose");
        choose.addElement(XSL, "when", "test", "$mcp_iso = ''");
        final XmlElement otherwise = choose.addElement(XSL, "otherwise");
        otherwise.addElement(XSL, "variable", "name", "mcp_state", "select",
                "tokenize(string(stroom:get(" + key + ")), '\\|')");
        otherwise.addElement(XSL, "variable", "name", "mcp_days", "select",
                "if (count($mcp_state) lt 2 or substring($mcp_state[1], 1, 10) ne substring($mcp_iso, 1, 10)) then 0 "
                + "else xs:integer($mcp_state[2]) + (if ($mcp_iso lt $mcp_state[1]) then 1 else 0)");
        otherwise.addElement(XSL, "value-of", "select", "stroom:put(" + key + ", concat($mcp_iso, '|', $mcp_days))");
        otherwise.addElement(XSL, "value-of", "select",
                "if ($mcp_days eq 0) then $mcp_iso else format-dateTime(xs:dateTime($mcp_iso) + $mcp_days * "
                + "xs:dayTimeDuration('P1D'), '[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f001]Z')");
    }

    // A time that doesn't parse (Stroom's format-date gives nothing) written as the stream meta time the user agreed
    // to, with a warning naming the record, in place of an empty time the schema refuses.
    private void writeWithFallback(final XmlElement element, final FieldMapping entry) {
        element.addElement(XSL, "variable", "name", "mcp_time", "select", "string(" + valueExpression(entry) + ")");
        final XmlElement choose = element.addElement(XSL, "choose");
        choose.addElement(XSL, "when", "test", "$mcp_time != ''").addElement(XSL, "value-of", "select", "$mcp_time");
        final XmlElement otherwise = choose.addElement(XSL, "otherwise");
        otherwise.addElement(XSL, "sequence", "select", "stroom:log('WARN', concat("
                                                        + XPathText.literal(entry.strippedPath() + ": the time didn't "
                                                                            + "parse, so the stream's "
                                                                            + entry.timeFallback()
                                                                            + " is written, record ")
                                                        + ", stroom:record-no()))");
        otherwise.addElement(XSL, "value-of", "select", "stroom:meta(" + XPathText.literal(entry.timeFallback()) + ")");
    }

    // A warning logged where an entry without repeat reads several values from its own xpath: the first is written.
    // Seen in testing: a group list split with tokenize() but not repeated; a field gives one value per name, and
    // lookups, joins and any_of read one already. The log call writes nothing, so the output is the same.
    private void warnOfSeveral(final XmlElement parent, final FieldMapping entry) {
        if (entry.repeat() || entry.value() != null || !isSet(entry.xpath())) {
            return;
        }
        final String values = entryRef(entry);
        parent.addElement(XSL, "if", "test", "count(" + values + ") gt 1")
                .addElement(XSL, "sequence", "select", "stroom:log('WARN', concat('Several values (', count("
                                                       + values + "), ') for ', "
                                                       + XPathText.literal(entry.strippedPath() + ", "
                                                                           + SEVERAL_VALUES)
                                                       + ", stroom:record-no()))");
    }

    // --- XSLT ---

    /// @param node An element of a rule's tree.
    /// @return The test that it has a value to write (any of the inputs below it has one), or null if it is always
    /// written.
    String testOf(final EventNode node) {
        if (node.shared != null) {
            return null;     // the shared template decides; its parent is written whatever the record holds
        }
        if (node.leaf != null) {
            return leafTest(node.leaf);
        }
        final List<String> tests = new ArrayList<>();
        for (final EventNode kid : node.kids.values()) {
            tests.add(testOf(kid));
        }
        for (final EventNode.DataEntry entry : node.data) {
            tests.add(leafTest(entry.entry()));
        }
        if (tests.isEmpty() || tests.contains(null)) {
            return null;
        }
        return String.join(" or ", new LinkedHashSet<>(tests));
    }

    private void emit(final XmlElement parent, final EventNode node, final String enclosing, final boolean inline) {
        final List<Item> list = new ArrayList<>();
        int n = 0;
        for (final EventNode kid : node.kids.values()) {
            list.add(new Item(kid.child.index(), 0, n++, kid, null));
        }
        n = 0;
        for (final EventNode.DataEntry entry : node.data) {
            list.add(new Item(entry.child().index(), 1, n++, null, entry));
        }
        list.sort(Comparator.comparingInt(Item::index).thenComparingInt(Item::isData).thenComparingInt(Item::n));
        final Map<Integer, Run> runs = new HashMap<>();
        for (final Run run : dataPlan(node)) {
            runs.put(run.start(), run);
        }
        int dataAt = -1;
        int skip = 0;
        final Set<String> containers = new HashSet<>();
        for (final Item item : list) {
            if (item.data() != null) {
                dataAt++;
                if (skip > 0) {
                    skip--;
                    continue;
                }
                final FieldMapping data = item.data().entry();
                if (data.dataParent() != null) {
                    // Its container, with every entry of the node's written in it, where the first of them goes.
                    final String outer = data.dataParent().split("/", 2)[0];
                    if (containers.add(outer)) {
                        emitContainer(parent, outer, node.data.stream()
                                .map(EventNode.DataEntry::entry)
                                .filter(e -> e.dataParent() != null && outer.equals(e.dataParent().split("/", 2)[0]))
                                .toList(), enclosing);
                    }
                    continue;
                }
                if (runs.containsKey(dataAt)) {
                    // Data entries other rules write the same way, in a row: their template, in their place.
                    final Run run = runs.get(dataAt);
                    final List<FieldMapping> entries = node.data.subList(dataAt, Math.min(node.data.size(),
                            dataAt + run.length())).stream().map(EventNode.DataEntry::entry).toList();
                    invoke(parent, dataTemplate(run.tokens(), entries));
                    skip = run.length() - 1;
                    continue;
                }
                emitData(parent, item.data().entry(), enclosing);
            } else if (item.node().shared != null) {
                final XmlElement call = parent.addElement(XSL, "call-template", "name",
                        item.node().shared.template());
                item.node().shared.withParams().forEach((name, select) -> call.addElement(XSL, "with-param",
                        "name", name, "select", select));
            } else if (item.node().repeat != null) {
                // One element per value: the loop stands in for the guard, and the leaf below reads the current
                // value.
                final XmlElement loop = parent.addElement(XSL, "for-each", "select", repeatItems(item.node().repeat));
                emitElement(loop, item.node(), testOf(item.node()), true);
            } else if (!inline && shareable(item.node()) && shared.contains(key(item.node()))) {
                invoke(parent, templateFor(item.node()));
            } else {
                emitElement(parent, item.node(), enclosing, inline);
            }
        }
    }

    // A Data element holding Data entries (their data_parent's outermost Name): written only when one of them has a
    // value, each inside it, an entry with a deeper data_parent in a container of its own there.
    private void emitContainer(final XmlElement parent,
                               final String name,
                               final List<FieldMapping> entries,
                               final String enclosing) {
        // A repeated entry's test is already exists() over its values with text (leafTest): wrapping it again made
        // exists(false), always true, and wrote an empty container.
        final List<String> tests = entries.stream()
                .map(this::leafTest)
                .toList();
        final String test = tests.contains(null)
                ? null
                : String.join(" or ", new LinkedHashSet<>(tests));
        final boolean guarded = isSet(test) && !test.equals(enclosing);
        final XmlElement holder = guarded
                ? parent.addElement(XSL, "if", "test", test)
                : parent;
        final XmlElement container = holder.addElement(ns, "Data", "Name", name);
        final Set<String> inner = new HashSet<>();
        for (final FieldMapping entry : entries) {
            final String[] parents = entry.dataParent().split("/", 2);
            if (parents.length == 1) {
                emitData(container, entry, guarded
                        ? test
                        : enclosing);
            } else {
                final String next = parents[1].split("/", 2)[0];
                if (inner.add(next)) {
                    emitContainer(container, next, entries.stream()
                            .filter(e -> e.dataParent().contains("/"))
                            .map(e -> e.withDataParent(e.dataParent().split("/", 2)[1]))
                            .filter(e -> next.equals(e.dataParent().split("/", 2)[0]))
                            .toList(), guarded
                            ? test
                            : enclosing);
                }
            }
        }
    }

    // A Data entry: mcp:data(name, value) when its guard is only that the value is present, else an xsl:if around
    // the element (or a loop over repeated values).
    private void emitData(final XmlElement parent, final FieldMapping entry, final String enclosing) {
        final XmlElement holder;
        if (entry.repeat()) {
            holder = parent.addElement(XSL, "for-each", "select", repeatItems(entry));
        } else {
            final String test = leafTest(entry);
            final boolean guarded = isSet(test) && !test.equals(enclosing);
            if (entry.value() == null && dataFunctionWanted && guarded) {
                // The values' variable, passed whole: the function writes the first.
                final String expression = valueExpression(entry);
                final String values = expression.endsWith("[1]")
                        ? expression.substring(0, expression.length() - 3)
                        : expression;
                if (VARIABLE_ONLY.matcher(values).matches()
                    && (test.equals(values) || test.equals("exists(" + values + ")"))) {
                    dataFunctionUsed = true;
                    warnOfSeveral(parent, entry);
                    parent.addElement(XSL, "sequence", "select", "mcp:" + dataFunction + "("
                                                                 + XPathText.literal(entry.dataName()) + ", "
                                                                 + values + ")");
                    return;
                }
            }
            holder = guarded
                    ? parent.addElement(XSL, "if", "test", test)
                    : parent;
            warnOfSeveral(holder, entry);
        }
        final XmlElement element = holder.addElement(ns, "Data", "Name", entry.dataName());
        if (entry.value() != null) {
            // A literal element's attributes are value templates: a brace in a constant is doubled.
            element.set("Value", entry.value().replace("{", "{{").replace("}", "}}"));
        } else {
            final String expression = valueExpression(entry);
            if ("interpolated".equals(style.dataValues()) && expression.indexOf('{') < 0
                && expression.indexOf('}') < 0) {
                element.set("Value", "{" + expression + "}");
            } else {
                element.addElement(XSL, "attribute", "name", "Value", "select", expression);
            }
        }
    }

    // A Data entry written out in full, as a template would hold it: equal tokens write the same Data.
    private String dataToken(final FieldMapping entry) {
        if (entry.repeat() || entry.dataParent() != null) {
            if (!tokenCache.containsKey(entry)) {
                tokenCache.put(entry, "#repeat-" + repeatTokens++);     // a loop over values: never shared
            }
            return tokenCache.get(entry);
        }
        if (!tokenCache.containsKey(entry)) {
            final XmlElement holder = XmlElement.create(null, "fragment");
            inScope(holder, () -> emitData(holder, entry, null));
            tokenCache.put(entry, XmlSerialiser.toXml(holder));
        }
        return tokenCache.get(entry);
    }

    private List<String> tokens(final EventNode node) {
        return node.data.stream().map(entry -> dataToken(entry.entry())).toList();
    }

    // Runs of Data entries several rules write the same way, in a row, data_run_min or more: each written once as a
    // template. Greedily, the run saving most first; each rule keeps its Data in order, so its Events don't change
    // (asked for by the user: a FortiGate translation wrote the same thirty Data entries in four rules).
    private void chooseDataRuns(final List<Rooted> roots) {
        if ("inline".equals(style.layout())) {
            return;
        }
        final List<List<String>> lists = new ArrayList<>();
        final List<List<String>> writers = new ArrayList<>();
        final Map<List<String>, Integer> index = new HashMap<>();
        for (final Rooted rooted : roots) {
            collectData(rooted.root(), rooted.rule().name(), lists, writers, index);
        }
        final long least = style.dataRunMin();
        final List<boolean[]> taken = lists.stream().map(tokens -> new boolean[tokens.size()]).toList();
        while (true) {
            final Map<List<String>, List<int[]>> places = new LinkedHashMap<>();
            for (int n = 0; n < lists.size(); n++) {
                final List<String> tokens = lists.get(n);
                for (int i = 0; i < tokens.size(); i++) {
                    for (int j = i + 1; j <= tokens.size(); j++) {
                        if (taken.get(n)[j - 1]) {
                            break;
                        }
                        if (j - i >= least) {
                            places.computeIfAbsent(List.copyOf(tokens.subList(i, j)), ignored -> new ArrayList<>())
                                    .add(new int[]{n, i});
                        }
                    }
                }
            }
            // Where a run occurs in distinct places (one list may hold it twice only if they don't overlap).
            List<String> best = null;
            List<int[]> bestSpots = null;
            int bestSaving = 0;
            for (final Map.Entry<List<String>, List<int[]>> place : places.entrySet()) {
                final List<String> run = place.getKey();
                final List<int[]> spots = new ArrayList<>();
                final Map<Integer, Integer> last = new HashMap<>();
                for (final int[] found : place.getValue()) {
                    if (found[1] >= last.getOrDefault(found[0], -1)) {
                        spots.add(found);
                        last.put(found[0], found[1] + run.size());
                    }
                }
                final int count = spots.stream().mapToInt(spot -> writers.get(spot[0]).size()).sum();
                final int saving = run.size() * (count - 1);
                if (count > 1 && (saving > bestSaving || (saving == bestSaving && best != null
                                                          && run.size() > best.size()))) {
                    best = run;
                    bestSpots = spots;
                    bestSaving = saving;
                }
            }
            if (best == null) {
                break;
            }
            final List<String> runUsers = new ArrayList<>();
            for (final int[] spot : bestSpots) {
                for (int k = spot[1]; k < spot[1] + best.size(); k++) {
                    taken.get(spot[0])[k] = true;
                }
                dataPlans.computeIfAbsent(lists.get(spot[0]), ignored -> new ArrayList<>())
                        .add(new Run(spot[1], best.size(), best));
                writers.get(spot[0]).stream().filter(u -> !runUsers.contains(u)).forEach(runUsers::add);
            }
            dataUsers.put(best, runUsers);
        }
    }

    private void collectData(final EventNode node,
                             final String rule,
                             final List<List<String>> lists,
                             final List<List<String>> writers,
                             final Map<List<String>, Integer> index) {
        for (final EventNode kid : node.kids.values()) {
            collectData(kid, rule, lists, writers, index);
        }
        if (!node.data.isEmpty()) {
            final List<String> tokens = tokens(node);
            if (!index.containsKey(tokens)) {
                index.put(tokens, lists.size());
                lists.add(tokens);
                writers.add(new ArrayList<>());
            }
            final List<String> those = writers.get(index.get(tokens));
            if (!those.contains(rule)) {
                those.add(rule);
            }
        }
    }

    private List<Run> dataPlan(final EventNode node) {
        if (node.data.isEmpty() || dataPlans.isEmpty()) {
            return List.of();
        }
        return dataPlans.getOrDefault(tokens(node), List.of());
    }

    // The template writing a run of Data entries, named after its first and last, made once.
    private String dataTemplate(final List<String> run, final List<FieldMapping> entries) {
        if (!dataTemplates.containsKey(run)) {
            final String naming = style.naming();
            final Set<String> taken = takenTemplateNames();
            final List<String> words = new ArrayList<>();
            for (final FieldMapping entry : List.of(entries.getFirst(), entries.getLast())) {
                final String word = ToolText.strip(NOT_ALPHANUMERIC.matcher(or(entry.dataName(), "")).replaceAll("-"),
                        "-").toLowerCase(Locale.ROOT);
                words.add(word.isEmpty()
                        ? "data"
                        : word);
            }
            final String name = XsltNames.uniqueName(XsltNames.styleName("data-" + words.get(0) + "-to-"
                                                                         + words.get(1), naming), taken, naming);
            final XmlElement template = newTemplate(name);
            dataTemplates.put(run, name);
            final String key = "data run " + name;
            templates.put(key, new NamedTemplate(name, template));
            users.put(key, dataUsers.getOrDefault(run, List.of()));
            inScope(template, () -> entries.forEach(entry -> emitData(template, entry, null)));
        }
        return dataTemplates.get(run);
    }

    private Set<String> takenTemplateNames() {
        final Set<String> taken = new HashSet<>();
        templates.values().forEach(template -> taken.add(template.name()));
        m.shared().forEach(use -> taken.add(use.template()));
        taken.addAll(ruleNames.values());
        taken.add("event");
        taken.add("item");
        return taken;
    }

    private String repeatItems(final FieldMapping entry) {
        final Source given = sourceOf(entry);
        return itemsOf(given.field(), given.xpath(), given.scope());
    }

    private void emitElement(final XmlElement parent,
                             final EventNode node,
                             final String enclosing,
                             final boolean inline) {
        // Working out the guard reads every field below; declare them only if the guard is written.
        final Map<String, String> outer = scope;
        scope = new LinkedHashMap<>();
        final String test = testOf(node);
        final Map<String, String> used = scope;
        scope = outer;
        // EventDetail/Unknown is never guarded: the schema requires an action element, so leaving it out when its
        // Data are all empty fails the Event, where Unknown (all of whose content is optional) written empty is valid.
        // Seen: text records with no stack trace, their Unknown's only Data the trace's, written with no action
        // element.
        final boolean unknown = node.leaf == null && "Unknown".equals(node.child.name())
                                && "EventDetail".equals(parent.localName());
        final boolean guarded = !unknown && isSet(test) && !test.equals(enclosing);
        if (guarded) {
            used.forEach(scope::putIfAbsent);
        }
        final XmlElement holder = guarded
                ? parent.addElement(XSL, "if", "test", test)
                : parent;
        final XmlElement element = holder.addElement(ns, node.child.name());
        if (node.leaf != null) {
            writeValue(element, node.leaf);
        } else {
            emit(element, node, isSet(test)
                    ? test
                    : enclosing, inline);
        }
    }

    // --- named templates for fragments that repeat ---

    // Elements with children, or leaves read from the record; constant leaves aren't worth a template.
    private static boolean shareable(final EventNode node) {
        if (node.shared != null) {      // already a call: not wrapped in another template
            return false;
        }
        return node.leaf == null || node.leaf.value() == null;
    }

    // The node's path and the node written out in full with its guard, so equal keys mean the same element with the
    // same output for any record. Only the same path is shared: an EventSource/Client/IPAddress and a
    // Destination/Device/IPAddress read from one field stay apart, as they mean different things. The action element
    // alone may differ (Network's Deny and Permit, Authenticate and View): a Source, a Destination or a Data list below
    // it means the same whichever it is (seen: four Network rules each writing the same Source, Destination and
    // thirty Data, 93 KB of XSLT).
    private String key(final EventNode node) {
        if (!keys.containsKey(node)) {
            final XmlElement holder = XmlElement.create(null, "fragment");
            inScope(holder, () -> emitElement(holder, node, null, true));
            keys.put(node, MappingFacts.actionFree(node.path) + "\n" + XmlSerialiser.toXml(holder));
        }
        return keys.get(node);
    }

    // Keep a variable only where it helps. Every field is first read into one at the top of the template; a field
    // read fewer than style.variable_min_reads times goes back inline, and a variable whose reads all sit in one rule
    // (one xsl:when) is declared at the start of that rule instead of the top.
    private void tidyVariables(final XmlElement template) {
        final List<XmlElement> branches = new ArrayList<>();
        for (final XmlElement choose : template.childElements(XSL, "choose")) {
            branches.addAll(choose.childElements());
        }
        final Map<XmlNode, XmlElement> homes = new IdentityHashMap<>();
        for (final XmlElement branch : branches) {
            for (final XmlNode below : branch.iter()) {
                if (below != branch) {
                    homes.put(below, branch);
                }
            }
        }
        final Map<String, String> raw = new HashMap<>();
        names.forEach((selector, name) -> raw.put(name, selector));
        final Map<XmlElement, Integer> placed = new IdentityHashMap<>();
        for (final XmlElement variable : template.childElements(XSL, "variable")) {
            final String name = variable.get("name");
            final String select = variable.get("select");
            if (keep.contains(name)) {
                continue;
            }
            final Pattern pattern = Pattern.compile("\\$" + Pattern.quote(name) + "(?![\\w.-])",
                    Pattern.UNICODE_CHARACTER_CLASS);
            // A Data element's Value="{...}" (style data_values interpolated) reads it too.
            final List<Read> reads = new ArrayList<>();
            for (final XmlElement el : template.iterElements()) {
                if (el == variable) {
                    continue;
                }
                for (final String attribute : List.of("test", "select", "Value")) {
                    final String value = el.get(attribute);
                    if (value != null && pattern.matcher(value).find()
                        && (!"Value".equals(attribute) || el.is(ns, "Data"))) {
                        reads.add(new Read(el, attribute));
                    }
                }
            }
            // Text two extractions read stays a variable, whatever the style's threshold: each is a regex over it.
            int parsed = 0;
            if (extractedTexts.contains(name)) {
                final Pattern analysed = Pattern.compile("analyze-string\\(string\\(\\$" + Pattern.quote(name)
                                                         + "\\[1\\]\\)");
                for (final Read read : reads) {
                    parsed += count(analysed, read.element().get(read.attribute()));
                }
            }
            // Inlined into a value template, a brace in the expression would be read as one: it stays a variable.
            final String selector = raw.get(name);
            final boolean braced = reads.stream().anyMatch(read -> "Value".equals(read.attribute()))
                                   && (selector.indexOf('{') >= 0 || selector.indexOf('}') >= 0);
            final int readCount = reads.stream().mapToInt(read -> count(pattern, read.element().get(read.attribute())))
                    .sum();
            if (parsed < 2 && !braced && readCount < style.variableMinReads()) {
                template.remove(variable);
                inline(name, select, pattern, reads, raw);
                continue;
            }
            final Set<XmlElement> rules = Collections.newSetFromMap(new IdentityHashMap<>());
            boolean outside = false;
            for (final Read read : reads) {
                final XmlElement home = homes.get(read.element());
                if (home == null) {
                    outside = true;
                } else {
                    rules.add(home);
                }
            }
            if (rules.size() == 1 && !outside) {
                // A rule's own test sits on its xsl:when, outside the branch, so it counts as the template's.
                final XmlElement branch = rules.iterator().next();
                final int at = placed.getOrDefault(branch, 0);
                branch.insert(at, variable);
                placed.put(branch, at + 1);
            }
        }
    }

    // A variable read too few times, written back where it is read.
    private void inline(final String name,
                        final String select,
                        final Pattern pattern,
                        final List<Read> reads,
                        final Map<String, String> raw) {
        final String selector = raw.get(name);
        final String plain = !xpathNames.contains(name) || MappingFacts.isCall(selector)
                ? selector
                : "(" + selector + ")";
        // A key=value function gives one string at most (as="xs:string?"), so its test reads as normalize-space().
        final boolean oneValue = m.nilValues().isEmpty() && keyed.values().stream()
                .anyMatch(function -> selector.startsWith("mcp:" + function.function() + "(")
                                      && selector.endsWith(")"));
        // A test of whether the input has a value keeps the predicate: any input may give several values (a key
        // given again in a Data Splitter's record, an XML path, an xpath), which normalize-space() would refuse.
        // Seen: a present condition on a repeated key failed every record holding it twice.
        final String hasValue = oneValue
                ? XPathText.hasValueText(selector)
                : select;
        // The first value, as $name[1] reads it: in brackets, as a [1] after a path takes the first of each step's
        // nodes (every data element's @value) rather than the first of all. An xpath's selector is in brackets
        // already, and a key=value function gives one string at most.
        final String values = oneValue || xpathNames.contains(name)
                ? plain
                : "(" + selector + ")";
        for (final Read read : reads) {
            final String value = read.element().get(read.attribute());
            final Matcher matcher = pattern.matcher(value);
            final StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                // Only a test of whether the field has a value needs blank values left out. A value (after its
                // guard, or after 'then'), a comparison and a [1] read the selector as it is.
                final String after = value.substring(matcher.end());
                final String before = ToolText.rstrip(value.substring(0, matcher.start()));
                if (after.startsWith("[1]")) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(values));
                    continue;
                }
                final boolean asIs = "Value".equals(read.attribute())
                                     || ("select".equals(read.attribute())
                                         && value.startsWith("mcp:" + dataFunction + "("))
                                     || (before.isEmpty() && after.isEmpty() && "select".equals(read.attribute()))
                                     || after.startsWith("[")
                                     || EQUALS_AFTER.matcher(after).lookingAt()
                                     || before.endsWith("then");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(asIs
                        ? plain
                        : hasValue));
            }
            matcher.appendTail(sb);
            read.element().set(read.attribute(), sb.toString());
        }
        // A variable still to be inlined may read this one: its selector reads the input itself from now on, or
        // inlining it would bring back a variable no longer declared (seen: $msg, the text a key=value function
        // parses, read by $srcip and inlined first).
        for (final String other : List.copyOf(raw.keySet())) {
            raw.put(other, pattern.matcher(raw.get(other)).replaceAll(Matcher.quoteReplacement(plain)));
        }
    }

    private static int count(final Pattern pattern, final String text) {
        final Matcher matcher = pattern.matcher(text);
        int n = 0;
        while (matcher.find()) {
            n++;
        }
        return n;
    }

    // Run write for a template's body, then declare the variables it used at the top of the template.
    private void inScope(final XmlElement template, final Runnable write) {
        final Map<String, String> outer = scope;
        scope = new LinkedHashMap<>();
        try {
            write.run();
            int n = 0;
            for (final Map.Entry<String, String> variable : scope.entrySet()) {
                template.insert(n++, XmlElement.create(XSL, "variable", "name", variable.getKey(), "select",
                        variable.getValue()));
            }
        } finally {
            scope = outer;
        }
    }

    // Pick the fragments to write once as named templates: the largest that still occurs more than once, counting a
    // template's body once however often it is called, until none repeats.
    private void chooseShared(final List<Rooted> roots) {
        for (final Rooted rooted : roots) {
            countShared(rooted.root(), rooted.rule().name(), new LinkedHashMap<>(), null);
        }
        while (true) {
            final Map<String, Integer> counts = new LinkedHashMap<>();
            final Set<String> seen = new HashSet<>();
            for (final Rooted rooted : roots) {
                countShared(rooted.root(), rooted.rule().name(), counts, seen);
            }
            String longest = null;
            int longestLength = -1;
            for (final Map.Entry<String, Integer> counted : counts.entrySet()) {
                if (counted.getValue() > 1 && !shared.contains(counted.getKey())) {
                    final int length = counted.getKey().codePointCount(0, counted.getKey().length());
                    if (length > longestLength) {
                        longest = counted.getKey();
                        longestLength = length;
                    }
                }
            }
            if (longest == null) {
                return;
            }
            shared.add(longest);
        }
    }

    private void countShared(final EventNode node, final String rule, final Map<String, Integer> counts,
                             final Set<String> seen) {
        for (final EventNode kid : node.kids.values()) {
            if (!shareable(kid)) {
                continue;
            }
            final String k = key(kid);
            counts.merge(k, 1, Integer::sum);
            if (seen == null) {
                final List<String> those = users.computeIfAbsent(k, ignored -> new ArrayList<>());
                if (!those.contains(rule)) {
                    those.add(rule);
                }
            } else if (shared.contains(k)) {
                if (seen.contains(k)) {
                    continue;
                }
                seen.add(k);
            }
            countShared(kid, rule, counts, seen);
        }
    }

    private String templateFor(final EventNode node) {
        final String k = key(node);
        if (!templates.containsKey(k)) {
            // The element's name, or as much of its path as tells it apart, e.g. authenticate_user; variants of one
            // path are numbered.
            final String naming = style.naming();
            // Not a shared template's name either: the importing XSLT's own template would override it.
            final Set<String> taken = takenTemplateNames();
            final List<String> steps = ToolText.split(node.path, "/");
            final List<String> pathParts = steps.subList(1, steps.size());
            final List<String> candidates = new ArrayList<>();
            for (int n = 1; n <= pathParts.size(); n++) {
                candidates.add(XsltNames.styleName(String.join("-", pathParts.subList(pathParts.size() - n,
                        pathParts.size())), naming));
            }
            final String name = candidates.stream().filter(candidate -> !taken.contains(candidate)).findFirst()
                    .orElseGet(() -> XsltNames.uniqueName(candidates.getLast(), taken, naming));
            final XmlElement template = newTemplate(name);
            templates.put(k, new NamedTemplate(name, template));
            inScope(template, () -> emitElement(template, node, null, false));
        }
        return templates.get(k).name();
    }

    // A template of the layout's kind: a template rule with its own mode, or a named template.
    private XmlElement newTemplate(final String name) {
        if ("modes".equals(style.layout())) {
            return XmlElement.create(XSL, "template", "match", "node()", "mode", name);
        }
        return XmlElement.create(XSL, "template", "name", name);
    }

    // Run a template of the layout's kind on the record (or item): it keeps the context, and $record.
    private void invoke(final XmlElement parent, final String name) {
        if ("modes".equals(style.layout())) {
            parent.addElement(XSL, "apply-templates", "select", ".", "mode", name);
        } else {
            parent.addElement(XSL, "call-template", "name", name);
        }
    }

    private String stylesheet(final String version) {
        eventTrees.checkInput();
        // Drop conditions are rules without an event, tried first.
        final List<EventRule> rules = new ArrayList<>();
        m.dropWhen().forEach(drop -> rules.add(EventRule.of("drop: " + drop.reason(), drop.when(), true, List.of())));
        rules.addAll(m.events());
        final List<Rooted> trees = new ArrayList<>();
        for (final EventRule rule : rules) {
            trees.add(new Rooted(rule, rule.drop()
                    ? null
                    : eventTrees.tree(rule)));
        }
        // Each event kind's template, named before the shared parts' templates are, which avoid these names.
        if (!"inline".equals(style.layout())) {
            final Set<String> taken = new HashSet<>();
            m.shared().forEach(use -> taken.add(use.template()));
            taken.add("event");
            taken.add("item");
            for (final EventRule rule : m.events()) {
                if (!rule.drop()) {
                    final String name = XsltNames.uniqueName(XsltNames.styleName("event-type-" + rule.name(),
                            style.naming()), taken, style.naming());
                    ruleNames.put(rule.name(), name);
                    taken.add(name);
                }
            }
        }
        final List<String> catchAll = rules.subList(0, rules.size() - 1).stream()
                .filter(rule -> rule.when().isEmpty())
                .map(EventRule::name)
                .toList();
        if (!catchAll.isEmpty()) {
            note(problems, "Rules " + ToolText.list(catchAll) + " have no conditions, so the rules after them "
                           + "never run; put the rule without conditions last");
        }
        eventTrees.dataWithAHome(rules);
        final List<String> unread = new ArrayList<>();
        for (final Extraction ex : m.extract()) {
            ex.names().stream().filter(name -> !name.isEmpty() && !MappingFacts.readsInput(m, name))
                    .forEach(unread::add);
        }
        if (!unread.isEmpty()) {
            note(warnings, "extract names " + ToolText.list(unread) + ", which nothing reads: map each to the "
                           + "element that means it (a Data entry if nothing else fits), or leave it out of names "
                           + "('').");
        }
        final boolean usesDictMap = m.allEntries().stream().anyMatch(entry -> isSet(entry.dictionary()));
        final boolean callsJson = JSON_CALL.matcher(ToolJson.dumps(m.toJson(DumpMode.WITHOUT_NONE))).find();
        final boolean usesJson = !m.jsonFields().isEmpty() || !m.xmlFields().isEmpty() || callsJson
                                 || !ownFunctions.isEmpty() || !keyedFunctions.isEmpty() || zoneFunctionWanted
                                 || dataFunctionWanted;     // the mcp prefix: its own functions
        final Map<String, String> bound = new LinkedHashMap<>();
        m.functions().forEach(functions -> bound.put(functions.prefix(), functions.namespace()));
        final XmlElement sheet = XmlElement.create(XSL, "stylesheet", namespaces(usesDictMap, usesJson, bound));
        sheet.set("version", "3.0");
        sheet.set("xpath-default-namespace", MappingFacts.inputNamespace(m));
        // Imports come first in a stylesheet: one per document, for its named templates or its functions.
        final Set<String> hrefs = new LinkedHashSet<>();
        m.shared().forEach(use -> hrefs.add(use.href()));
        m.functions().forEach(functions -> hrefs.add(functions.href()));
        hrefs.addAll(m.imports());
        hrefs.forEach(href -> sheet.addElement(XSL, "import", "href", href));
        final List<String> own = new ArrayList<>(List.of("stroom", "xs"));
        if (!m.extract().isEmpty()) {
            own.add("fn");
        }
        if (usesDictMap) {
            own.add("map");
        }
        if (usesJson) {
            own.add("mcp");
        }
        final List<String> excluded = new ArrayList<>(own);
        bound.keySet().stream().filter(prefix -> !own.contains(prefix)).forEach(excluded::add);
        sheet.set("exclude-result-prefixes", String.join(" ", excluded));
        if (!m.jsonFields().isEmpty() || callsJson) {
            sheet.append(XmlParser.parse(SAFE_JSON));
        }
        if (!m.xmlFields().isEmpty()) {
            sheet.append(XmlParser.parse(SAFE_XML));
        }
        final XmlElement rootTemplate = sheet.addElement(XSL, "template", "match", isSet(m.root())
                ? m.root()
                : DEFAULT_ROOT.getOrDefault(m.input(), ""));
        final XmlElement events = rootTemplate.addElement(ns, "Events", "Version", version);
        // The schema's own system id where it is known (an environment's fork has one of its own), else
        // event-logging's for the version.
        final String systemId = NullSafe.nonBlankStringElse(NullSafe.get(schema, EventSchema::systemId),
                "file://event-logging-v" + version + ".xsd");
        events.set(MappingFacts.XSI, "schemaLocation", ns + " " + systemId);
        String records = isSet(m.record())
                ? m.record()
                : "json".equals(m.input())
                        ? JSON_RECORDS.get(m.jsonLayout())
                        : DEFAULT_RECORD.getOrDefault(m.input(), "");
        if ("xml_fragments".equals(m.input()) && !isSet(m.root())) {
            records = "*/" + m.record();   // the fragments sit under the wrapper's root, whatever its namespace
        }
        events.addElement(XSL, "apply-templates", "select", records, "mode", "event");
        final List<Rooted> written = trees.stream().filter(rooted -> rooted.root() != null).toList();
        chooseShared(written);
        chooseDataRuns(written);
        XmlElement recordTemplate = sheet.addElement(XSL, "template", "match", "*", "mode", "event");
        final XmlElement eventTemplate = recordTemplate;
        XmlElement apply = null;
        if (itemMode) {
            // One record, several events: each item is handed to the rules with the record as a tunnel parameter.
            final String select = FOR_EACH_XPATH.matcher(m.forEach()).find()
                    ? m.forEach()
                    : itemsOf(m.forEach(), null, null);
            apply = recordTemplate.addElement(XSL, "apply-templates", "select", select, "mode", "item");
            apply.addElement(XSL, "with-param", "name", "record", "select", ".", "tunnel", "yes");
            recordTemplate = sheet.addElement(XSL, "template", "match", "*", "mode", "item");
        }
        final XmlElement body = recordTemplate;
        inScope(body, () -> writeRules(body, rules, trees));
        tidyVariables(body);
        final List<XmlElement> others = new ArrayList<>();
        ruleTemplates.forEach(template -> others.add(template.template()));
        templates.values().forEach(template -> others.add(template.template()));
        others.forEach(this::tidyVariables);
        final List<XmlElement> all = new ArrayList<>();
        all.add(body);
        all.addAll(others);
        if ("just_in_time".equals(style.variables())) {
            all.forEach(XsltGenerator::justInTime);
        }
        if (usesRecord) {
            for (final XmlElement template : all) {
                if (XmlSerialiser.toXml(template).contains("$record")) {
                    template.insert(0, XmlElement.create(XSL, "param", "name", "record", "tunnel", "yes"));
                }
            }
        }
        warnOfUnreadFields(sheet, eventTemplate, all);
        logKinds(sheet);
        if (logsRecordNo) {
            readRecordNo(eventTemplate, apply, body);
        }
        declareMaps(sheet);
        if (!referenceMaps.isEmpty()) {
            note(warnings, "Lookups read reference map(s) " + ToolText.list(new TreeSet<>(referenceMaps)) + ": "
                           + "the pipeline needs the feed that loads each as a pipeline reference (create_pipeline "
                           + "references, or update_pipeline (references=...)); find_reference_data lists the maps and "
                           + "their feeds.");
        }
        // Called with the record as context, so they read its fields just as the event rules do.
        for (final RuleTemplate template : ruleTemplates) {
            sheet.append(new XmlComment(template.header()));
            sheet.append(template.template());
        }
        for (final Map.Entry<String, NamedTemplate> template : templates.entrySet()) {
            final List<String> those = users.get(template.getKey());
            sheet.append(new XmlComment(XsltComments.comment(template.getValue().name() + ": shared by the rule"
                                                             + (those.size() > 1
                    ? "s"
                    : "") + " " + those.stream().map(ToolText::repr).collect(Collectors.joining(", ")))));
            sheet.append(template.getValue().template());
        }
        ownFunctions().forEach(sheet::append);
        return XmlSerialiser.toPrettyDocument(sheet);
    }

    private Map<String, String> namespaces(final boolean usesDictMap,
                                           final boolean usesJson,
                                           final Map<String, String> bound) {
        final Map<String, String> namespaces = new LinkedHashMap<>();
        namespaces.put(null, ns);
        namespaces.put("xsl", XSL);
        namespaces.put("xsi", MappingFacts.XSI);
        namespaces.put("stroom", "stroom");
        namespaces.put("xs", MappingFacts.XS);
        if (!m.extract().isEmpty()) {
            namespaces.put("fn", MappingFacts.FN);
        }
        if (usesDictMap) {
            namespaces.put("map", MappingFacts.MAP_NS);
        }
        if (usesJson) {
            namespaces.put("mcp", MappingFacts.MCP_NS);
        }
        namespaces.putAll(bound);
        return namespaces;
    }

    private void declareMaps(final XmlElement sheet) {
        int n = 0;
        for (final Map.Entry<List<List<String>>, String> map : maps.entrySet()) {
            final XmlElement variable = XmlElement.create(XSL, "variable", "name", map.getValue(), "as",
                    "map(xs:string, xs:string)");
            final XmlElement entries = variable.addElement(XSL, "map");
            for (final List<String> item : map.getKey()) {
                entries.addElement(XSL, "map-entry", "key", XPathText.literal(item.get(0)), "select",
                        XPathText.literal(item.get(1)));
            }
            sheet.insert(n++, variable);
        }
        for (final Map.Entry<List<String>, String> dictionary : dicts.entrySet()) {
            sheet.insert(n++, XmlElement.create(XSL, "variable", "name", dictionary.getValue(), "select",
                    XPathText.dictionaryText(dictionary.getKey().get(0), dictionary.getKey().get(1))));
        }
    }

    // The record's number read once, first, for every record ([#READ_RECORD_NO]); after the variables are tidied, so
    // it isn't moved into the branch that logs. Declared first, and read after the template's other leading
    // variables, so a template that declares its variables at its start still does. With items, read for the record
    // and handed to the items' template.
    // A record's fields that no rule reads, nor ignored_fields lists, logged (WARN) once a stream each, with the
    // field's name: what the sample the mapping was drafted from lacked, reported by processing (seen in testing: a
    // field only some records had was never mapped, and nothing said so). WARN, not INFO: Stroom writes at most
    // 1,000 lines of each severity to a stream's Error stream, and a stream's kept-Unknown lines (INFO, one a record)
    // used them all, so a field first seen later went unreported (seen in the e2e suite). Error triage classes these
    // lines benign, so stepping stays clean though many a mapping leaves fields out (a syslog header's, say).
    // Read from the templates written: the names their xpaths read. Data Splitter and JSON records only, whose
    // fields are named.
    private void warnOfUnreadFields(final XmlElement sheet,
                                    final XmlElement eventTemplate,
                                    final List<XmlElement> templates) {
        if (!checksUnreadFields()) {
            return;
        }
        if (itemMode) {
            // Its rules run on the items, the fields are the record's: checked once for the record.
            eventTemplate.addElement(XSL, "call-template", "name", UNREAD_TEMPLATE);
        } else if (!unreadCalled) {
            // Every rule leaves its records untranslated.
            return;
        }
        final boolean data = "data_splitter".equals(m.input());
        final Pattern named = data
                ? DATA_NAME_READ
                : JSON_KEY_READ;
        final Set<String> read = new TreeSet<>(m.ignoredFields());
        final List<XmlElement> scanned = new ArrayList<>(templates);
        // With for_each, the record's own template reads the items' field.
        scanned.add(eventTemplate);
        for (final XmlElement template : scanned) {
            final Matcher matcher = named.matcher(XmlSerialiser.toXml(template));
            while (matcher.find()) {
                read.add(matcher.group(2));
            }
        }
        final String attribute = data
                ? "@name"
                : "@key";
        final String value = data
                ? "@value"
                : ".";
        final String known = read.isEmpty()
                ? ""
                : "[not(" + attribute + " = (" + read.stream().map(XPathText::literal)
                        .collect(Collectors.joining(", ")) + "))]";
        final String nils = m.nilValues().isEmpty()
                ? ""
                : "[not(normalize-space(" + value + ") = (" + m.nilValues().stream().map(XPathText::literal)
                        .collect(Collectors.joining(", ")) + "))]";
        // Turned off and on in the XSLT itself, by its variable near the top (asked for by the user): a team that
        // doesn't want the Error streams can stop them in Stroom's editor.
        final int top = leading(sheet, "import", "output", "strip-space", "preserve-space", "key", "param",
                "variable");
        sheet.insert(top, XmlElement.create(XSL, "variable", "name", LOG_UNMAPPED, "as", "xs:boolean", "select",
                m.logUnmappedFields()
                        ? "true()"
                        : "false()"));
        sheet.insert(top, new XmlComment(XsltComments.comment("Fields of the source no rule reads are logged (WARN, "
                                                              + "once a stream) to the Error stream: false() to "
                                                              + "stop it (the mapping's log_unmapped_fields)")));
        final XmlElement template = XmlElement.create(XSL, "template", "name", UNREAD_TEMPLATE);
        final XmlElement each = template.addElement(XSL, "if", "test", "$" + LOG_UNMAPPED)
                .addElement(XSL, "for-each", "select", (data
                ? "data"
                : "*") + "[" + attribute + "]" + known + "[normalize-space(string(" + value + "))]" + nils);
        each.addElement(XSL, "variable", "name", "mcp_unread", "select", "concat('mcp-unread:', " + attribute + ")");
        final XmlElement first = each.addElement(XSL, "if", "test", "not(string(stroom:get($mcp_unread)))");
        first.addElement(XSL, "value-of", "select", "stroom:put($mcp_unread, 'y')");
        // Once a stream, by name: no record's number, which every record would then read (see READ_RECORD_NO).
        first.addElement(XSL, "sequence", "select", "stroom:log('WARN', concat(" + XPathText.literal(UNREAD_FIELD)
                                                    + ", " + attribute + ", " + XPathText.literal(UNREAD_FIELD_END)
                                                    + "))");
        sheet.append(new XmlComment(XsltComments.comment(UNREAD_TEMPLATE + ": a field of the record no rule reads, "
                                                         + "logged once a stream (ignored_fields: left out on "
                                                         + "purpose); called by the rules that translate their "
                                                         + "records")));
        sheet.append(template);
    }

    private static void readRecordNo(final XmlElement eventTemplate,
                                     final XmlElement apply,
                                     final XmlElement rulesTemplate) {
        eventTemplate.insert(leading(eventTemplate, "param"), XmlElement.create(XSL, "variable", "name", RECORD_NO,
                "select", "stroom:record-no()"));
        eventTemplate.insert(leading(eventTemplate, "param", "variable"), XmlElement.create(XSL, "sequence",
                "select", READ_RECORD_NO));
        if (apply != null) {
            apply.addElement(XSL, "with-param", "name", RECORD_NO, "select", "$" + RECORD_NO, "tunnel", "yes");
            rulesTemplate.insert(0, XmlElement.create(XSL, "param", "name", RECORD_NO, "tunnel", "yes"));
        }
    }

    // The index, among a template's children, after its leading xsl elements of these names (and the white space
    // and comments between them).
    private static int leading(final XmlElement template, final String... names) {
        final Set<String> wanted = Set.of(names);
        final List<XmlNode> children = template.children();
        int after = 0;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof final XmlElement element) {
                if (!XSL.equals(element.namespace()) || !wanted.contains(element.localName())) {
                    break;
                }
                after = i + 1;
            }
        }
        return after;
    }

    // The call logging a record of the rule's kind: once a stream for each kind (its group: the rule and the values the
    // rules test), the first record's line as it was, not the records run together on one line, so no line is longer
    // than a record's.
    private void logKind(final XmlElement holder, final EventRule rule, final String text, final List<String> tested) {
        final XmlElement call = holder.addElement(XSL, "call-template", "name", LOG_KIND_TEMPLATE);
        call.addElement(XSL, "with-param", "name", "group", "select", "concat(" + XPathText.literal(rule.name()
                                                                                                + "|") + ", "
                                                                          + testedValues(tested) + ")");
        call.addElement(XSL, "with-param", "name", "line", "select", logged(text.replace("{rule}", rule.name()),
                tested));
        logKindCalled = true;
    }

    // The template that logs a record of a kind, once a stream for each kind (its group), as fields no rule reads are
    // logged.
    private void logKinds(final XmlElement sheet) {
        if (!logKindCalled) {
            return;
        }
        final XmlElement template = XmlElement.create(XSL, "template", "name", LOG_KIND_TEMPLATE);
        template.addElement(XSL, "param", "name", "group");
        template.addElement(XSL, "param", "name", "line");
        final XmlElement first = template.addElement(XSL, "if", "test",
                "not(string(stroom:get(concat('mcp-kind:', $group))))");
        first.addElement(XSL, "value-of", "select", "stroom:put(concat('mcp-kind:', $group), 'y')");
        first.addElement(XSL, "sequence", "select", "stroom:log('INFO', $line)");
        sheet.append(new XmlComment(XsltComments.comment(LOG_KIND_TEMPLATE + ": a record a rule keeps as Unknown on "
                                                         + "purpose, or a catch-all rule takes, logged once a "
                                                         + "stream for each kind (the rule and the values the rules "
                                                         + "test)")));
        sheet.append(template);
    }

    // The values the rules test, joined, for a kept-Unknown record's kind ('' when the rules test none).
    private String testedValues(final List<String> tested) {
        if (tested.isEmpty()) {
            return "''";
        }
        final List<String> values = tested.stream()
                .map(field -> "string((" + ref(field, null, "condition", null) + ")[1])")
                .toList();
        return "string-join((" + String.join(", ", values) + "), '|')";
    }

    // The values the rules test, put in the log lines: a whole feed's missed records are then grouped from its Error
    // streams alone, however large the feed, without reading the raw records.
    private String logged(final String text, final List<String> tested) {
        logsRecordNo = true;
        final List<String> logParts = new ArrayList<>(List.of(XPathText.literal(text), "$" + RECORD_NO));
        if (!tested.isEmpty()) {
            final List<String> values = tested.stream()
                    .map(field -> "concat(" + XPathText.literal(field + "=") + ", string((" + ref(field, null,
                            "condition", null) + ")[1]))")
                    .toList();
            logParts.addAll(List.of("' ('", "string-join((" + String.join(", ", values) + "), ' | ')", "')'"));
        }
        return "concat(" + String.join(", ", logParts) + ")";
    }

    // Whether the translation checks for fields no rule reads: its records' fields are named.
    private boolean checksUnreadFields() {
        return "data_splitter".equals(m.input()) || "json".equals(m.input());
    }

    private void writeRules(final XmlElement recordTemplate, final List<EventRule> rules, final List<Rooted> trees) {
        final boolean isConditional = rules.stream().anyMatch(rule -> !rule.when().isEmpty());
        final List<String> tested = new ArrayList<>(rules.stream()
                .flatMap(rule -> rule.when().stream())
                .map(Condition::field)
                .filter(XsltGenerator::isSet)
                .distinct()
                .toList());
        final List<String> logged = tested.subList(0, Math.min(LOGGED_FIELDS, tested.size()));
        final XmlElement body = isConditional
                ? recordTemplate.addElement(XSL, "choose")
                : recordTemplate;
        // Each rule's test and whether it leaves its records untranslated, in order, for the check of fields no rule
        // reads.
        final List<String> tests = new ArrayList<>();
        final List<Boolean> drops = new ArrayList<>();
        for (final Rooted rooted : trees) {
            final EventRule rule = rooted.rule();
            // Each condition is a comparison or a call, which bind tighter than and: no brackets needed.
            final String test = rule.when().stream().map(c -> condition(c, false)).collect(Collectors.joining(" and "));
            final XmlElement holder = isConditional
                    ? (test.isEmpty()
                    ? body.addElement(XSL, "otherwise")
                    : body.addElement(XSL, "when", "test", test))
                    : body;
            tests.add(test);
            drops.add(rule.drop());
            final List<List<Condition>> earlier = rules.subList(0, rules.indexOf(rule)).stream()
                    .filter(before -> !before.drop())
                    .map(EventRule::when)
                    .toList();
            if (!rule.drop() && checksUnreadFields() && !itemMode) {
                unreadCalled = true;
                if (!isConditional) {
                    holder.addElement(XSL, "call-template", "name", UNREAD_TEMPLATE);
                }
            }
            final Object when = rule.when().isEmpty()
                    ? "every record"
                    : rule.when().stream().map(c -> condition(c, true)).toList();
            if (rule.drop()) {
                holder.append(new XmlComment(" " + rule.name() + ": left untranslated on purpose "));
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("event", rule.name());
                entry.put("when", when);
                entry.put("dropped", true);
                summary.add(entry);
            } else {
                writeRule(rule, rooted.root(), holder, isConditional, logged, isConditional
                                                                              && catchAll(rule, earlier));
                final Set<String> fields = new TreeSet<>();
                final List<FieldMapping> entries = new ArrayList<>(m.common());
                entries.addAll(rule.fields());
                entries.forEach(e -> fields.add(e.strippedPath() + (isSet(e.dataName())
                        ? "[" + e.dataKey() + "]"
                        : "")));
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("event", rule.name());
                entry.put("when", when);
                entry.put("fields", List.copyOf(fields));
                summary.add(entry);
            }
            if (test.isEmpty() && isConditional) {
                break;
            }
        }
        if (isConditional && rules.stream().allMatch(rule -> !rule.when().isEmpty()) && "warn".equals(m.unmatched())) {
            final XmlElement otherwise = body.addElement(XSL, "otherwise");
            otherwise.addElement(XSL, "sequence", "select", "stroom:log('WARN', " + logged(UNMATCHED, logged) + ")");
        }
        if (isConditional && unreadCalled && checksUnreadFields() && !itemMode) {
            unreadCheckBefore(recordTemplate, body, tests, drops);
        }
    }

    // Whether a rule is a catch-all: it takes what the rules before it leave, being the one with no conditions after
    // rules with them, or one whose conditions are fewer than an earlier rule's, all of them that rule's (variant=cef,
    // after variant=cef and cef_name=APPFW_SQL).
    private static boolean catchAll(final EventRule rule, final List<List<Condition>> earlier) {
        if (earlier.isEmpty()) {
            return false;
        }
        return rule.when().isEmpty()
               || earlier.stream().anyMatch(before -> before.size() > rule.when().size()
                                                      && before.containsAll(rule.when()));
    }

    // The check of fields no rule reads, called once before the rules (asked for by the user: a call in each rule's
    // branch was unsightly, and a branch added by hand would miss it), for every record but those a rule leaves
    // untranslated on purpose (seen in the e2e suite: their fields reported as read by no rule). A drop rule takes a
    // record when its test holds and no translating rule before it does.
    private static void unreadCheckBefore(final XmlElement recordTemplate,
                                          final XmlElement rules,
                                          final List<String> tests,
                                          final List<Boolean> drops) {
        final List<String> dropped = new ArrayList<>();
        final List<String> translatingBefore = new ArrayList<>();
        for (int i = 0; i < tests.size(); i++) {
            final String test = tests.get(i).isEmpty()
                    ? "true()"
                    : "(" + tests.get(i) + ")";
            if (drops.get(i)) {
                dropped.add(translatingBefore.isEmpty()
                        ? test
                        : "(" + test + " and not(" + String.join(" or ", translatingBefore) + "))");
            } else {
                translatingBefore.add(test);
            }
        }
        final int at = recordTemplate.children().indexOf(rules);
        final XmlElement call = XmlElement.create(XSL, "call-template", "name", UNREAD_TEMPLATE);
        if (dropped.isEmpty()) {
            recordTemplate.insert(at, call);
        } else {
            final XmlElement guard = XmlElement.create(XSL, "if", "test", "not(" + String.join(" or ", dropped) + ")");
            guard.append(call);
            recordTemplate.insert(at, guard);
        }
        recordTemplate.insert(at, new XmlComment(XsltComments.comment("Fields of the record no rule reads are "
                                                                      + "logged (" + UNREAD_TEMPLATE + ")"
                                                                      + (dropped.isEmpty()
                ? ""
                : ", but for records left untranslated on purpose"))));
    }

    private void writeRule(final EventRule rule,
                           final EventNode tree,
                           final XmlElement holder,
                           final boolean isConditional,
                           final List<String> tested,
                           final boolean catchAll) {
        final String name = ruleNames.get(rule.name());
        final XmlElement home;
        if (name != null) {
            // The kind's own template, run on the record; its variables are declared in it.
            invoke(holder, name);
            home = newTemplate(name);
            ruleTemplates.add(new RuleTemplate(ruleHeader(rule, isConditional), home));
        } else {
            home = holder;
        }
        if (isSet(rule.allowUnknown())) {
            final String reason = rule.allowUnknown().replace("--", "-");
            home.append(new XmlComment(" " + rule.name() + ": EventDetail/Unknown on purpose: " + reason + " "));
            // Logged (INFO, into the raw stream's Error stream) so a whole feed's Unknown records are found from its
            // Error streams, not by sampling its Events: once a stream for each kind, the rule and the values the
            // rules test (asked for by the user: a line a record filled the Error stream), the first record's line
            // as it was, not the records run together on one line, so no line is longer than a record's.
            logKind(holder, rule, KEPT_UNKNOWN, tested);
        } else if (catchAll) {
            // A catch-all rule's records logged as a kept-Unknown rule's are (asked for by the user: seen in chat 163,
            // two kinds of event no one had seen went through a catch-all into Alerts, and nothing said so), so
            // review_coverage finds the kinds it takes for the user to classify.
            logKind(holder, rule, CAUGHT, tested);
        }
        final XmlElement event = home.addElement(ns, "Event");
        if (markRules) {
            // A processing instruction the output carries: a comment in the stylesheet is not copied to the output,
            // and one written (xsl:comment) Stroom's stepping drops, so every event was left to the guess by its
            // elements (seen in testing, an event with an element added by hand fitted no rule).
            event.addElement(XSL, "processing-instruction", "name", RULE_INSTRUCTION).setText(rule.name());
        }
        if (name != null) {
            inScope(home, () -> emit(event, tree, null, false));
        } else {
            emit(event, tree, null, false);
        }
    }

    // A record's zone from its source: the value (blank-trimmed), each mapped value its zone, then mcp:zone.
    private String zoneText(final ZoneSource source) {
        final String value = "normalize-space(string((" + (source.field() != null
                ? XPathText.fieldText(m.input(), source.field())
                : "stroom:meta(" + XPathText.literal(source.meta()) + ")") + ")[1]))";
        String mapped = value;
        if (source.map() != null) {
            final List<Map.Entry<String, String>> entries = new ArrayList<>(source.map().entrySet());
            for (int i = entries.size() - 1; i >= 0; i--) {
                mapped = XPathText.mapStepText(value, entries.get(i).getKey(), entries.get(i).getValue(), mapped);
            }
        }
        return "mcp:" + ZONE_FUNCTION + "(" + mapped + ", " + XPathText.literal(source.defaultZone()) + ")";
    }

    // The XSLT's own functions, one per repeated conversion: a value in, the converted value out.
    private List<XmlElement> ownFunctions() {
        final List<XmlElement> out = new ArrayList<>();
        if (zoneFunctionWanted) {
            // As ZoneSource.asGiven writes a zone: an offset Stroom can't read but whose meaning is plain (+5, +5:30,
            // +10.5, after any UTC/GMT/UT) as it reads it; blank, the default; anything else as it is.
            final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:" + ZONE_FUNCTION, "as",
                    "xs:string");
            function.append(new XmlComment(XsltComments.comment("A record's time zone as Stroom reads one: the "
                                                                + "default where the record gives none")));
            function.addElement(XSL, "param", "name", "value", "as", "xs:string");
            function.addElement(XSL, "param", "name", "default", "as", "xs:string");
            // A UTC, GMT or UT prefix only before an offset (XPath's replace refuses a regex matching nothing).
            function.addElement(XSL, "variable", "name", "prefix", "select",
                    "if (matches($value, '^(UTC|GMT|UT)[+-]')) then replace($value, '^(UTC|GMT|UT)[+-].*$', '$1') "
                    + "else ''");
            function.addElement(XSL, "variable", "name", "offset", "select",
                    "substring($value, string-length($prefix) + 1)");
            function.addElement(XSL, "sequence", "select", "if ($value = '') then $default "
                    + "else if (matches($offset, '^[+-]\\d(:\\d\\d)?$')) then concat($prefix, "
                    + "substring($offset, 1, 1), '0', substring($offset, 2, 1), ':', "
                    + "if (string-length($offset) gt 2) then substring($offset, 4, 2) else '00') "
                    + "else if (matches($offset, '^[+-]\\d{1,2}\\.(5|25|75)$')) then concat($prefix, "
                    + "substring($offset, 1, 1), format-number(xs:integer(substring-before(substring($offset, 2), "
                    + "'.')), '00'), ':', "
                    + "if (ends-with($offset, '.25')) then '15' else if (ends-with($offset, '.75')) then '45' else "
                    + "'30') "
                    + "else $value");
            out.add(function);
        }
        if (dataFunctionUsed) {
            final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:" + dataFunction, "as",
                    "element()?");
            function.append(new XmlComment(XsltComments.comment("A Data entry, Name and Value, when the value is "
                                                                + "present" + (m.nilValues().isEmpty()
                    ? " (not blank)"
                    : " (not blank, nor " + String.join(", ", m.nilValues()) + ")"))));
            function.addElement(XSL, "param", "name", "name", "as", "xs:string");
            function.addElement(XSL, "param", "name", "value", "as", "item()*");
            final XmlElement present = function.addElement(XSL, "if", "test", "exists($value[normalize-space(.)]"
                                                                              + nil() + ")");
            present.addElement(ns, "Data", "Name", "{$name}", "Value", "{$value[1]}");
            out.add(function);
        }
        for (final Map.Entry<Shape, String> entry : keyedFunctions.entrySet()) {
            final Shape shape = entry.getKey();
            final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:" + entry.getValue(), "as",
                    "xs:string?");
            function.append(new XmlComment(XsltComments.comment("The value of a key in the text: the regex "
                                                                + shape.prefix() + "<key>" + shape.suffix()
                                                                + ", its first group")));
            function.addElement(XSL, "param", "name", "text", "as", "item()*");
            function.addElement(XSL, "param", "name", "key", "as", "xs:string");
            function.addElement(XSL, "sequence", "select", XPathText.keyedText(shape.prefix(), shape.suffix(),
                    shape.flags()));
            out.add(function);
        }
        for (final Map.Entry<Conversion, String> entry : ownFunctions.entrySet()) {
            final Conversion conversion = entry.getKey();
            final String body = conversion.isTime()
                    ? XPathText.timeExpression(conversion.value(), conversion.timezone(), "$value")
                    : XPathText.transformExpression(conversion.value(), "$value");
            final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:" + entry.getValue(), "as",
                    "xs:string?");
            // Any one item, as the inline expression takes it: a node's text, or a number from an xpath.
            function.addElement(XSL, "param", "name", "value", "as", "item()?");
            function.addElement(XSL, "sequence", "select", body);
            out.add(function);
        }
        return out;
    }

    /// Each of the template's variables declared just before the first element that reads it, inside the innermost
    /// element holding every read (asked for by the user: hoisted to the template's start, thirty of them, the XSLT
    /// was hard to read against their use). Last first, so a variable another reads is placed before that one.
    ///
    /// @param template A template.
    public static void justInTime(final XmlElement template) {
        final List<XmlElement> variables = template.iterElements(XSL, "variable");
        for (int v = variables.size() - 1; v >= 0; v--) {
            final XmlElement variable = variables.get(v);
            final Pattern pattern = Pattern.compile("\\$" + Pattern.quote(or(variable.get("name"), ""))
                                                    + "(?![\\w.-])", Pattern.UNICODE_CHARACTER_CLASS);
            final List<XmlElement> reads = new ArrayList<>();
            for (final XmlElement el : template.iterElements()) {
                if (el != variable && el.attributes().stream()
                        .anyMatch(attribute -> pattern.matcher(attribute.value()).find())) {
                    reads.add(el);
                }
            }
            if (reads.isEmpty()) {
                continue;
            }
            final List<List<XmlElement>> chains = reads.stream().map(read -> chain(read, template)).toList();
            XmlElement common = template;
            final int shortest = chains.stream().mapToInt(List::size).min().orElse(0);
            for (int level = 0; level < shortest; level++) {
                final XmlElement at = chains.getFirst().get(level);
                final int depth = level;
                if (chains.stream().allMatch(chain -> chain.get(depth) == at)) {
                    common = at;
                } else {
                    break;
                }
            }
            // The first read in document order as it is now (a variable moved already reads from where it went).
            final XmlElement first = reads.getFirst();
            final List<XmlElement> path = chain(first, template);
            final int commonAt = indexOf(path, common);
            XmlElement parent;
            XmlNode child;
            if (common == first || path.size() <= commonAt + 1) {
                parent = first.parent();
                child = first;
            } else {
                parent = common;
                child = path.get(commonAt + 1);
            }
            while (parent != null && parent != template && XSL.equals(parent.namespace())
                   && NO_VARIABLES.contains(parent.localName())) {
                child = parent;
                parent = parent.parent();
            }
            if (parent == null) {
                continue;
            }
            variable.parent().remove(variable);
            parent.insert(parent.indexOf(child), variable);
        }
    }

    private static List<XmlElement> chain(final XmlElement element, final XmlElement template) {
        final List<XmlElement> out = new ArrayList<>();
        for (XmlElement at = element; at != null; at = at.parent()) {
            out.addFirst(at);
            if (at == template) {
                break;
            }
        }
        return out;
    }

    private static int indexOf(final List<XmlElement> path, final XmlElement element) {
        for (int i = 0; i < path.size(); i++) {
            if (path.get(i) == element) {
                return i;
            }
        }
        return -1;
    }

    private static String or(final String value, final String otherwise) {
        return value == null || value.isEmpty()
                ? otherwise
                : value;
    }

    private static boolean isSet(final String text) {
        return text != null && !text.isEmpty();
    }

    // --------------------------------------------------------------------------------


    /// What [XsltGenerator#generate(TranslationMapping, EventSchema, String, boolean)] gives.
    ///
    /// @param ok            True if there are no problems.
    /// @param problems      What stops the XSLT being written, per mapping entry.
    /// @param warnings      What may be wrong.
    /// @param events        Per rule: its name, `when` (its conditions with the input's selectors, or `every
    ///                      record`), and `fields` (the paths it writes) or `dropped`.
    /// @param xslt          The XSLT, or null when there are problems.
    /// @param referenceMaps `reference_maps`: the reference maps lookups read, sorted.
    /// @param dictionaries  The Dictionary docs read, sorted.
    /// @param items         `items`: the mapping's for_each, or null.
    /// @param requiredOnInput Each rule's required elements written only when their inputs have a value (paths
    ///                      below Event), by rule, for the sample check to count the records that leave them out
    ///                      (not in [#toMap()]).
    public record Generated(boolean ok,
                            List<String> problems,
                            List<String> warnings,
                            List<Map<String, Object>> events,
                            String xslt,
                            List<String> referenceMaps,
                            List<String> dictionaries,
                            String items,
                            Map<String, List<String>> requiredOnInput) {

        /// @return It as a map, keys in the order the golden files pin: `ok`, `problems`, `warnings`, `events`, `xslt`,
        /// `reference_maps`, `dictionaries` and, with for_each, `items`.
        public Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("ok", ok);
            map.put("problems", problems);
            map.put("warnings", warnings);
            map.put("events", events);
            map.put("xslt", xslt);
            map.put("reference_maps", referenceMaps);
            map.put("dictionaries", dictionaries);
            if (items != null) {
                map.put("items", items);
            }
            return map;
        }
    }


    // --------------------------------------------------------------------------------


    private record Derived(Extraction extraction, int nr) {

    }

    private record Keyed(String function, String key) {

    }

    private record Shape(String field, String xpath, String scope, String flags, String prefix, String suffix) {

    }

    /// An entry's input.
    ///
    /// @param field The input field, or null.
    /// @param xpath The XPath computing it, or null.
    /// @param scope With for_each, `record` for the record's input.
    record Source(String field, String xpath, String scope) {

    }

    private record Run(int start, int length, List<String> tokens) {

    }

    private record NamedTemplate(String name, XmlElement template) {

    }

    private record RuleTemplate(String header, XmlElement template) {

    }

    private record Rooted(EventRule rule, EventNode root) {

    }

    private record Read(XmlElement element, String attribute) {

    }

    private record Item(int index, int isData, int n, EventNode node, EventNode.DataEntry data) {

    }

    // A conversion that could be a function of the XSLT's own: a transform, or a time format and zone.
    private record Conversion(boolean isTime, String value, String timezone) {

        /// @param transform A transform.
        /// @return It as a conversion.
        static Conversion transform(final String transform) {
            return new Conversion(false, transform, null);
        }

        /// @param format   A time format.
        /// @param timezone Its zone, or null.
        /// @return It as a conversion.
        static Conversion time(final String format, final String timezone) {
            return new Conversion(true, format, timezone);
        }

        /// @return Its key as text, e.g. `('time', 'yyyy', 'UTC')`: the XSLT's functions are in this key's order,
        /// which the golden XSLTs pin.
        String repr() {
            return isTime
                    ? "('time', " + ToolText.repr(value) + ", " + ToolText.repr(timezone) + ")"
                    : "('transform', " + ToolText.repr(value) + ")";
        }
    }
}
