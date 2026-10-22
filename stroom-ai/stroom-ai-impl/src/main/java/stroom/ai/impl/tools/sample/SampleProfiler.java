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

package stroom.ai.impl.tools.sample;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Profiles a raw data sample: its format, structure, fields, value types and timestamp patterns, and how Stroom
/// reads it (the parser, any text converter, what the XSLT then reads). The Stroom MCP server's
/// `utils/profile.py`.
///
/// A profile is JSON, as the Python server gave it: `characters`, `lines`, `format` (`xml`, `xml fragments`,
/// `json array`, `json object`, `json lines`, `delimited`, `cef`, `syslog rfc5424`, `syslog rfc3164`, `cri`,
/// `multi-line text`, `key=value` or `unknown text`), `records`, `fields` (each `field`, `fill_rate`, `type` and
/// `examples`), `suggested_parser`, and what the format needs (`text_converter`, `parser_properties`,
/// `xslt_input`, `delimiter`, `has_header`, `columns`, `malformed_lines`, `note`...).
public final class SampleProfiler {

    /// One XML document.
    public static final String XML = "xml";
    /// Several XML root elements, one record each.
    public static final String XML_FRAGMENTS = "xml fragments";
    /// A JSON array of records.
    public static final String JSON_ARRAY = "json array";
    /// One JSON object whose array holds the records.
    public static final String JSON_OBJECT = "json object";
    /// A JSON object a line.
    public static final String JSON_LINES = "json lines";
    /// Delimited text.
    public static final String DELIMITED = "delimited";
    /// CEF, alone or after a syslog header.
    public static final String CEF_FORMAT = "cef";
    /// RFC 5424 syslog.
    public static final String SYSLOG_5424_FORMAT = "syslog rfc5424";
    /// RFC 3164 syslog.
    public static final String SYSLOG_3164_FORMAT = "syslog rfc3164";
    /// Kubernetes CRI container log lines.
    public static final String CRI = "cri";
    /// Records of several lines, each starting with its time.
    public static final String MULTI_LINE = "multi-line text";
    /// key=value pairs.
    public static final String KEY_VALUE_FORMAT = "key=value";
    /// Text of no format known.
    public static final String UNKNOWN_TEXT = "unknown text";

    /// The start of an RFC 5424 syslog line.
    public static final Pattern SYSLOG_5424 = PyText.re("^<\\d{1,3}>1 \\S+ \\S+ \\S+ \\S+ \\S+");
    /// The start of an RFC 3164 syslog line.
    public static final Pattern SYSLOG_3164 = PyText.re(
            "^(<\\d{1,3}>)?[A-Z][a-z]{2} [ \\d]\\d \\d{2}:\\d{2}:\\d{2} \\S+ ");
    /// A key=value pair.
    public static final Pattern KEY_VALUE = PyText.re("(\\w[\\w.-]*)=(\"[^\"]*\"|\\S*)");
    /// The start of a CEF record.
    public static final Pattern CEF = PyText.re("CEF:\\d+\\|");
    /// Lines read when looking for those that aren't what the rest are (a JSON line cut short, a row short of a
    /// column).
    public static final int MALFORMED_SCAN = 5000;
    /// The most records profiled.
    public static final int MAX_RECORDS = 200;
    /// The Data Splitter spec for JSON lines some of which aren't JSON: one record a line, the JSON parsed in the
    /// XSLT.
    public static final String JSON_LINES_SPEC =
            "build_data_splitter spec {'kind': 'regex', 'pattern': '^(.*)$', 'names': ['json']}";
    /// The wrapper an XMLFragmentParser puts round XML fragments (its text converter, type XML_FRAGMENT): the entity
    /// `fragment` is the stream. Fragments that declare no namespace of their own take the wrapper's default,
    /// records:2.
    public static final String XML_FRAGMENT_WRAPPER = """
            <?xml version="1.1" encoding="UTF-8"?>
            <!DOCTYPE records [
            <!ENTITY fragment SYSTEM "fragment">
            ]>
            <records xmlns="records:2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="records:2 file://records-v2.0.xsd" version="2.0">
            &fragment;
            </records>
            """;
    /// The wrapper for fragments that are already event-logging `<Event>`s, in event-logging:3, `{version}` its
    /// schema version: in a records one they would be records:2 elements. Environments write their own wrappers
    /// this way too (seen on live: 'Event Logging v3.4.2 Fragments', whose Events root puts every fragment that
    /// declares no namespace in event-logging:3).
    public static final String EVENTS_FRAGMENT_WRAPPER = """
            <?xml version="1.1" encoding="UTF-8"?>
            <!DOCTYPE Events [
            <!ENTITY fragment SYSTEM "fragment">
            ]>
            <Events xmlns="event-logging:3" xmlns:stroom="stroom" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="event-logging:3 file://event-logging-v{version}.xsd" Version="{version}">
            &fragment;
            </Events>
            """;
    /// How a record of several lines starts: its time, as logs write it (ISO, with a space or T; bracketed;
    /// syslog's; Apache's day/month/year).
    public static final List<String> RECORD_STARTS = List.of("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}",
            "\\[\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}", "[A-Z][a-z]{2} [ \\d]\\d \\d{2}:\\d{2}:\\d{2}",
            "\\d{2}/[A-Z][a-z]{2}/\\d{4}:\\d{2}:\\d{2}:\\d{2}", "\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");

    static final Pattern XML_DECL = PyText.re("^\\s*<\\?xml[^>]*\\?>");

