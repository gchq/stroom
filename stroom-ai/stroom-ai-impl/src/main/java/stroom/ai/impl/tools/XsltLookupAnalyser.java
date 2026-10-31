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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.ai.impl.workspace.SafeXPath;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.util.shared.NullSafe;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.transform.sax.SAXSource;

/// Works out the reference data lookups an XSLT makes, and the reference data maps a reference loader's XSLT
/// writes, from the XSLT itself rather than by searching its text, so a map name held in a variable or parameter,
/// defined elsewhere in the XSLT or in one it includes, or built at run time, is still found.
///
/// - The XSLT is parsed as XML, refusing a DOCTYPE and never loading anything from outside it.
/// - Calls to `stroom:lookup` and `stroom:bitmap-lookup` are found in every XPath-bearing attribute and in
///   attribute value templates.
/// - The map argument is resolved: a literal; a variable or parameter followed to its definition in scope (local
///   first, then global, then through `xsl:include` and `xsl:import`); several values (e.g. from an `xsl:choose`,
///   or a parameter's default and its `xsl:with-param` values); or an expression built at run time, reported as
///   dynamic with its constant parts. Anything it can't follow is dynamic with the expression quoted. It never
///   guesses.
/// - For a loader, the `<map>` elements of the reference-data schema it writes are found, and their content
///   resolved in the same way.
final class XsltLookupAnalyser {

    static final String STROOM_NAMESPACE = "stroom";
    static final String REFERENCE_DATA_NAMESPACE_PREFIX = "reference-data";
    static final String LOOKUP = "lookup";
    static final String BITMAP_LOOKUP = "bitmap-lookup";
    /// Shown in a pattern for a part of a name worked out at run time.
    static final String DYNAMIC_PART = "…";
    /// How far variables are followed before giving up.
    static final int MAX_DEPTH = 25;
    /// The most includes followed from one XSLT, in all.
    static final int MAX_INCLUDES = 50;
    /// The most values a name may have before it is treated as dynamic.
    static final int MAX_VALUES = 50;

    // The attributes of XSLT instructions that hold XPath expressions.
    private static final Set<String> XPATH_ATTRIBUTES = Set.of("select", "test", "match", "use", "use-when",
            "group-by", "group-adjacent", "group-starting-with", "group-ending-with", "value", "from", "count",
            "start-at", "for-each-item", "initial-value", "context-item");
    // The attributes of XSLT instructions that are attribute value templates.
    private static final Set<String> AVT_ATTRIBUTES = Set.of("namespace", "href", "format", "lang", "order",
            "case-order", "data-type", "collation", "letter-value", "ordinal", "grouping-separator",
            "grouping-size");
    // The XSLT instructions whose name attribute is an attribute value template.
    private static final Set<String> AVT_NAME_ELEMENTS = Set.of("element", "attribute", "processing-instruction");
    private static final Set<String> CASE_FUNCTIONS = Set.of("upper-case", "lower-case");
    private static final Set<String> IDENTITY_FUNCTIONS = Set.of("string", "xs:string", "normalize-space",
            "data", "fn:string", "fn:normalize-space");
    private static final Processor PROCESSOR = new Processor(false);

    private final IncludeResolver includeResolver;
    private final Map<String, Optional<Stylesheet>> includes = new LinkedHashMap<>();
    private final List<String> unreadableIncludes = new ArrayList<>();

    /// @param includeResolver Finds the XSLTs that `xsl:include` and `xsl:import` name, as the user may see them.
    XsltLookupAnalyser(final IncludeResolver includeResolver) {
        this.includeResolver = includeResolver;
    }

    /// Parses an XSLT.
    ///
    /// @param name The XSLT's name.
    /// @param uuid Its UUID.
    /// @param text Its text.
    /// @return The XSLT, parsed.
    /// @throws WorkspaceException if it isn't well-formed XML, or has a DOCTYPE.
    static Stylesheet parse(final String name, final String uuid, final String text) {
        try {
            final DocumentBuilder builder = PROCESSOR.newDocumentBuilder();
            builder.setLineNumbering(true);
            builder.setDTDValidation(false);
            final XdmNode document = builder.build(new SAXSource(SafeXPath.secureReader(),
                    new InputSource(new StringReader(NullSafe.string(text)))));
            return new Stylesheet(name, uuid, document);
        } catch (final SaxonApiException e) {
            throw new WorkspaceException("XSLT '" + name + "' isn't XML that can be read: " + e.getMessage()
                                         + " (DOCTYPE declarations are not allowed.)");
        }
    }

