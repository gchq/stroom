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

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.validation.XmlTrees;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// Translations written by hand, laid out as the reader reads the generator's (asked for by the user, 2026-11-24: a
/// build may start from an existing pipeline, hand-written or a content pack's, its XSLT read back into a mapping).
///
/// The reader ([XsltReader]) reads a start template applying a mode to the records, and a record template choosing
/// a rule per record, each writing one Event. Hand-written translations are laid out in other ways, which this turns
/// into that one before the reading, changing what is read, never what the XSLT does:
///
/// - **a template per event**: the start template applies templates (no mode) to the records, and each event has a
///   template of its own matching a record by a pattern (`map[string[@key='action'] = 'login']`): each template is a
///   rule, its pattern's predicates its condition, in the order XSLT picks them (priority, then the last first); a
///   template matching every record is the last rule, and an empty one drops what reaches it;
/// - **one template per record** with no mode: its body is the record template;
/// - **an xsl:choose inside the Event** (on an event id, say, inside EventDetail): each branch is a rule writing the
///   Event with that branch in the choose's place; a choose inside a branch is taken in turn, its tests joined;
/// - **a chain of xsl:if**, each writing an Event: each a rule, in turn (records two tests both hold for wrote two
///   Events; the rebuild's proof shows them);
/// - **JSON keys read by type** (`string[@key='x']`, as JSON input is usually read by hand): read as any key
///   (`*[@key='x']`), as the generator reads them.
///
/// Each rule is named by the TypeId it writes, where it is a constant, else by its place (`rule-3`, `other` for the
/// last without a test). An XSLT the generator wrote (its start template applies a mode) is left as it is.
final class HandLayouts {

    /// How every note of a hand-written layout starts: [RebuiltMapping#summary()] gives them apart from what wasn't
    /// read.
    static final String HAND = "written by hand: ";

    private static final String XSL = XmlTrees.XSL;
    private static final String EVT = XsltGenerator.EVT;
    private static final String JSON = "http://www.w3.org/2013/XSL/json";
    // A JSON key read by its type, as hand-written translations read JSON: string[@key='x'], map[@key='x'] ...
    private static final Pattern TYPED_KEY = Pattern.compile(
            "(?<![\\w:@$.\\-])(?:string|number|boolean|map|array|null)\\[\\s*@key\\s*=");
    // The XSLT attributes holding XPath, rewritten for JSON keys read by type.
    private static final Set<String> XPATH_ATTRIBUTES = Set.of("select", "test", "match", "group-by");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    // Tests of a value being there (spaces taken out), its expression the first group.
    private static final List<Pattern> PRESENCE = List.of(
            Pattern.compile("(.+?)(?:!=|ne)''"),
            Pattern.compile("(?:string|normalize-space)\\((.+)\\)(?:!=|ne)''"),
            Pattern.compile("(?:normalize-space|exists|string|boolean)\\((.+)\\)"),
            Pattern.compile("string-length\\((.+)\\)(?:>|gt|!=|ne)0"),
            Pattern.compile("not\\((.+?)(?:=|eq)''\\)"),
            Pattern.compile("(.+)\\[normalize-space\\(\\.\\)\\]"),
            Pattern.compile("(.+)"));
    private static final Pattern NOT_A_NAME = Pattern.compile("[^a-z0-9]+");
    // The most rules a layout is turned into: chooses inside chooses multiply.
    private static final int MOST_RULES = 400;
    private static final String RULE_MODE = "hand_rule_";

    private HandLayouts() {
        // Static only.
    }

