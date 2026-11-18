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

import stroom.ai.impl.tools.validation.XmlTrees.NotWellFormedException;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// What the tools won't run or save: XPath expressions (a mapping's xpaths, a CEF plan's sources and fields) and
/// XSLTs that read the environment or a resource, or look a function up by a name made at run time.
///
/// What they run is not the tools' own: expressions come from the model, and documents from the samples, written
/// by whoever the logs record. Saxon as it comes reads whatever the process can: doc() and unparsed-text() open
/// local files and URLs, and environment-variable() reads the process's settings. So an expression calling a
/// function that reads the environment or a resource, or that looks a function up by a name made at run time, is
/// refused before it reaches Saxon ([LockedSaxon] refuses every URI as well).
///
/// The XSLT the tools save runs in Stroom, on its nodes, with Stroom's access: the same functions are refused
/// there, with the instructions that read or write a resource or evaluate an XPath made at run time, imports by
/// URL, and Stroom's own functions that reach outside Stroom with a record's data ([#STROOM_REFUSED]), unless the
/// XSLT being replaced already calls them ([#keptBy(String, String)]): the user put them there. Stroom's DNS lookup
/// functions ([#STROOM_ASKED]) are not refused outright: the tools ask the user to agree to each new one
/// ([#newLookups(String, String)]).
public final class XsltSafety {

    /// The XPath functions' namespace.
    public static final String FN = "http://www.w3.org/2005/xpath-functions";
    /// Saxon's own namespace.
    public static final String SAXON = "http://saxon.sf.net/";
    /// The namespace of Stroom's XSLT functions.
    public static final String STROOM = "stroom";
    /// The functions refused, by local name, in the namespaces of [#REFUSED_NAMESPACES]. A user-defined function
    /// of the same name is the XSLT's business: defined in the XSLT, or in one it imports by name (a shared XSLT,
    /// as an AGENTS doc's Required imports may name), e.g. geo:document() from 'GeoIP-V1'.
    /// system-property() is one: with Saxon's external functions allowed (as Stroom runs XSLT), a name in no
    /// namespace reads the JVM's system properties, and available-system-properties() lists them all.
    public static final List<String> REFUSED = List.of(
            "environment-variable", "available-environment-variables", "system-property",
            "available-system-properties", "function-lookup", "transform", "load-xquery-module", "doc",
            "doc-available", "document", "collection", "uri-collection", "unparsed-text", "unparsed-text-lines",
            "unparsed-text-available", "json-doc");
    /// Stroom's XSLT functions that reach outside Stroom with what they are given, refused in an XSLT the tools
    /// save (not in a mapping's xpaths, which are only ever saved as XSLT): http-call() and fetch-json() request a
    /// URL, and ask-ai() sends its text to a chat model.
    public static final List<String> STROOM_REFUSED = List.of("http-call", "fetch-json", "ask-ai");
    /// Stroom's XSLT functions that look a name up in DNS, which carries the name (and so a record's data) to
    /// whichever server answers for it: host-name() and host-address(). They are not refused
    /// ([#unsafeInXslt(String)]): an XSLT the tools save or step may call them once the user agrees
    /// ([#newLookups(String, String)]).
    public static final List<String> STROOM_ASKED = List.of("host-name", "host-address");
    /// What the DNS lookup functions do, as the user is told when asked to agree to one.
    public static final String LOOKUP_EFFECT = "looks up host names in DNS when the pipeline runs; a lookup can "
                                               + "carry the looked-up value to the DNS server";
    /// The namespaces whose functions of those names are refused: the XPath functions' and Saxon's own.
    public static final List<String> REFUSED_NAMESPACES = List.of(FN, SAXON);
    /// XSLT instructions that write a file, read a document by URI, evaluate an XPath made at run time, or bring
    /// in a package from elsewhere. xsl:merge-source only with for-each-source (documents by URI).
    public static final List<String> REFUSED_INSTRUCTIONS = List.of(
            "evaluate", "result-document", "source-document", "use-package");

    // Prefixes Saxon's XPath binds unless they are bound otherwise.
    private static final Map<String, String> PREDECLARED = Map.of("fn", FN, "saxon", SAXON);
    private static final Pattern CALL = callOf(REFUSED);
    private static final Pattern STROOM_CALL = callOf(STROOM_REFUSED);
    private static final Pattern LOOKUP_CALL = callOf(STROOM_ASKED);
    private static final Pattern SCHEME = Pattern.compile("^\\s*[A-Za-z][A-Za-z0-9+.\\-]*:");
    private static final String READS = "which reads the environment, files or network, or looks a function up by "
                                        + "name";
    private static final String REACHES = "which reaches outside Stroom (a URL or a chat model) with "
                                          + "what it is given: Stroom would run it on its nodes for every record, so "
                                          + "the tools don't save it unless the XSLT it replaces already calls it";

