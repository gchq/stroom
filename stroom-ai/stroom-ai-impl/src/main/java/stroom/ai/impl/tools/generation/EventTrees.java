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

import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.EventSchema.Child;
import stroom.ai.impl.tools.validation.XsltSafety;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// The checks [XsltGenerator] makes of a mapping, and the tree of elements each rule's events are written with, as
/// the Stroom MCP server's generator makes them (`utils/xsltgen.py`): mistakes the schema can catch (unknown paths,
/// invalid constants, two alternatives of a choice, required elements left out) come back as problems per mapping
/// entry, and what may be wrong as warnings, worded as the server words them.
final class EventTrees {

    /// Example child paths for elements agents found hard to fill: (parent, element) -> paths below the element.
    static final Map<List<String>, List<String>> SHAPES = shapes();
    private static final Pattern LOOKAROUND = Pattern.compile("\\(\\?(P?<[A-Za-z_]|<?[=!])");

    private final XsltGenerator generator;
    private final TranslationMapping m;
    private final EventSchema schema;
    private final List<String> problems;
    private final List<String> warnings;
    private List<String> conditional = new ArrayList<>();
    // Each rule's required elements written only when their inputs have a value.
    private final Map<String, List<String>> conditionalByRule = new LinkedHashMap<>();
    private EventNode root;

    /// @param generator The generator writing the XSLT, whose reading of inputs the checks use.
    /// @param mapping   The mapping.
    /// @param schema    The event-logging schema.
    /// @param problems  Where problems are noted.
    /// @param warnings  Where warnings are noted.
    EventTrees(final XsltGenerator generator,
               final TranslationMapping mapping,
               final EventSchema schema,
               final List<String> problems,
               final List<String> warnings) {
        this.generator = generator;
        this.m = mapping;
        this.schema = schema;
        this.problems = problems;
        this.warnings = warnings;
    }

    private static void note(final List<String> bucket, final String message) {
        if (!bucket.contains(message)) {
            bucket.add(message);
        }
    }

    private static boolean isSet(final String text) {
        return text != null && !text.isEmpty();
    }

    private static String or(final String value, final String otherwise) {
        return value == null || value.isEmpty()
                ? otherwise
                : value;
    }

    private static boolean hasAnyOf(final FieldMapping entry) {
        return entry.anyOf() != null && !entry.anyOf().isEmpty();
    }

    // --- checks against the schema ---

    private void checkLeaf(final String where, final Child child, final FieldMapping entry) {
        final List<String> allowed = schema.enumeration(child.decl());
        final String kind = schema.baseType(child.decl());
        final List<String> outputs = new ArrayList<>();
        if (entry.value() != null) {
            outputs.add(entry.value());
        } else {
            if (entry.map() != null) {
                outputs.addAll(entry.map().values());
            }
            if (entry.defaultValue() != null) {
                outputs.add(entry.defaultValue());
            }
        }
        if (allowed != null && !allowed.isEmpty()) {
            final List<String> bad = outputs.stream().filter(value -> !allowed.contains(value)).toList();
            if (!bad.isEmpty()) {
                note(problems, where + ": " + PyText.list(bad) + " not allowed; " + child.name()
                               + " takes one of " + PyText.list(allowed));
            } else if (entry.value() == null && !entry.hasMap()) {
                note(warnings, where + ": " + child.name() + " only takes " + PyText.list(allowed)
                               + "; the input value is written as it is, so add a map unless it already uses these "
                               + "values");
            }
        }
        if ("boolean".equals(kind)) {
            final List<String> bad = outputs.stream()
                    .filter(value -> !"true".equals(value) && !"false".equals(value))
                    .toList();
            if (!bad.isEmpty()) {
                note(problems, where + ": " + child.name() + " is true or false, not " + PyText.list(bad));
            } else if (entry.value() == null && !entry.hasMap() && generator.sourceOf(entry).xpath() == null) {
                note(warnings, where + ": " + child.name() + " is true/false; map the input values, e.g. {'ok': "
                               + "'true', 'fail': 'false'}, unless they already are");
            }
        }
        if ("dateTime".equals(kind) && entry.value() == null && !isSet(entry.timeFormat())) {
            note(warnings, where + ": " + child.name() + " is a date-time; give time_format (from profile_sample) "
                           + "unless the input is already yyyy-MM-ddTHH:mm:ss.SSSZ");
        }
        if (isSet(entry.timeFormat()) && !XPathText.EPOCH_FORMATS.contains(entry.timeFormat())) {
            final String problem = MappingFacts.patternProblem(entry.timeFormat());
            if (problem != null) {
                note(problems, where + ": " + problem);
            }
        }
        if ((isSet(entry.timeFormat()) || isSet(entry.timezone())) && entry.value() != null) {
            note(problems, where + ": time_format needs a field or xpath, not a constant");
        }
        if (isSet(entry.timeFormat()) && entry.hasMap()) {
            note(problems, where + ": use time_format or map, not both");
        }
    }

