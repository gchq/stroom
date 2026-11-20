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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.CatalogueDiff;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// A set of event types as the user confirms it (decided by the user 2026-11-20: never a table of hundreds of rows,
/// never many forms): whatever its number and confidence, a short summary grouped by what happened (the action
/// element each is written under), a few TypeIds each with their records, then the event types worth a look, row by
/// row. Every row is in the source notes' "Event types" table, which the summary and its form refer to.
///
/// **Worth a look** is a generic rule, explainable to the user in a few words, with no knowledge of any source: an
/// event type is listed when
///
/// * it changed since the user last saw the set, whoever changed it (decided by the user 2026-11-20), and the set is
///   shown again (`changed since shown: title, recognised by`): they check their amendment landed;
/// * no record of the sample is of it (from documentation, say): nothing has shown its conditions hold;
/// * its records are translated as no event at all: no action element, or an action of `Other` or `Unknown` (the
///   event-logging schema's own words for an action it can't name), or a TypeId whose verb is `Unknown`;
/// * it is the only event type under its (named) action element in a set of [#LONE_FROM] or more where some
///   other element is shared: in a large set an element chosen once stands out as the likeliest slip, whereas
///   in a small set every element is lone and the summary names each type already;
/// * it has no description of its own: its records' Description then comes only from a message they hold, and
///   records without one have none;
/// * its records are dropped (not translated);
/// * the agent said why it doubts it (`doubt`);
/// * it is of a medium or low set: each of those is doubtful by its confidence, so every one is worth a look, with
///   why where the agent said (its `doubt`, and why records that look like several event types are one).
///
/// At most [#MOST_LISTED] rows are listed, the rest counted, so a medium or low set of hundreds is still a summary:
/// the amended first (the user is checking those), then those a rule above or the agent's doubt picks out, then
/// those doubtful by their confidence alone, each in the catalogue's order. Every row is in the source notes either
/// way, so the order only chooses which rows the chat shows; the user can ask about any of them
/// (`catalogue_event_types` `explain=`).
final class CatalogueSummary {

    /// The fewest event types in a set for an action element only one of them is written under to be worth a look.
    static final int LONE_FROM = 10;
    /// The most TypeIds a group names.
    static final int EXAMPLES = 3;
    /// The most event types worth a look listed row by row; the rest counted.
    static final int MOST_LISTED = 10;
    /// The header of the table of event types worth a look.
    static final String UNUSUAL_HEADER = "| Event type | Recognised by | Records | Translated as | Worth a look "
                                         + "because |";

    private static final String HIGH = "high";
    private static final String NO_ELEMENT = "no action element";
    private static final String DROPPED = "dropped (not translated)";
    private static final Set<String> NO_EVENT_WORDS = Set.of("other", "unknown");

    private CatalogueSummary() {
        // Static only.
    }

    /// The summary the chat shows before a set's form, before any amendment.
    ///
    /// @param set      The event types put to the user, of one confidence, in the catalogue's order.
    /// @param check    The check of their catalogue (for their records), or null.
    /// @param notesDoc Where every row is: a link to the source notes doc, or its name quoted.
    /// @return The summary, Markdown.
    static String summary(final List<EventType> set, final CatalogueCheckResult check, final String notesDoc) {
        return summary(set, check, notesDoc, Amendments.NONE);
    }

