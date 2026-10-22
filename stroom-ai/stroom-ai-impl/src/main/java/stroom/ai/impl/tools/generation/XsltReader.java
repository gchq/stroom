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

import stroom.ai.impl.tools.validation.XsltVersionHistory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Reads a translation XSLT back into a mapping (the Stroom MCP server's `utils/xsltread.py`): the mapping kept with
/// it lost (its Documentation tab cleared), or out of step with it (the XSLT changed by hand). Asked for by the
/// user.
///
/// An XSLT the generator wrote is read back the way the generator writes it ([#read(String)]): per rule, the
/// condition it is chosen by, and each element (or Data entry, or attribute) the Event gets, with its constant or
/// the expression that fills it, variables replaced by what they select and shared templates followed. Then
/// ([#rebuild]):
///
/// - with the mapping kept before (out of step): every element that reads as it did keeps its entry exactly as it
///   was (field, extraction, map, time format), and only what differs becomes a new entry;
/// - with none (lost): the input, records, blank values and key=value extractions come from the XSLT itself, and each
///   element's expression is read for the generator's idioms (a field, an extracted value, a time format, a
///   transform, a value map, a default); anything else is kept as the expression itself (an xpath entry).
///
/// Expressions are parsed ([XPathTreeParser]), not split with regular expressions (asked for by the user), and read
/// against shapes made by the generator's own functions called with slots ([ReadShapes]), so the reader follows
/// whatever the generator writes. Either way the result is only a candidate: the rebuild tool regenerates the XSLT
/// from it and steps both over the sample in Stroom, and saves it only when every record's output is the same.
public final class XsltReader {

    private static final String XSL = MappingFacts.XSL;
    private static final String EVT = XsltGenerator.EVT;
    // The generator's own comments (not XPath): a rule's name, a kind left untranslated, one left Unknown on
    // purpose.
    private static final Pattern RULE_HEADER = Pattern.compile("^\\s*Rule '((?:[^']|'')*)'",
            Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern DROPPED = Pattern.compile("^\\s*(.+?): left untranslated on purpose\\s*$",
            Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern UNKNOWN = Pattern.compile(
            "^\\s*(.+?): EventDetail/Unknown on purpose: (.*?)\\s*$", Pattern.DOTALL | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern WHOLE_TEMPLATE = Pattern.compile("\\{(?!\\{)(.*)\\}", Pattern.DOTALL);
    // The prefixes the generator binds for itself; any other is an imported XSLT's functions (a mapping's
    // functions), unless the XSLT defines functions with it (a hand edit's own, local:x say).
    private static final Set<String> OWN_PREFIXES = Set.of("xsl", "xsi", "stroom", "xs", "fn", "map", "mcp");
    /// The input namespace of each input the generator reads by its namespace.
    static final Map<String, String> INPUT_NAMESPACE = inputNamespaces();

    private XsltReader() {
        // Static only.
    }

    // --- parsing expressions ------------------------------------------------------------------------------------

    /// @param text   An expression, or null.
    /// @param parser The parser of the XSLT it is in.
    /// @return It parsed, or null when it isn't one the parser reads (it is then kept as written).
    static XPathNode parseOrNull(final String text, final XPathTreeParser parser) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return parser.parse(text);
        } catch (final XPathParseException e) {
            return null;
        }
    }

    /// @param text   An expression, or null.
    /// @param parser The parser of the XSLT it is in.
    /// @return What it is, whatever its spacing and the brackets around it: for telling one from another.
    static List<Object> keyOf(final String text, final XPathTreeParser parser) {
        final XPathNode node = parseOrNull(text, parser);
        return node != null
                ? XPathTrees.normal(node).key()
                : Arrays.asList("text", String.join(" ", words(text == null
                        ? ""
                        : text)));
    }

    /// @param expr An expression.
    /// @return It as the reader compares it (normal()), as written, parsed with the generator's prefixes: for
    /// showing, and for tests.
    public static String canonical(final String expr) {
        return XPathTrees.normal(ReadShapes.PARSER.parse(expr)).text();
    }

    /// @param expr       An expression.
    /// @param namespaces The namespace bindings to parse it with.
    /// @return It as the reader compares it (normal()), as written.
    public static String canonical(final String expr, final Map<String, String> namespaces) {
        final XPathTreeParser parser = namespaces.isEmpty()
                ? ReadShapes.PARSER
                : XPathTreeParser.parserFor(namespaces);
        return XPathTrees.normal(parser.parse(expr)).text();
    }

    /// @param tree A parsed expression.
    /// @return Its variable references (`$name`): not the variables a for, let, some or every binds.
    static List<XPathNode> variables(final XPathNode tree) {
        final Set<XPathNode> binding = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final XPathNode node : tree.walk()) {
            if (Set.of("for", "let", "some", "every").contains(node.symbol())) {
                for (int i = 0; i < node.size(); i++) {
                    if ("$".equals(node.get(i).symbol()) && i % 2 == 0 && i < node.size() - 1) {
                        binding.add(node.get(i));
                    }
                }
            }
        }
        return tree.walk().stream().filter(node -> "$".equals(node.symbol()) && !binding.contains(node)).toList();
    }

    /// @param expr   An expression.
    /// @param scope  Variables in scope: name to what each selects.
    /// @param parser The parser of the XSLT it is in.
    /// @return The expression with its variables replaced by what they select, in brackets (repeatedly: one may
    /// read another).
    public static String resolve(final String expr, final Map<String, String> scope, final XPathTreeParser parser) {
        String out = expr;
        for (int round = 0; round < 10; round++) {
            if (scope.isEmpty() || !out.contains("$")) {
                return out;
            }
            final XPathNode tree = parseOrNull(out, parser);
            if (tree == null) {
                return out;
            }
            final List<XPathTrees.Replacement> found = new ArrayList<>();
            for (final XPathNode variable : variables(tree)) {
                final String selects = scope.get(variable.valueText());
                if (selects != null) {
                    found.add(new XPathTrees.Replacement(variable, "(" + selects + ")"));
                }
            }
            if (found.isEmpty()) {
                return out;
            }
            out = XPathTrees.splice(out, found);
        }
        return out;
    }

    // --- reading ------------------------------------------------------------------------------------------------

    /// @param code A translation XSLT.
    /// @return It read the way the generator writes it.
    /// @throws XmlParser.XmlParseException if it isn't well-formed.
    /// @throws IllegalArgumentException if it has no template matching its input (no start template).
    public static XsltReading read(final String code) {
        final XmlElement root = XmlParser.parse(XsltVersionHistory.strip(code), true, false);
        final XPathTreeParser parser = XPathTreeParser.parserFor(root.namespaces());
        final String namespace = Objects.requireNonNullElse(root.get("xpath-default-namespace"), "");
        final Map<String, XmlElement> templates = new LinkedHashMap<>();
        for (final XmlElement template : root.childElements(XSL, "template")) {
            final String key = modeOrName(template);
            if (key != null) {
                templates.put(key, template);
            }
        }
        final Map<String, XsltReading.Function> functions = new LinkedHashMap<>();
        final Set<String> ownPrefixes = new HashSet<>(OWN_PREFIXES);
        for (final XmlElement function : root.childElements(XSL, "function")) {
            final String name = Objects.requireNonNullElse(function.get("name"), "");
            ownPrefixes.add(name.contains(":")
                    ? name.substring(0, name.indexOf(':'))
                    : name);
            final List<XmlElement> body = function.childElements().stream()
                    .filter(child -> !child.is(XSL, "param"))
                    .toList();
            if (body.size() == 1 && body.getFirst().is(XSL, "sequence") && truthy(body.getFirst().get("select"))) {
                final List<String> params = function.childElements(XSL, "param").stream()
                        .map(param -> param.get("name"))
                        .toList();
                functions.put(function.get("name"), new XsltReading.Function(params, body.getFirst().get("select")));
            }
        }
        final Map<String, Map<String, String>> maps = new LinkedHashMap<>();
        final Map<String, String> globals = new LinkedHashMap<>();
        readVariables(root, parser, maps, globals);
        final XmlElement start = root.childElements(XSL, "template").stream()
                .filter(template -> truthy(template.get("match")) && !truthy(template.get("mode")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The XSLT has no template matching its input: "
                                                                + "there is nothing to read"));
        final XmlElement apply = descendant(start, "apply-templates");
        final String records = apply != null
                ? apply.get("select")
                : "";
        final String recordMode = apply != null
                ? apply.get("mode")
                : "event";
        XmlElement record = recordMode == null
                ? null
                : templates.get(recordMode);
        final List<String> notes = new ArrayList<>();
        if ("event".equals(recordMode) && record != null && record.childElements(XSL, "apply-templates").stream()
                .anyMatch(child -> "item".equals(child.get("mode")))) {
            record = templates.get("item");
            notes.add("one record, several events (for_each): each item read as the record");
        }
        final Map<String, String> prefixes = new LinkedHashMap<>();
        root.namespaces().forEach((prefix, uri) -> {
            if (prefix != null && !prefix.isEmpty() && !ownPrefixes.contains(prefix)) {
                prefixes.put(prefix, uri);
            }
        });
        final List<String> imports = root.childElements(XSL, "import").stream()
                .map(element -> element.get("href"))
                .filter(XsltReader::truthy)
                .toList();
        final XsltReading reading = new XsltReading(namespace, start.get("match"), records, nilValues(root, parser),
                functions, maps, globals, parser, notes, imports, prefixes);
        if (record == null) {
            reading.notes().add("no record template: nothing to read");
            return reading;
        }
        readRules(root, record, templates, parser, reading);
        return reading;
    }

    private static void readVariables(final XmlElement root,
                                      final XPathTreeParser parser,
                                      final Map<String, Map<String, String>> maps,
                                      final Map<String, String> globals) {
        for (final XmlElement variable : root.childElements(XSL, "variable")) {
            final XmlElement entries = first(variable, "map");
            if (entries != null) {
                final Map<String, String> map = new LinkedHashMap<>();
                maps.put(variable.get("name"), map);
                for (final XmlElement entry : entries.childElements(XSL, "map-entry")) {
                    final List<String> key = stringsOf(entry.get("key"), parser);
                    final List<String> value = stringsOf(entry.get("select"), parser);
                    if (!key.isEmpty() && !value.isEmpty()) {
                        map.put(key.getFirst(), value.getFirst());
                    }
                }
            } else if (truthy(variable.get("select"))) {
                globals.put(variable.get("name"), variable.get("select"));
            }
        }
    }

    private static List<String> stringsOf(final String text, final XPathTreeParser parser) {
        final XPathNode node = parseOrNull(text, parser);
        return node == null
                ? List.of()
                : XPathTrees.strings(node).orElse(List.of());
    }

    private static void readRules(final XmlElement root,
                                  final XmlElement record,
                                  final Map<String, XmlElement> templates,
                                  final XPathTreeParser parser,
                                  final XsltReading reading) {
        final Map<String, String> headers = ruleHeaders(root);
        final Map<String, String> recordScope = scope(record);
        final XmlElement choose = first(record, "choose");
        final List<XmlElement> branches = choose != null
                ? choose.childElements()
                : List.of(record);
        for (int n = 0; n < branches.size(); n++) {
            final XmlElement branch = branches.get(n);
            final String test = branch.is(XSL, "when")
                    ? branch.get("test")
                    : null;
            final RuleReading rule = branch(branch, n, test, recordScope, templates, headers, parser);
            if (rule == null) {
                if (branch.iterElements(XSL, "sequence").stream()
                        .anyMatch(sequence -> Objects.requireNonNullElse(sequence.get("select"), "")
                                .contains("stroom:log('WARN'"))) {
                    reading.setUnmatchedWarn(true);
                }
                continue;
            }
            reading.rules().add(rule);
        }
    }

    // Each rule template's rule name, from the comment the generator writes before it.
    private static Map<String, String> ruleHeaders(final XmlElement root) {
        final Map<String, String> out = new LinkedHashMap<>();
        String name = null;
        for (final XmlNode node : root.children()) {
            if (node instanceof final XmlComment comment) {
                final Matcher matcher = RULE_HEADER.matcher(Objects.requireNonNullElse(comment.text(), ""));
                name = matcher.lookingAt()
                        ? matcher.group(1).replace("''", "'")
                        : null;
            } else if (node instanceof final XmlElement element) {
                if (name != null && element.is(XSL, "template")) {
                    out.put(modeOrName(element), name);
                }
                name = null;
            }
        }
        return out;
    }

    private static RuleReading branch(final XmlElement branch,
                                      final int n,
                                      final String test,
                                      final Map<String, String> scope,
                                      final Map<String, XmlElement> templates,
                                      final Map<String, String> headers,
                                      final XPathTreeParser parser) {
        for (final XmlNode node : branch.children()) {
            if (node instanceof final XmlComment comment) {
                final Matcher matcher = DROPPED.matcher(Objects.requireNonNullElse(comment.text(), ""));
                if (matcher.matches()) {
                    return new RuleReading("drop-" + matcher.group(1), matcher.group(1),
                            resolvedTest(test, scope, parser), true);
                }
            }
        }
        final XmlElement call = branch.childElements().stream()
                .filter(child -> child.is(XSL, "apply-templates") || child.is(XSL, "call-template"))
                .findFirst()
                .orElse(null);
        XmlElement event = firstEvent(branch);
        if (call == null && event == null) {
            return null;
        }
        final RuleReading rule;
        final Map<String, String> own;
        if (call != null) {
            final String key = modeOrName(call);
            final XmlElement template = key == null
                    ? null
                    : templates.get(key);
            rule = new RuleReading(key, key == null
                    ? null
                    : headers.get(key), resolvedTest(test, scope, parser), false);
            if (template == null) {
                rule.notes().add("its template " + (key == null
                        ? "None"
                        : key) + " isn't in the XSLT");
                return rule;
            }
            for (final XmlNode node : template.children()) {
                if (node instanceof final XmlComment comment) {
                    final Matcher matcher = UNKNOWN.matcher(Objects.requireNonNullElse(comment.text(), ""));
                    if (matcher.matches()) {
                        rule.setUnknown(matcher.group(2));
                    }
                }
            }
            event = firstEvent(template);
            own = scope(template);
        } else {
            rule = new RuleReading("inline-" + n, null, resolvedTest(test, scope, parser), false);
            for (final XmlNode node : branch.children()) {
                if (node instanceof final XmlComment comment) {
                    final Matcher matcher = UNKNOWN.matcher(Objects.requireNonNullElse(comment.text(), ""));
                    if (matcher.matches()) {
                        rule.setName(matcher.group(1));
                        rule.setUnknown(matcher.group(2));
                    }
                }
            }
            own = scope;
        }
        if (event == null) {
            rule.notes().add("no Event written");
            return rule;
        }
        walk(event, List.of(), merged(scope, own), templates, rule, parser);
        return rule;
    }

    private static String resolvedTest(final String test, final Map<String, String> scope,
                                       final XPathTreeParser parser) {
        return truthy(test)
                ? resolve(test, scope, parser)
                : null;
    }

    private static void walk(final XmlElement node,
                             final List<String> path,
                             final Map<String, String> scope,
                             final Map<String, XmlElement> templates,
                             final RuleReading rule,
                             final XPathTreeParser parser) {
        for (final XmlElement child : node.childElements()) {
            final int written = rule.leaves().size();
            readChild(child, path, scope, templates, rule, parser);
            for (final LeafReading leaf : rule.leaves().subList(written, rule.leaves().size())) {
                if (leaf.guard() == null && !rule.guards().isEmpty()) {
                    leaf.setGuard(rule.guards().getLast());
                }
            }
        }
    }

    private static void readChild(final XmlElement child,
                                  final List<String> path,
                                  final Map<String, String> scope,
                                  final Map<String, XmlElement> templates,
                                  final RuleReading rule,
                                  final XPathTreeParser parser) {
        final String at = String.join("/", path);
        if (EVT.equals(child.namespace())) {
            readEventElement(child, path, scope, templates, rule, parser);
        } else if (child.is(XSL, "if") || child.is(XSL, "choose") || child.is(XSL, "when")
                   || child.is(XSL, "otherwise")) {
            if (child.is(XSL, "choose")) {
                rule.notes().add((at.isEmpty()
                        ? "Event"
                        : at) + ": an xsl:choose inside the event, read as all its branches");
            }
            if (child.is(XSL, "if")) {
                rule.guards().add(resolve(Objects.requireNonNullElse(child.get("test"), ""), scope, parser));
            }
            walk(child, path, scope, templates, rule, parser);
            if (child.is(XSL, "if")) {
                rule.guards().removeLast();
            }
        } else if (child.is(XSL, "value-of")) {
            final LeafReading leaf = new LeafReading(at, null);
            leaf.setExpr(resolve(Objects.requireNonNullElse(child.get("select"), ""), scope, parser));
            rule.leaves().add(leaf);
        } else if (child.is(XSL, "attribute")) {
            final LeafReading leaf = new LeafReading(join(path, "@" + child.get("name")), null);
            leaf.setExpr(resolve(Objects.requireNonNullElse(child.get("select"), ""), scope, parser));
            rule.leaves().add(leaf);
        } else if (child.is(XSL, "apply-templates") || child.is(XSL, "call-template")) {
            readCall(child, path, scope, templates, rule, parser);
        } else if (child.is(XSL, "sequence")) {
            final String select = Objects.requireNonNullElse(child.get("select"), "");
            final List<String> data = dataCall(select, parser);
            if (data != null) {
                final LeafReading leaf = new LeafReading(join(path, "Data"), data.get(0));
                leaf.setExpr(resolve(data.get(1), scope, parser));
                rule.leaves().add(leaf);
            } else if (!select.startsWith("stroom:log(")) {
                rule.notes().add(at + ": xsl:sequence " + head(select, 80) + " not read");
            }
        } else if (child.is(XSL, "for-each")) {
            final LeafReading leaf = new LeafReading(join(path, firstEventName(child)), null);
            leaf.setRepeat(String.join(" ", words(XmlSerialiser.toXml(child))));
            rule.leaves().add(leaf);
        } else if (!child.is(XSL, "variable")) {
            rule.notes().add(at + ": " + child.localName() + " not read");
        }
    }

    private static void readEventElement(final XmlElement child,
                                         final List<String> path,
                                         final Map<String, String> scope,
                                         final Map<String, XmlElement> templates,
                                         final RuleReading rule,
                                         final XPathTreeParser parser) {
        final String name = child.localName();
        if ("Data".equals(name)) {
            dataElement(child, path, scope, rule, parser);
            return;
        }
        final List<String> here = new ArrayList<>(path);
        here.add(name);
        for (final XmlElement.Attribute attribute : child.attributes()) {
            if (attribute.namespace() == null) {
                final LeafReading leaf = new LeafReading(join(here, "@" + attribute.name()), null);
                setValue(leaf, attribute.value(), scope, parser);
                rule.leaves().add(leaf);
            }
        }
        final String text = PyStr.strip(Objects.requireNonNullElse(child.text(), ""));
        if (!text.isEmpty() && child.childElements().isEmpty()) {
            final LeafReading leaf = new LeafReading(String.join("/", here), null);
            leaf.setValue(text);
            rule.leaves().add(leaf);
        } else {
            walk(child, here, scope, templates, rule, parser);
        }
    }

    private static void readCall(final XmlElement child,
                                 final List<String> path,
                                 final Map<String, String> scope,
                                 final Map<String, XmlElement> templates,
                                 final RuleReading rule,
                                 final XPathTreeParser parser) {
        final String key = modeOrName(child);
        final XmlElement template = key == null
                ? null
                : templates.get(key);
        if (template == null && child.is(XSL, "call-template")) {
            // A named template of an imported XSLT (a mapping's shared entry): what it writes is in that XSLT.
            final Map<String, String> params = new LinkedHashMap<>();
            for (final XmlElement with : child.childElements(XSL, "with-param")) {
                if (truthy(with.get("name"))) {
                    params.put(with.get("name"), paramValue(with));
                }
            }
            rule.calls().add(new SharedCall(String.join("/", path), key, params));
        } else if (template == null) {
            rule.notes().add(String.join("/", path) + ": template " + key + " isn't in the XSLT");
        } else {
            // The XSLT's own template (the generator's, or one added by hand): followed in place, its parameters
            // bound to what the call passes.
            walk(template, path, merged(merged(scope, scope(template)), params(template, child, scope, parser)),
                    templates, rule, parser);
        }
    }

    // mcp:data('name', value): the generator's Data entry, its name and the expression for its value.
    private static List<String> dataCall(final String select, final XPathTreeParser parser) {
        if (!select.startsWith("mcp:")) {
            return null;
        }
        final XPathNode tree = parseOrNull(select, parser);
        final XPathNode node = tree != null
                ? XPathTrees.normal(tree)
                : null;
        if (node == null || !"call".equals(node.symbol()) || !node.valueText().startsWith("mcp:") || node.size() != 2
            || !"(string)".equals(XPathTrees.normal(node.get(0)).symbol())) {
            return null;
        }
        return List.of(XPathTrees.normal(node.get(0)).valueText(), node.get(1).text());
    }

    private static void dataElement(final XmlElement element,
                                    final List<String> path,
                                    final Map<String, String> scope,
                                    final RuleReading rule,
                                    final XPathTreeParser parser) {
        final LeafReading leaf = new LeafReading(join(path, "Data"), element.get("Name"));
        final XmlElement attribute = element.childElements(XSL, "attribute").stream()
                .filter(child -> "Value".equals(child.get("name")))
                .findFirst()
                .orElse(null);
        if (attribute != null) {
            leaf.setExpr(resolve(Objects.requireNonNullElse(attribute.get("select"), ""), scope, parser));
        } else {
            setValue(leaf, Objects.requireNonNullElse(element.get("Value"), ""), scope, parser);
        }
        rule.leaves().add(leaf);
    }

    // An attribute value template (XSLT, not XPath): a constant (braces doubled), or one expression in braces.
    private static void setValue(final LeafReading leaf,
                                 final String template,
                                 final Map<String, String> scope,
                                 final XPathTreeParser parser) {
        final Matcher whole = WHOLE_TEMPLATE.matcher(template);
        if (whole.matches() && !whole.group(1).replace("{{", "").contains("{")
            && !whole.group(1).replace("}}", "").contains("}")) {
            leaf.setExpr(resolve(whole.group(1), scope, parser));
        } else {
            leaf.setValue(template.replace("{{", "{").replace("}}", "}"));
        }
    }

    // The mapping's nil values, from the predicate the generator leaves them out with.
    private static List<String> nilValues(final XmlElement root, final XPathTreeParser parser) {
        for (final XmlElement element : root.iterElements()) {
            for (final String attribute : List.of("select", "test")) {
                final String text = Objects.requireNonNullElse(element.get(attribute), "");
                if (!text.contains("not(normalize-space(.)")) {
                    continue;
                }
                final XPathNode tree = parseOrNull(text, parser);
                for (final XPathNode node : tree != null
                        ? tree.walk()
                        : List.<XPathNode>of()) {
                    if ("[".equals(node.symbol()) && node.size() == 2 && XPathTrees.isNilCheck(node.get(1))) {
                        return XPathTrees.strings(node.get(1).get(0).get(1)).orElse(List.of());
                    }
                }
            }
        }
        return List.of();
    }

    private static Map<String, String> scope(final XmlElement template) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final XmlElement variable : template.iterElements(XSL, "variable")) {
            if (truthy(variable.get("select"))) {
                out.put(variable.get("name"), variable.get("select"));
            }
        }
        return out;
    }

    // What an xsl:param or xsl:with-param gives, as an expression: its select, else its text as a string (an
    // xsl:param with neither is the empty string).
    private static String paramValue(final XmlElement param) {
        if (truthy(param.get("select"))) {
            return param.get("select");
        }
        if (param.children().isEmpty()) {
            return XPathText.literal(Objects.requireNonNullElse(param.text(), ""));
        }
        // Content of elements: not an expression; left unread.
        return "";
    }

    // A called template's parameters: what the call passes (read in the caller's scope), else their defaults.
    private static Map<String, String> params(final XmlElement template,
                                              final XmlElement call,
                                              final Map<String, String> scope,
                                              final XPathTreeParser parser) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final XmlElement param : template.childElements(XSL, "param")) {
            if (truthy(param.get("name"))) {
                out.put(param.get("name"), paramValue(param));
            }
        }
        for (final XmlElement with : call.childElements(XSL, "with-param")) {
            if (truthy(with.get("name"))) {
                out.put(with.get("name"), resolve(paramValue(with), scope, parser));
            }
        }
        out.values().removeIf(String::isEmpty);
        return out;
    }

    // --- from a reading to a mapping ----------------------------------------------------------------------------

    /// @param code     The XSLT as it is.
    /// @param kept     The mapping kept with it before, or null (lost).
    /// @param keptCode What the kept mapping generates now, or null.
    /// @return A mapping for this XSLT: the kept mapping where the XSLT still reads as it generates, the
    /// generator's idioms read from the XSLT elsewhere.
    public static RebuiltMapping rebuild(final String code, final TranslationMapping kept, final String keptCode) {
        return rebuild(code, kept, keptCode, Map.of());
    }

    /// @param code     The XSLT as it is.
    /// @param kept     The mapping kept with it before, or null (lost).
    /// @param keptCode What the kept mapping generates now, or null.
    /// @param imported The XSLTs it imports, by href (as Stroom resolves an import by name): where its shared
    ///                 templates and functions come from, for a lost mapping.
    /// @return A mapping for this XSLT: the kept mapping where the XSLT still reads as it generates, the
    /// generator's idioms read from the XSLT elsewhere.
    public static RebuiltMapping rebuild(final String code,
                                         final TranslationMapping kept,
                                         final String keptCode,
                                         final Map<String, String> imported) {
        return new MappingRebuild(code, kept, keptCode, imported == null
                ? Map.of()
                : imported).run();
    }

    /// @param code   A translation XSLT.
    /// @param parser The parser of its expressions.
    /// @return Its key=value functions (keyedText), by name without `mcp:`: the regex before the key, after it,
    /// and the flags.
    static Map<String, XsltReading.KeyedFunction> keyedShapes(final String code, final XPathTreeParser parser) {
        final Map<String, XsltReading.KeyedFunction> out = new LinkedHashMap<>();
        final XmlElement root = XmlParser.parse(XsltVersionHistory.strip(code));
        for (final XmlElement function : root.childElements(XSL, "function")) {
            final XmlElement body = first(function, "sequence");
            final String name = Objects.requireNonNullElse(function.get("name"), "");
            if (body == null || !name.startsWith("mcp:")) {
                continue;
            }
            final XPathNode node = parseOrNull(body.get("select"), parser);
            for (final String shape : List.of("keyed, flags", "keyed")) {
                final XPathMatch match = node == null
                        ? null
                        : XPathTrees.unify(ReadShapes.SHAPES.get(shape), node).orElse(null);
                if (match != null) {
                    out.put(name.substring(4), new XsltReading.KeyedFunction(match.string(1), match.string(2),
                            "keyed, flags".equals(shape)
                                    ? match.string(3)
                                    : ""));
                    break;
                }
            }
        }
        return out;
    }

    // --- small helpers ------------------------------------------------------------------------------------------

    private static String modeOrName(final XmlElement element) {
        return truthy(element.get("mode"))
                ? element.get("mode")
                : truthy(element.get("name"))
                        ? element.get("name")
                        : null;
    }

    private static XmlElement first(final XmlElement parent, final String xslName) {
        return parent.childElements(XSL, xslName).stream().findFirst().orElse(null);
    }

    private static XmlElement firstEvent(final XmlElement parent) {
        return parent.childElements(EVT, "Event").stream().findFirst().orElse(null);
    }

    // The first xsl element of that name below (lxml's find('.//xsl:name')).
    private static XmlElement descendant(final XmlElement parent, final String xslName) {
        return parent.iterElements(XSL, xslName).stream().filter(element -> element != parent).findFirst()
                .orElse(null);
    }

    private static String firstEventName(final XmlElement loop) {
        return loop.iterElements().stream()
                .filter(element -> EVT.equals(element.namespace()))
                .findFirst()
                .map(XmlElement::localName)
                .orElse("?");
    }

    private static Map<String, String> merged(final Map<String, String> first, final Map<String, String> second) {
        final Map<String, String> out = new LinkedHashMap<>(first);
        out.putAll(second);
        return out;
    }

    private static String join(final List<String> path, final String last) {
        final List<String> parts = new ArrayList<>(path);
        parts.add(last);
        return String.join("/", parts);
    }

    /// @param text Some text, or null.
    /// @return Whether Python takes it for true: not null and not empty.
    static boolean truthy(final String text) {
        return text != null && !text.isEmpty();
    }

    /// @param node A node, or null.
    /// @return Whether Python takes it for true: not null, and with children (a node is as long as its children).
    static boolean truthy(final XPathNode node) {
        return node != null && node.size() > 0;
    }

    /// @param text Some text.
    /// @return Its words, as Python's `split()` gives them.
    static List<String> words(final String text) {
        final List<String> out = new ArrayList<>();
        final StringBuilder word = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (PyStr.isSpace(cp)) {
                if (!word.isEmpty()) {
                    out.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.appendCodePoint(cp);
            }
        });
        if (!word.isEmpty()) {
            out.add(word.toString());
        }
        return out;
    }

    /// @param text  Some text.
    /// @param count How many characters.
    /// @return Its first characters (code points), as Python's `text[:count]`.
    static String head(final String text, final int count) {
        return text.codePointCount(0, text.length()) <= count
                ? text
                : text.substring(0, text.offsetByCodePoints(0, count));
    }

    private static Map<String, String> inputNamespaces() {
        final Map<String, String> out = new LinkedHashMap<>();
        out.put("data_splitter", "records:2");
        out.put("json", "http://www.w3.org/2013/XSL/json");
        return Collections.unmodifiableMap(out);
    }
}
