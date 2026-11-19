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
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueKept;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.EventSchema.Child;
import stroom.util.shared.NullSafe;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
    /// The catalogue's event types (the contract's `event_types`).
    public static final String EVENT_TYPES = "event_types";
    /// The catalogue's catch-all (`catch_all`): its status, and the agreement for its records (`allow_unknown`).
    public static final String CATCH_ALL = "catch_all";
    /// The keys of the source notes' JSON block that hold the catalogue.
    public static final List<String> CATALOGUE_KEYS = List.of(EVENT_TYPES, CATCH_ALL, "scope");
    /// In what [#applyToDraft] returns: true when the rules are the catalogue's.
    public static final String CATALOGUED = "catalogued";
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

    /// The values the schema allows as an action element's Action, when it holds an enumeration there. Seen in the
    /// agent eval (cases 60 and 65): "Add member to role" and "MFA" confirmed as Authorise and Authenticate Actions,
    /// which the schema doesn't allow; the draft then wrote Modify and the build refused it as not what was confirmed.
    ///
    /// @param schema The event-logging schema.
    /// @param detail The event's `event_detail`, e.g. `Authorise`, or null.
    /// @return The Action values allowed, in the schema's order; null when the element has no Action, or its Action
    /// is not an enumeration (any text is allowed).
    public static List<String> allowedActions(final EventSchema schema, final String detail) {
        final String given = ToolText.strip(NullSafe.string(detail).strip(), "/");
        if (given.isEmpty() || !schema.hasPath(EVENT_DETAIL + given + "/Action")) {
            return null;
        }
        return schema.enumeration(schema.resolve(EVENT_DETAIL + given + "/Action").getLast().decl());
    }

    /// An event's Action as the schema spells it: the allowed value it names, ignoring case (`logon` is `Logon`).
    ///
    /// @param schema The event-logging schema.
    /// @param detail The event's `event_detail`.
    /// @param action The event's `action`, or null.
    /// @return The allowed value it names; the action as given when the schema allows any text there (or the element
    /// has no Action); empty when the schema's enumeration doesn't hold it.
    public static Optional<String> allowedAction(final EventSchema schema, final String detail, final String action) {
        final String given = NullSafe.string(action).strip();
        final List<String> allowed = allowedActions(schema, detail);
        if (allowed == null || given.isEmpty()) {
            return Optional.of(given);
        }
        return allowed.stream()
                .filter(value -> value.equalsIgnoreCase(given))
                .findFirst();
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

    /// The draft mapping rebuilt from the notes where they speak: fields the dictionary places, and, when the notes
    /// hold a catalogue of event types ([#catalogue]), the rules it gives ([CatalogueTranslation]): one per way of
    /// recognising each event type, in the catalogue's order, then the catch-all's. With no catalogue the draft's
    /// own rules stay (a draft before cataloguing). For the draft mapping (draft_translation_mapping), which drafts
    /// `mapping` from the sample first.
    ///
    /// @param mapping     The draft: `common` (field entries, each `path` and `field` or `value`) and `events`
    ///                    (rules, each `name`, `when` and `fields`, the one without `when` last). Changed in place.
    /// @param names       The sample's field names.
    /// @param values      Each field's values in the sample.
    /// @param notes       The source notes ([#readNotes]).
    /// @param detailCheck Why an `event_detail` can't be an action element ([#detailProblem]), or null.
    /// @return What was applied: `fields`, and with a catalogue [#CATALOGUED], `events`, `not_in_sample`,
    /// `uncatalogued`, `dropped` and `unread_fields` ([CatalogueTranslation#apply]).
    public static Map<String, Object> applyToDraft(final Map<String, Object> mapping,
                                                   final List<String> names,
                                                   final Map<String, List<String>> values,
                                                   final Map<String, Object> notes,
                                                   final Function<String, String> detailCheck) {
        return applyToDraft(mapping, names, values, notes, detailCheck, null);
    }

    /// [#applyToDraft(Map, List, Map, Map, Function)] with the sample's records, so each rule is given as Data only
    /// the fields left over that its own records hold (asked for by the user: seen in chat 172, every NetScaler rule
    /// given every key any NetScaler message held), and starts from the draft's rule that took its records.
    ///
    /// @param mapping     The draft's mapping, changed in place.
    /// @param names       The records' fields.
    /// @param values      Each field's values.
    /// @param notes       The source notes.
    /// @param detailCheck Why an event_detail can't be an action element, or null.
    /// @param records     The sample's records, as the draft reads them, or null when not known.
    /// @return What was applied, as [#applyToDraft(Map, List, Map, Map, Function)] returns.
    @SuppressWarnings("unchecked")
    public static Map<String, Object> applyToDraft(final Map<String, Object> mapping,
                                                   final List<String> names,
                                                   final Map<String, List<String>> values,
                                                   final Map<String, Object> notes,
                                                   final Function<String, String> detailCheck,
                                                   final List<Map<String, Object>> records) {
        final List<Map<String, Object>> common = (List<Map<String, Object>>) mapping.get("common");
        final Map<String, Object> applied = new LinkedHashMap<>();
        final List<String> appliedFields = new ArrayList<>();
        applied.put("fields", appliedFields);
        applied.put(CATALOGUED, false);

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

        final Catalogue catalogue = catalogue(notes);
        if (catalogue == null) {
            // No catalogue yet: the draft keeps its own rules, to be catalogued before building.
            return applied;
        }
        applied.put(CATALOGUED, true);
        applied.putAll(CatalogueTranslation.apply(mapping, names, values, catalogue, catchAllReason(notes, catalogue),
                detailFields, detailCheck, records));
        return applied;
    }

    /// @param notes The source notes ([#readNotes], [#merged]), or null.
    /// @return Their catalogue of event types (`event_types`, `catch_all`, `scope`), or null when they have none
    /// (no `event_types`) or it doesn't read.
    public static Catalogue catalogue(final Map<String, Object> notes) {
        if (notes == null || !(notes.get(EVENT_TYPES) instanceof List)) {
            return null;
        }
        final Map<String, Object> json = new LinkedHashMap<>();
        for (final String key : CATALOGUE_KEYS) {
            if (notes.get(key) != null) {
                json.put(key, notes.get(key));
            }
        }
        return Catalogue.parse(json).catalogue();
    }

    /// @param notes     The source notes.
    /// @param catalogue Their catalogue.
    /// @return The catch-all's agreement, the uncatalogued records' allow_unknown: the reason the notes' catch-all
    /// gives (`allow_unknown`), else the catch-all's confirmation; null while the catch-all isn't confirmed.
    public static String catchAllReason(final Map<String, Object> notes, final Catalogue catalogue) {
        if (notes.get(CATCH_ALL) instanceof final Map<?, ?> catchAll
            && catchAll.get("allow_unknown") instanceof final String reason && !reason.isBlank()) {
            return reason;
        }
        return catalogue.catchAll().confirmed()
                ? CatalogueTranslation.CATCH_ALL_AGREED
                : null;
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
        Map<String, Object> catalogue = null;
        for (final Map<String, Object> note : NullSafe.list(notes)) {
            if (catalogue == null && note.get(EVENT_TYPES) instanceof List) {
                // A build's catalogue is one: the first doc holding one.
                catalogue = new LinkedHashMap<>();
                for (final String key : CATALOGUE_KEYS) {
                    if (note.get(key) != null) {
                        catalogue.put(key, note.get(key));
                    }
                }
            }
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
        if (catalogue != null) {
            out.putAll(catalogue);
        }
        return out;
    }

    /// Where a mapping contradicts the catalogue, compared by the records rules take, not by condition text
    /// ([CatalogueKept]): an event type's sampled records taken by no rule, or by a rule writing another action
    /// element or TypeId than the catalogue gives. Of the event types not yet confirmed (documentation's, the
    /// agent's): what a mapping does with confirmed ones is put to the user apart (the confirmed-changed form).
    ///
    /// @param mapping   The mapping.
    /// @param catalogue The build's catalogue, or null.
    /// @param records   The sample's records, as [LocalCheck#sampleRecords] reads them for the mapping.
    /// @return The contradictions, each naming the event type and the rule; empty without a catalogue or records.
    public static List<String> checkMapping(final TranslationMapping mapping,
                                            final Catalogue catalogue,
                                            final List<Object> records) {
        if (catalogue == null || records.isEmpty()) {
            return List.of();
        }
        final Catalogue proposed = catalogue.withEventTypes(catalogue.eventTypes().stream()
                .filter(type -> !type.confirmed() && !type.drop())
                .toList());
        final List<String> problems = new ArrayList<>();
        for (final CatalogueKept.Taken taken : CatalogueKept.taken(mapping, proposed, records, null)) {
            final String label = taken.typeId() + (NullSafe.isNonBlankString(taken.type().title())
                    ? " (" + taken.type().title() + ")"
                    : "");
            final String want = CatalogueKept.detailOf(taken.type());
            taken.byRule().forEach((name, count) -> {
                if (CatalogueKept.NO_RULE.equals(name)) {
                    problems.add(label + ": no rule takes its " + count + " sample records");
                    return;
                }
                if (name.startsWith(CatalogueKept.DROP)) {
                    problems.add(label + ": its " + count + " sample records are dropped (" + name.substring(
                            CatalogueKept.DROP.length()) + ")");
                    return;
                }
                final EventRule rule = CatalogueKept.rule(mapping, name);
                if (rule == null) {
                    return;
                }
                final CatalogueKept.Written written = CatalogueKept.written(mapping, rule);
                if (!UNKNOWN.equals(want) && !written.details().contains(want)) {
                    problems.add(label + ": the catalogue says " + want + "; rule '" + name + "' writes "
                                 + (written.details().isEmpty()
                            ? "no action element"
                            : String.join(", ", written.details())) + " (" + count + " sample records)");
                }
                if (written.typeId() != null && !written.typeId().equals(taken.typeId())) {
                    problems.add(label + ": the catalogue's TypeId is '" + taken.typeId() + "'; rule '" + name
                                 + "' writes '" + written.typeId() + "'");
                }
            });
        }
        return problems;
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

    // The fields pattern events' capture groups read: an extract entry each (once), and the values they take.
    @SuppressWarnings("unchecked")
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