    /// Lays a hand-written translation out as the generator does, in place.
    ///
    /// @param root The XSLT's root element, as parsed.
    /// @return What was done, for the reading's notes; empty when the XSLT is already laid out as the generator's (or
    /// has nothing to lay out).
    static List<String> layOut(final XmlElement root) {
        final List<XmlElement> matching = root.childElements(XSL, "template").stream()
                .filter(template -> has(template.get("match")) && !has(template.get("mode")))
                .toList();
        final XmlElement start = matching.stream()
                .filter(template -> !template.iterElements(EVT, "Events").isEmpty()
                                    && descendant(template, "apply-templates") != null)
                .findFirst()
                .orElse(matching.isEmpty()
                        ? null
                        : matching.getFirst());
        if (start == null) {
            return List.of();
        }
        final XmlElement apply = descendant(start, "apply-templates");
        if (apply == null || has(apply.get("mode"))) {
            return List.of();
        }
        final List<String> notes = new ArrayList<>();
        if (JSON.equals(root.get("xpath-default-namespace")) && anyKeyByType(root)) {
            untypeKeys(root);
            notes.add(HAND + "JSON keys read by type (string[@key='x']) read as any key (*[@key='x'])");
        }
        final String select = apply.get("select");
        final String recordName = has(select)
                ? lastStepName(select)
                : null;
        // Templates applied from inside another (a helper writing part of an event) are no record's.
        final Set<String> helpers = new LinkedHashSet<>();
        for (final XmlElement template : matching) {
            if (template != start) {
                template.iterElements(XSL, "apply-templates").stream()
                        .map(applied -> applied.get("select"))
                        .filter(HandLayouts::has)
                        .map(applied -> SPACES.matcher(lastStep(applied)).replaceAll(""))
                        .forEach(helpers::add);
            }
        }
        final List<Candidate> candidates = new ArrayList<>();
        int order = 0;
        for (final XmlElement template : matching) {
            final String step = SPACES.matcher(lastStep(template.get("match"))).replaceAll("");
            if (template == start || helpers.contains(step)) {
                continue;
            }
            final MatchStep pattern = MatchStep.of(template.get("match"));
            if (pattern == null || "text()".equals(pattern.name()) || pattern.name().startsWith("@")
                || (recordName != null && !"*".equals(recordName) && !"node()".equals(recordName)
                    && !recordName.equals(pattern.name()) && !"*".equals(pattern.name()))) {
                continue;
            }
            candidates.add(new Candidate(template, pattern, priority(template, pattern), order++));
        }
        if (candidates.isEmpty()) {
            return notes;
        }
        // As XSLT picks a template: the highest priority, then the last of equal priority.
        candidates.sort(Comparator.comparingDouble(Candidate::priority).reversed()
                .thenComparing(Comparator.comparingInt(Candidate::order).reversed()));
        final XmlElement record = XmlElement.create(XSL, "template");
        root.insert(root.indexOf(start) + 1, record);
        record.set("match", "*");
        record.set("mode", "event");
        final List<Branch> branches = new ArrayList<>();
        for (final Candidate candidate : candidates) {
            branches.add(new Branch(candidate.pattern().test(), candidate.template().children()));
            if (candidate.pattern().test() == null) {
                // Every record reaches it: the templates after it are never chosen.
                break;
            }
        }
        notes.add(candidates.size() > 1
                ? HAND + "a template per event (match patterns), read as one rule each in the order XSLT chooses "
                  + "them"
                : HAND + "the records' template (applied with no mode) read as the record template");
        final Rules rules = new Rules(root, notes, record);
        if (branches.size() == 1 && branches.getFirst().test() == null) {
            rules.flattenInto(record, branches.getFirst().body(), null);
        } else {
            final XmlElement choose = record.addElement(XSL, "choose");
            for (final Branch branch : branches) {
                rules.addBranch(choose, branch.test(), branch.body());
            }
        }
        rules.writeRuleTemplates();
        apply.set("mode", "event");
        if (!has(select)) {
            final String name = candidates.getFirst().pattern().name();
            apply.set("select", "*".equals(name)
                    ? "*"
                    : name);
        }
        candidates.forEach(candidate -> root.remove(candidate.template()));
        return notes;
    }