    private static final String XML_PARSER = "XMLParser (a translation template with one: find_pipeline_templates)";
    private static final String XML_TEXT_CONVERTER = "none: the XMLParser reads the document";
    private static final String JSON_PARSER = "JSONParser (a translation template with one: find_pipeline_templates)";
    private static final Pattern COMMENT = PyText.re("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern WRAPPER_ROOT = PyText.re("<([A-Za-z_][\\w.-]*)(\\s[^>]*)?>\\s*&fragment;");
    private static final Pattern WRAPPER_NS = PyText.re("\\sxmlns\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern DOCTYPE = PyText.re("<!DOCTYPE\\s+[\\w.:-]+");
    private static final Pattern INTEGER = PyText.re("-?\\d+");
    private static final Pattern DECIMAL = PyText.re("-?\\d+\\.\\d+");
    private static final Pattern NUMBER = PyText.re("-?\\d+(\\.\\d+)?");
    private static final Pattern JSON_AFTER_PREFIX = PyText.re("\\{.+\\}$");
    private static final Pattern NAME_CELL = PyText.re("^[A-Za-z_][A-Za-z0-9_.-]{0,40}$");
    private static final Pattern JSON_START = PyText.re("(\\[\\s*)?\\{\\s*\"");
    private static final Pattern SEPARATOR = PyText.re(" *[^\\w\\s\"'.:/@()\\[\\]{}<>=+-]{1,3} *| +:: +| +- +");
    private static final Pattern WRAPPER_HEAD = PyText.re(
            "^\\{\\s*(?:\"[^\"]*\"\\s*:\\s*(?:\"(?:\\\\.|[^\"\\\\])*\"|-?[\\d.eE+-]+|true|false|null)\\s*,\\s*)*"
            + "\"([^\"]+)\"\\s*:\\s*\\[\\s*(?=\\{)");
    private static final Pattern DIRECTIVE = PyText.re("^(#[A-Za-z][\\w-]*:?)(?:[ \\t]+(.*))?$");
    private static final Pattern COLUMN_NAME = PyText.re("^[A-Za-z_][\\w.()\\-/:]*$");
    private static final Pattern CRI_LINE = PyText.re(SplitterDryRun.CRI_LINE);
    private static final Map<String, Map<String, Object>> JSON_SETUP = jsonSetup();

    private SampleProfiler() {
        // Static methods only.
    }

    private static Map<String, Map<String, Object>> jsonSetup() {
        // How a JSON sample reaches the XSLT. The JSONParser reads every top-level value in the stream and, with
        // addRootObject (its default), wraps them all in one <map>; so JSON lines parse as /map/map with no text
        // converter, and an array as /map/array/map, or /array/map with addRootObject off.
        final Map<String, Map<String, Object>> setup = new LinkedHashMap<>();
        setup.put("lines", map(
                "text_converter", "none: the JSONParser element parses JSON lines itself; a text converter cannot",
                "parser_properties", map("jsonParser.addRootObject", true),
                "xslt_input", map("namespace", "http://www.w3.org/2013/XSL/json", "root", "/map", "record", "map",
                        "mapping", map("input", "json", "json_layout", "lines"))));
        setup.put("lines_with_bad", map(
                "text_converter", "a Data Splitter, one record a line (" + JSON_LINES_SPEC + ")",
                "suggested_parser", "DSParser with a Data Splitter, one record a line, the JSON read by the "
                                    + "mapping's json_fields: Stroom's JSONParser stops at a line that isn't JSON",
                "parser_properties", map(),
                "xslt_input", map("namespace", "records:2", "root", "/records", "record", "record",
                        "mapping", map("input", "data_splitter", "json_fields", List.of("json")))));
        setup.put("array", map(
                "text_converter", "none: the JSONParser element parses the array itself; a text converter cannot",
                "parser_properties", map("jsonParser.addRootObject", false),
                "xslt_input", map("namespace", "http://www.w3.org/2013/XSL/json", "root", "/array", "record", "map",
                        "mapping", map("input", "json", "json_layout", "array"))));
        return setup;
    }

    /// @param keysAndValues Keys (strings) and values in turn.
    /// @return A map of them, in that order.
    static Map<String, Object> map(final Object... keysAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    // A deep copy of a JSON value, so a setup can be changed without changing the constant.
    @SuppressWarnings("unchecked")
    private static <T> T copy(final T value) {
        if (value instanceof final Map<?, ?> source) {
            final Map<String, Object> out = new LinkedHashMap<>();
            source.forEach((k, v) -> out.put((String) k, copy(v)));
            return (T) out;
        }
        if (value instanceof final List<?> source) {
            final List<Object> out = new ArrayList<>();
            source.forEach(v -> out.add(copy(v)));
            return (T) out;
        }
        return value;
    }

    /// @param layout `lines`, `lines_with_bad` or `array`.
    /// @return How JSON of that layout reaches the XSLT: `text_converter`, `parser_properties`, `xslt_input` (and
    /// `suggested_parser` for lines with bad ones).
    public static Map<String, Object> jsonSetup(final String layout) {
        return copy(JSON_SETUP.get(layout));
    }

    // --------------------------------------------------------------------------------
    // XML.
    // --------------------------------------------------------------------------------

    /// @param code A fragment wrapper's code.
    /// @return The wrapper's root element (null if it has none round `&fragment;`), and its default namespace (""
    /// for none): the namespace fragments that declare none are read in.
    public static String[] wrapperRoot(final String code) {
        final Matcher m = WRAPPER_ROOT.matcher(PyText.sub(COMMENT, "", code));
        if (!m.find()) {
            return new String[]{null, ""};
        }
        final Matcher ns = WRAPPER_NS.matcher(m.group(2) == null
                ? ""
                : m.group(2));
        return new String[]{m.group(1), ns.find()
                ? ns.group(1)
                : ""};
    }

    /// An environment's fragment wrapper as a build saves its copy: without comments (seen: live's 'Event Logging
    /// v3.4.2 Fragments' carries an older version of itself commented out, curly quotes and all, which every copy
    /// inherited), its DOCTYPE named after the root element it wraps (seen: DOCTYPE records on an `<Events>` root),
    /// and no blank lines left behind. The environment's own wrapper is left as it is.
    ///
    /// @param code The wrapper's code.
    /// @return It cleaned.
    public static String cleanWrapper(final String code) {
        String text = PyText.sub(COMMENT, "", code);
        final String root = wrapperRoot(text)[0];
        if (root != null) {
            text = PyText.subFirst(DOCTYPE, "<!DOCTYPE " + root, text);
        }
        final List<String> lines = new ArrayList<>();
        for (final String line : PyText.splitlines(text)) {
            if (!PyText.isBlank(line)) {
                lines.add(PyText.rstrip(line));
            }
        }
        return String.join("\n", lines) + "\n";
    }

    /// @param fragments XML fragments.
    /// @return True if they are event-logging `<Event>`s already (EventTime, EventDetail...), in its namespace or
    /// none; not a source's own `<Event>` (Windows' System and EventData).
    static boolean eventLoggingFragments(final List<XmlTree.Element> fragments) {
        final XmlTree.Element first = fragments.getFirst();
        final Set<String> parts = new LinkedHashSet<>();
        first.elements().forEach(child -> parts.add(child.localName));
        return "Event".equals(first.localName)
               && (first.namespace == null || "event-logging:3".equals(first.namespace))
               && parts.contains("EventTime") && parts.contains("EventDetail");
    }

    /// A single-root XML document of which only the start was read (a big stream's head: no closing root tag): its
    /// root holding the complete records before the cut. Seen in production: a 54 MB `<tweets>` stream, read to
    /// 1 MB, failed to parse as a document, wasn't fragments either, and was profiled as CSV with no records.
    ///
    /// @param text The text.
    /// @return The root's name and namespace with its complete records, or null when the text isn't the start of
    /// one document (fragments, a syntax error, not XML, or a whole document, which the XML parser reads or
    /// rejects).
    static XmlTree.Pull cutDocument(final String text) {
        final XmlTree.Pull pull = XmlTree.pull(PyText.subFirst(XML_DECL, "", PyText.strip(text, "\uFEFF\r\n ")),
                Integer.MAX_VALUE);
        if (pull.syntaxError() || pull.closed() || pull.root() == null || pull.records().isEmpty()) {
            return null;
        }
        return pull;
    }

    /// @param text A text.
    /// @return The top-level elements of text that is XML fragments (several root elements, e.g. one `<Event>` a
    /// line); null for a single document or anything that isn't XML.
    static List<XmlTree.Element> xmlFragments(final String text) {
        final String body = PyText.subFirst(XML_DECL, "", text);
        final XmlTree.Element root;
        try {
            root = XmlTree.parse("<fragments>" + body + "</fragments>");
        } catch (final XmlTree.XmlException e) {
            return null;
        }
        final List<XmlTree.Element> elements = root.elements();
        return elements.size() > 1
                ? elements
                : null;
    }

    /// How fragments are read: the wrapper converter (the environment's own when it has one: `wrapper` with its
    /// `code`, `name`, `uuid` and `path`), the parser, and the namespace the XSLT then reads them in.
    ///
    /// @param namespace The fragments' own namespace, or null if they declare none.
    /// @param record    The fragments' element name.
    /// @param wrapper   The environment's own wrapper (`code`, `name`, `uuid`, `path`), or null.
    /// @param others    The environment's other wrappers (`name`, `path`, `root`, `namespace`), or an empty list.
    /// @return `text_converter`, `parser` and `xslt_input`.
    public static Map<String, Object> xmlFragmentSetup(final String namespace,
                                                       final String record,
                                                       final Map<String, ?> wrapper,
                                                       final List<? extends Map<String, ?>> others) {
        final Object wrapperCode = wrapper == null
                ? null
                : wrapper.get("code");
        final String code = wrapperCode instanceof final String given && !given.isEmpty()
                ? cleanWrapper(given)
                : XML_FRAGMENT_WRAPPER;
        final String[] root = wrapperRoot(code);
        final String wrapperNs = root[1];
        final String effective = namespace != null && !namespace.isEmpty()
                ? namespace
                : wrapperNs;
        final Map<String, Object> converter = map("type", "XML_FRAGMENT", "code", code, "root", root[0],
                "namespace", wrapperNs);
        if (wrapper != null && wrapper.get("uuid") != null && !"".equals(wrapper.get("uuid"))) {
            final Map<String, Object> environment = new LinkedHashMap<>();
            for (final String key : List.of("name", "uuid", "path")) {
                if (wrapper.get(key) != null && !"".equals(wrapper.get(key))) {
                    environment.put(key, wrapper.get(key));
                }
            }
            converter.put("environment", environment);
            converter.put("note", "The environment's own wrapper (" + PyText.str(wrapper.get("name")) + "): save "
                                  + "this code in the build (build_data_splitter save_as=<name>), as sibling sources "
                                  + "do, so the fragments are read the way the environment's other pipelines read "
                                  + "theirs.");
        } else {
            converter.put("note", "The wrapper the XMLFragmentParser puts round the fragments (its textConverter); "
                                  + "use the template's own wrapper if it already sets one.");
        }
        if (others != null && !others.isEmpty()) {
            final List<Map<String, Object>> otherWrappers = new ArrayList<>();
            for (final Map<String, ?> other : others) {
                final Map<String, Object> kept = new LinkedHashMap<>();
                for (final String key : List.of("name", "path", "root", "namespace")) {
                    if (other.containsKey(key)) {
                        kept.put(key, other.get(key));
                    }
                }
                otherWrappers.add(kept);
            }
            converter.put("other_wrappers", otherWrappers);
        }
        final boolean ownNamespace = namespace != null && !namespace.isEmpty();
        return map(
                "text_converter", converter,
                "parser", "XMLFragmentParser: a template whose chain has one (child_must_supply names its "
                          + "textConverter), else create_pipeline from a translation template whose parser is the "
                          + "XMLParser, with replace_parser='XMLFragmentParser'",
                "xslt_input", map("namespace", effective, "root", "/", "record", "*/" + record,
                        "note", ownNamespace
                                ? "The fragments declare their own namespace and keep it inside the wrapper."
                                : "The fragments declare no namespace, so inside the wrapper they take its default, "
                                  + (wrapperNs.isEmpty()
                                        ? "none"
                                        : wrapperNs) + ": set xml_namespace to that. The XSLT reads them in it and "
                                  + "writes Events in event-logging:3.",
                        "mapping", map("input", "xml_fragments", "xml_namespace", effective, "record", record)));
    }

    // A record's leaf texts by element name, and its attributes as name@attribute.
    private static Map<String, Object> xmlRecordFields(final XmlTree.Element record) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final XmlTree.Element node : record.iter()) {
            for (final XmlTree.Attribute attribute : node.attributes) {
                out.put(node.localName + "@" + attribute.localName(), attribute.value());
            }
            if (node.isLeaf() && !PyText.isBlank(node.text())) {
                out.put(node.localName, PyText.strip(node.text()));
            }
        }
        return out;
    }

    private static Map<String, Integer> localNames(final List<XmlTree.Element> elements) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        elements.forEach(element -> counts.merge(element.localName, 1, Integer::sum));
        return counts;
    }

