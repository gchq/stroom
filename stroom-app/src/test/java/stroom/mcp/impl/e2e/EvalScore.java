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

package stroom.mcp.impl.e2e;

import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeRows;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/// A case's score, and the scoring of a case's Events against what the case expects: the record count, the event types,
/// the paths every event has, values some event holds, values and types none may hold, and Data where it belongs; and
/// for an aggregated feed's case, its build's source types ([#scoreSourceTypes]).
@SuppressWarnings("unchecked")
final class EvalScore {

    private static final String EVT = "event-logging:3";
    private static final Set<String> DETAIL_META = Set.of("TypeId", "Description", "Classification", "Purpose");

    private final String caseId;
    private final List<String> problems = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private boolean stage1;
    private boolean indexed;
    private int events;
    private int validEvents;
    private Set<String> eventTypes = Set.of();
    private List<String> missingTypes = List.of();
    private List<String> missingPaths = List.of();
    // The source type checks, each with whether it passed: none for a case that isn't an aggregated feed's.
    private final List<Map<String, Object>> sourceTypeChecks = new ArrayList<>();

    /// @param caseId The case.
    EvalScore(final String caseId) {
        this.caseId = caseId;
    }

    /// Scores the Events read from a case's Events streams (`score_events`).
    ///
    /// @param evalCase The case.
    /// @param records  The Events records read, as XML.
    /// @param validity Whether each is valid against the schema.
    /// @param total    How many events the Events streams hold, when only some were read (a large source), else
    ///                 null: the count is scored on it, and the types, paths and validity on those read.
    void scoreEvents(final Map<String, Object> evalCase,
                     final List<String> records,
                     final List<Boolean> validity,
                     final Integer total) {
        final Map<String, Object> expected = (Map<String, Object>) evalCase.get("expected");
        final List<Element> read = events(records);
        final Set<String> types = new TreeSet<>();
        for (final Element event : read) {
            types.addAll(eventTypes(event));
        }
        events = read.size();
        validEvents = (int) validity.stream().filter(Boolean::booleanValue).count();
        eventTypes = types;
        final int counted = total != null
                ? total
                : read.size();
        // An expected type may name alternatives the source fits equally (Authenticate|Authorise for a badge).
        missingTypes = strings(expected.get("event_types")).stream()
                .filter(type -> List.of(type.split("\\|")).stream().noneMatch(types::contains))
                .sorted()
                .toList();
        final Set<String> missing = new TreeSet<>();
        for (final String path : strings(expected.get("paths"))) {
            if (read.stream().anyMatch(event -> !hasPath(event, path))) {
                missing.add(path);
            }
        }
        missingPaths = List.copyOf(missing);
        final int records1 = ((Number) expected.get("records")).intValue();
        if (counted != records1) {
            problems.add(counted + " events, expected " + records1);
        }
        if (total != null) {
            notes.add(total + " events in the Events streams; " + read.size() + " read and checked");
            events = total;
        }
        if (validEvents != validity.size() || validity.isEmpty()) {
            problems.add((validity.size() - validEvents) + " of " + validity.size() + " Events records invalid");
        }
        // Values some event must hold exactly, e.g. a free-text message carried whole.
        boolean valuesMissing = false;
        for (final Map.Entry<String, Object> entry : maps(expected.get("values")).entrySet()) {
            final Set<String> held = pathValues(read, entry.getKey());
            final List<String> absent = strings(entry.getValue()).stream().filter(v -> !held.contains(v)).toList();
            if (!absent.isEmpty()) {
                valuesMissing = true;
                problems.add("no event has " + entry.getKey() + " = " + absent);
            }
        }
        // Values no event may hold: a source's "-" for none written as a user, say.
        boolean heldWrongly = false;
        for (final Map.Entry<String, Object> entry : maps(expected.get("forbidden_values")).entrySet()) {
            final Set<String> wrong = new TreeSet<>(strings(entry.getValue()));
            wrong.retainAll(pathValues(read, entry.getKey()));
            if (!wrong.isEmpty()) {
                heldWrongly = true;
                problems.add("events have " + entry.getKey() + " = " + wrong + ", which none should");
            }
        }
        // Types no event may have: Unknown for a source whose every record has an action element.
        final Set<String> forbidden = new TreeSet<>(strings(expected.get("forbidden_types")));
        forbidden.retainAll(types);
        if (!forbidden.isEmpty()) {
            problems.add("events of type " + forbidden + ", which none should be");
        }
        // Fields with no element of their own, carried as Data where they belong.
        boolean misplaced = false;
        for (final Map<String, Object> data : (List<Map<String, Object>>) expected.getOrDefault("data", List.of())) {
            if (!dataHeld(read, data)) {
                misplaced = true;
                final boolean every = !Boolean.FALSE.equals(data.get("every"));
                problems.add((every
                        ? "not every"
                        : "no") + " event has Data '" + data.get("name") + "'" + (data.containsKey("value")
                        ? " = '" + data.get("value") + "'"
                        : "") + " under " + data.get("at"));
            }
        }
        stage1 = counted == records1 && !validity.isEmpty() && validEvents == validity.size()
                 && missingTypes.isEmpty() && missingPaths.isEmpty() && !valuesMissing && !heldWrongly
                 && forbidden.isEmpty() && !misplaced;
    }

