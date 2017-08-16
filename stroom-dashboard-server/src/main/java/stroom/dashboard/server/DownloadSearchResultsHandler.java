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

package stroom.dashboard.server;

import org.springframework.context.annotation.Scope;
import stroom.dashboard.server.download.DelimitedTarget;
import stroom.dashboard.server.download.ExcelTarget;
import stroom.dashboard.server.download.SearchResultWriter;
import stroom.dashboard.server.format.FieldFormatter;
import stroom.dashboard.server.format.FormatterFactory;
import stroom.dashboard.server.logging.SearchEventLog;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.DownloadSearchResultsAction;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.datasource.DataSourceProvider;
import stroom.datasource.DataSourceProviderRegistry;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.EntityServiceException;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.Result;
import stroom.query.api.v1.Row;
import stroom.security.Secured;
import stroom.servlet.SessionResourceStore;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@TaskHandlerBean(task = DownloadSearchResultsAction.class)
@Scope(StroomScope.PROTOTYPE)
@Secured(Dashboard.DOWNLOAD_SEARCH_RESULTS_PERMISSION)
class DownloadSearchResultsHandler extends AbstractTaskHandler<DownloadSearchResultsAction, ResourceGeneration> {
    private static final Pattern NON_BASIC_CHARS = Pattern.compile("[^A-Za-z0-9-_ ]");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile(" +");

    private final SessionResourceStore sessionResourceStore;
    private final SearchEventLog searchEventLog;
    private final ActiveQueriesManager activeQueriesManager;
    private final DataSourceProviderRegistry searchDataSourceProviderRegistry;
    private final SearchRequestMapper searchRequestMapper;

    @Inject
    DownloadSearchResultsHandler(final SessionResourceStore sessionResourceStore,
                                 final SearchEventLog searchEventLog,
                                 final ActiveQueriesManager activeQueriesManager,
                                 final DataSourceProviderRegistry searchDataSourceProviderRegistry,
                                 final SearchRequestMapper searchRequestMapper) {
        this.sessionResourceStore = sessionResourceStore;
        this.searchEventLog = searchEventLog;
        this.activeQueriesManager = activeQueriesManager;
        this.searchDataSourceProviderRegistry = searchDataSourceProviderRegistry;
        this.searchRequestMapper = searchRequestMapper;
    }

    @Override
    public ResourceGeneration exec(final DownloadSearchResultsAction action) {
        ResourceKey resourceKey;

        final DashboardQueryKey queryKey = action.getQueryKey();
        final stroom.dashboard.shared.SearchRequest searchRequest = action.getSearchRequest();
        final Search search = searchRequest.getSearch();

        try {
            final String searchSessionId = action.getUserToken() + "_" + action.getApplicationInstanceId();
            final ActiveQueries activeQueries = activeQueriesManager.getOrCreate(searchSessionId);

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

            stroom.query.api.v1.SearchRequest mappedRequest = searchRequestMapper.mapRequest(queryKey, searchRequest);
            stroom.query.api.v1.SearchResponse searchResponse = dataSourceProvider.search(mappedRequest);

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

            if (!(result instanceof stroom.query.api.v1.TableResult)) {
                throw new EntityServiceException("Result is not a table");
            }

            final stroom.query.api.v1.TableResult tableResult = (stroom.query.api.v1.TableResult) result;

            // Import file.
            String fileName = action.getQueryKey().toString();
            fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
            fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
            fileName = fileName + "." + action.getFileType().getExtension();

            resourceKey = sessionResourceStore.createTempFile(fileName);
            final Path file = sessionResourceStore.getTempFile(resourceKey);

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

            download(fields, rows, file, action.getFileType(), action.isSample(),
                    action.getPercent(), action.getDateTimeLocale());

            searchEventLog.downloadResults(search.getDataSourceRef(), search.getExpression());
        } catch (final Exception ex) {
            searchEventLog.downloadResults(search.getDataSourceRef(), search.getExpression(), ex);
            throw EntityServiceExceptionUtil.create(ex);
        }

        return new ResourceGeneration(resourceKey, new ArrayList<>());
    }

    private void download(final List<stroom.dashboard.shared.Field> fields, final List<Row> rows, final Path file,
                          final DownloadSearchResultFileType fileType, final boolean sample, final int percent, final String dateTimeLocale) {
        final FormatterFactory formatterFactory = new FormatterFactory(dateTimeLocale);
        final FieldFormatter fieldFormatter = new FieldFormatter(formatterFactory);

        try {
            final OutputStream outputStream = Files.newOutputStream(file);
            SearchResultWriter.Target target = null;

            // Write delimited file.
            switch (fileType) {
                case CSV:
                    target = new DelimitedTarget(fieldFormatter, outputStream, ",");
                    break;
                case TSV:
                    target = new DelimitedTarget(fieldFormatter, outputStream, "\t");
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
