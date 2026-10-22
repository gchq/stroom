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

package stroom.mcp.impl.tools;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Shared XSLTs: documents a pipeline's XSLT pulls in with `xsl:import` or `xsl:include`, and what it uses them
/// for.
///
/// An environment often keeps common steps in one XSLT that every translation (or indexing) XSLT imports, e.g. a
/// named template writing EventSource/Device from stroom:meta() attributes, or Event/Meta with the stream's GUID.
/// New XSLTs should call the same templates where their siblings do, for maintainability, and must then not write
/// those elements themselves: a second EventSource/Device fails schema validation, a second JSON key is a
/// duplicate field.
///
/// Read here from the XSLT text: each named template a shared document defines and the elements (or JSON keys) it
/// writes at its top level, and each call to one of them in a calling XSLT, with the elements it sits inside. The
/// call's place is the path of the element it writes, below Event (EventSource/Device, Meta) or below the
/// document's map for an indexing XSLT (a key such as guid).
final class SharedXslt {

    static final String XSL = XmlDocs.XSL;
    static final String EVT = "event-logging:3";
    static final String FN = "http://www.w3.org/2005/xpath-functions";
    static final String RECORDS = "records:2";

    private static final Set<String> FLOW = Set.of("if", "choose", "when", "otherwise", "for-each",
            "for-each-group", "variable", "sequence", "where-populated");
    private static final Pattern META = Pattern.compile("stroom:meta\\(\\s*'([^']+)'\\s*\\)");

    private SharedXslt() {
    }

    /// @param xslt An XSLT's text.
    /// @return The hrefs it imports or includes (Stroom resolves each to the XSLT document of that name).
    static List<String> importsOf(final String xslt) {
        return XmlDocs.parse(xslt).map(SharedXslt::importsOf).orElse(List.of());
    }

    private static List<String> importsOf(final Element root) {
        final List<String> hrefs = new ArrayList<>();
        for (final Element element : XmlDocs.descendantsAndSelf(root)) {
            if ((XmlDocs.is(element, XSL, "import") || XmlDocs.is(element, XSL, "include"))
                && nonEmpty(XmlDocs.attribute(element, "href"))) {
                hrefs.add(element.getAttribute("href"));
            }
        }
        return hrefs;
    }

