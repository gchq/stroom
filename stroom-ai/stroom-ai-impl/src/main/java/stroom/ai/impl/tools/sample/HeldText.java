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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Structured data held in a string field, as a source type is read from it: its format detected per record, in
/// this order, the first that applies:
///
/// 1. **JSON**: an object (the text starts with `{`), or JSON encoded twice (a JSON string, starting with `"`, whose
///    text is an object);
/// 2. **XML**: a well-formed document (the text starts with `<`);
/// 3. **syslog**: an RFC 5424 or RFC 3164 header, read for its `host`, `app` (the app name, or the program before
///    `[pid]:`) and `message` (the rest);
/// 4. **key=value** (`kv`): two or more `key=` at the text's start or after white space, `,` or `;`;
/// 5. **text**: anything else, never read for a value (a plain text's words would be false source types).
///
/// A JSON or XML text that doesn't parse is read on down the list, so a cut-off JSON line with pairs in it is read
/// as key=value, one without as text. The text is stripped of XML's white space (and a byte order mark) first. The
/// rules are written once, here, and the generated XSLT (`SourceTypeReadingXslt`) runs the same rules, with the same
/// regular expressions, in XPath: every regular expression here is one both dialects read alike (no `\s`, `\d`,
/// `\w`, lookaround or non-capturing groups), so the tools and processing read a record's source type the same.
public final class HeldText {

    /// A held object of JSON.
    public static final String JSON = "json";
    /// A held XML document.
    public static final String XML = "xml";
    /// A held syslog line.
    public static final String SYSLOG = "syslog";
    /// Held key=value pairs.
    public static final String KEY_VALUE = "kv";
    /// Held plain text: never read for a value.
    public static final String TEXT = "text";
    /// The structured formats, in the order they are tried.
    public static final List<String> STRUCTURED = List.of(JSON, XML, SYSLOG, KEY_VALUE);
    /// The syslog header's fields a reading may name.
    public static final List<String> SYSLOG_FIELDS = List.of("host", "app", "message");
    /// XML's white space (and a byte order mark, at the start) around a held text, as it is stripped before its
    /// format is told: XPath's dialect too.
    public static final String AROUND = "^[\uFEFF \\t\\n\\r]+|[ \\t\\n\\r]+$";
    /// XML's white space around a value read, as it is stripped: XPath's dialect too.
    public static final String SPACE_AROUND = "^[ \\t\\n\\r]+|[ \\t\\n\\r]+$";
    /// Where a key of key=value pairs starts: the text's start, or after white space, `,` or `;`; then the key and
    /// its `=`. XPath's dialect too.
    public static final String PAIR = "(^|[ \\t\\n\\r,;])[A-Za-z_][A-Za-z0-9_.\\-]*=";
    /// What a key of key=value pairs is.
    public static final String KEY = "[A-Za-z_][A-Za-z0-9_.\\-]*";
    /// The fewest pairs a key=value text holds.
    public static final int MIN_PAIRS = 2;
    /// An RFC 5424 header: priority and version, time, then `host` (group 1), `app` (group 2), process id and
    /// message id; the rest (structured data and the message) is `message` (group 4). XPath's dialect too.
    public static final String SYSLOG_5424 = "^<[0-9]{1,3}>1 [^ \\t\\n\\r]+ ([^ \\t\\n\\r]+) ([^ \\t\\n\\r]+) "
                                             + "[^ \\t\\n\\r]+ [^ \\t\\n\\r]+( (.*))?$";
    /// An RFC 3164 header: an optional priority, the time, `host` (group 2), then an optional `app` (group 4) with
    /// an optional `[pid]` and a `:`; the rest is `message` (group 6). XPath's dialect too.
    public static final String SYSLOG_3164 = "^(<[0-9]{1,3}>)?[A-Z][a-z]{2} [ 0-9][0-9] [0-9]{2}:[0-9]{2}:[0-9]{2} "
                                             + "([^ \\t\\n\\r]+) (([^ \\t\\n\\r:\\[]+)(\\[[^\\]]*\\])?: ?)?(.*)$";
    /// Each syslog field's group in [#SYSLOG_5424] and [#SYSLOG_3164].
    public static final Map<String, List<Integer>> SYSLOG_GROUPS = Map.of("host", List.of(1, 2), "app",
            List.of(2, 4), "message", List.of(4, 6));
    /// The values a field holds for no value, as the tools and a source type group read them (any case), the empty
    /// value aside.
    public static final List<String> NIL_VALUES = List.of("-", "null", "none", "n/a");