    // For a value given an element the schema hasn't got (Network/Protocol, Alert/AlertSeverity): it can be carried
    // as a Data entry of the action element, Name and Value, rather than invented or left Unknown.
    private String asData(final String path, final String dataName, final FieldMapping entry, final EventRule rule) {
        final List<String> steps = PyText.split(PyText.strip(path, "/"), "/");
        if (dataName != null || steps.size() < 3 || !"EventDetail".equals(steps.get(0))
            || "Unknown".equals(steps.get(1))) {
            return "";
        }
        // The action the rule already maps below the element (Network/Permit), where its Data goes.
        final List<String> siblings = new ArrayList<>();
        for (final FieldMapping field : rule.fields()) {
            final List<String> mapped = PyText.split(field.strippedPath(), "/");
            if (mapped.subList(0, Math.min(2, mapped.size())).equals(steps.subList(0, 2)) && mapped.size() > 3) {
                siblings.add(String.join("/", mapped.subList(0, 3)) + "/Data");
            }
        }
        // The nearest element above it that takes Data (a destination_key under Destination), then the action the
        // rule maps, then the element itself.
        final List<String> candidates = new ArrayList<>();
        // A path through a Data element (Network/Data/Source/...: Network's action may be Data itself) carries the
        // value as that Data, not a Data inside it. Seen in testing: Network/Data/Data suggested, then refused for a
        // data list.
        for (int k = 3; k < steps.size(); k++) {
            if ("Data".equals(steps.get(k - 1))) {
                candidates.add(String.join("/", steps.subList(0, k)));
            }
        }
        for (int k = steps.size() - 1; k > 2; k--) {
            candidates.add(String.join("/", steps.subList(0, k)) + "/Data");
        }
        candidates.addAll(siblings);
        candidates.add(String.join("/", steps.subList(0, 2)) + "/Data");
        String home = null;
        for (final String candidate : candidates) {
            try {
                final List<Child> chain = schema.resolve(candidate);
                if (!chain.isEmpty() && "Data".equals(chain.getLast().name())) {
                    home = candidate;
                    break;
                }
            } catch (final IllegalArgumentException e) {
                // Not a path the schema has.
            }
        }
        final Map<String, Object> suggested = new LinkedHashMap<>();
        suggested.put("path", home != null
                ? home
                : "EventDetail/" + steps.get(1) + "/<the action>/Data");
        suggested.put("data_name", isSet(entry.field())
                ? entry.field()
                : steps.getLast());
        if (isSet(entry.field())) {
            suggested.put("field", entry.field());
        }
        if (isSet(entry.xpath())) {
            suggested.put("xpath", entry.xpath());
        }
        if (entry.value() != null) {
            suggested.put("value", entry.value());
        }
        return " If no element means this value, carry it as Data of the action element instead: "
               + PyJson.dumps(suggested) + " (Name and Value in the event, searchable as they are). A value with no "
               + "element of its own is no reason to leave the event Unknown.";
    }

    // TypeId is required by the schema. Read from an input with no default, it is left out of the events whose
    // records lack that input, and they fail validation: seen in production, Unknown events from the rule for the
    // rest, whose records didn't have the field the kinds were named by. Unless the rule's own conditions say the
    // input is there, its name is the fallback (a default of the user's choosing replaces it).
    private void typeIdFallback(final EventRule rule, final Map<List<String>, FieldMapping> byKey) {
        final List<String> key = Arrays.asList("EventDetail/TypeId", null);
        final FieldMapping entry = byKey.get(key);
        if (entry == null || rule.drop() || entry.value() != null || entry.defaultValue() != null) {
            return;
        }
        final Set<String> read = new TreeSet<>();
        if (entry.field() != null) {
            read.add(entry.field());
        }
        if (entry.anyOf() != null) {
            read.addAll(entry.anyOf());
        }
        if (entry.join() != null) {
            read.addAll(entry.join());
        }
        final Set<String> held = new HashSet<>();
        for (final Condition condition : rule.when()) {
            if (isSet(condition.field()) && !Boolean.FALSE.equals(condition.present())) {
                held.add(condition.field());
            }
        }
        if (read.stream().anyMatch(held::contains) && !entry.hasMap()) {
            return;
        }
        byKey.put(key, entry.withDefault(rule.name()));
        final String source = read.isEmpty()
                ? "its input"
                : String.join(" or ", read);
        note(warnings, "[" + rule.name() + "] TypeId is read from " + source + ", which its records may lack: those "
                       + "get TypeId '" + rule.name() + "', as the schema requires one (without it the event fails "
                       + "validation). Give TypeId a default to choose another.");
    }

