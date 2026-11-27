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

package stroom.ai.impl.tools.timeline;

import stroom.ai.api.ElasticEsql;
import stroom.ai.impl.tools.timeline.TimelineReader.Source;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.task.api.TaskTerminatedException;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

/// A long period's events by aggregation, before any is read (decided with the user 2026-11-25): first each source's
/// events of the user in the period, counted (with the first and last) in one aggregate; where they are more than a
/// timeline reads (the events cap), no event is read: the period is given a day at a time instead (events per day by
/// source, and by type where the index holds one, and the active days), for the user, or the calling model, to pick
/// days to read in detail. Elasticsearch counts them as the user; the days are in the user's zone (the query's
/// `time_zone`).
final class TimelineOverview {

    /// The most days an overview gives per source.
    static final int MAX_DAYS = 400;
    /// The most day and type rows an overview gives per source.
    static final int MAX_TYPE_ROWS = 2_000;

    private TimelineOverview() {
        // Static only.
    }

    /// Counts a source's events of the user in the period, with the first and last.
    ///
    /// @param esql      Runs the aggregate.
    /// @param source    The source.
    /// @param timeField Its index's time field.
    /// @param range     The period.
    /// @param left      How long the query may take.
    /// @param stopped   True once the user has stopped the call.
    /// @return The count; unknown (-1) where the source can't be counted so (its user matched by a script, or by
    /// several fields) or the aggregate fails.
    static Counted count(final ElasticEsql esql,
                         final Source source,
                         final String timeField,
                         final TimeRange range,
                         final Duration left,
                         final BooleanSupplier stopped) {
        if (!countable(source, timeField)) {
            return Counted.unknown(source.name());
        }
        final String time = quoted(timeField);
        final ElasticEsql.Result result = run(esql, new ElasticEsql.Request(source.doc(), "| STATS first_seen = "
                + "MIN(" + time + "), last_seen = MAX(" + time + "), events = COUNT(*)", filter(source), range, 1,
                left, false, stopped));
        if (result == null || result.rows().isEmpty()) {
            return Counted.unknown(source.name());
        }
        final List<Object> row = result.rows().getFirst();
        final long events = row.size() > 2 && row.get(2) instanceof final Number number
                ? number.longValue()
                : -1;
        return new Counted(source.name(), events, events > 0
                ? text(row.get(0))
                : null, events > 0
                ? text(row.get(1))
                : null);
    }

    /// The period a day at a time, per source: its events each day, and each day's by type where the index holds
    /// the type.
    ///
    /// @param esql      Runs the aggregates.
    /// @param source    The source.
    /// @param timeField Its index's time field.
    /// @param typeField The field holding an event's type (its event code, else its element), or null.
    /// @param range     The period.
    /// @param zone      The user's zone, the days'.
    /// @param left      How long each query may take.
    /// @param stopped   True once the user has stopped the call.
    /// @return The source's days, each with its events (and by type); empty where it can't be aggregated so.
    static SourceDays days(final ElasticEsql esql,
                           final Source source,
                           final String timeField,
                           final String typeField,
                           final TimeRange range,
                           final String zone,
                           final Duration left,
                           final BooleanSupplier stopped) {
        final Map<String, Long> byDay = new TreeMap<>();
        final Map<String, Map<String, Long>> byType = new TreeMap<>();
        if (!countable(source, timeField)) {
            return new SourceDays(source.name(), typeField, byDay, byType, false);
        }
        final String day = "| EVAL day = DATE_FORMAT(\"yyyy-MM-dd\", " + quoted(timeField) + ")";
        final ElasticEsql.Result days = run(esql, new ElasticEsql.Request(source.doc(), day + " | STATS events = "
                + "COUNT(*) BY day | SORT day", filter(source), range, MAX_DAYS, left, false, stopped, zone));
        boolean cut = days != null && days.truncated();
        for (final List<Object> row : days == null
                ? List.<List<Object>>of()
                : days.rows()) {
            final Long events = row.isEmpty() || !(row.getFirst() instanceof final Number number)
                    ? null
                    : number.longValue();
            if (events != null && row.size() > 1 && row.get(1) != null) {
                byDay.put(String.valueOf(row.get(1)), events);
            }
        }
        if (typeField != null) {
            final ElasticEsql.Result types = run(esql, new ElasticEsql.Request(source.doc(), day + " | STATS events "
                    + "= COUNT(*) BY day, " + quoted(typeField) + " | SORT day", filter(source), range, MAX_TYPE_ROWS,
                    left, false, stopped, zone));
            cut |= types != null && types.truncated();
            for (final List<Object> row : types == null
                    ? List.<List<Object>>of()
                    : types.rows()) {
                if (row.size() > 2 && row.getFirst() instanceof final Number number && row.get(1) != null) {
                    byType.computeIfAbsent(String.valueOf(row.get(1)), k -> new TreeMap<>())
                            .put(NullSafe.nonBlankStringElse(text(row.get(2)), "(none)"), number.longValue());
                }
            }
        }
        return new SourceDays(source.name(), typeField, byDay, byType, cut);
    }

    // A source's user can be filtered on in an aggregate: one field, matched exactly (a transform is a script).
    private static boolean countable(final Source source, final String timeField) {
        return timeField != null && source.transforms().isEmpty() && source.alsoUserFields().isEmpty()
               && !source.values().isEmpty();
    }

    private static ExpressionOperator filter(final Source source) {
        return ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder().field(source.userField()).condition(Condition.IN)
                        .value(String.join(",", source.values())).build())
                .build();
    }

    private static ElasticEsql.Result run(final ElasticEsql esql, final ElasticEsql.Request request) {
        try {
            return esql.query(request);
        } catch (final ElasticEsql.Refusal refusal) {
            if (refusal.reason() == ElasticEsql.Reason.STOPPED) {
                throw new TaskTerminatedException();
            }
            return null;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static String quoted(final String field) {
        return "`" + field.replace("`", "``") + "`";
    }

    private static String text(final Object value) {
        if (value == null) {
            return null;
        }
        final String text = String.valueOf(value).strip();
        return text.isEmpty()
                ? null
                : text;
    }

    /// @param deadline When the call must end.
    /// @return What remains of its time, at least a second.
    static Duration atLeastASecond(final Instant deadline) {
        final Duration left = Duration.between(Instant.now(), deadline);
        return left.compareTo(Duration.ofSeconds(1)) < 0
                ? Duration.ofSeconds(1)
                : left;
    }


    // --------------------------------------------------------------------------------


    /// A source's events of the user in the period.
    ///
    /// @param source Its name.
    /// @param events How many, or -1 if that isn't known.
    /// @param first  The first one's time, or null.
    /// @param last   The last one's time, or null.
    record Counted(String source, long events, String first, String last) {

        static Counted unknown(final String source) {
            return new Counted(source, -1, null, null);
        }
    }


    // --------------------------------------------------------------------------------


    /// A source's period a day at a time.
    ///
    /// @param source    Its name.
    /// @param typeField The field its types are by, or null.
    /// @param byDay     Its events each day (`yyyy-MM-dd` in the user's zone), in order.
    /// @param byType    Each day's events by type, in order.
    /// @param cut       True if a day or type row was left out past the rows an overview gives.
    record SourceDays(String source,
                      String typeField,
                      Map<String, Long> byDay,
                      Map<String, Map<String, Long>> byType,
                      boolean cut) {

        /// @return The days it has any events.
        List<String> activeDays() {
            final List<String> out = new ArrayList<>();
            byDay.forEach((day, events) -> {
                if (events > 0) {
                    out.add(day);
                }
            });
            return out;
        }
    }
}
