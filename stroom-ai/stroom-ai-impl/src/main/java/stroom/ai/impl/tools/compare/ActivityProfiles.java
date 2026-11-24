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

package stroom.ai.impl.tools.compare;

import stroom.ai.impl.tools.compare.AggregateQuery.Bucket;
import stroom.ai.impl.tools.compare.AggregateQuery.Kind;
import stroom.ai.impl.tools.compare.AggregateQuery.Metric;
import stroom.ai.impl.tools.compare.AggregateQuery.Source;
import stroom.ai.impl.tools.compare.AggregateRunner.Table;
import stroom.util.shared.NullSafe;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/// The aggregates `compare_activity` asks of one index for one person, and the daily features read back from them:
/// volume (events, active hours, sums of numeric fields), rhythm (the first and last activity of each day in the
/// person's zone; events by hour of the week, a profile a month), breadth (distinct values of fields a day) and
/// novelty (values first seen each day, from each value's first time, so a field of many values is still a row a
/// value, not a row an event). Beside them, the whole source's daily volume (everyone's), for coverage and
/// source-wide shifts.
final class ActivityProfiles {

    /// The most rows an aggregate gives (ES|QL's own cap).
    static final int MAX_ROWS = 10_000;
    /// The hours in a week, the length of an hour-of-week profile.
    static final int HOURS_OF_WEEK = 7 * 24;
    static final String EVENTS = "events";
    static final String FIRST = "first_t";
    static final String LAST = "last_t";
    static final String PEOPLE = "people";
    static final String SUM_PREFIX = "s";
    static final String DISTINCT_PREFIX = "d";
    static final String ONE_EVENT = "one_event";

    private ActivityProfiles() {
        // Static only.
    }