    /// The tree of elements the rule's events are written with: its fields and the common ones, in the schema's
    /// places, each checked against the schema (problems and warnings noted).
    ///
    /// @param rule A rule.
    /// @return Its tree, from Event.
    EventNode tree(final EventRule rule) {
        final EventNode tree = new EventNode(null, "Event");
        final Map<List<String>, FieldMapping> byKey = new LinkedHashMap<>();
        for (final FieldMapping entry : m.common()) {
            byKey.put(Arrays.asList(entry.strippedPath(), entry.dataKey()), entry);
        }
        for (final FieldMapping entry : rule.fields()) {
            byKey.put(Arrays.asList(entry.strippedPath(), entry.dataKey()), entry);
        }
        typeIdFallback(rule, byKey);
        for (final Map.Entry<List<String>, FieldMapping> keyed : byKey.entrySet()) {
            final String path = keyed.getKey().get(0);
            final FieldMapping entry = keyed.getValue();
            final String dataName = entry.dataName();
            final String where = "[" + rule.name() + "] " + path + (isSet(dataName)
                    ? " (Data " + entry.dataKey() + ")"
                    : "");
            final List<Child> chain;
            try {
                chain = schema.resolve(path);
            } catch (final IllegalArgumentException e) {
                note(problems, "[" + rule.name() + "] " + path + ": " + e.getMessage() + asData(path, dataName, entry,
                        rule));
                continue;
            }
            if (chain.isEmpty()) {
                note(problems, where + ": map a path below Event");
                continue;
            }
            final Child last = chain.getLast();
            if (dataName != null) {
                if (!"Data".equals(last.name())) {
                    note(problems, where + ": data_name only goes with a path ending in Data");
                    continue;
                }
                final EventNode parent = walk(tree, chain.subList(0, chain.size() - 1), where);
                if (parent != null) {
                    parent.data.add(new EventNode.DataEntry(last, entry));
                }
                continue;
            }
            if ("Data".equals(last.name())) {
                note(problems, where + ": a Data path needs data_name (the Data element's Name)");
                continue;
            }
            if (!schema.isLeaf(last.decl())) {
                final List<String> options = schema.children(last.decl()).stream().map(Child::name).toList();
                final String parentName = chain.size() > 1
                        ? chain.get(chain.size() - 2).name()
                        : "";
                List<String> example = SHAPES.get(List.of(parentName, last.name()));
                if (example == null) {
                    example = SHAPES.get(List.of("", last.name()));
                }
                note(problems, where + ": " + last.name() + " holds other elements; map one of "
                               + PyText.list(options) + (example != null
                        ? ", e.g. " + example.stream().map(e -> path + "/" + e).collect(Collectors.joining(", "))
                        : ""));
                continue;
            }
            final EventNode node = walk(tree, chain, where);
            if (node == null) {
                continue;
            }
            if (!node.kids.isEmpty()) {
                note(problems, where + ": already has child elements mapped");
                continue;
            }
            node.leaf = entry;
            checkLeaf(where, last, entry);
            if (entry.repeat()) {
                int anchor = -1;
                for (int i = 0; i < chain.size(); i++) {
                    if (chain.get(i).repeatable()) {
                        anchor = i;
                    }
                }
                if (anchor < 0) {
                    note(problems, where + ": repeat needs an element on the path the schema lets repeat; none of "
                                   + PyText.list(chain.stream().map(Child::name).toList())
                                   + " may occur more than once");
                } else {
                    walk(tree, chain.subList(0, anchor + 1), where).repeat = entry;
                }
            }
        }
        for (final EventNode anchor : anchors(tree)) {
            final List<EventNode> leaves = leaves(anchor);
            if (leaves.size() > 1 || nodes(anchor).stream().anyMatch(n -> !n.data.isEmpty())) {
                final FieldMapping repeated = anchor.repeat;
                final Object input = isSet(repeated.field())
                        ? repeated.field()
                        : isSet(repeated.xpath())
                                ? repeated.xpath()
                                : repeated.anyOf();
                note(problems, "[" + rule.name() + "] " + anchor.path + " is written once per value of "
                               + (input instanceof String
                        ? input
                        : PyText.repr(input)) + "; map nothing else below it (found "
                               + PyText.list(leaves.stream().map(n -> n.path).toList()) + ")");
            }
        }
        for (final SharedTemplate use : m.shared()) {
            // The shared template writes the element: it is called in its place, and nothing below it is mapped,
            // which would write it twice (or a second element the schema allows once).
            final List<Child> chain;
            try {
                chain = schema.resolve(PyText.strip(use.at(), "/"));
            } catch (final IllegalArgumentException e) {
                note(problems, "shared " + use.template() + " (" + use.href() + "): at " + use.at() + ": "
                               + e.getMessage());
                continue;
            }
            final EventNode node = walk(tree, chain, "shared " + use.template());
            if (node == null) {
                continue;
            }
            final List<String> mapped = new ArrayList<>();
            for (final EventNode below : nodes(node)) {
                if (below != node && (below.leaf != null || !below.data.isEmpty())) {
                    mapped.add(below.path);
                }
            }
            if (node.leaf != null || !node.data.isEmpty()) {
                mapped.add(node.path);
            }
            if (!mapped.isEmpty() || node.shared != null) {
                note(problems, "[" + rule.name() + "] " + use.at() + " is written by the shared template "
                               + use.template() + " (" + use.href() + "), so it must not be mapped as well: it would "
                               + "be written twice, which fails schema validation. Remove "
                               + PyText.list(mapped.isEmpty()
                        ? List.of("the other shared template for it")
                        : mapped) + " from the mapping, or drop the shared template.");
                continue;
            }
            node.shared = use;
        }
        conditional = new ArrayList<>();
        root = tree;
        checkStructure(rule.name(), tree, schema.event());
        checkActions(rule, tree);
        return tree;
    }

