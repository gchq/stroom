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

package stroom.ai.impl.tools.sequence;

import stroom.ai.api.ElasticEql.Result;
import stroom.ai.impl.tools.ToolTables;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.sequence.EqlSequenceResults.Entity;
import stroom.ai.impl.tools.sequence.SequenceQueryTool.Outcome;
import stroom.ai.impl.tools.sequence.SequenceQueryTool.Preflight;
import stroom.ai.impl.tools.sequence.SequenceQueryTool.ReviewNote;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// What a `sequence_query` call ([SequenceQueryTool]) that ran as one EQL sequence returns: its text, for a chat,
/// and its structured content, with the notes on the sequences found.
final class SequenceResultText {

    private SequenceResultText() {
        // Static methods only.
    }

    /// @param outcome The call's outcome.
    /// @return The result as the chat shows it.
    static String markdown(final Outcome outcome) {
        final Result result = outcome.result();
        final StringBuilder sb = new StringBuilder("Ran the EQL sequence on ")
                .append(String.join(", ", outcome.docs().stream().map(DataSourceAccess::describe).toList()))
                .append(" (Elasticsearch ").append(result.version()).append(", ").append(result.tookMs())
                .append(" ms), with maxspan ").append(EqlQueryWriter.maxspanText(outcome.maxspan())).append(" (")
                .append(outcome.maxspanSource()).append("). ").append(SequenceQueryTool.EQL_REASON).append("\n\n```\n")
                .append(result.query()).append("\n```\n");
        outcome.fieldNotes().forEach(note -> sb.append(note).append('\n'));
        if (outcome.timeRange() != null) {
            sb.append("Time: ").append(NullSafe.nonBlankStringElse(outcome.timeRange().getFrom(), "any"))
                    .append(" to ").append(NullSafe.nonBlankStringElse(outcome.timeRange().getTo(), "any"))
                    .append(".\n");
        }
        sb.append(outcome.zone().stated()).append('\n');
        final String counted = preflightText(outcome.preflight());
        if (!counted.isEmpty()) {
            sb.append('\n').append(counted).append('\n');
        }
        sb.append('\n').append(sequencesNote(outcome)).append('\n');
        if (!outcome.entities().isEmpty()) {
            final List<String> columns = new ArrayList<>(outcome.steps().getFirst().joinFields());
            columns.addAll(List.of("sequences", "first", "last", "shortest gap (s)"));
            final List<List<String>> cells = outcome.entities().stream()
                    .map(entity -> entityCells(entity, outcome.zone()))
                    .toList();
            sb.append("\nBy entity (sequences found, at least these):\n\n")
                    .append(ToolTables.preview(columns, cells, SequenceQueryTool.SHOWN_ROWS)).append('\n');
            sb.append("\nSequences:\n\n")
                    .append(ToolTables.preview(outcome.columns(), outcome.cells(), SequenceQueryTool.SHOWN_ROWS))
                    .append("\n\nThe sequences are in ").append(outcome.out()).append(" (each step's StreamId and "
                                                                                    + "EventId, for fetch_events and "
                                                                                    + "search_events).\n");
        }
        sb.append('\n').append(SequenceQueryTool.CAVEAT);
        if (!outcome.reviews().isEmpty()) {
            sb.append("\n\n").append(reviewHint(outcome.reviews().size()));
            for (final ReviewNote review : outcome.reviews()) {
                sb.append("\n\n").append(DataSourceAccess.describe(review.review().doc())).append(": ")
                        .append(review.note());
                if (review.invalid() != null) {
                    sb.append(" It isn't valid StroomQL (").append(review.invalid()).append("), so don't offer it.");
                }
                sb.append("\n\n```\n").append(review.review().query()).append("\n```");
            }
        }
        if (outcome.stroomQl() != null && outcome.stroomQlInvalid() == null) {
            sb.append("\n\n").append(SequenceQueryTool.STROOMQL_NOTE).append("\n\n```\n").append(outcome.stroomQl())
                    .append("\n```");
        }
        return sb.toString();
    }

    /// @param reviews How many docs have a review query.
    /// @return What to do with them.
    static String reviewHint(final int reviews) {
        return reviews == 1
                ? "The review query (validated StroomQL) looks at the events in Stroom: offer it with propose_query, "
                  + "or propose_dashboard for a table with the event preview."
                : "There is a review query (validated StroomQL) per doc, and a query or dashboard reads one doc: ask "
                  + "the user which doc to review (ask_user, the docs as options), then offer that doc's with "
                  + "propose_query, or propose_dashboard for a table with the event preview.";
    }

    private static List<String> entityCells(final Entity entity, final ToolTimeZone zone) {
        final List<String> cells = new ArrayList<>(entity.keys());
        cells.add(String.valueOf(entity.sequences()));
        cells.add(zone.inZone(EqlSequenceResults.iso(entity.first())));
        cells.add(zone.inZone(EqlSequenceResults.iso(entity.last())));
        cells.add(EqlSequenceResults.seconds(entity.shortestSpan()));
        return cells;
    }

    private static String preflightText(final Preflight preflight) {
        final StringBuilder sb = new StringBuilder();
        if (preflight.counted()) {
            sb.append("Counted first: ").append(SequenceQueryTool.countsText(preflight.counts())).append(" events.");
        } else if (preflight.skippedNote() != null) {
            sb.append(preflight.skippedNote());
        }
        preflight.notes().forEach(note -> sb.append(' ').append(note));
        return sb.toString().strip();
    }

