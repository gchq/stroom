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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.EventSchema.Child;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Source notes: what the user's vendor or event reference documentation says about a source, kept so the tools use
/// it.
///
/// record_source_notes keeps the documents themselves verbatim (Documentation docs `<source> reference - <title>`,
/// to read and search later) and the notes condensed from them: a field dictionary (each source field's meaning, its
/// codes, the event-logging element it belongs in) and an event catalogue (each kind of event, how a record shows
/// it, and what it is in event-logging terms). The notes doc carries them as a block ([#block]) the tools read back
/// ([#readNotes]):
///
/// - draft_translation_mapping builds its draft from them ([#applyToDraft]): fields where the dictionary puts them,
///   one rule per catalogued event with its action element, TypeId, description, action and outcome;
/// - build_translation_xslt checks a mapping against the catalogue, so a rule that contradicts it is reported;
/// - write_documentation lists each source field with the documentation's meaning and codes.
///
/// The notes are maps and lists as JSON reads them: `fields` (each `field`, `meaning`, `type`, `example`,
/// `event_logging_path`, `values`), `events` (each `event`, `description`, `event_detail`, `type_id`, `field`,
/// `value`, `action`, `success`), `documents` and `references`.
public final class SourceNotes {

    /// The end of a notes doc's name: `<source> source notes`.
    public static final String NOTES_SUFFIX = " source notes";
    /// Between the source and the title in a kept document's name: `<source> reference - <title>`.
    public static final String REFERENCE_INFIX = " reference - ";

