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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.ToolRefusal;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What a translation XSLT does: for each output element or attribute, the input field, expression or constant
/// that fills it (grouped by the template it is written in), the input fields it reads, and the XSLT libraries,
/// dictionaries and reference lookups it uses. An indexing XSLT is described by its fields instead.
public final class TranslationDescriptions {

    private static final String FN = XsltSafety.FN;
    private static final Pattern INPUT_FIELD = Pattern.compile(
            "(?:data|string|number|boolean|map|array)\\[@(?:name|key)\\s*=\\s*['\"]([^'\"]+)['\"]\\]");
    private static final Pattern DICTIONARY = Pattern.compile("stroom:dictionary\\(\\s*'([^']+)'");
    private static final Pattern LOOKUP = Pattern.compile("stroom:(?:lookup|bitmap-lookup)\\(\\s*'([^']+)'");
    private static final Pattern VARIABLE = Pattern.compile("\\$([\\w.-]+)", Pattern.UNICODE_CHARACTER_CLASS);

    private TranslationDescriptions() {
        // Static only.
    }

    /// Describes what a translation XSLT does.
    ///
    /// @param xslt The XSLT's text.
    /// @param name Its document's name, or null for text given.
    /// @return `xslt` (the name), and for an indexing XSLT `kind` (`indexing`), `field_count` and what
    /// [#describe(Element)] gives; for a translation `mapping_counts` (by kind) and what [#describe(Element)] gives.
    /// @throws ToolRefusal if it is not well-formed.
    public static Map<String, Object> describeTranslation(final String xslt, final String name) {
        final Map<String, Object> described = describe(XsltChecks.parse(xslt, "XSLT"));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("xslt", name);
        if (described.containsKey("index_fields")) {
            result.put("kind", "indexing");
            result.put("field_count", ((List<?>) described.get("index_fields")).size());
        } else {
            final Map<String, Integer> kinds = new LinkedHashMap<>();
            for (final Object mapping : (List<?>) described.get("mappings")) {
                kinds.merge((String) ((Map<?, ?>) mapping).get("kind"), 1, Integer::sum);
            }
            result.put("mapping_counts", kinds);
        }
        result.putAll(described);
        return result;
    }

    /// @param root An XSLT's root element.
    /// @return For an indexing XSLT, `index_fields`, `imports` and `lookups`; for any other, `mappings` (each
    /// `output`, `source` and `kind`: value, attribute or constant), `input_fields`, `imports`, `dictionaries` and
    /// `lookups`.
    public static Map<String, Object> describe(final Element root) {
        final List<Map<String, String>> fields = indexFields(root);
        final String sourceText = XmlTrees.serialise(root);
        final Map<String, Object> result = new LinkedHashMap<>();
        if (fields != null) {
            // An indexing XSLT: its fields by name, not the JSON elements that write them.
            result.put("index_fields", fields);
            result.put("imports", imports(root));
            result.put("lookups", sortedMatches(LOOKUP, sourceText));
            return result;
        }
        final List<Map<String, String>> mappings = new ArrayList<>();
        for (final Element node : XmlTrees.descendantsAndSelf(root)) {
            if (XmlTrees.isXsl(node, "value-of") || XmlTrees.isXsl(node, "sequence")) {
                mappings.add(mapping(outputPath(parentElement(node)), node.getAttribute("select"), "value"));
            } else if (XmlTrees.isXsl(node, "attribute")) {
                final String select = node.getAttribute("select");
                mappings.add(mapping(outputPath(parentElement(node)) + "/@" + XmlTrees.attribute(node, "name"),
                        select.isEmpty()
                                ? XmlTrees.allText(node).strip()
                                : select,
                        "attribute"));
            } else if (!XmlTrees.isXsl(node)) {
                for (final Attr attr : XmlTrees.attributes(node)) {
                    if (attr.getValue().contains("{") && attr.getNamespaceURI() == null) {
                        mappings.add(mapping(outputPath(node) + "/@" + attr.getName(), attr.getValue(),
                                "attribute"));
                    }
                }
                if (!XmlTrees.text(node).isEmpty() && XmlTrees.isLeaf(node)) {
                    mappings.add(mapping(outputPath(node), XmlTrees.text(node), "constant"));
                }
            }
        }
        result.put("mappings", mappings);
        result.put("input_fields", sortedMatches(INPUT_FIELD, sourceText));
        result.put("imports", imports(root));
        result.put("dictionaries", sortedMatches(DICTIONARY, sourceText));
        result.put("lookups", sortedMatches(LOOKUP, sourceText));
        return result;
    }

