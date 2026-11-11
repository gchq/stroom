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

import stroom.ai.impl.tools.IpAddresses;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.EventSchema.Child;
import stroom.util.shared.NullSafe;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

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

    // What a draft entry read from a field's own values, kept when a note maps the field elsewhere.
    private static final List<String> KEPT_FROM_VALUES = List.of("time_format", "timezone");
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
    /// A catalogue event's pattern: the regex (XPath's dialect) its field's value matches, in place of a value. Asked
    /// for by the user after chat 166: events said in free text (sshd's 'Failed password for', 'Invalid user')
    /// told apart by the user's and agent's patterns, not by splitter parts nor by inferred phrases.
    public static final String MATCHES = "matches";
    /// The names of a pattern's capture groups, each a field of its records (the user, an address), read by an
    /// extract entry.
    public static final String NAMES = "names";
    /// The classifications classify_kinds showed the user that wait for their confirmation.
    public static final String PROPOSED = "proposed";
    /// The kinds the user said to leave untranslated (classify_kinds), each as its key: `field=value`, or a split's
    /// `within: field = value` / `within: field matches pattern`.
    public static final String DROPPED = "dropped";
    /// The fields that tell the kinds of event apart, as the user chose them (classify_kinds, survey_feed), or
    /// [stroom.ai.impl.tools.sample.KindFields#ONE_KIND] alone: the draft makes its rules by them.
    public static final String KIND_FIELDS = "kind_fields";
    /// The key (`field=value`) of the kind classify_kinds listed for records it told no kinds apart in, by a field
    /// every sample record shares: the draft makes that kind's rule the rule for every record.
    public static final String WHOLE_KIND = "whole_kind";
    /// The reason a dropped kind's drop rule gives.
    public static final String DROPPED_REASON = "the user said to leave these untranslated";
    // A split's key: the kind it is within, the field, and its value or pattern.
    private static final Pattern SPLIT_KEY = Pattern.compile("^([^:=\\s]+=[^:]*): (\\S+) (matches|=) (.*)$");
    // How a pattern event's key holds its regex, apart from a value.
    private static final String PATTERN = "~";
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

    /// The fields' event-logging paths the schema doesn't have, each left out of the notes it was given in, so a
    /// draft from them maps nothing to an element that doesn't exist. Seen: paths such as
    /// EventSource/Client/Device/IpAddress copied into the draft, each then costing schema lookups to put right.
    ///
    /// @param schema The event-logging schema of the build's version.
    /// @param fields The field notes given; an invalid `event_logging_path` is removed from its note.
    /// @return For each path left out, the field, the path, and why (what the schema allows where it stops).
    public static List<Map<String, Object>> withoutUnknownPaths(final EventSchema schema,
                                                                final List<Map<String, Object>> fields) {
        final List<Map<String, Object>> problems = new ArrayList<>();
        for (final Map<String, Object> field : fields) {
            final String path = ToolText.strip(text(field.get("event_logging_path")).strip(), "/");
            if (path.isEmpty()) {
                continue;
            }
            try {
                schema.resolve(path);
            } catch (final IllegalArgumentException e) {
                final Map<String, Object> problem = new LinkedHashMap<>();
                problem.put("field", text(field.get("field")));
                problem.put("event_logging_path", path);
                problem.put("problem", e.getMessage());
                problems.add(problem);
                field.remove("event_logging_path");
            }
        }
        return problems;
    }

    /// What a catalogue event's `affected_as` names under its action element: the object acted on (`User`,
    /// `File`, `Document`, `Group`...), with the leaf the affected field's value goes in (its `Id`, else `Name`,
    /// `Path` or `Title`) when only the object is named.
    ///
    /// @param schema     The event-logging schema.
    /// @param detail     The event's `event_detail`, e.g. `Delete`.
    /// @param affectedAs The event's `affected_as`, e.g. `User` or `User/Name`.
    /// @return The path below the action element, e.g. `User/Id`.
    /// @throws IllegalArgumentException saying what the action element holds, when it has no such object.
    public static String affectedPath(final EventSchema schema, final String detail, final String affectedAs) {
        final String element = ToolText.strip(NullSafe.string(detail).strip(), "/");
        final String given = ToolText.strip(NullSafe.string(affectedAs).strip(), "/");
        final List<Child> chain;
        try {
            chain = schema.resolve(EVENT_DETAIL + element + "/" + given);
        } catch (final IllegalArgumentException e) {
            final List<String> holds = schema.children(schema.resolve(EVENT_DETAIL + element).getLast().decl())
                    .stream()
                    .map(Child::name)
                    .filter(name -> !"Data".equals(name))
                    .toList();
            throw new IllegalArgumentException("affected_as '" + given + "' isn't under " + element + ", which "
                                               + "holds " + String.join(", ", holds), e);
        }
        if (schema.isLeaf(chain.getLast().decl())) {
            return given;
        }
        for (final String leaf : List.of("Id", "Name", "Path", "Title")) {
            if (schema.hasPath(EVENT_DETAIL + element + "/" + given + "/" + leaf)) {
                return given + "/" + leaf;
            }
        }
        // Seen in the agent eval: told only to name a leaf, the agent guessed three times before finding one.
        final List<String> leaves = new ArrayList<>();
        leaves(schema, chain.getLast().decl(), given, 3, leaves);
        throw new IllegalArgumentException("affected_as '" + given + "' under " + element + " has no Id, Name, Path "
                                           + "or Title for the affected field's value: name its leaf, one of "
                                           + String.join(", ", leaves.subList(0, Math.min(12, leaves.size()))));
    }

    // The leaf paths below an element, to a depth, Data aside.
    private static void leaves(final EventSchema schema,
                               final Element decl,
                               final String at,
                               final int depth,
                               final List<String> out) {
        for (final Child child : schema.children(decl)) {
            if ("Data".equals(child.name())) {
                continue;
            }
            final String path = at + "/" + child.name();
            if (schema.isLeaf(child.decl())) {
                out.add(path);
            } else if (depth > 1) {
                leaves(schema, child.decl(), path, depth - 1, out);
            }
        }
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
            final String label = firstTruthy(event.get("event"), event.get("value"), text(event.get(MATCHES)));
            final String problem = detailCheck == null
                    ? null
                    : detailCheck.apply(text(event.get("event_detail")));
            if (NullSafe.isNonEmptyString(problem)) {
                problems.add(label + ": " + problem);
            }
            if (JsonValues.truthy(event.get("field")) && (hasValue(event.get("value")) || isPattern(event))) {
                final List<String> key = twinKey(event);
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
        final List<String> unread = new ArrayList<>();
        applied.put("unread_fields", unread);

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
                // The dictionary's word wins over a guess from the field's name; but what the draft read from the
                // field's own values stays (its time format and zone). Seen: TimeCreated replaced by the note's entry
                // for the same epoch field lost its time_format, and the agent spent turns finding the syntax.
                common.stream()
                        .filter(existing -> name.equals(existing.get("field")))
                        .findFirst()
                        .ifPresent(existing -> KEPT_FROM_VALUES.forEach(key -> {
                            if (existing.get(key) != null && !entry.containsKey(key)) {
                                entry.put(key, existing.get(key));
                            }
                        }));
                common.removeIf(existing -> path.equals(existing.get("path")) || name.equals(existing.get("field")));
                common.add(entry);
            }
            appliedFields.add(name + " -> " + path);
        });

        // Splits first (patterns, and values within a kind): each tells apart some of a kind's records, so its rule
        // comes before the kind's own (the rest of a catch-all, classified too), or that rule would take them all.
        final List<Map<String, Object>> events = JsonValues.maps(notes.get("events")).stream()
                .filter(event -> names.contains(text(event.get("field")))
                                 && (hasValue(event.get("value")) || isPattern(event)))
                .sorted(Comparator.comparingInt(event -> isPattern(event) || isSplit(event)
                        ? 0
                        : 1))
                .toList();
        if (events.isEmpty()) {
            return applied;
        }
        // The fields a pattern's capture groups read (the user in 'Failed password for (\S+)'): an extract entry
        // each pattern, and their values, so the rules can name them.
        final List<String> knownNames = new ArrayList<>(names);
        final Map<String, List<String>> knownValues = new LinkedHashMap<>();
        values.forEach((name, held) -> knownValues.put(name, new ArrayList<>(held)));
        captured(mapping, events, values, knownNames, knownValues);
        final Set<List<String>> seen = new LinkedHashSet<>();
        events.forEach(event -> seen.add(key(event)));
        final Set<String> taken = new LinkedHashSet<>();
        common.forEach(entry -> taken.add(NullSafe.get(entry.get("field"), String::valueOf)));
        detailFields.forEach(entry -> taken.add(text(entry.get("field"))));
        events.forEach(event -> taken.add(text(event.get("field"))));
        final List<String> rest = names.stream()
                .filter(name -> !taken.contains(name) && !NullSafe.isEmptyCollection(values.get(name)))
                .toList();
        // Two events a record can't tell apart: the same field and value (or pattern) within the same kind.
        final Set<List<String>> twins = new LinkedHashSet<>();
        for (final List<String> key : seen) {
            if (events.stream().filter(event -> key(event).equals(key)).map(SourceNotes::twinKey).distinct().count()
                < events.stream().filter(event -> key(event).equals(key)).count()) {
                twins.add(key);
            }
        }

        final List<Map<String, Object>> rules = new ArrayList<>();
        final Catalogued known = new Catalogued(knownNames, knownValues);
        // The records classified as one kind (classify_kinds, where none was told apart: told by a field every record
        // has the same value of): its rule is the rule for every record, in place of the draft's own, so records with
        // another value of that field (another host, a newer version) aren't left out of it.
        Map<String, Object> whole = null;
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
            final List<String> ownRest = new ArrayList<>(rest);
            JsonValues.strings(event.get(NAMES)).stream().filter(name -> !ownRest.contains(name)).forEach(ownRest::add);
            rules.add(rule(event, key, detail, common, detailFields, sampled, ownRest, known));
            if (sampled.isEmpty() && events.size() == 1 && !isPattern(event) && !isSplit(event)
                && JsonValues.strings(notes.get(WHOLE_KIND)).contains(key.get(0) + "=" + key.get(1))
                && Set.of(key.get(1)).equals(new LinkedHashSet<>(NullSafe.list(values.get(key.get(0)))))) {
                whole = rules.getLast();
                whole.remove("when");
            }
            final String actor = text(event.get("actor"));
            if (!actor.isBlank() && known.person(actor) == null) {
                unread.add(key.get(1) + ": actor '" + actor + "'" + (known.field(actor) == null
                        ? " isn't a field of the records"
                        : " holds addresses, not a user"));
            }
            final String affected = text(event.get("affected"));
            if (!affected.isBlank() && known.field(affected) == null) {
                unread.add(key.get(1) + ": affected '" + affected + "' isn't a field of the records");
            }
            final boolean shown = isPattern(event)
                    ? NullSafe.list(values.get(key.get(0))).stream().anyMatch(value -> matches(event, value))
                    : NullSafe.list(values.get(key.get(0))).contains(key.get(1));
            appliedEvents.add(firstTruthy(event.get("event"), key.get(1), "") + " (" + key.get(0) + "=" + key.get(1)
                              + ") -> " + detail + (shown
                    ? ""
                    : ", not in the sample"));
            if (!shown) {
                notInSample.add(key.get(0) + "=" + key.get(1));
            }
        }
        // Values the catalogue doesn't list, of fields whose kinds are values (a pattern's field is free text: its
        // values are every message).
        final Set<String> catalogued = new LinkedHashSet<>();
        events.stream().filter(event -> !isPattern(event)).forEach(event -> catalogued.add(text(event.get("field"))));
        events.stream().filter(SourceNotes::isPattern).forEach(event -> catalogued.remove(text(event.get("field"))));
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
        if (other != null && whole == null) {
            all.add(other);
        }
        mapping.put("events", all);
        return applied;
    }

    /// Several notes docs read as one: fields by name (the first of each), events in order.
    ///
    /// @param notes The notes of each doc.
    /// @return `fields` and `events`, [#PROPOSED] when classifications wait for the user's confirmation, and
    /// [#DROPPED] when the user said to leave kinds untranslated.
    public static Map<String, Object> merged(final List<Map<String, Object>> notes) {
        final Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        final List<Map<String, Object>> events = new ArrayList<>();
        final List<Map<String, Object>> proposed = new ArrayList<>();
        final Set<String> dropped = new LinkedHashSet<>();
        List<String> kindFields = List.of();
        final Set<String> wholeKinds = new LinkedHashSet<>();
        for (final Map<String, Object> note : NullSafe.list(notes)) {
            if (kindFields.isEmpty()) {
                kindFields = JsonValues.strings(note.get(KIND_FIELDS));
            }
            wholeKinds.addAll(JsonValues.strings(note.get(WHOLE_KIND)));
            JsonValues.maps(note.get("fields")).forEach(field -> fields.putIfAbsent(text(field.get("field")), field));
            events.addAll(JsonValues.maps(note.get("events")));
            proposed.addAll(JsonValues.maps(note.get(PROPOSED)));
            dropped.addAll(JsonValues.strings(note.get(DROPPED)));
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("fields", new ArrayList<>(fields.values()));
        out.put("events", events);
        if (!proposed.isEmpty()) {
            // Classifications shown the user and not yet confirmed (classify_kinds): the draft waits for them.
            out.put(PROPOSED, proposed);
        }
        if (!dropped.isEmpty()) {
            // Kinds the user said to leave untranslated: the draft gives each a drop rule.
            out.put(DROPPED, new ArrayList<>(dropped));
        }
        if (!kindFields.isEmpty()) {
            out.put(KIND_FIELDS, kindFields);
        }
        if (!wholeKinds.isEmpty()) {
            out.put(WHOLE_KIND, new ArrayList<>(wholeKinds));
        }
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
            if (!JsonValues.truthy(event.get("field")) || (!hasValue(event.get("value")) && !isPattern(event))) {
                continue;
            }
            final String value = isPattern(event)
                    ? text(event.get(MATCHES))
                    : ToolText.str(event.get("value"));
            final String label = firstTruthy(event.get("event"), value, value) + " (" + ToolText.str(event.get("field"))
                                 + (isPattern(event)
                    ? " matching "
                    : "=") + value + ")";
            final Map<String, Object> rule = isPattern(event) || isSplit(event)
                    ? patternRule(JsonValues.maps(mapping.get("events")), ToolText.str(event.get("field")), value,
                            isPattern(event)
                                    ? MATCHES
                                    : "equals")
                    : ruleFor(JsonValues.maps(mapping.get("events")), ToolText.str(event.get("field")), value);
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

    // The rule a split event's records go to: the first testing its field with the same pattern (or value).
    private static Map<String, Object> patternRule(final List<Map<String, Object>> rules,
                                                   final String field,
                                                   final String regex,
                                                   final String test) {
        return rules.stream()
                .filter(rule -> JsonValues.maps(rule.get("when")).stream()
                        .anyMatch(condition -> field.equals(condition.get("field"))
                                               && regex.equals(text(condition.get(test)))))
                .findFirst()
                .orElse(null);
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

    // One rule for a catalogued event: the sample's own rule for its records first, so what the draft read stays
    // (asked for by the user; seen in the agent eval, a catalogue rule wrote one fixed description over each
    // record's message, and lost the severity, names and addresses the sample's rule had), then what the catalogue
    // says over it: the TypeId, the action element's Action and outcome, who acted and what was acted on.
    private static Map<String, Object> rule(final Map<String, Object> event,
                                            final List<String> key,
                                            final String detail,
                                            final List<Map<String, Object>> common,
                                            final List<Map<String, Object>> detailFields,
                                            final List<Map<String, Object>> sampled,
                                            final List<String> rest,
                                            final Catalogued fieldsKnown) {
        final String prefix = EVENT_DETAIL + detail + "/";
        final List<Map<String, Object>> fields = new ArrayList<>();
        sampled.stream()
                .filter(rule -> covers(rule, key))
                .findFirst()
                .ifPresent(like -> {
                    for (final Map<String, Object> field : JsonValues.maps(like.get("fields"))) {
                        final String path = text(field.get("path"));
                        if ("EventDetail/TypeId".equals(path)) {
                            continue;
                        }
                        if (path.startsWith(EVENT_DETAIL) && path.chars().filter(c -> c == '/').count() >= 2
                            && !path.startsWith(prefix)) {
                            // Under another action element (Unknown, or the sample's guess): its Data move to the
                            // catalogue's; its other children are that element's, not this one's.
                            if (path.endsWith("/Data") && JsonValues.truthy(field.get("data_name"))) {
                                final Map<String, Object> moved = new LinkedHashMap<>(field);
                                moved.put("path", prefix + "Data");
                                fields.add(moved);
                            }
                            continue;
                        }
                        fields.add(new LinkedHashMap<>(field));
                    }
                });
        fields.addFirst(JsonValues.truthy(event.get("type_id"))
                ? entry("path", "EventDetail/TypeId", "value", text(event.get("type_id")))
                : isPattern(event)
                        // The field is the whole message: the kind's own name is its TypeId.
                        ? entry("path", "EventDetail/TypeId", "value", firstTruthy(event.get("event"),
                        event.get("title"), "Other"))
                        : entry("path", "EventDetail/TypeId", "field", key.get(0)));
        // The catalogue's description only where the records carry no message of their own.
        final boolean ownMessage = Stream.concat(common.stream(), fields.stream())
                .anyMatch(entry -> "EventDetail/Description".equals(entry.get("path"))
                                   && (JsonValues.truthy(entry.get("field")) || JsonValues.truthy(entry.get("xpath"))
                                       || JsonValues.truthy(entry.get("any_of"))));
        // A field of free text a person wrote about each event (a ticket's comment) is the event's message before
        // the catalogue's words for the kind (seen in the agent eval: every ticket event described as 'A ticket is
        // closed', its comment kept as Data).
        final String freeText = ownMessage
                ? null
                : fieldsKnown.freeText();
        if (freeText != null) {
            final Map<String, Object> message = fields.stream()
                    .filter(field -> freeText.equals(field.get("field")))
                    .findFirst()
                    .map(LinkedHashMap::new)
                    .orElseGet(() -> new LinkedHashMap<>(entry("field", freeText)));
            fields.removeIf(field -> freeText.equals(field.get("field")));
            message.put("path", "EventDetail/Description");
            message.remove("data_name");
            put(fields, message);
        } else if (JsonValues.truthy(event.get("description")) && !ownMessage) {
            put(fields, entry("path", "EventDetail/Description", "value", event.get("description")));
        }
        if (JsonValues.truthy(event.get("action"))) {
            put(fields, entry("path", EVENT_DETAIL + detail + "/Action", "value", event.get("action")));
        }
        if (event.get("success") != null) {
            put(fields, entry("path", EVENT_DETAIL + detail + "/Outcome/Success", "value",
                    JsonValues.truthy(event.get("success"))
                            ? "true"
                            : "false"));
        }
        // Who acted, where the catalogue says (asked for by the user: kind by kind, not one field for the source):
        // only a field of the records, and not one holding addresses (seen in the eval: a firewall's source address
        // given as the actor of records naming no user).
        final String actor = fieldsKnown.person(text(event.get("actor")));
        if (actor != null) {
            put(fields, entry("path", "EventSource/User/Id", "field", actor));
        }
        // What it was done to: the object the catalogue names (affected_as, checked against the schema when
        // recorded), its value from the affected field.
        final String affected = fieldsKnown.field(text(event.get("affected")));
        if (affected != null && JsonValues.truthy(event.get("affected_as"))) {
            put(fields, entry("path", EVENT_DETAIL + detail + "/" + text(event.get("affected_as")), "field",
                    affected));
        }
        final String user = actor != null
                ? actor
                : common.stream()
                        .filter(entry -> "EventSource/User/Id".equals(entry.get("path"))
                                         && JsonValues.truthy(entry.get("field")))
                        .map(entry -> text(entry.get("field")))
                        .findFirst()
                        .orElse(null);
        if ("Authenticate".equals(detail) && user != null
            && !hasPath(fields, entry("path", "EventDetail/Authenticate/User/Id"))) {
            // The schema wants the user authenticating: the one the event is about.
            fields.add(entry("path", "EventDetail/Authenticate/User/Id", "field", user));
        }
        fields.addAll(detailFields.stream()
                .filter(field -> text(field.get("path")).startsWith(prefix) && !hasPath(fields, field))
                .toList());
        // The fields left over, as Data: not one the rule already reads (as Data or into an element).
        final Set<String> read = new LinkedHashSet<>();
        fields.stream()
                .filter(field -> !"EventDetail/TypeId".equals(field.get("path")))
                .forEach(field -> {
                    read.add(text(field.get("data_name")));
                    read.add(text(field.get("field")));
                });
        for (final String name : rest) {
            if (!read.contains(name)) {
                fields.add(entry("path", prefix + "Data", "data_name", name, "field", name));
            }
        }
        final String name = ToolText.strip(NOT_NAME.matcher(firstTruthy(event.get("event"), isPattern(event)
                        ? event.get("title")
                        : key.get(1), ""))
                .replaceAll("_"), "_").toLowerCase(Locale.ROOT);
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", name.isEmpty()
                ? "event"
                : name);
        // The kind a pattern (or another field's value) splits first (app=sshd), so the rule takes only that kind's
        // records, as the user confirmed it (another program's 'Failed password for' lines stay theirs). Not a
        // format's field, which a format's own draft has left out: its condition is added with the format's rules.
        final List<Map<String, Object>> when = new ArrayList<>();
        within(event, fieldsKnown).ifPresent(when::add);
        when.add(isPattern(event)
                ? entry("field", key.get(0), MATCHES, text(event.get(MATCHES)))
                : entry("field", key.get(0), "equals", key.get(1)));
        rule.put("when", when);
        rule.put("fields", fields);
        // Unknown is not agreed here: build_translation_xslt puts it to the user with the records it catches.
        return rule;
    }

    // An entry in place of any with its path (and Data name), where it was; else last.
    private static void put(final List<Map<String, Object>> fields, final Map<String, Object> entry) {
        for (int i = 0; i < fields.size(); i++) {
            if (Objects.equals(fields.get(i).get("path"), entry.get("path"))
                && Objects.equals(fields.get(i).get("data_name"), entry.get("data_name"))) {
                fields.set(i, entry);
                return;
            }
        }
        fields.add(entry);
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
            if (key.get(1).startsWith(PATTERN)) {
                if (key.get(1).substring(PATTERN.length()).equals(text(condition.get(MATCHES)))) {
                    return true;
                }
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
        return List.of(text(event.get("field")), isPattern(event)
                ? PATTERN + text(event.get(MATCHES))
                : ToolText.str(event.get("value")));
    }

    // What tells an event's records from another's: its field and value (or pattern), and the kind it splits.
    private static List<String> twinKey(final Map<String, Object> event) {
        final List<String> key = new ArrayList<>(key(event));
        key.add(text(event.get("within")));
        return key;
    }

    /// @param event A catalogue event.
    /// @return True if it is told by a pattern its field's value matches, not a value.
    public static boolean isPattern(final Map<?, ?> event) {
        return event.get(MATCHES) instanceof final String regex && !regex.isBlank();
    }

    /// @param key A dropped kind's key (see [#DROPPED]).
    /// @return The conditions its records meet, as a drop rule's `when`: its field's value (or pattern), after the
    /// kind a split is within; none when the key can't be read.
    public static List<Map<String, Object>> dropConditions(final String key) {
        final Matcher split = SPLIT_KEY.matcher(NullSafe.string(key));
        final List<Map<String, Object>> when = new ArrayList<>();
        if (split.matches()) {
            final int equals = split.group(1).indexOf('=');
            when.add(entry("field", split.group(1).substring(0, equals), "equals",
                    split.group(1).substring(equals + 1)));
            when.add("matches".equals(split.group(3))
                    ? entry("field", split.group(2), MATCHES, split.group(4))
                    : entry("field", split.group(2), "equals", split.group(4)));
            return when;
        }
        final int equals = NullSafe.string(key).indexOf('=');
        if (equals > 0) {
            when.add(entry("field", key.substring(0, equals), "equals", key.substring(equals + 1)));
        }
        return when;
    }

    /// The notes as one format's draft follows them: without the events that split another format's records
    /// (`within` naming the format field and another format). Seen in chat 170: sshd's patterns, within
    /// variant=linux, drafted into the NetScaler format too and noted there as "not in the sample".
    ///
    /// @param notes   The source notes, or null.
    /// @param field   The field naming each record's format.
    /// @param variant The format drafted, or null for the records no format read.
    /// @return The notes for it.
    public static Map<String, Object> forVariant(final Map<String, Object> notes,
                                                 final String field,
                                                 final String variant) {
        if (notes == null || field == null) {
            return notes;
        }
        final String prefix = field + "=";
        final List<Map<String, Object>> events = JsonValues.maps(notes.get("events")).stream()
                .filter(event -> !text(event.get("within")).startsWith(prefix)
                                 || text(event.get("within")).equals(prefix + variant))
                .toList();
        final Map<String, Object> out = new LinkedHashMap<>(notes);
        out.put("events", events);
        return out;
    }

    /// @param event A catalogue event.
    /// @return True if it splits a listed kind (classify_kinds' `within`): told by a pattern, or by a value of another
    /// field than the kind's (event_id=LOGIN within category=SSLVPN).
    public static boolean isSplit(final Map<?, ?> event) {
        return event.get("within") instanceof final String within && !within.isBlank();
    }

    /// @param event A catalogue event told by a value or a pattern.
    /// @param value A value of its field.
    /// @return True if the value is the event's: its pattern matches it, or it is the event's value.
    public static boolean holds(final Map<?, ?> event, final String value) {
        return isPattern(event)
                ? matches(event, value)
                : value != null && value.equals(text(event.get("value")));
    }

    /// @param event A pattern event.
    /// @param value A value of its field.
    /// @return True if the pattern matches it, as the XSLT's matches() does (anywhere in the value, unless anchored).
    public static boolean matches(final Map<?, ?> event, final String value) {
        if (value == null || !isPattern(event)) {
            return false;
        }
        try {
            return Pattern.compile(text(event.get(MATCHES))).matcher(value).find();
        } catch (final PatternSyntaxException e) {
            return false;
        }
    }

    // A pattern event's within (field=value) as a condition, when the records hold its field; empty for '*'.
    private static Optional<Map<String, Object>> within(final Map<String, Object> event, final Catalogued known) {
        final String within = text(event.get("within"));
        final int equals = within.indexOf('=');
        if (equals <= 0) {
            return Optional.empty();
        }
        final String field = within.substring(0, equals);
        return known.field(field) == null
                ? Optional.empty()
                : Optional.of(entry("field", field, "equals", within.substring(equals + 1)));
    }

    // The fields pattern events' capture groups read: an extract entry each (once), and the values they take.
    @SuppressWarnings("unchecked")
    private static void captured(final Map<String, Object> mapping,
                                 final List<Map<String, Object>> events,
                                 final Map<String, List<String>> values,
                                 final List<String> knownNames,
                                 final Map<String, List<String>> knownValues) {
        for (final Map<String, Object> event : events) {
            final List<String> groups = JsonValues.strings(event.get(NAMES));
            if (!isPattern(event) || groups.isEmpty()) {
                continue;
            }
            final String field = text(event.get("field"));
            final Map<String, Object> extract = entry("field", field, "regex", text(event.get(MATCHES)), "names",
                    new ArrayList<>(groups));
            final List<Map<String, Object>> extracts = (List<Map<String, Object>>) mapping.computeIfAbsent("extract",
                    k -> new ArrayList<>());
            if (!extracts.contains(extract)) {
                extracts.add(extract);
            }
            final Pattern pattern;
            try {
                pattern = Pattern.compile(text(event.get(MATCHES)));
            } catch (final PatternSyntaxException e) {
                continue;
            }
            for (final String value : NullSafe.list(values.get(field))) {
                final Matcher found = pattern.matcher(value);
                if (found.find()) {
                    for (int i = 0; i < groups.size() && i < found.groupCount(); i++) {
                        if (found.group(i + 1) != null) {
                            knownValues.computeIfAbsent(groups.get(i), k -> new ArrayList<>()).add(found.group(i + 1));
                        }
                    }
                }
            }
            groups.stream().filter(name -> !knownNames.contains(name)).forEach(knownNames::add);
        }
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


    // --------------------------------------------------------------------------------


    // The sample's fields, for checking the fields a catalogue event names.
    private record Catalogued(List<String> names, Map<String, List<String>> values) {

        // The field, when the records have it; else null.
        String field(final String name) {
            return name != null && !name.isBlank() && names.contains(name)
                    ? name
                    : null;
        }

        // The first field of free text a person writes about an event (a comment, a remark, a note) the records
        // hold; else null.
        String freeText() {
            return names.stream()
                    .filter(name -> DraftMapping.FREE_TEXT.matcher(name).matches()
                                    && !NullSafe.list(values.get(name)).isEmpty())
                    .findFirst()
                    .orElse(null);
        }

        // The field, when the records have it and it names a person or account: not one whose values are
        // addresses.
        String person(final String name) {
            final String known = field(name);
            if (known == null) {
                return null;
            }
            final List<String> held = NullSafe.list(values.get(known));
            return !held.isEmpty() && held.stream().allMatch(IpAddresses::isAddress)
                    ? null
                    : known;
        }
    }
}
