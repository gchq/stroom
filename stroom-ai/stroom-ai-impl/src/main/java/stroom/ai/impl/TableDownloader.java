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

package stroom.ai.impl;

import stroom.ai.impl.workspace.EventsFile;
import stroom.ai.impl.workspace.MarkdownTables;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.dashboard.impl.DashboardService;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.TableResultRequest;
import stroom.query.api.OffsetRange;
import stroom.query.impl.QueryService;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/// Downloads the rows of a dashboard table or a query's results as a Markdown table, through Stroom's own
/// download of search results (as the user, so with their permissions), with each row's stream and event ids. One
/// row more than asked for is fetched, to tell whether there were more, and then left out.
///
/// Used both for a table the user attaches to a chat and for a snapshot of the results shown in their attached tab.
public class TableDownloader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableDownloader.class);

    private final Provider<DashboardService> dashboardServiceProvider;
    private final Provider<QueryService> queryServiceProvider;
    private final Provider<ResourceStore> resourceStoreProvider;

    /// @param dashboardServiceProvider Downloads a dashboard table's rows.
    /// @param queryServiceProvider     Downloads a query's results.
    /// @param resourceStoreProvider    Holds the downloaded file until it is moved.
    @Inject
    public TableDownloader(final Provider<DashboardService> dashboardServiceProvider,
                           final Provider<QueryService> queryServiceProvider,
                           final Provider<ResourceStore> resourceStoreProvider) {
        this.dashboardServiceProvider = dashboardServiceProvider;
        this.queryServiceProvider = queryServiceProvider;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    /// Downloads a table's rows to a file.
    ///
    /// @param context The table: a dashboard table or a query's results, with the query key of the results to
    ///                download.
    /// @param maxRows The most rows to keep.
    /// @param target  The file to write the table to, replacing any there.
    /// @return How many rows and columns it has, and whether there were more rows than kept.
    public Downloaded download(final AskStroomAiContext context, final long maxRows, final Path target) {
        final ResourceGeneration generation = generate(context, maxRows);
        final ResourceKey resourceKey = generation.getResourceKey();
        if (resourceKey == null) {
            throw new IllegalStateException("The results could not be downloaded: "
                                            + generation.getMessageList());
        }
        final ResourceStore resourceStore = resourceStoreProvider.get();
        try {
            Files.move(resourceStore.getTempFile(resourceKey), target, StandardCopyOption.REPLACE_EXISTING);
            // A data source without streams (e.g. reference data) gives empty ids, which only mislead.
            MarkdownTables.dropEmptyColumns(target, EventsFile.SOURCE_ID_COLUMNS);
            return trim(target, maxRows);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                resourceStore.deleteTempFile(resourceKey);
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Failed to delete temp file", e);
            }
        }
    }

    /// Leaves out the row fetched beyond the most asked for, if there was one.
    ///
    /// @param file    A Markdown table: a header, a separator, then a row per line.
    /// @param maxRows The most rows to keep.
    /// @return How many rows and columns it has, and whether there were more rows than kept.
    static Downloaded trim(final Path file, final long maxRows) throws IOException {
        long rowCount = 0;
        final String header;
        try (final BufferedReader reader = Files.newBufferedReader(file)) {
            header = reader.readLine();
            reader.readLine();
            while (reader.readLine() != null) {
                rowCount++;
            }
        }
        final boolean truncated = rowCount > maxRows;
        if (truncated) {
            final Path trimmed = file.resolveSibling(file.getFileName() + ".tmp");
            try (final BufferedReader reader = Files.newBufferedReader(file);
                    final BufferedWriter writer = Files.newBufferedWriter(trimmed)) {
                final long maxLines = maxRows + 2;
                long linesWritten = 0;
                String line;
                while (linesWritten < maxLines && (line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    linesWritten++;
                }
            }
            Files.move(trimmed, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return new Downloaded(truncated
                ? maxRows
                : rowCount, header == null
                ? 0
                : columnCount(header), truncated);
    }

    /// @param headerLine A Markdown table's header line, e.g. `| A | B |`.
    /// @return How many columns it has.
    static int columnCount(final String headerLine) {
        final String trimmed = headerLine.strip();
        if (trimmed.isEmpty()) {
            return 0;
        }
        int count = 0;
        boolean escaped = false;
        for (int i = 0; i < trimmed.length(); i++) {
            final char chr = trimmed.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (chr == '\\') {
                escaped = true;
            } else if (chr == '|') {
                count++;
            }
        }
        // A row starts and ends with a pipe, so has one more pipe than columns.
        return Math.max(0, count - 1);
    }

    // Asks Stroom for the table's rows, one more than wanted to tell whether there were more.
    private ResourceGeneration generate(final AskStroomAiContext context, final long maxRows) {
        final OffsetRange probeRange = new OffsetRange(0L, maxRows + 1);
        if (context instanceof final DashboardTableContext dashboard) {
            final DashboardSearchRequest searchRequest = dashboard.getSearchRequest();
            final List<ComponentResultRequest> limitedRequests = searchRequest.getComponentResultRequests().stream()
                    .map(request -> request instanceof final TableResultRequest table
                            ? table.copy().requestedRange(probeRange).build()
                            : request)
                    .toList();
            final String componentId = limitedRequests.stream()
                    .filter(TableResultRequest.class::isInstance)
                    .map(ComponentResultRequest::getComponentId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No table component found"));
            // With each row's stream and event ids, so the agent can look at the events behind the rows.
            return dashboardServiceProvider.get().downloadSearchResults(new DownloadSearchResultsRequest(
                    searchRequest.copy().componentResultRequests(limitedRequests).build(), componentId,
                    DownloadSearchResultFileType.MARKDOWN, false, false, 100, true));
        } else if (context instanceof final QueryTableContext query) {
            final QuerySearchRequest limited = query.getSearchRequest().copy()
                    .requestedRange(probeRange)
                    .build();
            return queryServiceProvider.get().downloadSearchResults(new DownloadQueryResultsRequest(
                    limited, DownloadSearchResultFileType.MARKDOWN, false, 100, true));
        }
        throw new IllegalStateException("Cannot download the rows of " + (context == null
                ? "nothing"
                : context.getClass().getSimpleName()));
    }


    // --------------------------------------------------------------------------------


    /// A table, downloaded.
    ///
    /// @param rows      How many rows were kept.
    /// @param columns   How many columns it has.
    /// @param truncated True if there were more rows than kept.
    public record Downloaded(long rows, int columns, boolean truncated) {

    }
}