    private XsltSafety() {
        // Static only.
    }

    /// The refused function an XPath expression calls, if any. Read as XPath reads it, so a name in a string is
    /// not a call and a comment between a name and its '(' doesn't hide one; one that can't be read is read as
    /// written. A name with no prefix is the functions namespace's. One whose prefix nothing binds is not refused:
    /// it can't compile, in Stroom or here, and the caller says so more usefully (a mapping: "no functions entry
    /// binds 'geo'").
    ///
    /// @param expression An XPath expression, or null.
    /// @param namespaces The prefixes in scope where it runs (an XSLT's, or those a mapping's functions entries
    ///                   bind), or null for none.
    /// @return The local name of the refused function it calls, or null.
    public static String called(final String expression, final Map<String, String> namespaces) {
        return calledIn(expression, namespaces, CALL, REFUSED_NAMESPACES);
    }

    /// The Stroom function that reaches outside Stroom ([#STROOM_REFUSED]) an XPath expression calls, if any, read
    /// as [#called(String, Map)] reads one.
    ///
    /// @param expression An XPath expression, or null.
    /// @param namespaces The prefixes in scope where it runs, or null for none.
    /// @return The local name of the function, or null.
    public static String calledOutside(final String expression, final Map<String, String> namespaces) {
        return calledIn(expression, namespaces, STROOM_CALL, List.of(STROOM));
    }

    /// The Stroom DNS lookup functions ([#STROOM_ASKED]) an XPath expression calls, read as
    /// [#called(String, Map)] reads one.
    ///
    /// @param expression An XPath expression, or null.
    /// @param namespaces The prefixes in scope where it runs, or null for none.
    /// @return The local name of each one it calls, in the order first called; empty if none.
    public static List<String> lookupsCalled(final String expression, final Map<String, String> namespaces) {
        return callsIn(expression, namespaces, LOOKUP_CALL, List.of(STROOM));
    }

    private static String calledIn(final String expression,
                                   final Map<String, String> namespaces,
                                   final Pattern call,
                                   final List<String> refusedNamespaces) {
        final List<String> calls = callsIn(expression, namespaces, call, refusedNamespaces);
        return calls.isEmpty()
                ? null
                : calls.getFirst();
    }

    // The local names of the functions of those names the expression calls in those namespaces, distinct, in the
    // order first called.
    private static List<String> callsIn(final String expression,
                                        final Map<String, String> namespaces,
                                        final Pattern call,
                                        final List<String> refusedNamespaces) {
        final List<String> calls = new ArrayList<>();
        final String text = Objects.requireNonNullElse(expression, "");
        final Map<String, String> bound = Objects.requireNonNullElse(namespaces, Map.of());
        final String code = code(text);
        final Matcher matcher = call.matcher(code != null
                ? code
                : text);
        while (matcher.find()) {
            final String prefix = matcher.group("prefix");
            final String uri;
            if (matcher.group("uri") != null) {
                uri = matcher.group("uri").strip();
            } else if (prefix != null && !prefix.isEmpty()) {
                uri = bound.containsKey(prefix)
                        ? bound.get(prefix)
                        : PREDECLARED.get(prefix);
            } else {
                uri = FN;
            }
            if (uri != null && refusedNamespaces.contains(uri) && !calls.contains(matcher.group("name"))) {
                calls.add(matcher.group("name"));
            }
        }
        return calls;
    }

    /// @param expression An XPath expression, or null.
    /// @param namespaces The prefixes in scope where it runs, or null for none.
    /// @return Why the tools won't run or save it, or null if they will.
    public static String refused(final String expression, final Map<String, String> namespaces) {
        final String name = called(expression, namespaces);
        return name == null
                ? null
                : "it calls " + name + "(), " + READS + ": the tools don't run or save it. Read the record's own "
                  + "values";
    }