    /// The summary the chat shows before a set's form.
    ///
    /// @param set        The event types put to the user, of one confidence, in the catalogue's order.
    /// @param check      The check of their catalogue (for their records), or null.
    /// @param notesDoc   Where every row is: a link to the source notes doc, or its name quoted (before it exists).
    /// @param amendments What the user's amendments to this set changed, so far ([Amendments#NONE] before any).
    /// @return The summary, Markdown.
    static String summary(final List<EventType> set,
                          final CatalogueCheckResult check,
                          final String notesDoc,
                          final Amendments amendments) {
        final String confidence = confidenceOf(set);
        final boolean doubtful = !HIGH.equals(confidence);
        final List<String> lines = new ArrayList<>();
        lines.add("**" + set.size() + " " + confidence + "-confidence event type" + plural(set.size()) + "**, by "
                  + "what happened" + (doubtful
                ? ": each is doubtful, so each is worth a look below"
                : "") + ". Each one, row by row, is in the source notes " + notesDoc + "; ask about any of them in "
                  + "the chat.");
        lines.add("");
        for (final Group group : groups(set, check)) {
            lines.add("- " + said(group, check));
        }
        lines.add("");
        final List<String> removed = amendments.removed().stream()
                .filter(typeId -> set.stream().noneMatch(type -> typeId.equalsIgnoreCase(type.typeId())))
                .toList();
        if (!removed.isEmpty()) {
            lines.add("Removed since shown: " + String.join(", ", removed) + ".");
            lines.add("");
        }
        final List<Unusual> unusual = worthALook(set, check, amendments);
        if (unusual.isEmpty()) {
            lines.add("Worth a look: none. Every one is in the sample, written as an event under its action "
                      + "element, with a description of its own.");
            return String.join("\n", lines);
        }
        lines.add("**Worth a look** (" + unusual.size() + " event type" + plural(unusual.size()) + "):");
        lines.add("");
        lines.add(UNUSUAL_HEADER);
        lines.add("| --- | --- | --- | --- | --- |");
        for (final Unusual one : unusual.subList(0, Math.min(MOST_LISTED, unusual.size()))) {
            final EventType type = one.type();
            lines.add("| " + CatalogueTables.cell("`" + type.typeId() + "` " + NullSafe.string(type.title()))
                      + " | " + CatalogueTables.cell(CatalogueTables.recognisedBy(type)) + " | "
                      + CatalogueTables.records(type, check) + " | "
                      + CatalogueTables.cell(CatalogueTables.translatedAs(type)) + " | "
                      + CatalogueTables.cell(String.join("; ", one.why())) + " |");
        }
        if (unusual.size() > MOST_LISTED) {
            lines.add("");
            lines.add("And " + (unusual.size() - MOST_LISTED) + " more worth a look, in the source notes "
                      + notesDoc + ".");
        }
        return String.join("\n", lines);
    }

    /// The event types of a set worth a look, by the rule in this class's description, in the order listed: the
    /// amended first, then those a rule or the agent's doubt picks out, then those doubtful by their confidence
    /// alone, each in the set's order.
    ///
    /// @param set        The event types, of one confidence.
    /// @param check      Their catalogue's check (for their records), or null when unchecked.
    /// @param amendments What the user's amendments to the set changed so far.
    /// @return Those worth a look, each with why.
    static List<Unusual> worthALook(final List<EventType> set,
                                    final CatalogueCheckResult check,
                                    final Amendments amendments) {
        final boolean doubtful = !HIGH.equals(confidenceOf(set));
        final Map<EventType, List<String>> byRule = new LinkedHashMap<>();
        unusual(set, check).forEach(one -> byRule.put(one.type(), one.why()));
        final List<List<Unusual>> tiers = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (final EventType type : set) {
            final List<String> why = new ArrayList<>();
            final List<String> amended = amendments.of(type.typeId());
            if (!amended.isEmpty()) {
                why.add("changed since shown: " + String.join(", ", amended));
            }
            if (doubtful) {
                why.add(type.confidence() + " confidence" + (type.doubt() == null
                        ? ""
                        : ": " + type.doubt()));
                if (NullSafe.isNonBlankString(type.oneTypeBecause())) {
                    why.add("one event type because " + type.oneTypeBecause().strip());
                }
            } else if (type.doubt() != null) {
                why.add("doubtful: " + type.doubt());
            }
            final List<String> rules = byRule.getOrDefault(type, List.of());
            why.addAll(rules);
            if (why.isEmpty()) {
                continue;
            }
            final int tier = !amended.isEmpty()
                    ? 0
                    : !rules.isEmpty() || type.doubt() != null || NullSafe.isNonBlankString(type.oneTypeBecause())
                            ? 1
                            : 2;
            tiers.get(tier).add(new Unusual(type, List.copyOf(why)));
        }
        final List<Unusual> out = new ArrayList<>();
        tiers.forEach(out::addAll);
        return out;
    }