    /// Finds the lookups an XSLT makes.
    ///
    /// @param stylesheet The XSLT.
    /// @return Each lookup, in document order.
    List<Lookup> lookups(final Stylesheet stylesheet) {
        final List<Lookup> lookups = new ArrayList<>();
        for (final XdmNode element : elements(stylesheet.document())) {
            ToolDeadline.check();
            for (final Expression expression : expressions(element)) {
                for (final Call call : calls(expression.text(), element)) {
                    final List<String> chain = new ArrayList<>();
                    final Resolution map = call.arguments().isEmpty()
                            ? Resolution.dynamic("(no map given)", null)
                            : resolve(call.arguments().getFirst(), element, stylesheet,
                                    new Context(chain, new HashSet<>(), 0));
                    lookups.add(new Lookup(stylesheet.name(), stylesheet.uuid(), call.function(),
                            element.getLineNumber(), element.getColumnNumber(), where(element),
                            call.arguments().isEmpty()
                                    ? ""
                                    : call.arguments().getFirst().strip(),
                            call.arguments().size() > 1
                                    ? call.arguments().get(1).strip()
                                    : "",
                            map, List.copyOf(chain)));
                }
            }
        }
        return lookups;
    }

    /// Finds the reference data maps a reference loader's XSLT writes, in `<map>` elements of the reference-data
    /// schema.
    ///
    /// @param stylesheet The loader's XSLT.
    /// @return Each `<map>` it writes, in document order.
    List<MapWrite> mapWrites(final Stylesheet stylesheet) {
        final List<MapWrite> writes = new ArrayList<>();
        for (final XdmNode element : elements(stylesheet.document())) {
            ToolDeadline.check();
            final boolean literalMap = "map".equals(element.getNodeName().getLocalName())
                                       && element.getNodeName().getNamespaceURI()
                                               .startsWith(REFERENCE_DATA_NAMESPACE_PREFIX);
            final boolean constructedMap = isXsl(element, "element")
                                           && "map".equals(element.attribute("name"));
            if (literalMap || constructedMap) {
                final List<String> chain = new ArrayList<>();
                final Resolution name = resolveContent(element, stylesheet,
                        new Context(chain, new HashSet<>(), 0)).trimmed();
                writes.add(new MapWrite(stylesheet.name(), stylesheet.uuid(), element.getLineNumber(),
                        element.getColumnNumber(), where(element), name, List.copyOf(chain)));
            }
        }
        return writes;
    }

    /// @return The includes and imports that couldn't be read: not found, or not visible to the user, which
    /// aren't told apart.
    List<String> unreadableIncludes() {
        return List.copyOf(unreadableIncludes);
    }

