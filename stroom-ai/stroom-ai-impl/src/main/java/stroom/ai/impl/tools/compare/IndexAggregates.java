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

import stroom.ai.api.ElasticEsql;
import stroom.ai.api.ElasticEsql.Reason;
import stroom.ai.api.ElasticEsql.Refusal;
import stroom.ai.api.ElasticEsql.Request;
import stroom.ai.api.ElasticEsql.Result;
import stroom.ai.impl.tools.compare.AggregateQuery.Bucket;
import stroom.ai.impl.tools.compare.AggregateQuery.Kind;
import stroom.ai.impl.tools.compare.AggregateQuery.Metric;
import stroom.ai.impl.tools.query.AgentSearches;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.query.SearchProblems;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.query.api.Column;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.Row;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TableResult;
import stroom.query.impl.QueryService;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/// Runs `compare_activity`'s aggregates on the indexes, as the user: by ES|QL ([ElasticEsql], which checks Use on
/// the Elastic Index) on an Elastic Index, by StroomQL ([QueryService], which checks Use on the index) on any other.
/// A query bucketed by time is cut into months (in the person's zone), run in parallel up to the workers setting;
/// each search Stroom runs is removed once read.
class IndexAggregates implements AggregateRunner {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexAggregates.class);
    private static final long POLL_MS = 300;
    private static final DateTimeFormatter STROOMQL_HOUR = DateTimeFormatter.ofPattern(
            AggregateText.STROOMQL_HOUR_PATTERN);
    private static final Pattern EPOCH_MS = Pattern.compile("-?\\d{10,}");
    static final String ZONE_NOT_APPLIED = "The Elasticsearch cluster didn't apply the person's time zone, so days "
                                           + "were cut at a fixed offset: around a daylight saving change a day's "
                                           + "edges are out by an hour.";

    private final Provider<ElasticEsql> elasticEsqlProvider;
    private final Provider<QueryService> queryServiceProvider;
    private final AgentSearches agentSearches;
    private final Provider<SecurityContext> securityContextProvider;

    /// @param elasticEsqlProvider     Runs ES|QL.
    /// @param queryServiceProvider    Runs StroomQL.
    /// @param agentSearches           Removes the searches run.
    /// @param securityContextProvider Runs the slices as the user.
    @Inject
    IndexAggregates(final Provider<ElasticEsql> elasticEsqlProvider,
                    final Provider<QueryService> queryServiceProvider,
                    final AgentSearches agentSearches,
                    final Provider<SecurityContext> securityContextProvider) {
        this.elasticEsqlProvider = elasticEsqlProvider;
        this.queryServiceProvider = queryServiceProvider;
        this.agentSearches = agentSearches;
        this.securityContextProvider = securityContextProvider;
    }

    @Override
    public List<Table> run(final List<AggregateQuery> queries, final Context context) {
        final List<List<AggregateQuery>> sliced = queries.stream()
                .map(query -> query.bucket() == Bucket.NONE
                        ? List.of(query)
                        : months(query, context.zone()))
                .toList();
        final List<AggregateQuery> all = sliced.stream().flatMap(List::stream).toList();
        final List<Slice> results = parallel(all, context);
        final List<Table> tables = new ArrayList<>();
        int next = 0;
        for (final List<AggregateQuery> slices : sliced) {
            final List<Map<String, String>> rows = new ArrayList<>();
            final Set<String> notes = new LinkedHashSet<>();
            boolean cutShort = false;
            String text = null;
            for (int i = 0; i < slices.size(); i++) {
                final Slice slice = results.get(next++);
                rows.addAll(slice.rows());
                cutShort |= slice.cutShort();
                notes.addAll(slice.notes());
                if (text == null) {
                    text = slice.text();
                }
            }
            tables.add(new Table(rows, cutShort, slices.size(), new ArrayList<>(notes), text));
        }
        return tables;
    }

    /// Cuts a query's range into calendar months in a zone.
    ///
    /// @param query The query.
    /// @param zone  The zone.
    /// @return The query over each month (the first and last cut to the range), in order.
    static List<AggregateQuery> months(final AggregateQuery query, final ZoneId zone) {
        final List<AggregateQuery> slices = new ArrayList<>();
        Instant start = query.from();
        while (start.isBefore(query.to())) {
            final Instant nextMonth = ZonedDateTime.ofInstant(start, zone).toLocalDate()
                    .with(TemporalAdjusters.firstDayOfNextMonth())
                    .atStartOfDay(zone)
                    .toInstant();
            final Instant end = nextMonth.isBefore(query.to())
                    ? nextMonth
                    : query.to();
            slices.add(query.between(start, end));
            start = end;
        }
        return slices;
    }

    // Runs the slices on a pool as the user, waiting for each in turn.
    private List<Slice> parallel(final List<AggregateQuery> queries, final Context context) {
        if (queries.isEmpty()) {
            return List.of();
        }
        final SecurityContext securityContext = securityContextProvider.get();
        final UserRef user = securityContext.getUserRef();
        final int threads = Math.clamp(context.workers(), 1, queries.size());
        final AtomicInteger named = new AtomicInteger();
        final ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            final Thread thread = new Thread(runnable, "Stroom AI compare worker " + named.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        try {
            final List<Future<Slice>> futures = new ArrayList<>();
            for (final AggregateQuery query : queries) {
                futures.add(pool.submit(() -> user == null
                        ? runOne(query, context)
                        : securityContext.asUserResult(user, () -> runOne(query, context))));
            }
            final List<Slice> slices = new ArrayList<>();
            for (final Future<Slice> future : futures) {
                slices.add(await(future));
            }
            return slices;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Slice await(final Future<Slice> future) {
        try {
            return future.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskTerminatedException();
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof final RuntimeException runtime) {
                throw runtime;
            }
            throw new WorkspaceException("An aggregate failed: " + e.getCause().getMessage());
        }
    }

    private Slice runOne(final AggregateQuery query, final Context context) {
        if (context.stopped().getAsBoolean()) {
            throw new TaskTerminatedException();
        }
        try {
            return query.source().elastic()
                    ? esql(query, context)
                    : stroomQl(query, context);
        } catch (final PermissionException e) {
            throw new WorkspaceException(query.source().name() + ": " + DataSourceAccess.NOT_FOUND);
        }
    }

    // --------------------------------------------------------------------------------
    // ES|QL

    private Slice esql(final AggregateQuery query, final Context context) {
        final boolean utc = ZoneOffset.UTC.equals(context.zone().normalized());
        Result result = esqlRun(query, null, context, utc
                ? null
                : context.zone().getId());
        final List<String> notes = new ArrayList<>();
        if (!utc && query.bucket() == Bucket.DAY && result.timeZone() == null) {
            // The cluster refused the zone: the days again, at the zone's offset in the slice's middle.
            final Instant middle = query.from().plusMillis((query.to().toEpochMilli() - query.from().toEpochMilli())
                                                           / 2);
            final int offsetMinutes = context.zone().getRules().getOffset(middle).getTotalSeconds() / 60;
            result = esqlRun(query, offsetMinutes, context, null);
            notes.add(ZONE_NOT_APPLIED);
        }
        final List<String> columns = result.columns().stream().map(ElasticEsql.Column::name).toList();
        final List<Map<String, String>> rows = new ArrayList<>();
        for (final List<Object> row : result.rows()) {
            final Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                values.put(columns.get(i), cell(i < row.size()
                        ? row.get(i)
                        : null));
            }
            rows.add(normalise(values, query));
        }
        return new Slice(rows, result.truncated(), notes, result.query());
    }

    private Result esqlRun(final AggregateQuery query,
                           final Integer offsetMinutes,
                           final Context context,
                           final String zone) {
        final String pipeline = AggregateText.esql(query, offsetMinutes);
        try {
            return elasticEsqlProvider.get().query(new Request(query.source().doc(), pipeline, null, null,
                    query.limit(), context.timeLimit(), false, context.stopped(), zone));
        } catch (final Refusal refusal) {
            if (refusal.reason() == Reason.STOPPED) {
                throw new TaskTerminatedException();
            }
            throw new WorkspaceException(query.source().name() + ": the aggregate was refused ("
                                         + refusal.reason() + "): " + refusal.getMessage() + "\n\nThe query was:\n"
                                         + pipeline);
        }
    }

    // --------------------------------------------------------------------------------
    // StroomQL

    private Slice stroomQl(final AggregateQuery query, final Context context) {
        final String text = AggregateText.stroomQl(query, context.zone());
        final QueryService queryService = queryServiceProvider.get();
        final long now = System.currentTimeMillis();
        // Times come back in UTC, as the rows' times are read.
        final QuerySearchRequest request = QuerySearchRequest.builder()
                .searchRequestSource(SearchRequestSource.builder().sourceType(SourceType.API).build())
                .query(text)
                .queryContext(QueryContext.builder().dateTimeSettings(ToolTimeZone.UTC.dateTimeSettings(now)).build())
                .incremental(false)
                .timeout(context.timeLimit().toMillis())
                .storeHistory(false)
                .requestedRange(new OffsetRange(0L, (long) query.limit()))
                .build();
        QueryKey queryKey = null;
        try {
            DashboardSearchResponse response = queryService.search(request);
            queryKey = NullSafe.get(response, DashboardSearchResponse::getQueryKey);
            while (response != null && SearchProblems.errors(response).isEmpty() && !response.isComplete()) {
                pause(context);
                response = queryService.search(request.copy().queryKey(response.getQueryKey()).build());
            }
            if (response == null) {
                throw new WorkspaceException(query.source().name() + ": Stroom's query service gave no response.");
            }
            final List<String> errors = SearchProblems.errors(response);
            if (!errors.isEmpty()) {
                throw new WorkspaceException(query.source().name() + ": the aggregate failed: "
                                             + String.join("; ", errors) + "\n\nThe query was:\n" + text);
            }
            final TableResult table = NullSafe.list(response.getResults()).stream()
                    .filter(TableResult.class::isInstance)
                    .map(TableResult.class::cast)
                    .findFirst()
                    .orElse(null);
            final List<Map<String, String>> rows = new ArrayList<>();
            if (table != null) {
                final List<String> columns = NullSafe.list(table.getColumns()).stream().map(Column::getName).toList();
                for (final Row row : NullSafe.list(table.getRows())) {
                    final List<String> cells = NullSafe.list(row.getValues());
                    final Map<String, String> values = new LinkedHashMap<>();
                    for (int i = 0; i < columns.size(); i++) {
                        values.put(columns.get(i), i < cells.size()
                                ? NullSafe.string(cells.get(i))
                                : "");
                    }
                    rows.add(normalise(values, query));
                }
            }
            return new Slice(rows, rows.size() >= query.limit(), List.of(), text);
        } finally {
            agentSearches.destroy(queryKey);
        }
    }

    private static void pause(final Context context) {
        if (context.stopped().getAsBoolean()) {
            throw new TaskTerminatedException();
        }
        try {
            Thread.sleep(POLL_MS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskTerminatedException();
        }
    }

    // --------------------------------------------------------------------------------
    // Cells

    // The bucket and times as ISO instants in UTC (a day bucket stays a date).
    static Map<String, String> normalise(final Map<String, String> row, final AggregateQuery query) {
        if (query.bucket() == Bucket.HOUR) {
            row.computeIfPresent(AggregateQuery.BUCKET_COLUMN, (k, v) -> instant(v));
        }
        for (final Metric metric : query.metrics()) {
            if (metric.kind() == Kind.MIN_TIME || metric.kind() == Kind.MAX_TIME) {
                row.computeIfPresent(metric.name(), (k, v) -> instant(v));
            }
        }
        return row;
    }

    /// Reads a time as an aggregate gives it, in any of the forms the two query languages write.
    ///
    /// @param value An ISO time with an offset, StroomQL's UTC hour (`yyyy-MM-dd HH`), or milliseconds since the
    ///              epoch.
    /// @return The ISO instant in UTC, or the value as given if it can't be read.
    static String instant(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        final String trimmed = value.strip();
        try {
            if (EPOCH_MS.matcher(trimmed).matches()) {
                return AggregateText.iso(Instant.ofEpochMilli(Long.parseLong(trimmed)));
            }
            if (trimmed.length() == AggregateText.STROOMQL_HOUR_PATTERN.length()) {
                return AggregateText.iso(LocalDateTime.parse(trimmed, STROOMQL_HOUR).toInstant(ZoneOffset.UTC));
            }
            return AggregateText.iso(OffsetDateTime.parse(trimmed).toInstant());
        } catch (final DateTimeParseException | NumberFormatException e) {
            LOGGER.debug(() -> "Unable to read the time " + trimmed, e);
            return trimmed;
        }
    }

    private static String cell(final Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof final List<?> list) {
            return list.isEmpty()
                    ? ""
                    : String.valueOf(list.getFirst());
        }
        return String.valueOf(value);
    }


    // --------------------------------------------------------------------------------


    // One slice's rows.
    private record Slice(List<Map<String, String>> rows, boolean cutShort, List<String> notes, String text) {

    }
}
