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

import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// The high-confidence event types as the user confirms them (decided by the user 2026-11-20: never a table of
/// hundreds of rows, never many forms): whatever their number, a short summary grouped by what happened (the action
/// element each is written under), a few TypeIds each with their records, then the event types worth a look, row by
/// row. Every row is in the source notes' "Event types" table, which the summary and its form refer to.
///
/// **Worth a look** is a generic rule, explainable to the user in a few words, with no knowledge of any source: an
/// event type is listed when
///
/// * no record of the sample is of it (from documentation, say): nothing has shown its conditions hold;
/// * its records are translated as no event at all: no action element, or an action of `Other` or `Unknown` (the
///   event-logging schema's own words for an action it can't name), or a TypeId whose verb is `Unknown`;
/// * it is the only event type under its (named) action element in a set of [#LONE_FROM] or more where some
///   other element is shared: in a large set an element chosen once stands out as the likeliest slip, whereas
///   in a small set every element is lone and the summary names each type already;
/// * it has no description of its own: its records' Description then comes only from a message they hold, and
///   records without one have none;
/// * its records are dropped (not translated).
final class CatalogueSummary {

    /// The fewest event types in a set for an action element only one of them is written under to be worth a look.
    static final int LONE_FROM = 10;
    /// The most TypeIds a group names.
    static final int EXAMPLES = 3;
    /// The most event types worth a look listed row by row; the rest counted.
    static final int MOST_LISTED = 25;
    /// The header of the table of event types worth a look.
    static final String UNUSUAL_HEADER = "| Event type | Recognised by | Records | Translated as | Worth a look "
                                         + "because |";

    private static final String NO_ELEMENT = "no action element";
    private static final String DROPPED = "dropped (not translated)";
    private static final Set<String> NO_EVENT_WORDS = Set.of("other", "unknown");

    private CatalogueSummary() {
        // Static only.
    }

    /// The summary the chat shows before the high-confidence event types' form.
    ///
    /// @param set      The high-confidence event types put to the user, in the catalogue's order.
    /// @param check    The check of their catalogue (for their records), or null.
    /// @param notesDoc Where every row is: the source notes doc's name, quoted, or words naming it.
    /// @return The summary, Markdown.
    static String summary(final List<EventType> set, final CatalogueCheckResult check, final String notesDoc) {
        final List<String> lines = new ArrayList<>();
        lines.add("**" + set.size() + " high-confidence event type" + plural(set.size()) + "**, by what happened. "
                  + "Each one, row by row, is in the Event types table of the source notes " + notesDoc + ".");
        lines.add("");
        for (final Group group : groups(set, check)) {
            lines.add("- " + said(group, check));
        }
        lines.add("");
        final List<Unusual> unusual = unusual(set, check);
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

    /// The event types of the set worth a look, by the rule in this class's description, in the set's order.
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
}