    /// The set grouped by what happened: one group an action element (a dropped event type's group is the dropped
    /// ones), in the order each element first comes in the set.
    ///
    /// @param set   The event types.
    /// @param check Their catalogue's check (for their records), or null.
    /// @return The groups.
    static List<Group> groups(final List<EventType> set, final CatalogueCheckResult check) {
        final Map<String, List<EventType>> byWhat = new LinkedHashMap<>();
        for (final EventType type : set) {
            byWhat.computeIfAbsent(whatHappened(type), ignored -> new ArrayList<>()).add(type);
        }
        final List<Group> out = new ArrayList<>();
        byWhat.forEach((what, types) -> out.add(new Group(what, List.copyOf(types),
                types.stream().mapToInt(type -> records(type, check)).sum())));
        return out;
    }

    /// The event types of the set worth a look by the generic rules in this class's description (neither the
    /// amendments, the agent's doubt nor the set's confidence), in the set's order.
    ///
    /// @param set   The event types.
    /// @param check Their catalogue's check (for their records), or null when unchecked (no record counted then).
    /// @return Those worth a look, each with why.
    static List<Unusual> unusual(final List<EventType> set, final CatalogueCheckResult check) {
        final List<Group> groups = groups(set, check);
        final boolean shared = groups.stream().anyMatch(group -> group.types().size() > 1);
        final List<Unusual> out = new ArrayList<>();
        for (final EventType type : set) {
            final List<String> why = new ArrayList<>();
            if (check != null && records(type, check) == 0) {
                why.add("no record of it in the sample");
            }
            if (type.drop()) {
                why.add("its records are dropped");
            } else if (NullSafe.isBlankString(type.eventDetail())) {
                why.add("no action element: its records are written as Unknown");
            } else if (noEventWord(type.action())) {
                why.add("its action is " + type.action());
            } else if (noEventWord(verbOf(type.typeId()))) {
                why.add("its TypeId's verb is " + verbOf(type.typeId()));
            }
            final String what = whatHappened(type);
            if (set.size() >= LONE_FROM && shared && !type.drop() && NullSafe.isNonBlankString(type.eventDetail())
                && groups.stream()
                    .anyMatch(group -> group.whatHappened().equals(what) && group.types().size() == 1)) {
                why.add("the only one under " + what);
            }
            if (NullSafe.isBlankString(type.description())) {
                why.add("no description of its own");
            }
            if (!why.isEmpty()) {
                out.add(new Unusual(type, List.copyOf(why)));
            }
        }
        return out;
    }

    // A group said in a line: `Authenticate: 2 event types, 16 records: Authenticate-Logon (8), ...`, its commonest
    // TypeIds named.
    private static String said(final Group group, final CatalogueCheckResult check) {
        final List<EventType> commonest = group.types().stream()
                .sorted(Comparator.comparingInt((EventType type) -> -records(type, check)))
                .limit(EXAMPLES)
                .toList();
        final List<String> named = new ArrayList<>();
        for (final EventType type : commonest) {
            named.add(type.typeId() + " (" + records(type, check) + ")");
        }
        final int more = group.types().size() - commonest.size();
        return group.whatHappened() + ": " + group.types().size() + " event type" + plural(group.types().size())
               + ", " + group.records() + " record" + plural(group.records()) + (more > 0
                ? "; e.g. "
                : "; ") + String.join(", ", named) + (more > 0
                ? " and " + more + " more"
                : "");
    }

    // What happened, for grouping: the action element; the dropped ones together.
    private static String whatHappened(final EventType type) {
        if (type.drop()) {
            return DROPPED;
        }
        return NullSafe.isBlankString(type.eventDetail())
                ? NO_ELEMENT
                : type.eventDetail();
    }

    private static int records(final EventType type, final CatalogueCheckResult check) {
        if (check == null) {
            return 0;
        }
        final CatalogueCheckResult.Count count = check.counts().get(type.typeId());
        return count == null
                ? 0
                : count.records();
    }

    private static String confidenceOf(final List<EventType> set) {
        return set.isEmpty()
                ? HIGH
                : NullSafe.nonBlankStringElse(set.getFirst().confidence(), HIGH);
    }