    /// What the sequences are: how many, and whether the limit cut them short.
    ///
    /// @param outcome The call's outcome.
    /// @return The note.
    static String sequencesNote(final Outcome outcome) {
        final int count = outcome.result().sequences().size();
        final StringBuilder sb = new StringBuilder().append(count).append(" sequence").append(count == 1
                ? ""
                : "s").append(" found, for ").append(outcome.entities().size()).append(" entit")
                .append(outcome.entities().size() == 1
                        ? "y"
                        : "ies").append('.');
        if (count > 0) {
            sb.append(' ').append(SequenceQueryTool.AT_LEAST);
        } else if (outcome.docs().size() > 1) {
            sb.append(' ').append(SequenceQueryTool.NO_MATCH_ACROSS_DOCS);
        }
        if (outcome.untimed() > 0) {
            sb.append(' ').append(untimedNote(outcome.untimed(), count));
        }
        if (outcome.limitNote() != null) {
            sb.append(' ').append(outcome.limitNote());
        }
        if (outcome.truncated()) {
            sb.append(" They reached the limit (").append(outcome.limit()).append("), so they are cut short: these "
                                                                                + "are the latest ")
                    .append(outcome.limit()).append(", and there may be more. Narrow the window or the steps, or "
                                                    + "raise limit (at most " + outcome.mostSequences() + "), and "
                                                    + "don't present them as all.");
        }
        return sb.toString();
    }

    /// @param untimed   How many sequences have no time.
    /// @param sequences How many sequences there are.
    /// @return What that means for their times.
    static String untimedNote(final int untimed, final int sequences) {
        return untimed + " of the " + sequences + " sequence" + (sequences == 1
                ? ""
                : "s") + " came back with no time on " + (untimed == 1
                ? "its"
                : "their") + " events (Elasticsearch didn't give the time field), so " + (untimed == 1
                ? "its"
                : "their") + " times and gaps are empty: don't read them as quick, and check the events with "
               + "fetch_events (by StreamId and EventId) before giving times.";
    }

    /// @param outcome The call's outcome.
    /// @return The result's structured content, which a client outside the chat is given as JSON.
    static Map<String, Object> structured(final Outcome outcome) {
        final Result result = outcome.result();
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("engine", "EQL");
        map.put("engine_reason", SequenceQueryTool.EQL_REASON);
        map.put("indexes", outcome.docs().stream().map(doc -> {
            final Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("type", doc.getType());
            ref.put("uuid", doc.getUuid());
            ref.put("name", doc.getName());
            return ref;
        }).toList());
        map.put("index_names", result.indexNames());
        map.put("query", result.query());
        map.put("maxspan", EqlQueryWriter.maxspanText(outcome.maxspan()));
        map.put("maxspan_source", outcome.maxspanSource());
        if (outcome.timeRange() != null) {
            final Map<String, Object> time = new LinkedHashMap<>();
            time.put("from", outcome.timeRange().getFrom());
            time.put("to", outcome.timeRange().getTo());
            map.put("time_range", time);
        }
        map.putAll(outcome.zone().structured());
        map.put("time_field", result.timeField());
        if (!outcome.fieldNotes().isEmpty()) {
            map.put("field_notes", outcome.fieldNotes());
        }
        map.put("version", result.version());
        map.put("took_ms", result.tookMs());
        final Preflight preflight = outcome.preflight();
        if (preflight.counted()) {
            final List<Object> counts = new ArrayList<>(preflight.counts());
            map.put("step_counts", counts);
        }
        if (!preflight.notes().isEmpty() || preflight.skippedNote() != null) {
            map.put("preflight_notes", preflightText(preflight));
        }
        map.put("sequence_count", result.sequences().size());
        map.put("entity_count", outcome.entities().size());
        if (outcome.untimed() > 0) {
            map.put("untimed_sequence_count", outcome.untimed());
        }
        map.put("limit", outcome.limit());
        map.put("truncated", outcome.truncated());
        map.put("note", sequencesNote(outcome));
        map.put("entities", outcome.entities().stream().limit(SequenceQueryTool.SHOWN_ROWS).map(entity -> {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("keys", entity.keys());
            row.put("sequences", entity.sequences());
            row.put("first", outcome.zone().inZone(EqlSequenceResults.iso(entity.first())));
            row.put("last", outcome.zone().inZone(EqlSequenceResults.iso(entity.last())));
            row.put("shortest_gap_s", EqlSequenceResults.seconds(entity.shortestSpan()));
            return row;
        }).toList());
        map.put("columns", outcome.columns());
        map.put("rows", outcome.cells().stream().limit(SequenceQueryTool.SHOWN_ROWS).toList());
        if (!outcome.cells().isEmpty()) {
            map.put("table", outcome.out().toString());
        }
        map.put("caveat", SequenceQueryTool.CAVEAT);
        map.put("review_queries", outcome.reviews().stream().map(review -> {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", review.review().doc().getName());
            entry.put("uuid", review.review().doc().getUuid());
            entry.put("query", review.review().query());
            entry.put("exact", review.review().exact());
            entry.put("valid", review.invalid() == null);
            if (review.invalid() != null) {
                entry.put("invalid", review.invalid());
            }
            entry.put("note", review.note());
            return entry;
        }).toList());
        if (!outcome.reviews().isEmpty()) {
            map.put("review_hint", reviewHint(outcome.reviews().size()));
        }
        if (outcome.stroomQl() != null) {
            map.put("stroomql", outcome.stroomQl());
            map.put("stroomql_valid", outcome.stroomQlInvalid() == null);
            if (outcome.stroomQlInvalid() != null) {
                map.put("stroomql_invalid", outcome.stroomQlInvalid());
            }
            map.put("stroomql_note", SequenceQueryTool.STROOMQL_NOTE);
        }
        return map;
    }
}
