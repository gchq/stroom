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

import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueCheckResult;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.catalogue.Placement;
import stroom.ai.impl.tools.catalogue.Way;
import stroom.ai.impl.tools.generation.Condition;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The catalogue of event types as the user reads it, in the design's words (source type, event type, record,
/// recognised by, uncatalogued records): the table a set of event types is confirmed by, shown in the chat (the
/// form refers to it: forms are short plain text), and the source notes' "Event types" part, a section per action
/// element, the uncatalogued records last. How a record of an event type is recognised is said in words
/// (`logger_name is …SecurityEventLogger and eventType is "Password change"`), never as the conditions' JSON.
final class CatalogueTables {

    /// The table a set of event types is confirmed by.
    static final String SET_HEADER = "| Event type | Recognised by | Records | Translated as |";
    /// The source notes' table of the catalogue.
    static final String NOTES_HEADER = "| Event type | What happened | Recognised by | Records | Translated as | "
                                       + "Status |";
    private static final String NOTES_SEPARATOR = "| --- | --- | --- | --- | --- | --- |";
    /// The catch-all's row's TypeId (the uncatalogued records' events).
    static final String UNKNOWN = "Unknown";

    // A value longer than this is shortened in words: a qualified name to its last part, any other cut.
    private static final int LONG_VALUE = 40;
    private static final int CUT = 60;

    private CatalogueTables() {
        // Static only.
    }

    /// @param types The event types of the set, in the order shown.
    /// @param check The check of the catalogue they are in (for their records), or null.
    /// @return The set's table, Markdown.
    static String setTable(final List<EventType> types, final CatalogueCheckResult check) {
        final List<String> lines = new ArrayList<>(List.of(SET_HEADER, "| --- | --- | --- | --- |"));
        for (final EventType type : types) {
            lines.add("| " + cell("`" + type.typeId() + "` " + nonNull(type.title())) + " | "
                      + cell(recognisedBy(type)) + " | " + records(type, check) + " | " + cell(translatedAs(type))
                      + " |");
        }
        return String.join("\n", lines);
    }

    /// The source notes' "Event types" part, for people (decided by the user 2026-11-20): a section per action
    /// element, as the chat's summaries group them and in their order, each a table of its event types (TypeId and
    /// title, what happened, how a record is recognised in words, records, what it is translated as (decided by the
    /// user 2026-11-20), and the status in short words), then the
    /// uncatalogued records in a section of their own. It holds every event type, so the chat need show only a
    /// summary of a large set. Descriptions are never cut.
    ///
    /// @param catalogue A catalogue.
    /// @param records   The records of the sample each event type recognises, by TypeId, as last checked (empty
    ///                  when unknown: a hand edit read back).
    /// @return The part's lines.
    static List<String> notesTable(final Catalogue catalogue, final Map<String, ?> records) {
        final List<String> lines = new ArrayList<>(List.of("## Event types", ""));
        for (final CatalogueSummary.Group group : CatalogueSummary.groups(catalogue.eventTypes(), null)) {
            lines.addAll(List.of("### " + heading(group.whatHappened()), "", NOTES_HEADER, NOTES_SEPARATOR));
            for (final EventType type : group.types()) {
                lines.add("| " + cell("`" + nonNull(type.typeId()) + "` " + nonNull(type.title())) + " | "
                          + cell(NullSafe.isBlankString(type.description())
                        ? "-"
                        : type.description()) + " | " + cell(recognisedBy(type)) + " | "
                          + recordsKept(records.get(type.typeId())) + " | " + cell(translatedAs(type)
                                                                                  + placedAs(type)) + " | "
                          + cell(status(type)) + " |");
            }
            lines.add("");
        }
        final Catalogue.CatchAll catchAll = catalogue.catchAll();
        lines.addAll(List.of("### Uncatalogued records", "", NOTES_HEADER, NOTES_SEPARATOR));
        lines.add("| `" + UNKNOWN + "` Uncatalogued records | - | records no event type recognises | "
                  + (records.get(UNKNOWN) instanceof final Number count
                ? String.valueOf(count.longValue())
                : "") + " | " + uncataloguedAs(catchAll) + ", their fields kept as Data | "
                  + cell(catchAll.confirmed()
                ? "confirmed by the user" + (catchAll.allowUnknown() == null
                        ? ""
                        : ": " + catchAll.allowUnknown())
                : "proposed") + " |");
        lines.add("");
        return lines;
    }