    /// Scores an aggregated feed's build against the case's `expected.source_types`, from the build's end state: its
    /// registry (`<feed> source types`) and what its group's pipeline logged processing the feed. Each check given is
    /// made, and a failed one is a problem:
    ///
    /// - `field`: the registry's source type field (`meta:<Attribute>` for a stream meta attribute).
    /// - `group`: the build's source type group (the registry's scope): `values` are values of the field whose
    ///   records the group must take (the first row matching each, the rows tried in order, is one of the group's
    ///   source types), `others` values it must not, so the group is exactly the source types of those values
    ///   whatever the rows are named; `name` (any case) what the user called it.
    /// - `rows`: rows the registry must have, each by its `match` (`value`, `pattern` or `member`) and `value`
    ///   (the value, the anchored pattern or the list's item), and its `status` if given.
    /// - `passed_over`: the records the group's pipeline passed over, as its INFO lines count them, with no ERROR or
    ///   FATAL line logged: the other source types' records are not errors, nor worked round.
    ///
    /// @param evalCase    The case.
    /// @param registry    The build's registry, as [SourceTypeRegistry#read] reads it, or null for none.
    /// @param passedOver  The records the group's pipeline logged as passed over, or null if it processed nothing.
    /// @param errorLines  The ERROR and FATAL lines it logged.
    void scoreSourceTypes(final Map<String, Object> evalCase,
                          final Map<String, Object> registry,
                          final Long passedOver,
                          final int errorLines) {
        final Map<String, Object> expected = maps(maps(evalCase.get("expected")).get("source_types"));
        if (expected.isEmpty()) {
            return;
        }
        if (registry == null) {
            check("registry", false, "the build has no source type registry");
            return;
        }
        if (expected.get("field") != null) {
            final String field = String.valueOf(registry.get("field"));
            check("field", expected.get("field").equals(field), "the registry's source type field is " + field
                                                                + ", expected " + expected.get("field"));
        }
        final Map<String, Object> group = maps(expected.get("group"));
        if (!group.isEmpty()) {
            scoreGroup(group, registry);
        }
        final List<Map<String, Object>> rows = (List<Map<String, Object>>) registry.getOrDefault("rows", List.of());
        for (final Map<String, Object> row : (List<Map<String, Object>>) expected.getOrDefault("rows", List.of())) {
            final String wanted = row.get("match") + " " + row.get("value") + (row.get("status") != null
                    ? " (" + row.get("status") + ")"
                    : "");
            final boolean held = rows.stream().anyMatch(actual -> holds(actual, row));
            final List<String> actual = rows.stream()
                    .map(each -> each.get("name") + ": " + each.get("match") + " " + each.get("value") + " ("
                                 + each.get("status") + ")")
                    .toList();
            check("row " + wanted, held, "the registry has no row " + wanted + "; its rows: " + actual);
        }
        if (expected.get("passed_over") instanceof final Number count) {
            check("passed_over", passedOver != null && passedOver == count.longValue() && errorLines == 0,
                    "the group's pipeline passed over " + passedOver + " record(s), expected " + count + ", and "
                    + "logged " + errorLines + " ERROR or FATAL line(s)");
        }
    }

    /// @return Whether every source type check passed (true for a case with none).
    boolean sourceTypes() {
        return sourceTypeChecks.stream().allMatch(check -> Boolean.TRUE.equals(check.get("passed")));
    }

