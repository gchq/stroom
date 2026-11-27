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

import stroom.ai.impl.tools.compare.ActivityProfiles.Fields;
import stroom.ai.impl.tools.compare.ActivityProfiles.Scope;
import stroom.ai.impl.tools.compare.ActivityScoring.PeerData;
import stroom.ai.impl.tools.compare.ActivityScoring.SourceData;
import stroom.ai.impl.tools.compare.AggregateQuery.Source;
import stroom.ai.impl.tools.compare.AggregateRunner.Context;
import stroom.ai.impl.tools.compare.AggregateRunner.Table;
import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticIndexDoc;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/// Events for compare_activity's tests: working days of hourly events for people, in a zone, and the source's
/// aggregates read from them through [InMemoryAggregates], as the tool reads them.
final class CompareFixtures {

    static final String PERSON_FIELD = "user.id";
    static final String TIME_FIELD = "@timestamp";
    static final String HOST_FIELD = "host.name";
    static final String DESTINATION_FIELD = "destination.domain";
    static final DocRef PROXY = new DocRef(ElasticIndexDoc.TYPE, "proxy-uuid", "Proxy");
    static final Source SOURCE = new Source(PROXY, TIME_FIELD, true);

    private final InMemoryAggregates aggregates = new InMemoryAggregates();
    private final ZoneId zone;
    private int nextEventId = 1;

    /// @param zone The people's zone.
    CompareFixtures(final ZoneId zone) {
        this.zone = zone;
    }

    InMemoryAggregates aggregates() {
        return aggregates;
    }

    /// Adds a person's working days: on each weekday from `from` to `to` (exclusive), `perHour(date)` events in each
    /// hour from `startHour` to `endHour` (exclusive, local), each with the fields `fields(date)` gives.
    void workdays(final String person,
                  final LocalDate from,
                  final LocalDate to,
                  final int startHour,
                  final int endHour,
                  final Function<LocalDate, Integer> perHour,
                  final Function<LocalDate, Map<String, Object>> fields) {
        for (LocalDate date = from; date.isBefore(to); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            hours(person, date, startHour, endHour, perHour.apply(date), fields.apply(date));
        }
    }

    /// Adds `count` events in each hour from `startHour` to `endHour` (local; past 24 runs into the next day).
    void hours(final String person,
               final LocalDate date,
               final int startHour,
               final int endHour,
               final int count,
               final Map<String, Object> fields) {
        for (int hour = startHour; hour < endHour; hour++) {
            for (int i = 0; i < count; i++) {
                final Instant time = date.atStartOfDay(zone).plusHours(hour).plusMinutes(5L + (50L * i) / count)
                        .toInstant();
                add(person, time, fields);
            }
        }
    }

    /// Adds one event.
    void add(final String person, final Instant time, final Map<String, Object> fields) {
        final Map<String, Object> event = new LinkedHashMap<>(fields);
        event.put(TIME_FIELD, time);
        event.put(PERSON_FIELD, person);
        event.put("StreamId", 1000L + nextEventId / 1000);
        event.put("EventId", (long) (nextEventId % 1000) + 1);
        nextEventId++;
        aggregates.source(PROXY.getUuid()).add(event);
    }

    /// Takes a field out of every event before a day (local), as an index holds a field only from where a
    /// reprocessed range starts.
    void dropFieldBefore(final String field, final LocalDate date) {
        final Instant until = date.atStartOfDay(zone).toInstant();
        for (final Map<String, Object> event : aggregates.source(PROXY.getUuid())) {
            if (((Instant) event.get(TIME_FIELD)).isBefore(until)) {
                event.remove(field);
            }
        }
    }

    /// Adds `count` events spread evenly from `startMinute` to `endMinute` (minutes since local midnight).
    void minutes(final String person,
                 final LocalDate date,
                 final int startMinute,
                 final int endMinute,
                 final int count) {
        for (int i = 0; i < count; i++) {
            final long minute = startMinute + (long) (endMinute - startMinute) * i / Math.max(1, count - 1);
            add(person, date.atStartOfDay(zone).plusMinutes(minute).toInstant(), Map.of());
        }
    }

    /// Reads a person's source data as the tool does.
    SourceData data(final String person,
                    final Fields fields,
                    final LocalDate from,
                    final LocalDate to,
                    final List<String> peers) {
        final Scope scope = new Scope(PERSON_FIELD, List.of(person));
        final Instant start = from.atStartOfDay(zone).toInstant();
        final Instant end = to.atStartOfDay(zone).toInstant();
        final List<AggregateQuery> queries = new ArrayList<>();
        queries.add(ActivityProfiles.daily(SOURCE, scope, fields, start, end));
        queries.add(ActivityProfiles.hourly(SOURCE, scope, start, end));
        queries.add(ActivityProfiles.population(SOURCE, PERSON_FIELD, fields, start, end));
        fields.breadth().forEach(field -> queries.add(ActivityProfiles.seen(SOURCE, scope, field, start, end)));
        if (!peers.isEmpty()) {
            queries.add(ActivityProfiles.peersDaily(SOURCE, PERSON_FIELD, peers, start, end));
        }
        final List<Table> tables = aggregates.run(queries, context());
        final Map<String, List<ActivityProfiles.Seen>> seen = new LinkedHashMap<>();
        final Set<String> cutShort = new LinkedHashSet<>();
        for (int i = 0; i < fields.breadth().size(); i++) {
            seen.put(fields.breadth().get(i), ActivityProfiles.seen(tables.get(3 + i)));
            if (tables.get(3 + i).cutShort()) {
                cutShort.add(fields.breadth().get(i));
            }
        }
        return new SourceData(SOURCE.name(), "by person id", fields,
                ActivityProfiles.days(tables.get(0), tables.get(1), fields, zone),
                ActivityProfiles.hourOfWeek(tables.get(1), zone), seen, cutShort,
                ActivityProfiles.population(tables.get(2)), peers.isEmpty()
                ? null
                : new PeerData("Organisation 'Finance'", ActivityProfiles.peersDaily(tables.getLast())))
                .withFieldSpans(ActivityProfiles.fieldSpans(PERSON_FIELD, fields, tables.get(2)));
    }

    Context context() {
        return new Context(zone, Duration.ofMinutes(1), 2, () -> false);
    }
}
