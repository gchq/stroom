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

import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.EventsFile;
import stroom.ai.impl.workspace.MarkdownTables;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.TableProfiler;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code run_stroomql} - runs a StroomQL query as the chat's user (so only data they can see), and writes the
 * results to the workspace as a table, giving the model a profile of it rather than the rows. It only reads, and
 * is limited in time and in the size of what it writes.
 */
class RunStroomQlTool extends AbstractWorkspaceTool {

    static final String NAME = "run_stroomql";
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RunStroomQlTool.class);

    static final String QUERY_ARG = "query";
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Provider<QueryService> queryServiceProvider;
    private final Provider<ResourceStore> resourceStoreProvider;
    private final AgentSearches agentSearches;
    private LuceneTextFields luceneTextFields;

    @Inject
    RunStroomQlTool(final ChatWorkspace workspace,
                    final Provider<QueryService> queryServiceProvider,
                    final Provider<ResourceStore> resourceStoreProvider,
                    final AgentSearches agentSearches) {
        super(workspace);
        this.queryServiceProvider = queryServiceProvider;
        this.agentSearches = agentSearches;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    /// @param luceneTextFields A Lucene index's text fields' analyzers, for the misleading-search check (found
    ///                         reviewing the tools: without them, a CONTAINS on a keyword field ran, and found
    ///                         nothing).
    @Inject
    void setLuceneTextFields(final LuceneTextFields luceneTextFields) {
        this.luceneTextFields = luceneTextFields;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .note("Use on the query's data source is checked by Stroom's query service when the search runs, "
                      + "as for any search.")
                .build();
    }

    @Override
    public boolean usesRunSlot() {
        // It runs a search.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.SEARCH;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Runs a StroomQL query as the user (e.g. 'from \"My View\" where EventTime > "
                                 + "day() - 1d select Host, count() as N group by Host') and writes the results to a "
                                 + "table")
                        .chatOnly(" in the workspace")
                        .general(". You get the table's path and a profile of it (row count, columns, common "
                                 + "values)")
                        .chatOnly("; query it further with the table tool")
                        .general(". Use eval (e.g. 'eval Rate = Bytes / Duration') to work values out in the query, "
                                 + "in Stroom's expression language")
                        .chatOnly(", as the table tool's expressions are")
                        .general(". Add a limit to the query for large results. If more than one data source could "
                                 + "hold what the user asked for, ask them which to use")
                        .chatOnly(" with ask_user")
                        .general(" first; never choose one for them.")
                        .chatOnly(" If the user may instead want a Query document to run themselves "
                                  + "(propose_query), ask them which.")
                        .general(" A query on a Lucene or Elastic index whose answer through Stroom would mislead "
                                 + "(e.g. is null on Elasticsearch, which matches every document) is refused, "
                                 + "saying what to query instead.")
                        .chatOnly(" To find an event the user describes in an index, with each hit's StreamId and "
                                  + "EventId and the streams holding every match, use search_events with index.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(QUERY_ARG, "The StroomQL query.")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A .md file in /outputs/ for the results (default "
                                          + "/outputs/queries/query-<time>.md).")
                                .build())
                        .required(QUERY_ARG)
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit(final ToolCallContext context) {
        return limits(context).getQueryTimeLimit().getDuration();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final String given = rawArg(request, QUERY_ARG);
        if (given == null || given.isBlank()) {
            throw new WorkspaceException("Give a StroomQL query.");
        }
        final int maxQueryLength = limits(context).getMaxQueryLength();
        if (given.length() > maxQueryLength) {
            throw new WorkspaceException("The query may be at most " + maxQueryLength + " characters.");
        }
        // Never an unbounded search: the rows are capped, whatever the query asks for.
        final int maxRows = limits(context).getMaxQueryRows();
        final StroomQlLimit.Limited limited = StroomQlLimit.apply(given, maxRows);
        final String query = limited.query();
        final WorkspacePath givenOut = outArg(request);
        final WorkspacePath out = givenOut != null
                ? givenOut
                : WorkspacePath.parse("/outputs/queries/query-"
                                      + FILE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)) + ".md");
        final QueryService queryService = queryServiceProvider.get();
        final DocRef dataSource = queryService.getReferencedDataSource(query).orElse(null);
        final String sourceDocUuid = NullSafe.get(dataSource, DocRef::getUuid);
        final Optional<String> misleading = misleading(queryService, query, dataSource, luceneTextFields == null
                ? Map.of()
                : luceneTextFields.of(dataSource));
        if (misleading.isPresent()) {
            throw new WorkspaceException(misleading.get());
        }

        final QuerySearchRequest searchRequest = QuerySearchRequest.builder()
                .searchRequestSource(SearchRequestSource.builder().sourceType(SourceType.API).build())
                .query(query)
                .queryContext(QueryContext.builder().dateTimeSettings(DateTimeSettings.builder().build()).build())
                .incremental(false)
                .timeout(timeLimit(context).toMillis())
                .storeHistory(false)
                .requestedRange(new OffsetRange(0L, 100L))
                .build();
        QueryKey queryKey = null;
        try {
            DashboardSearchResponse response = queryService.search(searchRequest);
            queryKey = NullSafe.get(response, DashboardSearchResponse::getQueryKey);
            // Whether the search started, so a failure says if it failed part way rather than before it began.
            boolean started = false;
            while (response != null && NullSafe.isEmptyCollection(response.getErrors()) && !response.isComplete()) {
                started = true;
                pause();
                response = queryService.search(searchRequest.copy().queryKey(response.getQueryKey()).build());
            }
            return ToolResult.text(results(workspace, searchRequest, response, started, out, sourceDocUuid, limited,
                    maxRows));
        } finally {
            // The search is the agent's own, and is never left running, e.g. when the time limit is reached.
            agentSearches.destroy(queryKey);
        }
    }

    /// The refusal for a query on a Lucene or Elastic index whose answer through Stroom would mislead, as the Stroom
    /// MCP server's find_events refused such a search (e.g. IS_NULL on Elasticsearch matches every document, and a
    /// wildcard on an ip field finds nothing): it says what to query instead.
    ///
    /// @param queryService Parses the query and lists the index's fields.
    /// @param query        The query.
    /// @param dataSource   Its data source, or null.
    /// @return The refusal, or empty if the query can be trusted, isn't on an index, or can't be checked.
    static Optional<String> misleading(final QueryService queryService,
                                       final String query,
                                       final DocRef dataSource) {
        return misleading(queryService, query, dataSource, Map.of());
    }

    /// As [#misleading(QueryService, String, DocRef)], knowing a Lucene index's text fields' analyzers, so that a
    /// CONTAINS on a keyword field, or a range on a text field, is refused too.
    ///
    /// @param queryService Parses the query and lists the index's fields.
    /// @param query        The query.
    /// @param dataSource   Its data source, or null.
    /// @param luceneText   A Lucene index's text fields and their analyzers ([LuceneTextFields]), or none.
    /// @return The refusal, or empty if the query can be trusted, isn't on an index, or can't be checked.
    static Optional<String> misleading(final QueryService queryService,
                                       final String query,
                                       final DocRef dataSource,
                                       final Map<String, String> luceneText) {
        final Optional<String> backend = MisleadingSearches.backendOf(dataSource);
        if (backend.isEmpty()) {
            return Optional.empty();
        }
        final List<MisleadingSearches.Search> searches;
        final List<QueryField> fields;
        try {
            searches = MisleadingSearches.searches(queryService.parseQuery(query));
            if (searches.isEmpty()) {
                return Optional.empty();
            }
            fields = NullSafe.list(NullSafe.get(queryService.findFields(new FindFieldCriteria(
                    new PageRequest(0, IndexEvents.MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, dataSource,
                    null, null)), ResultPage::getValues));
        } catch (final RuntimeException e) {
            // A check on the query, not a reason for it not to run: its own errors are Stroom's to give.
            LOGGER.debug(() -> LogUtil.message("Unable to check the query's searches: {}", e.getMessage()), e);
            return Optional.empty();
        }
        return MisleadingSearches.refusal(backend.get(), searches, MisleadingSearches.ipFields(fields), List.of(),
                        luceneText, MisleadingSearches.unindexed(dataSource, fields,
                                searches.stream().map(MisleadingSearches.Search::field).toList()))
                .map(refusal -> refusal + ". In StroomQL, EQUALS is = and NOT_EQUALS is !=.");
    }

    // Writes the results of a search that has finished to the workspace, and says what they hold.
    private String results(final SessionWorkspace workspace,
                           final QuerySearchRequest searchRequest,
                           final DashboardSearchResponse response,
                           final boolean started,
                           final WorkspacePath out,
                           final String sourceDocUuid,
                           final StroomQlLimit.Limited limited,
                           final int maxRows) {
        final QueryService queryService = queryServiceProvider.get();
        if (response == null) {
            throw new WorkspaceException(started
                    ? "The query started, but Stroom's query service then gave no response."
                    : "The query did not run: Stroom's query service gave no response.");
        }
        if (!NullSafe.isEmptyCollection(response.getErrors())) {
            throw new WorkspaceException((started
                    ? "The query started, but failed while it ran: "
                    : "The query failed: ") + String.join("; ", response.getErrors()));
        }

        // The download takes the rows the request asks for, so it asks for all those the limit allows, not the few
        // the search was polled for.
        final ResourceGeneration generation = queryService.downloadSearchResults(new DownloadQueryResultsRequest(
                searchRequest.copy()
                        .queryKey(response.getQueryKey())
                        .requestedRange(new OffsetRange(0L, (long) maxRows))
                        .build(),
                // With each row's stream and event ids, so the events behind the rows can be fetched.
                DownloadSearchResultFileType.MARKDOWN, false, 100, true));
        final ResourceKey resourceKey = generation.getResourceKey();
        if (resourceKey == null) {
            throw new WorkspaceException("The query's results could not be written: " + generation.getMessageList());
        }
        final ResourceStore resourceStore = resourceStoreProvider.get();
        try {
            // A data source without streams gives empty ids, which only mislead.
            MarkdownTables.dropEmptyColumns(resourceStore.getTempFile(resourceKey), EventsFile.SOURCE_ID_COLUMNS);
            workspace.write(out, resourceStore.getTempFile(resourceKey), sourceDocUuid);
        } finally {
            resourceStore.deleteTempFile(resourceKey);
        }
        try (final Reader reader = Files.newBufferedReader(workspace.localFile(out),
                StandardCharsets.UTF_8)) {
            final String profile = TableProfiler.profile(reader, out.path());
            final long rows = MarkdownTables.countRows(workspace.localFile(out));
            return "Ran the query. The results are in " + out + "." + limitNote(limited, maxRows, rows) + "\n\n"
                   + profile;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// @return What the model is told about the row limit: nothing if the query's own limit was used and the rows
    /// didn't reach the most allowed.
    static String limitNote(final StroomQlLimit.Limited limited, final int maxRows, final long rows) {
        final StringBuilder sb = new StringBuilder();
        if (limited.change() == StroomQlLimit.Change.ADDED) {
            sb.append(" The query had no limit, so 'limit ").append(maxRows).append("' (the most rows a query may "
                                                                                   + "return here) was added.");
        } else if (limited.change() == StroomQlLimit.Change.LOWERED) {
            sb.append(" The query's limit was over ").append(maxRows).append(", the most rows a query may return "
                                                                               + "here, so it was lowered to that.");
        }
        if (rows >= maxRows) {
            sb.append(" It returned ").append(maxRows).append(" rows, the most allowed, so there may be more: don't "
                                                              + "take the row count for a total. To count rows, "
                                                              + "use count() with group by (without group by, "
                                                              + "each row is its own group).");
        }
        return sb.toString();
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

    @Override
    public boolean readsUntrustedContent() {
        // Stroom data can hold text written by anyone.
        return true;
    }

}
