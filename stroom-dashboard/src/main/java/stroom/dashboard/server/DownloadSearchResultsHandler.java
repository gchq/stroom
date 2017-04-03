/*
 * Copyright 2016 Crown Copyright
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
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.DownloadSearchResultsAction;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.EntityServiceException;
import stroom.logging.SearchEventLog;
import stroom.query.ResultStore;
import stroom.query.shared.Field;
import stroom.query.shared.Search;
import stroom.security.Secured;
import stroom.servlet.SessionResourceStore;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
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
public class DownloadSearchResultsHandler extends AbstractTaskHandler<DownloadSearchResultsAction, ResourceGeneration> {
    private static final Pattern NON_BASIC_CHARS = Pattern.compile("[^A-Za-z0-9-_ ]");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile(" +");

    @Resource
    private SessionResourceStore sessionResourceStore;
    @Resource
    private TaskManager taskManager;
    @Resource
    private SearchEventLog searchEventLog;
    @Resource
    private ActiveQueriesManager activeQueriesManager;

    @Override
    public ResourceGeneration exec(final DownloadSearchResultsAction action) {
        if (action.getSearch() == null) {
            return null;
        }

        final Search search = action.getSearch();

        ResourceKey resourceKey = null;
        try {
            // Import file.
            String fileName = action.getQueryKey().toString();
            fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
            fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
            fileName = fileName + "." + action.getFileType().getExtension();

            resourceKey = sessionResourceStore.createTempFile(fileName);
            final Path file = sessionResourceStore.getTempFile(resourceKey);

            final ActiveQueries searchSession = activeQueriesManager.get(action.getSessionId());
            final ActiveQuery activeQuery = searchSession.getExistingQuery(action.getQueryKey());

            if (activeQuery == null) {
                throw new EntityServiceException("The requested search data is not available");
            }

            download(activeQuery, action.getComponentId(), file, action.getFileType().toString(), action.isSample(),
                    action.getPercent(), action.getDateTimeLocale());

            searchEventLog.downloadResults(search.getDataSourceRef(), search.getExpression());
        } catch (final Exception ex) {
            searchEventLog.downloadResults(search.getDataSourceRef(), search.getExpression(), ex);

            throw EntityServiceExceptionUtil.create(ex);
        }

        return new ResourceGeneration(resourceKey, new ArrayList<>());
    }

    private void download(final ActiveQuery activeQuery, final String componentId, final Path file,
                          final String fileType, final boolean sample, final int percent, final String dateTimeLocale) {
        final FormatterFactory formatterFactory = new FormatterFactory(dateTimeLocale);
        final FieldFormatter fieldFormatter = new FieldFormatter(formatterFactory);

        try {
            // The result handler could possibly have not been set yet if the
            // AsyncSearchTask has not started execution.
            final ResultStore resultStore = activeQuery.getSearchResultCollector().getResultStore(componentId);
            if (resultStore == null) {
                throw new EntityServiceException("Search has not started yet");
            }

            final OutputStream outputStream = Files.newOutputStream(file);
            SearchResultWriter.Target target = null;

            // Write delimited file.
            switch (fileType) {
                case "CSV":
                    target = new DelimitedTarget(fieldFormatter, outputStream, ",");
                    break;
                case "TSV":
                    target = new DelimitedTarget(fieldFormatter, outputStream, "\t");
                    break;
                case "EXCEL":
                    target = new ExcelTarget(outputStream);
                    break;
            }

            if (target == null) {
                throw new RuntimeException("No target created for file type: " + fileType);
            }

            final ComponentResultCreator componentResultCreator = activeQuery.getComponentResultCreatorMap()
                    .get(componentId);
            final TableComponentResultCreator tableComponentResultCreator = (TableComponentResultCreator) componentResultCreator;
            final List<Field> fields = tableComponentResultCreator.getFields();

            final SampleGenerator sampleGenerator = new SampleGenerator(sample, percent);
            final SearchResultWriter searchResultWriter = new SearchResultWriter(resultStore, fields, sampleGenerator);
            searchResultWriter.write(target);

        } catch (final IOException e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }
}
