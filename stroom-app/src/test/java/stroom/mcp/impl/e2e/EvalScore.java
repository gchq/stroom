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

import stroom.ai.impl.tools.catalogue.TypeIds;
import stroom.ai.impl.tools.indexing.FeedIndexes;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.skills.SkillDoc;
import stroom.util.json.JsonUtil;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/// A case's score, and the scoring of a case's Events against what the case expects: the record count, the event types,
/// the paths every event has, values some event holds, values and types none may hold, Data where it belongs, the
/// TypeIds, the Descriptions, how many Unknown events, and what the events hold in the schema's own elements rather
/// than in Data; and for an aggregated feed's case, its build's source types ([#scoreSourceTypes]); for a case that
/// starts from another case's promoted build, what it changed of that build ([#scoreLaterBuild]); the forms the user
/// was asked ([#scoreForms]); and the build's event type catalogue ([#scoreCatalogue]).
///
/// What the events hold typed (a translation that puts nearly everything in Data passes every other check: seen in
/// test chat 182, 2026-11-21, View-MailItems with 2 typed values and 17 in Data):
///
/// - `expected.typed`: a list of entries, each `select`ing events and naming `paths` below EventDetail that at least
///   one of them must have a value at outside Data (alternatives `a|b`, `*` any one element); a Data element never
///   satisfies a typed path, nor anything it holds. `select`'s keys must all hold: `type_id`, a regular expression
///   the TypeId matches whole; `action`, the action element (`Send`, or `Move|Delete`); `text`, a text the event holds
///   anywhere (an element's text or a Data Value, e.g. the vendor's operation code, whatever element holds it);
///   `data`, Data fields and their values, as `type_ids.same`'s selectors are. An entry may give its own
///   `typed_at_least`, the fewest distinct typed leaf paths below EventDetail the events it selects hold between them
///   (an element outside Data with its own text, e.g. `Move/Source/Folder/Path`; Data, TypeId, Description,
///   Classification and Purpose not counted), its own `data_share_at_most`, for the events it selects, and
///   `not_typed`, texts no element of theirs outside Data may hold as its own text (2026-11-23, chat 187: a
///   name/value list's pair names, e.g. `MailAccessType`, written as a typed object's Name when each pair was typed
///   as an object, its value in a Description; a pair's name is a Data Name). An entry selecting no event is a
///   problem.
///   ```yaml
///   typed:
///     - select: {text: MoveToDeletedItems}
///       paths: [Move/Source/Folder/Path|Delete/Folder/Path, Move/Destination/Folder/Path]
///       typed_at_least: 4
///   ```
///   `typed_at_least` replaced the eval cases' Data-share caps (2026-11-22): a build keeping every field it doesn't
///   place as Data (the user's standing decision: nothing lost) can't meet a share cap however much it types, as
///   the references met it only by dropping most fields; how many elements are typed is what a build controls.
/// - `expected.data_share_at_most`: a number from 0 to 1, at most the share of the EventDetail values (element
///   texts and attribute values, its TypeId, Description, Classification and Purpose left out) held in Data (Data
///   Values, nested Data's too) over every event but the catch-all's Unknown ones.
///
/// Where either is given, the score holds `typed_checks`, the share over those events (`data_share`: its `share`,
/// the `data` values and all the `values`), the share by TypeId (`data_shares`) and the distinct typed leaf paths
/// by TypeId (`typed_paths`), so runs compare even when they pass.
@SuppressWarnings("unchecked")
final class EvalScore {

    // The tool whose calls on a file an answer call wrote may give its rows.
    private static final String TABLE_TOOL = "table";
    // A skill case's: the tool that reads skills, a problem check_skill reports, and a skill's sections.
    private static final String FIND_SKILLS = "find_skills";
    private static final String CHECK_PROBLEM = "Problem (";
    private static final String STEPS = "steps";
    private static final String DESCRIPTION = "description";
    private static final int NOTE_CHARS = 240;

    private static final String EVT = "event-logging:3";
    private static final Set<String> DETAIL_META = Set.of("TypeId", "Description", "Classification", "Purpose");
    // source_types.index.fields_added: scored only as the group's index recorded, fields being added when needed.
    private static final String WHEN_NEEDED = "when_needed";
    // The first date in a finding's window or a source-wide line.
    private static final Pattern FIRST_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    // type_ids.pattern: Verb-Object, as the event type catalogue's TypeIds are (Authenticate-Logon,
    // Process-SOAPRequest); or any TypeId at all.
    private static final String VERB_OBJECT = "verb_object";
    private static final String ANY = "any";
    // The catalogue's own: a verb and one or more parts of its object (seen in eval case 69: Raise-Malware-Alert,
    // which the catalogue takes, scored not Verb-Object).
    private static final Pattern VERB_OBJECT_ID = TypeIds.VERB_OBJECT;
    // What a build case is scored on when it doesn't say: TypeIds of this pattern, and whether every event (but
    // the catch-all's Unknown ones) must have a Description: not unless the case says so (a description is optional,
    // decided by the user 2026-11-20; a case whose records say what happened asks for one on every event).
    private static final String DEFAULT_TYPE_ID_PATTERN = VERB_OBJECT;
    private static final boolean DEFAULT_DESCRIPTION_EVERY_EVENT = false;
    private static final String UNKNOWN = "Unknown";
    private static final String CONFIRMED = "confirmed";
    private static final String DOCUMENTATION = "documentation";
    private static final String DATA = "Data";

    private final String caseId;
    private final List<String> problems = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private boolean stage1;
    private boolean indexed;
    // A question's checks (a case of kind `question`), each with whether it passed: none for any other case.
    private final List<Map<String, Object>> questionChecks = new ArrayList<>();
    private boolean question;
    private int events;
    private int validEvents;
    private Set<String> eventTypes = Set.of();
    private List<String> missingTypes = List.of();
    private List<String> missingPaths = List.of();
    private Set<String> typeIds = Set.of();
    // The source type checks, each with whether it passed: none for a case that isn't an aggregated feed's.
    private final List<Map<String, Object>> sourceTypeChecks = new ArrayList<>();
    // The event type catalogue's checks, each with whether it passed: none for a case that expects none.
    private final List<Map<String, Object>> catalogueChecks = new ArrayList<>();
    // The checks of what the events hold typed rather than in Data, each with whether it passed: none for a case
    // that expects none.
    private final List<Map<String, Object>> typedChecks = new ArrayList<>();
    // The share of the EventDetail values held in Data, over every event but the Unknown ones and by TypeId: null
    // for a case that expects neither typed paths nor a share.
    private Map<String, Object> dataShare;
    private Map<String, Object> dataShares;
    // The distinct typed leaf paths below EventDetail by TypeId: null as the shares are.
    private Map<String, Object> typedPaths;

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
        // Texts some event must hold anywhere (an element's text or a Data Value), whatever element the agent put
        // them in: a quoted value's escapes undone, say.
        final Set<String> anywhere = allTexts(read);
        final List<String> textsAbsent = strings(expected.get("texts")).stream()
                .filter(text -> !anywhere.contains(text))
                .toList();
        if (!textsAbsent.isEmpty()) {
            problems.add("no event holds " + textsAbsent + " anywhere");
        }
        // Texts no event may hold anywhere: a quoted value with its escapes kept, say.
        final Set<String> textsWrong = new TreeSet<>(strings(expected.get("forbidden_texts")));
        textsWrong.retainAll(anywhere);
        if (!textsWrong.isEmpty()) {
            problems.add("events hold " + textsWrong + ", which none should");
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
        final boolean typeIdsHeld = scoreTypeIds(expected, read);
        final boolean described = scoreDescriptions(expected, read);
        final boolean fewUnknown = scoreUnknown(expected, read);
        final boolean typedHeld = scoreTyped(expected, read);
        stage1 = counted == records1 && !validity.isEmpty() && validEvents == validity.size()
                 && missingTypes.isEmpty() && missingPaths.isEmpty() && !valuesMissing && !heldWrongly
                 && forbidden.isEmpty() && !misplaced && textsAbsent.isEmpty() && textsWrong.isEmpty()
                 && typeIdsHeld && described && fewUnknown && typedHeld;
    }

    // expected.typed and expected.data_share_at_most: what the events hold in the schema's own elements rather than
    // in Data. Each `typed` entry's `select` picks events ([#selected]); each of its `paths` (below EventDetail,
    // alternatives a|b, * any one element) must have a value outside Data in at least one of them, its
    // `typed_at_least`, if given, is the fewest distinct typed leaf paths they hold between them, and its
    // `data_share_at_most`, if given, caps the share of their EventDetail values held in Data. The top level's
    // `data_share_at_most` caps that share over every event but the catch-all's Unknown ones. The shares, overall
    // and by TypeId, and the typed leaf paths by TypeId, are kept in the score whenever either is given, so runs
    // compare even when they pass. Whether every check held.
    private boolean scoreTyped(final Map<String, Object> expected, final List<Element> read) {
        final List<Map<String, Object>> entries = (List<Map<String, Object>>) expected.getOrDefault("typed",
                List.of());
        final Object most = expected.get("data_share_at_most");
        if (entries.isEmpty() && most == null) {
            return true;
        }
        final List<Element> catalogued = read.stream()
                .filter(event -> !eventTypes(event).contains(UNKNOWN))
                .toList();
        final Map<String, DetailValues> byTypeId = new TreeMap<>();
        for (final Element event : catalogued) {
            byTypeId.merge(typeId(event), detailValues(event), DetailValues::plus);
        }
        final DetailValues overall = byTypeId.values().stream()
                .reduce(new DetailValues(0, 0), DetailValues::plus);
        dataShare = overall.toMap();
        dataShares = new LinkedHashMap<>();
        byTypeId.forEach((typeId, values) -> dataShares.put(typeId, values.toMap()));
        final Map<String, Set<String>> pathsByTypeId = new TreeMap<>();
        for (final Element event : catalogued) {
            pathsByTypeId.computeIfAbsent(typeId(event), ignored -> new TreeSet<>()).addAll(typedLeafPaths(event));
        }
        typedPaths = new LinkedHashMap<>();
        pathsByTypeId.forEach((typeId, paths) -> typedPaths.put(typeId, paths.size()));
        boolean held = true;
        if (most != null) {
            held = shareHeld("data share", overall, share(most), "the events hold");
        }
        for (final Map<String, Object> entry : entries) {
            final Map<String, Object> selector = maps(entry.get("select"));
            final List<Element> selected = read.stream().filter(event -> selected(event, selector)).toList();
            if (selected.isEmpty()) {
                held = false;
                typedCheck("typed " + selector, false, "no event matches " + selector + ", whose typed paths are "
                                                       + "expected");
                continue;
            }
            final Set<String> ids = new TreeSet<>();
            selected.forEach(event -> ids.add(typeId(event)));
            for (final String path : strings(entry.get("paths"))) {
                final boolean typed = selected.stream().anyMatch(event -> hasTypedValue(event, path));
                held = typed && held;
                typedCheck("typed " + selector + " " + path, typed, "no event of " + selector + " (TypeIds " + ids
                                                                    + ") has a typed EventDetail/" + path
                                                                    + " (Data doesn't count)");
            }
            if (entry.get("typed_at_least") != null) {
                held = typedPathsHeld(selector, ids, selected, least(entry.get("typed_at_least"))) && held;
            }
            for (final String text : strings(entry.get("not_typed"))) {
                final List<String> at = new ArrayList<>();
                selected.forEach(event -> named(event, "EventDetail").forEach(detail -> typedAt(detail, text, "",
                        at)));
                held = at.isEmpty() && held;
                typedCheck("not typed " + selector + " '" + text + "'", at.isEmpty(), "the events of " + selector
                                                                                     + " (TypeIds " + ids + ") hold '"
                                                                                     + text + "' typed, at " + at
                                                                                     + " (expected as a Data Name)");
            }
            if (entry.get("data_share_at_most") != null) {
                final DetailValues values = selected.stream()
                        .map(EvalScore::detailValues)
                        .reduce(new DetailValues(0, 0), DetailValues::plus);
                held = shareHeld("data share " + selector, values, share(entry.get("data_share_at_most")),
                        "the events of " + selector + " (TypeIds " + ids + ") hold") && held;
            }
        }
        return held;
    }

