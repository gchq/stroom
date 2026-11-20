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
import stroom.ai.impl.tools.LargeResults;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.workspace.SessionWorkspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// A catalogue_event_types result too large for a client outside Stroom ([LargeResults]): seen in eval case 68 (300
/// event types) on Haiku, results of 61,000 to 93,000 characters, the catalogue, its check's counts and the event
/// types not in the sample listed in full beside the set's table. Outlined: those not in the sample counted, the
/// check's counts (problems, overlaps, uncatalogued records and the rest kept whole), and the catalogue by status;
/// the set's table, which the user must be shown whole, is cut last of all, saying so and where the rest is.
final class CatalogueOutline {

    private static final String TABLE = "table";
    private static final String EVENT_TYPES = "event_types";
    private static final String STATUS = "status";
    // The most characters of TypeIds listed in an outline.
    private static final int TYPE_ID_CHARS = 1_500;
    // A table's header and its separator line.
    private static final int HEADER_LINES = 2;
    // Room kept beside a cut table for what is said of it.
    private static final int TABLE_NOTE_ROOM = 600;

    private CatalogueOutline() {
        // Static only.
    }

    /// Gives a catalogue_event_types result in outline when it is too large for the caller ([LargeResults#apply]).
    ///
    /// @param caller    Who called the tool.
    /// @param workspace The session's workspace, if it has one.
    /// @param build     The build.
    /// @param result    The result, changed in place.
    /// @param limit     The most characters it is given whole in.
    /// @return True if it was given in outline.
    static boolean outlineIfLarge(final ToolCaller caller,
                                  final Optional<SessionWorkspace> workspace,
                                  final String build,
                                  final Map<String, Object> result,
                                  final int limit) {
        return LargeResults.of(CatalogueEventTypesTool.NAME, build)
                .keep(TABLE, "recorded", "source_notes", "listing", "survey", "carried", "since_promoted",
                        "amendments", "amended", "check.problems", "check.overlaps", "check.several",
                        "check.not_checked", "check.uncatalogued", "check.unparsed")
                .outline("not_in_sample", CatalogueOutline::notInSample)
                .outline("check", CatalogueOutline::check)
                .outline("catalogue", CatalogueOutline::catalogue)
                .outline("confirmed_now", (value, room) -> typeIds(JsonValues.strings(value), "confirmed now"))
                .cutTheRest()
                .outline(TABLE, CatalogueOutline::table)
                .apply(caller, workspace, result, limit);
    }

    // "Create-Account: not in the sample (kept, and said so in the review)", each: counted, their TypeIds listed.
    private static Object notInSample(final Object value, final LargeResults.Room room) {
        final List<String> typeIds = JsonValues.strings(value).stream()
                .map(said -> said.contains(":")
                        ? said.substring(0, said.indexOf(':'))
                        : said)
                .toList();
        return typeIds(typeIds, "not in the sample (kept, and said so in the review)");
    }

    // The check, its counts a record count an event type (or, past a third of the room, in total), its order left
    // out (the counts' own); the rest, which the agent acts on, whole.
    private static Object check(final Object value, final LargeResults.Room room) {
        final Map<String, Object> check = JsonValues.map(value);
        if (check == null) {
            return value;
        }
        final Map<String, Object> outlined = new LinkedHashMap<>(check);
        final Map<String, Object> counts = JsonValues.mapOrEmpty(check.get("counts"));
        final Map<String, Object> records = new LinkedHashMap<>();
        int total = 0;
        int none = 0;
        for (final Map.Entry<String, Object> entry : counts.entrySet()) {
            final Object count = entry.getValue() instanceof final Map<?, ?> map
                    ? map.get("records")
                    : entry.getValue();
            final int n = count instanceof final Number number
                    ? number.intValue()
                    : 0;
            records.put(entry.getKey(), n);
            total += n;
            if (n == 0) {
                none++;
            }
        }
        if (LargeResults.length(records) <= room.target() / 3) {
            outlined.put("counts", records);
        } else {
            final Map<String, Object> said = new LinkedHashMap<>();
            said.put(EVENT_TYPES, counts.size());
            said.put("records", total);
            said.put("event_types_with_none", none);
            outlined.put("counts", said);
        }
        outlined.remove("order");
        return outlined;
    }

    // The catalogue: its event types counted by status, their TypeIds listed (so far as they fit), the catch-all's
    // status as it is.
    private static Object catalogue(final Object value, final LargeResults.Room room) {
        final Map<String, Object> catalogue = JsonValues.map(value);
        if (catalogue == null) {
            return value;
        }
        final Map<String, Object> outlined = new LinkedHashMap<>();
        final List<Map<String, Object>> types = JsonValues.maps(catalogue.get(EVENT_TYPES));
        final Map<String, Integer> byStatus = new LinkedHashMap<>();
        final List<String> unconfirmed = new ArrayList<>();
        for (final Map<String, Object> type : types) {
            final String status = ToolText.str(type.get(STATUS));
            byStatus.merge(status, 1, Integer::sum);
            if (!"confirmed".equals(status)) {
                unconfirmed.add(ToolText.str(type.get("type_id")));
            }
        }
        outlined.put(EVENT_TYPES, types.size());
        outlined.put("by_status", byStatus);
        if (!unconfirmed.isEmpty()) {
            outlined.put("not_confirmed", typeIds(unconfirmed, "not confirmed yet"));
        }
        catalogue.forEach((key, v) -> {
            if (!EVENT_TYPES.equals(key)) {
                outlined.put(key, v);
            }
        });
        return outlined;
    }

    // The set's table, which the user must be shown whole: as it is when it fits; else its header and the rows that
    // fit, then a line saying it is cut, how many rows are left and how to read them for the user.
    private static Object table(final Object value, final LargeResults.Room room) {
        if (!(value instanceof final String table) || LargeResults.length(table) <= room.target()) {
            return value;
        }
        final List<String> lines = table.lines().toList();
        final StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (final String line : lines) {
            // As JSON, each line its newline escaped.
            if (shown >= HEADER_LINES && sb.length() + LargeResults.length(line) > room.target() - TABLE_NOTE_ROOM) {
                break;
            }
            sb.append(line).append('\n');
            shown++;
        }
        if (shown >= lines.size()) {
            return value;
        }
        final int rows = lines.size() - HEADER_LINES;
        final int rowsShown = shown - HEADER_LINES;
        return sb.append('\n')
                .append("(This table is too large to give whole here: these are the first ").append(rowsShown)
                .append(" of its ").append(rows).append(" rows. Show the user all of it, as it is: these rows, ")
                .append("then the other ").append(rows - rowsShown).append(", read with jq (path=")
                .append(room.saved()).append(", query='.table[").append(shown).append(":][]', raw=true).)")
                .toString();
    }

    // TypeIds counted and listed, so far as they fit.
    private static String typeIds(final List<String> typeIds, final String what) {
        final String listed = String.join(", ", typeIds);
        final int cut = listed.lastIndexOf(", ", TYPE_ID_CHARS);
        return typeIds.size() + " event type(s) " + what + ": " + (listed.length() <= TYPE_ID_CHARS
                ? listed
                : listed.substring(0, cut > 0
                        ? cut
                        : TYPE_ID_CHARS) + " ... (the rest in the saved file)");
    }
}
