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

import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.validation.XmlTrees.NotWellFormedException;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// The checks of an XSLT that need no Stroom: ported from the pure helpers of the Stroom MCP server's
/// `tools/validation.py` that `check_xslt` is made of. Each says what a model gets wrong writing pipeline content
/// by hand: XML sent HTML-escaped, stroom: functions that don't exist, element names that select nothing for want
/// of the input's namespace, event-logging elements the schema has no place for, and json-to-xml() unguarded.
public final class XsltChecks {

    /// The event-logging namespace.
    public static final String EVT = HeldSchemas.EVENT_LOGGING_NS;
    /// The namespace of the JSONParser's output.
    public static final String JSON_NS = "http://www.w3.org/2013/XSL/json";
    /// The records namespace (a Data Splitter's output).
    public static final String RECORDS_NS = HeldSchemas.RECORDS_NS;
    /// The XPath functions' namespace (json-to-xml()'s output).
    public static final String FN_NS = XsltSafety.FN;
    /// What is said of XML that came HTML-escaped.
    public static final String ESCAPED = "It came HTML-escaped (&lt; for <) and was read unescaped: send XML as it "
                                         + "is, not escaped.";
    /// Stroom's XSLT extension functions (namespace 'stroom'), as its function library registers them. Anything
    /// else fails to compile when the pipeline runs, so an unknown name is refused with the nearest real one.
    public static final Set<String> STROOM_FUNCTIONS = Set.of(
            "add-meta", "ask-ai", "bitmap-lookup", "cidr-to-numeric-ip", "cidr-to-numeric-ip-range",
            "classification", "col-from", "col-to", "cosine-similarity", "current-time", "current-unixTime",
            "current-user", "dec-to-bin", "dec-to-hex", "dec-to-oct", "decode-url", "dictionary", "encode-url",
            "feed-attribute", "feed-name", "fetch-json", "format-date", "format-dateTime", "from-unixTime",
            "generate-url", "get", "hash", "hex-to-dec", "hex-to-oct", "hex-to-string", "host-address", "host-name",
            "http-call", "ip-in-cidr", "json-to-xml", "line-from", "line-to", "link", "log", "lookup", "manifest",
            "manifest-for-id", "meta", "meta-attribute", "meta-keys", "meta-stream", "meta-stream-for-id",
            "numeric-ip", "parent-for-id", "parent-id", "parse-dateTime", "parse-uri", "part-no", "pipeline-name",
            "plan-b-lookup", "pointIsInsideXYPolygon", "put", "random", "record-no", "search-id", "source",
            "source-id", "split-document", "stream-id", "to-unixTime");