    // Whether the values' share in Data is at most the cap: a check either way, a problem if not.
    private boolean shareHeld(final String check, final DetailValues values, final double most, final String who) {
        final boolean held = values.share() <= most;
        typedCheck(check + " at most " + most, held, who + " " + values.rounded() + " of their EventDetail values "
                                                     + "in Data (" + values.data() + " of " + values.all()
                                                     + "), at most " + most + " expected");
        return held;
    }

    // Whether the selected events hold at least the fewest distinct typed leaf paths between them: a check either
    // way, a problem if not.
    private boolean typedPathsHeld(final Map<String, Object> selector,
                                   final Set<String> ids,
                                   final List<Element> selected,
                                   final int least) {
        final Set<String> paths = new TreeSet<>();
        selected.forEach(event -> paths.addAll(typedLeafPaths(event)));
        final boolean held = paths.size() >= least;
        typedCheck("typed paths " + selector + " at least " + least, held, "the events of " + selector + " (TypeIds "
                                                                          + ids + ") hold " + paths.size()
                                                                          + " distinct typed EventDetail leaf paths "
                                                                          + "(Data doesn't count) " + paths
                                                                          + ", at least " + least + " expected");
        return held;
    }

    // A typed_at_least: a whole number, at least 1.
    private static int least(final Object given) {
        if (!(given instanceof final Number number) || number.intValue() < 1
            || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("typed_at_least " + given + " is not a whole number of at least 1");
        }
        return number.intValue();
    }

    // The paths below EventDetail of the elements outside Data with their own text (TypeId, Description,
    // Classification and Purpose left out), e.g. Move/Source/Folder/Path.
    private static Set<String> typedLeafPaths(final Element event) {
        final Set<String> paths = new TreeSet<>();
        for (final Element detail : named(event, "EventDetail")) {
            for (final Element child : children(detail)) {
                if (!DETAIL_META.contains(child.getLocalName())) {
                    typedLeafPaths(child, child.getLocalName(), paths);
                }
            }
        }
        return paths;
    }

    private static void typedLeafPaths(final Element element, final String path, final Set<String> paths) {
        if (DATA.equals(element.getLocalName())) {
            return;
        }
        if (!directText(element).isBlank()) {
            paths.add(path);
        }
        for (final Element child : children(element)) {
            typedLeafPaths(child, path + "/" + child.getLocalName(), paths);
        }
    }

    // A data_share_at_most: a number from 0 to 1.
    private static double share(final Object given) {
        if (!(given instanceof final Number number) || number.doubleValue() < 0 || number.doubleValue() > 1) {
            throw new IllegalArgumentException("data_share_at_most " + given + " is not a number from 0 to 1");
        }
        return number.doubleValue();
    }

    // Whether a typed entry's selector picks the event: each of its keys holds. `type_id`, a regular expression the
    // event's TypeId matches whole; `action`, the event's action element (EventDetail's child, alternatives a|b);
    // `text`, a text the event holds anywhere (an element's text or a Data Value, as `texts` reads them); `data`,
    // Data fields with their values, as type_ids.same's selectors ([#matches]).
    private static boolean selected(final Element event, final Map<String, Object> selector) {
        for (final Map.Entry<String, Object> key : selector.entrySet()) {
            final String value = String.valueOf(key.getValue());
            final boolean holds = switch (key.getKey()) {
                case "type_id" -> Pattern.compile(value).matcher(typeId(event)).matches();
                case "action" -> List.of(value.split("\\|")).stream().anyMatch(eventTypes(event)::contains);
                case "text" -> allTexts(List.of(event)).contains(value);
                case "data" -> matches(event, maps(key.getValue()));
                default -> throw new IllegalArgumentException("typed select " + key.getKey() + " is none of "
                                                              + "type_id, action, text and data");
            };
            if (!holds) {
                return false;
            }
        }
        return true;
    }

