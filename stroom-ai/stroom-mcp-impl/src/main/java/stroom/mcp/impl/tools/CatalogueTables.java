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
import stroom.ai.impl.tools.catalogue.Way;
import stroom.ai.impl.tools.generation.Condition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The catalogue of event types as the user reads it, in the design's words (source type, event type, record,
/// recognised by, uncatalogued records): the table a set of event types is confirmed by, shown in the chat (the
/// form refers to it: forms are short plain text), and the source notes' "Event types" table, the catch-all's row
/// last. How a record of an event type is recognised is said in words (`logger_name is …SecurityEventLogger and
/// eventType is "Password change"`), never as the conditions' JSON.
final class CatalogueTables {

    /// The table a set of event types is confirmed by.
    static final String SET_HEADER = "| Event type | Recognised by | Records | Translated as |";
    /// The source notes' table of the catalogue.
    static final String NOTES_HEADER = "| TypeId | Title | Recognised by | Records | Translated as | Basis |";
    /// The longest a set's table is in the chat: past it, its first rows and where every row is (the source notes).
    static final int CHAT_TABLE_CHARS = 20_000;
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

    /// A set's table as the chat shows it: whole when it fits in [#CHAT_TABLE_CHARS]; else its first rows, then how
    /// many more there are and where every row is (the source notes' "Event types" table), so a long set is still
    /// one form (decided by the user 2026-11-20).
    ///
    /// @param types    The event types of the set, in the order shown.
    /// @param check    The check of the catalogue they are in (for their records), or null.
    /// @param notesDoc Where every row is: the source notes doc's name, quoted, or words naming it.
    /// @return The table, Markdown.
    static String chatTable(final List<EventType> types, final CatalogueCheckResult check, final String notesDoc) {
        final String whole = setTable(types, check);
        if (whole.length() <= CHAT_TABLE_CHARS) {
            return whole;
        }
        final List<String> lines = whole.lines().toList();
        final StringBuilder shown = new StringBuilder();
        int rows = 0;
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            // Room kept for the closing line.
            if (i >= 2 && shown.length() + line.length() + 300 > CHAT_TABLE_CHARS) {
                break;
            }
            shown.append(line).append('\n');
            if (i >= 2) {
                rows++;
            }
        }
        return shown + "\nThe first " + rows + " of these " + types.size() + " event types; the other "
               + (types.size() - rows) + ", and every row, are in the Event types table of the source notes "
               + notesDoc + ".";
    }

    /// @param catalogue A catalogue.
    /// @param records   The records of the sample each event type recognises, by TypeId, as last checked (empty
    ///                  when unknown: a hand edit read back).
    /// @return Its "Event types" table for the source notes: an event type a row, in the catalogue's order, then
    /// the catch-all's. It holds every event type, so the chat need show only a summary of a large set.
    static List<String> notesTable(final Catalogue catalogue, final Map<String, ?> records) {
        final List<String> lines = new ArrayList<>(List.of("## Event types", "", NOTES_HEADER,
                "| --- | --- | --- | --- | --- | --- |"));
        for (final EventType type : catalogue.eventTypes()) {
            lines.add("| " + cell(nonNull(type.typeId())) + " | " + cell(nonNull(type.title())) + " | "
                      + cell(recognisedBy(type)) + " | " + recordsKept(records.get(type.typeId())) + " | "
                      + cell(translatedAs(type)) + " | " + cell(basis(type)) + " |");
        }
        final Catalogue.CatchAll catchAll = catalogue.catchAll();
        lines.add("| " + UNKNOWN + " | Uncatalogued records | records no event type recognises | "
                  + (records.get(UNKNOWN) instanceof final Number count
                ? String.valueOf(count.longValue())
                : "") + " | Unknown, their "
                  + "fields kept as Data | " + cell(catchAll.confirmed()
                ? "confirmed by the user" + (catchAll.allowUnknown() == null
                        ? ""
                        : ": " + catchAll.allowUnknown())
                : "proposed") + " |");
        lines.add("");
        return lines;
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

    private static String basis(final EventType type) {
        final String basis = switch (nonNull(type.basis())) {
            case EventType.USER_CONFIRMED -> "the agent's proposal, confirmed by the user";
            case EventType.DOCUMENTATION -> "documentation";
            case EventType.DRAFT -> "the draft's seed";
            default -> "agent knowledge (" + type.confidence() + ")";
        };
        return type.confirmed() && !EventType.USER_CONFIRMED.equals(type.basis())
                ? basis + ", confirmed by the user"
                : type.confirmed()
                        ? basis
                        : basis + ", proposed";
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
