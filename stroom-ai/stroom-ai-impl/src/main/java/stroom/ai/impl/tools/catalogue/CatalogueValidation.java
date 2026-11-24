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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.generation.XPathRegex;
import stroom.ai.impl.tools.sample.SourceTypeReading;
import stroom.ai.impl.tools.validation.EventSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/// The checks of a catalogue that need no records: each event type's TypeId (Verb-Object, unique) and title
/// (unique), its ways of being recognised (each with conditions, but for a sole event type; none on the build's
/// source type field; capture names unique and as many as the regex's groups; XPath's regex dialect, at most
/// [#MAX_REGEX_LENGTH] long), what it means (the event-logging schema's checks, [EventMeaning]), and its provenance
/// (dropped only on the user's word; the draft's seed never confirmed but in the user's form). The checks against
/// the records (overlaps, counts, uncatalogued records) are the check engine's.
public final class CatalogueValidation {

    /// The longest a condition's regex may be: a way of recognising an event type tells records by a short pattern;
    /// one longer is a sign it reads the whole value (lessons: no regex over whole long values, 2026-11-12; Saxon's
    /// limits).
    public static final int MAX_REGEX_LENGTH = 500;

    // What separates the names of a field, in any of its spellings: JSON's dots, XML's slashes, a held value's >,
    // an attribute's @.
    private static final Pattern NAME_PARTS = Pattern.compile("[./>@]+");
    // The action element saying what happened is not known.
    private static final String UNKNOWN = "Unknown";

    private CatalogueValidation() {
        // Static only.
    }

    /// @param catalogue The catalogue.
    /// @param schema    The event-logging schema its event types' meanings are checked against, or null to skip
    ///                  those checks.
    /// @return Its problems, in the catalogue's order; none when it has none.
    public static List<Problem> problems(final Catalogue catalogue, final EventSchema schema) {
        final List<Problem> problems = new ArrayList<>();
        final Map<String, Integer> typeIds = new HashMap<>();
        final Map<String, String> titles = new HashMap<>();
        final Map<String, String> names = new LinkedHashMap<>();
        final boolean sole = catalogue.eventTypes().size() == 1;
        for (final EventType type : catalogue.eventTypes()) {
            final String id = type.typeId();
            // An existing pipeline's TypeId is kept as it writes it: changing it would change its Events.
            final String idProblem = EventType.EXISTING_PIPELINE.equals(type.basis())
                    ? TypeIds.keptProblem(id)
                    : TypeIds.problem(id);
            if (idProblem != null) {
                problems.add(new Problem(id, -1, idProblem));
            } else if (typeIds.merge(id, 1, Integer::sum) == 2) {
                problems.add(new Problem(id, -1, "type_id '" + id + "' is given to two event types: each has its own"));
            }
            if (type.title() != null && !type.title().isBlank()) {
                final String title = type.title().strip().toLowerCase(Locale.ROOT);
                final String first = titles.putIfAbsent(title, id);
                if (first != null) {
                    problems.add(new Problem(id, -1, "title '" + type.title().strip() + "' is " + first + "'s too: "
                                                     + "each event type has its own"));
                }
            }
            ways(catalogue, type, sole, names, problems);
            provenance(type, problems);
            if (schema != null) {
                meaning(schema, type, problems);
            }
        }
        return problems;
    }