    private static final Pattern STROOM_CALL = Pattern.compile("\\bstroom:([A-Za-z][A-Za-z0-9-]*)\\s*\\(");
    // Element names that, in a pipeline, only ever come from one parser or stream type, each in its own namespace.
    // Written bare in a match or select with no xpath-default-namespace they select nothing: the XSLT's output is
    // empty text, and processing writes nowhere.
    private static final Map<String, Set<String>> INPUT_NAMESPACES = inputNamespaces();
    private static final List<String> XPATH_ATTRS = List.of("match", "select", "test", "group-by", "use");
    private static final Pattern LITERAL = Pattern.compile("'[^']*'|\"[^\"]*\"");
    // A bare element-name step: not an attribute, variable, prefixed name, function, map/array constructor or axis.
    private static final Pattern NAME_STEP = Pattern.compile(
            "(?<![\\w.:@$-])([A-Za-z_][\\w.-]*)(?!\\s*[:({]|[\\w.-])", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ELEMENT_AXIS = Pattern.compile(
            "\\b(?:child|descendant|self|descendant-or-self|following-sibling|preceding-sibling|parent"
            + "|ancestor|ancestor-or-self|following|preceding)::");
    private static final Pattern JSON_CALL = Pattern.compile("(?<![\\w-])(?:\\w+:)?json-to-xml\\(",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern IF_GUARD = Pattern.compile("\\bif\\s*\\(");
    private static final Pattern NUMERIC_REFERENCE = Pattern.compile("&#(x[0-9A-Fa-f]+|[0-9]+);");

    private XsltChecks() {
        // Static only.
    }

    /// XML sent HTML-escaped, with no < anywhere (seen: Qwen in VS Code, answered only "Start tag expected"):
    /// read unescaped, and said so. Escaped text inside real XML is left as it is.
    ///
    /// @param text XML text as sent.
    /// @return The text to read, and [#ESCAPED] if it was unescaped.
    public static Unescaped unescaped(final String text) {
        if (!text.contains("<") && text.stripLeading().startsWith("&lt;")) {
            return new Unescaped(htmlUnescape(text), List.of(ESCAPED));
        }
        return new Unescaped(text, List.of());
    }

    /// @param text XML text.
    /// @param what What it is, for the message, e.g. `Events XML`.
    /// @return Its root element.
    /// @throws ToolRefusal if it is not well-formed, e.g. "Events XML is not well-formed XML: ...".
    public static Element parse(final String text, final String what) {
        try {
            return XmlTrees.parse(text);
        } catch (final NotWellFormedException e) {
            throw new ToolRefusal(what + " is not well-formed XML: " + e.getMessage()
                                  + (e.getLine() > 0
                    ? " (line " + e.getLine() + ")"
                    : ""));
        }
    }

    /// The stroom: functions an XSLT calls, and the problems with them: the stroom namespace not declared, or a
    /// name that is not a Stroom function (with the nearest real ones, or for a JSON one, how JSON is parsed).
    ///
    /// @param xslt The XSLT's text.
    /// @param root Its root element.
    /// @return The errors, and the names of the stroom: functions it calls.
    public static FunctionProblems functionProblems(final String xslt, final Element root) {
        final Set<String> calls = new TreeSet<>();
        final Matcher matcher = STROOM_CALL.matcher(xslt);
        while (matcher.find()) {
            calls.add(matcher.group(1));
        }
        final List<String> errors = new ArrayList<>();
        if (!calls.isEmpty() && !XmlTrees.namespaces(root).containsKey("stroom")) {
            errors.add("stroom: functions are used but xmlns:stroom=\"stroom\" is not declared");
        }
        final List<String> known = new ArrayList<>(new TreeSet<>(STROOM_FUNCTIONS));
        for (final String name : calls) {
            if (STROOM_FUNCTIONS.contains(name)) {
                continue;
            }
            final List<String> close = CloseMatches.closeMatches(name, known, 2, 0.6);
            String message = "stroom:" + name + "() is not a Stroom function and will not compile";
            if (name.toLowerCase(Locale.ROOT).contains("json")) {
                message += ": JSON held in a string is parsed with json-to-xml(text), an XPath function with no "
                           + "prefix (its output is in the xpath-functions namespace); raw JSON input is parsed by "
                           + "the pipeline's JSONParser element, not in XSLT";
            } else if (!close.isEmpty()) {
                message += "; did you mean " + close.stream()
                        .map(c -> "stroom:" + c + "()")
                        .collect(Collectors.joining(" or ")) + "?";
            }
            errors.add(message);
        }
        return new FunctionProblems(errors, calls);
    }

    /// @param node An element of an XSLT.
    /// @return The xpath-default-namespace in force at it, or null when none is declared.
    public static String defaultNamespace(final Element node) {
        Node current = node;
        while (current instanceof final Element element) {
            if (element.hasAttribute("xpath-default-namespace")) {
                return element.getAttribute("xpath-default-namespace");
            }
            current = element.getParentNode();
        }
        return null;
    }

    /// Bare element names of a parser's output read with no xpath-default-namespace: they select nothing.
    ///
    /// @param root           An XSLT's root element.
    /// @param inputNamespace The namespace of its input when known, or null: "" (the sample is XML in no
    ///                       namespace) means its own records, record or data elements are what they say, not a
    ///                       Data Splitter's records:2.
    /// @return A problem for each namespace whose elements are read bare, with the expressions and the fix.
    public static List<String> namespaceProblems(final Element root, final String inputNamespace) {
        final Map<String, List<String>> found = new LinkedHashMap<>();
        for (final Element node : XmlTrees.named(root, XmlTrees.XSL, "*")) {
            if (defaultNamespace(node) != null) {
                continue;
            }
            for (final String attr : XPATH_ATTRS) {
                final String expr = node.getAttribute(attr);
                if (expr.isEmpty()) {
                    continue;
                }
                final Set<String> names = nameSteps(expr);
                for (final Map.Entry<String, Set<String>> entry : INPUT_NAMESPACES.entrySet()) {
                    final String namespace = entry.getKey();
                    if (JSON_NS.equals(namespace) && (expr.contains("json-to-xml") || expr.contains("parse-xml")
                                                      || expr.contains("analyze-string"))) {
                        continue;
                    }
                    if (names.stream().anyMatch(entry.getValue()::contains)) {
                        found.computeIfAbsent(namespace, k -> new ArrayList<>()).add(attr + "=\"" + expr + "\"");
                    }
                }
            }
        }
        if ("".equals(inputNamespace)) {
            found.remove(RECORDS_NS);
        }
        final List<String> problems = new ArrayList<>();
        found.forEach((namespace, where) -> {
            final String shown = String.join(", ", where.subList(0, Math.min(3, where.size())))
                                 + (where.size() > 3
                    ? " and " + (where.size() - 3) + " more"
                    : "");
            final String note = JSON_NS.equals(namespace)
                    ? " (the JSONParser's output; json-to-xml() output is in " + FN_NS + ")"
                    : RECORDS_NS.equals(namespace)
                            ? " (a Data Splitter's output, or XML that declares records:2)"
                            : " (an Events stream)";
            problems.add(shown + " select nothing: those elements are in namespace " + namespace + note + ", and no "
                         + "xpath-default-namespace is declared. Set xpath-default-namespace=\"" + namespace
                         + "\" on xsl:stylesheet, or bind a prefix to it. If the input really has no namespace, "
                         + "declare xpath-default-namespace=\"\" to say so.");
        });
        return problems;
    }

    /// @param node An element of an XSLT.
    /// @return The literal event-logging elements written directly below it, looking through xsl:if, xsl:choose
    /// and so on (not into variables and parameters).
    public static List<Element> literalChildren(final Element node) {
        final List<Element> found = new ArrayList<>();
        for (final Element child : XmlTrees.children(node)) {
            if (EVT.equals(child.getNamespaceURI())) {
                found.add(child);
            } else if (!XmlTrees.isXsl(child, "variable") && !XmlTrees.isXsl(child, "param")) {
                found.addAll(literalChildren(child));
            }
        }
        return found;
    }

    /// Literal result elements in the event-logging namespace that the schema has no place for, e.g.
    /// Event/EventDetail/ServerEvent. A fragment written in a named template (an EventSource on its own) is
    /// accepted wherever the schema allows an element of that name.
    ///
    /// @param root   An XSLT's root element.
    /// @param schema The event-logging schema its output must follow.
    /// @return A problem for each element out of place.
    public static List<String> eventElementProblems(final Element root, final EventSchema schema) {
        final List<String> problems = new ArrayList<>();
        for (final Element node : XmlTrees.named(root, EVT, "*")) {
            if (XmlTrees.ancestors(node).stream().anyMatch(a -> EVT.equals(a.getNamespaceURI()))) {
                continue;
            }
            final String name = node.getLocalName();
            final List<String> options;
            if ("Events".equals(name) || "Event".equals(name)) {
                options = List.of(name);
            } else {
                options = schema.pathsNamed(name);
                if (options.isEmpty()) {
                    problems.add("No element '" + name + "' exists anywhere in the event-logging schema");
                    continue;
                }
            }
            walk(schema, node, options, problems);
        }
        return problems;
    }

    /// json-to-xml on a value that may be empty or not JSON stops processing with a fatal error ("empty
    /// sequence", seen in a test environment): each call outside an xsl:try, in an expression without an if
    /// guard.
    ///
    /// @param root An XSLT's root element.
    /// @return A warning naming the calls, with how to guard them; empty if there are none.
    public static List<String> unguardedJson(final Element root) {
        final List<String> found = new ArrayList<>();
        for (final Element element : XmlTrees.descendantsAndSelf(root)) {
            if (XmlTrees.ancestors(element).stream().anyMatch(a -> XmlTrees.isXsl(a, "try"))) {
                continue;
            }
            for (final Attr attr : XmlTrees.attributes(element)) {
                final String value = attr.getValue();
                if (JSON_CALL.matcher(value).find() && !IF_GUARD.matcher(value).find()) {
                    found.add(attr.getName() + "=\"" + value.substring(0, Math.min(80, value.length())) + "\"");
                }
            }
        }
        if (found.isEmpty()) {
            return List.of();
        }
        return List.of("json-to-xml without a guard (" + String.join("; ", found.subList(0, Math.min(3,
                found.size()))) + "): a record whose value is empty, or isn't JSON, stops processing with a fatal "
                       + "error. Guard it: select=\"if (normalize-space(x)) then json-to-xml(x) else ()\", inside "
                       + "xsl:try with an empty xsl:catch for text that isn't JSON. build_translation_xslt does "
                       + "this itself.");
    }

    // Walks the literal elements below node, each against the paths its parent may be at.
    private static void walk(final EventSchema schema,
                             final Element node,
                             final List<String> options,
                             final List<String> problems) {
        for (final Element child : literalChildren(node)) {
            final String name = child.getLocalName();
            final List<String> kept = new ArrayList<>();
            String reason = null;
            for (final String option : options) {
                if ("Events".equals(option)) {
                    if ("Event".equals(name)) {
                        kept.add("Event");
                    } else {
                        reason = "Events holds Event elements, not " + name;
                    }
                    continue;
                }
                try {
                    schema.resolve(option + "/" + name);
                    kept.add(option + "/" + name);
                } catch (final IllegalArgumentException e) {
                    reason = e.getMessage();
                }
            }
            if (kept.isEmpty()) {
                final String message = options.size() == 1
                        ? options.getFirst() + "/" + name + " is not in the event-logging schema" + (reason != null
                        ? ": " + reason
                        : "")
                        : name + " is not allowed below any of " + PyText.list(options.subList(0,
                                Math.min(4, options.size()))) + " in the event-logging schema";
                if (!problems.contains(message)) {
                    problems.add(message);
                }
                continue;
            }
            walk(schema, child, kept, problems);
        }
    }

    // The bare element names an expression steps to, outside its literals and with its axes left out.
    private static Set<String> nameSteps(final String expr) {
        final String code = ELEMENT_AXIS.matcher(LITERAL.matcher(expr).replaceAll("''")).replaceAll("");
        final Set<String> names = new TreeSet<>();
        final Matcher matcher = NAME_STEP.matcher(code);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static Map<String, Set<String>> inputNamespaces() {
        final Map<String, Set<String>> namespaces = new LinkedHashMap<>();
        namespaces.put(RECORDS_NS, Set.of("records", "record", "data"));
        namespaces.put(EVT, Set.of("Events", "Event"));
        namespaces.put(JSON_NS, Set.of("map", "array", "string", "number", "boolean", "null"));
        return namespaces;
    }

    // Text HTML-escaped as XML is (the five entities and character references), unescaped.
    private static String htmlUnescape(final String text) {
        final Matcher matcher = NUMERIC_REFERENCE.matcher(text);
        final StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            final String number = matcher.group(1);
            final int codePoint = number.startsWith("x")
                    ? Integer.parseInt(number.substring(1), 16)
                    : Integer.parseInt(number);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
        }
        matcher.appendTail(sb);
        return sb.toString()
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }


    // --------------------------------------------------------------------------------


    /// XML text as it is to be read.
    ///
    /// @param text     The text, unescaped if it came HTML-escaped.
    /// @param warnings [#ESCAPED] if it was unescaped; empty otherwise.
    public record Unescaped(String text, List<String> warnings) {

    }

    /// What is wrong with the stroom: functions an XSLT calls.
    ///
    /// @param errors The errors.
    /// @param calls  The names of the stroom: functions it calls, sorted.
    public record FunctionProblems(List<String> errors, Set<String> calls) {

    }
}
