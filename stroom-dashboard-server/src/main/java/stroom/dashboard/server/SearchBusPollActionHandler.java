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
import stroom.dashboard.shared.QueryEntity;
import stroom.dashboard.shared.QueryService;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.SearchBusPollAction;
import stroom.dashboard.shared.SearchBusPollResult;
import stroom.dashboard.shared.SearchRequest;
import stroom.dashboard.shared.SearchResponse;
import stroom.dashboard.shared.SharedQueryKey;
import stroom.logging.SearchEventLog;
import stroom.query.api.DocRef;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.security.SecurityContext;
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
    private final SearchEventLog searchEventLog;
    private final DataSourceProviderRegistry searchDataSourceProviderRegistry;
    private final ActiveQueriesManager searchSessionManager;
    private final SearchRequestMapper searchRequestMapper;
    private final SecurityContext securityContext;

    @Inject
    SearchBusPollActionHandler(final QueryService queryService,
                               final SearchEventLog searchEventLog,
                               final DataSourceProviderRegistry searchDataSourceProviderRegistry,
                               final ActiveQueriesManager searchSessionManager,
                               final SearchRequestMapper searchRequestMapper,
                               final SecurityContext securityContext) {
        this.queryService = queryService;
        this.searchEventLog = searchEventLog;
        this.searchDataSourceProviderRegistry = searchDataSourceProviderRegistry;
        this.searchSessionManager = searchSessionManager;
        this.searchRequestMapper = searchRequestMapper;
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
                sb.append(action.getSessionId());
                sb.append("'\n");
                for (final QueryKey queryKey : action.getSearchActionMap().keySet()) {
                    sb.append("\t");
                    sb.append(queryKey.toString());
                }
                LOGGER.debug(sb.toString());
            }

            final String searchSessionId = action.getSessionId() + "_" + action.getApplicationInstanceId();
            final ActiveQueries searchSession = searchSessionManager.get(searchSessionId);
            final Map<QueryKey, SearchResponse> searchResultMap = new HashMap<>();

            // Fix query keys so they have session and user info.
            for (final Entry<QueryKey, SearchRequest> entry : action.getSearchActionMap().entrySet()) {
                final QueryKey queryKey = entry.getKey();
                queryKey.setSessionId(action.getSessionId());
                queryKey.setUserId(action.getUserId());
            }

            // Kill off any queries that are no longer required by the UI.
            searchSession.destroyUnusedQueries(action.getSearchActionMap().keySet());

            // Get query results for every active query.
            for (final Entry<QueryKey, SearchRequest> entry : action.getSearchActionMap().entrySet()) {
                final QueryKey queryKey = entry.getKey();

                final SearchRequest searchRequest = entry.getValue();

                if (searchRequest != null && searchRequest.getSearch() != null) {
                    final SearchResponse searchResponse = processRequest(searchSession, queryKey, searchRequest);
                    if (searchResponse != null) {
                        searchResultMap.put(queryKey, searchResponse);
                    }
                }
            }

            return new SearchBusPollResult(searchResultMap);
        } finally {
            securityContext.restorePermissions();
        }
    }

    private SearchResponse processRequest(final ActiveQueries activeQueries, final QueryKey queryKey, final SearchRequest searchRequest) {
        SearchResponse result = null;

        boolean newSearch = false;
        final Search search = searchRequest.getSearch();

        try {
            synchronized (SearchBusPollActionHandler.class) {
                // Make sure we have active queries for all current UI queries.
                // Note: This also ensures that the active query cache is kept alive
                // for all open UI components.
                ActiveQuery activeQuery = activeQueries.getExistingQuery(queryKey);

                // If the query doesn't have an active query for this query key then
                // this is new.
                if (activeQuery == null) {
                    newSearch = true;

                    // Store the new active query for this query.
                    activeQuery = activeQueries.addNewQuery(queryKey, search.getDataSourceRef());

                    // Add this search to the history so the user can get back to this
                    // search again.
                    storeSearchHistory(queryKey, search);
                }
            }

            // Perform the search or update results.
            final DocRef dataSourceRef = search.getDataSourceRef();
            if (dataSourceRef == null || dataSourceRef.getUuid() == null) {
                throw new RuntimeException("No search data source has been specified");
            }

            // Get the data source provider for this query.
            final DataSourceProvider dataSourceProvider = searchDataSourceProviderRegistry.getDataSourceProvider(dataSourceRef);

            if (dataSourceProvider == null) {
                throw new RuntimeException(
                        "No search provider found for '" + dataSourceRef.getType() + "' data source");
            }

            stroom.query.api.SearchRequest mappedRequest = searchRequestMapper.mapRequest(queryKey, searchRequest);
            stroom.query.api.SearchResponse searchResponse = dataSourceProvider.search(mappedRequest);

            // TODO : Write response mapping code.
            // result = mapResponse(searchResponse);

            if (newSearch) {
                // Log this search action for the current user.
                searchEventLog.search(search.getDataSourceRef(), search.getExpression());
            }

        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);

            if (newSearch) {
                searchEventLog.search(search.getDataSourceRef(), search.getExpression(), e);
            }

            result = new SearchResponse();
            result.setErrors(e.getMessage());
            result.setComplete(true);
        }

        return result;
    }

    private void storeSearchHistory(final QueryKey queryKey, final Search search) {
        try {
            if (queryKey instanceof SharedQueryKey) {
                final SharedQueryKey queryKeyImpl = (SharedQueryKey) queryKey;

                // Add this search to the history so the user can get back to
                // this search again.
                Param[] params;
                if (search.getParamMap() != null && search.getParamMap().size() > 0) {
                    params = new Param[search.getParamMap().size()];
                    int i = 0;
                    for (final Entry<String, String> entry : search.getParamMap().entrySet()) {
                        params[i++] = new Param(entry.getKey(), entry.getValue());
                    }
                } else {
                    params = null;
                }

                final Query query = new Query(search.getDataSourceRef(), search.getExpression(), params);
                final QueryEntity queryEntity = queryService.create(null, "History");

                queryEntity.setDashboard(Dashboard.createStub(queryKeyImpl.getDashboardId()));
                queryEntity.setQuery(query);
                queryService.save(queryEntity);
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }


}
