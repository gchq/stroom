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
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.Query;
import stroom.dashboard.shared.QueryKeyImpl;
import stroom.dashboard.shared.QueryService;
import stroom.dashboard.shared.SearchBusPollAction;
import stroom.dashboard.shared.SearchBusPollResult;
import stroom.logging.SearchEventLog;
import stroom.query.SearchDataSourceProvider;
import stroom.query.SearchResultCollector;
import stroom.query.shared.QueryData;
import stroom.query.shared.QueryKey;
import stroom.query.shared.Search;
import stroom.query.shared.SearchRequest;
import stroom.query.shared.SearchResult;
import stroom.security.SecurityContext;
import stroom.task.cluster.ClusterResultCollector;
import stroom.task.cluster.ClusterResultCollectorCache;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@TaskHandlerBean(task = SearchBusPollAction.class)
@Scope(value = StroomScope.TASK)
class SearchBusPollActionHandler extends AbstractTaskHandler<SearchBusPollAction, SearchBusPollResult> {
    private transient static final StroomLogger LOGGER = StroomLogger.getLogger(SearchBusPollActionHandler.class);

    private final QueryService queryService;
    private final SearchResultCreator searchResultCreator;
    private final SearchEventLog searchEventLog;
    private final SearchDataSourceProviderRegistry searchDataSourceProviderRegistry;
    private final ActiveQueriesManager activeQueriesManager;
    private final ClusterResultCollectorCache clusterResultCollectorCache;
    private final SecurityContext securityContext;

    @Inject
    SearchBusPollActionHandler(final QueryService queryService, final SearchResultCreator searchResultCreator,
                               final SearchEventLog searchEventLog,
                               final SearchDataSourceProviderRegistry searchDataSourceProviderRegistry,
                               final ActiveQueriesManager activeQueriesManager,
                               final ClusterResultCollectorCache clusterResultCollectorCache, final SecurityContext securityContext) {
        this.queryService = queryService;
        this.searchResultCreator = searchResultCreator;
        this.searchEventLog = searchEventLog;
        this.searchDataSourceProviderRegistry = searchDataSourceProviderRegistry;
        this.activeQueriesManager = activeQueriesManager;
        this.clusterResultCollectorCache = clusterResultCollectorCache;
        this.securityContext = securityContext;
    }

    @Override
    public SearchBusPollResult exec(final SearchBusPollAction action) {
        try {
            // Elevate the users permissions for the duration of this task so they can read the index if they have 'use' permission.
            securityContext.elevatePermissions();

            if (LOGGER.isDebugEnabled()) {
                final StringBuilder sb = new StringBuilder(
                        "Only the following search queries should be active for session '");
                sb.append(action.getUserToken());
                sb.append("'\n");
                for (final QueryKey queryKey : action.getSearchActionMap().keySet()) {
                    sb.append("\t");
                    sb.append(queryKey.toString());
                }
                LOGGER.debug(sb.toString());
            }

            final String searchSessionId = action.getUserToken() + "_" + action.getApplicationInstanceId();
            final ActiveQueries searchSession = activeQueriesManager.get(searchSessionId);
            final Map<QueryKey, SearchResult> searchResultMap = new HashMap<>();

            // First kill off any queries that are no longer required by the UI.
            searchSession.destroyUnusedQueries(action.getSearchActionMap().keySet());

            // Get query results for every active query.
            for (final Entry<QueryKey, SearchRequest> entry : action.getSearchActionMap().entrySet()) {
                final QueryKey queryKey = entry.getKey();
                final SearchRequest searchRequest = entry.getValue();

                final SearchResult searchResult = processRequest(action.getUserToken(),
                        searchSession, queryKey, searchRequest);
                if (searchResult != null) {
                    searchResultMap.put(queryKey, searchResult);
                }
            }

            return new SearchBusPollResult(searchResultMap);
        } finally {
            securityContext.restorePermissions();
        }
    }

    private SearchResult processRequest(final String userToken, final ActiveQueries activeQueries,
                                        final QueryKey queryKey, final SearchRequest searchRequest) {
        SearchResult result = null;

        try {
            // Make sure we have active queries for all current UI queries.
            // Note: This also ensures that the active query cache is kept alive
            // for all open UI components.
            ActiveQuery activeQuery = activeQueries.getExistingQuery(queryKey);

            // If the query doesn't have an active query for this query key then
            // this is new.
            if (activeQuery == null) {
                // Create a collector for this query.
                final SearchResultCollector newCollector = createCollector(userToken, queryKey, searchRequest);

                // Create a new active query to store the result collector and
                // any other state that we wish to maintain for the duration of
                // the query.
                final ActiveQuery newQuery = new ActiveQuery(newCollector);

                // Store the new active query for this query.
                activeQueries.addNewQuery(queryKey, newQuery);

                // Start asynchronous search execution.
                newCollector.start();

                activeQuery = newQuery;
            }

            // Keep the cluster result collector cache fresh.
            if (activeQuery.getSearchResultCollector() instanceof ClusterResultCollector<?>) {
                clusterResultCollectorCache
                        .get(((ClusterResultCollector<?>) activeQuery.getSearchResultCollector()).getId());
            }

            // Perform the search or update results.
            if (searchRequest != null) {
                result = searchResultCreator.createResult(activeQuery, searchRequest);
            }
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);

            result = new SearchResult();
            result.setErrors(e.getMessage());
            result.setComplete(true);
        }

        return result;
    }

    private SearchResultCollector createCollector(final String userToken, final QueryKey queryKey,
                                                  final SearchRequest searchRequest) {
        final Search search = searchRequest.getSearch();
        try {
            if (search.getDataSourceRef() == null || search.getDataSourceRef().getUuid() == null) {
                throw new RuntimeException("No search data source has been specified");
            }
            final SearchDataSourceProvider searchDataSourceProvider = searchDataSourceProviderRegistry
                    .getProvider(search.getDataSourceRef().getType());
            if (searchDataSourceProvider == null) {
                throw new RuntimeException(
                        "No search provider found for '" + search.getDataSourceRef().getType() + "' data source");
            }

            // Create a collector for this search.
            final SearchResultCollector searchResultCollector = searchDataSourceProvider.createCollector(userToken, queryKey, searchRequest);

            // Add this search to the history so the user can get back to this
            // search again.
            storeSearchHistory(queryKey, search);

            // Log this search action for the current user.
            searchEventLog.search(search.getDataSourceRef(), search.getExpression());

            return searchResultCollector;

        } catch (final Exception e) {
            searchEventLog.search(search.getDataSourceRef(), search.getExpression(), e);
            throw e;
        }
    }

    private void storeSearchHistory(final QueryKey queryKey, final Search search) {
        try {
            if (queryKey instanceof QueryKeyImpl) {
                final QueryKeyImpl queryKeyImpl = (QueryKeyImpl) queryKey;

                // Add this search to the history so the user can get back to
                // this search again.
                final QueryData queryData = new QueryData();
                queryData.setDataSource(search.getDataSourceRef());
                queryData.setExpression(search.getExpression());

                final Query query = queryService.create(null, "History");

                query.setDashboard(Dashboard.createStub(queryKeyImpl.getDashboardId()));
                query.setQueryData(queryData);
                queryService.save(query);
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
