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
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.OffsetRange;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.impl.QueryService;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

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
import java.util.Set;

/**
 * {@code run_stroomql} - runs a StroomQL query as the chat's user (so only data they can see), and writes the
 * results to the workspace as a table, giving the model a profile of it rather than the rows. It only reads, and
 * is limited in time and in the size of what it writes.
 */
class RunStroomQlTool extends AbstractWorkspaceTool {

    static final String NAME = "run_stroomql";
    static final String QUERY_ARG = "query";
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Provider<QueryService> queryServiceProvider;
    private final Provider<ResourceStore> resourceStoreProvider;
    private final AgentSearches agentSearches;

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

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .note("Use on the query's data source is checked by Stroom's query service when the search runs, "
                      + "as for any search.")
                .build();
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
    ToolResult run(final ToolCall request, final ToolCallContext context) {
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
        final String sourceDocUuid = queryService.getReferencedDataSource(query)
                .map(DocRef::getUuid)
                .orElse(null);

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
            while (response != null && NullSafe.isEmptyCollection(response.getErrors()) && !response.isComplete()) {
                pause();
                response = queryService.search(searchRequest.copy().queryKey(response.getQueryKey()).build());
            }
            return ToolResult.text(results(workspace, searchRequest, response, out, sourceDocUuid, limited, maxRows));
        } finally {
            // The search is the agent's own, and is never left running, e.g. when the time limit is reached.
            agentSearches.destroy(queryKey);
        }
    }

    // Writes the results of a search that has finished to the workspace, and says what they hold.
    private String results(final SessionWorkspace workspace,
                           final QuerySearchRequest searchRequest,
                           final DashboardSearchResponse response,
                           final WorkspacePath out,
                           final String sourceDocUuid,
                           final StroomQlLimit.Limited limited,
                           final int maxRows) {
        final QueryService queryService = queryServiceProvider.get();
        if (response == null) {
            throw new WorkspaceException("The query did not run.");
        }
        if (!NullSafe.isEmptyCollection(response.getErrors())) {
            throw new WorkspaceException("The query failed: " + String.join("; ", response.getErrors()));
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
