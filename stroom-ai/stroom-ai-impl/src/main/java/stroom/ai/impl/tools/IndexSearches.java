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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Format;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.Row;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// Searches an index as a dashboard does, through Stroom's dashboard search (as the user, so with their
/// permissions; nothing is saved), ported from the Stroom MCP server's `_search`, `_count` and `_column`: a query
/// on the index and a table of columns, polled until Stroom has finished or the wait is up, when what it has found
/// by then is the answer, said to be partial. Every search is stopped once read ([AgentSearches]): a search left
/// behind runs to its end even when nobody asks for its results (seen: three whole-index scans of a large
/// Elasticsearch index carried on after the survey gave up on them).
public class IndexSearches {

    /// How long a search is polled; past that, what it has found so far is the answer, said to be partial.
    public static final Duration SEARCH_WAIT = Duration.ofSeconds(60);
    /// The time kept back from the call's time limit, so a search that would run past it gives what it has.
    static final Duration MARGIN = Duration.ofSeconds(2);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final String QUERY_ID = "query-FIND";
    private static final String TABLE_ID = "table-FIND";

    private final Provider<DashboardService> dashboardServiceProvider;
    private final AgentSearches agentSearches;

    /// @param dashboardServiceProvider Runs the searches, as the user.
    /// @param agentSearches            Stops each once read.
    @Inject
    IndexSearches(final Provider<DashboardService> dashboardServiceProvider,
                  final AgentSearches agentSearches) {
        this.dashboardServiceProvider = dashboardServiceProvider;
        this.agentSearches = agentSearches;
    }

    /// A table column of an index field, as a dashboard shows it.
    ///
    /// @param name The field.
    /// @return Its column.
    public static Column column(final String name) {
        return Column.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .expression("${" + name + "}")
                .visible(true)
                .width(150)
                .format(Format.GENERAL)
                .build();
    }

    /// The table's rows, polling for up to [#SEARCH_WAIT] (less if the call's time limit is nearer); past that,
    /// the rows Stroom had found by then, with complete false.
    ///
    /// @param context The call, told what the search is doing as it runs.
    /// @param probe   The search.
    /// @param length  The most rows to read.
    /// @param doing   What the search is doing (e.g. 'Searching ecs-web-v1 for ...'), told to the client, and told
    ///                again at every poll Stroom hasn't finished; or null.
    /// @return The rows, by column name, with Stroom's errors.
    public Found search(final ToolCallContext context, final Probe probe, final int length, final String doing) {
        return search(context, probe, length, doing, SEARCH_WAIT);
    }

    /// As [#search(ToolCallContext, Probe, int, String)], polling for up to `longest` (less if the call's time
    /// limit is nearer), e.g. for a sample whose searches wait a few seconds in all.
    ///
    /// @param context The call, told what the search is doing as it runs.
    /// @param probe   The search.
    /// @param length  The most rows to read.
    /// @param doing   What the search is doing, told to the client; or null.
    /// @param longest The longest the search is polled.
    /// @return The rows, by column name, with Stroom's errors.
    public Found search(final ToolCallContext context,
                        final Probe probe,
                        final int length,
                        final String doing,
                        final Duration longest) {
        final CallProgress progress = CallProgress.of(context);
        if (doing != null) {
            progress.say(doing);
        }
        final Duration wait = waitFor(longest);
        final long started = System.nanoTime();
        DashboardSearchRequest request = request(probe, length);
        DashboardSearchResponse response = null;
        QueryKey queryKey = null;
        try {
            while (true) {
                // Stroom holds each poll until the search completes or this many ms pass: never past the wait.
                final long leftMs = wait.toMillis() - elapsed(started).toMillis();
                request = request.copy().timeout(Math.max(500L, Math.min(5000L, leftMs))).build();
                response = dashboardServiceProvider.get().search(request);
                if (response == null) {
                    break;
                }
                if (response.getQueryKey() != null) {
                    queryKey = response.getQueryKey();
                }
                if (response.isComplete() || elapsed(started).compareTo(wait) >= 0) {
                    break;
                }
                if (doing != null) {
                    progress.say(doing + ": still searching, " + elapsed(started).toSeconds() + " s of "
                                 + wait.toSeconds());
                }
                request = request.copy().queryKey(queryKey).build();
                pause();
            }
        } finally {
            agentSearches.destroy(queryKey);
        }
        return found(probe, response);
    }

    /// How many documents the expression finds, however many: the table asked for count() grouped on a constant,
    /// so one row holds it. Counting the rows of a search stopped at its first page (100) and at the table's
    /// maxResults (seen: 32,000 documents counted as 100).
    ///
    /// @param context The call.
    /// @param probe   The search; its columns are replaced by the count.
    /// @param doing   What the count is doing, told to the client; or null.
    /// @return The count (null if Stroom gave none), with Stroom's errors, and whether Stroom finished counting: a
    /// count cut off at the wait is a floor, not the number.
    public Counted count(final ToolCallContext context, final Probe probe, final String doing) {
        final Probe counting = probe.withColumns(List.of(
                Column.builder().id("mcp-all").name("All").expression("\"all\"").group(0).visible(true).build(),
                Column.builder().id("mcp-count").name("Count").expression("count()").format(Format.NUMBER)
                        .visible(true).build()), probe.maxResults());
        final Found found = search(context, counting, 1, doing);
        if (found.rows().isEmpty()) {
            return new Counted(found.errors().isEmpty()
                    ? 0L
                    : null, found.errors(), found.complete());
        }
        try {
            return new Counted((long) Double.parseDouble(NullSafe.nonBlankStringElse(found.rows().getFirst()
                    .get("Count"), "0")), found.errors(), found.complete());
        } catch (final NumberFormatException e) {
            return new Counted(null, found.errors(), found.complete());
        }
    }