    // Its ways of being recognised: conditions in each (none only for a sole event type), none on the source type
    // field, regexes XPath runs and short, capture names unique and one a group.
    private static void ways(final Catalogue catalogue,
                             final EventType type,
                             final boolean sole,
                             final Map<String, String> names,
                             final List<Problem> problems) {
        final String id = type.typeId();
        if (type.recognisedBy().isEmpty() && !sole) {
            problems.add(new Problem(id, -1, "recognised_by is empty: only an event type that is its source's only "
                                             + "one is recognised by every record; give the conditions its records "
                                             + "meet"));
        }
        for (int at = 0; at < type.recognisedBy().size(); at++) {
            final int n = at;
            final Way way = type.recognisedBy().get(n);
            if (way.when().isEmpty() && !sole) {
                problems.add(new Problem(id, n, "this way of recognising it has no conditions: only a sole event type "
                                                + "is recognised by every record"));
            }
            final List<Condition> regexes = new ArrayList<>();
            for (final Condition condition : way.when()) {
                if (onSourceTypeField(catalogue.scope().sourceTypeField(), condition.field())) {
                    problems.add(new Problem(id, n, "condition on " + condition.field() + ", the build's source type "
                                                    + "field: the source type group's module passes other source "
                                                    + "types over already, so tell the event types apart by "
                                                    + "their records' other fields or shape"));
                }
                if (condition.matches() != null) {
                    regexes.add(condition);
                    regexProblem(condition.matches()).ifPresent(problem -> problems.add(new Problem(id, n,
                            "the regex of " + condition.field() + " " + problem)));
                }
            }
            names(id, n, way, regexes, names, problems);
        }
    }

    private static Optional<String> regexProblem(final String regex) {
        if (regex.length() > MAX_REGEX_LENGTH) {
            return Optional.of("is " + regex.length() + " characters long, at most " + MAX_REGEX_LENGTH + ": tell the "
                               + "records apart by a short pattern, or by starts_with or contains");
        }
        return XPathRegex.problem(regex);
    }

    // A way's capture names: for its one matches condition, as many as its groups, each unique in the build.
    private static void names(final String id,
                              final int n,
                              final Way way,
                              final List<Condition> regexes,
                              final Map<String, String> names,
                              final List<Problem> problems) {
        if (way.names().isEmpty()) {
            return;
        }
        if (regexes.size() != 1) {
            problems.add(new Problem(id, n, "names name a matches condition's capture groups: this way has "
                                            + regexes.size() + " matches conditions, not one"));
        } else {
            try {
                final int groups = XPathRegex.groups(regexes.getFirst().matches());
                if (groups != way.names().size()) {
                    problems.add(new Problem(id, n, "names has " + way.names().size() + " name(s) for the regex's "
                                                    + groups + " capture group(s): one name a group"));
                }
            } catch (final XPathRegex.XPathRegexException e) {
                // Said by the regex's own check.
            }
        }
        for (final String name : way.names()) {
            final String first = names.putIfAbsent(name, id);
            if (first != null) {
                problems.add(new Problem(id, n, "name '" + name + "' is " + (first.equals(id)
                        ? "given twice"
                        : first + "'s too") + ": capture names are unique in the build"));
            }
        }
    }

    // Dropped only on the user's word; the draft's seed never confirmed but in the user's form.
    private static void provenance(final EventType type, final List<Problem> problems) {
        if (type.drop() && !EventType.USER_CONFIRMED.equals(type.basis())) {
            problems.add(new Problem(type.typeId(), -1, "drop is the user's word only: an event type is dropped when "
                                                        + "the user says so in the form (basis user_confirmed)"));
        }
        if (EventType.DRAFT.equals(type.basis()) && type.confirmed()) {
            problems.add(new Problem(type.typeId(), -1, "basis draft is the draft's seed, never confirmed but by the "
                                                        + "user in the form"));
        }
    }