    // The group: the values whose records it takes, and those it doesn't, by the registry's rows in order.
    private void scoreGroup(final Map<String, Object> group, final Map<String, Object> registry) {
        final Map<String, Object> scope = maps(registry.get("scope"));
        final List<String> inGroup = strings(scope.get("source_types"));
        final List<SourceTypeRows.Row> rows = SourceTypeRegistry.rows(registry);
        final List<String> notTaken = new ArrayList<>();
        for (final String value : strings(group.get("values"))) {
            if (SourceTypeRows.first(rows, value).filter(row -> inGroup.contains(row.name())).isEmpty()) {
                notTaken.add(value);
            }
        }
        final List<String> taken = new ArrayList<>();
        for (final String value : strings(group.get("others"))) {
            if (SourceTypeRows.first(rows, value).filter(row -> inGroup.contains(row.name())).isPresent()) {
                taken.add(value);
            }
        }
        final String groupName = String.valueOf(scope.get("group"));
        check("group source types", notTaken.isEmpty() && taken.isEmpty(), "the group " + groupName + " "
                                                                             + inGroup + " doesn't take "
                                                                             + notTaken + " and takes " + taken
                                                                             + ", which it shouldn't");
        if (group.get("name") != null) {
            check("group name", String.valueOf(group.get("name")).equalsIgnoreCase(groupName),
                    "the group is called " + groupName + ", expected " + group.get("name"));
        }
    }

    // Whether a registry row is the one expected: its match and value, and its status if one is expected.
    private static boolean holds(final Map<String, Object> actual, final Map<String, Object> expected) {
        return Objects.equals(expected.get("match"), actual.get("match"))
               && Objects.equals(expected.get("value"), actual.get("value"))
               && (expected.get("status") == null || Objects.equals(expected.get("status"), actual.get("status")));
    }

    // A source type check: kept, and a problem if it failed.
    private void check(final String check, final boolean passed, final String problem) {
        final Map<String, Object> made = new LinkedHashMap<>();
        made.put("check", check);
        made.put("passed", passed);
        if (!passed) {
            made.put("problem", problem);
            problems.add(problem);
        }
        sourceTypeChecks.add(made);
    }

    /// @param problem A problem found with the case's build.
    void problem(final String problem) {
        problems.add(problem);
    }

    /// @param note Something to know of the case's run.
    void note(final String note) {
        notes.add(note);
    }

    /// @param indexed Whether the events were indexed and found by the verification searches.
    void indexed(final boolean indexed) {
        this.indexed = indexed;
    }

    /// @return Whether the Events hold what the case expects.
    boolean stage1() {
        return stage1;
    }

    /// @return How many events there are (all of the Events streams', if only some were read).
    int events() {
        return events;
    }

    /// @return Whether the case passed: its Events as expected and indexed (with no hints for a reference run),
    /// and no other problem found (its documentation, stepping).
    boolean passed() {
        return stage1 && indexed && problems.isEmpty() && missingTypes.isEmpty() && missingPaths.isEmpty();
    }

    /// @return The score as `print_score` prints it, with its problems.
    String summary() {
        final StringBuilder sb = new StringBuilder(caseId).append(": ").append(passed()
                        ? "PASS"
                        : "FAIL")
                .append(" stage1=").append(stage1).append(" indexed=").append(indexed)
                .append(" events=").append(validEvents).append('/').append(events)
                .append(" types=").append(eventTypes);
        problems.forEach(problem -> sb.append("\n  - ").append(problem));
        if (!missingTypes.isEmpty()) {
            sb.append("\n  - missing types ").append(missingTypes);
        }
        if (!missingPaths.isEmpty()) {
            sb.append("\n  - missing paths ").append(missingPaths);
        }
        notes.forEach(note -> sb.append("\n  (").append(note).append(')'));
        return sb.toString();
    }

    /// @return The score, as written to the results.
    Map<String, Object> toMap() {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("case", caseId);
        map.put("passed", passed());
        map.put("stage1", stage1);
        map.put("indexed", indexed);
        map.put("events", events);
        map.put("valid_events", validEvents);
        map.put("event_types", List.copyOf(eventTypes));
        map.put("missing_types", missingTypes);
        map.put("missing_paths", missingPaths);
        map.put("problems", List.copyOf(problems));
        if (!sourceTypeChecks.isEmpty()) {
            map.put("source_type_checks", List.copyOf(sourceTypeChecks));
        }
        map.put("notes", List.copyOf(notes));
        return map;
    }