    // How long a search may be polled: SEARCH_WAIT, or what the call has left (less a margin), what it has found by
    // then rather than the call stopped part way.
    private static Duration waitFor(final Duration longest) {
        final Duration wait = ToolDeadline.remaining()
                .map(left -> left.minus(MARGIN))
                .map(left -> left.compareTo(longest) < 0
                        ? left
                        : longest)
                .orElse(longest);
        return wait.compareTo(Duration.ofSeconds(1)) < 0
                ? Duration.ofSeconds(1)
                : wait;
    }

    /// @param probe  The search.
    /// @param length The most rows to read.
    /// @return The dashboard search the probe makes, keeping no history.
    static DashboardSearchRequest request(final Probe probe, final int length) {
        final TableSettings settings = TableSettings.builder()
                .queryId(QUERY_ID)
                .columns(probe.columns())
                .extractValues(false)
                .maxResults(List.of((long) probe.maxResults()))
                .build();
        return DashboardSearchRequest.builder()
                .searchRequestSource(SearchRequestSource.builder()
                        .sourceType(SourceType.DASHBOARD_UI)
                        .componentId(QUERY_ID)
                        .ownerDocRef(new DocRef("Dashboard", UUID.randomUUID().toString(), probe.name()))
                        .build())
                .search(Search.builder()
                        .dataSourceRef(probe.dataSource())
                        .expression(probe.expression())
                        .incremental(true)
                        .build())
                .componentResultRequests(List.of(TableResultRequest.builder()
                        .componentId(TABLE_ID)
                        .fetch(Fetch.ALL)
                        .tableName("Table")
                        .tableSettings(settings)
                        .requestedRange(new OffsetRange(0L, (long) length))
                        .build()))
                .dateTimeSettings(DateTimeSettings.builder()
                        .localZoneId("UTC")
                        .referenceTime(System.currentTimeMillis())
                        .build())
                .storeHistory(false)
                .build();
    }

    // The table's rows, by column name, and the errors, from the last response.
    private static Found found(final Probe probe, final DashboardSearchResponse response) {
        if (response == null) {
            return new Found(List.of(), List.of("Stroom's dashboard search gave no response"), false);
        }
        final List<String> errors = new ArrayList<>(NullSafe.list(response.getErrors()));
        errors.addAll(messages(response.getErrorMessages()));
        final List<String> columns = probe.columns().stream().map(Column::getName).toList();
        final List<Map<String, String>> rows = new ArrayList<>();
        for (final Result result : NullSafe.list(response.getResults())) {
            if (result instanceof final TableResult table && TABLE_ID.equals(result.getComponentId())) {
                errors.addAll(NullSafe.list(table.getErrors()));
                errors.addAll(messages(table.getErrorMessages()));
                for (final Row row : NullSafe.list(table.getRows())) {
                    rows.add(byColumn(columns, NullSafe.list(row.getValues())));
                }
            }
        }
        return new Found(rows, errors, response.isComplete());
    }

    private static List<String> messages(final List<ErrorMessage> errorMessages) {
        return NullSafe.list(errorMessages).stream()
                .map(ErrorMessage::getMessage)
                .filter(NullSafe::isNonBlankString)
                .toList();
    }

    private static Map<String, String> byColumn(final List<String> columns, final List<String> values) {
        final Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(columns.size(), values.size()); i++) {
            row.put(columns.get(i), values.get(i));
        }
        return row;
    }

    private static Duration elapsed(final long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }

    private static void pause() {
        ToolDeadline.check();
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolTimeoutException();
        }
    }


    // --------------------------------------------------------------------------------


    /// A search, as an unsaved dashboard makes it: a query on an index, and a table.
    ///
    /// @param dataSource The index.
    /// @param name       What the dashboard would be called, e.g. "web search".
    /// @param expression The query's expression.
    /// @param columns    The table's columns.
    /// @param maxResults The most rows the table keeps.
    public record Probe(DocRef dataSource,
                        String name,
                        ExpressionOperator expression,
                        List<Column> columns,
                        int maxResults) {

        /// @param newColumns    Other columns.
        /// @param newMaxResults The most rows the table keeps.
        /// @return The same search, with another table.
        public Probe withColumns(final List<Column> newColumns, final int newMaxResults) {
            return new Probe(dataSource, name, expression, newColumns, newMaxResults);
        }
    }

    /// What a search found.
    ///
    /// @param rows     Its rows, each by column name.
    /// @param errors   Stroom's errors.
    /// @param complete False if Stroom hadn't finished by the wait.
    public record Found(List<Map<String, String>> rows, List<String> errors, boolean complete) {

    }

    /// A count.
    ///
    /// @param count    How many documents, or null if Stroom gave no count.
    /// @param errors   Stroom's errors.
    /// @param complete False if Stroom hadn't finished counting by the wait.
    public record Counted(Long count, List<String> errors, boolean complete) {

    }
}