    /// @return The XSLTs read through includes and imports.
    List<Stylesheet> includedStylesheets() {
        return includes.values().stream()
                .flatMap(Optional::stream)
                .toList();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Finding calls

    private static List<XdmNode> elements(final XdmNode node) {
        final List<XdmNode> elements = new ArrayList<>();
        for (final XdmNode descendant : nodes(node, Axis.DESCENDANT)) {
            if (descendant.getNodeKind() == XdmNodeKind.ELEMENT) {
                elements.add(descendant);
            }
        }
        return elements;
    }

    /// @return The XPath expressions in an element's attributes.
    static List<Expression> expressions(final XdmNode element) {
        final List<Expression> expressions = new ArrayList<>();
        final boolean xsl = XmlTrees.XSL.equals(element.getNodeName().getNamespaceURI());
        final String elementName = element.getNodeName().getLocalName();
        for (final XdmNode attribute : nodes(element, Axis.ATTRIBUTE)) {
            final QName name = attribute.getNodeName();
            final String local = name.getLocalName();
            final String value = attribute.getStringValue();
            if (xsl && name.getNamespaceURI().isEmpty()) {
                if (XPATH_ATTRIBUTES.contains(local)) {
                    expressions.add(new Expression(local, value));
                } else if (AVT_ATTRIBUTES.contains(local)
                           || ("name".equals(local) && AVT_NAME_ELEMENTS.contains(elementName))) {
                    avt(value).forEach(text -> expressions.add(new Expression(local, text)));
                }
            } else if (XmlTrees.XSL.equals(name.getNamespaceURI())) {
                // E.g. xsl:use-when on a literal result element.
                if (XPATH_ATTRIBUTES.contains(local)) {
                    expressions.add(new Expression(local, value));
                }
            } else if (!xsl) {
                avt(value).forEach(text -> expressions.add(new Expression(local, text)));
            }
        }
        return expressions;
    }

    /// @param value An attribute value template, e.g. `a{$b}c{{d}}`.
    /// @return The expressions in it, e.g. `$b`.
    static List<String> avt(final String value) {
        final List<String> expressions = new ArrayList<>();
        int i = 0;
        while (i < value.length()) {
            final char c = value.charAt(i);
            if (c == '{') {
                if (i + 1 < value.length() && value.charAt(i + 1) == '{') {
                    i += 2;
                    continue;
                }
                final int end = endOfAvtExpression(value, i + 1);
                if (end < 0) {
                    break;
                }
                expressions.add(value.substring(i + 1, end));
                i = end + 1;
            } else {
                i++;
            }
        }
        return expressions;
    }

    private static int endOfAvtExpression(final String value, final int start) {
        int depth = 0;
        int i = start;
        while (i < value.length()) {
            final char c = value.charAt(i);
            if (c == '\'' || c == '"') {
                i = endOfString(value, i);
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
            i++;
        }
        return -1;
    }

    /// @return The calls to `stroom:lookup` and `stroom:bitmap-lookup` in an expression, including any nested in
    /// another call's arguments.
    static List<Call> calls(final String expression, final XdmNode element) {
        final List<Call> calls = new ArrayList<>();
        int i = 0;
        while (i < expression.length()) {
            final char c = expression.charAt(i);
            if (c == '\'' || c == '"') {
                i = endOfString(expression, i);
            } else if (c == '(' && i + 1 < expression.length() && expression.charAt(i + 1) == ':') {
                i = endOfComment(expression, i);
            } else if (isNameStart(c) && (i == 0 || !isNameChar(expression.charAt(i - 1)))) {
                int end = i;
                while (end < expression.length() && (isNameChar(expression.charAt(end))
                                                     || expression.charAt(end) == ':')) {
                    end++;
                }
                final String name = expression.substring(i, end);
                int open = end;
                while (open < expression.length() && Character.isWhitespace(expression.charAt(open))) {
                    open++;
                }
                final int colon = name.indexOf(':');
                if (open < expression.length() && expression.charAt(open) == '(' && colon > 0) {
                    final String prefix = name.substring(0, colon);
                    final String local = name.substring(colon + 1);
                    if ((LOOKUP.equals(local) || BITMAP_LOOKUP.equals(local))
                        && STROOM_NAMESPACE.equals(namespaceUri(element, prefix))) {
                        final int close = matchingClose(expression, open);
                        final String inner = expression.substring(open + 1, close < 0
                                ? expression.length()
                                : close);
                        calls.add(new Call(local, splitTopLevel(inner, ',')));
                    }
                }
                i = end;
            } else {
                i++;
            }
        }
        return calls;
    }

    private static List<XdmNode> nodes(final XdmNode node, final Axis axis) {
        final List<XdmNode> nodes = new ArrayList<>();
        final XdmSequenceIterator<XdmNode> iterator = node.axisIterator(axis);
        while (iterator.hasNext()) {
            nodes.add(iterator.next());
        }
        return nodes;
    }

    private static String namespaceUri(final XdmNode element, final String prefix) {
        for (final XdmNode namespace : nodes(element, Axis.NAMESPACE)) {
            if (prefix.equals(namespace.getNodeName() == null
                    ? ""
                    : namespace.getNodeName().getLocalName())) {
                return namespace.getStringValue();
            }
        }
        return null;
    }

    private static boolean isNameStart(final char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNameChar(final char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.';
    }

    /// @return The index just after the string literal starting at `start`.
    private static int endOfString(final String text, final int start) {
        final char quote = text.charAt(start);
        int i = start + 1;
        while (i < text.length()) {
            if (text.charAt(i) == quote) {
                if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return text.length();
    }

    /// @return The index just after the (possibly nested) comment starting at `start`.
    private static int endOfComment(final String text, final int start) {
        int depth = 0;
        int i = start;
        while (i + 1 < text.length()) {
            if (text.charAt(i) == '(' && text.charAt(i + 1) == ':') {
                depth++;
                i += 2;
            } else if (text.charAt(i) == ':' && text.charAt(i + 1) == ')') {
                depth--;
                i += 2;
                if (depth == 0) {
                    return i;
                }
            } else {
                i++;
            }
        }
        return text.length();
    }

    /// @return The index of the bracket closing the one at `open`, or -1.
    private static int matchingClose(final String text, final int open) {
        int depth = 0;
        int i = open;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '\'' || c == '"') {
                i = endOfString(text, i);
                continue;
            }
            if (c == '(' && i + 1 < text.length() && text.charAt(i + 1) == ':') {
                i = endOfComment(text, i);
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    /// @return The parts of some text separated by a character outside brackets, strings and comments.
    static List<String> splitTopLevel(final String text, final char separator) {
        final List<String> parts = new ArrayList<>();
        int depth = 0;
        int from = 0;
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '\'' || c == '"') {
                i = endOfString(text, i);
                continue;
            }
            if (c == '(' && i + 1 < text.length() && text.charAt(i + 1) == ':') {
                i = endOfComment(text, i);
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == separator && depth == 0) {
                parts.add(text.substring(from, i));
                from = i + 1;
            }
            i++;
        }
        if (!text.isBlank() || !parts.isEmpty()) {
            parts.add(text.substring(from));
        }
        return parts;
    }

    /// @return The parts of an expression joined by the `||` operator outside brackets, or one part.
    private static List<String> splitConcatenation(final String text) {
        final List<String> parts = new ArrayList<>();
        int depth = 0;
        int from = 0;
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '\'' || c == '"') {
                i = endOfString(text, i);
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == '|' && depth == 0 && i + 1 < text.length() && text.charAt(i + 1) == '|') {
                parts.add(text.substring(from, i));
                i += 2;
                from = i;
                continue;
            }
            i++;
        }
        parts.add(text.substring(from));
        return parts;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Resolving

    /// Works out the value of an expression, as far as it can be without the input.
    ///
    /// @param expression The expression.
    /// @param element    The element it is in, for the variables in scope.
    /// @param stylesheet The XSLT the element is in.
    /// @param context    What has been followed so far.
    /// @return Its value or values, or dynamic.
    Resolution resolve(final String expression,
                       final XdmNode element,
                       final Stylesheet stylesheet,
                       final Context context) {
        final String text = stripOuterBrackets(stripComments(NullSafe.string(expression)).strip());
        if (context.depth() > MAX_DEPTH) {
            return Resolution.dynamic(text, null);
        }
        if (text.isEmpty()) {
            return Resolution.literal("");
        }
        final String literal = stringLiteral(text);
        if (literal != null) {
            return Resolution.literal(literal);
        }
        if (text.matches("[0-9]+(\\.[0-9]+)?")) {
            return Resolution.literal(text);
        }
        if (text.matches("\\$[\\p{L}_][\\p{L}\\p{N}_.\\-]*(:[\\p{L}_][\\p{L}\\p{N}_.\\-]*)?")) {
            return variable(text.substring(1), element, stylesheet, context);
        }
        final List<String> concatenated = splitConcatenation(text);
        if (concatenated.size() > 1) {
            return Resolution.concat(concatenated.stream()
                    .map(part -> resolve(part, element, stylesheet, context.deeper()))
                    .toList(), text);
        }
        final Resolution conditional = conditional(text, element, stylesheet, context);
        if (conditional != null) {
            return conditional;
        }
        final FunctionCall call = functionCall(text);
        if (call != null) {
            if ("concat".equals(call.name()) || "fn:concat".equals(call.name())) {
                return Resolution.concat(call.arguments().stream()
                        .map(argument -> resolve(argument, element, stylesheet, context.deeper()))
                        .toList(), text);
            }
            if (IDENTITY_FUNCTIONS.contains(call.name()) && call.arguments().size() == 1) {
                final Resolution inner = resolve(call.arguments().getFirst(), element, stylesheet,
                        context.deeper());
                return "normalize-space".equals(call.name()) || "fn:normalize-space".equals(call.name())
                        ? inner.trimmed()
                        : inner;
            }
            if (CASE_FUNCTIONS.contains(call.name()) && call.arguments().size() == 1) {
                final Resolution inner = resolve(call.arguments().getFirst(), element, stylesheet,
                        context.deeper());
                return inner.mapValues("upper-case".equals(call.name())
                        ? value -> value.toUpperCase(Locale.ROOT)
                        : value -> value.toLowerCase(Locale.ROOT), text);
            }
        }
        // E.g. a value from the input data.
        return Resolution.dynamic(text, null);
    }

    /// @return The values of `if (...) then a else b`, or null if the expression isn't one.
    private Resolution conditional(final String text,
                                   final XdmNode element,
                                   final Stylesheet stylesheet,
                                   final Context context) {
        if (!text.startsWith("if") || text.length() < 3) {
            return null;
        }
        int open = 2;
        while (open < text.length() && Character.isWhitespace(text.charAt(open))) {
            open++;
        }
        if (open >= text.length() || text.charAt(open) != '(') {
            return null;
        }
        final int close = matchingClose(text, open);
        if (close < 0) {
            return null;
        }
        final String rest = text.substring(close + 1).strip();
        if (!rest.startsWith("then")) {
            return null;
        }
        final int elseAt = topLevelKeyword(rest, "else");
        if (elseAt < 0) {
            return null;
        }
        final String then = rest.substring("then".length(), elseAt);
        final String otherwise = rest.substring(elseAt + "else".length());
        context.chain().add("an if expression, giving either of its branches");
        return Resolution.union(List.of(
                resolve(then, element, stylesheet, context.deeper()),
                resolve(otherwise, element, stylesheet, context.deeper())));
    }

    private static int topLevelKeyword(final String text, final String keyword) {
        int depth = 0;
        int nestedIfs = 0;
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '\'' || c == '"') {
                i = endOfString(text, i);
                continue;
            }
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            } else if (depth == 0 && (i == 0 || !isNameChar(text.charAt(i - 1)))) {
                if (text.startsWith("if", i) && (i + 2 >= text.length() || !isNameChar(text.charAt(i + 2)))) {
                    nestedIfs++;
                } else if (text.startsWith(keyword, i)
                           && (i + keyword.length() >= text.length()
                               || !isNameChar(text.charAt(i + keyword.length())))) {
                    if (nestedIfs == 0) {
                        return i;
                    }
                    nestedIfs--;
                }
            }
            i++;
        }
        return -1;
    }