    /// An indexing XSLT's fields, each its name and source: an Elasticsearch document's dotted key path (each
    /// map's key, then the value's own), or a Lucene record's data name. A variable is read as what it holds. A
    /// field a template writes for whatever element it is applied to (an object several actions share) is named
    /// from that template's own keys, with the template's mode: its source is relative to that element. Seen in VS
    /// Code: an indexing XSLT described as '[Event] map/map/string' outputs, 36 of them, which say nothing of the
    /// fields; the agent read them out of a file the client spilled them to, and its model fell into a loop.
    ///
    /// @param root An XSLT's root element.
    /// @return The fields (`field`, `source` and, for JSON, `type`), or null when the XSLT writes neither.
    public static List<Map<String, String>> indexFields(final Element root) {
        final List<Map<String, String>> fields = new ArrayList<>();
        for (final Element template : XmlTrees.named(root, XmlTrees.XSL, "template")) {
            final Map<String, String> held = variables(template);
            final String match = XmlTrees.attribute(template, "match");
            final String where = (match == null || "Event".equals(match)) && !template.hasAttribute("name")
                    ? ""
                    : "[" + pythonOr(XmlTrees.attribute(template, "mode"), XmlTrees.attribute(template, "name"))
                      + "] ";
            for (final Element element : XmlTrees.descendantsAndSelf(template)) {
                final String namespace = element.getNamespaceURI();
                final String local = element.getLocalName();
                final String key = element.getAttribute("key");
                if (FN.equals(namespace) && !key.isEmpty() && !"map".equals(local) && !"array".equals(local)) {
                    final List<String> keys = new ArrayList<>();
                    for (final Element ancestor : XmlTrees.ancestors(element)) {
                        if (FN.equals(ancestor.getNamespaceURI()) && !ancestor.getAttribute("key").isEmpty()) {
                            keys.addFirst(ancestor.getAttribute("key"));
                        }
                    }
                    keys.add(key);
                    final String value = XmlTrees.descendantsAndSelf(element).stream()
                            .filter(v -> XmlTrees.isXsl(v, "value-of") || XmlTrees.isXsl(v, "sequence"))
                            .findFirst()
                            .map(v -> XmlTrees.attribute(v, "select"))
                            .orElse(null);
                    final Map<String, String> field = new LinkedHashMap<>();
                    field.put("field", where + String.join(".", keys));
                    field.put("source", resolved(value == null || value.isEmpty()
                            ? XmlTrees.text(element)
                            : value, held));
                    field.put("type", local);
                    fields.add(field);
                } else if ("data".equals(local) && HeldSchemas.RECORDS_NS.equals(namespace)
                           && !element.getAttribute("name").isEmpty()) {
                    final String value = element.getAttribute("value");
                    final Map<String, String> field = new LinkedHashMap<>();
                    field.put("field", where + element.getAttribute("name"));
                    field.put("source", resolved(value.startsWith("{")
                            ? value.substring(1, Math.max(1, value.length() - 1))
                            : value, held));
                    fields.add(field);
                } else if (XmlTrees.isXsl(element, "call-template") && where.isEmpty()) {
                    final Map<String, String> field = new LinkedHashMap<>();
                    field.put("field", "(written by the shared template " + element.getAttribute("name") + ")");
                    field.put("source", "");
                    fields.add(field);
                }
            }
        }
        return fields.stream().anyMatch(field -> !field.get("source").isEmpty())
                ? fields
                : null;
    }

    // Where an element is written: the template's match or name, then the literal elements down to it.
    private static String outputPath(final Element node) {
        final List<String> parts = new ArrayList<>();
        Node current = node;
        while (current instanceof final Element element && !XmlTrees.isXsl(element, "template")) {
            if (!XmlTrees.isXsl(element)) {
                parts.addFirst(element.getLocalName());
            }
            current = element.getParentNode();
        }
        final String template = current instanceof final Element element
                ? matchOrName(element)
                : null;
        final String path = String.join("/", parts);
        if (template != null && !template.isEmpty() && !path.isEmpty()) {
            return "[" + template + "] " + path;
        }
        return path.isEmpty()
                ? "[" + (template == null
                ? "None"
                : template) + "]"
                : path;
    }

    // A template's match if it is not empty, else its name (null if it has neither).
    private static String matchOrName(final Element template) {
        final String match = XmlTrees.attribute(template, "match");
        return match != null && !match.isEmpty()
                ? match
                : XmlTrees.attribute(template, "name");
    }

    private static Element parentElement(final Element node) {
        return node.getParentNode() instanceof final Element parent
                ? parent
                : node;
    }

    private static Map<String, String> mapping(final String output, final String source, final String kind) {
        final Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("output", output);
        mapping.put("source", source);
        mapping.put("kind", kind);
        return mapping;
    }

    private static List<String> imports(final Element root) {
        return XmlTrees.named(root, XmlTrees.XSL, "*").stream()
                .filter(element -> "import".equals(element.getLocalName())
                                   || "include".equals(element.getLocalName()))
                .map(element -> XmlTrees.attribute(element, "href"))
                .toList();
    }

    private static List<String> sortedMatches(final Pattern pattern, final String text) {
        final Set<String> found = new TreeSet<>();
        final Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return List.copyOf(found);
    }

    // A template's variables, by name, with the expression each holds.
    private static Map<String, String> variables(final Element template) {
        final Map<String, String> held = new LinkedHashMap<>();
        for (final Element variable : XmlTrees.named(template, XmlTrees.XSL, "variable")) {
            final String name = variable.getAttribute("name");
            final String select = variable.getAttribute("select");
            if (!name.isEmpty() && !select.isEmpty()) {
                held.put(name, select);
            }
        }
        return held;
    }

    // The text with its variables read as what they hold; a variable may read another.
    private static String resolved(final String text, final Map<String, String> held) {
        String current = text == null
                ? ""
                : text;
        for (int i = 0; i < 3; i++) {
            final Matcher matcher = VARIABLE.matcher(current);
            final StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(held.getOrDefault(matcher.group(1),
                        matcher.group(0))));
            }
            matcher.appendTail(sb);
            current = sb.toString();
        }
        return current;
    }

    // The first if it is not empty, else the second, a missing second written `None`: the descriptions'
    // established wording, which the tests pin.
    private static String pythonOr(final String first, final String second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return second == null
                ? "None"
                : second;
    }
}
