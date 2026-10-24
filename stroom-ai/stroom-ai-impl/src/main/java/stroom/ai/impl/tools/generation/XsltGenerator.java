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
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.XmlTrees;

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

/// Writes an event-logging translation XSLT from a [TranslationMapping], as the Stroom MCP server's generator
/// (`utils/xsltgen.py`) writes it, to the byte.
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
    /// Values listed per element (EventSource table) or per TypeId (Descriptions) in the documentation.
    public static final int DOC_VALUES_SHOWN = 3;
    /// Logged for each record no rule matches (WARN), then the values the rules test: "No event mapping matched
    /// record 5 (action=view | type=)".
    public static final String UNMATCHED = "No event mapping matched record ";
    /// Logged (INFO) for each record a kept-Unknown rule takes; `{rule}` is the rule's name.
    public static final String KEPT_UNKNOWN = "Kept as Unknown by rule '{rule}': record ";
    /// How many of the fields the rules test the log lines carry.
    public static final int LOGGED_FIELDS = 4;
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
    private static final Map<String, String> EPOCH_FUNCTIONS = Map.of("epoch_ms", "from-epoch-ms",
            "epoch_s", "from-epoch-s", "epoch_us", "from-epoch-us", "epoch_ns", "from-epoch-ns");

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
    private boolean usesRecord;
    private final Set<List<List<String>>> xslMaps = new HashSet<>();
    private final Map<Conversion, String> ownFunctions;
    private final Map<Extraction, Keyed> keyed = new IdentityHashMap<>();
    private final Map<Shape, String> keyedFunctions = new LinkedHashMap<>();
    private final boolean dataFunctionWanted;
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

    private XsltGenerator(final TranslationMapping mapping, final EventSchema schema, final boolean markRules) {
        this.m = mapping;
        this.schema = schema;
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
        this.ownFunctions = chooseFunctions();
        chooseKeyed();
        // mcp:data writes a Data entry only when its value is present: one line a Data entry instead of an xsl:if
        // and two reads of the value (seen: 135 of them, 31 KB of a 50 KB translation).
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
                        : null);
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
        if (isSet(entry.timeFormat()) && !entry.hasMap() && !entry.repeat()) {
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
                        .add(entry.strippedPath() + "[" + or(entry.dataName(), "") + "]");
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
                final String words = String.join("[ _-]", PyText.split(key, "_"));
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
            final List<String> jsonKeys = PyText.split(fieldName.substring(held.length() + 1), ".");
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
                ? XPathText.anyOfText(entry.anyOf().stream().map(field -> source(field, null, entryScope)).toList())
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
        final List<String> steps = PyText.split(entry.strippedPath(), "/");
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
            final List<String> steps = PyText.split(entry.strippedPath(), "/");
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
            final Source given = sourceOf(entry);
            return "(" + source(given.field(), given.xpath(), given.scope()) + ")[normalize-space(.)]";
        }
        if (entry.hasMap()) {
            final String src = entryRef(entry);
            final String key = entry.transform() != null
                    ? scalar(entry, src)
                    : src;
            if (asXslMap(entry)) {
                return "exists(" + mapRef(entry, src) + "?(" + key + "))";
            }
            return key + " = (" + entry.map().keySet().stream().map(XPathText::literal)
                    .collect(Collectors.joining(", ")) + ")";
        }
        return entryHas(entry);
    }

    private String valueExpression(final FieldMapping entry) {
        if (entry.repeat()) {
            // Written inside xsl:for-each over the values: the current value, transformed if asked.
            return entry.transform() != null
                    ? scalar(entry, ".")
                    : ".";
        }
        final String src = entryRef(entry);
        final String key = entry.transform() != null
                ? scalar(entry, src)
                : src;
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
        final String one = entry.transform() != null
                ? scalar(entry, src)
                : src + "[1]";
        final String expression;
        if (isSet(format) && ownFunctions.containsKey(Conversion.time(format, zone))) {
            expression = "mcp:" + ownFunctions.get(Conversion.time(format, zone)) + "(" + one + ")";
        } else if (isSet(format)) {
            expression = XPathText.timeExpression(format, zone, one);
        } else {
            expression = entry.transform() != null
                    ? one
                    : src;
        }
        if (entry.defaultValue() != null) {
            return XPathText.defaultText(entryHas(entry), expression, entry.defaultValue());
        }
        return expression;
    }

    private void writeValue(final XmlElement element, final FieldMapping entry) {
        if (entry.value() != null) {
            element.setText(entry.value());
        } else {
            element.addElement(XSL, "value-of", "select", valueExpression(entry));
        }
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
        for (final Item item : list) {
            if (item.data() != null) {
                dataAt++;
                if (skip > 0) {
                    skip--;
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
                final String expression = valueExpression(entry);
                if (VARIABLE_ONLY.matcher(expression).matches()
                    && (test.equals(expression) || test.equals("exists(" + expression + ")"))) {
                    dataFunctionUsed = true;
                    parent.addElement(XSL, "sequence", "select", "mcp:" + dataFunction + "("
                                                                 + XPathText.literal(entry.dataName()) + ", "
                                                                 + expression + ")");
                    return;
                }
            }
            holder = guarded
                    ? parent.addElement(XSL, "if", "test", test)
                    : parent;
        }
        final XmlElement element = holder.addElement(EVT, "Data", "Name", entry.dataName());
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
        if (entry.repeat()) {
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
                final String word = PyText.strip(NOT_ALPHANUMERIC.matcher(or(entry.dataName(), "")).replaceAll("-"),
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
        final boolean guarded = isSet(test) && !test.equals(enclosing);
        if (guarded) {
            used.forEach(scope::putIfAbsent);
        }
        final XmlElement holder = guarded
                ? parent.addElement(XSL, "if", "test", test)
                : parent;
        final XmlElement element = holder.addElement(EVT, node.child.name());
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
                        && (!"Value".equals(attribute) || el.is(EVT, "Data"))) {
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
        // A Data Splitter or JSON field has one value per record, so its test reads naturally as
        // normalize-space(field); an XML path or an xpath may give several, which normalize-space() would refuse, so
        // those keep the predicate.
        final boolean single = !xpathNames.contains(name)
                               && ("data_splitter".equals(m.input()) || "json".equals(m.input()))
                               && m.nilValues().isEmpty();
        final String hasValue = single
                ? XPathText.hasValueText(selector)
                : select;
        for (final Read read : reads) {
            final String value = read.element().get(read.attribute());
            final Matcher matcher = pattern.matcher(value);
            final StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                // Only a test of whether the field has a value needs blank values left out. A value (after its
                // guard, or after 'then'), a comparison and a [1] read the selector as it is.
                final String after = value.substring(matcher.end());
                final String before = PyText.rstrip(value.substring(0, matcher.start()));
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
            final List<String> steps = PyText.split(node.path, "/");
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
            note(problems, "Rules " + PyText.list(catchAll) + " have no conditions, so the rules after them "
                           + "never run; put the rule without conditions last");
        }
        eventTrees.dataWithAHome(rules);
        final List<String> unread = new ArrayList<>();
        for (final Extraction ex : m.extract()) {
            ex.names().stream().filter(name -> !name.isEmpty() && !MappingFacts.readsInput(m, name))
                    .forEach(unread::add);
        }
        if (!unread.isEmpty()) {
            note(warnings, "extract names " + PyText.list(unread) + ", which nothing reads: map each to the "
                           + "element that means it (a Data entry if nothing else fits), or leave it out of names "
                           + "('').");
        }
        final boolean usesDictMap = m.allEntries().stream().anyMatch(entry -> isSet(entry.dictionary()));
        final boolean callsJson = JSON_CALL.matcher(PyJson.dumps(m.toJson(DumpMode.WITHOUT_NONE))).find();
        final boolean usesJson = !m.jsonFields().isEmpty() || !m.xmlFields().isEmpty() || callsJson
                                 || !ownFunctions.isEmpty() || !keyedFunctions.isEmpty()
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
        final XmlElement events = rootTemplate.addElement(EVT, "Events", "Version", version);
        events.set(MappingFacts.XSI, "schemaLocation", EVT + " file://event-logging-v" + version + ".xsd");
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
        if (itemMode) {
            // One record, several events: each item is handed to the rules with the record as a tunnel parameter.
            final String select = FOR_EACH_XPATH.matcher(m.forEach()).find()
                    ? m.forEach()
                    : itemsOf(m.forEach(), null, null);
            final XmlElement apply = recordTemplate.addElement(XSL, "apply-templates", "select", select, "mode",
                    "item");
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
        declareMaps(sheet);
        if (!referenceMaps.isEmpty()) {
            note(warnings, "Lookups read reference map(s) " + PyText.list(new TreeSet<>(referenceMaps)) + ": "
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
                    : "") + " " + those.stream().map(PyText::repr).collect(Collectors.joining(", ")))));
            sheet.append(template.getValue().template());
        }
        ownFunctions().forEach(sheet::append);
        return XmlSerialiser.toPrettyDocument(sheet);
    }

    private Map<String, String> namespaces(final boolean usesDictMap,
                                           final boolean usesJson,
                                           final Map<String, String> bound) {
        final Map<String, String> namespaces = new LinkedHashMap<>();
        namespaces.put(null, EVT);
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

    // The values the rules test, put in the log lines: a whole feed's missed records are then grouped from its Error
    // streams alone, however large the feed, without reading the raw records.
    private String logged(final String text, final List<String> tested) {
        final List<String> logParts = new ArrayList<>(List.of(XPathText.literal(text), "stroom:record-no()"));
        if (!tested.isEmpty()) {
            final List<String> values = tested.stream()
                    .map(field -> "concat(" + XPathText.literal(field + "=") + ", string((" + ref(field, null,
                            "condition", null) + ")[1]))")
                    .toList();
            logParts.addAll(List.of("' ('", "string-join((" + String.join(", ", values) + "), ' | ')", "')'"));
        }
        return "concat(" + String.join(", ", logParts) + ")";
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
        for (final Rooted rooted : trees) {
            final EventRule rule = rooted.rule();
            // Each condition is a comparison or a call, which bind tighter than and: no brackets needed.
            final String test = rule.when().stream().map(c -> condition(c, false)).collect(Collectors.joining(" and "));
            final XmlElement holder = isConditional
                    ? (test.isEmpty()
                    ? body.addElement(XSL, "otherwise")
                    : body.addElement(XSL, "when", "test", test))
                    : body;
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
                writeRule(rule, rooted.root(), holder, isConditional, logged);
                final Set<String> fields = new TreeSet<>();
                final List<FieldMapping> entries = new ArrayList<>(m.common());
                entries.addAll(rule.fields());
                entries.forEach(e -> fields.add(e.strippedPath() + (isSet(e.dataName())
                        ? "[" + e.dataName() + "]"
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
    }

    private void writeRule(final EventRule rule,
                           final EventNode tree,
                           final XmlElement holder,
                           final boolean isConditional,
                           final List<String> tested) {
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
            // Logged (INFO, into the raw stream's Error stream) so a whole feed's Unknown records are found exactly,
            // as unmatched ones are, not by sampling its Events.
            holder.addElement(XSL, "sequence", "select", "stroom:log('INFO', "
                                                         + logged(KEPT_UNKNOWN.replace("{rule}", rule.name()), tested)
                                                         + ")");
        }
        final XmlElement event = home.addElement(EVT, "Event");
        if (markRules) {
            event.append(new XmlComment(RULE_MARK + rule.name()));
        }
        if (name != null) {
            inScope(home, () -> emit(event, tree, null, false));
        } else {
            emit(event, tree, null, false);
        }
    }

    // The XSLT's own functions, one per repeated conversion: a value in, the converted value out.
    private List<XmlElement> ownFunctions() {
        final List<XmlElement> out = new ArrayList<>();
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
            present.addElement(EVT, "Data", "Name", "{$name}", "Value", "{$value}");
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


    /// What [XsltGenerator#generate(TranslationMapping, EventSchema, String, boolean)] gives, as the Stroom MCP
    /// server's `generate` gives it.
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
    public record Generated(boolean ok,
                            List<String> problems,
                            List<String> warnings,
                            List<Map<String, Object>> events,
                            String xslt,
                            List<String> referenceMaps,
                            List<String> dictionaries,
                            String items) {

        /// @return It as the Python generator's dict: `ok`, `problems`, `warnings`, `events`, `xslt`,
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

        /// @return As Python's repr of the tuple the server keys it by, which orders the functions.
        String repr() {
            return isTime
                    ? "('time', " + PyText.repr(value) + ", " + PyText.repr(timezone) + ")"
                    : "('transform', " + PyText.repr(value) + ")";
        }
    }
}