    /// @return The value of a variable or parameter in scope where it is used.
    private Resolution variable(final String name,
                                final XdmNode element,
                                final Stylesheet stylesheet,
                                final Context context) {
        final Binding binding = findBinding(name, element, stylesheet);
        if (binding == null) {
            context.chain().add("$" + name + ": not defined here or in an XSLT this one includes");
            return Resolution.dynamic("$" + name, null);
        }
        if (!context.visiting().add(binding.element())) {
            context.chain().add("$" + name + ": refers to itself");
            return Resolution.dynamic("$" + name, null);
        }
        try {
            final boolean param = isXsl(binding.element(), "param");
            final boolean global = isTopLevel(binding.element());
            context.chain().add("$" + name + ": " + (global
                    ? "global "
                    : "local ") + (param
                    ? "xsl:param"
                    : "xsl:variable") + " at line " + binding.element().getLineNumber()
                                + (binding.stylesheet() == stylesheet
                    ? ""
                    : " of included XSLT '" + binding.stylesheet().name() + "'"));
            final List<Resolution> values = new ArrayList<>();
            final Resolution own = value(binding.element(), binding.stylesheet(), context.deeper());
            if (param && own.isEmpty() && !hasValue(binding.element())) {
                // No default.
            } else {
                values.add(own);
            }
            if (param && !global) {
                for (final Binding withParam : withParams(binding)) {
                    context.chain().add("$" + name + ": passed by xsl:with-param at line "
                                        + withParam.element().getLineNumber()
                                        + (withParam.stylesheet() == stylesheet
                            ? ""
                            : " of included XSLT '" + withParam.stylesheet().name() + "'"));
                    values.add(value(withParam.element(), withParam.stylesheet(), context.deeper()));
                }
            }
            if (values.isEmpty()) {
                return Resolution.dynamic("$" + name + " (a parameter with no default, never passed)", null);
            }
            return Resolution.union(values);
        } finally {
            context.visiting().remove(binding.element());
        }
    }