    /// Whether an xsl:if around a leaf written by hand tests more than its value being there: `$user != '-'` does;
    /// `x != ''`, `normalize-space(x)`, `exists(x)` and `string-length(x) > 0` of what the leaf writes don't, as the
    /// generator leaves an empty value out anyway.
    ///
    /// @param guard The test of the innermost xsl:if around the leaf, resolved, or null.
    /// @param expr  What the leaf writes, resolved, or null (a constant).
    /// @return True when the test says more than that.
    static boolean conditionalGuard(final String guard, final String expr) {
        if (guard == null || guard.isBlank()) {
            return false;
        }
        final String test = SPACES.matcher(guard).replaceAll("");
        final String written = expr == null
                ? ""
                : SPACES.matcher(expr).replaceAll("");
        for (final Pattern presence : PRESENCE) {
            final java.util.regex.Matcher matcher = presence.matcher(test);
            if (matcher.matches() && !matcher.group(1).isEmpty() && written.contains(matcher.group(1))) {
                return false;
            }
        }
        return true;
    }

    // --- patterns -----------------------------------------------------------------------------------------------

    private static double priority(final XmlElement template, final MatchStep pattern) {
        final String given = template.get("priority");
        if (has(given)) {
            try {
                return Double.parseDouble(given.strip());
            } catch (final NumberFormatException e) {
                // As XSLT would refuse it, a default.
                return 0;
            }
        }
        if (pattern.test() != null || pattern.steps() > 1) {
            return 0.5;
        }
        return "*".equals(pattern.name()) || "node()".equals(pattern.name())
                ? -0.5
                : 0;
    }

    // An expression's last step, with its predicates (`array/map[x]` gives `map[x]`).
    private static String lastStep(final String path) {
        final List<String> steps = topLevelSplit(path.strip(), '/');
        return steps.isEmpty()
                ? path
                : steps.getLast();
    }

    // The name of an expression's last step, without its predicates (`array/map` gives `map`).
    private static String lastStepName(final String path) {
        final String last = lastStep(path);
        final int bracket = last.indexOf('[');
        return (bracket < 0
                ? last
                : last.substring(0, bracket)).strip();
    }

