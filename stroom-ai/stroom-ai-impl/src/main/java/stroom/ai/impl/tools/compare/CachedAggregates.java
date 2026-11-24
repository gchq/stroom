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
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/// Keeps the time-bucketed aggregates `compare_activity` ran in the workspace, so a re-run (a longer period, the
/// same question tomorrow) asks only for the days since: a query asked before is answered from the file for the days
/// it holds, the last of them asked again (it may have been part of a day), and only the rest run. Aggregates not
/// bucketed by time (each value's first and last times, the people active) are always run, being one query each.
final class CachedAggregates implements AggregateRunner {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CachedAggregates.class);

    private final AggregateRunner runner;
    private final SessionWorkspace workspace;
    private final WorkspacePath path;

    /// @param runner    Runs what isn't held.
    /// @param workspace The chat's workspace.
    /// @param path      The file the aggregates are held in.
    CachedAggregates(final AggregateRunner runner, final SessionWorkspace workspace, final WorkspacePath path) {
        this.runner = runner;
        this.workspace = workspace;
        this.path = path;
    }

    @Override
    public List<Table> run(final List<AggregateQuery> queries, final Context context) {
        final Map<String, Entry> cache = read();
        final List<AggregateQuery> toRun = new ArrayList<>();
        final List<Plan> plans = new ArrayList<>();
        for (final AggregateQuery query : queries) {
            final Entry held = query.bucket() == Bucket.NONE
                    ? null
                    : cache.get(key(query));
            if (held == null || held.from().isAfter(query.from()) || !held.to().isAfter(query.from())) {
                plans.add(new Plan(query, null, null, toRun.size()));
                toRun.add(query);
                continue;
            }
            // Held from before the query's start: the days held are used, from the start of the last on run again.
            final Instant rerunFrom = dayStart(held.to().minusMillis(1), context.zone());
            if (!query.to().isAfter(rerunFrom)) {
                plans.add(new Plan(query, held, query.to(), -1));
            } else {
                plans.add(new Plan(query, held, rerunFrom, toRun.size()));
                toRun.add(query.between(rerunFrom, query.to()));
            }
        }
        final List<Table> ran = toRun.isEmpty()
                ? List.of()
                : runner.run(toRun, context);
        final List<Table> tables = new ArrayList<>();
        boolean changed = false;
        for (final Plan plan : plans) {
            final Table fresh = plan.ranAt() < 0
                    ? null
                    : ran.get(plan.ranAt());
            if (plan.held() == null) {
                tables.add(fresh);
                if (plan.query().bucket() != Bucket.NONE && fresh != null && !fresh.cutShort()) {
                    cache.put(key(plan.query()), new Entry(plan.query().from(), plan.query().to(), fresh.rows()));
                    changed = true;
                }
                continue;
            }
            final List<Map<String, String>> rows = new ArrayList<>();
            for (final Map<String, String> row : plan.held().rows()) {
                final Instant start = bucketStart(row.get(AggregateQuery.BUCKET_COLUMN), plan.query().bucket(),
                        context.zone());
                if (start != null && !start.isBefore(plan.query().from()) && start.isBefore(plan.heldUntil())) {
                    rows.add(row);
                }
            }
            int queriesRun = 0;
            final List<String> notes = new ArrayList<>();
            boolean cutShort = false;
            String text = null;
            if (fresh != null) {
                rows.addAll(fresh.rows());
                queriesRun = fresh.queries();
                notes.addAll(fresh.notes());
                cutShort = fresh.cutShort();
                text = fresh.text();
            }
            tables.add(new Table(rows, cutShort, queriesRun, notes, text));
            if (fresh != null && !cutShort) {
                final List<Map<String, String>> kept = new ArrayList<>();
                for (final Map<String, String> row : plan.held().rows()) {
                    final Instant start = bucketStart(row.get(AggregateQuery.BUCKET_COLUMN), plan.query().bucket(),
                            context.zone());
                    if (start != null && start.isBefore(plan.heldUntil())) {
                        kept.add(row);
                    }
                }
                kept.addAll(fresh.rows());
                cache.put(key(plan.query()), new Entry(plan.held().from(), plan.query().to(), kept));
                changed = true;
            }
        }
        if (changed) {
            write(cache);
        }
        return tables;
    }

    /// @param query A query.
    /// @return What it asks, its time range aside.
    static String key(final AggregateQuery query) {
        return query.source().doc().getUuid() + "|" + query.source().timeField() + "|" + query.filters() + "|"
               + query.bucket() + "|" + query.groups() + "|" + query.metrics() + "|" + query.limit()
               + (query.excludes().isEmpty() && query.differing().isEmpty()
                ? ""
                : "|" + query.excludes() + "|" + query.differing());
    }

    private static Instant dayStart(final Instant instant, final ZoneId zone) {
        return ZonedDateTime.ofInstant(instant, zone).toLocalDate().atStartOfDay(zone).toInstant();
    }

    private static Instant bucketStart(final String bucket, final Bucket kind, final ZoneId zone) {
        if (bucket == null || bucket.isBlank()) {
            return null;
        }
        try {
            return kind == Bucket.DAY
                    ? LocalDate.parse(bucket).atStartOfDay(zone).toInstant()
                    : Instant.parse(bucket);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private Map<String, Entry> read() {
        if (workspace.find(path).isEmpty()) {
            return new TreeMap<>();
        }
        try {
            final Map<String, Entry> held = JsonUtil.getMapper().readValue(Files.readString(workspace.localFile(path),
                    StandardCharsets.UTF_8), new TypeReference<Map<String, Entry>>() {
                    });
            return held == null
                    ? new TreeMap<>()
                    : new TreeMap<>(held);
        } catch (final IOException | RuntimeException e) {
            LOGGER.debug(() -> "Unable to read " + path, e);
            return new TreeMap<>();
        }
    }

    private void write(final Map<String, Entry> cache) {
        workspace.write(path, JsonUtil.writeValueAsString(cache, false), null);
    }


    // --------------------------------------------------------------------------------


    /// An aggregate held.
    ///
    /// @param fromMs Its range's start, in milliseconds since the epoch.
    /// @param toMs   Its range's end, likewise.
    /// @param rows   Its rows.
    record Entry(@JsonProperty("fromMs") long fromMs,
                 @JsonProperty("toMs") long toMs,
                 @JsonProperty("rows") List<Map<String, String>> rows) {

        /// Copies the rows.
        @JsonCreator
        Entry {
            rows = rows == null
                    ? List.of()
                    : rows.stream().map(row -> (Map<String, String>) new LinkedHashMap<>(row)).toList();
        }

        /// @param from Its range's start.
        /// @param to   Its range's end.
        /// @param rows Its rows.
        Entry(final Instant from, final Instant to, final List<Map<String, String>> rows) {
            this(from.toEpochMilli(), to.toEpochMilli(), rows);
        }

        /// @return Its range's start.
        Instant from() {
            return Instant.ofEpochMilli(fromMs);
        }

        /// @return Its range's end.
        Instant to() {
            return Instant.ofEpochMilli(toMs);
        }
    }


    // --------------------------------------------------------------------------------


    // How one query is answered: from the file up to heldUntil, and by the query run at ranAt (-1 for none).
    private record Plan(AggregateQuery query, Entry held, Instant heldUntil, int ranAt) {

    }
}