    private static boolean hasValue(final XdmNode element) {
        return element.attribute("select") != null || element.children().iterator().hasNext();
    }

    /// @return The value of a variable, parameter or with-param: its select, or its content.
    private Resolution value(final XdmNode element, final Stylesheet stylesheet, final Context context) {
        final String select = element.attribute("select");
        if (select != null) {
            return resolve(select, element, stylesheet, context);
        }
        return resolveContent(element, stylesheet, context);
    }

    /// Works out the text an element's content (a sequence constructor) makes.
    ///
    /// @return Its value or values, or dynamic.
    Resolution resolveContent(final XdmNode element, final Stylesheet stylesheet, final Context context) {
        final List<Resolution> parts = new ArrayList<>();
        for (final XdmNode child : element.children()) {
            if (child.getNodeKind() == XdmNodeKind.TEXT) {
                final String text = child.getStringValue();
                // Whitespace-only text in an XSLT is stripped.
                if (!text.isBlank()) {
                    parts.add(Resolution.literal(text));
                }
            } else if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                if (isXsl(child, "text")) {
                    parts.add(Resolution.literal(child.getStringValue()));
                } else if (isXsl(child, "value-of") || isXsl(child, "sequence") || isXsl(child, "copy-of")) {
                    parts.add(value(child, stylesheet, context.deeper()));
                } else if (isXsl(child, "choose")) {
                    final List<Resolution> branches = new ArrayList<>();
                    for (final XdmNode branch : child.children()) {
                        if (isXsl(branch, "when") || isXsl(branch, "otherwise")) {
                            branches.add(resolveContent(branch, stylesheet, context.deeper()));
                        }
                    }
                    context.chain().add("an xsl:choose at line " + child.getLineNumber()
                                        + ", giving any of its branches");
                    parts.add(Resolution.union(branches));
                } else if (isXsl(child, "variable") || isXsl(child, "param")) {
                    // A binding, which adds nothing to the value.
                } else {
                    parts.add(Resolution.dynamic("<" + child.getNodeName() + "> at line " + child.getLineNumber(),
                            null));
                }
            }
        }
        if (parts.isEmpty()) {
            return Resolution.literal("");
        }
        return parts.size() == 1
                ? parts.getFirst()
                : Resolution.concat(parts, "the content of <" + element.getNodeName() + "> at line "
                                           + element.getLineNumber());
    }

    /// @return The nearest definition of a variable or parameter in scope at an element: local first, then
    /// global, then in included and imported XSLTs.
    private Binding findBinding(final String name, final XdmNode element, final Stylesheet stylesheet) {
        XdmNode current = element;
        while (current != null && current.getParent() != null && !isTopLevel(current)) {
            XdmNode sibling = previousSibling(current);
            while (sibling != null) {
                if ((isXsl(sibling, "variable") || isXsl(sibling, "param")) && name.equals(sibling.attribute("name"))) {
                    return new Binding(sibling, stylesheet);
                }
                sibling = previousSibling(sibling);
            }
            current = current.getParent();
        }
        return findGlobal(name, stylesheet, new LinkedHashSet<>());
    }

    private Binding findGlobal(final String name, final Stylesheet stylesheet, final Set<String> visited) {
        if (!visited.add(stylesheet.uuid())) {
            return null;
        }
        final XdmNode root = root(stylesheet);
        if (root == null) {
            return null;
        }
        for (final XdmNode child : root.children()) {
            if ((isXsl(child, "variable") || isXsl(child, "param")) && name.equals(child.attribute("name"))) {
                return new Binding(child, stylesheet);
            }
        }
        for (final Stylesheet included : includesOf(stylesheet)) {
            final Binding binding = findGlobal(name, included, visited);
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    /// @return The xsl:with-param elements that may pass a value to a template's parameter: those of the
    /// xsl:call-template calls of a named template, or of any xsl:apply-templates for a matching one.
    private List<Binding> withParams(final Binding param) {
        final XdmNode template = param.element().getParent();
        if (template == null || !isXsl(template, "template")) {
            return List.of();
        }
        final String paramName = param.element().attribute("name");
        final String templateName = template.attribute("name");
        final List<Binding> withParams = new ArrayList<>();
        for (final Stylesheet stylesheet : family(param.stylesheet())) {
            for (final XdmNode element : elements(stylesheet.document())) {
                final XdmNode parent = element.getParent();
                if (isXsl(element, "with-param") && paramName.equals(element.attribute("name"))
                    && parent != null
                    && ((templateName != null && isXsl(parent, "call-template")
                         && templateName.equals(parent.attribute("name")))
                        || (template.attribute("match") != null && isXsl(parent, "apply-templates")))) {
                    withParams.add(new Binding(element, stylesheet));
                }
            }
        }
        return withParams;
    }

    /// @return A stylesheet and every XSLT it includes, directly or not.
    private List<Stylesheet> family(final Stylesheet stylesheet) {
        final Map<String, Stylesheet> family = new LinkedHashMap<>();
        final List<Stylesheet> pending = new ArrayList<>(List.of(stylesheet));
        while (!pending.isEmpty()) {
            final Stylesheet next = pending.removeFirst();
            if (family.putIfAbsent(next.uuid(), next) == null) {
                pending.addAll(includesOf(next));
            }
        }
        return List.copyOf(family.values());
    }

    /// @return The XSLTs a stylesheet includes or imports, as far as they can be read.
    private List<Stylesheet> includesOf(final Stylesheet stylesheet) {
        final XdmNode root = root(stylesheet);
        if (root == null) {
            return List.of();
        }
        final List<Stylesheet> result = new ArrayList<>();
        for (final XdmNode child : root.children()) {
            if ((isXsl(child, "include") || isXsl(child, "import")) && child.attribute("href") != null) {
                final String href = child.attribute("href");
                if (!includes.containsKey(href) && includes.size() >= MAX_INCLUDES) {
                    continue;
                }
                final Optional<Stylesheet> included = includes.computeIfAbsent(href, key -> {
                    final Optional<Stylesheet> found = includeResolver.resolve(key);
                    if (found.isEmpty()) {
                        unreadableIncludes.add(key);
                    }
                    return found;
                });
                included.ifPresent(result::add);
            }
        }
        return result;
    }

    private static XdmNode root(final Stylesheet stylesheet) {
        for (final XdmNode child : stylesheet.document().children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                return child;
            }
        }
        return null;
    }

    private static boolean isTopLevel(final XdmNode node) {
        final XdmNode parent = node.getParent();
        return parent != null && (isXsl(parent, "stylesheet") || isXsl(parent, "transform"));
    }

    private static XdmNode previousSibling(final XdmNode node) {
        final XdmNode parent = node.getParent();
        if (parent == null) {
            return null;
        }
        XdmNode previous = null;
        for (final XdmNode child : parent.children()) {
            if (child.equals(node)) {
                return previous;
            }
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                previous = child;
            }
        }
        return null;
    }

    /// @param node      A node.
    /// @param localName The local name of an XSLT instruction, e.g. `variable`.
    /// @return True if the node is that XSLT instruction.
    static boolean isXsl(final XdmNode node, final String localName) {
        return node != null
               && node.getNodeKind() == XdmNodeKind.ELEMENT
               && XmlTrees.XSL.equals(node.getNodeName().getNamespaceURI())
               && localName.equals(node.getNodeName().getLocalName());
    }

    /// @return Where an element is: the template it is in (by match or name), or the global it defines.
    static String where(final XdmNode element) {
        XdmNode current = element;
        while (current != null) {
            if (isXsl(current, "template")) {
                return current.attribute("match") != null
                        ? "template match=\"" + current.attribute("match") + "\""
                        : "template name=\"" + current.attribute("name") + "\"";
            }
            if (isTopLevel(current)) {
                final String name = current.attribute("name");
                return "global " + current.getNodeName() + (name == null
                        ? ""
                        : " " + name);
            }
            current = current.getParent();
        }
        return "";
    }

    private static String stringLiteral(final String text) {
        if (text.length() >= 2) {
            final char quote = text.charAt(0);
            if ((quote == '\'' || quote == '"') && endOfString(text, 0) == text.length()
                && text.charAt(text.length() - 1) == quote) {
                final String doubled = String.valueOf(quote) + quote;
                return text.substring(1, text.length() - 1).replace(doubled, String.valueOf(quote));
            }
        }
        return null;
    }

    private static String stripOuterBrackets(final String text) {
        String result = text;
        while (result.startsWith("(") && matchingClose(result, 0) == result.length() - 1) {
            result = result.substring(1, result.length() - 1).strip();
        }
        return result;
    }

    private static String stripComments(final String text) {
        if (!text.contains("(:")) {
            return text;
        }
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '\'' || c == '"') {
                final int end = endOfString(text, i);
                sb.append(text, i, end);
                i = end;
            } else if (c == '(' && i + 1 < text.length() && text.charAt(i + 1) == ':') {
                i = endOfComment(text, i);
                sb.append(' ');
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /// @return The function call the whole expression is, or null.
    private static FunctionCall functionCall(final String text) {
        int end = 0;
        while (end < text.length() && (isNameChar(text.charAt(end)) || text.charAt(end) == ':')) {
            end++;
        }
        if (end == 0 || !isNameStart(text.charAt(0))) {
            return null;
        }
        int open = end;
        while (open < text.length() && Character.isWhitespace(text.charAt(open))) {
            open++;
        }
        if (open >= text.length() || text.charAt(open) != '(' || matchingClose(text, open) != text.length() - 1) {
            return null;
        }
        return new FunctionCall(text.substring(0, end), splitTopLevel(text.substring(open + 1, text.length() - 1),
                ','));
    }

    /// Finds an XSLT that another includes or imports.
    @FunctionalInterface
    interface IncludeResolver {

        /// @param href The `href` of the `xsl:include` or `xsl:import`.
        /// @return The XSLT, parsed, if it exists and the user can view it.
        Optional<Stylesheet> resolve(String href);
    }

    /// An XSLT, parsed.
    ///
    /// @param name     Its name.
    /// @param uuid     Its UUID.
    /// @param document Its XML.
    record Stylesheet(String name, String uuid, XdmNode document) {

    }

    /// A call to `stroom:lookup` or `stroom:bitmap-lookup`.
    ///
    /// @param xsltName      The XSLT it is in.
    /// @param xsltUuid      Its UUID.
    /// @param function      `lookup` or `bitmap-lookup`.
    /// @param line          The line of the element it is in.
    /// @param column        The column where that element's start tag ends.
    /// @param where         The template or global it is in.
    /// @param mapExpression The map argument, as written.
    /// @param keyExpression The key argument, as written.
    /// @param map           The map name, as resolved.
    /// @param chain         How it was resolved, step by step.
    record Lookup(String xsltName,
                  String xsltUuid,
                  String function,
                  int line,
                  int column,
                  String where,
                  String mapExpression,
                  String keyExpression,
                  Resolution map,
                  List<String> chain) {

    }

    /// A `<map>` element a reference loader's XSLT writes.
    ///
    /// @param xsltName The XSLT.
    /// @param xsltUuid Its UUID.
    /// @param line     The line of the element.
    /// @param column   The column where its start tag ends.
    /// @param where    The template it is in.
    /// @param map      The map name it writes, as resolved.
    /// @param chain    How it was resolved.
    record MapWrite(String xsltName,
                    String xsltUuid,
                    int line,
                    int column,
                    String where,
                    Resolution map,
                    List<String> chain) {

    }

    /// What a name was worked out to be.
    ///
    /// @param values     The values it may have (one if resolved).
    /// @param dynamic    True if (also) it may have a value worked out at run time.
    /// @param pattern    For a dynamic name built from parts, its constant parts with [#DYNAMIC_PART] for the
    ///                   others, e.g. "USER_…", or null.
    /// @param expression The expressions that couldn't be followed, or null.
    record Resolution(List<String> values, boolean dynamic, String pattern, String expression) {

        /// @param value A value known for certain.
        /// @return It, resolved.
        static Resolution literal(final String value) {
            return new Resolution(List.of(value), false, null, null);
        }

        /// @param expression The expression that can't be followed.
        /// @param pattern    Its constant parts, e.g. "USER_…", or null.
        /// @return A value worked out at run time.
        static Resolution dynamic(final String expression, final String pattern) {
            return new Resolution(List.of(), true, pattern, expression);
        }

        /// @return Any of the values of each.
        static Resolution union(final List<Resolution> resolutions) {
            final Set<String> values = new LinkedHashSet<>();
            final List<String> expressions = new ArrayList<>();
            final List<String> patterns = new ArrayList<>();
            boolean dynamic = false;
            for (final Resolution resolution : resolutions) {
                values.addAll(resolution.values());
                if (resolution.dynamic()) {
                    dynamic = true;
                    NullSafe.consumeNonBlankString(resolution.expression(), expressions::add);
                    NullSafe.consumeNonBlankString(resolution.pattern(), patterns::add);
                }
            }
            if (values.size() > MAX_VALUES) {
                return dynamic(String.join("; ", expressions), null);
            }
            return new Resolution(List.copyOf(values), dynamic, patterns.size() == 1
                    ? patterns.getFirst()
                    : null, expressions.isEmpty()
                    ? null
                    : String.join("; ", expressions));
        }

        /// @return The values of the parts joined, or dynamic with the constant parts if any part is.
        static Resolution concat(final List<Resolution> parts, final String expression) {
            if (parts.stream().noneMatch(Resolution::dynamic)) {
                List<String> values = List.of("");
                for (final Resolution part : parts) {
                    final List<String> next = new ArrayList<>();
                    for (final String prefix : values) {
                        for (final String value : part.values()) {
                            next.add(prefix + value);
                        }
                    }
                    if (next.size() > MAX_VALUES) {
                        return dynamic(expression, null);
                    }
                    values = next;
                }
                return new Resolution(List.copyOf(new LinkedHashSet<>(values)), false, null, null);
            }
            final StringBuilder pattern = new StringBuilder();
            for (final Resolution part : parts) {
                if (!part.dynamic() && part.values().size() == 1) {
                    pattern.append(part.values().getFirst());
                } else if (part.dynamic() && part.pattern() != null) {
                    pattern.append(part.pattern());
                } else if (!pattern.toString().endsWith(DYNAMIC_PART)) {
                    pattern.append(DYNAMIC_PART);
                }
            }
            final String text = pattern.toString();
            return dynamic(expression, text.replace(DYNAMIC_PART, "").isBlank()
                    ? null
                    : text);
        }

        /// @return This, with leading and trailing whitespace removed from its values, as Stroom does for the
        /// names of maps.
        Resolution trimmed() {
            return new Resolution(values.stream().map(String::strip).distinct().toList(), dynamic,
                    pattern == null
                            ? null
                            : pattern.strip(), expression);
        }

        /// @param mapper Changes each value, e.g. to upper case.
        /// @param text   The expression that does so, for when this is dynamic.
        /// @return This, with each value changed.
        Resolution mapValues(final UnaryOperator<String> mapper, final String text) {
            if (dynamic) {
                return dynamic(text, null);
            }
            return new Resolution(values.stream().map(mapper).distinct().toList(), false, null, null);
        }

        /// @return True if it is known for certain to be empty.
        boolean isEmpty() {
            return !dynamic && (values.isEmpty() || values.equals(List.of("")));
        }

        /// @return True if it is just one value, known for certain.
        boolean isResolved() {
            return !dynamic && values.size() == 1;
        }

        /// @param name A name, e.g. a map written by a loader.
        /// @return True if this may be that name: one of its values, or dynamic with constant parts it has.
        boolean mayBe(final String name) {
            if (values.contains(name)) {
                return true;
            }
            if (!dynamic) {
                return false;
            }
            if (pattern == null) {
                return true;
            }
            final String regex = Arrays.stream(pattern.split(DYNAMIC_PART, -1))
                    .map(Pattern::quote)
                    .collect(Collectors.joining(".*"));
            return name.matches(regex);
        }

        /// @return How it was resolved, in words.
        String describe() {
            final String quoted = values.stream().map(value -> "'" + value + "'").collect(Collectors.joining(", "));
            if (!dynamic) {
                return values.size() == 1
                        ? quoted + " (resolved)"
                        : "one of " + quoted;
            }
            final String runTime = pattern != null
                    ? "dynamic: " + pattern + " (from " + expression + ")"
                    : "dynamic: worked out at run time from " + expression;
            return values.isEmpty()
                    ? runTime
                    : "one of " + quoted + ", or " + runTime;
        }
    }

    /// What has been followed while resolving one name.
    ///
    /// @param chain    The steps taken, in words.
    /// @param visiting The definitions being followed, to stop at one that refers to itself.
    /// @param depth    How deep the resolving is.
    record Context(List<String> chain, Set<XdmNode> visiting, int depth) {

        /// @return This context, one level deeper.
        Context deeper() {
            return new Context(chain, visiting, depth + 1);
        }
    }

    /// An expression in an attribute.
    ///
    /// @param attribute The attribute's name.
    /// @param text      The expression.
    record Expression(String attribute, String text) {

    }

    /// A call found in an expression.
    ///
    /// @param function  The function's local name.
    /// @param arguments Its arguments, as written.
    record Call(String function, List<String> arguments) {

    }

    private record FunctionCall(String name, List<String> arguments) {

    }

    private record Binding(XdmNode element, Stylesheet stylesheet) {

    }
}