    // What a rule's events say happened, and who did it.
    private void checkActions(final EventRule rule, final EventNode tree) {
        final EventNode detail = tree.kids.get("EventDetail");
        if (detail != null && isSet(rule.allowUnknown()) && !detail.kids.containsKey("Unknown")) {
            // Seen: a rule moved from Unknown to Alert kept its allow_unknown, and the user was asked to keep it
            // Unknown.
            note(warnings, "[" + rule.name() + "] allow_unknown is ignored: the rule writes "
                           + detail.kids.keySet().stream()
                                   .filter(k -> !"TypeId".equals(k) && !"Description".equals(k))
                                   .collect(Collectors.joining(", ")) + ", not Unknown. Remove it.");
        }
        if (detail != null && detail.kids.containsKey("Unknown") && rule.allowUnknown() != null
            && PyText.isBlank(rule.allowUnknown())) {
            note(problems, "[" + rule.name() + "] allow_unknown takes the reason no action element describes these "
                           + "records, as text (in the user's words), not true.");
        }
        if (!rule.when().isEmpty() && detail != null && detail.kids.containsKey("Unknown")
            && rule.allowUnknown() == null) {
            // A catch-all rule (no conditions) may rightly say Unknown; a kind told apart by conditions has an
            // action element nearly always, and the draft's Unknown placeholders were being kept as they were.
            note(problems, "[" + rule.name() + "] writes EventDetail/Unknown, which says what happened is not known, "
                           + "yet its conditions single these records out: use the action element that describes "
                           + "them, with its own child elements: connections allowed or denied Network/Permit or "
                           + "Network/Deny (Source and Destination with Device/IPAddress and Port), logons and "
                           + "logoffs Authenticate (Action, User/Id, Outcome/Success), configuration changes Update "
                           + "(After/Configuration/Type), alerts and health messages Alert (Type, Severity), "
                           + "processes started or stopped Process; also Create, Delete, View, Send, Receive. A "
                           + "schema problem in the action element is fixed from what it names, not avoided with "
                           + "Unknown, and a value no element means is carried as Data of the action element "
                           + "(EventDetail/<Action>/Data, data_name). allow_unknown (the reason, which the user "
                           + "confirms) is only for records no action element describes.");
        }
        final EventNode source = tree.kids.get("EventSource");
        final List<String> acted = new ArrayList<>();
        if (detail != null) {
            detail.kids.forEach((name, node) -> {
                if (node.kids.containsKey("User")) {
                    acted.add(name + "/User");
                }
            });
        }
        if (!acted.isEmpty() && (source == null || !source.kids.containsKey("User"))) {
            note(warnings, "[" + rule.name() + "] maps EventDetail/" + acted.getFirst() + " but not EventSource/User: "
                           + "EventSource/User/Id is the account that acted (for a logon, the account logging on), and "
                           + "searches by user read it. Map it as well, from the same input when it is the same "
                           + "account.");
        }
        conditionalByRule.put(rule.name(), List.copyOf(conditional));
        if (!conditional.isEmpty()) {
            note(warnings, "[" + rule.name() + "] required " + PyText.list(conditional) + " are left out when "
                           + "their input fields are empty, which makes the event invalid. Fine if those fields are "
                           + "always filled; otherwise give the mapping a default.");
        }
    }

