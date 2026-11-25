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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.util.shared.NullSafe;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The intermediate form a CEF forwarder of two stages hands its CEF lines on in (decided with the user,
/// 2026-11-25): its own XSLT (the flattener) writes each Event's CEF line into it, and a shared XSLT the pipeline
/// inherits from its template turns that into kafka-records:1 for the Kafka producer, the topic set there, once.
///
/// The form is read from the shared XSLT itself, never assumed: from its templates' match patterns, the paths it
/// selects and the values it takes (e.g. it matches `data[@name='x']` and takes `@value`), as candidates
/// ([#candidates]), each then checked by running the shared XSLT over a document of that form ([#infer], [#check]):
/// it must write kafka-records:1 with the CEF line as a record's value.
///
/// @param namespace      The namespace the elements are in, or "" for none.
/// @param path           The elements from the root to the one carrying the line, e.g. `records`, `record`, `data`:
///                       the root written once, the rest once an Event. At least two.
/// @param nameAttribute  The attribute naming the item, e.g. `name`, or null where the shared XSLT tests none.
/// @param nameValue      Its value, e.g. the one the shared XSLT's predicate tests, or null with no name attribute.
/// @param valueAttribute The attribute the line is in, e.g. `value`; null for the item's text.
public record CefHandoff(String namespace,
                         List<String> path,
                         String nameAttribute,
                         String nameValue,
                         String valueAttribute) {

    /// The name given to the item where the shared XSLT tests only that it has a name attribute.
    static final String ANY_NAME = "CEF";
    /// The root used where the shared XSLT names none (it reads its items wherever they are): Stroom's records.
    static final String ANY_ROOT = "records";
    /// The kafka-records namespace the shared stage must write.
    static final String KAFKA_RECORDS = "kafka-records:1";
    /// The line put through the shared stage to check a form.
    static final String CHECK_LINE = "CEF:0|Vendor|Product|1.0|Id|Name|3|suser=check";

    private static final Pattern XPATH_DEFAULT_NAMESPACE = Pattern.compile(
            "xpath-default-namespace\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final String NAME = "(?:[A-Za-z_*][\\w.\\-]*:)?[A-Za-z_*][\\w.\\-]*";
    // A step of a path: a name with predicates, an attribute, the context item, its parent or its text.
    private static final String STEP = "(?:" + NAME + "(?:\\[[^\\]]*\\])*|@" + NAME + "|\\.\\.|\\.|text\\(\\))";
    private static final Pattern PATH = Pattern.compile(
            "(?<![\\w@$:.\\-])(/{0,2}" + STEP + "(?:/{1,2}" + STEP + ")*|/)(?!\\s*\\(|[\\w:.\\-])");
    private static final Pattern AXIS = Pattern.compile("\\b(?:child|descendant|descendant-or-self|self)::");
    private static final Pattern ATTRIBUTE_AXIS = Pattern.compile("\\battribute::");
    private static final Pattern PREDICATE = Pattern.compile(
            "@(" + NAME + ")(?:\\s*(?:=|eq)\\s*(?:'([^']*)'|\"([^\"]*)\"))?");
    private static final Pattern AVT = Pattern.compile("\\{([^{}]*)}");
    private static final Set<String> WORDS = Set.of("and", "or", "div", "mod", "eq", "ne", "lt", "le", "gt", "ge",
            "is", "to", "in", "return", "for", "some", "every", "satisfies", "if", "then", "else", "instance", "of",
            "cast", "castable", "as", "treat", "union", "intersect", "except", "let");
    private static final String GAP = "**";

    /// Copies the path.
    public CefHandoff {
        namespace = NullSafe.string(namespace);
        path = List.copyOf(Objects.requireNonNullElse(path, List.of()));
    }

    /// The forms the shared XSLT may read, best first: those whose line it puts in a Kafka record's `value` first,
    /// then the rest of what it takes, each with a root and wrappers from the paths it selects from the document's
    /// root. Not checked: [#infer] runs each.
    ///
    /// @param xslt The shared XSLT's text.
    /// @return The candidate forms, without repeats; none if it isn't well-formed or takes no value from an element.
    public static List<CefHandoff> candidates(final String xslt) {
        final String text = XsltVersionHistory.strip(NullSafe.string(xslt));
        final Optional<Element> root = XmlTrees.tryParse(text);
        if (root.isEmpty()) {
            return List.of();
        }
        final Matcher defaults = XPATH_DEFAULT_NAMESPACE.matcher(text);
        final String defaultNamespace = defaults.find()
                ? defaults.group(1).strip()
                : "";
        final Walk walk = new Walk(defaultNamespace);
        for (final Element template : XmlTrees.children(root.get())) {
            if (XmlTrees.isXsl(template, "template") && template.hasAttribute("match")) {
                for (final String pattern : splitTopLevel(template.getAttribute("match"))) {
                    walk.body(template, Context.of(walk.steps(pattern, template)), false);
                }
            }
        }
        final Set<CefHandoff> out = new LinkedHashSet<>();
        final List<Read> ordered = new ArrayList<>(walk.reads.stream().filter(Read::inValue).toList());
        walk.reads.stream().filter(read -> !read.inValue()).forEach(ordered::add);
        for (final Read read : ordered) {
            out.addAll(forms(read, walk.absolutes));
        }
        return List.copyOf(out);
    }

    /// The form the shared XSLT reads: each candidate ([#candidates]) run through it with a CEF line, the first
    /// giving kafka-records:1 with the line as a record's value. Where the XSLT can't be run here (it imports
    /// another, or calls Stroom's own functions), the best candidate, unchecked: stepping the pipeline checks it.
    ///
    /// @param xslt The shared XSLT's text.
    /// @return The form and whether it was checked; empty if no candidate can be read from it, or none passes.
    public static Optional<Inferred> infer(final String xslt) {
        final List<CefHandoff> candidates = candidates(xslt);
        for (final CefHandoff candidate : candidates) {
            final Checked checked = check(xslt, candidate.document(List.of(CHECK_LINE)), List.of(CHECK_LINE));
            if (checked.ran() && checked.problems().isEmpty()) {
                return Optional.of(new Inferred(candidate, true, null));
            }
            if (!checked.ran()) {
                return Optional.of(new Inferred(candidates.getFirst(), false, checked.problems().getFirst()));
            }
        }
        return Optional.empty();
    }

    /// Runs the shared XSLT over a document of the intermediate form (with the locked-down Saxon processor, no
    /// resource read) and checks it writes kafka-records:1: a record for each line, on a topic, its value the line.
    ///
    /// @param xslt     The shared XSLT's text.
    /// @param document A document of the intermediate form, e.g. what the flattener writes.
    /// @param lines    The CEF lines in it, in order.
    /// @return What it wrote and its problems; not run where it can't be here.
    public static Checked check(final String xslt, final String document, final List<String> lines) {
        final String output;
        try {
            output = CefSample.transform(XsltVersionHistory.strip(NullSafe.string(xslt)), document);
        } catch (final RuntimeException e) {
            return new Checked(false, null, List.of("the shared XSLT can't be run here: "
                                                    + NullSafe.string(e.getMessage()).lines().findFirst()
                                                            .orElse("")));
        }
        return new Checked(true, output, kafkaProblems(output, lines));
    }

    /// @param output What the shared stage wrote.
    /// @param lines  The CEF lines it was given, in order.
    /// @return Why it isn't kafka-records:1 with a record on a topic for each line, its value the line; none if it
    /// is.
    public static List<String> kafkaProblems(final String output, final List<String> lines) {
        final String text = NullSafe.string(output).replaceFirst("^\\s*<\\?xml[^>]*\\?>", "").strip();
        final Optional<Element> root = XmlTrees.tryParse(text);
        if (root.isEmpty() || !XmlTrees.is(root.get(), KAFKA_RECORDS, "kafkaRecords")) {
            return List.of("it wrote no kafkaRecords in " + KAFKA_RECORDS);
        }
        final List<Element> records = XmlTrees.named(root.get(), KAFKA_RECORDS, "kafkaRecord");
        final List<String> problems = new ArrayList<>();
        if (records.size() != lines.size()) {
            problems.add("it wrote " + records.size() + " Kafka record(s) for " + lines.size() + " CEF line(s)");
        }
        for (int i = 0; i < Math.min(records.size(), lines.size()); i++) {
            final Element record = records.get(i);
            if (record.getAttribute("topic").isBlank()) {
                problems.add("Kafka record " + (i + 1) + " has no topic");
            }
            final String value = XmlTrees.child(record, KAFKA_RECORDS, "value").map(XmlTrees::allText).orElse(null);
            if (!lines.get(i).equals(value)) {
                problems.add("Kafka record " + (i + 1) + "'s value isn't the CEF line handed on");
            }
        }
        return problems;
    }

    /// @param lines CEF lines.
    /// @return A document of this form holding them, one an item.
    public String document(final List<String> lines) {
        final StringBuilder sb = new StringBuilder("<").append(path.getFirst());
        if (!namespace.isEmpty()) {
            sb.append(" xmlns=\"").append(escape(namespace)).append('"');
        }
        sb.append('>');
        for (final String line : lines) {
            for (int i = 1; i < path.size() - 1; i++) {
                sb.append('<').append(path.get(i)).append('>');
            }
            sb.append('<').append(item());
            if (nameAttribute != null) {
                sb.append(' ').append(nameAttribute).append("=\"").append(escape(nameValue)).append('"');
            }
            if (valueAttribute != null) {
                sb.append(' ').append(valueAttribute).append("=\"").append(escape(line)).append("\"/>");
            } else {
                sb.append('>').append(escape(line)).append("</").append(item()).append('>');
            }
            for (int i = path.size() - 2; i >= 1; i--) {
                sb.append("</").append(path.get(i)).append('>');
            }
        }
        return sb.append("</").append(path.getFirst()).append('>').toString();
    }

    /// @param output What a flattener wrote in this form.
    /// @return The CEF lines in it: each item's (with the name tested) value, in order; none if it isn't XML.
    public List<String> linesIn(final String output) {
        final String text = NullSafe.string(output).replaceFirst("^\\s*<\\?xml[^>]*\\?>", "").strip();
        final Optional<Element> root = XmlTrees.tryParse(text);
        if (root.isEmpty()) {
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        for (final Element element : XmlTrees.named(root.get(), namespace.isEmpty()
                ? null
                : namespace, item())) {
            if (nameAttribute != null && !NullSafe.string(nameValue).equals(element.getAttribute(nameAttribute))) {
                continue;
            }
            out.add(valueAttribute != null
                    ? element.getAttribute(valueAttribute)
                    : XmlTrees.allText(element));
        }
        return out;
    }

    /// @return The element carrying the line.
    public String item() {
        return path.getLast();
    }

    /// @return The elements written once an Event, the item last.
    public List<String> perEvent() {
        return path.subList(1, path.size());
    }

    /// @return The form in words, e.g. `<records><record><data name="x" value="CEF line"/></record></records>` in
    /// namespace `n`.
    public String said() {
        final String example = document(List.of("CEF line"));
        return "`" + example + "`" + (namespace.isEmpty()
                ? ""
                : " (namespace `" + namespace + "`)");
    }

    /// @return It as a plan keeps it: `namespace` (where there is one), `path`, `name_attribute`, `name_value` and
    /// `value_attribute` (where set).
    public Map<String, Object> toJson() {
        final Map<String, Object> out = new LinkedHashMap<>();
        if (!namespace.isEmpty()) {
            out.put("namespace", namespace);
        }
        out.put("path", path);
        if (nameAttribute != null) {
            out.put("name_attribute", nameAttribute);
            out.put("name_value", nameValue);
        }
        if (valueAttribute != null) {
            out.put("value_attribute", valueAttribute);
        }
        return out;
    }

    /// @param json A form as [#toJson] writes it.
    /// @return It, or null where it has no path of two elements or more.
    public static CefHandoff fromJson(final Map<?, ?> json) {
        if (json == null || !(json.get("path") instanceof final List<?> names) || names.size() < 2) {
            return null;
        }
        return new CefHandoff(string(json.get("namespace")), names.stream().map(String::valueOf).toList(),
                string(json.get("name_attribute")), string(json.get("name_value")),
                string(json.get("value_attribute")));
    }

    // ------------------------------------------------------------------------------------------------

    // The forms one value the XSLT takes implies: its item, and each root and wrappers that lead to it.
    private static List<CefHandoff> forms(final Read read, final Set<List<String>> absolutes) {
        final List<Step> steps = read.path();
        int itemAt = -1;
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (!steps.get(i).isAttribute() && !steps.get(i).isGap()) {
                itemAt = i;
                break;
            }
        }
        if (itemAt < 0 || steps.get(itemAt).name().contains("*")) {
            return List.of();
        }
        final Step item = steps.get(itemAt);
        final String taken = itemAt + 1 < steps.size() && steps.get(itemAt + 1).isAttribute()
                ? steps.get(itemAt + 1).name().substring(1)
                : null;
        if (taken != null && taken.equals(item.predicateAttribute())) {
            // Its name read, not its value.
            return List.of();
        }
        final List<Step> above = steps.subList(0, itemAt);
        int lastGap = -1;
        for (int i = 0; i < above.size(); i++) {
            if (above.get(i).isGap()) {
                lastGap = i;
            }
        }
        final List<String> known = above.subList(lastGap + 1, above.size()).stream().map(Step::name).toList();
        final List<List<String>> chains = new ArrayList<>();
        if (lastGap < 0 && read.absolute()) {
            chains.add(known);
        } else {
            for (final List<String> absolute : absolutes) {
                final int at = absolute.indexOf(item.name());
                final List<String> prefix = at >= 0
                        ? absolute.subList(0, at)
                        : absolute;
                chains.add(joined(prefix, known));
            }
            if (!known.isEmpty()) {
                chains.add(known);
            }
            if (chains.stream().allMatch(List::isEmpty)) {
                chains.add(joined(List.of(ANY_ROOT), known));
            }
        }
        final String nameValue = item.predicateAttribute() == null
                ? null
                : NullSafe.nonBlankStringElse(item.predicateValue(), ANY_NAME);
        final List<CefHandoff> out = new ArrayList<>();
        for (final List<String> chain : chains) {
            if (chain.isEmpty() || chain.contains("*")) {
                continue;
            }
            final List<String> path = new ArrayList<>(chain);
            path.add(item.name());
            out.add(new CefHandoff(item.namespace(), path, item.predicateAttribute(), nameValue, taken));
        }
        return out;
    }

    // A known prefix from the root, then what is known above the item, overlapping where the one ends as the other
    // starts.
    private static List<String> joined(final List<String> prefix, final List<String> known) {
        for (int overlap = Math.min(prefix.size(), known.size()); overlap > 0; overlap--) {
            if (prefix.subList(prefix.size() - overlap, prefix.size()).equals(known.subList(0, overlap))) {
                final List<String> out = new ArrayList<>(prefix);
                out.addAll(known.subList(overlap, known.size()));
                return out;
            }
        }
        final List<String> out = new ArrayList<>(prefix);
        out.addAll(known);
        return out;
    }

    // A pattern or expression split at its top-level bars (a union), not those in predicates or literals.
    static List<String> splitTopLevel(final String text) {
        final List<String> out = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '[' || c == '(') {
                depth++;
            } else if (c == ']' || c == ')') {
                depth--;
            } else if (c == '|' && depth == 0) {
                out.add(text.substring(start, i).strip());
                start = i + 1;
            }
        }
        out.add(text.substring(start).strip());
        return out.stream().filter(part -> !part.isEmpty()).toList();
    }

    // An expression with its string literals outside predicates emptied, so words in them aren't taken for names.
    static String withoutLiterals(final String expression) {
        final StringBuilder sb = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < expression.length(); i++) {
            final char c = expression.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                    sb.append(c);
                } else if (depth > 0) {
                    sb.append(c);
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String escape(final String text) {
        return NullSafe.string(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String string(final Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }


    // --------------------------------------------------------------------------------


    /// The form read from a shared XSLT.
    ///
    /// @param handoff The form.
    /// @param checked True if the shared XSLT was run over it here and wrote the line to a Kafka record.
    /// @param why     Why it wasn't checked, or null.
    public record Inferred(CefHandoff handoff, boolean checked, String why) {

    }


    // --------------------------------------------------------------------------------


    /// The shared XSLT run over a document of the form.
    ///
    /// @param ran      True if it could be run here.
    /// @param output   What it wrote, or null where it didn't run.
    /// @param problems Why its output isn't kafka-records:1 carrying the lines (or why it didn't run); none if it is.
    public record Checked(boolean ran, String output, List<String> problems) {

    }


    // --------------------------------------------------------------------------------


    // One step of a path: an element (its local name and namespace, and an attribute its predicate tests), an
    // attribute (`@name`), or a gap (`**`: any elements, from `//` or a pattern that doesn't start at the root).
    private record Step(String name, String namespace, String predicateAttribute, String predicateValue) {

        boolean isAttribute() {
            return name.startsWith("@");
        }

        boolean isGap() {
            return GAP.equals(name);
        }
    }


    // --------------------------------------------------------------------------------


    // Where a template or for-each is: its steps, and whether they start at the document's root.
    private record Context(List<Step> steps, boolean absolute) {

        static Context of(final Parsed parsed) {
            return new Context(parsed.steps(), parsed.absolute());
        }
    }


    // --------------------------------------------------------------------------------


    // A path's steps, and whether it starts at the document's root.
    private record Parsed(List<Step> steps, boolean absolute) {

    }


    // --------------------------------------------------------------------------------


    // A value the XSLT takes: the path to it, whether that starts at the root, and whether it is put in a Kafka
    // record's value.
    private record Read(List<Step> path, boolean absolute, boolean inValue) {

    }


    // --------------------------------------------------------------------------------


    // The walk over the XSLT's templates: what it takes, and the paths from the root it selects.
    private static final class Walk {

        private final String defaultNamespace;
        private final List<Read> reads = new ArrayList<>();
        private final Set<List<String>> absolutes = new LinkedHashSet<>();

        private Walk(final String defaultNamespace) {
            this.defaultNamespace = defaultNamespace;
        }

        private void body(final Element element, final Context context, final boolean inValue) {
            for (final Element child : XmlTrees.children(element)) {
                if (XmlTrees.isXsl(child, "for-each") || XmlTrees.isXsl(child, "for-each-group")) {
                    final Context inner = resolved(context, child.getAttribute("select"), child);
                    noteAbsolute(inner);
                    body(child, inner, inValue);
                } else if (XmlTrees.isXsl(child, "apply-templates")) {
                    if (child.hasAttribute("select")) {
                        noteAbsolute(resolved(context, child.getAttribute("select"), child));
                    }
                    body(child, context, inValue);
                } else if (XmlTrees.isXsl(child, "value-of") || XmlTrees.isXsl(child, "sequence")
                           || XmlTrees.isXsl(child, "copy-of") || XmlTrees.isXsl(child, "attribute")) {
                    readsIn(child.getAttribute("select"), context, child, inValue);
                    body(child, context, inValue);
                } else if (XmlTrees.isXsl(child, "variable") || XmlTrees.isXsl(child, "param")) {
                    readsIn(child.getAttribute("select"), context, child, false);
                    body(child, context, false);
                } else if (XmlTrees.isXsl(child)) {
                    body(child, context, inValue);
                } else {
                    for (final org.w3c.dom.Attr attribute : XmlTrees.attributes(child)) {
                        final Matcher avt = AVT.matcher(attribute.getValue());
                        while (avt.find()) {
                            readsIn(avt.group(1), context, child, inValue);
                        }
                    }
                    body(child, context, inValue || "value".equals(child.getLocalName()));
                }
            }
        }

        private void readsIn(final String expression, final Context context, final Element at,
                             final boolean inValue) {
            if (NullSafe.isBlankString(expression)) {
                return;
            }
            final String plain = ATTRIBUTE_AXIS.matcher(AXIS.matcher(withoutLiterals(expression)).replaceAll(""))
                    .replaceAll("@");
            final Matcher path = PATH.matcher(plain);
            while (path.find()) {
                final String found = path.group(1);
                if (WORDS.contains(found) || "/".equals(found)) {
                    continue;
                }
                final Context resolved = resolved(context, found, at);
                reads.add(new Read(resolved.steps(), resolved.absolute(), inValue));
            }
        }

        private void noteAbsolute(final Context context) {
            if (context.absolute() && context.steps().stream().noneMatch(step -> step.isGap()
                                                                                 || step.isAttribute())) {
                final List<String> names = context.steps().stream().map(Step::name).toList();
                if (!names.isEmpty()) {
                    absolutes.add(names);
                }
            }
        }

        private Context resolved(final Context context, final String expression, final Element at) {
            if (NullSafe.isBlankString(expression)) {
                return context;
            }
            final Parsed parsed = steps(expression.strip(), at);
            if (parsed.absolute()) {
                return new Context(parsed.steps(), true);
            }
            final List<Step> out = new ArrayList<>(context.steps());
            for (final Step step : parsed.steps()) {
                if ("..".equals(step.name())) {
                    if (!out.isEmpty()) {
                        out.removeLast();
                    }
                } else {
                    out.add(step);
                }
            }
            return new Context(out, context.absolute());
        }

        // A path or pattern's steps; one that doesn't start at the root (a pattern matching anywhere) starts with
        // a gap.
        private Parsed steps(final String expression, final Element at) {
            final String text = ATTRIBUTE_AXIS.matcher(AXIS.matcher(expression.strip()).replaceAll(""))
                    .replaceAll("@");
            if ("/".equals(text)) {
                return new Parsed(List.of(), true);
            }
            final boolean absolute = text.startsWith("/");
            final List<Step> out = new ArrayList<>();
            final List<String> parts = splitSteps(text);
            for (int i = 0; i < parts.size(); i++) {
                final String part = parts.get(i);
                if (part.isEmpty()) {
                    // From '//' (or the root's '/').
                    if (i > 0 || text.startsWith("//")) {
                        out.add(new Step(GAP, null, null, null));
                    }
                    continue;
                }
                if (".".equals(part) || "text()".equals(part) || "node()".equals(part)) {
                    continue;
                }
                if ("..".equals(part)) {
                    out.add(new Step("..", null, null, null));
                    continue;
                }
                if (part.startsWith("@")) {
                    out.add(new Step("@" + localName(part.substring(1)), null, null, null));
                    continue;
                }
                final int bracket = part.indexOf('[');
                final String qualified = bracket < 0
                        ? part
                        : part.substring(0, bracket);
                String testedAttribute = null;
                String testedValue = null;
                if (bracket >= 0) {
                    final Matcher predicate = PREDICATE.matcher(part.substring(bracket));
                    if (predicate.find()) {
                        testedAttribute = localName(predicate.group(1));
                        testedValue = predicate.group(2) != null
                                ? predicate.group(2)
                                : predicate.group(3);
                    }
                }
                out.add(new Step(localName(qualified), namespaceOf(qualified, at), testedAttribute, testedValue));
            }
            if (!absolute && !text.startsWith(".") && at != null && XmlTrees.isXsl(at, "template")) {
                // A pattern: it matches wherever the element is.
                out.addFirst(new Step(GAP, null, null, null));
            }
            return new Parsed(out, absolute);
        }

        private String namespaceOf(final String qualified, final Element at) {
            final int colon = qualified.indexOf(':');
            if (colon < 0) {
                return defaultNamespace;
            }
            final String prefix = qualified.substring(0, colon);
            if ("*".equals(prefix)) {
                return defaultNamespace;
            }
            return NullSafe.string(XmlTrees.namespaces(at).get(prefix));
        }

        private static String localName(final String qualified) {
            return qualified.substring(qualified.indexOf(':') + 1);
        }

        // A path's steps split at the slashes outside predicates; a leading slash gives an empty first step.
        private static List<String> splitSteps(final String text) {
            final List<String> out = new ArrayList<>();
            int depth = 0;
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                final char c = text.charAt(i);
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                } else if (c == '/' && depth == 0) {
                    out.add(text.substring(start, i).strip());
                    start = i + 1;
                }
            }
            out.add(text.substring(start).strip());
            return out;
        }
    }
}
