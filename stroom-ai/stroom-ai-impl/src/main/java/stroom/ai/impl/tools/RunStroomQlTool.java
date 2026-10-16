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

import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.EventsFile;
import stroom.ai.impl.workspace.MarkdownTables;
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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
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

    @Inject
    RunStroomQlTool(final ChatWorkspace workspace,
                    final Provider<QueryService> queryServiceProvider,
                    final Provider<ResourceStore> resourceStoreProvider) {
        super(workspace);
        this.queryServiceProvider = queryServiceProvider;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Runs a StroomQL query as the user (e.g. 'from \"My View\" where EventTime > "
                             + "day() - 1d select Host, count() as N group by Host') and writes the results to a "
                             + "table in the workspace. You get the table's path and a profile of it (row count, "
                             + "columns, common values); query it further with the table tool. Add a limit to the "
                             + "query for large results.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(QUERY_ARG, "The StroomQL query.")
                        .addStringProperty(OUT_ARG, "A .md file in /outputs/ for the results (default "
                                                    + "/outputs/queries/query-<time>.md).")
                        .required(QUERY_ARG)
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit() {
        return limits().getQueryTimeLimit().getDuration();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final String query = rawArg(request, QUERY_ARG);
        if (query == null || query.isBlank()) {
            throw new WorkspaceException("Give a StroomQL query.");
        }
        final int maxQueryLength = workspace.limits().getMaxQueryLength();
        if (query.length() > maxQueryLength) {
            throw new WorkspaceException("The query may be at most " + maxQueryLength + " characters.");
        }
        final WorkspacePath given = outArg(request);
        final WorkspacePath out = given != null
                ? given
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
                .timeout(timeLimit().toMillis())
                .storeHistory(false)
                .requestedRange(new OffsetRange(0L, 100L))
                .build();
        DashboardSearchResponse response = queryService.search(searchRequest);
        while (response != null && NullSafe.isEmptyCollection(response.getErrors()) && !response.isComplete()) {
            pause();
            response = queryService.search(searchRequest.copy().queryKey(response.getQueryKey()).build());
        }
        if (response == null) {
            throw new WorkspaceException("The query did not run.");
        }
        if (!NullSafe.isEmptyCollection(response.getErrors())) {
            throw new WorkspaceException("The query failed: " + String.join("; ", response.getErrors()));
        }

        final ResourceGeneration generation = queryService.downloadSearchResults(new DownloadQueryResultsRequest(
                searchRequest.copy().queryKey(response.getQueryKey()).build(),
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
            workspace.write(chatId, out, resourceStore.getTempFile(resourceKey), sourceDocUuid);
        } finally {
            resourceStore.deleteTempFile(resourceKey);
        }
        try (final Reader reader = Files.newBufferedReader(workspace.localFile(chatId, out),
                StandardCharsets.UTF_8)) {
            return "Ran the query. The results are in " + out + ".\n\n" + TableProfiler.profile(reader, out.path());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
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
