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
import stroom.ai.impl.tools.compare.AggregateQuery.Metric;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Runs [AggregateQuery]s over events held in memory, as the indexes would, for tests: each event a map of field to
/// value, its time an [Instant] under the source's time field. Counts every query run.
final class InMemoryAggregates implements AggregateRunner {

    private final Map<String, List<Map<String, Object>>> events = new LinkedHashMap<>();
    private final List<AggregateQuery> ran = new ArrayList<>();

    /// @param uuid The index's uuid.
    /// @return The index's events, to add to.
    List<Map<String, Object>> source(final String uuid) {
        return events.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    /// @return Every query run, a slice each.
    List<AggregateQuery> ran() {
        return ran;
    }

    @Override
    public List<Table> run(final List<AggregateQuery> queries, final Context context) {
        final List<Table> tables = new ArrayList<>();
        for (final AggregateQuery query : queries) {
            final List<AggregateQuery> slices = query.bucket() == Bucket.NONE
                    ? List.of(query)
                    : IndexAggregates.months(query, context.zone());
            final List<Map<String, String>> rows = new ArrayList<>();
            boolean cutShort = false;
            for (final AggregateQuery slice : slices) {
                ran.add(slice);
                final List<Map<String, String>> sliceRows = rows(slice, context);
                if (sliceRows.size() > slice.limit()) {
                    cutShort = true;
                    rows.addAll(sliceRows.subList(0, slice.limit()));
                } else {
                    rows.addAll(sliceRows);
                }
            }
            tables.add(new Table(rows, cutShort, slices.size(), List.of(), "in memory"));
        }
        return tables;
    }

    private List<Map<String, String>> rows(final AggregateQuery query, final Context context) {
        final Map<List<String>, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (final Map<String, Object> event : events.getOrDefault(query.source().doc().getUuid(), List.of())) {
            final Instant time = (Instant) event.get(query.source().timeField());
            if (time == null || time.isBefore(query.from()) || !time.isBefore(query.to())) {
                continue;
            }
            final boolean matches = query.filters().entrySet().stream()
                    .allMatch(filter -> filter.getValue().contains(String.valueOf(event.get(filter.getKey()))));
            if (!matches) {
                continue;
            }
            final List<String> key = new ArrayList<>();
            key.add(switch (query.bucket()) {
                case NONE -> "";
                case HOUR -> AggregateText.iso(time.truncatedTo(ChronoUnit.HOURS));
                case DAY -> ZonedDateTime.ofInstant(time, context.zone()).toLocalDate().toString();
            });
            boolean hasGroups = true;
            for (final String group : query.groups()) {
                final Object value = event.get(group);
                if (value == null) {
                    hasGroups = false;
                }
                key.add(String.valueOf(value));
            }
            if (hasGroups) {
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
            }
        }
        final List<Map<String, String>> rows = new ArrayList<>();
        for (final Map.Entry<List<String>, List<Map<String, Object>>> group : groups.entrySet()) {
            final Map<String, String> row = new LinkedHashMap<>();
            if (query.bucket() != Bucket.NONE) {
                row.put(AggregateQuery.BUCKET_COLUMN, group.getKey().getFirst());
            }
            for (int i = 0; i < query.groups().size(); i++) {
                row.put(AggregateQuery.groupColumn(i), group.getKey().get(i + 1));
            }
            for (final Metric metric : query.metrics()) {
                row.put(metric.name(), metric(metric, group.getValue(), query.source().timeField()));
            }
            rows.add(row);
        }
        return rows;
    }

    private static String metric(final Metric metric, final List<Map<String, Object>> events, final String time) {
        return switch (metric.kind()) {
            case COUNT -> String.valueOf(events.size());
            case SUM -> String.valueOf(events.stream()
                    .map(event -> event.get(metric.field()))
                    .filter(Objects::nonNull)
                    .mapToDouble(value -> ((Number) value).doubleValue())
                    .sum());
            case DISTINCT -> {
                final Set<Object> values = new HashSet<>();
                events.stream().map(event -> event.get(metric.field())).filter(Objects::nonNull).forEach(values::add);
                yield String.valueOf(values.size());
            }
            case MIN_TIME -> AggregateText.iso(events.stream()
                    .map(event -> (Instant) event.get(time))
                    .min(Instant::compareTo)
                    .orElseThrow());
            case MAX_TIME -> AggregateText.iso(events.stream()
                    .map(event -> (Instant) event.get(time))
                    .max(Instant::compareTo)
                    .orElseThrow());
            case ONE_EVENT -> events.stream()
                    .map(event -> event.get("StreamId") + ":" + event.get("EventId"))
                    .min(String::compareTo)
                    .orElse("");
            case CENTROID -> {
                final double[] sum = new double[2];
                events.forEach(event -> {
                    final double[] point = (double[]) event.get(metric.field());
                    sum[0] += point[0];
                    sum[1] += point[1];
                });
                yield "POINT (" + sum[0] / events.size() + " " + sum[1] / events.size() + ")";
            }
        };
    }
}