    // --------------------------------------------------------------------------------

    // event_facts: the Events records' Event elements (records that aren't XML are left out).
    private static List<Element> events(final List<String> records) {
        final List<Element> found = new ArrayList<>();
        for (final String xml : records) {
            final Document document = parse(xml);
            if (document == null) {
                continue;
            }
            final List<Element> all = new ArrayList<>();
            collect(document.getDocumentElement(), all);
            found.addAll(all);
        }
        return found;
    }

    private static void collect(final Element element, final List<Element> into) {
        if (EVT.equals(element.getNamespaceURI()) && "Event".equals(element.getLocalName())) {
            into.add(element);
        }
        for (final Element child : children(element)) {
            collect(child, into);
        }
    }

    // The EventDetail children that aren't its metadata: the event's type (Authenticate, Network, ...).
    private static Set<String> eventTypes(final Element event) {
        final Set<String> types = new LinkedHashSet<>();
        for (final Element detail : named(event, "EventDetail")) {
            for (final Element child : children(detail)) {
                if (!DETAIL_META.contains(child.getLocalName())) {
                    types.add(child.getLocalName());
                }
            }
        }
        return types;
    }

    // has_path: the event has the path, or one of its alternatives (a|b), with a value or children; * is any one
    // element.
    private static boolean hasPath(final Element event, final String path) {
        for (final String alternative : path.split("\\|")) {
            for (final Element node : find(event, alternative)) {
                if (!node.getTextContent().strip().isEmpty() || !children(node).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> pathValues(final List<Element> events, final String path) {
        final Set<String> values = new LinkedHashSet<>();
        for (final Element event : events) {
            for (final String alternative : path.split("\\|")) {
                for (final Element node : find(event, alternative)) {
                    values.add(directText(node).strip());
                }
            }
        }
        return values;
    }

    // data_held: every event (or, with every: false, some event) has a Data element of that Name, with a value
    // (or that value), directly under the element at.
    private static boolean dataHeld(final List<Element> events, final Map<String, Object> spec) {
        final List<Boolean> found = new ArrayList<>();
        for (final Element event : events) {
            boolean held = false;
            for (final String alternative : ((String) spec.get("at")).split("\\|")) {
                for (final Element node : find(event, alternative)) {
                    for (final Element data : named(node, "Data")) {
                        final String value = data.getAttribute("Value");
                        if (Objects.equals(spec.get("name"), data.getAttribute("Name"))
                            && (spec.containsKey("value")
                                ? Objects.equals(String.valueOf(spec.get("value")), value)
                                : !value.isEmpty())) {
                            held = true;
                        }
                    }
                }
            }
            found.add(held);
        }
        final boolean every = !Boolean.FALSE.equals(spec.get("every"));
        return !found.isEmpty() && (every
                ? found.stream().allMatch(Boolean::booleanValue)
                : found.stream().anyMatch(Boolean::booleanValue));
    }

    // ElementPath's findall of a path of event-logging elements, step by step: * is any one of them.
    private static List<Element> find(final Element from, final String path) {
        List<Element> at = List.of(from);
        for (final String step : path.split("/")) {
            final List<Element> next = new ArrayList<>();
            for (final Element element : at) {
                for (final Element child : children(element)) {
                    if ("*".equals(step) || step.equals(child.getLocalName())) {
                        next.add(child);
                    }
                }
            }
            at = next;
        }
        return at;
    }

    private static List<Element> named(final Element parent, final String name) {
        return children(parent).stream().filter(child -> name.equals(child.getLocalName())).toList();
    }

    // The element's event-logging children.
    private static List<Element> children(final Element parent) {
        final List<Element> children = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof final Element element && EVT.equals(element.getNamespaceURI())) {
                children.add(element);
            }
        }
        return children;
    }

    // The element's own text before its first child.
    private static String directText(final Element element) {
        final StringBuilder sb = new StringBuilder();
        for (Node node = element.getFirstChild(); node != null && node.getNodeType() != Node.ELEMENT_NODE;
                node = node.getNextSibling()) {
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(node.getNodeValue());
            }
        }
        return sb.toString();
    }

    private static Document parse(final String xml) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            final DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (final ParserConfigurationException | SAXException | IOException e) {
            return null;
        }
    }

    private static List<String> strings(final Object value) {
        return value instanceof final List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
    }

    private static Map<String, Object> maps(final Object value) {
        return value instanceof final Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
    }
}
