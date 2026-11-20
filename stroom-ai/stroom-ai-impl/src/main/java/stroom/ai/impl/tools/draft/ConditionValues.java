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
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XPathRegex;
import stroom.ai.impl.tools.generation.XPathText;
import stroom.ai.impl.tools.sample.JsonFieldNames;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.sample.XmlTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The values a condition's field has in a record, exactly as the generated XSLT selects and compares them, and the
/// tests over them: the one evaluator of the conditions ([LocalCheck#holds]).
///
/// * A field's values are its nodes' string values, as XPath's general comparison reads them: every element a path
///   selects (XML's repeated elements), an element's text and its descendants' (unstripped), a JSON object's
///   members' text run together, as `*[@key='event']` gives it. A JSON array's values are its items, one by one
///   (the generated XPath's [XPathText#ITEMS_STEP]); an item that is an object or array is one value, its text run
///   together.
/// * A value counts only when it has text and isn't one of the mapping's nil values (the XSLT's
///   `[normalize-space(.)]` and nil predicates); a `not` holds when no value that counts passes the test, so when
///   the field is missing.
/// * JSON and XML held in a field (json_fields, xml_fields) are read as the XSLT's guarded helpers read them: JSON
///   encoded twice read again, a value that doesn't parse giving nothing, a character XML can't hold made U+FFFD as
///   `json-to-xml()` makes it; XML by local name.
/// * XML records' elements are those of the mapping's namespace (xpath-default-namespace), attributes those of
///   none.
/// * A record's NUL is no value's: Stroom's XML writer drops it. A condition's text loses the characters XML 1.0
///   can't hold, as its XPath literal does ([XPathText#literal(String)]).
/// * A field the mapping reads with an unescape transform ([MappingFacts#unescapeOf]) is compared decoded, as the
///   generated XPath decodes it.
/// * Regular expressions run in XPath's own dialect, by Saxon's engine ([XPathRegex#finds(String, String)]).
final class ConditionValues {