    /// The expressions in an attribute (or text) value template: the parts in braces, '{{' and '}}' being braces
    /// themselves. A brace in a string or comment, or a map's braces, don't end one.
    ///
    /// @param text A value template.
    /// @return Its expressions; the whole text if it can't be read.
    public static List<String> valueTemplateParts(final String text) {
        final List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("{{", i) || text.startsWith("}}", i)) {
                i += 2;
                continue;
            }
            if (text.charAt(i) != '{') {
                i++;
                continue;
            }
            int j = i + 1;
            int depth = 0;
            while (true) {
                if (j >= text.length()) {
                    return List.of(text);
                }
                final char ch = text.charAt(j);
                if (ch == '\'' || ch == '"' || text.startsWith("(:", j)) {
                    j = ch == '\'' || ch == '"'
                            ? literalEnd(text, j)
                            : commentEnd(text, j);
                    if (j < 0) {
                        return List.of(text);
                    }
                    continue;
                }
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    if (depth == 0) {
                        break;
                    }
                    depth--;
                }
                j++;
            }
            out.add(text.substring(i + 1, j));
            i = j + 1;
        }
        return out;
    }

    /// Each thing in an XSLT the tools won't save: a refused function (or a Stroom function that reaches outside
    /// Stroom) in an XSL attribute (an expression, or a template whose braces are), in another element's attribute
    /// value template or a text value template; a refused instruction; an import or include by URL. A DNS lookup
    /// ([#STROOM_ASKED]) is not one: see [#lookupsInXslt(String)].
    ///
    /// @param code An XSLT's text.
    /// @return What is refused, with why, in document order; empty if nothing is, or if it is not well-formed
    /// (the XSLT check says so).
    public static List<Unsafe> unsafeInXslt(final String code) {
        final Element root;
        try {
            root = XmlTrees.parse(code);
        } catch (final NotWellFormedException e) {
            return List.of();
        }
        final Scan scan = new Scan();
        scanNode(root, scan);
        final List<Unsafe> unsafe = new ArrayList<>();
        scan.unsafe.forEach((what, why) -> unsafe.add(new Unsafe(what, why)));
        return unsafe;
    }

    /// Each place an XSLT calls one of Stroom's DNS lookup functions ([#STROOM_ASKED]), read where
    /// [#unsafeInXslt(String)] reads a refused function.
    ///
    /// @param code An XSLT's text.
    /// @return Each function and where it is called (once per template), in document order; empty if none, or if
    /// it is not well-formed.
    public static List<Lookup> lookupsInXslt(final String code) {
        final Element root;
        try {
            root = XmlTrees.parse(code);
        } catch (final NotWellFormedException e) {
            return List.of();
        }
        final Scan scan = new Scan();
        scanNode(root, scan);
        if (scan.lookups.isEmpty()) {
            return List.of();
        }
        final Map<Element, Integer> lines = elementLines(code, root);
        final Map<String, Lookup> lookups = new LinkedHashMap<>();
        for (final Hit hit : scan.lookups) {
            final String where = placeOf(hit.element());
            lookups.putIfAbsent(hit.function() + "\n" + where, new Lookup(hit.function(), where,
                    lines.getOrDefault(hit.element(), -1)));
        }
        return List.copyOf(lookups.values());
    }

    /// The DNS lookups of an XSLT whose function the XSLT it replaces doesn't call: those the user is asked to
    /// agree to. One the XSLT replaced already calls was put there by a person, as [#keptBy(String, String)] keeps
    /// a refused one.
    ///
    /// @param code     The new XSLT's text.
    /// @param previous The text of the XSLT it replaces, or null.
    /// @return Each lookup of a function new to the XSLT, as [#lookupsInXslt(String)] gives them; empty if none.
    public static List<Lookup> newLookups(final String code, final String previous) {
        final Set<String> kept = new HashSet<>();
        if (previous != null && !previous.isEmpty()) {
            lookupsInXslt(previous).forEach(lookup -> kept.add(lookup.function()));
        }
        return lookupsInXslt(code).stream()
                .filter(lookup -> !kept.contains(lookup.function()))
                .toList();
    }

    /// The refusals of an XSLT that the XSLT it replaces has too (the same function, instruction or import): those
    /// were put there by a person, so a working copy may keep doing what its production XSLT already did.
    ///
    /// @param code     The new XSLT's text.
    /// @param previous The text of the XSLT it replaces, or null.
    /// @return The `why` of each of the new XSLT's refusals that the previous XSLT has too; empty if none.
    public static Set<String> keptBy(final String code, final String previous) {
        if (previous == null || previous.isEmpty()) {
            return Set.of();
        }
        final Set<String> kept = new HashSet<>();
        unsafeInXslt(previous).forEach(unsafe -> kept.add(unsafe.what()));
        final Set<String> allowed = new HashSet<>();
        unsafeInXslt(code).stream()
                .filter(unsafe -> kept.contains(unsafe.what()))
                .forEach(unsafe -> allowed.add(unsafe.why()));
        return allowed;
    }

    // Scans an element (its instruction, attributes and text), then its children, in document order.
    private static void scanNode(final Element element, final Scan found) {
        final boolean xsl = XmlTrees.isXsl(element);
        final String local = element.getLocalName();
        final String name = xsl
                ? "xsl:" + local
                : local;
        if (xsl && (REFUSED_INSTRUCTIONS.contains(local)
                    || ("merge-source".equals(local) && !element.getAttribute("for-each-source").isEmpty()))) {
            found.unsafe.putIfAbsent(name, name + " reads or writes a document by URI, or evaluates an XPath made at "
                                           + "run time");
        }
        final String href = element.getAttribute("href");
        if (xsl && ("import".equals(local) || "include".equals(local)) && SCHEME.matcher(href).find()) {
            found.unsafe.putIfAbsent(name + " " + href, name + " href='" + href + "' is a URL: import Stroom XSLTs "
                                                        + "by name");
        }
        final Map<String, String> namespaces = XmlTrees.namespaces(element);
        for (final Attr attribute : XmlTrees.attributes(element)) {
            // An XSL element's attributes (and xsl:use-when and the like on others) are expressions or value
            // templates: read both ways. Another element's are value templates.
            final boolean expression = xsl || XmlTrees.XSL.equals(attribute.getNamespaceURI());
            final List<String> expressions = new ArrayList<>();
            if (expression) {
                expressions.add(attribute.getValue());
            }
            expressions.addAll(valueTemplateParts(attribute.getValue()));
            final String attributeName = attribute.getLocalName() != null
                    ? attribute.getLocalName()
                    : attribute.getName();
            scan(name + "/@" + attributeName, element, expressions, namespaces, found);
        }
        // Each run of text, before or between the children, is read with the prefixes of the element holding it.
        final StringBuilder run = new StringBuilder();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                run.append(child.getNodeValue());
                continue;
            }
            scan("text", element, valueTemplateParts(run.toString()), namespaces, found);
            run.setLength(0);
            if (child instanceof final Element childElement) {
                scanNode(childElement, found);
            }
        }
        scan("text", element, valueTemplateParts(run.toString()), namespaces, found);
    }

    private static void scan(final String where,
                             final Element element,
                             final List<String> expressions,
                             final Map<String, String> namespaces,
                             final Scan found) {
        for (final String expression : expressions) {
            final String name = called(expression, namespaces);
            if (name != null && !found.unsafe.containsKey(name + "()")) {
                found.unsafe.put(name + "()", where + " calls " + name + "(), " + READS + ": Stroom would run it "
                                              + "with its own access, so the tools don't save it");
            }
            final String outside = calledOutside(expression, namespaces);
            if (outside != null && !found.unsafe.containsKey("stroom:" + outside + "()")) {
                found.unsafe.put("stroom:" + outside + "()", where + " calls stroom:" + outside + "(), " + REACHES);
            }
            lookupsCalled(expression, namespaces).forEach(lookup ->
                    found.lookups.add(new Hit("stroom:" + lookup + "()", element)));
        }
    }

    // Where an element is in an XSLT, as the user is told: its template (by match or name), its function, or the
    // top-level declaration holding it.
    private static String placeOf(final Element element) {
        final List<Element> outwards = new ArrayList<>();
        outwards.add(element);
        outwards.addAll(XmlTrees.ancestors(element));
        for (final Element at : outwards) {
            if (XmlTrees.isXsl(at, "template")) {
                final String match = at.getAttribute("match");
                return match.isEmpty()
                        ? "template name='" + at.getAttribute("name") + "'"
                        : "template match='" + match + "'";
            }
            if (XmlTrees.isXsl(at, "function")) {
                return "function '" + at.getAttribute("name") + "'";
            }
        }
        // The outermost is the stylesheet; the one inside it, the top-level declaration.
        if (outwards.size() >= 2 && XmlTrees.isXsl(outwards.get(outwards.size() - 2))) {
            final Element topLevel = outwards.get(outwards.size() - 2);
            final String declared = topLevel.getAttribute("name");
            return "top-level xsl:" + topLevel.getLocalName() + (declared.isEmpty()
                    ? ""
                    : " name='" + declared + "'");
        }
        return "the stylesheet";
    }

    // The line each element of the XSLT starts on (where its start tag ends, as a SAX parser reports it), read with
    // the locked-down reader, by element; empty if the lines can't be read.
    private static Map<Element, Integer> elementLines(final String code, final Element root) {
        final List<Integer> lines = new ArrayList<>();
        try {
            final XMLReader reader = LockedSaxon.reader();
            reader.setContentHandler(new LineCounter(lines));
            reader.parse(new InputSource(new StringReader(code)));
        } catch (final IOException | SAXException | RuntimeException e) {
            return Map.of();
        }
        final List<Element> elements = XmlTrees.descendantsAndSelf(root);
        if (elements.size() != lines.size()) {
            return Map.of();
        }
        final Map<Element, Integer> byElement = new IdentityHashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            byElement.put(elements.get(i), lines.get(i));
        }
        return byElement;
    }

    // A call, name(...), or a function reference, name#1, of one of the names, with its prefix or Q{uri}. Not
    // $name(...), a variable's function.
    private static Pattern callOf(final List<String> names) {
        return Pattern.compile(
                "(?<![\\w.\\-$])(?:(?<prefix>[\\w.\\-]+):|Q\\{(?<uri>[^}]*)\\})?(?<name>"
                + names.stream()
                        .sorted(Comparator.comparingInt(String::length).reversed())
                        .map(Pattern::quote)
                        .collect(Collectors.joining("|"))
                + ")\\s*[(#]",
                Pattern.UNICODE_CHARACTER_CLASS);
    }

    // The index after the XPath comment starting at `at` (comments nest; a quote in one is just text), or -1.
    private static int commentEnd(final String text, final int at) {
        int depth = 0;
        int i = at;
        while (i < text.length()) {
            if (text.startsWith("(:", i)) {
                depth++;
                i += 2;
            } else if (text.startsWith(":)", i)) {
                depth--;
                i += 2;
                if (depth == 0) {
                    return i;
                }
            } else {
                i++;
            }
        }
        return -1;
    }

    // The index after the string literal starting at `at`, or -1. A doubled quote reads as two literals side by
    // side, which hides nothing.
    private static int literalEnd(final String text, final int at) {
        final int end = text.indexOf(text.charAt(at), at + 1);
        return end >= 0
                ? end + 1
                : -1;
    }

    // The expression with its string literals emptied and its comments made spaces, as XPath reads it; null when
    // a literal or comment never ends.
    private static String code(final String expression) {
        final StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < expression.length()) {
            final int end;
            final char ch = expression.charAt(i);
            if (ch == '\'' || ch == '"') {
                end = literalEnd(expression, i);
                if (end < 0) {
                    return null;
                }
                out.append("''");
            } else if (expression.startsWith("(:", i)) {
                end = commentEnd(expression, i);
                if (end < 0) {
                    return null;
                }
                out.append(' ');
            } else {
                out.append(ch);
                end = i + 1;
            }
            i = end;
        }
        return out.toString();
    }


    // --------------------------------------------------------------------------------


    /// A thing in an XSLT the tools won't save.
    ///
    /// @param what What it is, e.g. `doc()`, `xsl:evaluate` or `xsl:import file:///etc/x.xsl`.
    /// @param why  Why it is refused.
    public record Unsafe(String what, String why) {

    }

    /// A call in an XSLT of one of Stroom's DNS lookup functions ([#STROOM_ASKED]).
    ///
    /// @param function The function, e.g. `stroom:host-name()`.
    /// @param where    Where it is called, e.g. `template match='record'`.
    /// @param line     The line it is called on (its element's), or -1 if unknown.
    public record Lookup(String function, String where, int line) {

        /// @return Where it is called, as the user is told, e.g. `template match='record' (line 12)`.
        public String said() {
            return line > 0
                    ? where + " (line " + line + ")"
                    : where;
        }
    }

    // What a scan of an XSLT found: what is refused (what, then why), and each DNS lookup's call.
    private static final class Scan {

        private final Map<String, String> unsafe = new LinkedHashMap<>();
        private final List<Hit> lookups = new ArrayList<>();
    }

    // A DNS lookup function called in an element.
    private record Hit(String function, Element element) {

    }

    // Keeps the line each element starts on, in document order.
    private static final class LineCounter extends DefaultHandler {

        private final List<Integer> lines;
        private Locator locator;

        private LineCounter(final List<Integer> lines) {
            this.lines = lines;
        }

        @Override
        public void setDocumentLocator(final Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(final String uri,
                                 final String localName,
                                 final String qName,
                                 final Attributes attributes) {
            lines.add(locator == null
                    ? -1
                    : locator.getLineNumber());
        }
    }
}