    /// @param counts Counts by key, in the order the keys were first seen.
    /// @param <K>    The keys' type.
    /// @return The most common key, the first seen of those as common (Python's `Counter.most_common(1)`).
    static <K> K mostCommon(final Map<K, Integer> counts) {
        K best = null;
        int most = Integer.MIN_VALUE;
        for (final Map.Entry<K, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > most) {
                best = entry.getKey();
                most = entry.getValue();
            }
        }
        return best;
    }

    private static List<Map<String, Object>> xmlFieldsOf(final List<XmlTree.Element> records, final int most) {
        final List<Map<String, Object>> fields = new ArrayList<>();
        for (final XmlTree.Element record : records.subList(0, Math.min(most, records.size()))) {
            fields.add(xmlRecordFields(record));
        }
        return fields;
    }

    // --------------------------------------------------------------------------------
    // Values and fields.
    // --------------------------------------------------------------------------------

    /// The type of a value, as the profile names it: `boolean`, `number`, `timestamp (<pattern>)`, `object`,
    /// `array`, `empty`, `ip`, `integer`, `decimal`, `embedded json`, `embedded json after prefix` or `string`.
    ///
    /// @param value A value (text, or a value read from JSON).
    /// @return Its type.
    public static String valueType(final Object value) {
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            // A number can be a time too (epoch seconds, with or without a fraction, or milliseconds): read as its
            // text.
            final String pattern = TimePatterns.inferTimePattern(PyText.str(value));
            return pattern != null && TimePatterns.EPOCH_NAMES.containsKey(pattern)
                    ? "timestamp (" + pattern + ")"
                    : "number";
        }
        if (value instanceof Map) {
            return "object";
        }
        if (value instanceof Collection) {
            return "array";
        }
        final String text = PyText.strip(PyText.str(value));
        if (text.isEmpty()) {
            return "empty";
        }
        final int length = PyText.len(text);
        if (length >= 6 && length <= 40 && text.chars().anyMatch(Character::isDigit)) {
            final String pattern = TimePatterns.inferTimePattern(text);
            if (pattern != null && TimePatterns.checkTimeFormat(pattern, List.of(text)) == null) {
                return "timestamp (" + pattern + ")";
            }
        }
        if (IpAddresses.isIpAddress(text)) {
            return "ip";
        }
        if (PyText.fullmatch(INTEGER, text)) {
            return "integer";
        }
        if (PyText.fullmatch(DECIMAL, text)) {
            return "decimal";
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return "boolean";
        }
        if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
            if (parses(text)) {
                return "embedded json";
            }
        }
        if (PyText.search(JSON_AFTER_PREFIX, text) && parses(text.substring(text.indexOf('{')))) {
            return "embedded json after prefix";
        }
        return "string";
    }

    private static boolean parses(final String json) {
        try {
            PyJson.loads(json);
            return true;
        } catch (final PyJson.JsonDecodeException e) {
            return false;
        }
    }

    /// A JSON value's fields, nested objects flattened into dotted names to four levels (a value that isn't an
    /// object is the field `(value)`).
    ///
    /// @param value A value read from JSON.
    /// @return Its fields by name.
    public static Map<String, Object> flatten(final Object value) {
        final Map<String, Object> out = new LinkedHashMap<>();
        flatten(value, "", 0, out);
        return out;
    }

    private static void flatten(final Object value, final String prefix, final int depth,
                                final Map<String, Object> out) {
        if (value instanceof final Map<?, ?> map && depth < 4) {
            map.forEach((key, inner) -> flatten(inner, prefix.isEmpty()
                    ? String.valueOf(key)
                    : prefix + "." + key, depth + 1, out));
            return;
        }
        out.put(prefix.isEmpty()
                ? "(value)"
                : prefix, value);
    }

    // Each field of the records: how often it is there, its commonest type, and up to three examples.
    private static List<Map<String, Object>> inventory(final List<? extends Map<String, ?>> records) {
        final Map<String, Map<String, Integer>> types = new LinkedHashMap<>();
        final Map<String, Integer> present = new LinkedHashMap<>();
        final Map<String, List<String>> examples = new LinkedHashMap<>();
        for (final Map<String, ?> record : records) {
            for (final Map.Entry<String, ?> field : record.entrySet()) {
                final String name = field.getKey();
                present.merge(name, 1, Integer::sum);
                types.computeIfAbsent(name, k -> new LinkedHashMap<>()).merge(valueType(field.getValue()), 1,
                        Integer::sum);
                final List<String> kept = examples.computeIfAbsent(name, k -> new ArrayList<>());
                final String text = field.getValue() instanceof final String string
                        ? string
                        : PyJson.dumps(field.getValue());
                if (!kept.contains(text) && kept.size() < 3) {
                    kept.add(PyText.head(text, 120));
                }
            }
        }
        final int total = Math.max(1, records.size());
        final List<Map<String, Object>> fields = new ArrayList<>();
        for (final Map.Entry<String, Integer> entry : present.entrySet()) {
            fields.add(map("field", entry.getKey(),
                    "fill_rate", PyText.round(100.0 * entry.getValue() / total),
                    "type", mostCommon(types.get(entry.getKey())),
                    "examples", examples.get(entry.getKey())));
        }
        return fields;
    }

    private static List<Map<String, Object>> flattenAll(final List<?> values) {
        final List<Map<String, Object>> records = new ArrayList<>();
        values.forEach(value -> records.add(flatten(value)));
        return records;
    }

    // --------------------------------------------------------------------------------
    // Several files.
    // --------------------------------------------------------------------------------

    /// Several sample files of one source, profiled each and together: the fields' fill rates per file, fields and
    /// timestamp shapes only some files have (what breaks a mapping built from one file), and whether the files
    /// even share a format.
    ///
    /// @param samples    Each file's text, by name.
    /// @param maxRecords The most records profiled a file.
    /// @return `files` (each one's format and counts), `format` (or `mixed`), `records`, `fields`, `differences`,
    /// the first file's setup (parser, text converter...) and a `hint`.
    public static Map<String, Object> profileMany(final Map<String, String> samples, final int maxRecords) {
        final Map<String, Map<String, Object>> profiles = new LinkedHashMap<>();
        samples.forEach((name, text) -> profiles.put(name, profile(text, maxRecords)));
        final Set<Object> formats = new LinkedHashSet<>();
        profiles.values().forEach(p -> formats.add(p.get("format")));
        final Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        profiles.forEach((name, p) -> {
            for (final Map<String, Object> field : fieldsOf(p)) {
                final String fieldName = (String) field.get("field");
                final Map<String, Object> entry = merged.computeIfAbsent(fieldName, k -> map("field", k,
                        "files", new LinkedHashMap<String, Object>(), "types", new LinkedHashMap<String, Integer>(),
                        "examples", new ArrayList<String>()));
                files(entry).put(name, field.get("fill_rate"));
                typeCounts(entry).merge((String) field.get("type"), 1, Integer::sum);
                final List<String> kept = exampleList(entry);
                final List<String> added = new ArrayList<>();
                for (final Object example : (List<?>) field.get("examples")) {
                    if (!kept.contains(example)) {
                        added.add((String) example);
                    }
                }
                kept.addAll(added.subList(0, Math.max(0, Math.min(added.size(), 3 - kept.size()))));
            }
        });
        final List<Map<String, Object>> fields = new ArrayList<>();
        final List<String> differences = new ArrayList<>();
        for (final Map<String, Object> entry : merged.values()) {
            final List<String> missing = new ArrayList<>();
            profiles.keySet().forEach(n -> {
                if (!files(entry).containsKey(n)) {
                    missing.add(n);
                }
            });
            final List<String> types = new ArrayList<>(typeCounts(entry).keySet());
            final Map<String, Object> field = map("field", entry.get("field"), "fill_rate_by_file", files(entry),
                    "type", types.getFirst(), "examples", exampleList(entry));
            final List<String> onlyIn = new ArrayList<>(new TreeSet<>(files(entry).keySet()));
            onlyIn.sort(PyText::compare);
            if (!missing.isEmpty()) {
                field.put("only_in", onlyIn);
            }
            if (types.size() > 1) {
                field.put("types_by_file", types);
            }
            fields.add(field);
            if (!missing.isEmpty()) {
                differences.add("field '" + entry.get("field") + "' is only in " + PyText.repr(onlyIn));
            }
            if (types.size() > 1 && types.stream().anyMatch(t -> t.startsWith("timestamp"))) {
                differences.add("field '" + entry.get("field") + "' has different timestamp shapes across files: "
                                + PyText.repr(types));
            }
        }
        if (formats.size() > 1) {
            final Map<String, Object> byFile = new LinkedHashMap<>();
            profiles.forEach((n, p) -> byFile.put(n, p.get("format")));
            differences.addFirst("the files are not one format: " + PyText.repr(byFile));
        }
        final Map<String, Object> first = profiles.values().iterator().next();
        final Map<String, Object> result = new LinkedHashMap<>();
        final Map<String, Object> files = new LinkedHashMap<>();
        final List<String> kept = List.of("format", "records", "lines", "note", "delimiter", "has_header",
                "record_element", "namespace", "event_logging", "malformed_lines", "malformed_note");
        profiles.forEach((n, p) -> {
            final Map<String, Object> summary = new LinkedHashMap<>();
            p.forEach((k, v) -> {
                if (kept.contains(k)) {
                    summary.put(k, v);
                }
            });
            files.put(n, summary);
        });
        result.put("files", files);
        result.put("format", formats.size() == 1
                ? first.get("format")
                : "mixed");
        long records = 0;
        for (final Map<String, Object> p : profiles.values()) {
            records += ((Number) p.getOrDefault("records", 0)).longValue();
        }
        result.put("records", records);
        result.put("fields", fields);
        result.put("differences", differences);
        for (final String key : List.of("suggested_parser", "text_converter", "parser_properties", "xslt_input",
                "parser")) {
            if (first.containsKey(key)) {
                result.put(key, first.get(key));
            }
        }
        result.put("hint", differences.isEmpty()
                ? "The files agree; upload each as its own stream and step them all."
                : "Map every field a rule needs with any_of where files name it differently, and give rules for the "
                  + "kinds of event each file shows; upload each file as its own stream and step them all.");
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fieldsOf(final Map<String, Object> profile) {
        final Object fields = profile.get("fields");
        return fields instanceof List<?>
                ? (List<Map<String, Object>>) fields
                : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> files(final Map<String, Object> entry) {
        return (Map<String, Object>) entry.get("files");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> typeCounts(final Map<String, Object> entry) {
        return (Map<String, Integer>) entry.get("types");
    }

    @SuppressWarnings("unchecked")
    private static List<String> exampleList(final Map<String, Object> entry) {
        return (List<String>) entry.get("examples");
    }

    // --------------------------------------------------------------------------------
    // JSON.
    // --------------------------------------------------------------------------------

    // The complete items at the start of a JSON array whose text was cut short (a stream read up to a limit), or
    // null when what comes before the cut isn't a JSON array. Seen: a 755 KB one-line array read to 20,000
    // characters profiled as key=value (its records' body field), and create_pipeline refused the JSON parser it
    // needed.
    private static List<Object> arrayHead(final String text, final int limit) {
        final List<Object> items = new ArrayList<>();
        int at = text.indexOf('[') + 1;
        while (items.size() < limit) {
            while (at < text.length() && " \t\r\n,".indexOf(text.charAt(at)) >= 0) {
                at++;
            }
            if (at >= text.length() || text.charAt(at) == ']') {
                break;
            }
            try {
                final PyJson.Decoded decoded = PyJson.rawDecode(text, at);
                items.add(decoded.value());
                at = decoded.end();
            } catch (final PyJson.JsonDecodeException e) {
                // The cut, or not JSON at all: what was read so far decides.
                break;
            }
        }
        return items.isEmpty()
                ? null
                : items;
    }

    /// Text that starts as JSON and doesn't parse, with where and why. Seen: a FortiOS file's text retyped by the
    /// agent with `tz=\"\+1000\"` (`\+` is no JSON escape), profiled as key=value from its records' body field,
    /// with a key=value template proposed for a JSON file.
    private static SampleRefusal notJson(final String text) {
        final String first = text.stripLeading().startsWith("[")
                ? text
                : PyText.splitlines(text).getFirst();
        try {
            PyJson.loads(first);
            // Not reached: called only when it fails.
            return new SampleRefusal("not JSON");
        } catch (final PyJson.JsonDecodeException e) {
            final String near = first.substring(Math.max(0, e.pos() - 40), Math.min(first.length(), e.pos() + 40));
            return new SampleRefusal("This starts as JSON but isn't valid JSON: " + e.msg() + " at character "
                                     + e.pos() + ", near " + PyText.repr(near) + ". Pass the source's text exactly as "
                                     + "it is, not retyped (an escape such as \\+ isn't JSON). A file on the user's "
                                     + "disk isn't passed through you: upload_sample files= sends it whole, and the "
                                     + "server reads it from the stream.");
        }
    }

    /// (key, records) for one JSON object whose array holds the records, as AWS CloudTrail writes them
    /// (`{"Records": [...]}`) and many APIs do ("data", "events", "items"...): the key whose array of objects is the
    /// longest. Seen: such a file refused as "starts as JSON but isn't valid JSON", read a line at a time.
    ///
    /// @param text A text.
    /// @return The key and the records (of a text cut off, the items whole before the cut), or null if it isn't
    /// such an object.
    public static Map.Entry<String, List<Object>> wrappedRecords(final String text) {
        if (!text.startsWith("{")) {
            return null;
        }
        final Object value;
        try {
            value = PyJson.loads(text);
        } catch (final PyJson.JsonDecodeException e) {
            return wrappedHead(text);
        }
        if (!(value instanceof final Map<?, ?> object)) {
            return null;
        }
        Map.Entry<String, List<Object>> best = null;
        for (final Map.Entry<?, ?> entry : object.entrySet()) {
            if (entry.getValue() instanceof final List<?> list && !list.isEmpty()
                && list.stream().allMatch(Map.class::isInstance)) {
                if (best == null || list.size() > best.getValue().size()) {
                    best = Map.entry((String) entry.getKey(), new ArrayList<>(list));
                }
            }
        }
        return best;
    }

    // Cut off (a stream's head): the array key among the object's first keys, and the items whole before the cut.
    private static Map.Entry<String, List<Object>> wrappedHead(final String text) {
        final Matcher m = PyText.matchAt(WRAPPER_HEAD, text);
        if (m == null) {
            return null;
        }
        final List<Object> items = new ArrayList<>();
        int at = m.end();
        while (true) {
            while (at < text.length() && " \t\r\n,".indexOf(text.charAt(at)) >= 0) {
                at++;
            }
            if (at >= text.length() || text.charAt(at) != '{') {
                break;
            }
            try {
                final PyJson.Decoded decoded = PyJson.rawDecode(text, at);
                items.add(decoded.value());
                at = decoded.end();
            } catch (final PyJson.JsonDecodeException e) {
                break;
            }
        }
        return items.isEmpty()
                ? null
                : Map.entry(m.group(1), items);
    }

    /// How a wrapped JSON object reaches the XSLT: the JSONParser's record is the whole object (addRootObject on,
    /// as for JSON lines), and for_each makes an Event of each item of its array.
    ///
    /// @param key The key whose array holds the records.
    /// @return `text_converter`, `parser_properties` and `xslt_input`.
    public static Map<String, Object> jsonSetupWrapped(final String key) {
        final Map<String, Object> setup = jsonSetup("lines");
        setup.put("text_converter", "none: the JSONParser element parses the object itself; a text converter cannot");
        @SuppressWarnings("unchecked") final Map<String, Object> input = (Map<String, Object>) setup.get("xslt_input");
        input.put("mapping", map("input", "json", "json_layout", "lines", "for_each", key));
        return setup;
    }

    // --------------------------------------------------------------------------------
    // Delimited text.
    // --------------------------------------------------------------------------------

    // A value of a type a column name never is: a time, a number, an IP address.
    private static boolean typed(final String value) {
        return PyText.fullmatch(NUMBER, value) || IpAddresses.isIpAddress(value)
               || TimePatterns.inferTimePattern(value) != null;
    }

    /// A first line of names (user, src_ip, eventTime), each unique, over columns whose values aren't names like
    /// it: a header the sniffer missed because every column is text (seen: user,name,department,site over people's
    /// names, the header read as a record and the columns called col1, col2 ...).
    ///
    /// @param rows Delimited rows.
    /// @return True if the first row names the columns.
    static boolean namesAHeader(final List<List<String>> rows) {
        final List<String> first = rows.getFirst();
        final List<List<String>> body = new ArrayList<>();
        rows.subList(1, rows.size()).forEach(row -> {
            if (!row.isEmpty()) {
                body.add(row);
            }
        });
        if (new LinkedHashSet<>(first).size() != first.size()
            || !first.stream().allMatch(cell -> PyText.match(NAME_CELL, PyText.strip(cell)))) {
            return false;
        }
        // A column whose every value is typed (a time, a number, an IP address) under a first cell that is a plain
        // name: a header, whatever the other columns hold (seen: time,user,host,action over ISO times and words,
        // the header read as a record, as only one column of four differed).
        for (int n = 0; n < first.size(); n++) {
            final List<String> values = new ArrayList<>();
            for (final List<String> row : body) {
                if (n < row.size() && !PyText.isBlank(row.get(n))) {
                    values.add(PyText.strip(row.get(n)));
                }
            }
            if (values.size() >= 2 && values.stream().allMatch(SampleProfiler::typed)
                && !typed(PyText.strip(first.get(n)))) {
                return true;
            }
        }
        int differing = 0;
        for (int n = 0; n < first.size(); n++) {
            final String name = first.get(n);
            final List<String> values = new ArrayList<>();
            for (final List<String> row : body) {
                if (n < row.size()) {
                    values.add(PyText.strip(row.get(n)));
                }
            }
            if (!values.isEmpty() && !values.contains(name)
                && !values.stream().allMatch(v -> PyText.match(NAME_CELL, v)
                                                  && PyText.isLower(v) == PyText.isLower(name))) {
                differing++;
            }
        }
        return differing >= Math.max(1, first.size() / 2);
    }

    /// (start, records) for text whose records run over several lines: most records' first lines start with a
    /// time, and lines between them (a stack trace's 'at ...', a wrapped message) don't. Text whose every line
    /// starts one is single-line, and left to the other formats.
    ///
    /// @param lines The text's lines.
    /// @return The regex a record's first line starts with, and the records, or null.
    public static Map.Entry<String, List<String>> multilineRecords(final List<String> lines) {
        for (final String start : RECORD_STARTS) {
            final Pattern pattern = PyText.re(start);
            final long begins = lines.stream().filter(line -> PyText.match(pattern, line)).count();
            if (begins >= 2 && begins < lines.size() && PyText.match(pattern, lines.getFirst())
                && begins >= 0.2 * lines.size()) {
                return Map.entry(start, SplitterDryRun.groupLines(lines, start));
            }
        }
        return null;
    }

    /// Delimited text with new lines inside quoted values (a comment over two lines): read as CSV over the whole
    /// text, not a line at a time, which made such a file unknown text.
    ///
    /// @param text       The text.
    /// @param maxRecords The most records read for its fields.
    /// @return Its profile's delimited part, with `_records` (the records read), or null unless some quoted value
    /// holds a new line.
    static Map<String, Object> quotedCsv(final String text, final int maxRecords) {
        if (!text.contains("\"") || !text.contains("\n")) {
            return null;
        }
        for (final char delimiter : List.of(',', '\t', '|', ';')) {
            List<List<String>> rows;
            try {
                rows = PyCsv.read(text, PyCsv.Dialect.delimitedBy(delimiter));
            } catch (final PyCsv.CsvException e) {
                continue;
            }
            rows = rows.stream().filter(row -> row.stream().anyMatch(cell -> !PyText.isBlank(cell))).toList();
            if (rows.size() < 3 || rows.stream().noneMatch(row -> row.stream().anyMatch(c -> c.contains("\n")))) {
                continue;
            }
            final Map<Integer, Integer> widths = new LinkedHashMap<>();
            rows.forEach(row -> widths.merge(row.size(), 1, Integer::sum));
            final int width = mostCommon(widths);
            if (width < 2 || rows.stream().filter(row -> row.size() == width).count() < 0.9 * rows.size()) {
                continue;
            }
            final List<String> joined = new ArrayList<>();
            rows.subList(0, Math.min(20, rows.size())).forEach(row -> joined.add(String.join(",", row)));
            final boolean hasHeader = namesAHeader(rows) || PyCsv.hasHeader(String.join("\n", joined));
            final List<String> header = hasHeader
                    ? rows.getFirst()
                    : columnNames(width);
            final List<List<String>> body = hasHeader
                    ? rows.subList(1, rows.size())
                    : rows;
            final List<Map<String, Object>> records = new ArrayList<>();
            body.subList(0, Math.min(maxRecords, body.size())).forEach(row -> records.add(zip(header, row)));
            return map("format", DELIMITED, "delimiter", String.valueOf(delimiter), "has_header", hasHeader,
                    "columns", header, "quoted_newlines", true, "records", body.size(), "_records", records,
                    "suggested_parser", "Data Splitter for delimited data whose quoted values hold new lines "
                                        + "(quoted_newlines: a record ends at a new line outside quotes)");
        }
        return null;
    }

    private static List<String> columnNames(final int width) {
        final List<String> names = new ArrayList<>();
        for (int i = 1; i <= width; i++) {
            names.add("col" + i);
        }
        return names;
    }

    /// @param names  Column names.
    /// @param values A row's values.
    /// @return The row by column, as Python's `dict(zip(names, values))`: as many as the shorter has, a name given
    /// twice keeping its last value.
    static Map<String, Object> zip(final List<String> names, final List<String> values) {
        final Map<String, Object> record = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(names.size(), values.size()); i++) {
            record.put(names.get(i), values.get(i));
        }
        return record;
    }

    /// A separator the CSV sniffer doesn't try: longer than one character (' ~ ', '||', ' :: ') or an unusual one
    /// ('~', '^'), found the same number of times (two or more) on every line. Seen unknown text: '... ~ alice ~
    /// LOGIN ~ OK ~ ...'.
    ///
    /// @param lines The text's lines.
    /// @return The separator, or null.
    public static String customDelimiter(final List<String> lines) {
        if (lines.size() < 2) {
            return null;
        }
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final String token : new LinkedHashSet<>(PyText.findall(SEPARATOR, lines.getFirst()))) {
            final String stripped = PyText.strip(token);
            if (List.of(",", ";", "|", "").contains(stripped) && token.equals(stripped)) {
                // The sniffer's own, unpadded.
                continue;
            }
            final List<Integer> found = new ArrayList<>();
            lines.forEach(line -> found.add(PyText.count(line, token)));
            if (found.stream().mapToInt(Integer::intValue).min().orElse(0) >= 2
                && new LinkedHashSet<>(found).size() == 1) {
                counts.put(token, found.getFirst());
            }
        }
        String best = null;
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (best == null || PyText.len(entry.getKey()) > PyText.len(best)
                || (PyText.len(entry.getKey()) == PyText.len(best) && entry.getValue() > counts.get(best))) {
                best = entry.getKey();
            }
        }
        return best;
    }

    /// @param lines The text's lines.
    /// @return The separator (',', tab, '|', ';') that splits at least four in five lines into the same number (two
    /// or more) of columns, or null.
    public static String steadyDelimiter(final List<String> lines) {
        for (final String delimiter : List.of(",", "\t", "|", ";")) {
            final Map<Integer, Integer> widths = new LinkedHashMap<>();
            lines.forEach(line -> widths.merge(SplitterDryRun.splitQuoted(line, delimiter, "\"").size(), 1,
                    Integer::sum));
            final int width = mostCommon(widths);
            if (width >= 2 && widths.get(width) >= 0.8 * lines.size()) {
                return delimiter;
            }
        }
        return null;
    }

    /// @param lines     The text's lines.
    /// @param delimiter The delimiter.
    /// @param width     How many columns the rows have.
    /// @param hasHeader True if the first line is a header.
    /// @return The delimited lines that aren't like the rest (a quote opened and not closed, or another number of
    /// columns): each `line`, `reason` and `text`.
    public static List<Map<String, Object>> offRows(final List<String> lines, final String delimiter,
                                                    final int width, final boolean hasHeader) {
        final List<Map<String, Object>> out = new ArrayList<>();
        final int first = hasHeader
                ? 1
                : 0;
        for (int i = first; i < lines.size(); i++) {
            final String line = lines.get(i);
            final int number = i + 1;
            if (PyText.count(line, "\"") % 2 == 1) {
                out.add(map("line", number, "reason", "a quote opened and not closed", "text",
                        PyText.head(line, 160)));
                continue;
            }
            final int cells = SplitterDryRun.splitQuoted(line, delimiter, "\"").size();
            if (cells != width) {
                out.add(map("line", number, "reason", cells + " columns where the rest have " + width, "text",
                        PyText.head(line, 160)));
            }
        }
        return out;
    }

    // The lines that aren't like the rest, said rather than left to be found in processing.
    private static Map<String, Object> malformed(final List<Map<String, Object>> bad, final String whatStroomDoes) {
        if (bad.isEmpty()) {
            return map();
        }
        return map("malformed_lines", bad.subList(0, Math.min(20, bad.size())),
                "malformed_note", bad.size() + " line(s) aren't like the rest (first: line "
                                  + bad.getFirst().get("line") + ", " + bad.getFirst().get("reason") + "). "
                                  + whatStroomDoes + ". Show them to the user: fixed at the source, left out on "
                                  + "purpose (drop_when), or mapped as they are.");
    }

    /// A log whose columns are named by a directive line rather than a first line: W3C extended logs (IIS, Blue
    /// Coat: `#Software`, `#Fields: date time ...`), Zeek (`#separator`, `#fields ts uid ...`) and the like. The
    /// data lines' delimiter and quoting, and the first directive whose value splits into as many names as they
    /// have columns.
    ///
    /// @param lines The text's lines.
    /// @return Its profile's delimited part, with `_records` (the records read), or null.
    static Map<String, Object> directiveLog(final List<String> lines) {
        final List<String> data = lines.stream().filter(line -> !line.startsWith("#")).toList();
        if (data.size() == lines.size() || data.isEmpty()) {
            return null;
        }
        final String quote = data.subList(0, Math.min(50, data.size())).stream().anyMatch(l -> l.contains("\""))
                ? "\""
                : null;
        final List<String> read = lines.subList(0, Math.min(2000, lines.size()));
        final List<Matcher> parsed = new ArrayList<>();
        read.forEach(line -> parsed.add(PyText.matchAt(DIRECTIVE, line)));
        final long dataRead = read.stream().filter(line -> !line.startsWith("#")).count();
        final Set<String> prefixes = new LinkedHashSet<>();
        parsed.forEach(m -> {
            if (m != null) {
                prefixes.add(m.group(1));
            }
        });
        for (final String delimiter : List.of("\t", " ", ",", "|", ";")) {
            for (final String prefix : prefixes) {
                // Each data line read under the latest of these directives above it (a log restarted mid-file names
                // its columns again): it names the columns if most data lines then have as many as it names.
                List<String> heading = null;
                int fits = 0;
                int under = 0;
                for (int i = 0; i < read.size(); i++) {
                    final Matcher m = parsed.get(i);
                    if (m != null && prefix.equals(m.group(1))) {
                        final List<String> names = namesOf(m, delimiter, quote);
                        heading = names.size() >= 3 && names.stream().allMatch(n -> PyText.match(COLUMN_NAME, n))
                                ? names
                                : null;
                    } else if (!read.get(i).startsWith("#") && heading != null) {
                        under++;
                        if (SplitterDryRun.splitQuoted(read.get(i), delimiter, quote).size() == heading.size()) {
                            fits++;
                        }
                    }
                }
                if (under == 0 || fits < 0.8 * dataRead) {
                    continue;
                }
                final Set<String> directives = new LinkedHashSet<>();
                parsed.forEach(m -> {
                    if (m != null && !prefix.equals(m.group(1))) {
                        directives.add(m.group(1));
                    }
                });
                final SplitterSpec spec = SplitterSpec.builder("delimited").delimiter(delimiter).header(true)
                        .quote(quote).commentPrefix("#").headerDirective(prefix)
                        .directives(new ArrayList<>(directives)).build();
                final List<String> first = parsed.stream()
                        .filter(m -> m != null && prefix.equals(m.group(1)))
                        .findFirst()
                        .map(m -> namesOf(m, delimiter, quote))
                        .orElse(List.of());
                return map("format", DELIMITED, "delimiter", delimiter, "has_header", true, "quote", quote,
                        "comment_prefix", "#", "header_directive", prefix, "directives",
                        new ArrayList<>(directives), "columns", first, "records", data.size(),
                        "_records", SplitterDryRun.dryRun(spec, String.join("\n", lines)).records());
            }
        }
        return null;
    }

    private static List<String> namesOf(final Matcher m, final String delimiter, final String quote) {
        final List<String> names = new ArrayList<>();
        final String value = m.group(2) == null
                ? ""
                : m.group(2);
        SplitterDryRun.splitQuoted(PyText.strip(value), delimiter, quote).forEach(n -> names.add(PyText.strip(n)));
        return names;
    }

    // --------------------------------------------------------------------------------
    // The profile.
    // --------------------------------------------------------------------------------

    /// Profiles a sample, reading at most [#MAX_RECORDS] records for its fields.
    ///
    /// @param sample The sample's text.
    /// @return Its profile.
    /// @throws SampleRefusal if it starts as JSON and isn't JSON.
    public static Map<String, Object> profile(final String sample) {
        return profile(sample, MAX_RECORDS);
    }

    /// Profiles a sample.
    ///
    /// @param sample     The sample's text.
    /// @param maxRecords The most records read for its fields.
    /// @return Its profile.
    /// @throws SampleRefusal if it starts as JSON and isn't JSON.
    public static Map<String, Object> profile(final String sample, final int maxRecords) {
        final String text = PyText.strip(sample, "\uFEFF\r\n ");
        final List<String> lines = PyText.nonBlankLines(text);
        final Map<String, Object> result = map("characters", PyText.len(sample), "lines", lines.size());
        if (text.startsWith("<") && !PyText.match(SYSLOG_5424, text) && !PyText.match(SYSLOG_3164, text)) {
            final Map<String, Object> xml = profileXml(text, result, maxRecords);
            if (xml != null) {
                return xml;
            }
        }
        final Map<String, Object> json = profileJson(text, lines, result, maxRecords);
        if (json != null) {
            return json;
        }
        final Map<String, Object> directed = directiveLog(lines);
        if (directed != null) {
            final Map<String, Object> found = new LinkedHashMap<>(result);
            @SuppressWarnings("unchecked") final List<Map<String, Object>> records =
                    (List<Map<String, Object>>) directed.remove("_records");
            found.putAll(directed);
            found.put("fields", inventory(records.subList(0, Math.min(maxRecords, records.size()))));
            found.put("suggested_parser", "Data Splitter for delimited data whose columns are named by a directive "
                                          + "line (" + directed.get("header_directive") + "): build_data_splitter "
                                          + "infers it (a translation template with a DSParser: "
                                          + "find_pipeline_templates)");
            return found;
        }
        final Map<String, Object> line = profileLines(text, lines, result, maxRecords);
        if (line != null) {
            return line;
        }
        return profileDelimited(text, lines, result, maxRecords);
    }

    private static Map<String, Object> with(final Map<String, Object> result, final Object... keysAndValues) {
        final Map<String, Object> found = new LinkedHashMap<>(result);
        found.putAll(map(keysAndValues));
        return found;
    }

    private static Map<String, Object> profileXml(final String text, final Map<String, Object> result,
                                                  final int maxRecords) {
        try {
            final XmlTree.Element root = XmlTree.parse(text);
            final List<XmlTree.Element> records = root.elements();
            final Map<String, Integer> children = localNames(records);
            final String recordTag = children.isEmpty()
                    ? root.localName
                    : mostCommon(children);
            return with(result, "format", XML, "root", root.localName, "namespace", root.namespace,
                    "record_element", recordTag, "records", records.size(),
                    "fields", inventory(xmlFieldsOf(records, maxRecords)),
                    "suggested_parser", XML_PARSER, "text_converter", XML_TEXT_CONVERTER);
        } catch (final XmlTree.XmlException e) {
            final List<XmlTree.Element> fragments = xmlFragments(text);
            final XmlTree.Pull cut = fragments != null
                    ? null
                    : cutDocument(text);
            if (cut != null) {
                final XmlTree.Element root = cut.root();
                return with(result, "format", XML, "root", root.localName, "namespace", root.namespace,
                        "record_element", mostCommon(localNames(cut.records())), "records", cut.records().size(),
                        "fields", inventory(xmlFieldsOf(cut.records(), maxRecords)),
                        "note", "one XML document, read only to here (no closing </" + root.localName + ">): "
                                + "these are its first " + cut.records().size() + " complete records",
                        "suggested_parser", XML_PARSER, "text_converter", XML_TEXT_CONVERTER);
            }
            if (fragments != null) {
                final String recordTag = mostCommon(localNames(fragments));
                final String namespace = fragments.getFirst().namespace;
                final boolean events = eventLoggingFragments(fragments);
                // The version is the default's here, where no settings are read: the tools write the configured
                // one.
                final Map<String, Object> wrapper = events
                        ? map("code", EVENTS_FRAGMENT_WRAPPER.replace("{version}", "3.5.2"))
                        : null;
                final Map<String, Object> found = with(result, "format", XML_FRAGMENTS, "record_element", recordTag,
                        "namespace", namespace);
                if (events) {
                    found.put("event_logging", true);
                }
                found.put("records", fragments.size());
                found.put("fields", inventory(xmlFieldsOf(fragments, maxRecords)));
                found.put("suggested_parser", "XMLFragmentParser with an XML_FRAGMENT text converter (the wrapper "
                                              + "below); no root element, so the XMLParser cannot read it");
                found.putAll(xmlFragmentSetup(namespace, recordTag, wrapper, List.of()));
                return found;
            }
            result.put("note", "Starts with < but is not well-formed XML, as a document or as fragments");
            return null;
        }
    }

    private static Map<String, Object> profileJson(final String text,
                                                   final List<String> lines,
                                                   final Map<String, Object> result,
                                                   final int maxRecords) {
        if (text.startsWith("[")) {
            final Map<String, Object> array = profileArray(text, result, maxRecords);
            if (array != null) {
                return array;
            }
        }
        final Map.Entry<String, List<Object>> wrapped = wrappedRecords(text);
        if (wrapped != null) {
            final String key = wrapped.getKey();
            final List<Object> items = wrapped.getValue();
            final Map<String, Object> found = with(result, "format", JSON_OBJECT, "records_key", key,
                    "records", items.size(),
                    "fields", inventory(flattenAll(items.subList(0, Math.min(maxRecords, items.size())))),
                    "suggested_parser", "JSONParser: one object whose '" + key + "' array holds the records (as AWS "
                                        + "CloudTrail writes them); the mapping makes an Event of each with "
                                        + "for_each='" + key + "' (a translation template with a JSONParser: "
                                        + "find_pipeline_templates)");
            found.putAll(jsonSetupWrapped(key));
            return found;
        }
        final List<Object> jsonLines = new ArrayList<>();
        List<Map<String, Object>> bad = new ArrayList<>();
        final int scan = Math.max(maxRecords, MALFORMED_SCAN);
        for (int n = 1; n <= Math.min(scan, lines.size()); n++) {
            final String line = lines.get(n - 1);
            final Object value;
            try {
                value = PyJson.loads(line);
            } catch (final PyJson.JsonDecodeException e) {
                if (jsonLines.isEmpty() && n == 1) {
                    // Not JSON lines at all (or not JSON): the other formats are tried.
                    break;
                }
                bad.add(map("line", n, "reason", "isn't JSON: " + e.getMessage().split(":", -1)[0], "text",
                        PyText.head(line, 160)));
                continue;
            }
            if (value instanceof Map) {
                jsonLines.add(value);
            } else {
                bad.add(map("line", n, "reason", "isn't a JSON object", "text", PyText.head(line, 160)));
            }
        }
        int read = Math.min(lines.size(), scan);
        if (bad.size() == 1 && (int) bad.getLast().get("line") == read && read == lines.size()
            && !jsonLines.isEmpty()) {
            // Only the last line, cut short by a read limit.
            bad = new ArrayList<>();
            read--;
        }
        if (jsonLines.size() >= 2 && jsonLines.size() >= 0.8 * (read - bad.size())
            && jsonLines.size() >= 0.8 * read) {
            final Map<String, Object> found = with(result, "format", JSON_LINES, "records", lines.size(),
                    "fields", inventory(flattenAll(jsonLines.subList(0, Math.min(maxRecords, jsonLines.size())))),
                    "suggested_parser", "JSONParser, one object per line (a translation template with one: "
                                        + "find_pipeline_templates)");
            found.putAll(jsonSetup("lines"));
            if (!bad.isEmpty()) {
                // Seen on the local Stroom: a line cut short stopped the JSONParser with a fatal error, and every
                // record after it in the stream was lost.
                found.putAll(jsonSetup("lines_with_bad"));
                found.put("malformed_lines", bad.subList(0, Math.min(20, bad.size())));
                found.put("note", bad.size() + " of the " + read + " lines read aren't JSON objects (first: line "
                                  + bad.getFirst().get("line") + ", " + bad.getFirst().get("reason") + "). Stroom's "
                                  + "JSONParser stops the stream at the first such line, a fatal error, and every "
                                  + "record after it is lost. Read the lines with a Data Splitter instead, one record "
                                  + "a line (" + JSON_LINES_SPEC + "), and map their keys through json_fields: "
                                  + "['json'] (a key is then json.<key>): a line that isn't JSON gives no fields, "
                                  + "matches no rule and is logged, and the rest are read.");
            }
            return found;
        }
        if (jsonLines.isEmpty() && PyText.match(JSON_START, text)) {
            throw notJson(text);
        }
        return null;
    }

    private static Map<String, Object> profileArray(final String text, final Map<String, Object> result,
                                                    final int maxRecords) {
        try {
            final List<?> data = (List<?>) PyJson.loads(text);
            final List<Map<String, Object>> records = new ArrayList<>();
            for (final Object item : data.subList(0, Math.min(maxRecords, data.size()))) {
                if (item instanceof Map) {
                    records.add(flatten(item));
                }
            }
            final Map<String, Object> found = with(result, "format", JSON_ARRAY, "records", data.size(),
                    "fields", inventory(records), "suggested_parser", JSON_PARSER);
            found.putAll(jsonSetup("array"));
            return found;
        } catch (final PyJson.JsonDecodeException e) {
            final List<Object> head = arrayHead(text, maxRecords);
            if (head != null && head.stream().allMatch(Map.class::isInstance)) {
                final Map<String, Object> found = with(result, "format", JSON_ARRAY, "records", head.size(),
                        "fields", inventory(flattenAll(head)),
                        "note", "the array is cut short here: these are its first records",
                        "suggested_parser", JSON_PARSER);
                found.putAll(jsonSetup("array"));
                return found;
            }
            return null;
        }
    }

    // CEF, syslog, CRI, records of several lines and key=value: formats read a line (or a few) a record.
    private static Map<String, Object> profileLines(final String text,
                                                    final List<String> lines,
                                                    final Map<String, Object> result,
                                                    final int maxRecords) {
        final List<String> examples = lines.subList(0, Math.min(3, lines.size()));
        if (count(lines, line -> PyText.search(CEF, line)) >= 0.8 * lines.size()) {
            // CEF, alone or after a syslog header: seen read as key=value, its header fields taken for keys and its
            // values cut at their first space.
            return with(result, "format", CEF_FORMAT, "records", lines.size(), "examples", examples,
                    "suggested_parser", "Data Splitter for CEF: the header's fields, then the extension's key=value "
                                        + "pairs, whose values may hold spaces (build_data_splitter infers it; a "
                                        + "translation template with a DSParser)");
        }
        if (count(lines, line -> PyText.match(SYSLOG_5424, line)) >= 0.8 * lines.size()) {
            return with(result, "format", SYSLOG_5424_FORMAT, "records", lines.size(), "examples", examples,
                    "suggested_parser", "Data Splitter with a regex per RFC 5424 part (a translation template with a "
                                        + "DSParser: find_pipeline_templates)");
        }
        if (count(lines, line -> PyText.match(SYSLOG_3164, line)) >= 0.8 * lines.size()) {
            return with(result, "format", SYSLOG_3164_FORMAT, "records", lines.size(), "examples", examples,
                    "suggested_parser", "Data Splitter with a regex for PRI, timestamp, host, tag and message");
        }
        if (!lines.isEmpty() && count(lines, line -> PyText.match(CRI_LINE, line)) >= 0.9 * lines.size()) {
            final List<String> records = SplitterDryRun.groupLinesToEnd(lines, SplitterDryRun.CRI_END);
            return with(result, "format", CRI, "record_end", SplitterDryRun.CRI_END, "records", records.size(),
                    "examples", records.subList(0, Math.min(3, records.size())),
                    "suggested_parser", "Data Splitter with record_end: Kubernetes CRI container log lines, a message "
                                        + "split into partial (P) lines ended by its full (F) line "
                                        + "(build_data_splitter infers it); the mapping joins the parts with xpath "
                                        + SplitterDryRun.CRI_JOIN);
        }
        final Map.Entry<String, List<String>> multi = multilineRecords(lines);
        if (multi != null) {
            final List<String> records = multi.getValue();
            return with(result, "format", MULTI_LINE, "record_start", multi.getKey(), "records", records.size(),
                    "examples", records.subList(0, Math.min(3, records.size())),
                    "suggested_parser", "Data Splitter with record_start: a record starts at a line beginning with "
                                        + "its time, and the lines after it (a stack trace, a wrapped message) are "
                                        + "part of it (build_data_splitter infers the start; give the record's "
                                        + "pattern, starting (?s))");
        }
        final List<Map<String, Object>> pairs = new ArrayList<>();
        lines.subList(0, Math.min(maxRecords, lines.size())).forEach(line -> pairs.add(keyValues(line)));
        if (!pairs.isEmpty() && pairs.stream().filter(r -> r.size() >= 3).count() >= 0.8 * pairs.size()) {
            final Map<String, Object> found = with(result, "format", KEY_VALUE_FORMAT, "records", lines.size(),
                    "fields", inventory(pairs),
                    "suggested_parser", "Data Splitter splitting on spaces then = (a translation template with a "
                                        + "DSParser: find_pipeline_templates)");
            final List<Map<String, Object>> bad = new ArrayList<>();
            final List<String> scanned = lines.subList(0, Math.min(MALFORMED_SCAN, lines.size()));
            for (int n = 0; n < scanned.size(); n++) {
                if (!PyText.search(KEY_VALUE, scanned.get(n))) {
                    bad.add(map("line", n + 1, "reason", "holds no key=value pairs", "text",
                            PyText.head(scanned.get(n), 160)));
                }
            }
            found.putAll(malformed(bad, "Stroom's Data Splitter writes no record for such a line and reports it as "
                                        + "content its expressions failed to match"));
            return found;
        }
        return null;
    }

    /// @param line A line.
    /// @return Its key=value pairs (quotes taken off the values), as the profile reads them.
    public static Map<String, Object> keyValues(final String line) {
        final Map<String, Object> pairs = new LinkedHashMap<>();
        for (final String[] pair : PyText.findallGroups(KEY_VALUE, line)) {
            pairs.put(pair[0], PyText.strip(pair[1], "\""));
        }
        return pairs;
    }

    private static long count(final List<String> lines, final Predicate<String> test) {
        return lines.stream().filter(test).count();
    }

    // Delimited text, quoted, custom or sniffed; else unknown text.
    private static Map<String, Object> profileDelimited(final String text,
                                                        final List<String> lines,
                                                        final Map<String, Object> result,
                                                        final int maxRecords) {
        final Map<String, Object> quoted = quotedCsv(text, maxRecords);
        if (quoted != null) {
            final Map<String, Object> found = new LinkedHashMap<>(result);
            @SuppressWarnings("unchecked") final List<Map<String, Object>> records =
                    (List<Map<String, Object>>) quoted.remove("_records");
            found.putAll(quoted);
            found.put("fields", inventory(records));
            return found;
        }
        final List<String> head = lines.subList(0, Math.min(maxRecords, lines.size()));
        final String custom = customDelimiter(head);
        if (custom != null) {
            return profileCustom(lines, head, custom, result);
        }
        String delimiter;
        try {
            delimiter = String.valueOf(PyCsv.sniff(String.join("\n", lines.subList(0, Math.min(20, lines.size()))),
                    ",\t|;").delimiter());
        } catch (final PyCsv.CsvException e) {
            // The sniffer gives up on a few bad rows (an unbalanced quote, a row short of a column): the separator
            // that splits most lines into the same number of columns.
            delimiter = head.isEmpty()
                    ? null
                    : steadyDelimiter(head);
        }
        if (delimiter == null) {
            return with(result, "format", UNKNOWN_TEXT, "examples", lines.subList(0, Math.min(5, lines.size())),
                    "suggested_parser", "Data Splitter with regexes; ask the user for the record structure");
        }
        boolean hasHeader;
        try {
            hasHeader = PyCsv.hasHeader(String.join("\n", lines.subList(0, Math.min(20, lines.size()))));
        } catch (final PyCsv.CsvException e) {
            hasHeader = false;
        }
        final List<List<String>> rows = PyCsv.read(String.join("\n", lines.subList(0,
                Math.min(maxRecords + 1, lines.size()))), PyCsv.Dialect.delimitedBy(delimiter.charAt(0)));
        if (!hasHeader && rows.size() > 1) {
            hasHeader = namesAHeader(rows);
        }
        final List<String> header = hasHeader
                ? rows.getFirst()
                : columnNames(rows.getFirst().size());
        final List<List<String>> body = hasHeader
                ? rows.subList(1, rows.size())
                : rows;
        final List<Map<String, Object>> records = new ArrayList<>();
        body.forEach(row -> records.add(zip(header, row)));
        final Map<String, Object> found = with(result, "format", DELIMITED, "delimiter", delimiter,
                "has_header", hasHeader, "columns", header, "records", lines.size() - (hasHeader
                        ? 1
                        : 0),
                "fields", inventory(records),
                "suggested_parser", "Data Splitter for delimited data" + (hasHeader
                        ? " with a header row"
                        : "") + " (a translation template with a DSParser: find_pipeline_templates)");
        found.putAll(malformed(offRows(lines.subList(0, Math.min(MALFORMED_SCAN, lines.size())), delimiter,
                header.size(), hasHeader), "Stroom's Data Splitter still writes a record for such a line, its "
                                           + "fields as the line splits: a missing column is a missing field, an "
                                           + "unclosed quote runs to the line's end"));
        return found;
    }

    private static Map<String, Object> profileCustom(final List<String> lines,
                                                     final List<String> head,
                                                     final String custom,
                                                     final Map<String, Object> result) {
        final List<List<String>> rows = new ArrayList<>();
        head.forEach(line -> rows.add(PyText.split(line, custom)));
        final List<String> firstStripped = new ArrayList<>();
        rows.getFirst().forEach(cell -> firstStripped.add(PyText.strip(cell)));
        final List<String> header = namesAHeader(rows)
                ? firstStripped
                : columnNames(rows.getFirst().size());
        final boolean hasHeader = header.equals(firstStripped);
        final List<List<String>> body = hasHeader
                ? rows.subList(1, rows.size())
                : rows;
        final List<Map<String, Object>> records = new ArrayList<>();
        body.forEach(row -> records.add(zip(header, row)));
        return with(result, "format", DELIMITED, "delimiter", custom, "has_header", hasHeader, "columns", header,
                "records", lines.size() - (rows.size() - body.size()), "fields", inventory(records),
                "suggested_parser", "Data Splitter for delimited data, columns separated by " + PyText.repr(custom));
    }
}