    // The deepest a discovery reads names into a held JSON object or XML document.
    private static final int MAX_NAMES = 4;
    // The most names a discovery takes from one held value.
    private static final int MAX_FOUND = 200;
    private static final Pattern AROUND_PATTERN = Pattern.compile(AROUND);
    private static final Pattern SPACE_AROUND_PATTERN = Pattern.compile(SPACE_AROUND);
    private static final Pattern PAIR_PATTERN = Pattern.compile(PAIR);
    private static final Pattern KEY_PATTERN = Pattern.compile(KEY);
    private static final Pattern SYSLOG_5424_PATTERN = Pattern.compile(SYSLOG_5424, Pattern.DOTALL);
    private static final Pattern SYSLOG_3164_PATTERN = Pattern.compile(SYSLOG_3164, Pattern.DOTALL);
    // Each key's value pattern, compiled once: a survey reads a reading over every record.
    private static final Map<String, Pattern> KEY_PATTERNS = new ConcurrentHashMap<>();
    private static final int MAX_KEY_PATTERNS = 1000;
    // The characters a name a discovery offers can't hold: the reading's own separators and marks.
    private static final Pattern UNNAMEABLE = Pattern.compile("[.>|:@#]|^\\s|\\s$|^$");

    private HeldText() {
        // Static only.
    }

    /// Reads a held text's format and content.
    ///
    /// @param text A field's value, or null.
    /// @return Its format and content; [#TEXT] for null, empty or unstructured text.
    public static Parsed parse(final String text) {
        final String stripped = text == null
                ? ""
                : AROUND_PATTERN.matcher(text).replaceAll("");
        if (stripped.isEmpty()) {
            return new Parsed(TEXT, stripped, null);
        }
        final Map<String, Object> json = json(stripped);
        if (json != null) {
            return new Parsed(JSON, stripped, json);
        }
        if (stripped.startsWith("<")) {
            try {
                return new Parsed(XML, stripped, XmlTree.parse(stripped));
            } catch (final XmlTree.XmlException | IllegalArgumentException e) {
                // Not XML: read on.
            }
        }
        if (SYSLOG_5424_PATTERN.matcher(stripped).find() || SYSLOG_3164_PATTERN.matcher(stripped).find()) {
            return new Parsed(SYSLOG, stripped, null);
        }
        return pairs(stripped) >= MIN_PAIRS
                ? new Parsed(KEY_VALUE, stripped, null)
                : new Parsed(TEXT, stripped, null);
    }

    /// @param text A held text, stripped.
    /// @return How many key=value pairs start in it, as [#PAIR] finds them, one after another.
    public static int pairs(final String text) {
        final Matcher found = PAIR_PATTERN.matcher(text);
        int count = 0;
        while (found.find()) {
            count++;
        }
        return count;
    }