    // Whether the event has a value at the path below EventDetail (or one of its alternatives, a|b; * any one
    // element) outside Data: Data elements never satisfy a typed path, nor what they hold.
    private static boolean hasTypedValue(final Element event, final String path) {
        for (final Element detail : named(event, "EventDetail")) {
            for (final String alternative : path.split("\\|")) {
                List<Element> at = List.of(detail);
                for (final String step : alternative.split("/")) {
                    final List<Element> next = new ArrayList<>();
                    for (final Element element : at) {
                        for (final Element child : children(element)) {
                            if (!DATA.equals(child.getLocalName())
                                && ("*".equals(step) || step.equals(child.getLocalName()))) {
                                next.add(child);
                            }
                        }
                    }
                    at = next;
                }
                if (at.stream().anyMatch(EvalScore::typedValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    // The paths below EventDetail (outside Data) of the elements whose own text is the text given.
    private static void typedAt(final Element element, final String text, final String path, final List<String> out) {
        for (final Element child : children(element)) {
            if (DATA.equals(child.getLocalName())) {
                continue;
            }
            final String at = path.isEmpty()
                    ? child.getLocalName()
                    : path + "/" + child.getLocalName();
            if (text.equals(directText(child).strip()) && !out.contains(at)) {
                out.add(at);
            }
            typedAt(child, text, at, out);
        }
    }

    // Whether an element outside Data has a value: its own text, or a child (not Data) with one.
    private static boolean typedValue(final Element element) {
        if (DATA.equals(element.getLocalName())) {
            return false;
        }
        return !directText(element).isBlank() || children(element).stream().anyMatch(EvalScore::typedValue);
    }

    // The values an event's EventDetail holds (element texts and attribute values, its TypeId, Description,
    // Classification and Purpose left out), and how many of them are in Data (a Data element's Value, nested Data's
    // too; a Name is not a value).
    private static DetailValues detailValues(final Element event) {
        DetailValues values = new DetailValues(0, 0);
        for (final Element detail : named(event, "EventDetail")) {
            for (final Element child : children(detail)) {
                if (!DETAIL_META.contains(child.getLocalName())) {
                    values = values.plus(valuesIn(child, false));
                }
            }
        }
        return values;
    }

    private static DetailValues valuesIn(final Element element, final boolean inData) {
        final boolean data = inData || DATA.equals(element.getLocalName());
        int all = 0;
        if (data) {
            all += element.getAttribute("Value").isBlank()
                    ? 0
                    : 1;
        } else {
            all += directText(element).isBlank()
                    ? 0
                    : 1;
            final NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                final Node attribute = attributes.item(i);
                if (attribute.getNamespaceURI() == null && !attribute.getNodeValue().isBlank()) {
                    all++;
                }
            }
        }
        DetailValues values = new DetailValues(data
                ? all
                : 0, all);
        for (final Element child : children(element)) {
            values = values.plus(valuesIn(child, data));
        }
        return values;
    }

    // A check of what the events hold typed: kept, and a problem if it failed.
    private void typedCheck(final String check, final boolean passed, final String problem) {
        final Map<String, Object> made = new LinkedHashMap<>();
        made.put("check", check);
        made.put("passed", passed);
        if (!passed) {
            made.put("problem", problem);
            problems.add(problem);
        }
        typedChecks.add(made);
    }

    // expected.type_ids: the events' TypeIds (the catch-all's Unknown events left out of the pattern and the count)
    // are of the `pattern` (`verb_object` or `any`), `exactly` these if given, at least `min_distinct` distinct; none
    // is one of `forbidden`;
    // and each of `same`'s groups, records told by their Data ([#matches]), share one TypeId. Whether they are.
    private boolean scoreTypeIds(final Map<String, Object> expected, final List<Element> read) {
        final Map<String, Object> spec = maps(expected.get("type_ids"));
        final Set<String> all = new TreeSet<>();
        final Set<String> catalogued = new TreeSet<>();
        for (final Element event : read) {
            all.add(typeId(event));
            if (!eventTypes(event).contains(UNKNOWN)) {
                catalogued.add(typeId(event));
            }
        }
        typeIds = all;
        boolean held = true;
        final String pattern = String.valueOf(spec.getOrDefault("pattern", DEFAULT_TYPE_ID_PATTERN));
        if (VERB_OBJECT.equals(pattern)) {
            final List<String> notVerbObject = catalogued.stream()
                    .filter(typeId -> !VERB_OBJECT_ID.matcher(typeId).matches())
                    .toList();
            if (!notVerbObject.isEmpty()) {
                held = false;
                problems.add("TypeIds " + notVerbObject + " are not Verb-Object");
            }
        } else if (!ANY.equals(pattern)) {
            throw new IllegalArgumentException("type_ids.pattern " + pattern + " is neither " + VERB_OBJECT
                                               + " nor " + ANY);
        }
        final List<String> exactly = strings(spec.get("exactly"));
        if (!exactly.isEmpty() && !catalogued.equals(new TreeSet<>(exactly))) {
            held = false;
            problems.add("TypeIds " + catalogued + ", expected exactly " + new TreeSet<>(exactly));
        }
        if (spec.get("min_distinct") instanceof final Number least && catalogued.size() < least.intValue()) {
            held = false;
            problems.add(catalogued.size() + " distinct TypeIds " + catalogued + ", expected at least " + least);
        }
        final Set<String> wrong = new TreeSet<>(strings(spec.get("forbidden")));
        wrong.retainAll(all);
        if (!wrong.isEmpty()) {
            held = false;
            problems.add("events have TypeId " + wrong + ", which none should");
        }
        for (final Object group : (List<Object>) spec.getOrDefault("same", List.of())) {
            held = sameTypeId((List<Map<String, Object>>) group, read) && held;
        }
        return held;
    }

    // The records each selector of a group matches all have one TypeId, and each selector matches some record.
    private boolean sameTypeId(final List<Map<String, Object>> group, final List<Element> read) {
        boolean held = true;
        final Set<String> shared = new TreeSet<>();
        final List<String> each = new ArrayList<>();
        for (final Map<String, Object> selector : group) {
            final Set<String> ids = new TreeSet<>();
            read.stream().filter(event -> matches(event, selector)).forEach(event -> ids.add(typeId(event)));
            if (ids.isEmpty()) {
                held = false;
                problems.add("no event matches " + selector + ", which must share its TypeId");
            }
            shared.addAll(ids);
            each.add(selector + " " + ids);
        }
        if (shared.size() > 1) {
            held = false;
            problems.add("records that must share one TypeId have " + shared + ": " + String.join(", ", each));
        }
        return held;
    }

    // Whether an event holds, for each of the selector's fields, a Data element named after the field (the last
    // segment of either name, any case, its punctuation left out: `event.eventType` is `eventType`) with the value:
    // the vendor's code, as every event type's rule writes it.
    private static boolean matches(final Element event, final Map<String, Object> selector) {
        final NodeList data = event.getElementsByTagNameNS(EVT, "Data");
        for (final Map.Entry<String, Object> entry : selector.entrySet()) {
            boolean found = false;
            for (int i = 0; i < data.getLength() && !found; i++) {
                final Element element = (Element) data.item(i);
                found = fieldName(element.getAttribute("Name")).equals(fieldName(entry.getKey()))
                        && String.valueOf(entry.getValue()).equals(element.getAttribute("Value"));
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    // A field's name, compared: its last segment (after a ., /, > or @), lower case, letters and digits only.
    private static String fieldName(final String name) {
        final String[] segments = name.split("[./>@]");
        final String last = segments.length == 0
                ? name
                : segments[segments.length - 1];
        return last.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // expected.description_every_event: every event but the catch-all's Unknown ones has an EventDetail/Description.
    private boolean scoreDescriptions(final Map<String, Object> expected, final List<Element> read) {
        final boolean every = expected.get("description_every_event") instanceof final Boolean given
                ? given
                : DEFAULT_DESCRIPTION_EVERY_EVENT;
        if (!every) {
            return true;
        }
        final List<Element> catalogued = read.stream()
                .filter(event -> !eventTypes(event).contains(UNKNOWN))
                .toList();
        final List<Element> bare = catalogued.stream()
                .filter(event -> pathValues(List.of(event), "EventDetail/Description").stream()
                        .allMatch(String::isEmpty))
                .toList();
        if (bare.isEmpty()) {
            return true;
        }
        final Set<String> ids = new TreeSet<>();
        bare.forEach(event -> ids.add(typeId(event)));
        problems.add(bare.size() + " of " + catalogued.size() + " events have no EventDetail/Description: TypeIds "
                     + ids);
        return false;
    }

    // expected.unknown_at_most: no more of the events read are Unknown (the catch-all's) than that.
    private boolean scoreUnknown(final Map<String, Object> expected, final List<Element> read) {
        if (!(expected.get("unknown_at_most") instanceof final Number most)) {
            return true;
        }
        final long unknown = read.stream().filter(event -> eventTypes(event).contains(UNKNOWN)).count();
        if (unknown <= most.longValue()) {
            return true;
        }
        problems.add(unknown + " Unknown events, at most " + most + " expected");
        return false;
    }

    /// Scores a build's event type catalogue against the case's `expected.catalogue`, from the catalogue its source
    /// notes hold (their JSON block: `event_types`, `catch_all`, `scope`) and the catalogue's last check (as
    /// `catalogue_event_types` returns it: `counts`, `overlaps`...). Each check given is made, and a failed one is a
    /// problem; an event type is named by its TypeId, any case, or alternatives the source fits equally (`a|b`):
    ///
    /// - `catch_all_confirmed`: the catch-all's status is `confirmed`.
    /// - `no_overlaps`: the check found no record two event types recognise.
    /// - `description_every`: every event type has a description (optional in the catalogue; for a case that needs
    ///   one).
    /// - `no_condition_on_source_type_field`: no way of recognising an event type tests the scope's source type
    ///   field (`event>log_type` is `event.log_type` too).
    /// - `recognised_two_ways`: each event type named, or at least so many, is recognised two or more ways.
    /// - `documentation_only`: each event type named, or at least so many, is from the documentation (`basis`
    ///   `documentation`) with no record in the sample.
    /// - `dropped`: each event type named is dropped.
    /// - `event_types_at_least`: the catalogue has at least so many event types that aren't dropped (one that grew
    ///   from a promoted build's, say).
    ///
    /// @param evalCase  The case.
    /// @param catalogue The build's source notes, as their JSON block reads, or null for none.
    /// @param check     The catalogue's check, or null if none was made.
    void scoreCatalogue(final Map<String, Object> evalCase,
                        final Map<String, Object> catalogue,
                        final Map<String, Object> check) {
        final Map<String, Object> expected = maps(maps(evalCase.get("expected")).get("catalogue"));
        if (expected.isEmpty()) {
            return;
        }
        if (catalogue == null || !(catalogue.get("event_types") instanceof List<?>)) {
            catalogueCheck("catalogue", false, "the build's source notes hold no event type catalogue");
            return;
        }
        final List<Map<String, Object>> types = (List<Map<String, Object>>) catalogue.get("event_types");
        if (Boolean.TRUE.equals(expected.get("catch_all_confirmed"))) {
            final Object status = maps(catalogue.get("catch_all")).get("status");
            catalogueCheck("catch-all confirmed", CONFIRMED.equals(status), "the catch-all is " + status
                                                                            + ", not confirmed");
        }
        if (Boolean.TRUE.equals(expected.get("no_overlaps"))) {
            if (check == null) {
                catalogueCheck("no overlaps", false, "no catalogue check to score the overlaps on");
            } else {
                final List<String> overlaps = ((List<Map<String, Object>>) check.getOrDefault("overlaps",
                        List.of())).stream()
                        .map(overlap -> "record " + overlap.get("record") + " " + overlap.get("type_ids"))
                        .toList();
                catalogueCheck("no overlaps", overlaps.isEmpty(), "event types overlap: " + String.join(", ",
                        overlaps));
            }
        }
        if (Boolean.TRUE.equals(expected.get("description_every"))) {
            final List<String> bare = types.stream()
                    .filter(type -> String.valueOf(Objects.requireNonNullElse(type.get("description"), ""))
                            .isBlank())
                    .map(EvalScore::typeIdOf)
                    .toList();
            catalogueCheck("description on every event type", bare.isEmpty(), "event types with no description: "
                                                                               + bare);
        }
        if (Boolean.TRUE.equals(expected.get("no_condition_on_source_type_field"))) {
            final Object field = maps(catalogue.get("scope")).get("source_type_field");
            final List<String> testing = field == null
                    ? List.of()
                    : types.stream().filter(type -> tests(type, String.valueOf(field))).map(EvalScore::typeIdOf)
                            .toList();
            catalogueCheck("no condition on the source type field", testing.isEmpty(), "event types recognised by "
                                                                                        + "the source type field "
                                                                                        + field + ": " + testing);
        }
        if (expected.get("recognised_two_ways") instanceof final Number least) {
            // So many, whatever the agent named them (seen in cases 30 and 67: Start-Session for
            // Authenticate-Logon).
            final List<String> twoWays = types.stream()
                    .filter(type -> ((List<?>) type.getOrDefault("recognised_by", List.of())).size() >= 2)
                    .map(EvalScore::typeIdOf)
                    .toList();
            catalogueCheck("at least " + least + " recognised two ways", twoWays.size() >= least.intValue(),
                    twoWays.size() + " event type(s) recognised two or more ways " + twoWays + ", expected at least "
                    + least);
        }
        for (final String named : strings(expected.get("recognised_two_ways"))) {
            final Map<String, Object> type = typeNamed(types, named);
            final int ways = type == null
                    ? 0
                    : ((List<?>) type.getOrDefault("recognised_by", List.of())).size();
            catalogueCheck(named + " recognised two ways", type != null && ways >= 2, type == null
                    ? "no event type " + named + " in the catalogue"
                    : typeIdOf(type) + " is recognised " + ways + (ways == 1
                            ? " way"
                            : " ways") + ", expected two or more");
        }
        scoreDocumentationOnly(expected.get("documentation_only"), types, check);
        if (expected.get("event_types_at_least") instanceof final Number least) {
            final long real = types.stream().filter(type -> !Boolean.TRUE.equals(type.get("drop"))).count();
            catalogueCheck("at least " + least + " event types", real >= least.longValue(), "the catalogue has "
                                                                                         + real + " event types, "
                                                                                         + "expected at least "
                                                                                         + least);
        }
        for (final String named : strings(expected.get("dropped"))) {
            final Map<String, Object> type = typeNamed(types, named);
            catalogueCheck(named + " dropped", type != null && Boolean.TRUE.equals(type.get("drop")), type == null
                    ? "no event type " + named + " in the catalogue"
                    : typeIdOf(type) + " is not dropped");
        }
    }

    // documentation_only: the event types named (or at least so many) from the documentation, with no record in the
    // sample (none counted by the check).
    private void scoreDocumentationOnly(final Object expected,
                                        final List<Map<String, Object>> types,
                                        final Map<String, Object> check) {
        if (expected == null) {
            return;
        }
        final Map<String, Object> counts = check == null
                ? Map.of()
                : maps(check.get("counts"));
        final List<Map<String, Object>> documented = types.stream()
                .filter(type -> DOCUMENTATION.equals(type.get("basis")))
                .filter(type -> !(maps(counts.get(typeIdOf(type))).get("records") instanceof final Number records)
                                || records.intValue() == 0)
                .toList();
        if (expected instanceof final Number least) {
            catalogueCheck("at least " + least + " from documentation only", documented.size() >= least.intValue(),
                    documented.size() + " event type(s) from documentation only, with no record in the sample, "
                    + "expected at least " + least);
            return;
        }
        for (final String named : strings(expected)) {
            final Map<String, Object> type = typeNamed(types, named);
            catalogueCheck(named + " from documentation only", type != null && documented.contains(type),
                    type == null
                            ? "no event type " + named + " in the catalogue"
                            : typeIdOf(type) + " is not from documentation only (basis " + type.get("basis")
                              + ", " + maps(counts.get(typeIdOf(type))).get("records") + " record(s))");
        }
    }

    // Whether any way of recognising the event type tests the field (a held field's > read as the . of a nested
    // one's, a meta: prefix left out).
    private static boolean tests(final Map<String, Object> type, final String field) {
        final String wanted = spelling(field);
        for (final Map<String, Object> way : (List<Map<String, Object>>) type.getOrDefault("recognised_by",
                List.of())) {
            for (final Map<String, Object> condition : (List<Map<String, Object>>) way.getOrDefault("when",
                    List.of())) {
                if (condition.get("field") != null && wanted.equals(spelling(String.valueOf(condition.get(
                        "field"))))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String spelling(final String field) {
        return field.replaceFirst("^meta:", "").replace('>', '.').replace('/', '.');
    }

    // The event type the name gives (its TypeId, any case, or one of its alternatives a|b), or null.
    private static Map<String, Object> typeNamed(final List<Map<String, Object>> types, final String named) {
        final List<String> alternatives = List.of(named.split("\\|"));
        return types.stream()
                .filter(type -> alternatives.stream().anyMatch(name -> name.equalsIgnoreCase(typeIdOf(type))))
                .findFirst()
                .orElse(null);
    }

    private static String typeIdOf(final Map<String, Object> type) {
        return String.valueOf(type.get("type_id"));
    }

    // A catalogue check: kept, and a problem if it failed.
    private void catalogueCheck(final String check, final boolean passed, final String problem) {
        final Map<String, Object> made = new LinkedHashMap<>();
        made.put("check", check);
        made.put("passed", passed);
        if (!passed) {
            made.put("problem", problem);
            problems.add(problem);
        }
        catalogueChecks.add(made);
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
    /// - `groups`: the registry's source type groups, exactly (the groups its rows name, any order).
    /// - `passed_over`: the records the group's pipeline passed over, told as the tools tell them (the registry's
    ///   field and rows over the raw records: those of source types outside the group), with no ERROR or FATAL line
    ///   logged: the other source types' records are not errors, nor worked round.
    ///
    /// @param evalCase    The case.
    /// @param registry    The build's registry, as [SourceTypeRegistry#read] reads it, or null for none.
    /// @param passedOver  The records the group's pipeline passed over, or null if it processed nothing.
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
        if (expected.get("groups") != null) {
            final Set<String> groups = new TreeSet<>();
            rows.stream().map(row -> row.get("group")).filter(Objects::nonNull).forEach(g -> groups.add(
                    String.valueOf(g)));
            check("groups", groups.equals(new TreeSet<>(strings(expected.get("groups")))), "the registry's groups "
                                                                                           + "are " + groups
                                                                                           + ", expected "
                                                                                           + expected.get("groups"));
        }
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

    /// Scores a later build against the promoted build it started from (a case's `setup`), from the build's end state:
    ///
    /// - `source_types.pipeline`: `new` where the group's rows (those of `source_types.group.values`) name a pipeline
    ///   other than the setup group's (a new version of it, or a new group's), `same` where they name the setup
    ///   group's own (no new version made).
    /// - `source_types.index.fields_added`: `true`, the registry's index of the group has fields this build added;
    ///   `when_needed`, only that the group's index is recorded (fields are added only when the plan needs them).
    /// - `unchanged`: the setup group's pipeline, XSLT and index as the setup left them.
    /// - `separate_index`: the build's Events were found in an index other than the setup group's.
    ///
    /// @param evalCase  The case.
    /// @param registry  The registry scored, as [SourceTypeRegistry#read] reads it, or null for none.
    /// @param before    What the setup left.
    /// @param now       The setup group's documents as they are now ([Before#snapshot]'s keys).
    /// @param build     The build.
    /// @param indexUsed The UUID of the index the build's Events were found in, or null if none.
    void scoreLaterBuild(final Map<String, Object> evalCase,
                         final Map<String, Object> registry,
                         final Before before,
                         final Map<String, String> now,
                         final String build,
                         final String indexUsed) {
        final Map<String, Object> expected = maps(evalCase.get("expected"));
        final Map<String, Object> sourceTypes = maps(expected.get("source_types"));
        final List<Map<String, Object>> rows = registry == null
                ? List.of()
                : (List<Map<String, Object>>) registry.getOrDefault("rows", List.of());
        if (sourceTypes.get("pipeline") instanceof final String wanted) {
            final List<String> values = strings(maps(sourceTypes.get("group")).get("values"));
            final List<String> pipelines = new ArrayList<>();
            final List<SourceTypeRows.Row> parsed = registry == null
                    ? List.of()
                    : SourceTypeRegistry.rows(registry);
            for (final String value : values) {
                final String name = SourceTypeRows.first(parsed, value).map(SourceTypeRows.Row::name).orElse(null);
                // A row with no pipeline yet (a build that never finished: its source type still parked) is
                // "null", not a NullPointerException from findFirst on a null uuid (seen in eval case 61).
                final Object pipeline = rows.stream()
                        .filter(row -> Objects.equals(name, row.get("name")))
                        .findFirst()
                        .map(row -> maps(row.get("pipeline")).get("uuid"))
                        .orElse(null);
                pipelines.add(String.valueOf(pipeline));
            }
            final String setupPipeline = String.valueOf(before.pipeline());
            final boolean passed = !pipelines.isEmpty() && ("same".equals(wanted)
                    ? pipelines.stream().allMatch(setupPipeline::equals)
                    : pipelines.stream().noneMatch(p -> "null".equals(p) || setupPipeline.equals(p)));
            check("pipeline " + wanted, passed, "the group's rows name pipeline(s) " + pipelines + ", expected "
                                                + ("same".equals(wanted)
                    ? "the setup's " + before.pipeline()
                    : "a new one, not the setup's " + before.pipeline()));
        }
        final Object fieldsAdded = maps(sourceTypes.get("index")).get("fields_added");
        if (Boolean.TRUE.equals(fieldsAdded)) {
            final String group = String.valueOf(maps(sourceTypes.get("group")).get("name"));
            final Map<String, Object> index = registry == null
                    ? Map.of()
                    : maps(FeedIndexes.indexOfGroup(registry, group));
            final Map<String, Object> addedBy = maps(index.get("added_by"));
            check("index fields added", addedBy.containsValue(build), "the index of group " + group + " ("
                                                                      + index.get("index") + ") has no field "
                                                                      + "added by " + build + ": " + addedBy);
        } else if (WHEN_NEEDED.equals(fieldsAdded)) {
            // Fields are added only when the build's plan indexes some the index lacks: seen in an eval, the
            // agent's mapping wrote elements the index already had, and promotion rightly added none. Scored
            // only that the index of the group is recorded.
            final String group = String.valueOf(maps(sourceTypes.get("group")).get("name"));
            final Object index = registry == null
                    ? null
                    : FeedIndexes.indexOfGroup(registry, group);
            check("index of the group recorded", index != null, "no index recorded for group " + group);
        }
        if (Boolean.TRUE.equals(expected.get("unchanged"))) {
            for (final Map.Entry<String, String> entry : before.snapshot().entrySet()) {
                check("unchanged " + entry.getKey(), Objects.equals(entry.getValue(), now.get(entry.getKey())),
                        "the setup group " + before.group() + "'s " + entry.getKey() + " changed");
            }
        }
        if (Boolean.TRUE.equals(expected.get("separate_index"))) {
            check("separate index", indexUsed != null && !indexUsed.equals(before.index()), "the build's Events "
                                                                                           + "were found in "
                                                                                           + (indexUsed == null
                    ? "no index"
                    : "index " + indexUsed) + ", expected one of their own, not the setup group's "
                                                                                           + before.index());
        }
    }

    /// Scores the forms the user was shown in the build against the case's `expected.forms`: `asked`, each a form
    /// that must have been shown (its `message`, a regular expression found in the form's message), where an
    /// `answer` is given answered with a value starting with it, and where `at_most` is given shown no more times than
    /// that (a form asked again and again is a loop); `not_asked`, each a regular expression no form's message may
    /// hold, or a `message` and an `offering`, a regular expression none of such a form's options may match (a kind
    /// field form offering a time field, say).
    ///
    /// @param evalCase The case.
    /// @param forms    The forms shown, in order: each its `message`, the `content` it was answered with and the
    ///                 `options` its fields offered (their choices and examples), where recorded.
    void scoreForms(final Map<String, Object> evalCase, final List<Map<String, Object>> forms) {
        final Map<String, Object> expected = maps(maps(evalCase.get("expected")).get("forms"));
        for (final Map<String, Object> asked : (List<Map<String, Object>>) expected.getOrDefault("asked",
                List.of())) {
            final Pattern message = Pattern.compile(String.valueOf(asked.get("message")));
            final String answer = (String) asked.get("answer");
            final List<Map<String, Object>> shown = forms.stream()
                    .filter(form -> message.matcher(String.valueOf(form.get("message"))).find())
                    .toList();
            final boolean answered = answer == null || shown.stream().anyMatch(form -> maps(form.get("content"))
                    .values().stream().anyMatch(value -> String.valueOf(value).startsWith(answer)));
            check("form " + message + " asked", !shown.isEmpty() && answered, shown.isEmpty()
                    ? "no form asked '" + message + "'"
                    : "the form '" + message + "' wasn't answered '" + answer + "…': " + shown.stream()
                            .map(form -> form.get("content"))
                            .toList());
            if (asked.get("at_most") instanceof final Number most) {
                check("form " + message + " asked at most " + most, shown.size() <= most.intValue(),
                        "the form '" + message + "' was asked " + shown.size() + " times, at most " + most
                        + " expected");
            }
        }
        for (final Object notAsked : (List<Object>) expected.getOrDefault("not_asked", List.of())) {
            if (notAsked instanceof final Map<?, ?> spec) {
                scoreNotOffered(forms, Pattern.compile(String.valueOf(spec.get("message"))),
                        Pattern.compile(String.valueOf(spec.get("offering"))));
            } else {
                final Pattern message = Pattern.compile(String.valueOf(notAsked));
                final boolean asked = forms.stream()
                        .anyMatch(form -> message.matcher(String.valueOf(form.get("message"))).find());
                check("form " + notAsked + " not asked", !asked, "a form asked '" + notAsked + "', which none "
                                                                 + "should");
            }
        }
    }

    /// Scores where a case's group's pipeline was promoted to (`expected.source_types.layout`, a regular expression
    /// found in the explorer path of its folder, e.g. role first then version: `/Events/V1\.0$`). A case with no
    /// layout given is not scored.
    ///
    /// @param evalCase The case.
    /// @param folder   The folder of the group's pipeline, an explorer path, or null if it has none.
    void scoreLayout(final Map<String, Object> evalCase, final String folder) {
        final Object layout = maps(maps(evalCase.get("expected")).get("source_types")).get("layout");
        if (layout == null) {
            return;
        }
        notes.add("the group's pipeline is in " + folder);
        check("layout", folder != null && Pattern.compile(String.valueOf(layout)).matcher(folder).find(),
                "the group's pipeline is in " + folder + ", expected a folder matching " + layout);
    }

    // No form whose message matches may offer an option the offering matches.
    private void scoreNotOffered(final List<Map<String, Object>> forms,
                                 final Pattern message,
                                 final Pattern offering) {
        final List<String> offered = forms.stream()
                .filter(form -> message.matcher(String.valueOf(form.get("message"))).find())
                .flatMap(form -> strings(form.get("options")).stream())
                .filter(option -> offering.matcher(option).find())
                .toList();
        check("form " + message + " not offering " + offering, offered.isEmpty(), "a form asking '" + message
                                                                                  + "' offered " + offered
                                                                                  + ", which none should");
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

    /// @return Whether the events were indexed and found by the verification searches.
    boolean indexed() {
        return indexed;
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
        if (question) {
            return problems.isEmpty();
        }
        return stage1 && indexed && problems.isEmpty() && missingTypes.isEmpty() && missingPaths.isEmpty();
    }

    // The rows an answer expects: its rows, or of its rows_any_of (several ways to give a right answer, e.g. a
    // session's start and end in one row or as its two events) the first an accepted call matches, else the first.
    private static List<List<Object>> wantedRows(final Map<String, Object> answer,
                                                 final List<ToolUse> accepted,
                                                 final Map<ToolUse, ToolUse> tabled,
                                                 final boolean ordered,
                                                 final double tolerance,
                                                 final boolean extraRows) {
        if (!(answer.get("rows_any_of") instanceof final List<?> ways) || ways.isEmpty()) {
            return rows(answer.get("rows"));
        }
        for (final Object way : ways) {
            final List<List<Object>> rows = rows(way);
            if (accepted.stream()
                    .anyMatch(call -> rowsMismatch(rows, call, tabled, ordered, tolerance, extraRows) == null)) {
                return rows;
            }
        }
        return rows(ways.getFirst());
    }

    /// Scores a question's answer (a case of kind `question`, [EvalCases#isQuestion]) from the tool calls made:
    ///
    /// * `expected.tools_used`: each tool was called at least once;
    /// * a call of `expected.answer.tool` (default `esql_query`) was accepted (how many were refused is noted);
    /// * `expected.answer.rows`: an accepted call's rows hold the expected rows (in order when `ordered: true`, else in
    ///   any order), and no others. The first accepted call that matches is the answer, whatever calls came after it
    ///   (an agent may answer from one call, then run others to check or add to it), and which call it was is noted; if
    ///   none matches, the problem is said of the closest accepted call (the one holding the most expected rows, in any
    ///   order; the latest of those as close), and the other checks are of it. `extra_rows: true` allows rows besides
    ///   the expected ones; `rows_any_of` lists several right forms, the first an accepted call matches being used.
    ///   A row matches when each expected value
    ///   is one of its values, whatever the columns are called: a string exactly, or as the start of a date
    ///   (`2026-01-02` matches `2026-01-02T00:00:00.000Z`); a number within `tolerance` of it (a fraction of it,
    ///   default 0.001), or 0.05 for one given with decimals, as an answer rounded to one place is. The rows must be
    ///   the query's: a value worked out in the agent's reply (a conversion, a cut-off) doesn't count. A call's rows
    ///   may also be those of the latest `table` call on the file it wrote (its result's `out`, the table call's
    ///   `path`), as when Sonnet kept a join's logon rows with table in case 72; the answer call is still the call that
    ///   wrote the file, so the other checks are of its query;
    /// * `expected.answer.total_groups`: the `total_groups` of any accepted call that gives it, or the answer
    ///   call's row count, if it wasn't cut short;
    /// * `expected.answer.uncapped`: when true, the answer call's rows weren't cut at the most a query may return
    ///   (its result's `capped`), as rows a hand join reads are (how many accepted calls were is noted);
    /// * `expected.answer.query`: a regular expression (any case) found in the answer call's `query`, e.g. that it
    ///   joins in Stroom rather than naming values read by an earlier call, a join done by hand;
    /// * `expected.answer.result`: values the answer call's result holds, by key, e.g. a sequence's `engine`;
    /// * `expected.findings`: of a tool whose result has `findings` (each a map with `kind`, `feature`, `window`,
    ///   `evidence`) and `source_wide` (each a line), as compare_activity's: `must_include` and `must_not_include`,
    ///   each a list of matchers ([#findingMatches]), each matched against the answer call's findings (with
    ///   `source_wide: true`, against its source-wide lines; with `done_to_them: true`, against the findings of what
    ///   was done to the person, its `done_to_them.findings`); `answer_must_not_say` (none of these words in the
    ///   reply) and `answer_must_say_one_of` (at least one), checked only when the reply is given; and
    ///   `answer_must_say_all_of` (each a word or a list of alternatives), in the reply, else (a reference run) the
    ///   accepted calls' results. Wording is checked with or without `expected.answer` (an answer, whatever tool).
    ///
    /// @param evalCase The case.
    /// @param calls    The tool calls made, in order.
    void scoreQuestion(final Map<String, Object> evalCase, final List<ToolUse> calls) {
        scoreQuestion(evalCase, calls, null);
    }

    /// Scores a question's tool calls ([#scoreQuestion(Map, List)]) and the agent's reply.
    ///
    /// @param evalCase The case.
    /// @param calls    The tool calls made, in order.
    /// @param reply    The agent's reply, or null where there is none (a reference run): its wording isn't checked.
    void scoreQuestion(final Map<String, Object> evalCase, final List<ToolUse> calls, final String reply) {
        question = true;
        final Map<String, Object> expected = maps(evalCase.get("expected"));
        final Set<String> called = new LinkedHashSet<>();
        calls.forEach(call -> called.add(call.name()));
        for (final String tool : strings(expected.get("tools_used"))) {
            questionCheck("used " + tool, called.contains(tool), tool + " was not used (used: " + called + ")");
        }
        final Map<String, Object> answer = maps(expected.get("answer"));
        if (answer.isEmpty()) {
            scoreWording(maps(expected.get("findings")), calls, reply);
            return;
        }
        final String tool = String.valueOf(answer.getOrDefault("tool", "esql_query"));
        final List<ToolUse> tries = calls.stream().filter(call -> tool.equals(call.name())).toList();
        if (tries.isEmpty()) {
            questionCheck("answer", false, "no " + tool + " call to take the answer from");
            return;
        }
        final long refused = tries.stream().filter(ToolUse::refused).count();
        note(tool + ": " + refused + " of " + tries.size() + " call(s) refused");
        final List<ToolUse> accepted = tries.stream().filter(call -> !call.refused()).toList();
        questionCheck(tool + " accepted", !accepted.isEmpty(), "every " + tool + " call was refused, the last: "
                                                               + tries.getLast().text());
        if (accepted.isEmpty()) {
            return;
        }
        final long capped = accepted.stream().filter(EvalScore::capped).count();
        if (capped > 0) {
            note(tool + ": " + capped + " of " + accepted.size() + " accepted call(s) cut at the row limit");
        }
        ToolUse answerCall = accepted.getLast();
        final double tolerance = answer.get("tolerance") instanceof final Number number
                ? number.doubleValue()
                : 0.001;
        final boolean ordered = Boolean.TRUE.equals(answer.get("ordered"));
        final Map<ToolUse, ToolUse> tabled = tabled(calls, tool);
        final boolean extraRows = Boolean.TRUE.equals(answer.get("extra_rows"));
        final List<List<Object>> wanted = wantedRows(answer, accepted, tabled, ordered, tolerance, extraRows);
        if (!wanted.isEmpty()) {
            final ToolUse matching = accepted.stream()
                    .filter(call -> rowsMismatch(wanted, call, tabled, ordered, tolerance, extraRows) == null)
                    .findFirst()
                    .orElse(null);
            if (matching != null) {
                answerCall = matching;
                final boolean fromTable = mismatch(wanted,
                        kept(wanted, rows(matching.result().get("rows")), extraRows, tolerance),
                        ordered, tolerance) != null;
                note("answer rows from " + (fromTable
                        ? "a " + TABLE_TOOL + " call on "
                        : "") + tool + " call " + (tries.indexOf(matching) + 1) + " of " + tries.size());
            } else {
                // The call that came closest is the one whose problem is said, the latest of those as close.
                ToolUse closest = answerCall;
                int most = -1;
                for (final ToolUse call : accepted) {
                    final int held = held(wanted, call, tabled, tolerance);
                    if (held >= most) {
                        most = held;
                        closest = call;
                    }
                }
                answerCall = closest;
                note("no " + tool + " call matched; the closest, call " + (tries.indexOf(closest) + 1) + " of "
                     + tries.size() + ", held " + most + " of " + wanted.size() + " expected rows");
            }
            final String mismatch = rowsMismatch(wanted, answerCall, tabled, ordered, tolerance, extraRows);
            questionCheck("answer rows", mismatch == null, "the answer's rows " + mismatch);
        }
        if (Boolean.TRUE.equals(answer.get("uncapped"))) {
            questionCheck("answer uncapped", !capped(answerCall), "the answer's rows were cut at the row limit, so "
                                                                  + "they are not all the data's");
        }
        if (answer.get("total_groups") instanceof final Number total) {
            final List<Object> given = totalGroups(answerCall, accepted);
            final Object said = given.size() > 1
                    ? given
                    : given.stream().findFirst().orElse(null);
            questionCheck("total groups", given.stream().anyMatch(value -> value instanceof final Number number
                                                                         && number.longValue() == total.longValue()),
                    "the total groups were " + said + ", expected " + total);
        }
        if (answer.get("query") instanceof final String query) {
            final Object given = answerCall.result().get("query");
            questionCheck("answer query", given != null && Pattern.compile(query, Pattern.CASE_INSENSITIVE
                                                                                   | Pattern.DOTALL)
                    .matcher(String.valueOf(given)).find(), "the answer's query doesn't match /" + query + "/: "
                                                             + given);
        }
        for (final Map.Entry<String, Object> value : maps(answer.get("result")).entrySet()) {
            final Object given = answerCall.result().get(value.getKey());
            questionCheck("answer " + value.getKey(), String.valueOf(value.getValue()).equals(String.valueOf(given)),
                    "the answer's " + value.getKey() + " was " + given + ", expected " + value.getValue());
        }
        scoreFindings(maps(expected.get("findings")), answerCall);
        scoreWording(maps(expected.get("findings")), calls, reply);
    }

    /// Scores what a hand-written case's build did besides its Events ([AbstractEvalTest#scoreHandWritten]):
    ///
    /// * `expected.route.calls_in_order`: the tools called hold these in this order (others may come between);
    ///   `forbidden_calls`: none of these was called (e.g. a translation drafted from scratch);
    /// * `expected.catalogue.origin`: the catalogue's origin, as `catalogue_event_types` lists it, starts so;
    /// * `expected.promoted.pipeline`: the copy is named so; `original_unchanged`: the original's XSLT is as the
    ///   setup wrote it.
    ///
    /// @param evalCase          The case.
    /// @param made              The tools called, in order.
    /// @param origin            The catalogue's origin as last listed, or null if it never was.
    /// @param copyName          The copy's name, or null if no copy was made.
    /// @param originalUnchanged Whether the original's XSLT is as the setup wrote it.
    void scoreHandWritten(final Map<String, Object> evalCase,
                          final List<String> made,
                          final String origin,
                          final String copyName,
                          final boolean originalUnchanged) {
        final Map<String, Object> expected = maps(evalCase.get("expected"));
        final Map<String, Object> route = maps(expected.get("route"));
        final List<String> inOrder = strings(route.get("calls_in_order"));
        int next = 0;
        for (final String tool : made) {
            if (next < inOrder.size() && inOrder.get(next).equals(tool)) {
                next++;
            }
        }
        check("route in order", next == inOrder.size(), "the calls didn't hold " + inOrder + " in order: from "
                                                        + (next < inOrder.size()
                ? inOrder.get(next)
                : "") + " on was missing (made: " + made + ")");
        for (final String forbidden : strings(route.get("forbidden_calls"))) {
            check("didn't call " + forbidden, !made.contains(forbidden), forbidden + " was called");
        }
        if (maps(expected.get("catalogue")).get("origin") instanceof final String wanted) {
            check("catalogue origin", origin != null && origin.startsWith(wanted), "the catalogue's origin is "
                                                                                   + origin + ", expected '"
                                                                                   + wanted + "...'");
        }
        final Map<String, Object> promoted = maps(expected.get("promoted"));
        if (promoted.get("pipeline") instanceof final String name) {
            check("copy named " + name, name.equals(copyName), "the copy is named " + copyName + ", expected "
                                                                + name);
        }
        if (Boolean.TRUE.equals(promoted.get("original_unchanged"))) {
            check("original unchanged", originalUnchanged, "the original's XSLT was changed");
        }
    }

    /// Scores what a build of several feeds kept and promoted (decided by the user, 2026-11-25), as
    /// `expected.several_feeds` gives it: `compared` (the user's confirmation of the feeds' differences kept in the
    /// build), `fields_only_in` (each field some feeds have and others lack, with those feeds), `dormant` (the feeds
    /// compare_feeds said are dormant), `agents_chosen` (the AGENTS conflicts answered, by key), `index_split`
    /// (`per_feed` or `shared`, as chosen), and `filter_feeds` (the feeds the promoted pipeline's filters select).
    ///
    /// @param evalCase    The case.
    /// @param kept        What the build's record keeps of the comparison ([stroom.mcp.impl.tools.BuildRecords
    ///                    #FEED_COMPARISON]); empty when none.
    /// @param compared    compare_feeds' last answer, or null.
    /// @param filterFeeds The feeds the promoted pipeline's filters select, or null when not promoted.
    @SuppressWarnings("unchecked")
    void scoreSeveralFeeds(final Map<String, Object> evalCase,
                           final Map<String, Object> kept,
                           final Map<String, Object> compared,
                           final List<String> filterFeeds) {
        final Map<String, Object> differences = compared == null
                ? null
                : maps(compared.get("differences"));
        final Map<String, Object> wanted = maps(maps(evalCase.get("expected")).get("several_feeds"));
        if (wanted.isEmpty()) {
            return;
        }
        if (Boolean.TRUE.equals(wanted.get("compared"))) {
            check("feeds compared and confirmed", kept.get("digest") != null, "the user's confirmation of how "
                                                                              + "the feeds differ wasn't kept");
        }
        if (wanted.get("fields_only_in") instanceof final Map<?, ?> fields) {
            final Object found = differences == null
                    ? null
                    : differences.get("fields_only_in");
            check("fields in some feeds only " + fields, String.valueOf(fields).equals(String.valueOf(found)),
                    "the differences shown gave fields in some feeds only as " + found + ", expected " + fields);
        }
        if (wanted.get("dormant") != null) {
            final List<String> expected = strings(wanted.get("dormant"));
            final List<String> found = compared == null
                    ? List.of()
                    : ((List<Object>) compared.getOrDefault("feeds", List.of())).stream()
                            .map(item -> (Map<String, Object>) item)
                            .filter(feed -> Boolean.TRUE.equals(feed.get("dormant")))
                            .map(feed -> String.valueOf(feed.get("feed")))
                            .toList();
            check("dormant " + expected, found.equals(expected), "compare_feeds said " + found + " are dormant, "
                                                                 + "expected " + expected);
        }
        for (final String key : strings(wanted.get("agents_chosen"))) {
            check("AGENTS conflict " + key + " answered", maps(kept.get("agents")).containsKey(key),
                    "no answer kept for the AGENTS conflict '" + key + "' (kept: " + kept.get("agents") + ")");
        }
        if (wanted.get("index_split") instanceof final String split) {
            check("index " + split, split.equals(kept.get("index")), "the index choice kept is "
                                                                     + kept.get("index") + ", expected " + split);
        }
        if (wanted.get("filter_feeds") != null) {
            final List<String> expected = strings(wanted.get("filter_feeds"));
            check("filters select " + expected, filterFeeds != null && filterFeeds.size() == expected.size()
                                                && filterFeeds.containsAll(expected),
                    "the promoted filters select " + filterFeeds + ", expected " + expected);
        }
    }

    /// Scores a copy's Events against the original's: each record's the same, the paths given aside.
    ///
    /// @param except    The paths the copy may add, below Event.
    /// @param differing The records (from 0) whose Events differ otherwise.
    void sameAsSetup(final List<String> except, final List<Integer> differing) {
        check("same as the setup except " + except, differing.isEmpty(), "the Events of record(s) " + differing
                                                                         + " differ from the original's beyond "
                                                                         + except);
    }

    /// Scores a skill case's run (a case of kind `skill`, [EvalCases#isSkill]) from the chat: every check given must
    /// hold, and the chat's run must not have failed.
    ///
    /// * `expected.tools_used`: each tool was called and the call accepted; `tools_not_used`: none of these was
    ///   called at all (e.g. a broken skill's steps not run against a missing index);
    /// * `expected.find_skills`: an accepted `find_skills` call read the skill by `name` (its name or its command's,
    ///   any case), with `arguments` holding each of these values (any case); `not_chosen`: no call read any of these
    ///   skills;
    /// * `expected.calls_with`: each a `tool` and what its arguments must hold, `contains` (any case), and its
    ///   result, `result_contains`: some call of it holds them all, e.g. the skill's steps followed with the values
    ///   bound;
    /// * `expected.proposal`: of the last proposal (the 'Review and apply' button a `propose_skill` call offered):
    ///   `folder`, where its skills doc is or is to be made (an Explorer path, with or without `/` or `System/`);
    ///   `skill`, the skill it proposes, by name; `parameters`, each a name (or a list of names, any one of them)
    ///   it lists; `not_parameters`, names it doesn't; `defaults`, a parameter's default by name (`a|b` for either
    ///   name); `steps_call`, tools its steps' example calls call; `steps_contain`, text in its `## Steps`;
    ///   `section_contains`, text by section (e.g. `event chains`); `check_problems`, how many problems
    ///   `check_skill` finds in it; `changed_sections`, the sections (`description` for its first paragraph) that
    ///   must differ from the skill before it, with `may_change` those that may, every other section and every
    ///   other skill of the doc unchanged; `history_change_contains`, text in its latest history entry's change;
    /// * `expected.instructions`: of the last AGENTS doc proposed (an instructions case's, `propose_instructions`'s
    ///   button): `folder`, where it is or is to be made; `fields`, the fields its `## Fields` lines give each key,
    ///   as the tools read them (a field, or the list in order); `keeps`, text of the doc before it that it still
    ///   holds; `check_problems`, how many problems `check_instructions` finds in it; `check_without`, the checks
    ///   (e.g. `plan_disagrees`) that find nothing in it;
    /// * `expected.no_proposal`: true when nothing may be proposed (e.g. a user who can't change the skills doc);
    /// * `expected.answer_must_say_all_of`, `answer_must_say_one_of` and `answer_must_not_say`: what the agent told
    ///   the user, as a question's are checked ([#scoreQuestion(Map, List)]).
    ///
    /// @param evalCase The case.
    /// @param run      What happened in the chat.
    void scoreSkill(final Map<String, Object> evalCase, final SkillRun run) {
        question = true;
        final Map<String, Object> expected = maps(evalCase.get("expected"));
        for (final String error : run.errors()) {
            questionCheck("chat ran", false, "the chat's run failed: " + error);
        }
        // A call refused (e.g. its tool group not on yet: "call it again") isn't a use; any attempt is, for a tool
        // that mustn't be used.
        final Set<String> called = new LinkedHashSet<>();
        final Set<String> tried = new LinkedHashSet<>();
        run.calls().forEach(call -> {
            tried.add(call.tool());
            if (!call.error()) {
                called.add(call.tool());
            }
        });
        note(run.calls().size() + " call(s): " + tried + "; " + run.proposals().size() + " proposal(s)");
        for (final SkillCall call : run.calls()) {
            note(call.tool() + (call.error()
                    ? " (failed)"
                    : "") + ": " + firstWords(call.text()));
        }
        run.proposals().forEach(proposal -> note((EvalCases.isInstructions(evalCase)
                ? "check_instructions: "
                : "check_skill: ") + firstWords(proposal.checked())));
        for (final String tool : strings(expected.get("tools_used"))) {
            questionCheck("used " + tool, called.contains(tool), tool + " was not used (used: " + called + ")");
        }
        for (final String tool : strings(expected.get("tools_not_used"))) {
            questionCheck("didn't use " + tool, !tried.contains(tool), tool + " was used");
        }
        scoreFindSkills(expected, run.calls());
        for (final Object item : listOf(expected.get("calls_with"))) {
            final Map<String, Object> wanted = maps(item);
            final String tool = String.valueOf(wanted.get("tool"));
            final List<String> contains = strings(wanted.get("contains"));
            final List<String> resultContains = strings(wanted.get("result_contains"));
            final boolean found = run.calls().stream()
                    .filter(call -> tool.equals(call.tool()) && !call.error())
                    .anyMatch(call -> holdsAll(call.arguments(), contains) && resultContains.stream()
                            .allMatch(text -> String.valueOf(call.text()).contains(text)));
            questionCheck(tool + " with " + contains + resultContains, found, "no accepted " + tool + " call's "
                                                                              + "arguments hold all of " + contains
                                                                              + (resultContains.isEmpty()
                    ? ""
                    : " with a result holding " + resultContains));
        }
        if (Boolean.TRUE.equals(expected.get("no_proposal"))) {
            questionCheck("no proposal", run.proposals().isEmpty(), run.proposals().size() + " proposal(s) made");
        }
        final Map<String, Object> instructions = maps(expected.get("instructions"));
        if (!instructions.isEmpty()) {
            if (run.proposals().isEmpty()) {
                questionCheck("proposal", false, "nothing was proposed");
            } else {
                scoreInstructions(instructions, run.proposals().getLast());
            }
        }
        final Map<String, Object> proposal = maps(expected.get("proposal"));
        if (!proposal.isEmpty()) {
            if (run.proposals().isEmpty()) {
                questionCheck("proposal", false, "nothing was proposed");
            } else {
                scoreProposal(proposal, run.proposals().getLast());
            }
        }
        final List<ToolUse> uses = run.calls().stream()
                .map(call -> new ToolUse(call.tool(), call.error(), Map.of(), call.text()))
                .toList();
        scoreWording(expected, uses, run.reply());
    }

    // The start of a tool's result, for the notes.
    private static String firstWords(final String text) {
        final String value = String.valueOf(text).replaceAll("\\s+", " ").strip();
        return value.length() > NOTE_CHARS
                ? value.substring(0, NOTE_CHARS) + "…"
                : value;
    }

    // The find_skills calls: the skill read by name with the arguments wanted, and none of the distractors.
    private void scoreFindSkills(final Map<String, Object> expected, final List<SkillCall> calls) {
        final List<SkillCall> reads = calls.stream()
                .filter(call -> FIND_SKILLS.equals(call.tool()) && !call.error()
                                && call.arguments().get("name") != null)
                .toList();
        final Map<String, Object> wanted = maps(expected.get(FIND_SKILLS));
        if (wanted.get("name") instanceof final String name) {
            final List<SkillCall> named = reads.stream()
                    .filter(call -> sameSkill(name, String.valueOf(call.arguments().get("name"))))
                    .toList();
            questionCheck("read " + name, !named.isEmpty(), "no find_skills call read '" + name + "' by name (read: "
                                                           + reads.stream().map(call -> call.arguments().get("name"))
                                                                   .toList() + ")");
            final Map<String, Object> arguments = maps(wanted.get("arguments"));
            if (!named.isEmpty() && !arguments.isEmpty()) {
                final boolean bound = named.stream().anyMatch(call -> arguments.entrySet().stream()
                        .allMatch(entry -> String.valueOf(maps(call.arguments().get("arguments"))
                                        .get(entry.getKey())).equalsIgnoreCase(String.valueOf(entry.getValue()))));
                questionCheck("read with " + arguments, bound, "no find_skills call of '" + name + "' had the "
                                                               + "arguments " + arguments);
            }
        }
        for (final String distractor : strings(expected.get("not_chosen"))) {
            questionCheck("didn't read " + distractor, reads.stream()
                    .noneMatch(call -> sameSkill(distractor, String.valueOf(call.arguments().get("name")))),
                    "find_skills read '" + distractor + "'");
        }
    }

    // The last AGENTS doc proposed against what the case expects of it: what the tools would read of it.
    private void scoreInstructions(final Map<String, Object> expected, final SkillProposal proposal) {
        if (expected.get("folder") instanceof final String folder) {
            questionCheck("proposal folder", explorerPath(folder).equalsIgnoreCase(explorerPath(proposal.folder())),
                    "the proposal is for folder " + proposal.folder() + ", expected " + folder);
        }
        final Map<String, InstructionRules.FieldRule> read = InstructionRules.fields(List.of(
                new AgentInstructions.InstructionDoc(null, "/", proposal.after(), List.of())));
        for (final Map.Entry<String, Object> entry : maps(expected.get("fields")).entrySet()) {
            final List<String> wanted = entry.getValue() instanceof List<?>
                    ? strings(entry.getValue())
                    : List.of(String.valueOf(entry.getValue()));
            final List<String> given = Optional.ofNullable(read.get(entry.getKey()))
                    .map(InstructionRules.FieldRule::fields)
                    .orElse(List.of());
            questionCheck(entry.getKey() + " field " + wanted, wanted.size() == 1
                    ? !given.isEmpty() && given.getFirst().equals(wanted.getFirst())
                    : given.equals(wanted), "its '## Fields' gives " + entry.getKey() + " as " + given
                                            + ", expected " + wanted);
        }
        for (final String text : strings(expected.get("keeps"))) {
            questionCheck("keeps " + text, proposal.after().contains(text), "the doc no longer holds '" + text + "'");
        }
        if (expected.get("check_problems") instanceof final Number problems) {
            final List<String> found = proposal.checked().lines()
                    .filter(line -> line.contains(CHECK_PROBLEM))
                    .map(String::strip)
                    .toList();
            questionCheck("check_instructions problems", found.size() == problems.intValue(), "check_instructions "
                                                                                           + "found " + found.size()
                                                                                           + " problem(s), "
                                                                                           + "expected " + problems
                                                                                           + ": " + found);
        }
        for (final String rule : strings(expected.get("check_without"))) {
            questionCheck("check_instructions without " + rule, !proposal.checked().contains("(" + rule + ")"),
                    "check_instructions found " + rule + ": " + firstWords(proposal.checked()));
        }
    }

    // The last proposal against what the case expects of it.
    private void scoreProposal(final Map<String, Object> expected, final SkillProposal proposal) {
        if (expected.get("folder") instanceof final String folder) {
            questionCheck("proposal folder", explorerPath(folder).equalsIgnoreCase(explorerPath(proposal.folder())),
                    "the proposal is for folder " + proposal.folder() + ", expected " + folder);
        }
        final String name = String.valueOf(expected.get("skill"));
        final SkillDoc after = SkillDoc.parse(proposal.after());
        final SkillDoc.Skill skill = after.skills().stream()
                .filter(found -> found.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        questionCheck("proposes " + name, skill != null, "the proposal holds no skill '" + name + "' (it holds "
                                                        + after.skills().stream().map(SkillDoc.Skill::name).toList()
                                                        + ")");
        if (skill == null) {
            return;
        }
        final List<String> parameters = skill.parameters().stream()
                .map(parameter -> parameter.name().toLowerCase(Locale.ROOT))
                .toList();
        for (final Object item : listOf(expected.get("parameters"))) {
            final List<String> names = strings(item instanceof List<?> ? item : List.of(item));
            questionCheck("parameter " + names, names.stream().anyMatch(one -> parameters.contains(
                    one.toLowerCase(Locale.ROOT))), "no parameter " + names + " (its parameters: " + parameters + ")");
        }
        for (final String unwanted : strings(expected.get("not_parameters"))) {
            questionCheck("no parameter " + unwanted, !parameters.contains(unwanted.toLowerCase(Locale.ROOT)),
                    "'" + unwanted + "' is a parameter, which the work defines");
        }
        for (final Map.Entry<String, Object> entry : maps(expected.get("defaults")).entrySet()) {
            final List<String> names = List.of(entry.getKey().toLowerCase(Locale.ROOT).split("\\|"));
            final SkillDoc.Parameter parameter = skill.parameters().stream()
                    .filter(found -> names.contains(found.name().toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .orElse(null);
            final String given = parameter == null
                    ? null
                    : parameter.defaultValue();
            questionCheck("default " + entry.getKey(), String.valueOf(entry.getValue()).equalsIgnoreCase(
                    String.valueOf(given)), "the default of " + entry.getKey() + " is " + given + ", expected "
                                            + entry.getValue());
        }
        final Set<String> stepTools = new LinkedHashSet<>();
        skill.calls().forEach(call -> stepTools.add(call.tool()));
        for (final String tool : strings(expected.get("steps_call"))) {
            questionCheck("a step calls " + tool, stepTools.contains(tool), "no step's example call calls " + tool
                                                                           + " (they call " + stepTools + ")");
        }
        final String steps = section(skill, STEPS);
        for (final String text : strings(expected.get("steps_contain"))) {
            questionCheck("steps hold " + text, steps.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)),
                    "its steps don't hold '" + text + "'");
        }
        for (final Map.Entry<String, Object> entry : maps(expected.get("section_contains")).entrySet()) {
            final String text = section(skill, entry.getKey());
            for (final String wanted : strings(entry.getValue())) {
                questionCheck(entry.getKey() + " holds " + wanted, text.toLowerCase(Locale.ROOT).contains(
                        wanted.toLowerCase(Locale.ROOT)), "its " + entry.getKey() + " doesn't hold '" + wanted + "'");
            }
        }
        if (expected.get("check_problems") instanceof final Number problems) {
            final List<String> found = proposal.checked().lines()
                    .filter(line -> line.contains(CHECK_PROBLEM))
                    .map(String::strip)
                    .toList();
            questionCheck("check_skill problems", found.size() == problems.intValue(), "check_skill found "
                                                                                     + found.size() + " problem(s), "
                                                                                     + "expected " + problems + ": "
                                                                                     + found);
        }
        if (expected.get("changed_sections") instanceof final List<?> changed) {
            scoreChangedOnly(name, strings(changed), strings(expected.get("may_change")), proposal, after, skill);
        }
        if (expected.get("history_change_contains") instanceof final String text) {
            final String change = after.latestEntry().map(SkillDoc.HistoryEntry::change).orElse("");
            questionCheck("history says " + text, change.toLowerCase(Locale.ROOT).contains(text.toLowerCase(
                    Locale.ROOT)), "the latest history entry's change is '" + change + "'");
        }
    }

    // A modification touches only what was asked: the sections it must change differ, those it may change aside;
    // every other section of the skill, and every other skill of the doc, is as it was.
    private void scoreChangedOnly(final String name,
                                  final List<String> changed,
                                  final List<String> mayChange,
                                  final SkillProposal proposal,
                                  final SkillDoc after,
                                  final SkillDoc.Skill skill) {
        final SkillDoc before = SkillDoc.parse(proposal.before());
        final SkillDoc.Skill old = before.skill(name).orElse(null);
        questionCheck("modifies " + name, old != null, "the doc had no skill '" + name + "' before the proposal");
        if (old == null) {
            return;
        }
        final Set<String> sections = new TreeSet<>(old.sections().keySet());
        sections.addAll(skill.sections().keySet());
        sections.add(DESCRIPTION);
        for (final String section : sections) {
            final boolean same = normalised(section(old, section)).equals(normalised(section(skill, section)));
            if (changed.contains(section)) {
                questionCheck("changed " + section, !same, "its " + section + " wasn't changed");
            } else if (!mayChange.contains(section)) {
                questionCheck("kept " + section, same, "its " + section + " was changed, which wasn't asked");
            }
        }
        for (final SkillDoc.Skill other : before.skills()) {
            if (!other.name().equalsIgnoreCase(name)) {
                questionCheck("kept skill " + other.name(), after.skill(other.name()).map(SkillDoc.Skill::text)
                        .map(EvalScore::normalised)
                        .filter(normalised(other.text())::equals)
                        .isPresent(), "the skill '" + other.name() + "' was changed or removed");
            }
        }
    }

    // A section of a skill by its heading (any case); `description` its first paragraph.
    private static String section(final SkillDoc.Skill skill, final String heading) {
        final String key = heading.toLowerCase(Locale.ROOT);
        return DESCRIPTION.equals(key)
                ? Objects.requireNonNullElse(skill.description(), "")
                : Objects.requireNonNullElse(skill.sections().get(key), "");
    }

    private static String normalised(final String text) {
        return text.strip().replaceAll("\\s+", " ");
    }

    // A path as a case or the explorer gives it, without the root: Hunts/Leavers.
    private static String explorerPath(final String path) {
        return String.valueOf(path).strip().replaceFirst("^/+", "").replaceFirst("^System(/|$)", "")
                .replaceFirst("/+$", "");
    }

    // A skill named by its name or its command's (any case).
    private static boolean sameSkill(final String name, final String given) {
        return SkillDoc.slug(name).equals(SkillDoc.slug(given));
    }

    // A call's arguments hold each text (any case), as JSON.
    private static boolean holdsAll(final Map<String, Object> arguments, final List<String> texts) {
        final String json = JsonUtil.writeValueAsString(arguments, false).toLowerCase(Locale.ROOT);
        return texts.stream().allMatch(text -> json.contains(text.toLowerCase(Locale.ROOT)));
    }

    // The answer call's findings against the case's matchers.
    private void scoreFindings(final Map<String, Object> expected, final ToolUse call) {
        if (expected.isEmpty()) {
            return;
        }
        final List<Map<String, Object>> findings = new ArrayList<>();
        if (call.result().get("findings") instanceof final List<?> given) {
            given.stream().filter(Map.class::isInstance).forEach(finding -> findings.add(maps(finding)));
        }
        final List<String> sourceWide = strings(call.result().get("source_wide"));
        final List<Map<String, Object>> doneToThem = new ArrayList<>();
        if (maps(call.result().get("done_to_them")).get("findings") instanceof final List<?> given) {
            given.stream().filter(Map.class::isInstance).forEach(finding -> doneToThem.add(maps(finding)));
        }
        for (final Object matcher : listOf(expected.get("must_include"))) {
            final Map<String, Object> wanted = maps(matcher);
            questionCheck("finding " + wanted, anyFinding(wanted, findings, sourceWide, doneToThem),
                    "no finding matched " + wanted);
        }
        for (final Object matcher : listOf(expected.get("must_not_include"))) {
            final Map<String, Object> unwanted = maps(matcher);
            questionCheck("no finding " + unwanted, !anyFinding(unwanted, findings, sourceWide, doneToThem),
                    "a finding matched " + unwanted);
        }
    }

    // The reply's wording; what it must say all of, in a reference run (no reply) against the tool results.
    private void scoreWording(final Map<String, Object> expected, final List<ToolUse> calls, final String reply) {
        final String text = reply != null
                ? reply
                : String.join("\n", calls.stream().filter(call -> !call.refused()).map(ToolUse::text).toList());
        for (final Object item : listOf(expected.get("answer_must_say_all_of"))) {
            final List<String> words = strings(item instanceof List<?> ? item : List.of(item));
            questionCheck("says one of " + words, words.stream().anyMatch(word -> text.toLowerCase(Locale.ROOT)
                    .contains(word.toLowerCase(Locale.ROOT))), (reply == null
                    ? "no reply: no tool result says any of "
                    : "the reply says none of ") + words);
        }
        final List<String> mustNot = strings(expected.get("answer_must_not_say"));
        final List<String> oneOf = strings(expected.get("answer_must_say_one_of"));
        if (mustNot.isEmpty() && oneOf.isEmpty()) {
            return;
        }
        if (reply == null) {
            note("the reply's wording wasn't checked: there is no reply");
            return;
        }
        final String said = reply.toLowerCase(Locale.ROOT);
        for (final String word : mustNot) {
            questionCheck("reply doesn't say " + word, !said.contains(word.toLowerCase(Locale.ROOT)),
                    "the reply says '" + word + "'");
        }
        if (!oneOf.isEmpty()) {
            questionCheck("reply says one of " + oneOf, oneOf.stream().anyMatch(word -> said.contains(
                    word.toLowerCase(Locale.ROOT))), "the reply says none of " + oneOf);
        }
    }

    private static boolean anyFinding(final Map<String, Object> matcher,
                                      final List<Map<String, Object>> findings,
                                      final List<String> sourceWide,
                                      final List<Map<String, Object>> doneToThem) {
        if (Boolean.TRUE.equals(matcher.get("source_wide"))) {
            return sourceWide.stream().anyMatch(line -> sourceWideMatches(matcher, line));
        }
        return (Boolean.TRUE.equals(matcher.get("done_to_them"))
                ? doneToThem
                : findings).stream().anyMatch(finding -> findingMatches(matcher, finding));
    }

    /// Whether a finding matches: each of the matcher's keys given must hold. `kind`, `feature` and `window`: equal
    /// (any case); `evidence_contains`: in its evidence; `near` (a date) and `days` (default 0): its window's first
    /// date within that many days of it; `window_in` (two dates): its window's first date from the one to the other,
    /// both included. `source_wide` chooses what is matched ([#scoreQuestion(Map, List)]).
    ///
    /// @param matcher The matcher.
    /// @param finding The finding.
    /// @return True if it matches.
    static boolean findingMatches(final Map<String, Object> matcher, final Map<String, Object> finding) {
        for (final String key : List.of("kind", "feature", "window")) {
            if (matcher.containsKey(key) && !String.valueOf(matcher.get(key)).equalsIgnoreCase(String.valueOf(
                    finding.get(key)))) {
                return false;
            }
        }
        if (matcher.get("evidence_contains") instanceof final String text
            && !String.valueOf(finding.get("evidence")).contains(text)) {
            return false;
        }
        return datesMatch(matcher, String.valueOf(finding.get("window")));
    }

    // A source-wide line matches on its words (evidence_contains) and the first date it holds (near, window_in).
    private static boolean sourceWideMatches(final Map<String, Object> matcher, final String line) {
        if (matcher.get("evidence_contains") instanceof final String text && !line.contains(text)) {
            return false;
        }
        return datesMatch(matcher, line);
    }

    // Whether the first date in the text is within the matcher's near and days, and window_in.
    private static boolean datesMatch(final Map<String, Object> matcher, final String text) {
        if (!matcher.containsKey("near") && !matcher.containsKey("window_in")) {
            return true;
        }
        final Matcher date = FIRST_DATE.matcher(text);
        if (!date.find()) {
            return false;
        }
        final LocalDate first = LocalDate.parse(date.group());
        if (matcher.get("near") != null) {
            final LocalDate near = LocalDate.parse(String.valueOf(matcher.get("near")));
            final long days = matcher.get("days") instanceof final Number number
                    ? number.longValue()
                    : 0;
            if (Math.abs(ChronoUnit.DAYS.between(near, first)) > days) {
                return false;
            }
        }
        if (matcher.get("window_in") instanceof final List<?> range && range.size() == 2) {
            return !first.isBefore(LocalDate.parse(String.valueOf(range.get(0))))
                   && !first.isAfter(LocalDate.parse(String.valueOf(range.get(1))));
        }
        return true;
    }

    private static List<?> listOf(final Object value) {
        return value instanceof final List<?> given
                ? given
                : List.of();
    }

    // Whether a call's rows were cut at the most a query may return (its result's `capped`).
    private static boolean capped(final ToolUse call) {
        return Boolean.TRUE.equals(call.result().get("capped"));
    }

    // Why neither a call's rows nor those of the latest table call on the file it wrote hold what is wanted (said of
    // the table call's, if there is one), or null if either does.
    private static String rowsMismatch(final List<List<Object>> wanted,
                                       final ToolUse call,
                                       final Map<ToolUse, ToolUse> tabled,
                                       final boolean ordered,
                                       final double tolerance,
                                       final boolean extraRows) {
        final String own = mismatch(wanted, kept(wanted, rows(call.result().get("rows")), extraRows, tolerance),
                ordered, tolerance);
        final ToolUse table = tabled.get(call);
        if (own == null || table == null) {
            return own;
        }
        return mismatch(wanted, kept(wanted, rows(table.result().get("rows")), extraRows, tolerance), ordered,
                tolerance);
    }

    // A call's rows, or where the answer allows extra rows (extra_rows: true, as when an agent looks wider than the
    // question to be sure), only those that match an expected row.
    private static List<List<Object>> kept(final List<List<Object>> wanted,
                                           final List<List<Object>> rows,
                                           final boolean extraRows,
                                           final double tolerance) {
        return extraRows
                ? rows.stream().filter(row -> wanted.stream().anyMatch(want -> rowMatches(want, row, tolerance)))
                        .toList()
                : rows;
    }

    // The latest accepted table call on the file each accepted call of the tool wrote, after it and before any
    // later call of the tool wrote the same file, by the call.
    private static Map<ToolUse, ToolUse> tabled(final List<ToolUse> calls, final String tool) {
        final Map<ToolUse, ToolUse> tabled = new IdentityHashMap<>();
        final Map<Object, ToolUse> writers = new HashMap<>();
        for (final ToolUse call : calls) {
            if (call.refused()) {
                continue;
            }
            final Object out = call.result().get("out");
            if (tool.equals(call.name()) && out != null) {
                writers.put(out, call);
            } else if (TABLE_TOOL.equals(call.name()) && writers.get(call.result().get("path")) instanceof
                    final ToolUse writer) {
                tabled.put(writer, call);
            }
        }
        return tabled;
    }

    // The total groups the calls give: the answer call's total_groups (or its row count, if it wasn't cut short),
    // then each other accepted call's total_groups.
    private static List<Object> totalGroups(final ToolUse answerCall, final List<ToolUse> accepted) {
        final List<Object> given = new ArrayList<>();
        final Map<String, Object> result = answerCall.result();
        if (result.get("total_groups") != null) {
            given.add(result.get("total_groups"));
        } else if (!Boolean.TRUE.equals(result.get("truncated"))) {
            given.add(rows(result.get("rows")).size());
        }
        for (final ToolUse call : accepted) {
            if (call != answerCall && call.result().get("total_groups") != null) {
                given.add(call.result().get("total_groups"));
            }
        }
        return given;
    }

    // How many of the wanted rows a call's rows (or those of the latest table call on the file it wrote) hold, in any
    // order: the more of the two.
    private static int held(final List<List<Object>> wanted,
                            final ToolUse call,
                            final Map<ToolUse, ToolUse> tabled,
                            final double tolerance) {
        final int own = held(wanted, rows(call.result().get("rows")), tolerance);
        final ToolUse table = tabled.get(call);
        return table == null
                ? own
                : Math.max(own, held(wanted, rows(table.result().get("rows")), tolerance));
    }

    // How many of the wanted rows the rows hold, in any order, each row matched once.
    private static int held(final List<List<Object>> wanted,
                            final List<List<Object>> rows,
                            final double tolerance) {
        final List<List<Object>> left = new ArrayList<>(rows);
        int held = 0;
        for (final List<Object> want : wanted) {
            final List<Object> found = left.stream()
                    .filter(row -> rowMatches(want, row, tolerance))
                    .findFirst()
                    .orElse(null);
            if (found != null) {
                left.remove(found);
                held++;
            }
        }
        return held;
    }

    // Why the rows don't hold what is wanted, or null if they do.
    static String mismatch(final List<List<Object>> wanted,
                           final List<List<Object>> rows,
                           final boolean ordered,
                           final double tolerance) {
        if (rows.size() != wanted.size()) {
            return "were " + rows.size() + ", expected " + wanted.size() + ": " + rows;
        }
        final List<List<Object>> left = new ArrayList<>(rows);
        for (int i = 0; i < wanted.size(); i++) {
            final List<Object> want = wanted.get(i);
            if (ordered) {
                if (!rowMatches(want, rows.get(i), tolerance)) {
                    return "row " + (i + 1) + " was " + rows.get(i) + ", expected " + want;
                }
            } else {
                final List<Object> found = left.stream()
                        .filter(row -> rowMatches(want, row, tolerance))
                        .findFirst()
                        .orElse(null);
                if (found == null) {
                    return "had no row " + want + " (rows: " + rows + ")";
                }
                left.remove(found);
            }
        }
        return null;
    }

    // Each wanted value is one of the row's values, each value matched once.
    private static boolean rowMatches(final List<Object> want, final List<Object> row, final double tolerance) {
        final List<Object> left = new ArrayList<>(row);
        for (final Object value : want) {
            final Object found = left.stream()
                    .filter(candidate -> valueMatches(value, candidate, tolerance))
                    .findFirst()
                    .orElse(null);
            if (found == null) {
                return false;
            }
            left.remove(found);
        }
        return true;
    }

    private static boolean valueMatches(final Object want, final Object value, final double tolerance) {
        if (want instanceof final Number number) {
            final Double actual = value instanceof final Number given
                    ? Double.valueOf(given.doubleValue())
                    : parse(value);
            if (actual == null) {
                return false;
            }
            final double expected = number.doubleValue();
            final double allowed = want instanceof Double || want instanceof Float
                    ? Math.max(0.05, Math.abs(expected) * tolerance)
                    : Math.abs(expected) * tolerance;
            return Math.abs(actual - expected) <= allowed;
        }
        final String expected = String.valueOf(want);
        final String actual = String.valueOf(value);
        return expected.equals(actual)
               || (DAY.matcher(expected).matches() && actual.startsWith(expected))
               || sameInstant(expected, actual);
    }

    // Whether two ISO 8601 times with offsets are the same instant, as a time given in the user's zone
    // (2026-09-17T20:05:00.000+10:00) is the expected UTC one (2026-09-17T10:05:00.000Z).
    private static boolean sameInstant(final String expected, final String actual) {
        if (expected.length() < 20 || actual.length() < 20 || !DAY.matcher(expected.substring(0, 10)).matches()) {
            return false;
        }
        try {
            return OffsetDateTime.parse(expected).toInstant().equals(OffsetDateTime.parse(actual).toInstant());
        } catch (final DateTimeParseException e) {
            return false;
        }
    }

    private static Double parse(final Object value) {
        try {
            return value == null
                    ? null
                    : Double.valueOf(String.valueOf(value).replace(",", ""));
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static List<List<Object>> rows(final Object value) {
        final List<List<Object>> rows = new ArrayList<>();
        if (value instanceof final List<?> list) {
            for (final Object row : list) {
                if (row instanceof final List<?> cells) {
                    rows.add(new ArrayList<>(cells));
                }
            }
        }
        return rows;
    }

    private void questionCheck(final String check, final boolean passed, final String problem) {
        final Map<String, Object> made = new LinkedHashMap<>();
        made.put("check", check);
        made.put("passed", passed);
        if (!passed) {
            made.put("problem", problem);
            problems.add(problem);
        }
        questionChecks.add(made);
    }

    /// @return The score as `print_score` prints it, with its problems.
    String summary() {
        final StringBuilder sb = new StringBuilder(caseId).append(": ").append(passed()
                        ? "PASS"
                        : "FAIL")
                .append(" stage1=").append(stage1).append(" indexed=").append(indexed)
                .append(" events=").append(validEvents).append('/').append(events)
                .append(" types=").append(eventTypes);
        if (dataShare != null) {
            sb.append(" data_share=").append(dataShare.get("share"));
        }
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
        map.put("type_ids", List.copyOf(typeIds));
        map.put("missing_types", missingTypes);
        map.put("missing_paths", missingPaths);
        map.put("problems", List.copyOf(problems));
        if (!sourceTypeChecks.isEmpty()) {
            map.put("source_type_checks", List.copyOf(sourceTypeChecks));
        }
        if (!questionChecks.isEmpty()) {
            map.put("question_checks", List.copyOf(questionChecks));
        }
        if (!catalogueChecks.isEmpty()) {
            map.put("catalogue_checks", List.copyOf(catalogueChecks));
        }
        if (dataShare != null) {
            map.put("typed_checks", List.copyOf(typedChecks));
            map.put("data_share", dataShare);
            map.put("data_shares", dataShares);
            map.put("typed_paths", typedPaths);
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

    // The event's EventDetail/TypeId, or "" if it has none.
    private static String typeId(final Element event) {
        return pathValues(List.of(event), "EventDetail/TypeId").stream().findFirst().orElse("");
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

    // Every text an event holds: its elements' own text and its Data elements' Values, each stripped.
    private static Set<String> allTexts(final List<Element> events) {
        final Set<String> texts = new LinkedHashSet<>();
        for (final Element event : events) {
            final NodeList all = event.getElementsByTagNameNS("*", "*");
            for (int i = 0; i < all.getLength(); i++) {
                final Element element = (Element) all.item(i);
                texts.add(directText(element).strip());
                if ("Data".equals(element.getLocalName())) {
                    texts.add(element.getAttribute("Value").strip());
                }
            }
        }
        return texts;
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

    // --------------------------------------------------------------------------------

    /// One tool call of a question's answer.
    ///
    /// @param name    The tool.
    /// @param refused True if it was refused or failed.
    /// @param result  Its result as JSON (for esql_query, its `rows`, `truncated`, `total_groups`...; for run_stroomql,
    ///                its table's `columns` and `rows`, its `query` and the `out` file it wrote; for table, the
    ///                `columns` and `rows` it gave and the `path` it read); empty if none.
    /// @param text    Its text, for saying why it was refused.
    record ToolUse(String name, boolean refused, Map<String, Object> result, String text) {

    }

    // --------------------------------------------------------------------------------

    /// The values an EventDetail holds, and how many of them are in Data.
    ///
    /// @param data How many are in Data.
    /// @param all  How many there are.
    record DetailValues(int data, int all) {

        /// @param other More values.
        /// @return These and the other's together.
        DetailValues plus(final DetailValues other) {
            return new DetailValues(data + other.data, all + other.all);
        }

        /// @return The share in Data, 0 for no values at all.
        double share() {
            return all == 0
                    ? 0
                    : (double) data / all;
        }

        /// @return The share in Data, to two places.
        double rounded() {
            return Math.round(share() * 100) / 100.0;
        }

        /// @return The share and its counts, as the score holds them.
        Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("share", rounded());
            map.put("data", data);
            map.put("values", all);
            return map;
        }
    }

    // --------------------------------------------------------------------------------

    /// What a case's setup left for the case's own build (a promoted build of a source type group).
    ///
    /// @param group    The setup's source type group.
    /// @param pipeline Its pipeline's UUID.
    /// @param index    Its index's UUID.
    /// @param snapshot Its pipeline, XSLT and index as the setup left them, by name, each as text to compare.
    record Before(String group, String pipeline, String index, Map<String, String> snapshot) {

    }

    // --------------------------------------------------------------------------------

    /// A tool call in a skill case's chat.
    ///
    /// @param tool      The tool.
    /// @param arguments Its arguments.
    /// @param error     True if it failed or wasn't run.
    /// @param text      What the model was given of its result.
    record SkillCall(String tool, Map<String, Object> arguments, boolean error, String text) {

    }

    /// A proposal in a skill case's chat: a 'Review and apply' button a `propose_skill` call offered.
    ///
    /// @param action  The button's action (`apply_documentation_change`, or `create_instructions` for a new doc).
    /// @param folder  The Explorer path of the folder its skills doc is in, or is to be made in.
    /// @param before  The doc's text before it (empty for a new doc).
    /// @param after   The doc's text it proposes.
    /// @param checked What `check_skill` said of each skill it adds or changes.
    record SkillProposal(String action, String folder, String before, String after, String checked) {

    }

    /// What happened in a skill case's chat, from the request on.
    ///
    /// @param calls     The tool calls, in order.
    /// @param reply     The model's replies, joined.
    /// @param proposals The proposals, in order.
    /// @param errors    The run's errors, if it failed.
    record SkillRun(List<SkillCall> calls, String reply, List<SkillProposal> proposals, List<String> errors) {

        /// Copies the lists.
        SkillRun {
            calls = List.copyOf(calls);
            proposals = List.copyOf(proposals);
            errors = List.copyOf(errors);
        }
    }
}