    /// @return Each rule's required elements written only when their inputs have a value (paths below Event), by
    /// the rule's name.
    Map<String, List<String>> conditionalByRule() {
        return Map.copyOf(conditionalByRule);
    }

    // For a missing required choice: the members the event already maps outside node (EventSource/User/Id when
    // Authenticate lacks its User, say), as paths under node the same input could fill; '' when there are none.
    private String mappedElsewhere(final EventNode node, final List<String> members) {
        final List<String> mapped = new ArrayList<>();
        final List<String> candidates = new ArrayList<>();
        for (final EventNode leaf : leaves(root)) {
            if (leaf.path.startsWith(node.path + "/")) {
                continue;
            }
            final List<String> steps = PyText.split(leaf.path, "/");
            int member = -1;
            for (int i = steps.size() - 2; i > 0; i--) {
                if (members.contains(steps.get(i))) {
                    member = i;
                    break;
                }
            }
            if (member < 0) {
                continue;
            }
            final String candidate = withoutEvent(node.path + "/" + String.join("/", steps.subList(member,
                    steps.size())));
            try {
                schema.resolve(candidate);
            } catch (final IllegalArgumentException e) {
                continue;
            }
            final FieldMapping entry = leaf.leaf;
            final String source;
            if (isSet(entry.field())) {
                source = "field '" + entry.field() + "'";
            } else if (hasAnyOf(entry)) {
                source = "any_of " + PyText.list(entry.anyOf());
            } else if (entry.join() != null) {
                source = "join " + PyText.list(entry.join());
            } else if (isSet(entry.xpath())) {
                source = "xpath `" + entry.xpath() + "`";
            } else if (entry.value() != null) {
                source = "value " + PyText.repr(entry.value());
            } else {
                source = "a lookup";
            }
            mapped.add(withoutEvent(leaf.path) + " (" + source + ")");
            candidates.add(candidate);
        }
        if (mapped.isEmpty()) {
            return "";
        }
        return ". Already mapped elsewhere in the event: " + String.join(", ", mapped.subList(0, Math.min(4,
                mapped.size()))) + ". If the action is about one of them, map the same input here as well ("
               + String.join(" or ", candidates.subList(0, Math.min(4, candidates.size()))) + "), and keep the "
               + "existing mapping: it says something different";
    }

    private static String withoutEvent(final String path) {
        return path.startsWith("Event/")
                ? path.substring("Event/".length())
                : path;
    }

    private static List<EventNode> nodes(final EventNode node) {
        final List<EventNode> out = new ArrayList<>();
        out.add(node);
        for (final EventNode kid : node.kids.values()) {
            out.addAll(nodes(kid));
        }
        return out;
    }

    private static List<EventNode> leaves(final EventNode node) {
        return nodes(node).stream().filter(n -> n.leaf != null).toList();
    }

    private static List<EventNode> anchors(final EventNode node) {
        return nodes(node).stream().filter(n -> n.repeat != null).toList();
    }

    private EventNode walk(final EventNode tree, final List<Child> chain, final String where) {
        EventNode node = tree;
        for (final Child child : chain) {
            if (node.leaf != null) {
                note(problems, where + ": " + node.path + " is mapped to a value and cannot have children");
                return null;
            }
            final EventNode parent = node;
            node = node.kids.computeIfAbsent(child.name(), name -> new EventNode(child, parent.path + "/" + name));
        }
        return node;
    }