    // A group's heading: its action element as it is, the others' words with a capital.
    private static String heading(final String whatHappened) {
        return whatHappened.isEmpty()
                ? whatHappened
                : Character.toUpperCase(whatHappened.charAt(0)) + whatHappened.substring(1);
    }

    // What the uncatalogued records are written as: Unknown events, or the event the user said.
    private static String uncataloguedAs(final Catalogue.CatchAll catchAll) {
        final Catalogue.CatchAll.Translation own = catchAll.translation();
        return own == null || NullSafe.isBlankString(own.eventDetail())
                ? "Unknown events"
                : cell(own.typeId() + " events (" + own.eventDetail() + ")");
    }

    // An event type's status in short words: confirmed or proposed, its confidence, where it came from.
    private static String status(final EventType type) {
        final String from = switch (nonNull(type.basis())) {
            case EventType.USER_CONFIRMED -> "the user's";
            case EventType.DOCUMENTATION -> "from the documentation";
            case EventType.DRAFT -> "from the draft's seed";
            default -> "from the agent's knowledge";
        };
        return (type.confirmed()
                ? "confirmed"
                : "proposed") + "; " + nonNull(type.confidence()) + "; " + from;
    }

    /// @param type An event type.
    /// @return How a record of it is recognised, in words: its ways joined by "or", each its conditions by "and";
    /// `every record` for a sole event type.
    static String recognisedBy(final EventType type) {
        if (type.recognisedBy().isEmpty()) {
            return "every record";
        }
        final List<String> ways = new ArrayList<>();
        for (final Way way : type.recognisedBy()) {
            ways.add(way.when().isEmpty()
                    ? "every record"
                    : String.join(" and ", way.when().stream().map(CatalogueTables::words).toList()));
        }
        return String.join("; or ", ways);
    }

    /// @param condition A condition.
    /// @return It in words: `action is login`, `message starts with "Request:"`, `eventType isn't "Logon"`.
    static String words(final Condition condition) {
        final String field = condition.field() != null
                ? condition.field()
                : condition.xpath();
        final boolean not = condition.not();
        if (condition.equalTo() != null) {
            return field + (not
                    ? " isn't "
                    : " is ") + value(condition.equalTo());
        }
        if (condition.oneOf() != null) {
            return field + (not
                    ? " is none of "
                    : " is one of ") + String.join(", ", condition.oneOf().stream()
                    .map(CatalogueTables::value)
                    .toList());
        }
        if (condition.matches() != null) {
            return field + (not
                    ? " doesn't match "
                    : " matches ") + "`" + condition.matches() + "`";
        }
        if (condition.startsWith() != null) {
            return field + (not
                    ? " doesn't start with "
                    : " starts with ") + quoted(condition.startsWith());
        }
        if (condition.contains() != null) {
            return field + (not
                    ? " doesn't contain "
                    : " contains ") + quoted(condition.contains());
        }
        if (condition.inDictionary() != null) {
            return field + (not
                    ? " isn't in the dictionary "
                    : " is in the dictionary ") + condition.inDictionary();
        }
        return field + (Boolean.TRUE.equals(condition.present())
                ? " has a value"
                : " has no value");
    }

    /// @param type An event type.
    /// @return What its records are translated as: its action element and Action, who acted and what was acted on.
    static String translatedAs(final EventType type) {
        final List<String> parts = new ArrayList<>();
        parts.add(nonNull(type.eventDetail()).isEmpty()
                ? UNKNOWN
                : type.eventDetail() + (type.action() == null
                        ? ""
                        : " " + type.action()));
        if (type.drop()) {
            parts.set(0, "dropped (not translated)");
        }
        if (type.success() != null) {
            parts.add("outcome " + type.success());
        }
        if (type.actor() != null) {
            parts.add("who acted: " + type.actor());
        }
        if (type.affected() != null) {
            parts.add("acted on: " + type.affected() + (type.affectedAs() == null
                    ? ""
                    : " (" + type.affectedAs() + ")"));
        }
        return String.join(", ", parts);
    }

