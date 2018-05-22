/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard;

import stroom.dashboard.download.DelimitedTarget;
import stroom.dashboard.download.ExcelTarget;
import stroom.dashboard.download.SearchResultWriter;
import stroom.dashboard.logging.SearchEventLog;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.DownloadSearchResultsAction;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.datasource.DataSourceProvider;
import stroom.datasource.DataSourceProviderRegistry;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.Row;
import stroom.resource.ResourceStore;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@TaskHandlerBean(task = DownloadSearchResultsAction.class)
class DownloadSearchResultsHandler extends AbstractTaskHandler<DownloadSearchResultsAction, ResourceGeneration> {
    private static final Pattern NON_BASIC_CHARS = Pattern.compile("[^A-Za-z0-9-_ ]");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile(" +");

    private final ResourceStore resourceStore;
    private final SearchEventLog searchEventLog;
    private final ActiveQueriesManager activeQueriesManager;
    private final DataSourceProviderRegistry searchDataSourceProviderRegistry;
    private final SearchRequestMapper searchRequestMapper;
    private final Security security;

    @Inject
    DownloadSearchResultsHandler(final ResourceStore resourceStore,
                                 final SearchEventLog searchEventLog,
                                 final ActiveQueriesManager activeQueriesManager,
                                 final DataSourceProviderRegistry searchDataSourceProviderRegistry,
                                 final SearchRequestMapper searchRequestMapper,
                                 final Security security) {
        this.resourceStore = resourceStore;
        this.searchEventLog = searchEventLog;
        this.activeQueriesManager = activeQueriesManager;
        this.searchDataSourceProviderRegistry = searchDataSourceProviderRegistry;
        this.searchRequestMapper = searchRequestMapper;
        this.security = security;
    }

    @Override
    public ResourceGeneration exec(final DownloadSearchResultsAction action) {
        return security.secureResult(PermissionNames.DOWNLOAD_SEARCH_RESULTS_PERMISSION, () -> {
            ResourceKey resourceKey;

            final DashboardQueryKey queryKey = action.getQueryKey();
            final stroom.dashboard.shared.SearchRequest searchRequest = action.getSearchRequest();
            final Search search = searchRequest.getSearch();

            try {
                final String searchSessionId = action.getUserToken() + "_" + action.getApplicationInstanceId();
                final ActiveQueries activeQueries = activeQueriesManager.get(searchSessionId);

                // Make sure we have active queries for all current UI queries.
                // Note: This also ensures that the active query cache is kept alive
                // for all open UI components.
                final ActiveQuery activeQuery = activeQueries.getExistingQuery(action.getQueryKey());
                if (activeQuery == null) {
                    throw new EntityServiceException("The requested search data is not available");
                }

                // Perform the search or update results.
                final DocRef dataSourceRef = search.getDataSourceRef();
                if (dataSourceRef == null || dataSourceRef.getUuid() == null) {
                    throw new RuntimeException("No search data source has been specified");
                }

                // Get the data source provider for this query.
                final DataSourceProvider dataSourceProvider = searchDataSourceProviderRegistry
                        .getDataSourceProvider(dataSourceRef)
                        .orElseThrow(() ->
                                new RuntimeException("No search provider found for '" + dataSourceRef.getType() + "' data source"));

                stroom.query.api.v2.SearchRequest mappedRequest = searchRequestMapper.mapRequest(queryKey, searchRequest);
                stroom.query.api.v2.SearchResponse searchResponse = dataSourceProvider.search(mappedRequest);

                if (searchResponse == null || searchResponse.getResults() == null) {
                    throw new EntityServiceException("No results can be found");
                }

                Result result = null;
                for (final Result res : searchResponse.getResults()) {
                    if (res.getComponentId().equals(action.getComponentId())) {
                        result = res;
                        break;
                    }
                }

                if (result == null) {
                    throw new EntityServiceException("No result for component can be found");
                }

                if (!(result instanceof stroom.query.api.v2.TableResult)) {
                    throw new EntityServiceException("Result is not a table");
                }

                final stroom.query.api.v2.TableResult tableResult = (stroom.query.api.v2.TableResult) result;

                // Import file.
                String fileName = action.getQueryKey().toString();
                fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
                fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
                fileName = fileName + "." + action.getFileType().getExtension();

                resourceKey = resourceStore.createTempFile(fileName);
                final Path file = resourceStore.getTempFile(resourceKey);

                final ComponentResultRequest componentResultRequest = searchRequest.getComponentResultRequests().get(action.getComponentId());
                if (componentResultRequest == null) {
                    throw new EntityServiceException("No component result request found");
                }

                if (!(componentResultRequest instanceof TableResultRequest)) {
                    throw new EntityServiceException("Component result request is not a table");
                }

                final TableResultRequest tableResultRequest = (TableResultRequest) componentResultRequest;
                final List<stroom.dashboard.shared.Field> fields = tableResultRequest.getTableSettings().getFields();
                final List<Row> rows = tableResult.getRows();

                download(fields, rows, file, action.getFileType(), action.isSample(), action.getPercent());

                searchEventLog.downloadResults(search.getDataSourceRef(), search.getExpression(), search.getQueryInfo());
            } catch (final RuntimeException e) {
                searchEventLog.downloadResults(search.getDataSourceRef(), search.getExpression(), search.getQueryInfo(), e);
                throw EntityServiceExceptionUtil.create(e);
            }

            return new ResourceGeneration(resourceKey, new ArrayList<>());
        });
    }

    private void download(final List<stroom.dashboard.shared.Field> fields, final List<Row> rows, final Path file,
                          final DownloadSearchResultFileType fileType, final boolean sample, final int percent) {
        try {
            final OutputStream outputStream = Files.newOutputStream(file);
            SearchResultWriter.Target target = null;

            // Write delimited file.
            switch (fileType) {
                case CSV:
                    target = new DelimitedTarget(outputStream, ",");
                    break;
                case TSV:
                    target = new DelimitedTarget(outputStream, "\t");
                    break;
                case EXCEL:
                    target = new ExcelTarget(outputStream);
                    break;
            }

            final SampleGenerator sampleGenerator = new SampleGenerator(sample, percent);
            final SearchResultWriter searchResultWriter = new SearchResultWriter(fields, rows, sampleGenerator);
            searchResultWriter.write(target);

        } catch (final IOException e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }
}