    /// The person's daily aggregate: events, first and last times, each sum and each distinct count, by day.
    ///
    /// @param source  The index.
    /// @param scope   The person's field and values.
    /// @param fields  The sum and breadth fields.
    /// @param from    The start.
    /// @param to      The end.
    /// @return The query.
    static AggregateQuery daily(final Source source,
                                final Scope scope,
                                final Fields fields,
                                final Instant from,
                                final Instant to) {
        final List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric(Kind.COUNT, null, EVENTS));
        metrics.add(new Metric(Kind.MIN_TIME, null, FIRST));
        metrics.add(new Metric(Kind.MAX_TIME, null, LAST));
        for (int i = 0; i < fields.sums().size(); i++) {
            metrics.add(new Metric(Kind.SUM, fields.sums().get(i), SUM_PREFIX + i));
        }
        for (int i = 0; i < fields.breadth().size(); i++) {
            metrics.add(new Metric(Kind.DISTINCT, fields.breadth().get(i), DISTINCT_PREFIX + i));
        }
        return new AggregateQuery(source, scope.filter(), from, to, Bucket.DAY, List.of(), metrics, MAX_ROWS);
    }

    /// @return The person's events by hour.
    static AggregateQuery hourly(final Source source, final Scope scope, final Instant from, final Instant to) {
        return new AggregateQuery(source, scope.filter(), from, to, Bucket.HOUR, List.of(),
                List.of(new Metric(Kind.COUNT, null, EVENTS)), MAX_ROWS);
    }

    /// @return Each value of a field the person's events hold, with its first and last times and events.
    static AggregateQuery seen(final Source source,
                               final Scope scope,
                               final String field,
                               final Instant from,
                               final Instant to) {
        return new AggregateQuery(source, scope.filter(), from, to, Bucket.NONE, List.of(field), List.of(
                new Metric(Kind.MIN_TIME, null, FIRST),
                new Metric(Kind.MAX_TIME, null, LAST),
                new Metric(Kind.COUNT, null, EVENTS)), MAX_ROWS);
    }

    /// @return Everyone's events and the distinct people (by the person's field) by day: the source's own volume.
    static AggregateQuery population(final Source source, final String field, final Instant from, final Instant to) {
        return new AggregateQuery(source, Map.of(), from, to, Bucket.DAY, List.of(), List.of(
                new Metric(Kind.COUNT, null, EVENTS),
                new Metric(Kind.DISTINCT, field, PEOPLE)), MAX_ROWS);
    }

    /// @return The people (values of the field) active in the range, with their events and one event's ids each.
    static AggregateQuery active(final Source source,
                                 final String field,
                                 final Instant from,
                                 final Instant to,
                                 final int limit) {
        return new AggregateQuery(source, Map.of(), from, to, Bucket.NONE, List.of(field), List.of(
                new Metric(Kind.COUNT, null, EVENTS),
                new Metric(Kind.ONE_EVENT, null, ONE_EVENT)), limit);
    }

    /// @return Each of some people's events by day.
    static AggregateQuery peersDaily(final Source source,
                                     final String field,
                                     final List<String> people,
                                     final Instant from,
                                     final Instant to) {
        return new AggregateQuery(source, Map.of(field, people), from, to, Bucket.DAY, List.of(field),
                List.of(new Metric(Kind.COUNT, null, EVENTS)), MAX_ROWS);
    }

    // --------------------------------------------------------------------------------
    // Reading

    /// Reads the person's days.
    ///
    /// @param daily  The daily aggregate's table.
    /// @param hourly The hourly aggregate's table.
    /// @param fields The sum and breadth fields, as asked.
    /// @param zone   The person's zone.
    /// @return Each day with activity, in order.
    static SortedMap<LocalDate, Day> days(final Table daily,
                                          final Table hourly,
                                          final Fields fields,
                                          final ZoneId zone) {
        final Map<LocalDate, Integer> activeHours = new TreeMap<>();
        for (final Map<String, String> row : hourly.rows()) {
            final Instant hour = instant(row.get(AggregateQuery.BUCKET_COLUMN));
            if (hour != null && number(row.get(EVENTS)) > 0) {
                activeHours.merge(ZonedDateTime.ofInstant(hour, zone).toLocalDate(), 1, Integer::sum);
            }
        }
        final SortedMap<LocalDate, Day> days = new TreeMap<>();
        for (final Map<String, String> row : daily.rows()) {
            final LocalDate date = date(row.get(AggregateQuery.BUCKET_COLUMN));
            if (date == null) {
                continue;
            }
            final Map<String, Double> sums = new LinkedHashMap<>();
            for (int i = 0; i < fields.sums().size(); i++) {
                sums.put(fields.sums().get(i), number(row.get(SUM_PREFIX + i)));
            }
            final Map<String, Double> distinct = new LinkedHashMap<>();
            for (int i = 0; i < fields.breadth().size(); i++) {
                distinct.put(fields.breadth().get(i), number(row.get(DISTINCT_PREFIX + i)));
            }
            final Day day = new Day(date, number(row.get(EVENTS)), activeHours.getOrDefault(date, 0),
                    minuteOfDay(row.get(FIRST), zone), minuteOfDay(row.get(LAST), zone), sums, distinct);
            days.merge(date, day, Day::plus);
        }
        return days;
    }

    /// Reads the person's events by hour of the week, a profile a month.
    ///
    /// @param hourly The hourly aggregate's table.
    /// @param zone   The person's zone.
    /// @return Each month's profile: events at each hour of the week, Monday 00:00 first.
    static SortedMap<YearMonth, double[]> hourOfWeek(final Table hourly, final ZoneId zone) {
        final SortedMap<YearMonth, double[]> profiles = new TreeMap<>();
        for (final Map<String, String> row : hourly.rows()) {
            final Instant hour = instant(row.get(AggregateQuery.BUCKET_COLUMN));
            if (hour == null) {
                continue;
            }
            final ZonedDateTime local = ZonedDateTime.ofInstant(hour, zone);
            final int slot = (local.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue()) * 24 + local.getHour();
            profiles.computeIfAbsent(YearMonth.from(local), k -> new double[HOURS_OF_WEEK])[slot]
                    += number(row.get(EVENTS));
        }
        return profiles;
    }

    /// Reads each value's first and last times.
    ///
    /// @param seen The seen aggregate's table.
    /// @return Each value, in the order given.
    static List<Seen> seen(final Table seen) {
        final List<Seen> values = new ArrayList<>();
        for (final Map<String, String> row : seen.rows()) {
            final String value = row.get(AggregateQuery.groupColumn(0));
            final Instant first = instant(row.get(FIRST));
            final Instant last = instant(row.get(LAST));
            if (NullSafe.isNonBlankString(value) && first != null && last != null) {
                values.add(new Seen(value, first, last, (long) number(row.get(EVENTS))));
            }
        }
        return values;
    }

    /// Reads the source's own volume.
    ///
    /// @param population The population aggregate's table.
    /// @return Everyone's events and people by day.
    static SortedMap<LocalDate, Population> population(final Table population) {
        final SortedMap<LocalDate, Population> days = new TreeMap<>();
        for (final Map<String, String> row : population.rows()) {
            final LocalDate date = date(row.get(AggregateQuery.BUCKET_COLUMN));
            if (date != null) {
                days.merge(date, new Population(number(row.get(EVENTS)), number(row.get(PEOPLE))),
                        (a, b) -> new Population(a.events() + b.events(), Math.max(a.people(), b.people())));
            }
        }
        return days;
    }

    /// Reads several people's events by day.
    ///
    /// @param peers The peers' aggregate's table.
    /// @return Each day's events by person.
    static SortedMap<LocalDate, Map<String, Double>> peersDaily(final Table peers) {
        final SortedMap<LocalDate, Map<String, Double>> days = new TreeMap<>();
        for (final Map<String, String> row : peers.rows()) {
            final LocalDate date = date(row.get(AggregateQuery.BUCKET_COLUMN));
            final String person = row.get(AggregateQuery.groupColumn(0));
            if (date != null && NullSafe.isNonBlankString(person)) {
                days.computeIfAbsent(date, k -> new LinkedHashMap<>()).merge(person, number(row.get(EVENTS)),
                        Double::sum);
            }
        }
        return days;
    }

    /// @param value A number as an aggregate gives it, or empty.
    /// @return It, or 0 if empty or not a number.
    static double number(final String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.strip());
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    /// @param value An ISO instant, or empty.
    /// @return It, or null.
    static Instant instant(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(IndexAggregates.instant(value));
        } catch (final DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate date(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (final DateTimeParseException e) {
            return null;
        }
    }

    // The minutes since the local midnight of a time, or null.
    private static Integer minuteOfDay(final String value, final ZoneId zone) {
        final Instant instant = instant(value);
        if (instant == null) {
            return null;
        }
        final ZonedDateTime local = ZonedDateTime.ofInstant(instant, zone);
        return local.getHour() * 60 + local.getMinute();
    }


    // --------------------------------------------------------------------------------


    /// Whose events an index's aggregates are of.
    ///
    /// @param field  The field filtered on.
    /// @param values Its values (the person id, or the logon ids).
    record Scope(String field, List<String> values) {

        /// Copies the list.
        Scope {
            values = List.copyOf(values);
        }

        /// @return The filter of the aggregates.
        Map<String, List<String>> filter() {
            return Map.of(field, values);
        }
    }


    // --------------------------------------------------------------------------------


    /// The fields summed and counted for breadth.
    ///
    /// @param sums    Numeric fields summed a day (e.g. bytes out).
    /// @param breadth Fields whose distinct values are counted a day, and whose new values are found.
    record Fields(List<String> sums, List<String> breadth) {

        /// Copies the lists.
        Fields {
            sums = List.copyOf(sums);
            breadth = List.copyOf(breadth);
        }
    }


    // --------------------------------------------------------------------------------


    /// A day's features.
    ///
    /// @param date        The day, in the person's zone.
    /// @param events      The events.
    /// @param activeHours The hours with any event.
    /// @param firstMinute The first event's minutes since midnight, or null.
    /// @param lastMinute  The last event's minutes since midnight, or null.
    /// @param sums        Each summed field's sum.
    /// @param distinct    Each breadth field's distinct values.
    record Day(LocalDate date,
               double events,
               int activeHours,
               Integer firstMinute,
               Integer lastMinute,
               Map<String, Double> sums,
               Map<String, Double> distinct) {

        /// The same day from two slices (a day cut by a slice's edge in a zone a cluster didn't apply): added, the
        /// distinct counts the larger (an underestimate).
        ///
        /// @param other The other part.
        /// @return The two as one.
        Day plus(final Day other) {
            final Map<String, Double> sums = new LinkedHashMap<>(this.sums);
            other.sums.forEach((k, v) -> sums.merge(k, v, Double::sum));
            final Map<String, Double> distinct = new LinkedHashMap<>(this.distinct);
            other.distinct.forEach((k, v) -> distinct.merge(k, v, Math::max));
            return new Day(date, events + other.events, Math.max(activeHours, other.activeHours),
                    min(firstMinute, other.firstMinute), max(lastMinute, other.lastMinute), sums, distinct);
        }

        private static Integer min(final Integer a, final Integer b) {
            return a == null
                    ? b
                    : b == null
                            ? a
                            : Integer.valueOf(Math.min(a, b));
        }

        private static Integer max(final Integer a, final Integer b) {
            return a == null
                    ? b
                    : b == null
                            ? a
                            : Integer.valueOf(Math.max(a, b));
        }
    }


    // --------------------------------------------------------------------------------


    /// A value's first and last times in the person's events.
    ///
    /// @param value  The value.
    /// @param first  Its first time.
    /// @param last   Its last time.
    /// @param events The events holding it.
    record Seen(String value, Instant first, Instant last, long events) {

    }


    // --------------------------------------------------------------------------------


    /// The whole source's volume on a day.
    ///
    /// @param events Everyone's events.
    /// @param people The distinct people (values of the person's field).
    record Population(double events, double people) {

    }
}