    private static final Pattern BLOCK = Pattern.compile("<!-- stroom-mcp source notes\n(.*?)\n-->", Pattern.DOTALL);
    private static final Pattern SUCCESS = Pattern.compile(
            "\\b(success|succeeded|successful|ok|allow(ed)?|accept(ed)?|granted|pass(ed)?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FAILURE = Pattern.compile(
            "\\b(fail(ed|ure)?|denied|den(y|ies)|reject(ed)?|block(ed)?|error|invalid|locked)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_NAME = Pattern.compile("[^A-Za-z0-9]+");
    private static final Set<String> NOT_ACTIONS = Set.of("TypeId", "Description", "Classification", "Purpose");
    private static final Set<String> ALLOW_OR_DENY = Set.of("allow", "allowed", "accept", "permit", "deny",
            "denied", "block", "blocked", "drop", "reject", "traffic");
    private static final String UNKNOWN = "Unknown";
    private static final String EVENT_DETAIL = "EventDetail/";

    private SourceNotes() {
        // Static methods only.
    }

    /// @param notes The notes.
    /// @return The block a notes doc carries them in, for the tools to read back: an HTML comment holding them as
    /// JSON, indented by one.
    public static String block(final Map<String, Object> notes) {
        return "<!-- stroom-mcp source notes\n" + ToolJson.dumps(notes, 1) + "\n-->";
    }

    /// @param text A notes doc's text, or null.
    /// @return The notes its block holds, or null when it has none, or it isn't a JSON object.
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readNotes(final String text) {
        final Matcher found = BLOCK.matcher(NullSafe.string(text));
        if (!found.find()) {
            return null;
        }
        try {
            return ToolJson.loads(found.group(1)) instanceof final Map<?, ?> notes
                    ? (Map<String, Object>) notes
                    : null;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /// @param values A field's codes and what each means, from the documentation.
    /// @return The codes as Outcome/Success values (`true` or `false`), when the meaning of every code says
    /// success or failure; else null.
    public static Map<String, String> outcomeMap(final Map<String, String> values) {
        final Map<String, String> out = new LinkedHashMap<>();
        values.forEach((code, meaning) -> {
            if (FAILURE.matcher(meaning).find()) {
                out.put(code, "false");
            } else if (SUCCESS.matcher(meaning).find()) {
                out.put(code, "true");
            }
        });
        return !out.isEmpty() && out.size() == values.size()
                ? out
                : null;
    }

    /// Why an event's `event_detail` can't be its action element: it must be an EventDetail action element that
    /// takes Data (where the draft puts the event's other fields), so Network needs its action (Network/Permit).
    ///
    /// @param schema The event-logging schema.
    /// @param detail The event's `event_detail`, e.g. `Authenticate` or `Network/Permit`, or null.
    /// @return Why it can't be, or null when it can (or none is given).
    public static String detailProblem(final EventSchema schema, final String detail) {
        final String given = ToolText.strip(NullSafe.string(detail).strip(), "/");
        if (given.isEmpty()) {
            return null;
        }
        final List<String> top = schema.children(schema.resolve("EventDetail").getLast().decl()).stream()
                .map(Child::name)
                .filter(name -> !NOT_ACTIONS.contains(name))
                .toList();
        List<Child> chain;
        try {
            final List<Child> resolved = schema.resolve("EventDetail/" + given);
            // Below EventDetail.
            chain = resolved.subList(1, resolved.size());
        } catch (final IllegalArgumentException e) {
            chain = null;
        }
        if (chain == null || chain.isEmpty() || NOT_ACTIONS.contains(chain.getFirst().name())) {
            final String hint = ALLOW_OR_DENY.contains(given.split("/")[0].toLowerCase(Locale.ROOT))
                    ? " A connection allowed or denied is Network/Permit or Network/Deny."
                    : "";
            return "'" + given + "' is not an EventDetail action element." + hint + " The action elements: "
                   + String.join(", ", top);
        }
        List<String> below = schema.children(chain.getLast().decl()).stream().map(Child::name).toList();
        if (!below.contains("Data") || chain.stream().map(Child::name).toList().equals(List.of("Network"))) {
            // Network's action is an element of its own (Permit, Deny, Connect...); v4 lets Network hold Data as
            // well.
            below = below.stream().filter(name -> !"Data".equals(name)).toList();
            return "'" + given + "' needs its action below it: " + String.join(", ", below.stream()
                    .limit(12)
                    .map(name -> given + "/" + name)
                    .toList());
        }
        return null;
    }

    /// What makes an event catalogue unusable for drafting: an action element the schema doesn't have, and events a
    /// record can't tell apart (the same field and value).
    ///
    /// @param events      The catalogue's events.
    /// @param detailCheck Why an event's `event_detail` can't be its action element ([#detailProblem]), or null
    ///                    when the schema can't be read (the events are still checked for twins).
    /// @return The problems, each naming the event; empty when it can be drafted from.
    public static List<String> catalogueProblems(final List<Map<String, Object>> events,
                                                 final Function<String, String> detailCheck) {
        final List<String> problems = new ArrayList<>();
        final Map<List<String>, String> seen = new LinkedHashMap<>();
        for (final Map<String, Object> event : events) {
            final String label = firstTruthy(event.get("event"), event.get("value"), "?");
            final String problem = detailCheck == null
                    ? null
                    : detailCheck.apply(text(event.get("event_detail")));
            if (NullSafe.isNonEmptyString(problem)) {
                problems.add(label + ": " + problem);
            }
            if (JsonValues.truthy(event.get("field")) && hasValue(event.get("value"))) {
                final List<String> key = key(event);
                if (seen.containsKey(key)) {
                    problems.add(seen.get(key) + " and " + label + " are both " + key.get(0) + "=" + key.get(1)
                                 + ": a record can't show which it is. Give each the field and value that tell "
                                 + "them apart (e.g. action=ALLOW and action=DENY), or record them as one event");
                } else {
                    seen.put(key, label);
                }
            }
        }
        return problems;
    }

    /// The draft mapping rebuilt from the notes where they speak: fields the dictionary places, one rule per
    /// catalogued event the sample's fields can show. Where the catalogue can't be followed (an action element the
    /// schema doesn't have, Unknown, events with the same field and value), the rules the sample's own values gave
    /// are kept. For the draft mapping (draft_translation_mapping), which drafts `mapping` from the sample first.
    ///
    /// @param mapping     The draft: `common` (field entries, each `path` and `field` or `value`) and `events`
    ///                    (rules, each `name`, `when` and `fields`, the one without `when` last). Changed in place.
    /// @param names       The sample's field names.
    /// @param values      Each field's values in the sample.
    /// @param notes       The source notes ([#readNotes]).
    /// @param detailCheck Why an `event_detail` can't be an action element ([#detailProblem]), or null.
    /// @return What was applied, and what the sample and notes disagree on: `fields`, `events`, `not_in_sample`,
    /// `not_in_catalogue` and `from_sample`.
    @SuppressWarnings("unchecked")
    public static Map<String, Object> applyToDraft(final Map<String, Object> mapping,
                                                   final List<String> names,
                                                   final Map<String, List<String>> values,
                                                   final Map<String, Object> notes,
                                                   final Function<String, String> detailCheck) {
        final List<Map<String, Object>> common = (List<Map<String, Object>>) mapping.get("common");
        final List<Map<String, Object>> drafted = (List<Map<String, Object>>) mapping.get("events");
        final List<Map<String, Object>> sampled = drafted.stream()
                .filter(rule -> JsonValues.truthy(rule.get("when")))
                .toList();
        final Map<String, Object> applied = new LinkedHashMap<>();
        final List<String> appliedFields = new ArrayList<>();
        final List<String> appliedEvents = new ArrayList<>();
        final List<String> notInSample = new ArrayList<>();
        final List<String> notInCatalogue = new ArrayList<>();
        final List<String> fromSample = new ArrayList<>();
        applied.put("fields", appliedFields);
        applied.put("events", appliedEvents);
        applied.put("not_in_sample", notInSample);
        applied.put("not_in_catalogue", notInCatalogue);
        applied.put("from_sample", fromSample);

        final Map<String, Map<String, Object>> byField = new LinkedHashMap<>();
        for (final Map<String, Object> field : JsonValues.maps(notes.get("fields"))) {
            if (names.contains(text(field.get("field")))) {
                byField.put(text(field.get("field")), field);
            }
        }
        final List<Map<String, Object>> detailFields = new ArrayList<>();
        byField.forEach((name, note) -> {
            final String path = ToolText.strip(text(note.get("event_logging_path")).strip(), "/");
            if (path.isEmpty()) {
                return;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", path);
            entry.put("field", name);
            if (path.endsWith("Outcome/Success") && note.get("values") instanceof final Map<?, ?> codes
                && !codes.isEmpty()) {
                final Map<String, String> mapped = outcomeMap(strings(codes));
                if (mapped != null) {
                    entry.put("map", mapped);
                }
            }
            if (path.startsWith(EVENT_DETAIL) && !"EventDetail/TypeId".equals(path)) {
                detailFields.add(entry);
            } else {
                // The dictionary's word wins over a guess from the field's name.
                common.removeIf(existing -> path.equals(existing.get("path")) || name.equals(existing.get("field")));
                common.add(entry);
            }
            appliedFields.add(name + " -> " + path);
        });

        final List<Map<String, Object>> events = JsonValues.maps(notes.get("events")).stream()
                .filter(event -> names.contains(text(event.get("field"))) && hasValue(event.get("value")))
                .toList();
        if (events.isEmpty()) {
            return applied;
        }
        final Set<List<String>> seen = new LinkedHashSet<>();
        events.forEach(event -> seen.add(key(event)));
        final Set<String> taken = new LinkedHashSet<>();
        common.forEach(entry -> taken.add(NullSafe.get(entry.get("field"), String::valueOf)));
        detailFields.forEach(entry -> taken.add(text(entry.get("field"))));
        events.forEach(event -> taken.add(text(event.get("field"))));
        final List<String> rest = names.stream()
                .filter(name -> !taken.contains(name) && !NullSafe.isEmptyCollection(values.get(name)))
                .toList();
        final Set<List<String>> twins = new LinkedHashSet<>();
        for (final List<String> key : seen) {
            if (events.stream().filter(event -> key(event).equals(key)).count() > 1) {
                twins.add(key);
            }
        }

        final List<Map<String, Object>> rules = new ArrayList<>();
        for (final Map<String, Object> event : events) {
            String detail = ToolText.strip(NullSafe.nonBlankStringElse(text(event.get("event_detail")), UNKNOWN)
                    .strip(), "/");
            if (detail.isEmpty()) {
                detail = UNKNOWN;
            }
            final List<String> key = key(event);
            final String why = why(twins.contains(key), detail, detailCheck);
            if (why != null) {
                // The sample's values say more than the catalogue here: keep the rules they gave, when they name an
                // action.
                final List<Map<String, Object>> kept = sampled.stream()
                        .filter(rule -> covers(rule, key) && !namesOnlyUnknown(rule))
                        .toList();
                if (!kept.isEmpty()) {
                    final List<Map<String, Object>> fresh = kept.stream().filter(rule -> !rules.contains(rule))
                            .toList();
                    rules.addAll(fresh);
                    if (!fresh.isEmpty()) {
                        fromSample.add(key.get(0) + "=" + key.get(1) + ": " + why + ", so the sample's rules are "
                                       + "kept (" + String.join("; ", fresh.stream()
                                .map(rule -> rule.get("name") + ": " + String.join(", ", new TreeSet<>(detailOf(rule))))
                                .toList()) + ")");
                    }
                    continue;
                }
                if (!UNKNOWN.equals(detail) && detailCheck != null && detailCheck.apply(detail) != null) {
                    detail = UNKNOWN;
                }
            }
            rules.add(rule(event, key, detail, common, detailFields, sampled, rest));
            final boolean shown = NullSafe.list(values.get(key.get(0))).contains(key.get(1));
            appliedEvents.add(firstTruthy(event.get("event"), key.get(1), "") + " (" + key.get(0) + "=" + key.get(1)
                              + ") -> " + detail + (shown
                    ? ""
                    : ", not in the sample"));
            if (!shown) {
                notInSample.add(key.get(0) + "=" + key.get(1));
            }
        }
        final Set<String> catalogued = new LinkedHashSet<>();
        events.forEach(event -> catalogued.add(text(event.get("field"))));
        for (final String field : catalogued) {
            for (final String value : new LinkedHashSet<>(NullSafe.list(values.get(field)))) {
                if (!seen.contains(List.of(field, value))) {
                    notInCatalogue.add(field + "=" + value);
                }
            }
        }
        // Records of values the catalogue doesn't list keep the sample's rule when it names an action, not Unknown.
        for (final Map<String, Object> rule : sampled) {
            if (!rules.contains(rule) && !namesOnlyUnknown(rule) && seen.stream().noneMatch(key -> covers(rule, key))) {
                rules.add(rule);
            }
        }
        final Map<String, Object> other = drafted.stream()
                .filter(rule -> !JsonValues.truthy(rule.get("when")))
                .findFirst()
                .orElse(null);
        final Set<String> namesUsed = new LinkedHashSet<>();
        for (final Map<String, Object> rule : rules) {
            String name = text(rule.get("name"));
            while (namesUsed.contains(name)) {
                name = name + "_";
            }
            rule.put("name", name);
            namesUsed.add(name);
        }
        final List<Map<String, Object>> all = new ArrayList<>(rules);
        if (other != null) {
            all.add(other);
        }
        mapping.put("events", all);
        return applied;
    }

    /// Several notes docs read as one: fields by name (the first of each), events in order.
    ///
    /// @param notes The notes of each doc.
    /// @return `fields` and `events`.
    public static Map<String, Object> merged(final List<Map<String, Object>> notes) {
        final Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        final List<Map<String, Object>> events = new ArrayList<>();
        for (final Map<String, Object> note : NullSafe.list(notes)) {
            JsonValues.maps(note.get("fields")).forEach(field -> fields.putIfAbsent(text(field.get("field")), field));
            events.addAll(JsonValues.maps(note.get("events")));
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("fields", new ArrayList<>(fields.values()));
        out.put("events", events);
        return out;
    }

    /// Where a mapping contradicts the catalogue: a catalogued event's records meet a rule writing another action
    /// element or TypeId than the documentation gives.
    ///
    /// @param mapping The mapping, as JSON maps and lists.
    /// @param notes   The notes ([#merged]).
    /// @return The contradictions.
    public static List<String> checkMapping(final Map<String, Object> mapping, final Map<String, Object> notes) {
        final List<String> problems = new ArrayList<>();
        final Map<Object, Map<String, Object>> common = new LinkedHashMap<>();
        JsonValues.maps(mapping.get("common")).forEach(entry -> common.put(entry.get("path"), entry));
        for (final Map<String, Object> event : JsonValues.maps(notes.get("events"))) {
            if (!JsonValues.truthy(event.get("field")) || !hasValue(event.get("value"))) {
                continue;
            }
            final String value = ToolText.str(event.get("value"));
            final String label = firstTruthy(event.get("event"), value, value) + " (" + ToolText.str(event.get("field"))
                                 + "=" + value + ")";
            final Map<String, Object> rule = ruleFor(JsonValues.maps(mapping.get("events")),
                    ToolText.str(event.get("field")), value);
            if (rule == null) {
                problems.add(label + ": no rule takes it");
                continue;
            }
            final Set<String> details = new TreeSet<>();
            for (final Map<String, Object> field : JsonValues.maps(rule.get("fields"))) {
                final String path = text(field.get("path"));
                if (path.startsWith(EVENT_DETAIL) && path.chars().filter(c -> c == '/').count() >= 2) {
                    details.add(path.split("/")[1]);
                }
            }
            final String want = ToolText.strip(text(event.get("event_detail")).strip(), "/").split("/")[0];
            if (!want.isEmpty() && !UNKNOWN.equals(want) && !details.contains(want)) {
                problems.add(label + ": the documentation says " + want + "; rule '" + text(rule.get("name"))
                             + "' writes " + (details.isEmpty()
                        ? "no action element"
                        : String.join(", ", details)));
            }
            if (JsonValues.truthy(event.get("type_id"))) {
                final Map<String, Object> typeId = JsonValues.maps(rule.get("fields")).stream()
                        .filter(field -> "EventDetail/TypeId".equals(field.get("path")))
                        .findFirst()
                        .orElse(common.get("EventDetail/TypeId"));
                if (typeId != null && typeId.get("value") != null
                    && !ToolText.str(typeId.get("value")).equals(ToolText.str(event.get("type_id")))) {
                    problems.add(label + ": the documentation's TypeId is '" + ToolText.str(event.get("type_id"))
                                 + "'; rule '" + text(rule.get("name")) + "' writes '"
                                 + ToolText.str(typeId.get("value")) + "'");
                }
            }
        }
        return problems;
    }

    // The first rule a record holding only field=value would meet (equals and one_of tests; a rule testing anything
    // else is passed over, as it cannot be judged from the catalogue).
    private static Map<String, Object> ruleFor(final List<Map<String, Object>> rules,
                                               final String field,
                                               final String value) {
        for (final Map<String, Object> rule : rules) {
            final List<Map<String, Object>> when = JsonValues.maps(rule.get("when"));
            if (when.isEmpty()) {
                return rule;
            }
            boolean ok = true;
            for (final Map<String, Object> condition : when) {
                if (!Objects.equals(condition.get("field"), field)) {
                    ok = false;
                    break;
                }
                if (condition.get("equals") != null && !ToolText.str(condition.get("equals")).equals(value)) {
                    ok = false;
                } else if (condition.get("one_of") != null && (!(condition.get("one_of") instanceof final List<?> one)
                                                               || one.stream().map(ToolText::str)
                                                                       .noneMatch(value::equals))) {
                    ok = false;
                } else if (condition.get("equals") == null && condition.get("one_of") == null) {
                    ok = false;
                }
            }
            if (ok) {
                return rule;
            }
        }
        return null;
    }

    // Why the catalogue can't be followed for an event, or null when it can.
    private static String why(final boolean twin, final String detail, final Function<String, String> detailCheck) {
        if (twin) {
            return "the catalogue has more than one event for it";
        }
        final String problem = detailCheck != null && !UNKNOWN.equals(detail)
                ? detailCheck.apply(detail)
                : null;
        if (NullSafe.isNonEmptyString(problem)) {
            return problem;
        }
        return UNKNOWN.equals(detail)
                ? "the catalogue gives no action element"
                : null;
    }

    // One rule for a catalogued event.
    private static Map<String, Object> rule(final Map<String, Object> event,
                                            final List<String> key,
                                            final String detail,
                                            final List<Map<String, Object>> common,
                                            final List<Map<String, Object>> detailFields,
                                            final List<Map<String, Object>> sampled,
                                            final List<String> rest) {
        final List<Map<String, Object>> fields = new ArrayList<>();
        if (JsonValues.truthy(event.get("type_id"))) {
            fields.add(entry("path", "EventDetail/TypeId", "value", text(event.get("type_id"))));
        } else {
            fields.add(entry("path", "EventDetail/TypeId", "field", key.get(0)));
        }
        if (JsonValues.truthy(event.get("description"))) {
            fields.add(entry("path", "EventDetail/Description", "value", event.get("description")));
        }
        if (JsonValues.truthy(event.get("action"))) {
            fields.add(entry("path", EVENT_DETAIL + detail + "/Action", "value", event.get("action")));
        }
        if (event.get("success") != null) {
            fields.add(entry("path", EVENT_DETAIL + detail + "/Outcome/Success", "value",
                    JsonValues.truthy(event.get("success"))
                            ? "true"
                            : "false"));
        }
        final String user = common.stream()
                .filter(entry -> "EventSource/User/Id".equals(entry.get("path"))
                                 && JsonValues.truthy(entry.get("field")))
                .map(entry -> text(entry.get("field")))
                .findFirst()
                .orElse(null);
        if ("Authenticate".equals(detail) && user != null) {
            // The schema wants the user authenticating: the one the event is about.
            fields.add(entry("path", "EventDetail/Authenticate/User/Id", "field", user));
        }
        final String prefix = EVENT_DETAIL + detail + "/";
        fields.addAll(detailFields.stream()
                .filter(field -> text(field.get("path")).startsWith(prefix) && !hasPath(fields, field))
                .toList());
        // What the schema wants that the catalogue doesn't say (Authenticate's Action, Update's After), from the
        // sample's rule for the same records when it writes the same element.
        final String element = detail.split("/")[0];
        sampled.stream()
                .filter(rule -> covers(rule, key) && detailOf(rule).contains(element))
                .findFirst()
                .ifPresent(like -> fields.addAll(JsonValues.maps(like.get("fields")).stream()
                        .filter(field -> text(field.get("path")).startsWith(prefix)
                                         && !(text(field.get("path")).endsWith("/Data") || hasPath(fields, field)))
                        .toList()));
        for (final String name : rest) {
            fields.add(entry("path", prefix + "Data", "data_name", name, "field", name));
        }
        final String name = ToolText.strip(NOT_NAME.matcher(firstTruthy(event.get("event"), key.get(1), ""))
                .replaceAll("_"), "_").toLowerCase(Locale.ROOT);
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", name.isEmpty()
                ? "event"
                : name);
        rule.put("when", new ArrayList<>(List.of(entry("field", key.get(0), "equals", key.get(1)))));
        rule.put("fields", fields);
        // Unknown is not agreed here: build_translation_xslt puts it to the user with the records it catches.
        return rule;
    }

    // The action elements a rule writes.
    private static Set<String> detailOf(final Map<String, Object> rule) {
        final Set<String> elements = new LinkedHashSet<>();
        for (final Map<String, Object> field : JsonValues.maps(rule.get("fields"))) {
            final String path = text(field.get("path"));
            if (path.startsWith(EVENT_DETAIL) && path.chars().filter(c -> c == '/').count() >= 2) {
                elements.add(path.split("/")[1]);
            }
        }
        return elements;
    }

    private static boolean namesOnlyUnknown(final Map<String, Object> rule) {
        final Set<String> elements = new LinkedHashSet<>(detailOf(rule));
        elements.remove(UNKNOWN);
        return elements.isEmpty();
    }

    private static boolean covers(final Map<String, Object> rule, final List<String> key) {
        for (final Map<String, Object> condition : JsonValues.maps(rule.get("when"))) {
            if (!key.get(0).equals(condition.get("field"))) {
                continue;
            }
            final boolean matches = condition.get("equals") != null
                    ? ToolText.str(condition.get("equals")).equals(key.get(1))
                    : NullSafe.list(condition.get("one_of") instanceof final List<?> list
                            ? list
                            : null).stream().map(ToolText::str).toList().contains(key.get(1));
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPath(final List<Map<String, Object>> fields, final Map<String, Object> field) {
        return fields.stream().anyMatch(existing -> Objects.equals(existing.get("path"), field.get("path")));
    }

    private static List<String> key(final Map<String, Object> event) {
        return List.of(text(event.get("field")), ToolText.str(event.get("value")));
    }

    private static Map<String, Object> entry(final Object... keysAndValues) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            entry.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return entry;
    }

    private static Map<String, String> strings(final Map<?, ?> map) {
        final Map<String, String> out = new LinkedHashMap<>();
        map.forEach((key, value) -> out.put(String.valueOf(key), ToolText.str(value)));
        return out;
    }

    private static String firstTruthy(final Object first, final Object second, final String otherwise) {
        if (JsonValues.truthy(first)) {
            return ToolText.str(first);
        }
        return JsonValues.truthy(second)
                ? ToolText.str(second)
                : otherwise;
    }

    private static boolean hasValue(final Object value) {
        return value != null && !"".equals(value);
    }

    // A value as text, "" for null.
    private static String text(final Object value) {
        return value == null
                ? ""
                : ToolText.str(value);
    }
}