    // The characters XML 1.0 can't hold, which json-to-xml() replaces with U+FFFD.
    private static final Pattern NOT_XML_10 = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]"
                                                             + "|[\\uD800-\\uDBFF](?![\\uDC00-\\uDFFF])"
                                                             + "|(?<![\\uD800-\\uDBFF])[\\uDC00-\\uDFFF]");
    // What json-to-xml() writes for a character XML can't hold.
    private static final String REPLACEMENT = "�";
    // The JSON held in a string that the XSLT's helper reads again (a JSON string whose text is an object or array).
    // A quote or backslash escaped with a backslash (the unescape_backslashes transform).
    private static final Pattern ESCAPED = Pattern.compile("\\\\([\"\\\\])");
    private static final Pattern JSON_TEXT = Pattern.compile("^[ \\t\\n\\r]*[\\[{]");

    private ConditionValues() {
        // Static only.
    }

    /// @param unescape The transform the condition's field is decoded with ([MappingFacts#unescapeOf]), or null.
    /// @param nilTexts The mapping's nil values, as XPath literals give them ([XPathText#literalText(String)]).
    /// @param c        A field condition (no xpath, no dictionary, not of the record's scope).
    /// @param values   The field's values ([#values(TranslationMapping, String, Object)]).
    /// @return Whether the condition holds over them; null when its regex can't be run (it doesn't compile as
    /// XPath's, or overflows the stack as it would in processing).
    static Boolean holds(final String unescape,
                         final List<String> nilTexts,
                         final Condition c,
                         final List<String> values) {
        final List<String> counted = values.stream()
                .filter(value -> counts(nilTexts, value))
                .map(value -> unescaped(unescape, value))
                .toList();
        if (c.present() != null) {
            return !counted.isEmpty() == c.present();
        }
        boolean any = false;
        for (final String value : counted) {
            final Boolean test = test(c, value);
            if (test == null) {
                return null;
            }
            if (test) {
                any = true;
                break;
            }
        }
        return c.not() != any;
    }

    /// @param mapping The mapping.
    /// @param field   A field, as the mapping names it.
    /// @param record  A record, as [LocalCheck#sampleRecords] reads them.
    /// @return The field's values in the record, every node's, in document order (none when it has none), as the XSLT
    /// selects them before its predicates.
    static List<String> values(final TranslationMapping mapping, final String field, final Object record) {
        final String xmlHeld = MappingFacts.heldXmlField(mapping.xmlFields(), field);
        if (xmlHeld != null) {
            final List<String> base = values(mapping, xmlHeld, record);
            return base.isEmpty()
                    ? List.of()
                    : heldXmlValues(base.getFirst(), field.substring(xmlHeld.length()));
        }
        final String jsonHeld = MappingFacts.heldJsonField(mapping.jsonFields(), field);
        if (jsonHeld != null) {
            final List<String> base = values(mapping, jsonHeld, record);
            return base.isEmpty()
                    ? List.of()
                    : heldJsonValues(base.getFirst(), JsonFieldNames.keys(JsonFieldNames.below(jsonHeld, field)));
        }
        if (record instanceof final Map<?, ?> map) {
            return mapValues(map, field, "json".equals(mapping.input()));
        }
        if (record instanceof final XmlTree.Element element) {
            return xmlValues(mapping, element, field);
        }
        return List.of();
    }

    /// @param text A text.
    /// @return It as XPath's normalize-space() gives it: XML's white space (space, tab, new line, CR) trimmed and
    /// each run made one space.
    static String normalizeSpace(final String text) {
        final StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                space = !out.isEmpty();
            } else {
                if (space) {
                    out.append(' ');
                    space = false;
                }
                out.append(c);
            }
        }
        return out.toString();
    }

    // A value as the field's unescape transform decodes it, as XPathText.transformExpression writes it.
    private static String unescaped(final String unescape, final String value) {
        if ("unescape_quotes".equals(unescape)) {
            return value.replace("\"\"", "\"");
        }
        if ("unescape_backslashes".equals(unescape)) {
            return ESCAPED.matcher(value).replaceAll("$1");
        }
        return value;
    }

    // Whether a value counts: it has text, and isn't a nil value.
    private static boolean counts(final List<String> nilTexts, final String value) {
        final String normal = normalizeSpace(value);
        return !normal.isEmpty() && !nilTexts.contains(normal);
    }

    // A test of one value (all but present), before any not; null when its regex can't be run.
    private static Boolean test(final Condition c, final String value) {
        if (c.equalTo() != null) {
            return value.equals(XPathText.literalText(c.equalTo()));
        }
        if (c.oneOf() != null) {
            return c.oneOf().stream().anyMatch(one -> value.equals(XPathText.literalText(one)));
        }
        if (c.startsWith() != null) {
            return value.startsWith(XPathText.literalText(c.startsWith()));
        }
        if (c.contains() != null) {
            return value.contains(XPathText.literalText(c.contains()));
        }
        try {
            return XPathRegex.finds(XPathText.literalText(c.matches()), value);
        } catch (final XPathRegex.XPathRegexException | StackOverflowError e) {
            return null;
        }
    }

    // A map record's values of a field: its value, or (a JSON object the record holds, its members flattened into
    // the record) its members' text run together; NUL dropped.
    private static List<String> mapValues(final Map<?, ?> map, final String field, final boolean json) {
        if (map.containsKey(field)) {
            return json
                    ? items(map.get(field)).stream().map(item -> withoutNul(stringValue(item))).toList()
                    : List.of(withoutNul(stringValue(map.get(field))));
        }
        if (!json) {
            return List.of();
        }
        final StringBuilder members = new StringBuilder();
        boolean found = false;
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            if (JsonFieldNames.below(field, String.valueOf(entry.getKey())) != null) {
                found = true;
                members.append(stringValue(entry.getValue()));
            }
        }
        return found
                ? List.of(withoutNul(members.toString()))
                : List.of();
    }

    // A JSON value's text as XPath's string() gives its element: a string as it is, a number as written, null
    // empty, an object's or array's members run together.
    private static String stringValue(final Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof final Map<?, ?> map) {
            final StringBuilder out = new StringBuilder();
            map.values().forEach(inner -> out.append(stringValue(inner)));
            return out.toString();
        }
        if (value instanceof final List<?> list) {
            final StringBuilder out = new StringBuilder();
            list.forEach(inner -> out.append(stringValue(inner)));
            return out.toString();
        }
        return value instanceof final String text
                ? text
                : JsonValues.jsonText(value);
    }

    private static String withoutNul(final String text) {
        return text.indexOf('\0') < 0
                ? text
                : text.replace("\0", "");
    }

    // An XML record's values of a path (steps by name in the mapping's namespace, `@name` attributes of none,
    // `name[@attr='value']` steps): every node's string value.
    private static List<String> xmlValues(final TranslationMapping mapping,
                                          final XmlTree.Element record,
                                          final String path) {
        final String namespace = MappingFacts.inputNamespace(mapping);
        List<XmlTree.Element> nodes = List.of(record);
        final List<String> steps = new ArrayList<>();
        for (final String step : path.split("/", -1)) {
            if (!step.isEmpty() && !".".equals(step)) {
                steps.add(step);
            }
        }
        for (int i = 0; i < steps.size(); i++) {
            final String step = steps.get(i);
            if (step.startsWith("@")) {
                if (i != steps.size() - 1) {
                    return List.of();
                }
                final String name = step.substring(1);
                return nodes.stream().map(node -> node.get(name)).filter(Objects::nonNull).toList();
            }
            final XmlStep parsed = XmlStep.of(step);
            if (parsed == null) {
                return List.of();
            }
            final List<XmlTree.Element> found = new ArrayList<>();
            for (final XmlTree.Element node : nodes) {
                for (final XmlTree.Element child : node.elements()) {
                    if (parsed.matches(child) && inNamespace(child, namespace, mapping.input())) {
                        found.add(child);
                    }
                }
            }
            nodes = found;
        }
        return nodes.stream().map(node -> String.join("", node.itertext())).toList();
    }

    // Whether an element is in the namespace an unprefixed step selects: the mapping's; for fragments, also none (the
    // wrapper they are read in declares it).
    private static boolean inNamespace(final XmlTree.Element element, final String namespace, final String input) {
        final String own = Objects.requireNonNullElse(element.namespace(), "");
        final String wanted = Objects.requireNonNullElse(namespace, "");
        return own.equals(wanted) || ("xml_fragments".equals(input) && own.isEmpty());
    }

    // JSON held in a field, read as mcp:json-to-xml reads it, then its keys: their values' text.
    private static List<String> heldJsonValues(final String text, final List<String> keys) {
        Object at = heldJson(text);
        if (at == null) {
            return List.of();
        }
        for (final String key : keys) {
            if (!(at instanceof final Map<?, ?> map) || !map.containsKey(key)) {
                return List.of();
            }
            at = map.get(key);
        }
        return items(at).stream().map(item -> NOT_XML_10.matcher(stringValue(item)).replaceAll(REPLACEMENT))
                .toList();
    }

    // A JSON value's values one by one, as the generated XPath's items step reads them: an array's items (each
    // object or array among them one item), any other value itself.
    private static List<?> items(final Object value) {
        return value instanceof final List<?> list
                ? list
                : Collections.singletonList(value);
    }

    // The JSON a field holds: nothing for an empty value, or one that doesn't parse; a JSON string whose text is an
    // object or array read again, as often as it is one.
    private static Object heldJson(final String text) {
        if (normalizeSpace(text).isEmpty()) {
            return null;
        }
        try {
            Object parsed = ToolJson.loads(text);
            while (parsed instanceof final String inner && JSON_TEXT.matcher(inner).find()) {
                parsed = ToolJson.loads(inner);
            }
            return parsed;
        } catch (final ToolJson.JsonDecodeException | StackOverflowError e) {
            return null;
        }
    }

    // XML held in a field, read as mcp:parse-xml reads it, then the path from its document (`/Envelope/Body/User`,
    // `//User`, `/Login/@id`), every step by local name: the nodes' values.
    private static List<String> heldXmlValues(final String text, final String path) {
        if (!normalizeSpace(text).startsWith("<")) {
            return List.of();
        }
        final XmlTree.Element root;
        try {
            root = XmlTree.parse(text);
        } catch (final XmlTree.XmlException e) {
            return List.of();
        }
        // The document's node: its one child the root.
        List<XmlTree.Element> nodes = null;
        String rest = path;
        while (!rest.isEmpty()) {
            final boolean anywhere = rest.startsWith("//");
            rest = rest.substring(anywhere
                    ? 2
                    : rest.startsWith("/")
                            ? 1
                            : 0);
            final int slash = rest.indexOf('/');
            final String step = slash < 0
                    ? rest
                    : rest.substring(0, slash);
            rest = slash < 0
                    ? ""
                    : rest.substring(slash);
            if (step.startsWith("@")) {
                final String name = step.substring(1);
                final List<XmlTree.Element> owners = anywhere
                        ? below(root, nodes, true)
                        : Objects.requireNonNullElse(nodes, List.of());
                return rest.isEmpty()
                        ? owners.stream().flatMap(node -> node.attrib().entrySet().stream()
                                .filter(attribute -> localName(attribute.getKey()).equals(name))
                                .map(Map.Entry::getValue)).toList()
                        : List.of();
            }
            nodes = below(root, nodes, anywhere).stream()
                    .filter(node -> "*".equals(step) || node.localName().equals(step))
                    .toList();
        }
        return nodes == null
                ? List.of()
                : nodes.stream().map(node -> String.join("", node.itertext())).toList();
    }

    // The elements a step looks at: the children of the nodes (the root, for the document), or with // every element
    // below them.
    private static List<XmlTree.Element> below(final XmlTree.Element root,
                                               final List<XmlTree.Element> nodes,
                                               final boolean anywhere) {
        if (nodes == null) {
            return anywhere
                    ? root.iter()
                    : List.of(root);
        }
        final List<XmlTree.Element> out = new ArrayList<>();
        for (final XmlTree.Element node : nodes) {
            if (anywhere) {
                node.iter().stream().filter(inner -> inner != node && !out.contains(inner)).forEach(out::add);
            } else {
                out.addAll(node.elements());
            }
        }
        return out;
    }

    private static String localName(final String clarkName) {
        return clarkName.startsWith("{")
                ? clarkName.substring(clarkName.indexOf('}') + 1)
                : clarkName;
    }


    // --------------------------------------------------------------------------------


    // One step of an XML record's path: a name (or *), and an attribute it must have, or none.
    private record XmlStep(String name, String attribute, String value) {

        private static final Pattern STEP = Pattern.compile("^(?<name>[\\w.-]+|\\*)(?:\\[@(?<attr>[\\w.-]+)="
                                                            + "(?<q>['\"])(?<value>.*?)\\k<q>])?$");

        private static XmlStep of(final String step) {
            final Matcher m = STEP.matcher(step);
            return m.matches()
                    ? new XmlStep(m.group("name"), m.group("attr"), m.group("value"))
                    : null;
        }

        private boolean matches(final XmlTree.Element element) {
            return ("*".equals(name) || element.localName().equals(name))
                   && (attribute == null || value.equals(element.get(attribute)));
        }
    }
}