    /// What a shared XSLT does, read from its text: each named template with the elements (or JSON keys) it writes
    /// at its top level and in full, the stream meta it reads (stroom:meta) and its parameters (required="yes" or
    /// optional); and the template rules it supplies (match, mode), which apply without a call.
    ///
    /// @param xslt A shared XSLT's text.
    /// @return What it does, or `{unreadable: true}` if it doesn't parse.
    static Map<String, Object> describe(final String xslt) {
        final Optional<Element> parsed = XmlDocs.parse(xslt);
        if (parsed.isEmpty()) {
            return new LinkedHashMap<>(Map.of("unreadable", true));
        }
        final Element root = parsed.get();
        final Map<String, Object> templates = new LinkedHashMap<>();
        final List<Map<String, Object>> rules = new ArrayList<>();
        for (final Element template : XmlDocs.elements(root, XSL, "template")) {
            final List<String> meta = metaRead(template);
            final String name = XmlDocs.attribute(template, "name");
            if (nonEmpty(name)) {
                // Without required="yes", a parameter the call doesn't pass is its default (empty if none given).
                final Map<String, Object> params = new LinkedHashMap<>();
                for (final Element param : XmlDocs.children(template)) {
                    if (XmlDocs.is(param, XSL, "param")) {
                        params.put(XmlDocs.attribute(param, "name"), "yes".equals(XmlDocs.attribute(param, "required"))
                                ? "required"
                                : "optional");
                    }
                }
                final Map<String, Object> about = new LinkedHashMap<>();
                about.put("writes", written(template));
                about.put("paths", paths(template, List.of()));
                if (!meta.isEmpty()) {
                    about.put("reads_meta", meta);
                }
                if (!params.isEmpty()) {
                    about.put("params", params);
                }
                templates.put(name, about);
            } else {
                final Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("match", XmlDocs.attribute(template, "match"));
                if (nonEmpty(XmlDocs.attribute(template, "mode"))) {
                    rule.put("mode", template.getAttribute("mode"));
                }
                final List<String> writes = paths(template, List.of());
                rule.put("writes", writes.subList(0, Math.min(10, writes.size())));
                if (!meta.isEmpty()) {
                    rule.put("reads_meta", meta);
                }
                rules.add(rule);
            }
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("templates", templates);
        if (!rules.isEmpty()) {
            result.put("template_rules", rules);
        }
        final Map<String, Map<String, Object>> functions = functionsOf(root);
        if (!functions.isEmpty()) {
            result.put("functions", functions);
        }
        final List<String> imports = importsOf(root);
        if (!imports.isEmpty()) {
            result.put("imports", imports);
        }
        return result;
    }

    /// The xsl:functions a stylesheet defines: name -> its prefix's namespace, parameters and return type, for a
    /// mapping's functions entry (href, prefix, namespace) and the calls that pass the right arguments.
    ///
    /// @param root A stylesheet's root, or null.
    /// @return Its functions by name.
    static Map<String, Map<String, Object>> functionsOf(final Element root) {
        final Map<String, Map<String, Object>> functions = new LinkedHashMap<>();
        if (root == null) {
            return functions;
        }
        for (final Element function : XmlDocs.elements(root, XSL, "function")) {
            final String name = Objects.requireNonNullElse(XmlDocs.attribute(function, "name"), "");
            final String prefix = name.contains(":")
                    ? name.substring(0, name.indexOf(':'))
                    : null;
            final List<String> params = new ArrayList<>();
            for (final Element param : XmlDocs.children(function)) {
                if (XmlDocs.is(param, XSL, "param")) {
                    final String as = XmlDocs.attribute(param, "as");
                    params.add(nonEmpty(as)
                            ? XmlDocs.attribute(param, "name") + " as " + as
                            : XmlDocs.attribute(param, "name"));
                }
            }
            final Map<String, Object> about = new LinkedHashMap<>();
            about.put("prefix", prefix);
            about.put("namespace", prefix == null
                    ? function.lookupNamespaceURI(null)
                    : function.lookupNamespaceURI(prefix));
            about.put("params", params);
            if (nonEmpty(XmlDocs.attribute(function, "as"))) {
                about.put("returns", function.getAttribute("as"));
            }
            functions.put(name, about);
        }
        return functions;
    }

    /// @param xslt      A calling XSLT's text.
    /// @param functions The shared functions, by name.
    /// @return Each shared function it calls: how often, and one call as written (its select or test).
    static List<Map<String, Object>> functionCalls(final String xslt,
                                                   final Map<String, Map<String, Object>> functions) {
        final Optional<Element> parsed = XmlDocs.parse(xslt);
        if (parsed.isEmpty()) {
            return List.of();
        }
        final Map<String, Map<String, Object>> own = functionsOf(parsed.get());
        final List<Map<String, Object>> calls = new ArrayList<>();
        functions.forEach((name, about) -> {
            final Pattern call = Pattern.compile(Pattern.quote(name) + "\\s*\\(");
            final List<String> where = new ArrayList<>();
            for (final Element element : XmlDocs.descendantsAndSelf(parsed.get())) {
                for (final String value : XmlDocs.attributeValues(element)) {
                    if (call.matcher(value).find()) {
                        where.add(value);
                    }
                }
            }
            if (!where.isEmpty()) {
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("function", name);
                entry.putAll(about);
                entry.put("calls", where.size());
                entry.put("example", shorten(where.getFirst(), 200));
                if (own.containsKey(name)) {
                    entry.put("own_copy", "the calling XSLT defines its own, which wins over the import");
                }
                calls.add(entry);
            }
        });
        return calls;
    }

    /// @param xslt A shared XSLT's text.
    /// @return The named templates it defines, each with the elements (or JSON keys) it writes at its top level.
    static Map<String, List<String>> templatesOf(final String xslt) {
        final Map<String, List<String>> templates = new LinkedHashMap<>();
        XmlDocs.parse(xslt).ifPresent(root -> {
            for (final Element template : XmlDocs.elements(root, XSL, "template")) {
                if (nonEmpty(XmlDocs.attribute(template, "name"))) {
                    templates.put(template.getAttribute("name"), written(template));
                }
            }
        });
        return templates;
    }

    /// @param xslt      A calling XSLT's text.
    /// @param templates The shared named templates, each with what it writes.
    /// @return Each call in the XSLT to one of them: `{template, within, writes, at, with_params}`.
    static List<Map<String, Object>> callsOf(final String xslt, final Map<String, List<String>> templates) {
        final Optional<Element> parsed = XmlDocs.parse(xslt);
        if (parsed.isEmpty()) {
            return List.of();
        }
        final List<Map<String, Object>> calls = new ArrayList<>();
        for (final Element call : XmlDocs.elements(parsed.get(), XSL, "call-template")) {
            final String name = XmlDocs.attribute(call, "name");
            if (name == null || !templates.containsKey(name)) {
                continue;
            }
            final List<String> within = within(call, Collections.newSetFromMap(new IdentityHashMap<>()));
            final List<String> writes = templates.get(name);
            final Map<String, Object> passed = new LinkedHashMap<>();
            for (final Element withParam : XmlDocs.children(call)) {
                if (XmlDocs.is(withParam, XSL, "with-param") && nonEmpty(XmlDocs.attribute(withParam, "name"))) {
                    final String select = XmlDocs.attribute(withParam, "select");
                    passed.put(withParam.getAttribute("name"), nonEmpty(select)
                            ? select
                            : XmlDocs.leadingText(withParam).strip());
                }
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("template", name);
            entry.put("within", String.join("/", within));
            entry.put("writes", writes);
            entry.put("at", writes.stream()
                    .map(written -> join(within, written))
                    .toList());
            if (!passed.isEmpty()) {
                entry.put("with_params", passed);
            }
            calls.add(entry);
        }
        return calls;
    }

    /// How an XSLT uses the shared documents it imports. One entry per call, and one per import whose templates it
    /// does not call by name (template rules or xsl:apply-imports).
    ///
    /// @param xslt   A calling XSLT's text.
    /// @param shared Each href it imports, with the shared XSLT's text (null when it could not be read).
    /// @return How it uses each.
    static List<Map<String, Object>> usage(final String xslt, final Map<String, String> shared) {
        final List<Map<String, Object>> uses = new ArrayList<>();
        for (final String href : importsOf(xslt)) {
            final String text = shared.get(href);
            final Map<String, List<String>> templates = text == null
                    ? Map.of()
                    : templatesOf(text);
            final List<Map<String, Object>> calls = callsOf(xslt, templates);
            // {unreadable: true} for one that won't parse: no templates.
            final Object described = text == null
                    ? null
                    : describe(text).get("templates");
            final Map<?, ?> details = described instanceof final Map<?, ?> map
                    ? map
                    : Map.of();
            for (final Map<String, Object> call : calls) {
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("href", href);
                entry.putAll(call);
                if (details.get(call.get("template")) instanceof final Map<?, ?> about) {
                    about.forEach((key, value) -> {
                        if (!"writes".equals(key)) {
                            entry.put(String.valueOf(key), value);
                        }
                    });
                }
                uses.add(entry);
            }
            final List<Map<String, Object>> functions = text == null
                    ? List.of()
                    : functionCalls(xslt, functionsOf(XmlDocs.parse(text).orElse(null)));
            for (final Map<String, Object> function : functions) {
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("href", href);
                entry.put("template", null);
                entry.putAll(function);
                uses.add(entry);
            }
            if (calls.isEmpty() && functions.isEmpty()) {
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("href", href);
                entry.put("template", null);
                entry.put("note", text == null
                        ? "not found as an XSLT document"
                        : "imported, but none of its named templates or functions is called: it supplies template "
                          + "rules or xsl:apply-imports");
                uses.add(entry);
            }
        }
        return uses;
    }

    // The names of the elements an XSLT fragment writes at its top level: event-logging elements by name, JSON
    // (xpath-functions) values by key; looking through xsl:if, xsl:choose and the like, not into xsl:attribute.
    private static List<String> written(final Element element) {
        final Set<String> names = new LinkedHashSet<>();
        for (final Element child : XmlDocs.children(element)) {
            final String namespace = child.getNamespaceURI();
            if (XSL.equals(namespace)) {
                if (FLOW.contains(child.getLocalName())) {
                    names.addAll(written(child));
                } else if ("element".equals(child.getLocalName())) {
                    names.add(localPart(child.getAttribute("name")));
                }
            } else if (FN.equals(namespace) && nonEmpty(XmlDocs.attribute(child, "key"))) {
                names.add(child.getAttribute("key"));
            } else if (RECORDS.equals(namespace) && "data".equals(child.getLocalName())
                       && nonEmpty(XmlDocs.attribute(child, "name"))) {
                names.add(child.getAttribute("name"));
            } else if (!FN.equals(namespace)) {
                names.add(child.getLocalName());
            }
        }
        names.remove("");
        return List.copyOf(names);
    }

    // Every element path (or JSON key path) an XSLT fragment writes, e.g. Device/HostName, Device/IPAddress.
    private static List<String> paths(final Element element, final List<String> prefix) {
        final Set<String> paths = new LinkedHashSet<>();
        for (final Element child : XmlDocs.children(element)) {
            final String namespace = child.getNamespaceURI();
            if (XSL.equals(namespace)) {
                if (FLOW.contains(child.getLocalName())) {
                    paths.addAll(paths(child, prefix));
                } else if ("element".equals(child.getLocalName())) {
                    final List<String> here = with(prefix, localPart(child.getAttribute("name")));
                    paths.addAll(orSelf(paths(child, here), here));
                }
                continue;
            }
            if (FN.equals(namespace)) {
                final String name = XmlDocs.attribute(child, "key");
                if ("map".equals(child.getLocalName()) || "array".equals(child.getLocalName())) {
                    final List<String> here = nonEmpty(name)
                            ? with(prefix, name)
                            : prefix;
                    final List<String> below = paths(child, here);
                    if (!below.isEmpty()) {
                        paths.addAll(below);
                    } else if (nonEmpty(name)) {
                        paths.add(String.join("/", here));
                    }
                } else if (nonEmpty(name)) {
                    paths.add(String.join("/", with(prefix, name)));
                }
                continue;
            }
            if (RECORDS.equals(namespace) && "data".equals(child.getLocalName())
                && nonEmpty(XmlDocs.attribute(child, "name"))) {
                paths.add(child.getAttribute("name"));
                continue;
            }
            final List<String> here = with(prefix, child.getLocalName());
            paths.addAll(orSelf(paths(child, here), here));
        }
        return List.copyOf(paths);
    }

    // The literal elements (or JSON keys) a call sits inside, outermost first, below Event or the document map.
    // A call in a template of its own (a mode, or a named template) continues from where that template is applied:
    // seen live, shared templates called in a networkSource mode applied inside Network/Connect/Source.
    private static List<String> within(final Element call, final Set<Element> seen) {
        final List<String> path = new ArrayList<>();
        Node node = call.getParentNode();
        while (node instanceof final Element element) {
            if (XmlDocs.is(element, XSL, "template")) {
                final Element caller = caller(element, seen);
                if (caller != null) {
                    final Set<Element> seenHere = Collections.newSetFromMap(new IdentityHashMap<>());
                    seenHere.addAll(seen);
                    seenHere.add(element);
                    final List<String> outer = new ArrayList<>(within(caller, seenHere));
                    outer.addAll(path.reversed());
                    return outer;
                }
                break;
            }
            if (EVT.equals(element.getNamespaceURI())) {
                if ("Event".equals(element.getLocalName()) || "Events".equals(element.getLocalName())) {
                    break;
                }
                path.add(element.getLocalName());
            } else if (FN.equals(element.getNamespaceURI()) && nonEmpty(XmlDocs.attribute(element, "key"))) {
                path.add(element.getAttribute("key"));
            }
            node = element.getParentNode();
        }
        return path.reversed();
    }

    // Where a part's template is run from: the first apply-templates of its mode, or call of its name, that is
    // inside an element (the record's own templates, with no element around the call, don't count).
    private static Element caller(final Element template, final Set<Element> seen) {
        if (seen.contains(template)) {
            return null;
        }
        final Element root = template.getOwnerDocument().getDocumentElement();
        final List<Element> found;
        final String name = XmlDocs.attribute(template, "name");
        final String mode = XmlDocs.attribute(template, "mode");
        if (nonEmpty(name)) {
            found = XmlDocs.elements(root, XSL, "call-template").stream()
                    .filter(candidate -> name.equals(candidate.getAttribute("name")))
                    .toList();
        } else if (nonEmpty(mode) && !"#default".equals(mode)) {
            found = XmlDocs.elements(root, XSL, "apply-templates").stream()
                    .filter(candidate -> mode.equals(candidate.getAttribute("mode")))
                    .toList();
        } else {
            return null;
        }
        for (final Element candidate : found) {
            Node node = candidate.getParentNode();
            while (node instanceof final Element element && !XmlDocs.is(element, XSL, "template")) {
                if (EVT.equals(element.getNamespaceURI()) || FN.equals(element.getNamespaceURI())) {
                    return candidate;
                }
                node = element.getParentNode();
            }
        }
        return null;
    }

    // The stream meta a template reads, by its stroom:meta() calls.
    private static List<String> metaRead(final Element template) {
        final List<String> texts = new ArrayList<>();
        for (final Element element : XmlDocs.descendantsAndSelf(template)) {
            texts.addAll(XmlDocs.attributeValues(element));
        }
        texts.add(template.getTextContent());
        final Set<String> names = new TreeSet<>();
        for (final String text : texts) {
            final Matcher matcher = META.matcher(Objects.requireNonNullElse(text, ""));
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return List.copyOf(names);
    }

    private static List<String> with(final List<String> prefix, final String name) {
        final List<String> path = new ArrayList<>(prefix);
        path.add(name);
        return path;
    }

    private static List<String> orSelf(final List<String> below, final List<String> here) {
        return below.isEmpty()
                ? List.of(String.join("/", here))
                : below;
    }

    private static String join(final List<String> within, final String name) {
        final List<String> path = new ArrayList<>(within);
        path.add(name);
        return String.join("/", path);
    }

    private static String localPart(final String name) {
        final String value = Objects.requireNonNullElse(name, "");
        return value.substring(value.lastIndexOf(':') + 1);
    }

    private static boolean nonEmpty(final String value) {
        return value != null && !value.isEmpty();
    }

    private static String shorten(final String value, final int max) {
        return value.length() > max
                ? value.substring(0, max)
                : value;
    }
}