    private void checkStructure(final String rule, final EventNode node, final Element decl) {
        if (node.leaf != null || node.shared != null) {
            return;
        }
        final List<Child> allowed = schema.children(decl);
        final Map<Integer, List<String>> chosen = new LinkedHashMap<>();
        for (final EventNode kid : node.kids.values()) {
            if (kid.child.choice() != null && !kid.child.repeatable()) {
                chosen.computeIfAbsent(kid.child.choice(), ignored -> new ArrayList<>()).add(kid.child.name());
            }
        }
        for (final List<String> alternatives : chosen.values()) {
            if (alternatives.size() > 1 && alternatives.contains("Unknown") && "Event/EventDetail".equals(node.path)) {
                // The draft's Unknown placeholders kept beside the action element the agent added.
                final String action = alternatives.stream().filter(n -> !"Unknown".equals(n)).findFirst()
                        .orElseThrow();
                final EventNode inner = node.kids.get(action);
                final String into = "EventDetail/" + action + ("Network".equals(action) && !inner.kids.isEmpty()
                        ? "/" + inner.kids.keySet().iterator().next()
                        : "");
                note(problems, "[" + rule + "] EventDetail/" + action + " and EventDetail/Unknown are alternatives: "
                               + "remove the EventDetail/Unknown entries from this rule (Unknown was the draft's "
                               + "placeholder for the action), and carry their values as " + into + "/Data, "
                               + "data_name kept, or map them where they belong");
            } else if (alternatives.size() > 1) {
                note(problems, "[" + rule + "] " + node.path + ": " + PyText.list(alternatives) + " are "
                               + "alternatives; an event has only one of them (use separate event rules)");
            }
        }
        final Set<String> present = new HashSet<>(node.kids.keySet());
        if (!node.data.isEmpty()) {
            present.add("Data");
        }
        for (final Child c : allowed) {
            if (c.required() && !present.contains(c.name())) {
                // Seen: Haiku left Environment out when the user didn't know it, then stopped for good on this
                // problem.
                final String unknown = "Event/EventSource/System".equals(node.path)
                        ? " (a value only the user knows: if they don't, a placeholder they agree to, such as "
                          + "Unknown, recorded as an open item)"
                        : "";
                note(problems, "[" + rule + "] " + node.path + "/" + c.name() + " is required by the schema; map it"
                               + unknown);
            } else if (c.required() && node.kids.containsKey(c.name())) {
                final String kidTest = generator.testOf(node.kids.get(c.name()));
                final String nodeTest = generator.testOf(node);
                if (kidTest != null && !kidTest.equals(nodeTest)) {
                    conditional.add(withoutEvent(node.path + "/" + c.name()));
                }
            }
        }
        for (final Map.Entry<Integer, List<String>> choice : schema.requiredChoices().entrySet()) {
            if (allowed.stream().anyMatch(c -> choice.getKey().equals(c.choice()))
                && choice.getValue().stream().noneMatch(present::contains)) {
                note(problems, "[" + rule + "] " + node.path + " needs one of " + PyText.list(choice.getValue())
                               + mappedElsewhere(node, choice.getValue()));
            }
        }
        for (final EventNode kid : List.copyOf(node.kids.values())) {
            checkStructure(rule, kid, kid.child.decl());
        }
    }

    /// Each xpath the mapping gives is safe to run, reads no extract name as a variable, and starts at the record.
    ///
    /// @param derivedNames The names the extractions give.
    void checkXpaths(final Set<String> derivedNames) {
        final Map<String, String> bound = MappingFacts.mappingNamespaces(m);
        for (final String x : new LinkedHashSet<>(MappingFacts.mappingXpaths(m))) {
            final String refused = XsltSafety.refused(x, bound);
            if (refused != null) {
                note(problems, "xpath " + PyText.repr(x) + ": " + refused);
            }
            final List<String> read = MappingFacts.variableReads(x, derivedNames);
            if (!read.isEmpty()) {
                note(problems, "xpath " + PyText.repr(x) + " reads " + read.stream().map(n -> "$" + n)
                        .collect(Collectors.joining(", ")) + ": an extract's names are fields, not variables, and "
                               + "the XSLT declares none by that name, so it wouldn't compile. Read one with field: '"
                               + read.getFirst() + "' (an extraction takes an earlier extraction's name as its field "
                               + "too); to change the text first, put the change in the xpath that reads the source "
                               + "itself.");
            }
            final String rooted = MappingFacts.rootedPath(x);
            if (rooted != null) {
                // Seen: .//*:EventData/*:Data written //*:EventData/*:Data, clean at stepping, then processing
                // stopped: "A sequence of more than one item is not allowed as the first argument of fn:replace()".
                final List<String> steps = PyText.split(PyText.rstrip(rooted, "/"), "/");
                final String relative = rooted.startsWith("//")
                        ? "." + rooted
                        : ".//" + or(steps.getLast(), "*");
                final String said = "xpath " + PyText.repr(x) + " starts at the document root (" + rooted + "), "
                                    + "not at the record. Stepping hands the XSLT one record at a time, so there it "
                                    + "finds that record's value; processing reads a stream's records as one "
                                    + "document, where " + rooted + " finds every record's: a function taking one "
                                    + "value fails the whole stream (\"A sequence of more than one item is not "
                                    + "allowed\"), and otherwise every event gets the first record's value. Start "
                                    + "from the record: " + relative + " (anywhere below it), or the path from the "
                                    + "record's own children.";
                if ("xml".equals(m.input())) {
                    // A value outside the records (the document's header), read from the root: the one use it has.
                    note(warnings, said + " Fine only for a value outside the records.");
                } else {
                    note(problems, said);
                }
            }
        }
    }