    // What it means, checked against the schema as record_source_notes checks its events.
    private static void meaning(final EventSchema schema, final EventType type, final List<Problem> problems) {
        final String detail = type.eventDetail();
        final String label = "event type " + type.typeId();
        if (detail == null || detail.isBlank() || UNKNOWN.equals(ToolText.strip(detail.strip(), "/"))) {
            // As the translation has it (a rule its conditions single out writes an action element, not Unknown):
            // seen in eval case 56, seven event types confirmed as Unknown, then every save refused.
            if (!type.drop() && type.recognisedBy().stream().anyMatch(way -> !way.when().isEmpty())) {
                problems.add(new Problem(type.typeId(), -1, label + ": no event_detail, so its records would be "
                                                            + "translated as Unknown, which the translation refuses "
                                                            + "for records its conditions single out: give the "
                                                            + "action element that describes them (Authenticate, "
                                                            + "View, Create, Update, Delete, Send, "
                                                            + "Network/Permit...)"));
            }
            return;
        }
        final String detailProblem = SourceNotes.detailProblem(schema, detail);
        if (detailProblem != null) {
            problems.add(new Problem(type.typeId(), -1, label + ": " + detailProblem));
            return;
        }
        EventMeaning.parts(schema, label, ToolText.strip(detail.strip(), "/"), type.success(), type.action())
                .problems().forEach(problem -> problems.add(new Problem(type.typeId(), -1, problem)));
        if (type.affectedAs() != null && !type.affectedAs().isBlank()) {
            final String problem = EventMeaning.affected(schema, label, detail, type.affectedAs()).problem();
            if (problem != null) {
                problems.add(new Problem(type.typeId(), -1, problem));
            }
        }
    }

    /// Whether a condition's field is the build's source type field, in any of its spellings: the reading's field
    /// and steps dotted (`event.log_type`, held JSON or nested), slashed (`event/Log/log_type`, held XML, its root
    /// element's name between), anywhere below (`event//log_type`), or `>` as the reading writes it; each reading of a
    /// combination or of readings tried in turn.
    ///
    /// @param sourceTypeField The build's source type field (a reading), or null.
    /// @param field           A condition's field, or null.
    /// @return True if the field is (a spelling of) the source type field.
    public static boolean onSourceTypeField(final String sourceTypeField, final String field) {
        if (sourceTypeField == null || sourceTypeField.isBlank() || field == null || field.isBlank()) {
            return false;
        }
        final List<String> given = names(field);
        for (final SourceTypeReading part : SourceTypeReading.of(sourceTypeField).parts()) {
            for (final SourceTypeReading.Path path : part.paths()) {
                final List<String> read = names(path);
                if (read == null || read.isEmpty()) {
                    continue;
                }
                final int base = names(path.field()).size();
                if (given.equals(read) || withoutRoot(given, base).equals(read)
                    || (field.contains("//") && given.getFirst().equals(read.getFirst())
                        && given.getLast().equals(read.getLast()))) {
                    return true;
                }
            }
        }
        return false;
    }

    // A reading's names: its field's, then each held step's; null for one reading a part's meta, a format or a root.
    private static List<String> names(final SourceTypeReading.Path path) {
        if (path.field().startsWith("meta:")) {
            return null;
        }
        final List<String> out = new ArrayList<>(names(path.field()));
        for (final SourceTypeReading.Step step : path.held()) {
            if (step.special() != null) {
                return null;
            }
            out.addAll(step.names());
            if (step.attribute() != null) {
                out.add(step.attribute().replace("@", ""));
            }
        }
        return out;
    }

    private static List<String> names(final String field) {
        return Arrays.stream(NAME_PARTS.split(field.replace("*:", "")))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    // The names without the one after the reading's field: a held XML document's root element.
    private static List<String> withoutRoot(final List<String> names, final int base) {
        if (names.size() <= base) {
            return names;
        }
        final List<String> out = new ArrayList<>(names);
        out.remove(base);
        return out;
    }


    // --------------------------------------------------------------------------------


    /// A problem of a catalogue, as the check reports it.
    ///
    /// @param typeId  The event type's TypeId (as given; null when none is).
    /// @param way     Which of its ways of being recognised (from 0), or -1 for the event type as a whole.
    /// @param problem What the problem is.
    public record Problem(String typeId, int way, String problem) {

        /// @return It as the check's JSON writes it.
        public Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("type_id", typeId);
            out.put("way", way);
            out.put("problem", problem);
            return out;
        }
    }
}