    /// @param key A key of key=value pairs.
    /// @return The regular expression reading its value from a key=value text (XPath's dialect too, with the `s`
    /// flag): the first `key=` at the start or after white space, `,` or `;`; the value quoted (group 3, without its
    /// quotes) or up to white space, `,` or `;` (group 4); null when the key can't be one ([#KEY]).
    public static String keyRegex(final String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            return null;
        }
        // The empty branch first, then the shortest run before a separator: the first `key=` (Saxon's `??` doesn't
        // match where Java's does, so it isn't used).
        return "^(|.*?[ \\t\\n\\r,;])" + SourceTypeRows.escape(key) + "=(\"([^\"]*)\"|([^ \\t\\n\\r,;]*)).*$";
    }

    /// @param value A value read, or null.
    /// @return It stripped of XML's white space; null for none, an empty value or a nil value ([#NIL_VALUES]).
    public static String normalised(final String value) {
        if (value == null) {
            return null;
        }
        final String text = SPACE_AROUND_PATTERN.matcher(value).replaceAll("");
        return text.isEmpty() || NIL_VALUES.contains(text.toLowerCase(Locale.ROOT))
                ? null
                : text;
    }

    /// @param value A parsed value.
    /// @return It as text when it is a string, number or boolean; null otherwise (an object, a list, none).
    public static String scalar(final Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
               || value instanceof BigInteger
                ? String.valueOf(value)
                : null;
    }

    /// Reads one step into a held text.
    ///
    /// @param parsed The held text, parsed.
    /// @param step   The step.
    /// @return The value it reads, or null when the format isn't the step's, or the step finds nothing.
    public static String read(final Parsed parsed, final SourceTypeReading.Step step) {
        if (TEXT.equals(parsed.format()) && parsed.text().isEmpty()) {
            return null;
        }
        if (step.format() != null && !step.format().equals(parsed.format())) {
            return null;
        }
        if (SourceTypeReading.FORMAT_STEP.equals(step.special())) {
            return parsed.format();
        }
        return switch (parsed.format()) {
            case JSON -> step.special() != null || step.attribute() != null
                    ? null
                    : jsonAt(parsed.json(), step.names());
            case XML -> SourceTypeReading.ROOT_STEP.equals(step.special())
                    ? parsed.xml().localName()
                    : step.special() != null
                            ? null
                            : xmlAt(List.of(parsed.xml()), step.localNames(), step.attribute());
            case SYSLOG -> step.special() != null || step.attribute() != null
                    ? null
                    : syslog(parsed.text(), step.raw());
            case KEY_VALUE -> step.special() != null || step.attribute() != null
                    ? null
                    : keyValue(parsed.text(), step.raw());
            default -> null;
        };
    }

    /// The paths a discovery offers inside a record's held fields: for each string field holding structured data,
    /// the names in it (a JSON object's keys, dotted; an XML document's elements and attributes below its root, and
    /// `#root`; the keys of key=value pairs; a syslog header's fields), each read as [SourceTypeReading] reads it,
    /// and inside those holding structured data again, at most three held fields deep. Names are format-free
    /// (`event>logger_name` whether the held value is JSON or key=value), so the same name in several formats is one
    /// path.
    ///
    /// @param record A record, parsed (flattened to dotted names).
    /// @return Each path's value (normalised; values that are none left out), and each held field's format.
    public static Expansion expand(final Map<String, Object> record) {
        final Map<String, String> values = new LinkedHashMap<>();
        final Map<String, String> formats = new LinkedHashMap<>();
        if (record != null) {
            record.forEach((name, value) -> {
                if (value instanceof final String text && !SourceTypeMeta.isMeta(name) && !name.contains(">")
                    && !name.contains("|")) {
                    expandHeld(name, text, 1, values, formats);
                }
            });
        }
        return new Expansion(values, formats);
    }

    // Reads a held field's names, and those of the held fields inside it.
    private static void expandHeld(final String path,
                                   final String text,
                                   final int depth,
                                   final Map<String, String> values,
                                   final Map<String, String> formats) {
        final Parsed parsed = parse(text);
        formats.put(path, parsed.format());
        if (TEXT.equals(parsed.format())) {
            return;
        }
        final Set<String> names = new LinkedHashSet<>();
        switch (parsed.format()) {
            case JSON -> jsonNames(parsed.json(), "", 1, names);
            case XML -> xmlNames(parsed.xml(), names);
            case SYSLOG -> names.addAll(SYSLOG_FIELDS);
            default -> {
                final Matcher found = PAIR_PATTERN.matcher(parsed.text());
                while (found.find() && names.size() < MAX_FOUND) {
                    final String key = found.group().substring(found.group(1).length(), found.group().length() - 1);
                    names.add(key);
                }
            }
        }
        for (final String name : names) {
            final String held = path + SourceTypeReading.HELD + name;
            final SourceTypeReading.Step step = SourceTypeReading.Step.of(name);
            final String raw = read(parsed, step);
            if (raw == null) {
                continue;
            }
            if (!"message".equals(name) || !SYSLOG.equals(parsed.format())) {
                final String value = normalised(raw);
                if (value != null) {
                    values.put(held, value);
                }
            }
            if (depth < SourceTypeReading.MAX_HELD && !SourceTypeReading.ROOT_STEP.equals(name)) {
                final Parsed inner = parse(raw);
                if (!TEXT.equals(inner.format())) {
                    expandHeld(held, raw, depth + 1, values, formats);
                }
            }
        }
    }

    private static void jsonNames(final Map<String, Object> object,
                                  final String prefix,
                                  final int depth,
                                  final Set<String> names) {
        for (final Map.Entry<String, Object> entry : object.entrySet()) {
            if (names.size() >= MAX_FOUND || UNNAMEABLE.matcher(entry.getKey()).find()) {
                continue;
            }
            final String name = prefix + entry.getKey();
            if (entry.getValue() instanceof final Map<?, ?> inner && depth < MAX_NAMES) {
                @SuppressWarnings("unchecked") final Map<String, Object> nested = (Map<String, Object>) inner;
                jsonNames(nested, name + ".", depth + 1, names);
            } else if (scalar(entry.getValue()) != null) {
                names.add(name);
            }
        }
    }

    private static void xmlNames(final XmlTree.Element root, final Set<String> names) {
        names.add(SourceTypeReading.ROOT_STEP);
        xmlNames(root, "", 0, names);
    }

    private static void xmlNames(final XmlTree.Element element,
                                 final String prefix,
                                 final int depth,
                                 final Set<String> names) {
        for (final String attribute : element.attrib().keySet()) {
            final String local = localOf(attribute);
            if (names.size() < MAX_FOUND && !UNNAMEABLE.matcher(local).find()) {
                names.add(prefix + "@" + local);
            }
        }
        if (depth >= MAX_NAMES) {
            return;
        }
        for (final XmlTree.Element child : element.elements()) {
            if (names.size() >= MAX_FOUND || UNNAMEABLE.matcher(child.localName()).find()) {
                continue;
            }
            final String name = prefix + child.localName();
            if (child.elements().isEmpty()) {
                names.add(name);
            }
            xmlNames(child, name + ".", depth + 1, names);
        }
    }

    // An attribute's local name: `{namespace}name` or `name`.
    private static String localOf(final String attribute) {
        final int brace = attribute.indexOf('}');
        return brace >= 0
                ? attribute.substring(brace + 1)
                : attribute;
    }

    // A JSON object held in a text: the text an object, or a JSON string whose text is one.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(final String text) {
        try {
            if (text.startsWith("{")) {
                return ToolJson.loads(text) instanceof final Map<?, ?> map
                        ? (Map<String, Object>) map
                        : null;
            }
            if (text.startsWith("\"") && ToolJson.loads(text) instanceof final String inner) {
                final String decoded = AROUND_PATTERN.matcher(inner).replaceAll("");
                return decoded.startsWith("{") && ToolJson.loads(decoded) instanceof final Map<?, ?> map
                        ? (Map<String, Object>) map
                        : null;
            }
        } catch (final ToolJson.JsonDecodeException e) {
            return null;
        }
        return null;
    }

    // A JSON object's value at the keys given, each a key of the object the one before it reaches; a scalar only.
    @SuppressWarnings("unchecked")
    private static String jsonAt(final Map<String, Object> object, final List<String> names) {
        Object at = object;
        for (final String name : names) {
            if (!(at instanceof final Map<?, ?> map)) {
                return null;
            }
            at = ((Map<String, Object>) map).get(name);
        }
        return at == object
                ? null
                : scalar(at);
    }

    // An XML document's value at the elements given (by local name, from the root's children down), then an
    // attribute (by local name): of every element the steps reach, in document order, the first's, as XPath's
    // string((...)[1]) reads it.
    private static String xmlAt(final List<XmlTree.Element> from, final List<String> names, final String attribute) {
        List<XmlTree.Element> at = from;
        for (final String name : names) {
            final List<XmlTree.Element> next = new ArrayList<>();
            at.forEach(element -> element.elements().stream()
                    .filter(child -> child.localName().equals(name))
                    .forEach(next::add));
            at = next;
        }
        if (attribute != null) {
            for (final XmlTree.Element element : at) {
                for (final Map.Entry<String, String> entry : element.attrib().entrySet()) {
                    if (localOf(entry.getKey()).equals(attribute)) {
                        return entry.getValue();
                    }
                }
            }
            return "";
        }
        return at.isEmpty()
                ? ""
                : String.join("", at.getFirst().itertext());
    }

    // A syslog header's field.
    private static String syslog(final String text, final String field) {
        final List<Integer> groups = SYSLOG_GROUPS.get(field);
        if (groups == null) {
            return null;
        }
        final Matcher m5424 = SYSLOG_5424_PATTERN.matcher(text);
        if (m5424.find()) {
            return orEmpty(m5424.group(groups.get(0)));
        }
        final Matcher m3164 = SYSLOG_3164_PATTERN.matcher(text);
        return m3164.find()
                ? orEmpty(m3164.group(groups.get(1)))
                : null;
    }

    // A key's value of key=value pairs: quoted, or up to white space, `,` or `;`.
    private static String keyValue(final String text, final String key) {
        final String regex = keyRegex(key);
        if (regex == null) {
            return null;
        }
        if (KEY_PATTERNS.size() > MAX_KEY_PATTERNS) {
            KEY_PATTERNS.clear();
        }
        final Matcher found = KEY_PATTERNS.computeIfAbsent(regex, each -> Pattern.compile(each, Pattern.DOTALL))
                .matcher(text);
        return found.find()
                ? orEmpty(found.group(3)) + orEmpty(found.group(4))
                : null;
    }

    private static String orEmpty(final String text) {
        return text == null
                ? ""
                : text;
    }


    // --------------------------------------------------------------------------------


    /// A held text, its format told.
    ///
    /// @param format  [#JSON], [#XML], [#SYSLOG], [#KEY_VALUE] or [#TEXT].
    /// @param text    The text, stripped.
    /// @param content A JSON object's map, or an XML document's root element; null for the others.
    public record Parsed(String format, String text, Object content) {

        /// @return A JSON object's map.
        @SuppressWarnings("unchecked")
        public Map<String, Object> json() {
            return (Map<String, Object>) content;
        }

        /// @return An XML document's root element.
        public XmlTree.Element xml() {
            return (XmlTree.Element) content;
        }
    }

    /// The paths a discovery found in a record's held fields.
    ///
    /// @param values  Each path's value (`event>logger_name`), normalised.
    /// @param formats Each held field's format (`event` json, `event>payload` kv).
    public record Expansion(Map<String, String> values, Map<String, String> formats) {

    }
}