    // The text split on a character outside brackets, parentheses and quotes.
    private static List<String> topLevelSplit(final String text, final char at) {
        final List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int from = 0;
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
            } else if (c == at && depth == 0) {
                parts.add(text.substring(from, i));
                from = i + 1;
            }
        }
        parts.add(text.substring(from));
        return parts;
    }

    // --- JSON keys by type ----------------------------------------------------------------------------------------

    private static boolean anyKeyByType(final XmlElement root) {
        for (final XmlElement element : root.iterElements()) {
            for (final XmlElement.Attribute attribute : element.attributes()) {
                if (attribute.namespace() == null && TYPED_KEY.matcher(attribute.value()).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void untypeKeys(final XmlElement root) {
        for (final XmlElement element : root.iterElements()) {
            final boolean xsl = XSL.equals(element.namespace());
            for (final XmlElement.Attribute attribute : List.copyOf(element.attributes())) {
                if (attribute.namespace() != null) {
                    continue;
                }
                // An XSLT instruction's XPath attributes, and the {...} of a literal result element's attributes.
                if ((xsl && XPATH_ATTRIBUTES.contains(attribute.name()))
                    || (!xsl && attribute.value().contains("{"))) {
                    element.set(attribute.name(), TYPED_KEY.matcher(attribute.value()).replaceAll("*[@key="));
                }
            }
        }
    }

    // --- copying --------------------------------------------------------------------------------------------------

    // The nodes copied into the target, the one to replace (if met) by the replacement's nodes.
    private static void copyInto(final XmlElement target,
                                 final List<XmlNode> nodes,
                                 final XmlElement replaced,
                                 final List<XmlNode> replacement) {
        for (final XmlNode node : nodes) {
            if (node == replaced) {
                copyInto(target, replacement, null, List.of());
                continue;
            }
            if (node instanceof final XmlElement element) {
                final XmlElement copy = target.addElement(element.namespace(), element.localName());
                for (final XmlElement.Attribute attribute : element.attributes()) {
                    copy.set(attribute.namespace(), attribute.name(), attribute.value());
                }
                copy.setText(element.text());
                copy.setTail(element.tail());
                copyInto(copy, element.children(), replaced, replacement);
            } else if (node instanceof final XmlComment comment) {
                final XmlComment copy = new XmlComment(comment.text());
                copy.setTail(comment.tail());
                target.append(copy);
            }
        }
    }

    // --- helpers --------------------------------------------------------------------------------------------------

    private static XmlElement descendant(final XmlElement parent, final String xslName) {
        return parent.iterElements(XSL, xslName).stream()
                .filter(element -> element != parent)
                .findFirst()
                .orElse(null);
    }

    private static boolean has(final String text) {
        return text != null && !text.isBlank();
    }

    private static List<XmlElement> elementsOf(final List<XmlNode> nodes) {
        final List<XmlElement> out = new ArrayList<>();
        for (final XmlNode node : nodes) {
            if (node instanceof final XmlElement element) {
                out.add(element);
            }
        }
        return out;
    }

    private static boolean isVariable(final XmlElement element) {
        return element.is(XSL, "variable") || element.is(XSL, "param");
    }

    // Whether a choose's branches write events (each an Event, or elements of one).
    private static boolean writesEventElements(final XmlElement choose) {
        return choose.childElements().stream()
                .anyMatch(branch -> branch.iterElements().stream()
                        .anyMatch(element -> EVT.equals(element.namespace())));
    }

    private static String and(final String outer, final String inner) {
        if (outer == null) {
            return inner;
        }
        if (inner == null) {
            return outer;
        }
        return "(" + outer + ") and (" + inner + ")";
    }


    // --------------------------------------------------------------------------------


    // The rules being laid out: each a branch of the record template's choose, calling a template of its own.
    private static final class Rules {

        private final XmlElement root;
        private final List<String> notes;
        private final List<XmlElement> ruleTemplates = new ArrayList<>();
        private final List<XmlComment> headers = new ArrayList<>();
        private final Set<String> names = new LinkedHashSet<>();
        private final XmlElement record;
        private boolean hoisted;
        private boolean chained;

        private Rules(final XmlElement root, final List<String> notes, final XmlElement record) {
            this.root = root;
            this.notes = notes;
            this.record = record;
        }

        // The body laid out into the target (the record template, or a branch): its variables kept where they are,
        // then its events as rules.
        private void flattenInto(final XmlElement target, final List<XmlNode> body, final String test) {
            final List<XmlElement> elements = elementsOf(body);
            final List<XmlElement> rest = elements.stream().filter(element -> !isVariable(element)).toList();
            final List<XmlElement> events = rest.stream().filter(element -> element.is(EVT, "Event")).toList();
            final boolean ifChain = !rest.isEmpty() && rest.stream().allMatch(element -> element.is(XSL, "if")
                    && !element.childElements(EVT, "Event").isEmpty());
            if (ifChain) {
                copyVariables(target, body);
                chained = true;
                final XmlElement choose = target.addElement(XSL, "choose");
                for (final XmlElement branch : rest) {
                    addBranch(choose, and(test, branch.get("test")), branch.children());
                }
                return;
            }
            if (rest.size() == 1 && rest.getFirst().is(XSL, "choose")) {
                copyVariables(target, body);
                final XmlElement choose = target.addElement(XSL, "choose");
                addChoose(choose, test, rest.getFirst());
                return;
            }
            if (events.size() == 1 && rest.size() == 1) {
                final XmlElement inner = innerChoose(events.getFirst());
                if (inner != null && ruleTemplates.size() + inner.childElements().size() <= MOST_RULES) {
                    hoisted = true;
                    copyVariables(target, body);
                    final XmlElement choose = target.addElement(XSL, "choose");
                    for (final XmlElement branch : inner.childElements()) {
                        final XmlElement holder = XmlElement.create(XSL, "sequence");
                        copyInto(holder, body.stream().filter(node -> !(node instanceof final XmlElement e
                                                                        && isVariable(e))).toList(),
                                inner, branch.children());
                        addBranch(choose, and(test, branch.is(XSL, "when")
                                ? branch.get("test")
                                : null), holder.children());
                    }
                    return;
                }
            }
            rule(target, body, test);
        }

        // A branch of the record template's choose: its body laid out (a choose or chain inside it taken in turn).
        private void addBranch(final XmlElement choose, final String test, final List<XmlNode> body) {
            final List<XmlElement> rest = elementsOf(body).stream().filter(element -> !isVariable(element)).toList();
            final boolean nested = rest.size() == 1 && rest.getFirst().is(XSL, "choose")
                                   && writesEventElements(rest.getFirst());
            final boolean inner = rest.size() == 1 && rest.getFirst().is(EVT, "Event")
                                  && innerChoose(rest.getFirst()) != null;
            final boolean chain = !rest.isEmpty() && rest.stream().allMatch(element -> element.is(XSL, "if")
                    && !element.childElements(EVT, "Event").isEmpty());
            if ((nested || inner || chain) && ruleTemplates.size() < MOST_RULES) {
                // Laid out in a holder, then its choose's branches taken into this one, the tests joined.
                final XmlElement holder = XmlElement.create(XSL, "template");
                flattenInto(holder, body, test);
                for (final XmlElement element : List.copyOf(holder.childElements())) {
                    if (isVariable(element) && record != null) {
                        // Read in the record template's scope, as the reader reads variables.
                        record.insert(0, element);
                    } else if (element.is(XSL, "choose")) {
                        for (final XmlElement branch : List.copyOf(element.childElements())) {
                            choose.append(branch);
                        }
                    }
                }
                return;
            }
            final XmlElement branch = choose.addElement(XSL, test == null
                    ? "otherwise"
                    : "when");
            if (test != null) {
                branch.set("test", test);
            }
            rule(branch, body, test);
        }

        // A choose's branches added to the record template's, the tests joined with the outer test.
        private void addChoose(final XmlElement choose, final String test, final XmlElement inner) {
            for (final XmlElement branch : inner.childElements()) {
                final String own = branch.is(XSL, "when")
                        ? branch.get("test")
                        : null;
                addBranch(choose, and(test, own), branch.children());
            }
            if (inner.childElements(XSL, "otherwise").isEmpty() && test != null) {
                // A record no branch takes writes nothing: dropped, so later rules don't take it.
                choose.addElement(XSL, "when", "test", test).append(dropped());
            }
        }

        // One rule: its body in a template of its own, called from the target.
        private void rule(final XmlElement target, final List<XmlNode> body, final String test) {
            final List<XmlElement> elements = elementsOf(body);
            if (elements.stream().noneMatch(element -> !isVariable(element))) {
                // An empty template, or a branch writing nothing: the records it takes are dropped, as a drop rule
                // where it has a test (else later rules would take them).
                if (test != null) {
                    target.append(dropped());
                }
                return;
            }
            final String name = ruleName(elements, test);
            final XmlElement template = XmlElement.create(XSL, "template");
            template.set("match", "node()");
            template.set("mode", RULE_MODE + name.replace('-', '_'));
            copyInto(template, body, null, List.of());
            ruleTemplates.add(template);
            target.addElement(XSL, "apply-templates", "select", ".", "mode", RULE_MODE + name.replace('-', '_'));
            headers.add(new XmlComment(" Rule '" + name + "': written by hand "));
        }

        // A drop rule's comment, as the generator writes one for a kind left untranslated.
        private XmlComment dropped() {
            return new XmlComment(" " + unique("dropped") + ": left untranslated on purpose ");
        }

        // The rule templates after the record template, each with the header naming its rule.
        private void writeRuleTemplates() {
            int at = root.indexOf(record) + 1;
            for (int i = 0; i < ruleTemplates.size(); i++) {
                root.insert(at++, headers.get(i));
                root.insert(at++, ruleTemplates.get(i));
            }
            if (hoisted) {
                notes.add(HAND + "an xsl:choose inside the event, read as one rule per branch");
            }
            if (chained) {
                notes.add(HAND + "a chain of xsl:if, each writing an event, read as one rule each in turn");
            }
        }

        // A rule's name: the TypeId it writes where that is a constant, else its place.
        private String ruleName(final List<XmlElement> elements, final String test) {
            String name = null;
            for (final XmlElement element : elements) {
                for (final XmlElement typeId : element.iterElements(EVT, "TypeId")) {
                    final String text = ToolText.strip(Objects.requireNonNullElse(typeId.text(), ""));
                    if (!text.isEmpty() && typeId.childElements().isEmpty()) {
                        name = text;
                        break;
                    }
                }
                if (name != null) {
                    break;
                }
            }
            String base = name == null
                    ? test == null
                            ? "other"
                            : "rule-" + (ruleTemplates.size() + 1)
                    : NOT_A_NAME.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("^-|-$", "");
            if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
                base = "type-" + base;
            }
            return unique(base);
        }

        // The name, or the name with the next number after it where a rule has it already.
        private String unique(final String base) {
            String unique = base;
            for (int n = 2; !names.add(unique); n++) {
                unique = base + "-" + n;
            }
            return unique;
        }

        private static void copyVariables(final XmlElement target, final List<XmlNode> body) {
            copyInto(target, body.stream()
                    .filter(node -> node instanceof final XmlElement element && isVariable(element))
                    .toList(), null, List.of());
        }

        // The first choose inside an Event whose branches write its elements (not one inside a loop).
        private static XmlElement innerChoose(final XmlElement event) {
            for (final XmlElement element : event.iterElements(XSL, "choose")) {
                final boolean inLoop = element.ancestors().stream()
                        .takeWhile(ancestor -> ancestor != event)
                        .anyMatch(ancestor -> ancestor.is(XSL, "for-each") || ancestor.is(XSL, "choose"));
                if (!inLoop && writesEventElements(element)) {
                    return element;
                }
            }
            return null;
        }
    }

    // A template that may take a record, as XSLT orders them.
    private record Candidate(XmlElement template, MatchStep pattern, double priority, int order) {

    }

    // A branch to lay out: its test (null for every record) and its body.
    private record Branch(String test, List<XmlNode> body) {

    }

    // A match pattern's last step: its name, its predicates joined as a test (null for none), and how many steps.
    private record MatchStep(String name, String test, int steps) {

        private static MatchStep of(final String match) {
            if (match == null || match.contains("|")) {
                return null;
            }
            final List<String> steps = topLevelSplit(match.strip(), '/').stream()
                    .filter(step -> !step.isBlank())
                    .toList();
            if (steps.isEmpty()) {
                return null;
            }
            final String last = steps.getLast().strip();
            final int bracket = last.indexOf('[');
            final String name = (bracket < 0
                    ? last
                    : last.substring(0, bracket)).strip();
            final List<String> predicates = new ArrayList<>();
            if (bracket >= 0) {
                int depth = 0;
                char quote = 0;
                int from = -1;
                for (int i = bracket; i < last.length(); i++) {
                    final char c = last.charAt(i);
                    if (quote != 0) {
                        if (c == quote) {
                            quote = 0;
                        }
                    } else if (c == '\'' || c == '"') {
                        quote = c;
                    } else if (c == '[') {
                        if (depth == 0) {
                            from = i + 1;
                        }
                        depth++;
                    } else if (c == ']') {
                        depth--;
                        if (depth == 0 && from >= 0) {
                            predicates.add(last.substring(from, i).strip());
                        }
                    }
                }
            }
            final String test = predicates.isEmpty()
                    ? null
                    : predicates.size() == 1
                            ? predicates.getFirst()
                            : "(" + String.join(") and (", predicates) + ")";
            return new MatchStep(name, test, steps.size());
        }
    }
}