    private static boolean noEventWord(final String word) {
        return word != null && NO_EVENT_WORDS.contains(word.strip().toLowerCase(Locale.ROOT));
    }

    private static String verbOf(final String typeId) {
        if (typeId == null) {
            return null;
        }
        final int dash = typeId.indexOf('-');
        return dash < 0
                ? typeId
                : typeId.substring(0, dash);
    }

    private static String plural(final int count) {
        return count == 1
                ? ""
                : "s";
    }


    // --------------------------------------------------------------------------------


    /// Event types of a set under one action element.
    ///
    /// @param whatHappened The action element (or the dropped ones', or those with none).
    /// @param types        The event types, in the set's order.
    /// @param records      Their records in the sample, together.
    record Group(String whatHappened, List<EventType> types, int records) {

    }


    // --------------------------------------------------------------------------------


    /// An event type worth a look, and why.
    ///
    /// @param type The event type.
    /// @param why  Why it is worth a look, in words.
    record Unusual(EventType type, List<String> why) {

    }


    // --------------------------------------------------------------------------------


    /// What the user's amendments to a set changed (as the agent proposed them), kept while the set is put to them
    /// again, so the amended event types are worth a look.
    ///
    /// @param changed What changed of each event type, by TypeId, in words ([CatalogueDiff]'s: `title`,
    ///                `recognised by`), or `added` for one the amendments added.
    /// @param removed The TypeIds the amendments removed.
    record Amendments(Map<String, List<String>> changed, List<String> removed) {

        /// No amendment.
        static final Amendments NONE = new Amendments(Map.of(), List.of());

        private static final String CHANGED = "changed";
        private static final String REMOVED = "removed";
        private static final String ADDED = "added";

        /// Copies what is given.
        Amendments {
            changed = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(changed)));
            removed = List.copyOf(NullSafe.list(removed));
        }

        /// @param typeId An event type's TypeId.
        /// @return What the amendments changed of it, in words; empty when nothing.
        List<String> of(final String typeId) {
            if (typeId == null) {
                return List.of();
            }
            return changed.entrySet().stream()
                    .filter(entry -> typeId.equalsIgnoreCase(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(List.of());
        }

        /// @param diff What the amendments' proposals changed.
        /// @return These amendments and those changes together: an event type removed then added again counts as
        /// changed, one added then removed as never there.
        Amendments with(final CatalogueDiff.Diff diff) {
            final Map<String, List<String>> now = new LinkedHashMap<>(changed);
            final Set<String> gone = new LinkedHashSet<>(removed);
            diff.added().forEach(typeId -> {
                if (gone.remove(typeId)) {
                    now.put(typeId, List.of(CHANGED));
                } else {
                    now.put(typeId, List.of(ADDED));
                }
            });
            diff.changed().forEach(one -> {
                final Set<String> what = new LinkedHashSet<>(now.getOrDefault(one.typeId(), List.of()));
                what.addAll(one.what());
                now.put(one.typeId(), List.copyOf(what));
            });
            diff.removed().forEach(typeId -> {
                if (!ADDED.equals(String.join("", now.getOrDefault(typeId, List.of())))) {
                    gone.add(typeId);
                }
                now.remove(typeId);
            });
            return new Amendments(now, List.copyOf(gone));
        }

        /// @param json The amendments as [#toJson] wrote them, or null.
        /// @return Them; [#NONE] for null.
        static Amendments fromJson(final Map<String, Object> json) {
            if (json == null) {
                return NONE;
            }
            final Map<String, List<String>> changed = new LinkedHashMap<>();
            JsonValues.mapOrEmpty(json.get(CHANGED)).forEach((typeId, what) -> changed.put(typeId,
                    JsonValues.strings(what)));
            return new Amendments(changed, JsonValues.strings(json.get(REMOVED)));
        }

        /// @return Them as JSON, for the confirmation in the source notes.
        Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put(CHANGED, changed);
            out.put(REMOVED, removed);
            return out;
        }

        /// @return True when nothing was changed.
        boolean none() {
            return changed.isEmpty() && removed.isEmpty();
        }
    }
}
