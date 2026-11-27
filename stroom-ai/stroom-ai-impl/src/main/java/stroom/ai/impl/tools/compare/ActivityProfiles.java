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

import stroom.ai.api.ElasticKeyStreams.Transform;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    /// The prefix of the population's columns of each sum and breadth field's distinct values a day.
    static final String HELD_PREFIX = "h";
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
        return new AggregateQuery(source, scope.filter(), from, to, Bucket.DAY, List.of(), dailyMetrics(fields),
                MAX_ROWS);
    }

    /// What a day gives: events, first and last times, each sum and each distinct count.
    ///
    /// @param fields The sum and breadth fields.
    /// @return The metrics.
    static List<Metric> dailyMetrics(final Fields fields) {
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
        return metrics;
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
        return population(source, field, new Fields(List.of(), List.of()), from, to);
    }

    /// Everyone's events and the distinct people (by the person's field) by day, the source's own volume, with the
    /// distinct values of each sum and breadth field a day, which tell the days each field is held on.
    ///
    /// @param source The index.
    /// @param field  The person's field.
    /// @param fields The sum and breadth fields.
    /// @param from   The start.
    /// @param to     The end.
    /// @return The aggregate.
    static AggregateQuery population(final Source source,
                                     final String field,
                                     final Fields fields,
                                     final Instant from,
                                     final Instant to) {
        final List<Metric> metrics = new ArrayList<>();
        metrics.add(new Metric(Kind.COUNT, null, EVENTS));
        metrics.add(new Metric(Kind.DISTINCT, field, PEOPLE));
        final List<String> held = heldFields(field, fields);
        for (int i = 0; i < held.size(); i++) {
            metrics.add(new Metric(Kind.DISTINCT, held.get(i), HELD_PREFIX + i));
        }
        return new AggregateQuery(source, Map.of(), from, to, Bucket.DAY, List.of(), metrics, MAX_ROWS);
    }

    /// The days each field is held on, from the first to the last day it holds any value in everyone's events: the
    /// person's field (from the people a day) and each sum and breadth field.
    ///
    /// @param field      The person's field.
    /// @param fields     The sum and breadth fields.
    /// @param population The population aggregate's table ([#population(Source, String, Fields, Instant, Instant)]).
    /// @return Each field held on any day, with its first and last day; none for a field never held.
    static List<ActivityScoring.FieldSpan> fieldSpans(final String field,
                                                      final Fields fields,
                                                      final Table population) {
        final List<String> held = heldFields(field, fields);
        final List<ActivityScoring.FieldSpan> spans = new ArrayList<>();
        span(field, PEOPLE, true, population).ifPresent(spans::add);
        for (int i = 0; i < held.size(); i++) {
            span(held.get(i), HELD_PREFIX + i, false, population).ifPresent(spans::add);
        }
        return spans;
    }

    // The sum and breadth fields other than the person's, once each.
    private static List<String> heldFields(final String field, final Fields fields) {
        final Set<String> held = new LinkedHashSet<>(fields.sums());
        held.addAll(fields.breadth());
        held.remove(field);
        return List.copyOf(held);
    }

    private static Optional<ActivityScoring.FieldSpan> span(final String field,
                                                            final String column,
                                                            final boolean person,
                                                            final Table population) {
        LocalDate first = null;
        LocalDate last = null;
        for (final Map<String, String> row : population.rows()) {
            final LocalDate date = date(row.get(AggregateQuery.BUCKET_COLUMN));
            if (date == null || !row.containsKey(column) || number(row.get(column)) <= 0) {
                continue;
            }
            if (first == null || date.isBefore(first)) {
                first = date;
            }
            if (last == null || date.isAfter(last)) {
                last = date;
            }
        }
        return first == null
                ? Optional.empty()
                : Optional.of(new ActivityScoring.FieldSpan(field, first, last, person));
    }

    /// Everyone's events by the field telling feeds apart (a feed field, or the StreamId to be resolved to its
    /// stream's feed), with each value's first and last time: the source's feeds, whoever's events.
    ///
    /// @param source The index.
    /// @param field  The feed field, or `StreamId`.
    /// @param from   The range's start.
    /// @param to     The range's end.
    /// @return The aggregate.
    static AggregateQuery feeds(final Source source, final String field, final Instant from, final Instant to) {
        return new AggregateQuery(source, Map.of(), from, to, Bucket.NONE, List.of(field), List.of(
                new Metric(Kind.COUNT, null, EVENTS),
                new Metric(Kind.MIN_TIME, null, FIRST),
                new Metric(Kind.MAX_TIME, null, LAST)), MAX_ROWS);
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

    /// The events done to the person by day (and by type, where the index has a type field): their target field
    /// holding them, their own acts on themselves left out.
    ///
    /// @param source    The index.
    /// @param target    Whom the events were done to, and the actor's own acts left out.
    /// @param typeField The field holding the type (the Action, else the element or event code), or null.
    /// @param from      The start.
    /// @param to        The end.
    /// @return The query.
    static AggregateQuery targetDaily(final Source source,
                                      final TargetScope target,
                                      final String typeField,
                                      final Instant from,
                                      final Instant to) {
        return new AggregateQuery(source, target.filter(), from, to, Bucket.DAY, typeField == null
                ? List.of()
                : List.of(typeField), List.of(new Metric(Kind.COUNT, null, EVENTS)), MAX_ROWS, target.excludes(),
                Map.of());
    }

    /// @return Each account that acted on the person, with its first and last times: those acting on them for the
    /// first time are found from it, as values first seen are.
    static AggregateQuery targetActors(final Source source,
                                       final TargetScope target,
                                       final String actorField,
                                       final Instant from,
                                       final Instant to) {
        return new AggregateQuery(source, target.filter(), from, to, Bucket.NONE, List.of(actorField), List.of(
                new Metric(Kind.MIN_TIME, null, FIRST),
                new Metric(Kind.MAX_TIME, null, LAST),
                new Metric(Kind.COUNT, null, EVENTS)), MAX_ROWS, target.excludes(), Map.of());
    }

    /// @return Everyone's events done to them by someone else, and the people they were done to, by day: the
    /// source-wide check of what was done to the person (ES|QL only, as it compares two fields).
    static AggregateQuery targetPopulation(final Source source,
                                           final String targetField,
                                           final String actorField,
                                           final Instant from,
                                           final Instant to) {
        final Map<String, String> differing = new LinkedHashMap<>();
        differing.put(targetField, actorField);
        return new AggregateQuery(source, Map.of(), from, to, Bucket.DAY, List.of(), List.of(
                new Metric(Kind.COUNT, null, EVENTS),
                new Metric(Kind.DISTINCT, targetField, PEOPLE)), MAX_ROWS, Map.of(), differing);
    }

    // --------------------------------------------------------------------------------
    // Reading

    /// Reads the events done to the person by day and type.
    ///
    /// @param daily The target daily aggregate's table.
    /// @param typed True if it was grouped by a type field.
    /// @return Each day's events by type ("" untyped).
    static SortedMap<LocalDate, Map<String, Double>> targetDays(final Table daily, final boolean typed) {
        final SortedMap<LocalDate, Map<String, Double>> days = new TreeMap<>();
        for (final Map<String, String> row : daily.rows()) {
            final LocalDate date = date(row.get(AggregateQuery.BUCKET_COLUMN));
            if (date == null) {
                continue;
            }
            final String type = typed
                    ? NullSafe.nonBlankStringElse(row.get(AggregateQuery.groupColumn(0)), "")
                    : "";
            days.computeIfAbsent(date, k -> new LinkedHashMap<>()).merge(type, number(row.get(EVENTS)),
                    Double::sum);
        }
        return days;
    }

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
    /// @param field      The field filtered on.
    /// @param values     Its values (the person id, or the logon ids), as transformed.
    /// @param transforms What is done to the field's values before they are compared (the call's logonForms);
    ///                   empty to match exactly.
    record Scope(String field, List<String> values, List<Transform> transforms) {

        /// Copies the lists.
        Scope {
            values = List.copyOf(values);
            transforms = List.copyOf(NullSafe.list(transforms));
        }

        /// Matched exactly.
        ///
        /// @param field  The person's field.
        /// @param values Its values.
        Scope(final String field, final List<String> values) {
            this(field, values, List.of());
        }

        /// @return The filter of the aggregates.
        Map<String, List<String>> filter() {
            return Map.of(field, values);
        }
    }


    // --------------------------------------------------------------------------------


    /// Whom an index's events were done to.
    ///
    /// @param field       The target field filtered on.
    /// @param values      Its values (the person id, or the logon ids).
    /// @param actorField  The field holding who acted, whose `actorValues` are left out; null for none.
    /// @param actorValues The actor's values left out (the person's own acts on themselves).
    record TargetScope(String field, List<String> values, String actorField, List<String> actorValues) {

        /// Copies the lists.
        TargetScope {
            values = List.copyOf(values);
            actorValues = List.copyOf(NullSafe.list(actorValues));
        }

        /// @return The filter of the aggregates.
        Map<String, List<String>> filter() {
            return Map.of(field, values);
        }

        /// @return What the aggregates leave out: the person's own acts.
        Map<String, List<String>> excludes() {
            return actorField == null || actorValues.isEmpty()
                    ? Map.of()
                    : Map.of(actorField, actorValues);
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