    /// Each function prefix an xpath calls is bound, by the generator or a shared XSLT in functions, and each
    /// functions entry is called somewhere.
    void checkFunctions() {
        final Map<String, String> bound = new LinkedHashMap<>();
        for (final SharedFunctions functions : m.functions()) {
            final String known = MappingFacts.KNOWN_PREFIXES.getOrDefault(functions.prefix(), functions.namespace());
            if (!known.equals(functions.namespace())) {
                note(problems, "functions: prefix '" + functions.prefix() + "' is the XSLT's own, for " + known
                               + "; bind " + functions.namespace() + " (" + functions.href() + ") to another");
            } else if (!bound.getOrDefault(functions.prefix(), functions.namespace()).equals(functions.namespace())) {
                note(problems, "functions: prefix '" + functions.prefix() + "' is bound to two namespaces");
            }
            bound.put(functions.prefix(), functions.namespace());
        }
        final Set<String> called = new HashSet<>();
        for (final String x : MappingFacts.mappingXpaths(m)) {
            final Matcher matcher = MappingFacts.FUNCTION_PREFIX.matcher(x);
            while (matcher.find()) {
                called.add(matcher.group(1));
            }
        }
        final Set<String> unbound = new TreeSet<>(called);
        unbound.removeAll(MappingFacts.KNOWN_PREFIXES.keySet());
        unbound.removeAll(bound.keySet());
        for (final String prefix : unbound) {
            note(problems, "An xpath calls " + prefix + ":... functions, but no functions entry binds '" + prefix
                           + "': add the shared XSLT that defines them (href, prefix, namespace; describe_template's "
                           + "shared_xslt lists them)");
        }
        for (final SharedFunctions functions : m.functions()) {
            if (!called.contains(functions.prefix()) && !MappingFacts.KNOWN_PREFIXES.containsKey(functions.prefix())) {
                note(warnings, "functions: nothing calls " + functions.prefix() + ":... (" + functions.href()
                               + "); leave it out, or call its functions in the xpaths that need them");
            }
        }
    }

    /// An extraction's regex compiles (as Python's `re` compiles it), has a group per name, and has XPath's flags.
    ///
    /// @param n  Its position in extract.
    /// @param ex The extraction.
    void checkExtraction(final int n, final Extraction ex) {
        final String where = "extract[" + n + "] (" + (isSet(ex.field())
                ? ex.field()
                : String.valueOf(ex.xpath() == null
                        ? "None"
                        : ex.xpath())) + ")";
        if (LOOKAROUND.matcher(ex.regex()).find()) {
            note(problems, where + ": XPath regular expressions have no named groups and no lookaround; use plain "
                           + "groups, (?:...) for ones that are not fields, and anchors");
            return;
        }
        final int groups;
        try {
            groups = PythonRegex.groups(ex.regex());
        } catch (final PythonRegex.PythonRegexException e) {
            note(problems, where + ": the regex does not compile: " + e.getMessage());
            return;
        }
        if (ex.names().size() > groups) {
            note(problems, where + ": " + ex.names().size() + " names but the regex has " + groups
                           + " capture groups");
        } else if (ex.names().size() < groups) {
            note(warnings, where + ": the regex has " + groups + " capture groups and " + ex.names().size()
                           + " names; the later groups are not fields");
        }
        if (ex.names().stream().allMatch(String::isEmpty)) {
            note(problems, where + ": names every group '', so nothing is extracted");
        }
        if (ex.flags().chars().anyMatch(ch -> "imsxq".indexOf(ch) < 0)) {
            note(problems, where + ": flags are any of i, m, s, x, q; not " + PyText.repr(ex.flags()));
        }
    }