    /// @param type An event type.
    /// @return Its typed placements in words, after what it is translated as (`; typed: event.Subject ->
    /// Email/Subject, ...`); "" when it has none of its own.
    static String placedAs(final EventType type) {
        if (type.placements().isEmpty()) {
            return "";
        }
        return "; typed: " + String.join("; ", type.placements().stream().map(Placement::said).toList());
    }

    // A count kept in the notes, said as the set's table says it.
    private static String recordsKept(final Object count) {
        if (!(count instanceof final Number number)) {
            return "";
        }
        return number.longValue() == 0
                ? "0 (not in the sample)"
                : String.valueOf(number.longValue());
    }

    /// @param check A check.
    /// @return The records of the sample each event type recognises, by TypeId, and the uncatalogued ones' under
    /// [#UNKNOWN]: as the source notes keep them for their table.
    static Map<String, Object> recordsOf(final CatalogueCheckResult check) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (final String typeId : check.order()) {
            final CatalogueCheckResult.Count count = check.counts().get(typeId);
            if (count != null) {
                out.put(typeId, count.records());
            }
        }
        out.put(UNKNOWN, check.uncatalogued().records());
        return out;
    }

    /// @param type  An event type.
    /// @param check The check of its catalogue, or null.
    /// @return Its records in the sample, for a table's Records column: blank when unchecked.
    static String records(final EventType type, final CatalogueCheckResult check) {
        if (check == null) {
            return "";
        }
        final CatalogueCheckResult.Count count = check.counts().get(type.typeId());
        if (count == null) {
            return "";
        }
        return count.records() == 0
                ? "0 (not in the sample)"
                : String.valueOf(count.records());
    }

    // A value said: short ones as they are (quoted when they hold spaces), a long qualified name by its last part.
    private static String value(final String value) {
        String shown = value;
        if (value.length() > LONG_VALUE) {
            final int dot = value.lastIndexOf('.');
            shown = dot > 0 && value.indexOf(' ') < 0 && value.length() - dot - 1 <= LONG_VALUE
                    ? "…" + value.substring(dot + 1)
                    : value.substring(0, Math.min(CUT, value.length())) + "…";
        }
        return shown.indexOf(' ') >= 0 || shown.isEmpty()
                ? quoted(shown)
                : shown;
    }

    private static String quoted(final String text) {
        return "\"" + text + "\"";
    }

    /// @param text A table cell's text.
    /// @return It as a cell: no line breaks, its pipes escaped.
    static String cell(final String text) {
        return nonNull(text).replace("\r", " ").replace("\n", " ").replace("|", "\\|");
    }

    private static String nonNull(final String text) {
        return text == null
                ? ""
                : text;
    }

    /// @param check A check.
    /// @return The TypeIds it counts no record for, in its order: event types not in the sample.
    static List<String> notInSample(final CatalogueCheckResult check) {
        final List<String> out = new ArrayList<>();
        for (final String typeId : check.order()) {
            final CatalogueCheckResult.Count count = check.counts().get(typeId);
            if (count != null && count.records() == 0) {
                out.add(typeId);
            }
        }
        return out;
    }

    /// @param check A check.
    /// @return The uncatalogued records' shapes, said for the catch-all's form, short: `N records of shape S` each.
    static List<String> uncataloguedShapes(final CatalogueCheckResult check) {
        final List<String> out = new ArrayList<>();
        for (final CatalogueCheckResult.Shape shape : check.uncatalogued().shapes()) {
            out.add(shape.records() + " with " + shape.signature());
        }
        return out;
    }

    /// @param json A JSON value's keys, as a map.
    /// @param key  A key.
    /// @return Its value as text, or null.
    static String text(final Map<?, ?> json, final String key) {
        final Object value = json == null
                ? null
                : json.get(key);
        return value == null
                ? null
                : String.valueOf(value);
    }
}