    /// Data entries whose name says the schema has an element for them (the draft's own field-name homes: a client
    /// address, a user, a host, a session id), where the event doesn't map that element. Seen: a source IP and a
    /// session id carried as Data on every SecretServer event, where EventSource/Client/IPAddress and
    /// EventSource/SessionId were there to hold them.
    ///
    /// @param rules The rules, drop rules first.
    void dataWithAHome(final List<EventRule> rules) {
        final List<FieldHomes.Home> homes = new ArrayList<>();
        for (final FieldHomes.Home home : FieldHomes.HOMES) {
            try {
                schema.resolve(home.path());
                homes.add(home);
            } catch (final IllegalArgumentException e) {
                // Not in this version of the schema.
            }
        }
        final Map<List<String>, List<String>> found = new LinkedHashMap<>();
        for (final EventRule rule : rules) {
            if (rule.drop()) {
                continue;
            }
            final List<FieldMapping> entries = new ArrayList<>(m.common());
            entries.addAll(rule.fields());
            final Set<String> mapped = entries.stream().map(FieldMapping::path).collect(Collectors.toSet());
            for (final FieldMapping entry : entries) {
                if (!entry.path().endsWith("/Data") || !isSet(entry.dataName())) {
                    continue;
                }
                final List<String> given = new ArrayList<>();
                given.add(entry.dataName());
                if (isSet(entry.field())) {
                    given.add(entry.field());
                }
                final String home = homes.stream()
                        .filter(candidate -> given.stream().anyMatch(n -> FieldHomes.matches(candidate.regex(), n)))
                        .map(FieldHomes.Home::path)
                        .findFirst()
                        .orElse(null);
                if (home != null && !mapped.contains(home)) {
                    found.computeIfAbsent(List.of(entry.dataName(), home), ignored -> new ArrayList<>())
                            .add(rule.name());
                }
            }
        }
        found.forEach((key, where) -> note(warnings, "[" + String.join(", ", where) + "] Data '" + key.get(0)
                                                     + "' looks like " + key.get(1) + ", which the schema has: map it "
                                                     + "there (Data is for what has no element of its own), or keep "
                                                     + "it as Data if it means something else."));
    }

    /// The input names its records as its kind needs, and no drop rule has fields.
    void checkInput() {
        if ("xml".equals(m.input()) && !(isSet(m.root()) && isSet(m.record()))) {
            note(problems, "xml input needs root and record, e.g. root='logons', record='logon'");
        }
        if ("xml_fragments".equals(m.input()) && !isSet(m.record())) {
            note(problems, "xml_fragments input needs record, the fragment element, e.g. record='Event'");
        }
        for (final EventRule rule : m.events()) {
            if (rule.drop() && !rule.fields().isEmpty()) {
                note(problems, "[" + rule.name() + "] a drop rule writes no Event, so it takes no fields");
            }
        }
    }

    private static Map<List<String>, List<String>> shapes() {
        final List<String> networkAction = List.of("Source/Device/IPAddress", "Source/Port",
                "Source/TransportProtocol", "Destination/Device/IPAddress", "Destination/Port", "Outcome/Success");
        final Map<List<String>, List<String>> out = new LinkedHashMap<>();
        for (final String action : List.of("Permit", "Deny", "Open", "Close", "Connect", "Bind", "Listen", "Send",
                "Receive")) {
            out.put(List.of("Network", action), networkAction);
        }
        out.put(List.of("EventDetail", "Network"), List.of("Permit/Source/Device/IPAddress", "Deny/Destination/Port"));
        out.put(List.of("", "Source"), List.of("Device/IPAddress", "Port", "TransportProtocol"));
        out.put(List.of("", "Destination"), List.of("Device/IPAddress", "Port", "TransportProtocol"));
        out.put(List.of("", "Device"), List.of("IPAddress", "HostName"));
        out.put(List.of("", "Outcome"), List.of("Success", "Description"));
        out.put(List.of("", "After"), List.of("Configuration/Type", "Configuration/Description", "File/Path",
                "User/Id"));
        out.put(List.of("", "Before"), List.of("Configuration/Type", "Configuration/Description", "File/Path",
                "User/Id"));
        out.put(List.of("", "Configuration"), List.of("Type", "Name", "Description"));
        out.put(List.of("EventDetail", "Authenticate"), List.of("Action", "User/Id", "Outcome/Success"));
        out.put(List.of("EventDetail", "Update"), List.of("After/Configuration/Type",
                "After/Configuration/Description"));
        out.put(List.of("EventDetail", "Alert"), List.of("Type", "Severity", "Description"));
        return Collections.unmodifiableMap(out);
    }
}
